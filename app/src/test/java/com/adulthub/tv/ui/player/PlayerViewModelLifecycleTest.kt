package com.adulthub.tv.ui.player

import androidx.lifecycle.ViewModelStore
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.local.TelegramProgressRecord
import com.adulthub.tv.data.local.TelegramProgressStore
import com.adulthub.tv.data.model.PlaybackInfo
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.PlaybackRepository
import com.adulthub.tv.player.PlaybackReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.model.api.PlayMethod
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelLifecycleTest {

    private val testDispatcher = StandardTestDispatcher()

    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val telegramProgressStore: TelegramProgressStore = mockk(relaxed = true)
    private val libraryRepository = LifecycleFakeLibraryRepository()
    private val playbackRepository = LifecycleFakePlaybackRepository()

    private val telegramItem = TvItem(
        id = "telegram:-1001234567890",
        title = "Telegram Scene",
        providerVideoId = "provider-video-1",
        runtimeTicks = 20L * 60 * 10_000_000L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsDataStore.selectedLibraryId } returns flowOf("lib-1")
        coEvery { telegramProgressStore.getProgress("provider-video-1") } returns null
        libraryRepository.itemResult = Result.success(telegramItem)
        playbackRepository.playbackInfoResult = Result.success(
            PlaybackInfo(
                itemId = telegramItem.id,
                streamUrl = "https://resolver.example.com/stream.mp4",
                playMethod = PlayMethod.DIRECT_PLAY,
                title = telegramItem.title,
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clearing the view model reports the session stop and persists the telegram progress`() = runTest(testDispatcher) {
        val persistenceScope = CoroutineScope(testDispatcher)
        val playbackReporter = PlaybackReporter(playbackRepository, CoroutineScope(testDispatcher))
        val viewModel = PlayerViewModel(
            libraryRepository = libraryRepository,
            playbackRepository = playbackRepository,
            settingsDataStore = settingsDataStore,
            playbackReporter = playbackReporter,
            telegramProgressStore = telegramProgressStore,
            persistenceScope = persistenceScope,
        )

        viewModel.loadItem(telegramItem.id)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PlayerUiState.Ready)

        viewModel.updatePlaybackPosition(120_000L)

        val store = ViewModelStore()
        store.put("player", viewModel)
        store.clear()
        advanceUntilIdle()

        coVerify {
            telegramProgressStore.saveProgress(
                providerVideoId = "provider-video-1",
                positionMs = 120_000L,
                durationMs = 20L * 60 * 1000L,
                title = "Telegram Scene",
                thumbnailUrl = any(),
                channelId = any(),
                isPlayed = false,
            )
        }
    }
}

private class LifecycleFakeLibraryRepository : LibraryRepository(mockk(relaxed = true), mockk(relaxed = true)) {
    var itemResult: Result<TvItem> = Result.success(TvItem(id = "default", title = "Default"))

    override suspend fun getItemDetails(itemId: String): Result<TvItem> = itemResult
}

private class LifecycleFakePlaybackRepository : PlaybackRepository(mockk(relaxed = true), mockk(relaxed = true)) {
    var playbackInfoResult: Result<PlaybackInfo>? = null

    override suspend fun resolvePlaybackInfo(item: TvItem, variantId: String?): Result<PlaybackInfo> {
        return playbackInfoResult ?: Result.success(
            PlaybackInfo(
                itemId = item.id,
                streamUrl = "https://resolver.example.com/stream.mp4",
                playMethod = PlayMethod.DIRECT_PLAY,
                title = item.title,
            )
        )
    }
}
