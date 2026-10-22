package com.adulthub.tv.data.repository

import com.adulthub.tv.data.local.SessionOwner
import com.adulthub.tv.data.local.SessionOwnerProvider
import com.adulthub.tv.data.model.TvChannel
import com.adulthub.tv.data.resolver.ResolverApi
import com.adulthub.tv.data.resolver.VideoDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChannelHistoryPage(
    val videos: List<VideoDto>,
    val nextOffsetId: Long?,
    val hasMore: Boolean,
    val messageCount: Int,
)

/**
 * Reports that a result belongs to a session that is no longer active.
 *
 * The result is dropped instead of being shown or cached for the new session.
 */
class StaleSessionException : RuntimeException("The session changed while the request was running")

open class ChannelRepository(
    private val resolverApi: ResolverApi,
    private val accountScope: SessionOwnerProvider? = null,
) {
    private val videoCacheLock = Any()
    private val videoCache = LinkedHashMap<String, VideoDto>(CACHE_MAX_VIDEOS * 4, 0.75f, true)
    private val videoIdLru = LinkedHashMap<String, Unit>(CACHE_MAX_VIDEOS, 0.75f, true)
    private val aliasesByVideoId = mutableMapOf<String, MutableSet<String>>()

    fun cacheVideos(videos: List<VideoDto>) {
        synchronized(videoCacheLock) {
            for (v in videos) {
                val aliases = buildSet {
                    add(v.id)
                    if (v.providerVideoId.isNotBlank()) {
                        add(v.providerVideoId)
                        add("${v.provider}:${v.providerVideoId}")
                    }
                    if (v.slug.isNotBlank()) add(v.slug)
                }
                val knownAliases = aliasesByVideoId.getOrPut(v.id) { mutableSetOf() }
                (knownAliases - aliases).forEach(videoCache::remove)
                knownAliases.retainAll(aliases)
                aliases.forEach { alias ->
                    val previousId = videoCache[alias]?.id
                    if (previousId != null && previousId != v.id) {
                        aliasesByVideoId[previousId]?.let { previousAliases ->
                            previousAliases.remove(alias)
                            if (previousAliases.isEmpty()) {
                                aliasesByVideoId.remove(previousId)
                                videoIdLru.remove(previousId)
                            }
                        }
                    }
                    videoCache[alias] = v
                    knownAliases += alias
                }
                videoIdLru[v.id] = Unit
            }

            while (videoIdLru.size > CACHE_MAX_VIDEOS) {
                val oldestVideoId = videoIdLru.keys.first()
                aliasesByVideoId.remove(oldestVideoId).orEmpty().forEach { alias ->
                    if (videoCache[alias]?.id == oldestVideoId) videoCache.remove(alias)
                }
                videoIdLru.remove(oldestVideoId)
            }
        }
    }

    fun cacheVideo(video: VideoDto) {
        cacheVideos(listOf(video))
    }

    /**
     * Drops the cached videos of the previous session.
     *
     * The cache holds data of one account and one resolver origin, so it is
     * cleared whenever either of them changes.
     */
    fun invalidateCache() {
        synchronized(videoCacheLock) {
            videoCache.clear()
            videoIdLru.clear()
            aliasesByVideoId.clear()
        }
    }

    fun getVideo(id: String): VideoDto? {
        synchronized(videoCacheLock) {
            val video = videoCache[id] ?: videoCache["telegram:$id"]
            if (video != null) videoIdLru[video.id] = Unit
            return video
        }
    }

    fun getRandomCachedVideo(excludeId: String? = null): VideoDto? {
        synchronized(videoCacheLock) {
            val list = videoCache.values.distinctBy { it.id }
                .filter { it.id != excludeId && it.providerVideoId != excludeId }
            return if (list.isNotEmpty()) list.random() else null
        }
    }

    open suspend fun getCategories(): Result<List<TvChannel>> = withContext(Dispatchers.IO) {
        val owner = accountScope?.currentOwner()
        try {
            val response = resolverApi.getCategories()
            if (!isCurrent(owner)) return@withContext Result.failure(StaleSessionException())
            val channels = response.map {
                TvChannel(
                    id = it.id,
                    title = it.title,
                    slug = it.slug,
                    url = it.url,
                )
            }
            Result.success(channels)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getChannelHistory(
        channelId: String,
        offsetId: Long? = null,
        limit: Int = 100,
    ): Result<ChannelHistoryPage> = withContext(Dispatchers.IO) {
        // The owner is captured before the request runs. A response of a session that
        // ended meanwhile must not fill the cache of the new session.
        val owner = accountScope?.currentOwner()
        try {
            val response = resolverApi.getChannelHistory(
                channelId = channelId,
                offsetId = offsetId,
                limit = limit,
            )
            if (!isCurrent(owner)) return@withContext Result.failure(StaleSessionException())
            val deduped = response.videos.distinctBy { it.providerVideoId.ifBlank { it.id } }
            cacheVideos(deduped)
            val page = ChannelHistoryPage(
                videos = deduped,
                nextOffsetId = response.nextOffsetId,
                hasMore = response.hasMore,
                messageCount = response.messageCount,
            )
            Result.success(page)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getCategoryVideos(
        channelId: String,
        page: Int = 1,
        sort: String = "latest",
        minDuration: Int? = null,
    ): Result<List<VideoDto>> = withContext(Dispatchers.IO) {
        val owner = accountScope?.currentOwner()
        try {
            val videos = resolverApi.getCategoryVideos(
                channelId = channelId,
                page = page,
                sort = sort,
                minDuration = minDuration,
            )
            if (!isCurrent(owner)) return@withContext Result.failure(StaleSessionException())
            val deduped = videos.distinctBy { it.providerVideoId.ifBlank { it.id } }
            cacheVideos(deduped)
            Result.success(deduped)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun search(query: String): Result<List<VideoDto>> = withContext(Dispatchers.IO) {
        val owner = accountScope?.currentOwner()
        try {
            val results = resolverApi.search(query = query)
            if (!isCurrent(owner)) return@withContext Result.failure(StaleSessionException())
            val deduped = results.distinctBy { it.providerVideoId.ifBlank { it.id } }
            cacheVideos(deduped)
            Result.success(deduped)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** True when the owner of a request is still the active session. */
    private suspend fun isCurrent(owner: SessionOwner?): Boolean =
        owner == null || accountScope?.isCurrent(owner) != false
}

private const val CACHE_MAX_VIDEOS = 500
