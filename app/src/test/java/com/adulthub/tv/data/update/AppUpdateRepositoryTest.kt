package com.adulthub.tv.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class AppUpdateRepositoryTest {

    private val packageName = "com.adulthub.tv"
    private val sdkInt = 34

    private fun manifest(
        schemaVersion: Int = 2,
        applicationId: String = packageName,
        versionCode: Long = 71L,
        versionName: String = "0.1.71",
        minSdk: Int = 28,
        sizeBytes: Long = 1024L,
        sha256: String = "a".repeat(64),
        apkAssetName: String = "AdultHubTV-0.1.71.apk",
    ) = UpdateManifest(
        schemaVersion = schemaVersion,
        applicationId = applicationId,
        versionCode = versionCode,
        versionName = versionName,
        minSdk = minSdk,
        apkAssetName = apkAssetName,
        sizeBytes = sizeBytes,
        sha256 = sha256,
    )

    @Test
    fun `valid manifest is accepted`() {
        validateUpdateManifest(manifest(), packageName, sdkInt)
    }

    @Test
    fun `unsupported schema version is rejected`() {
        val error = runCatching { validateUpdateManifest(manifest(schemaVersion = 1), packageName, sdkInt) }
            .exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `manifest for another application is rejected`() {
        val error = runCatching { validateUpdateManifest(manifest(applicationId = "com.other.app"), packageName, sdkInt) }
            .exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `manifest that needs a newer Android version is rejected`() {
        val error = runCatching { validateUpdateManifest(manifest(minSdk = 40), packageName, sdkInt) }
            .exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `manifest with an invalid checksum is rejected`() {
        val error = runCatching { validateUpdateManifest(manifest(sha256 = "not-a-checksum"), packageName, sdkInt) }
            .exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `manifest with an invalid asset name is rejected`() {
        val error = runCatching { validateUpdateManifest(manifest(apkAssetName = "../../evil.apk"), packageName, sdkInt) }
            .exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `manifest with an oversized apk is rejected`() {
        val error = runCatching { validateUpdateManifest(manifest(sizeBytes = 400L * 1024 * 1024), packageName, sdkInt) }
            .exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `sha256 of a file matches the expected digest`() {
        val file = File.createTempFile("update", ".apk")
        try {
            file.writeBytes("adult-hub".toByteArray())
            val expected = MessageDigest.getInstance("SHA-256")
                .digest("adult-hub".toByteArray())
                .joinToString("") { "%02x".format(it) }
            assertEquals(expected, computeSha256(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `signing certificates must match exactly`() {
        val installed = setOf(listOf<Byte>(1, 2, 3))
        assertTrue(sameSigningCertificates(installed, installed))
        assertFalse(sameSigningCertificates(installed, setOf(listOf<Byte>(4, 5, 6))))
        assertFalse(sameSigningCertificates(emptySet(), emptySet()))
    }

    @Test
    fun `redirects are resolved relative to the current url`() {
        assertEquals(
            "https://api.github.com/repos/owner/repo/releases/assets/1",
            resolveRedirectTarget("https://api.github.com/repos/owner/repo", "/repos/owner/repo/releases/assets/1"),
        )
    }

    @Test
    fun `insecure redirect targets are rejected`() {
        val error = runCatching {
            resolveRedirectTarget("https://api.github.com/repos/owner/repo", "http://evil.example.com/apk")
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
