package com.adulthub.tv.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRestoreRetryPolicyTest {

    @Test
    fun `the default policy retries three times after the first attempt`() {
        val policy = SessionRestoreRetryPolicy()

        assertEquals(4, policy.attempts)
        assertEquals(0L, policy.delayBefore(0))
        assertEquals(2_000L, policy.delayBefore(1))
        assertEquals(5_000L, policy.delayBefore(2))
        assertEquals(10_000L, policy.delayBefore(3))
    }

    @Test
    fun `a custom list defines both the attempts and their delays`() {
        val policy = SessionRestoreRetryPolicy(listOf(0L, 500L))

        assertEquals(2, policy.attempts)
        assertEquals(500L, policy.delayBefore(1))
    }

    @Test
    fun `an attempt beyond the list keeps the last delay`() {
        val policy = SessionRestoreRetryPolicy(listOf(0L, 300L, 900L))

        assertEquals(900L, policy.delayBefore(9))
    }

    @Test
    fun `a negative index runs immediately`() {
        val policy = SessionRestoreRetryPolicy(listOf(0L, 300L))

        assertEquals(0L, policy.delayBefore(-3))
    }

    @Test
    fun `an empty list still attempts once`() {
        val policy = SessionRestoreRetryPolicy(emptyList())

        assertEquals(1, policy.attempts)
        assertEquals(0L, policy.delayBefore(0))
        assertEquals(0L, policy.delayBefore(2))
    }
}
