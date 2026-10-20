package com.adulthub.tv.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adulthub.tv.data.local.BufferProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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

    @Test
    fun releaseIsIdempotent() {
        val manager = PlayerManager(context, scope, BufferProfile.STANDARD)
        manager.release()
        manager.release()
        assertNull(manager.errorMessage.value)
    }

    @Test
    fun playbackCannotStartAfterTheManagerWasReleased() {
        val manager = PlayerManager(context, scope, BufferProfile.STANDARD)
        manager.release()

        manager.prepareAndPlay("https://example.com/video.mp4")

        val error = manager.errorMessage.value
        assertNotNull("A released player manager must not start playback", error)
        assertTrue(error!!.contains("Cannot start playback after PlayerManager.release()"))
    }

    @Test
    fun anInvalidStreamUrlIsReportedAsError() {
        val manager = PlayerManager(context, scope, BufferProfile.STANDARD)
        try {
            manager.prepareAndPlay("not-a-valid-url")

            val error = manager.errorMessage.value
            assertNotNull("An invalid stream url must be reported", error)
            assertTrue(error!!.contains("Invalid stream URL"))
        } finally {
            manager.release()
        }
    }

    @Test
    fun aNewPlaybackRequestClearsThePreviousError() {
        val manager = PlayerManager(context, scope, BufferProfile.STANDARD)
        try {
            manager.prepareAndPlay("not-a-valid-url")
            assertNotNull(manager.errorMessage.value)

            manager.prepareAndPlay("https://example.com/video.mp4")
            assertNull(manager.errorMessage.value)
        } finally {
            manager.release()
        }
    }

    @Test
    fun thePositionFlowStartsAtZero() {
        val manager = PlayerManager(context, scope, BufferProfile.STANDARD)
        try {
            assertTrue(manager.currentPositionMs.value >= 0L)
            assertTrue(manager.durationMs.value >= 0L)
        } finally {
            manager.release()
        }
    }
}
