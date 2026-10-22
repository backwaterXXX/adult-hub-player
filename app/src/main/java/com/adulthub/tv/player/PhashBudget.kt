package com.adulthub.tv.player

import com.adulthub.tv.data.local.StoredPhashJob

/**
 * The total budget of one video matching task.
 *
 * The budget has two bounds: a task may not live longer than [totalLifetimeMs] and
 * it may not be polled more than [maxPolls] times in one process. Without a bound a
 * task that the resolver never finishes would be polled for the whole life of the
 * app and would be started again at every launch.
 *
 * The budget only bounds what the app does. The resolver keeps working on the task,
 * so a thumbnail that is computed later is still written by the server.
 */
data class PhashBudget(
    val totalLifetimeMs: Long = DEFAULT_TOTAL_LIFETIME_MS,
    val maxPolls: Int = DEFAULT_MAX_POLLS,
) {
    init {
        require(totalLifetimeMs > 0L) { "totalLifetimeMs must be positive" }
        require(maxPolls > 0) { "maxPolls must be positive" }
    }

    /** True when the task is older than the total lifetime of the budget. */
    fun isExpired(startedAtMs: Long, nowMs: Long): Boolean = nowMs - startedAtMs >= totalLifetimeMs

    /**
     * True when the task must not be polled again.
     *
     * [polls] is the number of status requests that were already sent for the task.
     */
    fun isExhausted(startedAtMs: Long, nowMs: Long, polls: Int): Boolean =
        isExpired(startedAtMs, nowMs) || polls >= maxPolls

    /** True when a stored task may be continued when the app starts. */
    fun mayResume(record: StoredPhashJob, nowMs: Long): Boolean = !isExpired(record.startedAt, nowMs)

    companion object {
        /**
         * The total lifetime of one task.
         *
         * The value is generous on purpose: the pHash of one video takes seconds to
         * minutes, and a slow resolver must not lose its task.
         */
        const val DEFAULT_TOTAL_LIFETIME_MS = 15 * 60 * 1000L

        /** The number of status requests of one task in one process. */
        const val DEFAULT_MAX_POLLS = 120
    }
}
