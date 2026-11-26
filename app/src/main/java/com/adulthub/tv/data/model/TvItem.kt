package com.adulthub.tv.data.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.adulthub.tv.R

@Immutable
data class TvPersonRole(
    val id: String,
    val name: String,
    val role: String? = null,
    val type: String? = null,
    val primaryImageTag: String? = null
)

@Immutable
data class TvItem(
    val id: String,
    val title: String,
    val overview: String? = null,
    val year: Int? = null,
    val runtimeTicks: Long? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    /** Display label of the source channel; empty studios fall back to it. */
    val channelName: String? = null,
    val people: List<TvPersonRole> = emptyList(),
    val primaryImageTag: String? = null,
    val backdropImageTag: String? = null,
    val thumbImageTag: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val channelId: String? = null,
    val isFavorite: Boolean = false,
    val playbackPositionTicks: Long = 0L,
    val playedPercentage: Double? = null,
    val isPlayed: Boolean = false,
    val providerVideoId: String? = null,
    val mediaSourceId: String? = null,
    val container: String? = null,
    val fileSizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** Server-side quality tier; the card badge and the library filter share it. */
    val quality: String? = null
) {
    /**
     * True only for a legacy provider-only reference the catalog cannot resolve.
     * Adult Hub v1 catalog ids are themselves "telegram:<channel>:<message>:<file>",
     * so those stay playable and keep detail, genres and favourites available.
     */
    val isTelegram: Boolean
        get() = id.isNotBlank() && !id.startsWith("telegram:") && !providerVideoId.isNullOrBlank()

    val runtimeMinutes: Int?
        get() = runtimeTicks?.let { (it / 10_000_000 / 60).toInt() }

    /**
     * Badge for the poster: only a real resolution earns "4K", because the tier
     * alone can come from the bitrate. Low tier stays unlabelled so most cards
     * do not carry noise.
     */
    val qualityBadge: QualityBadge?
        get() = when {
            (height ?: 0) >= UHD_MIN_HEIGHT -> QualityBadge.UHD
            quality.equals("high", ignoreCase = true) -> QualityBadge.HQ
            quality.equals("medium", ignoreCase = true) -> QualityBadge.HD
            else -> null
        }

    val progressFraction: Float
        get() = when {
            playedPercentage != null -> (playedPercentage / 100.0).toFloat().coerceIn(0f, 1f)
            runtimeTicks != null && runtimeTicks > 0L -> (playbackPositionTicks.toDouble() / runtimeTicks.toDouble()).toFloat().coerceIn(0f, 1f)
            else -> 0f
        }
}

/** Poster badge for the known quality of a video. */
enum class QualityBadge(@StringRes val labelRes: Int) {
    UHD(R.string.quality_badge_4k),
    HQ(R.string.filter_quality_hq),
    HD(R.string.filter_quality_hd)
}

/** 4K starts at 2000 lines; the catalogue's `high` tier may be bitrate-only. */
private const val UHD_MIN_HEIGHT = 2000
