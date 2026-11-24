package com.adulthub.tv.data.repository

import com.adulthub.tv.data.api.AdultHubApi
import com.adulthub.tv.data.api.AdultHubClientManager
import com.adulthub.tv.data.api.AuthLoginRequestDto
import com.adulthub.tv.data.api.AuthLoginResponseDto
import com.adulthub.tv.data.api.AuthRevokeResponseDto
import com.adulthub.tv.data.api.AuthUserDto
import com.adulthub.tv.data.api.VersionDiscoveryDto
import com.adulthub.tv.data.local.AuthDataStore
import com.adulthub.tv.data.local.PreferenceWriteException
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.AuthState
import com.adulthub.tv.data.network.ServiceUrlPolicy
import com.adulthub.tv.data.network.ServiceUrlValidation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {

    private val clientManager: AdultHubClientManager = mockk(relaxed = true)
    private val authDataStore: AuthDataStore = mockk(relaxed = true)
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val mockApi: AdultHubApi = mockk(relaxed = true)

    private val repository = AuthRepository(
        clientManager = clientManager,
        authDataStore = authDataStore,
        settingsDataStore = settingsDataStore
    )

    init {
        coEvery { settingsDataStore.requireAllowedServiceUrl(any(), any()) } coAnswers {
            when (val validation = ServiceUrlPolicy.inspect(firstArg<String>())) {
                is ServiceUrlValidation.Valid -> validation.value
                is ServiceUrlValidation.Invalid -> throw IllegalArgumentException(validation.reason)
            }
        }
    }

    @Test
    fun `login with valid credentials returns Authenticated state and saves session`() = runTest {
        val serverUrl = "http://192.168.1.10:8096"
        val username = "testuser"
        val password = "secret_password"
        val userId = "user-123"
        val accessToken = "test_access_token_12345"
        val refreshToken = "test_refresh_token_abcde"

        val loginResponse = AuthLoginResponseDto(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = AuthUserDto(id = userId, username = username)
        )

        every { clientManager.createApi(serverUrl) } returns mockApi
        coEvery { mockApi.login(any()) } returns loginResponse

        val result = repository.login(
            serverUrl = serverUrl,
            username = username,
            password = password
        )

        assertTrue("Expected login to succeed", result.isSuccess)
        val authState = result.getOrThrow()
        assertEquals(serverUrl, authState.serverUrl)
        assertEquals(userId, authState.userId)
        assertEquals(accessToken, authState.accessToken)
        assertEquals(username, authState.username)
        assertEquals(refreshToken, authState.refreshToken)

        verify(exactly = 1) {
            clientManager.updateSession(serverUrl, accessToken, userId, username, refreshToken)
        }
        coVerify(exactly = 1) {
            authDataStore.saveSession(
                serverUrl = serverUrl,
                accessToken = accessToken,
                userId = userId,
                username = username,
                serverName = any(),
                refreshToken = refreshToken
            )
        }
    }

    @Test
    fun `login with invalid URL fails`() = runTest {
        val result = repository.login(
            serverUrl = "not-a-valid-url",
            username = "user",
            password = "pwd"
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `logout clears the stored session and active client`() = runTest {
        every { clientManager.currentRefreshToken } returns "refresh-tok"
        every { clientManager.getApiOrNull() } returns mockApi
        coEvery { mockApi.revoke(any()) } returns AuthRevokeResponseDto(revoked = true)

        val result = repository.logout()

        assertTrue(result.isSuccess)
        verify(exactly = 1) { clientManager.clearSession() }
        coVerify(exactly = 1) { authDataStore.clearSession() }
    }

    @Test
    fun `discoverServer returns success when Adult Hub version responds`() = runTest {
        every { clientManager.createApi("http://192.168.1.10:8096") } returns mockApi
        coEvery { mockApi.getVersion() } returns VersionDiscoveryDto(serverVersion = "1.0.0")

        val result = repository.discoverServer("http://192.168.1.10:8096")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `initiateQuickConnect returns the pairing code issued by Adult Hub`() = runTest {
        every { clientManager.createApi(any<String>()) } returns mockApi
        coEvery { mockApi.pairInit(any()) } returns com.adulthub.tv.data.api.PairingInitResponseDto(
            pairingCode = "123-456",
            secret = "pair_sec_test"
        )

        val result = repository.initiateQuickConnect("http://192.168.1.10:8096")

        assertTrue(result.isSuccess)
        assertEquals("123-456", result.getOrThrow().code)
        assertEquals("pair_sec_test", result.getOrThrow().secret)
        coVerify(exactly = 1) { mockApi.pairInit(any()) }
    }

    @Test
    fun `restore session requests consent before opening a saved local HTTP server`() = runTest {
        val savedSession = AuthState.Authenticated(
            serverUrl = "http://192.168.1.10:8096",
            userId = "user-1",
            accessToken = "token",
            username = "user",
            serverName = "Adult Hub",
            refreshToken = "refresh-tok"
        )
        coEvery { authDataStore.getAuthState() } returns savedSession
        coEvery { settingsDataStore.isCleartextOriginApproved(savedSession.serverUrl) } returns false

        val result = repository.restoreSession()

        assertEquals(AuthState.HttpConsentRequired(savedSession.serverUrl), result)
        coVerify(exactly = 0) { clientManager.updateSession(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `restore session returns HttpBlocked when URL is invalid`() = runTest {
        val savedSession = AuthState.Authenticated(
            serverUrl = "invalid-url-without-scheme",
            userId = "user-1",
            accessToken = "token",
            username = "user",
            serverName = "Adult Hub"
        )
        coEvery { authDataStore.getAuthState() } returns savedSession

        val result = repository.restoreSession()

        assertTrue(result is AuthState.HttpBlocked)
        assertEquals(savedSession.serverUrl, (result as AuthState.HttpBlocked).serverUrl)
    }

    @Test
    fun `handleRejectedSession clears session and client when token matches`() = runTest {
        val token = "rejected_access_token_123"
        val session = AuthState.Authenticated(
            serverUrl = "http://192.168.1.10:8096",
            userId = "user-1",
            accessToken = token,
            username = "user"
        )
        coEvery { authDataStore.getAuthState() } returns session
        coEvery { authDataStore.clearSessionIfAccessTokenMatches(token) } returns true
        every { clientManager.clearSessionIfTokenMatches(token) } returns true

        val cleared = repository.handleRejectedSession(token)

        assertEquals(session, cleared)
        coVerify(exactly = 1) { authDataStore.clearSessionIfAccessTokenMatches(token) }
        verify(exactly = 1) { clientManager.clearSessionIfTokenMatches(token) }
    }

    @Test
    fun `handleRejectedSession for different token does not clear session`() = runTest {
        val currentToken = "current_token"
        val staleToken = "stale_token"
        val session = AuthState.Authenticated(
            serverUrl = "http://192.168.1.10:8096",
            userId = "user-1",
            accessToken = currentToken,
            username = "user"
        )
        coEvery { authDataStore.getAuthState() } returns session

        val cleared = repository.handleRejectedSession(staleToken)

        assertNull(cleared)
        coVerify(exactly = 0) { authDataStore.clearSessionIfAccessTokenMatches(any()) }
        verify(exactly = 0) { clientManager.clearSessionIfTokenMatches(any()) }
    }

    @Test
    fun `failed local sign out does not revoke token on server`() = runTest {
        coEvery { authDataStore.getAuthState() } returns AuthState.Authenticated(
            serverUrl = "http://192.168.1.10:8096",
            userId = "u-1",
            accessToken = "access",
            username = "user",
            refreshToken = "refresh"
        )
        coEvery { authDataStore.clearSession() } throws PreferenceWriteException("Disk full", IOException())

        val result = repository.logout()

        assertTrue(result.isFailure)
        verify(exactly = 0) { clientManager.clearSession() }
        coVerify(exactly = 0) { mockApi.revoke(any()) }
    }

    @Test
    fun `rejection waiting for login does not clear the new session`() = runTest {
        val serverUrl = "http://192.168.1.10:8096"
        val username = "testuser"
        val password = "secret_password"
        val userId = "user-123"
        val oldToken = "old_token"
        val newToken = "new_token"

        val stored = AtomicReference<AuthState>(
            AuthState.Authenticated(
                serverUrl = serverUrl,
                userId = userId,
                username = username,
                accessToken = oldToken,
                serverName = "Adult Hub"
            )
        )
        val loginSaving = CompletableDeferred<Unit>()
        val releaseLogin = CompletableDeferred<Unit>()
        val rejectionRead = CompletableDeferred<Unit>()
        val rejectionArrivedAtLock = CompletableDeferred<Unit>()

        val mutexDelegate = Mutex()
        val signalingMutex = object : Mutex by mutexDelegate {
            override suspend fun lock(owner: Any?) {
                if (mutexDelegate.isLocked) {
                    rejectionArrivedAtLock.complete(Unit)
                }
                mutexDelegate.lock(owner)
            }
        }
        val customRepository = AuthRepository(
            clientManager = clientManager,
            authDataStore = authDataStore,
            settingsDataStore = settingsDataStore,
            sessionMutex = signalingMutex
        )

        every { clientManager.createApi(serverUrl) } returns mockApi
        coEvery { mockApi.login(any()) } returns AuthLoginResponseDto(
            accessToken = newToken,
            refreshToken = "new_refresh",
            user = AuthUserDto(id = userId, username = username)
        )

        coEvery { authDataStore.saveSession(any(), any(), any(), any(), any(), any()) } coAnswers {
            loginSaving.complete(Unit)
            releaseLogin.await()
            stored.set(
                AuthState.Authenticated(
                    serverUrl = firstArg(),
                    userId = secondArg(),
                    username = thirdArg(),
                    accessToken = arg<String>(3),
                    serverName = arg<String>(4),
                    refreshToken = arg<String?>(5)
                )
            )
        }
        coEvery { authDataStore.getAuthState() } coAnswers {
            rejectionRead.complete(Unit)
            stored.get()
        }
        coEvery { authDataStore.clearSessionIfAccessTokenMatches(any()) } coAnswers {
            stored.set(AuthState.NotAuthenticated)
            true
        }
        every { clientManager.clearSessionIfTokenMatches(any()) } returns true

        val loginJob = async(Dispatchers.Default) {
            customRepository.login(serverUrl = serverUrl, username = username, password = password)
        }
        loginSaving.await()

        val rejectionJob = async(Dispatchers.Default) {
            customRepository.handleRejectedSession(oldToken)
        }
        rejectionArrivedAtLock.await()
        assertFalse(rejectionRead.isCompleted)

        releaseLogin.complete(Unit)

        val read = withContext(Dispatchers.Default) {
            withTimeoutOrNull(5_000L) { rejectionRead.await() }
        }
        assertNotNull("Rejection should read session once login lock is released", read)

        assertTrue(loginJob.await().isSuccess)
        assertNull(rejectionJob.await())
        assertEquals(newToken, (stored.get() as AuthState.Authenticated).accessToken)
        coVerify(exactly = 0) { authDataStore.clearSessionIfAccessTokenMatches(any()) }
        coVerify(exactly = 0) { clientManager.clearSessionIfTokenMatches(any()) }
        verify(exactly = 1) {
            clientManager.updateSession(serverUrl, newToken, userId, username, "new_refresh")
        }
    }
}
