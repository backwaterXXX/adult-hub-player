package com.adulthub.tv.ui.library

import androidx.annotation.StringRes
import com.adulthub.tv.R
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.ChannelSortMode
import com.adulthub.tv.data.model.DurationFilter
import com.adulthub.tv.data.model.TvCategory
import com.adulthub.tv.data.model.TvChannel
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.model.TvStudio
import com.adulthub.tv.data.resolver.VideoDto
import org.jellyfin.sdk.model.api.SortOrder

enum class LibraryTab(@StringRes val titleRes: Int) {
    ALL(R.string.library_tab_movies),
    CATEGORIES(R.string.library_tab_categories),
    ACTORS(R.string.library_tab_actors),
    STUDIOS(R.string.library_tab_studios),
    CHANNELS(R.string.library_tab_channels)
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
    val lastSelectedItemId: String? = null
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
    val categoryMoviesOffset: Int = 0,
    val categoryMoviesError: String? = null,
    val lastSelectedCategoryId: String? = null,
    val lastSelectedMovieId: String? = null
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
    val lastSelectedPersonId: String? = null
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
    val studioMoviesOffset: Int = 0,
    val studioMoviesError: String? = null,
    val lastSelectedStudioId: String? = null,
    val lastSelectedMovieId: String? = null
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
    val lastSelectedVideoId: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && errorMessage == null && channels.isEmpty()
}

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.ALL,
    val allState: AllItemsTabState = AllItemsTabState(),
    val categoriesState: CategoriesTabState = CategoriesTabState(),
    val actorsState: ActorsTabState = ActorsTabState(),
    val studiosState: StudiosTabState = StudiosTabState(),
    val channelsState: ChannelsTabState = ChannelsTabState()
)

sealed interface LibraryNavEvent {
    data class NavigateToItemDetail(val itemId: String) : LibraryNavEvent
    data class NavigateToPersonDetail(val personId: String, val personName: String) : LibraryNavEvent
    data class NavigateToPlayer(val itemId: String) : LibraryNavEvent
}
