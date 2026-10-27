package com.adulthub.tv.data.resolver

import retrofit2.HttpException

/** The identifier the resolver uses for the untouched original file. */
const val ORIGINAL_VARIANT_ID = "original"

/** A single playable quality variant reported by the resolver. */
data class VideoVariant(
    val id: String,
    val isOriginal: Boolean,
    val width: Int?,
    val height: Int?,
    val codec: String?,
    val mimeType: String?,
    val fileSizeBytes: Long?,
    val durationSec: Double?,
    /** Reliably known upstream bitrate in bps. Null when the server does not know it. */
    val bitrate: Long?,
    /** Average bitrate derived from file size and duration, in bps. Null when not computable. */
    val estimatedAverageBitrate: Long?,
)

/** Outcome of a variants request. A missing feature never blocks playback. */
sealed interface VideoVariantsResult {
    /** Alternative qualities are selectable. Always contains the original. */
    data class Available(val variants: List<VideoVariant>) : VideoVariantsResult

    /** The server answered, but only the original exists. */
    data object OriginalOnly : VideoVariantsResult

    /** The server is still transcoding; only the original plays for now. */
    data object Processing : VideoVariantsResult

    /** The resolver does not expose variants (legacy server or unknown video). */
    data object Unsupported : VideoVariantsResult

    /** The variants could not be fetched. Distinct from [OriginalOnly]. */
    data class Failed(val error: Exception) : VideoVariantsResult
}

fun VideoVariantDto.toDomain(): VideoVariant = VideoVariant(
    id = id,
    isOriginal = isOriginal,
    width = width?.takeIf { it > 0 },
    height = height?.takeIf { it > 0 },
    codec = codec?.takeIf { it.isNotBlank() },
    mimeType = mimeType?.takeIf { it.isNotBlank() },
    fileSizeBytes = fileSize?.takeIf { it > 0 },
    durationSec = duration?.takeIf { it > 0.0 },
    bitrate = bitrate?.takeIf { it > 0 },
    estimatedAverageBitrate = estimatedAverageBitrate?.takeIf { it > 0 },
)

fun classifyVariantsResponse(dto: VideoVariantsDto): VideoVariantsResult {
    val variants = dto.variants.map { it.toDomain() }
    val alternatives = variants.filterNot { it.isOriginal }
    return when {
        dto.status == "processing" || dto.videoProcessingPending -> VideoVariantsResult.Processing
        dto.hasAlternatives && alternatives.isNotEmpty() -> VideoVariantsResult.Available(variants)
        else -> VideoVariantsResult.OriginalOnly
    }
}

/**
 * Maps a failed variants request.
 *
 * A 404/405 means the endpoint does not exist on this resolver (legacy server) or the
 * video is unknown; both are a missing feature, not a fetch error. Anything else
 * (5xx, auth, network, undecodable body) is a real failure.
 */
fun classifyVariantsError(error: Exception): VideoVariantsResult {
    if (error is HttpException) {
        return when (error.code()) {
            404, 405 -> VideoVariantsResult.Unsupported
            else -> VideoVariantsResult.Failed(error)
        }
    }
    if (error is kotlinx.serialization.SerializationException) {
        return VideoVariantsResult.Unsupported
    }
    return VideoVariantsResult.Failed(error)
}

/**
 * Builds one distinct label per variant, in the given order.
 *
 * The height names the resolution; the original is always marked. When several variants
 * are selectable, codec and file size are appended so users can compare formats and weights.
 * Variants that stay identical on all three get a positional suffix, so no two entries in
 * the picker read the same.
 */
fun buildQualityLabels(variants: List<VideoVariant>): List<String> {
    val decorate = variants.size > 1
    val baseLabels = variants.map { variant ->
        val parts = mutableListOf(variant.qualityBaseLabel())
        if (decorate) {
            variant.codec?.let { parts.add(it) }
            formatVariantSize(variant.fileSizeBytes)?.let { parts.add(it) }
        }
        if (variant.isOriginal) parts.add("(Original)")
        parts.joinToString(" ").trim()
    }
    val repeated = baseLabels.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    return baseLabels.mapIndexed { index, label ->
        if (label in repeated) "$label #${index + 1}" else label
    }
}

private fun VideoVariant.qualityBaseLabel(): String = when {
    height != null -> "${height}p"
    width != null -> "${width}px"
    else -> "Unknown"
}

fun formatVariantSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0L) return null
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        else -> String.format(java.util.Locale.US, "%.0f KB", kb)
    }
}

fun formatBitrate(bps: Long?): String? {
    if (bps == null || bps <= 0L) return null
    return if (bps >= 1_000_000L) {
        String.format(java.util.Locale.US, "%.1f Mbps", bps / 1_000_000.0)
    } else {
        "${bps / 1000} kbps"
    }
}

/** Full description of the variant that is actually playing, for diagnostics. */
fun formatQualityDescriptor(variant: VideoVariant): String = listOfNotNull(
    when {
        variant.width != null && variant.height != null -> "${variant.width}×${variant.height}"
        variant.height != null -> "${variant.height}p"
        variant.width != null -> "${variant.width}px"
        else -> null
    },
    variant.codec,
    formatVariantSize(variant.fileSizeBytes),
    "Original".takeIf { variant.isOriginal },
).joinToString(" · ").ifBlank { "Unknown" }
