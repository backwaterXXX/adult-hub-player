package com.adulthub.tv.data.repository

import com.adulthub.tv.data.api.AdultHubApi
import com.adulthub.tv.data.api.AdultHubClientManager
import com.adulthub.tv.data.api.CatalogPerformerRefDto
import com.adulthub.tv.data.api.CatalogTaxonomyItemDto
import com.adulthub.tv.data.api.CatalogTaxonomyListResponseDto
import com.adulthub.tv.data.api.CatalogVideoItemDto
import com.adulthub.tv.data.api.CatalogVideoListResponseDto
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.local.TelegramProgressStore
import com.adulthub.tv.data.model.ItemFilter
import com.adulthub.tv.data.model.SortOrder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRepositoryTest {

    private val clientManager: AdultHubClientManager = mockk(relaxed = true)
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val progressStore: TelegramProgressStore = mockk(relaxed = true)
    private val mockApi: AdultHubApi = mockk(relaxed = true)

    private val repository = LibraryRepository(
        clientManager = clientManager,
        settingsDataStore = settingsDataStore,
        telegramProgressStore = progressStore
    )

    init {
        coEvery { clientManager.getApi() } returns mockApi
        every { clientManager.getApiOrNull() } returns mockApi
        every { clientManager.currentServerUrl } returns "https://adulthub.example.com"
    }

    @Test
    fun `marking a non UUID video persists Telegram watched state`() = runTest {
        assertEquals(true, repository.markAsPlayed("telegram:-100:42:7", true).getOrThrow())
        coVerify(exactly = 1) { progressStore.markPlayed("telegram:-100:42:7", true) }
    }

    @Test
    fun `getLibraries returns canonical catalog`() = runTest {
        val result = repository.getLibraries()
        assertTrue(result.isSuccess)
        val libs = result.getOrThrow()
        assertEquals(1, libs.size)
        assertEquals("catalog", libs[0].id)
        assertEquals("Adult Hub", libs[0].name)
    }

    @Test
    fun `getItems forwards pagination, sort, and filters to Adult Hub API`() = runTest {
        val mockResponse = CatalogVideoListResponseDto(
            items = listOf(
                CatalogVideoItemDto(
                    id = "video-1",
                    title = "Test Video",
                    durationSeconds = 3600
                )
            ),
            totalCount = 73,
            offset = 10,
            limit = 25
        )
        coEvery {
            mockApi.getVideos(
                offset = 10,
                limit = 25,
                sort = "latest",
                direction = "desc",
                q = "query",
                isFavorite = true
            )
        } returns mockResponse

        val result = repository.getItems(
            startIndex = 10,
            limit = 25,
            sortBy = "DateCreated",
            sortOrder = SortOrder.DESCENDING,
            filters = setOf(ItemFilter.IS_FAVORITE),
            searchTerm = "query"
        )

        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(73, page.totalRecordCount)
        assertEquals(10, page.startIndex)
        assertEquals(1, page.items.size)
        assertEquals("Test Video", page.items[0].title)
    }

    @Test
    fun `toggleFavorite delegates to putFavorite and deleteFavorite`() = runTest {
        val markResult = repository.toggleFavorite("video-1", markFavorite = true)
        assertTrue(markResult.isSuccess)
        assertEquals(true, markResult.getOrThrow())
        coVerify(exactly = 1) { mockApi.putFavorite("video-1") }

        val unmarkResult = repository.toggleFavorite("video-1", markFavorite = false)
        assertTrue(unmarkResult.isSuccess)
        assertEquals(false, unmarkResult.getOrThrow())
        coVerify(exactly = 1) { mockApi.deleteFavorite("video-1") }
    }

    @Test
    fun `markAsPlayed delegates to putWatchProgress`() = runTest {
        val playedResult = repository.markAsPlayed("video-1", isPlayed = true)
        assertTrue(playedResult.isSuccess)
        assertEquals(true, playedResult.getOrThrow())
        coVerify(exactly = 1) {
            mockApi.putWatchProgress(
                videoId = "video-1",
                body = match { it.isPlayed }
            )
        }
    }

    @Test
    fun `getSimilarItems queries the first performer and drops the open item`() = runTest {
        coEvery { mockApi.getVideoDetail("video-1") } returns CatalogVideoItemDto(
            id = "video-1",
            title = "Open Item",
            performers = listOf(CatalogPerformerRefDto(id = "name:Star One", name = "Star One"))
        )
        coEvery {
            mockApi.getVideos(offset = 0, limit = 9, sort = "latest", performer = "Star One")
        } returns CatalogVideoListResponseDto(
            items = listOf(
                CatalogVideoItemDto(id = "video-1", title = "Open Item"),
                CatalogVideoItemDto(id = "video-2", title = "Sibling"),
                CatalogVideoItemDto(id = "video-2", title = "Sibling duplicate")
            ),
            totalCount = 3
        )

        val result = repository.getSimilarItems("video-1", limit = 8)

        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(listOf("video-2"), items.map { it.id })
        assertEquals("Sibling", items.single().title)
    }

    @Test
    fun `getSimilarItems falls back to the studio when no performer is listed`() = runTest {
        coEvery { mockApi.getVideoDetail("video-3") } returns CatalogVideoItemDto(
            id = "video-3",
            title = "Studio Only",
            studio = "Acme Studio"
        )
        coEvery {
            mockApi.getVideos(offset = 0, limit = 5, sort = "latest", studio = "Acme Studio")
        } returns CatalogVideoListResponseDto(
            items = listOf(CatalogVideoItemDto(id = "video-4", title = "Stable Mate")),
            totalCount = 1
        )

        val items = repository.getSimilarItems("video-3", limit = 4).getOrThrow()

        assertEquals(listOf("video-4"), items.map { it.id })
    }

    @Test
    fun `getSimilarItems reports an empty row without performers or studio`() = runTest {
        coEvery { mockApi.getVideoDetail("video-5") } returns CatalogVideoItemDto(
            id = "video-5",
            title = "Anonymous"
        )

        val result = repository.getSimilarItems("video-5", limit = 8)

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Any>(), result.getOrThrow())
    }

    @Test
    fun `getGenres forwards limit to Adult Hub getCategories`() = runTest {
        val mockResponse = CatalogTaxonomyListResponseDto(
            items = listOf(CatalogTaxonomyItemDto(name = "Anal", count = 10)),
            totalCount = 1
        )
        coEvery { mockApi.getCategories(offset = 0, limit = 100) } returns mockResponse

        val result = repository.getGenres(limit = 100)

        assertTrue(result.isSuccess)
        val genres = result.getOrThrow()
        assertEquals(1, genres.size)
        assertEquals("Anal", genres[0].name)
    }

    @Test
    fun `getRandomItemFiltered does not return excluded item when only excluded item exists`() = runTest {
        val mockResponse = CatalogVideoListResponseDto(
            items = listOf(
                CatalogVideoItemDto(
                    id = "video-exclude-me",
                    title = "Excluded Video"
                )
            ),
            totalCount = 1
        )
        coEvery { mockApi.getVideos(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns mockResponse

        val result = repository.getRandomItem(excludeItemId = "video-exclude-me")
        assertTrue(result.isSuccess)
        org.junit.Assert.assertNull("Should not return the excluded item", result.getOrThrow())
    }

    @Test
    fun `getItems filters out played items when IS_UNPLAYED filter is present`() = runTest {
        val mockResponse = CatalogVideoListResponseDto(
            items = listOf(
                CatalogVideoItemDto(
                    id = "v-unplayed",
                    title = "Unplayed",
                    userState = com.adulthub.tv.data.api.CatalogUserStateDto(isPlayed = false)
                ),
                CatalogVideoItemDto(
                    id = "v-played",
                    title = "Played",
                    userState = com.adulthub.tv.data.api.CatalogUserStateDto(isPlayed = true)
                )
            ),
            totalCount = 2
        )
        coEvery { mockApi.getVideos(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns mockResponse

        val result = repository.getItems(filters = setOf(ItemFilter.IS_UNPLAYED))
        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(1, page.items.size)
        assertEquals("v-unplayed", page.items[0].id)
    }

    @Test
    fun `getItemDetails queries the catalog for canonical Telegram ids`() = runTest {
        val result = repository.getItemDetails("telegram:-100123:456:789")
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mockApi.getVideoDetail("telegram:-100123:456:789") }
    }

    @Test
    fun `toggleFavorite sends canonical Telegram ids to the server`() = runTest {
        val result = repository.toggleFavorite("telegram:-100123:456:789", true)
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mockApi.putFavorite("telegram:-100123:456:789") }
    }

    @Test
    fun `getResumeItems returns catalog items with server progress for canonical Telegram ids`() = runTest {
        val watchHistoryStore = mockk<com.adulthub.tv.data.local.WatchHistoryStore>(relaxed = true)
        val repoWithHistory = LibraryRepository(
            clientManager = clientManager,
            settingsDataStore = settingsDataStore,
            watchHistoryStore = watchHistoryStore
        )

        val historyRecords = listOf(
            com.adulthub.tv.data.local.WatchHistoryRecord(
                itemId = "cat-movie-1",
                title = "Catalog Movie 1",
                watchedAt = System.currentTimeMillis(),
                runtimeTicks = 100_000_000_000L,
                progressFraction = 0.40f,
                isPlayed = false
            ),
            com.adulthub.tv.data.local.WatchHistoryRecord(
                itemId = "telegram:-100:999:1",
                title = "Telegram Video",
                watchedAt = System.currentTimeMillis(),
                runtimeTicks = 50_000_000_000L,
                progressFraction = 0.30f,
                isPlayed = false
            )
        )
        every { watchHistoryStore.history } returns kotlinx.coroutines.flow.flowOf(historyRecords)
        coEvery { mockApi.getWatchProgress("cat-movie-1") } returns com.adulthub.tv.data.api.WatchProgressDto(
            videoId = "cat-movie-1",
            positionSeconds = 45,
            durationSeconds = 100,
            isPlayed = false,
            playedPercentage = 45.0
        )

        coEvery { mockApi.getWatchProgress("telegram:-100:999:1") } returns com.adulthub.tv.data.api.WatchProgressDto(
            videoId = "telegram:-100:999:1",
            positionSeconds = 30,
            durationSeconds = 100,
            isPlayed = false,
            playedPercentage = 30.0
        )

        val result = repoWithHistory.getResumeItems(limit = 10)
        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        // Both records are canonical catalogue ids, so both stay resumable.
        assertEquals(2, items.size)
        assertEquals(450_000_000L, items.first { it.id == "cat-movie-1" }.playbackPositionTicks)
        coVerify(exactly = 1) { mockApi.getWatchProgress("telegram:-100:999:1") }
    }

    @Test
    fun `getRandomItems respects IS_UNPLAYED filter and does not return played items`() = runTest {
        val mockResponse = CatalogVideoListResponseDto(
            items = listOf(
                CatalogVideoItemDto(
                    id = "v-unplayed",
                    title = "Unplayed Movie",
                    userState = com.adulthub.tv.data.api.CatalogUserStateDto(isPlayed = false)
                ),
                CatalogVideoItemDto(
                    id = "v-played",
                    title = "Played Movie",
                    userState = com.adulthub.tv.data.api.CatalogUserStateDto(isPlayed = true)
                )
            ),
            totalCount = 2
        )
        coEvery { mockApi.getVideos(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns mockResponse

        val result = repository.getRandomItems(limit = 10, filters = setOf(ItemFilter.IS_UNPLAYED))
        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        assertEquals("v-unplayed", items[0].id)
    }
}
