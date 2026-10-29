package com.adulthub.tv.data.network

import com.adulthub.tv.data.local.SettingsDataStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class CleartextOriginInterceptorTest {

    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val chain: Interceptor.Chain = mockk(relaxed = true)

    private fun intercept(url: String): Response {
        val request = Request.Builder().url(url).build()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
        return CleartextOriginInterceptor(settingsDataStore).intercept(chain)
    }

    @Test
    fun `an HTTPS request is not checked against the cleartext policy`() {
        val response = intercept("https://jf.example.com/Items")

        assertEquals(200, response.code)
        verify(exactly = 0) { settingsDataStore.requireAllowedNetworkUrl(any()) }
    }

    @Test
    fun `an approved cleartext LAN request is allowed`() {
        every { settingsDataStore.requireAllowedNetworkUrl("http://192.168.1.10:8096/Items") } returns
            ServiceUrl("http://192.168.1.10:8096/Items", "http://192.168.1.10:8096", usesCleartext = true)

        val response = intercept("http://192.168.1.10:8096/Items")

        assertEquals(200, response.code)
    }

    @Test
    fun `an unapproved cleartext request is refused before it reaches the network`() {
        every { settingsDataStore.requireAllowedNetworkUrl(any()) } throws
            IllegalStateException("Confirm unencrypted HTTP for this private LAN host before connecting")

        val error = assertThrows(IOException::class.java) {
            intercept("http://192.168.1.10:8096/Items")
        }

        assertEquals(true, error.message?.contains("unapproved origin"))
        verify(exactly = 0) { chain.proceed(any()) }
    }
}
