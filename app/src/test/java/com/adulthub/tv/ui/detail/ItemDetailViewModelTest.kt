package com.adulthub.tv.ui.detail

import android.content.Context
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.repository.LibraryRepository
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItemDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeLibraryRepository = FakeLibraryRepository()
    private val clientManager: JellyfinClientManager = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private val sampleItem = TvItem(
        id = "movie-1",
        title = "Inception",
        genres = listOf("Sci-Fi"),
        isFavorite = false,
    )

    private val similarItem = TvItem(
        id = "movie-2",
        title = "Interstellar",
        genres = listOf("Sci-Fi"),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeLibraryRepository.itemDetailsResult = Result.success(sampleItem)
        fakeLibraryRepository.similarResult = Result.success(listOf(similarItem))
        fakeLibraryRepository.singleRandomResult = Result.success(similarItem)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadDetails loads item details and similar items`() = runTest(testDispatcher) {
        val viewModel = ItemDetailViewModel(
            libraryRepository = fakeLibraryRepository,
            clientManager = clientManager,
            context = context,
        )

        viewModel.loadDetails("movie-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.item)
        assertEquals("movie-1", state.item?.id)
        assertEquals("Inception", state.item?.title)
        assertFalse(state.isFavorite)

        assertFalse(state.isLoadingSimilar)
        assertEquals(1, state.similarItems.size)
        assertEquals("movie-2", state.similarItems.first().id)
    }

    @Test
    fun `toggleFavorite updates favorite state and calls repository`() = runTest(testDispatcher) {
        val viewModel = ItemDetailViewModel(
            libraryRepository = fakeLibraryRepository,
            clientManager = clientManager,
            context = context,
        )

        viewModel.loadDetails("movie-1")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isFavorite)

        // Toggle to true
        viewModel.toggleFavorite()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isFavorite)
        assertEquals(true, fakeLibraryRepository.lastToggledFavoriteState)

        // Toggle back to false
        viewModel.toggleFavorite()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isFavorite)
        assertEquals(false, fakeLibraryRepository.lastToggledFavoriteState)
    }

    @Test
    fun `toggleWatched uses provider video id for Telegram items`() = runTest(testDispatcher) {
        fakeLibraryRepository.itemDetailsResult = Result.success(
            TvItem(id = "internal-item-id", title = "Channel clip", providerVideoId = "telegram:clip-42"),
        )
        val viewModel = ItemDetailViewModel(
            libraryRepository = fakeLibraryRepository,
            clientManager = clientManager,
            context = context,
        )

        viewModel.loadDetails("internal-item-id")
        advanceUntilIdle()
        viewModel.toggleWatched()
        advanceUntilIdle()

        assertEquals("telegram:clip-42", fakeLibraryRepository.lastWatchedStateId)
        assertEquals(true, fakeLibraryRepository.lastWatchedState)
    }

    @Test
    fun `playMovie emits NavigateToPlayer event`() = runTest(testDispatcher) {
        val viewModel = ItemDetailViewModel(
            libraryRepository = fakeLibraryRepository,
            clientManager = clientManager,
            context = context,
        )

        viewModel.loadDetails("movie-1")
        advanceUntilIdle()

        val events = mutableListOf<ItemDetailNavEvent>()
        val job = launch {
            viewModel.navEvents.collect { events.add(it) }
        }

        viewModel.playMovie()
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(ItemDetailNavEvent.NavigateToPlayer("movie-1", false), events.first())

        job.cancel()
    }

    @Test
    fun `playRandom emits NavigateToItemDetail for random movie`() = runTest(testDispatcher) {
        val viewModel = ItemDetailViewModel(
            libraryRepository = fakeLibraryRepository,
            clientManager = clientManager,
            context = context,
        )

        viewModel.loadDetails("movie-1")
        advanceUntilIdle()

        val events = mutableListOf<ItemDetailNavEvent>()
        val job = launch {
            viewModel.navEvents.collect { events.add(it) }
        }

        viewModel.playRandom()
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(ItemDetailNavEvent.NavigateToItemDetail("movie-2"), events.first())

        job.cancel()
    }

    @Test
    fun `excludeTag delegates to settingsDataStore`() = runTest(testDispatcher) {
        val settingsDataStore: com.adulthub.tv.data.local.SettingsDataStore = mockk(relaxed = true)
        val viewModel = ItemDetailViewModel(
            libraryRepository = fakeLibraryRepository,
            clientManager = clientManager,
            settingsDataStore = settingsDataStore,
            context = context,
        )

        viewModel.excludeTag("pov")
        advanceUntilIdle()

        io.mockk.coVerify { settingsDataStore.excludeTag("pov") }
    }

    private class FakeLibraryRepository : LibraryRepository(mockk(relaxed = true), mockk(relaxed = true)) {
        var itemDetailsResult: Result<TvItem> = Result.failure(RuntimeException("Not found"))
        var similarResult: Result<List<TvItem>> = Result.success(emptyList())
        var singleRandomResult: Result<TvItem?> = Result.success(null)
        var lastToggledFavoriteState: Boolean? = null
        var lastWatchedStateId: String? = null
        var lastWatchedState: Boolean? = null

        override suspend fun getItemDetails(itemId: String): Result<TvItem> = itemDetailsResult

        override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<TvItem>> = similarResult

        override suspend fun getRandomItem(parentId: String?, excludeItemId: String?): Result<TvItem?> = singleRandomResult

        override suspend fun toggleFavorite(itemId: String, markFavorite: Boolean): Result<Boolean> {
            lastToggledFavoriteState = markFavorite
            return Result.success(markFavorite)
        }

        override suspend fun markAsPlayed(itemId: String, isPlayed: Boolean): Result<Boolean> {
            lastWatchedStateId = itemId
            lastWatchedState = isPlayed
            return Result.success(isPlayed)
        }
    }
}
