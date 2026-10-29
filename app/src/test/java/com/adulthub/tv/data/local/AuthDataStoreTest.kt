package com.adulthub.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.adulthub.tv.data.model.AuthState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AuthDataStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val context = mockk<Context>(relaxed = true)
    private val crypto = mockk<KeyStoreCrypto>()

    private lateinit var authDataStore: AuthDataStore

    @Before
    fun setUp() {
        coEvery { crypto.encrypt(any()) } answers { "enc_" + firstArg<String>() }
        coEvery { crypto.decrypt(any()) } answers { firstArg<String>().removePrefix("enc_") }
        val testFile = tmpFolder.newFile("auth.preferences_pb")
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile },
        )
        authDataStore = AuthDataStore(context, crypto, dataStore)
    }

    private suspend fun save(accessToken: String) {
        authDataStore.saveSession(
            serverUrl = "https://jf.example.com",
            userId = "user-1",
            username = "user",
            accessToken = accessToken,
            serverName = "jf.example.com",
        )
    }

    @Test
    fun `a matching token clears the stored session`() = runTest(testDispatcher) {
        save("current-token")

        assertTrue(authDataStore.clearSessionIfAccessTokenMatches("current-token"))
        assertEquals(AuthState.NotAuthenticated, authDataStore.getAuthState())
    }

    @Test
    fun `a different token leaves the stored session`() = runTest(testDispatcher) {
        save("current-token")

        assertFalse(authDataStore.clearSessionIfAccessTokenMatches("older-token"))
        assertEquals(
            "current-token",
            (authDataStore.getAuthState() as AuthState.Authenticated).accessToken,
        )
    }

    @Test
    fun `a session saved during the token check survives a stale rejection`() =
        runTest(testDispatcher) {
            save("current-token")
            val decryptStarted = CompletableDeferred<Unit>()
            val releaseDecrypt = CompletableDeferred<Unit>()
            coEvery { crypto.decrypt(any()) } coAnswers {
                val value = firstArg<String>()
                if (value == "enc_current-token") {
                    decryptStarted.complete(Unit)
                    releaseDecrypt.await()
                }
                value.removePrefix("enc_")
            }

            val clearing = async { authDataStore.clearSessionIfAccessTokenMatches("current-token") }
            decryptStarted.await()
            save("newer-token")
            releaseDecrypt.complete(Unit)

            assertFalse(clearing.await())
            assertEquals(
                "newer-token",
                (authDataStore.getAuthState() as AuthState.Authenticated).accessToken,
            )
        }
}
