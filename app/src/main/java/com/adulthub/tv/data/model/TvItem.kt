package com.adulthub.tv.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class TvPersonRole(
    val id: String,
    val name: String,
    val role: String? = null,
    val type: String? = null,
    val primaryImageTag: String? = null,
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
) {
    val isTelegram: Boolean
        get() = !providerVideoId.isNullOrBlank() || id.startsWith("telegram:")

    val isHd: Boolean
        get() = (width ?: 0) >= 1280 || (height ?: 0) >= 720 || (fileSizeBytes ?: 0L) >= 300 * 1024 * 1024L

    val runtimeMinutes: Int?
        get() = runtimeTicks?.let { (it / 10_000_000 / 60).toInt() }

    val progressFraction: Float
        get() = when {
            playedPercentage != null -> (playedPercentage / 100.0).toFloat().coerceIn(0f, 1f)
            runtimeTicks != null && runtimeTicks > 0L -> (playbackPositionTicks.toDouble() / runtimeTicks.toDouble()).toFloat().coerceIn(0f, 1f)
            else -> 0f
        }

    val formattedDuration: String
        get() {
            val minutes = runtimeMinutes ?: return ""
            val hours = minutes / 60
            val remMinutes = minutes % 60
            return if (hours > 0) {
                "${hours}h ${remMinutes}m"
            } else {
                "${minutes}m"
            }
        }
}
