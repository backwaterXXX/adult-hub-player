package com.adulthub.tv.ui.home

import com.adulthub.tv.diagnostics.AppLog as Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.local.TelegramProgressStore
import com.adulthub.tv.data.local.WatchHistoryStore
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.PersonRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import androidx.compose.runtime.Immutable
import org.jellyfin.sdk.model.api.ItemFilter

@Immutable
data class SectionState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && items.isEmpty()
}

@Immutable
data class HomeCategoryRow(
    val categoryId: String,
    val categoryName: String,
    val items: List<TvItem>,
)

@Immutable
data class HomeUiState(
    val libraryName: String = "",
    val resumeSection: SectionState<TvItem> = SectionState(isLoading = true),
    val watchHistorySection: SectionState<TvItem> = SectionState(isLoading = true),
    val latestSection: SectionState<TvItem> = SectionState(isLoading = true),
    val randomSection: SectionState<TvItem> = SectionState(isLoading = true),
    val randomUnwatchedOnly: Boolean = false,
    val performersSection: SectionState<TvPerson> = SectionState(isLoading = true),
    val categoryRows: List<HomeCategoryRow> = emptyList(),
    val excludedTags: Set<String> = emptySet(),
)

sealed interface HomeNavEvent {
    data class NavigateToItemDetail(val itemId: String) : HomeNavEvent
    data class NavigateToPlayer(val itemId: String) : HomeNavEvent
    data class NavigateToPersonDetail(val personId: String, val personName: String) : HomeNavEvent
    data class NavigateToLibraryCategory(val categoryId: String) : HomeNavEvent
    data object NavigateToLibrary : HomeNavEvent
    data object NavigateToSearch : HomeNavEvent
    data object NavigateToSettings : HomeNavEvent
}

