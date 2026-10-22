package com.adulthub.tv.data.network

import java.net.URI

/**
 * Local Network Protection on Android 17 (API 37).
 *
 * Apps that target SDK 37 or newer reach the local network only after the user grants
 * `ACCESS_LOCAL_NETWORK`. Without the grant the networking stack drops every packet to a
 * private address before it leaves the device, so a Jellyfin server on the LAN answers with
 * a connection timeout instead of a response. The check below decides when the app must ask
 * for that permission: only on Android 17 and newer, only while the grant is missing, and
 * only for a target that actually sits on the local network.
 */
object LocalNetworkAccessPolicy {
    /** Android version that enforces the protection for apps targeting this SDK level. */
    const val ENFORCEMENT_SDK_INT = 37

    /**
     * Declared in the manifest. Kept as a literal so the policy stays unit testable without
     * the Android framework.
     */
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    fun isEnforced(sdkInt: Int): Boolean = sdkInt >= ENFORCEMENT_SDK_INT

    /**
     * @param targetUrl server address the app is about to contact, may be blank while the
     * user has not typed one yet. An unknown address is treated as local, because asking for
     * the permission is harmless while a blocked connection is not.
     */
    fun requiresPermission(sdkInt: Int, granted: Boolean, targetUrl: String?): Boolean {
        if (!isEnforced(sdkInt) || granted) return false
        val host = hostOf(targetUrl) ?: return true
        return ServiceUrlPolicy.isLocalNetworkHost(host)
    }

    private fun hostOf(rawUrl: String?): String? {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return hostOfUri(trimmed) ?: hostOfUri("http://$trimmed")
    }

    private fun hostOfUri(rawUrl: String): String? =
        runCatching { URI(rawUrl).host }.getOrNull().orEmpty().takeIf { it.isNotEmpty() }
}
