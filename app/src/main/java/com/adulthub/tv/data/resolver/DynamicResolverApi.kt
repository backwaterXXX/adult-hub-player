package com.adulthub.tv.data.resolver

import com.adulthub.tv.data.local.SettingsDataStore

/** Resolves the configured origin for each call while reusing Retrofit clients per origin. */
class DynamicResolverApi(
    private val client: ResolverApiClient,
    private val settingsDataStore: SettingsDataStore,
) : ResolverApi {
    private suspend fun api(): ResolverApi {
        val configuredUrl = settingsDataStore.getResolverBaseUrl()
        check(configuredUrl.isNotBlank()) { "Set a Resolver URL in Settings before using channel features" }
        val allowedUrl = settingsDataStore.requireAllowedServiceUrl(configuredUrl).normalized
        return client.create(allowedUrl)
    }

    override suspend fun getCategories(): List<CategoryDto> = api().getCategories()

    override suspend fun getCategoryVideos(
        channelId: String,
        page: Int,
        sort: String,
        minDuration: Int?,
    ): List<VideoDto> = api().getCategoryVideos(channelId, page, sort, minDuration)

    override suspend fun getChannelHistory(
        channelId: String,
        limit: Int,
        offsetId: Long?,
    ): TelegramHistoryResponseDto = api().getChannelHistory(channelId, limit, offsetId)

    override suspend fun search(query: String, provider: String): List<VideoDto> =
        api().search(query, provider)

    override suspend fun getPerformerBio(name: String): PerformerBioDto = api().getPerformerBio(name)

    override suspend fun generatePhash(token: String, body: PhashGenerationRequest): PhashGenerationDto =
        api().generatePhash(token, body)

    override suspend fun getPhashJob(token: String, jobId: String): PhashJobStatusDto =
        api().getPhashJob(token, jobId)

    override suspend fun getVideoVariants(videoId: String): VideoVariantsDto =
        api().getVideoVariants(videoId)
}
