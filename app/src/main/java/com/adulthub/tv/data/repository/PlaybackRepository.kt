package com.adulthub.tv.data.repository

import com.adulthub.tv.data.api.AdultHubClientManager
import com.adulthub.tv.data.api.WatchProgressUpdateDto
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.PlayMethod
import com.adulthub.tv.data.model.PlaybackInfo
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.resolver.ORIGINAL_VARIANT_ID
import com.adulthub.tv.data.resolver.VideoVariant
import com.adulthub.tv.data.resolver.VideoVariantsResult
import com.adulthub.tv.data.resolver.classifyVariantsError
import com.adulthub.tv.diagnostics.AppLog as Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PLAYBACK_TAG = "PlaybackRepository"

private suspend inline fun <T> capturePlaybackResult(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

open class PlaybackRepository(
    private val clientManager: AdultHubClientManager,
    private val settingsDataStore: SettingsDataStore
) {
    private val itemDurations = ConcurrentHashMap<String, Int>()
    private val itemPlayedStatus = ConcurrentHashMap<String, Boolean>()

    /**
     * Drops the per-account playback caches. Durations and played flags belong to one
     * account: a played flag of the previous user must not leak into the watch-progress
     * reports of the next one. The repository module invalidates on account changes.
     */
    fun invalidateCache() {
        itemDurations.clear()
        itemPlayedStatus.clear()
    }

    open suspend fun resolvePlaybackInfo(
        item: TvItem,
        variantId: String? = null
    ): Result<PlaybackInfo> = withContext(Dispatchers.IO) {
        try {
            // Canonical catalog ids are provider ids ("telegram:<channel>:<msg>:<file>"), so the
            // only id this cannot resolve is an empty one.
            if (item.id.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Cannot start playback without a video id")
                )
            }
            if (item.isPlayed) {
                itemPlayedStatus[item.id] = true
            }

            val api = clientManager.getApi()
            val descriptor = api.getPlaybackDescriptor(item.id)

            val durationSec = descriptor.durationSeconds?.toInt()
                ?: item.runtimeTicks?.div(10_000_000L)?.toInt()
                ?: 0
            if (durationSec > 0) {
                itemDurations[item.id] = durationSec
            }

            val selectedVariantId = variantId?.trim()?.takeIf { it.isNotEmpty() && it != ORIGINAL_VARIANT_ID }
            val selectedRendition = descriptor.renditions.firstOrNull { it.id == selectedVariantId }
            val streamPath = selectedRendition?.streamPath ?: descriptor.streamPath

            val session = clientManager.getSessionSnapshot()
                ?: (
                    clientManager.currentServerUrl?.let { url ->
                        com.adulthub.tv.data.api.ClientSession(
                            serverUrl = url,
                            accessToken = clientManager.currentAccessToken.orEmpty(),
                            userId = clientManager.currentUserId.orEmpty(),
                            username = clientManager.currentUsername.orEmpty()
                        )
                    }
                    )
                ?: return@withContext Result.failure(IllegalStateException("No authenticated server session for playback"))

            val origin = session.serverUrl
            if (origin.isBlank()) {
                return@withContext Result.failure(IllegalStateException("No server origin configured for playback"))
            }
            val allowedOrigin = settingsDataStore.requireAllowedServiceUrl(origin).normalized
            val streamUrl = "${allowedOrigin.trimEnd('/')}/${streamPath.trimStart('/')}"
            settingsDataStore.requireAllowedServiceUrl(streamUrl, allowQuery = true)

            val token = session.accessToken
            val headers = if (token.isNotBlank()) {
                mapOf("Authorization" to "Bearer $token")
            } else {
                emptyMap()
            }

            val playbackInfo = PlaybackInfo(
                itemId = item.id,
                streamUrl = streamUrl,
                mediaSourceId = selectedRendition?.id ?: descriptor.videoId,
                playSessionId = null,
                startPositionTicks = item.playbackPositionTicks,
                headers = headers,
                playMethod = PlayMethod.DIRECT_PLAY,
                title = item.title,
                fileSizeBytes = selectedRendition?.fileSizeBytes ?: descriptor.fileSizeBytes ?: item.fileSizeBytes,
                container = descriptor.mimeType?.substringAfterLast('/') ?: "mp4",
                durationMs = descriptor.durationSeconds?.let { (it * 1000).toLong() } ?: item.runtimeTicks?.div(10_000L),
                resolverVideoId = descriptor.videoId,
                variantId = selectedVariantId
            )
            Result.success(playbackInfo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun fetchVideoVariants(resolverVideoId: String?): VideoVariantsResult {
        if (resolverVideoId.isNullOrBlank()) {
            return VideoVariantsResult.OriginalOnly
        }
        return try {
            val api = clientManager.getApiOrNull() ?: return VideoVariantsResult.OriginalOnly
            val descriptor = api.getPlaybackDescriptor(resolverVideoId)
            if (descriptor.renditions.isNotEmpty()) {
                val variants = descriptor.renditions.map { r ->
                    VideoVariant(
                        id = r.id,
                        isOriginal = r.isDefault,
                        width = r.width,
                        height = r.height,
                        codec = null,
                        mimeType = r.mimeType,
                        fileSizeBytes = r.fileSizeBytes,
                        durationSec = descriptor.durationSeconds,
                        bitrate = null,
                        estimatedAverageBitrate = null
                    )
                }
                val alternatives = variants.filterNot { it.isOriginal }
                if (alternatives.isNotEmpty()) {
                    VideoVariantsResult.Available(variants)
                } else {
                    VideoVariantsResult.OriginalOnly
                }
            } else {
                VideoVariantsResult.OriginalOnly
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            classifyVariantsError(error)
        }
    }

    open suspend fun reportPlaybackStart(
        itemId: String,
        mediaSourceId: String?,
        positionTicks: Long = 0L,
        playSessionId: String? = null,
        canSeek: Boolean,
        playMethod: PlayMethod
    ): Result<Unit> = Result.success(Unit)

    open suspend fun reportPlaybackProgress(
        itemId: String,
        mediaSourceId: String?,
        positionTicks: Long,
        playSessionId: String? = null,
        isPaused: Boolean = false,
        canSeek: Boolean,
        playMethod: PlayMethod,
        durationSeconds: Int? = null,
        isPlayed: Boolean? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        capturePlaybackResult {
            if (itemId.isBlank()) {
                // Without a video id there is nothing to report; local progress is still kept.
                return@capturePlaybackResult
            }
            val api = clientManager.getApiOrNull() ?: return@capturePlaybackResult
            val posSeconds = (positionTicks / 10_000_000L).toInt()
            val duration = durationSeconds ?: itemDurations[itemId] ?: 0
            val wasPlayed = itemPlayedStatus[itemId] ?: false
            val played = isPlayed ?: (wasPlayed || (duration > 0 && posSeconds >= (duration * 0.90).toInt()))
            if (played) {
                itemPlayedStatus[itemId] = true
            }
            api.putWatchProgress(
                videoId = itemId,
                body = WatchProgressUpdateDto(
                    positionSeconds = posSeconds,
                    durationSeconds = duration,
                    isPlayed = played
                )
            )
            Unit
        }
    }

    open suspend fun reportPlaybackStopped(
        itemId: String,
        mediaSourceId: String?,
        positionTicks: Long,
        playSessionId: String? = null,
        durationSeconds: Int? = null,
        isPlayed: Boolean? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        capturePlaybackResult {
            if (itemId.isBlank()) {
                // Without a video id there is nothing to report; local progress is still kept.
                return@capturePlaybackResult
            }
            val api = clientManager.getApiOrNull() ?: return@capturePlaybackResult
            val posSeconds = (positionTicks / 10_000_000L).toInt()
            val duration = durationSeconds ?: itemDurations[itemId] ?: 0
            val wasPlayed = itemPlayedStatus[itemId] ?: false
            val played = isPlayed ?: (wasPlayed || (duration > 0 && posSeconds >= (duration * 0.90).toInt()))
            if (played) {
                itemPlayedStatus[itemId] = true
            }
            api.putWatchProgress(
                videoId = itemId,
                body = WatchProgressUpdateDto(
                    positionSeconds = posSeconds,
                    durationSeconds = duration,
                    isPlayed = played
                )
            )
            Unit
        }
    }
}
