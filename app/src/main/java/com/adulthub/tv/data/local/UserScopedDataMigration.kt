package com.adulthub.tv.data.local

/**
 * Imports the data of installs without account isolation.
 *
 * Versions before the account isolation wrote the history, the progress, the
 * search queries and the resolver jobs without an owner. That data belongs to
 * whoever used the device last, so it is never attributed to an account
 * automatically. It stays in its own namespace, where no account can see it,
 * until the owner of the device decides to import it.
 */
class UserScopedDataMigration(
    private val watchHistoryStore: WatchHistoryStore,
    private val searchHistoryStore: SearchHistoryStore,
    private val telegramProgressStore: TelegramProgressStore,
    private val phashJobStore: PhashJobStore,
    private val accountKeys: AccountKeyProvider,
) {
    /** True when unisolated data of an older install waits for an explicit import. */
    suspend fun hasPendingImport(): Boolean =
        watchHistoryStore.hasLegacyData() ||
            searchHistoryStore.hasLegacyData() ||
            telegramProgressStore.hasLegacyData() ||
            phashJobStore.hasLegacyData()

    /**
     * Imports the unisolated data into the account that is signed in now.
     *
     * Returns true when something was imported. A failed write is reported to the
     * caller, because the import must not look successful when it was not stored.
     */
    suspend fun importIntoCurrentAccount(): Boolean {
        val accountKey = accountKeys.currentKey()
        var imported = false
        if (watchHistoryStore.hasLegacyData()) {
            imported = watchHistoryStore.importLegacyData(accountKey) || imported
        }
        if (searchHistoryStore.hasLegacyData()) {
            imported = searchHistoryStore.importLegacyData(accountKey) || imported
        }
        if (telegramProgressStore.hasLegacyData()) {
            imported = telegramProgressStore.importLegacyData(accountKey) || imported
        }
        if (phashJobStore.hasLegacyData()) {
            imported = phashJobStore.importLegacyData(accountKey) || imported
        }
        return imported
    }
}
