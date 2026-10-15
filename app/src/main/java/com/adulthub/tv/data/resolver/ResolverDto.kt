package com.adulthub.tv.data.resolver

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class CategoryDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("slug") val slug: String,
    @SerialName("url") val url: String = "",
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class VideoDto(
    @SerialName("id") val id: String,
    @SerialName("provider") val provider: String = "telegram",
    @SerialName("provider_video_id") val providerVideoId: String = "",
    @SerialName("title") val title: String,
    @SerialName("slug") val slug: String = "",
    @SerialName("url") val url: String = "",
    @SerialName("thumbnail") val thumbnail: String? = null,
    @SerialName("duration") val duration: Double = 0.0,
    @SerialName("performers") val performers: List<String> = emptyList(),
    @SerialName("categories") val categories: List<String> = emptyList(),
    @SerialName("views") val views: String? = null,
    @SerialName("published") val published: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("file_size")
    @JsonNames("size", "file_size_bytes", "fileSize", "fileSizeBytes")
    val fileSizeBytes: Long? = null,
)

@Serializable
data class PerformerBioDto(
    @SerialName("name") val name: String,
    @SerialName("bio") val bio: String? = null,
    @SerialName("birth") val birth: String? = null,
    @SerialName("nationality") val nationality: String? = null,
    @SerialName("aliases") val aliases: List<String> = emptyList(),
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PhashGenerationRequest(
    @SerialName("video_id") val videoId: String,
    @SerialName("duration_us") val durationUs: Long,
)

@Serializable
data class PhashGenerationDto(
    @SerialName("ok") val ok: Boolean = false,
    @SerialName("job_id") val jobId: String? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("already_computed") val alreadyComputed: Boolean = false,
    @SerialName("thumbnail_ready") val thumbnailReady: Boolean = false,
    @SerialName("error_code") val errorCode: String? = null,
)

@Serializable
data class PhashJobStatusDto(
    @SerialName("ok") val ok: Boolean = false,
    @SerialName("job") val job: PhashJobDto? = null,
)

@Serializable
data class PhashJobDto(
    @SerialName("id") val id: String,
    @SerialName("video_id") val videoId: String,
    @SerialName("state") val state: String,
    @SerialName("decoder_profile") val decoderProfile: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("thumbnail_ready") val thumbnailReady: Boolean = false,
)

@Serializable
data class TelegramHistoryResponseDto(
    @SerialName("videos") val videos: List<VideoDto> = emptyList(),
    @SerialName("next_offset_id") val nextOffsetId: Long? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
)
