package com.adulthub.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import com.adulthub.tv.data.resolver.ORIGINAL_VARIANT_ID
import com.adulthub.tv.ui.theme.NeonPink
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MobilePlayerOverlay(
    title: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionFlow: StateFlow<Long>,
    durationMs: Long,
    isOsdVisible: Boolean,
    onShowOsd: () -> Unit,
    onInteraction: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    isMuted: Boolean,
    onMuteClick: () -> Unit,
    onRandomClick: () -> Unit,
    onInfoClick: () -> Unit,
    onBackClick: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    resizeMode: Int,
    onResizeModeChange: (Int) -> Unit,
    seekFeedback: String?,
    seekStepSec: Int,
    qualityOptions: List<QualityOption> = emptyList(),
    isQualitySwitching: Boolean = false,
    qualityLoadFailed: Boolean = false,
    onQualitySelected: (String) -> Unit = {},
    onQualityRetry: () -> Unit = {},
) {
    // Collected inside the overlay so the 1 Hz position updates do not recompose the whole screen.
    val currentPositionMs by positionFlow.collectAsStateWithLifecycle()
    val duration = durationMs.coerceAtLeast(0L)
    val position = currentPositionMs.coerceIn(0L, duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
    var expanded by remember { mutableStateOf(false) }
    var showQualityPicker by remember { mutableStateOf(false) }
    var isDraggingSeek by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(position.toFloat()) }
    LaunchedEffect(isOsdVisible) {
        if (!isOsdVisible) {
            expanded = false
            showQualityPicker = false
        }
    }
    LaunchedEffect(position, isDraggingSeek) {
        if (!isDraggingSeek) dragPositionMs = position.toFloat()
    }

    // Keep the video tappable when the controls have faded away.
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onShowOsd,
            ),
    ) {
        if (!seekFeedback.isNullOrBlank()) {
            Text(
                seekFeedback,
                modifier = Modifier.align(Alignment.Center).clip(CircleShape)
                    .background(Color.Black.copy(alpha = .78f)).padding(horizontal = 20.dp, vertical = 12.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        if (isBuffering) {
            Box(Modifier.align(Alignment.Center).clip(CircleShape).background(Color.Black.copy(alpha = .65f)).padding(18.dp)) {
                com.adulthub.tv.ui.components.LoadingIndicator()
            }
        }

        AnimatedVisibility(
            visible = isOsdVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .88f), Color.Transparent)))
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackClick) { Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White) }
                    Text(
                        title,
                        Modifier.weight(1f).padding(horizontal = 8.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onToggleFavorite(); onInteraction() }) {
                        Icon(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite", tint = if (isFavorite) NeonPink else Color.White)
                    }
                    IconButton(onClick = { onInfoClick(); onInteraction() }) { Icon(Icons.Rounded.Info, "Playback information", tint = Color.White) }
                }

                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .74f), Color.Black.copy(alpha = .94f))))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (expanded) {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    val next = when { playbackSpeed < 1.15f -> 1.25f; playbackSpeed < 1.4f -> 1.5f; playbackSpeed < 1.75f -> 2f; else -> 1f }
                                    onPlaybackSpeedChange(next)
                                    onInteraction()
                                }) {
                                    Text("${playbackSpeed}×", color = Color.White, style = MaterialTheme.typography.labelLarge)
                                }
                                IconButton(onClick = {
                                    onResizeModeChange(when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    })
                                    onInteraction()
                                }) { Icon(Icons.Rounded.AspectRatio, "Change aspect ratio", tint = Color.White) }
                                // Without any option the only useful entry left is a retry
                                // after the variants request failed.
                                if (qualityLoadFailed && qualityOptions.size <= 1) {
                                    TextButton(
                                        onClick = {
                                            onQualityRetry()
                                            onInteraction()
                                        },
                                    ) {
                                        Text("Quality: retry", color = NeonPink, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    if (showQualityPicker && qualityOptions.size > 1) {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                        ) {
                            qualityOptions.forEach { option ->
                                TextButton(
                                    onClick = {
                                        if (!isQualitySwitching) {
                                            onQualitySelected(option.variantId)
                                            showQualityPicker = false
                                        }
                                        onInteraction()
                                    },
                                ) {
                                    Text(
                                        text = (if (option.isSelected) "✓ " else "") + option.label,
                                        color = if (option.isSelected) NeonPink else Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(position), color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = if (isDraggingSeek) dragPositionMs else position.toFloat(),
                            enabled = duration > 0L,
                            onValueChange = {
                                dragPositionMs = it
                                isDraggingSeek = true
                                onInteraction()
                            },
                            onValueChangeFinished = {
                                if (duration > 0L) onSeekTo(dragPositionMs.toLong())
                                isDraggingSeek = false
                                onInteraction()
                            },
                            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).graphicsLayer { scaleY = 0.6f },
                        )
                        Text(if (duration > 0L) formatTime(duration) else "--:--", color = Color.White.copy(alpha = .75f), fontSize = 12.sp)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onSeekRelative(-seekStepSec * 1_000L); onInteraction() }) { Icon(Icons.Rounded.FastRewind, "Rewind $seekStepSec seconds", tint = Color.White, modifier = Modifier.size(30.dp)) }
                        IconButton(
                            onClick = { onPlayPauseClick(); onInteraction() },
                            enabled = !isBuffering,
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(NeonPink),
                        ) {
                            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = { onSeekRelative(seekStepSec * 1_000L); onInteraction() }) { Icon(Icons.Rounded.FastForward, "Forward $seekStepSec seconds", tint = Color.White, modifier = Modifier.size(30.dp)) }
                        IconButton(onClick = { onRandomClick(); onInteraction() }) { Icon(Icons.Rounded.Shuffle, "Play random", tint = Color.White) }
                        // The quality button joins the bar only while another file version
                        // is actually selectable.
                        if (qualityOptions.size > 1) {
                            val selectedOption = qualityOptions.firstOrNull { it.isSelected }
                            val qualityActive = showQualityPicker ||
                                (selectedOption != null && selectedOption.variantId != ORIGINAL_VARIANT_ID)
                            IconButton(
                                onClick = {
                                    if (!isQualitySwitching) showQualityPicker = !showQualityPicker
                                    onInteraction()
                                },
                            ) {
                                Icon(
                                    Icons.Rounded.HighQuality,
                                    contentDescription = when {
                                        isQualitySwitching -> "Changing quality"
                                        else -> "Quality: ${selectedOption?.label ?: "Original"}"
                                    },
                                    tint = if (qualityActive) NeonPink else Color.White,
                                )
                            }
                        }
                        IconButton(onClick = { onMuteClick(); onInteraction() }) { Icon(if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, if (isMuted) "Unmute" else "Mute", tint = Color.White) }
                        IconButton(onClick = { expanded = !expanded; onInteraction() }) { Icon(Icons.Rounded.MoreHoriz, "More playback options", tint = Color.White) }
                    }
                }
            }
        }
    }
}
