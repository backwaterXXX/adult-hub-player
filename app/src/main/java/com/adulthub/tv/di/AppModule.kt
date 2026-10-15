package com.adulthub.tv.di

import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.AuthDataStore
import com.adulthub.tv.data.local.KeyStoreCrypto
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.local.SearchHistoryStore
import com.adulthub.tv.data.local.TelegramProgressStore
import com.adulthub.tv.data.local.PhashJobStore
import com.adulthub.tv.data.local.WatchHistoryStore
import com.adulthub.tv.data.resolver.ResolverApiClient
import com.adulthub.tv.data.resolver.DynamicResolverApi
import com.adulthub.tv.data.resolver.ResolverApi
import com.adulthub.tv.data.update.AppUpdateManager
import com.adulthub.tv.data.update.AppUpdateRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { KeyStoreCrypto() }
    single { AuthDataStore(androidContext(), get()) }
    single { SettingsDataStore(androidContext(), get()) }
    single { TelegramProgressStore(androidContext()) }
    single { PhashJobStore(androidContext()) }
    single { WatchHistoryStore(androidContext()) }
    single { SearchHistoryStore(androidContext()) }
    single { JellyfinClientManager(androidContext()) }
    single { ResolverApiClient() }
    single { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO) }
    single { AppUpdateRepository(androidContext(), get()) }
    single { AppUpdateManager(get(), get(), get()) }
    single {
        com.adulthub.tv.player.PhashCoordinator(
            authDataStore = get(), jellyfinClientManager = get(), settingsDataStore = get(), apiClient = get(), applicationScope = get(),
            jobStore = get(),
        )
    }
    single<ResolverApi> { DynamicResolverApi(get(), get()) }
    single {
        com.adulthub.tv.player.PlaybackReporter(
            playbackRepository = get(),
            coroutineScope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
            ),
        )
    }
}
