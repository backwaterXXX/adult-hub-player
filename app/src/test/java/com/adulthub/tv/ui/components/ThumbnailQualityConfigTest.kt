package com.adulthub.tv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailQualityConfigTest {

    @Test
    fun `movieTargetWidth returns 480 when lowRamMode is disabled and 320 when enabled`() {
        assertEquals(480, ThumbnailQualityConfig.movieTargetWidth(lowRamMode = false))
        assertEquals(320, ThumbnailQualityConfig.movieTargetWidth(lowRamMode = true))
    }

    @Test
    fun `movieTargetHeight maintains 16 by 9 aspect ratio`() {
        assertEquals(270, ThumbnailQualityConfig.movieTargetHeight(lowRamMode = false))
        assertEquals(180, ThumbnailQualityConfig.movieTargetHeight(lowRamMode = true))
    }

    @Test
    fun `personTargetWidth returns 300 when lowRamMode is disabled and 200 when enabled`() {
        assertEquals(300, ThumbnailQualityConfig.personTargetWidth(lowRamMode = false))
        assertEquals(200, ThumbnailQualityConfig.personTargetWidth(lowRamMode = true))
    }

    @Test
    fun `personTargetHeight maintains 2 by 3 aspect ratio`() {
        assertEquals(450, ThumbnailQualityConfig.personTargetHeight(lowRamMode = false))
        assertEquals(300, ThumbnailQualityConfig.personTargetHeight(lowRamMode = true))
    }
}
