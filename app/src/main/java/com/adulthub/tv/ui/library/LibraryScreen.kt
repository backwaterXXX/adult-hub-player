package com.adulthub.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.adulthub.tv.ui.components.TvPrimaryButton
import com.adulthub.tv.ui.components.TvPageHeader
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.theme.OledBlack
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun LibraryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToItemDetail: (String) -> Unit,
    onNavigateToPersonDetail: (String, String) -> Unit,
    onNavigateToPlayer: (String) -> Unit = {},
    initialCategoryId: String? = null,
    isCurrentDestination: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val contentFocusRequester = remember { FocusRequester() }
    var hasBeenCurrentDestination by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialCategoryId) {
        initialCategoryId?.let(viewModel::openCategory)
    }

    val handleBack: () -> Unit = {
        when {
            state.selectedTab == LibraryTab.CATEGORIES && state.categoriesState.selectedCategory != null -> {
                viewModel.selectCategory(null)
            }
            state.selectedTab == LibraryTab.STUDIOS && state.studiosState.selectedStudio != null -> {
                viewModel.selectStudio(null)
            }
            state.selectedTab == LibraryTab.CHANNELS && state.channelsState.selectedChannel != null -> {
                viewModel.selectChannel(null)
            }
            else -> onNavigateBack()
        }
    }

    BackHandler(onBack = handleBack)

    LaunchedEffect(isCurrentDestination) {
        if (isCurrentDestination) {
            if (hasBeenCurrentDestination) {
                delay(100L)
                runCatching { contentFocusRequester.restoreFocusedChild() }
            }
            hasBeenCurrentDestination = true
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            runCatching { contentFocusRequester.saveFocusedChild() }
            when (event) {
                is LibraryNavEvent.NavigateToItemDetail -> onNavigateToItemDetail(event.itemId)
                is LibraryNavEvent.NavigateToPersonDetail -> onNavigateToPersonDetail(event.personId, event.personName)
                is LibraryNavEvent.NavigateToPlayer -> onNavigateToPlayer(event.itemId)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(contentFocusRequester)
                .focusGroup()
                .focusRestorer(),
        ) {
            // Header with Back button and TabRow
            TvPageHeader(
                title = "Catalog",
                onBack = handleBack,
            ) {
                // Tabs
                LibraryTab.entries.forEach { tab ->
                    val isSelected = state.selectedTab == tab
                    if (isSelected) {
                        TvPrimaryButton(
                            onClick = { viewModel.selectTab(tab) },
                            modifier = Modifier.height(38.dp),
                        ) {
                            Text(
                                text = tab.title,
                                fontSize = 13.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            )
                        }
                    } else {
                        TvSecondaryButton(
                            onClick = { viewModel.selectTab(tab) },
                            modifier = Modifier.height(38.dp),
                        ) {
                            Text(
                                text = tab.title,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (state.selectedTab) {
                    LibraryTab.ALL -> {
                        AllItemsTab(
                            state = state.allState,
                            onMovieClick = viewModel::onMovieClicked,
                            onLoadMore = { viewModel.loadAllItems(reset = false) },
                            onRetry = { viewModel.loadAllItems(reset = true) },
                            onSortChange = viewModel::setSort,
                            onToggleFavorites = viewModel::toggleFavoritesOnly,
                            onToggleUnwatched = viewModel::toggleUnwatchedOnly,
                            onDurationFilterChange = viewModel::setDurationFilter,
                            onToggleHdOnly = viewModel::toggleHdOnly,
                            onSelectGenre = viewModel::selectGenre,
                            onSelectStudio = viewModel::selectCatalogStudio,
                            onSelectPerformer = viewModel::selectPerformer,
                            onLoadGenres = viewModel::loadGenresForFilter,
                            onLoadStudios = viewModel::loadStudiosForFilter,
                            onLoadPerformers = viewModel::loadPerformersForFilter,
                            onSurpriseMe = viewModel::onSurpriseMeClicked,
                            getImageUrl = viewModel::getItemImageUrl,
                        )
                    }
                    LibraryTab.CATEGORIES -> {
                        CategoriesTab(
                            state = state.categoriesState,
                            onCategoryClick = viewModel::selectCategory,
                            onMovieClick = viewModel::onMovieClicked,
                            onBackToCategories = { viewModel.selectCategory(null) },
                            onRetry = viewModel::loadCategories,
                            onRetryCategoryMovies = viewModel::retrySelectedCategoryMovies,
                            onLoadMore = viewModel::loadMoreCategoryMovies,
                            getImageUrl = viewModel::getItemImageUrl,
                        )
                    }
                    LibraryTab.ACTORS -> {
                        ActorsTab(
                            state = state.actorsState,
                            onPersonClick = viewModel::onPersonClicked,
                            onSelectLetter = viewModel::selectAlphabetLetter,
                            onToggleOnlyWithPhoto = viewModel::toggleOnlyWithPhoto,
                            onRetry = { viewModel.loadActors() },
                            onLoadMore = viewModel::loadMoreActors,
                            getImageUrl = viewModel::getPersonImageUrl,
                        )
                    }
                    LibraryTab.STUDIOS -> {
                        StudiosTab(
                            state = state.studiosState,
                            onStudioClick = viewModel::selectStudio,
                            onMovieClick = viewModel::onMovieClicked,
                            onBackToStudios = { viewModel.selectStudio(null) },
                            onRetry = viewModel::loadStudios,
                            onRetryStudioMovies = viewModel::retrySelectedStudioMovies,
                            onLoadMore = viewModel::loadMoreStudioMovies,
                            getImageUrl = viewModel::getItemImageUrl,
                        )
                    }
                    LibraryTab.CHANNELS -> {
                        ChannelsTab(
                            state = state.channelsState,
                            onChannelClick = viewModel::selectChannel,
                            onVideoClick = viewModel::onChannelVideoClicked,
                            onBackToChannels = { viewModel.selectChannel(null) },
                            onRetry = viewModel::loadChannels,
                            onRetryChannelVideos = viewModel::retrySelectedChannelVideos,
                            onLoadMore = viewModel::loadMoreChannelVideos,
                            onSortModeChange = viewModel::setChannelSortMode,
                            onHideChannel = viewModel::hideCurrentChannel,
                            onDurationFilterChange = viewModel::setChannelDurationFilter,
                            getVideoThumbnailUrl = viewModel::getChannelVideoThumbnailUrl,
                        )
                    }
                }
            }
        }
    }
}
