package com.adulthub.tv.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.ChannelSortMode
import com.adulthub.tv.data.model.DurationFilter
import com.adulthub.tv.data.model.TvCategory
import com.adulthub.tv.data.model.TvChannel
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.model.TvStudio
import com.adulthub.tv.data.repository.ChannelRepository
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.PersonRepository
import com.adulthub.tv.data.repository.StudioRepository
import com.adulthub.tv.data.resolver.VideoDto
import com.adulthub.tv.data.resolver.resolveResolverUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID

enum class LibraryTab(val title: String) {
    ALL("Movies"),
    CATEGORIES("Categories"),
    ACTORS("Actors"),
    STUDIOS("Studios"),
    CHANNELS("Channels"),
}

data class AllItemsTabState(
    val items: List<TvItem> = emptyList(),
    val rawItems: List<TvItem> = emptyList(),
    val durationFilter: DurationFilter = DurationFilter.ALL,
    val hdOnly: Boolean = false,
    val excludedTags: Set<String> = emptySet(),
    val totalCount: Int = 0,
    val startIndex: Int = 0,
    val pageSize: Int = 40,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val sortBy: String = "DateCreated",
    val sortOrder: SortOrder = SortOrder.DESCENDING,
    val favoritesOnly: Boolean = false,
    val unwatchedOnly: Boolean = false,
    val genres: List<TvCategory> = emptyList(),
    val areGenresLoaded: Boolean = false,
    val isLoadingGenres: Boolean = false,
    val studios: List<TvStudio> = emptyList(),
    val areStudiosLoaded: Boolean = false,
    val isLoadingStudios: Boolean = false,
    val performers: List<TvPerson> = emptyList(),
    val arePerformersLoaded: Boolean = false,
    val isLoadingPerformers: Boolean = false,
    val selectedGenre: TvCategory? = null,
    val selectedStudio: TvStudio? = null,
    val selectedPerformer: TvPerson? = null,
    val lastSelectedItemId: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && items.isEmpty()
}

data class CategoriesTabState(
    val categories: List<TvCategory> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedCategory: TvCategory? = null,
    val categoryMovies: List<TvItem> = emptyList(),
    val isLoadingCategoryMovies: Boolean = false,
    val isLoadingMoreCategoryMovies: Boolean = false,
    val hasMoreCategoryMovies: Boolean = false,
    val totalCount: Int = 0,
    val categoryMoviesError: String? = null,
    val lastSelectedCategoryId: String? = null,
    val lastSelectedMovieId: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && categories.isEmpty()
}

data class ActorsTabState(
    val allPersons: List<TvPerson> = emptyList(),
    val filteredPersons: List<TvPerson> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val totalCount: Int = 0,
    val errorMessage: String? = null,
    val selectedLetter: Char? = null,
    val onlyWithPhoto: Boolean = false,
    val searchQuery: String = "",
    val lastSelectedPersonId: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && filteredPersons.isEmpty()
}

data class StudiosTabState(
    val studios: List<TvStudio> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedStudio: TvStudio? = null,
    val studioMovies: List<TvItem> = emptyList(),
    val isLoadingStudioMovies: Boolean = false,
    val isLoadingMoreStudioMovies: Boolean = false,
    val hasMoreStudioMovies: Boolean = false,
    val totalCount: Int = 0,
    val studioMoviesError: String? = null,
    val lastSelectedStudioId: String? = null,
    val lastSelectedMovieId: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && studios.isEmpty()
}

data class ChannelsTabState(
    val channels: List<TvChannel> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedChannel: TvChannel? = null,
    val sortMode: ChannelSortMode = ChannelSortMode.LATEST,
    val durationFilter: DurationFilter = DurationFilter.ALL,
    val excludedTags: Set<String> = emptySet(),
    val hiddenChannelIds: Set<String> = emptySet(),
    val rawChannelVideos: List<VideoDto> = emptyList(),
    val channelVideos: List<VideoDto> = emptyList(),
    val isLoadingVideos: Boolean = false,
    val isLoadingMoreVideos: Boolean = false,
    val hasMoreVideos: Boolean = false,
    val nextOffsetId: Long? = null,
    val totalMessageCount: Int = 0,
    val channelVideosError: String? = null,
    val resolverBaseUrl: String = SettingsDataStore.DEFAULT_RESOLVER_URL,
    val lastSelectedChannelId: String? = null,
    val lastSelectedVideoId: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && channels.isEmpty()
}

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.ALL,
    val allState: AllItemsTabState = AllItemsTabState(),
    val categoriesState: CategoriesTabState = CategoriesTabState(),
    val actorsState: ActorsTabState = ActorsTabState(),
    val studiosState: StudiosTabState = StudiosTabState(),
    val channelsState: ChannelsTabState = ChannelsTabState(),
)

