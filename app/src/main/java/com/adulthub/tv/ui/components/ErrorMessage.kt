package com.adulthub.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.R
import com.adulthub.tv.ui.theme.ErrorRed
import com.adulthub.tv.ui.theme.TextSecondary

@Composable
fun ErrorMessage(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = ErrorRed,
            textAlign = TextAlign.Center,
        )

        if (onRetry != null || onBack != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onRetry != null) {
                    TvPrimaryButton(onClick = onRetry) {
                        Text(text = stringResource(R.string.action_retry))
                    }
                }
                if (onRetry != null && onBack != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                }
                if (onBack != null) {
                    TvSecondaryButton(onClick = onBack) {
                        Text(text = stringResource(R.string.action_back))
                    }
                }
            }
        }
    }
}
