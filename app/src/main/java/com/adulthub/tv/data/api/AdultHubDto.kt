package com.adulthub.tv.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VersionDiscoveryDto(
    @SerialName("api_version") val apiVersion: String = "",
    @SerialName("server_version") val serverVersion: String = "",
    @SerialName("status") val status: String = "",
    @SerialName("capabilities") val capabilities: Map<String, Boolean> = emptyMap(),
    @SerialName("endpoints") val endpoints: Map<String, String> = emptyMap()
)

@Serializable
data class AuthLoginRequestDto(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("device_id") val deviceId: String? = null
)

@Serializable
data class AuthLoginResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Int = 900,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("user") val user: AuthUserDto
)

@Serializable
data class PairingInitRequestDto(
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("device_id") val deviceId: String? = null
)

@Serializable
data class PairingInitResponseDto(
    @SerialName("pairing_code") val pairingCode: String,
    @SerialName("secret") val secret: String,
    @SerialName("expires_in") val expiresIn: Int = 300,
    @SerialName("poll_interval_seconds") val pollIntervalSeconds: Int = 2
)

@Serializable
data class PairingStatusResponseDto(
    @SerialName("status") val status: String = "pending",
    @SerialName("expires_in") val expiresIn: Int? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("user") val user: AuthUserDto? = null
)

@Serializable
data class AuthUserDto(
    @SerialName("id") val id: String,
    @SerialName("username") val username: String
)

@Serializable
data class AuthRefreshRequestDto(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class AuthRefreshResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Int = 900,
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class AuthRevokeRequestDto(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class AuthRevokeResponseDto(
    @SerialName("revoked") val revoked: Boolean = false
)

@Serializable
data class CatalogVideoItemDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String = "",
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("date_added") val dateAdded: String? = null,
    @SerialName("studio") val studio: String? = null,
    @SerialName("channel_name") val channelName: String? = null,
    @SerialName("plot") val plot: String? = null,
    @SerialName("categories") val categories: List<String> = emptyList(),
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("performers") val performers: List<CatalogPerformerRefDto> = emptyList(),
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    @SerialName("file_size_bytes") val fileSizeBytes: Long? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("is_hd") val isHd: Boolean? = null,
    @SerialName("user_state") val userState: CatalogUserStateDto? = null
)

@Serializable
data class CatalogPerformerRefDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("video_count") val videoCount: Int? = null
)

@Serializable
data class CatalogUserStateDto(
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("position_seconds") val positionSeconds: Int = 0,
    @SerialName("played_percentage") val playedPercentage: Double = 0.0,
    @SerialName("is_played") val isPlayed: Boolean = false
)

@Serializable
data class CatalogVideoListResponseDto(
    @SerialName("items") val items: List<CatalogVideoItemDto> = emptyList(),
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("offset") val offset: Int = 0,
    @SerialName("limit") val limit: Int = 50,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class CatalogTaxonomyItemDto(
    @SerialName("name") val name: String,
    @SerialName("count") val count: Int = 0
)

@Serializable
data class CatalogTaxonomyListResponseDto(
    @SerialName("items") val items: List<CatalogTaxonomyItemDto> = emptyList(),
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("offset") val offset: Int = 0,
    @SerialName("limit") val limit: Int = 100,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class CatalogPerformerDetailDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("disambiguation") val disambiguation: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("video_count") val videoCount: Int = 0,
    @SerialName("bio") val bio: String? = null,
    @SerialName("nationality") val nationality: String? = null,
    @SerialName("birth") val birth: String? = null,
    @SerialName("aliases") val aliases: List<String> = emptyList()
)

@Serializable
data class CatalogPerformerListResponseDto(
    @SerialName("items") val items: List<CatalogPerformerRefDto> = emptyList(),
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("offset") val offset: Int = 0,
    @SerialName("limit") val limit: Int = 50,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class CatalogChannelItemDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("video_count") val videoCount: Int = 0
)

@Serializable
data class CatalogChannelListResponseDto(
    @SerialName("items") val items: List<CatalogChannelItemDto> = emptyList(),
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("offset") val offset: Int = 0,
    @SerialName("limit") val limit: Int = 50,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class PlaybackDescriptorDto(
    @SerialName("video_id") val videoId: String,
    @SerialName("stream_path") val streamPath: String,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
    @SerialName("file_size_bytes") val fileSizeBytes: Long? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("renditions") val renditions: List<PlaybackRenditionDto> = emptyList()
)

@Serializable
data class PlaybackRenditionDto(
    @SerialName("id") val id: String,
    @SerialName("label") val label: String,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("file_size_bytes") val fileSizeBytes: Long? = null,
    @SerialName("width") val width: Int? = null,
    @SerialName("height") val height: Int? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("stream_path") val streamPath: String
)

@Serializable
data class WatchProgressDto(
    @SerialName("video_id") val videoId: String,
    @SerialName("position_seconds") val positionSeconds: Int = 0,
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    @SerialName("played_percentage") val playedPercentage: Double = 0.0,
    @SerialName("is_played") val isPlayed: Boolean = false,
    @SerialName("last_watched_at") val lastWatchedAt: String? = null
)

@Serializable
data class WatchProgressUpdateDto(
    @SerialName("position_seconds") val positionSeconds: Int,
    @SerialName("duration_seconds") val durationSeconds: Int,
    @SerialName("is_played") val isPlayed: Boolean,
    @SerialName("last_watched_at") val lastWatchedAt: String? = null
)

@Serializable
data class GenericOkResponseDto(
    @SerialName("ok") val ok: Boolean = true
)