sealed interface LibraryNavEvent {
    data class NavigateToItemDetail(val itemId: String) : LibraryNavEvent
    data class NavigateToPersonDetail(val personId: String, val personName: String) : LibraryNavEvent
    data class NavigateToPlayer(val itemId: String) : LibraryNavEvent
}

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val personRepository: PersonRepository,
    private val studioRepository: StudioRepository,
    private val channelRepository: ChannelRepository,
    private val clientManager: JellyfinClientManager,
    private val settingsDataStore: SettingsDataStore? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _navEvents = MutableSharedFlow<LibraryNavEvent>()
    val navEvents: SharedFlow<LibraryNavEvent> = _navEvents.asSharedFlow()
    private var actorsLoadJob: Job? = null
    private var categoryMoviesJob: Job? = null
    private var studioMoviesJob: Job? = null
    private var allItemsLoadJob: Job? = null
    private var allItemsGeneration = 0L
    private var allItemsRequestInFlight = false

    init {
        loadAllItems(reset = true)
        settingsDataStore?.let { settings ->
            viewModelScope.launch {
                settings.resolverBaseUrl.collect { url ->
                    _uiState.update { state ->
                        state.copy(
                            channelsState = state.channelsState.copy(resolverBaseUrl = url),
                        )
                    }
                }
            }
            viewModelScope.launch {
                settings.channelSortMode.collect { mode ->
                    _uiState.update { state ->
                        val sorted = applyChannelFilters(
                            state.channelsState.rawChannelVideos,
                            state.channelsState.durationFilter,
                            state.channelsState.excludedTags,
                            mode,
                        )
                        state.copy(
                            channelsState = state.channelsState.copy(
                                sortMode = mode,
                                channelVideos = sorted,
                            )
                        )
                    }
                }
            }
            viewModelScope.launch {
                settings.excludedTags.collect { excluded ->
                    _uiState.update { state ->
                        val as_ = state.allState
                        val cs = state.channelsState
                        val allFiltered = applyAllItemsFilters(as_.rawItems, as_.durationFilter, as_.hdOnly, excluded)
                        val chFiltered = applyChannelFilters(cs.rawChannelVideos, cs.durationFilter, excluded, cs.sortMode)
                        state.copy(
                            allState = as_.copy(excludedTags = excluded, items = allFiltered),
                            channelsState = cs.copy(excludedTags = excluded, channelVideos = chFiltered),
                        )
                    }
                }
            }
            viewModelScope.launch {
                settings.hiddenChannelIds.collect { hidden ->
                    _uiState.update { state ->
                        val cs = state.channelsState
                        val visible = cs.channels.filter { it.id !in hidden }
                        state.copy(
                            channelsState = cs.copy(hiddenChannelIds = hidden, channels = visible),
                        )
                    }
                }
            }
        }
    }

    fun loadGenresForFilter() {
        if (_uiState.value.allState.areGenresLoaded || _uiState.value.allState.isLoadingGenres) return
        viewModelScope.launch {
            _uiState.update { it.copy(allState = it.allState.copy(isLoadingGenres = true)) }
            val result = libraryRepository.getGenres()
            _uiState.update { state ->
                state.copy(allState = state.allState.copy(
                    genres = result.getOrDefault(emptyList()).sortedBy { it.name.lowercase() },
                    areGenresLoaded = result.isSuccess,
                    isLoadingGenres = false,
                ))
            }
        }
    }

    fun loadStudiosForFilter() {
        if (_uiState.value.allState.areStudiosLoaded || _uiState.value.allState.isLoadingStudios) return
        viewModelScope.launch {
            _uiState.update { it.copy(allState = it.allState.copy(isLoadingStudios = true)) }
            val result = studioRepository.getStudios(limit = 500)
            _uiState.update { state ->
                state.copy(allState = state.allState.copy(
                    studios = result.getOrDefault(emptyList()).sortedBy { it.name.lowercase() },
                    areStudiosLoaded = result.isSuccess,
                    isLoadingStudios = false,
                ))
            }
        }
    }

    fun loadPerformersForFilter() {
        if (_uiState.value.allState.arePerformersLoaded || _uiState.value.allState.isLoadingPerformers) return
        viewModelScope.launch {
            _uiState.update { it.copy(allState = it.allState.copy(isLoadingPerformers = true)) }
            val result = personRepository.getPersons(limit = 100)
            _uiState.update { state ->
                state.copy(allState = state.allState.copy(
                    performers = result.getOrDefault(emptyList()).sortedBy { it.name.lowercase() },
                    arePerformersLoaded = result.isSuccess,
                    isLoadingPerformers = false,
                ))
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            LibraryTab.ALL -> if (_uiState.value.allState.items.isEmpty()) loadAllItems(reset = true)
            LibraryTab.CATEGORIES -> if (_uiState.value.categoriesState.categories.isEmpty()) loadCategories()
            LibraryTab.ACTORS -> if (_uiState.value.actorsState.allPersons.isEmpty()) loadActors()
            LibraryTab.STUDIOS -> if (_uiState.value.studiosState.studios.isEmpty()) loadStudios()
            LibraryTab.CHANNELS -> if (_uiState.value.channelsState.channels.isEmpty()) loadChannels()
        }
    }

    // --- CATEGORIES TAB ---

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(categoriesState = state.categoriesState.copy(isLoading = true, errorMessage = null))
            }
            val result = libraryRepository.getGenres()
            _uiState.update { state ->
                result.fold(
                    onSuccess = { categories ->
                        state.copy(
                            categoriesState = state.categoriesState.copy(
                                categories = categories.sortedBy { it.name.lowercase() },
                                isLoading = false,
                            )
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            categoriesState = state.categoriesState.copy(
                                isLoading = false,
                                errorMessage = error.localizedMessage ?: "Failed to load categories",
                            )
                        )
                    }
                )
            }
        }
    }

    fun selectCategory(category: TvCategory?) {
        categoryMoviesJob?.cancel()
        _uiState.update { state ->
            state.copy(
                categoriesState = state.categoriesState.copy(
                    selectedCategory = category,
                    lastSelectedCategoryId = category?.id ?: state.categoriesState.lastSelectedCategoryId,
                    categoryMovies = emptyList(),
                    categoryMoviesError = null,
                    isLoadingCategoryMovies = false,
                    isLoadingMoreCategoryMovies = false,
                    hasMoreCategoryMovies = false,
                    totalCount = 0,
                )
            )
        }
        category?.let { loadCategoryMovies(it, reset = true) }
    }

    fun loadMoreCategoryMovies() {
        val catState = _uiState.value.categoriesState
        val category = catState.selectedCategory ?: return
        if (!catState.hasMoreCategoryMovies || catState.isLoadingMoreCategoryMovies || catState.isLoadingCategoryMovies || categoryMoviesJob?.isActive == true) return
        loadCategoryMovies(category, reset = false)
    }

    fun retrySelectedCategoryMovies() {
        val catState = _uiState.value.categoriesState
        val category = catState.selectedCategory ?: return
        val isFirstPage = catState.categoryMovies.isEmpty()
        loadCategoryMovies(category, reset = isFirstPage)
    }

    private fun loadCategoryMovies(category: TvCategory, reset: Boolean = true) {
        val currentState = _uiState.value.categoriesState
        if (!reset && (!currentState.hasMoreCategoryMovies || currentState.isLoadingMoreCategoryMovies || categoryMoviesJob?.isActive == true)) return
        if (reset) categoryMoviesJob?.cancel()

        categoryMoviesJob = viewModelScope.launch {
            _uiState.update { state ->
                if (state.categoriesState.selectedCategory?.id == category.id) {
                    state.copy(
                        categoriesState = state.categoriesState.copy(
                            isLoadingCategoryMovies = reset,
                            isLoadingMoreCategoryMovies = !reset,
                            categoryMoviesError = if (reset) null else state.categoriesState.categoryMoviesError,
                        )
                    )
                } else state
            }

            val startIndex = if (reset) 0 else _uiState.value.categoriesState.categoryMovies.size
            val pageSize = 60
            val result = libraryRepository.getGenreMoviesPage(
                genreName = category.name,
                genreId = category.id,
                startIndex = startIndex,
                limit = pageSize,
            )

            _uiState.update { state ->
                if (state.categoriesState.selectedCategory?.id != category.id) return@update state
                result.fold(
                    onSuccess = { page ->
                        val combined = if (reset) {
                            page.items
                        } else {
                            state.categoriesState.categoryMovies + page.items
                        }.distinctBy { it.providerVideoId?.ifBlank { null } ?: it.id }

                        val hasMore = page.items.isNotEmpty() && (startIndex + page.items.size < page.totalRecordCount)
                        state.copy(
                            categoriesState = state.categoriesState.copy(
                                categoryMovies = combined,
                                totalCount = page.totalRecordCount,
                                isLoadingCategoryMovies = false,
                                isLoadingMoreCategoryMovies = false,
                                categoryMoviesError = null,
                                hasMoreCategoryMovies = hasMore,
                            )
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            categoriesState = state.categoriesState.copy(
                                isLoadingCategoryMovies = false,
                                isLoadingMoreCategoryMovies = false,
                                categoryMoviesError = error.localizedMessage ?: "Failed to load category movies",
                            )
                        )
                    }
                )
            }
        }
    }

    // --- ALL ITEMS TAB ---

    fun loadAllItems(reset: Boolean = false, autoFetchEmptyAttempts: Int = 0) {
        if (reset) {
            allItemsGeneration += 1
            allItemsLoadJob?.cancel()
            allItemsRequestInFlight = false
        } else if (allItemsRequestInFlight) {
            return
        }

        val generation = allItemsGeneration
        val currentState = _uiState.value.allState
        val startIndex = if (reset) 0 else currentState.rawItems.size
        if (!reset && (!currentState.hasMore || currentState.isLoadingMore)) return
        allItemsRequestInFlight = true
        allItemsLoadJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    allState = if (reset) {
                        state.allState.copy(isLoading = true, errorMessage = null, items = emptyList(), rawItems = emptyList())
                    } else {
                        state.allState.copy(isLoadingMore = true)
                    }
                )
            }

            val filters = buildSet {
                if (currentState.favoritesOnly) add(ItemFilter.IS_FAVORITE)
                if (currentState.unwatchedOnly) add(ItemFilter.IS_UNPLAYED)
            }
            val result = libraryRepository.getItems(
                startIndex = startIndex,
                limit = currentState.pageSize,
                sortBy = currentState.sortBy,
                sortOrder = currentState.sortOrder,
                filters = filters,
                genreIds = listOfNotNull(currentState.selectedGenre?.id?.toUuidOrNull()),
                studioIds = listOfNotNull(currentState.selectedStudio?.id?.toUuidOrNull()),
                personIds = listOfNotNull(currentState.selectedPerformer?.id?.toUuidOrNull()),
            )

            if (generation != allItemsGeneration) return@launch
            allItemsRequestInFlight = false

            var shouldAutoFetch = false
            _uiState.update { state ->
                result.fold(
                    onSuccess = { page ->
                        val rawCombined = if (reset) page.items else state.allState.rawItems + page.items
                        val filtered = applyAllItemsFilters(
                            rawCombined,
                            state.allState.durationFilter,
                            state.allState.hdOnly,
                            state.allState.excludedTags,
                        )
                        val hasMore = rawCombined.size < page.totalRecordCount
                        if (filtered.isEmpty() && hasMore && autoFetchEmptyAttempts < 3) {
                            shouldAutoFetch = true
                        }
                        state.copy(
                            allState = state.allState.copy(
                                rawItems = rawCombined,
                                items = filtered,
                                totalCount = page.totalRecordCount,
                                startIndex = startIndex,
                                isLoading = false,
                                isLoadingMore = false,
                                hasMore = hasMore,
                            )
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            allState = state.allState.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                errorMessage = error.localizedMessage ?: "Failed to load movies",
                            )
                        )
                    }
                )
            }
            if (shouldAutoFetch) {
                loadAllItems(reset = false, autoFetchEmptyAttempts = autoFetchEmptyAttempts + 1)
            }
        }
    }

    fun setDurationFilter(filter: DurationFilter) {
        _uiState.update { state ->
            val as_ = state.allState
            val filtered = applyAllItemsFilters(as_.rawItems, filter, as_.hdOnly, as_.excludedTags)
            state.copy(allState = as_.copy(durationFilter = filter, items = filtered))
        }
    }

    fun toggleHdOnly() {
        _uiState.update { state ->
            val as_ = state.allState
            val newHd = !as_.hdOnly
            val filtered = applyAllItemsFilters(as_.rawItems, as_.durationFilter, newHd, as_.excludedTags)
            state.copy(allState = as_.copy(hdOnly = newHd, items = filtered))
        }
    }

    private fun applyAllItemsFilters(
        items: List<TvItem>,
        durationFilter: DurationFilter,
        hdOnly: Boolean,
        excludedTags: Set<String>,
    ): List<TvItem> {
        return items.filter { item ->
            if (excludedTags.isNotEmpty()) {
                val hasExcludedGenre = item.genres.any { g -> excludedTags.any { it.equals(g, ignoreCase = true) } }
                val hasExcludedTag = item.tags.any { t -> excludedTags.any { it.equals(t, ignoreCase = true) } }
                if (hasExcludedGenre || hasExcludedTag) return@filter false
            }
            if (hdOnly && !item.isHd) {
                return@filter false
            }
            val durationMinutes = item.runtimeMinutes ?: (item.runtimeTicks?.div(10_000_000L * 60L)?.toInt()) ?: 0
            when (durationFilter) {
                DurationFilter.ALL -> true
                DurationFilter.SHORT -> durationMinutes in 1..14
                DurationFilter.MEDIUM -> durationMinutes in 15..40
                DurationFilter.LONG -> durationMinutes > 40
            }
        }
    }

    fun setSort(sortBy: String, sortOrder: SortOrder) {
        _uiState.update { state ->
            state.copy(allState = state.allState.copy(sortBy = sortBy, sortOrder = sortOrder))
        }
        loadAllItems(reset = true)
    }

    fun toggleFavoritesOnly() {
        _uiState.update { state ->
            state.copy(allState = state.allState.copy(favoritesOnly = !state.allState.favoritesOnly))
        }
        loadAllItems(reset = true)
    }

    fun toggleUnwatchedOnly() {
        _uiState.update { state ->
            state.copy(allState = state.allState.copy(unwatchedOnly = !state.allState.unwatchedOnly))
        }
        loadAllItems(reset = true)
    }

    fun selectGenre(genre: TvCategory?) {
        _uiState.update { state -> state.copy(allState = state.allState.copy(selectedGenre = genre)) }
        loadAllItems(reset = true)
    }

    fun selectCatalogStudio(studio: TvStudio?) {
        _uiState.update { state -> state.copy(allState = state.allState.copy(selectedStudio = studio)) }
        loadAllItems(reset = true)
    }

    fun selectPerformer(performer: TvPerson?) {
        _uiState.update { state -> state.copy(allState = state.allState.copy(selectedPerformer = performer)) }
        loadAllItems(reset = true)
    }

    fun onSurpriseMeClicked() {
        viewModelScope.launch {
            val filters = _uiState.value.allState
            val item = libraryRepository.getRandomItemFiltered(
                genreIds = listOfNotNull(filters.selectedGenre?.id?.toUuidOrNull()),
                studioIds = listOfNotNull(filters.selectedStudio?.id?.toUuidOrNull()),
                personIds = listOfNotNull(filters.selectedPerformer?.id?.toUuidOrNull()),
                filters = buildSet {
                    if (filters.favoritesOnly) add(ItemFilter.IS_FAVORITE)
                    if (filters.unwatchedOnly) add(ItemFilter.IS_UNPLAYED)
                },
            ).getOrNull() ?: filters.items.shuffled().firstOrNull()
            item?.let { _navEvents.emit(LibraryNavEvent.NavigateToItemDetail(it.id)) }
        }
    }

    // --- ACTORS TAB ---

    fun loadActors(reset: Boolean = true) {
        val current = _uiState.value.actorsState
        if (!reset && (!current.hasMore || current.isLoadingMore || actorsLoadJob?.isActive == true)) return
        if (reset) actorsLoadJob?.cancel()
        actorsLoadJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(actorsState = state.actorsState.copy(
                    isLoading = reset,
                    isLoadingMore = !reset,
                    errorMessage = null,
                    allPersons = if (reset) emptyList() else state.actorsState.allPersons,
                    filteredPersons = if (reset) emptyList() else state.actorsState.filteredPersons,
                    hasMore = if (reset) true else state.actorsState.hasMore,
                    totalCount = if (reset) 0 else state.actorsState.totalCount,
                ))
            }
            val requestState = _uiState.value.actorsState
            val startIndex = if (reset) 0 else requestState.allPersons.size
            val pageSize = 100
            val result = personRepository.getPersonsPage(
                startIndex = startIndex,
                limit = pageSize,
                searchTerm = requestState.searchQuery.takeIf(String::isNotBlank),
                nameStartsWith = requestState.selectedLetter?.toString(),
            )
            _uiState.update { state ->
                val actors = state.actorsState
                result.fold(
                    onSuccess = { page ->
                        val combined = (if (reset) emptyList() else actors.allPersons)
                            .plus(page.persons)
                            .distinctBy(TvPerson::id)
                            .sortedBy { it.name.lowercase() }
                        val filtered = filterActors(
                            combined,
                            actors.selectedLetter,
                            actors.onlyWithPhoto,
                            actors.searchQuery,
                        )
                        state.copy(actorsState = actors.copy(
                            allPersons = combined,
                            filteredPersons = filtered,
                            totalCount = page.totalRecordCount,
                            isLoading = false,
                            isLoadingMore = false,
                            hasMore = startIndex + page.persons.size < page.totalRecordCount,
                            errorMessage = null,
                        ))
                    },
                    onFailure = { error ->
                        state.copy(actorsState = actors.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = error.localizedMessage ?: "Failed to load actors",
                        ))
                    },
                )
            }
        }
    }

    fun loadMoreActors() = loadActors(reset = false)

    fun selectAlphabetLetter(letter: Char?) {
        _uiState.update { state ->
            val updatedLetter = if (state.actorsState.selectedLetter == letter) null else letter
            val filtered = filterActors(state.actorsState.allPersons, updatedLetter, state.actorsState.onlyWithPhoto, state.actorsState.searchQuery)
            state.copy(
                actorsState = state.actorsState.copy(
                    selectedLetter = updatedLetter,
                    filteredPersons = filtered,
                )
            )
        }
        loadActors(reset = true)
    }

    fun toggleOnlyWithPhoto() {
        _uiState.update { state ->
            val newOnly = !state.actorsState.onlyWithPhoto
            val filtered = filterActors(state.actorsState.allPersons, state.actorsState.selectedLetter, newOnly, state.actorsState.searchQuery)
            state.copy(
                actorsState = state.actorsState.copy(
                    onlyWithPhoto = newOnly,
                    filteredPersons = filtered,
                )
            )
        }
    }

    fun setActorSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = filterActors(state.actorsState.allPersons, state.actorsState.selectedLetter, state.actorsState.onlyWithPhoto, query)
            state.copy(
                actorsState = state.actorsState.copy(
                    searchQuery = query,
                    filteredPersons = filtered,
                )
            )
        }
        loadActors(reset = true)
    }

