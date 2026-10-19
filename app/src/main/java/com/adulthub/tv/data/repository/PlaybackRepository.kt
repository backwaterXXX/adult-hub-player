package com.adulthub.tv.data.repository

import com.adulthub.tv.diagnostics.AppLog as Log
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.PlaybackInfo
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.resolver.resolveResolverUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.RepeatMode
import java.util.UUID
import java.net.URI

private const val PLAYBACK_TAG = "PlaybackRepository"

private suspend inline fun <T> capturePlaybackResult(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

open class PlaybackRepository(
    private val clientManager: JellyfinClientManager,
    private val settingsDataStore: SettingsDataStore,
) {
    open suspend fun resolvePlaybackInfo(item: TvItem): Result<PlaybackInfo> = withContext(Dispatchers.IO) {
        try {
            val uuid = runCatching { UUID.fromString(item.id) }.getOrNull()
            val userId = clientManager.currentUserId?.let { runCatching { UUID.fromString(it) }.getOrNull() }

            val playbackInfoResponse = if (uuid != null) {
                val authenticatedUserId = userId ?: return@withContext Result.failure(
                    IllegalStateException("Authenticated Jellyfin user ID is unavailable"),
                )
                val api = clientManager.getApi()
                api.mediaInfoApi.getPlaybackInfo(
                    itemId = uuid,
                    userId = authenticatedUserId,
                ).content
            } else null

            val mediaSource = selectPlayableMediaSource(playbackInfoResponse?.mediaSources.orEmpty())
            mediaSource?.let { selected ->
                Log.i(
                    PLAYBACK_TAG,
                    "Selected playback source: container=${selected.container ?: "unknown"}, " +
                        "directPlay=${selected.supportsDirectPlay}, " +
                        "directStream=${selected.supportsDirectStream}, bitrate=${selected.bitrate ?: 0}",
                )
            }
            val mediaSourcePath = mediaSource?.path
            val playSessionId = playbackInfoResponse?.playSessionId
            val mediaSourceId = mediaSource?.id ?: item.mediaSourceId

            val streamUrl = when {
                !mediaSourcePath.isNullOrBlank() && mediaSourcePath.startsWith("http", ignoreCase = true) -> {
                    val configuredResolver = settingsDataStore.getResolverBaseUrl()
                    val allowedResolver = if (configuredResolver.isBlank()) "" else {
                        settingsDataStore.requireAllowedServiceUrl(configuredResolver).normalized
                    }
                    rewriteResolverMediaSourceUrl(
                        mediaSourcePath = mediaSourcePath,
                        resolverOrigin = allowedResolver,
                    )
                }
                !item.providerVideoId.isNullOrBlank() -> {
                    val resolverUrl = settingsDataStore.getResolverBaseUrl()
                    if (resolverUrl.isBlank()) {
                        return@withContext Result.failure(IllegalStateException("Set a Resolver URL in Settings before playback"))
                    }
                    val resolverOrigin = settingsDataStore.requireAllowedServiceUrl(resolverUrl).normalized
                    val ref = if (item.providerVideoId.startsWith("telegram:")) {
                        item.providerVideoId
                    } else {
                        "telegram:${item.providerVideoId}"
                    }
                    "$resolverOrigin/admin/play/$ref/stream?mode=proxy"
                }
                item.id.startsWith("telegram:") -> {
                    val resolverUrl = settingsDataStore.getResolverBaseUrl()
                    if (resolverUrl.isBlank()) {
                        return@withContext Result.failure(IllegalStateException("Set a Resolver URL in Settings before playback"))
                    }
                    val resolverOrigin = settingsDataStore.requireAllowedServiceUrl(resolverUrl).normalized
                    "$resolverOrigin/admin/play/${item.id}/stream?mode=proxy"
                }
                else -> {
                    return@withContext Result.failure(
                        IllegalStateException("No valid HTTP stream URL found for item ${item.title}")
                    )
                }
            }
            settingsDataStore.requireAllowedServiceUrl(streamUrl, allowQuery = true)

            val playbackInfo = PlaybackInfo(
                itemId = item.id,
                streamUrl = streamUrl,
                mediaSourceId = mediaSourceId,
                playSessionId = playSessionId,
                startPositionTicks = item.playbackPositionTicks,
                headers = mediaSource?.requiredHttpHeaders.orEmpty().mapNotNull { (name, value) ->
                    value?.let { name to it }
                }.toMap(),
                playMethod = when {
                    mediaSource?.supportsDirectPlay == true -> PlayMethod.DIRECT_PLAY
                    mediaSource?.supportsDirectStream == true -> PlayMethod.DIRECT_STREAM
                    else -> PlayMethod.TRANSCODE
                },
                title = item.title,
                fileSizeBytes = mediaSource?.size ?: item.fileSizeBytes,
                container = mediaSource?.container ?: item.container,
                durationMs = item.runtimeTicks?.div(10_000L),
            )
            Result.success(playbackInfo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun reportPlaybackStart(
        itemId: String,
        mediaSourceId: String?,
        positionTicks: Long = 0L,
        playSessionId: String? = null,
        canSeek: Boolean,
        playMethod: PlayMethod,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uuid = runCatching { UUID.fromString(itemId) }.getOrNull() ?: return@withContext Result.success(Unit)
        capturePlaybackResult {
            val api = clientManager.getApi()
            api.playStateApi.reportPlaybackStart(
                data = PlaybackStartInfo(
                    itemId = uuid,
                    mediaSourceId = mediaSourceId,
                    positionTicks = positionTicks,
                    playSessionId = playSessionId,
                    canSeek = canSeek,
                    isMuted = false,
                    isPaused = false,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                    playMethod = playMethod,
                )
            )
            Unit
        }
    }

    open suspend fun reportPlaybackProgress(
        itemId: String,
        mediaSourceId: String?,
        positionTicks: Long,
        playSessionId: String? = null,
        isPaused: Boolean = false,
        canSeek: Boolean,
        playMethod: PlayMethod,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uuid = runCatching { UUID.fromString(itemId) }.getOrNull() ?: return@withContext Result.success(Unit)
        capturePlaybackResult {
            val api = clientManager.getApi()
            api.playStateApi.reportPlaybackProgress(
                data = PlaybackProgressInfo(
                    itemId = uuid,
                    mediaSourceId = mediaSourceId,
                    positionTicks = positionTicks,
                    playSessionId = playSessionId,
                    canSeek = canSeek,
                    isMuted = false,
                    isPaused = isPaused,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                    playMethod = playMethod,
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
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uuid = runCatching { UUID.fromString(itemId) }.getOrNull() ?: return@withContext Result.success(Unit)
        capturePlaybackResult {
            val api = clientManager.getApi()
            api.playStateApi.reportPlaybackStopped(
                data = PlaybackStopInfo(
                    itemId = uuid,
                    mediaSourceId = mediaSourceId,
                    positionTicks = positionTicks,
                    playSessionId = playSessionId,
                    failed = false,
                )
            )
            Unit
        }
    }
}

private fun selectPlayableMediaSource(sources: List<MediaSourceInfo>): MediaSourceInfo? =
    sources
        .filter { source -> source.path?.startsWith("http", ignoreCase = true) == true }
        .maxWithOrNull(
            compareBy<MediaSourceInfo> { it.supportsDirectPlay }
                .thenBy { it.supportsDirectStream }
                .thenBy { it.bitrate ?: 0 },
        )

internal fun rewriteResolverMediaSourceUrl(mediaSourcePath: String, resolverOrigin: String): String {
    val uri = runCatching { URI(mediaSourcePath) }.getOrNull() ?: return mediaSourcePath
    if (!uri.rawPath.orEmpty().startsWith("/admin/play/")) return mediaSourcePath
    return resolveResolverUrl(mediaSourcePath, resolverOrigin)
}
