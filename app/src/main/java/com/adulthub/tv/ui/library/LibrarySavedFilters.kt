package com.adulthub.tv.ui.library

import androidx.lifecycle.SavedStateHandle
import com.adulthub.tv.data.model.DurationFilter
import com.adulthub.tv.data.model.TvCategory
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.model.TvStudio
import org.jellyfin.sdk.model.api.SortOrder

/**
 * Stores the library filters in the saved state, so that they survive a process death.
 */
internal class LibrarySavedFilters(private val savedStateHandle: SavedStateHandle) {

    fun save(state: LibraryUiState) {
        savedStateHandle[KEY_SELECTED_TAB] = state.selectedTab.name
        savedStateHandle[KEY_SORT_BY] = state.allState.sortBy
        savedStateHandle[KEY_SORT_ORDER] = state.allState.sortOrder.name
        savedStateHandle[KEY_FAVORITES_ONLY] = state.allState.favoritesOnly
        savedStateHandle[KEY_UNWATCHED_ONLY] = state.allState.unwatchedOnly
        savedStateHandle[KEY_HD_ONLY] = state.allState.hdOnly
        savedStateHandle[KEY_DURATION_FILTER] = state.allState.durationFilter.name
        savedStateHandle[KEY_GENRE_ID] = state.allState.selectedGenre?.id
        savedStateHandle[KEY_GENRE_NAME] = state.allState.selectedGenre?.name
        savedStateHandle[KEY_STUDIO_ID] = state.allState.selectedStudio?.id
        savedStateHandle[KEY_STUDIO_NAME] = state.allState.selectedStudio?.name
        savedStateHandle[KEY_PERFORMER_ID] = state.allState.selectedPerformer?.id
        savedStateHandle[KEY_PERFORMER_NAME] = state.allState.selectedPerformer?.name
        savedStateHandle[KEY_ACTOR_LETTER] = state.actorsState.selectedLetter?.toString()
        savedStateHandle[KEY_ONLY_WITH_PHOTO] = state.actorsState.onlyWithPhoto
        savedStateHandle[KEY_CHANNEL_DURATION_FILTER] = state.channelsState.durationFilter.name
        savedStateHandle[KEY_HIDDEN_CHANNELS] = ArrayList(state.channelsState.hiddenChannelIds)
    }

    /**
     * Returns [current] with every filter that has a saved value replaced by that value.
     */
    fun restore(current: LibraryUiState): LibraryUiState {
        val restoredTab = savedStateHandle.get<String>(KEY_SELECTED_TAB)
            ?.let { name -> LibraryTab.entries.firstOrNull { it.name == name } }
            ?: current.selectedTab
        val restoredAll = current.allState.copy(
            sortBy = savedStateHandle.get<String>(KEY_SORT_BY) ?: current.allState.sortBy,
            sortOrder = savedStateHandle.get<String>(KEY_SORT_ORDER)
                ?.let { name -> SortOrder.entries.firstOrNull { it.name == name } }
                ?: current.allState.sortOrder,
            favoritesOnly = savedStateHandle.get<Boolean>(KEY_FAVORITES_ONLY) ?: current.allState.favoritesOnly,
            unwatchedOnly = savedStateHandle.get<Boolean>(KEY_UNWATCHED_ONLY) ?: current.allState.unwatchedOnly,
            hdOnly = savedStateHandle.get<Boolean>(KEY_HD_ONLY) ?: current.allState.hdOnly,
            durationFilter = savedStateHandle.get<String>(KEY_DURATION_FILTER)
                ?.let { name -> DurationFilter.entries.firstOrNull { it.name == name } }
                ?: current.allState.durationFilter,
            selectedGenre = restoreSelection(KEY_GENRE_ID, KEY_GENRE_NAME) { id, name -> TvCategory(id, name) },
            selectedStudio = restoreSelection(KEY_STUDIO_ID, KEY_STUDIO_NAME) { id, name -> TvStudio(id, name) },
            selectedPerformer = restoreSelection(KEY_PERFORMER_ID, KEY_PERFORMER_NAME) { id, name -> TvPerson(id, name) }
        )
        val restoredActors = current.actorsState.copy(
            selectedLetter = savedStateHandle.get<String>(KEY_ACTOR_LETTER)?.firstOrNull(),
            onlyWithPhoto = savedStateHandle.get<Boolean>(KEY_ONLY_WITH_PHOTO) ?: current.actorsState.onlyWithPhoto
        )
        val restoredChannels = current.channelsState.copy(
            durationFilter = savedStateHandle.get<String>(KEY_CHANNEL_DURATION_FILTER)
                ?.let { name -> DurationFilter.entries.firstOrNull { it.name == name } }
                ?: current.channelsState.durationFilter,
            hiddenChannelIds = savedStateHandle.get<ArrayList<String>>(KEY_HIDDEN_CHANNELS)?.toSet()
                ?: current.channelsState.hiddenChannelIds
        )
        return current.copy(
            selectedTab = restoredTab,
            allState = restoredAll,
            actorsState = restoredActors,
            channelsState = restoredChannels
        )
    }

    private fun <T> restoreSelection(idKey: String, nameKey: String, factory: (String, String) -> T): T? {
        val id = savedStateHandle.get<String>(idKey) ?: return null
        val name = savedStateHandle.get<String>(nameKey) ?: return null
        return factory(id, name)
    }

    private companion object {
        const val KEY_SELECTED_TAB = "library_tab"
        const val KEY_SORT_BY = "library_sort_by"
        const val KEY_SORT_ORDER = "library_sort_order"
        const val KEY_FAVORITES_ONLY = "library_favorites_only"
        const val KEY_UNWATCHED_ONLY = "library_unwatched_only"
        const val KEY_HD_ONLY = "library_hd_only"
        const val KEY_DURATION_FILTER = "library_duration_filter"
        const val KEY_GENRE_ID = "library_genre_id"
        const val KEY_GENRE_NAME = "library_genre_name"
        const val KEY_STUDIO_ID = "library_studio_id"
        const val KEY_STUDIO_NAME = "library_studio_name"
        const val KEY_PERFORMER_ID = "library_performer_id"
        const val KEY_PERFORMER_NAME = "library_performer_name"
        const val KEY_ACTOR_LETTER = "library_actor_letter"
        const val KEY_ONLY_WITH_PHOTO = "library_only_with_photo"
        const val KEY_CHANNEL_DURATION_FILTER = "library_channel_duration_filter"
        const val KEY_HIDDEN_CHANNELS = "library_hidden_channels"
    }
}
