package com.adulthub.tv.ui.library

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adulthub.tv.R
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.SortOrder

/**
 * State and actions of the library screen. Each tab with paging or a search of its own
 * is delegated to a controller; this class keeps the shared state, the All tab, and
 * the navigation events.
 */
class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    private val personRepository: PersonRepository,
    private val studioRepository: StudioRepository,
    private val channelRepository: ChannelRepository,
    private val clientManager: JellyfinClientManager,
    private val settingsDataStore: SettingsDataStore? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val context: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _navEvents = MutableSharedFlow<LibraryNavEvent>()
    val navEvents: SharedFlow<LibraryNavEvent> = _navEvents.asSharedFlow()

    private val categoriesTab = CategoriesTabController(
        scope = viewModelScope,
        uiState = _uiState,
        libraryRepository = libraryRepository,
        context = context
    )
    private val studiosTab = StudiosTabController(
        scope = viewModelScope,
        uiState = _uiState,
        studioRepository = studioRepository,
        context = context
    )
    private val actorsTab = ActorsTabController(
        scope = viewModelScope,
        uiState = _uiState,
        personRepository = personRepository,
        context = context
    )
    private val channelsTab = ChannelsTabController(
        scope = viewModelScope,
        uiState = _uiState,
        channelRepository = channelRepository,
        settingsDataStore = settingsDataStore,
        context = context
    )
    private val savedFilters = LibrarySavedFilters(savedStateHandle)

    private var allItemsLoadJob: Job? = null
    private var allItemsGeneration = 0L
    private var allItemsRequestInFlight = false

    init {
        _uiState.value = savedFilters.restore(_uiState.value)
        loadAllItems(reset = true)
        viewModelScope.launch {
            _uiState.collect { state -> savedFilters.save(state) }
        }
        settingsDataStore?.let { settings ->
            viewModelScope.launch {
                settings.resolverBaseUrl.distinctUntilChanged().collect { url ->
                    _uiState.update { state ->
                        state.copy(
                            channelsState = state.channelsState.copy(resolverBaseUrl = url)
                        )
                    }
                }
            }
            viewModelScope.launch {
                settings.channelSortMode.distinctUntilChanged().collect { mode ->
                    _uiState.update { state ->
                        val sorted = applyChannelFilters(
                            state.channelsState.rawChannelVideos,
                            state.channelsState.durationFilter,
                            state.channelsState.excludedTags,
                            mode
                        )
                        state.copy(
                            channelsState = state.channelsState.copy(
                                sortMode = mode,
                                channelVideos = sorted
                            )
                        )
                    }
                }
            }
            viewModelScope.launch {
                settings.excludedTags.distinctUntilChanged().collect { excluded ->
                    _uiState.update { state ->
                        val allState = state.allState
                        val cs = state.channelsState
                        val allFiltered = applyAllItemsFilters(allState.rawItems, allState.durationFilter, allState.hdOnly, excluded)
                        val chFiltered = applyChannelFilters(cs.rawChannelVideos, cs.durationFilter, excluded, cs.sortMode)
                        state.copy(
                            allState = allState.copy(excludedTags = excluded, items = allFiltered),
                            channelsState = cs.copy(excludedTags = excluded, channelVideos = chFiltered)
                        )
                    }
                }
            }
            viewModelScope.launch {
                settings.hiddenChannelIds.distinctUntilChanged().collect { hidden ->
                    _uiState.update { state ->
                        val cs = state.channelsState
                        val visible = cs.channels.filter { it.id !in hidden }
                        state.copy(
                            channelsState = cs.copy(hiddenChannelIds = hidden, channels = visible)
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
                state.copy(
                    allState = state.allState.copy(
                        genres = result.getOrDefault(emptyList()).sortedBy { it.name.lowercase() },
                        areGenresLoaded = result.isSuccess,
                        isLoadingGenres = false
                    )
                )
            }
        }
    }

    fun loadStudiosForFilter() {
        if (_uiState.value.allState.areStudiosLoaded || _uiState.value.allState.isLoadingStudios) return
        viewModelScope.launch {
            _uiState.update { it.copy(allState = it.allState.copy(isLoadingStudios = true)) }
            // The filter panel only needs the names; counting every studio would
            // add one request per studio to a list of several hundred entries.
            val result = studioRepository.getStudios(limit = 500)
            _uiState.update { state ->
                state.copy(
                    allState = state.allState.copy(
                        studios = result.getOrDefault(emptyList()).sortedBy { it.name.lowercase() },
                        areStudiosLoaded = result.isSuccess,
                        isLoadingStudios = false
                    )
                )
            }
        }
    }

    fun loadPerformersForFilter() {
        if (_uiState.value.allState.arePerformersLoaded || _uiState.value.allState.isLoadingPerformers) return
        viewModelScope.launch {
            _uiState.update { it.copy(allState = it.allState.copy(isLoadingPerformers = true)) }
            val result = personRepository.getPersons(limit = 100)
            _uiState.update { state ->
                state.copy(
                    allState = state.allState.copy(
                        performers = result.getOrDefault(emptyList()).sortedBy { it.name.lowercase() },
                        arePerformersLoaded = result.isSuccess,
                        isLoadingPerformers = false
                    )
                )
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

    // --- CATEGORIES TAB (see CategoriesTabController) ---

    fun openCategory(categoryId: String) = categoriesTab.openCategory(categoryId)

    fun loadCategories() = categoriesTab.loadCategories()

    fun selectCategory(category: TvCategory?) = categoriesTab.selectCategory(category)

    fun loadMoreCategoryMovies() = categoriesTab.loadMoreCategoryMovies()

    fun retrySelectedCategoryMovies() = categoriesTab.retrySelectedCategoryMovies()

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
                personIds = listOfNotNull(currentState.selectedPerformer?.id?.toUuidOrNull())
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
                            state.allState.excludedTags
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
                                hasMore = hasMore
                            )
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            allState = state.allState.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                errorMessage = error.localizedMessage ?: context.getString(R.string.library_error_movies)
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
            val allState = state.allState
            val filtered = applyAllItemsFilters(allState.rawItems, filter, allState.hdOnly, allState.excludedTags)
            state.copy(allState = allState.copy(durationFilter = filter, items = filtered))
        }
    }

    fun toggleHdOnly() {
        _uiState.update { state ->
            val allState = state.allState
            val newHd = !allState.hdOnly
            val filtered = applyAllItemsFilters(allState.rawItems, allState.durationFilter, newHd, allState.excludedTags)
            state.copy(allState = allState.copy(hdOnly = newHd, items = filtered))
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
                }
            ).getOrNull() ?: filters.items.shuffled().firstOrNull()
            item?.let { _navEvents.emit(LibraryNavEvent.NavigateToItemDetail(it.id)) }
        }
    }

    // --- ACTORS TAB (see ActorsTabController) ---

    fun loadActors(reset: Boolean = true) = actorsTab.loadActors(reset)

    fun loadMoreActors() = actorsTab.loadMoreActors()

    fun selectAlphabetLetter(letter: Char?) = actorsTab.selectAlphabetLetter(letter)

    fun toggleOnlyWithPhoto() = actorsTab.toggleOnlyWithPhoto()

    fun setActorSearchQuery(query: String) = actorsTab.setActorSearchQuery(query)

    // --- STUDIOS TAB (see StudiosTabController) ---

    fun loadStudios() = studiosTab.loadStudios()

    fun selectStudio(studio: TvStudio?) = studiosTab.selectStudio(studio)

    fun loadMoreStudioMovies() = studiosTab.loadMoreStudioMovies()

    fun retrySelectedStudioMovies() = studiosTab.retrySelectedStudioMovies()

    // --- CHANNELS TAB (see ChannelsTabController) ---

    fun loadChannels() = channelsTab.loadChannels()

    fun hideCurrentChannel() = channelsTab.hideCurrentChannel()

    fun setChannelDurationFilter(filter: DurationFilter) = channelsTab.setChannelDurationFilter(filter)

    fun selectChannel(channel: TvChannel?) = channelsTab.selectChannel(channel)

    fun setChannelSortMode(mode: ChannelSortMode) = channelsTab.setChannelSortMode(mode)

    fun retrySelectedChannelVideos() = channelsTab.retrySelectedChannelVideos()

    fun loadMoreChannelVideos() = channelsTab.loadMoreChannelVideos()

    // --- NAVIGATION & IMAGES ---

    fun getItemImageUrl(item: TvItem): String = clientManager.getItemImageUrl(item)

    fun getPersonImageUrl(person: TvPerson): String = if (!person.primaryImageTag.isNullOrBlank()) {
        clientManager.getPersonImageUrl(person.id, person.primaryImageTag, maxWidth = 300)
    } else if (!person.photoUrl.isNullOrBlank()) {
        person.photoUrl
    } else {
        clientManager.getPersonImageUrl(person.id, null, maxWidth = 300)
    }

    fun getChannelVideoThumbnailUrl(thumbnail: String?): String = resolveResolverUrl(thumbnail, _uiState.value.channelsState.resolverBaseUrl)

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
