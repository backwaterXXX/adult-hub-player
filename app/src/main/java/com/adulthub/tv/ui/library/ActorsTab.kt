package com.adulthub.tv.ui.library

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.ui.components.ErrorMessage
import com.adulthub.tv.ui.components.LoadingIndicator
import com.adulthub.tv.ui.components.PersonCard
import com.adulthub.tv.ui.components.TvPrimaryButton
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ActorsTab(
    state: ActorsTabState,
    onPersonClick: (TvPerson) -> Unit,
    onSelectLetter: (Char?) -> Unit,
    onToggleOnlyWithPhoto: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    getImageUrl: (TvPerson) -> String,
    modifier: Modifier = Modifier,
) {
    val alphabet = ('A'..'Z').toList()
    val gridState = rememberLazyGridState()
    val focusRequesterMap = remember { mutableMapOf<String, FocusRequester>() }

    LaunchedEffect(state.lastSelectedPersonId) {
        val targetId = state.lastSelectedPersonId ?: return@LaunchedEffect
        val index = state.filteredPersons.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            gridState.scrollToItem(index)
            delay(150L)
            runCatching {
                focusRequesterMap[targetId]?.requestFocus()
            }
        }
    }

    LaunchedEffect(state.selectedLetter, state.searchQuery) {
        gridState.scrollToItem(0)
    }

    LaunchedEffect(gridState, state.filteredPersons.size, state.hasMore, state.errorMessage) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (
                    lastVisibleIndex != null &&
                    state.filteredPersons.isNotEmpty() &&
                    lastVisibleIndex >= state.filteredPersons.lastIndex - 8 &&
                    state.hasMore &&
                    state.errorMessage == null &&
                    !state.isLoadingMore
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
        // Controls: Alphabet & Photo filter (Photo only placed BEFORE "ALL")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 1. Photo only button at the beginning before ALL
                item {
                    if (state.onlyWithPhoto) {
                        TvPrimaryButton(
                            onClick = onToggleOnlyWithPhoto,
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text(text = "Photo only", fontSize = 12.sp)
                        }
                    } else {
                        TvSecondaryButton(
                            onClick = onToggleOnlyWithPhoto,
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text(text = "Photo only", fontSize = 12.sp)
                        }
                    }
                }

                // 2. ALL button
                item {
                    if (state.selectedLetter == null) {
                        TvPrimaryButton(
                            onClick = { onSelectLetter(null) },
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text(text = "ALL", fontSize = 12.sp)
                        }
                    } else {
                        TvSecondaryButton(
                            onClick = { onSelectLetter(null) },
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text(text = "ALL", fontSize = 12.sp)
                        }
                    }
                }

                // 3. Letters A-Z
                items(alphabet) { letter ->
                    val isSelected = state.selectedLetter == letter
                    if (isSelected) {
                        TvPrimaryButton(
                            onClick = { onSelectLetter(letter) },
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text(text = letter.toString(), fontSize = 12.sp)
                        }
                    } else {
                        TvSecondaryButton(
                            onClick = { onSelectLetter(letter) },
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text(text = letter.toString(), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Subtitle / count
        Text(
            text = "Showing ${state.filteredPersons.size} of ${state.totalCount} performers",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(message = "Loading actors…")
                }
            }
            state.errorMessage != null && state.allPersons.isEmpty() -> {
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
                        text = "No performers match the selected filter",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    state = gridState,
                    contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = state.filteredPersons,
                        key = { it.id },
                    ) { person ->
                        val requester = remember(person.id) {
                            focusRequesterMap.getOrPut(person.id) { FocusRequester() }
                        }
                        PersonCard(
                            person = person,
                            imageUrl = getImageUrl(person),
                            onClick = { onPersonClick(person) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(requester),
                        )
                    }
                    if (state.isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingIndicator(message = "Loading more actors…")
                            }
                        }
                    }
                    if (state.errorMessage != null && state.allPersons.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = state.errorMessage,
                                    color = TextSecondary,
                                )
                                TvSecondaryButton(onClick = onLoadMore) {
                                    Text(text = "Retry")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
