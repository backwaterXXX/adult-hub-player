package com.adulthub.tv.data.repository

import com.adulthub.tv.data.model.TvItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRepositoryTest {
    @Test
    fun `resolver media source is routed through configured local origin`() {
        val original = "https://resolver.example.com/admin/play/telegram:channel:42:0/stream?mode=proxy"

        val result = rewriteResolverMediaSourceUrl(
            mediaSourcePath = original,
            resolverOrigin = "http://192.168.1.100:8099/",
        )

        assertEquals(
            "http://192.168.1.100:8099/admin/play/telegram:channel:42:0/stream?mode=proxy",
            result,
        )
    }

    @Test
    fun `non resolver media source is unchanged`() {
        val original = "https://media.example.test/videos/42/stream.mp4?api_key=token"

        val result = rewriteResolverMediaSourceUrl(
            mediaSourcePath = original,
            resolverOrigin = "http://192.168.1.100:8099",
        )

        assertEquals(original, result)
    }

    @Test
    fun `provider video id is normalized to a telegram resolver reference`() {
        assertEquals(
            "telegram:-100:21406:5393323",
            telegramResolverRef(TvItem(id = "jellyfin-uuid", title = "t", providerVideoId = "-100:21406:5393323")),
        )
        assertEquals(
            "telegram:-100:21406:5393323",
            telegramResolverRef(TvItem(id = "x", title = "t", providerVideoId = "telegram:-100:21406:5393323")),
        )
        assertEquals(
            "telegram:-100:1:2",
            telegramResolverRef(TvItem(id = "telegram:-100:1:2", title = "t")),
        )
        assertNull(telegramResolverRef(TvItem(id = "plain-id", title = "t")))
    }

    @Test
    fun `original variant keeps the historic stream address`() {
        val url = "http://resolver:8090/admin/play/telegram:-100:1:2/stream?mode=proxy"

        assertEquals(url, appendVariantQuery(url, null))
        assertEquals(url, appendVariantQuery(url, "original"))
        assertEquals(url, appendVariantQuery(url, "  original  "))
    }

    @Test
    fun `alternative variant is appended as query parameter`() {
        val url = "http://resolver:8090/admin/play/telegram:-100:1:2/stream?mode=proxy"

        assertEquals(
            "$url&variant=alt_5393558434975097219",
            appendVariantQuery(url, "alt_5393558434975097219"),
        )
    }

    @Test
    fun `variant uses a question mark when the url has no query yet`() {
        val url = "https://resolver.example/admin/play/telegram:-100:1:2/stream"

        assertEquals(
            "$url?variant=alt_7",
            appendVariantQuery(url, "alt_7"),
        )
    }

    @Test
    fun `unexpected characters in a variant id are percent encoded`() {
        val url = "http://resolver/stream?mode=proxy"

        assertEquals(
            "$url&variant=a%26b",
            appendVariantQuery(url, "a&b"),
        )
    }
}
