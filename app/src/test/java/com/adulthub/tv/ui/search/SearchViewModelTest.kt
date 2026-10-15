package com.adulthub.tv.ui.search

import com.adulthub.tv.data.local.SearchHistoryStore
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.PersonRepository
import com.adulthub.tv.data.repository.ChannelRepository
import com.adulthub.tv.data.repository.TvItemsPage
import com.adulthub.tv.data.resolver.VideoDto
import com.adulthub.tv.data.resolver.ResolverApi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeLibraryRepository = FakeSearchLibraryRepo()
    private val fakePersonRepository = FakeSearchPersonRepo()
    private val fakeChannelRepository = FakeSearchChannelRepo()
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)

    private val sampleMovie = TvItem(
        id = "movie-1",
        title = "Action Hero",
    )

    private val sampleActor = TvPerson(
        id = "person-1",
        name = "John Action",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsDataStore.selectedLibraryId } returns flowOf("lib-1")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty query and results`() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
        )

        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertFalse(state.isSearching)
        assertFalse(state.hasSearched)
        assertTrue(state.movies.isEmpty())
        assertTrue(state.actors.isEmpty())
    }

    @Test
    fun `onQueryChanged with debounce triggers search and updates state`() = runTest(testDispatcher) {
        fakeLibraryRepository.itemsResult = Result.success(
            TvItemsPage(items = listOf(sampleMovie), totalRecordCount = 1, startIndex = 0)
        )
        fakePersonRepository.personsResult = Result.success(listOf(sampleActor))

        val viewModel = SearchViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
        )

        viewModel.onQueryChanged("Action")
        // Before 300ms debounce
        advanceTimeBy(100L)
        assertFalse(viewModel.uiState.value.hasSearched)

        // After 300ms debounce
        advanceTimeBy(250L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Action", state.query)
        assertFalse(state.isSearching)
        assertTrue(state.hasSearched)
        assertEquals(1, state.movies.size)
        assertEquals("movie-1", state.movies.first().id)
        assertEquals(1, state.actors.size)
        assertEquals("person-1", state.actors.first().id)
        assertNull(state.errorMessage)
    }

    @Test
    fun `clearQuery resets search state immediately`() = runTest(testDispatcher) {
        fakeLibraryRepository.itemsResult = Result.success(
            TvItemsPage(items = listOf(sampleMovie), totalRecordCount = 1, startIndex = 0)
        )
        fakePersonRepository.personsResult = Result.success(listOf(sampleActor))

        val viewModel = SearchViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
        )

        viewModel.onQueryChanged("Action")
        advanceTimeBy(350L)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasSearched)

        viewModel.clearQuery()
        advanceTimeBy(350L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertFalse(state.hasSearched)
        assertTrue(state.movies.isEmpty())
        assertTrue(state.actors.isEmpty())
    }

    @Test
    fun `partial search failure preserves successful section`() = runTest(testDispatcher) {
        fakeLibraryRepository.itemsResult = Result.success(
            TvItemsPage(items = listOf(sampleMovie), totalRecordCount = 1, startIndex = 0)
        )
        fakePersonRepository.personsResult = Result.failure(RuntimeException("Actors service unavailable"))

        val viewModel = SearchViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
        )

        viewModel.onQueryChanged("Action")
        advanceTimeBy(350L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasSearched)
        assertEquals(1, state.movies.size)
        assertTrue(state.actors.isEmpty())
        assertNull(state.errorMessage) // Not both failed, so no blocker error
    }

    @Test
    fun `resolver results are returned alongside Jellyfin results`() = runTest(testDispatcher) {
        val video = VideoDto(
            id = "telegram:video-7",
            providerVideoId = "video-7",
            title = "Resolver Action",
        )
        fakeChannelRepository.searchResult = Result.success(listOf(video))

        val viewModel = SearchViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
            channelRepository = fakeChannelRepository,
        )

        viewModel.onQueryChanged("Action")
        advanceTimeBy(350L)
        advanceUntilIdle()

        assertEquals(listOf(video), viewModel.uiState.value.channelVideos)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `search records query to SearchHistoryStore`() = runTest(testDispatcher) {
        val fakeSearchStore = FakeSearchHistoryStore()
        fakeLibraryRepository.itemsResult = Result.success(
            TvItemsPage(items = listOf(sampleMovie), totalRecordCount = 1, startIndex = 0)
        )

        val viewModel = SearchViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
            searchHistoryStore = fakeSearchStore,
        )

        viewModel.onQueryChanged("Action")
        advanceTimeBy(350L)
        advanceUntilIdle()

        assertEquals(listOf("Action"), fakeSearchStore.recordedQueries)
    }

    @Test
    fun `search deduplicates movies and channel videos by providerVideoId`() = runTest(testDispatcher) {
        val movie1 = TvItem(id = "m-1", title = "Movie 1", providerVideoId = "p-1")
        val movie2Dup = TvItem(id = "m-2", title = "Movie 1 Dup", providerVideoId = "p-1")
        val movie3 = TvItem(id = "m-3", title = "Movie 3", providerVideoId = "p-3")

        val video1 = VideoDto(id = "tg-1", title = "Vid 1", providerVideoId = "v-1")
        val video2Dup = VideoDto(id = "tg-2", title = "Vid 1 Dup", providerVideoId = "v-1")

        fakeLibraryRepository.itemsResult = Result.success(
            TvItemsPage(items = listOf(movie1, movie2Dup, movie3), totalRecordCount = 3, startIndex = 0)
        )
        fakeChannelRepository.searchResult = Result.success(listOf(video1, video2Dup))

        val viewModel = SearchViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
            channelRepository = fakeChannelRepository,
        )

        viewModel.onQueryChanged("Action")
        advanceTimeBy(350L)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.movies.size)
        assertEquals("m-1", viewModel.uiState.value.movies[0].id)
        assertEquals("m-3", viewModel.uiState.value.movies[1].id)

        assertEquals(1, viewModel.uiState.value.channelVideos.size)
        assertEquals("tg-1", viewModel.uiState.value.channelVideos[0].id)
    }

    @Test
    fun `setLastSelectedKey updates state`() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
        )

        viewModel.setLastSelectedKey("movie:123")
        assertEquals("movie:123", viewModel.uiState.value.lastSelectedKey)

        viewModel.setLastSelectedKey(null)
        assertNull(viewModel.uiState.value.lastSelectedKey)
    }
}

private class FakeSearchHistoryStore : SearchHistoryStore(mockk(relaxed = true), mockk(relaxed = true)) {
    val recordedQueries = mutableListOf<String>()

    override suspend fun recordQuery(query: String) {
        recordedQueries.add(query)
    }
}

private class FakeSearchLibraryRepo : LibraryRepository(mockk(relaxed = true), mockk(relaxed = true)) {
    var itemsResult: Result<TvItemsPage> = Result.success(TvItemsPage(emptyList(), 0, 0))

    override suspend fun getItems(
        parentId: String?,
        startIndex: Int,
        limit: Int,
        sortBy: String,
        sortOrder: org.jellyfin.sdk.model.api.SortOrder,
        filters: Set<org.jellyfin.sdk.model.api.ItemFilter>,
        searchTerm: String?,
        genreIds: List<java.util.UUID>,
        studioIds: List<java.util.UUID>,
        personIds: List<java.util.UUID>,
    ): Result<TvItemsPage> {
        return itemsResult
    }
}

private class FakeSearchPersonRepo : PersonRepository(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)) {
    var personsResult: Result<List<TvPerson>> = Result.success(emptyList())

    override suspend fun getPersons(
        parentId: String?,
        startIndex: Int,
        limit: Int,
        searchTerm: String?,
        nameStartsWith: String?,
    ): Result<List<TvPerson>> {
        return personsResult
    }
}

private class FakeSearchChannelRepo : ChannelRepository(mockk<ResolverApi>(relaxed = true)) {
    var searchResult: Result<List<VideoDto>> = Result.success(emptyList())

    override suspend fun search(query: String): Result<List<VideoDto>> = searchResult
}
