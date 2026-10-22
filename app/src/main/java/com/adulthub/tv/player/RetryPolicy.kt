package com.adulthub.tv.player

/**
 * Tracks the automatic retry budget of a playback.
 *
 * The budget covers consecutive failures. It is given back after the media really
 * played for a while. Only the wall clock time between two ticks counts, and only
 * while the player is ready and playing: a seek, a pause, a buffering phase or a
 * changed speed cannot shorten the way to a stable playback, and a jump of the
 * position cannot fake it either.
 */
internal class RetryBudget(
    private val stablePlaybackThresholdMs: Long,
) {
    private var attempts = 0
    private var stablePlaybackMs = 0L
    private var lastPlayingTickMs: Long? = null

    /** The number of automatic retries that were used. */
    val attemptCount: Int
        get() = attempts

    /** True when another automatic retry is allowed. */
    fun canRetry(maxAttempts: Int): Boolean = attempts < maxAttempts

    /** Registers a failure and returns the number of the attempt. */
    fun registerFailure(): Int {
        attempts += 1
        return attempts
    }

    /**
     * Records the time between two ticks.
     *
     * [playing] is true while the player is ready and not paused. A buffering
     * phase and a pause stop the measurement.
     */
    fun track(nowMs: Long, playing: Boolean) {
        val previous = lastPlayingTickMs
        lastPlayingTickMs = if (playing) nowMs else null
        if (!playing || previous == null) return
        stablePlaybackMs += (nowMs - previous).coerceAtLeast(0L)
        if (stablePlaybackMs < stablePlaybackThresholdMs) return
        stablePlaybackMs = 0L
        attempts = 0
    }

    /** Forgets the budget, for example when a new playback starts. */
    fun reset() {
        attempts = 0
        stablePlaybackMs = 0L
        lastPlayingTickMs = null
    }
}

/**
 * The delay of an automatic retry.
 *
 * The first retry waits a second, the next ones twice as long.
 */
internal fun retryDelayMs(attempt: Int): Long = 1_000L shl (attempt - 1).coerceAtLeast(0)

/**
 * Resolves the stream address for an automatic retry.
 *
 * The address of a stream can expire while the video plays. The retry therefore
 * asks the owner of the playback for a new address and keeps the position. When
 * the owner cannot provide one, the retry fails instead of using the expired
 * address a second time.
 */
internal suspend fun resolveRetryStreamUrl(
    provider: (suspend (itemId: String, positionMs: Long) -> String?)?,
    itemId: String,
    positionMs: Long,
    fallbackUrl: String,
): String? {
    if (provider == null) return fallbackUrl
    val refreshed = runCatching { provider(itemId, positionMs) }.getOrNull()
    return refreshed?.takeIf { it.isNotBlank() }
}
