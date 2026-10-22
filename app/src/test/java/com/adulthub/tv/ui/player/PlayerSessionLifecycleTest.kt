package com.adulthub.tv.ui.player

import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.local.TelegramProgressRecord
import com.adulthub.tv.data.local.TelegramProgressStore
import com.adulthub.tv.data.model.PlaybackInfo
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.PlaybackRepository
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.PlayMethod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The reporting session belongs to the view model that created it.
 *
 * A cleared view model must not stop the session of the player that runs now, and
 * the final write of a playback must not block the final write of the next one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSessionLifecycleTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeLibraryRepo = FakeLifecycleLibraryRepository()
    private val fakePlaybackRepo = FakeLifecyclePlaybackRepository()
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val progressStore = RecordingProgressStore()

    private val firstItem = TvItem(
        id = "movie-1",
        title = "First Scene",
        providerVideoId = "movie-1",
        runtimeTicks = 45L * 60 * 10_000_000L,
    )
    private val secondItem = TvItem(
        id = "movie-2",
        title = "Second Scene",
        providerVideoId = "movie-2",
        runtimeTicks = 45L * 60 * 10_000_000L,
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

    private fun newViewModel(reporter: PlaybackReporter, scope: CoroutineScope) = PlayerViewModel(
        libraryRepository = fakeLibraryRepo,
        playbackRepository = fakePlaybackRepo,
        settingsDataStore = settingsDataStore,
        playbackReporter = reporter,
        telegramProgressStore = progressStore,
        persistenceScope = scope,
    )

    @Test
    fun `the view model knows its session id as soon as the session exists`() = runTest(testDispatcher) {
        fakeLibraryRepo.itemResult = Result.success(firstItem)
        val reporter = PlaybackReporter(fakePlaybackRepo, backgroundScope)
        val viewModel = newViewModel(reporter, backgroundScope)

        assertNull(viewModel.reportingSessionId.value)

        val sessionId = viewModel.startReportingSession(
            itemId = "movie-1",
            mediaSourceId = null,
            playSessionId = null,
            playMethod = PlayMethod.DIRECT_PLAY,
            getCurrentPositionMs = { 0L },
            getIsPaused = { true },
        )

        assertTrue(sessionId > 0L)
        assertEquals(sessionId, viewModel.reportingSessionId.value)
    }

    @Test
    fun `a cleared view model does not stop the session of the player that runs now`() = runTest(testDispatcher) {
        fakeLibraryRepo.itemResult = Result.success(firstItem)
        val reporter = PlaybackReporter(fakePlaybackRepo, backgroundScope)
        val oldViewModel = newViewModel(reporter, backgroundScope)
        val oldSession = oldViewModel.startReportingSession(
            itemId = "movie-1",
            mediaSourceId = null,
            playSessionId = null,
            playMethod = PlayMethod.DIRECT_PLAY,
            getCurrentPositionMs = { 0L },
            getIsPaused = { true },
        )

        // A second player starts its own session.
        val newViewModel = newViewModel(reporter, backgroundScope)
        val newSession = newViewModel.startReportingSession(
            itemId = "movie-2",
            mediaSourceId = null,
            playSessionId = null,
            playMethod = PlayMethod.DIRECT_PLAY,
            getCurrentPositionMs = { 0L },
            getIsPaused = { true },
        )
        assertTrue(newSession > oldSession)

        // The old view model is cleared.
        oldViewModel.clearOwnedSession()
        advanceTimeBy(1_000L)
        runCurrent()

        // The session of the running player is still alive.
        reporter.reportStart(0L, canSeek = true, sessionId = newSession)
        reporter.reportProgressNow(1_000L, false, newSession)
        advanceTimeBy(1_000L)
        runCurrent()
        assertNotNull(fakePlaybackRepo.lastReportedProgressTicks)
    }

    @Test
    fun `the final write of the next film is not blocked by the one of the finished film`() = runTest(testDispatcher) {
        fakeLibraryRepo.itemResult = Result.success(firstItem)
        fakeLibraryRepo.randomItemResult = Result.success(secondItem)
        val reporter = PlaybackReporter(fakePlaybackRepo, backgroundScope)
        val viewModel = newViewModel(reporter, backgroundScope)

        viewModel.loadItem("movie-1")
        advanceTimeBy(1_000L)
        runCurrent()
        viewModel.startReportingSession(
            itemId = "movie-1",
            mediaSourceId = null,
            playSessionId = null,
            playMethod = PlayMethod.DIRECT_PLAY,
            getCurrentPositionMs = { 0L },
            getIsPaused = { true },
        )

        // The first film ends.
        viewModel.onPlaybackEnded(20_000_000L, viewModel.reportingSessionId.value)
        advanceTimeBy(1_000L)
        runCurrent()

        // The next film starts and ends.
        viewModel.playRandomMovie(currentPositionMs = 0L, reportingSessionId = viewModel.reportingSessionId.value)
        advanceTimeBy(1_000L)
        runCurrent()
        val ready = viewModel.uiState.value as PlayerUiState.Ready
        assertEquals("movie-2", ready.item.id)

        viewModel.onExitPlayer(30_000_000L, viewModel.reportingSessionId.value)
        advanceTimeBy(1_000L)
        runCurrent()

        // The write of the second film happened.
        assertTrue(progressStore.savedVideoIds.contains("movie-2"))
        assertTrue(viewModel.finalizationCompleted.value)
    }

    @Test
    fun `a failed final write is reported and does not take the player down`() = runTest(testDispatcher) {
        fakeLibraryRepo.itemResult = Result.success(firstItem)
        progressStore.failure = com.adulthub.tv.data.local.PreferenceWriteException(
            "The preference could not be stored",
            java.io.IOException("disk full"),
        )
        val reporter = PlaybackReporter(fakePlaybackRepo, backgroundScope)
        val viewModel = newViewModel(reporter, backgroundScope)

        viewModel.loadItem("movie-1")
        advanceTimeBy(1_000L)
        runCurrent()
        viewModel.startReportingSession(
            itemId = "movie-1",
            mediaSourceId = null,
            playSessionId = null,
            playMethod = PlayMethod.DIRECT_PLAY,
            getCurrentPositionMs = { 0L },
            getIsPaused = { true },
        )

        viewModel.onExitPlayer(20_000_000L, viewModel.reportingSessionId.value)
        advanceTimeBy(1_000L)
        runCurrent()

        assertFalse(viewModel.finalizationCompleted.value)
    }

    @Test
    fun `the final write runs once per playback`() = runTest(testDispatcher) {
        fakeLibraryRepo.itemResult = Result.success(firstItem)
        val reporter = PlaybackReporter(fakePlaybackRepo, backgroundScope)
        val viewModel = newViewModel(reporter, backgroundScope)

        viewModel.loadItem("movie-1")
        advanceTimeBy(1_000L)
        runCurrent()
        viewModel.startReportingSession(
            itemId = "movie-1",
            mediaSourceId = null,
            playSessionId = null,
            playMethod = PlayMethod.DIRECT_PLAY,
            getCurrentPositionMs = { 0L },
            getIsPaused = { true },
        )

        viewModel.onExitPlayer(20_000_000L, viewModel.reportingSessionId.value)
        advanceTimeBy(1_000L)
        runCurrent()
        val writesAfterFirstExit = progressStore.saveCalls

        // The exit handler and the disposal both report the end of the playback.
        viewModel.clearOwnedSession()
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(writesAfterFirstExit, progressStore.saveCalls)
    }

    @Test
    fun `a retry resolves the playback data again`() = runTest(testDispatcher) {
        fakeLibraryRepo.itemResult = Result.success(firstItem)
        fakePlaybackRepo.playbackInfoResult = Result.success(
            PlaybackInfo(
                itemId = "movie-1",
                streamUrl = "https://resolver.example.com/fresh.mp4",
                playMethod = PlayMethod.DIRECT_PLAY,
                title = "First Scene",
            ),
        )
        val reporter = PlaybackReporter(fakePlaybackRepo, backgroundScope)
        val viewModel = newViewModel(reporter, backgroundScope)

        viewModel.loadItem("movie-1")
        advanceTimeBy(1_000L)
        runCurrent()

        val refreshed = viewModel.resolveFreshStreamUrl("movie-1")

        assertEquals("https://resolver.example.com/fresh.mp4", refreshed)
        // A different item has no stream address of this playback.
        assertNull(viewModel.resolveFreshStreamUrl("movie-9"))
    }
}

