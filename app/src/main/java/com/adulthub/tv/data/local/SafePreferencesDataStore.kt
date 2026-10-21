package com.adulthub.tv.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.adulthub.tv.diagnostics.AppLog as Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.IOException

private const val TAG = "PreferencesStore"

/**
 * Wraps a preferences store so a failed write does not take the app down.
 *
 * DataStore reports write failures as an [IOException]. An unguarded write
 * inside a view model scope crashes the process, so the failure is logged and
 * the unchanged state is returned. The UI keeps its recoverable state and the
 * next write can still succeed.
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
        Log.w(TAG, "Could not persist preferences (${error.javaClass.simpleName})")
        currentState()
    }

    private suspend fun currentState(): Preferences = try {
        delegate.data.first()
    } catch (_: IOException) {
        Log.w(TAG, "Could not read preferences")
        emptyPreferences()
    }
}
