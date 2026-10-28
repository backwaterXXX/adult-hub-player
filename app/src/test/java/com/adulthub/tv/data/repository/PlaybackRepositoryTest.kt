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

    @Test
    fun `existing variant parameter is replaced instead of duplicated`() {
        val url = "http://resolver/stream?mode=proxy&variant=old_1"

        assertEquals(
            "http://resolver/stream?mode=proxy&variant=new_2",
            appendVariantQuery(url, "new_2"),
        )
    }

    @Test
    fun `original variant clears an existing variant parameter`() {
        val url = "http://resolver/stream?mode=proxy&variant=old_1"

        assertEquals(
            "http://resolver/stream?mode=proxy",
            appendVariantQuery(url, "original"),
        )
    }

    @Test
    fun `an address without any variant choice is returned untouched`() {
        // Rebuilding through HttpUrl would normalize escapes, ports and trailing slashes,
        // which must never happen to a signed media source address.
        val url = "https://media.example.test:443/videos/42/stream.mp4?api_key=Sig%2Fnature%2Bx"

        assertEquals(url, appendVariantQuery(url, null))
        assertEquals(url, appendVariantQuery(url, "   "))
    }

    @Test
    fun `jellyfin headers are kept on the jellyfin origin`() {
        val headers = mapOf(
            "Authorization" to "MediaBrowser Token=\"token\"",
            "X-Emby-Token" to "token",
            "Referer" to "https://media.example.test",
        )

        val result = filterPlaybackHeaders(
            headers = headers,
            streamUrl = "https://media.example.test/videos/42/stream.mp4",
            serverUrl = "https://media.example.test/",
            accessToken = "token",
        )

        assertEquals(headers, result)
    }

    @Test
    fun `jellyfin credentials never follow a stream to another origin`() {
        val result = filterPlaybackHeaders(
            headers = mapOf(
                "Authorization" to "MediaBrowser Token=\"token\"",
                "Cookie" to "jellyfin=token",
                "X-Emby-Token" to "token",
                "X-MediaBrowser-Token" to "token",
                "X-Playback-Referer" to "https://media.example.test/?token",
                "Referer" to "https://media.example.test",
            ),
            streamUrl = "http://192.168.1.100:8099/admin/play/telegram:1/stream?mode=proxy",
            serverUrl = "http://192.168.1.50:8096",
            accessToken = "token",
        )

        assertEquals(mapOf("Referer" to "https://media.example.test"), result)
    }

    @Test
    fun `credentials of an external source survive a cross-origin stream`() {
        // A CDN may authenticate with its own Basic credentials or cookie; neither
        // belongs to Jellyfin, even when the header name matches a Jellyfin one.
        val headers = mapOf(
            "Authorization" to "Basic dXNlcjpwYXNz",
            "Cookie" to "cdn_session=abc123",
            "Proxy-Authorization" to "Basic cHJveHk=",
            "X-Emby-Token" to "issued-by-the-source",
        )

        val result = filterPlaybackHeaders(
            headers = headers,
            streamUrl = "https://cdn.example.test/video.mp4",
            serverUrl = "http://192.168.1.50:8096",
            accessToken = "token",
        )

        assertEquals(headers, result)
    }

    @Test
    fun `without a known server origin jellyfin credentials are dropped`() {
        val result = filterPlaybackHeaders(
            headers = mapOf("Authorization" to "Bearer token", "Accept" to "video/*"),
            streamUrl = "https://cdn.example.test/video.mp4",
            serverUrl = null,
            accessToken = "token",
        )

        assertEquals(mapOf("Accept" to "video/*"), result)
    }

    @Test
    fun `a stream address without an http origin sends no server headers`() {
        val result = filterPlaybackHeaders(
            headers = mapOf("Authorization" to "Bearer token"),
            streamUrl = "not a url",
            serverUrl = "https://media.example.test",
            accessToken = "token",
        )

        assertEquals(emptyMap<String, String>(), result)
    }
}
