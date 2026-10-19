package com.adulthub.tv.data.repository

import org.jellyfin.sdk.model.api.ItemSortBy
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortMappingTest {
    @Test
    fun `UI sort names map to Jellyfin sort fields`() {
        assertEquals(ItemSortBy.SORT_NAME, itemSortBy("SortName"))
        assertEquals(ItemSortBy.COMMUNITY_RATING, itemSortBy("CommunityRating"))
        assertEquals(ItemSortBy.DATE_CREATED, itemSortBy("DateCreated"))
        assertEquals(ItemSortBy.RUNTIME, itemSortBy("Runtime"))
    }

    @Test
    fun `legacy aliases and unknown sort names have deterministic mappings`() {
        assertEquals(ItemSortBy.SORT_NAME, itemSortBy("name"))
        assertEquals(ItemSortBy.DATE_CREATED, itemSortBy("latest"))
        assertEquals(ItemSortBy.DATE_CREATED, itemSortBy("unsupported"))
    }
}
