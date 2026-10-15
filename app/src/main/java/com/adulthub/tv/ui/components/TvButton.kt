package com.adulthub.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import com.adulthub.tv.ui.theme.CardSurface
import com.adulthub.tv.ui.theme.CardSurfaceFocused
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.TextPrimary

@Composable
fun TvPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1.05f),
        colors = ButtonDefaults.colors(
            containerColor = CardSurface,
            contentColor = TextPrimary,
            focusedContainerColor = NeonPink,
            focusedContentColor = Color.White,
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Color.White)),
        ),
        content = content,
    )
}

@Composable
fun TvSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        scale = OutlinedButtonDefaults.scale(scale = 1f, focusedScale = 1.05f),
        colors = OutlinedButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = TextPrimary,
            focusedContainerColor = CardSurfaceFocused,
            focusedContentColor = NeonPink,
        ),
        border = OutlinedButtonDefaults.border(
            border = Border(border = BorderStroke(1.dp, CardSurfaceFocused)),
            focusedBorder = Border(border = BorderStroke(2.dp, NeonPink)),
        ),
        content = content,
    )
}
