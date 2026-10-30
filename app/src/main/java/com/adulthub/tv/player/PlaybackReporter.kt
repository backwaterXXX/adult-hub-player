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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "PlaybackReporter"
private const val REPORTING_INTERVAL_MS = 10_000L
private const val PROGRESS_COALESCE_MS = 500L

// Periods add one progress command at a time; the cap only has to absorb a slow server.
private const val MAX_QUEUED_PROGRESS = 8

class PlaybackReporter(
    private val playbackRepository: PlaybackRepository,
    private val coroutineScope: CoroutineScope,
) {
    private var reportingJob: Job? = null
    private val sessionCounter = AtomicLong(0)
    private val commandLock = Any()
    private val queuedCommands = ArrayDeque<ReportCommand>()
    private val commandSignal = Channel<Unit>(Channel.CONFLATED)
    private val pendingProgressLock = Any()
    private var pendingProgress: ReportCommand.Progress? = null
    private var pendingProgressJob: Job? = null

    init {
        coroutineScope.launch {
            for (unit in commandSignal) {
                drainCommands()
            }
        }
    }

    private suspend fun drainCommands() {
        while (true) {
            val command = synchronized(commandLock) { queuedCommands.removeFirstOrNull() } ?: return
            try {
                process(command)
            } catch (e: CancellationException) {
                coroutineScope.ensureActive()
                Log.w(TAG, "Playback report command was cancelled (${e.message})")
            } catch (e: Exception) {
                Log.w(TAG, "Playback report command failed (${e.javaClass.simpleName})")
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
        val getCurrentPositionMs: () -> Long,
        val getIsPaused: () -> Boolean,
        @Volatile
        var canSeek: Boolean = false,
        @Volatile
        var hasStarted: Boolean = false,
        @Volatile
        var stopped: Boolean = false,
    )

    @Synchronized
    fun startSession(
        itemId: String,
        mediaSourceId: String?,
        playSessionId: String?,
        playMethod: PlayMethod,
        getCurrentPositionMs: () -> Long,
        getIsPaused: () -> Boolean,
    ): Long {
        // Stop any existing session first
        currentSession?.let { existingSession ->
            stopSession(existingSession.getCurrentPositionMs(), existingSession.sessionId)
        }

        val sessionId = sessionCounter.incrementAndGet()
        val newSession = SessionInfo(
            sessionId = sessionId,
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            playSessionId = playSessionId,
            playMethod = playMethod,
            getCurrentPositionMs = getCurrentPositionMs,
            getIsPaused = getIsPaused,
        )
        currentSession = newSession
        Log.d(TAG, "Starting playback reporting session")

        reportingJob = coroutineScope.launch {
            try {
                while (isActive) {
                    delay(REPORTING_INTERVAL_MS)
                    val activeSession = currentSession
                    if (activeSession != null && activeSession.sessionId == sessionId) {
                        if (activeSession.hasStarted) {
                            val positionMs = activeSession.getCurrentPositionMs()
                            reportProgressNow(positionMs, activeSession.getIsPaused(), activeSession.sessionId)
                        }
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
        return sessionId
    }

    @Synchronized
    fun reportStart(positionMs: Long, canSeek: Boolean, sessionId: Long? = null) {
        val session = currentSession?.takeIf { sessionId == null || it.sessionId == sessionId } ?: return
        if (session.hasStarted) return
        session.canSeek = canSeek
        session.hasStarted = true

        enqueue(ReportCommand.Start(session, positionMs))
    }

    @Synchronized
    fun reportProgressNow(positionMs: Long, isPaused: Boolean, sessionId: Long? = null) {
        val session = currentSession?.takeIf { (sessionId == null || it.sessionId == sessionId) && it.hasStarted } ?: return
        synchronized(pendingProgressLock) {
            pendingProgress = ReportCommand.Progress(session, positionMs, isPaused)
            if (pendingProgressJob?.isActive == true) return
            pendingProgressJob = coroutineScope.launch {
                delay(PROGRESS_COALESCE_MS)
                flushPendingProgress()
            }
        }
    }

    /**
     * Stops a reporting session.
     *
     * A known session id stops only that session, so a caller that knows its own
     * session can never stop the session of another player. A caller without a
     * session id stops the session that is running, because such a caller owns the
     * reporter and has to end its own reporting.
     */
    @Synchronized
    fun stopSession(finalPositionMs: Long, sessionId: Long? = null) {
        val session = currentSession ?: return
        if (sessionId != null && session.sessionId != sessionId) return
        currentSession = null
        reportingJob?.cancel()
        reportingJob = null

        synchronized(pendingProgressLock) {
            pendingProgressJob?.cancel()
            pendingProgressJob = null
            pendingProgress = null
        }

        session.stopped = true
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
        synchronized(commandLock) {
            if (command is ReportCommand.Progress) {
                // A newer position replaces a queued progress report of the same session.
                // Start and Stop keep their place, so the server still sees the session
                // begin and end in order.
                val last = queuedCommands.lastOrNull()
                if (last is ReportCommand.Progress && last.session === command.session) {
                    queuedCommands.removeLast()
                } else if (queuedCommands.count { it is ReportCommand.Progress } >= MAX_QUEUED_PROGRESS) {
                    val oldestProgress = queuedCommands.indexOfFirst { it is ReportCommand.Progress }
                    if (oldestProgress >= 0) queuedCommands.removeAt(oldestProgress)
                }
            }
            queuedCommands.addLast(command)
        }
        if (commandSignal.trySend(Unit).isFailure) {
            Log.w(TAG, "Playback report queue unavailable")
        }
    }

    private suspend fun process(command: ReportCommand) {
        val session = command.session
        if (command is ReportCommand.Progress && session.stopped) return

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
