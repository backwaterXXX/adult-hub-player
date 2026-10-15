package com.adulthub.tv.data.resolver

import java.net.URI

fun resolveResolverUrl(urlOrPath: String?, resolverOrigin: String): String {
    if (urlOrPath.isNullOrBlank()) return ""
    val origin = resolverOrigin.trim().trimEnd('/')
    if (origin.isBlank()) return urlOrPath

    val uri = runCatching { URI(urlOrPath) }.getOrNull()
    if (uri?.isAbsolute == true) {
        if (!uri.rawPath.orEmpty().startsWith("/admin/")) return urlOrPath
        return buildString {
            append(origin)
            append(uri.rawPath)
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
    }

    return "$origin/${urlOrPath.trimStart('/')}"
}
