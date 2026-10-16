package com.adulthub.tv.player

import com.adulthub.tv.diagnostics.AppLog as Log
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.AuthDataStore
import com.adulthub.tv.data.local.PhashJobStore
import com.adulthub.tv.data.local.StoredPhashJob
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.AuthState
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.resolver.PhashGenerationRequest
import com.adulthub.tv.data.resolver.ResolverApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException

private const val PHASH_TAG = "PlaybackPhash"
const val STASH_COMPATIBLE_PHASH_FRAME_COUNT = 25
private val TELEGRAM_VIDEO_ID_REGEX = Regex("""^telegram:-?\d+:\d+:\d{1,20}$""")

internal fun resolvePhashDurationUs(runtimeTicks: Long?, playerDurationMs: Long?): Long? {
    val itemDurationUs = runtimeTicks?.takeIf { it > 0L }?.div(10L)?.takeIf { it > 0L }
    if (itemDurationUs != null) return itemDurationUs
    return playerDurationMs?.takeIf { it > 0L }?.times(1_000L)
}

internal fun calculatePhashTargetTimestamps(durationUs: Long): List<Long> {
    require(durationUs > 0) { "durationUs must be positive" }
    return List(STASH_COMPATIBLE_PHASH_FRAME_COUNT) { index ->
        (durationUs.toDouble() * (0.05 + 0.90 * index.toDouble() / STASH_COMPATIBLE_PHASH_FRAME_COUNT.toDouble())).toLong()
    }
}

