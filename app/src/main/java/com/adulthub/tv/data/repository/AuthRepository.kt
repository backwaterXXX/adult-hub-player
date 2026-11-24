package com.adulthub.tv.data.repository

import com.adulthub.tv.data.api.AdultHubClientManager
import com.adulthub.tv.data.api.AuthLoginRequestDto
import com.adulthub.tv.data.api.AuthRevokeRequestDto
import com.adulthub.tv.data.local.AuthDataStore
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.AuthState
import com.adulthub.tv.data.model.ServerInfo
import com.adulthub.tv.data.network.ServiceUrlPolicy
import com.adulthub.tv.data.network.ServiceUrlValidation
import com.adulthub.tv.diagnostics.AppLog as Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class QuickConnectInitResult(
    val code: String,
    val secret: String
)

private const val TAG = "AuthRepository"
private const val SERVER_SIGN_OUT_TIMEOUT_MS = 3_000L

open class AuthRepository(
    private val clientManager: AdultHubClientManager,
    private val authDataStore: AuthDataStore,
    private val settingsDataStore: SettingsDataStore,
    /**
     * Serializes every change to the stored session together with the in-memory
     * client activation, so cleanup never observes a session that was saved but
     * not yet made active (or the reverse).
     */
    internal val sessionMutex: Mutex = Mutex()
) {
    open val authState: Flow<AuthState> = authDataStore.authState

    /** Tokens that the server rejected while they were the active session. */
    open val sessionRejections: Flow<String> = clientManager.sessionRejections

    suspend fun discoverServer(serverUrl: String): Result<ServerInfo> = withContext(Dispatchers.IO) {
        try {
            val allowedUrl = settingsDataStore.requireAllowedServiceUrl(serverUrl).normalized
            val api = clientManager.createApi(baseUrl = allowedUrl)
            val versionInfo = api.getVersion()
            Result.success(
                ServerInfo(
                    id = "adult-hub",
                    name = "Adult Hub",
                    version = versionInfo.serverVersion,
                    url = allowedUrl
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun discoverServers(): Flow<ServerInfo> = emptyFlow()

    suspend fun login(
        serverUrl: String,
        username: String,
        password: String
    ): Result<AuthState.Authenticated> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = settingsDataStore.requireAllowedServiceUrl(serverUrl).normalized
            val api = clientManager.createApi(baseUrl = normalizedUrl)
            val response = api.login(
                AuthLoginRequestDto(
                    username = username,
                    password = password,
                    deviceName = "Adult Hub TV",
                    deviceId = clientManager.deviceId
                )
            )
            val token = response.accessToken
            val refreshToken = response.refreshToken
            val userId = response.user.id
            val resolvedUsername = response.user.username.ifBlank { username }
            val serverName = "Adult Hub"

            val authenticated = sessionMutex.withLock {
                authDataStore.saveSession(
                    serverUrl = normalizedUrl,
                    userId = userId,
                    username = resolvedUsername,
                    accessToken = token,
                    serverName = serverName,
                    refreshToken = refreshToken
                )

                clientManager.updateSession(
                    serverUrl = normalizedUrl,
                    accessToken = token,
                    userId = userId,
                    username = resolvedUsername,
                    refreshToken = refreshToken
                )

                AuthState.Authenticated(
                    serverUrl = normalizedUrl,
                    userId = userId,
                    accessToken = token,
                    username = resolvedUsername,
                    serverName = serverName,
                    refreshToken = refreshToken
                )
            }
            Result.success(authenticated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun initiateQuickConnect(serverUrl: String): Result<QuickConnectInitResult> =
        Result.failure(UnsupportedOperationException("Pairing is not supported; please use password login"))

    suspend fun checkQuickConnectState(serverUrl: String, secret: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("Pairing is not supported; please use password login"))

    suspend fun authenticateQuickConnect(serverUrl: String, secret: String): Result<AuthState.Authenticated> =
        Result.failure(UnsupportedOperationException("Pairing is not supported; please use password login"))

    suspend fun restoreSession(): AuthState = withContext(Dispatchers.IO) {
        authDataStore.migrateSensitiveValues()
        settingsDataStore.migrateSensitiveValues()
        sessionMutex.withLock {
            val state = authDataStore.getAuthState()
            if (state is AuthState.Authenticated) {
                when (val validation = ServiceUrlPolicy.inspect(state.serverUrl)) {
                    is ServiceUrlValidation.Invalid -> return@withLock AuthState.HttpBlocked(
                        serverUrl = state.serverUrl,
                        reason = validation.reason
                    )

                    is ServiceUrlValidation.Valid -> if (
                        validation.value.usesCleartext &&
                        !settingsDataStore.isCleartextOriginApproved(state.serverUrl)
                    ) {
                        return@withLock AuthState.HttpConsentRequired(state.serverUrl)
                    }
                }
                clientManager.updateSession(
                    serverUrl = settingsDataStore.requireAllowedServiceUrl(state.serverUrl).normalized,
                    accessToken = state.accessToken,
                    userId = state.userId,
                    username = state.username,
                    refreshToken = state.refreshToken
                )
            }
            state
        }
    }

    /**
     * Signs the account out.
     *
     * The stored session is cleared first, so a failed local write never leaves a
     * token that has already been revoked on the server. Only after the local
     * isolation succeeds is the token revoked on the server, with a bounded wait;
     * an unreachable server cannot undo the local sign-out.
     */
    open suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        val session = try {
            sessionMutex.withLock {
                val current = currentSessionForSignOut()
                authDataStore.clearSession()
                clientManager.clearSession()
                current
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Sign out failed because the session could not be cleared")
            return@withContext Result.failure(error)
        }
        revokeSessionOnServer(session)
        Result.success(Unit)
    }

    private suspend fun revokeSessionOnServer(session: AuthState.Authenticated?) {
        val refreshToken = session?.refreshToken ?: return
        try {
            val api = clientManager.createApi(baseUrl = session.serverUrl)
            withTimeoutOrNull(SERVER_SIGN_OUT_TIMEOUT_MS) {
                api.revoke(AuthRevokeRequestDto(refreshToken = refreshToken))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Server-side sign-out failed (${error.javaClass.simpleName})")
        }
    }

    /**
     * Clears the stored session after the server rejected [rejectedAccessToken].
     */
    open suspend fun handleRejectedSession(rejectedAccessToken: String): AuthState.Authenticated? = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            val session = currentSessionForSignOut() ?: return@withLock null
            if (session.accessToken != rejectedAccessToken) return@withLock null
            try {
                if (!authDataStore.clearSessionIfAccessTokenMatches(rejectedAccessToken)) {
                    return@withLock null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "The rejected session could not be cleared")
                return@withLock null
            }
            if (!clientManager.clearSessionIfTokenMatches(rejectedAccessToken)) {
                Log.w(
                    TAG,
                    "Stored session was cleared for rejected token, but client held a different token; resetting client defensively"
                )
                clientManager.clearSession()
                return@withLock null
            }
            session
        }
    }

    private suspend fun currentSessionForSignOut(): AuthState.Authenticated? = try {
        authDataStore.getAuthState() as? AuthState.Authenticated
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w(TAG, "The stored session could not be read (${error.javaClass.simpleName})")
        null
    }
}
