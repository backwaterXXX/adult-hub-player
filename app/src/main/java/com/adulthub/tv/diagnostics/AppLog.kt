package com.adulthub.tv.diagnostics

import android.util.Log
import com.adulthub.tv.BuildConfig
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap

private const val MAX_ENTRIES = 500
private const val MAX_MESSAGE_LENGTH = 2_000
private const val MAX_ROOT_CAUSE_MESSAGE_LENGTH = 150
private const val MAX_CAUSE_DEPTH = 5
private const val MAX_TOP_FRAMES = 3
private const val MAX_FRAMES_PER_THROWABLE = 8
private const val APP_PACKAGE_PREFIX = "com.adulthub."

/** App-owned logs with a bounded, content-redacted buffer for user-requested exports. */
object AppLog {
    private val lock = Any()
    private val entries = ArrayDeque<String>(MAX_ENTRIES)
    private val timestampFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC)

    private val urlPattern = Regex("(?i)https?://[^\\s\\\"'<>]+")
    private val uuidPattern = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b")

    // The separator may also be a JSON style colon, optionally quoted.
    private val secretPattern = Regex("(?i)\\b(token|authorization|password|api[_-]?key)\\b[\\\"']?\\s*[:=]\\s*[\\\"']?[^\\s,;\\\"'}]+")
    private val identifierPattern = Regex("(?i)\\b(item|video|user|session|media[_-]?source)[_-]?id\\b[\\\"']?\\s*[:=]\\s*[\\\"']?[^\\s,;\\\"'}]+")
    private val titlePattern = Regex("(?i)\\b(title|filename|file[_-]?name)\\b[\\\"']?\\s*[:=]\\s*[^,;\\n]+")

    fun d(tag: String, message: String): Int = write(Log.DEBUG, tag, message)
    fun i(tag: String, message: String): Int = write(Log.INFO, tag, message)
    fun w(tag: String, message: String): Int = write(Log.WARN, tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable?): Int = write(Log.WARN, tag, message, throwable)
    fun e(tag: String, message: String): Int = write(Log.ERROR, tag, message)

    private fun write(priority: Int, tag: String, message: String, throwable: Throwable? = null): Int {
        val rawMessage = if (throwable != null) {
            if (message.isBlank()) formatThrowable(throwable) else "$message\n${formatThrowable(throwable)}"
        } else {
            message
        }
        val safeMessage = truncateSafely(redact(rawMessage), throwable)
        val level = when (priority) {
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "?"
        }
        val line = "${timestampFormatter.format(Instant.now())} $level ${tag.take(40)}: $safeMessage"
        synchronized(lock) {
            if (entries.size == MAX_ENTRIES) entries.removeFirst()
            entries.addLast(line)
        }
        return Log.println(priority, tag, safeMessage)
    }

    fun exportText(): String {
        val snapshot = synchronized(lock) { entries.toList() }
        return buildString {
            appendLine("Adult Hub diagnostics")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android API: ${android.os.Build.VERSION.SDK_INT}")
            appendLine("Exported at: ${timestampFormatter.format(Instant.now())}")
            appendLine("Recent app events: ${snapshot.size}")
            appendLine("Content titles, service URLs, account tokens, and UUIDs are redacted.")
            appendLine()
            if (snapshot.isEmpty()) {
                appendLine("No diagnostic events have been recorded yet.")
            } else {
                snapshot.forEach(::appendLine)
            }
        }
    }

    internal fun redact(message: String): String = message
        .replace(urlPattern, "[URL]")
        .replace(secretPattern, "$1=[REDACTED]")
        .replace(identifierPattern, "$1=[REDACTED]")
        .replace(titlePattern, "$1=[REDACTED]")
        .replace(uuidPattern, "[ID]")
}

private fun truncateSafely(redacted: String, throwable: Throwable?): String {
    if (redacted.length <= MAX_MESSAGE_LENGTH) return redacted

    val marker = buildTruncationMarker(throwable)
    val prefixLength = (MAX_MESSAGE_LENGTH - marker.length).coerceAtLeast(0)
    val prefix = redacted.take(prefixLength).trimEnd()
    return prefix + marker
}

private fun buildTruncationMarker(throwable: Throwable?): String {
    val rootCause = throwable?.let(::findRootCause) ?: return "\n[TRUNCATED]"
    val rootClass = rootCause.javaClass.name
    val rootMsg = rootCause.message?.let { AppLog.redact(it).replace('\n', ' ').trim() }
    return if (rootMsg.isNullOrBlank()) {
        "\n[TRUNCATED; root cause: $rootClass]"
    } else {
        "\n[TRUNCATED; root cause: $rootClass: ${rootMsg.take(MAX_ROOT_CAUSE_MESSAGE_LENGTH)}]"
    }
}

private fun findRootCause(throwable: Throwable): Throwable? {
    var root: Throwable = throwable.cause ?: return null
    var depth = 0
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    visited.add(throwable)

    while (depth < MAX_CAUSE_DEPTH && visited.add(root)) {
        val next = root.cause ?: break
        root = next
        depth++
    }
    return root
}

private fun formatThrowable(throwable: Throwable): String = buildString {
    var current: Throwable? = throwable
    var depth = 0
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())

    while (current != null && depth < MAX_CAUSE_DEPTH && visited.add(current)) {
        appendThrowableHeader(this, current, depth)
        val frames = current.stackTrace
        if (!frames.isNullOrEmpty()) {
            appendFrames(this, frames)
        }
        current = current.cause
        depth++
    }
}

private fun appendThrowableHeader(sb: StringBuilder, current: Throwable, depth: Int) {
    if (depth > 0) {
        sb.append("\nCaused by: ")
    }
    val className = current.javaClass.name
    val message = current.message
    if (message.isNullOrBlank()) {
        sb.append(className)
    } else {
        sb.append(className).append(": ").append(message)
    }
}

private fun appendFrames(sb: StringBuilder, frames: Array<StackTraceElement>) {
    val usefulFrames = frames.filterIndexed { index, frame ->
        index < MAX_TOP_FRAMES || frame.className.startsWith(APP_PACKAGE_PREFIX)
    }.take(MAX_FRAMES_PER_THROWABLE)

    for (frame in usefulFrames) {
        sb.append("\n\tat ").append(frame)
    }
    val omitted = frames.size - usefulFrames.size
    if (omitted > 0) {
        sb.append("\n\t... ").append(omitted).append(" more")
    }
}
