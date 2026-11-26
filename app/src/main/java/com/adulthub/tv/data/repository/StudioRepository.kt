package com.adulthub.tv.data.repository

import com.adulthub.tv.data.api.AdultHubClientManager
import com.adulthub.tv.data.api.toTvItem
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvStudio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class StudioRepository(
    private val clientManager: AdultHubClientManager,
    private val settingsDataStore: SettingsDataStore
) {
    open suspend fun getStudios(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 50,
        includeMovieCounts: Boolean = false
    ): Result<List<TvStudio>> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val response = api.getStudios(
                offset = startIndex,
                limit = limit.coerceAtMost(100)
            )
            val studios = response.items.map { item ->
                TvStudio(
                    id = item.name,
                    name = item.name,
                    movieCount = item.count
                )
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
        limit: Int = 50
    ): Result<TvItemsPage> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val baseUrl = clientManager.currentServerUrl ?: ""
            val response = api.getVideos(
                studio = studioName,
                offset = startIndex,
                limit = limit.coerceAtMost(100),
                sort = "latest",
                direction = "desc"
            )
            val page = TvItemsPage(
                items = response.items.map { it.toTvItem(baseUrl) },
                totalRecordCount = response.totalCount,
                startIndex = response.offset
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
        limit: Int = 50
    ): Result<List<TvItem>> = getStudioMoviesPage(
        studioName = studioName,
        parentId = parentId,
        startIndex = startIndex,
        limit = limit
    ).map { it.items }
}
