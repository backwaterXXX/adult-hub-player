package com.adulthub.tv.data.repository

import com.adulthub.tv.data.api.AdultHubApi
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

/** Below this a viewer barely started; above it there is nothing left to resume. */
private const val RESUME_MIN_FRACTION = 0.01f
private const val RESUME_MAX_FRACTION = 0.90f
private const val RESUME_CANDIDATE_FACTOR = 3
private const val MAX_RESUME_CANDIDATES = 30
private const val TICKS_PER_SECOND = 10_000_000L

private const val RANDOM_POOL_SIZE = 50

private data class RandomVideoPool(
    val sampled: List<TvItem>,
    val firstPage: List<TvItem>,
    val totalCount: Int
)

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
        minDuration: Int? = null,
        maxDuration: Int? = null,
        quality: String? = null
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
                maxDuration = maxDuration,
                quality = quality,
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
            // The local store only knows what a caller handed it, so its progress fraction is
            // usually zero and cannot decide what is resumable. Every unfinished record is a
            // candidate and the server's watch progress is the source of truth.
            val candidates = records
                .filter { record -> !record.isPlayed && record.itemId.isNotBlank() }
                .take((limit * RESUME_CANDIDATE_FACTOR).coerceAtMost(MAX_RESUME_CANDIDATES))

            val api = clientManager.getApiOrNull()
            val items = candidates.mapNotNull { record ->
                var item = record.toTvItem()
                if (api != null) {
                    val serverProgress = runCatching { api.getWatchProgress(record.itemId) }.getOrNull()
                    if (serverProgress != null) {
                        item = item.copy(
                            playbackPositionTicks = serverProgress.positionSeconds * TICKS_PER_SECOND,
                            runtimeTicks = item.runtimeTicks
                                ?: serverProgress.durationSeconds.takeIf { it > 0 }?.times(TICKS_PER_SECOND),
                            isPlayed = serverProgress.isPlayed,
                            playedPercentage = serverProgress.playedPercentage
                        )
                    }
                }
                item.takeIf { !it.isPlayed && it.progressFraction in RESUME_MIN_FRACTION..RESUME_MAX_FRACTION }
            }.take(limit)

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
            val pool = fetchRandomVideoPool(
                api = clientManager.getApi(),
                baseUrl = clientManager.currentServerUrl ?: "",
                category = genres.firstOrNull(),
                studio = studios.firstOrNull(),
                performerId = personIds.firstOrNull()?.toString(),
                isFavorite = if (filters.contains(ItemFilter.IS_FAVORITE)) true else null
            )

            var eligible = pool.sampled.filterByPlayedState(filters)
            if (excludeItemId != null) {
                eligible = eligible.filter { it.id != excludeItemId }
            }
            if (eligible.isEmpty() && pool.totalCount > RANDOM_POOL_SIZE) {
                // The random window can land on played items only, so fall back to the first page.
                eligible = pool.firstPage.filterByPlayedState(filters)
                if (excludeItemId != null) {
                    eligible = eligible.filter { it.id != excludeItemId }
                }
            }

            Result.success(eligible.shuffled().firstOrNull())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Samples the catalogue at a random offset so repeated calls do not return the same title.
     * The first page is kept as a fallback pool for callers that filter the sample down to nothing.
     */
    private suspend fun fetchRandomVideoPool(
        api: AdultHubApi,
        baseUrl: String,
        category: String?,
        studio: String?,
        performerId: String? = null,
        isFavorite: Boolean?
    ): RandomVideoPool {
        val firstPage = api.getVideos(
            offset = 0,
            limit = RANDOM_POOL_SIZE,
            sort = "latest",
            category = category,
            studio = studio,
            performerId = performerId,
            isFavorite = isFavorite
        )
        val totalCount = firstPage.totalCount
        if (totalCount <= RANDOM_POOL_SIZE) {
            val page = firstPage.items.map { it.toTvItem(baseUrl) }
            return RandomVideoPool(sampled = page, firstPage = page, totalCount = totalCount)
        }
        val maxOffset = (totalCount - RANDOM_POOL_SIZE).coerceAtLeast(0)
        val sampled = api.getVideos(
            offset = kotlin.random.Random.nextInt(maxOffset + 1),
            limit = RANDOM_POOL_SIZE,
            sort = "latest",
            category = category,
            studio = studio,
            performerId = performerId,
            isFavorite = isFavorite
        )
        return RandomVideoPool(
            sampled = sampled.items.map { it.toTvItem(baseUrl) },
            firstPage = firstPage.items.map { it.toTvItem(baseUrl) },
            totalCount = totalCount
        )
    }

    private fun List<TvItem>.filterByPlayedState(filters: Set<ItemFilter>): List<TvItem> = when {
        filters.contains(ItemFilter.IS_UNPLAYED) -> filter { !it.isPlayed }
        filters.contains(ItemFilter.IS_PLAYED) -> filter { it.isPlayed }
        else -> this
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
                } else {
                    fallback
                }
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
            if (itemId.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Cannot query catalog detail without a video id")
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
     * Adult Hub has no dedicated /similar endpoint, so the row is built from the
     * strongest shared attribute of the open item: its first performer, then its
     * studio. The open item itself never appears in the answer.
     */
    open suspend fun getSimilarItems(itemId: String, limit: Int = 12): Result<List<TvItem>> = withContext(Dispatchers.IO) {
        try {
            if (itemId.isBlank() || limit <= 0) {
                return@withContext Result.success(emptyList())
            }
            val api = clientManager.getApi()
            val baseUrl = clientManager.currentServerUrl ?: ""
            val detail = api.getVideoDetail(itemId)
            val pageSize = (limit + 1).coerceAtMost(100)
            val performer = detail.performers.firstOrNull { it.name.isNotBlank() }?.name
            val studio = detail.studio?.takeIf { it.isNotBlank() }
            val response = when {
                performer != null -> api.getVideos(
                    offset = 0,
                    limit = pageSize,
                    sort = "latest",
                    performer = performer
                )

                studio != null -> api.getVideos(
                    offset = 0,
                    limit = pageSize,
                    sort = "latest",
                    studio = studio
                )

                else -> return@withContext Result.success(emptyList())
            }
            Result.success(
                response.items
                    .filter { it.id != itemId }
                    .map { it.toTvItem(baseUrl) }
                    .distinctBy { it.id }
                    .take(limit)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun toggleFavorite(itemId: String, markFavorite: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (itemId.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Cannot toggle favorite without a video id")
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
