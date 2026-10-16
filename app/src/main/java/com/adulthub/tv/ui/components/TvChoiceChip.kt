package com.adulthub.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Text
import com.adulthub.tv.ui.theme.CardSurfaceFocused
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.TextPrimary

@Composable
fun TvChoiceChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    fontSize: TextUnit = TextUnit.Unspecified,
    cornerRadius: Dp = 10.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    OutlinedButton(
        onClick = onClick,
        interactionSource = interactionSource,
        scale = OutlinedButtonDefaults.scale(scale = 1f, focusedScale = 1.04f),
        colors = OutlinedButtonDefaults.colors(
            containerColor = if (isSelected) NeonPink.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = CardSurfaceFocused,
            contentColor = if (isSelected) NeonPink else TextPrimary,
            focusedContentColor = androidx.compose.ui.graphics.Color.White,
        ),
        border = OutlinedButtonDefaults.border(
            border = Border(
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) NeonPink else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f),
                ),
            ),
            focusedBorder = Border(border = BorderStroke(2.dp, NeonPink)),
        ),
        shape = OutlinedButtonDefaults.shape(RoundedCornerShape(cornerRadius)),
        modifier = modifier.height(height).semantics { selected = isSelected },
    ) {
        Text(
            text = label,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
            fontSize = fontSize,
        )
    }
}
