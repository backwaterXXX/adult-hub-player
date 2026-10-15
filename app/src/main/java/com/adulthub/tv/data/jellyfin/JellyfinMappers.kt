package com.adulthub.tv.data.jellyfin

import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPersonRole
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

fun BaseItemDto.toTvItem(): TvItem {
    return TvItem(
        id = id.toString(),
        title = name ?: "Untitled",
        overview = overview,
        year = productionYear,
        runtimeTicks = runTimeTicks,
        genres = genres?.toList() ?: emptyList(),
        tags = tags?.toList() ?: emptyList(),
        studios = studios?.mapNotNull { it.name } ?: emptyList(),
        people = people?.map { person ->
            TvPersonRole(
                id = person.id.toString(),
                name = person.name ?: "",
                role = person.role,
                type = person.type?.name,
                primaryImageTag = person.primaryImageTag,
            )
        } ?: emptyList(),
        primaryImageTag = imageTags?.get(ImageType.PRIMARY),
        backdropImageTag = backdropImageTags?.firstOrNull(),
        thumbImageTag = imageTags?.get(ImageType.THUMB),
        isFavorite = userData?.isFavorite == true,
        playbackPositionTicks = userData?.playbackPositionTicks ?: 0L,
        playedPercentage = userData?.playedPercentage,
        isPlayed = userData?.played == true,
        providerVideoId = providerIds?.get("telegram"),
        mediaSourceId = mediaSources?.firstOrNull()?.id,
        container = container,
        fileSizeBytes = mediaSources?.firstOrNull()?.size,
        width = width,
        height = height,
    )
}
