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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.R
import com.adulthub.tv.ui.components.ErrorMessage
import com.adulthub.tv.ui.components.LoadingIndicator
import com.adulthub.tv.ui.components.TvPrimaryButton
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.components.TvTextField
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.OledBlack
import com.adulthub.tv.ui.theme.TextSecondary

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onBackToServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.loginState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (state.isSigningIn) {
            LoadingIndicator(message = stringResource(R.string.login_signing_in))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(0.55f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                item {
                    Text(
                        text = stringResource(R.string.login_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = NeonPink,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.serverUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    TvTextField(
                        value = state.username,
                        onValueChange = viewModel::onUsernameChanged,
                        label = stringResource(R.string.login_username_label),
                        hint = stringResource(R.string.login_username_hint),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    TvTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChanged,
                        label = stringResource(R.string.login_password_label),
                        hint = stringResource(R.string.login_password_hint),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.signIn() },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TvPrimaryButton(
                            onClick = { viewModel.signIn() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(R.string.action_sign_in))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        TvSecondaryButton(
                            onClick = { viewModel.startQuickConnect() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(R.string.action_quick_connect))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    TvSecondaryButton(
                        onClick = onBackToServer,
                        modifier = Modifier.fillMaxWidth(0.5f),
                    ) {
                        Text(text = stringResource(R.string.action_back))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (state.errorMessage != null) {
                    item {
                        ErrorMessage(
                            message = state.errorMessage ?: "",
                            onRetry = { viewModel.signIn() },
                        )
                    }
                }
            }
        }
    }
}
