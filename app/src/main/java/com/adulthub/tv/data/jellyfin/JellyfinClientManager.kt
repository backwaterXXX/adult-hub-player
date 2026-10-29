package com.adulthub.tv.data.jellyfin

import android.content.Context
import android.provider.Settings
import com.adulthub.tv.BuildConfig
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.network.CleartextOriginInterceptor
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import java.util.UUID

class JellyfinClientManager(
    private val context: Context,
    settingsDataStore: SettingsDataStore,
) {
    val deviceId: String by lazy {
        val identityPreferences = context.applicationContext
            .getSharedPreferences(DEVICE_ID_PREFERENCES, Context.MODE_PRIVATE)
        identityPreferences.getString(DEVICE_ID_KEY, null) ?: run {
            val androidIdSeed = runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull()?.takeIf(String::isNotBlank)
            val stableId = androidIdSeed?.let { seed ->
                UUID.nameUUIDFromBytes("adult-hub-tv:$seed".toByteArray(Charsets.UTF_8)).toString()
            } ?: UUID.randomUUID().toString()

            // commit() makes the generated fallback available before Jellyfin creates its client.
            identityPreferences.edit().putString(DEVICE_ID_KEY, stableId).commit()
            stableId
        }
    }

    private companion object {
        const val DEVICE_ID_PREFERENCES = "device_identity"
        const val DEVICE_ID_KEY = "jellyfin_device_id"
    }

    private val sessionRejectionStream = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** A Jellyfin call carried this token and the server answered with HTTP 401. */
    val sessionRejections: Flow<String> = sessionRejectionStream.asSharedFlow()

    @Volatile
    private var currentAccessToken: String? = null

    private val httpClient = OkHttpClient.Builder()
        .addNetworkInterceptor(CleartextOriginInterceptor(settingsDataStore))
        // A rejected token is only reported for the session that is active now, so a
        // late 401 of an earlier session cannot sign out its replacement.
        .addInterceptor(
            JellyfinSessionRejectionInterceptor { rejectedToken ->
                if (rejectedToken == currentAccessToken) {
                    sessionRejectionStream.tryEmit(rejectedToken)
                }
            }
        )
        .build()

    private val jellyfin: Jellyfin = createJellyfin {
        this.context = this@JellyfinClientManager.context
        clientInfo = ClientInfo(
            name = BuildConfig.CLIENT_NAME,
            version = BuildConfig.VERSION_NAME
        )
        deviceInfo = DeviceInfo(
            id = deviceId,
            name = BuildConfig.DEVICE_NAME
        )
        val apiFactory = OkHttpFactory(httpClient)
        apiClientFactory = apiFactory
        socketConnectionFactory = apiFactory
    }

    val discovery get() = jellyfin.discovery

    @Volatile
    private var currentApi: ApiClient? = null

    @Volatile
    var currentUserId: String? = null
        private set

    @Volatile
    var currentServerUrl: String? = null
        private set

    fun createApi(baseUrl: String, accessToken: String? = null): ApiClient {
        val normalized = if (baseUrl.endsWith("/")) baseUrl.trimEnd('/') else baseUrl
        return jellyfin.createApi(
            baseUrl = normalized,
            accessToken = accessToken,
        )
    }

    @Synchronized
    fun updateSession(serverUrl: String, accessToken: String, userId: String) {
        currentServerUrl = serverUrl
        currentUserId = userId
        currentAccessToken = accessToken
        currentApi = createApi(serverUrl, accessToken)
    }

    @Synchronized
    fun clearSession() {
        clearSessionState()
    }

    /** Clears the client only while its active token is [accessToken]. */
    @Synchronized
    fun clearSessionIfTokenMatches(accessToken: String): Boolean {
        if (currentAccessToken != accessToken) return false
        clearSessionState()
        return true
    }

    private fun clearSessionState() {
        currentServerUrl = null
        currentUserId = null
        currentAccessToken = null
        currentApi = null
    }

    fun getApi(): ApiClient {
        return currentApi ?: throw IllegalStateException("Jellyfin client is not authenticated")
    }

    fun getApiOrNull(): ApiClient? = currentApi

    fun getImageUrl(
        itemId: String,
        imageType: String = "Primary",
        maxWidth: Int = 480,
        tag: String? = null,
    ): String {
        val base = currentServerUrl?.trimEnd('/') ?: ""
        val tagParam = if (!tag.isNullOrBlank()) "&tag=$tag" else ""
        return "$base/Items/$itemId/Images/$imageType?maxWidth=$maxWidth$tagParam"
    }

    fun getItemImageUrl(item: com.adulthub.tv.data.model.TvItem, maxWidth: Int = 480): String {
        if (!item.posterUrl.isNullOrBlank()) {
            return item.posterUrl
        }
        val type = when {
            !item.thumbImageTag.isNullOrBlank() -> "Thumb"
            !item.backdropImageTag.isNullOrBlank() -> "Backdrop"
            else -> "Primary"
        }
        val tag = when (type) {
            "Thumb" -> item.thumbImageTag
            "Backdrop" -> item.backdropImageTag
            else -> item.primaryImageTag
        }
        return getImageUrl(item.id, type, maxWidth, tag)
    }

    fun getPersonImageUrl(personId: String, tag: String? = null, maxWidth: Int = 300): String {
        return getImageUrl(personId, "Primary", maxWidth, tag)
    }
}
