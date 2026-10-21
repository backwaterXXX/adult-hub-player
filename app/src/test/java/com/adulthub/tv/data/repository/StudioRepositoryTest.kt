package com.adulthub.tv.data.repository

import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.SettingsDataStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.studiosApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.api.operations.StudiosApi
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.ImageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class StudioRepositoryTest {

    private val clientManager: JellyfinClientManager = mockk(relaxed = true)
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val mockApi: ApiClient = mockk(relaxed = true)
    private val mockStudiosApi: StudiosApi = mockk()
    private val mockItemsApi: ItemsApi = mockk()

    private val repository = StudioRepository(
        clientManager = clientManager,
        settingsDataStore = settingsDataStore,
    )

    init {
        coEvery { clientManager.getApi() } returns mockApi
        every { mockApi.studiosApi } returns mockStudiosApi
        every { mockApi.itemsApi } returns mockItemsApi
    }

    @Test
    fun `getStudios maps studios and respects library filter`() = runTest {
        val libraryId = UUID.randomUUID()
        val studioId = UUID.randomUUID()
        coEvery { settingsDataStore.getSelectedLibraryId() } returns libraryId.toString()

        val mockStudioDto = mockk<BaseItemDto> {
            every { id } returns studioId
            every { name } returns "Brazzers"
            every { imageTags } returns mapOf(ImageType.PRIMARY to "studio-tag")
            every { childCount } returns 99
        }
        val mockQueryResult = mockk<BaseItemDtoQueryResult> {
            every { items } returns listOf(mockStudioDto)
        }
        val mockResponse = mockk<Response<BaseItemDtoQueryResult>> {
            every { content } returns mockQueryResult
        }
        coEvery { mockStudiosApi.getStudios(parentId = libraryId, startIndex = 0, limit = 50) } returns mockResponse

        val result = repository.getStudios(startIndex = 0, limit = 50)

        assertTrue(result.isSuccess)
        val studios = result.getOrThrow()
        assertEquals(1, studios.size)
        assertEquals(studioId.toString(), studios[0].id)
        assertEquals("Brazzers", studios[0].name)
        assertEquals("studio-tag", studios[0].primaryImageTag)
        // The count is not read for a plain list, and the studio child count is not
        // used as a substitute for the library movie total.
        assertNull(studios[0].movieCount)
    }

    @Test
    fun `getStudios rethrows CancellationException`() = runTest {
        coEvery { settingsDataStore.getSelectedLibraryId() } returns UUID.randomUUID().toString()
        coEvery { mockStudiosApi.getStudios(parentId = any(), startIndex = any(), limit = any()) } throws CancellationException("Cancelled")

        try {
            repository.getStudios()
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Cancelled", e.message)
        }
    }

    @Test
    fun `studio movie counts are read from the item endpoint when they are requested`() = runTest {
        val libraryId = UUID.randomUUID()
        coEvery { settingsDataStore.getSelectedLibraryId() } returns libraryId.toString()

        val mockStudioDto = mockk<BaseItemDto> {
            every { id } returns UUID.randomUUID()
            every { name } returns "Brazzers"
            every { imageTags } returns mapOf(ImageType.PRIMARY to "studio-tag")
            every { childCount } returns 7
        }
        val mockQueryResult = mockk<BaseItemDtoQueryResult> {
            every { items } returns listOf(mockStudioDto)
        }
        val mockResponse = mockk<Response<BaseItemDtoQueryResult>> {
            every { content } returns mockQueryResult
        }
        coEvery { mockStudiosApi.getStudios(parentId = libraryId, startIndex = 0, limit = 50) } returns mockResponse

        val mockItemsQueryResult = mockk<BaseItemDtoQueryResult> {
            every { items } returns emptyList()
            every { totalRecordCount } returns 42
            every { startIndex } returns 0
        }
        val mockItemsResponse = mockk<Response<BaseItemDtoQueryResult>> {
            every { content } returns mockItemsQueryResult
        }
        coEvery { mockItemsApi.getItems(any<GetItemsRequest>()) } returns mockItemsResponse

        val result = repository.getStudios(startIndex = 0, limit = 50, includeMovieCounts = true)

        assertTrue(result.isSuccess)
        val studios = result.getOrThrow()
        // The count comes from the item endpoint, not from the studio child count.
        assertEquals(42, studios[0].movieCount)
    }
}
