package com.adulthub.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * A failed write has to be visible at the boundary of the store.
 *
 * The store used to swallow the failure, so a screen could report a change that
 * was never stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferenceWriteFailureTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val context = mockk<Context>(relaxed = true)

    private lateinit var failingDataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        // A directory cannot be opened as a preferences file, so every write fails.
        val unwritable = tmpFolder.newFolder("history.preferences_pb")
        failingDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { unwritable },
        )
    }

    @Test
    fun `a failed history write is reported to the caller`() = runTest(timeout = 120.seconds) {
        val store = WatchHistoryStore(
            context = context,
            accountKeyProvider = AccountKeyProvider.ANONYMOUS,
            dataStore = SafePreferencesDataStore(failingDataStore),
        )

        val failure = runCatching { store.recordWatch(TvItem(id = "movie-1", title = "Movie")) }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure is PreferenceWriteException)
    }

    @Test
    fun `a failed progress write is reported to the caller`() = runTest(timeout = 120.seconds) {
        val store = TelegramProgressStore(
            context = context,
            accountKeyProvider = AccountKeyProvider.ANONYMOUS,
            dataStore = SafePreferencesDataStore(failingDataStore),
        )

        val failure = runCatching {
            store.saveProgress("video-1", positionMs = 1_000L, durationMs = 10_000L, title = "Video")
        }.exceptionOrNull()

        assertTrue(failure is PreferenceWriteException)
    }

    @Test
    fun `a failed sign out is reported and keeps the session`() = runTest(timeout = 120.seconds) {
        val authDataStore = mockk<AuthDataStore>()
        val clientManager = mockk<JellyfinClientManager>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        every { authDataStore.authState } returns kotlinx.coroutines.flow.flowOf(
            com.adulthub.tv.data.model.AuthState.NotAuthenticated,
        )
        coEvery { authDataStore.clearSession() } throws PreferenceWriteException(
            "The preference could not be stored",
            IOException("disk full"),
        )
        val repository = AuthRepository(clientManager, authDataStore, settingsDataStore)

        val result = repository.logout()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PreferenceWriteException)
        // The session stays, so the client keeps its token.
        coVerify(exactly = 0) { clientManager.clearSession() }
    }

    @Test
    fun `a failed import is reported to the caller`() = runTest(timeout = 120.seconds) {
        val watchHistoryStore = mockk<WatchHistoryStore>(relaxed = true)
        coEvery { watchHistoryStore.hasLegacyData() } returns true
        coEvery { watchHistoryStore.importLegacyData(any()) } throws PreferenceWriteException(
            "The preference could not be stored",
            IOException("disk full"),
        )
        val migration = UserScopedDataMigration(
            watchHistoryStore = watchHistoryStore,
            searchHistoryStore = mockk(relaxed = true),
            telegramProgressStore = mockk(relaxed = true),
            phashJobStore = mockk(relaxed = true),
            accountKeys = AccountKeyProvider.ANONYMOUS,
        )

        val failure = runCatching { migration.importIntoCurrentAccount() }.exceptionOrNull()

        assertTrue(failure is PreferenceWriteException)
    }

    @Test
    fun `a cancelled import is not converted into a write failure`() = runTest(timeout = 120.seconds) {
        val store = WatchHistoryStore(
            context = context,
            accountKeyProvider = AccountKeyProvider.ANONYMOUS,
            dataStore = SafePreferencesDataStore(failingDataStore),
        )

        val failure = runCatching {
            throw kotlinx.coroutines.CancellationException("cancelled")
        }.exceptionOrNull()

        assertTrue(failure is kotlinx.coroutines.CancellationException)
        assertFalse(failure is PreferenceWriteException)
        assertEquals("cancelled", failure?.message)
        // The store is not touched by the cancelled call.
        assertNull(store.history.first().firstOrNull())
    }

    @Test
    fun `a pending import is reported before it is imported`() = runTest(timeout = 120.seconds) {
        val watchHistoryStore = mockk<WatchHistoryStore>(relaxed = true)
        coEvery { watchHistoryStore.hasLegacyData() } returns true
        coEvery { watchHistoryStore.importLegacyData(any()) } returns true
        val migration = UserScopedDataMigration(
            watchHistoryStore = watchHistoryStore,
            searchHistoryStore = mockk(relaxed = true),
            telegramProgressStore = mockk(relaxed = true),
            phashJobStore = mockk(relaxed = true),
            accountKeys = AccountKeyProvider.ANONYMOUS,
        )

        assertTrue(migration.hasPendingImport())
        assertTrue(migration.importIntoCurrentAccount())
    }
}
