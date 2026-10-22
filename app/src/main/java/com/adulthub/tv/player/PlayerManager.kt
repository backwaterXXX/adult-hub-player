package com.adulthub.tv.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.adulthub.tv.diagnostics.AppLog as Log
import com.adulthub.tv.BuildConfig
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import com.adulthub.tv.data.local.BufferProfile
import com.adulthub.tv.data.network.ServiceUrlPolicy
import com.adulthub.tv.data.network.ServiceUrlValidation
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

private const val TAG = "PlayerManager"
private const val SEEK_INCREMENT_MS = 10_000L
private const val POSITION_POLL_INTERVAL_MS = 1_000L
private const val MAX_AUTOMATIC_RETRIES = 3
private const val STABLE_PLAYBACK_MS = 30_000L
private const val PLAYBACK_POLICY_ORIGIN_HEADER = "X-AdultHub-Playback-Origin"
private const val PLAYBACK_POLICY_HEADERS_HEADER = "X-AdultHub-Playback-Headers"

private data class PlaybackRequest(
    val itemId: String,
    val streamUrl: String,
    val title: String?,
    val headers: Map<String, String>,
)

@UnstableApi
class PlayerManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val bufferProfile: BufferProfile = BufferProfile.STANDARD,
) {
    val exoPlayer: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var positionPollJob: Job? = null
    private var retryJob: Job? = null
    private var playbackGeneration = 0L
    private val retryBudget = RetryBudget(STABLE_PLAYBACK_MS)
    private var totalRetryCount = 0
    private var playbackRequest: PlaybackRequest? = null
    private var released = false
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val allowedOrigin = request.header(PLAYBACK_POLICY_ORIGIN_HEADER).orEmpty()
                val forwardedHeaders = request.header(PLAYBACK_POLICY_HEADERS_HEADER)
                    .orEmpty()
                    .split(',')
                    .filter(String::isNotBlank)
                val requestWithoutPolicy = request.newBuilder()
                    .removeHeader(PLAYBACK_POLICY_ORIGIN_HEADER)
                    .removeHeader(PLAYBACK_POLICY_HEADERS_HEADER)
                val safeRequest = requestWithoutPolicy.build()
                if (request.url.isHttps) {
                    val redirectedOrigin = "${request.url.scheme}://${request.url.host}:${request.url.port}"
                    val originSafeRequest = if (redirectedOrigin == allowedOrigin) safeRequest else {
                        requestWithoutPolicy.apply {
                            forwardedHeaders.forEach(::removeHeader)
                            removeHeader("Authorization")
                            removeHeader("Cookie")
                            removeHeader("X-Emby-Token")
                        }.build()
                    }
                    chain.proceed(originSafeRequest)
                } else {
                    when (val validation = ServiceUrlPolicy.inspect(request.url.toString(), allowQuery = true)) {
                        is ServiceUrlValidation.Valid -> {
                            if (!validation.value.usesCleartext) {
                                throw IOException("Blocked an insecure stream redirect")
                            }
                            if (validation.value.origin != allowedOrigin) {
                                throw IOException("Blocked a cleartext redirect to a different host")
                            }
                            chain.proceed(safeRequest)
                        }
                        is ServiceUrlValidation.Invalid -> throw IOException("Blocked an insecure stream redirect: ${validation.reason}")
                    }
                }
            }
            .build()
    }
    private var preparingStartedAt: Long? = null
    private var readyLoggedForCurrentMedia = false
    private var videoDecoderName: String? = null
    private var audioDecoderName: String? = null
    private var totalDroppedFrames = 0
    private var firstFrameMs: Long? = null
    private var hasRenderedFirstFrame = false
    private var bufferingStartedAtMs: Long? = null
    private var rebufferCount = 0
    private var totalRebufferDurationMs = 0L
    private var pendingSeekAtMs: Long? = null
    private var seekBufferingStartedAtMs: Long? = null
    private var lastSeekBufferDurationMs: Long? = null
    @Volatile private var streamFileSizeBytes: Long? = null
    @Volatile private var streamContentType: String? = null
    val currentStreamFileSizeBytes: Long? get() = streamFileSizeBytes
    private val analyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            videoDecoderName = decoderName
            Log.i(TAG, "Video decoder initialized: $decoderName")
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            audioDecoderName = decoderName
            Log.i(TAG, "Audio decoder initialized: $decoderName")
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            totalDroppedFrames += droppedFrames
        }
    }

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _retryStatus = MutableStateFlow<String?>(null)
    val retryStatus: StateFlow<String?> = _retryStatus.asStateFlow()

    var onPlaybackReady: ((positionMs: Long) -> Unit)? = null
    var onPlaybackEnded: (() -> Unit)? = null
    var onPlaybackError: ((error: String) -> Unit)? = null

    /** Detaches the view before the player is released. */
    var onBeforeRelease: (() -> Unit)? = null

    /**
     * Provides a fresh stream URL for an automatic retry.
     *
     * A runtime error often means that the stream URL expired. The retry asks the
     * owner of the playback for a new address instead of reusing the old one.
     */
    var streamUrlProvider: (suspend (itemId: String, positionMs: Long) -> String?)? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _playbackState.value = playbackState
            val dur = exoPlayer.duration
            if (dur > 0L) {
                _durationMs.value = dur
            }

            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    val now = SystemClock.elapsedRealtime()
                    if (hasRenderedFirstFrame && bufferingStartedAtMs == null) {
                        bufferingStartedAtMs = now
                        rebufferCount++
                    }
                    val seekAt = pendingSeekAtMs
                    if (seekAt != null && now - seekAt <= SEEK_BUFFER_CORRELATION_WINDOW_MS) {
                        seekBufferingStartedAtMs = now
                        pendingSeekAtMs = null
                    } else if (seekAt != null) {
                        pendingSeekAtMs = null
                    }
                }
                Player.STATE_READY -> {
                    finishBufferingMeasurement()
                    // The retry budget is not reset here: a stream that reaches READY for a
                    // moment and fails again would otherwise retry forever. It is reset once
                    // the playback ran long enough to count as stable.
                    retryJob?.cancel()
                    retryJob = null
                    _retryStatus.value = null
                    _errorMessage.value = null
                    retryBudget.reset()
                    if (!readyLoggedForCurrentMedia) {
                        readyLoggedForCurrentMedia = true
                        preparingStartedAt?.let { startedAt ->
                            Log.i(TAG, "media_ready_ms=${SystemClock.elapsedRealtime() - startedAt}")
                        }
                    }
                    val currentPos = exoPlayer.currentPosition
                    _currentPositionMs.value = currentPos
                    onPlaybackReady?.invoke(currentPos)
                }
                Player.STATE_ENDED -> {
                    finishBufferingMeasurement()
                    onPlaybackEnded?.invoke()
                }
                Player.STATE_IDLE -> finishBufferingMeasurement()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK && hasRenderedFirstFrame) {
                pendingSeekAtMs = SystemClock.elapsedRealtime()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startPositionPolling()
            } else {
                stopPositionPolling()
                _currentPositionMs.value = exoPlayer.currentPosition
            }
        }

        override fun onRenderedFirstFrame() {
            val now = SystemClock.elapsedRealtime()
            preparingStartedAt?.let { startedAt ->
                if (firstFrameMs == null) firstFrameMs = now - startedAt
                Log.i(TAG, "first_frame_ms=$firstFrameMs")
            }
            hasRenderedFirstFrame = true
            preparingStartedAt = null
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer error: ${error.errorCodeName} (${error.errorCode})")
            if (scheduleAutomaticRetry(error)) return
            _retryStatus.value = null
            val friendlyMessage = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                    "Network error. Please check your connection."
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                    "Server error loading video stream."
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                    "Audio/video decoder error. Hardware codec failed to decode this stream."
                PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ->
                    "Audio output error on device."
                else -> error.localizedMessage ?: "Unknown playback error occurred."
            }
            val failureStage = when {
                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Server response"
                error.errorCodeName.startsWith("ERROR_CODE_IO_") -> "Network"
                error.errorCodeName.startsWith("ERROR_CODE_PARSING_") -> "Media format"
                error.errorCodeName.contains("DECOD", ignoreCase = true) -> "Video decoder"
                error.errorCodeName.startsWith("ERROR_CODE_AUDIO_TRACK_") -> "Audio output"
                else -> "Playback"
            }
            val diagnosticMessage = "$friendlyMessage\n\nStage: $failureStage\nCode: ${error.errorCodeName}"
            _errorMessage.value = diagnosticMessage
            onPlaybackError?.invoke(diagnosticMessage)
        }
    }

    init {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferProfile.minBufferMs,
                bufferProfile.maxBufferMs,
                bufferProfile.playbackStartMs,
                bufferProfile.rebufferMs,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
            .apply {
                addListener(playerListener)
                addAnalyticsListener(analyticsListener)
                playWhenReady = true
            }

        try {
            mediaSession = MediaSession.Builder(context, exoPlayer)
                .setId(MEDIA_SESSION_ID)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize MediaSession (${e.javaClass.simpleName})")
        }
    }

    fun prepareAndPlay(
        streamUrl: String,
        startPositionMs: Long = 0L,
        title: String? = null,
        headers: Map<String, String> = emptyMap(),
        itemId: String = "",
    ) {
        playbackGeneration += 1
        retryJob?.cancel()
        retryJob = null
        retryBudget.reset()
        totalRetryCount = 0
        _retryStatus.value = null
        playbackRequest = PlaybackRequest(itemId, streamUrl, title, headers.toMap())
        try {
            startPlayback(streamUrl, startPositionMs, title, headers, resetPlaybackSpeed = true, resumePlayback = true)
        } catch (error: Exception) {
            _errorMessage.value = error.message?.takeIf(String::isNotBlank) ?: "Could not prepare this video"
            Log.w(TAG, "Playback preparation failed (${error.javaClass.simpleName})")
        }
    }

    private fun startPlayback(
        streamUrl: String,
        startPositionMs: Long,
        title: String?,
        headers: Map<String, String>,
        resetPlaybackSpeed: Boolean,
        resumePlayback: Boolean,
    ) {
        check(!released) { "Cannot start playback after PlayerManager.release()" }
        _errorMessage.value = null
        _durationMs.value = 0L
        streamFileSizeBytes = null
        streamContentType = null
        videoDecoderName = null
        audioDecoderName = null
        // Playback statistics always describe the current attempt. Automatic retries therefore
        // restart the counters as well, otherwise several attempts are reported as one session
        // and the buffering of a retry is counted as a rebuffer of the previous attempt.
        totalDroppedFrames = 0
        firstFrameMs = null
        hasRenderedFirstFrame = false
        bufferingStartedAtMs = null
        rebufferCount = 0
        totalRebufferDurationMs = 0L
        pendingSeekAtMs = null
        seekBufferingStartedAtMs = null
        lastSeekBufferDurationMs = null
        if (resetPlaybackSpeed) {
            exoPlayer.setPlaybackSpeed(1.0f)
        }
        preparingStartedAt = SystemClock.elapsedRealtime()
        readyLoggedForCurrentMedia = false
        stopPositionPolling()

        val initialUrl = streamUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid stream URL")
        val initialOrigin = "${initialUrl.scheme}://${initialUrl.host}:${initialUrl.port}"
        val httpDataSourceFactory = OkHttpDataSource.Factory(httpClient)
            .setUserAgent("${BuildConfig.CLIENT_NAME}/${BuildConfig.VERSION_NAME} (${BuildConfig.DEVICE_NAME})")
            .setDefaultRequestProperties(
                headers + mapOf(
                    PLAYBACK_POLICY_ORIGIN_HEADER to initialOrigin,
                    PLAYBACK_POLICY_HEADERS_HEADER to headers.keys.joinToString(","),
                ),
            )

        val trackingDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            FileSizeTrackingDataSource(
                upstream = httpDataSourceFactory.createDataSource(),
                onFileSize = { size -> if (size > 0L) streamFileSizeBytes = size },
                onContentType = { contentType -> streamContentType = contentType },
            )
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(trackingDataSourceFactory)
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(title ?: "Video")
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setMediaMetadata(mediaMetadata)
            .build()

        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        exoPlayer.setMediaSource(mediaSource, startPositionMs.coerceAtLeast(0L))
        exoPlayer.prepare()
        _currentPositionMs.value = startPositionMs.coerceAtLeast(0L)

        exoPlayer.playWhenReady = resumePlayback
    }

    private fun scheduleAutomaticRetry(error: PlaybackException): Boolean {
        if (!retryBudget.canRetry(MAX_AUTOMATIC_RETRIES) || !isTransient(error)) return false
        val request = playbackRequest ?: return false
        val generation = playbackGeneration
        val attempt = retryBudget.registerFailure()
        totalRetryCount += 1
        val delayMs = retryDelayMs(attempt)
        _retryStatus.value = "Connection interrupted. Retrying automatically ($attempt/$MAX_AUTOMATIC_RETRIES)…"
        retryJob?.cancel()
        retryJob = coroutineScope.launch(Dispatchers.Main) {
            delay(delayMs)
            if (generation != playbackGeneration) return@launch
            Log.i(TAG, "Automatic playback retry $attempt/$MAX_AUTOMATIC_RETRIES")
            val resumePlayback = exoPlayer.playWhenReady
            val positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            try {
                // The stream address is resolved again. A runtime error often means
                // that the previous address expired, and reusing it would fail again.
                val streamUrl = resolveStreamUrlForRetry(request, positionMs)
                if (streamUrl == null) {
                    _errorMessage.value = "The stream address could not be refreshed"
                    Log.w(TAG, "Automatic retry failed because the stream address could not be refreshed")
                    return@launch
                }
                startPlayback(
                    streamUrl = streamUrl,
                    startPositionMs = positionMs,
                    title = request.title,
                    headers = request.headers,
                    resetPlaybackSpeed = false,
                    resumePlayback = resumePlayback,
                )
            } catch (error: Exception) {
                _errorMessage.value = error.message?.takeIf(String::isNotBlank) ?: "Could not prepare this video"
                Log.w(TAG, "Automatic retry preparation failed (${error.javaClass.simpleName})")
            }
        }
        return true
    }

    /**
     * Asks the owner of the playback for a fresh stream address.
     *
     * Without a provider the previous address is used again, because some streams
     * stay valid and the retry only has to restart the transfer.
     */
    private suspend fun resolveStreamUrlForRetry(request: PlaybackRequest, positionMs: Long): String? =
        resolveRetryStreamUrl(streamUrlProvider, request.itemId, positionMs, request.streamUrl)

    private fun isTransient(error: PlaybackException): Boolean {
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        ) return true
        if (error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return false
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode == 408 || cause.responseCode == 429 || cause.responseCode in 500..599
            }
            cause = cause.cause
        }
        return false
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun toggleMute() {
        val muted = !_isMuted.value
        _isMuted.value = muted
        exoPlayer.volume = if (muted) 0f else 1f
    }

    fun seekTo(positionMs: Long) {
        val duration = _durationMs.value
        val targetPos = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        exoPlayer.seekTo(targetPos)
        _currentPositionMs.value = targetPos
    }

    fun seekBy(deltaMs: Long) {
        val current = exoPlayer.currentPosition
        val duration = exoPlayer.duration
        val target = if (duration > 0L) {
            (current + deltaMs).coerceIn(0L, duration)
        } else {
            (current + deltaMs).coerceAtLeast(0L)
        }
        exoPlayer.seekTo(target)
        _currentPositionMs.value = target
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    private fun startPositionPolling() {
        if (positionPollJob?.isActive == true) return
        positionPollJob = coroutineScope.launch(Dispatchers.Main) {
            try {
                while (isActive) {
                    val pos = exoPlayer.currentPosition
                    _currentPositionMs.value = pos
                    val dur = exoPlayer.duration
                    if (dur > 0L && _durationMs.value != dur) {
                        _durationMs.value = dur
                    }
                    retryBudget.track(
                        SystemClock.elapsedRealtime(),
                        _playbackState.value == Player.STATE_READY && _isPlaying.value,
                    )
                    delay(POSITION_POLL_INTERVAL_MS)
                }
            } catch (e: CancellationException) {
                // Expected when polling cancelled
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = null
    }

    fun release() {
        if (released) return
        released = true
        // The view is detached before the player is released, so the view never
        // holds a released player.
        runCatching { onBeforeRelease?.invoke() }
        onBeforeRelease = null
        playbackGeneration += 1
        retryJob?.cancel()
        retryJob = null
        playbackRequest = null
        _retryStatus.value = null
        stopPositionPolling()
        exoPlayer.removeListener(playerListener)
        exoPlayer.removeAnalyticsListener(analyticsListener)
        try {
            mediaSession?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaSession (${e.javaClass.simpleName})")
        }
        mediaSession = null
        exoPlayer.release()
    }

    fun getTechnicalInfo(
        playbackInfo: com.adulthub.tv.data.model.PlaybackInfo,
        resolverLatencyMs: Long? = null,
    ): PlaybackTechnicalInfo {
        val videoFormat = exoPlayer.videoFormat
        val audioFormat = exoPlayer.audioFormat

        val isVideoSw = videoDecoderName?.let {
            val n = it.lowercase()
            n.startsWith("c2.android.") || n.startsWith("omx.google.") || n.contains("ffmpeg") || n.contains("sw")
        } ?: false

        val isAudioSw = audioDecoderName?.let {
            val n = it.lowercase()
            n.startsWith("c2.android.") || n.startsWith("omx.google.") || n.contains("ffmpeg") || n.contains("sw")
        } ?: false

        val decoderMode = when {
            videoDecoderName == null && audioDecoderName == null -> "Hardware (HW)"
            isVideoSw || isAudioSw -> {
                val parts = listOfNotNull(
                    if (isVideoSw) "V:SW" else null,
                    if (isAudioSw) "A:SW" else null,
                ).joinToString(", ")
                "Fallback ($parts)"
            }
            else -> "Hardware (HW)"
        }

        val isTelegramProxy = playbackInfo.streamUrl.contains("/stream?mode=proxy", ignoreCase = true)
        val isTranscode = playbackInfo.streamUrl.contains("m3u8", ignoreCase = true) ||
                playbackInfo.streamUrl.contains("transcoding", ignoreCase = true)

        val playMethod = when {
            isTelegramProxy -> "Direct Stream (Telegram Proxy)"
            isTranscode -> "Transcode"
            else -> "Direct Play"
        }

        val res = if (videoFormat != null && videoFormat.width > 0 && videoFormat.height > 0) {
            "${videoFormat.width}x${videoFormat.height}"
        } else "Unknown"

        val fps = if (videoFormat != null && videoFormat.frameRate > 0) {
            String.format(java.util.Locale.US, "%.1f fps", videoFormat.frameRate)
        } else "Unknown"

        val vCodec = when {
            videoFormat?.sampleMimeType?.contains("avc", ignoreCase = true) == true ||
            videoFormat?.codecs?.contains("avc", ignoreCase = true) == true -> "H.264 (AVC)"
            videoFormat?.sampleMimeType?.contains("hevc", ignoreCase = true) == true ||
            videoFormat?.codecs?.contains("hevc", ignoreCase = true) == true -> "H.265 (HEVC)"
            videoFormat?.sampleMimeType?.contains("vp9", ignoreCase = true) == true -> "VP9"
            videoFormat?.sampleMimeType?.contains("av01", ignoreCase = true) == true -> "AV1"
            !videoFormat?.sampleMimeType.isNullOrBlank() -> videoFormat.sampleMimeType.orEmpty()
            else -> "Unknown"
        }

        val fileSizeForBitrate = streamFileSizeBytes ?: playbackInfo.fileSizeBytes
        val durationMs = exoPlayer.duration.takeIf { it > 0L } ?: playbackInfo.durationMs ?: 0L
        val estimatedBitrate = fileSizeForBitrate?.takeIf { it > 0L && durationMs > 0L }
            ?.let { (it * 8_000L / durationMs).coerceAtLeast(1L).toInt() }
        val bitrate = videoFormat?.bitrate?.takeIf { it > 0 } ?: estimatedBitrate
        val vBitrate = bitrate?.let {
            val text = if (it >= 1_000_000) {
                String.format(java.util.Locale.US, "%.1f Mbps", it / 1_000_000.0)
            } else "${it / 1000} kbps"
            if (videoFormat?.bitrate?.let { value -> value > 0 } == true) text else "~$text"
        } ?: "Unknown"

        val aCodec = when {
            audioFormat?.sampleMimeType?.contains("mp4a", ignoreCase = true) == true ||
            audioFormat?.sampleMimeType?.contains("aac", ignoreCase = true) == true -> "AAC"
            audioFormat?.sampleMimeType?.contains("ac3", ignoreCase = true) == true -> "AC-3"
            audioFormat?.sampleMimeType?.contains("eac3", ignoreCase = true) == true -> "E-AC-3"
            audioFormat?.sampleMimeType?.contains("opus", ignoreCase = true) == true -> "Opus"
            !audioFormat?.sampleMimeType.isNullOrBlank() -> audioFormat.sampleMimeType.orEmpty()
            else -> "Unknown"
        }

        val channels = when (audioFormat?.channelCount) {
            1 -> "Mono (1.0)"
            2 -> "Stereo (2.0)"
            6 -> "5.1 Surround"
            8 -> "7.1 Surround"
            else -> if (audioFormat?.channelCount != null && audioFormat.channelCount > 0) "${audioFormat.channelCount} ch" else "Unknown"
        }

        val sampleRate = if (audioFormat != null && audioFormat.sampleRate > 0) {
            "${audioFormat.sampleRate} Hz"
        } else "Unknown"

        val path = runCatching { Uri.parse(playbackInfo.streamUrl).path.orEmpty() }.getOrDefault("")
        val container = listOfNotNull(playbackInfo.container, streamContentType, path.substringAfterLast('.', ""))
            .firstNotNullOfOrNull(::containerLabel) ?: "Unknown"

        val bufferHealth = (exoPlayer.bufferedPosition - exoPlayer.currentPosition).coerceAtLeast(0L)

        return PlaybackTechnicalInfo(
            playMethod = playMethod,
            resolution = res,
            frameRate = fps,
            videoCodec = vCodec,
            videoBitrate = vBitrate,
            audioCodec = aCodec,
            audioChannels = channels,
            audioSampleRate = sampleRate,
            container = container,
            bufferHealthMs = bufferHealth,
            streamUrl = playbackInfo.streamUrl,
            fileSize = formatFileSize(
                streamFileSizeBytes ?: playbackInfo.fileSizeBytes?.takeIf { it >= MIN_TRUSTWORTHY_FILE_SIZE_BYTES }
            ),
            retryCount = totalRetryCount,
            resolverLatencyMs = resolverLatencyMs,
            decoderMode = decoderMode,
            droppedFrames = totalDroppedFrames,
            firstFrameMs = firstFrameMs,
            rebufferCount = rebufferCount,
            rebufferDurationMs = totalRebufferDurationMs +
                (bufferingStartedAtMs?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) } ?: 0L),
            lastSeekBufferDurationMs = lastSeekBufferDurationMs,
        )
    }

    private fun finishBufferingMeasurement() {
        val now = SystemClock.elapsedRealtime()
        bufferingStartedAtMs?.let { startedAt ->
            val durationMs = (now - startedAt).coerceAtLeast(0L)
            totalRebufferDurationMs += durationMs
            Log.i(TAG, "rebuffer_event_ms=$durationMs total_rebuffers=$rebufferCount")
            bufferingStartedAtMs = null
        }
        seekBufferingStartedAtMs?.let { startedAt ->
            lastSeekBufferDurationMs = (now - startedAt).coerceAtLeast(0L)
            Log.i(TAG, "seek_buffer_ms=$lastSeekBufferDurationMs")
            seekBufferingStartedAtMs = null
        }
    }
}

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return "Unknown"
    if (bytes < MIN_TRUSTWORTHY_FILE_SIZE_BYTES) return "Unknown"

    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1024.0) {
        String.format(java.util.Locale.US, "%.1f GB", megabytes / 1024.0)
    } else {
        String.format(java.util.Locale.US, "%.1f MB", megabytes)
    }
}

