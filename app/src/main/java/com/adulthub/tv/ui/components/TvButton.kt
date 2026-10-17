package com.adulthub.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults as TouchButtonDefaults
import androidx.compose.material3.ButtonDefaults as TouchOutlinedButtonDefaults
import androidx.compose.material3.Button as TouchButton
import androidx.compose.material3.OutlinedButton as TouchOutlinedButton
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
    if (LocalTouchUi.current) {
        TouchButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = RoundedCornerShape(9.dp),
            colors = TouchButtonDefaults.buttonColors(
                containerColor = NeonPink,
                contentColor = Color.White,
                disabledContainerColor = CardSurfaceFocused,
                disabledContentColor = TextPrimary.copy(alpha = 0.55f),
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            content = content,
        )
        return
    }
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
    if (LocalTouchUi.current) {
        TouchOutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = RoundedCornerShape(9.dp),
            colors = TouchOutlinedButtonDefaults.outlinedButtonColors(
                contentColor = TextPrimary,
                disabledContentColor = TextPrimary.copy(alpha = 0.55f),
            ),
            border = BorderStroke(1.dp, CardSurfaceFocused),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            content = content,
        )
        return
    }
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
