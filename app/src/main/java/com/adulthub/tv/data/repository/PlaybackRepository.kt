package com.adulthub.tv.data.repository

import android.net.Uri
import com.adulthub.tv.diagnostics.AppLog as Log
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.PlaybackInfo
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.resolver.ORIGINAL_VARIANT_ID
import com.adulthub.tv.data.resolver.VideoVariantsResult
import com.adulthub.tv.data.resolver.ResolverApi
import com.adulthub.tv.data.resolver.classifyVariantsError
import com.adulthub.tv.data.resolver.classifyVariantsResponse
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val PLAYBACK_TAG = "PlaybackRepository"
private const val VARIANT_QUERY_PARAMETER = "variant"

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
    private val resolverApi: ResolverApi? = null,
) {
    open suspend fun resolvePlaybackInfo(
        item: TvItem,
        variantId: String? = null,
    ): Result<PlaybackInfo> = withContext(Dispatchers.IO) {
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

            val resolverRef = telegramResolverRef(item)
            val hasHttpMediaSource = !mediaSourcePath.isNullOrBlank() &&
                mediaSourcePath.startsWith("http", ignoreCase = true)
            val resolverServedSource = hasHttpMediaSource &&
                runCatching { URI(mediaSourcePath.orEmpty()).rawPath }.getOrNull()
                    ?.startsWith("/admin/play/") == true
            val selectedVariantId = variantId?.trim()?.takeIf { it.isNotEmpty() && it != ORIGINAL_VARIANT_ID }
            val explicitVariantId = variantId?.trim()?.takeIf { it.isNotEmpty() }
            val streamUrl = when {
                hasHttpMediaSource -> {
                    val configuredResolver = settingsDataStore.getResolverBaseUrl()
                    val allowedResolver = if (configuredResolver.isBlank()) "" else {
                        settingsDataStore.requireAllowedServiceUrl(configuredResolver).normalized
                    }
                    // An unset variant must not touch the address at all; only an explicit
                    // "original" clears a variant that the source already carries.
                    appendVariantQuery(
                        rewriteResolverMediaSourceUrl(
                            mediaSourcePath = mediaSourcePath,
                            resolverOrigin = allowedResolver,
                        ),
                        explicitVariantId.takeIf { resolverServedSource },
                    )
                }
                resolverRef != null -> {
                    val resolverUrl = settingsDataStore.getResolverBaseUrl()
                    if (resolverUrl.isBlank()) {
                        return@withContext Result.failure(IllegalStateException("Set a Resolver URL in Settings before playback"))
                    }
                    val resolverOrigin = settingsDataStore.requireAllowedServiceUrl(resolverUrl).normalized
                    resolverStreamUrl(resolverOrigin, resolverRef, selectedVariantId)
                }
                else -> {
                    return@withContext Result.failure(
                        IllegalStateException("No valid HTTP stream URL found for item ${item.title}")
                    )
                }
            }
            val resolverVideoId = resolverRef?.takeIf { !hasHttpMediaSource || resolverServedSource }
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
                resolverVideoId = resolverVideoId,
                variantId = selectedVariantId,
            )
            Result.success(playbackInfo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches the selectable qualities of a resolver video.
     *
     * A resolver without the variants feature, a video without alternatives and a
     * failed request are three different outcomes; none of them may block playback,
     * which continues on the original stream regardless.
     */
    open suspend fun fetchVideoVariants(resolverVideoId: String?): VideoVariantsResult {
        val api = resolverApi ?: return VideoVariantsResult.Unsupported
        if (resolverVideoId.isNullOrBlank()) return VideoVariantsResult.Unsupported
        return try {
            classifyVariantsResponse(api.getVideoVariants(resolverVideoId))
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

/** The identifier the resolver variants and stream endpoints expect for this item. */
internal fun telegramResolverRef(item: TvItem): String? = when {
    !item.providerVideoId.isNullOrBlank() ->
        if (item.providerVideoId.startsWith("telegram:")) item.providerVideoId
        else "telegram:${item.providerVideoId}"
    item.id.startsWith("telegram:") -> item.id
    else -> null
}

internal fun resolverStreamUrl(origin: String, ref: String, variantId: String?): String =
    appendVariantQuery("$origin/admin/play/${Uri.encode(ref)}/stream?mode=proxy", variantId)

/**
 * Sets or clears the `variant` query parameter.
 *
 * Without an explicit choice the address is returned untouched: rebuilding a URL through
 * HttpUrl normalizes percent-escapes, default ports and trailing slashes, which must never
 * happen to a signed media source. Only the deliberate "original" selection clears the
 * parameter, because a stale one would pin the wrong file version.
 */
internal fun appendVariantQuery(url: String, variantId: String?): String {
    val requested = variantId?.trim()?.takeIf { it.isNotEmpty() } ?: return url
    val isOriginal = requested == ORIGINAL_VARIANT_ID
    val httpUrl = url.toHttpUrlOrNull()
    if (httpUrl != null) {
        val builder = httpUrl.newBuilder()
        if (isOriginal) {
            builder.removeAllQueryParameters(VARIANT_QUERY_PARAMETER)
        } else {
            builder.setQueryParameter(VARIANT_QUERY_PARAMETER, requested)
        }
        return builder.build().toString()
    }
    if (isOriginal) return url
    val encoded = buildString {
        for (character in requested) {
            if (character.isLetterOrDigit() || character in "-._~") append(character)
            else append("%%%02X".format(character.code))
        }
    }
    val separator = if ('?' in url) '&' else '?'
    return "$url$separator" + "$VARIANT_QUERY_PARAMETER=$encoded"
}
