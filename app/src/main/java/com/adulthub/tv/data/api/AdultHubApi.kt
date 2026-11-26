package com.adulthub.tv.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface AdultHubApi {

    @GET("api/v1/version")
    suspend fun getVersion(): VersionDiscoveryDto

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: AuthLoginRequestDto): AuthLoginResponseDto

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: AuthRefreshRequestDto): AuthRefreshResponseDto

    @POST("api/v1/auth/revoke")
    suspend fun revoke(@Body body: AuthRevokeRequestDto): AuthRevokeResponseDto

    @GET("api/v1/catalog/videos")
    suspend fun getVideos(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 50,
        @Query("sort") sort: String = "latest",
        @Query("direction") direction: String? = null,
        @Query("channel_id") channelId: String? = null,
        @Query("q") q: String? = null,
        @Query("category") category: String? = null,
        @Query("tag") tag: String? = null,
        @Query("studio") studio: String? = null,
        @Query("performer") performer: String? = null,
        @Query("performer_id") performerId: String? = null,
        @Query("min_duration") minDuration: Int? = null,
        @Query("max_duration") maxDuration: Int? = null,
        @Query("quality") quality: String? = null,
        @Query("is_favorite") isFavorite: Boolean? = null
    ): CatalogVideoListResponseDto

    @GET("api/v1/catalog/videos/{video_id}")
    suspend fun getVideoDetail(@Path("video_id") videoId: String): CatalogVideoItemDto

    @GET("api/v1/catalog/categories")
    suspend fun getCategories(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100,
        @Query("q") q: String? = null
    ): CatalogTaxonomyListResponseDto

    @GET("api/v1/catalog/studios")
    suspend fun getStudios(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100,
        @Query("q") q: String? = null
    ): CatalogTaxonomyListResponseDto

    @GET("api/v1/catalog/tags")
    suspend fun getTags(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100,
        @Query("q") q: String? = null
    ): CatalogTaxonomyListResponseDto

    @POST("api/v1/auth/pair/init")
    suspend fun pairInit(@Body body: PairingInitRequestDto): PairingInitResponseDto

    /**
     * Polls a TV pairing session. The private pairing secret travels in the Authorization
     * header because the backend rejects any query parameter on auth routes, and the
     * first successful poll after approval is also the one-time token exchange.
     */
    @POST("api/v1/auth/pair/status")
    suspend fun pairStatus(@Header("Authorization") secretHeader: String): PairingStatusResponseDto

    @GET("api/v1/catalog/performers")
    suspend fun getPerformers(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 50,
        @Query("q") q: String? = null,
        @Query("letter") letter: String? = null,
        @Query("photo_only") photoOnly: Boolean = false,
        @Query("sort") sort: String = "name",
        @Query("direction") direction: String? = null
    ): CatalogPerformerListResponseDto

    @GET("api/v1/catalog/performers/{performer_id}")
    suspend fun getPerformerDetail(@Path("performer_id") performerId: String): CatalogPerformerDetailDto

    @GET("api/v1/catalog/channels")
    suspend fun getChannels(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 50
    ): CatalogChannelListResponseDto

    @GET("api/v1/playback/{video_id}")
    suspend fun getPlaybackDescriptor(@Path("video_id") videoId: String): PlaybackDescriptorDto

    @GET("api/v1/user/watch/{video_id}")
    suspend fun getWatchProgress(@Path("video_id") videoId: String): WatchProgressDto

    @PUT("api/v1/user/watch/{video_id}")
    suspend fun putWatchProgress(
        @Path("video_id") videoId: String,
        @Body body: WatchProgressUpdateDto
    ): GenericOkResponseDto

    @PUT("api/v1/user/favorites/{video_id}")
    suspend fun putFavorite(@Path("video_id") videoId: String): GenericOkResponseDto

    @DELETE("api/v1/user/favorites/{video_id}")
    suspend fun deleteFavorite(@Path("video_id") videoId: String): GenericOkResponseDto
}
