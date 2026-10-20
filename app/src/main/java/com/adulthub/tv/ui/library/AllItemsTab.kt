package com.adulthub.tv.ui.library

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.data.model.DurationFilter
import com.adulthub.tv.data.model.TvCategory
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.model.TvStudio
import com.adulthub.tv.ui.components.ErrorMessage
import com.adulthub.tv.ui.components.LoadingIndicator
import com.adulthub.tv.ui.components.MovieCard
import com.adulthub.tv.ui.components.TvPrimaryButton
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.theme.CardSurface
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.SurfaceDark
import com.adulthub.tv.ui.theme.TextSecondary
import org.jellyfin.sdk.model.api.SortOrder

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun AllItemsTab(
    state: AllItemsTabState,
    onMovieClick: (TvItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSortChange: (String, SortOrder) -> Unit,
    onToggleFavorites: () -> Unit,
    onToggleUnwatched: () -> Unit,
    onDurationFilterChange: (DurationFilter) -> Unit = {},
    onToggleHdOnly: () -> Unit = {},
    onSelectGenre: (TvCategory?) -> Unit,
    onSelectStudio: (TvStudio?) -> Unit,
    onSelectPerformer: (TvPerson?) -> Unit,
    onLoadGenres: () -> Unit,
    onLoadStudios: () -> Unit,
    onLoadPerformers: () -> Unit,
    onSurpriseMe: () -> Unit,
    getImageUrl: (TvItem) -> String,
    modifier: Modifier = Modifier,
) {
    var filterPicker by rememberSaveable { mutableStateOf<CatalogFilter?>(null) }
    val gridState = rememberLazyGridState()
    val focusRequesterMap = remember { mutableMapOf<String, FocusRequester>() }

    LaunchedEffect(state.lastSelectedItemId) {
        val targetId = state.lastSelectedItemId ?: return@LaunchedEffect
        val index = state.items.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            gridState.scrollToItem(index)
            delay(150L)
            runCatching {
                focusRequesterMap[targetId]?.requestFocus()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
    ) {
        // Filter & Sort Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${state.totalCount} movies",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                // Sort By Button
                TvSecondaryButton(
                    onClick = {
                        val nextSort = when (state.sortBy) {
                            "DateCreated" -> "SortName"
                            "SortName" -> "Runtime"
                            else -> "DateCreated"
                        }
                        onSortChange(nextSort, state.sortOrder)
                    },
                    modifier = Modifier.height(36.dp),
                ) {
                    val label = when (state.sortBy) {
                        "DateCreated" -> "Date Added"
                        "SortName" -> "Title"
                        "Runtime" -> "Duration"
                        else -> "Default"
                    }
                    Text(text = "Sort: $label", fontSize = 12.sp)
                }

                // Sort Order Button
                TvSecondaryButton(
                    onClick = {
                        val nextOrder = if (state.sortOrder == SortOrder.DESCENDING) {
                            SortOrder.ASCENDING
                        } else {
                            SortOrder.DESCENDING
                        }
                        onSortChange(state.sortBy, nextOrder)
                    },
                    modifier = Modifier.height(36.dp),
                ) {
                    val arrow = if (state.sortOrder == SortOrder.DESCENDING) "↓ Desc" else "↑ Asc"
                    Text(text = arrow, fontSize = 12.sp)
                }

                // Favorites Only Toggle
                if (state.favoritesOnly) {
                    TvPrimaryButton(
                        onClick = onToggleFavorites,
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text(text = "Favorites", fontSize = 12.sp)
                    }
                } else {
                    TvSecondaryButton(
                        onClick = onToggleFavorites,
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text(text = "Favorites", fontSize = 12.sp)
                    }
                }

                if (state.unwatchedOnly) {
                    TvPrimaryButton(onClick = onToggleUnwatched, modifier = Modifier.height(36.dp)) {
                        Text(text = "Unwatched", fontSize = 12.sp)
                    }
                } else {
                    TvSecondaryButton(onClick = onToggleUnwatched, modifier = Modifier.height(36.dp)) {
                        Text(text = "Unwatched", fontSize = 12.sp)
                    }
                }

                // HD Only Toggle
                if (state.hdOnly) {
                    TvPrimaryButton(onClick = onToggleHdOnly, modifier = Modifier.height(36.dp)) {
                        Text(text = "HD Only", fontSize = 12.sp)
                    }
                } else {
                    TvSecondaryButton(onClick = onToggleHdOnly, modifier = Modifier.height(36.dp)) {
                        Text(text = "HD Only", fontSize = 12.sp)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvSecondaryButton(
                onClick = {
                    val nextDuration = when (state.durationFilter) {
                        DurationFilter.ALL -> DurationFilter.SHORT
                        DurationFilter.SHORT -> DurationFilter.MEDIUM
                        DurationFilter.MEDIUM -> DurationFilter.LONG
                        DurationFilter.LONG -> DurationFilter.ALL
                    }
                    onDurationFilterChange(nextDuration)
                },
                modifier = Modifier.height(36.dp),
            ) {
                Text(state.durationFilter.label, fontSize = 12.sp)
            }
            TvSecondaryButton(onClick = { filterPicker = CatalogFilter.GENRE; onLoadGenres() }, modifier = Modifier.height(36.dp)) {
                Text("Genre: ${state.selectedGenre?.name ?: "All"}", fontSize = 12.sp)
            }
            TvSecondaryButton(onClick = { filterPicker = CatalogFilter.STUDIO; onLoadStudios() }, modifier = Modifier.height(36.dp)) {
                Text("Studio: ${state.selectedStudio?.name ?: "All"}", fontSize = 12.sp)
            }
            TvSecondaryButton(onClick = { filterPicker = CatalogFilter.PERFORMER; onLoadPerformers() }, modifier = Modifier.height(36.dp)) {
                Text("Performer: ${state.selectedPerformer?.name ?: "All"}", fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            TvPrimaryButton(onClick = onSurpriseMe, modifier = Modifier.height(36.dp)) {
                Text("Surprise Me", fontSize = 12.sp)
            }
        }

        filterPicker?.let { picker ->
            val options = when (picker) {
                CatalogFilter.GENRE -> state.genres.map { FilterChoice(it.id, it.name) }
                CatalogFilter.STUDIO -> state.studios.map { FilterChoice(it.id, it.name) }
                CatalogFilter.PERFORMER -> state.performers.map { FilterChoice(it.id, it.name) }
            }
            val selectedId = when (picker) {
                CatalogFilter.GENRE -> state.selectedGenre?.id
                CatalogFilter.STUDIO -> state.selectedStudio?.id
                CatalogFilter.PERFORMER -> state.selectedPerformer?.id
            }
            val isLoading = when (picker) {
                CatalogFilter.GENRE -> state.isLoadingGenres
                CatalogFilter.STUDIO -> state.isLoadingStudios
                CatalogFilter.PERFORMER -> state.isLoadingPerformers
            }
            CatalogFilterDialog(
                title = when (picker) {
                    CatalogFilter.GENRE -> "Choose a genre"
                    CatalogFilter.STUDIO -> "Choose a studio"
                    CatalogFilter.PERFORMER -> "Choose a performer"
                },
                options = options,
                selectedId = selectedId,
                isLoading = isLoading,
                onDismiss = { filterPicker = null },
                onSelect = { id ->
                    when (picker) {
                        CatalogFilter.GENRE -> onSelectGenre(state.genres.firstOrNull { it.id == id })
                        CatalogFilter.STUDIO -> onSelectStudio(state.studios.firstOrNull { it.id == id })
                        CatalogFilter.PERFORMER -> onSelectPerformer(state.performers.firstOrNull { it.id == id })
                    }
                    filterPicker = null
                },
            )
        }

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(message = "Loading movies…")
                }
            }
            state.errorMessage != null && state.items.isEmpty() -> {
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
                        text = "No movies found matching current filters",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = gridState,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                ) {
                    items(
                        items = state.items,
                        key = { it.id },
                    ) { item ->
                        val requester = remember(item.id) {
                            focusRequesterMap.getOrPut(item.id) { FocusRequester() }
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

                    if (state.hasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.isLoadingMore) {
                                    LoadingIndicator(message = "Loading more movies…")
                                } else {
                                    TvSecondaryButton(
                                        onClick = onLoadMore,
                                        modifier = Modifier.height(44.dp),
                                    ) {
                                        Text(text = "Load More Movies ↓")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class CatalogFilter { GENRE, STUDIO, PERFORMER }

private data class FilterChoice(val id: String, val name: String)

@Composable
private fun CatalogFilterDialog(
    title: String,
    options: List<FilterChoice>,
    selectedId: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    var query by remember(title) { mutableStateOf("") }
    val searchFocus = remember(title) { FocusRequester() }
    val searchInteraction = remember(title) { MutableInteractionSource() }
    val isSearchFocused by searchInteraction.collectIsFocusedAsState()
    var isSearchEditing by remember(title) { mutableStateOf(false) }
    var searchEditorHadFocus by remember(title) { mutableStateOf(false) }
    val searchEditFocus = remember(title) { FocusRequester() }
    LaunchedEffect(title) { runCatching { searchFocus.requestFocus() } }
    LaunchedEffect(isSearchEditing) {
        if (isSearchEditing) runCatching { searchEditFocus.requestFocus() }
    }
    val filteredOptions = remember(options, query) {
        options.filter { it.name.contains(query, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(720.dp)
                .heightIn(max = 680.dp)
                .background(SurfaceDark, RoundedCornerShape(18.dp))
                .border(2.dp, NeonPink, RoundedCornerShape(18.dp))
                .padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = NeonPink, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface, RoundedCornerShape(10.dp))
                    .border(
                        width = if (isSearchFocused) 2.dp else 1.dp,
                        color = if (isSearchFocused) NeonPink else Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .then(
                        if (isSearchEditing) Modifier else Modifier
                            .focusRequester(searchFocus)
                            .clickable(
                                interactionSource = searchInteraction,
                                indication = null,
                                onClick = {
                                    searchEditorHadFocus = false
                                    isSearchEditing = true
                                },
                            ),
                    ),
            ) {
                if (isSearchEditing) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                        interactionSource = searchInteraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchEditFocus)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    searchEditorHadFocus = true
                                } else if (searchEditorHadFocus) {
                                    searchEditorHadFocus = false
                                    isSearchEditing = false
                                }
                            },
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("Filter list…", color = Color.White.copy(alpha = 0.45f), fontSize = 16.sp)
                            }
                            inner()
                        },
                    )
                } else {
                    Text(
                        query.ifEmpty { "Filter list…" },
                        color = if (query.isEmpty()) Color.White.copy(alpha = 0.45f) else Color.White,
                        fontSize = 16.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(message = "Loading filters…")
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    item(key = "all") {
                        FilterOptionButton("All", isSelected = selectedId == null) { onSelect(null) }
                    }
                    items(filteredOptions, key = { it.id }) { choice ->
                        FilterOptionButton(choice.name, isSelected = selectedId == choice.id) { onSelect(choice.id) }
                    }
                    if (filteredOptions.isEmpty()) {
                        item(key = "empty") {
                            Text("No matches found. Close and open the filter to retry.", color = TextSecondary)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            TvSecondaryButton(onClick = onDismiss, modifier = Modifier.height(40.dp)) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun FilterOptionButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    if (isSelected) {
        TvPrimaryButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).height(42.dp)) {
            Text(label, maxLines = 1)
        }
    } else {
        TvSecondaryButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).height(42.dp)) {
            Text(label, maxLines = 1)
        }
    }
}
