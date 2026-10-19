package com.adulthub.tv.data.repository

import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.jellyfin.toTvItem
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.TvCategory
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.local.TelegramProgressStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID

data class TvLibrary(
    val id: String,
    val name: String,
    val collectionType: String? = null,
)

data class TvItemsPage(
    val items: List<TvItem>,
    val totalRecordCount: Int,
    val startIndex: Int,
)

internal fun itemSortBy(sortBy: String): ItemSortBy = when (sortBy.lowercase()) {
    "sortname", "name" -> ItemSortBy.SORT_NAME
    "communityrating" -> ItemSortBy.COMMUNITY_RATING
    "datecreated", "latest" -> ItemSortBy.DATE_CREATED
    "random" -> ItemSortBy.RANDOM
    "runtime" -> ItemSortBy.RUNTIME
    else -> ItemSortBy.DATE_CREATED
}

open class LibraryRepository(
    private val clientManager: JellyfinClientManager,
    private val settingsDataStore: SettingsDataStore,
    private val telegramProgressStore: TelegramProgressStore? = null,
) {
    open suspend fun getLibraries(): Result<List<TvLibrary>> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val response = api.userViewsApi.getUserViews()
            val libraries = response.content.items.map { item ->
                TvLibrary(
                    id = item.id.toString(),
                    name = item.name.orEmpty(),
                    collectionType = item.collectionType?.toString(),
                )
            }
            Result.success(libraries)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getItems(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 50,
        sortBy: String = "DateCreated",
        sortOrder: SortOrder = SortOrder.DESCENDING,
        filters: Set<ItemFilter> = emptySet(),
        searchTerm: String? = null,
        genreIds: List<UUID> = emptyList(),
        studioIds: List<UUID> = emptyList(),
        personIds: List<UUID> = emptyList(),
    ): Result<TvItemsPage> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val targetParentId = parentId ?: settingsDataStore.getSelectedLibraryId()
            val parentUuid = targetParentId?.let { UUID.fromString(it) }

            val sortByItem = itemSortBy(sortBy)

            val response = api.itemsApi.getItems(
                parentId = parentUuid,
                startIndex = startIndex,
                limit = limit,
                recursive = true,
                includeItemTypes = setOf(BaseItemKind.MOVIE),
                fields = setOf(ItemFields.PROVIDER_IDS),
                sortBy = setOf(sortByItem),
                sortOrder = setOf(sortOrder),
                filters = filters,
                searchTerm = searchTerm,
                genreIds = genreIds,
                studioIds = studioIds,
                personIds = personIds,
            )

            val page = TvItemsPage(
                items = response.content.items.map { it.toTvItem() },
                totalRecordCount = response.content.totalRecordCount,
                startIndex = response.content.startIndex ?: startIndex,
            )
            Result.success(page)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getLatestItems(
        parentId: String? = null,
        limit: Int = 20,
    ): Result<List<TvItem>> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val targetParentId = parentId ?: settingsDataStore.getSelectedLibraryId()
            val parentUuid = targetParentId?.let { UUID.fromString(it) }

            val response = api.userLibraryApi.getLatestMedia(
                parentId = parentUuid,
                limit = limit,
                fields = setOf(ItemFields.PROVIDER_IDS),
            )
            val items = response.content.map { it.toTvItem() }
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getResumeItems(limit: Int = 12): Result<List<TvItem>> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val response = api.itemsApi.getResumeItems(
                limit = limit,
                mediaTypes = setOf(MediaType.VIDEO),
                fields = setOf(ItemFields.PROVIDER_IDS),
            )
            val items = response.content.items.map { it.toTvItem() }
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getRandomItem(
        parentId: String? = null,
        excludeItemId: String? = null,
    ): Result<TvItem?> = getRandomItemFiltered(parentId, excludeItemId)

    open suspend fun getRandomItemFiltered(
        parentId: String? = null,
        excludeItemId: String? = null,
        filters: Set<ItemFilter> = emptySet(),
        genreIds: List<UUID> = emptyList(),
        studioIds: List<UUID> = emptyList(),
        personIds: List<UUID> = emptyList(),
        genres: List<String> = emptyList(),
        studios: List<String> = emptyList(),
    ): Result<TvItem?> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val targetParentId = parentId ?: settingsDataStore.getSelectedLibraryId()
            val parentUuid = targetParentId?.let { UUID.fromString(it) }

            val response = api.itemsApi.getItems(
                parentId = parentUuid,
                limit = 5,
                recursive = true,
                includeItemTypes = setOf(BaseItemKind.MOVIE),
                fields = setOf(ItemFields.PROVIDER_IDS),
                sortBy = setOf(ItemSortBy.RANDOM),
                filters = filters,
                genreIds = genreIds,
                studioIds = studioIds,
                personIds = personIds,
                genres = genres,
                studios = studios,
            )
            val excludeUuid = excludeItemId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val item = response.content.items
                .firstOrNull { excludeUuid == null || it.id != excludeUuid }
                ?.toTvItem()

            Result.success(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getRandomItems(
        parentId: String? = null,
        limit: Int = 20,
        filters: Set<ItemFilter> = emptySet(),
    ): Result<List<TvItem>> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val targetParentId = parentId ?: settingsDataStore.getSelectedLibraryId()
            val parentUuid = targetParentId?.let { UUID.fromString(it) }

            val response = api.itemsApi.getItems(
                parentId = parentUuid,
                limit = limit,
                recursive = true,
                includeItemTypes = setOf(BaseItemKind.MOVIE),
                fields = setOf(ItemFields.PROVIDER_IDS),
                sortBy = setOf(ItemSortBy.RANDOM),
                filters = filters,
            )
            val items = response.content.items.map { it.toTvItem() }
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getItemDetails(itemId: String): Result<TvItem> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val uuid = UUID.fromString(itemId)
            val response = api.userLibraryApi.getItem(itemId = uuid)
            Result.success(response.content.toTvItem())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getSimilarItems(itemId: String, limit: Int = 12): Result<List<TvItem>> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val uuid = UUID.fromString(itemId)
            val response = api.libraryApi.getSimilarItems(
                itemId = uuid,
                limit = limit,
                fields = setOf(ItemFields.PROVIDER_IDS),
            )
            Result.success(response.content.items.map { it.toTvItem() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun toggleFavorite(itemId: String, markFavorite: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val uuid = UUID.fromString(itemId)
            if (markFavorite) {
                api.userLibraryApi.markFavoriteItem(itemId = uuid)
            } else {
                api.userLibraryApi.unmarkFavoriteItem(itemId = uuid)
            }
            Result.success(markFavorite)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun markAsPlayed(itemId: String, isPlayed: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val uuid = runCatching { UUID.fromString(itemId) }.getOrNull()
            if (uuid != null) {
                val api = clientManager.getApi()
                val userId = clientManager.currentUserId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (isPlayed) {
                    api.playStateApi.markPlayedItem(itemId = uuid, userId = userId)
                } else {
                    api.playStateApi.markUnplayedItem(itemId = uuid, userId = userId)
                }
            } else {
                telegramProgressStore?.markPlayed(
                    providerVideoId = itemId,
                    isPlayed = isPlayed,
                )
            }
            Result.success(isPlayed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getGenres(
        parentId: String? = null,
        limit: Int = 200,
    ): Result<List<TvCategory>> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val targetParentId = parentId ?: settingsDataStore.getSelectedLibraryId()
            val parentUuid = targetParentId?.let { UUID.fromString(it) }

            val response = api.genresApi.getGenres(
                parentId = parentUuid,
                limit = limit,
                sortBy = setOf(ItemSortBy.SORT_NAME),
                sortOrder = setOf(SortOrder.ASCENDING),
            )
            val genres = response.content.items.map { item ->
                TvCategory(
                    id = item.id.toString(),
                    name = item.name.orEmpty(),
                    primaryImageTag = item.imageTags?.get(ImageType.PRIMARY),
                    movieCount = item.childCount ?: 0,
                )
            }
            Result.success(genres)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getGenreMoviesPage(
        genreName: String,
        genreId: String? = null,
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 60,
    ): Result<TvItemsPage> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val targetParentId = parentId ?: settingsDataStore.getSelectedLibraryId()
            val parentUuid = targetParentId?.let { UUID.fromString(it) }
            val genreUuid = genreId?.let { runCatching { UUID.fromString(it) }.getOrNull() }

            val response = api.itemsApi.getItems(
                parentId = parentUuid,
                startIndex = startIndex,
                limit = limit,
                recursive = true,
                includeItemTypes = setOf(BaseItemKind.MOVIE),
                fields = setOf(ItemFields.PROVIDER_IDS),
                genreIds = if (genreUuid != null) listOf(genreUuid) else emptyList(),
                genres = if (genreUuid == null) listOf(genreName) else emptyList(),
                sortBy = setOf(ItemSortBy.DATE_CREATED),
                sortOrder = setOf(SortOrder.DESCENDING),
            )
            val page = TvItemsPage(
                items = response.content.items.map { it.toTvItem() },
                totalRecordCount = response.content.totalRecordCount,
                startIndex = response.content.startIndex ?: startIndex,
            )
            Result.success(page)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getGenreMovies(
        genreName: String,
        genreId: String? = null,
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 60,
    ): Result<List<TvItem>> = getGenreMoviesPage(
        genreName = genreName,
        genreId = genreId,
        parentId = parentId,
        startIndex = startIndex,
        limit = limit,
    ).map { it.items }
}
