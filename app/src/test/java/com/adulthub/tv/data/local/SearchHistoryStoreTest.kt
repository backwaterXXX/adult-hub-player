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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SearchHistoryStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val context = mockk<Context>(relaxed = true)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SearchHistoryStore

    @Before
    fun setUp() {
        val testFile = tmpFolder.newFile("test_search_history.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile },
        )
        store = SearchHistoryStore(
            context = context,
            dataStore = dataStore,
        )
    }

    @Test
    fun `initial queries list is empty`() = runTest {
        val list = store.queries.first()
        assertTrue(list.isEmpty())
    }

    @Test
    fun `recordQuery ignores short strings`() = runTest {
        store.recordQuery("a")
        store.recordQuery(" ")
        val list = store.queries.first()
        assertTrue(list.isEmpty())
    }

    @Test
    fun `recordQuery adds query to front and deduplicates case-insensitively`() = runTest {
        store.recordQuery("blonde")
        store.recordQuery("brunette")
        store.recordQuery("Blonde")

        val list = store.queries.first()
        assertEquals(listOf("Blonde", "brunette"), list)
    }

    @Test
    fun `recordQuery caps at max 20 queries`() = runTest {
        for (i in 1..25) {
            store.recordQuery("query $i")
        }

        val list = store.queries.first()
        assertEquals(20, list.size)
        assertEquals("query 25", list.first())
    }

    @Test
    fun `clearHistory empties queries list`() = runTest {
        store.recordQuery("query 1")
        store.recordQuery("query 2")
        assertEquals(2, store.queries.first().size)

        store.clearHistory()
        assertTrue(store.queries.first().isEmpty())
    }
}
