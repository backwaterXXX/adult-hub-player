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
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID

open class StudioRepository(
    private val clientManager: JellyfinClientManager,
    private val settingsDataStore: SettingsDataStore,
) {
    open suspend fun getStudios(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 50,
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
            val countSemaphore = Semaphore(4)
            val studios = coroutineScope {
                items.map { item ->
                    async {
                        val name = item.name.orEmpty()
                        val movieCount = try {
                            countSemaphore.withPermit {
                                getStudioMoviesPage(name, targetParentId, startIndex = 0, limit = 1)
                                    .getOrThrow().totalRecordCount
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            item.childCount ?: 0
                        }
                        TvStudio(
                            id = item.id.toString(),
                            name = name,
                            primaryImageTag = item.imageTags?.get(ImageType.PRIMARY),
                            movieCount = movieCount,
                        )
                    }
                }.awaitAll()
            }
            Result.success(studios)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
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
                parentId = parentUuid,
                studios = setOf(studioName),
                startIndex = startIndex,
                limit = limit,
                recursive = true,
                includeItemTypes = setOf(BaseItemKind.MOVIE),
                fields = setOf(ItemFields.PROVIDER_IDS),
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
}
