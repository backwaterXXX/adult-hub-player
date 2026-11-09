package com.adulthub.tv.ui.library

import com.adulthub.tv.data.model.ChannelSortMode
import com.adulthub.tv.data.model.DurationFilter
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.resolver.VideoDto
import java.util.UUID

internal fun applyAllItemsFilters(
    items: List<TvItem>,
    durationFilter: DurationFilter,
    hdOnly: Boolean,
    excludedTags: Set<String>
): List<TvItem> {
    return items.filter { item ->
        if (excludedTags.isNotEmpty()) {
            val hasExcludedGenre = item.genres.any { g -> excludedTags.any { it.equals(g, ignoreCase = true) } }
            val hasExcludedTag = item.tags.any { t -> excludedTags.any { it.equals(t, ignoreCase = true) } }
            if (hasExcludedGenre || hasExcludedTag) return@filter false
        }
        if (hdOnly && !item.isHd) {
            return@filter false
        }
        val durationMinutes = item.runtimeMinutes ?: (item.runtimeTicks?.div(10_000_000L * 60L)?.toInt()) ?: 0
        when (durationFilter) {
            DurationFilter.ALL -> true
            DurationFilter.SHORT -> durationMinutes in 1..14
            DurationFilter.MEDIUM -> durationMinutes in 15..40
            DurationFilter.LONG -> durationMinutes > 40
        }
    }
}

internal fun applyChannelFilters(
    videos: List<VideoDto>,
    durationFilter: DurationFilter,
    excludedTags: Set<String>,
    mode: ChannelSortMode
): List<VideoDto> {
    val filtered = videos.filter { video ->
        if (excludedTags.isNotEmpty()) {
            val hasExcludedCat = video.categories.any { cat -> excludedTags.any { it.equals(cat, ignoreCase = true) } }
            val hasExcludedTitle = excludedTags.any { video.title.contains(it, ignoreCase = true) }
            val hasExcludedPerformer = video.performers.any { p -> excludedTags.any { it.equals(p, ignoreCase = true) } }
            if (hasExcludedCat || hasExcludedTitle || hasExcludedPerformer) return@filter false
        }
        val minutes = (video.duration / 60.0).toInt()
        when (durationFilter) {
            DurationFilter.ALL -> true
            DurationFilter.SHORT -> minutes in 1..14
            DurationFilter.MEDIUM -> minutes in 15..40
            DurationFilter.LONG -> minutes > 40
        }
    }
    return sortChannelVideos(filtered, mode)
}

private fun sortChannelVideos(videos: List<VideoDto>, mode: ChannelSortMode): List<VideoDto> = when (mode) {
    ChannelSortMode.LATEST -> videos

    ChannelSortMode.LONGEST -> videos.sortedWith(
        compareByDescending<VideoDto> { it.duration }
            .thenByDescending { it.id }
    )

    ChannelSortMode.SHORTEST -> videos.sortedWith(
        compareBy<VideoDto> { if (it.duration <= 0.0) Double.MAX_VALUE else it.duration }
            .thenBy { it.id }
    )
}

internal fun filterActors(
    all: List<TvPerson>,
    letter: Char?,
    onlyWithPhoto: Boolean,
    query: String
): List<TvPerson> = all.filter { person ->
    val matchesLetter = letter == null || person.name.startsWith(letter, ignoreCase = true)
    val matchesPhoto = !onlyWithPhoto || !person.primaryImageTag.isNullOrBlank()
    val matchesSearch = query.isBlank() || person.name.contains(query, ignoreCase = true)
    matchesLetter && matchesPhoto && matchesSearch
}

internal fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
