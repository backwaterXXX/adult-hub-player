package com.adulthub.tv.ui.player

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.adulthub.tv.player.PlayerManager
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.OledBlack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.adulthub.tv.diagnostics.AppLog as Log
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    itemId: String,
    resume: Boolean? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? Activity
    val lifecycleOwner = context as? LifecycleOwner

    // Keep screen on during playback
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaybackEnded by viewModel.isPlaybackEnded.collectAsStateWithLifecycle()
    val isLoadingRandom by viewModel.isLoadingRandom.collectAsStateWithLifecycle()

    val settingsDataStore: com.adulthub.tv.data.local.SettingsDataStore = koinInject()
    val bufferProfile by settingsDataStore.bufferProfile.collectAsStateWithLifecycle(initialValue = com.adulthub.tv.data.local.BufferProfile.STANDARD)
    val seekStepSec by settingsDataStore.seekStepSec.collectAsStateWithLifecycle(initialValue = 15)
    val autoplayNext by settingsDataStore.autoplayNext.collectAsStateWithLifecycle(initialValue = false)
    val playerManager = remember(bufferProfile) { PlayerManager(context, coroutineScope, bufferProfile) }
    val phashCoordinator: com.adulthub.tv.player.PhashCoordinator = koinInject()
    val phashJobStatuses by phashCoordinator.jobStatuses.collectAsStateWithLifecycle()

    val isPlaying by playerManager.isPlaying.collectAsStateWithLifecycle()
    val isMuted by playerManager.isMuted.collectAsStateWithLifecycle()
    val playbackState by playerManager.playbackState.collectAsStateWithLifecycle()
    val currentPositionMs by playerManager.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by playerManager.durationMs.collectAsStateWithLifecycle()
    val playerError by playerManager.errorMessage.collectAsStateWithLifecycle()
    val retryStatus by playerManager.retryStatus.collectAsStateWithLifecycle()

    DisposableEffect(activity, playerManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                playerManager.pause()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    var isOsdVisible by remember { mutableStateOf(true) }
    var osdTimerTrigger by remember { mutableLongStateOf(0L) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var autoplayCountdownSec by remember { mutableStateOf<Int?>(null) }
    val playPauseFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

    // Initial load
    LaunchedEffect(itemId, resume) {
        viewModel.loadItem(itemId, resume)
    }

    // Connect PlayerManager callbacks to ViewModel and PlaybackReporter
    LaunchedEffect(playerManager) {
        playerManager.onPlaybackReady = { posMs ->
            viewModel.playbackReporter.reportStart(
                positionMs = posMs,
                canSeek = playerManager.exoPlayer.isCurrentMediaItemSeekable,
            )
        }
        playerManager.onPlaybackEnded = {
            viewModel.onPlaybackEnded(playerManager.currentPositionMs.value)
        }
    }

    val readyState = uiState as? PlayerUiState.Ready

    // Recompose the UI for metadata/favorite changes without restarting this playback request.
    LaunchedEffect(readyState?.playbackRequestId, playerManager) {
        val state = readyState ?: return@LaunchedEffect
        val info = state.playbackInfo
        // Start reporting session
        viewModel.playbackReporter.startSession(
            itemId = info.itemId,
            mediaSourceId = info.mediaSourceId,
            playSessionId = info.playSessionId,
            playMethod = info.playMethod,
            getCurrentPositionMs = { playerManager.currentPositionMs.value },
            getIsPaused = { !playerManager.isPlaying.value },
        )

        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        playbackSpeed = 1.0f
        playerManager.setPlaybackSpeed(1.0f)

        // Prepare ExoPlayer
        playerManager.prepareAndPlay(
            streamUrl = info.streamUrl,
            startPositionMs = state.startPositionMs,
            title = info.title,
            headers = info.headers,
        )

        // If waiting for user to confirm resume from ResumePlaybackDialog, pause immediately
        if (state.pendingResumePositionMs != null && state.pendingResumePositionMs > 0L) {
            playerManager.pause()
        }

        isOsdVisible = true
        osdTimerTrigger = System.currentTimeMillis()
    }

    LaunchedEffect(readyState?.playbackRequestId, playerManager) {
        if (readyState == null) return@LaunchedEffect
        while (true) {
            delay(60_000L)
            if (playerManager.isPlaying.value) {
                viewModel.checkpointTelegramProgress(playerManager.currentPositionMs.value)
            }
        }
    }

    // Auto-play next countdown when scene ends
    LaunchedEffect(isPlaybackEnded, autoplayNext) {
        if (isPlaybackEnded && autoplayNext) {
            autoplayCountdownSec = 10
            while ((autoplayCountdownSec ?: 0) > 0) {
                delay(1000L)
                val current = autoplayCountdownSec ?: break
                if (current <= 1) {
                    autoplayCountdownSec = null
                    viewModel.playRandomMovie(currentPositionMs = 0L)
                    break
                } else {
                    autoplayCountdownSec = current - 1
                }
            }
        } else {
            autoplayCountdownSec = null
        }
    }

    LaunchedEffect(readyState?.playbackRequestId, playerManager) {
        val state = readyState ?: return@LaunchedEffect
        delay(8_000L)
        // Buffering can last longer than the initial delay. Wait for actual playback
        // instead of silently skipping pHash for this playback request.
        playerManager.isPlaying.first { it }
        try {
            phashCoordinator.enqueueGeneration(state.item, playerManager.durationMs.value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.i("PlaybackPhash", "optional sampling unavailable (${error.javaClass.simpleName})")
        }
    }

    // Safely transfer focus when OSD visibility changes
    LaunchedEffect(isOsdVisible) {
        if (isOsdVisible) {
            delay(100L)
            runCatching {
                playPauseFocusRequester.requestFocus()
            }
        } else {
            runCatching {
                rootFocusRequester.requestFocus()
            }
        }
    }

    // Auto-hide OSD after 5 seconds if playing and no dialog is open
    LaunchedEffect(isOsdVisible, isPlaying, osdTimerTrigger, isPlaybackEnded, playerError, showInfoDialog) {
        if (isOsdVisible && isPlaying && !isPlaybackEnded && playerError == null && !showInfoDialog) {
            delay(5_000L)
            isOsdVisible = false
        }
    }

    // Clear seek feedback after 1.5s
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(1500L)
            seekFeedback = null
        }
    }

    // Handle Back button
    BackHandler {
        if (autoplayCountdownSec != null) {
            autoplayCountdownSec = null
        } else if (showInfoDialog) {
            showInfoDialog = false
        } else if (isOsdVisible) {
            isOsdVisible = false
        } else {
            viewModel.onExitPlayer(playerManager.currentPositionMs.value)
            playerManager.release()
            onBack()
        }
    }

    // Cleanup when leaving screen
    DisposableEffect(playerManager) {
        onDispose {
            viewModel.onExitPlayer(playerManager.currentPositionMs.value)
            playerManager.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (!isOsdVisible && !isPlaybackEnded && playerError == null && !showInfoDialog) {
                                isOsdVisible = true
                                osdTimerTrigger = System.currentTimeMillis()
                                true
                            } else false
                        }
                        Key.DirectionLeft -> {
                            if (!isOsdVisible && !isPlaybackEnded && playerError == null && !showInfoDialog) {
                                val delta = -seekStepSec * 1_000L
                                playerManager.seekBy(delta)
                                seekFeedback = "-${seekStepSec}s"
                                viewModel.playbackReporter.reportProgressNow(
                                    playerManager.currentPositionMs.value,
                                    !playerManager.isPlaying.value,
                                )
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (!isOsdVisible && !isPlaybackEnded && playerError == null && !showInfoDialog) {
                                val delta = seekStepSec * 1_000L
                                playerManager.seekBy(delta)
                                seekFeedback = "+${seekStepSec}s"
                                viewModel.playbackReporter.reportProgressNow(
                                    playerManager.currentPositionMs.value,
                                    !playerManager.isPlaying.value,
                                )
                                true
                            } else false
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (!isOsdVisible && !isPlaybackEnded && playerError == null && !showInfoDialog) {
                                isOsdVisible = true
                                osdTimerTrigger = System.currentTimeMillis()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        if (retryStatus != null) {
            Text(
                text = retryStatus.orEmpty(),
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }

        when (val state = uiState) {
            is PlayerUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    com.adulthub.tv.ui.components.LoadingIndicator(
                        message = "Loading stream...",
                    )
                }
            }
            is PlayerUiState.Error -> {
                PlaybackErrorDialog(
                    errorMessage = state.message,
                    onRetry = { viewModel.loadItem(itemId, resume) },
                    onBack = {
                        viewModel.onExitPlayer(0L)
                        playerManager.release()
                        onBack()
                    },
                )
            }
            is PlayerUiState.Ready -> {
                // Video Surface
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.resizeMode = resizeMode
                            player = playerManager.exoPlayer
                        }
                    },
                    update = { view ->
                        view.player = playerManager.exoPlayer
                        view.resizeMode = resizeMode
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Custom TV OSD Overlay
                PlayerOverlay(
                    title = state.item.title,
                    isPlaying = isPlaying,
                    isBuffering = playbackState == Player.STATE_BUFFERING || isLoadingRandom,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    isOsdVisible = isOsdVisible,
                    seekFeedback = seekFeedback,
                    playPauseFocusRequester = playPauseFocusRequester,
                    onShowOsd = {
                        isOsdVisible = true
                        osdTimerTrigger = System.currentTimeMillis()
                    },
                    onResetOsdTimer = {
                        osdTimerTrigger = System.currentTimeMillis()
                    },
                    onPlayPauseClick = {
                        playerManager.togglePlayPause()
                        viewModel.playbackReporter.reportProgressNow(
                            playerManager.currentPositionMs.value,
                            !playerManager.isPlaying.value,
                        )
                    },
                    isMuted = isMuted,
                    onMuteClick = { playerManager.toggleMute() },
                    onSeekRelative = { deltaMs ->
                        playerManager.seekBy(deltaMs)
                        seekFeedback = if (deltaMs > 0) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s"
                        viewModel.playbackReporter.reportProgressNow(
                            playerManager.currentPositionMs.value,
                            !playerManager.isPlaying.value,
                        )
                    },
                    onRandomClick = {
                        viewModel.playRandomMovie(currentPositionMs = playerManager.currentPositionMs.value)
                    },
                    onInfoClick = {
                        showInfoDialog = !showInfoDialog
                    },
                    onBackClick = {
                        viewModel.onExitPlayer(playerManager.currentPositionMs.value)
                        playerManager.release()
                        onBack()
                    },
                    isFavorite = state.item.isFavorite,
                    onToggleFavorite = {
                        viewModel.toggleFavorite()
                    },
                    seekStepSec = seekStepSec,
                    playbackSpeed = playbackSpeed,
                    onPlaybackSpeedChange = { newSpeed ->
                        playbackSpeed = newSpeed
                        playerManager.setPlaybackSpeed(newSpeed)
                    },
                    resizeMode = resizeMode,
                    onResizeModeChange = { newMode ->
                        resizeMode = newMode
                    },
                )

                val phashVideoId = com.adulthub.tv.player.adultHubVideoId(state.item)
                phashVideoId?.let { phashJobStatuses[it] }?.let { phashStatus ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(20.dp)
                            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = phashStatus.message,
                            color = if (phashStatus.terminal && phashStatus.message.contains("failed", true)) Color(0xFFFFB4AB) else NeonPink,
                            fontSize = 12.sp,
                            maxLines = 2,
                        )
                    }
                }

                // Technical Playback Info HUD Overlay
                if (showInfoDialog) {
                    PlaybackInfoOverlay(
                        technicalInfo = playerManager.getTechnicalInfo(
                            playbackInfo = state.playbackInfo,
                            resolverLatencyMs = state.resolverLatencyMs,
                        ),
                    )
                }

                // End of Movie Dialog
                if (isPlaybackEnded) {
                    EndOfMovieDialog(
                        onNextRandom = {
                            autoplayCountdownSec = null
                            viewModel.playRandomMovie(currentPositionMs = 0L)
                        },
                        onBackToDetails = {
                            autoplayCountdownSec = null
                            viewModel.onExitPlayer(playerManager.currentPositionMs.value)
                            playerManager.release()
                            onBack()
                        },
                        countdownSeconds = autoplayCountdownSec,
                        onCancelCountdown = {
                            autoplayCountdownSec = null
                        },
                    )
                }

                // Resume Playback Dialog (Local Telegram or Jellyfin direct play)
                val pendingResume = state.pendingResumePositionMs
                if (pendingResume != null && pendingResume > 0L) {
                    ResumePlaybackDialog(
                        resumePositionMs = pendingResume,
                        onResume = {
                            viewModel.onResumeDialogHandled()
                            playerManager.seekTo(pendingResume)
                            playerManager.play()
                        },
                        onStartFromBeginning = {
                            viewModel.onResumeDialogHandled()
                            playerManager.seekTo(0L)
                            playerManager.play()
                        },
                    )
                }

                // Runtime Playback Exception Dialog
                playerError?.let { err ->
                    PlaybackErrorDialog(
                        errorMessage = err,
                        onRetry = {
                            playerManager.prepareAndPlay(
                                streamUrl = state.playbackInfo.streamUrl,
                                startPositionMs = currentPositionMs,
                                title = state.playbackInfo.title,
                                headers = state.playbackInfo.headers,
                            )
                        },
                        onBack = {
                            viewModel.onExitPlayer(playerManager.currentPositionMs.value)
                            playerManager.release()
                            onBack()
                        },
                    )
                }
            }
        }

    }
}
