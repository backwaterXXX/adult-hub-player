package com.adulthub.tv.player

import com.adulthub.tv.data.resolver.PhashGenerationRequest
import com.adulthub.tv.data.resolver.ResolverApiClient
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PhashSamplingTest {
    @Test
    fun `duration uses Jellyfin runtime ticks then player duration`() {
        assertEquals(600_000_000L, resolvePhashDurationUs(6_000_000_000L, 700_000L))
        assertEquals(700_000_000L, resolvePhashDurationUs(null, 700_000L))
        assertEquals(null, resolvePhashDurationUs(null, 0L))
    }

    @Test
    fun `only valid Telegram video identifiers are queued`() {
        assertEquals("telegram:-100123:42:99", adultHubVideoId(
            com.adulthub.tv.data.model.TvItem(id = "x", title = "Video", providerVideoId = "-100123:42:99")
        ))
        assertEquals(null, adultHubVideoId(
            com.adulthub.tv.data.model.TvItem(id = "x", title = "Video", providerVideoId = "not-telegram")
        ))
    }

    @Test
    fun `resolver client posts a small authenticated queue request`() = runBlocking {
        var intercepted: Request? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            intercepted = request
            okhttp3.Response.Builder()
                .request(request).protocol(Protocol.HTTP_1_1).code(202).message("Accepted")
                .body("""{"ok":true,"job_id":"p-abc","state":"queued","already_computed":false,"thumbnail_ready":false}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val api = ResolverApiClient(client).create("https://example.com")
        val result = api.generatePhash("secret-token", PhashGenerationRequest("telegram:-100:1:1", 90_000_000L))
        assertEquals(true, result.ok)
        assertEquals("queued", result.state)
        assertNotNull(intercepted)
        assertEquals("POST", intercepted!!.method)
        assertEquals("/admin/api/v2/video-phash/generate", intercepted!!.url.encodedPath)
        assertEquals("secret-token", intercepted!!.header("X-Emby-Token"))
        val requestBody = okio.Buffer().also { intercepted!!.body!!.writeTo(it) }.readUtf8()
        assertEquals("""{"video_id":"telegram:-100:1:1","duration_us":90000000}""", requestBody)
    }
}
