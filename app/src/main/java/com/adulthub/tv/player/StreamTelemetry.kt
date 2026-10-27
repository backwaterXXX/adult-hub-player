package com.adulthub.tv.player

import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener

private const val LOG_TAG = "PlayerManager"

/**
 * Reports the size, content type and transferred bytes of one stream open.
 *
 * The diagnostics overlay and the quality switch both need the real file size, but a
 * server only announces it in one of two ways, so both are accepted and anything else
 * stays unknown rather than guessed.
 */
internal class FileSizeTrackingDataSource(
    private val upstream: DataSource,
    private val onFileSize: (Long) -> Unit,
    private val onContentType: (String) -> Unit,
    private val onBytesTransferred: (Int) -> Unit,
) : DataSource {
    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val length = upstream.open(dataSpec)
        val headers = upstream.responseHeaders
        headers.firstHeaderValue("Content-Type")?.let(onContentType)
        detectedStreamSize(
            headers = headers,
            contentLength = length,
            position = dataSpec.position,
            requestedLength = dataSpec.length,
        )?.let { totalSize ->
            onFileSize(totalSize)
            Log.i(LOG_TAG, "Detected stream size=$totalSize bytes")
        }
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = upstream.read(buffer, offset, length)
        if (read > 0) onBytesTransferred(read)
        return read
    }

    override fun getUri(): android.net.Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()
}

/**
 * Total size of the file being transferred, or null when the server did not announce it.
 *
 * A range response states the size after the slash of `Content-Range`; a full response
 * states it as the length returned by the open. A partial `Content-Length` of the requested
 * window is not the size of the file and must not be reported as one.
 */
internal fun detectedStreamSize(
    headers: Map<String, List<String>>,
    contentLength: Long,
    position: Long,
    requestedLength: Long,
): Long? {
    val totalFromRange = headers.firstHeaderValue("Content-Range")
        ?.substringAfterLast('/', "")
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
    val totalFromFullResponse = contentLength.takeIf {
        position == 0L && requestedLength == C.LENGTH_UNSET.toLong() && it > 0L
    }
    return totalFromRange ?: totalFromFullResponse
}

private fun Map<String, List<String>>.firstHeaderValue(name: String): String? =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
