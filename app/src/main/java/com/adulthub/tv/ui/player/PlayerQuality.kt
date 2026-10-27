package com.adulthub.tv.ui.player

import com.adulthub.tv.data.resolver.ORIGINAL_VARIANT_ID
import com.adulthub.tv.data.resolver.VideoVariant
import com.adulthub.tv.data.resolver.VideoVariantsResult
import com.adulthub.tv.data.resolver.buildQualityLabels

/** One selectable entry in the player's quality menu. */
data class QualityOption(
    val variantId: String,
    val label: String,
    val isSelected: Boolean,
)

/**
 * Quality state of the player screen.
 *
 * An empty [options] list means no selector is shown: the resolver is old, the video
 * has no alternatives, or the alternatives are still unknown. A fetch failure keeps
 * [qualityUnavailableMessage] set so it is never confused with "no alternatives".
 */
data class PlayerQualityState(
    val options: List<QualityOption> = emptyList(),
    val isFetching: Boolean = false,
    val isSwitching: Boolean = false,
    val qualityUnavailableMessage: String? = null,
)

fun buildQualityOptions(variants: List<VideoVariant>, selectedVariantId: String?): List<QualityOption> {
    if (variants.isEmpty()) return emptyList()
    val effectiveSelected = selectedVariantId ?: ORIGINAL_VARIANT_ID
    val labels = buildQualityLabels(variants)
    return variants.mapIndexed { index, variant ->
        QualityOption(
            variantId = variant.id,
            label = labels[index],
            isSelected = variant.id == effectiveSelected,
        )
    }
}

/** Turns a variants fetch outcome into the selectable option list (empty hides the selector). */
fun qualityOptionsFor(result: VideoVariantsResult, selectedVariantId: String?): List<QualityOption> {
    val variants = when (result) {
        is VideoVariantsResult.Available -> result.variants
        else -> emptyList()
    }
    val options = buildQualityOptions(variants, selectedVariantId)
    // A selector with only the original is noise; the requirement is an explicit no-op.
    return if (options.size > 1) options else emptyList()
}

fun variantsFor(result: VideoVariantsResult): List<VideoVariant> = when (result) {
    is VideoVariantsResult.Available -> result.variants
    else -> emptyList()
}
