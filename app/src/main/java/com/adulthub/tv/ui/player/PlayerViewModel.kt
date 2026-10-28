package com.adulthub.tv.ui.player

import android.os.SystemClock
import com.adulthub.tv.diagnostics.AppLog as Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adulthub.tv.data.local.AccountScope
import com.adulthub.tv.data.local.SessionOwner
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.local.TelegramProgressStore
import com.adulthub.tv.data.local.WatchHistoryStore
import com.adulthub.tv.data.model.PlaybackInfo
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.repository.ChannelRepository
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.PlaybackRepository
import com.adulthub.tv.data.resolver.ORIGINAL_VARIANT_ID
import com.adulthub.tv.data.resolver.VideoVariant
import com.adulthub.tv.data.resolver.VideoVariantsResult
import com.adulthub.tv.player.PlaybackReporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

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
    internal val channelRepository: ChannelRepository? = null,
    internal val telegramProgressStore: TelegramProgressStore? = null,
    internal val watchHistoryStore: WatchHistoryStore? = null,
    private val persistenceScope: CoroutineScope? = null,
    private val accountScope: AccountScope? = null,
) : ViewModel() {

    companion object {
        private const val PERF_TAG = "PlaybackPerf"
        private const val MAX_PROCESSING_POLLS = 2
        private const val PROCESSING_POLL_INTERVAL_MS = 20_000L
    }

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _quality = MutableStateFlow(PlayerQualityState())
    val quality: StateFlow<PlayerQualityState> = _quality.asStateFlow()

    private val _qualityMessage = MutableStateFlow<String?>(null)
    val qualityMessage: StateFlow<String?> = _qualityMessage.asStateFlow()

    private var qualityVariants: List<VideoVariant> = emptyList()
    private var selectedVariantId: String? = null
    private var qualityFetchJob: Job? = null

    /** The playback request the accepted quality switch belongs to, or null when none is running. */
    private var qualitySwitchRequestId: Long? = null

    private val _isPlaybackEnded = MutableStateFlow(false)
    val isPlaybackEnded: StateFlow<Boolean> = _isPlaybackEnded.asStateFlow()

    private val _isLoadingRandom = MutableStateFlow(false)
    val isLoadingRandom: StateFlow<Boolean> = _isLoadingRandom.asStateFlow()

    private var playbackRequestCounter = 0L
    private var loadItemJob: Job? = null
    private var lastPlaybackPositionMs: Long = 0L

    /**
     * The reporting session this view model owns.
     *
     * The id is known as soon as the session is created, so every cleanup path can
     * pass it. A view model that is cleared after a newer session started cannot
     * stop that newer session.
     */
    private val _reportingSessionId = MutableStateFlow<Long?>(null)
    val reportingSessionId: StateFlow<Long?> = _reportingSessionId.asStateFlow()

    /**
     * The owner of the current playback.
     *
     * The account and the session are captured when the playback starts, so a late
     * write stays with the account the playback belongs to.
     */
    private var playbackOwner: SessionOwner? = null

    /** The playback whose final write is running. */
    private var finalizingPlaybackId: Long? = null

    /** The playback whose final write finished. */
    private var finalizedPlaybackId: Long? = null

    /** True when the final write of the current playback finished. */
    private val _finalizationCompleted = MutableStateFlow(false)
    val finalizationCompleted: StateFlow<Boolean> = _finalizationCompleted.asStateFlow()

    fun loadItem(itemId: String, resume: Boolean? = null) {
        val playbackRequestId = ++playbackRequestCounter
        loadItemJob?.cancel()
        _isLoadingRandom.value = false
        resetQualityState()
        loadItemJob = viewModelScope.launch {
            val loadStartedAt = SystemClock.elapsedRealtime()
            _uiState.value = PlayerUiState.Loading
            _isPlaybackEnded.value = false
            // The owner of the playback is captured before the first write. A late
            // write of this playback stays with the account it belongs to.
            playbackOwner = accountScope?.currentOwner()
            // The completion of the final write belongs to one playback, so a new
            // playback starts without the completion of the previous one.
            _finalizationCompleted.value = false

            val itemLookupStartedAt = SystemClock.elapsedRealtime()
            val itemResult = libraryRepository.getItemDetails(itemId)
            Log.i(PERF_TAG, "item_lookup_ms=${SystemClock.elapsedRealtime() - itemLookupStartedAt}")
            if (playbackRequestId != playbackRequestCounter) return@launch
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
            if (playbackRequestId != playbackRequestCounter) return@launch
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
            if (playbackRequestId != playbackRequestCounter) return@launch

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

            startQualityFetch(playbackInfo, playbackRequestId)

            // Record in watch history
            if (playbackRequestId == playbackRequestCounter) {
                recordWatchHistory(item)
            }
        }
    }

    fun playRandomMovie(currentPositionMs: Long = 0L, reportingSessionId: Long? = null) {
        if (_isLoadingRandom.value) return
        val playbackRequestId = ++playbackRequestCounter
        loadItemJob?.cancel()
        _isLoadingRandom.value = true
        _finalizationCompleted.value = false
        resetQualityState()
        viewModelScope.launch {
            val currentItem = (_uiState.value as? PlayerUiState.Ready)?.item
            if (currentItem != null) persistTelegramProgress(currentItem, currentPositionMs, isEnded = false)
            // Stop the reporting session of this playback. The session this view
            // model knows is preferred, so a caller without an id cannot stop the
            // session of another player.
            val stoppedSessionId = reportingSessionId ?: _reportingSessionId.value
            playbackReporter.stopSession(currentPositionMs, stoppedSessionId)
            // The session is gone, so nothing may report into it any more. A session
            // that the next playback starts replaces this value.
            if (stoppedSessionId != null && _reportingSessionId.value == stoppedSessionId) {
                _reportingSessionId.value = null
            }

            val currentId = currentItem?.id
            val libraryId = settingsDataStore.selectedLibraryId.firstOrNull()
            if (playbackRequestId != playbackRequestCounter) return@launch
            // The random playback belongs to the account that is signed in now, not
            // to the account of the video that is being replaced.
            playbackOwner = accountScope?.currentOwner()

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
                if (playbackRequestId != playbackRequestCounter) return@launch
                _isLoadingRandom.value = false
                _uiState.value = PlayerUiState.Error("No random video available")
                return@launch
            }

            val randomPlaybackLookupStartedAt = SystemClock.elapsedRealtime()
            val playbackResult = playbackRepository.resolvePlaybackInfo(randomItem)
            val randomResolverLatencyMs = SystemClock.elapsedRealtime() - randomPlaybackLookupStartedAt
            if (playbackRequestId != playbackRequestCounter) return@launch
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

            startQualityFetch(playbackInfo, playbackRequestId)

            // Record in watch history
            recordWatchHistory(randomItem)
        }
    }

    /**
     * Creates the reporting session of the current playback.
     *
     * The view model owns the session, so it knows its id from the first moment
     * and every cleanup path can stop exactly this session.
     */
    fun startReportingSession(
        itemId: String,
        mediaSourceId: String?,
        playSessionId: String?,
        playMethod: org.jellyfin.sdk.model.api.PlayMethod,
        getCurrentPositionMs: () -> Long,
        getIsPaused: () -> Boolean,
    ): Long {
        val sessionId = playbackReporter.startSession(
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            playSessionId = playSessionId,
            playMethod = playMethod,
            getCurrentPositionMs = getCurrentPositionMs,
            getIsPaused = getIsPaused,
        )
        _reportingSessionId.value = sessionId
        return sessionId
    }

    fun onPlaybackEnded(finalPositionMs: Long, reportingSessionId: Long? = null) {
        _isPlaybackEnded.value = true
        reportingSessionId?.let { _reportingSessionId.value = it }
        finalizePlayback(finalPositionMs, isEnded = true)
        stopOwnedSession(finalPositionMs)
    }

    /**
     * Resolves a fresh stream address for the current item.
     *
     * A stream address can expire while a video plays. A retry resolves the
     * playback data again, so an expired address is not used a second time. The
     * position is kept by the caller.
     */
    suspend fun resolveFreshStreamUrl(itemId: String): String? {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return null
        if (ready.item.id != itemId) return null
        val playbackInfo = playbackRepository
            .resolvePlaybackInfo(ready.item, selectedVariantId)
            .getOrNull() ?: return null
        return playbackInfo.streamUrl.takeIf { it.isNotBlank() }
    }

    /**
     * Opens a quality switch.
     *
     * Returns false while another switch is still running, so overlapping switches
     * are impossible from the moment the request is accepted. The accepted switch is
     * bound to the current playback request: a new movie makes it stale, and [confirmQualitySwitch]
     * and [abortQualitySwitch] then do nothing instead of rewriting the new state.
     */
    fun beginQualitySwitch(variantId: String): Boolean {
        val state = _quality.value
        if (state.isSwitching || state.isFetching) return false
        if (qualityVariants.none { it.id == variantId }) return false
        if (variantId == (selectedVariantId ?: ORIGINAL_VARIANT_ID)) return false
        qualitySwitchRequestId = playbackRequestCounter
        _quality.value = state.copy(isSwitching = true, qualityUnavailableMessage = null)
        return true
    }

    /**
     * Prepares the address of another quality without touching the running playback.
     *
     * The current source keeps playing while this resolves. The caller only swaps
     * the source after a URL came back. A switch that a new playback already superseded
     * resolves nothing, so its address can never reach the player.
     */
    suspend fun resolveQualityStreamUrl(variantId: String): PlaybackInfo? {
        if (qualitySwitchRequestId != playbackRequestCounter) return null
        val ready = _uiState.value as? PlayerUiState.Ready ?: return null
        return playbackRepository.resolvePlaybackInfo(ready.item, variantId).getOrNull()
    }

    fun confirmQualitySwitch(variantId: String) {
        if (qualitySwitchRequestId != playbackRequestCounter) return
        qualitySwitchRequestId = null
        selectedVariantId = variantId.takeIf { it != ORIGINAL_VARIANT_ID }
        _quality.value = _quality.value.copy(
            isSwitching = false,
            options = buildQualityOptions(qualityVariants, selectedVariantId),
        )
    }

    /** Ends a switch that never reached the player; the current source keeps running. */
    fun abortQualitySwitch(message: String) {
        if (qualitySwitchRequestId != playbackRequestCounter) return
        qualitySwitchRequestId = null
        _quality.value = _quality.value.copy(
            isSwitching = false,
            qualityUnavailableMessage = message,
        )
        _qualityMessage.value = message
    }

    fun onQualityMessageShown() {
        _qualityMessage.value = null
    }

    /** Retries the variants request after a failure. A missing feature stays hidden. */
    fun retryQualityFetch() {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return
        val ref = ready.playbackInfo.resolverVideoId ?: return
        startQualityFetch(ready.playbackInfo, ready.playbackRequestId)
    }

    /** The variant the player should report in diagnostics, when it is known. */
    fun activeVariant(): VideoVariant? =
        qualityVariants.firstOrNull { it.id == (selectedVariantId ?: ORIGINAL_VARIANT_ID) }

    fun variantFor(variantId: String): VideoVariant? =
        qualityVariants.firstOrNull { it.id == variantId }

    private fun resetQualityState() {
        qualityFetchJob?.cancel()
        qualityFetchJob = null
        qualityVariants = emptyList()
        selectedVariantId = null
        qualitySwitchRequestId = null
        _quality.value = PlayerQualityState()
        _qualityMessage.value = null
    }

    private fun startQualityFetch(playbackInfo: PlaybackInfo, playbackRequestId: Long) {
        qualityFetchJob?.cancel()
        val ref = playbackInfo.resolverVideoId
        if (ref.isNullOrBlank()) {
            _quality.value = PlayerQualityState()
            return
        }
        _quality.value = PlayerQualityState(isFetching = true)
        qualityFetchJob = viewModelScope.launch {
            // A video that is still being transcoded gains its alternatives during playback,
            // so the answer is asked again a bounded number of times instead of staying hidden
            // until the viewer leaves the player.
            var processingPolls = 0
            while (true) {
                val result = playbackRepository.fetchVideoVariants(ref)
                if (playbackRequestId != playbackRequestCounter) return@launch
                if (result == VideoVariantsResult.Processing && processingPolls < MAX_PROCESSING_POLLS) {
                    processingPolls++
                    delay(PROCESSING_POLL_INTERVAL_MS)
                    if (playbackRequestId != playbackRequestCounter) return@launch
                    continue
                }
                qualityVariants = variantsFor(result)
                _quality.value = when (result) {
                    is VideoVariantsResult.Failed -> PlayerQualityState(
                        qualityUnavailableMessage = "Could not load available qualities",
                    )
                    else -> PlayerQualityState(options = qualityOptionsFor(result, selectedVariantId))
                }
                return@launch
            }
        }
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

    fun onExitPlayer(currentPositionMs: Long, reportingSessionId: Long? = null) {
        reportingSessionId?.let { _reportingSessionId.value = it }
        finalizePlayback(currentPositionMs, isEnded = false)
        stopOwnedSession(currentPositionMs)
    }

    /**
     * Stops the reporting session of this view model.
     *
     * Without a known session id no session is stopped. A cleared view model must
     * not stop the session of the player that runs now.
     */
    private fun stopOwnedSession(positionMs: Long) {
        val sessionId = _reportingSessionId.value ?: return
        playbackReporter.stopSession(positionMs, sessionId)
    }

    /**
     * Writes the final progress of the current playback once.
     *
     * The exit handler and the composition disposal both report the end of a
     * playback, so the write is guarded per playback. The write of a playback does
     * not block the finalisation of the next one, and the completion is only
     * reported after the write finished.
     */
    private fun finalizePlayback(positionMs: Long, isEnded: Boolean) {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return
        val playbackId = ready.playbackRequestId
        if (finalizingPlaybackId == playbackId || finalizedPlaybackId == playbackId) return
        finalizingPlaybackId = playbackId
        lastPlaybackPositionMs = positionMs
        (persistenceScope ?: viewModelScope).launch {
            val stored = runCatching {
                persistTelegramProgress(ready.item, positionMs, isEnded)
            }
            if (stored.isFailure) {
                Log.w(PERF_TAG, "The final progress write failed")
            }
            finalizedPlaybackId = playbackId
            finalizingPlaybackId = null
            // The completion belongs to this playback. A playback that started in the
            // meantime does not inherit the completion of the previous one.
            if ((_uiState.value as? PlayerUiState.Ready)?.playbackRequestId == playbackId) {
                _finalizationCompleted.value = stored.isSuccess
            }
        }
    }

    /**
     * Keeps the last known playback position so that it can still be reported and persisted
     * when the view model is cleared without a regular exit from the player.
     */
    fun updatePlaybackPosition(positionMs: Long) {
        if (positionMs >= 0L) lastPlaybackPositionMs = positionMs
    }

    override fun onCleared() {
        super.onCleared()
        clearOwnedSession()
    }

    /**
     * Releases the session of this view model.
     *
     * The view model clears itself through this path, so the cleanup of a view
     * model that is discarded is covered by the same code.
     */
    internal fun clearOwnedSession() {
        val positionMs = lastPlaybackPositionMs
        val ready = _uiState.value as? PlayerUiState.Ready
        val playbackId = ready?.playbackRequestId
        val shouldPersist = playbackId != null &&
            finalizingPlaybackId != playbackId &&
            finalizedPlaybackId != playbackId
        if (shouldPersist) {
            finalizingPlaybackId = playbackId
        }
        // The view model scope is cancelled right after this callback, so the persistence work
        // runs in the application scope whenever one is available.
        val scope = persistenceScope
        if (scope != null) {
            scope.launch {
                // The session id of this view model is passed, so a cleared view model
                // cannot stop a session that started after it.
                stopOwnedSession(positionMs)
                if (ready != null && shouldPersist) {
                    val stored = runCatching {
                        persistTelegramProgress(ready.item, positionMs, isEnded = false)
                    }
                    if (stored.isFailure) {
                        Log.w(PERF_TAG, "The final progress write failed")
                    }
                    finalizedPlaybackId = playbackId
                    finalizingPlaybackId = null
                }
            }
        } else {
            stopOwnedSession(positionMs)
        }
    }

    fun onResumeDialogHandled() {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return
        _uiState.value = ready.copy(pendingResumePositionMs = null)
    }

    suspend fun checkpointTelegramProgress(positionMs: Long) {
        val ready = _uiState.value as? PlayerUiState.Ready ?: return
        persistTelegramProgress(ready.item, positionMs, isEnded = false)
    }

    /**
     * Writes the progress of a playback.
     *
     * The write uses the account the playback belongs to, so a late write of a
     * finished playback does not land in the account that signed in meanwhile.
     */
    private suspend fun persistTelegramProgress(item: TvItem, positionMs: Long, isEnded: Boolean) {
        val provVideoId = item.providerVideoId ?: return
        if (runCatching { java.util.UUID.fromString(item.id) }.isSuccess) return

        val accountKey = playbackOwner?.accountKey
        val durationMs = item.runtimeTicks?.div(10_000L) ?: 0L
        if (isEnded || (durationMs > 0L && positionMs >= durationMs * 0.90f)) {
            telegramProgressStore?.markPlayed(
                providerVideoId = provVideoId,
                isPlayed = true,
                title = item.title,
                thumbnailUrl = item.posterUrl,
                channelId = item.channelId,
                accountKey = accountKey,
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
                accountKey = accountKey,
            )
        }
    }

    /**
     * Records a playback in the watch history of the account that owns it.
     *
     * A failed write is logged; it must not take the playback down.
     */
    private suspend fun recordWatchHistory(item: TvItem) {
        val store = watchHistoryStore ?: return
        val stored = runCatching {
            store.recordWatch(item, accountKey = playbackOwner?.accountKey)
        }
        if (stored.isFailure) {
            Log.w(PERF_TAG, "The watch history write failed")
        }
    }
}
