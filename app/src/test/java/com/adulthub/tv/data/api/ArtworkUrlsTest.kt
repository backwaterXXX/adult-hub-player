package com.adulthub.tv.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkUrlsTest {

    @Test
    fun `artwork routes get the width parameter`() {
        assertEquals(
            "https://hub.example/api/v1/artwork/video/telegram%3A1%3A2/poster?w=480",
            artworkUrlWithWidth("https://hub.example/api/v1/artwork/video/telegram%3A1%3A2/poster", 480)
        )
    }

    @Test
    fun `an existing query keeps its separator`() {
        assertEquals(
            "https://hub.example/api/v1/artwork/performer/x/photo?v=2&w=240",
            artworkUrlWithWidth("https://hub.example/api/v1/artwork/performer/x/photo?v=2", 240)
        )
    }

    @Test
    fun `a width outside the ladder snaps up`() {
        assertEquals(
            "https://hub.example/api/v1/artwork/video/x/poster?w=640",
            artworkUrlWithWidth("https://hub.example/api/v1/artwork/video/x/poster", 500)
        )
        assertEquals(
            "https://hub.example/api/v1/artwork/video/x/poster?w=1280",
            artworkUrlWithWidth("https://hub.example/api/v1/artwork/video/x/poster", 4000)
        )
    }

    @Test
    fun `an explicit width is never overridden`() {
        val url = "https://hub.example/api/v1/artwork/video/x/poster?w=960"
        assertEquals(url, artworkUrlWithWidth(url, 480))
    }

    @Test
    fun `non artwork urls are untouched`() {
        assertEquals(
            "https://cdn.example/poster.jpg",
            artworkUrlWithWidth("https://cdn.example/poster.jpg", 480)
        )
        assertEquals(
            "https://hub.example/api/v1/stream/telegram%3A1",
            artworkUrlWithWidth("https://hub.example/api/v1/stream/telegram%3A1", 480)
        )
        assertEquals("", artworkUrlWithWidth("", 480))
    }
}
