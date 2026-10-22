package com.adulthub.tv.player

import com.adulthub.tv.data.local.StoredPhashJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The total budget of a video matching task.
 *
 * A task that the resolver never finishes may not be polled for the whole life of
 * the app, and it may not be started again at every launch.
 */
class PhashBudgetTest {

    private val budget = PhashBudget(totalLifetimeMs = 60_000L, maxPolls = 3)

    @Test
    fun `a task inside its lifetime may be polled`() {
        assertFalse(budget.isExpired(startedAtMs = 1_000L, nowMs = 30_000L))
        assertFalse(budget.isExhausted(startedAtMs = 1_000L, nowMs = 30_000L, polls = 2))
    }

    @Test
    fun `a task that lives longer than the budget is expired`() {
        assertTrue(budget.isExpired(startedAtMs = 1_000L, nowMs = 61_000L))
        // The end of the lifetime is the moment the task is expired.
        assertTrue(budget.isExpired(startedAtMs = 1_000L, nowMs = 1_000L + 60_000L))
        assertFalse(budget.isExpired(startedAtMs = 1_000L, nowMs = 1_000L + 60_000L - 1L))
    }

    @Test
    fun `the number of polls of one task is bounded`() {
        assertFalse(budget.isExhausted(startedAtMs = 1_000L, nowMs = 1_001L, polls = 2))
        assertTrue(budget.isExhausted(startedAtMs = 1_000L, nowMs = 1_001L, polls = 3))
    }

    @Test
    fun `the budget is used up by the lifetime and by the number of polls`() {
        // The lifetime alone is enough.
        assertTrue(budget.isExhausted(startedAtMs = 1_000L, nowMs = 90_000L, polls = 0))
        // The number of polls alone is enough.
        assertTrue(budget.isExhausted(startedAtMs = 1_000L, nowMs = 1_001L, polls = 3))
        // Nothing is used up yet.
        assertFalse(budget.isExhausted(startedAtMs = 1_000L, nowMs = 1_001L, polls = 0))
    }

    @Test
    fun `a clock that moved backwards does not expire a task`() {
        assertFalse(budget.isExpired(startedAtMs = 10_000L, nowMs = 5_000L))
    }

    @Test
    fun `a stored task that used up its budget is not continued at the start of the app`() {
        val expired = StoredPhashJob(
            videoId = "video-1",
            jobId = "job-1",
            state = "processing",
            updatedAt = 1_000L,
            startedAt = 1_000L,
        )
        val fresh = StoredPhashJob(
            videoId = "video-2",
            jobId = "job-2",
            state = "processing",
            updatedAt = 90_000L,
            startedAt = 90_000L,
        )

        assertFalse(budget.mayResume(expired, nowMs = 90_000L))
        assertTrue(budget.mayResume(fresh, nowMs = 90_000L))
    }

    @Test
    fun `the default budget is generous enough for a slow resolver`() {
        val default = PhashBudget()

        assertEquals(15 * 60 * 1000L, default.totalLifetimeMs)
        // A task that is polled once a minute may run for the whole lifetime.
        assertTrue(default.maxPolls >= 60)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a budget without a lifetime is rejected`() {
        PhashBudget(totalLifetimeMs = 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a budget without polls is rejected`() {
        PhashBudget(maxPolls = 0)
    }
}
