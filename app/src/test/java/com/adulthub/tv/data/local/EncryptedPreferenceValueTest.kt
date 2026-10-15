package com.adulthub.tv.data.local

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EncryptedPreferenceValueTest {
    private val crypto = mockk<KeyStoreCrypto>()
    private val codec = EncryptedPreferenceValue(crypto)

    @Test
    fun `legacy plaintext remains readable until migration`() = runTest {
        assertEquals("private value", codec.decode("private value"))
    }

    @Test
    fun `values use a versioned encrypted representation`() = runTest {
        coEvery { crypto.encrypt("private value") } returns "ciphertext"
        coEvery { crypto.decrypt("ciphertext") } returns "private value"

        val stored = codec.encode("private value")

        assertEquals("enc:v1:ciphertext", stored)
        assertEquals("private value", codec.decode(stored))
    }

    @Test
    fun `already encrypted values are not encrypted twice`() = runTest {
        val stored = "enc:v1:existing-ciphertext"

        assertEquals(stored, codec.encode(stored))
        coVerify(exactly = 0) { crypto.encrypt(any()) }
    }

    @Test
    fun `unreadable encrypted values fail closed`() = runTest {
        coEvery { crypto.decrypt("broken") } returns null

        assertNull(codec.decode("enc:v1:broken"))
    }
}
