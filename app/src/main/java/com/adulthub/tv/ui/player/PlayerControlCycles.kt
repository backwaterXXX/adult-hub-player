package com.adulthub.tv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.media3.ui.AspectRatioFrameLayout
import com.adulthub.tv.R

/**
 * Speed and aspect ratio cycles shared by the TV and mobile control bars, so the two
 * remotes cannot drift into different step sequences.
 */

fun nextPlaybackSpeed(current: Float): Float = when {
    current < 1.15f -> 1.25f
    current < 1.4f -> 1.5f
    current < 1.75f -> 2.0f
    else -> 1.0f
}

fun nextResizeMode(current: Int): Int = when (current) {
    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

@Composable
fun resizeModeLabel(current: Int): String = when (current) {
    AspectRatioFrameLayout.RESIZE_MODE_FILL -> stringResource(R.string.player_zoom_fill)
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> stringResource(R.string.player_zoom_crop)
    else -> stringResource(R.string.player_zoom_fit)
}
