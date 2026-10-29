package com.adulthub.tv.data.update

import android.content.Context
import com.adulthub.tv.R
import com.adulthub.tv.data.local.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    private val repository: AppUpdateRepository = mockk(relaxed = true)
    private val settings: SettingsDataStore = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { settings.autoUpdateEnabled } returns flowOf(true)
        coEvery { settings.lastUpdateCheck } returns flowOf(0L)
        every { context.getString(R.string.settings_update_up_to_date) } returns "App is up to date"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a failed check does not postpone the next automatic check`() = runTest(testDispatcher) {
        coEvery { repository.checkAndDownload(any()) } returns UpdateCheckResult.Failed("network down")
        val manager = AppUpdateManager(repository, settings, CoroutineScope(testDispatcher), context = context)

        manager.checkNow()
        advanceUntilIdle()

        assertEquals("network down", manager.state.value.message)
        coVerify(exactly = 0) { settings.setLastUpdateCheck(any()) }
    }

    @Test
    fun `a successful check records the check timestamp`() = runTest(testDispatcher) {
        coEvery { repository.checkAndDownload(any()) } returns UpdateCheckResult.UpToDate
        val manager = AppUpdateManager(repository, settings, CoroutineScope(testDispatcher), context = context)

        manager.checkNow()
        advanceUntilIdle()

        assertEquals("App is up to date", manager.state.value.message)
        coVerify(exactly = 1) { settings.setLastUpdateCheck(any()) }
        assertFalse(manager.state.value.isChecking)
    }

    @Test
    fun `the automatic check is skipped when the last check is recent`() = runTest(testDispatcher) {
        coEvery { settings.lastUpdateCheck } returns flowOf(System.currentTimeMillis())
        val manager = AppUpdateManager(repository, settings, CoroutineScope(testDispatcher), context = context)

        manager.checkAutomaticallyIfDue()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.checkAndDownload(any()) }
    }

    @Test
    fun `the automatic check runs when no check was performed yet`() = runTest(testDispatcher) {
        coEvery { settings.lastUpdateCheck } returns flowOf(0L)
        coEvery { repository.checkAndDownload(any()) } returns UpdateCheckResult.UpToDate
        val manager = AppUpdateManager(repository, settings, CoroutineScope(testDispatcher), context = context)

        manager.checkAutomaticallyIfDue()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.checkAndDownload(any()) }
        assertTrue(manager.state.value.enabled)
    }
}