private fun filterActors(
        all: List<TvPerson>,
        letter: Char?,
        onlyWithPhoto: Boolean,
        query: String,
    ): List<TvPerson> {
        return all.filter { person ->
            val matchesLetter = letter == null || person.name.startsWith(letter, ignoreCase = true)
            val matchesPhoto = !onlyWithPhoto || !person.primaryImageTag.isNullOrBlank()
            val matchesSearch = query.isBlank() || person.name.contains(query, ignoreCase = true)
            matchesLetter && matchesPhoto && matchesSearch
        }
    }

    // --- STUDIOS TAB ---

    fun loadStudios() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(studiosState = state.studiosState.copy(isLoading = true, errorMessage = null))
            }
            val result = studioRepository.getStudios(limit = 100)
            _uiState.update { state ->
                result.fold(
                    onSuccess = { studios ->
                        state.copy(
                            studiosState = state.studiosState.copy(
                                studios = studios.sortedBy { it.name.lowercase() },
                                isLoading = false,
                            )
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            studiosState = state.studiosState.copy(
                                isLoading = false,
                                errorMessage = error.localizedMessage ?: "Failed to load studios",
                            )
                        )
                    }
                )
            }
        }
    }

    fun selectStudio(studio: TvStudio?) {
        studioMoviesJob?.cancel()
        _uiState.update { state ->
            state.copy(
                studiosState = state.studiosState.copy(
                    selectedStudio = studio,
                    lastSelectedStudioId = studio?.id ?: state.studiosState.lastSelectedStudioId,
                    studioMovies = emptyList(),
                    studioMoviesError = null,
                    isLoadingStudioMovies = false,
                    isLoadingMoreStudioMovies = false,
                    hasMoreStudioMovies = false,
                    totalCount = 0,
                )
            )
        }
        studio?.let { loadStudioMovies(it, reset = true) }
    }

    fun loadMoreStudioMovies() {
        val stdState = _uiState.value.studiosState
        val studio = stdState.selectedStudio ?: return
        if (!stdState.hasMoreStudioMovies || stdState.isLoadingMoreStudioMovies || stdState.isLoadingStudioMovies || studioMoviesJob?.isActive == true) return
        loadStudioMovies(studio, reset = false)
    }

    fun retrySelectedStudioMovies() {
        val stdState = _uiState.value.studiosState
        val studio = stdState.selectedStudio ?: return
        val isFirstPage = stdState.studioMovies.isEmpty()
        loadStudioMovies(studio, reset = isFirstPage)
    }

    private fun loadStudioMovies(studio: TvStudio, reset: Boolean = true) {
        val currentState = _uiState.value.studiosState
        if (!reset && (!currentState.hasMoreStudioMovies || currentState.isLoadingMoreStudioMovies || studioMoviesJob?.isActive == true)) return
        if (reset) studioMoviesJob?.cancel()

        studioMoviesJob = viewModelScope.launch {
            _uiState.update { state ->
                if (state.studiosState.selectedStudio?.id == studio.id) {
                    state.copy(
                        studiosState = state.studiosState.copy(
                            isLoadingStudioMovies = reset,
                            isLoadingMoreStudioMovies = !reset,
                            studioMoviesError = if (reset) null else state.studiosState.studioMoviesError,
                        )
                    )
                } else state
            }

            val startIndex = if (reset) 0 else _uiState.value.studiosState.studioMovies.size
            val pageSize = 50
            val result = studioRepository.getStudioMoviesPage(
                studioName = studio.name,
                startIndex = startIndex,
                limit = pageSize,
            )

            _uiState.update { state ->
                if (state.studiosState.selectedStudio?.id != studio.id) return@update state
                result.fold(
                    onSuccess = { page ->
                        val combined = if (reset) {
                            page.items
                        } else {
                            state.studiosState.studioMovies + page.items
                        }.distinctBy { it.providerVideoId?.ifBlank { null } ?: it.id }

                        val hasMore = page.items.isNotEmpty() && (startIndex + page.items.size < page.totalRecordCount)
                        state.copy(
                            studiosState = state.studiosState.copy(
                                studioMovies = combined,
                                totalCount = page.totalRecordCount,
                                isLoadingStudioMovies = false,
                                isLoadingMoreStudioMovies = false,
                                studioMoviesError = null,
                                hasMoreStudioMovies = hasMore,
                            )
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            studiosState = state.studiosState.copy(
                                isLoadingStudioMovies = false,
                                isLoadingMoreStudioMovies = false,
                                studioMoviesError = error.localizedMessage ?: "Failed to load studio movies",
                            )
                        )
                    }
                )
            }
        }
    }

    // --- CHANNELS TAB ---

    fun loadChannels() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(channelsState = state.channelsState.copy(isLoading = true, errorMessage = null))
            }
            val result = channelRepository.getCategories()
            _uiState.update { state ->
                val hidden = state.channelsState.hiddenChannelIds
                result.fold(
                    onSuccess = { channels ->
                        val visible = channels.filter { it.id !in hidden }
                        state.copy(
                            channelsState = state.channelsState.copy(
                                channels = visible,
                                isLoading = false,
                            )
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            channelsState = state.channelsState.copy(
                                isLoading = false,
                                errorMessage = error.localizedMessage ?: "Failed to load channels",
                            )
                        )
                    }
                )
            }
        }
    }

    fun hideCurrentChannel() {
        val currentChannel = _uiState.value.channelsState.selectedChannel ?: return
        viewModelScope.launch {
            settingsDataStore?.hideChannel(currentChannel.id)
            selectChannel(null)
            loadChannels()
        }
    }

    fun setChannelDurationFilter(filter: DurationFilter) {
        _uiState.update { state ->
            val cs = state.channelsState
            val filtered = applyChannelFilters(cs.rawChannelVideos, filter, cs.excludedTags, cs.sortMode)
            state.copy(
                channelsState = cs.copy(
                    durationFilter = filter,
                    channelVideos = filtered,
                )
            )
        }
    }

    private var channelVideosJob: Job? = null

    fun selectChannel(channel: TvChannel?) {
        channelVideosJob?.cancel()
        _uiState.update { state ->
            state.copy(channelsState = state.channelsState.copy(
                selectedChannel = channel,
                lastSelectedChannelId = channel?.id ?: state.channelsState.lastSelectedChannelId,
                rawChannelVideos = emptyList(),
                channelVideos = emptyList(),
                channelVideosError = null,
                isLoadingVideos = false,
                isLoadingMoreVideos = false,
                hasMoreVideos = false,
                nextOffsetId = null,
                totalMessageCount = 0,
            ))
        }
        channel?.let { loadChannelVideos(it, reset = true) }
    }

    fun setChannelSortMode(mode: ChannelSortMode) {
        _uiState.update { state ->
            val cs = state.channelsState
            val sorted = applyChannelFilters(cs.rawChannelVideos, cs.durationFilter, cs.excludedTags, mode)
            state.copy(
                channelsState = cs.copy(
                    sortMode = mode,
                    channelVideos = sorted,
                )
            )
        }
        viewModelScope.launch {
            settingsDataStore?.setChannelSortMode(mode)
        }
    }

    private fun sortChannelVideos(videos: List<VideoDto>, mode: ChannelSortMode): List<VideoDto> {
        return when (mode) {
            ChannelSortMode.LATEST -> videos
            ChannelSortMode.LONGEST -> videos.sortedWith(
                compareByDescending<VideoDto> { it.duration }
                    .thenByDescending { it.id }
            )
            ChannelSortMode.SHORTEST -> videos.sortedWith(
                compareBy<VideoDto> { if (it.duration <= 0.0) Double.MAX_VALUE else it.duration }
                    .thenBy { it.id }
            )
        }
    }

    private fun applyChannelFilters(
        videos: List<VideoDto>,
        durationFilter: DurationFilter,
        excludedTags: Set<String>,
        mode: ChannelSortMode,
    ): List<VideoDto> {
        val filtered = videos.filter { video ->
            if (excludedTags.isNotEmpty()) {
                val hasExcludedCat = video.categories.any { cat -> excludedTags.any { it.equals(cat, ignoreCase = true) } }
                val hasExcludedTitle = excludedTags.any { video.title.contains(it, ignoreCase = true) }
                val hasExcludedPerformer = video.performers.any { p -> excludedTags.any { it.equals(p, ignoreCase = true) } }
                if (hasExcludedCat || hasExcludedTitle || hasExcludedPerformer) return@filter false
            }
            val minutes = (video.duration / 60.0).toInt()
            when (durationFilter) {
                DurationFilter.ALL -> true
                DurationFilter.SHORT -> minutes in 1..14
                DurationFilter.MEDIUM -> minutes in 15..40
                DurationFilter.LONG -> minutes > 40
            }
        }
        return sortChannelVideos(filtered, mode)
    }

    fun retrySelectedChannelVideos() {
        val channel = _uiState.value.channelsState.selectedChannel ?: return
        val isFirstPage = _uiState.value.channelsState.channelVideos.isEmpty()
        loadChannelVideos(channel, reset = isFirstPage)
    }

    fun loadMoreChannelVideos() {
        val channelsState = _uiState.value.channelsState
        val channel = channelsState.selectedChannel ?: return
        if (!channelsState.hasMoreVideos || channelsState.isLoadingMoreVideos || channelsState.isLoadingVideos || channelVideosJob?.isActive == true) return
        loadChannelVideos(channel, reset = false)
    }

    private fun loadChannelVideos(channel: TvChannel, reset: Boolean = true, autoFetchEmptyAttempts: Int = 0) {
        val currentState = _uiState.value.channelsState
        if (!reset && (!currentState.hasMoreVideos || currentState.isLoadingMoreVideos || channelVideosJob?.isActive == true)) return
        if (reset) channelVideosJob?.cancel()

        channelVideosJob = viewModelScope.launch {
            _uiState.update { state ->
                if (state.channelsState.selectedChannel?.id == channel.id) {
                    state.copy(channelsState = state.channelsState.copy(
                        isLoadingVideos = reset,
                        isLoadingMoreVideos = !reset,
                        channelVideosError = if (reset) null else state.channelsState.channelVideosError,
                    ))
                } else state
            }

            val currentOffset = if (reset) null else _uiState.value.channelsState.nextOffsetId
            val historyResult = channelRepository.getChannelHistory(
                channelId = channel.id,
                offsetId = currentOffset,
                limit = 100,
            )

            var shouldAutoFetch = false
            _uiState.update { state ->
                if (state.channelsState.selectedChannel?.id != channel.id) return@update state
                historyResult.fold(
                    onSuccess = { page ->
                        val combined = if (reset) {
                            page.videos.distinctBy { it.providerVideoId.ifBlank { it.id } }
                        } else {
                            (state.channelsState.rawChannelVideos + page.videos).distinctBy { it.providerVideoId.ifBlank { it.id } }
                        }
                        val filtered = applyChannelFilters(
                            combined,
                            state.channelsState.durationFilter,
                            state.channelsState.excludedTags,
                            state.channelsState.sortMode,
                        )
                        val hasMore = page.hasMore && page.nextOffsetId != null && page.nextOffsetId != currentOffset
                        if (filtered.isEmpty() && hasMore && autoFetchEmptyAttempts < 3) {
                            shouldAutoFetch = true
                        }
                        state.copy(channelsState = state.channelsState.copy(
                            rawChannelVideos = combined,
                            channelVideos = filtered,
                            isLoadingVideos = false,
                            isLoadingMoreVideos = false,
                            channelVideosError = null,
                            hasMoreVideos = hasMore,
                            nextOffsetId = page.nextOffsetId,
                            totalMessageCount = page.messageCount,
                        ))
                    },
                    onFailure = { error ->
                        if (reset) {
                            // Fallback to getCategoryVideos on initial load if getChannelHistory fails
                            val fallbackResult = channelRepository.getCategoryVideos(channelId = channel.id)
                            fallbackResult.fold(
                                onSuccess = { fallbackVideos ->
                                    val distinct = fallbackVideos.distinctBy { it.providerVideoId.ifBlank { it.id } }
                                    val sortedFallback = applyChannelFilters(
                                        distinct,
                                        state.channelsState.durationFilter,
                                        state.channelsState.excludedTags,
                                        state.channelsState.sortMode,
                                    )
                                    state.copy(channelsState = state.channelsState.copy(
                                        rawChannelVideos = distinct,
                                        channelVideos = sortedFallback,
                                        isLoadingVideos = false,
                                        isLoadingMoreVideos = false,
                                        channelVideosError = null,
                                        hasMoreVideos = false,
                                        nextOffsetId = null,
                                        totalMessageCount = distinct.size,
                                    ))
                                },
                                onFailure = { fallbackError ->
                                    state.copy(channelsState = state.channelsState.copy(
                                        rawChannelVideos = emptyList(),
                                        channelVideos = emptyList(),
                                        isLoadingVideos = false,
                                        isLoadingMoreVideos = false,
                                        channelVideosError = fallbackError.localizedMessage ?: error.localizedMessage ?: "Failed to load channel videos",
                                        hasMoreVideos = false,
                                        nextOffsetId = null,
                                    ))
                                }
                            )
                        } else {
                            state.copy(channelsState = state.channelsState.copy(
                                isLoadingMoreVideos = false,
                                channelVideosError = error.localizedMessage ?: "Failed to load more channel videos",
                            ))
                        }
                    },
                )
            }
            if (shouldAutoFetch) {
                loadChannelVideos(channel, reset = false, autoFetchEmptyAttempts = autoFetchEmptyAttempts + 1)
            }
        }
    }

    // --- NAVIGATION & IMAGES ---

    fun getItemImageUrl(item: TvItem): String = clientManager.getItemImageUrl(item)

    fun getPersonImageUrl(person: TvPerson): String =
        if (!person.primaryImageTag.isNullOrBlank()) {
            clientManager.getPersonImageUrl(person.id, person.primaryImageTag, maxWidth = 300)
        } else if (!person.photoUrl.isNullOrBlank()) {
            person.photoUrl
        } else {
            clientManager.getPersonImageUrl(person.id, null, maxWidth = 300)
        }

    fun getChannelVideoThumbnailUrl(thumbnail: String?): String {
        return resolveResolverUrl(thumbnail, _uiState.value.channelsState.resolverBaseUrl)
    }

    fun onMovieClicked(item: TvItem) {
        _uiState.update { state ->
            when (state.selectedTab) {
                LibraryTab.ALL -> state.copy(allState = state.allState.copy(lastSelectedItemId = item.id))
                LibraryTab.CATEGORIES -> state.copy(categoriesState = state.categoriesState.copy(lastSelectedMovieId = item.id))
                LibraryTab.STUDIOS -> state.copy(studiosState = state.studiosState.copy(lastSelectedMovieId = item.id))
                else -> state
            }
        }
        viewModelScope.launch {
            _navEvents.emit(LibraryNavEvent.NavigateToItemDetail(item.id))
        }
    }

    fun onPersonClicked(person: TvPerson) {
        _uiState.update {
            it.copy(actorsState = it.actorsState.copy(lastSelectedPersonId = person.id))
        }
        viewModelScope.launch {
            _navEvents.emit(LibraryNavEvent.NavigateToPersonDetail(person.id, person.name))
        }
    }

    fun onChannelVideoClicked(video: VideoDto) {
        _uiState.update { state ->
            state.copy(channelsState = state.channelsState.copy(lastSelectedVideoId = video.id))
        }
        channelRepository.cacheVideo(video)
        val playId = video.providerVideoId.ifBlank { video.id }
        viewModelScope.launch {
            _navEvents.emit(LibraryNavEvent.NavigateToPlayer(playId))
        }
    }
}

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
