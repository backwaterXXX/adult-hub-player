package com.adulthub.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adulthub.tv.data.model.TvItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.watchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "watch_history_prefs")

@Serializable
data class WatchHistoryRecord(
    val itemId: String,
    val title: String,
    val watchedAt: Long,
    val overview: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val runtimeMinutes: Int? = null,
    val runtimeTicks: Long? = null,
    val providerVideoId: String? = null,
    val isPlayed: Boolean = false,
    val progressFraction: Float = 0f,
    val genres: List<String> = emptyList(),
) {
    fun toTvItem(): TvItem {
        return TvItem(
            id = itemId,
            title = title,
            overview = overview,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            runtimeTicks = runtimeTicks,
            providerVideoId = providerVideoId,
            isPlayed = isPlayed,
            playbackPositionTicks = if (runtimeTicks != null && progressFraction > 0f) {
                (runtimeTicks * progressFraction).toLong()
            } else 0L,
            genres = genres,
        )
    }
}

open class WatchHistoryStore(
    private val context: Context,
    accountKeyProvider: AccountKeyProvider = AccountKeyProvider.ANONYMOUS,
    private val dataStore: DataStore<Preferences> = SafePreferencesDataStore(context.watchHistoryDataStore),
) {
    companion object {
        /** Unscoped key written by versions that did not isolate accounts yet. */
        const val LEGACY_KEY_HISTORY_LIST = "watch_history_list"
        const val MAX_HISTORY_ENTRIES = 50
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

    val history: Flow<List<WatchHistoryRecord>> =
        combine(dataStoreFlow, accountKeys.accountKey) { prefs, accountKey ->
            parseList(prefs[historyKey(accountKey)])
        }

    suspend fun recordWatch(
        item: TvItem,
        watchedAt: Long = System.currentTimeMillis(),
    ) {
        if (item.id.isBlank()) return
        // The key is resolved before the write starts, so a session change during
        // the write cannot move the record into the next account.
        val accountKey = accountKeys.currentKey()
        dataStore.edit { prefs ->
            val currentList = parseList(prefs[historyKey(accountKey)]).toMutableList()
            // Deduplicate: remove any existing entry for this item
            currentList.removeAll { it.itemId == item.id || (item.providerVideoId != null && it.providerVideoId == item.providerVideoId) }

            val newRecord = WatchHistoryRecord(
                itemId = item.id,
                title = item.title.ifBlank { "Untitled Video" },
                watchedAt = watchedAt,
                overview = item.overview,
                posterUrl = item.posterUrl,
                backdropUrl = item.backdropUrl,
                runtimeMinutes = item.runtimeMinutes,
                runtimeTicks = item.runtimeTicks,
                providerVideoId = item.providerVideoId,
                isPlayed = item.isPlayed,
                progressFraction = item.progressFraction,
                genres = item.genres,
            )

            // Insert at the front (most recent)
            currentList.add(0, newRecord)

            // Cap at MAX_HISTORY_ENTRIES
            val trimmed = if (currentList.size > MAX_HISTORY_ENTRIES) {
                currentList.take(MAX_HISTORY_ENTRIES)
            } else {
                currentList
            }

            prefs[historyKey(accountKey)] = json.encodeToString(trimmed)
        }
    }

    open suspend fun clearHistory() {
        val accountKey = accountKeys.currentKey()
        dataStore.edit { prefs ->
            prefs.remove(historyKey(accountKey))
        }
    }

    /**
     * Moves the history of installs without account isolation to the account
     * that is signed in now, so an update does not silently empty the history.
     */
    open suspend fun migrateLegacyHistory() {
        val accountKey = accountKeys.currentKey()
        dataStore.edit { prefs ->
            val legacy = prefs[stringPreferencesKey(LEGACY_KEY_HISTORY_LIST)] ?: return@edit
            val target = historyKey(accountKey)
            if (prefs[target] == null) {
                prefs[target] = legacy
            }
            prefs.remove(stringPreferencesKey(LEGACY_KEY_HISTORY_LIST))
        }
    }

    private fun historyKey(accountKey: String) =
        stringPreferencesKey("${LEGACY_KEY_HISTORY_LIST}__$accountKey")

    private fun parseList(jsonStr: String?): List<WatchHistoryRecord> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<WatchHistoryRecord>>(jsonStr)
        }.getOrDefault(emptyList())
    }
}
