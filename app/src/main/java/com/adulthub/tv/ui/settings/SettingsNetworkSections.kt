package com.adulthub.tv.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.R
import com.adulthub.tv.data.local.ResolverUrlSource
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.components.TvTextField
import com.adulthub.tv.ui.theme.TextPrimary
import com.adulthub.tv.ui.theme.TextSecondary

/** Resolver address, its source and the connection checks. */
@Composable
internal fun SettingsResolverCard(uiState: SettingsUiState, viewModel: SettingsViewModel, isMobile: Boolean) {
    SettingsCard(title = stringResource(R.string.settings_resolver_title)) {
        Column {
            if (isMobile) {
                Text(
                    text = stringResource(R.string.settings_resolver_desc),
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            SettingsInfoRow(
                label = stringResource(R.string.settings_resolver_current_origin),
                value = uiState.resolverBaseUrl.ifBlank { stringResource(R.string.settings_not_configured) }
            )
            Spacer(modifier = Modifier.height(6.dp))
            SettingsInfoRow(
                label = stringResource(R.string.settings_resolver_origin_source),
                value = stringResource(resolverUrlSourceLabel(uiState.resolverBaseUrlSource))
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsInfoRow(
                label = stringResource(R.string.settings_value_jellyfin),
                value = uiState.jellyfinConnectionStatus
            )
            Spacer(modifier = Modifier.height(6.dp))
            SettingsInfoRow(
                label = stringResource(R.string.settings_label_resolver),
                value = uiState.resolverConnectionStatus
            )
            Spacer(modifier = Modifier.height(12.dp))
            TvSecondaryButton(
                onClick = viewModel::checkConnections,
                enabled = !uiState.isCheckingConnections,
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    stringResource(
                        if (uiState.isCheckingConnections) {
                            R.string.settings_checking_connections
                        } else {
                            R.string.settings_check_connections
                        }
                    ),
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            TvTextField(
                value = uiState.resolverUrlDraft,
                onValueChange = viewModel::onResolverUrlDraftChanged,
                label = stringResource(R.string.settings_resolver_url_label),
                hint = stringResource(R.string.settings_resolver_url_hint),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { viewModel.setResolverUrl(uiState.resolverUrlDraft) }
                ),
                modifier = Modifier.fillMaxWidth(0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvSecondaryButton(
                    onClick = { viewModel.setResolverUrl(uiState.resolverUrlDraft) },
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(stringResource(R.string.settings_save_resolver_url), fontWeight = FontWeight.Medium)
                }
                if (uiState.resolverBaseUrl.isNotBlank()) {
                    TvSecondaryButton(
                        onClick = { viewModel.setResolverUrl("") },
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(stringResource(R.string.settings_use_jellyfin_url), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** Hosts the user approved for cleartext HTTP, with the option to revoke each one. */
@Composable
internal fun SettingsHttpConsentCard(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsCard(title = stringResource(R.string.settings_http_consent_title)) {
        Text(
            text = stringResource(R.string.settings_http_consent_desc),
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (uiState.approvedCleartextOrigins.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_http_no_hosts),
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )
        } else {
            uiState.approvedCleartextOrigins.sorted().forEach { origin ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(origin, color = TextPrimary)
                    TvSecondaryButton(
                        onClick = { viewModel.revokeCleartextConsent(origin) },
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(stringResource(R.string.settings_revoke), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/**
 * Describes where the effective Resolver address comes from.
 *
 * The address can be configured explicitly, inherited from the Jellyfin server
 * address, or still empty. The source tells the user which service the app
 * actually talks to.
 */
@StringRes
private fun resolverUrlSourceLabel(source: ResolverUrlSource): Int = when (source) {
    ResolverUrlSource.CONFIGURED -> R.string.settings_resolver_source_configured
    ResolverUrlSource.SERVER_FALLBACK -> R.string.settings_resolver_source_server
    ResolverUrlSource.DEFAULT -> R.string.settings_not_configured
}
