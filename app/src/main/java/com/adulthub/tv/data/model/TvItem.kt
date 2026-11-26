package com.adulthub.tv.data.model

import androidx.compose.runtime.Immutable

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
    val height: Int? = null
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

    val progressFraction: Float
        get() = when {
            playedPercentage != null -> (playedPercentage / 100.0).toFloat().coerceIn(0f, 1f)
            runtimeTicks != null && runtimeTicks > 0L -> (playbackPositionTicks.toDouble() / runtimeTicks.toDouble()).toFloat().coerceIn(0f, 1f)
            else -> 0f
        }
}
