package com.adulthub.tv.data.repository

import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.resolver.PerformerBioDto
import com.adulthub.tv.data.resolver.ResolverApi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class PersonRepositoryTest {

    private val clientManager: JellyfinClientManager = mockk(relaxed = true)
    private val resolverApi: ResolverApi = mockk(relaxed = true)
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val mockApi: ApiClient = mockk(relaxed = true)
    private val mockLibraryApi: LibraryApi = mockk()

    private val repository = PersonRepository(
        clientManager = clientManager,
        resolverApi = resolverApi,
        settingsDataStore = settingsDataStore,
    )

    init {
        coEvery { clientManager.getApi() } returns mockApi
        every { mockApi.libraryApi } returns mockLibraryApi
    }

    @Test
    fun `getPersonsPage forwards pagination, filter, and alphabetical sort parameters`() = runTest {
        val libraryId = UUID.randomUUID()
        val personId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { clientManager.currentUserId } returns userId.toString()
        coEvery { settingsDataStore.getSelectedLibraryId() } returns libraryId.toString()

        val slot = slot<GetItemsRequest>()
        val mockPersonDto = mockk<BaseItemDto> {
            every { id } returns personId
            every { name } returns "Jane Doe"
            every { imageTags } returns mapOf(ImageType.PRIMARY to "img-123")
            every { childCount } returns 15
        }
        val mockQueryResult = mockk<BaseItemDtoQueryResult> {
            every { items } returns listOf(mockPersonDto)
            every { totalRecordCount } returns 42
        }
        val mockResponse = mockk<Response<BaseItemDtoQueryResult>> {
            every { content } returns mockQueryResult
        }
        coEvery { mockLibraryApi.getItems(capture(slot)) } returns mockResponse

        val result = repository.getPersonsPage(
            startIndex = 20,
            limit = 60,
            searchTerm = "Jane",
            nameStartsWith = "J",
        )

        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(42, page.totalRecordCount)
        assertEquals(1, page.persons.size)
        assertEquals(personId.toString(), page.persons[0].id)
        assertEquals("Jane Doe", page.persons[0].name)
        assertEquals("img-123", page.persons[0].primaryImageTag)
        assertEquals(15, page.persons[0].movieCount)

        val captured = slot.captured
        assertEquals(userId, captured.userId)
        assertEquals(libraryId, captured.parentId)
        assertEquals(20, captured.startIndex)
        assertEquals(60, captured.limit)
        assertEquals("Jane", captured.searchTerm)
        assertEquals("J", captured.nameStartsWith)
        assertEquals(listOf(ItemSortBy.SORT_NAME), captured.sortBy)
        assertEquals(listOf(SortOrder.ASCENDING), captured.sortOrder)
        assertEquals(setOf(BaseItemKind.PERSON), captured.includeItemTypes)
        assertEquals(listOf("Actor"), captured.personTypes)
    }

    @Test
    fun `getPersonMovies requests items filtered by personId and sorted by date created`() = runTest {
        val libraryId = UUID.randomUUID()
        val personId = UUID.randomUUID()
        val movieId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        every { clientManager.currentUserId } returns userId.toString()
        coEvery { settingsDataStore.getSelectedLibraryId() } returns libraryId.toString()

        val slot = slot<GetItemsRequest>()
        val mockMovieDto = mockk<BaseItemDto>(relaxed = true) {
            every { id } returns movieId
            every { name } returns "Test Movie"
        }
        val mockQueryResult = mockk<BaseItemDtoQueryResult> {
            every { items } returns listOf(mockMovieDto)
        }
        val mockResponse = mockk<Response<BaseItemDtoQueryResult>> {
            every { content } returns mockQueryResult
        }
        coEvery { mockLibraryApi.getItems(capture(slot)) } returns mockResponse

        val result = repository.getPersonMovies(personId = personId.toString())

        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        assertEquals(movieId.toString(), items[0].id)
        assertEquals("Test Movie", items[0].title)

        val captured = slot.captured
        assertEquals(userId, captured.userId)
        assertEquals(libraryId, captured.parentId)
        assertEquals(listOf(personId), captured.personIds)
        assertEquals(setOf(BaseItemKind.MOVIE), captured.includeItemTypes)
        assertEquals(listOf(ItemSortBy.DATE_CREATED), captured.sortBy)
        assertEquals(listOf(SortOrder.DESCENDING), captured.sortOrder)
    }

    @Test
    fun `getPersonDetails combines Jellyfin metadata with Resolver bio`() = runTest {
        val personId = UUID.randomUUID()
        val mockPersonDto = mockk<BaseItemDto> {
            every { id } returns personId
            every { name } returns "Jane Doe"
            every { imageTags } returns mapOf(ImageType.PRIMARY to "img-123")
            every { overview } returns "JF Overview"
            every { childCount } returns 10
        }
        val mockQueryResult = mockk<BaseItemDtoQueryResult> {
            every { items } returns listOf(mockPersonDto)
        }
        val mockResponse = mockk<Response<BaseItemDtoQueryResult>> {
            every { content } returns mockQueryResult
        }
        coEvery { mockLibraryApi.getItems(any<GetItemsRequest>()) } returns mockResponse
        coEvery { resolverApi.getPerformerBio("Jane Doe") } returns PerformerBioDto(
            name = "Jane Doe",
            bio = "Resolver Bio",
            birth = "1990-01-01",
            nationality = "US",
            aliases = listOf("JD"),
            photoUrl = "https://example.com/photo.jpg",
        )

        val result = repository.getPersonDetails(personId = personId.toString(), personName = "Jane Doe")

        assertTrue(result.isSuccess)
        val person = result.getOrThrow()
        assertEquals("Jane Doe", person.name)
        assertEquals("Resolver Bio", person.bio)
        assertEquals("1990-01-01", person.birthDate)
        assertEquals("US", person.nationality)
        assertEquals(listOf("JD"), person.aliases)
        assertEquals("https://example.com/photo.jpg", person.photoUrl)
        assertEquals(10, person.movieCount)
    }

    @Test
    fun `getPersonsPage rethrows CancellationException`() = runTest {
        coEvery { settingsDataStore.getSelectedLibraryId() } returns UUID.randomUUID().toString()
        coEvery { mockLibraryApi.getItems(any<GetItemsRequest>()) } throws CancellationException("Job was cancelled")

        try {
            repository.getPersonsPage()
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Job was cancelled", e.message)
        }
    }
}
