package com.adulthub.tv.di

import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.AccountKeyProvider
import com.adulthub.tv.data.local.AccountScope
import com.adulthub.tv.data.local.SessionOwnerProvider
import com.adulthub.tv.data.local.AuthDataStore
import com.adulthub.tv.data.local.KeyStoreCrypto
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.local.SearchHistoryStore
import com.adulthub.tv.data.local.TelegramProgressStore
import com.adulthub.tv.data.local.PhashJobStore
import com.adulthub.tv.data.local.UserScopedDataMigration
import com.adulthub.tv.data.local.WatchHistoryStore
import com.adulthub.tv.data.resolver.ResolverApiClient
import com.adulthub.tv.data.resolver.DynamicResolverApi
import com.adulthub.tv.data.resolver.ResolverApi
import com.adulthub.tv.data.update.AppUpdateManager
import com.adulthub.tv.data.update.AppUpdateRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.binds
import org.koin.dsl.module

val appModule = module {
    single { KeyStoreCrypto() }
    single { AuthDataStore(androidContext(), get()) }
    // Namespaces user-scoped data so one account never sees the data of another.
    // The interface bindings keep one shared instance for the stores, for the
    // session owner and for the concrete scope.
    single { AccountScope(get()) } binds arrayOf(
        AccountKeyProvider::class,
        SessionOwnerProvider::class,
    )
    single { SettingsDataStore(androidContext(), get(), authDataStore = get(), accountKeyProvider = get()) }
    single { TelegramProgressStore(androidContext(), accountKeyProvider = get()) }
    single { PhashJobStore(androidContext(), accountKeyProvider = get()) }
    single { WatchHistoryStore(androidContext(), accountKeyProvider = get()) }
    single { SearchHistoryStore(androidContext(), accountKeyProvider = get()) }
    single { UserScopedDataMigration(get(), get(), get(), get(), get()) }
    single { JellyfinClientManager(androidContext()) }
    single { ResolverApiClient() }
    single { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO) }
    single { AppUpdateRepository(androidContext(), get()) }
    single { AppUpdateManager(get(), get(), get()) }
    single {
        com.adulthub.tv.player.PhashCoordinator(
            authDataStore = get(), jellyfinClientManager = get(), settingsDataStore = get(), apiClient = get(), applicationScope = get(),
            jobStore = get(),
            accountScope = get(),
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
