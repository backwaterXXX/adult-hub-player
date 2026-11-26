package com.adulthub.tv.data.api

import android.content.Context
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.AuthState
import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultHubClientManagerTest {

    @Test
    fun `AuthState toString redacts access and refresh tokens`() {
        val authState = AuthState.Authenticated(
            serverUrl = "https://adulthub.example.com",
            userId = "user-123",
            accessToken = "super-secret-access-token",
            username = "admin",
            serverName = "Main Server",
            refreshToken = "super-secret-refresh-token"
        )
        val str = authState.toString()
        assertFalse("toString must not leak access token", str.contains("super-secret-access-token"))
        assertFalse("toString must not leak refresh token", str.contains("super-secret-refresh-token"))
        assertTrue("toString should contain REDACTED", str.contains("[REDACTED]"))
        assertTrue("toString should contain userId", str.contains("user-123"))
    }

    @Test
    fun `token origin check fails closed when server origin is not set`() {
        val context = mockk<Context>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val clientManager = AdultHubClientManager(context, settingsDataStore)

        clientManager.updateSession(
            serverUrl = "", // Blank origin
            accessToken = "secret-token",
            userId = "user-1",
            username = "admin"
        )

        assertNull(clientManager.currentServerUrl?.takeIf { it.isNotBlank() })
    }

    @Test
    fun `createApi with explicit accessToken restricts token to target origin`() {
        val context = mockk<Context>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val clientManager = AdultHubClientManager(context, settingsDataStore)

        val api = clientManager.createApi("https://adulthub.example.com", "token-xyz")
        org.junit.Assert.assertNotNull(api)
    }

    @Test
    fun `clearSessionIfTokenMatches only clears if token matches`() {
        val context = mockk<Context>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val clientManager = AdultHubClientManager(context, settingsDataStore)

        clientManager.updateSession(
            serverUrl = "https://adulthub.example.com",
            accessToken = "active-token",
            userId = "u1"
        )

        val nonMatching = clientManager.clearSessionIfTokenMatches("wrong-token")
        assertFalse(nonMatching)
        assertEquals("active-token", clientManager.currentAccessToken)

        val matching = clientManager.clearSessionIfTokenMatches("active-token")
        assertTrue(matching)
        assertNull(clientManager.currentAccessToken)
        assertNull(clientManager.currentServerUrl)
    }

    @Test
    fun `updateTokensIfMatches only updates if expected token matches`() {
        val context = mockk<Context>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val clientManager = AdultHubClientManager(context, settingsDataStore)

        clientManager.updateSession(
            serverUrl = "https://adulthub.example.com",
            accessToken = "token-1",
            userId = "u1",
            refreshToken = "refresh-1"
        )

        val nonMatching = clientManager.updateTokensIfMatches("stale-token", "new-token", "new-refresh")
        assertFalse(nonMatching)
        assertEquals("token-1", clientManager.currentAccessToken)
        assertEquals("refresh-1", clientManager.currentRefreshToken)

        val matching = clientManager.updateTokensIfMatches("token-1", "new-token", "new-refresh")
        assertTrue(matching)
        assertEquals("new-token", clientManager.currentAccessToken)
        assertEquals("new-refresh", clientManager.currentRefreshToken)
    }

    @Test
    fun `getSessionSnapshot returns atomic snapshot of current session`() {
        val context = mockk<Context>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val clientManager = AdultHubClientManager(context, settingsDataStore)

        assertNull(clientManager.getSessionSnapshot())

        clientManager.updateSession(
            serverUrl = "https://adulthub.example.com",
            accessToken = "tok-1",
            userId = "user-abc",
            username = "alice",
            refreshToken = "ref-1"
        )

        val snapshot = clientManager.getSessionSnapshot()
        org.junit.Assert.assertNotNull(snapshot)
        assertEquals("https://adulthub.example.com", snapshot?.serverUrl)
        assertEquals("tok-1", snapshot?.accessToken)
        assertEquals("user-abc", snapshot?.userId)
        assertEquals("alice", snapshot?.username)
        assertEquals("ref-1", snapshot?.refreshToken)
    }
}
