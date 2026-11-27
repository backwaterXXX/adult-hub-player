package com.adulthub.tv.data.repository

import com.adulthub.tv.data.api.AdultHubApi
import com.adulthub.tv.data.api.AdultHubClientManager
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
    fun `getRandomItemFiltered keeps the selected performer in the request`() = runTest {
        val performer = "11111111-1111-1111-1111-111111111111"
        coEvery {
            mockApi.getVideos(performerId = performer, limit = any())
        } returns CatalogVideoListResponseDto(
            items = listOf(CatalogVideoItemDto(id = "video-1", title = "Same Performer")),
            totalCount = 1
        )

        val result = repository.getRandomItemFiltered(
            personIds = listOf(java.util.UUID.fromString(performer))
        )

        assertTrue(result.isSuccess)
        assertEquals("video-1", result.getOrThrow()?.id)
        coVerify(exactly = 1) { mockApi.getVideos(performerId = performer) }
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
    fun `getSimilarItems uses server recommendations`() = runTest {
        coEvery { mockApi.getSimilarVideos("video-1", 8) } returns CatalogVideoListResponseDto(
            items = listOf(CatalogVideoItemDto(id = "video-2", title = "Recommended"))
        )
        assertEquals(listOf("video-2"), repository.getSimilarItems("video-1", 8).getOrThrow().map { it.id })
        coVerify(exactly = 0) { mockApi.getVideoDetail(any()) }
    }

    @Test
    fun `getSimilarItems reports backend failure without fallback`() = runTest {
        coEvery { mockApi.getSimilarVideos("video-1", 8) } throws IllegalStateException("unavailable")
        assertTrue(repository.getSimilarItems("video-1", 8).isFailure)
        coVerify(exactly = 0) { mockApi.getVideoDetail(any()) }
        coVerify(exactly = 0) { mockApi.getVersion() }
    }

    @Test
    fun `getSimilarItems caps limit to server maximum`() = runTest {
        coEvery { mockApi.getSimilarVideos("video-1", 24) } returns CatalogVideoListResponseDto()
        assertTrue(repository.getSimilarItems("video-1", 100).isSuccess)
        coVerify(exactly = 1) { mockApi.getSimilarVideos("video-1", 24) }
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
    fun `getRandomItemFiltered forwards raw performerId even when not a UUID`() = runTest {
        val mockResponse = CatalogVideoListResponseDto(
            items = listOf(CatalogVideoItemDto(id = "video-performer", title = "Performer Video")),
            totalCount = 1
        )
        coEvery {
            mockApi.getVideos(
                offset = any(),
                limit = any(),
                sort = any(),
                category = any(),
                studio = any(),
                performerId = "performer-slug-42",
                isFavorite = any()
            )
        } returns mockResponse

        val result = repository.getRandomItemFiltered(performerId = "performer-slug-42")
        assertTrue(result.isSuccess)
        assertEquals("video-performer", result.getOrThrow()?.id)
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
