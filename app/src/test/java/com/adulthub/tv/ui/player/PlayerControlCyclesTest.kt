package com.adulthub.tv.ui.player

import androidx.media3.ui.AspectRatioFrameLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerControlCyclesTest {
    @Test
    fun `speed steps forward through the supported rates and wraps`() {
        assertEquals(1.25f, nextPlaybackSpeed(1.0f), 0f)
        assertEquals(1.5f, nextPlaybackSpeed(1.25f), 0f)
        assertEquals(2.0f, nextPlaybackSpeed(1.5f), 0f)
        assertEquals(1.0f, nextPlaybackSpeed(2.0f), 0f)
    }

    @Test
    fun `a slow rate is raised to the first step above normal`() {
        assertEquals(1.25f, nextPlaybackSpeed(0.5f), 0f)
    }

    @Test
    fun `aspect ratio cycles through fill and crop back to fit`() {
        assertEquals(
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            nextResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT),
        )
        assertEquals(
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            nextResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL),
        )
        assertEquals(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            nextResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
        )
    }

    @Test
    fun `an unknown resize mode restarts the cycle at fit`() {
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, nextResizeMode(-1))
    }
}
