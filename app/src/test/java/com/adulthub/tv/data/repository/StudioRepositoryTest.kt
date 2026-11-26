package com.adulthub.tv.data.repository

import com.adulthub.tv.data.api.AdultHubApi
import com.adulthub.tv.data.api.AdultHubClientManager
import com.adulthub.tv.data.api.CatalogTaxonomyItemDto
import com.adulthub.tv.data.api.CatalogTaxonomyListResponseDto
import com.adulthub.tv.data.api.CatalogVideoItemDto
import com.adulthub.tv.data.api.CatalogVideoListResponseDto
import com.adulthub.tv.data.local.SettingsDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StudioRepositoryTest {

    private val clientManager: AdultHubClientManager = mockk(relaxed = true)
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val mockApi: AdultHubApi = mockk(relaxed = true)

    private val repository = StudioRepository(
        clientManager = clientManager,
        settingsDataStore = settingsDataStore
    )

    init {
        coEvery { clientManager.getApi() } returns mockApi
        every { clientManager.currentServerUrl } returns "https://adulthub.example.com"
    }

    @Test
    fun `getStudios maps studios and movie counts`() = runTest {
        val mockResponse = CatalogTaxonomyListResponseDto(
            items = listOf(CatalogTaxonomyItemDto(name = "Brazzers", count = 99)),
            totalCount = 1
        )
        coEvery { mockApi.getStudios(offset = 0, limit = 50) } returns mockResponse

        val result = repository.getStudios(startIndex = 0, limit = 50)

        assertTrue(result.isSuccess)
        val studios = result.getOrThrow()
        assertEquals(1, studios.size)
        assertEquals("Brazzers", studios[0].id)
        assertEquals("Brazzers", studios[0].name)
        assertEquals(99, studios[0].movieCount)
    }

    @Test
    fun `getStudios rethrows CancellationException`() = runTest {
        coEvery { mockApi.getStudios(any(), any()) } throws CancellationException("Cancelled")

        try {
            repository.getStudios()
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Cancelled", e.message)
        }
    }

    @Test
    fun `getStudioMoviesPage fetches videos by studio name`() = runTest {
        val mockResponse = CatalogVideoListResponseDto(
            items = listOf(
                CatalogVideoItemDto(
                    id = "video-1",
                    title = "Studio Video",
                    studio = "Brazzers"
                )
            ),
            totalCount = 1,
            offset = 0,
            limit = 50
        )
        coEvery {
            mockApi.getVideos(
                studio = "Brazzers",
                offset = 0,
                limit = 50,
                sort = "latest",
                direction = "desc"
            )
        } returns mockResponse

        val result = repository.getStudioMoviesPage(studioName = "Brazzers", startIndex = 0, limit = 50)

        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(1, page.items.size)
        assertEquals("video-1", page.items[0].id)
        assertEquals("Studio Video", page.items[0].title)
    }
}
