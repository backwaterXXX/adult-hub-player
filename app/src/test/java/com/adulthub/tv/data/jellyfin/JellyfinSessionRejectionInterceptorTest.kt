package com.adulthub.tv.data.jellyfin

import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinSessionRejectionInterceptorTest {

    private val chain = mockk<Interceptor.Chain>()
    private val rejectedTokens = mutableListOf<String>()

    private fun intercept(request: Request, responseCode: Int): Response {
        every { chain.request() } returns request
        every { chain.proceed(request) } returns Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(responseCode)
            .message("response")
            .build()
        return JellyfinSessionRejectionInterceptor(rejectedTokens::add).intercept(chain)
    }

    @Test
    fun `a 401 on a token request reports the token`() {
        val request = Request.Builder()
            .url("https://jf.example.com/Users")
            .header(
                "Authorization",
                "MediaBrowser Client=\"app\", Device=\"tv\", DeviceId=\"id\", Version=\"1\", Token=\"tok-1\"",
            )
            .build()

        val response = intercept(request, 401)

        assertEquals(401, response.code)
        assertEquals(listOf("tok-1"), rejectedTokens)
    }

    @Test
    fun `a 401 from the X-Emby-Token header reports that token`() {
        val request = Request.Builder()
            .url("https://jf.example.com/Users")
            .header("X-Emby-Token", "tok-2")
            .build()

        intercept(request, 401)

        assertEquals(listOf("tok-2"), rejectedTokens)
    }

    @Test
    fun `a rejected sign-in without a token is not a session rejection`() {
        val request = Request.Builder()
            .url("https://jf.example.com/Users/AuthenticateByName")
            .header(
                "Authorization",
                "MediaBrowser Client=\"app\", Device=\"tv\", DeviceId=\"id\", Version=\"1\"",
            )
            .build()

        intercept(request, 401)

        assertTrue(rejectedTokens.isEmpty())
    }

    @Test
    fun `an authorization failure is not a session rejection`() {
        val request = Request.Builder()
            .url("https://jf.example.com/Users")
            .header(
                "Authorization",
                "MediaBrowser Client=\"app\", Device=\"tv\", DeviceId=\"id\", Version=\"1\", Token=\"tok-3\"",
            )
            .build()

        intercept(request, 403)

        assertTrue(rejectedTokens.isEmpty())
    }
}
