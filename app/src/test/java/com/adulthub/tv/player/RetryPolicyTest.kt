package com.adulthub.tv.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The automatic retry budget covers consecutive failures.
 *
 * It is only given back after the media really played for a while. A seek, a
 * pause, a buffering phase and a changed speed cannot shorten the way to a
 * stable playback.
 */
class RetryPolicyTest {

    private fun tick(budget: RetryBudget, nowMs: Long, playing: Boolean) {
        budget.track(nowMs, playing)
    }

    @Test
    fun `the budget is given back after thirty seconds of playing`() {
        val budget = RetryBudget(30_000L)
        budget.registerFailure()
        assertEquals(1, budget.attemptCount)

        // The first tick only marks the start of the measurement.
        var now = 0L
        repeat(30) {
            now += 1_000L
            tick(budget, now, playing = true)
        }
        assertEquals(1, budget.attemptCount)

        now += 1_000L
        tick(budget, now, playing = true)
        assertEquals(0, budget.attemptCount)
    }

    @Test
    fun `a pause does not count towards the stable playback`() {
        val budget = RetryBudget(30_000L)
        budget.registerFailure()

        var now = 0L
        repeat(60) {
            now += 1_000L
            // The player is paused for half of the time.
            tick(budget, now, playing = it % 2 == 0)
        }

        assertEquals(1, budget.attemptCount)
    }

    @Test
    fun `a buffering phase does not count towards the stable playback`() {
        val budget = RetryBudget(30_000L)
        budget.registerFailure()

        var now = 0L
        repeat(20) {
            now += 1_000L
            // The player buffers for two seconds of every ten seconds.
            tick(budget, now, playing = it % 10 >= 2)
        }

        // Only the seconds without buffering count, so the budget is still used.
        assertEquals(1, budget.attemptCount)
    }

    @Test
    fun `a pause that stops polling does not count as playback`() {
        val budget = RetryBudget(30_000L)
        budget.registerFailure()

        budget.track(0L, playing = true)
        budget.track(1_000L, playing = true)
        // Polling stops with the pause and resumes a minute later; the break must not
        // be measured as playback.
        budget.stopTracking()
        budget.track(61_000L, playing = true)
        budget.track(62_000L, playing = true)

        assertEquals(1, budget.attemptCount)
    }

    @Test
    fun `a seek cannot fake the elapsed time`() {
        val budget = RetryBudget(30_000L)
        budget.registerFailure()

        // The measurement only knows the wall clock and the playing state, so a
        // jump of the position cannot add time.
        var now = 0L
        repeat(10) {
            now += 1_000L
            tick(budget, now, playing = true)
        }

        assertEquals(1, budget.attemptCount)
    }

    @Test
    fun `a changed speed does not change the measured time`() {
        val budget = RetryBudget(30_000L)
        budget.registerFailure()

        var now = 0L
        repeat(31) {
            now += 1_000L
            tick(budget, now, playing = true)
        }

        // The playback ran for thirty seconds of wall clock, whatever the speed was.
        assertEquals(0, budget.attemptCount)
    }

    @Test
    fun `the number of attempts stays bounded`() {
        val budget = RetryBudget(30_000L)

        assertTrue(budget.canRetry(3))
        budget.registerFailure()
        assertTrue(budget.canRetry(3))
        budget.registerFailure()
        assertTrue(budget.canRetry(3))
        budget.registerFailure()
        assertFalse(budget.canRetry(3))
        assertEquals(3, budget.attemptCount)
    }

    @Test
    fun `the delay grows with the attempt`() {
        assertEquals(1_000L, retryDelayMs(1))
        assertEquals(2_000L, retryDelayMs(2))
        assertEquals(4_000L, retryDelayMs(3))
    }

    @Test
    fun `a reset forgets the budget`() {
        val budget = RetryBudget(30_000L)
        budget.registerFailure()

        budget.reset()

        assertEquals(0, budget.attemptCount)
        assertTrue(budget.canRetry(3))
    }

    @Test
    fun `a retry resolves a fresh address and keeps the position`() = runTest(timeout = 120.seconds) {
        var requestedPosition = -1L
        var requestedItem = ""

        val url = resolveRetryStreamUrl(
            provider = { itemId, positionMs ->
                requestedItem = itemId
                requestedPosition = positionMs
                "https://resolver.example.com/fresh.mp4"
            },
            itemId = "movie-1",
            positionMs = 42_000L,
            fallbackUrl = "https://resolver.example.com/expired.mp4",
        )

        assertEquals("https://resolver.example.com/fresh.mp4", url)
        assertEquals("movie-1", requestedItem)
        assertEquals(42_000L, requestedPosition)
    }

    @Test
    fun `without a provider the previous address is used again`() = runTest(timeout = 120.seconds) {
        val url = resolveRetryStreamUrl(
            provider = null,
            itemId = "movie-1",
            positionMs = 42_000L,
            fallbackUrl = "https://resolver.example.com/expired.mp4",
        )

        assertEquals("https://resolver.example.com/expired.mp4", url)
    }

    @Test
    fun `a provider that cannot refresh fails the retry instead of using the expired address`() =
        runTest(timeout = 120.seconds) {
            val url = resolveRetryStreamUrl(
                provider = { _, _ -> null },
                itemId = "movie-1",
                positionMs = 42_000L,
                fallbackUrl = "https://resolver.example.com/expired.mp4",
            )

            assertNull(url)
        }

    @Test
    fun `a provider that throws fails the retry instead of using the expired address`() =
        runTest(timeout = 120.seconds) {
            val url = resolveRetryStreamUrl(
                provider = { _, _ -> throw IllegalStateException("resolver offline") },
                itemId = "movie-1",
                positionMs = 42_000L,
                fallbackUrl = "https://resolver.example.com/expired.mp4",
            )

            assertNull(url)
        }

    @Test
    fun `cancelling the retry propagates instead of failing it`() = runTest(timeout = 120.seconds) {
        var thrown: CancellationException? = null
        try {
            resolveRetryStreamUrl(
                provider = { _, _ -> throw CancellationException("playback stopped") },
                itemId = "movie-1",
                positionMs = 42_000L,
                fallbackUrl = "https://resolver.example.com/expired.mp4",
            )
        } catch (error: CancellationException) {
            thrown = error
        }

        assertNotNull(thrown)
    }
}
