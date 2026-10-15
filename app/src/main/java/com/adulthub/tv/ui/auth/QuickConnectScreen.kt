package com.adulthub.tv.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.R
import com.adulthub.tv.ui.components.ErrorMessage
import com.adulthub.tv.ui.components.LoadingIndicator
import com.adulthub.tv.ui.components.TvPrimaryButton
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.theme.CardSurface
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.OledBlack
import com.adulthub.tv.ui.theme.TextSecondary

@Composable
fun QuickConnectScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.quickConnectState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopQuickConnect()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.quick_connect_title),
                style = MaterialTheme.typography.headlineLarge,
                color = NeonPink,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.quick_connect_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))

            if (state.code.isNotEmpty()) {
                val formattedCode = state.code.map { it.toString() }.joinToString(" ")
                Box(
                    modifier = Modifier
                        .background(CardSurface, shape = RoundedCornerShape(12.dp))
                        .border(2.dp, NeonPink, shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 32.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = formattedCode,
                        color = Color.White,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 8.sp,
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (state.isPolling) {
                LoadingIndicator(message = stringResource(R.string.quick_connect_status_pending))
                Spacer(modifier = Modifier.height(24.dp))
            } else if (state.isAuthorized) {
                Text(
                    text = stringResource(R.string.quick_connect_status_connected),
                    style = MaterialTheme.typography.bodyLarge,
                    color = NeonPink,
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (state.errorMessage != null) {
                ErrorMessage(
                    message = state.errorMessage ?: "",
                    onRetry = if (state.isExpired) {
                        { viewModel.startQuickConnect() }
                    } else null,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isExpired) {
                    TvPrimaryButton(onClick = { viewModel.startQuickConnect() }) {
                        Text(text = stringResource(R.string.quick_connect_get_new_code))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }
                TvSecondaryButton(onClick = onBack) {
                    Text(text = stringResource(R.string.action_back))
                }
            }
        }
    }
}
