package com.adulthub.tv.data.resolver

import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class VideoVariantsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val contractExample = """
        {
          "video_id": "telegram:-1001618765010:21406:5393323762257014475",
          "status": "ready",
          "video_processing_pending": false,
          "has_alternatives": true,
          "variants": [
            {
              "id": "original",
              "is_original": true,
              "width": 720,
              "height": 1280,
              "codec": "h264",
              "mime_type": "video/mp4",
              "file_size": 5892214,
              "duration": 30.555,
              "bitrate": null,
              "estimated_average_bitrate": 1542717
            },
            {
              "id": "alt_5393578174644794819",
              "is_original": false,
              "width": 720,
              "height": 1280,
              "codec": "h264",
              "mime_type": "video/mp4",
              "file_size": 3999707,
              "duration": 30.434,
              "bitrate": null,
              "estimated_average_bitrate": 1051379
            },
            {
              "id": "alt_5393558434975097219",
              "is_original": false,
              "width": 480,
              "height": 852,
              "codec": "av01",
              "mime_type": "video/mp4",
              "file_size": 1511665,
              "duration": 30.434,
              "bitrate": null,
              "estimated_average_bitrate": 397362
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `contract example decodes into truthful variant metadata`() {
        val dto = json.decodeFromString<VideoVariantsDto>(contractExample)
        val result = classifyVariantsResponse(dto)

        assertTrue(result is VideoVariantsResult.Available)
        val variants = (result as VideoVariantsResult.Available).variants
        assertEquals(3, variants.size)
        assertEquals("original", variants[0].id)
        assertTrue(variants[0].isOriginal)
        assertEquals(720, variants[0].width)
        assertEquals(1280, variants[0].height)
        assertEquals("h264", variants[0].codec)
        assertEquals(5892214L, variants[0].fileSizeBytes)
        assertNull(variants[0].bitrate)
        assertEquals(1542717L, variants[0].estimatedAverageBitrate)
        assertEquals("alt_5393558434975097219", variants[2].id)
        assertEquals("av01", variants[2].codec)
    }

    @Test
    fun `original only status yields no selector and never a failure`() {
        val dto = VideoVariantsDto(
            videoId = "telegram:-1:1:1",
            status = "original_only",
            hasAlternatives = false,
            variants = listOf(
                VideoVariantDto(id = "original", isOriginal = true, height = 720),
            ),
        )

        assertEquals(VideoVariantsResult.OriginalOnly, classifyVariantsResponse(dto))
    }

    @Test
    fun `processing status is reported separately and is selectable-free`() {
        val dto = VideoVariantsDto(
            videoId = "telegram:-1:1:1",
            status = "processing",
            videoProcessingPending = true,
            hasAlternatives = false,
            variants = listOf(
                VideoVariantDto(id = "original", isOriginal = true, height = 720),
            ),
        )

        val result = classifyVariantsResponse(dto)
        assertTrue(result is VideoVariantsResult.Processing)
    }

    @Test
    fun `has_alternatives without actual alternative entries is not selectable`() {
        val dto = VideoVariantsDto(
            status = "ready",
            hasAlternatives = true,
            variants = listOf(VideoVariantDto(id = "original", isOriginal = true)),
        )

        assertEquals(VideoVariantsResult.OriginalOnly, classifyVariantsResponse(dto))
    }

    @Test
    fun `missing variants route is treated as unsupported not as failure`() {
        val notFound = retrofit2.HttpException(
            Response.error<Any?>(404, "Not Found".toResponseBody(null)),
        )

        assertEquals(VideoVariantsResult.Unsupported, classifyVariantsError(notFound))
    }

    @Test
    fun `undecodable body is treated as unsupported resolver behavior`() {
        val decodeError = kotlinx.serialization.SerializationException("not json")

        assertEquals(VideoVariantsResult.Unsupported, classifyVariantsError(decodeError))
    }

    @Test
    fun `server and auth failures are real fetch errors`() {
        val badGateway = retrofit2.HttpException(
            Response.error<Any?>(502, "Bad Gateway".toResponseBody(null)),
        )
        val forbidden = retrofit2.HttpException(
            Response.error<Any?>(403, "Forbidden".toResponseBody(null)),
        )

        assertTrue(classifyVariantsError(badGateway) is VideoVariantsResult.Failed)
        assertTrue(classifyVariantsError(forbidden) is VideoVariantsResult.Failed)
    }

    @Test
    fun `labels name the original and keep equal resolutions distinguishable`() {
        val variants = (classifyVariantsResponse(json.decodeFromString<VideoVariantsDto>(contractExample))
            as VideoVariantsResult.Available).variants

        val labels = buildQualityLabels(variants)

        // Two 1280p variants share the resolution, so codec and size tell them apart.
        assertEquals("1280p h264 5.6 MB (Original)", labels[0])
        assertEquals("1280p h264 3.8 MB", labels[1])
        assertEquals("852p av01 1.4 MB", labels[2])
    }

    @Test
    fun `single variant label carries no codec or size noise`() {
        val variant = VideoVariant(
            id = "original",
            isOriginal = true,
            width = 720,
            height = 1280,
            codec = "h264",
            mimeType = "video/mp4",
            fileSizeBytes = 5892214,
            durationSec = 30.0,
            bitrate = null,
            estimatedAverageBitrate = null,
        )

        assertEquals(listOf("1280p (Original)"), buildQualityLabels(listOf(variant)))
    }

    @Test
    fun `quality descriptor shows resolution codec size and original`() {
        val variant = VideoVariant(
            id = "alt_1",
            isOriginal = false,
            width = 480,
            height = 852,
            codec = "av01",
            mimeType = null,
            fileSizeBytes = 1511665,
            durationSec = null,
            bitrate = null,
            estimatedAverageBitrate = 397362,
        )

        assertEquals("480×852 · av01 · 1.4 MB", formatQualityDescriptor(variant))
    }
}
