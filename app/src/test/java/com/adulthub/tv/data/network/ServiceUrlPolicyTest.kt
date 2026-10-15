package com.adulthub.tv.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceUrlPolicyTest {
    @Test
    fun `private LAN HTTP is accepted but marked for consent`() {
        val result = ServiceUrlPolicy.inspect("http://192.168.1.20:8099/")

        assertTrue(result is ServiceUrlValidation.Valid)
        val serviceUrl = (result as ServiceUrlValidation.Valid).value
        assertTrue(serviceUrl.usesCleartext)
        assertEquals("http://192.168.1.20:8099", serviceUrl.normalized)
        assertEquals("http://192.168.1.20:8099", serviceUrl.origin)
    }

    @Test
    fun `public HTTP is rejected`() {
        val result = ServiceUrlPolicy.inspect("http://resolver.example.com:8099")

        assertTrue(result is ServiceUrlValidation.Invalid)
        assertTrue((result as ServiceUrlValidation.Invalid).reason.contains("HTTPS"))
    }

    @Test
    fun `public HTTPS is accepted`() {
        val result = ServiceUrlPolicy.inspect("https://resolver.example.com/api/")

        assertTrue(result is ServiceUrlValidation.Valid)
        val serviceUrl = (result as ServiceUrlValidation.Valid).value
        assertFalse(serviceUrl.usesCleartext)
        assertEquals("https://resolver.example.com/api", serviceUrl.normalized)
        assertEquals("https://resolver.example.com:443", serviceUrl.origin)
    }

    @Test
    fun `HTTP request query can be preserved when validating playback URLs`() {
        val result = ServiceUrlPolicy.inspect(
            "http://192.168.1.20:8099/admin/play/video/stream?mode=proxy",
            allowQuery = true,
        )

        assertTrue(result is ServiceUrlValidation.Valid)
        assertEquals(
            "http://192.168.1.20:8099/admin/play/video/stream?mode=proxy",
            (result as ServiceUrlValidation.Valid).value.normalized,
        )
    }
}
