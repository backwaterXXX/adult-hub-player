package com.adulthub.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.resolver.VideoDto
import com.adulthub.tv.data.resolver.resolveResolverUrl
import com.adulthub.tv.ui.components.ErrorMessage
import com.adulthub.tv.ui.components.LoadingIndicator
import com.adulthub.tv.ui.components.MovieCard
import com.adulthub.tv.ui.components.PersonCard
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.theme.CardSurface
import com.adulthub.tv.ui.theme.CardSurfaceFocused
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.OledBlack
import com.adulthub.tv.ui.theme.SurfaceDark
import com.adulthub.tv.ui.theme.TextPrimary
import com.adulthub.tv.ui.theme.TextSecondary
import coil.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun SearchScreen(
    initialQuery: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToItemDetail: (itemId: String) -> Unit,
    onNavigateToPersonDetail: (personId: String, personName: String) -> Unit,
    onNavigateToPlayer: (itemId: String) -> Unit = {},
    isCurrentDestination: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchInputFocusRequester = remember { FocusRequester() }
    val resultsFocusRequester = remember { FocusRequester() }
    var hasBeenCurrentDestination by remember { mutableStateOf(false) }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.onQueryChanged(initialQuery)
        }
    }

    LaunchedEffect(isCurrentDestination) {
        if (isCurrentDestination) {
            if (hasBeenCurrentDestination) {
                delay(100L)
                val restored = runCatching { resultsFocusRequester.restoreFocusedChild() }.getOrDefault(false)
                if (!restored) searchInputFocusRequester.requestFocus()
            } else {
                searchInputFocusRequester.requestFocus()
            }
            hasBeenCurrentDestination = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .padding(top = 28.dp),
    ) {
        // Search Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Search Input Box
            SearchInputField(
                value = uiState.query,
                onValueChange = { viewModel.onQueryChanged(it) },
                focusRequester = searchInputFocusRequester,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Clear Button (if query not blank)
            if (uiState.query.isNotBlank()) {
                TvSecondaryButton(
                    onClick = { viewModel.clearQuery() },
                    modifier = Modifier.height(48.dp),
                ) {
                    Text("Clear", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.width(12.dp))
            }

            // Back Button
            TvSecondaryButton(
                onClick = onNavigateBack,
                modifier = Modifier.height(48.dp),
            ) {
                Text("Back", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Search Content / Results
        when {
            uiState.isSearching -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(message = "Searching for '${uiState.query}'…")
                }
            }

            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorMessage(
                        message = uiState.errorMessage ?: "Search failed",
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }
            }

            uiState.query.isBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 48.dp),
                    ) {
                        Text(
                            text = "Search Movies & Performers",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            ),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Use the remote to enter movie titles, genres, or actor names.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary,
                            ),
                        )
                        if (uiState.recentQueries.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Recent Searches",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = NeonPink,
                                ),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(items = uiState.recentQueries) { recentQuery ->
                                    TvSecondaryButton(
                                        onClick = { viewModel.onQueryChanged(recentQuery) },
                                        modifier = Modifier.height(36.dp),
                                    ) {
                                        Text(recentQuery, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            uiState.hasSearched && uiState.movies.isEmpty() && uiState.actors.isEmpty() && uiState.channelVideos.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No results found",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            ),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No movies, performers, or channel videos matched '${uiState.query}'. Try another keyword.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary,
                            ),
                        )
                    }
                }
            }

            else -> {
                SearchResultsList(
                    movies = uiState.movies,
                    actors = uiState.actors,
                    channelVideos = uiState.channelVideos,
                    resolverBaseUrl = uiState.resolverBaseUrl,
                    resultsFocusRequester = resultsFocusRequester,
                    onNavigateToItemDetail = { id ->
                        resultsFocusRequester.saveFocusedChild()
                        onNavigateToItemDetail(id)
                    },
                    onNavigateToPersonDetail = { id, name ->
                        resultsFocusRequester.saveFocusedChild()
                        onNavigateToPersonDetail(id, name)
                    },
                    onNavigateToPlayer = { id ->
                        resultsFocusRequester.saveFocusedChild()
                        onNavigateToPlayer(id)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SearchInputField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var isEditing by remember { mutableStateOf(false) }
    var editorHadFocus by remember { mutableStateOf(false) }
    val editFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditing) {
        if (isEditing) editFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) NeonPink else Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 20.dp)
            .then(
                if (isEditing) Modifier else Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        editorHadFocus = false
                        isEditing = true
                    },
                ),
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (isEditing) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                singleLine = true,
                cursorBrush = SolidColor(NeonPink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(editFocusRequester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            editorHadFocus = true
                        } else if (editorHadFocus) {
                            editorHadFocus = false
                            isEditing = false
                        }
                    },
            )
        } else {
            Text(
                text = value.ifEmpty { "Search movies, performers, categories…" },
                style = TextStyle(
                    color = if (value.isEmpty()) Color.White.copy(alpha = 0.45f) else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun SearchResultsList(
    movies: List<TvItem>,
    actors: List<TvPerson>,
    channelVideos: List<VideoDto>,
    resolverBaseUrl: String,
    resultsFocusRequester: FocusRequester,
    onNavigateToItemDetail: (itemId: String) -> Unit,
    onNavigateToPersonDetail: (personId: String, personName: String) -> Unit,
    onNavigateToPlayer: (itemId: String) -> Unit,
    modifier: Modifier = Modifier,
    clientManager: JellyfinClientManager = koinInject(),
) {
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 250.dp),
        state = gridState,
        modifier = modifier
            .focusRequester(resultsFocusRequester)
            .focusRestorer(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Movies Section
        if (movies.isNotEmpty()) {
            item(key = "section_movies_header", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Movies (${movies.size})",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    ),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            items(items = movies, key = { "movie_${it.id}" }) { item ->
                val imageUrl = clientManager.getItemImageUrl(item)
                MovieCard(
                    item = item,
                    imageUrl = imageUrl,
                    onClick = { onNavigateToItemDetail(item.id) },
                    cardWidth = null,
                )
            }

            item(key = "section_movies_spacer", span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // Keep performer results in the same vertical scroll as movies.
        if (actors.isNotEmpty()) {
            item(key = "section_actors_header", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Performers (${actors.size})",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    ),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            items(items = actors, key = { "person_${it.id}" }) { person ->
                val imageUrl = clientManager.getPersonImageUrl(person.id, person.primaryImageTag)
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PersonCard(
                        person = person,
                        imageUrl = imageUrl,
                        onClick = { onNavigateToPersonDetail(person.id, person.name) },
                    )
                }
            }
        }

        if (channelVideos.isNotEmpty()) {
            item(key = "section_channel_videos_header", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Resolver Videos (${channelVideos.size})",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    ),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            items(items = channelVideos, key = { "resolver_${it.id}" }) { video ->
                ResolverVideoCard(
                    video = video,
                    imageUrl = resolveResolverUrl(video.thumbnail, resolverBaseUrl),
                    onClick = {
                        onNavigateToPlayer(video.providerVideoId.ifBlank { video.id })
                    },
                )
            }
        }
    }
}

@Composable
private fun ResolverVideoCard(
    video: VideoDto,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1.06f),
        border = CardDefaults.border(
            border = Border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.5.dp, NeonPink)),
        ),
        colors = CardDefaults.colors(
            containerColor = CardSurface,
            focusedContainerColor = CardSurfaceFocused,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(SurfaceDark),
            ) {
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    fontWeight = FontWeight.SemiBold,
                )
                val metadata = (video.performers.firstOrNull() ?: video.categories.firstOrNull()).orEmpty()
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
