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
import com.adulthub.tv.data.model.TvCategory
import com.adulthub.tv.data.model.TvItem
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
fun CategoriesTab(
    state: CategoriesTabState,
    onCategoryClick: (TvCategory) -> Unit,
    onMovieClick: (TvItem) -> Unit,
    onBackToCategories: () -> Unit,
    onRetry: () -> Unit,
    getImageUrl: (TvItem) -> String,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {},
    onRetryCategoryMovies: () -> Unit = {},
) {
    val categoriesGridState = rememberLazyGridState()
    val categoryFocusRequesterMap = remember { mutableMapOf<String, FocusRequester>() }
    val movieGridState = rememberLazyGridState()
    val movieFocusRequesterMap = remember { mutableMapOf<String, FocusRequester>() }

    LaunchedEffect(state.lastSelectedCategoryId, state.selectedCategory) {
        if (state.selectedCategory == null) {
            val targetId = state.lastSelectedCategoryId ?: return@LaunchedEffect
            val index = state.categories.indexOfFirst { it.id == targetId }
            if (index >= 0) {
                categoriesGridState.scrollToItem(index)
                delay(150L)
                runCatching {
                    categoryFocusRequesterMap[targetId]?.requestFocus()
                }
            }
        }
    }

    LaunchedEffect(state.lastSelectedMovieId, state.selectedCategory) {
        if (state.selectedCategory != null) {
            val targetId = state.lastSelectedMovieId ?: return@LaunchedEffect
            val index = state.categoryMovies.indexOfFirst { it.id == targetId }
            if (index >= 0) {
                movieGridState.scrollToItem(index)
                delay(150L)
                runCatching {
                    movieFocusRequesterMap[targetId]?.requestFocus()
                }
            }
        }
    }

    LaunchedEffect(movieGridState, state.categoryMovies.size, state.hasMoreCategoryMovies, state.categoryMoviesError) {
        snapshotFlow { movieGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (
                    lastVisibleIndex != null &&
                    state.categoryMovies.isNotEmpty() &&
                    lastVisibleIndex >= state.categoryMovies.lastIndex - 4 &&
                    state.hasMoreCategoryMovies &&
                    state.categoryMoviesError == null &&
                    !state.isLoadingMoreCategoryMovies &&
                    !state.isLoadingCategoryMovies
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
        if (state.selectedCategory != null) {
            // Selected Category Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = state.selectedCategory.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = NeonPink,
                    )
                    val countText = if (state.totalCount > 0) {
                        if (state.hasMoreCategoryMovies) {
                            "${state.categoryMovies.size} of ${state.totalCount} movies"
                        } else {
                            "${state.categoryMovies.size} movies"
                        }
                    } else if (state.hasMoreCategoryMovies) {
                        "${state.categoryMovies.size}+ movies"
                    } else {
                        "${state.categoryMovies.size} movies"
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }

                TvSecondaryButton(onClick = onBackToCategories) {
                    Text(text = "Back to Categories")
                }
            }

            if (state.isLoadingCategoryMovies) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(message = "Loading category movies…")
                }
            } else if (state.categoryMoviesError != null && state.categoryMovies.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorMessage(
                        message = state.categoryMoviesError,
                        onRetry = onRetryCategoryMovies,
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }
            } else if (state.categoryMovies.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No movies found in category ${state.selectedCategory.name}",
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
                        items = state.categoryMovies,
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

                    if (state.isLoadingMoreCategoryMovies) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingIndicator(message = "Loading more category movies…")
                            }
                        }
                    }

                    if (state.hasMoreCategoryMovies && !state.isLoadingMoreCategoryMovies) {
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

                    if (state.categoryMoviesError != null && state.categoryMovies.isNotEmpty()) {
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
            // Categories Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.categories.size} categories",
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
                        LoadingIndicator(message = "Loading categories…")
                    }
                }
                state.errorMessage != null && state.categories.isEmpty() -> {
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
                            text = "No categories available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        state = categoriesGridState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = state.categories,
                            key = { it.id },
                        ) { category ->
                            val requester = remember(category.id) {
                                categoryFocusRequesterMap.getOrPut(category.id) { FocusRequester() }
                            }
                            CategoryCard(
                                category = category,
                                onClick = { onCategoryClick(category) },
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
private fun CategoryCard(
    category: TvCategory,
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
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
            )
            if (category.movieCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${category.movieCount} movies",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}
