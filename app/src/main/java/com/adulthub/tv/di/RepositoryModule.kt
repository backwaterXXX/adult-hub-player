package com.adulthub.tv.di

import com.adulthub.tv.data.repository.AuthRepository
import com.adulthub.tv.data.repository.ChannelRepository
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.PersonRepository
import com.adulthub.tv.data.repository.PlaybackRepository
import com.adulthub.tv.data.repository.StudioRepository
import org.koin.dsl.module

val repositoryModule = module {
    single { AuthRepository(get(), get(), get()) }
    single { LibraryRepository(get(), get()) }
    single { PersonRepository(get(), get(), get()) }
    single { StudioRepository(get(), get()) }
    single { ChannelRepository(get()) }
    single { PlaybackRepository(get(), get()) }
}
