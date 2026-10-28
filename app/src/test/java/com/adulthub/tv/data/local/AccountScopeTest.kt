package com.adulthub.tv.data.local

import com.adulthub.tv.data.model.AuthState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class AccountScopeTest {

    private val firstSession = AuthState.Authenticated(
        serverUrl = "https://jellyfin.example.org",
        userId = "user-1",
        accessToken = "token-one",
        username = "user",
        serverName = "Jellyfin",
    )

    private val secondSession = firstSession.copy(accessToken = "token-two")

    private fun authDataStore(state: AuthState): AuthDataStore {
        val store = mockk<AuthDataStore>()
        every { store.authState } returns flow { emit(state) }
        return store
    }

    @Test
    fun `the owner takes both fields from one auth state`() = runTest(timeout = 120.seconds) {
        var collections = 0
        val store = mockk<AuthDataStore>()
        every { store.authState } returns flow {
            collections++
            emit(if (collections == 1) firstSession else secondSession)
        }
        val scope = AccountScope(store)

        val owner = scope.currentOwner()

        assertEquals(1, collections)
        assertEquals(
            SessionOwner(
                accountKey = AccountScope.keyOf(firstSession),
                sessionMarker = AccountScope.markerOf(firstSession),
            ),
            owner,
        )
    }

    @Test
    fun `an owner is current only when both the account and the session match`() = runTest(timeout = 120.seconds) {
        val state = kotlinx.coroutines.flow.MutableStateFlow<AuthState>(firstSession)
        val store = mockk<AuthDataStore>()
        every { store.authState } returns state
        val scope = AccountScope(store)

        val owner = scope.currentOwner()
        assertTrue(scope.isCurrent(owner))
        assertFalse(scope.isCurrent(owner.copy(accountKey = "another-account")))
        assertFalse(scope.isCurrent(owner.copy(sessionMarker = "another-session")))

        state.value = secondSession
        assertFalse(scope.isCurrent(owner))
        assertEquals(owner.accountKey, scope.currentOwner().accountKey)
        assertNotEquals(owner.sessionMarker, scope.currentOwner().sessionMarker)
    }
}
