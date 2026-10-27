package com.adulthub.tv.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adulthub.tv.data.local.BufferProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The ExoPlayer of the player manager has to be used on the main thread, so every call runs
 * through the instrumentation.
 */
@RunWith(AndroidJUnit4::class)
class PlayerManagerInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun <T> onMain(block: () -> T): T {
        val holder = arrayOfNulls<Any?>(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { holder[0] = block() }
        @Suppress("UNCHECKED_CAST")
        return holder[0] as T
    }

    private fun createPlayerManager(): PlayerManager = onMain {
        PlayerManager(context, scope, BufferProfile.STANDARD)
    }

    @Test
    fun releaseIsIdempotent() {
        val manager = createPlayerManager()
        onMain {
            manager.release()
            manager.release()
        }
        assertNull(manager.errorMessage.value)
    }

    @Test
    fun playbackCannotStartAfterTheManagerWasReleased() {
        val manager = createPlayerManager()
        onMain { manager.release() }

        onMain { manager.prepareAndPlay("https://example.com/video.mp4") }

        val error = manager.errorMessage.value
        assertNotNull("A released player manager must not start playback", error)
        assertTrue(error!!.contains("Cannot start playback after PlayerManager.release()"))
    }

    @Test
    fun anInvalidStreamUrlIsReportedAsError() {
        val manager = createPlayerManager()
        try {
            onMain { manager.prepareAndPlay("not-a-valid-url") }

            val error = manager.errorMessage.value
            assertNotNull("An invalid stream url must be reported", error)
            assertTrue(error!!.contains("Invalid stream URL"))
        } finally {
            onMain { manager.release() }
        }
    }

    @Test
    fun aNewPlaybackRequestClearsThePreviousError() {
        val manager = createPlayerManager()
        try {
            onMain { manager.prepareAndPlay("not-a-valid-url") }
            assertNotNull(manager.errorMessage.value)

            onMain { manager.prepareAndPlay("https://example.com/video.mp4") }
            assertNull(manager.errorMessage.value)
        } finally {
            onMain { manager.release() }
        }
    }

    @Test
    fun thePositionFlowStartsAtZero() {
        val manager = createPlayerManager()
        try {
            assertTrue(manager.currentPositionMs.value >= 0L)
            assertTrue(manager.durationMs.value >= 0L)
        } finally {
            onMain { manager.release() }
        }
    }

    @Test
    fun releaseCancelsPendingQualitySwitchCleanly() {
        val manager = createPlayerManager()
        try {
            onMain {
                manager.prepareAndPlay("https://example.com/video.mp4")
            }
            var switchResult: Boolean? = null
            scope.launch {
                switchResult = manager.switchQualityStream("https://example.com/video_low.mp4", null)
            }
            onMain {
                manager.release()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertFalse(switchResult == true)
        } finally {
            onMain { manager.release() }
        }
    }

    @Test
    fun prepareAndPlayCancelsPendingQualitySwitchCleanly() {
        val manager = createPlayerManager()
        try {
            onMain {
                manager.prepareAndPlay("https://example.com/video.mp4")
            }
            var switchResult: Boolean? = null
            scope.launch {
                switchResult = manager.switchQualityStream("https://example.com/video_low.mp4", null)
            }
            onMain {
                manager.prepareAndPlay("https://example.com/video_2.mp4")
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertFalse(switchResult == true)
        } finally {
            onMain { manager.release() }
        }
    }
}