internal fun adultHubVideoId(item: TvItem): String? {
    val raw = item.providerVideoId?.takeIf { it.isNotBlank() }
        ?: item.id.takeIf { it.startsWith("telegram:") }
        ?: return null
    val fullId = if (raw.startsWith("telegram:")) raw else "telegram:$raw"
    return fullId.takeIf { it.matches(TELEGRAM_VIDEO_ID_REGEX) }
}
class PhashCoordinator(
    private val authDataStore: AuthDataStore,
    private val jellyfinClientManager: JellyfinClientManager,
    private val settingsDataStore: SettingsDataStore,
    private val apiClient: ResolverApiClient,
    private val jobStore: PhashJobStore,
    private val applicationScope: kotlinx.coroutines.CoroutineScope,
) {
    private val inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val _jobStatuses = MutableStateFlow<Map<String, PhashUiStatus>>(emptyMap())
    val jobStatuses: StateFlow<Map<String, PhashUiStatus>> = _jobStatuses.asStateFlow()

    init {
        // A job already accepted by the resolver outlives the player and the app process.
        applicationScope.launch {
            jobStore.getAll().forEach { record ->
                setStatus(record.videoId, statusFor(record))
                if (record.jobId != null && record.state in ACTIVE_STATES) start(record.videoId)
                else if (record.state == "submitting" && record.durationUs != null) {
                    start(record.videoId, durationOverrideUs = record.durationUs)
                }
            }
        }
    }

    /** Runs in application scope; leaving the player cannot cancel enqueue or status polling. */
    fun enqueueGeneration(item: TvItem, observedDurationMs: Long?) {
        val videoId = adultHubVideoId(item) ?: return
        start(videoId, item, observedDurationMs)
    }

    private fun start(
        videoId: String,
        item: TvItem? = null,
        observedDurationMs: Long? = null,
        durationOverrideUs: Long? = null,
    ) {
        if (!inFlight.add(videoId)) return
        applicationScope.launch {
            try {
                process(videoId, item, observedDurationMs, durationOverrideUs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.i(PHASH_TAG, "job handling failed (${error.javaClass.simpleName})")
                setStatus(videoId, PhashUiStatus("Resolver status unavailable; try again later"))
            } finally {
                inFlight.remove(videoId)
            }
        }
    }

    private suspend fun process(
        videoId: String,
        item: TvItem?,
        observedDurationMs: Long?,
        durationOverrideUs: Long?,
    ) {
        val stored = jobStore.get(videoId)
        if (stored != null && stored.state in ACTIVE_STATES && !stored.jobId.isNullOrBlank()) {
            poll(videoId, stored.jobId!!)
            return
        }
        if (stored != null && stored.state in TERMINAL_STATES) {
            setStatus(videoId, statusFor(stored))
            return
        }
        if (stored == null && !settingsDataStore.phashOnPlayback.first()) return
        val durationUs = durationOverrideUs ?: stored?.durationUs
            ?: item?.let { resolvePhashDurationUs(it.runtimeTicks, observedDurationMs) }
        if (durationUs == null || durationUs <= 0L) {
            Log.i(PHASH_TAG, "skipped: duration unavailable")
            return
        }
        if (authDataStore.getAuthState() !is AuthState.Authenticated) {
            setStatus(videoId, PhashUiStatus("Sign in to queue video matching"))
            return
        }
        val token = jellyfinClientManager.getApiOrNull()?.accessToken?.takeIf { it.isNotBlank() }
        if (token == null) {
            setStatus(videoId, PhashUiStatus("Sign in to queue video matching"))
            return
        }
        val resolverUrl = settingsDataStore.getResolverBaseUrl()
        if (resolverUrl.isBlank()) {
            setStatus(videoId, PhashUiStatus("Set a Resolver URL to queue video matching"))
            return
        }

        setStatus(videoId, PhashUiStatus("Sending task to Resolver"))
        jobStore.save(StoredPhashJob(videoId = videoId, state = "submitting", durationUs = durationUs))
        var lastHttpCode: Int? = null
        for (attempt in 0..2) {
            try {
                val api = apiClient.create(settingsDataStore.requireAllowedServiceUrl(resolverUrl).normalized)
                val response = withTimeout(15_000L) {
                    api.generatePhash(token, PhashGenerationRequest(videoId = videoId, durationUs = durationUs))
                }
                if (!response.ok) {
                    jobStore.remove(videoId)
                    setStatus(videoId, PhashUiStatus("Resolver rejected the video matching task"))
                    return
                }
                val jobId = response.jobId
                if (jobId.isNullOrBlank()) {
                    if (response.alreadyComputed || response.state.equals("done", true)) {
                        jobStore.save(StoredPhashJob(videoId, state = "done"))
                        setStatus(videoId, PhashUiStatus("Video matching complete", terminal = true))
                    } else {
                        jobStore.remove(videoId)
                        setStatus(videoId, PhashUiStatus("Resolver returned no task ID"))
                    }
                    return
                }
                val state = response.state.orEmpty().lowercase()
                val record = StoredPhashJob(
                    videoId = videoId,
                    jobId = jobId,
                    state = state.ifBlank { "queued" },
                    errorCode = response.errorCode,
                    durationUs = durationUs,
                )
                jobStore.save(record)
                setStatus(videoId, statusFor(record))
                if (response.alreadyComputed || state in SUCCESS_STATES) {
                    jobStore.save(record.copy(state = "done", errorCode = null))
                    setStatus(videoId, PhashUiStatus("Video matching complete", terminal = true))
                } else if (state in ACTIVE_STATES || state.isBlank()) {
                    poll(videoId, jobId)
                } else {
                    setStatus(videoId, statusFor(record))
                }
                return
            } catch (error: HttpException) {
                lastHttpCode = error.code()
                if (error.code() == 401 || error.code() == 403) {
                    jobStore.remove(videoId)
                    setStatus(videoId, PhashUiStatus("Jellyfin authorization was rejected"))
                    Log.i(PHASH_TAG, "enqueue request failed (HTTP ${error.code()})")
                    return
                }
                if (!isRetryable(error.code())) {
                    if (error.code() == 409) {
                        jobStore.save(StoredPhashJob(
                            videoId = videoId,
                            state = "failed",
                            errorCode = "terminal_job",
                            durationUs = durationUs,
                        ))
                    } else {
                        jobStore.remove(videoId)
                    }
                    val message = if (error.code() == 409) {
                        "This video matching task failed earlier; retry it from Resolver tasks"
                    } else "Resolver rejected the task (HTTP ${error.code()})"
                    setStatus(videoId, PhashUiStatus(message, terminal = error.code() == 409))
                    Log.i(PHASH_TAG, "enqueue request failed (HTTP ${error.code()})")
                    return
                }
                Log.i(PHASH_TAG, "enqueue request failed (HTTP ${error.code()})")
            } catch (_: TimeoutCancellationException) {
                Log.i(PHASH_TAG, "enqueue request timed out")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.i(PHASH_TAG, "enqueue request failed (${error.javaClass.simpleName})")
            }
            if (attempt < 2) delay(1_000L * (attempt + 1))
        }
        // Keep the submitting record: repeating POST is safe because the resolver returns the
        // existing queued/processing job for the same video and server profile.
        setStatus(videoId, PhashUiStatus(
            if (lastHttpCode == null) "Resolver unavailable; task will retry when opened again"
            else "Resolver unavailable (HTTP $lastHttpCode); task will retry when opened again",
        ))
    }

    private suspend fun poll(videoId: String, jobId: String) {
        var backoffMs = STATUS_POLL_INTERVAL_MS
        while (true) {
            delay(backoffMs)
            val token = jellyfinClientManager.getApiOrNull()?.accessToken?.takeIf { it.isNotBlank() }
            if (token == null) {
                setStatus(videoId, PhashUiStatus("Sign in again to check video matching"))
                return
            }
            try {
                val resolverUrl = settingsDataStore.getResolverBaseUrl()
                val api = apiClient.create(settingsDataStore.requireAllowedServiceUrl(resolverUrl).normalized)
                val response = withTimeout(15_000L) { api.getPhashJob(token, jobId) }
                val job = response.job
                if (!response.ok || job == null || job.id != jobId || job.videoId != videoId) {
                    setStatus(videoId, PhashUiStatus("Resolver returned an invalid task status"))
                    return
                }
                val profile = job.decoderProfile
                if (profile != null && profile !in SUPPORTED_DECODER_PROFILES) {
                    setStatus(videoId, PhashUiStatus("Resolver task uses an unsupported decoder profile"))
                    return
                }
                val state = job.state.lowercase()
                val record = StoredPhashJob(videoId, jobId, state, job.errorCode)
                jobStore.save(record)
                when (state) {
                    in SUCCESS_STATES -> {
                        setStatus(videoId, PhashUiStatus("Video matching complete", terminal = true))
                        return
                    }
                    "failed", "cancelled" -> {
                        setStatus(videoId, statusFor(record))
                        return
                    }
                    in ACTIVE_STATES -> {
                        setStatus(videoId, statusFor(record))
                        backoffMs = STATUS_POLL_INTERVAL_MS
                    }
                    else -> {
                        setStatus(videoId, PhashUiStatus("Resolver task state: $state"))
                        backoffMs = STATUS_POLL_INTERVAL_MS
                    }
                }
            } catch (_: TimeoutCancellationException) {
                Log.i(PHASH_TAG, "status request timed out; retrying")
                setStatus(videoId, PhashUiStatus("Resolver unavailable; retrying status check"))
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_STATUS_BACKOFF_MS)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: HttpException) {
                if (error.code() == 401 || error.code() == 403) {
                    setStatus(videoId, PhashUiStatus("Jellyfin authorization was rejected; sign in again"))
                    return
                }
                if (error.code() == 404) {
                    jobStore.remove(videoId)
                    setStatus(videoId, PhashUiStatus("Resolver task is no longer available"))
                    return
                }
                if (!isRetryable(error.code())) {
                    setStatus(videoId, PhashUiStatus("Could not check Resolver task (HTTP ${error.code()})"))
                    return
                }
                Log.i(PHASH_TAG, "status request failed (HTTP ${error.code()}); retrying")
                setStatus(videoId, PhashUiStatus("Resolver unavailable; retrying status check"))
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_STATUS_BACKOFF_MS)
            } catch (error: Exception) {
                Log.i(PHASH_TAG, "status request failed (${error.javaClass.simpleName}); retrying")
                setStatus(videoId, PhashUiStatus("Resolver unavailable; retrying status check"))
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_STATUS_BACKOFF_MS)
            }
        }
    }

    private fun setStatus(videoId: String, status: PhashUiStatus) {
        _jobStatuses.value = _jobStatuses.value + (videoId to status)
    }

    private fun statusFor(record: StoredPhashJob): PhashUiStatus = when (record.state.lowercase()) {
        "submitting" -> PhashUiStatus("Sending task to Resolver")
        "queued" -> PhashUiStatus("Video matching queued on Resolver")
        "processing" -> PhashUiStatus("Resolver is processing video matching")
        in SUCCESS_STATES -> PhashUiStatus("Video matching complete", terminal = true)
        "failed", "cancelled" -> PhashUiStatus(
            "Video matching failed${record.errorCode?.let { " ($it)" }.orEmpty()}", terminal = true,
        )
        else -> PhashUiStatus("Resolver task state: ${record.state}")
    }

    private fun isRetryable(code: Int): Boolean = code == 429 || code >= 500

    companion object {
        private const val STATUS_POLL_INTERVAL_MS = 5_000L
        private const val MAX_STATUS_BACKOFF_MS = 60_000L
        private val ACTIVE_STATES = setOf("queued", "processing")
        private val SUCCESS_STATES = setOf("done", "completed", "succeeded")
        private val TERMINAL_STATES = SUCCESS_STATES + setOf("failed", "cancelled")
        private val SUPPORTED_DECODER_PROFILES = setOf("resolver-ffmpeg-stash-v2", "resolver-ffmpeg-v1")
    }
}

data class PhashUiStatus(val message: String, val terminal: Boolean = false)
