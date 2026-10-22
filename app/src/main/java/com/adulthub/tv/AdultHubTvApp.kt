package com.adulthub.tv

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.network.ImageTokenOriginPolicy
import com.adulthub.tv.data.update.AppUpdateManager
import com.adulthub.tv.di.appModule
import com.adulthub.tv.di.repositoryModule
import com.adulthub.tv.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class AdultHubTvApp : Application(), ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imageCacheLimitMb = AtomicInteger(SettingsDataStore.DEFAULT_CACHE_LIMIT_MB)
    private val settingsDataStore: SettingsDataStore by lazy { GlobalContext.get().get() }

    override fun onCreate() {
        super.onCreate()
        System.setProperty("kotlin-logging-to-android-native", "true")

        startKoin {
            androidContext(this@AdultHubTvApp)
            modules(
                appModule,
                repositoryModule,
                viewModelModule,
            )
        }

        if (!BuildConfig.DEBUG) GlobalContext.get().get<AppUpdateManager>().checkAutomaticallyIfDue()

        applicationScope.launch {
            launch {
                runCatching {
                    settingsDataStore.cacheLimitMb.collect(imageCacheLimitMb::set)
                }
            }
            launch {
                // Keeps the cleartext consent snapshot in sync with the persisted policy.
                runCatching {
                    settingsDataStore.approvedCleartextOrigins.collect { }
                }
            }
            launch {
                runCatching {
                    settingsDataStore.refreshApprovedCleartextOriginsSnapshot()
                }
            }
            launch {
                // The image loader reads the Resolver address without suspending.
                runCatching {
                    settingsDataStore.refreshResolverBaseUrlSnapshot()
                }
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val cacheLimitMb = imageCacheLimitMb.get()
        val clientManager = GlobalContext.get().get<JellyfinClientManager>()
        val imageHttpClient = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val urlString = request.url.toString()
                try {
                    settingsDataStore.requireAllowedImageUrl(urlString)
                } catch (error: Exception) {
                    throw IOException("Blocked image request to an unapproved HTTP origin", error)
                }
                val token = clientManager.getApiOrNull()?.accessToken
                // The token may reach the Jellyfin server and the Resolver only. The
                // Resolver serves the thumbnails of the video matching, and Coil asks
                // for them itself, so the header is decided here for every request.
                val mayCarryToken = ImageTokenOriginPolicy.allows(
                    urlString,
                    listOf(clientManager.currentServerUrl, settingsDataStore.resolverBaseUrlSnapshot()),
                )
                val authenticatedRequest = if (mayCarryToken && !token.isNullOrBlank()) {
                    request.newBuilder().header("X-Emby-Token", token).build()
                } else {
                    request.newBuilder().removeHeader("X-Emby-Token").build()
                }
                chain.proceed(authenticatedRequest)
            }
            .build()

        com.adulthub.tv.diagnostics.AppLog.i(
            "AdultHubTvApp",
            "Coil ImageLoader initialized: memoryCache=15% RAM, diskCache=${cacheLimitMb}MB, RGB_565",
        )

        return ImageLoader.Builder(this)
            .okHttpClient(imageHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(cacheLimitMb.coerceAtLeast(1).toLong() * 1024L * 1024L)
                    .build()
            }
            .diskCachePolicy(if (cacheLimitMb <= 0) CachePolicy.DISABLED else CachePolicy.ENABLED)
            .crossfade(false)
            .respectCacheHeaders(false)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowHardware(true)
            .build()
    }
}