private class FakeLifecycleLibraryRepository : LibraryRepository(mockk(relaxed = true), mockk(relaxed = true)) {
    var itemResult: Result<TvItem> = Result.success(TvItem(id = "default", title = "Default"))
    var randomItemResult: Result<TvItem?> = Result.success(TvItem(id = "random", title = "Random"))

    override suspend fun getItemDetails(itemId: String): Result<TvItem> = itemResult

    override suspend fun getRandomItem(parentId: String?, excludeItemId: String?): Result<TvItem?> = randomItemResult

    override suspend fun getRandomItemFiltered(
        parentId: String?,
        excludeItemId: String?,
        filters: Set<org.jellyfin.sdk.model.api.ItemFilter>,
        genreIds: List<java.util.UUID>,
        studioIds: List<java.util.UUID>,
        personIds: List<java.util.UUID>,
        genres: List<String>,
        studios: List<String>,
    ): Result<TvItem?> = randomItemResult
}

private class FakeLifecyclePlaybackRepository : PlaybackRepository(mockk(relaxed = true), mockk(relaxed = true)) {
    var playbackInfoResult: Result<PlaybackInfo>? = null
    var lastReportedProgressTicks: Long? = null

    // The base implementations report on Dispatchers.IO. A test that waits for a
    // report on virtual time would depend on a real thread, so all reports of this
    // fake return on the test dispatcher.
    override suspend fun reportPlaybackStart(
        itemId: String,
        mediaSourceId: String?,
        positionTicks: Long,
        playSessionId: String?,
        canSeek: Boolean,
        playMethod: PlayMethod,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun reportPlaybackStopped(
        itemId: String,
        mediaSourceId: String?,
        positionTicks: Long,
        playSessionId: String?,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun resolvePlaybackInfo(item: TvItem): Result<PlaybackInfo> =
        playbackInfoResult ?: Result.success(
            PlaybackInfo(
                itemId = item.id,
                streamUrl = "https://resolver.example.com/admin/play/stream.mp4",
                playMethod = PlayMethod.DIRECT_PLAY,
                title = item.title,
            ),
        )

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
}

/** Records the progress writes of a playback. */
private class RecordingProgressStore : TelegramProgressStore(
    context = mockk(relaxed = true),
    accountKeyProvider = com.adulthub.tv.data.local.AccountKeyProvider.ANONYMOUS,
    dataStore = mockk(relaxed = true),
) {
    val savedVideoIds = mutableListOf<String>()
    var saveCalls = 0
    var failure: Throwable? = null

    override suspend fun getProgress(providerVideoId: String, accountKey: String?): TelegramProgressRecord? = null

    override suspend fun saveProgress(
        providerVideoId: String,
        positionMs: Long,
        durationMs: Long,
        title: String,
        thumbnailUrl: String?,
        channelId: String?,
        isPlayed: Boolean,
        accountKey: String?,
    ) {
        saveCalls += 1
        failure?.let { throw it }
        savedVideoIds += providerVideoId
    }

    override suspend fun markPlayed(
        providerVideoId: String,
        isPlayed: Boolean,
        title: String?,
        thumbnailUrl: String?,
        channelId: String?,
        accountKey: String?,
    ) {
        saveCalls += 1
        failure?.let { throw it }
        savedVideoIds += providerVideoId
    }
}
