package com.adulthub.tv.ui.auth

import kotlinx.coroutines.delay

/**
 * Runs [discover] until it succeeds or the [policy] is exhausted, and returns the
 * last result. The first attempt runs immediately, every later attempt waits for
 * the delay of the policy, so a network that is still waking up gets the time it
 * needs instead of costing the user the saved session.
 */
internal suspend fun <T> discoverWithRetry(
    policy: SessionRestoreRetryPolicy,
    discover: suspend () -> Result<T>,
): Result<T> {
    var result: Result<T> = Result.failure(IllegalStateException("no attempt"))
    for (attempt in 0 until policy.attempts) {
        val delayMs = policy.delayBefore(attempt)
        if (delayMs > 0) delay(delayMs)
        result = discover()
        if (result.isSuccess) return result
    }
    return result
}
