package com.adulthub.tv.data.local

/**
 * Moves user-scoped data of installs without account isolation into the
 * namespace of the account that is signed in.
 *
 * The data was written before accounts were isolated, so it belongs to whoever
 * used the device last. Assigning it to the current account keeps the history
 * of an upgrade and removes it from the shared namespace.
 */
class UserScopedDataMigration(
    private val watchHistoryStore: WatchHistoryStore,
    private val searchHistoryStore: SearchHistoryStore,
    private val telegramProgressStore: TelegramProgressStore,
    private val phashJobStore: PhashJobStore,
) {
    suspend fun migrateToCurrentAccount() {
        watchHistoryStore.migrateLegacyHistory()
        searchHistoryStore.migrateLegacyHistory()
        telegramProgressStore.migrateLegacyProgress()
        phashJobStore.migrateLegacyJobs()
    }
}
