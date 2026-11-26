package com.adulthub.tv.data.api

import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPersonRole

fun CatalogVideoItemDto.toTvItem(baseUrl: String = ""): TvItem {
    val base = baseUrl.trimEnd('/')
    val resolvedPoster = posterUrl?.trim()?.takeIf { it.isNotEmpty() }?.let {
        if (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) || base.isEmpty()) {
            it
        } else {
            "$base/${it.trimStart('/')}"
        }
    }
    val resolvedBackdrop = backdropUrl?.trim()?.takeIf { it.isNotEmpty() }?.let {
        if (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) || base.isEmpty()) {
            it
        } else {
            "$base/${it.trimStart('/')}"
        }
    }

    return TvItem(
        id = id,
        title = title.ifBlank { "Untitled" },
        overview = plot,
        year = releaseDate?.take(4)?.toIntOrNull(),
        runtimeTicks = durationSeconds.toLong() * 10_000_000L,
        genres = categories,
        tags = tags,
        studios = listOfNotNull(studio?.takeIf { it.isNotBlank() }),
        channelName = channelName?.trim()?.takeIf { it.isNotEmpty() },
        people = performers.map { p ->
            TvPersonRole(
                id = p.id,
                name = p.name,
                role = "Actor",
                type = "Actor",
                primaryImageTag = null
            )
        },
        posterUrl = resolvedPoster,
        backdropUrl = resolvedBackdrop,
        isFavorite = userState?.isFavorite ?: false,
        playbackPositionTicks = (userState?.positionSeconds ?: 0).toLong() * 10_000_000L,
        playedPercentage = userState?.playedPercentage,
        isPlayed = userState?.isPlayed ?: false,
        fileSizeBytes = fileSizeBytes,
        width = width,
        height = height,
        quality = quality
    )
}
