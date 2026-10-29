package com.adulthub.tv.data.update

import android.content.Context
import com.adulthub.tv.R
import com.adulthub.tv.data.local.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AppUpdateState(
    val enabled: Boolean = true,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val update: AvailableUpdate? = null,
    val message: String = "",
)

class AppUpdateManager(
    private val repository: AppUpdateRepository,
    private val settings: SettingsDataStore,
    private val scope: CoroutineScope,
    private val context: Context,
) {
    private val _state = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()
    private val operation = Mutex()

    /**
     * Serializes writes to [AppUpdateState.enabled] with the settings read in init.
     *
     * The operation lock cannot be used here: a check may be downloading an APK for
     * minutes, and the auto-update switch must not wait for it.
     */
    private val enabledLock = Mutex()

    init {
        scope.launch {
            val restoredUpdate = repository.restoreDownloadedUpdate()
            enabledLock.withLock {
                val enabled = settings.autoUpdateEnabled.first()
                _state.update { state ->
                    // A check that already finished owns the update slot; a late
                    // restore must not replace its result.
                    if (restoredUpdate == null || state.update != null) {
                        state.copy(enabled = enabled)
                    } else {
                        state.copy(
                            enabled = enabled,
                            update = restoredUpdate,
                            downloadProgress = 1f,
                            message = context.getString(
                                R.string.settings_update_version_ready,
                                restoredUpdate.manifest.versionName,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun checkAutomaticallyIfDue() {
        scope.launch {
            val enabled = settings.autoUpdateEnabled.first()
            val lastCheck = settings.lastUpdateCheck.first()
            if (enabled && (lastCheck == 0L || System.currentTimeMillis() - lastCheck >= CHECK_INTERVAL_MS)) {
                // An automatic check never queues up behind a running one.
                if (!operation.tryLock()) return@launch
                try {
                    checkAndDownload()
                } finally {
                    operation.unlock()
                }
            }
        }
    }

    fun checkNow() {
        // A check the user asked for is never dropped: it waits for a running
        // check and then runs.
        scope.launch {
            operation.withLock { checkAndDownload() }
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        enabledLock.withLock {
            settings.setAutoUpdateEnabled(enabled)
            _state.update { it.copy(enabled = enabled) }
        }
        if (enabled) checkAutomaticallyIfDue()
    }

    fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    private suspend fun checkAndDownload() {
        _state.update {
            it.copy(
                isChecking = true,
                isDownloading = false,
                downloadProgress = 0f,
                message = context.getString(R.string.settings_update_checking),
            )
        }
        try {
            val result = repository.checkAndDownload { received, total ->
                _state.update {
                    it.copy(
                        isChecking = false,
                        isDownloading = true,
                        downloadProgress = if (total <= 0) 0f else (received.toFloat() / total).coerceIn(0f, 1f),
                        message = context.getString(R.string.settings_update_downloading),
                    )
                }
            }
            if (result !is UpdateCheckResult.Failed) {
                settings.setLastUpdateCheck(System.currentTimeMillis())
            }
            _state.update { state ->
                when (result) {
                    is UpdateCheckResult.UpdateReady -> state.copy(
                        isChecking = false,
                        isDownloading = false,
                        downloadProgress = 1f,
                        update = result.update,
                        message = context.getString(
                            R.string.settings_update_version_ready,
                            result.update.manifest.versionName,
                        ),
                    )
                    UpdateCheckResult.UpToDate -> state.copy(
                        isChecking = false,
                        isDownloading = false,
                        update = null,
                        message = context.getString(R.string.settings_update_up_to_date),
                    )
                    is UpdateCheckResult.Failed -> state.copy(
                        isChecking = false,
                        isDownloading = false,
                        message = result.message,
                    )
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    message = error.message ?: context.getString(R.string.settings_update_check_failed),
                )
            }
        } finally {
            // A cancelled check must not leave the screen stuck on "Downloading update…".
            _state.update { it.copy(isChecking = false, isDownloading = false) }
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
