package com.adulthub.tv.data.repository

import com.adulthub.tv.data.local.SessionOwner
import com.adulthub.tv.data.local.SessionOwnerProvider
import com.adulthub.tv.data.resolver.ResolverApi
import com.adulthub.tv.data.resolver.VideoDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A result of a session that ended must not reach the new session.
 *
 * The channel cache holds videos of one account and one resolver origin, so a
 * late response must neither be shown nor fill the cache again.
 */
class StaleSessionTest {

    private class SwitchableAccountScope : SessionOwnerProvider {
        var owner: SessionOwner = SessionOwner("account-a", "marker-a")

        override suspend fun currentOwner(): SessionOwner = owner

        override suspend fun isCurrent(owner: SessionOwner): Boolean =
            this.owner.sessionMarker == owner.sessionMarker
    }

    @Test
    fun `a category response of an ended session is rejected and does not fill the cache`() = runTest {
        val scope = SwitchableAccountScope()
        val api = mockk<ResolverApi>()
        coEvery { api.getCategoryVideos(any(), any(), any(), any()) } coAnswers {
            // The session ends while the request runs.
            scope.owner = SessionOwner("account-b", "marker-b")
            listOf(VideoDto(id = "video-1", title = "Old session video"))
        }
        val repository = ChannelRepository(api, scope)

        val result = repository.getCategoryVideos("channel-1")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StaleSessionException)
        assertNull(repository.getVideo("video-1"))
        assertNull(repository.getRandomCachedVideo())
    }

    @Test
    fun `a search response of an ended session is rejected`() = runTest {
        val scope = SwitchableAccountScope()
        val api = mockk<ResolverApi>()
        coEvery { api.search(any()) } coAnswers {
            scope.owner = SessionOwner("account-b", "marker-b")
            listOf(VideoDto(id = "video-2", title = "Old session video"))
        }
        val repository = ChannelRepository(api, scope)

        val result = repository.search("query")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StaleSessionException)
        assertNull(repository.getVideo("video-2"))
    }

    @Test
    fun `a response of the running session is used`() = runTest {
        val scope = SwitchableAccountScope()
        val api = mockk<ResolverApi>()
        coEvery { api.getCategoryVideos(any(), any(), any(), any()) } returns
            listOf(VideoDto(id = "video-3", title = "Current video"))
        val repository = ChannelRepository(api, scope)

        val result = repository.getCategoryVideos("channel-1")

        assertTrue(result.isSuccess)
        assertTrue(repository.getVideo("video-3") != null)
    }
}
