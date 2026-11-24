package com.adulthub.tv.data.repository

import com.adulthub.tv.data.api.AdultHubApi
import com.adulthub.tv.data.api.AdultHubClientManager
import com.adulthub.tv.data.api.PlaybackDescriptorDto
import com.adulthub.tv.data.api.PlaybackRenditionDto
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.PlayMethod
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.network.ServiceUrl
import com.adulthub.tv.data.resolver.VideoVariantsResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlaybackRepositoryTest {

    @Test
    fun `resolvePlaybackInfo resolves descriptor into PlaybackInfo with same origin stream`() = runTest {
        val clientManager = mockk<AdultHubClientManager>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val mockApi = mockk<AdultHubApi>(relaxed = true)

        val serverOrigin = "https://adulthub.example.com"
        every { clientManager.getApi() } returns mockApi
        every { clientManager.getSessionSnapshot() } returns com.adulthub.tv.data.api.ClientSession(
            serverUrl = serverOrigin,
            accessToken = "test-token",
            userId = "user-123",
            username = "testuser"
        )
        every { clientManager.currentServerUrl } returns serverOrigin
        every { clientManager.currentAccessToken } returns "test-token"
        coEvery { settingsDataStore.requireAllowedServiceUrl(any(), any()) } returns ServiceUrl(
            normalized = serverOrigin,
            origin = serverOrigin,
            usesCleartext = false
        )

        val descriptor = PlaybackDescriptorDto(
            videoId = "video-123",
            streamPath = "api/v1/playback/stream/video-123",
            mimeType = "video/mp4",
            durationSeconds = 120.0,
            fileSizeBytes = 50000000L
        )
        coEvery { mockApi.getPlaybackDescriptor("video-123") } returns descriptor

        val repository = PlaybackRepository(
            clientManager = clientManager,
            settingsDataStore = settingsDataStore
        )

        val item = TvItem(id = "video-123", title = "Test Movie")
        val result = repository.resolvePlaybackInfo(item)

        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertEquals("video-123", info.itemId)
        assertEquals("https://adulthub.example.com/api/v1/playback/stream/video-123", info.streamUrl)
        assertEquals(PlayMethod.DIRECT_PLAY, info.playMethod)
        assertEquals(mapOf("Authorization" to "Bearer test-token"), info.headers)
        assertEquals(120000L, info.durationMs)
    }

    @Test
    fun `resolvePlaybackInfo fails when no session snapshot exists and does not fall back to resolver url`() = runTest {
        val clientManager = mockk<AdultHubClientManager>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val mockApi = mockk<AdultHubApi>(relaxed = true)

        every { clientManager.getApi() } returns mockApi
        every { clientManager.getSessionSnapshot() } returns null
        every { clientManager.currentServerUrl } returns null
        coEvery { settingsDataStore.getResolverBaseUrl() } returns "https://resolver.example.com"

        val repository = PlaybackRepository(clientManager, settingsDataStore)
        val result = repository.resolvePlaybackInfo(TvItem(id = "video-123", title = "Test"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { settingsDataStore.getResolverBaseUrl() }
    }

    @Test
    fun `resolvePlaybackInfo rejects Telegram-only IDs`() = runTest {
        val clientManager = mockk<AdultHubClientManager>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)

        val repository = PlaybackRepository(clientManager, settingsDataStore)
        val result = repository.resolvePlaybackInfo(TvItem(id = "telegram:-100123:456:789", title = "Tg"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { clientManager.getApi() }
    }

    @Test
    fun `reportPlaybackProgress and stop do not mark already played video unwatched`() = runTest {
        val clientManager = mockk<AdultHubClientManager>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val mockApi = mockk<AdultHubApi>(relaxed = true)

        every { clientManager.getApi() } returns mockApi
        every { clientManager.getApiOrNull() } returns mockApi
        every { clientManager.getSessionSnapshot() } returns com.adulthub.tv.data.api.ClientSession(
            serverUrl = "https://adulthub.example.com",
            accessToken = "token",
            userId = "u1"
        )
        coEvery { settingsDataStore.requireAllowedServiceUrl(any(), any()) } returns com.adulthub.tv.data.network.ServiceUrl(
            normalized = "https://adulthub.example.com",
            origin = "https://adulthub.example.com",
            usesCleartext = false
        )
        coEvery { mockApi.getPlaybackDescriptor("v-already-played") } returns PlaybackDescriptorDto(
            videoId = "v-already-played",
            streamPath = "stream",
            durationSeconds = 100.0
        )

        val repository = PlaybackRepository(clientManager, settingsDataStore)
        repository.resolvePlaybackInfo(TvItem(id = "v-already-played", title = "Movie", isPlayed = true))

        repository.reportPlaybackProgress(
            itemId = "v-already-played",
            mediaSourceId = null,
            positionTicks = 10L * 10_000_000L,
            canSeek = true,
            playMethod = PlayMethod.DIRECT_PLAY
        )

        coVerify(exactly = 1) {
            mockApi.putWatchProgress(
                videoId = "v-already-played",
                body = match { it.positionSeconds == 10 && it.isPlayed }
            )
        }
    }

    @Test
    fun `reportPlaybackProgress ignores Telegram-only ID without sending to server`() = runTest {
        val clientManager = mockk<AdultHubClientManager>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val mockApi = mockk<AdultHubApi>(relaxed = true)

        every { clientManager.getApiOrNull() } returns mockApi

        val repository = PlaybackRepository(clientManager, settingsDataStore)
        val result = repository.reportPlaybackProgress(
            itemId = "telegram:-100123:456:789",
            mediaSourceId = null,
            positionTicks = 10_000_000L,
            canSeek = true,
            playMethod = PlayMethod.DIRECT_PLAY
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { mockApi.putWatchProgress(any(), any()) }
    }

    @Test
    fun `resolvePlaybackInfo rethrows CancellationException`() = runTest {
        val clientManager = mockk<AdultHubClientManager>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val mockApi = mockk<AdultHubApi>(relaxed = true)

        every { clientManager.getApi() } returns mockApi
        coEvery { mockApi.getPlaybackDescriptor(any()) } throws CancellationException("Cancelled")

        val repository = PlaybackRepository(
            clientManager = clientManager,
            settingsDataStore = settingsDataStore
        )

        try {
            repository.resolvePlaybackInfo(TvItem(id = "v-1", title = "Title"))
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Cancelled", e.message)
        }
    }

    @Test
    fun `fetchVideoVariants returns renditions from Adult Hub descriptor`() = runTest {
        val clientManager = mockk<AdultHubClientManager>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val mockApi = mockk<AdultHubApi>(relaxed = true)

        every { clientManager.getApiOrNull() } returns mockApi

        val descriptor = PlaybackDescriptorDto(
            videoId = "video-123",
            streamPath = "api/v1/playback/stream/video-123",
            renditions = listOf(
                PlaybackRenditionDto(
                    id = "orig",
                    label = "Original",
                    isDefault = true,
                    streamPath = "api/v1/playback/stream/video-123?res=orig"
                ),
                PlaybackRenditionDto(
                    id = "720p",
                    label = "720p",
                    isDefault = false,
                    streamPath = "api/v1/playback/stream/video-123?res=720p"
                )
            )
        )
        coEvery { mockApi.getPlaybackDescriptor("video-123") } returns descriptor

        val repository = PlaybackRepository(
            clientManager = clientManager,
            settingsDataStore = settingsDataStore
        )

        val variantsResult = repository.fetchVideoVariants("video-123")
        assertTrue(variantsResult is VideoVariantsResult.Available)
        val variants = (variantsResult as VideoVariantsResult.Available).variants
        assertEquals(2, variants.size)
        assertEquals("orig", variants[0].id)
        assertTrue(variants[0].isOriginal)
        assertEquals("720p", variants[1].id)
    }

    @Test
    fun `playback reporting delegates to Adult Hub API putWatchProgress`() = runTest {
        val clientManager = mockk<AdultHubClientManager>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val mockApi = mockk<AdultHubApi>(relaxed = true)

        every { clientManager.getApiOrNull() } returns mockApi

        val repository = PlaybackRepository(
            clientManager = clientManager,
            settingsDataStore = settingsDataStore
        )

        val startResult = repository.reportPlaybackStart(
            itemId = "video-123",
            mediaSourceId = null,
            positionTicks = 50000000L,
            canSeek = true,
            playMethod = PlayMethod.DIRECT_PLAY
        )
        assertTrue(startResult.isSuccess)

        val progressResult = repository.reportPlaybackProgress(
            itemId = "video-123",
            mediaSourceId = null,
            positionTicks = 120000000L,
            canSeek = true,
            playMethod = PlayMethod.DIRECT_PLAY
        )
        assertTrue(progressResult.isSuccess)
        coVerify(exactly = 1) {
            mockApi.putWatchProgress(
                videoId = "video-123",
                body = match { it.positionSeconds == 12 && !it.isPlayed }
            )
        }

        val stopResult = repository.reportPlaybackStopped(
            itemId = "video-123",
            mediaSourceId = null,
            positionTicks = 200000000L
        )
        assertTrue(stopResult.isSuccess)
        coVerify(exactly = 1) {
            mockApi.putWatchProgress(
                videoId = "video-123",
                body = match { it.positionSeconds == 20 }
            )
        }
    }

    @Test
    fun `fetchVideoVariants returns OriginalOnly when descriptor has empty renditions`() = runTest {
        val clientManager = mockk<AdultHubClientManager>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val mockApi = mockk<AdultHubApi>(relaxed = true)

        every { clientManager.getApiOrNull() } returns mockApi
        coEvery { mockApi.getPlaybackDescriptor("video-plain") } returns PlaybackDescriptorDto(
            videoId = "video-plain",
            streamPath = "stream",
            renditions = emptyList()
        )

        val repository = PlaybackRepository(clientManager, settingsDataStore)
        val result = repository.fetchVideoVariants("video-plain")
        assertTrue(result is VideoVariantsResult.OriginalOnly)
    }

    @Test
    fun `resolvePlaybackInfo duration is carried into progress reports and marks watched at 90 percent`() = runTest {
        val clientManager = mockk<AdultHubClientManager>(relaxed = true)
        val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
        val mockApi = mockk<AdultHubApi>(relaxed = true)

        every { clientManager.getApi() } returns mockApi
        every { clientManager.getApiOrNull() } returns mockApi
        every { clientManager.currentServerUrl } returns "https://adulthub.example.com"
        coEvery { settingsDataStore.requireAllowedServiceUrl(any(), any()) } returns com.adulthub.tv.data.network.ServiceUrl(
            normalized = "https://adulthub.example.com",
            origin = "https://adulthub.example.com",
            usesCleartext = false
        )

        val descriptor = PlaybackDescriptorDto(
            videoId = "v-long",
            streamPath = "stream",
            durationSeconds = 100.0
        )
        coEvery { mockApi.getPlaybackDescriptor("v-long") } returns descriptor

        val repository = PlaybackRepository(clientManager, settingsDataStore)
        repository.resolvePlaybackInfo(TvItem(id = "v-long", title = "Long Video"))

        // Report progress at 50s (50% -> not played, duration = 100)
        repository.reportPlaybackProgress(
            itemId = "v-long",
            mediaSourceId = null,
            positionTicks = 50L * 10_000_000L,
            canSeek = true,
            playMethod = PlayMethod.DIRECT_PLAY
        )
        coVerify(exactly = 1) {
            mockApi.putWatchProgress(
                videoId = "v-long",
                body = match { it.positionSeconds == 50 && it.durationSeconds == 100 && !it.isPlayed }
            )
        }

        // Report stop at 95s (95% -> isPlayed = true, duration = 100)
        repository.reportPlaybackStopped(
            itemId = "v-long",
            mediaSourceId = null,
            positionTicks = 95L * 10_000_000L
        )
        coVerify(exactly = 1) {
            mockApi.putWatchProgress(
                videoId = "v-long",
                body = match { it.positionSeconds == 95 && it.durationSeconds == 100 && it.isPlayed }
            )
        }
    }
}