class HomeViewModel(
    private val libraryRepository: LibraryRepository,
    private val personRepository: PersonRepository,
    private val settingsDataStore: SettingsDataStore,
    private val clientManager: JellyfinClientManager,
    private val telegramProgressStore: TelegramProgressStore? = null,
    private val watchHistoryStore: WatchHistoryStore? = null,
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _navEvents = MutableSharedFlow<HomeNavEvent>()
    val navEvents: SharedFlow<HomeNavEvent> = _navEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.excludedTags.collect { excluded ->
                _uiState.update { it.copy(excludedTags = excluded) }
            }
        }
        viewModelScope.launch {
            settingsDataStore.selectedHomeCategoryIds.collectLatest { selectedIds ->
                loadCategoryRows(selectedIds)
            }
        }
        loadAll()
    }

    private fun filterExcluded(items: List<TvItem>): List<TvItem> {
        val excluded = _uiState.value.excludedTags
        if (excluded.isEmpty()) return items
        return items.filter { item ->
            val hasExcludedGenre = item.genres.any { g -> excluded.any { it.equals(g, ignoreCase = true) } }
            val hasExcludedTag = item.tags.any { t -> excluded.any { it.equals(t, ignoreCase = true) } }
            !hasExcludedGenre && !hasExcludedTag
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            val libName = settingsDataStore.getSavedLibraryName().orEmpty()
            _uiState.update { it.copy(libraryName = libName) }
        }
        loadResume()
        loadWatchHistory()
        loadLatest()
        loadRandom()
        loadPerformers()
    }

    fun loadResume() {
        viewModelScope.launch {
            _uiState.update { it.copy(resumeSection = it.resumeSection.copy(isLoading = true, errorMessage = null)) }
            val result = libraryRepository.getResumeItems(limit = 15)
            val telegramItems = telegramProgressStore?.allProgress?.firstOrNull().orEmpty()
                .filter { !it.isPlayed && it.positionMs > 15_000L && (it.durationMs <= 0 || it.positionMs < it.durationMs * 0.90f) }
                .map { record ->
                    TvItem(
                        id = record.providerVideoId,
                        title = record.title,
                        posterUrl = record.thumbnailUrl,
                        playbackPositionTicks = record.positionMs * 10_000L,
                        runtimeTicks = record.durationMs * 10_000L,
                        providerVideoId = record.providerVideoId,
                        channelId = record.channelId,
                        isPlayed = false,
                    )
                }

            _uiState.update { state ->
                result.fold(
                    onSuccess = { items ->
                        val combined = filterExcluded(
                            (telegramItems + items).distinctBy { it.providerVideoId?.ifBlank { null } ?: it.id }
                        )
                        state.copy(resumeSection = SectionState(items = combined, isLoading = false))
                    },
                    onFailure = { err ->
                        val filteredTelegram = filterExcluded(telegramItems)
                        if (filteredTelegram.isNotEmpty()) {
                            state.copy(resumeSection = SectionState(items = filteredTelegram, isLoading = false))
                        } else {
                            state.copy(resumeSection = SectionState(errorMessage = err.localizedMessage ?: "Failed to load continue watching", isLoading = false))
                        }
                    }
                )
            }
        }
    }

    fun loadWatchHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(watchHistorySection = it.watchHistorySection.copy(isLoading = true, errorMessage = null)) }
            val records = watchHistoryStore?.history?.firstOrNull().orEmpty()
            val items = filterExcluded(
                records.map { it.toTvItem() }.distinctBy { it.providerVideoId?.ifBlank { null } ?: it.id }
            )
            _uiState.update { it.copy(watchHistorySection = SectionState(items = items, isLoading = false)) }
        }
    }

    fun loadLatest() {
        viewModelScope.launch {
            _uiState.update { it.copy(latestSection = it.latestSection.copy(isLoading = true, errorMessage = null)) }
            val result = libraryRepository.getLatestItems(limit = 20)
            _uiState.update { state ->
                result.fold(
                    onSuccess = { items ->
                        val filtered = filterExcluded(items)
                        state.copy(latestSection = SectionState(items = filtered, isLoading = false))
                    },
                    onFailure = { err ->
                        state.copy(latestSection = SectionState(errorMessage = err.localizedMessage ?: "Failed to load recently added", isLoading = false))
                    }
                )
            }
        }
    }

    fun loadRandom() {
        viewModelScope.launch {
            _uiState.update { it.copy(randomSection = it.randomSection.copy(isLoading = true, errorMessage = null)) }
            val filters = if (_uiState.value.randomUnwatchedOnly) setOf(ItemFilter.IS_UNPLAYED) else emptySet()
            val result = libraryRepository.getRandomItems(limit = 20, filters = filters)
            _uiState.update { state ->
                result.fold(
                    onSuccess = { items ->
                        val filtered = filterExcluded(items)
                        state.copy(randomSection = SectionState(items = filtered, isLoading = false))
                    },
                    onFailure = { err ->
                        state.copy(randomSection = SectionState(errorMessage = err.localizedMessage ?: "Failed to load random picks", isLoading = false))
                    }
                )
            }
        }
    }

    fun toggleRandomUnwatchedOnly() {
        _uiState.update { it.copy(randomUnwatchedOnly = !it.randomUnwatchedOnly) }
        loadRandom()
    }

    private suspend fun loadCategoryRows(selectedIds: Set<String>?) {
        val genresResult = libraryRepository.getGenres(limit = 200)
        val genres = genresResult.getOrDefault(emptyList())
        val categoryIds = selectedIds ?: genres.take(2).map { it.id }.toSet()
        val rows = mutableListOf<HomeCategoryRow>()
        for (genre in genres.filter { it.id in categoryIds }) {
            val moviesResult = libraryRepository.getGenreMovies(genreName = genre.name, genreId = genre.id, limit = 10)
            val movies = moviesResult.getOrDefault(emptyList())
            val filtered = filterExcluded(movies).distinctBy { it.providerVideoId?.ifBlank { null } ?: it.id }
            if (filtered.isNotEmpty()) {
                rows.add(HomeCategoryRow(categoryId = genre.id, categoryName = genre.name, items = filtered))
            }
        }
        _uiState.update { it.copy(categoryRows = rows) }
    }

    fun onCategorySeeAllClicked(categoryId: String) {
        viewModelScope.launch {
            _navEvents.emit(HomeNavEvent.NavigateToLibraryCategory(categoryId))
        }
    }

    fun loadPerformers() {
        viewModelScope.launch {
            _uiState.update { it.copy(performersSection = it.performersSection.copy(isLoading = true, errorMessage = null)) }
            val result = personRepository.getRandomPersonsWithPhotos(limit = 20)
            _uiState.update { state ->
                result.fold(
                    onSuccess = { items ->
                        state.copy(performersSection = SectionState(items = items, isLoading = false))
                    },
                    onFailure = { err ->
                        state.copy(performersSection = SectionState(errorMessage = err.localizedMessage ?: "Failed to load performers", isLoading = false))
                    }
                )
            }
        }
    }

    fun getItemImageUrl(item: TvItem): String = clientManager.getItemImageUrl(item)

    fun getPersonImageUrl(person: TvPerson): String = clientManager.getPersonImageUrl(person.id, person.primaryImageTag)

    fun onMovieClicked(item: TvItem) {
        viewModelScope.launch {
            if (item.providerVideoId != null || item.id.startsWith("telegram:")) {
                _navEvents.emit(HomeNavEvent.NavigateToPlayer(item.id))
            } else {
                _navEvents.emit(HomeNavEvent.NavigateToItemDetail(item.id))
            }
        }
    }

    fun onPersonClicked(person: TvPerson) {
        viewModelScope.launch {
            _navEvents.emit(HomeNavEvent.NavigateToPersonDetail(person.id, person.name))
        }
    }

    fun onSurpriseMeClicked() {
        viewModelScope.launch {
            val randomItem = libraryRepository.getRandomItem().getOrNull()
            if (randomItem != null) {
                _navEvents.emit(HomeNavEvent.NavigateToItemDetail(randomItem.id))
            } else {
                val candidate = _uiState.value.randomSection.items.shuffled().firstOrNull()
                    ?: _uiState.value.latestSection.items.shuffled().firstOrNull()
                if (candidate != null) {
                    _navEvents.emit(HomeNavEvent.NavigateToItemDetail(candidate.id))
                }
            }
        }
    }

    fun onLibraryClicked() {
        viewModelScope.launch {
            _navEvents.emit(HomeNavEvent.NavigateToLibrary)
        }
    }

    fun onSearchClicked() {
        viewModelScope.launch {
            _navEvents.emit(HomeNavEvent.NavigateToSearch)
        }
    }

    fun onSettingsClicked() {
        viewModelScope.launch {
            _navEvents.emit(HomeNavEvent.NavigateToSettings)
        }
    }
}
