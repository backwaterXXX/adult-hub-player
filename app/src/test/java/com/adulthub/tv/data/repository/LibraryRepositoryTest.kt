package com.adulthub.tv.data.repository

import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.local.TelegramProgressStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.genreApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userDataApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.api.operations.GenreApi
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.UserDataApi
import org.jellyfin.sdk.api.operations.UserViewApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LibraryRepositoryTest {

    private val clientManager: JellyfinClientManager = mockk(relaxed = true)
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val progressStore: TelegramProgressStore = mockk(relaxed = true)
    private val mockApi: ApiClient = mockk(relaxed = true)
    private val mockLibraryApi: LibraryApi = mockk()
    private val mockUserDataApi: UserDataApi = mockk()
    private val mockUserViewApi: UserViewApi = mockk()
    private val mockGenreApi: GenreApi = mockk()

    private val userUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val libraryUuid = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private val repository = LibraryRepository(
        clientManager = clientManager,
        settingsDataStore = settingsDataStore,
        telegramProgressStore = progressStore,
    )

    init {
        every { clientManager.currentUserId } returns userUuid.toString()
        coEvery { clientManager.getApi() } returns mockApi
        every { mockApi.libraryApi } returns mockLibraryApi
        every { mockApi.userDataApi } returns mockUserDataApi
        every { mockApi.userViewApi } returns mockUserViewApi
        every { mockApi.genreApi } returns mockGenreApi
        coEvery { settingsDataStore.getSelectedLibraryId() } returns libraryUuid.toString()
    }

    @Test
    fun `marking a non UUID video persists Telegram watched state`() = runTest {
        assertEquals(true, repository.markAsPlayed("telegram:-100:42:7", true).getOrThrow())
        coVerify(exactly = 1) { progressStore.markPlayed("telegram:-100:42:7", true) }
    }

    @Test
    fun `getItems sets user scoping, recursive, total count, filters and ordering`() = runTest {
        val slot = slot<GetItemsRequest>()
        val mockDto = mockk<BaseItemDto> {
            every { id } returns UUID.randomUUID()
            every { name } returns "Test Movie"
            every { overview } returns null
            every { productionYear } returns 2024
            every { runTimeTicks } returns 36000000000L
            every { genres } returns listOf("Action")
            every { tags } returns emptyList()
            every { studios } returns emptyList()
            every { people } returns emptyList()
            every { imageTags } returns null
            every { backdropImageTags } returns null
            every { userData } returns null
            every { providerIds } returns mapOf("telegram" to "-100:1:2")
            every { mediaSources } returns emptyList()
            every { container } returns "mp4"
            every { width } returns 1920
            every { height } returns 1080
        }
        val mockQueryResult = mockk<BaseItemDtoQueryResult> {
            every { items } returns listOf(mockDto)
            every { totalRecordCount } returns 73
            every { startIndex } returns 10
        }
        val mockResponse = mockk<Response<BaseItemDtoQueryResult>> {
            every { content } returns mockQueryResult
        }
        coEvery { mockLibraryApi.getItems(capture(slot)) } returns mockResponse

        val result = repository.getItems(
            startIndex = 10,
            limit = 25,
            sortBy = "DateCreated",
            sortOrder = SortOrder.DESCENDING,
            filters = setOf(ItemFilter.IS_FAVORITE, ItemFilter.IS_UNPLAYED),
            searchTerm = "query",
        )

        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(73, page.totalRecordCount)
        assertEquals(10, page.startIndex)
        assertEquals(1, page.items.size)
        assertEquals("Test Movie", page.items[0].title)

        val captured = slot.captured
        assertEquals(userUuid, captured.userId)
        assertEquals(libraryUuid, captured.parentId)
        assertEquals(10, captured.startIndex)
        assertEquals(25, captured.limit)
        assertEquals(true, captured.recursive)
        assertEquals(true, captured.enableTotalRecordCount)
        assertEquals(setOf(BaseItemKind.MOVIE), captured.includeItemTypes)
        assertEquals(setOf(ItemFields.PROVIDER_IDS), captured.fields)
        assertEquals(listOf(ItemSortBy.DATE_CREATED), captured.sortBy)
        assertEquals(listOf(SortOrder.DESCENDING), captured.sortOrder)
        assertEquals(setOf(ItemFilter.IS_FAVORITE, ItemFilter.IS_UNPLAYED), captured.filters)
        assertEquals("query", captured.searchTerm)
    }

    @Test
    fun `getResumeItems requests video media types scoped to active user`() = runTest {
        val slot = slot<GetResumeItemsRequest>()
        val mockQueryResult = mockk<BaseItemDtoQueryResult> {
            every { items } returns emptyList()
            every { totalRecordCount } returns 0
        }
        val mockResponse = mockk<Response<BaseItemDtoQueryResult>> {
            every { content } returns mockQueryResult
        }
        coEvery { mockLibraryApi.getResumeItems(capture(slot)) } returns mockResponse

        val result = repository.getResumeItems(limit = 15)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
        assertEquals(userUuid, slot.captured.userId)
        assertEquals(15, slot.captured.limit)
        assertEquals(setOf(MediaType.VIDEO), slot.captured.mediaTypes)
        assertEquals(setOf(ItemFields.PROVIDER_IDS), slot.captured.fields)
    }

    @Test
    fun `getLatestItems forwards parentId and active user scoping`() = runTest {
        val slot = slot<GetLatestMediaRequest>()
        val mockResponse = mockk<Response<List<BaseItemDto>>> {
            every { content } returns emptyList()
        }
        coEvery { mockLibraryApi.getLatestMedia(capture(slot)) } returns mockResponse

        val result = repository.getLatestItems(limit = 10)

        assertTrue(result.isSuccess)
        assertEquals(userUuid, slot.captured.userId)
        assertEquals(libraryUuid, slot.captured.parentId)
        assertEquals(10, slot.captured.limit)
        assertEquals(setOf(ItemFields.PROVIDER_IDS), slot.captured.fields)
    }

    @Test
    fun `toggleFavorite delegates to userDataApi with active user`() = runTest {
        val itemUuid = UUID.randomUUID()
        val mockResponse = mockk<Response<UserItemDataDto>> {
            every { content } returns mockk()
        }
        coEvery { mockUserDataApi.markFavoriteItem(itemId = itemUuid, userId = userUuid) } returns mockResponse
        coEvery { mockUserDataApi.unmarkFavoriteItem(itemId = itemUuid, userId = userUuid) } returns mockResponse

        val markResult = repository.toggleFavorite(itemUuid.toString(), markFavorite = true)
        assertTrue(markResult.isSuccess)
        assertEquals(true, markResult.getOrThrow())
        coVerify(exactly = 1) { mockUserDataApi.markFavoriteItem(itemId = itemUuid, userId = userUuid) }

        val unmarkResult = repository.toggleFavorite(itemUuid.toString(), markFavorite = false)
        assertTrue(unmarkResult.isSuccess)
        assertEquals(false, unmarkResult.getOrThrow())
        coVerify(exactly = 1) { mockUserDataApi.unmarkFavoriteItem(itemId = itemUuid, userId = userUuid) }
    }

    @Test
    fun `markAsPlayed delegates to userDataApi with active user`() = runTest {
        val itemUuid = UUID.randomUUID()
        val mockResponse = mockk<Response<UserItemDataDto>> {
            every { content } returns mockk()
        }
        coEvery { mockUserDataApi.markPlayedItem(itemId = itemUuid, userId = userUuid) } returns mockResponse
        coEvery { mockUserDataApi.markUnplayedItem(itemId = itemUuid, userId = userUuid) } returns mockResponse

        val playedResult = repository.markAsPlayed(itemUuid.toString(), isPlayed = true)
        assertTrue(playedResult.isSuccess)
        assertEquals(true, playedResult.getOrThrow())
        coVerify(exactly = 1) { mockUserDataApi.markPlayedItem(itemId = itemUuid, userId = userUuid) }

        val unplayedResult = repository.markAsPlayed(itemUuid.toString(), isPlayed = false)
        assertTrue(unplayedResult.isSuccess)
        assertEquals(false, unplayedResult.getOrThrow())
        coVerify(exactly = 1) { mockUserDataApi.markUnplayedItem(itemId = itemUuid, userId = userUuid) }
    }

    @Test
    fun `getSimilarItems forwards active user and limit to LibraryApi`() = runTest {
        val itemUuid = UUID.randomUUID()
        val slot = slot<GetSimilarItemsRequest>()
        val mockQueryResult = mockk<BaseItemDtoQueryResult> {
            every { items } returns emptyList()
        }
        val mockResponse = mockk<Response<BaseItemDtoQueryResult>> {
            every { content } returns mockQueryResult
        }
        coEvery { mockLibraryApi.getSimilarItems(capture(slot)) } returns mockResponse

        val result = repository.getSimilarItems(itemId = itemUuid.toString(), limit = 8)

        assertTrue(result.isSuccess)
        assertEquals(itemUuid, slot.captured.itemId)
        assertEquals(userUuid, slot.captured.userId)
        assertEquals(8, slot.captured.limit)
    }

    @Test
    fun `getGenres forwards active user, parentId, and sorting to GenreApi`() = runTest {
        val slot = slot<GetGenresRequest>()
        val mockQueryResult = mockk<BaseItemDtoQueryResult> {
            every { items } returns emptyList()
        }
        val mockResponse = mockk<Response<BaseItemDtoQueryResult>> {
            every { content } returns mockQueryResult
        }
        coEvery { mockGenreApi.getGenres(capture(slot)) } returns mockResponse

        val result = repository.getGenres(limit = 100)

        assertTrue(result.isSuccess)
        assertEquals(libraryUuid, slot.captured.parentId)
        assertEquals(userUuid, slot.captured.userId)
        assertEquals(100, slot.captured.limit)
        assertEquals(listOf(ItemSortBy.SORT_NAME), slot.captured.sortBy)
        assertEquals(listOf(SortOrder.ASCENDING), slot.captured.sortOrder)
    }
}
