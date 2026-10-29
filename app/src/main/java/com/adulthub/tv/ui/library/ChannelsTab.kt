package com.adulthub.tv.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import coil.compose.AsyncImage
import com.adulthub.tv.R
import com.adulthub.tv.data.model.ChannelSortMode
import com.adulthub.tv.data.model.DurationFilter
import com.adulthub.tv.data.model.TvChannel
import com.adulthub.tv.data.resolver.VideoDto
import com.adulthub.tv.ui.components.DurationBadge
import com.adulthub.tv.ui.components.ErrorMessage
import com.adulthub.tv.ui.components.LoadingIndicator
import com.adulthub.tv.ui.components.TvPrimaryButton
import com.adulthub.tv.ui.components.TvSecondaryButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import com.adulthub.tv.ui.theme.CardSurface
import com.adulthub.tv.ui.theme.CardSurfaceFocused
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.SurfaceDark
import com.adulthub.tv.ui.theme.TextPrimary
import com.adulthub.tv.ui.theme.TextSecondary
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject

@Composable
fun ChannelsTab(
    state: ChannelsTabState,
    onChannelClick: (TvChannel) -> Unit,
    onVideoClick: (VideoDto) -> Unit,
    onBackToChannels: () -> Unit,
    onRetry: () -> Unit,
    onRetryChannelVideos: () -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {},
    onSortModeChange: (ChannelSortMode) -> Unit = {},
    onHideChannel: () -> Unit = {},
    onDurationFilterChange: (DurationFilter) -> Unit = {},
    getVideoThumbnailUrl: (String?) -> String = { it.orEmpty() },
) {
    val telegramProgressStore: com.adulthub.tv.data.local.TelegramProgressStore = koinInject()
    val progressList by telegramProgressStore.allProgress.collectAsStateWithLifecycle(initialValue = emptyList())
    val progressMap = remember(progressList) { progressList.associateBy { it.providerVideoId } }
    val gridState = rememberLazyGridState()
    val channelsGridState = rememberLazyGridState()
    val channelFocusRequesterMap = remember { mutableMapOf<String, FocusRequester>() }
    val videoFocusRequesterMap = remember { mutableMapOf<String, FocusRequester>() }

    LaunchedEffect(state.lastSelectedChannelId, state.selectedChannel) {
        if (state.selectedChannel == null) {
            val targetId = state.lastSelectedChannelId ?: return@LaunchedEffect
            val index = state.channels.indexOfFirst { it.id == targetId }
            if (index >= 0) {
                channelsGridState.scrollToItem(index)
                delay(150L)
                runCatching {
                    channelFocusRequesterMap[targetId]?.requestFocus()
                }
            }
        }
    }

    LaunchedEffect(state.lastSelectedVideoId, state.selectedChannel) {
        if (state.selectedChannel != null) {
            val targetId = state.lastSelectedVideoId ?: return@LaunchedEffect
            val index = state.channelVideos.indexOfFirst { it.id == targetId }
            if (index >= 0) {
                gridState.scrollToItem(index)
                delay(150L)
                runCatching {
                    videoFocusRequesterMap[targetId]?.requestFocus()
                }
            }
        }
    }

    LaunchedEffect(gridState, state.channelVideos.size, state.hasMoreVideos, state.channelVideosError) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (
                    lastVisibleIndex != null &&
                    state.channelVideos.isNotEmpty() &&
                    lastVisibleIndex >= state.channelVideos.lastIndex - 6 &&
                    state.hasMoreVideos &&
                    state.channelVideosError == null &&
                    !state.isLoadingMoreVideos &&
                    !state.isLoadingVideos
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
        if (state.selectedChannel != null) {
            // Selected Channel Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = state.selectedChannel.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonPink,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (state.hasMoreVideos) {
                            stringResource(R.string.library_video_count_plus, state.channelVideos.size)
                        } else {
                            stringResource(R.string.library_video_count, state.channelVideos.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Sort Cycler Button
                    TvSecondaryButton(
                        onClick = {
                            val nextMode = when (state.sortMode) {
                                ChannelSortMode.LATEST -> ChannelSortMode.LONGEST
                                ChannelSortMode.LONGEST -> ChannelSortMode.SHORTEST
                                ChannelSortMode.SHORTEST -> ChannelSortMode.LATEST
                            }
                            onSortModeChange(nextMode)
                        },
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text(text = stringResource(R.string.library_sort_value, stringResource(state.sortMode.labelRes)), fontSize = 12.sp)
                    }

                    // Duration Filter Cycler Button
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
                        modifier = Modifier.height(34.dp),
                    ) {
                        val durationText = if (state.durationFilter == DurationFilter.ALL) {
                            stringResource(R.string.filter_duration_all)
                        } else {
                            stringResource(R.string.library_filter_duration, stringResource(state.durationFilter.labelRes))
                        }
                        Text(text = durationText, fontSize = 12.sp)
                    }

                    // Hide Channel Button
                    TvSecondaryButton(
                        onClick = onHideChannel,
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text(text = stringResource(R.string.action_hide_channel), fontSize = 12.sp)
                    }

                    // Back to Channels Button
                    TvSecondaryButton(
                        onClick = onBackToChannels,
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text(text = stringResource(R.string.library_back_to_channels), fontSize = 12.sp)
                    }
                }
            }

            if (state.isLoadingVideos) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(message = stringResource(R.string.library_loading_channel_videos))
                }
            } else if (state.channelVideosError != null && state.channelVideos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorMessage(
                        message = state.channelVideosError,
                        onRetry = onRetryChannelVideos,
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }
            } else if (state.channelVideos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.library_no_videos_in_channel, state.selectedChannel.title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = gridState,
                    contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = state.channelVideos,
                        key = { it.id },
                    ) { video ->
                        val requester = remember(video.id) {
                            videoFocusRequesterMap.getOrPut(video.id) { FocusRequester() }
                        }
                        val provId = video.providerVideoId.ifBlank { video.id.removePrefix("telegram:") }
                        val record = progressMap[provId] ?: progressMap[video.id]
                        ChannelVideoCard(
                            video = video,
                            thumbnailUrl = getVideoThumbnailUrl(video.thumbnail),
                            isPlayed = record?.isPlayed == true,
                            progressFraction = record?.progressFraction ?: 0f,
                            onClick = { onVideoClick(video) },
                            modifier = Modifier.focusRequester(requester),
                        )
                    }

                    if (state.isLoadingMoreVideos) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingIndicator(message = stringResource(R.string.library_loading_more_channel_videos))
                            }
                        }
                    }

                    if (state.hasMoreVideos && !state.isLoadingMoreVideos) {
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
                                    Text(text = stringResource(R.string.library_load_more_videos))
                                }
                            }
                        }
                    }

                    if (state.channelVideosError != null && state.channelVideos.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = state.channelVideosError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                                TvSecondaryButton(
                                    onClick = onLoadMore,
                                    modifier = Modifier.height(36.dp),
                                ) {
                                    Text(text = stringResource(R.string.library_retry_loading_more))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Channel List
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.library_video_channels_count,
                        state.channels.size,
                        state.channels.size,
                    ),
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
                        LoadingIndicator(message = stringResource(R.string.library_loading_channels))
                    }
                }
                state.errorMessage != null && state.channels.isEmpty() -> {
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
                            text = stringResource(R.string.library_no_channels_available),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        state = channelsGridState,
                        contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = state.channels,
                            key = { it.id },
                        ) { channel ->
                            val requester = remember(channel.id) {
                                channelFocusRequesterMap.getOrPut(channel.id) { FocusRequester() }
                            }
                            ChannelCard(
                                channel = channel,
                                onClick = { onChannelClick(channel) },
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
private fun ChannelCard(
    channel: TvChannel,
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
                text = channel.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = channel.slug,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ChannelVideoCard(
    video: VideoDto,
    thumbnailUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlayed: Boolean = false,
    progressFraction: Float = 0f,
) {
    val totalSec = video.duration.toLong()
    val untitledVideo = stringResource(R.string.library_untitled_video)
    val formattedDuration = if (totalSec > 0) {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.US, "%02d:%02d", m, s)
    } else ""

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
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
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Watched badge top-right
                if (isPlayed) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
                            .border(1.dp, NeonPink.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.library_watched_badge),
                            color = NeonPink,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (formattedDuration.isNotBlank()) {
                    DurationBadge(
                        durationText = formattedDuration,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(horizontal = 8.dp, vertical = if (!isPlayed && progressFraction > 0f) 12.dp else 8.dp),
                    )
                }

                // Resume progress bar if partially played and not completed
                if (!isPlayed && progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color(0x80000000)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction)
                                .background(NeonPink),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text(
                    text = video.title.ifBlank { untitledVideo },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
