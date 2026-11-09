package com.adulthub.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.R
import com.adulthub.tv.ui.components.TvChoiceChip
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.theme.TextPrimary
import com.adulthub.tv.ui.theme.TextSecondary

/** The three kinds of local data the privacy card can clear. */
internal enum class ClearDataType(val titleRes: Int) {
    WATCH_HISTORY(R.string.settings_clear_history),
    SEARCH_HISTORY(R.string.settings_clear_search),
    RESUME_PROGRESS(R.string.settings_clear_resume)
}

/** Size limit of the local cache, and a button that empties it. */
@Composable
internal fun SettingsCacheCard(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    SettingsCard(title = stringResource(R.string.settings_cache_title)) {
        Column {
            Text(
                text = stringResource(R.string.settings_cache_desc),
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cache size choices
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val options = listOf(
                        0 to stringResource(R.string.settings_value_off),
                        128 to stringResource(R.string.settings_cache_size_mb, 128),
                        256 to stringResource(R.string.settings_cache_size_mb, 256),
                        512 to stringResource(R.string.settings_cache_size_mb, 512),
                        1024 to stringResource(R.string.settings_cache_size_mb, 1024)
                    )
                    options.forEach { (limit, label) ->
                        val isSelected = uiState.cacheLimitMb == limit
                        TvChoiceChip(
                            label = label,
                            isSelected = isSelected,
                            onClick = { viewModel.setCacheLimit(limit) },
                            height = 38.dp,
                            fontSize = 13.sp
                        )
                    }
                }

                // Clear Cache Button
                TvSecondaryButton(
                    onClick = { viewModel.clearCache(context) },
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(stringResource(R.string.settings_clear_cache), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Shown only when a history import from the previous install is waiting. */
@Composable
internal fun SettingsHistoryImportCard(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    if (uiState.hasPendingHistoryImport) {
        SettingsCard(title = stringResource(R.string.settings_history_import_title)) {
            Text(
                text = stringResource(R.string.settings_history_import_desc),
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
            Spacer(modifier = Modifier.height(12.dp))
            TvSecondaryButton(
                onClick = viewModel::importUnisolatedHistory,
                modifier = Modifier.height(40.dp)
            ) {
                Text(stringResource(R.string.settings_history_import_action), fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Buttons that ask the screen to confirm the removal of watch, search or resume data. */
@Composable
internal fun SettingsPrivacyCard(onRequestClear: (ClearDataType) -> Unit) {
    SettingsCard(title = stringResource(R.string.settings_privacy_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Watch history
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_clear_history),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_clear_history_desc),
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
                TvSecondaryButton(
                    onClick = { onRequestClear(ClearDataType.WATCH_HISTORY) },
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(stringResource(R.string.action_confirm_clear))
                }
            }

            // Search history
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_clear_search),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_clear_search_desc),
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
                TvSecondaryButton(
                    onClick = { onRequestClear(ClearDataType.SEARCH_HISTORY) },
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(stringResource(R.string.action_confirm_clear))
                }
            }

            // Resume progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_clear_resume),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_clear_resume_desc),
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
                TvSecondaryButton(
                    onClick = { onRequestClear(ClearDataType.RESUME_PROGRESS) },
                    modifier = Modifier.height(38.dp)
                ) {
                    Text(stringResource(R.string.action_confirm_clear))
                }
            }
        }
    }
}

/** Excluded tags and hidden channels. Tapping a chip removes that entry. */
@Composable
internal fun SettingsContentFiltersCard(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsCard(title = stringResource(R.string.settings_content_filters_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Excluded tags
            Column {
                Text(
                    text = stringResource(R.string.settings_excluded_tags),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (uiState.excludedTags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_excluded_tags_empty),
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        items(items = uiState.excludedTags.toList(), key = { it }) { tag ->
                            TvSecondaryButton(
                                onClick = { viewModel.removeExcludedTag(tag) },
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(text = "$tag  ✕", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Hidden channels
            Column {
                Text(
                    text = stringResource(R.string.settings_hidden_channels),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (uiState.hiddenChannelIds.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_hidden_channels_empty),
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        items(items = uiState.hiddenChannelIds.toList(), key = { it }) { channelId ->
                            TvSecondaryButton(
                                onClick = { viewModel.unhideChannel(channelId) },
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = "${uiState.hiddenChannelNames[channelId] ?: channelId}  ✕",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
