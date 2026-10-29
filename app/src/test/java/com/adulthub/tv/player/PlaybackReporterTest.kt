package com.adulthub.tv.player

import com.adulthub.tv.data.model.PlaybackInfo
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.repository.PlaybackRepository
import org.jellyfin.sdk.model.api.PlayMethod
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackReporterTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakePlaybackRepo = FakePlaybackReporterRepo()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `reportStart reports start once with correct ticks`() = runTest(testDispatcher) {
        val reporter = PlaybackReporter(fakePlaybackRepo, CoroutineScope(testDispatcher))

        reporter.startSession(
            itemId = "movie-123",
            mediaSourceId = "ms-1",
            playSessionId = "ps-1",
            playMethod = PlayMethod.DIRECT_PLAY,
            getCurrentPositionMs = { 5000L },
            getIsPaused = { false },
        )

        reporter.reportStart(5000L, canSeek = true)
        runCurrent()

        assertEquals(5000L * 10_000L, fakePlaybackRepo.lastReportedStartTicks)
        assertEquals("movie-123", fakePlaybackRepo.lastReportedItemId)

        // Second call should not trigger another report
        fakePlaybackRepo.lastReportedStartTicks = null
        reporter.reportStart(6000L, canSeek = true)
        runCurrent()
        assertEquals(null, fakePlaybackRepo.lastReportedStartTicks)

        reporter.stopSession(5000L)
        runCurrent()
    }

    @Test
    fun `stopSession reports stopped with correct position ticks`() = runTest(testDispatcher) {
        val reporter = PlaybackReporter(fakePlaybackRepo, CoroutineScope(testDispatcher))

        reporter.startSession(
            itemId = "movie-123",
            mediaSourceId = "ms-1",
            playSessionId = "ps-1",
            playMethod = PlayMethod.DIRECT_PLAY,
            getCurrentPositionMs = { 10_000L },
            getIsPaused = { false },
        )

        reporter.stopSession(10_000L)
        runCurrent()

        assertEquals(10_000L * 10_000L, fakePlaybackRepo.lastReportedStoppedTicks)
    }

    @Test
    fun `startSession triggers periodic progress reports`() = runTest(testDispatcher) {
        val reporter = PlaybackReporter(fakePlaybackRepo, CoroutineScope(testDispatcher))

        var currentPos = 0L
        reporter.startSession(
            itemId = "movie-123",
            mediaSourceId = "ms-1",
            playSessionId = "ps-1",
            playMethod = PlayMethod.DIRECT_PLAY,
            getCurrentPositionMs = { currentPos },
            getIsPaused = { false },
        )
        reporter.reportStart(0L, canSeek = true)

        // Advance 10s
        currentPos = 10_000L
        advanceTimeBy(10_500L)
        runCurrent()

        assertEquals(10_000L * 10_000L, fakePlaybackRepo.lastReportedProgressTicks)

        // Advance another 10s
        currentPos = 20_000L
        advanceTimeBy(10_000L)
        runCurrent()

        assertEquals(20_000L * 10_000L, fakePlaybackRepo.lastReportedProgressTicks)

        reporter.stopSession(currentPos)
        runCurrent()
    }

    @Test
    fun `queued progress reports are conflated while a slow report is in flight`() = runTest(testDispatcher) {
        val progressGate = CompletableDeferred<Unit>()
        fakePlaybackRepo.progressGate = progressGate
        val reporter = PlaybackReporter(fakePlaybackRepo, CoroutineScope(testDispatcher))

        reporter.startSession(
            itemId = "movie-123",
            mediaSourceId = "ms-1",
            playSessionId = "ps-1",
            playMethod = PlayMethod.DIRECT_PLAY,
            getCurrentPositionMs = { 0L },
            getIsPaused = { false },
        )
        reporter.reportStart(0L, canSeek = true)
        runCurrent()

        reporter.reportProgressNow(1_000L, isPaused = false)
        advanceTimeBy(500L)
        runCurrent()
        reporter.reportProgressNow(2_000L, isPaused = false)
        advanceTimeBy(500L)
        runCurrent()
        reporter.reportProgressNow(3_000L, isPaused = false)
        advanceTimeBy(500L)
        runCurrent()

        progressGate.complete(Unit)
        runCurrent()
        reporter.stopSession(4_000L)
        advanceUntilIdle()

        assertEquals(2, fakePlaybackRepo.progressReportCount)
        assertEquals(3_000L * 10_000L, fakePlaybackRepo.lastReportedProgressTicks)
        assertEquals(4_000L * 10_000L, fakePlaybackRepo.lastReportedStoppedTicks)
    }
}

private class FakePlaybackReporterRepo : PlaybackRepository(mockk(relaxed = true), mockk(relaxed = true)) {
    var lastReportedItemId: String? = null
    var lastReportedStartTicks: Long? = null
    var lastReportedProgressTicks: Long? = null
    var lastReportedStoppedTicks: Long? = null
    var progressReportCount = 0
    var progressGate: CompletableDeferred<Unit>? = null

    override suspend fun reportPlaybackStart(
        itemId: String,
        mediaSourceId: String?,
        positionTicks: Long,
        playSessionId: String?,
        canSeek: Boolean,
        playMethod: PlayMethod,
    ): Result<Unit> {
        lastReportedItemId = itemId
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
        progressGate?.await()
        progressReportCount += 1
        lastReportedItemId = itemId
        lastReportedProgressTicks = positionTicks
        return Result.success(Unit)
    }

    override suspend fun reportPlaybackStopped(
        itemId: String,
        mediaSourceId: String?,
        positionTicks: Long,
        playSessionId: String?,
    ): Result<Unit> {
        lastReportedItemId = itemId
        lastReportedStoppedTicks = positionTicks
        return Result.success(Unit)
    }
}
