package com.adulthub.tv.data.resolver

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class ResolverApiClient(
    okHttpClient: OkHttpClient? = null,
) {
    companion object {
        /**
         * Maximum number of cached resolver clients. Every resolver URL change creates a new
         * Retrofit instance, so the cache is bounded to keep the process memory stable.
         */
        const val MAX_CACHED_CLIENTS = 8
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val client = (okHttpClient ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build())
        // Resolver endpoints return JSON. Do not forward X-Emby-Token to redirect targets.
        .newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val longRunningClient = client.newBuilder()
        .readTimeout(11, TimeUnit.MINUTES)
        .build()
    private val apis = ResolverApiCache(MAX_CACHED_CLIENTS)
    private val longRunningApis = ResolverApiCache(MAX_CACHED_CLIENTS)

    fun create(baseUrl: String): ResolverApi = create(baseUrl, client, apis)

    fun createLongRunning(baseUrl: String): ResolverApi = create(baseUrl, longRunningClient, longRunningApis)

    private fun create(
        baseUrl: String,
        httpClient: OkHttpClient,
        cache: ResolverApiCache,
    ): ResolverApi {
        val normalizedUrl = baseUrl.trim().trimEnd('/') + "/"
        cache.get(normalizedUrl)?.let { return it }
        val contentType = "application/json".toMediaType()

        val api = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ResolverApi::class.java)
        return cache.put(normalizedUrl, api)
    }
}

/**
 * Bounded cache of resolver clients. The least recently used entry is dropped when the
 * maximum size is exceeded, so a changed resolver URL cannot pile up clients forever.
 */
private class ResolverApiCache(private val maxSize: Int) {
    private val entries = LinkedHashMap<String, ResolverApi>(16, 0.75f, true)

    @Synchronized
    fun get(url: String): ResolverApi? = entries[url]

    @Synchronized
    fun put(url: String, api: ResolverApi): ResolverApi {
        val previous = entries.put(url, api)
        while (entries.size > maxSize) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
        return previous ?: api
    }
}
