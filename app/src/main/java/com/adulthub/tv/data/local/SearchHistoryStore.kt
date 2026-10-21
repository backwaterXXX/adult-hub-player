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
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history_prefs")

open class SearchHistoryStore(
    private val context: Context,
    accountKeyProvider: AccountKeyProvider = AccountKeyProvider.ANONYMOUS,
    private val dataStore: DataStore<Preferences> = SafePreferencesDataStore(context.searchHistoryDataStore),
) {
    companion object {
        /** Unscoped key written by versions that did not isolate accounts yet. */
        const val LEGACY_KEY_SEARCH_QUERIES = "search_queries"
        const val MAX_SEARCH_QUERIES = 20
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

    open val queries: Flow<List<String>> =
        combine(dataStoreFlow, accountKeys.accountKey) { prefs, accountKey ->
            parseList(prefs[queriesKey(accountKey)])
        }

    open suspend fun recordQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        // The key is resolved before the write starts, so a session change during
        // the write cannot move the query into the next account.
        val accountKey = accountKeys.currentKey()
        dataStore.edit { prefs ->
            val list = parseList(prefs[queriesKey(accountKey)]).toMutableList()
            list.removeAll { it.equals(trimmed, ignoreCase = true) }
            list.add(0, trimmed)
            val limited = list.take(MAX_SEARCH_QUERIES)
            prefs[queriesKey(accountKey)] = json.encodeToString(limited)
        }
    }

    open suspend fun clearHistory() {
        val accountKey = accountKeys.currentKey()
        dataStore.edit { prefs ->
            prefs.remove(queriesKey(accountKey))
        }
    }

    /**
     * Moves the queries of installs without account isolation to the account
     * that is signed in now.
     */
    open suspend fun migrateLegacyHistory() {
        val accountKey = accountKeys.currentKey()
        dataStore.edit { prefs ->
            val legacy = prefs[stringPreferencesKey(LEGACY_KEY_SEARCH_QUERIES)] ?: return@edit
            val target = queriesKey(accountKey)
            if (prefs[target] == null) {
                prefs[target] = legacy
            }
            prefs.remove(stringPreferencesKey(LEGACY_KEY_SEARCH_QUERIES))
        }
    }

    private fun queriesKey(accountKey: String) =
        stringPreferencesKey("${LEGACY_KEY_SEARCH_QUERIES}__$accountKey")

    private fun parseList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<String>>(raw)
        }.getOrDefault(emptyList())
    }
}
