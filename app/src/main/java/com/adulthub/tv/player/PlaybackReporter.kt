package com.adulthub.tv.player

import com.adulthub.tv.diagnostics.AppLog as Log
import com.adulthub.tv.data.repository.PlaybackRepository
import org.jellyfin.sdk.model.api.PlayMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "PlaybackReporter"
private const val REPORTING_INTERVAL_MS = 10_000L
private const val PROGRESS_COALESCE_MS = 500L

class PlaybackReporter(
    private val playbackRepository: PlaybackRepository,
    private val coroutineScope: CoroutineScope,
) {
    private var reportingJob: Job? = null
    private val sessionCounter = AtomicLong(0)
    private val commands = Channel<ReportCommand>(Channel.UNLIMITED)
    private val pendingProgressLock = Any()
    private var pendingProgress: ReportCommand.Progress? = null
    private var pendingProgressJob: Job? = null

    init {
        coroutineScope.launch {
            for (command in commands) {
                try {
                    process(command)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Playback report command failed (${e.javaClass.simpleName})")
                }
            }
        }
    }

    @Volatile
    private var currentSession: SessionInfo? = null

    data class SessionInfo(
        val sessionId: Long,
        val itemId: String,
        val mediaSourceId: String?,
        val playSessionId: String?,
        val playMethod: PlayMethod,
        @Volatile
        var canSeek: Boolean = false,
        var hasStarted: Boolean = false,
    )

    fun startSession(
        itemId: String,
        mediaSourceId: String?,
        playSessionId: String?,
        playMethod: PlayMethod,
        getCurrentPositionMs: () -> Long,
        getIsPaused: () -> Boolean,
    ) {
        // Stop any existing session first
        currentSession?.let { existingSession ->
            val pos = getCurrentPositionMs()
            stopSession(pos)
        }

        val sessionId = sessionCounter.incrementAndGet()
        val newSession = SessionInfo(
            sessionId = sessionId,
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            playSessionId = playSessionId,
            playMethod = playMethod,
        )
        currentSession = newSession
        Log.d(TAG, "Starting playback reporting session")

        reportingJob = coroutineScope.launch {
            try {
                while (isActive) {
                    delay(REPORTING_INTERVAL_MS)
                    val activeSession = currentSession
                    if (activeSession != null && activeSession.sessionId == sessionId) {
                        val positionMs = getCurrentPositionMs()
                        val isPaused = getIsPaused()
                        enqueue(ReportCommand.Progress(activeSession, positionMs, isPaused))
                    } else {
                        break
                    }
                }
            } catch (e: CancellationException) {
                // Normal job cancellation
            } catch (e: Exception) {
                Log.w(TAG, "Periodic playback reporting failed (${e.javaClass.simpleName})")
            }
        }
    }

    fun reportStart(positionMs: Long, canSeek: Boolean) {
        val session = currentSession ?: return
        if (session.hasStarted) return
        session.canSeek = canSeek
        session.hasStarted = true

        enqueue(ReportCommand.Start(session, positionMs))
    }

    fun reportProgressNow(positionMs: Long, isPaused: Boolean) {
        val session = currentSession ?: return
        synchronized(pendingProgressLock) {
            pendingProgress = ReportCommand.Progress(session, positionMs, isPaused)
            if (pendingProgressJob?.isActive == true) return
            pendingProgressJob = coroutineScope.launch {
                delay(PROGRESS_COALESCE_MS)
                flushPendingProgress()
            }
        }
    }

    fun stopSession(finalPositionMs: Long) {
        reportingJob?.cancel()
        reportingJob = null

        val lastPendingProgress = synchronized(pendingProgressLock) {
            pendingProgressJob?.cancel()
            pendingProgressJob = null
            pendingProgress.also { pendingProgress = null }
        }
        lastPendingProgress?.let(::enqueue)

        val session = currentSession ?: return
        currentSession = null
        enqueue(ReportCommand.Stop(session, finalPositionMs))
    }

    private fun flushPendingProgress() {
        val command = synchronized(pendingProgressLock) {
            pendingProgressJob = null
            pendingProgress.also { pendingProgress = null }
        }
        command?.let(::enqueue)
    }

    private fun enqueue(command: ReportCommand) {
        val result = commands.trySend(command)
        if (result.isFailure) {
            Log.w(TAG, "Playback report queue unavailable")
        }
    }

    private suspend fun process(command: ReportCommand) {
        val session = command.session
        if (command !is ReportCommand.Stop && currentSession?.sessionId != session.sessionId) return

        val positionMs = command.positionMs
        val ticks = positionMs.coerceAtLeast(0L) * 10_000L
        when (command) {
            is ReportCommand.Start -> {
                Log.d(TAG, "Reporting playback start at ${positionMs}ms")
                playbackRepository.reportPlaybackStart(
                    itemId = session.itemId,
                    mediaSourceId = session.mediaSourceId,
                    positionTicks = ticks,
                    playSessionId = session.playSessionId,
                    canSeek = session.canSeek,
                    playMethod = session.playMethod,
                ).onFailure { error -> Log.w(TAG, "Playback start report failed (${error.javaClass.simpleName})") }
            }
            is ReportCommand.Progress -> {
                playbackRepository.reportPlaybackProgress(
                    itemId = session.itemId,
                    mediaSourceId = session.mediaSourceId,
                    positionTicks = ticks,
                    playSessionId = session.playSessionId,
                    isPaused = command.isPaused,
                    canSeek = session.canSeek,
                    playMethod = session.playMethod,
                ).onFailure { error -> Log.w(TAG, "Playback progress report failed (${error.javaClass.simpleName})") }
            }
            is ReportCommand.Stop -> {
                withContext(NonCancellable) {
                    withTimeoutOrNull(3_000L) {
                        Log.d(TAG, "Reporting playback stopped at ${positionMs}ms")
                        val result = playbackRepository.reportPlaybackStopped(
                            itemId = session.itemId,
                            mediaSourceId = session.mediaSourceId,
                            positionTicks = ticks,
                            playSessionId = session.playSessionId,
                        )
                        result.onFailure { error -> Log.w(TAG, "Playback stop report failed (${error.javaClass.simpleName})") }
                        if (result.isSuccess) Log.d(TAG, "Playback stop report accepted by server")
                    }
                }
            }
        }
    }

    private sealed interface ReportCommand {
        val session: SessionInfo
        val positionMs: Long

        data class Start(override val session: SessionInfo, override val positionMs: Long) : ReportCommand
        data class Progress(
            override val session: SessionInfo,
            override val positionMs: Long,
            val isPaused: Boolean,
        ) : ReportCommand
        data class Stop(override val session: SessionInfo, override val positionMs: Long) : ReportCommand
    }
}
