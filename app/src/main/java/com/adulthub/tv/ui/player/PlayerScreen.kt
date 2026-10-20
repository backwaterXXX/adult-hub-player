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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
    mobileControls: Boolean = false,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? Activity
    val lifecycleOwner = context as? LifecycleOwner

    // Keep screen on during playback
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        PlayerScreenState.isPlayerScreenVisible = true
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            PlayerScreenState.isPlayerScreenVisible = false
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPlaybackEnded by viewModel.isPlaybackEnded.collectAsStateWithLifecycle()
    val isLoadingRandom by viewModel.isLoadingRandom.collectAsStateWithLifecycle()

    val settingsDataStore: com.adulthub.tv.data.local.SettingsDataStore = koinInject()
    val playerManagerState by produceState<PlayerManager?>(null, context, settingsDataStore) {
        val bufferProfile = settingsDataStore.bufferProfile.first()
        value = PlayerManager(context, coroutineScope, bufferProfile)
    }
    if (playerManagerState == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Preparing player…", color = Color.White)
        }
        return
    }
    val playerManager = requireNotNull(playerManagerState)
    val seekStepSec by settingsDataStore.seekStepSec.collectAsStateWithLifecycle(initialValue = 15)
    val autoplayNext by settingsDataStore.autoplayNext.collectAsStateWithLifecycle(initialValue = false)
    var reportingSessionId by remember(playerManager) { mutableLongStateOf(0L) }
    val phashCoordinator: com.adulthub.tv.player.PhashCoordinator = koinInject()
    val phashJobStatuses by phashCoordinator.jobStatuses.collectAsStateWithLifecycle()

    val isPlaying by playerManager.isPlaying.collectAsStateWithLifecycle()
    val isMuted by playerManager.isMuted.collectAsStateWithLifecycle()
    val playbackState by playerManager.playbackState.collectAsStateWithLifecycle()
    val durationMs by playerManager.durationMs.collectAsStateWithLifecycle()
    val playerError by playerManager.errorMessage.collectAsStateWithLifecycle()
    val retryStatus by playerManager.retryStatus.collectAsStateWithLifecycle()

    DisposableEffect(activity, playerManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                playerManager.pause()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    var isOsdVisible by rememberSaveable { mutableStateOf(true) }
    var osdTimerTrigger by remember { mutableLongStateOf(0L) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }
    var resizeMode by rememberSaveable { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackSpeed by rememberSaveable { mutableFloatStateOf(1.0f) }
    var autoplayCountdownSec by remember { mutableStateOf<Int?>(null) }
    val playPauseFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

    // Initial load
    LaunchedEffect(itemId, resume, playerManager) {
        viewModel.loadItem(itemId, resume)
    }

    // Connect PlayerManager callbacks to ViewModel and PlaybackReporter
    LaunchedEffect(playerManager) {
        playerManager.onPlaybackReady = { posMs ->
            viewModel.playbackReporter.reportStart(
                positionMs = posMs,
                canSeek = playerManager.exoPlayer.isCurrentMediaItemSeekable,
                sessionId = reportingSessionId,
            )
        }
        playerManager.onPlaybackEnded = {
            viewModel.onPlaybackEnded(playerManager.currentPositionMs.value, reportingSessionId.takeIf { it > 0L })
        }
    }

    val readyState = uiState as? PlayerUiState.Ready
    val pendingResumeDialogPosition = readyState?.pendingResumePositionMs?.takeIf { it > 0L }
    val phashVideoId = readyState?.item?.let { com.adulthub.tv.player.adultHubVideoId(it) }
    val phashStatus = phashVideoId?.let { phashJobStatuses[it] }
    var hiddenPhashStatusMessage by remember(itemId) { mutableStateOf<String?>(null) }

    // Technical stats are refreshed on their own timer so that the HUD does not force a
    // recomposition of the whole player screen.
    var technicalInfo by remember { mutableStateOf<com.adulthub.tv.player.PlaybackTechnicalInfo?>(null) }
    LaunchedEffect(showInfoDialog, readyState?.playbackRequestId, playerManager) {
        if (!showInfoDialog) {
            technicalInfo = null
            return@LaunchedEffect
        }
        val state = readyState ?: return@LaunchedEffect
        while (true) {
            technicalInfo = playerManager.getTechnicalInfo(
                playbackInfo = state.playbackInfo,
                resolverLatencyMs = state.resolverLatencyMs,
            )
            delay(1_000L)
        }
    }

    LaunchedEffect(phashVideoId, phashStatus?.message) {
        val message = phashStatus?.message ?: return@LaunchedEffect
        hiddenPhashStatusMessage = null
        delay(PHASH_STATUS_VISIBLE_MS)
        hiddenPhashStatusMessage = message
    }

    // Recompose the UI for metadata/favorite changes without restarting this playback request.
    LaunchedEffect(readyState?.playbackRequestId, playerManager) {
        val state = readyState ?: return@LaunchedEffect
        val info = state.playbackInfo
        // Start reporting session
        reportingSessionId = viewModel.playbackReporter.startSession(
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

    // Track the position for the view model without touching Compose state, so the 1 Hz
    // updates never recompose this screen.
    LaunchedEffect(playerManager) {
        while (true) {
            viewModel.updatePlaybackPosition(playerManager.currentPositionMs.value)
            delay(1_000L)
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
                    viewModel.playRandomMovie(currentPositionMs = 0L, reportingSessionId = reportingSessionId.takeIf { it > 0L })
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
        if (isOsdVisible && !mobileControls) {
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
        when (resolvePlayerBackAction(
            autoplayCountdownSec = autoplayCountdownSec,
            showInfoDialog = showInfoDialog,
            hasPendingResumeDialog = pendingResumeDialogPosition != null,
            isOsdVisible = isOsdVisible,
        )) {
            PlayerBackAction.CANCEL_AUTOPLAY -> autoplayCountdownSec = null
            PlayerBackAction.CLOSE_INFO_DIALOG -> showInfoDialog = false
            PlayerBackAction.DISMISS_RESUME_DIALOG -> viewModel.onResumeDialogHandled()
            PlayerBackAction.HIDE_OSD -> isOsdVisible = false
            PlayerBackAction.EXIT_PLAYER -> {
                viewModel.onExitPlayer(playerManager.currentPositionMs.value, reportingSessionId.takeIf { it > 0L })
                playerManager.release()
                onBack()
            }
        }
    }

    // Cleanup when leaving screen
    DisposableEffect(playerManager) {
        onDispose {
            viewModel.onExitPlayer(playerManager.currentPositionMs.value, reportingSessionId.takeIf { it > 0L })
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
                if (pendingResumeDialogPosition != null) {
                    false
                } else if (event.type == KeyEventType.KeyDown) {
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
                                    reportingSessionId.takeIf { it > 0L },
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
                                    reportingSessionId.takeIf { it > 0L },
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
                        viewModel.onExitPlayer(0L, reportingSessionId.takeIf { it > 0L })
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

                // Use a touch-first overlay on phones. The TV overlay is designed for a remote.
                if (mobileControls) MobilePlayerOverlay(
                    title = state.item.title,
                    isPlaying = isPlaying,
                    isBuffering = playbackState == Player.STATE_BUFFERING || isLoadingRandom,
                    positionFlow = playerManager.currentPositionMs,
                    durationMs = durationMs,
                    isOsdVisible = isOsdVisible,
                    onShowOsd = {
                        isOsdVisible = true
                        osdTimerTrigger = System.currentTimeMillis()
                    },
                    onInteraction = { osdTimerTrigger = System.currentTimeMillis() },
                    onPlayPauseClick = {
                        playerManager.togglePlayPause()
                        viewModel.playbackReporter.reportProgressNow(playerManager.currentPositionMs.value, !playerManager.isPlaying.value, reportingSessionId.takeIf { it > 0L })
                    },
                    onSeekRelative = { deltaMs ->
                        playerManager.seekBy(deltaMs)
                        seekFeedback = if (deltaMs > 0) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s"
                        viewModel.playbackReporter.reportProgressNow(playerManager.currentPositionMs.value, !playerManager.isPlaying.value, reportingSessionId.takeIf { it > 0L })
                    },
                    onSeekTo = { positionMs ->
                        playerManager.seekTo(positionMs)
                        viewModel.playbackReporter.reportProgressNow(positionMs, !playerManager.isPlaying.value, reportingSessionId.takeIf { it > 0L })
                    },
                    isMuted = isMuted,
                    onMuteClick = { playerManager.toggleMute() },
                    onRandomClick = { viewModel.playRandomMovie(playerManager.currentPositionMs.value, reportingSessionId.takeIf { it > 0L }) },
                    onInfoClick = { showInfoDialog = !showInfoDialog },
                    onBackClick = {
                        viewModel.onExitPlayer(playerManager.currentPositionMs.value, reportingSessionId.takeIf { it > 0L })
                        playerManager.release()
                        onBack()
                    },
                    isFavorite = state.item.isFavorite,
                    onToggleFavorite = viewModel::toggleFavorite,
                    playbackSpeed = playbackSpeed,
                    onPlaybackSpeedChange = { speed -> playbackSpeed = speed; playerManager.setPlaybackSpeed(speed) },
                    resizeMode = resizeMode,
                    onResizeModeChange = { resizeMode = it },
                    seekFeedback = seekFeedback,
                    seekStepSec = seekStepSec,
                ) else PlayerOverlay(
                    title = state.item.title,
                    isPlaying = isPlaying,
                    isBuffering = playbackState == Player.STATE_BUFFERING || isLoadingRandom,
                    positionFlow = playerManager.currentPositionMs,
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
                            reportingSessionId.takeIf { it > 0L },
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
                            reportingSessionId.takeIf { it > 0L },
                        )
                    },
                    onRandomClick = {
                        viewModel.playRandomMovie(playerManager.currentPositionMs.value, reportingSessionId.takeIf { it > 0L })
                    },
                    onInfoClick = {
                        showInfoDialog = !showInfoDialog
                    },
                    onBackClick = {
                        viewModel.onExitPlayer(playerManager.currentPositionMs.value, reportingSessionId.takeIf { it > 0L })
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

                phashStatus?.takeIf { it.message != hiddenPhashStatusMessage }?.let { status ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(20.dp)
                            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = status.message,
                            color = if (status.terminal && status.message.contains("failed", true)) Color(0xFFFFB4AB) else NeonPink,
                            fontSize = 12.sp,
                            maxLines = 2,
                        )
                    }
                }

                // Technical Playback Info HUD Overlay
                technicalInfo?.let { info ->
                    PlaybackInfoOverlay(
                        technicalInfo = info,
                        isMobile = mobileControls,
                    )
                }

                // End of Movie Dialog
                if (isPlaybackEnded) {
                    EndOfMovieDialog(
                        onNextRandom = {
                            autoplayCountdownSec = null
                            viewModel.playRandomMovie(currentPositionMs = 0L, reportingSessionId = reportingSessionId.takeIf { it > 0L })
                        },
                        onBackToDetails = {
                            autoplayCountdownSec = null
                            viewModel.onExitPlayer(playerManager.currentPositionMs.value, reportingSessionId.takeIf { it > 0L })
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
                                startPositionMs = playerManager.currentPositionMs.value,
                                title = state.playbackInfo.title,
                                headers = state.playbackInfo.headers,
                            )
                        },
                        onBack = {
                            viewModel.onExitPlayer(playerManager.currentPositionMs.value, reportingSessionId.takeIf { it > 0L })
                            playerManager.release()
                            onBack()
                        },
                    )
                }
            }
        }

    }
}

private const val PHASH_STATUS_VISIBLE_MS = 10_000L
