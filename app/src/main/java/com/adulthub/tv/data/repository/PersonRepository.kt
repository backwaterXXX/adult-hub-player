package com.adulthub.tv.data.repository

import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.jellyfin.toTvItem
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.model.TvPerson
import com.adulthub.tv.data.resolver.ResolverApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

data class TvPersonPage(
    val persons: List<TvPerson>,
    val totalRecordCount: Int,
)

open class PersonRepository(
    private val clientManager: JellyfinClientManager,
    private val resolverApi: ResolverApi,
    private val settingsDataStore: SettingsDataStore,
) {
    open suspend fun getPersons(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
        searchTerm: String? = null,
        nameStartsWith: String? = null,
    ): Result<List<TvPerson>> = withContext(Dispatchers.IO) {
        getPersonsPage(parentId, startIndex, limit, searchTerm, nameStartsWith)
            .map(TvPersonPage::persons)
    }

    open suspend fun getPersonsPage(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
        searchTerm: String? = null,
        nameStartsWith: String? = null,
    ): Result<TvPersonPage> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val targetParentId = parentId ?: settingsDataStore.getSelectedLibraryId()
            val parentUuid = targetParentId?.let { UUID.fromString(it) }

            val response = api.itemsApi.getItems(
                GetItemsRequest(
                    parentId = parentUuid,
                    startIndex = startIndex,
                    recursive = true,
                    searchTerm = searchTerm,
                    nameStartsWith = nameStartsWith,
                    limit = limit,
                    includeItemTypes = setOf(BaseItemKind.PERSON),
                    personTypes = listOf("Actor"),
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    sortOrder = listOf(SortOrder.ASCENDING),
                    enableImages = true,
                    enableTotalRecordCount = true,
                ),
            )
            val persons = response.content.items.map { item ->
                TvPerson(
                    id = item.id.toString(),
                    name = item.name.orEmpty(),
                    primaryImageTag = item.imageTags?.get(ImageType.PRIMARY),
                    movieCount = item.childCount ?: 0,
                )
            }
            Result.success(
                TvPersonPage(
                    persons = persons,
                    totalRecordCount = response.content.totalRecordCount,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun getRandomPersonsWithPhotos(limit: Int = 20): Result<List<TvPerson>> =
        withContext(Dispatchers.IO) {
            try {
                val api = clientManager.getApi()
                val targetParentId = settingsDataStore.getSelectedLibraryId()
                val parentUuid = targetParentId?.let { UUID.fromString(it) }
                val fetchLimit = (limit.coerceAtLeast(1) * 5).coerceAtMost(100)
                val response = api.itemsApi.getItems(
                    GetItemsRequest(
                        parentId = parentUuid,
                        recursive = true,
                        limit = fetchLimit,
                        includeItemTypes = setOf(BaseItemKind.PERSON),
                        personTypes = listOf("Actor"),
                        sortBy = listOf(ItemSortBy.RANDOM),
                        enableImages = true,
                        enableTotalRecordCount = false,
                    ),
                )
                val persons = response.content.items.mapNotNull { item ->
                    val imageTag = item.imageTags?.get(ImageType.PRIMARY)
                        ?.takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    TvPerson(
                        id = item.id.toString(),
                        name = item.name.orEmpty(),
                        primaryImageTag = imageTag,
                        movieCount = item.childCount ?: 0,
                    )
                }.take(limit.coerceAtLeast(1))
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
            val uuid = UUID.fromString(personId)
            val jfPerson = try {
                api.itemsApi.getItems(ids = listOf(uuid)).content.items.firstOrNull()
            } catch (_: Exception) {
                null
            }

            // Optionally enrich with bio from Resolver
            val bioDto = try {
                resolverApi.getPerformerBio(personName)
            } catch (_: Exception) {
                null
            }

            val person = TvPerson(
                id = personId,
                name = jfPerson?.name ?: personName,
                primaryImageTag = jfPerson?.imageTags?.get(ImageType.PRIMARY),
                bio = bioDto?.bio ?: jfPerson?.overview,
                birthDate = bioDto?.birth,
                nationality = bioDto?.nationality,
                aliases = bioDto?.aliases ?: emptyList(),
                photoUrl = bioDto?.photoUrl,
                movieCount = jfPerson?.childCount ?: 0,
            )
            Result.success(person)
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
        limit: Int = 50,
    ): Result<List<TvItem>> = withContext(Dispatchers.IO) {
        try {
            val api = clientManager.getApi()
            val targetParentId = parentId ?: settingsDataStore.getSelectedLibraryId()
            val parentUuid = targetParentId?.let { UUID.fromString(it) }
            val personUuid = UUID.fromString(personId)

            val response = api.itemsApi.getItems(
                parentId = parentUuid,
                personIds = listOf(personUuid),
                startIndex = startIndex,
                limit = limit,
                recursive = true,
                includeItemTypes = setOf(BaseItemKind.MOVIE),
                sortBy = setOf(ItemSortBy.DATE_CREATED),
                sortOrder = setOf(SortOrder.DESCENDING),
            )
            Result.success(response.content.items.map { it.toTvItem() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
