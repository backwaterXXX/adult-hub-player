package com.adulthub.tv.data.repository

import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.AuthDataStore
import com.adulthub.tv.data.local.PreferenceWriteException
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.AuthState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.UserDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import com.adulthub.tv.data.network.ServiceUrlPolicy
import com.adulthub.tv.data.network.ServiceUrlValidation

class AuthRepositoryTest {

    private val clientManager: JellyfinClientManager = mockk(relaxed = true)
    private val authDataStore: AuthDataStore = mockk(relaxed = true)
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val mockApi: ApiClient = mockk(relaxed = true)
    private val mockUserApi: UserApi = mockk()

    private val repository = AuthRepository(
        clientManager = clientManager,
        authDataStore = authDataStore,
        settingsDataStore = settingsDataStore,
    )

    @Test
    fun `login with valid credentials returns Authenticated state and saves session`() = runTest {
        val serverUrl = "http://192.168.1.10:8096"
        val username = "testuser"
        val password = "secret_password"
        val userId = UUID.randomUUID()
        val accessToken = "jellyfin_test_access_token_12345"
        val serverId = "test_server_id"

        coEvery { settingsDataStore.requireAllowedServiceUrl(any(), any()) } coAnswers {
            when (val validation = ServiceUrlPolicy.inspect(firstArg<String>())) {
                is ServiceUrlValidation.Valid -> validation.value
                is ServiceUrlValidation.Invalid -> throw IllegalArgumentException(validation.reason)
            }
        }

        val mockUserDto = mockk<UserDto> {
            every { id } returns userId
            every { name } returns username
        }
        val mockAuthResult = mockk<AuthenticationResult> {
            every { this@mockk.accessToken } returns accessToken
            every { user } returns mockUserDto
            every { this@mockk.serverId } returns serverId
        }
        val mockResponse = mockk<Response<AuthenticationResult>> {
            every { content } returns mockAuthResult
        }

        every { clientManager.createApi(serverUrl) } returns mockApi
        every { mockApi.userApi } returns mockUserApi
        coEvery { mockUserApi.authenticateUserByName(any()) } returns mockResponse

        val result = repository.login(
            serverUrl = serverUrl,
            username = username,
            password = password,
        )

        assertTrue("Expected login to succeed", result.isSuccess)
        val authState = result.getOrThrow()
        assertEquals(serverUrl, authState.serverUrl)
        assertEquals(userId.toString(), authState.userId)
        assertEquals(accessToken, authState.accessToken)
        assertEquals(username, authState.username)
        val expectedServerName = "192.168.1.10:8096"
        assertEquals(expectedServerName, authState.serverName)

        coVerify {
            authDataStore.saveSession(
                serverUrl = serverUrl,
                userId = userId.toString(),
                username = username,
                accessToken = accessToken,
                serverName = expectedServerName,
            )
        }
        coVerify {
            clientManager.updateSession(
                serverUrl = serverUrl,
                accessToken = accessToken,
                userId = userId.toString(),
            )
        }
    }

    @Test
    fun `logout clears the stored session and the active Jellyfin client`() = runTest {
        coEvery { authDataStore.getAuthState() } returns AuthState.NotAuthenticated

        repository.logout()

        coVerify(exactly = 1) { authDataStore.clearSession() }
        coVerify(exactly = 1) { clientManager.clearSession() }
    }

    @Test
    fun `logout clears the local session before revoking the token on the server`() = runTest {
        val session = AuthState.Authenticated(
            serverUrl = "https://jf.example.com",
            userId = "user-1",
            accessToken = "token-1",
            username = "user",
            serverName = "jf.example.com",
        )
        coEvery { authDataStore.getAuthState() } returns session
        every { clientManager.createApi(session.serverUrl, session.accessToken) } returns mockApi

        val result = repository.logout()

        assertTrue(result.isSuccess)
        coVerifyOrder {
            authDataStore.clearSession()
            clientManager.createApi(session.serverUrl, session.accessToken)
        }
        coVerify(exactly = 1) { clientManager.clearSession() }
    }

