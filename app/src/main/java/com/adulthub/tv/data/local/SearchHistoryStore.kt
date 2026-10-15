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
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history_prefs")

open class SearchHistoryStore(
    private val context: Context,
    private val dataStore: DataStore<Preferences> = context.searchHistoryDataStore,
) {
    companion object {
        private val KEY_SEARCH_QUERIES = stringPreferencesKey("search_queries")
        const val MAX_SEARCH_QUERIES = 20
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

    open val queries: Flow<List<String>> = dataStoreFlow.map { prefs ->
        parseList(prefs[KEY_SEARCH_QUERIES])
    }

    open suspend fun recordQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        dataStore.edit { prefs ->
            val list = parseList(prefs[KEY_SEARCH_QUERIES]).toMutableList()
            list.removeAll { it.equals(trimmed, ignoreCase = true) }
            list.add(0, trimmed)
            val limited = list.take(MAX_SEARCH_QUERIES)
            prefs[KEY_SEARCH_QUERIES] = json.encodeToString(limited)
        }
    }

    open suspend fun clearHistory() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_SEARCH_QUERIES)
        }
    }

    private fun parseList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<String>>(raw)
        }.getOrDefault(emptyList())
    }
}
