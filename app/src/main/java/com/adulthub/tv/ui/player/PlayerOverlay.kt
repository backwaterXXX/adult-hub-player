package com.adulthub.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.OutlinedIconButton
import androidx.tv.material3.OutlinedIconButtonDefaults
import androidx.tv.material3.Text
import androidx.media3.ui.AspectRatioFrameLayout
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import com.adulthub.tv.R
import com.adulthub.tv.data.resolver.ORIGINAL_VARIANT_ID
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.SurfaceDark
import java.util.Locale

private const val SEEK_REPEAT_WINDOW_MS = 1_000L
private const val MAX_SEEK_STEP_SECONDS = 60

@Composable
fun PlayerOverlay(
    title: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionFlow: StateFlow<Long>,
    durationMs: Long,
    isOsdVisible: Boolean,
    seekFeedback: String?,
    playPauseFocusRequester: FocusRequester,
    onShowOsd: () -> Unit,
    onResetOsdTimer: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeekRelative: (deltaMs: Long) -> Unit,
    onRandomClick: () -> Unit,
    onInfoClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    seekStepSec: Int = 15,
    playbackSpeed: Float = 1.0f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    isMuted: Boolean = false,
    onMuteClick: () -> Unit = {},
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onResizeModeChange: (Int) -> Unit = {},
    qualityOptions: List<QualityOption> = emptyList(),
    isQualitySwitching: Boolean = false,
    qualityLoadFailed: Boolean = false,
    onQualitySelected: (String) -> Unit = {},
    onQualityRetry: () -> Unit = {},
) {
    // The position flow is collected here (and not in the parent screen) so that the 1 Hz
    // position updates only recompose the overlay instead of the whole player screen.
    val currentPositionMs by positionFlow.collectAsStateWithLifecycle()
    val duration = durationMs.coerceAtLeast(0L)
    val position = currentPositionMs.coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)
    val progress = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    var nextSeekStepSeconds by remember(seekStepSec) { mutableIntStateOf(seekStepSec) }
    var lastSeekDirection by remember { mutableIntStateOf(0) }
    var lastSeekAtMs by remember { mutableLongStateOf(0L) }
    var isMoreMenuVisible by remember { mutableStateOf(false) }
    var isQualityPickerVisible by remember { mutableStateOf(false) }
    val qualityPickerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isOsdVisible) {
        if (!isOsdVisible) {
            isMoreMenuVisible = false
            isQualityPickerVisible = false
        }
    }

    LaunchedEffect(isQualityPickerVisible) {
        if (isQualityPickerVisible) {
            runCatching { qualityPickerFocusRequester.requestFocus() }
        }
    }

    val seekWithAcceleration: (Int) -> Unit = { direction ->
        val now = SystemClock.elapsedRealtime()
        val isQuickRepeat = direction == lastSeekDirection && now - lastSeekAtMs <= SEEK_REPEAT_WINDOW_MS
        val stepSeconds = if (isQuickRepeat) nextSeekStepSeconds else seekStepSec
        onSeekRelative(direction * stepSeconds * 1_000L)
        nextSeekStepSeconds = (stepSeconds + seekStepSec).coerceAtMost(MAX_SEEK_STEP_SECONDS)
        lastSeekDirection = direction
        lastSeekAtMs = now
        onResetOsdTimer()
    }

    LaunchedEffect(lastSeekAtMs, seekStepSec) {
        if (lastSeekAtMs > 0L) {
            val seekAt = lastSeekAtMs
            delay(SEEK_REPEAT_WINDOW_MS)
            if (lastSeekAtMs == seekAt) {
                nextSeekStepSeconds = seekStepSec
                lastSeekDirection = 0
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    onResetOsdTimer()
                }
                false
            }
    ) {
        // Center seek feedback indicator
        if (!seekFeedback.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .border(1.5.dp, NeonPink, RoundedCornerShape(14.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val isForward = seekFeedback.startsWith("+")
                    Icon(
                        imageVector = if (isForward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                        contentDescription = null,
                        tint = NeonPink,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = seekFeedback,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Center buffering spinner
        if (isBuffering) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                com.adulthub.tv.ui.components.LoadingIndicator()
            }
        }

        // OSD Controls with Top & Bottom Gradients
        AnimatedVisibility(
            visible = isOsdVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Bar with gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.9f),
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Transparent,
                                )
                            )
                        )
                        .padding(horizontal = 48.dp, vertical = 28.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )

                        Text(
                            text = stringResource(R.string.player_brand),
                            color = NeonPink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }

                // Bottom Controls with gradient
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Black.copy(alpha = 0.95f),
                                )
                            )
                        )
                        .padding(horizontal = 48.dp, vertical = 32.dp),
                ) {
                    // Time and Progress Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatTime(position),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Interactive seekable progress bar
                        FocusableProgressBar(
                            progress = progress,
                            onSeekLeft = {
                                seekWithAcceleration(-1)
                            },
                            onSeekRight = {
                                seekWithAcceleration(1)
                            },
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = if (duration > 0L) formatTime(duration) else stringResource(R.string.player_time_unknown),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (isMoreMenuVisible) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.85f))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val speedText = stringResource(R.string.player_speed, playbackSpeed)
                                OsdActionChip(
                                    icon = Icons.Rounded.Speed,
                                    text = speedText,
                                    isActive = playbackSpeed != 1.0f,
                                    onClick = {
                                        onPlaybackSpeedChange(nextPlaybackSpeed(playbackSpeed))
                                        onResetOsdTimer()
                                    },
                                )
                                val zoomText = resizeModeLabel(resizeMode)
                                OsdActionChip(
                                    icon = Icons.Rounded.AspectRatio,
                                    text = zoomText,
                                    isActive = resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                    onClick = {
                                        onResizeModeChange(nextResizeMode(resizeMode))
                                        onResetOsdTimer()
                                    },
                                )
                                // Without any option the only useful entry left is a retry
                                // after the variants request failed.
                                if (qualityLoadFailed && qualityOptions.size <= 1) {
                                    OsdActionChip(
                                        icon = Icons.Rounded.HighQuality,
                                        text = stringResource(R.string.player_quality_unavailable_retry),
                                        onClick = {
                                            onQualityRetry()
                                            onResetOsdTimer()
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (isQualityPickerVisible && qualityOptions.size > 1) {
                        val focusedIndex = qualityOptions.indexOfFirst { it.isSelected }.takeIf { it >= 0 } ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.85f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            qualityOptions.forEachIndexed { index, option ->
                                OsdActionChip(
                                    icon = Icons.Rounded.HighQuality,
                                    text = option.label,
                                    isActive = option.isSelected,
                                    onClick = {
                                        if (!isQualitySwitching) {
                                            onQualitySelected(option.variantId)
                                            isQualityPickerVisible = false
                                        }
                                        onResetOsdTimer()
                                    },
                                    modifier = if (index == focusedIndex) {
                                        Modifier.focusRequester(qualityPickerFocusRequester)
                                    } else {
                                        Modifier
                                    },
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OsdIconButton(
                            icon = Icons.Rounded.FastRewind,
                            contentDescription = pluralStringResource(
                                R.plurals.player_cd_rewind,
                                nextSeekStepSeconds,
                                nextSeekStepSeconds,
                            ),
                            onClick = { seekWithAcceleration(-1) },
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        OsdIconButton(
                            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.player_cd_pause else R.string.player_cd_play),
                            isPrimary = true,
                            enabled = !isBuffering,
                            onClick = {
                                onPlayPauseClick()
                                onResetOsdTimer()
                            },
                            modifier = Modifier.focusRequester(playPauseFocusRequester),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        OsdIconButton(
                            icon = Icons.Rounded.FastForward,
                            contentDescription = pluralStringResource(
                                R.plurals.player_cd_forward,
                                nextSeekStepSeconds,
                                nextSeekStepSeconds,
                            ),
                            onClick = { seekWithAcceleration(1) },
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        OsdIconButton(
                            icon = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            contentDescription = stringResource(if (isMuted) R.string.player_cd_unmute else R.string.player_cd_mute),
                            isActive = isMuted,
                            onClick = {
                                onMuteClick()
                                onResetOsdTimer()
                            },
                        )
                        Spacer(modifier = Modifier.width(28.dp))
                        OsdIconButton(
                            icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = stringResource(if (isFavorite) R.string.player_cd_remove_favorite else R.string.player_cd_add_favorite),
                            isActive = isFavorite,
                            onClick = {
                                onToggleFavorite()
                                onResetOsdTimer()
                            },
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        OsdIconButton(
                            icon = Icons.Rounded.Shuffle,
                            contentDescription = stringResource(R.string.player_cd_random),
                            onClick = {
                                onRandomClick()
                                onResetOsdTimer()
                            },
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        OsdIconButton(
                            icon = Icons.Rounded.Info,
                            contentDescription = stringResource(R.string.player_cd_info),
                            onClick = {
                                onInfoClick()
                                onResetOsdTimer()
                            },
                        )
                        // The quality button joins the bar only while another file version
                        // is actually selectable.
                        if (qualityOptions.size > 1) {
                            Spacer(modifier = Modifier.width(14.dp))
                            val selectedOption = qualityOptions.firstOrNull { it.isSelected }
                            OsdIconButton(
                                icon = Icons.Rounded.HighQuality,
                                contentDescription = when {
                                    isQualitySwitching -> stringResource(R.string.player_cd_quality_changing)
                                    else -> stringResource(
                                        R.string.player_cd_quality,
                                        selectedOption?.label ?: stringResource(R.string.player_quality_original),
                                    )
                                },
                                isActive = isQualityPickerVisible ||
                                    (selectedOption != null && selectedOption.variantId != ORIGINAL_VARIANT_ID),
                                onClick = {
                                    if (!isQualitySwitching) {
                                        isQualityPickerVisible = !isQualityPickerVisible
                                    }
                                    onResetOsdTimer()
                                },
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        OsdIconButton(
                            icon = Icons.Rounded.MoreHoriz,
                            contentDescription = stringResource(R.string.player_cd_more),
                            isActive = isMoreMenuVisible,
                            onClick = {
                                isMoreMenuVisible = !isMoreMenuVisible
                                onResetOsdTimer()
                            },
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        OsdIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.player_cd_close),
                            onClick = onBackClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OsdIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isActive: Boolean = false,
    activeColor: Color = NeonPink,
    enabled: Boolean = true,
) {
    val buttonSize = if (isPrimary) 56.dp else 46.dp
    val iconSize = if (isPrimary) 30.dp else 22.dp

    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = OutlinedIconButtonDefaults.shape(CircleShape),
        scale = OutlinedIconButtonDefaults.scale(
            scale = 1.0f,
            focusedScale = if (isPrimary) 1.15f else 1.1f,
        ),
        colors = OutlinedIconButtonDefaults.colors(
            containerColor = if (isPrimary) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.55f),
            focusedContainerColor = SurfaceDark,
            contentColor = if (isActive) activeColor else Color.White.copy(alpha = 0.9f),
            focusedContentColor = if (isActive) activeColor else Color.White,
        ),
        border = OutlinedIconButtonDefaults.border(
            border = Border(
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isActive) activeColor.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.25f),
                )
            ),
            focusedBorder = Border(
                border = BorderStroke(
                    width = 2.5.dp,
                    color = NeonPink,
                )
            ),
        ),
        modifier = modifier
            .size(buttonSize)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun OsdActionChip(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        shape = OutlinedButtonDefaults.shape(RoundedCornerShape(14.dp)),
        colors = OutlinedButtonDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.65f),
            focusedContainerColor = SurfaceDark,
            contentColor = if (isActive) NeonPink else Color.White,
            focusedContentColor = if (isActive) NeonPink else Color.White,
        ),
        border = OutlinedButtonDefaults.border(
            border = Border(
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isActive) NeonPink.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.2f),
                )
            ),
            focusedBorder = Border(
                border = BorderStroke(
                    width = 2.dp,
                    color = NeonPink,
                )
            ),
        ),
        modifier = modifier.height(42.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isActive) NeonPink else Color.White,
            )
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FocusableProgressBar(
    progress: Float,
    onSeekLeft: () -> Unit,
    onSeekRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val seekBarDescription = stringResource(R.string.player_cd_seek_bar)
    val clampedProgress = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) NeonPink else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .focusable(interactionSource = interactionSource)
            .semantics {
                contentDescription = seekBarDescription
                progressBarRangeInfo = ProgressBarRangeInfo(clampedProgress, 0f..1f)
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onSeekLeft()
                            true
                        }
                        Key.DirectionRight -> {
                            onSeekRight()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Track background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFocused) 8.dp else 5.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.25f)),
        )
        // Progress fill
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(if (isFocused) 8.dp else 5.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(NeonPink),
        )
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
