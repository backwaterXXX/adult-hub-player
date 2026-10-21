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
    accountKeyProvider: AccountKeyProvider = AccountKeyProvider.ANONYMOUS,
    private val dataStore: DataStore<Preferences> = SafePreferencesDataStore(context.phashJobDataStore),
) {
    companion object {
        /** Unscoped key written by versions that did not isolate accounts yet. */
        const val LEGACY_KEY_JOBS = "jobs"
        const val MAX_JOBS = 200
    }

    private val accountKeys: AccountKeyProvider = accountKeyProvider
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val preferences = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    suspend fun get(videoId: String): StoredPhashJob? = readJobs().firstOrNull { it.videoId == videoId }

    suspend fun getAll(): List<StoredPhashJob> = readJobs()

    suspend fun save(record: StoredPhashJob) {
        // The key is resolved before the write starts, so a session change during
        // the write cannot move the job into the next account.
        val accountKey = accountKeys.currentKey()
        dataStore.edit { prefs ->
            val records = decode(prefs[jobsKey(accountKey)]).associateBy { it.videoId }.toMutableMap()
            records[record.videoId] = record.copy(updatedAt = System.currentTimeMillis())
            val trimmed = records.values.sortedByDescending { it.updatedAt }.take(MAX_JOBS)
            prefs[jobsKey(accountKey)] = json.encodeToString(trimmed)
        }
    }

    suspend fun remove(videoId: String) {
        val accountKey = accountKeys.currentKey()
        dataStore.edit { prefs ->
            val remaining = decode(prefs[jobsKey(accountKey)]).filterNot { it.videoId == videoId }
            if (remaining.isEmpty()) prefs.remove(jobsKey(accountKey))
            else prefs[jobsKey(accountKey)] = json.encodeToString(remaining)
        }
    }

    /**
     * Moves the persisted jobs of installs without account isolation to the
     * account that is signed in now.
     */
    suspend fun migrateLegacyJobs() {
        val accountKey = accountKeys.currentKey()
        dataStore.edit { prefs ->
            val legacy = prefs[stringPreferencesKey(LEGACY_KEY_JOBS)] ?: return@edit
            val target = jobsKey(accountKey)
            if (prefs[target] == null) {
                prefs[target] = legacy
            }
            prefs.remove(stringPreferencesKey(LEGACY_KEY_JOBS))
        }
    }

    private suspend fun readJobs(): List<StoredPhashJob> {
        val accountKey = accountKeys.currentKey()
        return decode(preferences.first()[jobsKey(accountKey)])
    }

    private fun jobsKey(accountKey: String) = stringPreferencesKey("${LEGACY_KEY_JOBS}__$accountKey")

    private fun decode(raw: String?): List<StoredPhashJob> =
        raw?.let { runCatching { json.decodeFromString<List<StoredPhashJob>>(it) }.getOrNull() }.orEmpty()
}
