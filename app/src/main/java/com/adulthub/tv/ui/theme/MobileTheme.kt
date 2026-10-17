package com.adulthub.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AdultHubMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NeonPink,
            onPrimary = Color.White,
            secondary = Color(0xFFFF80AB),
            onSecondary = OledBlack,
            background = OledBlack,
            onBackground = Color.White,
            surface = SurfaceDark,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF292932),
            onSurfaceVariant = Color(0xFFE2E0E8),
        ),
        content = content,
    )
}
