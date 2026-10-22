package com.adulthub.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The stored video matching tasks and the start time of a task.
 *
 * The polling budget measures the life of a task, so the start time must survive
 * every update of the task and it must not be inherited by another task.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhashJobStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private lateinit var dataStore: DataStore<Preferences>

    private fun createDataStore(scope: kotlinx.coroutines.CoroutineScope): DataStore<Preferences> {
        val testFile = tmpFolder.newFile("phash_jobs.preferences_pb")
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { testFile })
    }

    private fun createStore(scope: kotlinx.coroutines.CoroutineScope): PhashJobStore {
        dataStore = createDataStore(scope)
        return PhashJobStore(context, AccountKeyProvider.ANONYMOUS, dataStore)
    }

    @Test
    fun `the start time of a task survives an update of the same task`() = runTest {
        val store = createStore(backgroundScope)

        store.save(StoredPhashJob(videoId = "video-1", jobId = "job-1", state = "queued", startedAt = 5_000L))
        // An update that does not carry the start time keeps the start of the task.
        store.save(StoredPhashJob(videoId = "video-1", jobId = "job-1", state = "processing"))

        assertEquals(5_000L, store.get("video-1")?.startedAt)
    }

    @Test
    fun `a new task for the same video gets its own start time`() = runTest {
        val store = createStore(backgroundScope)

        store.save(StoredPhashJob(videoId = "video-1", jobId = "job-1", state = "queued", startedAt = 5_000L))
        store.save(StoredPhashJob(videoId = "video-1", jobId = "job-2", state = "queued", startedAt = 9_000L))

        assertEquals(9_000L, store.get("video-1")?.startedAt)
    }

    @Test
    fun `a record of an earlier version uses the time of its last change`() = runTest {
        val store = createStore(backgroundScope)

        // A record as it was written before the start time existed.
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("jobs__anonymous")] =
                """[{"videoId":"video-1","jobId":"job-1","state":"processing","updatedAt":4242}]"""
        }

        val record = store.get("video-1")
        assertNotNull(record)
        assertEquals(4242L, record?.updatedAt)
        assertEquals(4242L, record?.startedAt)
    }
}
