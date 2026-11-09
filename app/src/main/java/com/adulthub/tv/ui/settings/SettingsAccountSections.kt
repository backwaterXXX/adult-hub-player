package com.adulthub.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.R
import com.adulthub.tv.ui.components.TvChoiceChip
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.theme.TextSecondary

/** Server connection and the signed-in account. */
@Composable
internal fun SettingsSessionCard(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    SettingsCard(title = stringResource(R.string.settings_account_server_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SettingsInfoRow(
                    label = stringResource(R.string.settings_server_url_label),
                    value = uiState.serverUrl.ifEmpty { stringResource(R.string.settings_not_connected) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                SettingsInfoRow(
                    label = stringResource(R.string.settings_server_name_label),
                    value = uiState.serverName.ifEmpty { stringResource(R.string.settings_value_jellyfin) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                SettingsInfoRow(
                    label = stringResource(R.string.login_username_label),
                    value = uiState.username.ifEmpty { stringResource(R.string.settings_value_guest) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                SettingsInfoRow(
                    label = stringResource(R.string.settings_playback_label),
                    value = stringResource(R.string.settings_playback_streams)
                )
            }

            TvSecondaryButton(
                onClick = { viewModel.logout(cacheContext = context) },
                modifier = Modifier.height(44.dp)
            ) {
                Text(stringResource(R.string.settings_sign_out), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Choice of the active Jellyfin library. */
@Composable
internal fun SettingsLibraryCard(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsCard(title = stringResource(R.string.settings_active_library_title)) {
        Column {
            Text(
                text = stringResource(R.string.settings_currently_selected, uiState.selectedLibraryName),
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (uiState.availableLibraries.isNotEmpty()) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 4.dp)
                ) {
                    uiState.availableLibraries.forEach { library ->
                        val isSelected = library.id == uiState.selectedLibraryId
                        TvChoiceChip(
                            label = library.name,
                            isSelected = isSelected,
                            onClick = { viewModel.selectLibrary(library) },
                            height = 40.dp,
                            fontSize = 14.sp,
                            cornerRadius = 12.dp
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.settings_no_additional_libraries),
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
        }
    }
}

/** Categories shown on the home screen. */
@Composable
internal fun SettingsHomeScreenCard(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsCard(title = stringResource(R.string.settings_home_screen_title)) {
        Column {
            Text(
                text = stringResource(R.string.settings_home_screen_desc),
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (uiState.availableHomeCategories.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_home_categories_empty),
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            } else {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 4.dp)
                ) {
                    uiState.availableHomeCategories.forEach { category ->
                        TvChoiceChip(
                            label = category.name,
                            isSelected = category.id in uiState.selectedHomeCategoryIds,
                            onClick = { viewModel.toggleHomeCategory(category.id) },
                            height = 38.dp,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
