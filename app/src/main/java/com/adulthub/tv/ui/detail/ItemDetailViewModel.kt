package com.adulthub.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.ShuffleContextMode
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPersonRole
import com.adulthub.tv.data.repository.LibraryRepository
import org.jellyfin.sdk.model.api.ItemFilter
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ItemDetailUiState(
    val item: TvItem? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val similarItems: List<TvItem> = emptyList(),
    val isLoadingSimilar: Boolean = false,
    val isFavorite: Boolean = false,
)

sealed interface ItemDetailNavEvent {
    data class NavigateToPlayer(val itemId: String, val resume: Boolean? = null) : ItemDetailNavEvent
    data class NavigateToItemDetail(val itemId: String) : ItemDetailNavEvent
    data class NavigateToPersonDetail(val personId: String, val personName: String) : ItemDetailNavEvent
    data object NavigateBack : ItemDetailNavEvent
}

class ItemDetailViewModel(
    private val libraryRepository: LibraryRepository,
    private val clientManager: JellyfinClientManager,
    private val settingsDataStore: SettingsDataStore? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    private val _navEvents = MutableSharedFlow<ItemDetailNavEvent>()
    val navEvents: SharedFlow<ItemDetailNavEvent> = _navEvents.asSharedFlow()

    private var currentItemId: String? = null
    private var randomNavigationInFlight = false

    fun loadDetails(itemId: String) {
        if (currentItemId == itemId && _uiState.value.item != null) return
        currentItemId = itemId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isLoadingSimilar = false) }
            val itemResult = libraryRepository.getItemDetails(itemId)

            _uiState.update { state ->
                itemResult.fold(
                    onSuccess = { item ->
                        state.copy(
                            item = item,
                            isFavorite = item.isFavorite,
                            isLoading = false,
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Failed to load movie details",
                        )
                    }
                )
            }

            val loadedItem = itemResult.getOrNull()
            if (loadedItem != null && !loadedItem.isTelegram) {
                loadSimilar(itemId)
            }
        }
    }

    private fun loadSimilar(itemId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSimilar = true) }
            val similarResult = libraryRepository.getSimilarItems(itemId, limit = 12)
            // The screen may already show another item when the answer arrives.
            if (currentItemId != itemId) return@launch
            _uiState.update { state ->
                state.copy(
                    similarItems = similarResult.getOrDefault(emptyList()),
                    isLoadingSimilar = false,
                )
            }
        }
    }

    fun toggleFavorite() {
        val currentItem = _uiState.value.item ?: return
        if (currentItem.isTelegram) return
        val newFavorite = !_uiState.value.isFavorite

        // Optimistic update
        _uiState.update { it.copy(isFavorite = newFavorite) }

        viewModelScope.launch {
            val result = libraryRepository.toggleFavorite(currentItem.id, newFavorite)
            if (result.isFailure) {
                // Revert on failure, but never the favorite flag of another item.
                _uiState.update { state ->
                    if (state.item?.id == currentItem.id) state.copy(isFavorite = !newFavorite) else state
                }
            }
        }
    }

    fun toggleWatched() {
        val currentItem = _uiState.value.item ?: return
        val newPlayed = !currentItem.isPlayed

        // Optimistic update of the watched flag only, so a favorite toggled in the
        // meantime is not overwritten.
        _uiState.update { state ->
            val item = state.item
            if (item?.id == currentItem.id) state.copy(item = item.copy(isPlayed = newPlayed)) else state
        }

        viewModelScope.launch {
            val watchedStateId = if (currentItem.isTelegram) {
                currentItem.providerVideoId ?: currentItem.id
            } else {
                currentItem.id
            }
            val result = libraryRepository.markAsPlayed(watchedStateId, newPlayed)
            if (result.isFailure) {
                // Revert the flag instead of the whole item snapshot.
                _uiState.update { state ->
                    val item = state.item
                    if (item?.id == currentItem.id) {
                        state.copy(item = item.copy(isPlayed = currentItem.isPlayed))
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun playMovie() {
        navigateToPlayer(resume = false)
    }

    fun resumeMovie() {
        navigateToPlayer(resume = true)
    }

    private fun navigateToPlayer(resume: Boolean) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            _navEvents.emit(ItemDetailNavEvent.NavigateToPlayer(item.id, resume))
        }
    }

    fun excludeTag(tag: String) {
        viewModelScope.launch {
            settingsDataStore?.excludeTag(tag)
        }
    }

    fun playRandomContextual(mode: ShuffleContextMode = ShuffleContextMode.DEFAULT) {
        if (randomNavigationInFlight) return
        randomNavigationInFlight = true
        val current = _uiState.value.item
        viewModelScope.launch {
            try {
                when (mode) {
                    ShuffleContextMode.DEFAULT -> navigateToRandom()
                    ShuffleContextMode.UNWATCHED -> {
                        val random = libraryRepository.getRandomItemFiltered(
                            excludeItemId = current?.id,
                            filters = setOf(ItemFilter.IS_UNPLAYED),
                        ).getOrNull()
                        if (random != null) {
                            _navEvents.emit(ItemDetailNavEvent.NavigateToItemDetail(random.id))
                        } else {
                            navigateToRandom()
                        }
                    }
                    ShuffleContextMode.SAME_PERFORMER -> {
                        val actor = current?.people?.filter { it.type == "Actor" || it.type == null }?.shuffled()?.firstOrNull()
                        val actorUuid = actor?.let { runCatching { UUID.fromString(it.id) }.getOrNull() }
                        val random = if (actorUuid != null) {
                            libraryRepository.getRandomItemFiltered(
                                excludeItemId = current.id,
                                personIds = listOf(actorUuid),
                            ).getOrNull()
                        } else null
                        if (random != null) {
                            _navEvents.emit(ItemDetailNavEvent.NavigateToItemDetail(random.id))
                        } else {
                            navigateToRandom()
                        }
                    }
                    ShuffleContextMode.SAME_CATEGORY -> {
                        val genre = current?.genres?.shuffled()?.firstOrNull()
                        val random = if (!genre.isNullOrBlank()) {
                            libraryRepository.getRandomItemFiltered(
                                excludeItemId = current.id,
                                genres = listOf(genre),
                            ).getOrNull()
                        } else null
                        if (random != null) {
                            _navEvents.emit(ItemDetailNavEvent.NavigateToItemDetail(random.id))
                        } else {
                            navigateToRandom()
                        }
                    }
                }
            } finally {
                randomNavigationInFlight = false
            }
        }
    }

    fun playRandom() {
        if (randomNavigationInFlight) return
        randomNavigationInFlight = true
        viewModelScope.launch {
            try {
                navigateToRandom()
            } finally {
                randomNavigationInFlight = false
            }
        }
    }

    private suspend fun navigateToRandom() {
        val current = _uiState.value.item
        val random = libraryRepository.getRandomItem(excludeItemId = current?.id).getOrNull()
        if (random != null) {
            _navEvents.emit(ItemDetailNavEvent.NavigateToItemDetail(random.id))
        } else {
            val candidate = _uiState.value.similarItems.shuffled().firstOrNull()
            if (candidate != null) {
                _navEvents.emit(ItemDetailNavEvent.NavigateToItemDetail(candidate.id))
            }
        }
    }

    fun onSimilarMovieClicked(item: TvItem) {
        viewModelScope.launch {
            _navEvents.emit(ItemDetailNavEvent.NavigateToItemDetail(item.id))
        }
    }

    fun onCastPersonClicked(person: TvPersonRole) {
        viewModelScope.launch {
            _navEvents.emit(ItemDetailNavEvent.NavigateToPersonDetail(person.id, person.name))
        }
    }

    fun getBackdropImageUrl(item: TvItem): String {
        val base = clientManager.currentServerUrl?.trimEnd('/') ?: ""

        return if (!item.backdropImageTag.isNullOrBlank()) {
            "$base/Items/${item.id}/Images/Backdrop/0?maxWidth=1920&tag=${item.backdropImageTag}"
        } else if (!item.primaryImageTag.isNullOrBlank()) {
            "$base/Items/${item.id}/Images/Primary?maxWidth=1920&tag=${item.primaryImageTag}"
        } else {
            ""
        }
    }

    fun getItemImageUrl(item: TvItem): String = clientManager.getItemImageUrl(item)

    fun getPersonImageUrl(personId: String, tag: String?): String = clientManager.getPersonImageUrl(personId, tag)
}
