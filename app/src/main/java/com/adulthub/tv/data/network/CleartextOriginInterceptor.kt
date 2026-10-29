package com.adulthub.tv.data.network

import com.adulthub.tv.data.local.SettingsDataStore
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rejects cleartext HTTP to an origin that the user has not approved.
 *
 * Every HTTP client installs this as a network interceptor, so the policy also
 * covers every redirect hop and cannot be bypassed by a new call path.
 */
class CleartextOriginInterceptor(
    private val settingsDataStore: SettingsDataStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.isHttps) return chain.proceed(request)
        try {
            settingsDataStore.requireAllowedNetworkUrl(request.url.toString())
        } catch (error: Exception) {
            throw IOException("Blocked a cleartext request to an unapproved origin", error)
        }
        return chain.proceed(request)
    }
}
