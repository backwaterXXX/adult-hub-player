package com.adulthub.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.adulthub.tv.data.model.AuthState
import com.adulthub.tv.data.model.TvItem
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.seconds

/** A provider whose account key can be switched during the test. */
private class FakeAccountKeyProvider(initialKey: String) : AccountKeyProvider {
    private val key = MutableStateFlow(initialKey)
    override val accountKey: Flow<String> = key
    override suspend fun currentKey(): String = key.value
    fun switchTo(newKey: String) {
        key.value = newKey
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountIsolationTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val context = mockk<Context>(relaxed = true)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var accountKeys: FakeAccountKeyProvider

    private val firstAccount = "account-one"
    private val secondAccount = "account-two"

    @Before
    fun setUp() {
        val testFile = tmpFolder.newFile("test_account_isolation.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile },
        )
        accountKeys = FakeAccountKeyProvider(firstAccount)
    }

    @Test
    fun `watch history of one account is not visible to another account`() = runTest(timeout = 120.seconds) {
        val store = WatchHistoryStore(context = context, accountKeyProvider = accountKeys, dataStore = dataStore)

        store.recordWatch(TvItem(id = "movie-1", title = "First account movie"))
        assertEquals(1, store.history.first().size)

        accountKeys.switchTo(secondAccount)
        assertTrue(store.history.first().isEmpty())

        accountKeys.switchTo(firstAccount)
        assertEquals("First account movie", store.history.first().first().title)
    }

    @Test
    fun `search history of one account is not visible to another account`() = runTest(timeout = 120.seconds) {
        val store = SearchHistoryStore(context = context, accountKeyProvider = accountKeys, dataStore = dataStore)

        store.recordQuery("first account query")
        assertEquals(listOf("first account query"), store.queries.first())

        accountKeys.switchTo(secondAccount)
        assertTrue(store.queries.first().isEmpty())
    }

    @Test
    fun `playback progress of one account is not visible to another account`() = runTest(timeout = 120.seconds) {
        val store = TelegramProgressStore(context = context, accountKeyProvider = accountKeys, dataStore = dataStore)

        store.saveProgress("video-1", positionMs = 5_000L, durationMs = 60_000L, title = "First account video")
        assertEquals(1, store.allProgress.first().size)

        accountKeys.switchTo(secondAccount)
        assertTrue(store.allProgress.first().isEmpty())
        assertNull(store.getProgress("video-1"))
    }

    @Test
    fun `legacy history is moved into the namespace of the signed in account`() = runTest(timeout = 120.seconds) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey(WatchHistoryStore.LEGACY_KEY_HISTORY_LIST)] =
                """[{"itemId":"legacy-movie","title":"Legacy movie","watchedAt":1}]"""
        }
        val store = WatchHistoryStore(context = context, accountKeyProvider = accountKeys, dataStore = dataStore)

        store.migrateLegacyHistory()

        val history = store.history.first()
        assertEquals(1, history.size)
        assertEquals("legacy-movie", history.first().itemId)
        val prefs = dataStore.data.first()
        assertNull(prefs[stringPreferencesKey(WatchHistoryStore.LEGACY_KEY_HISTORY_LIST)])
    }

    @Test
    fun `the account key is a stable digest without the server url or the user id`() {
        val digest = AccountScope.digest("https://jellyfin.example.org", "user-42")

        assertEquals(16, digest.length)
        assertEquals(digest, AccountScope.digest("https://jellyfin.example.org", "user-42"))
        assertNotEquals(digest, AccountScope.digest("https://jellyfin.example.org", "user-43"))
        assertNotEquals(digest, AccountScope.digest("https://other.example.org", "user-42"))
        assertFalse(digest.contains("jellyfin"))
        assertFalse(digest.contains("user-42"))
    }

    @Test
    fun `the account key follows the authenticated session`() {
        val authenticated = AuthState.Authenticated(
            serverUrl = "https://jellyfin.example.org",
            userId = "user-42",
            accessToken = "token",
            username = "user",
            serverName = "Jellyfin",
        )

        assertEquals(
            AccountScope.digest("https://jellyfin.example.org", "user-42"),
            AccountScope.keyOf(authenticated),
        )
        assertEquals(AccountScope.ANONYMOUS_KEY, AccountScope.keyOf(AuthState.NotAuthenticated))
    }

    @Test
    fun `a signed out account reads no history`() = runTest(timeout = 120.seconds) {
        val store = WatchHistoryStore(context = context, accountKeyProvider = accountKeys, dataStore = dataStore)
        store.recordWatch(TvItem(id = "movie-1", title = "First account movie"))

        accountKeys.switchTo(AccountScope.ANONYMOUS_KEY)

        assertTrue(store.history.first().isEmpty())
    }
}
