package com.adulthub.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adulthub.tv.data.local.SearchHistoryStore
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.repository.ChannelRepository
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.PersonRepository
import com.adulthub.tv.data.resolver.VideoDto
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val movies: List<TvItem> = emptyList(),
    val actors: List<TvPerson> = emptyList(),
    val channelVideos: List<VideoDto> = emptyList(),
    val resolverBaseUrl: String = SettingsDataStore.DEFAULT_RESOLVER_URL,
    val errorMessage: String? = null,
    val hasSearched: Boolean = false,
    val lastSelectedKey: String? = null,
    val recentQueries: List<String> = emptyList(),
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val libraryRepository: LibraryRepository,
    private val personRepository: PersonRepository,
    private val settingsDataStore: SettingsDataStore,
    private val channelRepository: ChannelRepository? = null,
    private val searchHistoryStore: SearchHistoryStore? = null,
    private val accountScope: com.adulthub.tv.data.local.AccountScope? = null,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private var retryJob: Job? = null

    init {
        searchHistoryStore?.let { store ->
            viewModelScope.launch {
                store.queries.collect { queries ->
                    _uiState.update { it.copy(recentQueries = queries) }
                }
            }
        }
        viewModelScope.launch {
            _query
                .debounce(300L)
                .distinctUntilChanged()
                .collectLatest { q ->
                    if (q.isBlank()) {
                        _uiState.update {
                            it.copy(
                                query = "",
                                isSearching = false,
                                movies = emptyList(),
                                actors = emptyList(),
                                channelVideos = emptyList(),
                                hasSearched = false,
                                errorMessage = null,
                            )
                        }
                    } else {
                        performSearch(q)
                    }
                }
        }
    }

    fun onQueryChanged(newQuery: String) {
        retryJob?.cancel()
        retryJob = null
        _query.value = newQuery
        _uiState.update { it.copy(query = newQuery) }
    }

    fun clearQuery() {
        onQueryChanged("")
    }

    fun retry() {
        val q = _query.value
        if (q.isNotBlank()) {
            retryJob?.cancel()
            retryJob = viewModelScope.launch {
                performSearch(q)
            }
        }
    }

    private suspend fun performSearch(searchTerm: String) {
        // The account that started the search also owns the query that is written
        // when it finished.
        val accountKey = runCatching { accountScope?.currentKey() }.getOrNull()
        _uiState.update {
            it.copy(
                query = searchTerm,
                isSearching = true,
                hasSearched = false,
                movies = emptyList(),
                actors = emptyList(),
                channelVideos = emptyList(),
                errorMessage = null,
            )
        }

        val libraryId = settingsDataStore.selectedLibraryId.firstOrNull()

        coroutineScope {
            val moviesDeferred = async {
                libraryRepository.getItems(
                    parentId = libraryId,
                    searchTerm = searchTerm,
                    limit = 30,
                )
            }

            val personsDeferred = async {
                personRepository.getPersons(
                    parentId = libraryId,
                    searchTerm = searchTerm,
                    limit = 30,
                )
            }
            val channelVideosDeferred = async {
                channelRepository?.search(searchTerm)
            }

            val failures = mutableListOf<Throwable>()
            var successfulSections = 0
            val movieUpdates = launch {
                moviesDeferred.await().fold(
                    onSuccess = { page ->
                        successfulSections++
                        val movies = page.items.distinctBy { it.providerVideoId?.ifBlank { null } ?: it.id }
                        _uiState.update { state ->
                            if (state.query == searchTerm) state.copy(movies = movies) else state
                        }
                    },
                    onFailure = failures::add,
                )
            }
            val personUpdates = launch {
                personsDeferred.await().fold(
                    onSuccess = { actors ->
                        successfulSections++
                        _uiState.update { state ->
                            if (state.query == searchTerm) state.copy(actors = actors) else state
                        }
                    },
                    onFailure = failures::add,
                )
            }
            val channelUpdates = launch {
                channelVideosDeferred.await()?.fold(
                    onSuccess = { videos ->
                        successfulSections++
                        val channelVideos = videos.distinctBy { it.providerVideoId.ifBlank { it.id } }
                        _uiState.update { state ->
                            if (state.query == searchTerm) state.copy(channelVideos = channelVideos) else state
                        }
                    },
                    onFailure = failures::add,
                )
            }
            joinAll(movieUpdates, personUpdates, channelUpdates)
            if (searchTerm != _query.value) return@coroutineScope

            val totalSections = 2 + if (channelRepository != null) 1 else 0
            val error = if (successfulSections == 0 && failures.size == totalSections) {
                failures.firstOrNull()?.message ?: "Search failed. Please try again."
            } else null

            val resolverBaseUrl = try {
                settingsDataStore.getResolverBaseUrl()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                SettingsDataStore.DEFAULT_RESOLVER_URL
            }

            // A response that was superseded by a newer query neither enters the
            // history nor clears the spinner of the search the user is waiting for.
            if (searchTerm != _query.value) return@coroutineScope

            searchHistoryStore?.recordQuery(searchTerm, accountKey)
            if (searchTerm != _query.value) return@coroutineScope

            _uiState.update {
                it.copy(
                    isSearching = false,
                    resolverBaseUrl = resolverBaseUrl,
                    hasSearched = true,
                    errorMessage = error,
                )
            }
        }
    }

    fun setLastSelectedKey(key: String?) {
        _uiState.update { it.copy(lastSelectedKey = key) }
    }
}