private const val MIN_TRUSTWORTHY_FILE_SIZE_BYTES = 1024L * 1024L
private const val MEDIA_SESSION_ID = "AdultHubTvSession"

private class FileSizeTrackingDataSource(
    private val upstream: DataSource,
    private val onFileSize: (Long) -> Unit,
    private val onContentType: (String) -> Unit,
) : DataSource {
    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val length = upstream.open(dataSpec)
        val headers = upstream.responseHeaders
        headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value?.firstOrNull()?.let(onContentType)
        val contentRange = headers.entries.firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value?.firstOrNull()
        val totalFromRange = contentRange
            ?.substringAfterLast('/', "")
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
        val totalFromFullResponse = length
            .takeIf { dataSpec.position == 0L && dataSpec.length == C.LENGTH_UNSET.toLong() && it > 0L }

        (totalFromRange ?: totalFromFullResponse)?.let { totalSize ->
            onFileSize(totalSize)
            Log.i(TAG, "Detected stream size=$totalSize bytes")
        }
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        upstream.read(buffer, offset, length)

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()
}

private fun containerLabel(value: String): String? = when (value.substringBefore(';').trim().lowercase()) {
    "mkv", "matroska", "video/x-matroska" -> "MKV"
    "mp4", "m4v", "video/mp4", "application/mp4" -> "MP4"
    "webm", "video/webm" -> "WebM"
    "mov", "video/quicktime" -> "MOV"
    "m3u8", "hls", "application/vnd.apple.mpegurl", "application/x-mpegurl" -> "HLS"
    "avi", "video/x-msvideo" -> "AVI"
    "mpeg", "mpg", "video/mpeg" -> "MPEG"
    else -> null
}

data class PlaybackTechnicalInfo(
    val playMethod: String,
    val resolution: String,
    val frameRate: String,
    val videoCodec: String,
    val videoBitrate: String,
    val audioCodec: String,
    val audioChannels: String,
    val audioSampleRate: String,
    val container: String,
    val bufferHealthMs: Long,
    val streamUrl: String,
    val fileSize: String,
    val retryCount: Int = 0,
    val resolverLatencyMs: Long? = null,
    val decoderMode: String = "Hardware (HW)",
    val droppedFrames: Int = 0,
    val firstFrameMs: Long? = null,
    val rebufferCount: Int = 0,
    val rebufferDurationMs: Long = 0L,
    val lastSeekBufferDurationMs: Long? = null,
)

private const val SEEK_BUFFER_CORRELATION_WINDOW_MS = 3_000L
