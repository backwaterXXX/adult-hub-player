package com.adulthub.tv.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.repository.PersonRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PersonDetailUiState(
    val person: TvPerson? = null,
    val movies: List<TvItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

sealed interface PersonDetailNavEvent {
    data class NavigateToItemDetail(val itemId: String) : PersonDetailNavEvent
    data object NavigateBack : PersonDetailNavEvent
}

class PersonDetailViewModel(
    private val personRepository: PersonRepository,
    private val clientManager: JellyfinClientManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonDetailUiState())
    val uiState: StateFlow<PersonDetailUiState> = _uiState.asStateFlow()

    private val _navEvents = MutableSharedFlow<PersonDetailNavEvent>()
    val navEvents: SharedFlow<PersonDetailNavEvent> = _navEvents.asSharedFlow()

    private var currentPersonId: String? = null

    fun loadPerson(personId: String, personName: String) {
        if (currentPersonId == personId && _uiState.value.person != null) return
        currentPersonId = personId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val personResult = personRepository.getPersonDetails(personId, personName)

            _uiState.update { state ->
                personResult.fold(
                    onSuccess = { person ->
                        state.copy(
                            person = person,
                            isLoading = false,
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Failed to load performer details",
                        )
                    }
                )
            }

            if (personResult.isSuccess) {
                val moviesResult = personRepository.getPersonMovies(personId = personId, limit = 100)
                _uiState.update { state ->
                    state.copy(movies = moviesResult.getOrDefault(emptyList()))
                }
            }
        }
    }

    fun onMovieClicked(item: TvItem) {
        viewModelScope.launch {
            _navEvents.emit(PersonDetailNavEvent.NavigateToItemDetail(item.id))
        }
    }

    fun getPersonImageUrl(person: TvPerson): String =
        if (!person.primaryImageTag.isNullOrBlank()) {
            clientManager.getPersonImageUrl(person.id, person.primaryImageTag, maxWidth = 400)
        } else if (!person.photoUrl.isNullOrBlank()) {
            person.photoUrl
        } else {
            clientManager.getPersonImageUrl(person.id, null, maxWidth = 400)
        }

    fun getItemImageUrl(item: TvItem): String = clientManager.getItemImageUrl(item)
}
