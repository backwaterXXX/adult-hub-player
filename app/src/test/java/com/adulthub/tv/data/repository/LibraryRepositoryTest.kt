package com.adulthub.tv.data.repository

import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.local.TelegramProgressStore
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryRepositoryTest {
    @Test
    fun `marking a non UUID video persists Telegram watched state`() = runBlocking {
        val progressStore = mockk<TelegramProgressStore>(relaxed = true)
        val repository = LibraryRepository(
            clientManager = mockk<JellyfinClientManager>(relaxed = true),
            settingsDataStore = mockk<SettingsDataStore>(relaxed = true),
            telegramProgressStore = progressStore,
        )

        assertEquals(true, repository.markAsPlayed("telegram:-100:42:7", true).getOrThrow())

        coVerify(exactly = 1) { progressStore.markPlayed("telegram:-100:42:7", true) }
    }
}
