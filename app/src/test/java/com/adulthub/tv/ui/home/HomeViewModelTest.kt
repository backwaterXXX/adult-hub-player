package com.adulthub.tv.ui.home

import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.PersonRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeLibraryRepository = FakeLibraryRepository()
    private val fakePersonRepository = FakePersonRepository()
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val clientManager: JellyfinClientManager = mockk(relaxed = true)

    private val sampleItem = TvItem(
        id = "movie-1",
        title = "Sample Movie",
        runtimeTicks = 36000000000L,
        genres = listOf("Action"),
    )

    private val samplePerson = TvPerson(
        id = "person-1",
        name = "Eva Elfie",
        primaryImageTag = "image-tag",
        movieCount = 15,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { settingsDataStore.getSavedLibraryName() } returns "VIP Movies"
        fakeLibraryRepository.resumeResult = Result.success(listOf(sampleItem))
        fakeLibraryRepository.latestResult = Result.success(listOf(sampleItem))
        fakeLibraryRepository.randomResult = Result.success(listOf(sampleItem))
        fakeLibraryRepository.singleRandomResult = Result.success(sampleItem)
        fakePersonRepository.personsResult = Result.success(listOf(samplePerson))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads all sections concurrently and updates state`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
            clientManager = clientManager,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("VIP Movies", state.libraryName)
        assertFalse(state.resumeSection.isLoading)
        assertEquals(1, state.resumeSection.items.size)
        assertEquals("movie-1", state.resumeSection.items.first().id)

        assertFalse(state.latestSection.isLoading)
        assertEquals(1, state.latestSection.items.size)

        assertFalse(state.randomSection.isLoading)
        assertEquals(1, state.randomSection.items.size)

        assertFalse(state.performersSection.isLoading)
        assertEquals(1, state.performersSection.items.size)
        assertEquals("Eva Elfie", state.performersSection.items.first().name)
    }

    @Test
    fun `failure in one section isolates error and keeps other sections intact`() = runTest(testDispatcher) {
        fakeLibraryRepository.resumeResult = Result.failure(RuntimeException("Network error"))

        val viewModel = HomeViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
            clientManager = clientManager,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.resumeSection.errorMessage)
        assertTrue(state.resumeSection.items.isEmpty())

        // Other sections succeeded
        assertFalse(state.latestSection.isLoading)
        assertEquals(1, state.latestSection.items.size)
        assertFalse(state.randomSection.isLoading)
        assertEquals(1, state.randomSection.items.size)
        assertFalse(state.performersSection.isLoading)
        assertEquals(1, state.performersSection.items.size)
    }

    @Test
    fun `changing excluded tags immediately filters already loaded home sections`() = runTest(testDispatcher) {
        val excludedTags = MutableStateFlow(emptySet<String>())
        every { settingsDataStore.excludedTags } returns excludedTags
        fakeLibraryRepository.latestResult = Result.success(listOf(sampleItem))
        val viewModel = HomeViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
            clientManager = clientManager,
        )

        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.latestSection.items.size)

        excludedTags.value = setOf("Action")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.latestSection.items.isEmpty())
        assertTrue(viewModel.uiState.value.randomSection.items.isEmpty())
    }

    @Test
    fun `changing selected library reloads home sections`() = runTest(testDispatcher) {
        val selectedLibraryId = MutableStateFlow<String?>("library-1")
        every { settingsDataStore.selectedLibraryId } returns selectedLibraryId
        val viewModel = HomeViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
            clientManager = clientManager,
        )

        advanceUntilIdle()
        assertEquals(1, fakeLibraryRepository.latestRequests)

        selectedLibraryId.value = "library-2"
        advanceUntilIdle()

        assertEquals(2, fakeLibraryRepository.latestRequests)
    }

    @Test
    fun `retry on failed section reloads only that section`() = runTest(testDispatcher) {
        fakeLibraryRepository.resumeResult = Result.failure(RuntimeException("First attempt failed"))

        val viewModel = HomeViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
            clientManager = clientManager,
        )

        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.resumeSection.errorMessage)

        // Fix failure for retry
        fakeLibraryRepository.resumeResult = Result.success(listOf(sampleItem))
        viewModel.loadResume()
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value
        assertEquals(null, updatedState.resumeSection.errorMessage)
        assertEquals(1, updatedState.resumeSection.items.size)
    }

    @Test
    fun `onMovieClicked emits NavigateToItemDetail event`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
            clientManager = clientManager,
        )

        val events = mutableListOf<HomeNavEvent>()
        val job = launch {
            viewModel.navEvents.collect { events.add(it) }
        }

        viewModel.onMovieClicked(sampleItem)
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(HomeNavEvent.NavigateToItemDetail("movie-1"), events.first())

        job.cancel()
    }

    @Test
    fun `onMovieClicked for telegram movie emits NavigateToPlayer event`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
            clientManager = clientManager,
        )

        val events = mutableListOf<HomeNavEvent>()
        val job = launch {
            viewModel.navEvents.collect { events.add(it) }
        }

        val tgItem = TvItem(
            id = "-100123:456",
            title = "Telegram Vid",
            providerVideoId = "-100123:456",
        )
        viewModel.onMovieClicked(tgItem)
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(HomeNavEvent.NavigateToPlayer("-100123:456"), events.first())

        job.cancel()
    }

    @Test
    fun `onSurpriseMeClicked emits NavigateToItemDetail for random movie`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            libraryRepository = fakeLibraryRepository,
            personRepository = fakePersonRepository,
            settingsDataStore = settingsDataStore,
            clientManager = clientManager,
        )

        val events = mutableListOf<HomeNavEvent>()
        val job = launch {
            viewModel.navEvents.collect { events.add(it) }
        }

        viewModel.onSurpriseMeClicked()
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(HomeNavEvent.NavigateToItemDetail("movie-1"), events.first())

        job.cancel()
    }

    private class FakeLibraryRepository : LibraryRepository(mockk(relaxed = true), mockk(relaxed = true)) {
        var resumeResult: Result<List<TvItem>> = Result.success(emptyList())
        var latestResult: Result<List<TvItem>> = Result.success(emptyList())
        var randomResult: Result<List<TvItem>> = Result.success(emptyList())
        var singleRandomResult: Result<TvItem?> = Result.success(null)
        var latestRequests = 0

        override suspend fun getResumeItems(limit: Int): Result<List<TvItem>> = resumeResult
        override suspend fun getLatestItems(parentId: String?, limit: Int): Result<List<TvItem>> {
            latestRequests += 1
            return latestResult
        }
        override suspend fun getRandomItems(
            parentId: String?,
            limit: Int,
            filters: Set<org.jellyfin.sdk.model.api.ItemFilter>,
        ): Result<List<TvItem>> = randomResult
        override suspend fun getRandomItem(parentId: String?, excludeItemId: String?): Result<TvItem?> = singleRandomResult
    }

    private class FakePersonRepository : PersonRepository(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)) {
        var personsResult: Result<List<TvPerson>> = Result.success(emptyList())

        override suspend fun getPersons(
            parentId: String?,
            startIndex: Int,
            limit: Int,
            searchTerm: String?,
            nameStartsWith: String?,
        ): Result<List<TvPerson>> = personsResult

        override suspend fun getRandomPersonsWithPhotos(limit: Int): Result<List<TvPerson>> =
            personsResult.map { it.filter { person -> !person.primaryImageTag.isNullOrBlank() }.take(limit) }
    }
}
