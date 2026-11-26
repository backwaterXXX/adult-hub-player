package com.adulthub.tv.ui.components

import android.content.Context
import com.adulthub.tv.R
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

/**
 * Translates a transport failure into copy a viewer can act on.
 *
 * Exception messages ("timeout", "Failed to connect to /10.0.0.2:8090") used to reach the UI
 * verbatim, which told people nothing. Anything we cannot classify keeps its own message and
 * falls back to the caller's generic string.
 */
fun Throwable.userFacingMessage(context: Context, fallbackRes: Int): String = when (this) {
    is SocketTimeoutException, is InterruptedIOException -> context.getString(R.string.error_timeout)

    is UnknownHostException, is ConnectException -> context.getString(R.string.error_unreachable)

    is HttpException -> when (code()) {
        401, 403 -> context.getString(R.string.error_not_authorized)
        in 500..599 -> context.getString(R.string.error_server)
        else -> context.getString(R.string.error_request_failed, code())
    }

    is IOException -> context.getString(R.string.error_network)

    else -> message?.takeIf { it.isNotBlank() } ?: context.getString(fallbackRes)
}
