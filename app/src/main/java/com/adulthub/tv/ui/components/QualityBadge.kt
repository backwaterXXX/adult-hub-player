package com.adulthub.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adulthub.tv.data.model.QualityBadge
import com.adulthub.tv.ui.theme.TextPrimary

/** Marks the known quality of a poster: the same tier the library filter uses. */
@Composable
fun QualityBadge(
    badge: QualityBadge,
    modifier: Modifier = Modifier
) {
    Text(
        text = stringResource(badge.labelRes),
        color = TextPrimary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
