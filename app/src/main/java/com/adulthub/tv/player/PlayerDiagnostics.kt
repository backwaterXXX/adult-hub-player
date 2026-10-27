package com.adulthub.tv.player

/**
 * Names the decoder actually in use, so a software fallback is visible in the overlay
 * instead of being mistaken for a playback failure.
 */
internal fun decoderMode(videoDecoderName: String?, audioDecoderName: String?): String {
    val videoSoftware = isSoftwareDecoder(videoDecoderName)
    val audioSoftware = isSoftwareDecoder(audioDecoderName)
    if (!videoSoftware && !audioSoftware) return "Hardware (HW)"
    val parts = listOfNotNull(
        if (videoSoftware) "V:SW" else null,
        if (audioSoftware) "A:SW" else null,
    ).joinToString(", ")
    return "Fallback ($parts)"
}

private fun isSoftwareDecoder(decoderName: String?): Boolean {
    val normalized = decoderName?.lowercase() ?: return false
    return normalized.startsWith("c2.android.") ||
        normalized.startsWith("omx.google.") ||
        normalized.contains("ffmpeg") ||
        normalized.contains("sw")
}

/** How the stream reaches the player: the original file, a Telegram proxy or a transcode. */
internal fun playMethod(streamUrl: String): String = when {
    streamUrl.contains("/stream?mode=proxy", ignoreCase = true) -> "Direct Stream (Telegram Proxy)"
    streamUrl.contains("m3u8", ignoreCase = true) ||
        streamUrl.contains("transcoding", ignoreCase = true) -> "Transcode"
    else -> "Direct Play"
}
