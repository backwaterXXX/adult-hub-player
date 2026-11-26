package com.adulthub.tv.ui.library

import android.app.Application
import com.adulthub.tv.data.api.AdultHubClientManager
import com.adulthub.tv.data.model.DurationFilter
import com.adulthub.tv.data.model.ItemFilter
import com.adulthub.tv.data.model.SortOrder
import com.adulthub.tv.data.model.TvCategory
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.TvItemsPage
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RandomTabControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = RecordingLibraryRepository()
    private val context: Application = mockk(relaxed = true)
    private val state = MutableStateFlow(LibraryUiState())

    private fun controller(offset: Int = 0) = RandomTabController(
        scope = kotlinx.coroutines.CoroutineScope(dispatcher),
        uiState = state,
        libraryRepository = repository,
        context = context,
        randomOffset = { offset }
    )

    private fun movie(id: String, genres: List<String> = emptyList(), played: Boolean = false) = TvItem(
        id = id,
        title = id,
        genres = genres,
        isPlayed = played
    )

    @Test
    fun `a sample probes the catalogue size and then reads one window`() = runTest(dispatcher) {
        repository.total = 500
        repository.page = { offset, limit ->
            Result.success(TvItemsPage((offset until offset + limit).map { movie("m-$it") }, 500, offset))
        }

        controller(offset = 120).loadRandom(reset = true)
        advanceUntilIdle()

        assertEquals(listOf(1, RANDOM_PAGE_SIZE), repository.requestedLimits)
        assertEquals(listOf(0, 120), repository.requestedOffsets)
        val randomState = state.value.randomState
        assertEquals(500, randomState.totalCount)
        assertEquals(RANDOM_PAGE_SIZE, randomState.items.size)
        assertTrue(randomState.hasMore)
    }

    @Test
    fun `the offset never runs past the last full window`() = runTest(dispatcher) {
        repository.total = 25
        repository.page = { offset, limit ->
            Result.success(TvItemsPage((offset until offset + limit).map { movie("m-$it") }, 25, offset))
        }

        controller(offset = 999).loadRandom(reset = true)
        advanceUntilIdle()

        assertEquals(listOf(0, 0), repository.requestedOffsets)
        assertEquals(25, state.value.randomState.totalCount)
    }

    @Test
    fun `loading more drops ids that are already shown`() = runTest(dispatcher) {
        repository.total = 100
        repository.page = { offset, limit ->
            Result.success(TvItemsPage((offset until offset + limit).map { movie("m-$it") }, 100, offset))
        }
        val controller = controller(offset = 0)
        controller.loadRandom(reset = true)
        advanceUntilIdle()
        val firstPage = state.value.randomState.items.map { it.id }.toSet()

        repository.page = { _, limit ->
            Result.success(TvItemsPage((0 until limit).map { movie("m-$it") }, 100, 0))
        }
        controller.loadRandom(reset = false)
        advanceUntilIdle()

        val ids = state.value.randomState.items.map { it.id }
        assertEquals("a random window may overlap the previous sample", ids.distinct(), ids)
        assertTrue(ids.take(firstPage.size).toSet().containsAll(firstPage))
    }

    @Test
    fun `shuffle clears the previous sample before refilling`() = runTest(dispatcher) {
        repository.total = 100
        repository.page = { offset, limit ->
            Result.success(TvItemsPage((offset until offset + limit).map { movie("m-$it") }, 100, offset))
        }
        val controller = controller(offset = 0)
        controller.loadRandom(reset = true)
        advanceUntilIdle()

        repository.page = { offset, limit ->
            Result.success(TvItemsPage((offset until offset + limit).map { movie("n-$it") }, 100, offset))
        }
        controller.shuffle()
        advanceUntilIdle()

        val ids = state.value.randomState.items.map { it.id }
        assertEquals(RANDOM_PAGE_SIZE, ids.size)
        assertTrue(ids.all { it.startsWith("n-") })
    }

    @Test
    fun `the favorite and duration filters travel to the server`() = runTest(dispatcher) {
        repository.total = 10
        repository.page = { offset, limit ->
            Result.success(TvItemsPage((offset until offset + limit).map { movie("m-$it") }, 10, offset))
        }
        val controller = controller()
        controller.toggleFavoritesOnly()
        advanceUntilIdle()
        controller.setDurationFilter(DurationFilter.SHORT)
        advanceUntilIdle()

        assertTrue(ItemFilter.IS_FAVORITE in repository.lastFilters)
        // "Short" means up to 15 minutes, so only the upper bound is sent.
        assertEquals(null, repository.lastMinDuration)
        assertEquals(15 * 60, repository.lastMaxDuration)
    }

    @Test
    fun `the selected genre is sent as both id and name`() = runTest(dispatcher) {
        repository.total = 10
        repository.page = { offset, limit ->
            Result.success(TvItemsPage((offset until offset + limit).map { movie("m-$it") }, 10, offset))
        }
        val genreId = UUID.randomUUID().toString()
        controller().selectGenre(TvCategory(id = genreId, name = "Action"))
        advanceUntilIdle()

        assertEquals(listOf(UUID.fromString(genreId)), repository.lastGenreIds)
        assertEquals("Action", repository.lastCategory)
    }

    @Test
    fun `three empty samples stop the load more action`() = runTest(dispatcher) {
        repository.total = 100
        repository.page = { _, _ -> Result.success(TvItemsPage(emptyList(), 100, 0)) }
        val controller = controller()
        controller.loadRandom(reset = true)
        advanceUntilIdle()

        repeat(RANDOM_MAX_EMPTY_SAMPLES) {
            controller.loadRandom(reset = false)
            advanceUntilIdle()
        }

        assertFalse(state.value.randomState.hasMore)
    }

    @Test
    fun `a failure surfaces as an error and keeps the tab usable`() = runTest(dispatcher) {
        repository.total = 100
        repository.page = { _, _ -> Result.failure(IllegalStateException("boom")) }
        controller().loadRandom(reset = true)
        advanceUntilIdle()

        val randomState = state.value.randomState
        assertFalse(randomState.isLoading)
        assertTrue(randomState.errorMessage != null)
    }

    private class RecordingLibraryRepository :
        LibraryRepository(
            mockk<AdultHubClientManager>(relaxed = true),
            mockk(relaxed = true)
        ) {
        var total = 0
        var page: ((Int, Int) -> Result<TvItemsPage>)? = null
        val requestedOffsets = mutableListOf<Int>()
        val requestedLimits = mutableListOf<Int>()
        var lastFilters: Set<ItemFilter> = emptySet()
        var lastMinDuration: Int? = null
        var lastMaxDuration: Int? = null
        var lastGenreIds: List<UUID> = emptyList()
        var lastCategory: String? = null

        override suspend fun getItems(
            parentId: String?,
            startIndex: Int,
            limit: Int,
            sortBy: String,
            sortOrder: SortOrder,
            filters: Set<ItemFilter>,
            searchTerm: String?,
            genreIds: List<UUID>,
            studioIds: List<UUID>,
            personIds: List<UUID>,
            category: String?,
            tag: String?,
            studio: String?,
            performer: String?,
            performerId: String?,
            minDuration: Int?,
            maxDuration: Int?
        ): Result<TvItemsPage> {
            requestedOffsets += startIndex
            requestedLimits += limit
            lastFilters = filters
            lastMinDuration = minDuration
            lastMaxDuration = maxDuration
            lastGenreIds = genreIds
            lastCategory = category
            val provider = page ?: return Result.success(TvItemsPage(emptyList(), total, startIndex))
            return provider(startIndex, limit)
        }
    }
}
