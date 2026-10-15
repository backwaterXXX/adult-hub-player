package com.adulthub.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.R
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.SurfaceDark
import com.adulthub.tv.ui.theme.TextSecondary

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = stringResource(R.string.action_confirm_clear),
    cancelText: String = stringResource(R.string.action_cancel),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        cancelFocusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .widthIn(max = 640.dp)
                .background(SurfaceDark, RoundedCornerShape(18.dp))
                .padding(32.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = NeonPink,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            Spacer(Modifier.height(26.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TvSecondaryButton(
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(cancelFocusRequester),
                ) {
                    Text(cancelText)
                }
                Spacer(Modifier.width(14.dp))
                TvPrimaryButton(onClick = onConfirm) {
                    Text(confirmText)
                }
            }
        }
    }
}
