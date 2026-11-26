package com.adulthub.tv.data.repository

import com.adulthub.tv.data.api.AdultHubApi
import com.adulthub.tv.data.api.AdultHubClientManager
import com.adulthub.tv.data.api.CatalogPerformerDetailDto
import com.adulthub.tv.data.api.CatalogPerformerListResponseDto
import com.adulthub.tv.data.api.CatalogPerformerRefDto
import com.adulthub.tv.data.api.CatalogVideoItemDto
import com.adulthub.tv.data.api.CatalogVideoListResponseDto
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.resolver.PerformerBioDto
import com.adulthub.tv.data.resolver.ResolverApi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PersonRepositoryTest {

    private val clientManager: AdultHubClientManager = mockk(relaxed = true)
    private val resolverApi: ResolverApi = mockk(relaxed = true)
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val mockApi: AdultHubApi = mockk(relaxed = true)

    private val repository = PersonRepository(
        clientManager = clientManager,
        resolverApi = resolverApi,
        settingsDataStore = settingsDataStore
    )

    init {
        coEvery { clientManager.getApi() } returns mockApi
        every { clientManager.currentServerUrl } returns "https://adulthub.example.com"
    }

    @Test
    fun `getPersonsPage forwards pagination and search filters`() = runTest {
        val mockResponse = CatalogPerformerListResponseDto(
            items = listOf(
                CatalogPerformerRefDto(
                    id = "performer-1",
                    name = "Jane Doe",
                    photoUrl = "https://example.com/jane.jpg",
                    videoCount = 15
                )
            ),
            totalCount = 42,
            offset = 20,
            limit = 60
        )
        coEvery {
            mockApi.getPerformers(
                offset = 20,
                limit = 60,
                q = "Jane",
                letter = "J"
            )
        } returns mockResponse

        val result = repository.getPersonsPage(
            startIndex = 20,
            limit = 60,
            searchTerm = "Jane",
            nameStartsWith = "J"
        )

        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(42, page.totalRecordCount)
        assertEquals(1, page.persons.size)
        assertEquals("performer-1", page.persons[0].id)
        assertEquals("Jane Doe", page.persons[0].name)
        assertEquals("https://example.com/jane.jpg", page.persons[0].photoUrl)
        assertEquals(15, page.persons[0].movieCount)
    }

    @Test
    fun `getPersonsPage rethrows CancellationException`() = runTest {
        coEvery { mockApi.getPerformers(any(), any(), any(), any(), any(), any(), any()) } throws CancellationException("Cancelled")

        try {
            repository.getPersonsPage()
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Cancelled", e.message)
        }
    }

    @Test
    fun `getPersonDetails returns performer details from Adult Hub API`() = runTest {
        val detailDto = CatalogPerformerDetailDto(
            id = "performer-1",
            name = "Jane Doe",
            photoUrl = "https://example.com/photo.jpg",
            bio = "Performer bio",
            birth = "1995-01-01",
            nationality = "US",
            aliases = listOf("JD"),
            videoCount = 25
        )
        coEvery { mockApi.getPerformerDetail("performer-1") } returns detailDto

        val result = repository.getPersonDetails("performer-1", "Jane Doe")

        assertTrue(result.isSuccess)
        val person = result.getOrThrow()
        assertEquals("performer-1", person.id)
        assertEquals("Jane Doe", person.name)
        assertEquals("Performer bio", person.bio)
        assertEquals("1995-01-01", person.birthDate)
        assertEquals("US", person.nationality)
        assertEquals(25, person.movieCount)
    }

    @Test
    fun `getPersonDetails falls back to resolver bio if adult hub detail fails`() = runTest {
        coEvery { mockApi.getPerformerDetail("performer-1") } throws RuntimeException("Not found")
        coEvery { resolverApi.getPerformerBio(any()) } returns PerformerBioDto(
            name = "Jane Doe",
            bio = "Resolver Bio",
            birth = "1990-05-10",
            nationality = "CA"
        )

        val result = repository.getPersonDetails("performer-1", "Jane Doe")

        assertTrue(result.isSuccess)
        val person = result.getOrThrow()
        assertEquals("performer-1", person.id)
        assertEquals("Jane Doe", person.name)
        assertEquals("Resolver Bio", person.bio)
        assertEquals("1990-05-10", person.birthDate)
        assertEquals("CA", person.nationality)
    }

    @Test
    fun `getPersonMovies returns videos for performer`() = runTest {
        val mockResponse = CatalogVideoListResponseDto(
            items = listOf(
                CatalogVideoItemDto(
                    id = "video-10",
                    title = "Movie 10"
                )
            ),
            totalCount = 1,
            offset = 0,
            limit = 50
        )
        coEvery {
            mockApi.getVideos(
                performerId = "performer-1",
                offset = 0,
                limit = 50,
                sort = "latest",
                direction = "desc"
            )
        } returns mockResponse

        val result = repository.getPersonMovies("performer-1", startIndex = 0, limit = 50)

        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        assertEquals("video-10", items[0].id)
    }
}
