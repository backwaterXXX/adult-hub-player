package com.adulthub.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.adulthub.tv.data.model.TvItem
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class WatchHistoryStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val context = mockk<Context>(relaxed = true)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: WatchHistoryStore

    @Before
    fun setUp() {
        val testFile = tmpFolder.newFile("test_watch_history.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile },
        )
        store = WatchHistoryStore(
            context = context,
            dataStore = dataStore,
        )
    }

    @Test
    fun `recordWatch adds item to history and converts to TvItem`() = runTest {
        assertTrue(store.history.first().isEmpty())

        val item = TvItem(
            id = "jf_movie_1",
            title = "Awesome Movie",
            overview = "A great movie",
            posterUrl = "https://thumb/1.jpg",
            runtimeTicks = 54_000_000_000L,
            isPlayed = false,
            playbackPositionTicks = 27_000_000_000L,
            genres = listOf("Action", "Romance"),
        )

        store.recordWatch(item, watchedAt = 1000L)

        val history = store.history.first()
        assertEquals(1, history.size)
        val record = history.first()
        assertEquals("jf_movie_1", record.itemId)
        assertEquals("Awesome Movie", record.title)
        assertEquals(1000L, record.watchedAt)

        val tvItem = record.toTvItem()
        assertEquals("jf_movie_1", tvItem.id)
        assertEquals("Awesome Movie", tvItem.title)
        assertEquals("A great movie", tvItem.overview)
        assertEquals("https://thumb/1.jpg", tvItem.posterUrl)
        assertEquals(90, tvItem.runtimeMinutes)
        assertEquals(54_000_000_000L, tvItem.runtimeTicks)
        assertEquals(listOf("Action", "Romance"), tvItem.genres)
    }

    @Test
    fun `recordWatch deduplicates and moves item to front`() = runTest {
        val item1 = TvItem(id = "movie_1", title = "Movie 1")
        val item2 = TvItem(id = "movie_2", title = "Movie 2")

        store.recordWatch(item1, watchedAt = 1000L)
        store.recordWatch(item2, watchedAt = 2000L)

        var history = store.history.first()
        assertEquals(2, history.size)
        assertEquals("movie_2", history[0].itemId)
        assertEquals("movie_1", history[1].itemId)

        // Watch movie 1 again with updated timestamp
        store.recordWatch(item1, watchedAt = 3000L)

        history = store.history.first()
        assertEquals(2, history.size)
        assertEquals("movie_1", history[0].itemId)
        assertEquals(3000L, history[0].watchedAt)
        assertEquals("movie_2", history[1].itemId)
    }

    @Test
    fun `clearHistory removes all entries`() = runTest {
        store.recordWatch(TvItem(id = "movie_1", title = "Movie 1"))
        store.recordWatch(TvItem(id = "movie_2", title = "Movie 2"))

        store.clearHistory()
        assertTrue(store.history.first().isEmpty())
    }

    @Test
    fun `history is capped to MAX_HISTORY_ENTRIES`() = runTest {
        for (i in 1..(WatchHistoryStore.MAX_HISTORY_ENTRIES + 10)) {
            store.recordWatch(TvItem(id = "item_$i", title = "Title $i"), watchedAt = i.toLong())
        }

        val history = store.history.first()
        assertEquals(WatchHistoryStore.MAX_HISTORY_ENTRIES, history.size)
        assertEquals("item_${WatchHistoryStore.MAX_HISTORY_ENTRIES + 10}", history.first().itemId)
    }
}
