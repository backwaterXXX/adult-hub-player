package com.adulthub.tv.di

import com.adulthub.tv.ui.auth.AuthViewModel
import com.adulthub.tv.ui.auth.SessionRestoreRetryPolicy
import com.adulthub.tv.ui.detail.ItemDetailViewModel
import com.adulthub.tv.ui.home.HomeViewModel
import com.adulthub.tv.ui.library.LibraryViewModel
import com.adulthub.tv.ui.person.PersonDetailViewModel
import com.adulthub.tv.ui.player.PlayerViewModel
import com.adulthub.tv.ui.search.SearchViewModel
import com.adulthub.tv.ui.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModel {
        AuthViewModel(
            authRepository = get(),
            libraryRepository = get(),
            settingsDataStore = get(),
            sessionRestoreRetry = SessionRestoreRetryPolicy(),
            context = androidContext(),
        )
    }
    viewModel {
        HomeViewModel(
            libraryRepository = get(),
            personRepository = get(),
            settingsDataStore = get(),
            clientManager = get(),
            telegramProgressStore = getOrNull(),
            watchHistoryStore = getOrNull(),
            context = androidContext(),
        )
    }
    viewModelOf(::LibraryViewModel)
    viewModelOf(::ItemDetailViewModel)
    viewModelOf(::PersonDetailViewModel)
    // The player keeps an application scope so playback progress survives a view model reset.
    viewModel {
        PlayerViewModel(
            libraryRepository = get(),
            playbackRepository = get(),
            settingsDataStore = get(),
            playbackReporter = get(),
            channelRepository = getOrNull(),
            telegramProgressStore = getOrNull(),
            watchHistoryStore = getOrNull(),
            persistenceScope = getOrNull(),
            accountScope = getOrNull(),
            context = androidContext(),
        )
    }
    viewModelOf(::SearchViewModel)
    viewModel {
        SettingsViewModel(
            authRepository = get(),
            libraryRepository = get(),
            settingsDataStore = get(),
            channelRepository = getOrNull(),
            watchHistoryStore = getOrNull(),
            telegramProgressStore = getOrNull(),
            searchHistoryStore = getOrNull(),
            userScopedDataMigration = getOrNull(),
            context = androidContext(),
        )
    }
}
