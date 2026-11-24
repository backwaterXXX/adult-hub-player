package com.adulthub.tv.data.repository

import com.adulthub.tv.data.api.AdultHubClientManager
import com.adulthub.tv.data.api.WatchProgressUpdateDto
import com.adulthub.tv.data.api.toTvItem
import com.adulthub.tv.data.local.AccountKeyProvider
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.local.TelegramProgressStore
import com.adulthub.tv.data.model.ItemFilter
import com.adulthub.tv.data.model.SortOrder
import com.adulthub.tv.data.model.TvCategory
import com.adulthub.tv.data.model.TvItem
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

data class TvLibrary(
    val id: String,
    val name: String,
    val collectionType: String? = null
)

data class TvItemsPage(
    val items: List<TvItem>,
    val totalRecordCount: Int,
    val startIndex: Int
)

internal fun itemSortBy(sortBy: String): String = when (sortBy.lowercase()) {
    "sortname", "name", "title" -> "title"
    "runtime", "duration" -> "duration"
    else -> "latest"
}

open class LibraryRepository(
    private val clientManager: AdultHubClientManager,
    private val settingsDataStore: SettingsDataStore,
    private val telegramProgressStore: TelegramProgressStore? = null,
    private val accountKeyProvider: AccountKeyProvider? = null,
    private val watchHistoryStore: com.adulthub.tv.data.local.WatchHistoryStore? = null
) {
    open suspend fun getLibraries(): Result<List<TvLibrary>> = withContext(Dispatchers.IO) {
        try {
            // Adult Hub operates on a canonical catalog rather than partitioned Jellyfin libraries.
            val libraries = listOf(
                TvLibrary(
                    id = "catalog",
                    name = "Adult Hub",
                    collectionType = "movies"
                )
            )
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
        category: String? = null,
        tag: String? = null,
        studio: String? = null,
        performer: String? = null,
        performerId: String? = null,
        minDuration: Int? = null
    ): Result<TvItemsPage> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val baseUrl = clientManager.currentServerUrl ?: ""
            val sortKey = itemSortBy(sortBy)
            val direction = if (sortOrder == SortOrder.ASCENDING) "asc" else "desc"
            val isFavorite = if (filters.contains(ItemFilter.IS_FAVORITE)) true else null

            val response = api.getVideos(
                offset = startIndex,
                limit = limit.coerceAtMost(100),
                sort = sortKey,
                direction = direction,
                q = searchTerm?.takeIf { it.isNotBlank() },
                category = category,
                tag = tag,
                studio = studio,
                performer = performer,
                performerId = performerId,
                minDuration = minDuration,
                isFavorite = isFavorite
            )

            var items = response.items.map { it.toTvItem(baseUrl) }
            if (filters.contains(ItemFilter.IS_UNPLAYED)) {
                items = items.filter { !it.isPlayed }
            } else if (filters.contains(ItemFilter.IS_PLAYED)) {
                items = items.filter { it.isPlayed }
            }

            val page = TvItemsPage(
                items = items,
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

    open suspend fun getLatestItems(
        parentId: String? = null,
        limit: Int = 20
    ): Result<List<TvItem>> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val baseUrl = clientManager.currentServerUrl ?: ""
            val response = api.getVideos(
                offset = 0,
                limit = limit.coerceAtMost(100),
                sort = "latest",
                direction = "desc"
            )
            val items = response.items.map { it.toTvItem(baseUrl) }
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getResumeItems(limit: Int = 12): Result<List<TvItem>> = withContext(Dispatchers.IO) {
        try {
            val records = watchHistoryStore?.history?.firstOrNull().orEmpty()
            val candidateRecords = records.filter { record ->
                !record.isPlayed &&
                    record.progressFraction > 0.01f &&
                    record.progressFraction < 0.90f &&
                    !record.itemId.startsWith("telegram:") &&
                    !record.itemId.contains(":") &&
                    !record.itemId.startsWith("-100")
            }.take(limit)

            val api = clientManager.getApiOrNull()
            val items = candidateRecords.map { record ->
                var item = record.toTvItem()
                if (api != null) {
                    val serverProgress = runCatching { api.getWatchProgress(record.itemId) }.getOrNull()
                    if (serverProgress != null) {
                        item = item.copy(
                            playbackPositionTicks = serverProgress.positionSeconds * 10_000_000L,
                            isPlayed = serverProgress.isPlayed,
                            playedPercentage = serverProgress.playedPercentage
                        )
                    }
                }
                item
            }.filter { !it.isPlayed && it.playbackPositionTicks > 15_000_0000L }

            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getRandomItem(
        parentId: String? = null,
        excludeItemId: String? = null
    ): Result<TvItem?> = getRandomItemFiltered(parentId, excludeItemId)

    open suspend fun getRandomItemFiltered(
        parentId: String? = null,
        excludeItemId: String? = null,
        filters: Set<ItemFilter> = emptySet(),
        genreIds: List<UUID> = emptyList(),
        studioIds: List<UUID> = emptyList(),
        personIds: List<UUID> = emptyList(),
        genres: List<String> = emptyList(),
        studios: List<String> = emptyList()
    ): Result<TvItem?> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val baseUrl = clientManager.currentServerUrl ?: ""
            val isFavorite = if (filters.contains(ItemFilter.IS_FAVORITE)) true else null
            val category = genres.firstOrNull()
            val studio = studios.firstOrNull()

            val initial = api.getVideos(
                offset = 0,
                limit = 50,
                sort = "latest",
                category = category,
                studio = studio,
                isFavorite = isFavorite
            )
            val totalCount = initial.totalCount
            val pool = if (totalCount <= 50) {
                initial.items.map { it.toTvItem(baseUrl) }
            } else {
                val maxOffset = (totalCount - 50).coerceAtLeast(0)
                val randomOffset = kotlin.random.Random.nextInt(maxOffset + 1)
                val sampled = api.getVideos(
                    offset = randomOffset,
                    limit = 50,
                    sort = "latest",
                    category = category,
                    studio = studio,
                    isFavorite = isFavorite
                )
                sampled.items.map { it.toTvItem(baseUrl) }
            }

            var eligible = pool
            if (excludeItemId != null) {
                eligible = eligible.filter { it.id != excludeItemId }
            }
            if (filters.contains(ItemFilter.IS_UNPLAYED)) {
                eligible = eligible.filter { !it.isPlayed }
            } else if (filters.contains(ItemFilter.IS_PLAYED)) {
                eligible = eligible.filter { it.isPlayed }
            }

            if (eligible.isEmpty() && totalCount > 50) {
                val fallback = initial.items.map { it.toTvItem(baseUrl) }
                    .filter { it.id != excludeItemId }
                eligible = if (filters.contains(ItemFilter.IS_UNPLAYED)) {
                    fallback.filter { !it.isPlayed }
                } else fallback
            }

            Result.success(eligible.shuffled().firstOrNull())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getRandomItems(
        parentId: String? = null,
        limit: Int = 20,
        filters: Set<ItemFilter> = emptySet()
    ): Result<List<TvItem>> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val baseUrl = clientManager.currentServerUrl ?: ""
            val isFavorite = if (filters.contains(ItemFilter.IS_FAVORITE)) true else null

            val initial = api.getVideos(
                offset = 0,
                limit = 50,
                sort = "latest",
                isFavorite = isFavorite
            )
            val totalCount = initial.totalCount
            val pool = if (totalCount <= 50) {
                initial.items.map { it.toTvItem(baseUrl) }
            } else {
                val maxOffset = (totalCount - 50).coerceAtLeast(0)
                val randomOffset = kotlin.random.Random.nextInt(maxOffset + 1)
                val sampled = api.getVideos(
                    offset = randomOffset,
                    limit = 50,
                    sort = "latest",
                    isFavorite = isFavorite
                )
                sampled.items.map { it.toTvItem(baseUrl) }
            }

            var items = pool
            if (filters.contains(ItemFilter.IS_UNPLAYED)) {
                items = items.filter { !it.isPlayed }
            } else if (filters.contains(ItemFilter.IS_PLAYED)) {
                items = items.filter { it.isPlayed }
            }

            if (items.isEmpty() && totalCount > 50) {
                val fallback = initial.items.map { it.toTvItem(baseUrl) }
                items = if (filters.contains(ItemFilter.IS_UNPLAYED)) {
                    fallback.filter { !it.isPlayed }
                } else fallback
            }

            Result.success(items.shuffled().take(limit))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getItemDetails(itemId: String): Result<TvItem> = withContext(Dispatchers.IO) {
        try {
            if (itemId.isBlank() || itemId.startsWith("telegram:") || itemId.contains(":") || itemId.startsWith("-100")) {
                return@withContext Result.failure(
                    IllegalArgumentException("Cannot query catalog detail for provider/Telegram ID: $itemId")
                )
            }
            val api = clientManager.getApi()
            val baseUrl = clientManager.currentServerUrl ?: ""
            val response = api.getVideoDetail(itemId)
            Result.success(response.toTvItem(baseUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Adult Hub backend does not currently have a dedicated /similar catalog endpoint.
     * Retains build compatibility and safe fallback to empty list without mocking backend contracts.
     */
    open suspend fun getSimilarItems(itemId: String, limit: Int = 12): Result<List<TvItem>> = withContext(Dispatchers.IO) {
        Result.success(emptyList())
    }

    open suspend fun toggleFavorite(itemId: String, markFavorite: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (itemId.isBlank() || itemId.startsWith("telegram:") || itemId.contains(":") || itemId.startsWith("-100")) {
                return@withContext Result.failure(
                    IllegalArgumentException("Cannot toggle favorite for provider/Telegram ID: $itemId")
                )
            }
            val api = clientManager.getApi()
            if (markFavorite) {
                api.putFavorite(itemId)
            } else {
                api.deleteFavorite(itemId)
            }
            Result.success(markFavorite)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun markAsPlayed(itemId: String, isPlayed: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        val accountKey = runCatching { accountKeyProvider?.currentKey() }.getOrNull()
        try {
            val api = clientManager.getApiOrNull()
            api?.putWatchProgress(
                videoId = itemId,
                body = WatchProgressUpdateDto(
                    positionSeconds = 0,
                    durationSeconds = 0,
                    isPlayed = isPlayed
                )
            )
            telegramProgressStore?.markPlayed(
                providerVideoId = itemId,
                isPlayed = isPlayed,
                accountKey = accountKey
            )
            Result.success(isPlayed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getGenres(
        parentId: String? = null,
        limit: Int = 200
    ): Result<List<TvCategory>> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val response = api.getCategories(offset = 0, limit = limit.coerceAtMost(200))
            val categories = response.items.map { item ->
                TvCategory(
                    id = item.name,
                    name = item.name,
                    movieCount = item.count
                )
            }
            Result.success(categories)
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
        limit: Int = 60
    ): Result<TvItemsPage> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val baseUrl = clientManager.currentServerUrl ?: ""
            val response = api.getVideos(
                category = genreName,
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

    open suspend fun getGenreMovies(
        genreName: String,
        genreId: String? = null,
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 60
    ): Result<List<TvItem>> = getGenreMoviesPage(
        genreName = genreName,
        genreId = genreId,
        parentId = parentId,
        startIndex = startIndex,
        limit = limit
    ).map { it.items }
}
