package com.adulthub.tv.player

import android.content.Context
import com.adulthub.tv.R
import com.adulthub.tv.diagnostics.AppLog as Log
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.AccountScope
import com.adulthub.tv.data.local.AuthDataStore
import com.adulthub.tv.data.local.PhashJobStore
import com.adulthub.tv.data.local.SessionOwner
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
import kotlinx.coroutines.flow.update
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
    private val accountScope: AccountScope? = null,
    private val budget: PhashBudget = PhashBudget(),
    private val context: Context,
) {
    private val inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val _jobStatuses = MutableStateFlow<Map<String, PhashUiStatus>>(emptyMap())
    val jobStatuses: StateFlow<Map<String, PhashUiStatus>> = _jobStatuses.asStateFlow()
    private val timeoutMessage: String
        get() = context.getString(R.string.phash_timeout)

    init {
        // A job already accepted by the resolver outlives the player and the app process.
        applicationScope.launch {
            val owner = accountScope?.currentOwner()
            val nowMs = System.currentTimeMillis()
            jobStore.getAll(owner?.accountKey).forEach { record ->
                if (!budget.mayResume(record, nowMs)) {
                    // The task used up its budget while the app was not running. It is
                    // not continued and it leaves the store, so it is not started
                    // again at the next launch.
                    jobStore.remove(record.videoId, owner?.accountKey)
                    setStatus(record.videoId, PhashUiStatus(timeoutMessage, terminal = true))
                    return@forEach
                }
                setStatus(record.videoId, statusFor(record))
                if (record.jobId != null && record.state in ACTIVE_STATES) {
                    start(record.videoId, startedAtMs = record.startedAt)
                } else if (record.state == "submitting" && record.durationUs != null) {
                    start(record.videoId, durationOverrideUs = record.durationUs, startedAtMs = record.startedAt)
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
        startedAtMs: Long? = null,
    ) {
        if (!inFlight.add(videoId)) return
        applicationScope.launch {
            try {
                // The owner is captured when the job starts. A job of an older session
                // must not continue with the token or the Resolver of a newer session.
                val owner = accountScope?.currentOwner()
                process(videoId, item, observedDurationMs, durationOverrideUs, owner, startedAtMs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.i(PHASH_TAG, "job handling failed (${error.javaClass.simpleName})")
                setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_status_unavailable)))
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
        owner: SessionOwner?,
        startedAtMs: Long?,
    ) {
        val accountKey = owner?.accountKey
        val stored = jobStore.get(videoId, accountKey)
        // The moment the task was submitted. It stays the same over every update of
        // the task, so the budget measures the life of the task and not the life of
        // the process.
        val startedAt = startedAtMs ?: stored?.startedAt ?: System.currentTimeMillis()
        val resolverUrl = settingsDataStore.getResolverBaseUrl()
        // The token belongs to the session that starts the task. A task of an older
        // session is never continued with the token of a newer session.
        val token = jellyfinClientManager.getApiOrNull()?.accessToken?.takeIf { it.isNotBlank() }
        if (stored != null && stored.state in ACTIVE_STATES && !stored.jobId.isNullOrBlank()) {
            if (token == null) {
                setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_sign_in_to_check)))
                return
            }
            poll(videoId, stored.jobId!!, owner, resolverUrl, token, startedAt, stored.durationUs)
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
            setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_sign_in_to_queue)))
            return
        }
        if (token == null) {
            setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_sign_in_to_queue)))
            return
        }
        if (resolverUrl.isBlank()) {
            setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_set_resolver_url)))
            return
        }

        setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_sending)))
        jobStore.save(
            StoredPhashJob(
                videoId = videoId,
                state = "submitting",
                durationUs = durationUs,
                startedAt = startedAt,
            ),
            accountKey,
        )
        var lastHttpCode: Int? = null
        for (attempt in 0..2) {
            try {
                val api = apiClient.create(settingsDataStore.requireAllowedServiceUrl(resolverUrl).normalized)
                val response = withTimeout(15_000L) {
                    api.generatePhash(token, PhashGenerationRequest(videoId = videoId, durationUs = durationUs))
                }
                if (!response.ok) {
                    jobStore.remove(videoId, accountKey)
                    setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_rejected_task)))
                    return
                }
                val jobId = response.jobId
                if (jobId.isNullOrBlank()) {
                    if (response.alreadyComputed || response.state.equals("done", true)) {
                        jobStore.save(StoredPhashJob(videoId, state = "done"), accountKey)
                        setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_complete), terminal = true))
                    } else {
                        jobStore.remove(videoId, accountKey)
                        setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_no_task_id)))
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
                    startedAt = startedAt,
                )
                jobStore.save(record, accountKey)
                setStatus(videoId, statusFor(record))
                if (response.alreadyComputed || state in SUCCESS_STATES) {
                    jobStore.save(record.copy(state = "done", errorCode = null), accountKey)
                    setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_complete), terminal = true))
                } else if (state in ACTIVE_STATES || state.isBlank()) {
                    poll(videoId, jobId, owner, resolverUrl, token, startedAt, durationUs)
                } else {
                    setStatus(videoId, statusFor(record))
                }
                return
            } catch (error: HttpException) {
                lastHttpCode = error.code()
                if (error.code() == 401 || error.code() == 403) {
                    jobStore.remove(videoId, accountKey)
                    setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_jellyfin_rejected)))
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
                        ), accountKey)
                    } else {
                        jobStore.remove(videoId, accountKey)
                    }
                    val message = if (error.code() == 409) {
                        context.getString(R.string.phash_earlier_task_failed)
                    } else {
                        context.getString(R.string.phash_rejected_http, error.code())
                    }
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
            if (lastHttpCode == null) context.getString(R.string.phash_unavailable_retry)
            else context.getString(R.string.phash_unavailable_http_retry, lastHttpCode),
        ))
    }

    /**
     * Polls the status of a submitted task.
     *
     * The token and the Resolver address are the ones the task was started with.
     * A task of an older session is not continued with the credentials of a newer
     * session; the poll stops and the task keeps the state of its owner.
     *
     * The polling is bounded by the budget of the task. A task that used it up is
     * not polled again and is stored as failed with the reason of the timeout.
     */
    private suspend fun poll(
        videoId: String,
        jobId: String,
        owner: SessionOwner?,
        resolverUrl: String,
        token: String,
        startedAtMs: Long,
        durationUs: Long?,
    ) {
        var backoffMs = STATUS_POLL_INTERVAL_MS
        var polls = 0
        val accountKey = owner?.accountKey
        while (true) {
            if (budget.isExhausted(startedAtMs, System.currentTimeMillis(), polls)) {
                expireTask(videoId, jobId, accountKey, startedAtMs, durationUs)
                return
            }
            delay(backoffMs)
            // The budget can run out while the request waits.
            if (budget.isExhausted(startedAtMs, System.currentTimeMillis(), polls)) {
                expireTask(videoId, jobId, accountKey, startedAtMs, durationUs)
                return
            }
            if (owner != null && accountScope?.isCurrent(owner) == false) {
                setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_session_changed)))
                return
            }
            // The task belongs to the Resolver it was sent to. It is not continued
            // against another address.
            if (settingsDataStore.getResolverBaseUrl() != resolverUrl) {
                setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_resolver_changed)))
                return
            }
            try {
                polls += 1
                val api = apiClient.create(settingsDataStore.requireAllowedServiceUrl(resolverUrl).normalized)
                val response = withTimeout(15_000L) { api.getPhashJob(token, jobId) }
                val job = response.job
                if (!response.ok || job == null || job.id != jobId || job.videoId != videoId) {
                    setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_invalid_status)))
                    return
                }
                val profile = job.decoderProfile
                if (profile != null && profile !in SUPPORTED_DECODER_PROFILES) {
                    setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_unsupported_decoder)))
                    return
                }
                val state = job.state.lowercase()
                val record = StoredPhashJob(
                    videoId = videoId,
                    jobId = jobId,
                    state = state,
                    errorCode = job.errorCode,
                    durationUs = durationUs,
                    startedAt = startedAtMs,
                )
                jobStore.save(record, accountKey)
                when (state) {
                    in SUCCESS_STATES -> {
                        setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_complete), terminal = true))
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
                        setStatus(videoId, PhashUiStatus(
                            context.getString(R.string.phash_unsupported_state, state),
                            terminal = true,
                        ))
                        return
                    }
                }
            } catch (_: TimeoutCancellationException) {
                Log.i(PHASH_TAG, "status request timed out; retrying")
                setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_status_retrying)))
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_STATUS_BACKOFF_MS)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: HttpException) {
                if (error.code() == 401 || error.code() == 403) {
                    setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_jellyfin_rejected_again)))
                    return
                }
                if (error.code() == 404) {
                    jobStore.remove(videoId, accountKey)
                    setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_task_gone)))
                    return
                }
                if (!isRetryable(error.code())) {
                    setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_check_failed_http, error.code())))
                    return
                }
                Log.i(PHASH_TAG, "status request failed (HTTP ${error.code()}); retrying")
                setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_status_retrying)))
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_STATUS_BACKOFF_MS)
            } catch (error: Exception) {
                Log.i(PHASH_TAG, "status request failed (${error.javaClass.simpleName}); retrying")
                setStatus(videoId, PhashUiStatus(context.getString(R.string.phash_status_retrying)))
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_STATUS_BACKOFF_MS)
            }
        }
    }

    /**
     * Ends a task that used up its budget.
     *
     * The task is stored as failed with the reason of the timeout, so it is not
     * continued at the next start of the app and it does not stay in the list of
     * running tasks.
     */
    private suspend fun expireTask(
        videoId: String,
        jobId: String?,
        accountKey: String?,
        startedAtMs: Long,
        durationUs: Long?,
    ) {
        jobStore.save(
            StoredPhashJob(
                videoId = videoId,
                jobId = jobId,
                state = "failed",
                errorCode = TIMEOUT_ERROR_CODE,
                durationUs = durationUs,
                startedAt = startedAtMs,
            ),
            accountKey,
        )
        setStatus(videoId, PhashUiStatus(timeoutMessage, terminal = true))
        Log.i(PHASH_TAG, "the task used up its polling budget")
    }

    private fun setStatus(videoId: String, status: PhashUiStatus) {
        _jobStatuses.update { current ->
            val updated = current + (videoId to status)
            if (updated.size <= 200) updated else updated.entries.toList().takeLast(200).associate { it.key to it.value }
        }
    }

    private fun statusFor(record: StoredPhashJob): PhashUiStatus = when (record.state.lowercase()) {
        "submitting" -> PhashUiStatus(context.getString(R.string.phash_sending))
        "queued" -> PhashUiStatus(context.getString(R.string.phash_queued))
        "processing" -> PhashUiStatus(context.getString(R.string.phash_processing))
        in SUCCESS_STATES -> PhashUiStatus(context.getString(R.string.phash_complete), terminal = true)
        "failed", "cancelled" -> PhashUiStatus(
            if (record.errorCode == TIMEOUT_ERROR_CODE) {
                timeoutMessage
            } else {
                record.errorCode?.let { context.getString(R.string.phash_failed_with_code, it) }
                    ?: context.getString(R.string.phash_failed)
            },
            terminal = true,
        )
        else -> PhashUiStatus(context.getString(R.string.phash_state, record.state))
    }

    private fun isRetryable(code: Int): Boolean = code == 429 || code >= 500

    companion object {
        private const val STATUS_POLL_INTERVAL_MS = 5_000L
        private const val MAX_STATUS_BACKOFF_MS = 60_000L
        private const val TIMEOUT_ERROR_CODE = "timeout"
        private val ACTIVE_STATES = setOf("queued", "processing")
        private val SUCCESS_STATES = setOf("done", "completed", "succeeded")
        private val TERMINAL_STATES = SUCCESS_STATES + setOf("failed", "cancelled")
        private val SUPPORTED_DECODER_PROFILES = setOf("resolver-ffmpeg-stash-v2", "resolver-ffmpeg-v1")
    }
}

data class PhashUiStatus(val message: String, val terminal: Boolean = false)
