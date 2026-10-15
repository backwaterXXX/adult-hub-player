package com.adulthub.tv.ui.components

import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap

/** Detects tiny images and uniform black placeholders commonly returned for missing artwork. */
fun isPoorThumbnail(drawable: Drawable): Boolean {
    if (drawable.intrinsicWidth in 1..319 || drawable.intrinsicHeight in 1..159) return true

    val bitmap = drawable.toBitmap(width = 24, height = 24)
    if (bitmap.width <= 0 || bitmap.height <= 0) return false

    val stepX = (bitmap.width / 12).coerceAtLeast(1)
    val stepY = (bitmap.height / 12).coerceAtLeast(1)
    var count = 0
    var darkPixelCount = 0
    var luminanceSum = 0.0
    var luminanceSquaredSum = 0.0

    for (y in 0 until bitmap.height step stepY) {
        for (x in 0 until bitmap.width step stepX) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = Color.alpha(pixel) / 255.0
            val luminance = (0.2126 * Color.red(pixel) + 0.7152 * Color.green(pixel) + 0.0722 * Color.blue(pixel)) * alpha
            if (luminance < 18.0) darkPixelCount++
            luminanceSum += luminance
            luminanceSquaredSum += luminance * luminance
            count++
        }
    }

    if (count == 0) return false
    val average = luminanceSum / count
    val variance = (luminanceSquaredSum / count - average * average).coerceAtLeast(0.0)
    val nearlyAllBlack = darkPixelCount.toDouble() / count >= 0.88 && average < 55.0
    return nearlyAllBlack || (average < 32.0 && variance < 180.0)
}

/** Centralized sizing configuration for thumbnails and Low-RAM mode. */
object ThumbnailQualityConfig {
    const val DEFAULT_MOVIE_MAX_WIDTH = 480
    const val LOW_RAM_MOVIE_MAX_WIDTH = 320
    const val DEFAULT_PERSON_MAX_WIDTH = 300
    const val LOW_RAM_PERSON_MAX_WIDTH = 200

    fun movieTargetWidth(lowRamMode: Boolean): Int =
        if (lowRamMode) LOW_RAM_MOVIE_MAX_WIDTH else DEFAULT_MOVIE_MAX_WIDTH

    fun movieTargetHeight(lowRamMode: Boolean): Int =
        (movieTargetWidth(lowRamMode) * 9) / 16

    fun personTargetWidth(lowRamMode: Boolean): Int =
        if (lowRamMode) LOW_RAM_PERSON_MAX_WIDTH else DEFAULT_PERSON_MAX_WIDTH

    fun personTargetHeight(lowRamMode: Boolean): Int =
        (personTargetWidth(lowRamMode) * 3) / 2
}

