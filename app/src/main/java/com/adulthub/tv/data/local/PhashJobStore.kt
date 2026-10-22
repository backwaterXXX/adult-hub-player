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

    suspend fun get(videoId: String, accountKey: String? = null): StoredPhashJob? =
        readJobs(accountKey).firstOrNull { it.videoId == videoId }

    suspend fun getAll(accountKey: String? = null): List<StoredPhashJob> = readJobs(accountKey)

    suspend fun save(record: StoredPhashJob, accountKey: String? = null) {
        // The key is resolved before the write starts, so a session change during
        // the write cannot move the job into the next account. A caller that owns
        // a session passes its own key, so a late write stays with its owner.
        val accountKey = accountKey ?: accountKeys.currentKey()
        dataStore.edit { prefs ->
            val records = decode(prefs[jobsKey(accountKey)]).associateBy { it.videoId }.toMutableMap()
            records[record.videoId] = record.copy(updatedAt = System.currentTimeMillis())
            val trimmed = records.values.sortedByDescending { it.updatedAt }.take(MAX_JOBS)
            prefs[jobsKey(accountKey)] = json.encodeToString(trimmed)
        }
    }

    suspend fun remove(videoId: String, accountKey: String? = null) {
        val accountKey = accountKey ?: accountKeys.currentKey()
        dataStore.edit { prefs ->
            val remaining = decode(prefs[jobsKey(accountKey)]).filterNot { it.videoId == videoId }
            if (remaining.isEmpty()) prefs.remove(jobsKey(accountKey))
            else prefs[jobsKey(accountKey)] = json.encodeToString(remaining)
        }
    }

    /** True when data of an install without account isolation waits for an import. */
    suspend fun hasLegacyData(): Boolean {
        val prefs = preferences.first()
        return !prefs[stringPreferencesKey(LEGACY_KEY_JOBS)].isNullOrBlank()
    }

    /**
     * Imports the persisted jobs of an install without account isolation into one
     * account.
     *
     * The import is explicit because the unisolated data has no known owner. The
     * source is removed in the same atomic edit, so it is only dropped when the
     * merged result was stored.
     */
    suspend fun importLegacyData(accountKey: String): Boolean {
        var imported = false
        dataStore.edit { prefs ->
            val legacy = prefs[stringPreferencesKey(LEGACY_KEY_JOBS)] ?: return@edit
            val target = jobsKey(accountKey)
            val merged = mergeJobs(
                decode(prefs[target]),
                decode(legacy),
            )
            prefs[target] = json.encodeToString(merged)
            prefs.remove(stringPreferencesKey(LEGACY_KEY_JOBS))
            imported = true
        }
        return imported
    }

    private fun mergeJobs(current: List<StoredPhashJob>, legacy: List<StoredPhashJob>): List<StoredPhashJob> {
        val merged = LinkedHashMap<String, StoredPhashJob>()
        for (record in current + legacy) {
            val existing = merged[record.videoId]
            if (existing == null || record.updatedAt >= existing.updatedAt) {
                merged[record.videoId] = record
            }
        }
        return merged.values.sortedByDescending { it.updatedAt }.take(MAX_JOBS)
    }

    private suspend fun readJobs(accountKey: String? = null): List<StoredPhashJob> {
        val key = accountKey ?: accountKeys.currentKey()
        return decode(preferences.first()[jobsKey(key)])
    }

    private fun jobsKey(accountKey: String) = stringPreferencesKey("${LEGACY_KEY_JOBS}__$accountKey")

    private fun decode(raw: String?): List<StoredPhashJob> =
        raw?.let { runCatching { json.decodeFromString<List<StoredPhashJob>>(it) }.getOrNull() }.orEmpty()
}
