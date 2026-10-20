package com.adulthub.tv.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogRedactTest {

    @Test
    fun `tokens are redacted in key value form`() {
        val redacted = AppLog.redact("token=super-secret-value")
        assertFalse(redacted.contains("super-secret-value"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test
    fun `tokens are redacted in json form`() {
        val redacted = AppLog.redact("{\"token\":\"super-secret-value\"}")
        assertFalse(redacted.contains("super-secret-value"))
    }

    @Test
    fun `passwords and api keys are redacted`() {
        assertFalse(AppLog.redact("password=hunter2").contains("hunter2"))
        assertFalse(AppLog.redact("api_key=abcdef123456").contains("abcdef123456"))
        assertFalse(AppLog.redact("authorization=abcdef123456").contains("abcdef123456"))
    }

    @Test
    fun `urls are redacted`() {
        val redacted = AppLog.redact("request to https://example.com/private/path failed")
        assertFalse(redacted.contains("example.com"))
        assertTrue(redacted.contains("[URL]"))
    }

    @Test
    fun `item identifiers are redacted`() {
        assertFalse(AppLog.redact("itemId=12345678-abcd-1234-abcd-1234567890ab").contains("12345678"))
    }

    @Test
    fun `uuids are redacted`() {
        val redacted = AppLog.redact("id 123e4567-e89b-12d3-a456-426614174000")
        assertFalse(redacted.contains("123e4567-e89b-12d3-a456-426614174000"))
        assertTrue(redacted.contains("[ID]"))
    }

    @Test
    fun `titles are redacted`() {
        assertFalse(AppLog.redact("title: Secret Movie Name").contains("Secret Movie Name"))
    }
}
