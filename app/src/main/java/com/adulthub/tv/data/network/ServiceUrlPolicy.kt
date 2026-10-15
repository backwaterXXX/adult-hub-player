package com.adulthub.tv.data.network

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.Locale

data class ServiceUrl(
    val normalized: String,
    val origin: String,
    val usesCleartext: Boolean,
)

sealed interface ServiceUrlValidation {
    data class Valid(val value: ServiceUrl) : ServiceUrlValidation
    data class Invalid(val reason: String) : ServiceUrlValidation
}

object ServiceUrlPolicy {
    fun inspect(rawUrl: String, allowQuery: Boolean = false): ServiceUrlValidation {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull()
            ?: return ServiceUrlValidation.Invalid("Enter a valid server URL")
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.removePrefix("[")?.removeSuffix("]")?.lowercase(Locale.ROOT)
        if (scheme !in setOf("http", "https") || host.isNullOrBlank()) {
            return ServiceUrlValidation.Invalid("Use a complete HTTP or HTTPS URL")
        }
        if (uri.rawUserInfo != null || (!allowQuery && uri.rawQuery != null) || uri.rawFragment != null) {
            return ServiceUrlValidation.Invalid("Server URLs cannot include credentials, a fragment, or a query")
        }
        if (uri.port !in -1..65535 || uri.port == 0) {
            return ServiceUrlValidation.Invalid("Enter a valid server port")
        }

        val usesCleartext = scheme == "http"
        if (usesCleartext && !isLocalNetworkHost(host)) {
            return ServiceUrlValidation.Invalid(
                "Unencrypted HTTP is allowed only for a private LAN address, a .local host, or localhost. Use HTTPS for public servers."
            )
        }

        val normalizedPath = uri.rawPath?.trimEnd('/').orEmpty()
        val normalizedPort = if (uri.port == -1) "" else ":${uri.port}"
        val normalizedQuery = if (allowQuery) uri.rawQuery?.let { "?$it" }.orEmpty() else ""
        val normalized = "$scheme://${hostForUri(host)}$normalizedPort$normalizedPath$normalizedQuery"
        val effectivePort = when {
            uri.port != -1 -> uri.port
            usesCleartext -> 80
            else -> 443
        }
        val origin = "${scheme}://${hostForUri(host)}:$effectivePort"
        return ServiceUrlValidation.Valid(ServiceUrl(normalized, origin, usesCleartext))
    }

    private fun isLocalNetworkHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return true

        val ipv4 = host.split('.')
        if (ipv4.size == 4 && ipv4.all { part -> part.isNotEmpty() && part.all(Char::isDigit) }) {
            if (ipv4.any { it.length > 1 && it.startsWith('0') }) return false
            val octets = ipv4.map { it.toIntOrNull() ?: return false }
            if (octets.any { it !in 0..255 }) return false
            val first = octets[0]
            val second = octets[1]
            return first == 10 || first == 127 ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168) ||
                (first == 169 && second == 254) ||
                (first == 100 && second in 64..127)
        }

        if (':' in host) {
            val address = runCatching { InetAddress.getByName(host) }.getOrNull() as? Inet6Address ?: return false
            val bytes = address.address
            val uniqueLocal = (bytes[0].toInt() and 0xfe) == 0xfc
            return address.isLoopbackAddress || address.isLinkLocalAddress || uniqueLocal
        }
        return false
    }

    private fun hostForUri(host: String): String = if (':' in host) "[$host]" else host
}
