package com.adulthub.tv.di

import com.adulthub.tv.data.local.AccountScope
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.local.TelegramProgressStore
import com.adulthub.tv.data.repository.AuthRepository
import com.adulthub.tv.data.repository.ChannelRepository
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.PersonRepository
import com.adulthub.tv.data.repository.PlaybackRepository
import com.adulthub.tv.data.repository.StudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.koin.dsl.module

val repositoryModule = module {
    single { AuthRepository(get(), get(), get()) }
    single { LibraryRepository(get(), get(), get<TelegramProgressStore>(), get()) }
    single { PersonRepository(get(), get(), get()) }
    single { StudioRepository(get(), get()) }
    single {
        val channelRepository = ChannelRepository(get(), get())
        val scope = get<CoroutineScope>()
        val accountScope = get<AccountScope>()
        val settingsDataStore = get<SettingsDataStore>()
        // Cached videos belong to one account and one resolver origin.
        scope.launch {
            combine(accountScope.accountKey, settingsDataStore.resolverBaseUrl) { accountKey, resolverUrl ->
                "$accountKey|$resolverUrl"
            }
                .distinctUntilChanged()
                .drop(1)
                .collect { channelRepository.invalidateCache() }
        }
        channelRepository
    }
    single { PlaybackRepository(get(), get()) }
}
