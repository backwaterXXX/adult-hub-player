package com.adulthub.tv.diagnostics

import android.util.Log
import com.adulthub.tv.BuildConfig
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque

/** App-owned logs with a bounded, content-redacted buffer for user-requested exports. */
object AppLog {
    private const val MAX_ENTRIES = 500
    private const val MAX_MESSAGE_LENGTH = 1_000
    private val lock = Any()
    private val entries = ArrayDeque<String>(MAX_ENTRIES)
    private val timestampFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC)

    private val urlPattern = Regex("(?i)https?://[^\\s\\\"'<>]+")
    private val uuidPattern = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b")
    private val secretPattern = Regex("(?i)\\b(token|authorization|password|api[_-]?key)\\b\\s*[:=]\\s*[^\\s,;]+")
    private val identifierPattern = Regex("(?i)\\b(item|video|user|session|media[_-]?source)[_-]?id\\b\\s*[:=]\\s*[^\\s,;]+")
    private val titlePattern = Regex("(?i)\\b(title|filename|file[_-]?name)\\b\\s*[:=]\\s*[^,;\\n]+")

    fun d(tag: String, message: String): Int = write(Log.DEBUG, tag, message)
    fun i(tag: String, message: String): Int = write(Log.INFO, tag, message)
    fun w(tag: String, message: String): Int = write(Log.WARN, tag, message)
    fun e(tag: String, message: String): Int = write(Log.ERROR, tag, message)

    private fun write(priority: Int, tag: String, message: String): Int {
        val safeMessage = redact(message).take(MAX_MESSAGE_LENGTH)
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
            appendLine("Adult Hub TV diagnostics")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android API: ${android.os.Build.VERSION.SDK_INT}")
            appendLine("Exported at: ${timestampFormatter.format(Instant.now())}")
            appendLine("Recent app events: ${snapshot.size}")
            appendLine("Content titles, service URLs, account tokens, and UUIDs are redacted.")
            appendLine()
            if (snapshot.isEmpty()) appendLine("No diagnostic events have been recorded yet.")
            else snapshot.forEach(::appendLine)
        }
    }

    internal fun redact(message: String): String = message
        .replace(urlPattern, "[URL]")
        .replace(secretPattern, "$1=[REDACTED]")
        .replace(identifierPattern, "$1=[REDACTED]")
        .replace(titlePattern, "$1=[REDACTED]")
        .replace(uuidPattern, "[ID]")
}
