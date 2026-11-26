package com.adulthub.tv.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultHubMappersTest {

    @Test
    fun `channel name maps next to an empty studio`() {
        val item = CatalogVideoItemDto(
            id = "telegram:-1001618765010:1:2",
            title = "Scene",
            channelName = "Lukayan SHOW"
        ).toTvItem()

        assertEquals("Lukayan SHOW", item.channelName)
        assertTrue(item.studios.isEmpty())
    }

    @Test
    fun `studio and channel stay separate`() {
        val item = CatalogVideoItemDto(
            id = "telegram:-1001618765010:1:2",
            title = "Scene",
            studio = "BelAmi",
            channelName = "Lukayan SHOW"
        ).toTvItem()

        assertEquals(listOf("BelAmi"), item.studios)
        assertEquals("Lukayan SHOW", item.channelName)
    }

    @Test
    fun `blank channel name becomes null`() {
        val item = CatalogVideoItemDto(
            id = "telegram:-1001618765010:1:2",
            title = "Scene",
            channelName = "  "
        ).toTvItem()

        assertNull(item.channelName)
    }
}
