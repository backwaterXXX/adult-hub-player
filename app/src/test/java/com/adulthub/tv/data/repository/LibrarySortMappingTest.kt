package com.adulthub.tv.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortMappingTest {
    @Test
    fun `UI sort names map to Adult Hub sort fields`() {
        assertEquals("title", itemSortBy("SortName"))
        assertEquals("latest", itemSortBy("CommunityRating"))
        assertEquals("latest", itemSortBy("DateCreated"))
        assertEquals("duration", itemSortBy("Runtime"))
    }

    @Test
    fun `legacy aliases and unknown sort names have deterministic mappings`() {
        assertEquals("title", itemSortBy("name"))
        assertEquals("latest", itemSortBy("latest"))
        assertEquals("latest", itemSortBy("unsupported"))
    }
}
