package com.adulthub.tv.data.repository

import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.jellyfin.toTvItem
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvStudio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.studiosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

open class StudioRepository(
    private val clientManager: JellyfinClientManager,
    private val settingsDataStore: SettingsDataStore,
) {
    open suspend fun getStudios(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 50,
        includeMovieCounts: Boolean = false,
    ): Result<List<TvStudio>> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val targetParentId = parentId ?: settingsDataStore.getSelectedLibraryId()
            val parentUuid = targetParentId?.let { UUID.fromString(it) }

            val response = api.studiosApi.getStudios(
                parentId = parentUuid,
                startIndex = startIndex,
                limit = limit,
            )
            val items = response.content.items
            val movieCounts = if (includeMovieCounts) fetchMovieCounts(items, targetParentId) else emptyMap()
            val studios = items.map { item ->
                val name = item.name.orEmpty()
                TvStudio(
                    id = item.id.toString(),
                    name = name,
                    primaryImageTag = item.imageTags?.get(ImageType.PRIMARY),
                    movieCount = movieCounts[name],
                )
            }
            Result.success(studios)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads the movie count of a bounded number of studios.
     *
     * Every count is one extra request, so only the first [MAX_COUNTED_STUDIOS]
     * studios are counted. A failing count stays unknown instead of falling back
     * to a studio child count, whose semantics are not the library movie total.
     */
    private suspend fun fetchMovieCounts(
        items: List<BaseItemDto>,
        parentId: String?,
    ): Map<String, Int> {
        val countSemaphore = Semaphore(4)
        return coroutineScope {
            items.take(MAX_COUNTED_STUDIOS)
                .map { item ->
                    async {
                        val name = item.name.orEmpty()
                        val count = try {
                            countSemaphore.withPermit {
                                getStudioMoviesPage(name, parentId, startIndex = 0, limit = 1)
                                    .getOrThrow().totalRecordCount
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                        name to count
                    }
                }
                .awaitAll()
                .mapNotNull { (name, count) -> count?.let { name to it } }
                .toMap()
        }
    }

    open suspend fun getStudioMoviesPage(
        studioName: String,
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<TvItemsPage> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val targetParentId = parentId ?: settingsDataStore.getSelectedLibraryId()
            val parentUuid = targetParentId?.let { UUID.fromString(it) }

            val response = api.itemsApi.getItems(
                GetItemsRequest(
                    parentId = parentUuid,
                    studios = setOf(studioName),
                    startIndex = startIndex,
                    limit = limit,
                    recursive = true,
                    includeItemTypes = setOf(BaseItemKind.MOVIE),
                    fields = setOf(ItemFields.PROVIDER_IDS),
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    enableTotalRecordCount = true,
                ),
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

    open suspend fun getStudioMovies(
        studioName: String,
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<List<TvItem>> = getStudioMoviesPage(
        studioName = studioName,
        parentId = parentId,
        startIndex = startIndex,
        limit = limit,
    ).map { it.items }

    private companion object {
        /** Upper bound for the per-studio count requests of a single list load. */
        const val MAX_COUNTED_STUDIOS = 24
    }
}
