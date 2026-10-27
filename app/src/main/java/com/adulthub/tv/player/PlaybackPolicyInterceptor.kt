package com.adulthub.tv.player

import com.adulthub.tv.data.network.ServiceUrlPolicy
import com.adulthub.tv.data.network.ServiceUrlValidation
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/** Marks the origin the playback was authorised for; consumed by [PlaybackPolicyInterceptor]. */
internal const val PLAYBACK_POLICY_ORIGIN_HEADER = "X-AdultHub-Playback-Origin"

/** Lists the headers copied from the server request, so they can be dropped after a redirect. */
internal const val PLAYBACK_POLICY_HEADERS_HEADER = "X-AdultHub-Playback-Headers"

/**
 * Stops stream credentials from following a redirect to another origin.
 *
 * Jellyfin and the resolver authorise a stream on one origin, then hand the client off
 * to a CDN or a Telegram proxy. The session token must not survive that hop, and a
 * cleartext redirect is refused instead of downgrading the connection silently. Both
 * policy markers are internal to the app and are never sent over the network.
 */
internal class PlaybackPolicyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val allowedOrigin = request.header(PLAYBACK_POLICY_ORIGIN_HEADER).orEmpty()
        val forwardedHeaders = request.header(PLAYBACK_POLICY_HEADERS_HEADER)
            .orEmpty()
            .split(',')
            .filter(String::isNotBlank)
        val requestWithoutPolicy = request.newBuilder()
            .removeHeader(PLAYBACK_POLICY_ORIGIN_HEADER)
            .removeHeader(PLAYBACK_POLICY_HEADERS_HEADER)
        val safeRequest = requestWithoutPolicy.build()
        return if (request.url.isHttps) {
            val redirectedOrigin = "${request.url.scheme}://${request.url.host}:${request.url.port}"
            val originSafeRequest = if (redirectedOrigin == allowedOrigin) safeRequest else {
                requestWithoutPolicy.apply {
                    forwardedHeaders.forEach(::removeHeader)
                    removeHeader("Authorization")
                    removeHeader("Cookie")
                    removeHeader("X-Emby-Token")
                }.build()
            }
            chain.proceed(originSafeRequest)
        } else {
            when (val validation = ServiceUrlPolicy.inspect(request.url.toString(), allowQuery = true)) {
                is ServiceUrlValidation.Valid -> {
                    if (!validation.value.usesCleartext) {
                        throw IOException("Blocked an insecure stream redirect")
                    }
                    if (validation.value.origin != allowedOrigin) {
                        throw IOException("Blocked a cleartext redirect to a different host")
                    }
                    chain.proceed(safeRequest)
                }
                is ServiceUrlValidation.Invalid -> throw IOException("Blocked an insecure stream redirect: ${validation.reason}")
            }
        }
    }
}
