package com.adulthub.tv.data.resolver

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ResolverApiClient(
    okHttpClient: OkHttpClient? = null,
) {
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
    private val apis = ConcurrentHashMap<String, ResolverApi>()
    private val longRunningApis = ConcurrentHashMap<String, ResolverApi>()

    fun create(baseUrl: String): ResolverApi = create(baseUrl, client, apis)

    fun createLongRunning(baseUrl: String): ResolverApi = create(baseUrl, longRunningClient, longRunningApis)

    private fun create(
        baseUrl: String,
        httpClient: OkHttpClient,
        cache: ConcurrentHashMap<String, ResolverApi>,
    ): ResolverApi {
        val normalizedUrl = baseUrl.trim().trimEnd('/') + "/"
        return cache.getOrPut(normalizedUrl) {
            val contentType = "application/json".toMediaType()

            Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(httpClient)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(ResolverApi::class.java)
        }
    }
}
