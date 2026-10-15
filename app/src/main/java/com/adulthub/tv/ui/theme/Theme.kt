package com.adulthub.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val DarkColorScheme = darkColorScheme(
    primary = NeonPink,
    onPrimary = Color.White,
    primaryContainer = CardSurface,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    onSecondary = Color.Black,
    secondaryContainer = CardSurfaceFocused,
    onSecondaryContainer = TextPrimary,
    background = OledBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.Black,
    border = FocusBorder,
)

@Composable
fun AdultHubTvTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content,
    )
}
