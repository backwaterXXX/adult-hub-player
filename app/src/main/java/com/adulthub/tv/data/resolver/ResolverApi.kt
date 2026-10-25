package com.adulthub.tv.data.resolver

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Header
import retrofit2.http.Body
import retrofit2.http.POST

interface ResolverApi {

    @GET("admin/api/categories")
    suspend fun getCategories(): List<CategoryDto>

    @GET("admin/api/category/{channelId}")
    suspend fun getCategoryVideos(
        @Path("channelId") channelId: String,
        @Query("page") page: Int = 1,
        @Query("sort") sort: String = "latest",
        @Query("min_duration") minDuration: Int? = null,
    ): List<VideoDto>

    @GET("admin/api/telegram/history/{channelId}")
    suspend fun getChannelHistory(
        @Path("channelId") channelId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset_id") offsetId: Long? = null,
    ): TelegramHistoryResponseDto

    @GET("admin/api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("provider") provider: String = "telegram",
    ): List<VideoDto>

    @GET("admin/api/performer-bio")
    suspend fun getPerformerBio(
        @Query("name") name: String,
    ): PerformerBioDto

    @POST("admin/api/v2/video-phash/generate")
    suspend fun generatePhash(
        @Header("X-Emby-Token") token: String,
        @Body body: PhashGenerationRequest,
    ): PhashGenerationDto

    @GET("admin/api/v2/video-phash/jobs/{job_id}")
    suspend fun getPhashJob(
        @Header("X-Emby-Token") token: String,
        @Path("job_id") jobId: String,
    ): PhashJobStatusDto

    @GET("admin/play/{videoId}/variants")
    suspend fun getVideoVariants(
        @Path("videoId") videoId: String,
    ): VideoVariantsDto
}
