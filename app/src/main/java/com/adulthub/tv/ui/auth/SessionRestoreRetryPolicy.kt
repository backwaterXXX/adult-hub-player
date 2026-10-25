package com.adulthub.tv.ui.auth

/**
 * Bounds the retries of the saved-session discovery.
 *
 * The discovery of the saved server runs while the device may still be waking up:
 * a television that started the app during standby, or a phone that just left the
 * doze, has no route to the LAN for a few seconds. A single failed request used to
 * send the user to the connect screen, where the only way back to the saved session
 * was to restart the app. The policy therefore repeats the discovery a few times
 * before the failure is reported, and the delay grows so that a slow network has
 * time to come up.
 *
 * Every entry is the delay in milliseconds before its attempt; the first attempt
 * runs immediately, so the list always starts with `0`.
 */
class SessionRestoreRetryPolicy(
    private val delaysMs: List<Long> = DEFAULT_DELAYS_MS,
) {

    /** Number of discovery attempts, at least one. */
    val attempts: Int
        get() = delaysMs.size.coerceAtLeast(1)

    /** Delay before [attempt]; an unknown index keeps the last delay. */
    fun delayBefore(attempt: Int): Long = when {
        attempt <= 0 -> 0L
        delaysMs.isEmpty() -> 0L
        else -> delaysMs.getOrElse(attempt) { delaysMs.last() }
    }

    companion object {
        /** Immediate attempt, then 2 s, 5 s and 10 s for a network that is still waking up. */
        val DEFAULT_DELAYS_MS = listOf(0L, 2_000L, 5_000L, 10_000L)
    }
}
