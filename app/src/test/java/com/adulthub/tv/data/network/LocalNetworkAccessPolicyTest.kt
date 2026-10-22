package com.adulthub.tv.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the app must ask for the local network permission.
 *
 * Android 17 enforces the protection for apps that target SDK 37: every connection to a
 * private address fails with a timeout until the user grants `ACCESS_LOCAL_NETWORK`. The
 * policy must stay silent for public servers and for older Android versions, otherwise the
 * prompt would appear where no permission is needed.
 */
class LocalNetworkAccessPolicyTest {
    @Test
    fun `protection is enforced from android 17`() {
        assertFalse(LocalNetworkAccessPolicy.isEnforced(36))
        assertTrue(LocalNetworkAccessPolicy.isEnforced(37))
        assertTrue(LocalNetworkAccessPolicy.isEnforced(38))
    }

    @Test
    fun `granted permission never asks again`() {
        assertFalse(
            LocalNetworkAccessPolicy.requiresPermission(
                sdkInt = 37,
                granted = true,
                targetUrl = "http://192.168.1.111:8096",
            ),
        )
    }

    @Test
    fun `android 16 and older keep the implicit access`() {
        assertFalse(
            LocalNetworkAccessPolicy.requiresPermission(
                sdkInt = 36,
                granted = false,
                targetUrl = "http://192.168.1.111:8096",
            ),
        )
    }

    @Test
    fun `private addresses need the permission`() {
        val privateHosts = listOf(
            "http://192.168.1.111:8096",
            "https://10.0.0.5",
            "http://172.16.4.4:8096",
            "http://172.31.255.254",
            "http://100.66.238.125:8096",
            "http://169.254.10.10",
            "http://127.0.0.1:8096",
            "http://localhost:8096",
            "http://jellyfin.local:8096",
            "https://adult.ferges.eu",
        )
        // The last entry is a public name that resolves to the LAN through the local DNS, so
        // a by-name check cannot know it; the string tests below cover the parsed addresses.
        privateHosts.dropLast(1).forEach { url ->
            assertTrue(
                "expected the permission requirement for $url",
                LocalNetworkAccessPolicy.requiresPermission(sdkInt = 37, granted = false, targetUrl = url),
            )
        }
    }

    @Test
    fun `public servers need no permission`() {
        listOf(
            "https://jellyfin.example.com:8096",
            "https://8.8.8.8:8096",
            "https://172.32.0.1",
            "https://11.0.0.1",
        ).forEach { url ->
            assertFalse(
                "expected no permission requirement for $url",
                LocalNetworkAccessPolicy.requiresPermission(sdkInt = 37, granted = false, targetUrl = url),
            )
        }
    }

    @Test
    fun `address without a scheme is still understood`() {
        assertTrue(
            LocalNetworkAccessPolicy.requiresPermission(
                sdkInt = 37,
                granted = false,
                targetUrl = "192.168.1.111:8096",
            ),
        )
        assertFalse(
            LocalNetworkAccessPolicy.requiresPermission(
                sdkInt = 37,
                granted = false,
                targetUrl = "jellyfin.example.com:8096",
            ),
        )
    }

    @Test
    fun `unknown target is treated as local`() {
        assertTrue(LocalNetworkAccessPolicy.requiresPermission(sdkInt = 37, granted = false, targetUrl = null))
        assertTrue(LocalNetworkAccessPolicy.requiresPermission(sdkInt = 37, granted = false, targetUrl = "   "))
        assertTrue(LocalNetworkAccessPolicy.requiresPermission(sdkInt = 37, granted = false, targetUrl = "not a url"))
    }
}
