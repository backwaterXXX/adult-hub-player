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

    open suspend fun recordQuery(query: String, accountKey: String? = null) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        // The key is resolved before the write starts, so a session change during
        // the write cannot move the query into the next account. A caller that
        // owns a session passes its own key, so a late write stays with its owner.
        val accountKey = accountKey ?: accountKeys.currentKey()
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

    /** True when data of an install without account isolation waits for an import. */
    open suspend fun hasLegacyData(): Boolean {
        val prefs = dataStoreFlow.first()
        return !prefs[stringPreferencesKey(LEGACY_KEY_SEARCH_QUERIES)].isNullOrBlank()
    }

    /**
     * Imports the queries of an install without account isolation into one account.
     *
     * The import is explicit because the unisolated data has no known owner. The
     * source is removed in the same atomic edit, so it is only dropped when the
     * merged result was stored.
     */
    open suspend fun importLegacyData(accountKey: String): Boolean {
        var imported = false
        dataStore.edit { prefs ->
            val legacy = prefs[stringPreferencesKey(LEGACY_KEY_SEARCH_QUERIES)] ?: return@edit
            val target = queriesKey(accountKey)
            val merged = mergeQueries(
                parseList(prefs[target]),
                parseList(legacy),
            )
            prefs[target] = json.encodeToString(merged)
            prefs.remove(stringPreferencesKey(LEGACY_KEY_SEARCH_QUERIES))
            imported = true
        }
        return imported
    }

    private fun mergeQueries(current: List<String>, legacy: List<String>): List<String> {
        val merged = LinkedHashMap<String, String>()
        for (query in current + legacy) {
            val key = query.lowercase()
            if (!merged.containsKey(key)) merged[key] = query
        }
        return merged.values.take(MAX_SEARCH_QUERIES)
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
