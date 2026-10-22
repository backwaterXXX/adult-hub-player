package com.adulthub.tv.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import java.io.IOException

/**
 * Reports that a preference write failed.
 *
 * The store boundary turns a failed write into this exception, so a caller can
 * tell a stored value from a value that could not be stored. A coroutine
 * cancellation is never converted into this exception.
 */
class PreferenceWriteException(message: String, cause: Throwable) : RuntimeException(message, cause)

/**
 * Wraps a preferences store so a failed write is reported instead of being
 * hidden.
 *
 * DataStore reports write failures as an [IOException]. Swallowing the error
 * would let the UI report a successful change that was never stored, so the
 * failure is passed to the caller as a [PreferenceWriteException].
 */
internal class SafePreferencesDataStore(
    private val delegate: DataStore<Preferences>,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> = delegate.data

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = try {
        delegate.updateData(transform)
    } catch (error: IOException) {
        throw PreferenceWriteException("The preference could not be stored", error)
    }
}
