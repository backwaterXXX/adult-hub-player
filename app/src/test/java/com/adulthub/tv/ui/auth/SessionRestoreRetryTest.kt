package com.adulthub.tv.ui.auth

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRestoreRetryTest {

    @Test
    fun `a discovery that works runs once and waits for nothing`() = runTest {
        var calls = 0

        val result = discoverWithRetry(SessionRestoreRetryPolicy()) {
            calls++
            Result.success("Jellyfin")
        }

        assertEquals(1, calls)
        assertEquals("Jellyfin", result.getOrNull())
        assertEquals(0L, currentTime)
    }

    @Test
    fun `a network that wakes up on the third attempt keeps the session`() = runTest {
        var calls = 0

        val result = discoverWithRetry(SessionRestoreRetryPolicy(listOf(0L, 2_000L, 5_000L))) {
            calls++
            if (calls < 3) Result.failure<String>(IOException("network unreachable")) else Result.success("Jellyfin")
        }

        assertEquals(3, calls)
        assertEquals("Jellyfin", result.getOrNull())
        assertEquals(7_000L, currentTime)
    }

    @Test
    fun `every attempt fails and the last failure is returned`() = runTest {
        var calls = 0

        val result = discoverWithRetry(SessionRestoreRetryPolicy(listOf(0L, 2_000L, 5_000L))) {
            calls++
            Result.failure<String>(IOException("attempt $calls"))
        }

        assertEquals(3, calls)
        assertTrue(result.isFailure)
        assertEquals("attempt 3", result.exceptionOrNull()?.message)
        assertEquals(7_000L, currentTime)
    }

    @Test
    fun `the default policy waits seventeen seconds in total`() = runTest {
        var calls = 0

        discoverWithRetry(SessionRestoreRetryPolicy()) {
            calls++
            Result.failure<String>(IOException("offline"))
        }

        assertEquals(4, calls)
        assertEquals(17_000L, currentTime)
    }

    @Test
    fun `a policy without delays still attempts once`() = runTest {
        var calls = 0

        discoverWithRetry(SessionRestoreRetryPolicy(emptyList())) {
            calls++
            Result.success("Jellyfin")
        }

        assertEquals(1, calls)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `the retry stops at the first success`() = runTest {
        var calls = 0

        discoverWithRetry(SessionRestoreRetryPolicy()) {
            calls++
            if (calls == 1) Result.success("Jellyfin") else Result.failure<String>(IOException("unexpected"))
        }
        advanceUntilIdle()

        assertEquals(1, calls)
    }
}
