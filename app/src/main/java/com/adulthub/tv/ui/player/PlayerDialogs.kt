package com.adulthub.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Text
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.SurfaceDark

@Composable
fun ResumePlaybackDialog(
    resumePositionMs: Long,
    onResume: () -> Unit,
    onStartFromBeginning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resumeButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        resumeButtonFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(2.dp, NeonPink, RoundedCornerShape(16.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Resume Playback?",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Would you like to resume from ${formatTime(resumePositionMs)} or start over from the beginning?",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White.copy(alpha = 0.8f),
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.colors(
                        containerColor = NeonPink,
                        focusedContainerColor = NeonPink,
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    modifier = Modifier
                        .focusRequester(resumeButtonFocusRequester)
                        .height(44.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Resume (${formatTime(resumePositionMs)})",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                OutlinedButton(
                    onClick = onStartFromBeginning,
                    colors = OutlinedButtonDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = SurfaceDark,
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                    border = OutlinedButtonDefaults.border(
                        border = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                        ),
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, NeonPink)
                        ),
                    ),
                    shape = OutlinedButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    modifier = Modifier.height(44.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Replay,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Start Over",
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EndOfMovieDialog(
    onNextRandom: () -> Unit,
    onBackToDetails: () -> Unit,
    countdownSeconds: Int? = null,
    onCancelCountdown: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val nextButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        nextButtonFocusRequester.requestFocus()
    }

    val isCountingDown = countdownSeconds != null && countdownSeconds > 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(2.dp, NeonPink, RoundedCornerShape(16.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isCountingDown) "Next Movie in ${countdownSeconds}s" else "Scene Finished",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isCountingDown) "Playing next movie automatically or cancel to stay." else "What would you like to watch next?",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White.copy(alpha = 0.8f),
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(
                    onClick = onNextRandom,
                    colors = ButtonDefaults.colors(
                        containerColor = NeonPink,
                        focusedContainerColor = NeonPink,
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    modifier = Modifier
                        .focusRequester(nextButtonFocusRequester)
                        .height(44.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (isCountingDown) Icons.Rounded.PlayArrow else Icons.Rounded.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = if (isCountingDown) "Play now" else "Next movie",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                OutlinedButton(
                    onClick = if (isCountingDown) onCancelCountdown else onBackToDetails,
                    colors = OutlinedButtonDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = SurfaceDark,
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                    border = OutlinedButtonDefaults.border(
                        border = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                        ),
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, NeonPink)
                        ),
                    ),
                    shape = OutlinedButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    modifier = Modifier.height(44.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (isCountingDown) Icons.Rounded.Close else Icons.Rounded.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (isCountingDown) "Cancel" else "Back to Details",
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaybackErrorDialog(
    errorMessage: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        retryButtonFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(2.dp, Color.Red, RoundedCornerShape(16.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Playback Error",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White.copy(alpha = 0.85f),
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.colors(
                        containerColor = NeonPink,
                        focusedContainerColor = NeonPink,
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    modifier = Modifier
                        .focusRequester(retryButtonFocusRequester)
                        .height(44.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Retry",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                OutlinedButton(
                    onClick = onBack,
                    colors = OutlinedButtonDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = SurfaceDark,
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                    border = OutlinedButtonDefaults.border(
                        border = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                        ),
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, NeonPink)
                        ),
                    ),
                    shape = OutlinedButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    modifier = Modifier.height(44.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Back",
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaybackInfoOverlay(
    technicalInfo: com.adulthub.tv.player.PlaybackTechnicalInfo,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 28.dp, end = 40.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .border(1.dp, NeonPink.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TECHNICAL STATS",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                    ),
                )
                val isDirect = technicalInfo.playMethod.contains("Direct", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonPink.copy(alpha = 0.16f))
                        .border(1.dp, NeonPink, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = if (isDirect) "DIRECT" else "TRANSCODE",
                        color = NeonPink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TechnicalInfoRow(label = "Stream Mode", value = technicalInfo.playMethod)
                TechnicalInfoRow(label = "Decoder", value = technicalInfo.decoderMode)
                if (technicalInfo.resolverLatencyMs != null && technicalInfo.resolverLatencyMs > 0L) {
                    TechnicalInfoRow(label = "Resolver Latency", value = "${technicalInfo.resolverLatencyMs} ms")
                }
                if (technicalInfo.retryCount > 0) {
                    TechnicalInfoRow(label = "Retries", value = "${technicalInfo.retryCount}")
                }
                TechnicalInfoRow(label = "Dropped Frames", value = "${technicalInfo.droppedFrames}")
                TechnicalInfoRow(label = "Resolution", value = technicalInfo.resolution)
                TechnicalInfoRow(label = "Video Codec", value = technicalInfo.videoCodec)
                if (technicalInfo.frameRate.isNotBlank()) {
                    TechnicalInfoRow(label = "Frame Rate", value = technicalInfo.frameRate)
                }
                if (technicalInfo.videoBitrate.isNotBlank()) {
                    TechnicalInfoRow(label = "Bitrate", value = technicalInfo.videoBitrate)
                }
                TechnicalInfoRow(label = "Audio Codec", value = technicalInfo.audioCodec)
                TechnicalInfoRow(label = "Audio Channels", value = technicalInfo.audioChannels)
                if (technicalInfo.audioSampleRate.isNotBlank()) {
                    TechnicalInfoRow(label = "Sample Rate", value = technicalInfo.audioSampleRate)
                }
                TechnicalInfoRow(label = "Buffer Ahead", value = "${technicalInfo.bufferHealthMs / 1000}s")
                TechnicalInfoRow(label = "Container", value = technicalInfo.container)
                TechnicalInfoRow(label = "File Size", value = technicalInfo.fileSize)
            }
        }
    }
}

@Composable
fun PlaybackInfoDialog(
    technicalInfo: com.adulthub.tv.player.PlaybackTechnicalInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaybackInfoOverlay(
        technicalInfo = technicalInfo,
        modifier = modifier,
    )
}

@Composable
private fun TechnicalInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
        )
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}
