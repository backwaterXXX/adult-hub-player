package com.adulthub.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.R
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.ui.components.MovieCard
import com.adulthub.tv.ui.components.PersonCard
import com.adulthub.tv.ui.components.SectionRow
import com.adulthub.tv.ui.components.TvPrimaryButton
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.theme.CardSurface
import com.adulthub.tv.ui.theme.CardSurfaceFocused
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.OledBlack
import com.adulthub.tv.ui.theme.TextPrimary
import com.adulthub.tv.ui.theme.TextSecondary
import org.koin.androidx.compose.koinViewModel

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
    onNavigateToItemDetail: (String) -> Unit = {},
    onNavigateToPersonDetail: (String, String) -> Unit = { _, _ -> },
    onNavigateToPlayer: (String) -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToLibraryCategory: (String) -> Unit = { onNavigateToLibrary() },
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    isCurrentDestination: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var hasBeenCurrentDestination by rememberSaveable { mutableStateOf(false) }
    val homeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isCurrentDestination) {
        if (isCurrentDestination) {
            if (hasBeenCurrentDestination) {
                runCatching { homeFocusRequester.restoreFocusedChild() }
                // Give Jellyfin's playback-stop report time to update resume position.
                delay(500L)
                viewModel.loadResume()
                viewModel.loadWatchHistory()
            }
            hasBeenCurrentDestination = true
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            runCatching { homeFocusRequester.saveFocusedChild() }
            when (event) {
                is HomeNavEvent.NavigateToItemDetail -> onNavigateToItemDetail(event.itemId)
                is HomeNavEvent.NavigateToPlayer -> onNavigateToPlayer(event.itemId)
                is HomeNavEvent.NavigateToPersonDetail -> onNavigateToPersonDetail(event.personId, event.personName)
                is HomeNavEvent.NavigateToLibraryCategory -> onNavigateToLibraryCategory(event.categoryId)
                is HomeNavEvent.NavigateToLibrary -> onNavigateToLibrary()
                is HomeNavEvent.NavigateToSearch -> onNavigateToSearch()
                is HomeNavEvent.NavigateToSettings -> onNavigateToSettings()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(homeFocusRequester)
                .focusRestorer(),
            contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp),
        ) {
            // Top Bar
            item {
                HomeTopBar(
                    libraryName = state.libraryName,
                    onPlayRandom = viewModel::onSurpriseMeClicked,
                    onLibrary = viewModel::onLibraryClicked,
                    onSearch = viewModel::onSearchClicked,
                    onSettings = viewModel::onSettingsClicked,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Continue Watching Section
            item {
                SectionRow(
                    title = stringResource(R.string.home_section_resume),
                    isLoading = state.resumeSection.isLoading,
                    errorMessage = state.resumeSection.errorMessage,
                    isEmpty = state.resumeSection.isEmpty,
                    emptyMessage = stringResource(R.string.home_resume_empty),
                    onRetry = viewModel::loadResume,
                ) {
                    LazyRow(
                        modifier = Modifier.focusRestorer(),
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(
                            items = state.resumeSection.items,
                            key = { it.id },
                        ) { item ->
                            MovieCard(
                                item = item,
                                imageUrl = viewModel.getItemImageUrl(item),
                                onClick = { viewModel.onMovieClicked(item) },
                            )
                        }
                    }
                }
            }

            // Watch History Section
            if (state.watchHistorySection.items.isNotEmpty()) {
                item {
                    SectionRow(
                        title = stringResource(R.string.home_section_history),
                        isLoading = state.watchHistorySection.isLoading,
                        errorMessage = state.watchHistorySection.errorMessage,
                        isEmpty = state.watchHistorySection.isEmpty,
                        emptyMessage = "",
                        onRetry = viewModel::loadWatchHistory,
                    ) {
                        LazyRow(
                            modifier = Modifier.focusRestorer(),
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(
                                items = state.watchHistorySection.items,
                                key = { "history_${it.id}" },
                            ) { item ->
                                MovieCard(
                                    item = item,
                                    imageUrl = viewModel.getItemImageUrl(item),
                                    onClick = { viewModel.onMovieClicked(item) },
                                )
                            }
                        }
                    }
                }
            }

            // Recently Added Section
            item {
                SectionRow(
                    title = stringResource(R.string.home_section_latest),
                    isLoading = state.latestSection.isLoading,
                    errorMessage = state.latestSection.errorMessage,
                    isEmpty = state.latestSection.isEmpty,
                    emptyMessage = stringResource(R.string.home_latest_empty),
                    onRetry = viewModel::loadLatest,
                ) {
                    LazyRow(
                        modifier = Modifier.focusRestorer(),
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(
                            items = state.latestSection.items,
                            key = { it.id },
                        ) { item ->
                            MovieCard(
                                item = item,
                                imageUrl = viewModel.getItemImageUrl(item),
                                onClick = { viewModel.onMovieClicked(item) },
                            )
                        }
                    }
                }
            }

            // Random Picks Section
            item {
                SectionRow(
                    title = stringResource(R.string.home_section_random),
                    isLoading = state.randomSection.isLoading,
                    errorMessage = state.randomSection.errorMessage,
                    isEmpty = state.randomSection.isEmpty,
                    emptyMessage = stringResource(R.string.home_random_empty),
                    onRetry = viewModel::loadRandom,
                    headerAction = {
                        TvSecondaryButton(
                            onClick = viewModel::toggleRandomUnwatchedOnly,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text(
                                text = if (state.randomUnwatchedOnly) {
                                    stringResource(R.string.shuffle_unwatched)
                                } else {
                                    stringResource(R.string.shuffle_watched_and_unwatched)
                                },
                                fontSize = 12.sp,
                            )
                        }
                    },
                ) {
                    LazyRow(
                        modifier = Modifier.focusRestorer(),
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(
                            items = state.randomSection.items,
                            key = { it.id },
                        ) { item ->
                            MovieCard(
                                item = item,
                                imageUrl = viewModel.getItemImageUrl(item),
                                onClick = { viewModel.onMovieClicked(item) },
                            )
                        }
                    }
                }
            }

            // Popular Performers Section
            item {
                SectionRow(
                    title = stringResource(R.string.home_section_performers),
                    isLoading = state.performersSection.isLoading,
                    errorMessage = state.performersSection.errorMessage,
                    isEmpty = state.performersSection.isEmpty,
                    emptyMessage = stringResource(R.string.home_performers_empty),
                    onRetry = viewModel::loadPerformers,
                ) {
                    LazyRow(
                        modifier = Modifier.focusRestorer(),
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(
                            items = state.performersSection.items,
                            key = { it.id },
                        ) { person ->
                            PersonCard(
                                person = person,
                                imageUrl = viewModel.getPersonImageUrl(person),
                                onClick = { viewModel.onPersonClicked(person) },
                            )
                        }
                    }
                }
            }

            // Category Rows (Pkt 11)
            items(
                items = state.categoryRows,
                key = { "category_${it.categoryId}" },
            ) { categoryRow ->
                SectionRow(
                    title = categoryRow.categoryName,
                    headerAction = {
                        TvSecondaryButton(
                            onClick = { viewModel.onCategorySeeAllClicked(categoryRow.categoryId) },
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.action_see_all),
                                fontSize = 12.sp,
                            )
                        }
                    },
                ) {
                    LazyRow(
                        modifier = Modifier.focusRestorer(),
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(
                            items = categoryRow.items,
                            key = { it.id },
                        ) { item ->
                            MovieCard(
                                item = item,
                                imageUrl = viewModel.getItemImageUrl(item),
                                onClick = { viewModel.onMovieClicked(item) },
                            )
                        }
                    }
                }
            }
            if (state.categoryRowsLoading || state.categoryRowsErrorMessage != null) {
                item(key = "home_categories_status") {
                    SectionRow(
                        title = stringResource(R.string.home_categories_title),
                        isLoading = state.categoryRowsLoading,
                        errorMessage = state.categoryRowsErrorMessage,
                        onRetry = viewModel::reloadCategoryRows,
                    ) {}
                }
            }
        }
    }
}
@Composable
private fun HomeTopBar(
    libraryName: String,
    onPlayRandom: () -> Unit,
    onLibrary: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Logo & Library Name
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.app_brand),
                color = NeonPink,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )

            if (libraryName.isNotBlank()) {
                Spacer(modifier = Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardSurface)
                        .border(1.dp, CardSurfaceFocused, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = libraryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Navigation Actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvPrimaryButton(
                onClick = onPlayRandom,
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_play_random),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            TvSecondaryButton(
                onClick = onLibrary,
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    text = stringResource(R.string.nav_library),
                    fontSize = 13.sp,
                )
            }

            TvSecondaryButton(
                onClick = onSearch,
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    text = stringResource(R.string.nav_search),
                    fontSize = 13.sp,
                )
            }

            TvSecondaryButton(
                onClick = onSettings,
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    text = stringResource(R.string.nav_settings),
                    fontSize = 13.sp,
                )
            }
        }
    }
}
