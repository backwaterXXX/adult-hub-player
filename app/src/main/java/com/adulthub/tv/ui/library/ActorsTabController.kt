package com.adulthub.tv.ui.library

import android.app.Application
import com.adulthub.tv.R
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.repository.PersonRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the Actors tab: the paged person list, the letter and photo filters, and the
 * debounced server search.
 */
internal class ActorsTabController(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<LibraryUiState>,
    private val personRepository: PersonRepository,
    private val context: Application
) {

    private var actorsLoadJob: Job? = null
    private var actorSearchJob: Job? = null

    fun loadActors(reset: Boolean = true) {
        val current = uiState.value.actorsState
        if (!reset && (!current.hasMore || current.isLoadingMore || actorsLoadJob?.isActive == true)) return
        if (reset) actorsLoadJob?.cancel()
        actorsLoadJob = scope.launch {
            uiState.update { state ->
                state.copy(
                    actorsState = state.actorsState.copy(
                        isLoading = reset,
                        isLoadingMore = !reset,
                        errorMessage = null,
                        allPersons = if (reset) emptyList() else state.actorsState.allPersons,
                        filteredPersons = if (reset) emptyList() else state.actorsState.filteredPersons,
                        hasMore = if (reset) true else state.actorsState.hasMore,
                        totalCount = if (reset) 0 else state.actorsState.totalCount
                    )
                )
            }
            val requestState = uiState.value.actorsState
            val startIndex = if (reset) 0 else requestState.allPersons.size
            val pageSize = 100
            val result = personRepository.getPersonsPage(
                startIndex = startIndex,
                limit = pageSize,
                searchTerm = requestState.searchQuery.takeIf(String::isNotBlank),
                nameStartsWith = requestState.selectedLetter?.toString()
            )
            uiState.update { state ->
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
                            actors.searchQuery
                        )
                        state.copy(
                            actorsState = actors.copy(
                                allPersons = combined,
                                filteredPersons = filtered,
                                totalCount = page.totalRecordCount,
                                isLoading = false,
                                isLoadingMore = false,
                                hasMore = startIndex + page.persons.size < page.totalRecordCount,
                                errorMessage = null
                            )
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            actorsState = actors.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                errorMessage = error.localizedMessage ?: context.getString(R.string.library_error_actors)
                            )
                        )
                    }
                )
            }
        }
    }

    fun loadMoreActors() = loadActors(reset = false)

    fun selectAlphabetLetter(letter: Char?) {
        uiState.update { state ->
            val updatedLetter = if (state.actorsState.selectedLetter == letter) null else letter
            val filtered = filterActors(state.actorsState.allPersons, updatedLetter, state.actorsState.onlyWithPhoto, state.actorsState.searchQuery)
            state.copy(
                actorsState = state.actorsState.copy(
                    selectedLetter = updatedLetter,
                    filteredPersons = filtered
                )
            )
        }
        loadActors(reset = true)
    }

    fun toggleOnlyWithPhoto() {
        uiState.update { state ->
            val newOnly = !state.actorsState.onlyWithPhoto
            val filtered = filterActors(state.actorsState.allPersons, state.actorsState.selectedLetter, newOnly, state.actorsState.searchQuery)
            state.copy(
                actorsState = state.actorsState.copy(
                    onlyWithPhoto = newOnly,
                    filteredPersons = filtered
                )
            )
        }
    }

    fun setActorSearchQuery(query: String) {
        uiState.update { state ->
            val filtered = filterActors(state.actorsState.allPersons, state.actorsState.selectedLetter, state.actorsState.onlyWithPhoto, query)
            state.copy(
                actorsState = state.actorsState.copy(
                    searchQuery = query,
                    filteredPersons = filtered
                )
            )
        }
        // The query is a server side filter, so one request per keystroke would cancel the
        // previous page for a result that is already obsolete.
        actorSearchJob?.cancel()
        actorSearchJob = scope.launch {
            delay(ACTOR_SEARCH_DEBOUNCE_MS)
            loadActors(reset = true)
        }
    }

    private companion object {
        const val ACTOR_SEARCH_DEBOUNCE_MS = 300L
    }
}
