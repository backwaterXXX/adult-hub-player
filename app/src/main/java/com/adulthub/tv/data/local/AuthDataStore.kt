package com.adulthub.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adulthub.tv.data.model.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

class AuthDataStore(
    private val context: Context,
    private val crypto: KeyStoreCrypto,
) {
    private val encryptedValue = EncryptedPreferenceValue(crypto)

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_SERVER_NAME = stringPreferencesKey("server_name")
        private val KEY_ENCRYPTED_TOKEN = stringPreferencesKey("encrypted_token")
    }

    val authState: Flow<AuthState> = context.authDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> decodeAuthState(preferences).state }

    suspend fun getAuthState(): AuthState {
        val preferences = context.authDataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .first()
        val decoded = decodeAuthState(preferences)
        if (decoded.unreadableToken != null) clearSessionIfTokenMatches(decoded.unreadableToken)
        if (decoded.corruptMetadata != null) clearSessionIfSnapshotMatches(decoded.corruptMetadata)
        return decoded.state
    }

    /** Encrypt existing plaintext session metadata without invalidating older installs. */
    suspend fun migrateSensitiveValues() {
        val snapshot = context.authDataStore.data
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .first()
        val sensitiveValues = listOf(
            KEY_SERVER_URL to snapshot[KEY_SERVER_URL],
            KEY_USER_ID to snapshot[KEY_USER_ID],
            KEY_USERNAME to snapshot[KEY_USERNAME],
            KEY_SERVER_NAME to snapshot[KEY_SERVER_NAME],
        )
        val replacements = sensitiveValues.mapNotNull { (key, oldValue) ->
            if (oldValue.isNullOrBlank() || encryptedValue.isEncrypted(oldValue)) null
            else Triple(key, oldValue, encryptedValue.encode(oldValue))
        }
        if (replacements.isEmpty()) return

        context.authDataStore.edit { preferences ->
            replacements.forEach { (key, oldValue, newValue) ->
                if (preferences[key] == oldValue) preferences[key] = newValue
            }
        }
    }

    private suspend fun decodeAuthState(preferences: Preferences): DecodedAuthState {
        val rawServerUrl = preferences[KEY_SERVER_URL]
        val rawUserId = preferences[KEY_USER_ID]
        val rawUsername = preferences[KEY_USERNAME]
        val rawServerName = preferences[KEY_SERVER_NAME]
        val encryptedToken = preferences[KEY_ENCRYPTED_TOKEN]

        val serverUrl = encryptedValue.decode(rawServerUrl)
        val userId = encryptedValue.decode(rawUserId)
        val decodedUsername = encryptedValue.decode(rawUsername)
        val decodedServerName = encryptedValue.decode(rawServerName)
        val username = decodedUsername ?: ""
        val serverName = decodedServerName ?: ""

        val metadataFailedToDecrypt = listOf(
            rawServerUrl to serverUrl,
            rawUserId to userId,
            rawUsername to decodedUsername,
            rawServerName to decodedServerName,
        ).any { (stored, decoded) -> encryptedValue.isEncrypted(stored) && decoded == null }
        if (metadataFailedToDecrypt) {
            return DecodedAuthState(
                AuthState.NotAuthenticated,
                corruptMetadata = AuthSessionSnapshot(
                    serverUrl = rawServerUrl,
                    userId = rawUserId,
                    username = rawUsername,
                    serverName = rawServerName,
                    encryptedToken = encryptedToken,
                ),
            )
        }

        if (serverUrl.isNullOrBlank() || userId.isNullOrBlank() || encryptedToken.isNullOrBlank()) {
            return DecodedAuthState(AuthState.NotAuthenticated)
        }

        val decryptedToken = crypto.decrypt(encryptedToken)
        if (decryptedToken.isNullOrBlank()) {
            return DecodedAuthState(AuthState.NotAuthenticated, unreadableToken = encryptedToken)
        }
        return DecodedAuthState(
            AuthState.Authenticated(
                serverUrl = serverUrl,
                userId = userId,
                accessToken = decryptedToken,
                username = username,
                serverName = serverName,
            )
        )
    }

    suspend fun saveSession(
        serverUrl: String,
        userId: String,
        username: String,
        accessToken: String,
        serverName: String = "",
    ) {
        val encryptedToken = crypto.encrypt(accessToken)
        val encryptedServerUrl = encryptedValue.encode(serverUrl)
        val encryptedUserId = encryptedValue.encode(userId)
        val encryptedUsername = encryptedValue.encode(username)
        val encryptedServerName = encryptedValue.encode(serverName)
        context.authDataStore.edit { preferences ->
            preferences[KEY_SERVER_URL] = encryptedServerUrl
            preferences[KEY_USER_ID] = encryptedUserId
            preferences[KEY_USERNAME] = encryptedUsername
            preferences[KEY_SERVER_NAME] = encryptedServerName
            preferences[KEY_ENCRYPTED_TOKEN] = encryptedToken
        }
    }

    suspend fun clearSession() {
        context.authDataStore.edit { preferences ->
            preferences.clearAuthSession()
        }
    }

    private suspend fun clearSessionIfTokenMatches(encryptedToken: String) {
        context.authDataStore.edit { preferences ->
            // A new login may have replaced the unreadable token while this value was being decrypted.
            if (preferences[KEY_ENCRYPTED_TOKEN] == encryptedToken) {
                preferences.clearAuthSession()
            }
        }
    }

    private suspend fun clearSessionIfSnapshotMatches(snapshot: AuthSessionSnapshot) {
        context.authDataStore.edit { preferences ->
            if (
                preferences[KEY_SERVER_URL] == snapshot.serverUrl &&
                preferences[KEY_USER_ID] == snapshot.userId &&
                preferences[KEY_USERNAME] == snapshot.username &&
                preferences[KEY_SERVER_NAME] == snapshot.serverName &&
                preferences[KEY_ENCRYPTED_TOKEN] == snapshot.encryptedToken
            ) {
                preferences.clearAuthSession()
            }
        }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.clearAuthSession() {
        remove(KEY_SERVER_URL)
        remove(KEY_USER_ID)
        remove(KEY_USERNAME)
        remove(KEY_SERVER_NAME)
        remove(KEY_ENCRYPTED_TOKEN)
    }

    private data class DecodedAuthState(
        val state: AuthState,
        val unreadableToken: String? = null,
        val corruptMetadata: AuthSessionSnapshot? = null,
    )

    private data class AuthSessionSnapshot(
        val serverUrl: String?,
        val userId: String?,
        val username: String?,
        val serverName: String?,
        val encryptedToken: String?,
    )
}
