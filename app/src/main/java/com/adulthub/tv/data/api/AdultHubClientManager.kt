package com.adulthub.tv.data.api

import android.content.Context
import android.provider.Settings
import com.adulthub.tv.data.local.AuthDataStore
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.network.CleartextOriginInterceptor
import com.adulthub.tv.data.network.ImageTokenOriginPolicy
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

data class ClientSession(
    val serverUrl: String,
    val accessToken: String,
    val userId: String,
    val username: String = "",
    val refreshToken: String? = null
)

class AdultHubClientManager(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val authDataStore: AuthDataStore? = null
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

            identityPreferences.edit().putString(DEVICE_ID_KEY, stableId).commit()
            stableId
        }
    }

    private companion object {
        const val DEVICE_ID_PREFERENCES = "device_identity"
        const val DEVICE_ID_KEY = "adulthub_device_id"
        const val MAX_CACHED_APIS = 8
    }

    private val sessionRejectionStream = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Emits access tokens rejected with HTTP 401 while active. */
    val sessionRejections: Flow<String> = sessionRejectionStream.asSharedFlow()

    @Volatile
    private var _session: ClientSession? = null

    val currentSession: ClientSession?
        get() = _session

    fun getSessionSnapshot(): ClientSession? = _session

    val currentAccessToken: String?
        get() = _session?.accessToken

    val currentRefreshToken: String?
        get() = _session?.refreshToken

    val currentUserId: String?
        get() = _session?.userId

    val currentUsername: String?
        get() = _session?.username

    val currentServerUrl: String?
        get() = _session?.serverUrl

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val tokenRefreshLock = Any()

    private val refreshHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(CleartextOriginInterceptor(settingsDataStore))
        .build()

    private val authenticator: Authenticator = Authenticator { _: Route?, response: Response ->
        if (responseCount(response) >= 2) {
            return@Authenticator null
        }
        val requestPath = response.request.url.encodedPath
        if (requestPath.endsWith("/auth/login") || requestPath.endsWith("/auth/refresh") || requestPath.endsWith("/auth/revoke")) {
            return@Authenticator null
        }

        val snapshot = _session ?: return@Authenticator null
        val activeOrigin = snapshot.serverUrl
        if (activeOrigin.isBlank() || !ImageTokenOriginPolicy.allows(response.request.url.toString(), listOf(activeOrigin))) {
            return@Authenticator null
        }

        synchronized(tokenRefreshLock) {
            val sentAuth = response.request.header("Authorization")
            val sentToken = sentAuth?.removePrefix("Bearer ")?.trim()
            val latest = _session

            if (latest != null && sentToken != null && latest.accessToken != sentToken) {
                return@Authenticator response.request.newBuilder()
                    .header("Authorization", "Bearer ${latest.accessToken}")
                    .build()
            }

            val refreshToken = latest?.refreshToken
            if (refreshToken.isNullOrBlank()) {
                if (sentToken != null) {
                    sessionRejectionStream.tryEmit(sentToken)
                }
                return@Authenticator null
            }

            val refreshUrl = activeOrigin.trimEnd('/') + "/api/v1/auth/refresh"
            val refreshBody = json.encodeToString(AuthRefreshRequestDto.serializer(), AuthRefreshRequestDto(refreshToken))
                .toRequestBody("application/json".toMediaType())
            val refreshRequest = Request.Builder()
                .url(refreshUrl)
                .post(refreshBody)
                .build()

            val refreshResponse = try {
                refreshHttpClient.newCall(refreshRequest).execute()
            } catch (e: Exception) {
                // Transient network/IO error: retain session, do NOT emit rejection
                return@Authenticator null
            }

            refreshResponse.use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body.string()
                    val refreshed = json.decodeFromString(AuthRefreshResponseDto.serializer(), bodyStr)
                    val oldToken = latest.accessToken
                    val updated = updateTokensIfMatches(oldToken, refreshed.accessToken, refreshed.refreshToken)
                    if (updated) {
                        authDataStore?.let { store ->
                            runBlocking {
                                store.updateTokensIfOldTokenMatches(oldToken, refreshed.accessToken, refreshed.refreshToken)
                            }
                        }
                    }
                    return@Authenticator response.request.newBuilder()
                        .header("Authorization", "Bearer ${refreshed.accessToken}")
                        .build()
                } else {
                    val code = resp.code
                    // Transient 5xx: retain session!
                    if (code >= 500) {
                        return@Authenticator null
                    }
                    // 400 / 401 / 403: token expired / revoked / rejected
                    if (code == 400 || code == 401 || code == 403) {
                        if (sentToken != null) {
                            sessionRejectionStream.tryEmit(sentToken)
                        }
                    }
                    return@Authenticator null
                }
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .authenticator(authenticator)
        .addNetworkInterceptor(CleartextOriginInterceptor(settingsDataStore))
        .addInterceptor { chain ->
            val request = chain.request()
            val session = _session
            val token = session?.accessToken
            val activeOrigin = session?.serverUrl
            val requestUrl = request.url.toString()
            val mayCarryToken = !token.isNullOrBlank() &&
                !activeOrigin.isNullOrBlank() &&
                ImageTokenOriginPolicy.allows(requestUrl, listOf(activeOrigin))

            val reqBuilder = request.newBuilder()
            if (mayCarryToken && request.header("Authorization") == null) {
                reqBuilder.header("Authorization", "Bearer $token")
            }
            val response = chain.proceed(reqBuilder.build())
            if (response.code == 401) {
                val sentAuth = request.header("Authorization")
                val activeToken = _session?.accessToken
                val isAuthEndpoint = request.url.encodedPath.contains("/auth/")
                if (isAuthEndpoint && sentAuth != null && activeToken != null && sentAuth.contains(activeToken)) {
                    sessionRejectionStream.tryEmit(activeToken)
                }
            }
            response
        }
        .build()

    private val apiCache = LinkedHashMap<String, AdultHubApi>(MAX_CACHED_APIS, 0.75f, true)

    fun createApi(baseUrl: String, accessToken: String? = null): AdultHubApi {
        val normalizedUrl = baseUrl.trim().trimEnd('/') + "/"
        val cacheKey = if (accessToken != null) "$normalizedUrl#$accessToken" else normalizedUrl
        synchronized(apiCache) {
            apiCache[cacheKey]?.let { return it }
        }

        val clientToUse = if (accessToken != null) {
            httpClient.newBuilder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val reqUrl = original.url.toString()
                    val req = if (original.header("Authorization") == null &&
                        ImageTokenOriginPolicy.allows(reqUrl, listOf(normalizedUrl))
                    ) {
                        original.newBuilder().header("Authorization", "Bearer $accessToken").build()
                    } else {
                        original
                    }
                    chain.proceed(req)
                }
                .build()
        } else {
            httpClient
        }

        val api = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(clientToUse)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AdultHubApi::class.java)

        synchronized(apiCache) {
            apiCache[cacheKey] = api
            while (apiCache.size > MAX_CACHED_APIS) {
                val oldest = apiCache.keys.firstOrNull() ?: break
                apiCache.remove(oldest)
            }
        }
        return api
    }

    @Synchronized
    fun updateSession(
        serverUrl: String,
        accessToken: String,
        userId: String,
        username: String = "",
        refreshToken: String? = null
    ) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        _session = ClientSession(
            serverUrl = cleanUrl,
            accessToken = accessToken,
            userId = userId,
            username = username,
            refreshToken = refreshToken
        )
    }

    @Synchronized
    fun updateTokensIfMatches(
        expectedAccessToken: String,
        newAccessToken: String,
        newRefreshToken: String
    ): Boolean {
        val current = _session ?: return false
        if (current.accessToken != expectedAccessToken) return false
        _session = current.copy(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken
        )
        return true
    }

    @Synchronized
    fun clearSession() {
        clearSessionState()
    }

    @Synchronized
    fun clearSessionIfTokenMatches(accessToken: String): Boolean {
        if (_session?.accessToken != accessToken) return false
        clearSessionState()
        return true
    }

    private fun clearSessionState() {
        _session = null
        synchronized(apiCache) {
            apiCache.clear()
        }
    }

    fun getApi(): AdultHubApi {
        val url = currentServerUrl ?: throw IllegalStateException("Adult Hub client is not authenticated")
        return createApi(url)
    }

    fun getApiOrNull(): AdultHubApi? {
        val url = currentServerUrl ?: return null
        return createApi(url)
    }

    fun getItemImageUrl(item: TvItem, maxWidth: Int = 480): String {
        val base = currentServerUrl?.trimEnd('/') ?: ""
        if (!item.posterUrl.isNullOrBlank()) {
            val poster = item.posterUrl.trim()
            return if (poster.startsWith("http://", ignoreCase = true) || poster.startsWith("https://", ignoreCase = true) || base.isEmpty()) {
                poster
            } else {
                "$base/${poster.trimStart('/')}"
            }
        }
        if (!item.backdropUrl.isNullOrBlank()) {
            val backdrop = item.backdropUrl.trim()
            return if (backdrop.startsWith("http://", ignoreCase = true) || backdrop.startsWith("https://", ignoreCase = true) || base.isEmpty()) {
                backdrop
            } else {
                "$base/${backdrop.trimStart('/')}"
            }
        }
        return if (base.isNotEmpty()) "$base/api/v1/artwork/video/${item.id}/poster" else ""
    }

    fun getPersonImageUrl(personId: String, tag: String? = null, maxWidth: Int = 300, photoUrl: String? = null): String {
        val base = currentServerUrl?.trimEnd('/') ?: ""
        if (!photoUrl.isNullOrBlank()) {
            val photo = photoUrl.trim()
            return if (photo.startsWith("http://", ignoreCase = true) || photo.startsWith("https://", ignoreCase = true) || base.isEmpty()) {
                photo
            } else {
                "$base/${photo.trimStart('/')}"
            }
        }
        return if (base.isNotEmpty()) "$base/api/v1/artwork/performer/$personId/photo" else ""
    }
}
