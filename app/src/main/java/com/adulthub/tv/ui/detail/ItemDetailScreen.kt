package com.adulthub.tv.ui.detail

import android.net.Uri
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.model.TvPersonRole
import com.adulthub.tv.ui.components.ErrorMessage
import com.adulthub.tv.ui.components.LoadingIndicator
import com.adulthub.tv.ui.components.MovieCard
import com.adulthub.tv.ui.components.PersonCard
import com.adulthub.tv.ui.components.TvPrimaryButton
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.components.isPoorThumbnail
import com.adulthub.tv.ui.theme.CardSurface
import com.adulthub.tv.ui.theme.CardSurfaceFocused
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.res.stringResource
import com.adulthub.tv.R
import com.adulthub.tv.data.model.ShuffleContextMode
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.OledBlack
import com.adulthub.tv.ui.theme.SurfaceDark
import com.adulthub.tv.ui.theme.TextPrimary
import com.adulthub.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun ItemDetailScreen(
    itemId: String,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (String, Boolean?) -> Unit,
    onNavigateToItemDetail: (String) -> Unit,
    onNavigateToPersonDetail: (String, String) -> Unit,
    onNavigateToSearch: (String) -> Unit = {},
    isCurrentDestination: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: ItemDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val contentFocusRequester = remember(itemId) { FocusRequester() }
    var hasBeenCurrentDestination by remember(itemId) { mutableStateOf(false) }
    val context = LocalContext.current
    val settingsDataStore: SettingsDataStore = koinInject()
    val jellyfinClientManager: JellyfinClientManager = koinInject()
    var phashThumbnailUrl by remember(itemId) { mutableStateOf<String?>(null) }
    var usePhashBackdrop by remember(itemId) { mutableStateOf(false) }
    var selectedTagForAction by remember(itemId) { mutableStateOf<String?>(null) }

    LaunchedEffect(itemId) {
        viewModel.loadDetails(itemId)
    }

    LaunchedEffect(isCurrentDestination, itemId) {
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
            when (event) {
                is ItemDetailNavEvent.NavigateToPlayer -> {
                    runCatching { contentFocusRequester.saveFocusedChild() }
                    onNavigateToPlayer(event.itemId, event.resume)
                }
                is ItemDetailNavEvent.NavigateToItemDetail -> {
                    runCatching { contentFocusRequester.saveFocusedChild() }
                    onNavigateToItemDetail(event.itemId)
                }
                is ItemDetailNavEvent.NavigateToPersonDetail -> {
                    runCatching { contentFocusRequester.saveFocusedChild() }
                    onNavigateToPersonDetail(event.personId, event.personName)
                }
                is ItemDetailNavEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack),
    ) {
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(message = "Loading movie details…")
                }
            }
            state.errorMessage != null && state.item == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorMessage(
                        message = state.errorMessage ?: "Failed to load movie",
                        onRetry = { viewModel.loadDetails(itemId) },
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }
            }
            state.item != null -> {
                val item = state.item!!

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(contentFocusRequester)
                        .focusRestorer(),
                    contentPadding = PaddingValues(bottom = 64.dp),
                ) {
                    // Hero Backdrop with Gradient Overlay
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp),
                        ) {
                            val backdropUrl = viewModel.getBackdropImageUrl(item)
                            LaunchedEffect(item.id, item.providerVideoId) {
                                val rawId = item.providerVideoId?.takeIf { it.isNotBlank() }
                                    ?: item.id.takeIf { it.startsWith("telegram:") }?.removePrefix("telegram:")
                                if (rawId != null) {
                                    runCatching {
                                        val configured = settingsDataStore.getResolverBaseUrl().trimEnd('/')
                                        val base = settingsDataStore.requireAllowedServiceUrl(configured).normalized.trimEnd('/')
                                        val videoId = if (rawId.startsWith("telegram:")) rawId else "telegram:$rawId"
                                        phashThumbnailUrl = "$base/admin/api/v2/video-phash/thumbnail?video_id=${Uri.encode(videoId)}"
                                    }
                                }
                            }
                            val selectedBackdropUrl = if (usePhashBackdrop || backdropUrl.isBlank()) {
                                phashThumbnailUrl
                            } else {
                                backdropUrl
                            }
                            val backdropRequest = remember(selectedBackdropUrl, item.id) {
                                selectedBackdropUrl?.let { url ->
                                    ImageRequest.Builder(context).data(url).apply {
                                        jellyfinClientManager.getApiOrNull()?.accessToken
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { addHeader("X-Emby-Token", it) }
                                    }.build()
                                }
                            }
                            if (backdropRequest != null) {
                                AsyncImage(
                                    model = backdropRequest,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    onSuccess = { result ->
                                        if (!usePhashBackdrop && backdropUrl.isNotBlank()) {
                                            usePhashBackdrop = isPoorThumbnail(result.result.drawable)
                                        }
                                    },
                                    onError = {
                                        if (phashThumbnailUrl != null) usePhashBackdrop = true
                                    },
                                )
                            }

                            // Gradient fade from transparent at top to OledBlack at bottom
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.3f),
                                                Color.Black.copy(alpha = 0.6f),
                                                OledBlack,
                                            ),
                                            startY = 0f,
                                            endY = Float.POSITIVE_INFINITY,
                                        )
                                    ),
                            )

                            // Top Back Button
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 48.dp, top = 24.dp),
                            ) {
                                TvSecondaryButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.height(38.dp),
                                ) {
                                    Text(text = "Back", fontSize = 13.sp)
                                }
                            }

                            // Content overlay inside backdrop lower area
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 48.dp, vertical = 16.dp),
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.displaySmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    val meta = buildString {
                                        item.year?.let { append("$it") }
                                        if (item.formattedDuration.isNotBlank()) {
                                            if (isNotEmpty()) append(" • ")
                                            append(item.formattedDuration)
                                        }
                                        val studio = item.studios.firstOrNull()
                                        if (!studio.isNullOrBlank()) {
                                            if (isNotEmpty()) append(" • ")
                                            append(studio)
                                        }
                                    }

                                    if (meta.isNotBlank()) {
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = TextSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Action Buttons Row
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TvPrimaryButton(
                                onClick = viewModel::playMovie,
                                modifier = Modifier.height(44.dp),
                            ) {
                                Text(text = "Play", fontWeight = FontWeight.Bold)
                            }

                            val resumeMs = item.playbackPositionTicks / 10_000L
                            val durationMs = item.runtimeTicks?.div(10_000L)
                                ?: ((item.runtimeMinutes ?: 0) * 60_000L)
                            val canResume = resumeMs > 15_000L &&
                                (durationMs <= 0L || resumeMs < durationMs - 30_000L)
                            if (canResume) {
                                val pct = (item.progressFraction * 100).toInt()
                                TvSecondaryButton(
                                    onClick = viewModel::resumeMovie,
                                    modifier = Modifier.height(44.dp),
                                ) {
                                    Text(text = "Resume ($pct%)")
                                }
                            }

                            TvSecondaryButton(
                                onClick = viewModel::playRandom,
                                modifier = Modifier.height(44.dp),
                            ) {
                                Text(text = "Random movie")
                            }

                            val hasActors = item.people.any { it.type == "Actor" || it.type == null }
                            if (!item.isTelegram && hasActors) {
                                TvSecondaryButton(
                                    onClick = { viewModel.playRandomContextual(ShuffleContextMode.SAME_PERFORMER) },
                                    modifier = Modifier.height(44.dp),
                                ) {
                                    Text(text = stringResource(R.string.shuffle_same_performer))
                                }
                            }

                            if (!item.isTelegram && item.genres.isNotEmpty()) {
                                TvSecondaryButton(
                                    onClick = { viewModel.playRandomContextual(ShuffleContextMode.SAME_CATEGORY) },
                                    modifier = Modifier.height(44.dp),
                                ) {
                                    Text(text = stringResource(R.string.shuffle_same_category))
                                }
                            }

                            if (!item.isTelegram) {
                                val favText = if (state.isFavorite) "Favorited" else "Add to favorites"
                                TvSecondaryButton(
                                    onClick = viewModel::toggleFavorite,
                                    modifier = Modifier.height(44.dp),
                                ) {
                                    Text(text = favText)
                                }
                            }

                            val watchedText = if (item.isPlayed) "Watched ✓" else "Mark watched"
                            TvSecondaryButton(
                                onClick = viewModel::toggleWatched,
                                modifier = Modifier.height(44.dp),
                            ) {
                                Text(text = watchedText)
                            }
                        }
                    }

                    // Genres & Tags Chips (Clickable filters)
                    if (item.genres.isNotEmpty() || item.tags.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp, vertical = 8.dp),
                            ) {
                                if (item.genres.isNotEmpty()) {
                                    LazyRow(
                                        modifier = Modifier.focusRestorer().padding(bottom = 8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(item.genres, key = { "genre_$it" }) { genre ->
                                            TvFilterChip(
                                                text = genre,
                                                borderColor = NeonPink,
                                                onClick = { selectedTagForAction = genre },
                                            )
                                        }
                                    }
                                }

                                if (item.tags.isNotEmpty()) {
                                    LazyRow(
                                        modifier = Modifier.focusRestorer(),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(item.tags, key = { "tag_$it" }) { tag ->
                                            TvFilterChip(
                                                text = "#$tag",
                                                borderColor = NeonPink,
                                                onClick = { selectedTagForAction = tag },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Overview
                    if (!item.overview.isNullOrBlank()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = "Overview",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.overview ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.fillMaxWidth(0.8f),
                                )
                            }
                        }
                    }

                    // Cast Row
                    val castActors = item.people.filter { it.type == "Actor" || it.type == null }
                    if (castActors.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                            ) {
                                Text(
                                    text = "Cast",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 6.dp),
                                )

                                LazyRow(
                                    modifier = Modifier.focusRestorer(),
                                    contentPadding = PaddingValues(horizontal = 48.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    items(castActors, key = { "person_${it.id}" }) { role ->
                                        val person = TvPerson(
                                            id = role.id,
                                            name = role.name,
                                            primaryImageTag = role.primaryImageTag,
                                        )
                                        PersonCard(
                                            person = person,
                                            imageUrl = viewModel.getPersonImageUrl(role.id, role.primaryImageTag),
                                            onClick = { viewModel.onCastPersonClicked(role) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Similar Scenes Row (Jellyfin only)
                    if (!item.isTelegram && state.similarItems.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                            ) {
                                Text(
                                    text = "🔥 Similar Scenes",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 6.dp),
                                )

                                LazyRow(
                                    modifier = Modifier.focusRestorer(),
                                    contentPadding = PaddingValues(horizontal = 48.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    items(state.similarItems, key = { it.id }) { similarItem ->
                                        MovieCard(
                                            item = similarItem,
                                            imageUrl = viewModel.getItemImageUrl(similarItem),
                                            onClick = { viewModel.onSimilarMovieClicked(similarItem) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedTagForAction?.let { tag ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { selectedTagForAction = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .widthIn(max = 560.dp)
                    .background(SurfaceDark, RoundedCornerShape(18.dp))
                    .padding(28.dp),
            ) {
                Text(
                    text = stringResource(R.string.tag_options_title, tag),
                    style = MaterialTheme.typography.headlineSmall,
                    color = NeonPink,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Choose an action for this tag:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvSecondaryButton(
                        onClick = { selectedTagForAction = null },
                        modifier = Modifier.height(40.dp),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TvSecondaryButton(
                        onClick = {
                            val t = tag
                            selectedTagForAction = null
                            runCatching { contentFocusRequester.saveFocusedChild() }
                            onNavigateToSearch(t)
                        },
                        modifier = Modifier.height(40.dp),
                    ) {
                        Text(stringResource(R.string.action_search_tag))
                    }
                    TvPrimaryButton(
                        onClick = {
                            val t = tag
                            selectedTagForAction = null
                            viewModel.excludeTag(t)
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.tag_excluded_snackbar),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                        modifier = Modifier.height(40.dp),
                    ) {
                        Text(stringResource(R.string.action_exclude_tag))
                    }
                }
            }
        }
    }
}

@Composable
private fun TvFilterChip(
    text: String,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    OutlinedButton(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = OutlinedButtonDefaults.colors(
            containerColor = if (isFocused) SurfaceDark else Color.White.copy(alpha = 0.08f),
            focusedContainerColor = SurfaceDark,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = OutlinedButtonDefaults.border(
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isFocused) borderColor else borderColor.copy(alpha = 0.4f)
                )
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.5.dp, borderColor)
            ),
        ),
        shape = OutlinedButtonDefaults.shape(RoundedCornerShape(8.dp)),
        modifier = modifier.height(34.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isFocused) borderColor else Color.White,
        )
    }
}
