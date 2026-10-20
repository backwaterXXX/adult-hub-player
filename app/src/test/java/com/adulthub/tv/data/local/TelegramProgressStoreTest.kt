package com.adulthub.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TelegramProgressStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val context = mockk<Context>(relaxed = true)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: TelegramProgressStore

    @Before
    fun setUp() {
        val testFile = tmpFolder.newFile("test_telegram_progress.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile },
        )
        store = TelegramProgressStore(
            context = context,
            dataStore = dataStore,
        )
    }

    @Test
    fun `saveProgress and getProgress roundtrip`() = runTest(timeout = 120.seconds) {
        assertNull(store.getProgress("tg_123"))

        store.saveProgress(
            providerVideoId = "tg_123",
            positionMs = 60_000L,
            durationMs = 120_000L,
            title = "Test Video 1",
            thumbnailUrl = "https://thumb.url/1.jpg",
            channelId = "chan_1",
            isPlayed = false,
        )

        val record = store.getProgress("tg_123")
        assertNotNull(record)
        assertEquals("tg_123", record?.providerVideoId)
        assertEquals(60_000L, record?.positionMs)
        assertEquals(120_000L, record?.durationMs)
        assertEquals("Test Video 1", record?.title)
        assertEquals("https://thumb.url/1.jpg", record?.thumbnailUrl)
        assertEquals("chan_1", record?.channelId)
        assertFalse(record!!.isPlayed)
        assertEquals(0.5f, record.progressFraction, 0.001f)
    }

    @Test
    fun `markPlayed updates isPlayed flag and clears position`() = runTest(timeout = 120.seconds) {
        store.saveProgress(
            providerVideoId = "tg_456",
            positionMs = 50_000L,
            durationMs = 100_000L,
            title = "Video 2",
            isPlayed = false,
        )

        store.markPlayed("tg_456", isPlayed = true)

        val updated = store.getProgress("tg_456")
        assertNotNull(updated)
        assertTrue(updated!!.isPlayed)
        assertEquals(0L, updated.positionMs)

        store.markPlayed("tg_456", isPlayed = false)
        val unplayed = store.getProgress("tg_456")
        assertNotNull(unplayed)
        assertFalse(unplayed!!.isPlayed)
    }

    @Test
    fun `clearProgress removes specific video`() = runTest(timeout = 120.seconds) {
        store.saveProgress("tg_1", 10_000L, 60_000L, "Title 1")
        store.saveProgress("tg_2", 20_000L, 60_000L, "Title 2")

        assertEquals(2, store.allProgress.first().size)

        store.clearProgress("tg_1")
        assertNull(store.getProgress("tg_1"))
        assertNotNull(store.getProgress("tg_2"))
        assertEquals(1, store.allProgress.first().size)
    }

    @Test
    fun `clearAll removes all records`() = runTest(timeout = 120.seconds) {
        store.saveProgress("tg_1", 10_000L, 60_000L, "Title 1")
        store.saveProgress("tg_2", 20_000L, 60_000L, "Title 2")

        store.clearAll()
        assertTrue(store.allProgress.first().isEmpty())
    }

    @Test
    fun `progress is trimmed to MAX_LRU_ENTRIES`() = runTest(timeout = 120.seconds) {
        // Insert MAX_LRU_ENTRIES + 5 items
        for (i in 1..(TelegramProgressStore.MAX_LRU_ENTRIES + 5)) {
            store.saveProgress("tg_$i", 10_000L, 60_000L, "Title $i")
        }

        val all = store.allProgress.first()
        assertEquals(TelegramProgressStore.MAX_LRU_ENTRIES, all.size)
    }

    @Test
    fun `watched records are trimmed to MAX_LRU_ENTRIES`() = runTest(timeout = 120.seconds) {
        for (i in 1..(TelegramProgressStore.MAX_LRU_ENTRIES + 5)) {
            store.markPlayed("tg_$i", isPlayed = true, title = "Title $i")
        }

        assertEquals(TelegramProgressStore.MAX_LRU_ENTRIES, store.allProgress.first().size)
    }
}
