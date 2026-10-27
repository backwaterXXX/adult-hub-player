package com.adulthub.tv.player

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackPolicyInterceptorTest {
    private val interceptor = PlaybackPolicyInterceptor()

    private fun forwardedRequest(request: Request): Request {
        val captured = slot<Request>()
        val chain = mockk<Interceptor.Chain> {
            every { this@mockk.request() } returns request
            every { proceed(capture(captured)) } returns mockk<Response>()
        }
        interceptor.intercept(chain)
        return captured.captured
    }

    @Test
    fun `keeps credentials while the stream stays on the authorised origin`() {
        val request = Request.Builder()
            .url("https://server.example/stream")
            .header("Authorization", "Bearer token")
            .header(PLAYBACK_POLICY_ORIGIN_HEADER, "https://server.example:443")
            .header(PLAYBACK_POLICY_HEADERS_HEADER, "Authorization")
            .build()

        val forwarded = forwardedRequest(request)

        assertEquals("Bearer token", forwarded.header("Authorization"))
    }

    @Test
    fun `policy markers are never sent`() {
        val request = Request.Builder()
            .url("https://server.example/stream")
            .header(PLAYBACK_POLICY_ORIGIN_HEADER, "https://server.example:443")
            .header(PLAYBACK_POLICY_HEADERS_HEADER, "Authorization")
            .build()

        val forwarded = forwardedRequest(request)

        assertNull(forwarded.header(PLAYBACK_POLICY_ORIGIN_HEADER))
        assertNull(forwarded.header(PLAYBACK_POLICY_HEADERS_HEADER))
    }

    @Test
    fun `drops session credentials after a redirect to another host`() {
        val request = Request.Builder()
            .url("https://cdn.example/piece.m3u8")
            .header("Authorization", "Bearer token")
            .header("Cookie", "session=1")
            .header("X-Emby-Token", "token")
            .header(PLAYBACK_POLICY_ORIGIN_HEADER, "https://server.example:443")
            .build()

        val forwarded = forwardedRequest(request)

        assertNull(forwarded.header("Authorization"))
        assertNull(forwarded.header("Cookie"))
        assertNull(forwarded.header("X-Emby-Token"))
    }

    @Test
    fun `drops server headers the playback policy listed after a host change`() {
        val request = Request.Builder()
            .url("https://cdn.example/piece.m3u8")
            .header("X-Jellyfin-Device", "shield")
            .header("X-Unrelated", "kept")
            .header(PLAYBACK_POLICY_ORIGIN_HEADER, "https://server.example:443")
            .header(PLAYBACK_POLICY_HEADERS_HEADER, "X-Jellyfin-Device")
            .build()

        val forwarded = forwardedRequest(request)

        assertNull(forwarded.header("X-Jellyfin-Device"))
        assertEquals("kept", forwarded.header("X-Unrelated"))
    }

    @Test
    fun `cleartext is allowed on the approved private origin`() {
        val request = Request.Builder()
            .url("http://192.168.1.20:8099/stream")
            .header(PLAYBACK_POLICY_ORIGIN_HEADER, "http://192.168.1.20:8099")
            .build()

        val forwarded = forwardedRequest(request)

        assertEquals("http://192.168.1.20:8099/stream", forwarded.url.toString())
    }

    @Test
    fun `blocks a cleartext redirect to a different private host`() {
        val request = Request.Builder()
            .url("http://192.168.1.21:8099/stream")
            .header("Authorization", "Bearer token")
            .header(PLAYBACK_POLICY_ORIGIN_HEADER, "http://192.168.1.20:8099")
            .build()

        val chain = mockk<Interceptor.Chain> {
            every { this@mockk.request() } returns request
        }

        assertThrows(IOException::class.java) { interceptor.intercept(chain) }
    }

    @Test
    fun `blocks cleartext on a public host`() {
        val request = Request.Builder()
            .url("http://public.example/stream")
            .header(PLAYBACK_POLICY_ORIGIN_HEADER, "https://server.example:443")
            .build()

        val chain = mockk<Interceptor.Chain> {
            every { this@mockk.request() } returns request
        }

        assertThrows(IOException::class.java) { interceptor.intercept(chain) }
    }
}
