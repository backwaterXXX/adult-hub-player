package com.adulthub.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.phashJobDataStore: DataStore<Preferences> by preferencesDataStore(name = "phash_jobs_prefs")

@Serializable
data class StoredPhashJob(
    val videoId: String,
    val jobId: String? = null,
    val state: String,
    val errorCode: String? = null,
    val durationUs: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Persists resolver job IDs so polling can resume after navigation or process death. */
class PhashJobStore(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.phashJobDataStore,
) {
    companion object {
        private val KEY_JOBS = stringPreferencesKey("jobs")
        const val MAX_JOBS = 200
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val preferences = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    suspend fun get(videoId: String): StoredPhashJob? = readJobs().firstOrNull { it.videoId == videoId }

    suspend fun getAll(): List<StoredPhashJob> = readJobs()

    suspend fun save(record: StoredPhashJob) {
        dataStore.edit { prefs ->
            val records = decode(prefs[KEY_JOBS]).associateBy { it.videoId }.toMutableMap()
            records[record.videoId] = record.copy(updatedAt = System.currentTimeMillis())
            val trimmed = records.values.sortedByDescending { it.updatedAt }.take(MAX_JOBS)
            prefs[KEY_JOBS] = json.encodeToString(trimmed)
        }
    }

    suspend fun remove(videoId: String) {
        dataStore.edit { prefs ->
            val remaining = decode(prefs[KEY_JOBS]).filterNot { it.videoId == videoId }
            if (remaining.isEmpty()) prefs.remove(KEY_JOBS)
            else prefs[KEY_JOBS] = json.encodeToString(remaining)
        }
    }

    private suspend fun readJobs(): List<StoredPhashJob> =
        decode(preferences.first()[KEY_JOBS])

    private fun decode(raw: String?): List<StoredPhashJob> =
        raw?.let { runCatching { json.decodeFromString<List<StoredPhashJob>>(it) }.getOrNull() }.orEmpty()
}
