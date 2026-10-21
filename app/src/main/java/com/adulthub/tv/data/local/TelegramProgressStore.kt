package com.adulthub.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.telegramProgressDataStore: DataStore<Preferences> by preferencesDataStore(name = "telegram_progress_prefs")

@Serializable
data class TelegramProgressRecord(
    val providerVideoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val title: String,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    val isPlayed: Boolean = false,
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

open class TelegramProgressStore(
    private val context: Context,
    accountKeyProvider: AccountKeyProvider = AccountKeyProvider.ANONYMOUS,
    private val dataStore: DataStore<Preferences> = SafePreferencesDataStore(context.telegramProgressDataStore),
) {
    companion object {
        /** Unscoped key written by versions that did not isolate accounts yet. */
        const val LEGACY_KEY_PROGRESS_MAP = "telegram_progress_map"
        const val MAX_LRU_ENTRIES = 200
    }

    private val accountKeys: AccountKeyProvider = accountKeyProvider
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dataStoreFlow: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    val allProgress: Flow<List<TelegramProgressRecord>> =
        combine(dataStoreFlow, accountKeys.accountKey) { prefs, accountKey ->
            parseMap(prefs[progressKey(accountKey)]).values
                .sortedByDescending { it.updatedAt }
        }

    suspend fun getProgress(providerVideoId: String): TelegramProgressRecord? {
        val accountKey = accountKeys.currentKey()
        val prefs = dataStoreFlow.first()
        return parseMap(prefs[progressKey(accountKey)])[providerVideoId]
    }

    suspend fun saveProgress(
        providerVideoId: String,
        positionMs: Long,
        durationMs: Long,
        title: String,
        thumbnailUrl: String? = null,
        channelId: String? = null,
        isPlayed: Boolean = false,
    ) {
        if (providerVideoId.isBlank()) return
        // The key is resolved before the write starts, so a session change during
        // the write cannot move the progress into the next account.
        val accountKey = accountKeys.currentKey()
        dataStore.edit { prefs ->
            val currentMap = parseMap(prefs[progressKey(accountKey)]).toMutableMap()
            val existing = currentMap[providerVideoId]
            val record = TelegramProgressRecord(
                providerVideoId = providerVideoId,
                positionMs = positionMs,
                durationMs = if (durationMs > 0L) durationMs else existing?.durationMs ?: 0L,
                updatedAt = System.currentTimeMillis(),
                title = title.ifBlank { existing?.title ?: "Telegram Video" },
                thumbnailUrl = thumbnailUrl ?: existing?.thumbnailUrl,
                channelId = channelId ?: existing?.channelId,
                isPlayed = isPlayed,
            )
            currentMap[providerVideoId] = record

            // Trim to MAX_LRU_ENTRIES if needed
            val trimmedMap = if (currentMap.size > MAX_LRU_ENTRIES) {
                currentMap.entries
                    .sortedByDescending { it.value.updatedAt }
                    .take(MAX_LRU_ENTRIES)
                    .associate { it.key to it.value }
            } else {
                currentMap
            }

            prefs[progressKey(accountKey)] = json.encodeToString(trimmedMap)
        }
    }

    suspend fun markPlayed(
        providerVideoId: String,
        isPlayed: Boolean,
        title: String? = null,
        thumbnailUrl: String? = null,
        channelId: String? = null,
    ) {
        if (providerVideoId.isBlank()) return
        val accountKey = accountKeys.currentKey()
        dataStore.edit { prefs ->
            val currentMap = parseMap(prefs[progressKey(accountKey)]).toMutableMap()
            val existing = currentMap[providerVideoId]
            val record = TelegramProgressRecord(
                providerVideoId = providerVideoId,
                positionMs = if (isPlayed) 0L else existing?.positionMs ?: 0L,
                durationMs = existing?.durationMs ?: 0L,
                updatedAt = System.currentTimeMillis(),
                title = title ?: existing?.title ?: "Telegram Video",
                thumbnailUrl = thumbnailUrl ?: existing?.thumbnailUrl,
                channelId = channelId ?: existing?.channelId,
                isPlayed = isPlayed,
            )
            currentMap[providerVideoId] = record
            val trimmedMap = if (currentMap.size > MAX_LRU_ENTRIES) {
                currentMap.entries
                    .sortedByDescending { it.value.updatedAt }
                    .take(MAX_LRU_ENTRIES)
                    .associate { it.key to it.value }
            } else {
                currentMap
            }
            prefs[progressKey(accountKey)] = json.encodeToString(trimmedMap)
        }
    }

    suspend fun clearProgress(providerVideoId: String) {
        if (providerVideoId.isBlank()) return
        val accountKey = accountKeys.currentKey()
        dataStore.edit { prefs ->
            val currentMap = parseMap(prefs[progressKey(accountKey)]).toMutableMap()
            if (currentMap.remove(providerVideoId) != null) {
                prefs[progressKey(accountKey)] = json.encodeToString(currentMap)
            }
        }
    }

    open suspend fun clearAll() {
        val accountKey = accountKeys.currentKey()
        dataStore.edit { prefs ->
            prefs.remove(progressKey(accountKey))
        }
    }

    /**
     * Moves the progress of installs without account isolation to the account
     * that is signed in now.
     */
    open suspend fun migrateLegacyProgress() {
        val accountKey = accountKeys.currentKey()
        dataStore.edit { prefs ->
            val legacy = prefs[stringPreferencesKey(LEGACY_KEY_PROGRESS_MAP)] ?: return@edit
            val target = progressKey(accountKey)
            if (prefs[target] == null) {
                prefs[target] = legacy
            }
            prefs.remove(stringPreferencesKey(LEGACY_KEY_PROGRESS_MAP))
        }
    }

    private fun progressKey(accountKey: String) =
        stringPreferencesKey("${LEGACY_KEY_PROGRESS_MAP}__$accountKey")

    private fun parseMap(jsonStr: String?): Map<String, TelegramProgressRecord> {
        if (jsonStr.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, TelegramProgressRecord>>(jsonStr)
        }.getOrDefault(emptyMap())
    }
}
