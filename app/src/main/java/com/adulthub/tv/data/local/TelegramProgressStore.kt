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
    private val dataStore: DataStore<Preferences> = context.telegramProgressDataStore,
) {
    companion object {
        private val KEY_PROGRESS_MAP = stringPreferencesKey("telegram_progress_map")
        const val MAX_LRU_ENTRIES = 200
    }

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

    val allProgress: Flow<List<TelegramProgressRecord>> = dataStoreFlow.map { prefs ->
        parseMap(prefs[KEY_PROGRESS_MAP]).values
            .sortedByDescending { it.updatedAt }
    }

    suspend fun getProgress(providerVideoId: String): TelegramProgressRecord? {
        val prefs = dataStoreFlow.first()
        return parseMap(prefs[KEY_PROGRESS_MAP])[providerVideoId]
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
        dataStore.edit { prefs ->
            val currentMap = parseMap(prefs[KEY_PROGRESS_MAP]).toMutableMap()
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

            prefs[KEY_PROGRESS_MAP] = json.encodeToString(trimmedMap)
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
        dataStore.edit { prefs ->
            val currentMap = parseMap(prefs[KEY_PROGRESS_MAP]).toMutableMap()
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
            prefs[KEY_PROGRESS_MAP] = json.encodeToString(trimmedMap)
        }
    }

    suspend fun clearProgress(providerVideoId: String) {
        if (providerVideoId.isBlank()) return
        dataStore.edit { prefs ->
            val currentMap = parseMap(prefs[KEY_PROGRESS_MAP]).toMutableMap()
            if (currentMap.remove(providerVideoId) != null) {
                prefs[KEY_PROGRESS_MAP] = json.encodeToString(currentMap)
            }
        }
    }

    open suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_PROGRESS_MAP)
        }
    }

    private fun parseMap(jsonStr: String?): Map<String, TelegramProgressRecord> {
        if (jsonStr.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, TelegramProgressRecord>>(jsonStr)
        }.getOrDefault(emptyMap())
    }
}
