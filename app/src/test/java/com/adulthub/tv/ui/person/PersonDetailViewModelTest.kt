package com.adulthub.tv.ui.person

import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.repository.PersonRepository
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakePersonRepository = FakePersonRepository()
    private val clientManager: JellyfinClientManager = mockk(relaxed = true)

    private val samplePerson = TvPerson(
        id = "person-1",
        name = "Eva Elfie",
        bio = "Russian performer",
        birthDate = "1997-05-27",
        nationality = "Russian",
        movieCount = 12,
    )

    private val sampleMovie = TvItem(
        id = "movie-1",
        title = "Eva Scene 1",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePersonRepository.personDetailsResult = Result.success(samplePerson)
        fakePersonRepository.moviesResult = Result.success(listOf(sampleMovie))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadPerson loads details and filmography`() = runTest(testDispatcher) {
        val viewModel = PersonDetailViewModel(
            personRepository = fakePersonRepository,
            clientManager = clientManager,
        )

        viewModel.loadPerson("person-1", "Eva Elfie")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.person)
        assertEquals("person-1", state.person?.id)
        assertEquals("Eva Elfie", state.person?.name)
        assertEquals("Russian", state.person?.nationality)

        assertEquals(1, state.movies.size)
        assertEquals("movie-1", state.movies.first().id)
    }

    @Test
    fun `onMovieClicked emits NavigateToItemDetail`() = runTest(testDispatcher) {
        val viewModel = PersonDetailViewModel(
            personRepository = fakePersonRepository,
            clientManager = clientManager,
        )

        viewModel.loadPerson("person-1", "Eva Elfie")
        advanceUntilIdle()

        val events = mutableListOf<PersonDetailNavEvent>()
        val job = launch {
            viewModel.navEvents.collect { events.add(it) }
        }

        viewModel.onMovieClicked(sampleMovie)
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(PersonDetailNavEvent.NavigateToItemDetail("movie-1"), events.first())

        job.cancel()
    }

    private class FakePersonRepository : PersonRepository(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)) {
        var personDetailsResult: Result<TvPerson> = Result.failure(RuntimeException("Not found"))
        var moviesResult: Result<List<TvItem>> = Result.success(emptyList())

        override suspend fun getPersonDetails(personId: String, personName: String): Result<TvPerson> = personDetailsResult

        override suspend fun getPersonMovies(personId: String, parentId: String?, startIndex: Int, limit: Int): Result<List<TvItem>> = moviesResult
    }
}
