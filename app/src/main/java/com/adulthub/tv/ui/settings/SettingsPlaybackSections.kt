package com.adulthub.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.R
import com.adulthub.tv.data.local.BufferProfile
import com.adulthub.tv.diagnostics.DeviceDiagnostics
import com.adulthub.tv.ui.components.TvChoiceChip
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.SurfaceDark
import com.adulthub.tv.ui.theme.TextPrimary
import com.adulthub.tv.ui.theme.TextSecondary

/** Network buffer profile. Mobile shows the profiles as a list, TV as chips. */
@Composable
internal fun SettingsBufferProfileCard(uiState: SettingsUiState, viewModel: SettingsViewModel, isMobile: Boolean) {
    SettingsCard(title = stringResource(R.string.settings_buffer_title)) {
        Column {
            Text(
                text = stringResource(R.string.settings_buffer_desc),
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (isMobile) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BufferProfile.entries.forEach { profile ->
                        val selected = uiState.bufferProfile == profile
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) NeonPink.copy(alpha = 0.14f) else SurfaceDark)
                                .border(1.dp, if (selected) NeonPink else Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                .clickable { viewModel.setBufferProfile(profile) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(profile.labelRes).substringBefore(" ("), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = stringResource(
                                        R.string.settings_buffer_range,
                                        profile.minBufferMs / 1000,
                                        profile.maxBufferMs / 1000
                                    ),
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Text(if (selected) "✓" else "", color = NeonPink, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BufferProfile.entries.forEach { profile ->
                        val isSelected = uiState.bufferProfile == profile
                        TvChoiceChip(
                            label = stringResource(profile.labelRes),
                            isSelected = isSelected,
                            onClick = { viewModel.setBufferProfile(profile) },
                            height = 38.dp,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/** Low-memory mode. The recommendation depends on the RAM reported by the device. */
@Composable
internal fun SettingsPerformanceCard(uiState: SettingsUiState, viewModel: SettingsViewModel, diagnostics: DeviceDiagnostics) {
    val isLowMemoryDevice = diagnostics.totalRamMb in 1..2048
    SettingsCard(title = stringResource(R.string.settings_performance_title)) {
        Column {
            Text(
                text = stringResource(R.string.settings_low_ram_desc),
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )

            if (isLowMemoryDevice) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_low_ram_recommended, diagnostics.totalRamMb),
                    style = MaterialTheme.typography.bodySmall.copy(color = NeonPink)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TvChoiceChip(
                    label = stringResource(R.string.settings_option_off_default),
                    isSelected = !uiState.lowRamMode,
                    onClick = { viewModel.setLowRamMode(false) },
                    height = 38.dp,
                    fontSize = 13.sp
                )
                TvChoiceChip(
                    label = stringResource(R.string.settings_option_low_ram_on),
                    isSelected = uiState.lowRamMode,
                    onClick = { viewModel.setLowRamMode(true) },
                    height = 38.dp,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/** Seek step and autoplay of the next episode. */
@Composable
internal fun SettingsPlaybackControlsCard(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsCard(title = stringResource(R.string.settings_playback_controls_title)) {
        Column {
            Text(
                text = stringResource(R.string.settings_seek_step_desc),
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    10 to pluralStringResource(R.plurals.settings_seek_step_sec, 10, 10),
                    15 to stringResource(R.string.settings_seek_step_default),
                    30 to pluralStringResource(R.plurals.settings_seek_step_sec, 30, 30),
                    60 to pluralStringResource(R.plurals.settings_seek_step_sec, 60, 60)
                ).forEach { (step, label) ->
                    TvChoiceChip(
                        label = label,
                        isSelected = uiState.seekStepSec == step,
                        onClick = { viewModel.setSeekStepSec(step) },
                        height = 38.dp,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.settings_autoplay_desc),
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TvChoiceChip(
                    label = stringResource(R.string.settings_option_off_default),
                    isSelected = !uiState.autoplayNext,
                    onClick = { viewModel.setAutoplayNext(false) },
                    height = 38.dp,
                    fontSize = 13.sp
                )
                TvChoiceChip(
                    label = stringResource(R.string.settings_option_autoplay_on),
                    isSelected = uiState.autoplayNext,
                    onClick = { viewModel.setAutoplayNext(true) },
                    height = 38.dp,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/** Perceptual hash computed during playback, and its frame count. */
@Composable
internal fun SettingsPhashCard(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsCard(title = stringResource(R.string.settings_phash_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_phash_desc),
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            }
            TvChoiceChip(
                label = stringResource(if (uiState.phashOnPlayback) R.string.settings_value_on else R.string.settings_value_off),
                isSelected = uiState.phashOnPlayback,
                onClick = { viewModel.setPhashOnPlayback(!uiState.phashOnPlayback) }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_phash_frames_label),
                style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
            )
            Text(
                text = stringResource(R.string.settings_phash_frames_note),
                style = MaterialTheme.typography.bodyMedium.copy(color = NeonPink)
            )
        }
    }
}
