package com.adulthub.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adulthub.tv.data.model.ChannelSortMode
import com.adulthub.tv.data.network.ServiceUrl
import com.adulthub.tv.data.network.ServiceUrlPolicy
import com.adulthub.tv.data.network.ServiceUrlValidation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

enum class BufferProfile(
    val id: String,
    val label: String,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playbackStartMs: Int,
    val rebufferMs: Int,
) {
    FAST_START("fast_start", "Fast start (5s / 20s)", 5_000, 20_000, 1_000, 2_000),
    STANDARD("standard", "Standard (15s / 50s)", 15_000, 50_000, 1_500, 3_000),
    LARGE("large", "Large buffer (30s / 120s)", 30_000, 120_000, 2_500, 5_000);

    companion object {
        fun fromId(id: String?): BufferProfile = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

@Serializable
data class AppConfigExport(
    val version: Int = 1,
    val selectedLibraryId: String? = null,
    val selectedLibraryName: String? = null,
    val resolverBaseUrl: String? = null,
    val cacheLimitMb: Int = 256,
    val phashOnPlayback: Boolean = false,
    val bufferProfile: String = "standard",
    val lowRamMode: Boolean = false,
    val seekStepSec: Int = 15,
    val autoplayNext: Boolean = false,
    val selectedHomeCategoryIds: Set<String>? = null,
    val hiddenChannelIds: Set<String> = emptySet(),
    val excludedTags: Set<String> = emptySet(),
    val autoUpdateEnabled: Boolean = true,
)

open class SettingsDataStore(
    private val context: Context,
    crypto: KeyStoreCrypto = KeyStoreCrypto(),
    private val dataStore: DataStore<Preferences> = context.settingsDataStore,
) {
    companion object {
        private val KEY_SCHEMA_VERSION = intPreferencesKey("schema_version")
        private val KEY_SELECTED_LIBRARY_ID = stringPreferencesKey("selected_library_id")
        private val KEY_SELECTED_LIBRARY_NAME = stringPreferencesKey("selected_library_name")
        private val KEY_RESOLVER_BASE_URL = stringPreferencesKey("resolver_base_url")
        private val LEGACY_KEY_UPDATE_BASE_URL = stringPreferencesKey("update_base_url")
        private val KEY_AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        private val KEY_LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        private val KEY_APPROVED_CLEARTEXT_ORIGINS = stringSetPreferencesKey("approved_cleartext_origins")
        private val KEY_CACHE_LIMIT_MB = intPreferencesKey("cache_limit_mb")
        private val KEY_PHASH_ON_PLAYBACK = booleanPreferencesKey("phash_on_playback")
        private val KEY_BUFFER_PROFILE = stringPreferencesKey("buffer_profile")
        private val KEY_LOW_RAM_MODE = booleanPreferencesKey("low_ram_mode")
        private val KEY_SEEK_STEP_SEC = intPreferencesKey("seek_step_sec")
        private val KEY_AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
        private val KEY_SELECTED_HOME_CATEGORY_IDS = stringSetPreferencesKey("selected_home_category_ids")
        private val KEY_CHANNEL_SORT_MODE = stringPreferencesKey("channel_sort_mode")
        private val KEY_HIDDEN_CHANNEL_IDS = stringSetPreferencesKey("hidden_channel_ids")
        private val KEY_EXCLUDED_TAGS = stringSetPreferencesKey("excluded_tags")

        const val DEFAULT_RESOLVER_URL = ""
        const val DEFAULT_CACHE_LIMIT_MB = 256
        const val DEFAULT_SEEK_STEP_SEC = 15
        const val CURRENT_SCHEMA_VERSION = 1
    }

    private val encryptedValue = EncryptedPreferenceValue(crypto)

    open val bufferProfile: Flow<BufferProfile> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { BufferProfile.fromId(it[KEY_BUFFER_PROFILE]) }

    open val schemaVersion: Flow<Int> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SCHEMA_VERSION] ?: CURRENT_SCHEMA_VERSION }

    open val selectedLibraryId: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { encryptedValue.decode(it[KEY_SELECTED_LIBRARY_ID]) }

    open val selectedLibraryName: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { encryptedValue.decode(it[KEY_SELECTED_LIBRARY_NAME]) }

    open val resolverBaseUrl: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            encryptedValue.decode(preferences[KEY_RESOLVER_BASE_URL])
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_RESOLVER_URL
        }

    open val approvedCleartextOrigins: Flow<Set<String>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            preferences[KEY_APPROVED_CLEARTEXT_ORIGINS].orEmpty()
                .mapNotNull { encryptedValue.decode(it) }
                .toSet()
        }

    open val autoUpdateEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AUTO_UPDATE_ENABLED] ?: true }

    open val lastUpdateCheck: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LAST_UPDATE_CHECK] ?: 0L }

    open suspend fun setAutoUpdateEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_UPDATE_ENABLED] = enabled }
    }

    open suspend fun setLastUpdateCheck(timestamp: Long) {
        dataStore.edit { it[KEY_LAST_UPDATE_CHECK] = timestamp }
    }

    open val cacheLimitMb: Flow<Int> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_CACHE_LIMIT_MB] ?: DEFAULT_CACHE_LIMIT_MB }

    open val phashOnPlayback: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_PHASH_ON_PLAYBACK] ?: false }

    open val lowRamMode: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_LOW_RAM_MODE] ?: false }

    open val seekStepSec: Flow<Int> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SEEK_STEP_SEC] ?: DEFAULT_SEEK_STEP_SEC }

    open val autoplayNext: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_AUTOPLAY_NEXT] ?: false }

    open val selectedHomeCategoryIds: Flow<Set<String>?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_SELECTED_HOME_CATEGORY_IDS] }

    open val channelSortMode: Flow<ChannelSortMode> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { ChannelSortMode.fromId(it[KEY_CHANNEL_SORT_MODE]) }

    open val hiddenChannelIds: Flow<Set<String>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_HIDDEN_CHANNEL_IDS] ?: emptySet() }

    open val excludedTags: Flow<Set<String>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_EXCLUDED_TAGS] ?: emptySet() }

    open suspend fun getSavedLibraryId(): String? = selectedLibraryId.first()

    open suspend fun getSavedLibraryName(): String? = selectedLibraryName.first()

    open suspend fun getSelectedLibraryId(): String? = getSavedLibraryId()

    open suspend fun getSelectedLibraryName(): String? = getSavedLibraryName()

    open suspend fun getResolverBaseUrl(): String = resolverBaseUrl.first()

    /** Encrypt existing plaintext values once, retaining compatibility with older app versions. */
    open suspend fun migrateSensitiveValues() {
        val snapshot = dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()
        val sensitiveValues = listOf(
            KEY_SELECTED_LIBRARY_ID to snapshot[KEY_SELECTED_LIBRARY_ID],
            KEY_SELECTED_LIBRARY_NAME to snapshot[KEY_SELECTED_LIBRARY_NAME],
            KEY_RESOLVER_BASE_URL to snapshot[KEY_RESOLVER_BASE_URL],
        )
        val replacements = sensitiveValues.mapNotNull { (key, oldValue) ->
            if (oldValue.isNullOrBlank() || encryptedValue.isEncrypted(oldValue)) null
            else Triple(key, oldValue, encryptedValue.encode(oldValue))
        }
        val oldOrigins = snapshot[KEY_APPROVED_CLEARTEXT_ORIGINS].orEmpty()
        val encryptedOrigins = oldOrigins.map { origin -> encryptedValue.encode(origin) }.toSet()
        val versionNeedsUpdate = snapshot[KEY_SCHEMA_VERSION] != CURRENT_SCHEMA_VERSION
        val hasLegacyUpdateSource = snapshot[LEGACY_KEY_UPDATE_BASE_URL] != null
        if (replacements.isEmpty() && oldOrigins == encryptedOrigins && !versionNeedsUpdate && !hasLegacyUpdateSource) return

        dataStore.edit { preferences ->
            replacements.forEach { (key, oldValue, newValue) ->
                if (preferences[key] == oldValue) preferences[key] = newValue
            }
            if (preferences[KEY_APPROVED_CLEARTEXT_ORIGINS].orEmpty() == oldOrigins) {
                preferences[KEY_APPROVED_CLEARTEXT_ORIGINS] = encryptedOrigins
            }
            preferences.remove(LEGACY_KEY_UPDATE_BASE_URL)
            preferences[KEY_SCHEMA_VERSION] = CURRENT_SCHEMA_VERSION
        }
    }

    open suspend fun isCleartextOriginApproved(url: String): Boolean {
        val serviceUrl = when (val validation = ServiceUrlPolicy.inspect(url)) {
            is ServiceUrlValidation.Valid -> validation.value
            is ServiceUrlValidation.Invalid -> return false
        }
        return !serviceUrl.usesCleartext || serviceUrl.origin in approvedCleartextOrigins.first()
    }

    open suspend fun approveCleartextOrigin(url: String) {
        val serviceUrl = when (val validation = ServiceUrlPolicy.inspect(url)) {
            is ServiceUrlValidation.Valid -> validation.value
            is ServiceUrlValidation.Invalid -> throw IllegalArgumentException(validation.reason)
        }
        require(serviceUrl.usesCleartext) { "Consent is only required for HTTP URLs" }
        while (true) {
            val snapshot = dataStore.data.first()
            val oldOrigins = snapshot[KEY_APPROVED_CLEARTEXT_ORIGINS].orEmpty()
            val currentOrigins = oldOrigins.mapNotNull { encryptedValue.decode(it) }.toSet()
            val newOrigins = (currentOrigins + serviceUrl.origin)
                .map { encryptedValue.encode(it) }
                .toSet()
            var updated = false
            dataStore.edit { preferences ->
                if (preferences[KEY_APPROVED_CLEARTEXT_ORIGINS].orEmpty() == oldOrigins) {
                    preferences[KEY_APPROVED_CLEARTEXT_ORIGINS] = newOrigins
                    updated = true
                }
            }
            if (updated) return
        }
    }

    open suspend fun revokeCleartextOrigin(origin: String) {
        while (true) {
            val snapshot = dataStore.data.first()
            val oldOrigins = snapshot[KEY_APPROVED_CLEARTEXT_ORIGINS].orEmpty()
            val currentOrigins = oldOrigins.mapNotNull { encryptedValue.decode(it) }.toSet()
            val newOrigins = (currentOrigins - origin)
                .map { encryptedValue.encode(it) }
                .toSet()
            var updated = false
            dataStore.edit { preferences ->
                if (preferences[KEY_APPROVED_CLEARTEXT_ORIGINS].orEmpty() == oldOrigins) {
                    preferences[KEY_APPROVED_CLEARTEXT_ORIGINS] = newOrigins
                    updated = true
                }
            }
            if (updated) return
        }
    }

    open suspend fun requireAllowedServiceUrl(url: String, allowQuery: Boolean = false): ServiceUrl {
        val serviceUrl = when (val validation = ServiceUrlPolicy.inspect(url, allowQuery)) {
            is ServiceUrlValidation.Valid -> validation.value
            is ServiceUrlValidation.Invalid -> throw IllegalArgumentException(validation.reason)
        }
        if (serviceUrl.usesCleartext && serviceUrl.origin !in approvedCleartextOrigins.first()) {
            throw IllegalStateException("Confirm unencrypted HTTP for this private LAN host before connecting")
        }
        return serviceUrl
    }

    open suspend fun setSelectedLibrary(libraryId: String, libraryName: String) {
        val encryptedLibraryId = encryptedValue.encode(libraryId)
        val encryptedLibraryName = encryptedValue.encode(libraryName)
        dataStore.edit { preferences ->
            preferences[KEY_SELECTED_LIBRARY_ID] = encryptedLibraryId
            preferences[KEY_SELECTED_LIBRARY_NAME] = encryptedLibraryName
        }
    }

    open suspend fun setResolverBaseUrl(url: String) {
        val sanitized = url.trimEnd('/')
        val encryptedUrl = sanitized.takeIf { it.isNotBlank() }?.let { encryptedValue.encode(it) }
        dataStore.edit { preferences ->
            if (sanitized.isBlank()) preferences.remove(KEY_RESOLVER_BASE_URL)
            else preferences[KEY_RESOLVER_BASE_URL] = requireNotNull(encryptedUrl)
        }
    }

    open suspend fun setCacheLimitMb(limitMb: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_CACHE_LIMIT_MB] = limitMb
        }
    }

    open suspend fun setPhashOnPlayback(enabled: Boolean) {
        dataStore.edit { it[KEY_PHASH_ON_PLAYBACK] = enabled }
    }

    open suspend fun setBufferProfile(profile: BufferProfile) {
        dataStore.edit { it[KEY_BUFFER_PROFILE] = profile.id }
    }

    open suspend fun setLowRamMode(enabled: Boolean) {
        dataStore.edit { it[KEY_LOW_RAM_MODE] = enabled }
    }

    open suspend fun setSeekStepSec(seconds: Int) {
        dataStore.edit { it[KEY_SEEK_STEP_SEC] = seconds }
    }

    open suspend fun setAutoplayNext(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTOPLAY_NEXT] = enabled }
    }

    open suspend fun setSelectedHomeCategoryIds(ids: Set<String>) {
        dataStore.edit { it[KEY_SELECTED_HOME_CATEGORY_IDS] = ids }
    }

    open suspend fun setChannelSortMode(mode: ChannelSortMode) {
        dataStore.edit { it[KEY_CHANNEL_SORT_MODE] = mode.id }
    }

    open suspend fun setHiddenChannelIds(ids: Set<String>) {
        dataStore.edit { it[KEY_HIDDEN_CHANNEL_IDS] = ids }
    }

    open suspend fun hideChannel(channelId: String) {
        dataStore.edit {
            val current = it[KEY_HIDDEN_CHANNEL_IDS] ?: emptySet()
            it[KEY_HIDDEN_CHANNEL_IDS] = current + channelId
        }
    }

    open suspend fun unhideChannel(channelId: String) {
        dataStore.edit {
            val current = it[KEY_HIDDEN_CHANNEL_IDS] ?: emptySet()
            it[KEY_HIDDEN_CHANNEL_IDS] = current - channelId
        }
    }

    open suspend fun setExcludedTags(tags: Set<String>) {
        dataStore.edit { it[KEY_EXCLUDED_TAGS] = tags }
    }

    open suspend fun excludeTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        dataStore.edit {
            val current = it[KEY_EXCLUDED_TAGS] ?: emptySet()
            it[KEY_EXCLUDED_TAGS] = current + trimmed
        }
    }

    open suspend fun removeExcludedTag(tag: String) {
        dataStore.edit {
            val current = it[KEY_EXCLUDED_TAGS] ?: emptySet()
            it[KEY_EXCLUDED_TAGS] = current - tag
        }
    }

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    open suspend fun exportNonSecretConfig(): AppConfigExport {
        return AppConfigExport(
            version = schemaVersion.first(),
            selectedLibraryId = getSelectedLibraryId(),
            selectedLibraryName = getSelectedLibraryName(),
            resolverBaseUrl = getResolverBaseUrl(),
            cacheLimitMb = cacheLimitMb.first(),
            phashOnPlayback = phashOnPlayback.first(),
            bufferProfile = bufferProfile.first().id,
            lowRamMode = lowRamMode.first(),
            seekStepSec = seekStepSec.first(),
            autoplayNext = autoplayNext.first(),
            selectedHomeCategoryIds = selectedHomeCategoryIds.first(),
            hiddenChannelIds = hiddenChannelIds.first(),
            excludedTags = excludedTags.first(),
            autoUpdateEnabled = autoUpdateEnabled.first(),
        )
    }

    open suspend fun exportNonSecretConfigJson(): String {
        return jsonConfig.encodeToString(AppConfigExport.serializer(), exportNonSecretConfig())
    }

    open suspend fun importNonSecretConfig(config: AppConfigExport) {
        val libId = config.selectedLibraryId
        val libName = config.selectedLibraryName
        if (libId != null && libName != null) {
            setSelectedLibrary(libId, libName)
        }
        config.resolverBaseUrl?.let { url ->
            setResolverBaseUrl(url)
        }
        setAutoUpdateEnabled(config.autoUpdateEnabled)
        setCacheLimitMb(config.cacheLimitMb)
        setPhashOnPlayback(config.phashOnPlayback)
        setBufferProfile(BufferProfile.fromId(config.bufferProfile))
        setLowRamMode(config.lowRamMode)
        setSeekStepSec(config.seekStepSec)
        setAutoplayNext(config.autoplayNext)
        config.selectedHomeCategoryIds?.let { setSelectedHomeCategoryIds(it) }
        setHiddenChannelIds(config.hiddenChannelIds)
        setExcludedTags(config.excludedTags)
        dataStore.edit { preferences ->
            preferences[KEY_SCHEMA_VERSION] = config.version.coerceAtLeast(CURRENT_SCHEMA_VERSION)
        }
    }

    open suspend fun importNonSecretConfigJson(jsonString: String) {
        val config = jsonConfig.decodeFromString(AppConfigExport.serializer(), jsonString)
        importNonSecretConfig(config)
    }
}
