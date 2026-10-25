package com.adulthub.tv.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.R
import com.adulthub.tv.ui.components.CleartextHttpConsentDialog
import com.adulthub.tv.ui.components.ErrorMessage
import com.adulthub.tv.ui.components.LoadingIndicator
import com.adulthub.tv.ui.components.LocalNetworkPermission
import com.adulthub.tv.ui.components.TvPrimaryButton
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.components.TvTextField
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.OledBlack
import com.adulthub.tv.ui.theme.TextSecondary

@Composable
fun ServerScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.serverState.collectAsStateWithLifecycle()
    val localNetwork = LocalNetworkPermission.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (state.isConnecting) {
            LoadingIndicator(message = stringResource(R.string.server_connecting))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth(0.65f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                item {
                    Text(
                        text = stringResource(R.string.server_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = NeonPink,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.server_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                }

                item {
                    TvTextField(
                        value = state.serverUrl,
                        onValueChange = viewModel::onServerUrlChanged,
                        label = stringResource(R.string.server_url_label),
                        hint = stringResource(R.string.server_url_hint),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                localNetwork.ensureGranted(state.serverUrl) { viewModel.connectServer() }
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TvPrimaryButton(
                            onClick = {
                                localNetwork.ensureGranted(state.serverUrl) { viewModel.connectServer() }
                            },
                            modifier = Modifier.width(200.dp),
                        ) {
                            Text(text = stringResource(R.string.action_connect))
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (state.errorMessage != null) {
                    item {
                        ErrorMessage(
                            message = state.errorMessage ?: "",
                            onRetry = {
                                if (state.sessionRestoreFailed) {
                                    viewModel.retrySavedSession()
                                } else {
                                    localNetwork.ensureGranted(state.serverUrl) { viewModel.connectServer() }
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                if (state.discoveredServers.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.server_discovered_header),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                    }

                    items(state.discoveredServers, key = { it.url }) { server ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = server.name.ifEmpty { "Jellyfin Server" },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = server.url,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            TvSecondaryButton(
                                onClick = {
                                    localNetwork.ensureGranted(server.url) { viewModel.connectServer(server.url) }
                                },
                            ) {
                                Text(text = stringResource(R.string.action_connect))
                            }
                        }
                    }
                }
            }
        }

        state.pendingHttpConsentUrl?.let { url ->
            CleartextHttpConsentDialog(
                url = url,
                onApprove = viewModel::confirmHttpConsent,
                onDismiss = viewModel::dismissHttpConsent,
            )
        }
    }
}
