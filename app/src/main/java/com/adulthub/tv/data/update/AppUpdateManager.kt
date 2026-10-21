package com.adulthub.tv.data.update

import com.adulthub.tv.data.local.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
) {
    private val _state = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()
    private val operation = Mutex()

    init {
        scope.launch {
            _state.value = _state.value.copy(
                enabled = settings.autoUpdateEnabled.first(),
            )
            val restoredUpdate = repository.restoreDownloadedUpdate()
            if (restoredUpdate != null) {
                _state.value = _state.value.copy(
                    update = restoredUpdate,
                    downloadProgress = 1f,
                    message = "Version ${restoredUpdate.manifest.versionName} is ready to install",
                )
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
        settings.setAutoUpdateEnabled(enabled)
        _state.value = _state.value.copy(enabled = enabled)
        if (enabled) checkAutomaticallyIfDue()
    }

    fun showMessage(message: String) {
        _state.value = _state.value.copy(message = message)
    }

    private suspend fun checkAndDownload() {
        _state.value = _state.value.copy(isChecking = true, isDownloading = false, downloadProgress = 0f, message = "Checking for updates…")
        try {
            val result = repository.checkAndDownload { received, total ->
                _state.value = _state.value.copy(
                    isChecking = false,
                    isDownloading = true,
                    downloadProgress = if (total <= 0) 0f else (received.toFloat() / total).coerceIn(0f, 1f),
                    message = "Downloading update…",
                )
            }
            if (result !is UpdateCheckResult.Failed) {
                settings.setLastUpdateCheck(System.currentTimeMillis())
            }
            _state.value = when (result) {
                is UpdateCheckResult.UpdateReady -> _state.value.copy(
                    isChecking = false,
                    isDownloading = false,
                    downloadProgress = 1f,
                    update = result.update,
                    message = "Version ${result.update.manifest.versionName} is ready to install",
                )
                UpdateCheckResult.UpToDate -> _state.value.copy(
                    isChecking = false,
                    isDownloading = false,
                    update = null,
                    message = "App is up to date",
                )
                is UpdateCheckResult.Failed -> _state.value.copy(
                    isChecking = false,
                    isDownloading = false,
                    message = result.message,
                )
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.value = _state.value.copy(message = error.message ?: "Update check failed")
        } finally {
            // A cancelled check must not leave the screen stuck on "Downloading update…".
            _state.value = _state.value.copy(isChecking = false, isDownloading = false)
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
