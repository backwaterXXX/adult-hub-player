package com.adulthub.tv.data.update

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/**
 * Checks whether the running installation still carries the pinned release
 * signing certificate.
 *
 * The release key was rotated on 2026-10-20. An installation from before that
 * rotation can never pass the signer check of an update, because the update is
 * signed with the new key. The app detects that situation and asks for a fresh
 * install instead of failing later with a bare technical message.
 */
object SigningIdentity {
    /** Asset that carries the SHA-256 digest of the pinned release certificate. */
    const val PINNED_CERTIFICATE_ASSET = "release_cert.sha256"

    /** Message shown when the installation predates the signing key rotation. */
    const val FRESH_INSTALL_REQUIRED_MESSAGE =
        "This installation was signed with an older key, so it cannot be updated in place. " +
            "Uninstall the app and install the latest release again to receive updates."

    /** SHA-256 digest of the certificate the running app was signed with. */
    fun installedCertificateSha256(context: Context): String? =
        installedSignerBytes(context)?.let { sha256(it) }

    /** Encoded certificate of the running app, when the platform reports one. */
    fun installedSignerBytes(context: Context): ByteArray? = try {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
    } catch (_: Exception) {
        null
    }

    /** SHA-256 digest of the certificate the releases are signed with. */
    fun pinnedCertificateSha256(context: Context): String? = try {
        context.assets.open(PINNED_CERTIFICATE_ASSET).bufferedReader().use { reader ->
            reader.readText().trim().lowercase().takeIf { it.length == 64 }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * True when the running installation cannot be updated in place.
     *
     * A missing pinned digest means the build does not ship the asset, so the
     * check stays silent instead of blocking updates.
     */
    fun requiresFreshInstall(context: Context): Boolean =
        requiresFreshInstall(pinnedCertificateSha256(context), installedCertificateSha256(context))

    /** Comparison of the two digests; unknown values never block an update. */
    fun requiresFreshInstall(pinned: String?, installed: String?): Boolean {
        if (pinned.isNullOrBlank() || installed.isNullOrBlank()) return false
        return installed != pinned
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
