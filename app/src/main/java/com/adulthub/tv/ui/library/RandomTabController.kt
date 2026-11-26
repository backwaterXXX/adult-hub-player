package com.adulthub.tv.ui.library

import android.app.Application
import com.adulthub.tv.R
import com.adulthub.tv.data.model.DurationFilter
import com.adulthub.tv.data.model.ItemFilter
import com.adulthub.tv.data.model.QualityTier
import com.adulthub.tv.data.model.TvCategory
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.model.TvStudio
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.TvItemsPage
import com.adulthub.tv.ui.components.userFacingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How many movies one random sample returns. */
internal const val RANDOM_PAGE_SIZE = 40

/** Empty samples in a row before the tab stops offering "Load more". */
internal const val RANDOM_MAX_EMPTY_SAMPLES = 3

/**
 * Owns the Random tab: every request walks the catalogue at a random offset
 * instead of in order, so the tab shows a new slice of the library each time.
 *
 * Filters behave like the All tab (duration bounds and the favorite/unplayed
 * flags travel to the server, the rest is applied locally), a sample is shuffled
 * before it reaches the grid, and appending drops ids that are already shown — a
 * random offset can overlap the previous page.
 */
internal class RandomTabController(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<LibraryUiState>,
    private val libraryRepository: LibraryRepository,
    private val context: Application,
    private val randomOffset: (Int) -> Int = { bound -> kotlin.random.Random.nextInt(bound) }
) {

    private var loadJob: Job? = null
    private var generation = 0L
    private var emptySamples = 0

    fun loadRandom(reset: Boolean) {
        val state = uiState.value.randomState
        if (!reset && (state.isLoading || state.isLoadingMore || !state.hasMore)) return
        if (reset) {
            generation += 1
            loadJob?.cancel()
            emptySamples = 0
        }
        val currentGeneration = generation
        loadJob = scope.launch {
            markLoading(reset)
            val filters = uiState.value.randomState
            val result = if (reset) fetchFirstPage(filters) else fetchNextPage(filters)
            if (currentGeneration != generation) return@launch
            uiState.update { current -> applyResult(current, reset, result) }
        }
    }

    fun shuffle() = loadRandom(reset = true)

    /** The first page needs the catalogue size to pick an offset inside it. */
    private suspend fun fetchFirstPage(state: AllItemsTabState): Result<RandomSample> = requestPage(state, offset = 0, limit = 1).mapCatching { probe ->
        val count = probe.totalRecordCount
        if (count <= 0) {
            RandomSample(items = emptyList(), totalCount = 0, hasMore = false)
        } else {
            sampleAt(state, count, offsetWithin(count))
        }
    }

    private suspend fun fetchNextPage(state: AllItemsTabState): Result<RandomSample> {
        if (state.totalCount <= 0) return Result.success(RandomSample(emptyList(), 0, false))
        return runCatching { sampleAt(state, state.totalCount, offsetWithin(state.totalCount)) }
    }

    private suspend fun sampleAt(
        state: AllItemsTabState,
        totalCount: Int,
        offset: Int
    ): RandomSample {
        val page = requestPage(state, offset, RANDOM_PAGE_SIZE).getOrThrow()
        return RandomSample(
            items = page.items.shuffled(),
            totalCount = totalCount,
            hasMore = page.items.isNotEmpty()
        )
    }

    private suspend fun requestPage(
        state: AllItemsTabState,
        offset: Int,
        limit: Int
    ): Result<TvItemsPage> = libraryRepository.getItems(
        startIndex = offset,
        limit = limit,
        filters = filtersOf(state),
        genreIds = listOfNotNull(state.selectedGenre?.id?.toUuidOrNull()),
        studioIds = listOfNotNull(state.selectedStudio?.id?.toUuidOrNull()),
        personIds = listOfNotNull(state.selectedPerformer?.id?.toUuidOrNull()),
        category = state.selectedGenre?.name,
        studio = state.selectedStudio?.name,
        performerId = state.selectedPerformer?.id,
        minDuration = state.durationFilter.minMinutes?.takeIf { it > 0 }?.times(60),
        maxDuration = state.durationFilter.maxMinutes?.times(60),
        quality = state.quality.queryValue
    )

    private fun offsetWithin(totalCount: Int): Int {
        val lastOffset = (totalCount - RANDOM_PAGE_SIZE).coerceAtLeast(0)
        return if (lastOffset == 0) 0 else randomOffset(lastOffset + 1)
    }

    private fun filtersOf(state: AllItemsTabState): Set<ItemFilter> = buildSet {
        if (state.favoritesOnly) add(ItemFilter.IS_FAVORITE)
        if (state.unwatchedOnly) add(ItemFilter.IS_UNPLAYED)
    }

    private fun markLoading(reset: Boolean) {
        uiState.update { current ->
            val randomState = current.randomState
            current.copy(randomState = if (reset) randomState.startingOver() else randomState.appending())
        }
    }

    private fun applyResult(
        current: LibraryUiState,
        reset: Boolean,
        result: Result<RandomSample>
    ): LibraryUiState {
        val failure = result.exceptionOrNull()
        if (failure != null) {
            val message = failure.userFacingMessage(context, R.string.library_error_movies)
            return current.copy(
                randomState = current.randomState.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = message
                )
            )
        }
        return current.copy(randomState = merged(current.randomState, reset, result.getOrThrow()))
    }

    private fun merged(
        state: AllItemsTabState,
        reset: Boolean,
        sample: RandomSample
    ): AllItemsTabState {
        val combined = if (reset) sample.items else state.items + sample.items.excluding(state.items)
        if (!reset) emptySamples = if (sample.items.isEmpty()) emptySamples + 1 else 0
        return state.copy(
            rawItems = combined,
            items = applyAllItemsFilters(
                combined,
                state.durationFilter,
                state.excludedTags,
                state.unwatchedOnly
            ),
            totalCount = if (reset) sample.totalCount else state.totalCount,
            isLoading = false,
            isLoadingMore = false,
            hasMore = if (reset) sample.hasMore else emptySamples < RANDOM_MAX_EMPTY_SAMPLES,
            errorMessage = null
        )
    }

    private fun List<TvItem>.excluding(known: List<TvItem>): List<TvItem> {
        val knownIds = known.mapTo(mutableSetOf()) { it.id }
        return filterNot { it.id in knownIds }
    }

    private fun AllItemsTabState.startingOver(): AllItemsTabState = copy(
        items = emptyList(),
        isLoading = true,
        isLoadingMore = false,
        errorMessage = null,
        hasMore = true,
        totalCount = 0,
        startIndex = 0
    )

    private fun AllItemsTabState.appending(): AllItemsTabState = copy(isLoadingMore = true, errorMessage = null)

    // --- Filters: a new sample, because the previous order is no longer valid. ---

    fun setDurationFilter(filter: DurationFilter) {
        if (uiState.value.randomState.durationFilter == filter) return
        update { it.copy(durationFilter = filter) }
        loadRandom(reset = true)
    }

    fun setQuality(tier: QualityTier) {
        update { it.copy(quality = tier) }
        loadRandom(reset = true)
    }

    fun toggleFavoritesOnly() {
        update { it.copy(favoritesOnly = !it.favoritesOnly) }
        loadRandom(reset = true)
    }

    fun toggleUnwatchedOnly() {
        update { it.copy(unwatchedOnly = !it.unwatchedOnly) }
        loadRandom(reset = true)
    }

    fun selectGenre(genre: TvCategory?) {
        update { it.copy(selectedGenre = genre) }
        loadRandom(reset = true)
    }

    fun selectStudio(studio: TvStudio?) {
        update { it.copy(selectedStudio = studio) }
        loadRandom(reset = true)
    }

    fun selectPerformer(performer: TvPerson?) {
        update { it.copy(selectedPerformer = performer) }
        loadRandom(reset = true)
    }

    private fun update(transform: (AllItemsTabState) -> AllItemsTabState) {
        uiState.update { state -> state.copy(randomState = transform(state.randomState)) }
    }
}

internal data class RandomSample(
    val items: List<TvItem>,
    val totalCount: Int,
    val hasMore: Boolean
)
