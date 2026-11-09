package com.adulthub.tv.ui.library

import android.app.Application
import com.adulthub.tv.R
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.ChannelSortMode
import com.adulthub.tv.data.model.DurationFilter
import com.adulthub.tv.data.model.TvChannel
import com.adulthub.tv.data.repository.ChannelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the Channels tab: the channel list, the paged video list of the selected channel,
 * the fallback to the category listing, and the channel filters and sort order.
 */
internal class ChannelsTabController(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<LibraryUiState>,
    private val channelRepository: ChannelRepository,
    private val settingsDataStore: SettingsDataStore?,
    private val context: Application
) {

    private var channelVideosJob: Job? = null

    fun loadChannels() {
        scope.launch {
            uiState.update { state ->
                state.copy(channelsState = state.channelsState.copy(isLoading = true, errorMessage = null))
            }
            val result = channelRepository.getCategories()
            uiState.update { state ->
                val hidden = state.channelsState.hiddenChannelIds
                result.fold(
                    onSuccess = { channels ->
                        val visible = channels.filter { it.id !in hidden }
                        state.copy(
                            channelsState = state.channelsState.copy(
                                channels = visible,
                                isLoading = false
                            )
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            channelsState = state.channelsState.copy(
                                isLoading = false,
                                errorMessage = error.localizedMessage ?: context.getString(R.string.library_error_channels)
                            )
                        )
                    }
                )
            }
        }
    }

    fun hideCurrentChannel() {
        val currentChannel = uiState.value.channelsState.selectedChannel ?: return
        scope.launch {
            settingsDataStore?.hideChannel(currentChannel.id)
            selectChannel(null)
            loadChannels()
        }
    }

    fun setChannelDurationFilter(filter: DurationFilter) {
        uiState.update { state ->
            val cs = state.channelsState
            val filtered = applyChannelFilters(cs.rawChannelVideos, filter, cs.excludedTags, cs.sortMode)
            state.copy(
                channelsState = cs.copy(
                    durationFilter = filter,
                    channelVideos = filtered
                )
            )
        }
    }

    fun selectChannel(channel: TvChannel?) {
        channelVideosJob?.cancel()
        uiState.update { state ->
            state.copy(
                channelsState = state.channelsState.copy(
                    selectedChannel = channel,
                    lastSelectedChannelId = channel?.id ?: state.channelsState.lastSelectedChannelId,
                    rawChannelVideos = emptyList(),
                    channelVideos = emptyList(),
                    channelVideosError = null,
                    isLoadingVideos = false,
                    isLoadingMoreVideos = false,
                    hasMoreVideos = false,
                    nextOffsetId = null,
                    totalMessageCount = 0
                )
            )
        }
        channel?.let { loadChannelVideos(it, reset = true) }
    }

    fun setChannelSortMode(mode: ChannelSortMode) {
        uiState.update { state ->
            val cs = state.channelsState
            val sorted = applyChannelFilters(cs.rawChannelVideos, cs.durationFilter, cs.excludedTags, mode)
            state.copy(
                channelsState = cs.copy(
                    sortMode = mode,
                    channelVideos = sorted
                )
            )
        }
        scope.launch {
            settingsDataStore?.setChannelSortMode(mode)
        }
    }

    fun retrySelectedChannelVideos() {
        val channel = uiState.value.channelsState.selectedChannel ?: return
        val isFirstPage = uiState.value.channelsState.channelVideos.isEmpty()
        loadChannelVideos(channel, reset = isFirstPage)
    }

    fun loadMoreChannelVideos() {
        val channelsState = uiState.value.channelsState
        val channel = channelsState.selectedChannel ?: return
        if (!channelsState.hasMoreVideos || channelsState.isLoadingMoreVideos || channelsState.isLoadingVideos || channelVideosJob?.isActive == true) return
        loadChannelVideos(channel, reset = false)
    }

    private fun loadChannelVideos(channel: TvChannel, reset: Boolean = true, autoFetchEmptyAttempts: Int = 0) {
        val currentState = uiState.value.channelsState
        if (!reset && (!currentState.hasMoreVideos || currentState.isLoadingMoreVideos || channelVideosJob?.isActive == true)) return
        if (reset) channelVideosJob?.cancel()

        channelVideosJob = scope.launch {
            uiState.update { state ->
                if (state.channelsState.selectedChannel?.id == channel.id) {
                    state.copy(
                        channelsState = state.channelsState.copy(
                            isLoadingVideos = reset,
                            isLoadingMoreVideos = !reset,
                            channelVideosError = if (reset) null else state.channelsState.channelVideosError
                        )
                    )
                } else {
                    state
                }
            }

            val currentOffset = if (reset) null else uiState.value.channelsState.nextOffsetId
            val historyResult = channelRepository.getChannelHistory(
                channelId = channel.id,
                offsetId = currentOffset,
                limit = 100
            )

            // The fallback is a network call, so it is resolved before the state update:
            // update{} re-runs its whole lambda whenever another writer wins the
            // compare-and-set, which would repeat the request.
            val fallbackResult = historyResult.exceptionOrNull()
                ?.takeIf { reset && uiState.value.channelsState.selectedChannel?.id == channel.id }
                ?.let { channelRepository.getCategoryVideos(channelId = channel.id) }

            var shouldAutoFetch = false
            uiState.update { state ->
                if (state.channelsState.selectedChannel?.id != channel.id) return@update state
                historyResult.fold(
                    onSuccess = { page ->
                        val combined = if (reset) {
                            page.videos.distinctBy { it.providerVideoId.ifBlank { it.id } }
                        } else {
                            (state.channelsState.rawChannelVideos + page.videos).distinctBy { it.providerVideoId.ifBlank { it.id } }
                        }
                        val filtered = applyChannelFilters(
                            combined,
                            state.channelsState.durationFilter,
                            state.channelsState.excludedTags,
                            state.channelsState.sortMode
                        )
                        val hasMore = page.hasMore && page.nextOffsetId != null && page.nextOffsetId != currentOffset
                        if (filtered.isEmpty() && hasMore && autoFetchEmptyAttempts < 3) {
                            shouldAutoFetch = true
                        }
                        state.copy(
                            channelsState = state.channelsState.copy(
                                rawChannelVideos = combined,
                                channelVideos = filtered,
                                isLoadingVideos = false,
                                isLoadingMoreVideos = false,
                                channelVideosError = null,
                                hasMoreVideos = hasMore,
                                nextOffsetId = page.nextOffsetId,
                                totalMessageCount = page.messageCount
                            )
                        )
                    },
                    onFailure = { error ->
                        if (reset) {
                            fallbackResult?.fold(
                                onSuccess = { fallbackVideos ->
                                    val distinct = fallbackVideos.distinctBy { it.providerVideoId.ifBlank { it.id } }
                                    val sortedFallback = applyChannelFilters(
                                        distinct,
                                        state.channelsState.durationFilter,
                                        state.channelsState.excludedTags,
                                        state.channelsState.sortMode
                                    )
                                    state.copy(
                                        channelsState = state.channelsState.copy(
                                            rawChannelVideos = distinct,
                                            channelVideos = sortedFallback,
                                            isLoadingVideos = false,
                                            isLoadingMoreVideos = false,
                                            channelVideosError = null,
                                            hasMoreVideos = false,
                                            nextOffsetId = null,
                                            totalMessageCount = distinct.size
                                        )
                                    )
                                },
                                onFailure = { fallbackError ->
                                    state.copy(
                                        channelsState = state.channelsState.copy(
                                            rawChannelVideos = emptyList(),
                                            channelVideos = emptyList(),
                                            isLoadingVideos = false,
                                            isLoadingMoreVideos = false,
                                            channelVideosError = fallbackError.localizedMessage ?: error.localizedMessage ?: context.getString(R.string.library_error_channel_videos),
                                            hasMoreVideos = false,
                                            nextOffsetId = null
                                        )
                                    )
                                }
                            ) ?: state.copy(
                                channelsState = state.channelsState.copy(
                                    isLoadingVideos = false,
                                    isLoadingMoreVideos = false,
                                    channelVideosError = error.localizedMessage ?: context.getString(R.string.library_error_channel_videos)
                                )
                            )
                        } else {
                            state.copy(
                                channelsState = state.channelsState.copy(
                                    isLoadingMoreVideos = false,
                                    channelVideosError = error.localizedMessage ?: context.getString(R.string.library_error_more_channel_videos)
                                )
                            )
                        }
                    }
                )
            }
            if (shouldAutoFetch) {
                channelVideosJob = null
                loadChannelVideos(channel, reset = false, autoFetchEmptyAttempts = autoFetchEmptyAttempts + 1)
            }
        }
    }
}
