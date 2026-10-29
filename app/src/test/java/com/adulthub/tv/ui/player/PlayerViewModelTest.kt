package com.adulthub.tv.ui.player

import android.content.Context
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.local.TelegramProgressRecord
import com.adulthub.tv.data.local.TelegramProgressStore
import com.adulthub.tv.data.model.PlaybackInfo
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.repository.ChannelRepository
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.PlaybackRepository
import com.adulthub.tv.data.resolver.VideoDto
import com.adulthub.tv.player.PlaybackReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.PlayMethod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeLibraryRepo = FakeLibraryRepository()
    private val fakePlaybackRepo = FakePlaybackRepository()
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private val sampleItem = TvItem(
        id = "movie-1",
        title = "Test Scene",
        runtimeTicks = 45L * 60 * 10_000_000L,
        playbackPositionTicks = 0L,
    )

    private val resumeItem = TvItem(
        id = "movie-2",
        title = "Resume Scene",
        runtimeTicks = 45L * 60 * 10_000_000L,
        playbackPositionTicks = 600_000 * 10_000L, // 10 minutes in
    )

    private val randomItem = TvItem(
        id = "movie-3",
        title = "Random Scene",
        runtimeTicks = 30L * 60 * 10_000_000L,
        playbackPositionTicks = 0L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsDataStore.selectedLibraryId } returns flowOf("lib-1")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadItem with no resume position starts immediately at 0`() = runTest(testDispatcher) {
        fakeLibraryRepo.itemResult = Result.success(sampleItem)
        val playbackReporter = PlaybackReporter(fakePlaybackRepo, CoroutineScope(testDispatcher))
        val viewModel = PlayerViewModel(
            libraryRepository = fakeLibraryRepo,
            playbackRepository = fakePlaybackRepo,
            settingsDataStore = settingsDataStore,
            playbackReporter = playbackReporter,
            context = context,
        )

        viewModel.loadItem("movie-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PlayerUiState.Ready)
        val ready = state as PlayerUiState.Ready
        assertEquals("movie-1", ready.item.id)
        assertEquals(0L, ready.startPositionMs)
    }

    @Test
    fun `loadItem with significant resume position resumes automatically without a dialog`() = runTest(testDispatcher) {
        fakeLibraryRepo.itemResult = Result.success(resumeItem)
        val playbackReporter = PlaybackReporter(fakePlaybackRepo, CoroutineScope(testDispatcher))
        val viewModel = PlayerViewModel(
            libraryRepository = fakeLibraryRepo,
            playbackRepository = fakePlaybackRepo,
            settingsDataStore = settingsDataStore,
            playbackReporter = playbackReporter,
            context = context,
        )

        viewModel.loadItem("movie-2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PlayerUiState.Ready)
        val ready = state as PlayerUiState.Ready
        assertEquals(600_000L, ready.startPositionMs)
    }

    @Test
    fun `explicit start from beginning ignores saved resume position`() = runTest(testDispatcher) {
        fakeLibraryRepo.itemResult = Result.success(resumeItem)
        val playbackReporter = PlaybackReporter(fakePlaybackRepo, CoroutineScope(testDispatcher))
        val viewModel = PlayerViewModel(
            libraryRepository = fakeLibraryRepo,
            playbackRepository = fakePlaybackRepo,
            settingsDataStore = settingsDataStore,
            playbackReporter = playbackReporter,
            context = context,
        )

        viewModel.loadItem("movie-2", resume = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PlayerUiState.Ready)
        val ready = state as PlayerUiState.Ready
        assertEquals(0L, ready.startPositionMs)
    }

    @Test
    fun `toggleFavorite keeps the active playback request`() = runTest(testDispatcher) {
        fakeLibraryRepo.itemResult = Result.success(sampleItem)
        val playbackReporter = PlaybackReporter(fakePlaybackRepo, CoroutineScope(testDispatcher))
        val viewModel = PlayerViewModel(
            libraryRepository = fakeLibraryRepo,
            playbackRepository = fakePlaybackRepo,
            settingsDataStore = settingsDataStore,
            playbackReporter = playbackReporter,
            context = context,
        )

        viewModel.loadItem("movie-1")
        advanceUntilIdle()
        val requestIdBeforeToggle = (viewModel.uiState.value as PlayerUiState.Ready).playbackRequestId

        viewModel.toggleFavorite()
        advanceUntilIdle()

        val ready = viewModel.uiState.value as PlayerUiState.Ready
        assertTrue(ready.item.isFavorite)
        assertEquals(requestIdBeforeToggle, ready.playbackRequestId)
    }

    @Test
    fun `loadItem failure emits Error state`() = runTest(testDispatcher) {
        fakeLibraryRepo.itemResult = Result.failure(RuntimeException("Item not found"))
        val playbackReporter = PlaybackReporter(fakePlaybackRepo, CoroutineScope(testDispatcher))
        val viewModel = PlayerViewModel(
            libraryRepository = fakeLibraryRepo,
            playbackRepository = fakePlaybackRepo,
            settingsDataStore = settingsDataStore,
            playbackReporter = playbackReporter,
            context = context,
        )

        viewModel.loadItem("unknown-id")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PlayerUiState.Error)
        assertEquals("Item not found", (state as PlayerUiState.Error).message)
    }

    @Test
    fun `playRandomMovie switches to another video`() = runTest(testDispatcher) {
        fakeLibraryRepo.itemResult = Result.success(sampleItem)
        fakeLibraryRepo.randomItemResult = Result.success(randomItem)

        val playbackReporter = PlaybackReporter(fakePlaybackRepo, CoroutineScope(testDispatcher))
        val viewModel = PlayerViewModel(
            libraryRepository = fakeLibraryRepo,
            playbackRepository = fakePlaybackRepo,
            settingsDataStore = settingsDataStore,
            playbackReporter = playbackReporter,
            context = context,
        )

        viewModel.loadItem("movie-1")
        advanceUntilIdle()

        viewModel.playRandomMovie(currentPositionMs = 30_000L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PlayerUiState.Ready)
        val ready = state as PlayerUiState.Ready
        assertEquals("movie-3", ready.item.id)
        assertEquals("Random Scene", ready.item.title)
    }

    @Test
    fun `onPlaybackEnded sets isPlaybackEnded flag`() = runTest(testDispatcher) {
        fakeLibraryRepo.itemResult = Result.success(sampleItem)
        val playbackReporter = PlaybackReporter(fakePlaybackRepo, CoroutineScope(testDispatcher))
        val viewModel = PlayerViewModel(
            libraryRepository = fakeLibraryRepo,
            playbackRepository = fakePlaybackRepo,
            settingsDataStore = settingsDataStore,
            playbackReporter = playbackReporter,
            context = context,
        )

        viewModel.loadItem("movie-1")
        advanceUntilIdle()

        viewModel.onPlaybackEnded(45_000L)
        advanceUntilIdle()

        assertTrue(viewModel.isPlaybackEnded.value)
    }

    @Test
    fun `loadItem with channel video loads from channelRepository and plays`() = runTest(testDispatcher) {
        fakeLibraryRepo.itemResult = Result.failure(RuntimeException("Not in Jellyfin"))
        val channelRepo = ChannelRepository(mockk(relaxed = true))
        val testVideo = VideoDto(
            id = "telegram:12345",
            providerVideoId = "-1001188284602:100:12345",
            title = "Test Channel Video",
            duration = 600.0,
        )
        channelRepo.cacheVideo(testVideo)

        val playbackReporter = PlaybackReporter(fakePlaybackRepo, CoroutineScope(testDispatcher))
        val viewModel = PlayerViewModel(
            libraryRepository = fakeLibraryRepo,
            playbackRepository = fakePlaybackRepo,
            settingsDataStore = settingsDataStore,
            playbackReporter = playbackReporter,
            channelRepository = channelRepo,
            context = context,
        )

        viewModel.loadItem("-1001188284602:100:12345")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PlayerUiState.Ready)
        val ready = state as PlayerUiState.Ready
        assertEquals("Test Channel Video", ready.item.title)
        assertEquals("-1001188284602:100:12345", ready.item.providerVideoId)
    }

    @Test
    fun `loadItem for telegram video with saved progress prompts resume dialog`() = runTest(testDispatcher) {
        fakeLibraryRepo.itemResult = Result.failure(RuntimeException("Not in Jellyfin"))
        val tgStore = mockk<TelegramProgressStore>(relaxed = true)
        coEvery { tgStore.getProgress("-1001188284602:100:12345") } returns TelegramProgressRecord(
            providerVideoId = "-1001188284602:100:12345",
            positionMs = 45_000L,
            durationMs = 120_000L,
            updatedAt = System.currentTimeMillis(),
            title = "Test Channel Video",
        )
        val channelRepo = ChannelRepository(mockk(relaxed = true))
        val testVideo = VideoDto(
            id = "-1001188284602:100:12345",
            providerVideoId = "-1001188284602:100:12345",
            title = "Test Channel Video",
            duration = 120.0,
        )
        channelRepo.cacheVideo(testVideo)
        val playbackReporter = PlaybackReporter(fakePlaybackRepo, CoroutineScope(testDispatcher))
        val viewModel = PlayerViewModel(
            libraryRepository = fakeLibraryRepo,
            playbackRepository = fakePlaybackRepo,
            settingsDataStore = settingsDataStore,
            playbackReporter = playbackReporter,
            channelRepository = channelRepo,
            telegramProgressStore = tgStore,
            context = context,
        )

        viewModel.loadItem("-1001188284602:100:12345")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PlayerUiState.Ready)
        val ready = state as PlayerUiState.Ready
        assertEquals(0L, ready.startPositionMs)
        assertEquals(45_000L, ready.pendingResumePositionMs)

        viewModel.onResumeDialogHandled()
        val updated = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals(null, updated.pendingResumePositionMs)
    }

    @Test
    fun `switching to a random video saves current telegram progress first`() = runTest(testDispatcher) {
        val videoId = "-1001188284602:100:12345"
        val telegramStore = mockk<TelegramProgressStore>(relaxed = true)
        fakeLibraryRepo.itemResult = Result.failure(IllegalArgumentException("Not a Jellyfin item"))
        val channelRepo = ChannelRepository(mockk(relaxed = true)).apply {
            cacheVideo(
                VideoDto(
                    id = videoId,
                    providerVideoId = videoId,
                    title = "Current channel video",
                    duration = 120.0,
                )
            )
        }
        val viewModel = PlayerViewModel(
            libraryRepository = fakeLibraryRepo,
            playbackRepository = fakePlaybackRepo,
            settingsDataStore = settingsDataStore,
            playbackReporter = PlaybackReporter(fakePlaybackRepo, CoroutineScope(testDispatcher)),
            channelRepository = channelRepo,
            telegramProgressStore = telegramStore,
            context = context,
        )

        viewModel.loadItem(videoId)
        advanceUntilIdle()
        viewModel.playRandomMovie(currentPositionMs = 60_000L)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            telegramStore.saveProgress(
                providerVideoId = videoId,
                positionMs = 60_000L,
                durationMs = 120_000L,
                title = "Current channel video",
                thumbnailUrl = null,
                channelId = null,
                isPlayed = false,
            )
        }
    }
}

private class FakeLibraryRepository : LibraryRepository(mockk(relaxed = true), mockk(relaxed = true)) {
    var itemResult: Result<TvItem> = Result.success(TvItem(id = "default", title = "Default"))
    var randomItemResult: Result<TvItem?> = Result.success(TvItem(id = "random", title = "Random"))

    override suspend fun getItemDetails(itemId: String): Result<TvItem> {
        return itemResult
    }

    override suspend fun getRandomItem(parentId: String?, excludeItemId: String?): Result<TvItem?> {
        return randomItemResult
    }
}

private class FakePlaybackRepository : PlaybackRepository(mockk(relaxed = true), mockk(relaxed = true)) {
    var playbackInfoResult: Result<PlaybackInfo>? = null
    var lastReportedStartTicks: Long? = null
    var lastReportedProgressTicks: Long? = null
    var lastReportedStoppedTicks: Long? = null

    override suspend fun resolvePlaybackInfo(item: TvItem, variantId: String?): Result<PlaybackInfo> {
        return playbackInfoResult ?: Result.success(
            PlaybackInfo(
                itemId = item.id,
                streamUrl = "https://resolver.example.com/admin/play/stream.mp4",
                playMethod = PlayMethod.DIRECT_PLAY,
                title = item.title,
            )
        )
    }

    override suspend fun reportPlaybackStart(
        itemId: String,
        mediaSourceId: String?,
        positionTicks: Long,
        playSessionId: String?,
        canSeek: Boolean,
        playMethod: PlayMethod,
    ): Result<Unit> {
        lastReportedStartTicks = positionTicks
        return Result.success(Unit)
    }

    override suspend fun reportPlaybackProgress(
        itemId: String,
        mediaSourceId: String?,
        positionTicks: Long,
        playSessionId: String?,
        isPaused: Boolean,
        canSeek: Boolean,
        playMethod: PlayMethod,
    ): Result<Unit> {
        lastReportedProgressTicks = positionTicks
        return Result.success(Unit)
    }

    override suspend fun reportPlaybackStopped(
        itemId: String,
        mediaSourceId: String?,
        positionTicks: Long,
        playSessionId: String?,
    ): Result<Unit> {
        lastReportedStoppedTicks = positionTicks
        return Result.success(Unit)
    }
}
