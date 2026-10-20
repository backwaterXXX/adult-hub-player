package com.adulthub.tv.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyStoreCryptoInstrumentedTest {

    @Test
    fun encryptedValuesCanBeDecrypted() = runBlocking {
        val crypto = KeyStoreCrypto()
        val encrypted = crypto.encrypt("secret-token")
        assertNotEquals("secret-token", encrypted)
        assertEquals("secret-token", crypto.decrypt(encrypted))
    }

    @Test
    fun everyEncryptionUsesANewInitializationVector() = runBlocking {
        val crypto = KeyStoreCrypto()
        assertNotEquals(crypto.encrypt("secret-token"), crypto.encrypt("secret-token"))
    }

    @Test
    fun brokenValuesAreRejected() = runBlocking {
        val crypto = KeyStoreCrypto()
        assertNull(crypto.decrypt("not-encrypted"))
        assertNull(crypto.decrypt(""))
    }
}
