package com.adulthub.tv.data.repository

import com.adulthub.tv.data.resolver.CategoryDto
import com.adulthub.tv.data.resolver.ResolverApi
import com.adulthub.tv.data.resolver.TelegramHistoryResponseDto
import com.adulthub.tv.data.resolver.VideoDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ChannelRepositoryTest {

    @Test
    fun `category request leaves minimum duration to resolver configuration by default`() {
        val api = mockk<ResolverApi>()
        val videos = listOf(VideoDto(id = "video-1", title = "Video"))
        coEvery { api.getCategoryVideos("channel-1", 1, "latest", null) } returns videos
        val repository = ChannelRepository(api)

        val result = kotlinx.coroutines.runBlocking {
            repository.getCategoryVideos(channelId = "channel-1")
        }

        assertEquals(videos, result.getOrThrow())
        coVerify(exactly = 1) { api.getCategoryVideos("channel-1", 1, "latest", null) }
    }

    @Test
    fun `category request forwards an explicitly selected minimum duration`() {
        val api = mockk<ResolverApi>()
        val videos = listOf(VideoDto(id = "video-1", title = "Video"))
        coEvery { api.getCategoryVideos("channel-1", 2, "popular", 600) } returns videos
        val repository = ChannelRepository(api)

        val result = kotlinx.coroutines.runBlocking {
            repository.getCategoryVideos(
                channelId = "channel-1",
                page = 2,
                sort = "popular",
                minDuration = 600
            )
        }

        assertEquals(videos, result.getOrThrow())
        coVerify(exactly = 1) { api.getCategoryVideos("channel-1", 2, "popular", 600) }
    }

    @Test
    fun `cache looks up every video alias and evicts the least recently used video`() {
        val repository = ChannelRepository(mockk(relaxed = true))
        val videos = (1..500).map { index ->
            VideoDto(
                id = "video-$index",
                providerVideoId = "provider-$index",
                slug = "slug-$index",
                title = "Video $index"
            )
        }
        repository.cacheVideos(videos)

        assertSame(videos.first(), repository.getVideo("telegram:provider-1"))
        assertSame(videos.last(), repository.getVideo("slug-500"))

        repository.cacheVideo(
            VideoDto(id = "video-501", providerVideoId = "provider-501", title = "Video 501")
        )

        assertSame(videos.first(), repository.getVideo("video-1"))
        assertNull(repository.getVideo("video-2"))
        assertNotNull(repository.getVideo("telegram:provider-501"))
    }

    @Test
    fun `replacing a shared alias does not return the previous video`() {
        val repository = ChannelRepository(mockk(relaxed = true))
        repository.cacheVideo(VideoDto(id = "old-id", providerVideoId = "shared", title = "Old"))
        val replacement = VideoDto(id = "new-id", providerVideoId = "shared", title = "New")

        repository.cacheVideo(replacement)

        assertSame(replacement, repository.getVideo("shared"))
        assertNull(repository.getVideo("telegram:old-id"))
    }

    @Test
    fun `invalidating the cache removes the videos of the previous session`() {
        val repository = ChannelRepository(mockk(relaxed = true))
        repository.cacheVideo(VideoDto(id = "video-1", providerVideoId = "provider-1", title = "Video 1"))

        assertNotNull(repository.getVideo("video-1"))
        assertNotNull(repository.getRandomCachedVideo())

        repository.invalidateCache()

        assertNull(repository.getVideo("video-1"))
        assertNull(repository.getRandomCachedVideo())
    }

    @Test
    fun `getCategories caches results until TTL expires or cache is invalidated`() {
        var now = 1_000L
        val api = mockk<ResolverApi>()
        coEvery { api.getCategories() } returns listOf(
            CategoryDto(id = "ch-1", title = "Channel One", slug = "ch-1")
        )
        val repository = ChannelRepository(api, clockMillis = { now })

        val first = kotlinx.coroutines.runBlocking { repository.getCategories() }.getOrThrow()
        now += 60_000L
        val second = kotlinx.coroutines.runBlocking { repository.getCategories() }.getOrThrow()

        assertEquals(first, second)
        coVerify(exactly = 1) { api.getCategories() }

        now += 5 * 60_000L
        kotlinx.coroutines.runBlocking { repository.getCategories() }.getOrThrow()
        coVerify(exactly = 2) { api.getCategories() }

        repository.invalidateCategoriesCache()
        kotlinx.coroutines.runBlocking { repository.getCategories() }.getOrThrow()
        coVerify(exactly = 3) { api.getCategories() }
    }

    @Test
    fun `getChannelHistory caches pages until TTL expires or channel history is invalidated`() {
        var now = 1_000L
        val api = mockk<ResolverApi>()
        coEvery { api.getChannelHistory("ch-1", 40, null) } returns TelegramHistoryResponseDto(
            videos = listOf(VideoDto(id = "v-1", title = "Video 1", providerVideoId = "pv-1")),
            nextOffsetId = 10L,
            hasMore = true,
            messageCount = 40
        )
        val repository = ChannelRepository(api, clockMillis = { now })

        val first = kotlinx.coroutines.runBlocking {
            repository.getChannelHistory("ch-1", offsetId = null, limit = 40)
        }.getOrThrow()
        now += 30_000L
        val second = kotlinx.coroutines.runBlocking {
            repository.getChannelHistory("ch-1", offsetId = null, limit = 40)
        }.getOrThrow()

        assertEquals(first, second)
        coVerify(exactly = 1) { api.getChannelHistory("ch-1", 40, null) }

        now += 2 * 60_000L
        kotlinx.coroutines.runBlocking {
            repository.getChannelHistory("ch-1", offsetId = null, limit = 40)
        }.getOrThrow()
        coVerify(exactly = 2) { api.getChannelHistory("ch-1", 40, null) }

        repository.invalidateChannelHistory("ch-1")
        kotlinx.coroutines.runBlocking {
            repository.getChannelHistory("ch-1", offsetId = null, limit = 40)
        }.getOrThrow()
        coVerify(exactly = 3) { api.getChannelHistory("ch-1", 40, null) }
    }
}
