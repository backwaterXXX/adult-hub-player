package com.adulthub.tv.data.api

/**
 * Widths the resolver can render as cached artwork derivatives.
 *
 * The list mirrors `resolver/artwork_derivatives.py`: a width outside it is
 * ignored by the server, which would silently serve the full-size original.
 */
val ARTWORK_DERIVATIVE_WIDTHS = listOf(160, 240, 320, 400, 480, 640, 720, 960, 1280)

private const val ARTWORK_PATH_MARKER = "/api/v1/artwork/"

/** Round a requested width up to the smallest derivative the server renders. */
fun snapArtworkWidth(width: Int): Int {
    if (width <= 0) return ARTWORK_DERIVATIVE_WIDTHS.first()
    return ARTWORK_DERIVATIVE_WIDTHS.firstOrNull { it >= width } ?: ARTWORK_DERIVATIVE_WIDTHS.last()
}

/**
 * Ask the resolver for the artwork rendered at [width] pixels.
 *
 * Only artwork routes carry the `w` parameter: posters, backdrops and performer
 * photos are the images a card decodes, while any other URL (a stream, an
 * external avatar) is returned untouched. An existing `w` wins, so callers that
 * already chose a width are never overridden, and a resolver without derivatives
 * ignores the parameter and keeps serving the original.
 */
fun artworkUrlWithWidth(url: String, width: Int): String {
    val trimmed = url.trim()
    if (trimmed.isEmpty() || !trimmed.contains(ARTWORK_PATH_MARKER, ignoreCase = true)) {
        return url
    }
    if (hasWidthParameter(trimmed)) {
        return url
    }
    val separator = if (trimmed.contains('?')) '&' else '?'
    return "$trimmed$separator" + "w=" + snapArtworkWidth(width)
}

private fun hasWidthParameter(url: String): Boolean {
    val queryStart = url.indexOf('?')
    if (queryStart < 0) return false
    val query = url.substring(queryStart + 1)
    return query.split('&').any { part ->
        val name = part.substringBefore('=', missingDelimiterValue = part)
        name == "w" || name == "width"
    }
}
