package com.adulthub.tv.ui.player

import android.os.SystemClock
import com.adulthub.tv.diagnostics.AppLog as Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.local.TelegramProgressStore
import com.adulthub.tv.data.local.WatchHistoryStore
import com.adulthub.tv.data.model.PlaybackInfo
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.repository.ChannelRepository
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.PlaybackRepository
import com.adulthub.tv.player.PlaybackReporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Ready(
        val item: TvItem,
        val playbackInfo: PlaybackInfo,
        val startPositionMs: Long = 0L,
        val playbackRequestId: Long = 0L,
        val resolverLatencyMs: Long = 0L,
        val pendingResumePositionMs: Long? = null,
    ) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

class PlayerViewModel(
    private val libraryRepository: LibraryRepository,
    private val playbackRepository: PlaybackRepository,
    private val settingsDataStore: SettingsDataStore,
    val playbackReporter: PlaybackReporter,
    private val channelRepository: ChannelRepository? = null,
    private val telegramProgressStore: TelegramProgressStore? = null,
    private val watchHistoryStore: WatchHistoryStore? = null,
) : ViewModel() {

    companion object {
        private const val PERF_TAG = "PlaybackPerf"
    }

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _isPlaybackEnded = MutableStateFlow(false)
    val isPlaybackEnded: StateFlow<Boolean> = _isPlaybackEnded.asStateFlow()

    private val _isLoadingRandom = MutableStateFlow(false)
    val isLoadingRandom: StateFlow<Boolean> = _isLoadingRandom.asStateFlow()

    private var playbackRequestCounter = 0L

    fun loadItem(itemId: String, resume: Boolean? = null) {
        viewModelScope.launch {
            val playbackRequestId = ++playbackRequestCounter
            val loadStartedAt = SystemClock.elapsedRealtime()
            _uiState.value = PlayerUiState.Loading
            _isPlaybackEnded.value = false

            val itemLookupStartedAt = SystemClock.elapsedRealtime()
            val itemResult = libraryRepository.getItemDetails(itemId)
            Log.i(PERF_TAG, "item_lookup_ms=${SystemClock.elapsedRealtime() - itemLookupStartedAt}")
            var item = itemResult.getOrNull()

            if (item == null) {
                val cachedVideo = channelRepository?.getVideo(itemId)
                if (cachedVideo != null) {
                    item = TvItem(
                        id = cachedVideo.id,
                        title = cachedVideo.title.ifBlank { "Channel Video" },
                        overview = cachedVideo.description,
                        runtimeTicks = (cachedVideo.duration * 10_000_000).toLong(),
                        genres = cachedVideo.categories,
                        providerVideoId = cachedVideo.providerVideoId.ifBlank {
                            cachedVideo.id.removePrefix("telegram:")
                        },
                        fileSizeBytes = cachedVideo.fileSizeBytes,
                    )
                } else if (itemId.contains(":") || itemId.startsWith("-100")) {
                    val provRef = if (itemId.startsWith("telegram:")) itemId.removePrefix("telegram:") else itemId
                    item = TvItem(
                        id = itemId,
                        title = "Channel Video",
                        providerVideoId = provRef,
                    )
                }
            }

            if (item == null) {
                _uiState.value = PlayerUiState.Error(
                    itemResult.exceptionOrNull()?.message ?: "Failed to load video details"
                )
                return@launch
            }

            val playbackLookupStartedAt = SystemClock.elapsedRealtime()
            val playbackResult = playbackRepository.resolvePlaybackInfo(item)
            val resolverLatencyMs = SystemClock.elapsedRealtime() - playbackLookupStartedAt
            Log.i(PERF_TAG, "playback_info_ms=$resolverLatencyMs")
            val playbackInfo = playbackResult.getOrNull()
            if (playbackInfo == null) {
                _uiState.value = PlayerUiState.Error(
                    playbackResult.exceptionOrNull()?.message ?: "Failed to resolve video stream"
                )
                return@launch
            }

            // Check resume position (Telegram or Jellyfin)
            val provVideoId = item.providerVideoId
            val isTelegram = !provVideoId.isNullOrBlank() && !runCatching { java.util.UUID.fromString(item.id) }.isSuccess

            val durationMs = item.runtimeTicks
                ?.div(10_000L)
                ?.takeIf { it > 0L }
                ?: ((item.runtimeMinutes ?: 0) * 60_000L)

            val resumeMs = if (isTelegram) {
                val saved = telegramProgressStore?.getProgress(provVideoId)
                if (saved != null && !saved.isPlayed) saved.positionMs else 0L
            } else {
                item.playbackPositionTicks / 10_000L
            }

            // Resume only meaningful progress and avoid jumping to the end credits.
            val shouldPromptResume = resumeMs > 15_000L && (durationMs <= 0L || resumeMs < durationMs - 30_000L)

            val pendingResumePositionMs = if (isTelegram && resume == null && shouldPromptResume) resumeMs else null
            val actualStartPositionMs = when {
                resume == true && shouldPromptResume -> resumeMs
                resume == false -> 0L
                !isTelegram && shouldPromptResume -> resumeMs
                else -> 0L
            }

            Log.i(PERF_TAG, "player_ui_ready_ms=${SystemClock.elapsedRealtime() - loadStartedAt}")
            val readyState = PlayerUiState.Ready(
                item = item,
                playbackInfo = playbackInfo,
                startPositionMs = actualStartPositionMs,
                playbackRequestId = playbackRequestId,
                resolverLatencyMs = resolverLatencyMs,
                pendingResumePositionMs = pendingResumePositionMs,
            )

            _uiState.value = readyState

            // Record in watch history
            watchHistoryStore?.recordWatch(item)
        }
    }

    fun playRandomMovie(currentPositionMs: Long = 0L) {
        viewModelScope.launch {
            if (_isLoadingRandom.value) return@launch
            _isLoadingRandom.value = true
            val playbackRequestId = ++playbackRequestCounter

            // Stop current playback reporting session
            playbackReporter.stopSession(currentPositionMs)

            val currentItem = (_uiState.value as? PlayerUiState.Ready)?.item
            val currentId = currentItem?.id
            val libraryId = settingsDataStore.selectedLibraryId.firstOrNull()

            var randomItem = currentItem?.genres?.firstOrNull()?.let { genre ->
                libraryRepository.getRandomItemFiltered(
                    parentId = libraryId,
                    excludeItemId = currentId,
                    genres = listOf(genre),
                ).getOrNull()
            }
            if (randomItem == null) {
                val performerId = currentItem?.people?.firstOrNull()?.id
                    ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
                if (performerId != null) {
                    randomItem = libraryRepository.getRandomItemFiltered(
                        parentId = libraryId,
                        excludeItemId = currentId,
                        personIds = listOf(performerId),
                    ).getOrNull()
                }
            }
            if (randomItem == null) {
                currentItem?.studios?.firstOrNull()?.let { studio ->
                    randomItem = libraryRepository.getRandomItemFiltered(
                        parentId = libraryId,
                        excludeItemId = currentId,
                        studios = listOf(studio),
                    ).getOrNull()
                }
            }
            if (randomItem == null) {
                randomItem = libraryRepository.getRandomItem(
                    parentId = libraryId,
                    excludeItemId = currentId,
                ).getOrNull()
            }

            if (randomItem == null) {
                val cached = channelRepository?.getRandomCachedVideo(excludeId = currentId)
                if (cached != null) {
                    randomItem = TvItem(
                        id = cached.id,
                        title = cached.title.ifBlank { "Channel Video" },
                        overview = cached.description,
                        runtimeTicks = (cached.duration * 10_000_000).toLong(),
                        genres = cached.categories,
                        providerVideoId = cached.providerVideoId.ifBlank {
                            cached.id.removePrefix("telegram:")
                        },
                        fileSizeBytes = cached.fileSizeBytes,
                    )
                }
            }

            if (randomItem == null) {
                _isLoadingRandom.value = false
                _uiState.value = PlayerUiState.Error("No random video available")
                return@launch
            }

            val randomPlaybackLookupStartedAt = SystemClock.elapsedRealtime()
            val playbackResult = playbackRepository.resolvePlaybackInfo(randomItem)
            val randomResolverLatencyMs = SystemClock.elapsedRealtime() - randomPlaybackLookupStartedAt
            val playbackInfo = playbackResult.getOrNull()

            _isLoadingRandom.value = false
            if (playbackInfo == null) {
                _uiState.value = PlayerUiState.Error(
                    playbackResult.exceptionOrNull()?.message ?: "Failed to resolve stream for random video"
                )
                return@launch
            }

            _isPlaybackEnded.value = false
            _uiState.value = PlayerUiState.Ready(
                item = randomItem,
                playbackInfo = playbackInfo,
                startPositionMs = 0L,
                playbackRequestId = playbackRequestId,
                resolverLatencyMs = randomResolverLatencyMs,
            )

            // Record in watch history
            watchHistoryStore?.recordWatch(randomItem)
        }
    }

    fun onPlaybackEnded(finalPositionMs: Long) {
        _isPlaybackEnded.value = true
        saveTelegramProgress(finalPositionMs, isEnded = true)
        playbackReporter.stopSession(finalPositionMs)
    }

    fun toggleFavorite() {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return
        val newFav = !ready.item.isFavorite
        val updatedItem = ready.item.copy(isFavorite = newFav)
        _uiState.value = ready.copy(item = updatedItem)
        if (runCatching { java.util.UUID.fromString(updatedItem.id) }.isSuccess) {
            viewModelScope.launch {
                libraryRepository.toggleFavorite(updatedItem.id, newFav)
            }
        }
    }

    fun onExitPlayer(currentPositionMs: Long) {
        saveTelegramProgress(currentPositionMs, isEnded = false)
        playbackReporter.stopSession(currentPositionMs)
    }

    fun onResumeDialogHandled() {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return
        _uiState.value = ready.copy(pendingResumePositionMs = null)
    }

    private fun saveTelegramProgress(positionMs: Long, isEnded: Boolean) {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return
        val item = ready.item
        val provVideoId = item.providerVideoId ?: return
        if (runCatching { java.util.UUID.fromString(item.id) }.isSuccess) return

        val durationMs = item.runtimeTicks?.div(10_000L) ?: 0L
        viewModelScope.launch {
            if (isEnded || (durationMs > 0L && positionMs >= durationMs * 0.90f)) {
                telegramProgressStore?.markPlayed(
                    providerVideoId = provVideoId,
                    isPlayed = true,
                    title = item.title,
                    thumbnailUrl = item.posterUrl,
                    channelId = item.channelId,
                )
            } else if (positionMs > 15_000L) {
                telegramProgressStore?.saveProgress(
                    providerVideoId = provVideoId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    title = item.title,
                    thumbnailUrl = item.posterUrl,
                    channelId = item.channelId,
                    isPlayed = false,
                )
            }
        }
    }
}
