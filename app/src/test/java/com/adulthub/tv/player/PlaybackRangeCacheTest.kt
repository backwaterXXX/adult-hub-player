package com.adulthub.tv.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRangeCacheTest {
    @Test
    fun readsBackCachedPlaybackBytesAtTheirStreamOffsets() {
        val cache = PlaybackRangeCache()
        cache.put(12L, byteArrayOf(1, 2, 3, 4))
        val target = ByteArray(6)

        assertEquals(4, cache.readCached(12L, target, 1, 4))
        assertArrayEquals(byteArrayOf(0, 1, 2, 3, 4, 0), target)
    }

    @Test
    fun returnsOnlyContiguousCachedBytesBeforeARequestedGap() {
        val cache = PlaybackRangeCache()
        cache.put(0L, byteArrayOf(7, 8))
        cache.put(4L, byteArrayOf(9, 10))
        val target = ByteArray(5)

        assertEquals(2, cache.readCached(0L, target, 0, target.size))
        assertArrayEquals(byteArrayOf(7, 8, 0, 0, 0), target)
    }

    @Test
    fun mergesPlaybackReadsThatFillOneRangeBlock() {
        val cache = PlaybackRangeCache()
        cache.put(62_000L, byteArrayOf(1, 2, 3, 4))
        cache.put(62_004L, byteArrayOf(5, 6, 7, 8))
        val target = ByteArray(8)

        assertEquals(8, cache.readCached(62_000L, target, 0, target.size))
        assertTrue(target.contentEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
    }

    @Test
    fun cachesAndReadsAcrossBlockBoundaries() {
        val cache = PlaybackRangeCache()
        // Block boundary is at 65536 (64 KB)
        val data = ByteArray(200) { it.toByte() }
        val offset = 65_500L
        cache.put(offset, data)

        val target = ByteArray(200)
        val read = cache.readCached(offset, target, 0, target.size)
        assertEquals(200, read)
        assertArrayEquals(data, target)
    }

    @Test
    fun handlesLargeBulkChunkStreamingAccurately() {
        val cache = PlaybackRangeCache()
        val chunkSize = 128 * 1024 // 128 KB spanning 2 blocks
        val data = ByteArray(chunkSize) { (it % 256).toByte() }
        cache.put(100_000L, data)

        val target = ByteArray(chunkSize)
        val read = cache.readCached(100_000L, target, 0, target.size)
        assertEquals(chunkSize, read)
        assertArrayEquals(data, target)
    }

    @Test
    fun returnsZeroWhenRequestedOffsetIsNotCached() {
        val cache = PlaybackRangeCache()
        cache.put(0L, byteArrayOf(1, 2, 3))
        val target = ByteArray(10)

        assertEquals(0, cache.readCached(100L, target, 0, 10))
    }
}
