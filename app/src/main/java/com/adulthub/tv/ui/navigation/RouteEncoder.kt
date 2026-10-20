package com.adulthub.tv.ui.navigation

/**
 * Percent encoding for navigation route segments.
 *
 * The implementation follows the same rules as `android.net.Uri.encode`, but it is pure
 * Kotlin, so the route building can be covered by plain unit tests.
 */
internal object RouteEncoder {
    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-!.~'()*"

    fun encode(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val builder = StringBuilder(bytes.size)
        for (byte in bytes) {
            val char = byte.toInt().toChar()
            if (byte >= 0 && UNRESERVED.indexOf(char) >= 0) {
                builder.append(char)
            } else {
                builder.append('%').append(HEX[(byte.toInt() shr 4) and 0x0F]).append(HEX[byte.toInt() and 0x0F])
            }
        }
        return builder.toString()
    }

    private val HEX = "0123456789ABCDEF"
}
