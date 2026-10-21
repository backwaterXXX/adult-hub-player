package com.adulthub.tv.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SigningIdentityTest {

    private val pinnedDigest = "12045c6551156928271ebfe7bea96b41a67cbcef6d01250e03b9e216968329fe"

    private fun contextWithPinnedAsset(content: String): Context {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        val assetManager = mockk<AssetManager>()
        every { assetManager.open(SigningIdentity.PINNED_CERTIFICATE_ASSET) } returns ByteArrayInputStream(content.toByteArray())
        every { context.packageManager } returns packageManager
        every { context.assets } returns assetManager
        return context
    }

    private fun digestOf(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `the pinned certificate digest is read from the asset`() {
        val context = contextWithPinnedAsset("  $pinnedDigest\n")

        assertEquals(pinnedDigest, SigningIdentity.pinnedCertificateSha256(context))
    }

    @Test
    fun `a build without the pinned certificate asset does not block updates`() {
        val context = mockk<Context>()
        val assetManager = mockk<AssetManager>()
        every { assetManager.open(SigningIdentity.PINNED_CERTIFICATE_ASSET) } throws IOException("missing")
        every { context.assets } returns assetManager

        assertNull(SigningIdentity.pinnedCertificateSha256(context))
        assertFalse(SigningIdentity.requiresFreshInstall(context))
    }

    @Test
    fun `an installation with a different certificate needs a fresh install`() {
        val installed = digestOf(ByteArray(32) { 0x02 })

        assertTrue(SigningIdentity.requiresFreshInstall(pinnedDigest, installed))
        assertTrue(SigningIdentity.FRESH_INSTALL_REQUIRED_MESSAGE.contains("Uninstall the app"))
    }

    @Test
    fun `an installation with the pinned certificate can be updated in place`() {
        assertFalse(SigningIdentity.requiresFreshInstall(pinnedDigest, pinnedDigest))
    }

    @Test
    fun `an unknown certificate never blocks an update`() {
        assertFalse(SigningIdentity.requiresFreshInstall(pinnedDigest, null))
        assertFalse(SigningIdentity.requiresFreshInstall(null, pinnedDigest))
        assertFalse(SigningIdentity.requiresFreshInstall(null, null))
    }
}
