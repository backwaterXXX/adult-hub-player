package com.adulthub.tv.ui.settings

import android.app.Application
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import com.adulthub.tv.R
import com.adulthub.tv.diagnostics.AppLog
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.AuthState
import com.adulthub.tv.data.model.TvCategory
import com.adulthub.tv.data.network.ServiceUrlPolicy
import com.adulthub.tv.data.network.ServiceUrlValidation
import com.adulthub.tv.data.repository.AuthRepository
import com.adulthub.tv.data.repository.ChannelRepository
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.TvLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

import com.adulthub.tv.data.local.SearchHistoryStore
import com.adulthub.tv.data.local.TelegramProgressStore
import com.adulthub.tv.data.local.UserScopedDataMigration
import com.adulthub.tv.data.local.WatchHistoryStore
import com.adulthub.tv.data.local.BufferProfile
import com.adulthub.tv.data.local.ResolverUrlSource

data class SettingsUiState(
    val serverUrl: String = "",
    val serverName: String = "",
    val username: String = "",
    val userId: String = "",
    val selectedLibraryId: String = "",
    val selectedLibraryName: String = "",
    val resolverBaseUrl: String = SettingsDataStore.DEFAULT_RESOLVER_URL,
    val resolverBaseUrlSource: ResolverUrlSource = ResolverUrlSource.DEFAULT,
    val resolverUrlDraft: String = SettingsDataStore.DEFAULT_RESOLVER_URL,
    val approvedCleartextOrigins: Set<String> = emptySet(),
    val pendingHttpConsentUrl: String? = null,
    val cacheLimitMb: Int = SettingsDataStore.DEFAULT_CACHE_LIMIT_MB,
    val phashOnPlayback: Boolean = false,
    val bufferProfile: BufferProfile = BufferProfile.STANDARD,
    val lowRamMode: Boolean = false,
    val seekStepSec: Int = 15,
    val autoplayNext: Boolean = false,
    val availableHomeCategories: List<TvCategory> = emptyList(),
    val selectedHomeCategoryIds: Set<String> = emptySet(),
    val hiddenChannelIds: Set<String> = emptySet(),
    /** Resolver channel titles by id, so the hidden list can be read by name. */
    val hiddenChannelNames: Map<String, String> = emptyMap(),
    val excludedTags: Set<String> = emptySet(),
    val availableLibraries: List<TvLibrary> = emptyList(),
    val isLoggedOut: Boolean = false,
    val hasPendingHistoryImport: Boolean = false,
    val statusMessage: String? = null,
    val isCheckingConnections: Boolean = false,
    val jellyfinConnectionStatus: String = "",
    val resolverConnectionStatus: String = "",
)

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val libraryRepository: LibraryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val channelRepository: ChannelRepository? = null,
    private val watchHistoryStore: WatchHistoryStore? = null,
    private val telegramProgressStore: TelegramProgressStore? = null,
    private val searchHistoryStore: SearchHistoryStore? = null,
    private val userScopedDataMigration: UserScopedDataMigration? = null,
    private val context: Application,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            jellyfinConnectionStatus = context.getString(R.string.settings_connection_not_checked),
            resolverConnectionStatus = context.getString(R.string.settings_connection_not_checked),
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Selection writes replace the whole set, so rapid toggles have to read the
    // result of the previous write instead of the state captured before it.
    private val homeCategorySelectionMutex = Mutex()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            try {
                loadSettingsSnapshot()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        statusMessage = context.getString(
                            R.string.settings_error_load,
                            error.localizedMessage ?: context.getString(R.string.error_storage),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun loadSettingsSnapshot() {
        val authState = authRepository.authState.firstOrNull()
        val (serverUrl, serverName, username, userId) = when (authState) {
            is AuthState.Authenticated -> listOf(
                authState.serverUrl,
                authState.serverName.ifEmpty { context.getString(R.string.settings_server_name_default) },
                authState.username,
                authState.userId,
            )
            else -> listOf("", "", "", "")
        }

        val libraryId = settingsDataStore.selectedLibraryId.firstOrNull().orEmpty()
        val libraryName = settingsDataStore.selectedLibraryName.firstOrNull().orEmpty()
        val resolverUrl = settingsDataStore.resolverBaseUrl.firstOrNull() ?: SettingsDataStore.DEFAULT_RESOLVER_URL
        val resolverUrlSource = settingsDataStore.resolverBaseUrlSource.firstOrNull() ?: ResolverUrlSource.DEFAULT
        val approvedHttpOrigins = settingsDataStore.approvedCleartextOrigins.firstOrNull().orEmpty()
        val cacheLimit = settingsDataStore.cacheLimitMb.firstOrNull() ?: SettingsDataStore.DEFAULT_CACHE_LIMIT_MB
        val phashOnPlayback = settingsDataStore.phashOnPlayback.firstOrNull() ?: false
        val bufferProfile = settingsDataStore.bufferProfile.firstOrNull() ?: BufferProfile.STANDARD
        val lowRamMode = settingsDataStore.lowRamMode.firstOrNull() ?: false
        val seekStepSec = settingsDataStore.seekStepSec.firstOrNull() ?: SettingsDataStore.DEFAULT_SEEK_STEP_SEC
        val autoplayNext = settingsDataStore.autoplayNext.firstOrNull() ?: false
        val savedHomeCategoryIds = settingsDataStore.selectedHomeCategoryIds.firstOrNull()
        val hiddenChannels = settingsDataStore.hiddenChannelIds.firstOrNull().orEmpty()
        val excluded = settingsDataStore.excludedTags.firstOrNull().orEmpty()
        val pendingImport = runCatching { userScopedDataMigration?.hasPendingImport() }.getOrNull() ?: false

        val librariesResult = libraryRepository.getLibraries()
        val libraries = librariesResult.getOrDefault(emptyList())
        val genresResult = libraryRepository.getGenres(limit = 200)
        val homeCategories = genresResult.getOrDefault(emptyList())
        val selectedHomeCategoryIds = savedHomeCategoryIds
            ?: homeCategories.take(2).map { it.id }.toSet()

        // An id alone tells the viewer nothing about the channel they hid, but the titles
        // live behind the resolver, so they are only requested when there is a list to label.
        val hiddenChannelNames = if (hiddenChannels.isEmpty()) {
            emptyMap()
        } else {
            channelRepository?.getCategories()?.getOrDefault(emptyList())
                ?.associate { channel -> channel.id to channel.title }
                ?: emptyMap()
        }

        _uiState.update {
            it.copy(
                serverUrl = serverUrl,
                serverName = serverName,
                username = username,
                userId = userId,
                selectedLibraryId = libraryId,
                selectedLibraryName = libraryName.ifEmpty { context.getString(R.string.settings_no_library_selected) },
                resolverBaseUrl = resolverUrl,
                resolverBaseUrlSource = resolverUrlSource,
                resolverUrlDraft = resolverUrl,
                approvedCleartextOrigins = approvedHttpOrigins,
                cacheLimitMb = cacheLimit,
                phashOnPlayback = phashOnPlayback,
                bufferProfile = bufferProfile,
                lowRamMode = lowRamMode,
                seekStepSec = seekStepSec,
                autoplayNext = autoplayNext,
                availableHomeCategories = homeCategories,
                selectedHomeCategoryIds = selectedHomeCategoryIds,
                hiddenChannelIds = hiddenChannels,
                hiddenChannelNames = hiddenChannelNames,
                excludedTags = excluded,
                availableLibraries = libraries,
                hasPendingHistoryImport = pendingImport,
            )
        }
    }

    /**
     * Imports the data of an install without account isolation.
     *
     * The data has no known owner, so it is only attached to an account when the
     * owner of the device asks for it. Until then it stays in its own namespace
     * and no account can see it.
     */
    fun importUnisolatedHistory() {
        val migration = userScopedDataMigration ?: return
        viewModelScope.launch {
            try {
                val imported = migration.importIntoCurrentAccount()
                _uiState.update {
                    it.copy(
                        hasPendingHistoryImport = false,
                        statusMessage = if (imported) {
                            context.getString(R.string.settings_import_done)
                        } else {
                            context.getString(R.string.settings_import_nothing)
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        statusMessage = context.getString(
                            R.string.settings_import_failed,
                            error.localizedMessage ?: context.getString(R.string.error_storage),
                        ),
                    )
                }
            }
        }
    }

    fun selectLibrary(library: TvLibrary) {
        viewModelScope.launch {
            if (!persist { settingsDataStore.setSelectedLibrary(library.id, library.name) }) return@launch
            val categoriesResult = libraryRepository.getGenres(limit = 200)
            val categories = categoriesResult.getOrDefault(emptyList())
            val savedCategoryIds = settingsDataStore.selectedHomeCategoryIds.firstOrNull()
            val availableCategoryIds = categories.map { it.id }.toSet()
            val selectedIds = when {
                categoriesResult.isFailure -> savedCategoryIds.orEmpty()
                savedCategoryIds == null -> categories.take(2).map { it.id }.toSet()
                savedCategoryIds.isNotEmpty() && savedCategoryIds.intersect(availableCategoryIds).isEmpty() ->
                    categories.take(2).map { it.id }.toSet()
                else -> savedCategoryIds.intersect(availableCategoryIds)
            }
            if (categoriesResult.isSuccess) {
                persist { settingsDataStore.setSelectedHomeCategoryIds(selectedIds) }
            }
            _uiState.update {
                it.copy(
                    selectedLibraryId = library.id,
                    selectedLibraryName = library.name,
                    availableHomeCategories = categories,
                    selectedHomeCategoryIds = selectedIds,
                    statusMessage = context.getString(R.string.settings_library_changed, library.name),
                )
            }
        }
    }

    fun setCacheLimit(limitMb: Int) {
        viewModelScope.launch {
            if (!persist { settingsDataStore.setCacheLimitMb(limitMb) }) return@launch
            _uiState.update {
                it.copy(
                    cacheLimitMb = limitMb,
                    statusMessage = if (limitMb == 0) {
                        context.getString(R.string.settings_cache_limit_set_off)
                    } else {
                        context.getString(R.string.settings_cache_limit_set, limitMb)
                    },
                )
            }
        }
    }

    fun setPhashOnPlayback(enabled: Boolean) {
        viewModelScope.launch {
            if (!persist { settingsDataStore.setPhashOnPlayback(enabled) }) return@launch
            _uiState.update { it.copy(phashOnPlayback = enabled) }
        }
    }

    fun setBufferProfile(profile: BufferProfile) {
        viewModelScope.launch {
            if (!persist { settingsDataStore.setBufferProfile(profile) }) return@launch
            _uiState.update {
                it.copy(
                    bufferProfile = profile,
                    statusMessage = context.getString(
                        R.string.settings_buffer_profile_set,
                        context.getString(profile.labelRes),
                    ),
                )
            }
        }
    }

    fun setLowRamMode(enabled: Boolean) {
        viewModelScope.launch {
            if (!persist { settingsDataStore.setLowRamMode(enabled) }) return@launch
            _uiState.update {
                it.copy(
                    lowRamMode = enabled,
                    statusMessage = if (enabled) {
                        context.getString(R.string.settings_low_ram_enabled)
                    } else {
                        context.getString(R.string.settings_low_ram_disabled)
                    },
                )
            }
        }
    }

    fun setSeekStepSec(seconds: Int) {
        viewModelScope.launch {
            if (!persist { settingsDataStore.setSeekStepSec(seconds) }) return@launch
            _uiState.update {
                it.copy(
                    seekStepSec = seconds,
                    statusMessage = context.resources.getQuantityString(
                        R.plurals.settings_seek_step_set,
                        seconds,
                        seconds,
                    ),
                )
            }
        }
    }

    fun setAutoplayNext(enabled: Boolean) {
        viewModelScope.launch {
            if (!persist { settingsDataStore.setAutoplayNext(enabled) }) return@launch
            _uiState.update {
                it.copy(
                    autoplayNext = enabled,
                    statusMessage = if (enabled) {
                        context.getString(R.string.settings_autoplay_enabled)
                    } else {
                        context.getString(R.string.settings_autoplay_disabled)
                    },
                )
            }
        }
    }

    fun toggleHomeCategory(categoryId: String) {
        viewModelScope.launch {
            homeCategorySelectionMutex.withLock {
                val currentIds = _uiState.value.selectedHomeCategoryIds
                val selectedIds = if (categoryId in currentIds) currentIds - categoryId else currentIds + categoryId
                if (!persist { settingsDataStore.setSelectedHomeCategoryIds(selectedIds) }) return@withLock
                _uiState.update {
                    it.copy(
                        selectedHomeCategoryIds = selectedIds,
                        statusMessage = context.getString(R.string.settings_home_categories_saved),
                    )
                }
            }
        }
    }

    fun setResolverUrl(url: String) {
        viewModelScope.launch {
            if (url.isBlank()) {
                persistResolverUrl("")
                return@launch
            }

            when (val validation = ServiceUrlPolicy.inspect(url)) {
                is ServiceUrlValidation.Invalid -> _uiState.update {
                    it.copy(statusMessage = validation.reason)
                }
                is ServiceUrlValidation.Valid -> {
                    val serviceUrl = validation.value
                    if (serviceUrl.usesCleartext && !settingsDataStore.isCleartextOriginApproved(serviceUrl.normalized)) {
                        _uiState.update { it.copy(pendingHttpConsentUrl = serviceUrl.normalized) }
                    } else {
                        persistResolverUrl(serviceUrl.normalized)
                    }
                }
            }
        }
    }

    fun confirmHttpConsent() {
        val url = _uiState.value.pendingHttpConsentUrl ?: return
        viewModelScope.launch {
            try {
                settingsDataStore.approveCleartextOrigin(url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        statusMessage = context.getString(
                            R.string.settings_error_http_consent,
                            error.localizedMessage ?: context.getString(R.string.error_storage),
                        ),
                    )
                }
                return@launch
            }
            val origins = settingsDataStore.approvedCleartextOrigins.firstOrNull().orEmpty()
            _uiState.update { it.copy(pendingHttpConsentUrl = null, approvedCleartextOrigins = origins) }
            persistResolverUrl(url)
        }
    }

    fun dismissHttpConsent() {
        _uiState.update { it.copy(pendingHttpConsentUrl = null) }
    }

    fun revokeCleartextConsent(origin: String) {
        viewModelScope.launch {
            if (!persist { settingsDataStore.revokeCleartextOrigin(origin) }) return@launch
            val origins = settingsDataStore.approvedCleartextOrigins.firstOrNull().orEmpty()
            val currentServerUsesOrigin = when (val validation = ServiceUrlPolicy.inspect(_uiState.value.serverUrl)) {
                is ServiceUrlValidation.Invalid -> false
                is ServiceUrlValidation.Valid -> validation.value.usesCleartext && validation.value.origin == origin
            }
            // The consent of the server origin is gone, so the session has to end. A
            // failed sign-out is reported instead of being shown as a closed account.
            val signedOut = if (currentServerUsesOrigin) {
                authRepository.logout().isSuccess
            } else {
                true
            }
            _uiState.update {
                it.copy(
                    approvedCleartextOrigins = origins,
                    statusMessage = if (signedOut) {
                        context.getString(R.string.settings_http_consent_removed, origin)
                    } else {
                        context.getString(R.string.settings_http_consent_removed_signout_failed, origin)
                    },
                    isLoggedOut = currentServerUsesOrigin && signedOut,
                )
            }
        }
    }

    private suspend fun persistResolverUrl(url: String) {
        if (!persist { settingsDataStore.setResolverBaseUrl(url) }) return
        val effectiveUrl = url.ifBlank { _uiState.value.serverUrl }
        val source = if (url.isNotBlank()) {
            ResolverUrlSource.CONFIGURED
        } else if (effectiveUrl.isNotBlank()) {
            ResolverUrlSource.SERVER_FALLBACK
        } else {
            ResolverUrlSource.DEFAULT
        }
        _uiState.update {
            it.copy(
                resolverBaseUrl = effectiveUrl,
                resolverBaseUrlSource = source,
                resolverUrlDraft = effectiveUrl,
                resolverConnectionStatus = context.getString(R.string.settings_connection_not_checked),
                statusMessage = if (url.isBlank()) {
                    context.getString(R.string.settings_resolver_using_server)
                } else {
                    context.getString(R.string.settings_resolver_updated)
                },
            )
        }
    }

    fun onResolverUrlDraftChanged(url: String) {
        _uiState.update { it.copy(resolverUrlDraft = url) }
    }

    fun checkConnections() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingConnections = true,
                    jellyfinConnectionStatus = context.getString(R.string.settings_connection_checking),
                    resolverConnectionStatus = context.getString(R.string.settings_connection_checking),
                )
            }
            val resolverUrl = settingsDataStore.getResolverBaseUrl()
            val (jellyfin, resolver) = coroutineScope {
                val jellyfinCheck = async { libraryRepository.getLibraries() }
                val resolverCheck = async { channelRepository?.getCategories() }
                jellyfinCheck.await() to resolverCheck.await()
            }
            _uiState.update {
                it.copy(
                    isCheckingConnections = false,
                    jellyfinConnectionStatus = connectionMessage(jellyfin),
                    resolverConnectionStatus = when {
                        resolver == null -> context.getString(R.string.settings_not_configured)
                        resolver.exceptionOrNull().isResolverNotServed() -> context.getString(
                            R.string.settings_resolver_not_served,
                            resolverUrl,
                        )
                        else -> connectionMessage(resolver)
                    },
                )
            }
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearCache(context: Context) {
        viewModelScope.launch {
            val cleared = withContext(Dispatchers.IO) { clearImageCache(context) }
            _uiState.update {
                it.copy(
                    statusMessage = if (cleared) {
                        context.getString(R.string.settings_cache_cleared)
                    } else {
                        context.getString(R.string.settings_cache_clear_failed)
                    },
                )
            }
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    private fun clearImageCache(context: Context): Boolean =
        runCatching {
            val imageLoader = Coil.imageLoader(context)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        }.isSuccess

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun exportDiagnostics(context: Context, destination: Uri) {
        val report = AppLog.exportText()
        viewModelScope.launch {
            val writeResult = withContext(Dispatchers.IO) {
                runCatching {
                    val output = context.contentResolver.openOutputStream(destination, "wt")
                        ?: error("Could not open the selected destination")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(report) }
                }
            }

            if (writeResult.isFailure) {
                _uiState.update {
                    it.copy(statusMessage = context.getString(com.adulthub.tv.R.string.settings_diagnostics_failed))
                }
                return@launch
            }

            _uiState.update {
                it.copy(statusMessage = context.getString(com.adulthub.tv.R.string.settings_diagnostics_saved))
            }
            runCatching {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, destination)
                    clipData = ClipData.newUri(context.contentResolver, "Adult Hub diagnostics", destination)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(
                    shareIntent,
                    context.getString(com.adulthub.tv.R.string.settings_diagnostics_share_title),
                ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            if (!clearStore(context.getString(R.string.settings_label_watch_history)) { watchHistoryStore?.clearHistory() }) return@launch
            _uiState.update {
                it.copy(statusMessage = context.getString(R.string.settings_clear_history_done))
            }
        }
    }

    fun clearResumeProgress() {
        viewModelScope.launch {
            if (!clearStore(context.getString(R.string.settings_label_resume_progress)) { telegramProgressStore?.clearAll() }) return@launch
            _uiState.update {
                it.copy(statusMessage = context.getString(R.string.settings_clear_resume_done))
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            if (!clearStore(context.getString(R.string.settings_label_search_history)) { searchHistoryStore?.clearHistory() }) return@launch
            _uiState.update {
                it.copy(statusMessage = context.getString(R.string.settings_clear_search_done))
            }
        }
    }

    fun unhideChannel(channelId: String) {
        viewModelScope.launch {
            if (!persist { settingsDataStore.unhideChannel(channelId) }) return@launch
            _uiState.update { state ->
                state.copy(
                    hiddenChannelIds = state.hiddenChannelIds - channelId,
                    statusMessage = context.getString(R.string.settings_channel_unhidden),
                )
            }
        }
    }

    fun removeExcludedTag(tag: String) {
        viewModelScope.launch {
            if (!persist { settingsDataStore.removeExcludedTag(tag) }) return@launch
            _uiState.update { state ->
                state.copy(
                    excludedTags = state.excludedTags - tag,
                    statusMessage = context.getString(R.string.settings_tag_unexcluded, tag),
                )
            }
        }
    }

    fun excludeTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            if (!persist { settingsDataStore.excludeTag(trimmed) }) return@launch
            _uiState.update { state ->
                state.copy(
                    excludedTags = state.excludedTags + trimmed,
                    statusMessage = context.getString(R.string.settings_tag_excluded, trimmed),
                )
            }
        }
    }

    fun logout(cacheContext: Context? = null) {
        viewModelScope.launch {
            cacheContext?.let { withContext(Dispatchers.IO) { clearImageCache(it) } }
            // The session is only closed when the stored session could be cleared.
            authRepository.logout().onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoggedOut = false,
                        statusMessage = context.getString(
                            R.string.settings_error_sign_out,
                            error.localizedMessage ?: context.getString(R.string.error_storage),
                        ),
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isLoggedOut = true) }
        }
    }

    /**
     * Runs a settings write.
     *
     * Returns false when the write failed. The store reports the failure, so the
     * screen must not show a new value as saved when it was never stored.
     */
    private suspend fun persist(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        _uiState.update {
            it.copy(
                statusMessage = context.getString(
                    R.string.settings_error_save,
                    error.localizedMessage ?: context.getString(R.string.error_storage),
                ),
            )
        }
        false
    }

    /**
     * Runs a data-clearing write.
     *
     * Returns false when the store rejected it. A DataStore failure here is an
     * IOException from the disk, not a programming error, so it is reported instead
     * of reaching the coroutine's uncaught handler.
     */
    private suspend fun clearStore(label: String, block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        _uiState.update {
            it.copy(
                statusMessage = context.getString(
                    R.string.settings_error_clear,
                    label,
                    error.localizedMessage ?: context.getString(R.string.error_storage),
                ),
            )
        }
        false
    }

    /**
     * A 404 or 405 from the Resolver endpoint means the address answers but does
     * not serve the Resolver, which is the LAN Jellyfin fallback case. Other
     * failures stay generic, so a timeout is not reported as a wrong address.
     */
    private fun Throwable?.isResolverNotServed(): Boolean =
        this is retrofit2.HttpException && code() in setOf(404, 405)

    private fun <T> connectionMessage(result: Result<T>): String = result.fold(
        onSuccess = { context.getString(R.string.settings_connection_connected) },
        onFailure = { error ->
            context.getString(
                R.string.settings_connection_unavailable,
                error.localizedMessage ?: context.getString(R.string.settings_error_request_failed),
            ).take(100)
        },
    )
}
