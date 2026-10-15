package com.adulthub.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.adulthub.tv.ui.theme.GenreBadge
import com.adulthub.tv.ui.theme.TextPrimary

@Composable
fun GenreBadge(
    genre: String,
    modifier: Modifier = Modifier,
) {
    if (genre.isBlank()) return

    Text(
        text = genre.uppercase(),
        color = TextPrimary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(GenreBadge)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
