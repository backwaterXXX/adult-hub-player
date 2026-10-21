package com.adulthub.tv.data.local

import com.adulthub.tv.data.model.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

/**
 * Supplies the key that namespaces user-scoped data.
 *
 * Watch history, playback progress, search history, persisted resolver jobs and
 * the selected library belong to one account. Namespacing them by account keeps
 * a shared TV from showing the previous user's data to the next one, and keeps
 * the data of a signed-out account out of reach until that account signs in
 * again.
 */
interface AccountKeyProvider {
    /** Emits the key of the signed-in account, or [AccountScope.ANONYMOUS_KEY]. */
    val accountKey: Flow<String>

    /** The key of the account that is signed in right now. */
    suspend fun currentKey(): String

    companion object {
        /** Provider used when no session is bound, for example in unit tests. */
        val ANONYMOUS: AccountKeyProvider = object : AccountKeyProvider {
            override val accountKey: Flow<String> = flowOf(AccountScope.ANONYMOUS_KEY)
            override suspend fun currentKey(): String = AccountScope.ANONYMOUS_KEY
        }
    }
}

/**
 * Derives the account key from the persisted Jellyfin session.
 *
 * The key is a digest of the server URL and the user id: it is stable across
 * restarts, it cannot be turned back into a URL or a user name, and it leaves
 * no personally identifiable information in the preferences file.
 */
class AccountScope(
    private val authDataStore: AuthDataStore,
) : AccountKeyProvider {
    override val accountKey: Flow<String> = authDataStore.authState
        .map { state -> keyOf(state) }
        .distinctUntilChanged()

    override suspend fun currentKey(): String = accountKey.first()

    companion object {
        /** Namespace used while no account is signed in. */
        const val ANONYMOUS_KEY = "anonymous"

        fun keyOf(state: AuthState): String = when (state) {
            is AuthState.Authenticated -> digest(state.serverUrl, state.userId)
            else -> ANONYMOUS_KEY
        }

        /** Stable, non-reversible identifier of one server and user pair. */
        fun digest(serverUrl: String, userId: String): String {
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest("$serverUrl|$userId".toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }.take(16)
        }
    }
}
