package com.adulthub.tv.data.repository

import org.junit.Assert.assertEquals
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
}
