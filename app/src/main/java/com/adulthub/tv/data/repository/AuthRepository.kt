package com.adulthub.tv.data.repository

import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.AuthDataStore
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.AuthState
import com.adulthub.tv.data.model.ServerInfo
import com.adulthub.tv.data.network.ServiceUrlPolicy
import com.adulthub.tv.data.network.ServiceUrlValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.adulthub.tv.diagnostics.AppLog as Log
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.authenticateWithQuickConnect
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi

data class QuickConnectInitResult(
    val code: String,
    val secret: String,
)

private const val TAG = "AuthRepository"

open class AuthRepository(
    private val clientManager: JellyfinClientManager,
    private val authDataStore: AuthDataStore,
    private val settingsDataStore: SettingsDataStore,
) {
    open val authState: Flow<AuthState> = authDataStore.authState

    suspend fun discoverServer(serverUrl: String): Result<ServerInfo> = withContext(Dispatchers.IO) {
        try {
            val allowedUrl = settingsDataStore.requireAllowedServiceUrl(serverUrl).normalized
            val api = clientManager.createApi(baseUrl = allowedUrl)
            val response = api.systemApi.getPublicSystemInfo()
            val info = response.content
            Result.success(
                ServerInfo(
                    id = info.id.orEmpty(),
                    name = info.serverName.orEmpty(),
                    version = info.version.orEmpty(),
                    url = allowedUrl,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun discoverServers(): Flow<ServerInfo> = clientManager.discovery
        .discoverLocalServers()
        .map { info ->
            ServerInfo(
                id = info.id.orEmpty(),
                name = info.name.orEmpty(),
                version = "",
                url = info.address.orEmpty(),
            )
        }

    suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
    ): Result<AuthState.Authenticated> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = settingsDataStore.requireAllowedServiceUrl(serverUrl).normalized
            val api = clientManager.createApi(baseUrl = normalizedUrl)
            val response = api.userApi.authenticateUserByName(
                org.jellyfin.sdk.model.api.AuthenticateUserByName(
                    username = username,
                    pw = password,
                )
            )
            val authResult = response.content
            val token = authResult.accessToken
                ?: return@withContext Result.failure(IllegalStateException("No access token returned"))
            val user = authResult.user
                ?: return@withContext Result.failure(IllegalStateException("No user returned"))
            val userId = user.id.toString()
            val serverName = normalizedUrl.substringAfter("://").substringBefore('/')

            authDataStore.saveSession(
                serverUrl = normalizedUrl,
                userId = userId,
                username = username,
                accessToken = token,
                serverName = serverName,
            )

            clientManager.updateSession(
                serverUrl = normalizedUrl,
                accessToken = token,
                userId = userId,
            )

            val authenticated = AuthState.Authenticated(
                serverUrl = normalizedUrl,
                userId = userId,
                accessToken = token,
                username = username,
                serverName = serverName,
            )
            Result.success(authenticated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun initiateQuickConnect(serverUrl: String): Result<QuickConnectInitResult> = withContext(Dispatchers.IO) {
        try {
            val allowedUrl = settingsDataStore.requireAllowedServiceUrl(serverUrl).normalized
            val api = clientManager.createApi(baseUrl = allowedUrl)
            val response = api.quickConnectApi.initiateQuickConnect()
            val content = response.content
            Result.success(
                QuickConnectInitResult(
                    code = content.code,
                    secret = content.secret,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkQuickConnectState(serverUrl: String, secret: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val allowedUrl = settingsDataStore.requireAllowedServiceUrl(serverUrl).normalized
            val api = clientManager.createApi(baseUrl = allowedUrl)
            val response = api.quickConnectApi.getQuickConnectState(secret = secret)
            Result.success(response.content.authenticated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun authenticateQuickConnect(serverUrl: String, secret: String): Result<AuthState.Authenticated> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = settingsDataStore.requireAllowedServiceUrl(serverUrl).normalized
            val api = clientManager.createApi(baseUrl = normalizedUrl)
            val response = api.userApi.authenticateWithQuickConnect(
                org.jellyfin.sdk.model.api.QuickConnectDto(secret = secret)
            )
            val authResult = response.content
            val token = authResult.accessToken
                ?: return@withContext Result.failure(IllegalStateException("No access token returned"))
            val user = authResult.user
                ?: return@withContext Result.failure(IllegalStateException("No user returned"))
            val userId = user.id.toString()
            val username = user.name.orEmpty()
            val serverName = normalizedUrl.substringAfter("://").substringBefore('/')

            authDataStore.saveSession(
                serverUrl = normalizedUrl,
                userId = userId,
                username = username,
                accessToken = token,
                serverName = serverName,
            )

            clientManager.updateSession(
                serverUrl = normalizedUrl,
                accessToken = token,
                userId = userId,
            )

            Result.success(
                AuthState.Authenticated(
                    serverUrl = normalizedUrl,
                    userId = userId,
                    accessToken = token,
                    username = username,
                    serverName = serverName,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreSession(): AuthState = withContext(Dispatchers.IO) {
        authDataStore.migrateSensitiveValues()
        settingsDataStore.migrateSensitiveValues()
        val state = authDataStore.getAuthState()
        if (state is AuthState.Authenticated) {
            when (val validation = ServiceUrlPolicy.inspect(state.serverUrl)) {
                is ServiceUrlValidation.Invalid -> return@withContext AuthState.HttpBlocked(
                    serverUrl = state.serverUrl,
                    reason = validation.reason,
                )
                is ServiceUrlValidation.Valid -> if (
                    validation.value.usesCleartext &&
                    !settingsDataStore.isCleartextOriginApproved(state.serverUrl)
                ) {
                    return@withContext AuthState.HttpConsentRequired(state.serverUrl)
                }
            }
            clientManager.updateSession(
                serverUrl = settingsDataStore.requireAllowedServiceUrl(state.serverUrl).normalized,
                accessToken = state.accessToken,
                userId = state.userId,
            )
        }
        state
    }

    /**
     * Signs the account out.
     *
     * The stored session is cleared first. When that write fails the session
     * stays valid, so the client keeps its token and the caller is told that
     * the sign-out did not happen. The failure is never an unhandled exception.
     */
    open suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            authDataStore.clearSession()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Sign out failed because the session could not be cleared")
            return@withContext Result.failure(error)
        }
        clientManager.clearSession()
        Result.success(Unit)
    }
}