    @Test
    fun `a failed local sign out does not revoke a token that stays stored`() = runTest {
        val session = AuthState.Authenticated(
            serverUrl = "https://jf.example.com",
            userId = "user-1",
            accessToken = "token-1",
            username = "user",
            serverName = "jf.example.com",
        )
        coEvery { authDataStore.getAuthState() } returns session
        coEvery { authDataStore.clearSession() } throws PreferenceWriteException(
            "The session could not be cleared",
            java.io.IOException("disk full"),
        )

        val result = repository.logout()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { clientManager.createApi(any(), any()) }
        coVerify(exactly = 0) { clientManager.clearSession() }
    }

    @Test
    fun `a rejected token does not clear a session that replaced it`() = runTest {
        coEvery { authDataStore.getAuthState() } returns AuthState.Authenticated(
            serverUrl = "https://jf.example.com",
            userId = "user-1",
            accessToken = "current-token",
            username = "user",
            serverName = "jf.example.com",
        )

        val cleared = repository.handleRejectedSession("older-token")

        assertEquals(null, cleared)
        coVerify(exactly = 0) { authDataStore.clearSession() }
        coVerify(exactly = 0) { authDataStore.clearSessionIfAccessTokenMatches(any()) }
        coVerify(exactly = 0) { clientManager.clearSessionIfTokenMatches(any()) }
    }

    @Test
    fun `a login that replaced the session during cleanup is never deleted`() = runTest {
        coEvery { authDataStore.getAuthState() } returns AuthState.Authenticated(
            serverUrl = "https://jf.example.com",
            userId = "user-1",
            accessToken = "current-token",
            username = "user",
            serverName = "jf.example.com",
        )
        coEvery { authDataStore.clearSessionIfAccessTokenMatches("current-token") } returns false

        val cleared = repository.handleRejectedSession("current-token")

        assertEquals(null, cleared)
        coVerify(exactly = 0) { authDataStore.clearSession() }
        coVerify(exactly = 0) { clientManager.clearSessionIfTokenMatches(any()) }
    }

    @Test
    fun `a rejected active token clears the matching session`() = runTest {
        val session = AuthState.Authenticated(
            serverUrl = "https://jf.example.com",
            userId = "user-1",
            accessToken = "current-token",
            username = "user",
            serverName = "jf.example.com",
        )
        coEvery { authDataStore.getAuthState() } returns session
        coEvery { authDataStore.clearSessionIfAccessTokenMatches("current-token") } returns true
        every { clientManager.clearSessionIfTokenMatches("current-token") } returns true

        val cleared = repository.handleRejectedSession("current-token")

        assertEquals(session, cleared)
        coVerify(exactly = 1) { authDataStore.clearSessionIfAccessTokenMatches("current-token") }
        coVerify(exactly = 1) { clientManager.clearSessionIfTokenMatches("current-token") }
        coVerify(exactly = 0) { authDataStore.clearSession() }
    }

    @Test
    fun `a newer client session stops a stale rejection from reporting a sign-out`() = runTest {
        coEvery { authDataStore.getAuthState() } returns AuthState.Authenticated(
            serverUrl = "https://jf.example.com",
            userId = "user-1",
            accessToken = "current-token",
            username = "user",
            serverName = "jf.example.com",
        )
        coEvery { authDataStore.clearSessionIfAccessTokenMatches("current-token") } returns true
        every { clientManager.clearSessionIfTokenMatches("current-token") } returns false

        val cleared = repository.handleRejectedSession("current-token")

        assertEquals(null, cleared)
    }

    @Test
    fun `restore session requests consent before opening a saved local HTTP server`() = runTest {
        val savedSession = AuthState.Authenticated(
            serverUrl = "http://192.168.1.10:8096",
            userId = "user-1",
            accessToken = "token",
            username = "user",
            serverName = "192.168.1.10:8096",
        )
        coEvery { authDataStore.getAuthState() } returns savedSession
        coEvery { settingsDataStore.isCleartextOriginApproved(savedSession.serverUrl) } returns false

        val result = repository.restoreSession()

        assertEquals(AuthState.HttpConsentRequired(savedSession.serverUrl), result)
        coVerify(exactly = 0) { clientManager.updateSession(any(), any(), any()) }
    }
}
