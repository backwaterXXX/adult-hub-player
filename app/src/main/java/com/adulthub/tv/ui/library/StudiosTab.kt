package com.adulthub.tv.ui.library

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvStudio
import com.adulthub.tv.ui.components.ErrorMessage
import com.adulthub.tv.ui.components.LoadingIndicator
import com.adulthub.tv.ui.components.MovieCard
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.theme.CardSurface
import com.adulthub.tv.ui.theme.CardSurfaceFocused
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.TextPrimary
import com.adulthub.tv.ui.theme.TextSecondary

@Composable
fun StudiosTab(
    state: StudiosTabState,
    onStudioClick: (TvStudio) -> Unit,
    onMovieClick: (TvItem) -> Unit,
    onBackToStudios: () -> Unit,
    onRetry: () -> Unit,
    getImageUrl: (TvItem) -> String,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {},
    onRetryStudioMovies: () -> Unit = {},
) {
    val studiosGridState = rememberLazyGridState()
    val studioFocusRequesterMap = remember { mutableMapOf<String, FocusRequester>() }
    val movieGridState = rememberLazyGridState()
    val movieFocusRequesterMap = remember { mutableMapOf<String, FocusRequester>() }

    LaunchedEffect(state.lastSelectedStudioId, state.selectedStudio) {
        if (state.selectedStudio == null) {
            val targetId = state.lastSelectedStudioId ?: return@LaunchedEffect
            val index = state.studios.indexOfFirst { it.id == targetId }
            if (index >= 0) {
                studiosGridState.scrollToItem(index)
                delay(150L)
                runCatching {
                    studioFocusRequesterMap[targetId]?.requestFocus()
                }
            }
        }
    }

    LaunchedEffect(state.lastSelectedMovieId, state.selectedStudio) {
        if (state.selectedStudio != null) {
            val targetId = state.lastSelectedMovieId ?: return@LaunchedEffect
            val index = state.studioMovies.indexOfFirst { it.id == targetId }
            if (index >= 0) {
                movieGridState.scrollToItem(index)
                delay(150L)
                runCatching {
                    movieFocusRequesterMap[targetId]?.requestFocus()
                }
            }
        }
    }

    LaunchedEffect(movieGridState, state.studioMovies.size, state.hasMoreStudioMovies, state.studioMoviesError) {
        snapshotFlow { movieGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (
                    lastVisibleIndex != null &&
                    state.studioMovies.isNotEmpty() &&
                    lastVisibleIndex >= state.studioMovies.lastIndex - 4 &&
                    state.hasMoreStudioMovies &&
                    state.studioMoviesError == null &&
                    !state.isLoadingMoreStudioMovies &&
                    !state.isLoadingStudioMovies
                ) {
                    onLoadMore()
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
    ) {
        if (state.selectedStudio != null) {
            // Selected Studio Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = state.selectedStudio.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = NeonPink,
                    )
                    val countText = if (state.totalCount > 0) {
                        if (state.hasMoreStudioMovies) {
                            "${state.studioMovies.size} of ${state.totalCount} movies"
                        } else {
                            "${state.studioMovies.size} movies"
                        }
                    } else if (state.hasMoreStudioMovies) {
                        "${state.studioMovies.size}+ movies"
                    } else {
                        "${state.studioMovies.size} movies"
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }

                TvSecondaryButton(onClick = onBackToStudios) {
                    Text(text = "Back to Studios")
                }
            }

            if (state.isLoadingStudioMovies) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(message = "Loading studio movies…")
                }
            } else if (state.studioMoviesError != null && state.studioMovies.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorMessage(
                        message = state.studioMoviesError,
                        onRetry = onRetryStudioMovies,
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }
            } else if (state.studioMovies.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No movies found for ${state.selectedStudio.name}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = movieGridState,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = state.studioMovies,
                        key = { it.id },
                    ) { item ->
                        val requester = remember(item.id) {
                            movieFocusRequesterMap.getOrPut(item.id) { FocusRequester() }
                        }
                        MovieCard(
                            item = item,
                            imageUrl = getImageUrl(item),
                            onClick = { onMovieClick(item) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(requester),
                        )
                    }

                    if (state.isLoadingMoreStudioMovies) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingIndicator(message = "Loading more studio movies…")
                            }
                        }
                    }

                    if (state.hasMoreStudioMovies && !state.isLoadingMoreStudioMovies) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TvSecondaryButton(
                                    onClick = onLoadMore,
                                    modifier = Modifier.height(44.dp),
                                ) {
                                    Text(text = "Load More Movies ↓")
                                }
                            }
                        }
                    }

                    if (state.studioMoviesError != null && state.studioMovies.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TvSecondaryButton(
                                    onClick = onLoadMore,
                                    modifier = Modifier.height(44.dp),
                                ) {
                                    Text(text = "Retry Loading More Movies")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Studios Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.studios.size} studios",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator(message = "Loading studios…")
                    }
                }
                state.errorMessage != null && state.studios.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ErrorMessage(
                            message = state.errorMessage,
                            onRetry = onRetry,
                            modifier = Modifier.fillMaxWidth(0.5f),
                        )
                    }
                }
                state.isEmpty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No studios found in this library",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        state = studiosGridState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = state.studios,
                            key = { it.id },
                        ) { studio ->
                            val requester = remember(studio.id) {
                                studioFocusRequesterMap.getOrPut(studio.id) { FocusRequester() }
                            }
                            StudioCard(
                                studio = studio,
                                onClick = { onStudioClick(studio) },
                                modifier = Modifier.focusRequester(requester),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioCard(
    studio: TvStudio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .graphicsLayer { clip = false },
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1.05f),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))),
            focusedBorder = Border(BorderStroke(3.5.dp, NeonPink)),
        ),
        colors = CardDefaults.colors(
            containerColor = CardSurface,
            focusedContainerColor = CardSurfaceFocused,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = studio.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            studio.movieCount?.takeIf { it > 0 }?.let { movieCount ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$movieCount movies",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
