package com.adulthub.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhashDurationTest {
    @Test
    fun convertsJellyfinTicksToMicroseconds() {
        assertEquals(1_234_000_000L, resolvePhashDurationUs(12_340_000_000L, 999_000L))
    }

    @Test
    fun fallsBackToMedia3DurationWhenItemMetadataIsMissing() {
        assertEquals(5_432_000_000L, resolvePhashDurationUs(null, 5_432_000L))
    }

    @Test
    fun fallsBackWhenItemDurationIsInvalid() {
        assertEquals(5_432_000_000L, resolvePhashDurationUs(0L, 5_432_000L))
    }

    @Test
    fun returnsNullWhenNeitherDurationIsAvailable() {
        assertNull(resolvePhashDurationUs(null, 0L))
        assertNull(resolvePhashDurationUs(-10L, -1L))
    }
}
