package com.adulthub.tv.data.network

/**
 * Which image origins may receive the Jellyfin access token.
 *
 * The image loader fetches two kinds of pictures: the artwork of the Jellyfin
 * server and the thumbnails that the Resolver builds for the video matching.
 * The access token is a session credential, so it belongs only to the services
 * the app talks to. An image host of a third party must never receive it, even
 * when the address itself is allowed for images.
 */
object ImageTokenOriginPolicy {
    /**
     * True when the origin of [requestUrl] equals one of [trustedServiceUrls].
     *
     * Every value is reduced to `scheme://host:port` first, so a trailing slash,
     * a path, a query or an explicit default port cannot change the decision.
     * An empty or unparsable trusted address never matches.
     */
    fun allows(requestUrl: String, trustedServiceUrls: List<String?>): Boolean {
        val requestOrigin = originOf(requestUrl) ?: return false
        return trustedServiceUrls.any { originOf(it) == requestOrigin }
    }

    /** `scheme://host:port` of [url], or `null` when the address is not usable. */
    fun originOf(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return when (val validation = ServiceUrlPolicy.inspect(trimmed, allowQuery = true)) {
            is ServiceUrlValidation.Valid -> validation.value.origin
            is ServiceUrlValidation.Invalid -> null
        }
    }
}
