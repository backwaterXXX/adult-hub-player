package com.adulthub.tv.data.repository

import com.adulthub.tv.data.api.AdultHubClientManager
import com.adulthub.tv.data.api.toTvItem
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.resolver.ResolverApi
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TvPersonPage(
    val persons: List<TvPerson>,
    val totalRecordCount: Int
)

open class PersonRepository(
    private val clientManager: AdultHubClientManager,
    private val resolverApi: ResolverApi,
    private val settingsDataStore: SettingsDataStore
) {
    open suspend fun getPersons(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
        searchTerm: String? = null,
        nameStartsWith: String? = null
    ): Result<List<TvPerson>> = withContext(Dispatchers.IO) {
        getPersonsPage(parentId, startIndex, limit, searchTerm, nameStartsWith)
            .map(TvPersonPage::persons)
    }

    open suspend fun getPersonsPage(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
        searchTerm: String? = null,
        nameStartsWith: String? = null
    ): Result<TvPersonPage> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val response = api.getPerformers(
                offset = startIndex,
                limit = limit.coerceAtMost(200),
                q = searchTerm?.takeIf { it.isNotBlank() },
                letter = nameStartsWith?.takeIf { it.isNotBlank() }
            )
            val persons = response.items.map { item ->
                TvPerson(
                    id = item.id,
                    name = item.name,
                    photoUrl = item.photoUrl,
                    movieCount = item.videoCount ?: 0
                )
            }
            Result.success(
                TvPersonPage(
                    persons = persons,
                    totalRecordCount = response.totalCount
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getRandomPersonsWithPhotos(limit: Int = 20): Result<List<TvPerson>> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val take = limit.coerceAtLeast(1)
            val fetchLimit = (take * 3).coerceAtMost(100)
            // A fixed offset always returned the same first page, so the "random" row was the
            // same people on every launch. A random offset inside a bounded window fixes that
            // without an extra request; an empty page falls back to the beginning of the list.
            var items = api.getPerformers(
                offset = Random.nextInt(0, RANDOM_PERFORMERS_OFFSET_WINDOW),
                limit = fetchLimit,
                photoOnly = true
            ).items
            if (items.isEmpty()) {
                items = api.getPerformers(offset = 0, limit = fetchLimit, photoOnly = true).items
            }
            // Artwork is optional: when the catalogue has no servable performer photo the row
            // still lists people instead of collapsing into an empty section.
            val persons = items
                .filter { !it.photoUrl.isNullOrBlank() }
                .ifEmpty { items }
                .shuffled()
                .take(take)
                .map { item ->
                    TvPerson(
                        id = item.id,
                        name = item.name,
                        photoUrl = item.photoUrl,
                        movieCount = item.videoCount ?: 0
                    )
                }
            Result.success(persons)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getPersonDetails(personId: String, personName: String): Result<TvPerson> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val detail = try {
                api.getPerformerDetail(personId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }

            if (detail != null) {
                Result.success(
                    TvPerson(
                        id = detail.id,
                        name = detail.name,
                        photoUrl = detail.photoUrl,
                        bio = detail.bio,
                        birthDate = detail.birth,
                        nationality = detail.nationality,
                        aliases = detail.aliases,
                        movieCount = detail.videoCount
                    )
                )
            } else {
                // Try fallback to resolver bio if performer detail route fails
                val bioDto = try {
                    resolverApi.getPerformerBio(personName)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                Result.success(
                    TvPerson(
                        id = personId,
                        name = personName,
                        photoUrl = bioDto?.photoUrl,
                        bio = bioDto?.bio,
                        birthDate = bioDto?.birth,
                        nationality = bioDto?.nationality,
                        aliases = bioDto?.aliases ?: emptyList(),
                        movieCount = 0
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getPersonMovies(
        personId: String,
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 50
    ): Result<List<TvItem>> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val baseUrl = clientManager.currentServerUrl ?: ""
            val response = api.getVideos(
                performerId = personId,
                offset = startIndex,
                limit = limit.coerceAtMost(100),
                sort = "latest",
                direction = "desc"
            )
            val items = response.items.map { it.toTvItem(baseUrl) }
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/** How far into the performer list a random row may start. */
private const val RANDOM_PERFORMERS_OFFSET_WINDOW = 400
