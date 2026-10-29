package com.adulthub.tv.data.jellyfin

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Reports a token that the Jellyfin server rejected with HTTP 401.
 *
 * Only requests that carry an access token count: a rejected sign-in has no token
 * yet, and a 403 is a decision about one resource, not a rejected session.
 */
internal class JellyfinSessionRejectionInterceptor(
    private val onRejectedToken: (String) -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == HTTP_UNAUTHORIZED) {
            accessToken(chain.request())?.let(onRejectedToken)
        }
        return response
    }

    private fun accessToken(request: Request): String? {
        request.header("X-Emby-Token")?.takeIf(String::isNotBlank)?.let { return it }
        val authorization = request.header("Authorization") ?: return null
        val marker = "Token=\""
        val valueStart = authorization.indexOf(marker)
        if (valueStart < 0) return null
        val start = valueStart + marker.length
        val end = authorization.indexOf('"', start)
        if (end <= start) return null
        return authorization.substring(start, end)
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
