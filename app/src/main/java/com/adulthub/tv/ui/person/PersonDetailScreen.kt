package com.adulthub.tv.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import com.adulthub.tv.R
import com.adulthub.tv.ui.components.ErrorMessage
import com.adulthub.tv.ui.components.LoadingIndicator
import com.adulthub.tv.ui.components.MovieCard
import com.adulthub.tv.ui.components.TvPageHeader
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
fun PersonDetailScreen(
    personId: String,
    personName: String,
    onNavigateBack: () -> Unit,
    onNavigateToItemDetail: (String) -> Unit,
    isCurrentDestination: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: PersonDetailViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val contentFocusRequester = remember(personId) { FocusRequester() }
    var hasBeenCurrentDestination by rememberSaveable(personId) { mutableStateOf(false) }

    LaunchedEffect(personId, personName) {
        viewModel.loadPerson(personId, personName)
    }

    LaunchedEffect(isCurrentDestination, personId) {
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
                is PersonDetailNavEvent.NavigateToItemDetail -> {
                    runCatching { contentFocusRequester.saveFocusedChild() }
                    onNavigateToItemDetail(event.itemId)
                }
                is PersonDetailNavEvent.NavigateBack -> onNavigateBack()
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
                    LoadingIndicator(message = stringResource(R.string.library_loading_performer_details))
                }
            }
            state.errorMessage != null && state.person == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorMessage(
                        message = state.errorMessage ?: stringResource(R.string.library_failed_to_load_performer),
                        onRetry = { viewModel.loadPerson(personId, personName) },
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }
            }
            state.person != null -> {
                val person = state.person!!

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(contentFocusRequester)
                        .focusRestorer(),
                    contentPadding = PaddingValues(bottom = 48.dp),
                ) {
                    // Shared page header
                    item {
                        TvPageHeader(title = person.name, onBack = onNavigateBack)
                    }

                    // Performer Profile Info
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 12.dp),
                        ) {
                            // 2:3 Photo
                            Box(
                                modifier = Modifier
                                    .width(180.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CardSurface)
                                    .border(1.dp, CardSurfaceFocused, RoundedCornerShape(12.dp)),
                            ) {
                                AsyncImage(
                                    model = viewModel.getPersonImageUrl(person),
                                    contentDescription = person.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            Spacer(modifier = Modifier.width(32.dp))

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                Spacer(modifier = Modifier.height(4.dp))

                                if (!person.birthDate.isNullOrBlank()) {
                                    Row {
                                        Text(text = stringResource(R.string.library_label_born), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                        Text(text = person.birthDate ?: "", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                if (!person.nationality.isNullOrBlank()) {
                                    Row {
                                        Text(text = stringResource(R.string.library_label_nationality), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                        Text(text = person.nationality ?: "", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                if (person.aliases.isNotEmpty()) {
                                    Row {
                                        Text(text = stringResource(R.string.library_label_aliases), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                        Text(text = person.aliases.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                if (person.movieCount > 0 || state.movies.isNotEmpty()) {
                                    val count = if (state.movies.isNotEmpty()) state.movies.size else person.movieCount
                                    Row {
                                        Text(text = stringResource(R.string.library_label_filmography), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                        Text(text = stringResource(R.string.library_scenes_count, count), style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                if (!person.bio.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.library_bio),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = person.bio ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        lineHeight = 22.sp,
                                    )
                                }
                            }
                        }
                    }

                    // Filmography Row
                    if (state.movies.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.library_filmography_section, state.movies.size),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
                                )

                                LazyRow(
                                    modifier = Modifier.focusRestorer(),
                                    contentPadding = PaddingValues(horizontal = 48.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    items(state.movies, key = { it.id }) { movie ->
                                        MovieCard(
                                            item = movie,
                                            imageUrl = viewModel.getItemImageUrl(movie),
                                            onClick = { viewModel.onMovieClicked(movie) },
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
}
