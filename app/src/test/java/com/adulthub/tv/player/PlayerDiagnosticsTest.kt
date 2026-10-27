package com.adulthub.tv.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerDiagnosticsTest {
    @Test
    fun `reports hardware decoding when no decoder name is known yet`() {
        assertEquals("Hardware (HW)", decoderMode(null, null))
        assertEquals("Hardware (HW)", decoderMode("OMX.qcom.video.decoder.avc", "c2.qcom.ac3"))
    }

    @Test
    fun `names the software fallback per track`() {
        assertEquals("Fallback (V:SW)", decoderMode("c2.android.avc.decoder", "c2.qcom.ac3"))
        assertEquals("Fallback (A:SW)", decoderMode("OMX.qcom.video.decoder.avc", "OMX.google.aac.decoder"))
        assertEquals("Fallback (V:SW, A:SW)", decoderMode("c2.android.avc.decoder", "c2.android.aac.decoder"))
    }

    @Test
    fun `treats a missing video decoder as hardware`() {
        assertEquals("Fallback (A:SW)", decoderMode(null, "ffmpeg-audio-dec"))
    }

    @Test
    fun `recognises the telegram proxy stream as its own playback method`() {
        assertEquals(
            "Direct Stream (Telegram Proxy)",
            playMethod("http://192.168.1.20:8099/admin/play/telegram:-100:1:2/stream?mode=proxy"),
        )
    }

    @Test
    fun `recognises a transcode by its playlist or transcoding path`() {
        assertEquals("Transcode", playMethod("https://server.example/stream/main.m3u8"))
        assertEquals("Transcode", playMethod("https://server.example/videos/transcoding/42"))
    }

    @Test
    fun `falls back to direct play for a plain media address`() {
        assertEquals("Direct Play", playMethod("https://server.example/videos/42/stream.mp4"))
    }

    @Test
    fun `reads the total size from a range response`() {
        assertEquals(
            7_340_032L,
            detectedStreamSize(
                headers = mapOf("Content-Range" to listOf("bytes 0-1023/7340032")),
                contentLength = 1_024L,
                position = 0L,
                requestedLength = 1_024L,
            ),
        )
    }

    @Test
    fun `reads the total size from a full response`() {
        assertEquals(
            7_340_032L,
            detectedStreamSize(
                headers = emptyMap(),
                contentLength = 7_340_032L,
                position = 0L,
                requestedLength = C.LENGTH_UNSET.toLong(),
            ),
        )
    }

    @Test
    fun `refuses to report the length of a partial window as the file size`() {
        assertEquals(
            null,
            detectedStreamSize(
                headers = emptyMap(),
                contentLength = 1_024L,
                position = 4_096L,
                requestedLength = 1_024L,
            ),
        )
        assertEquals(
            null,
            detectedStreamSize(
                headers = mapOf("Content-Range" to listOf("bytes 0-1023/invalid")),
                contentLength = -1L,
                position = 0L,
                requestedLength = C.LENGTH_UNSET.toLong(),
            ),
        )
    }

    @Test
    fun `matches the range header regardless of casing`() {
        assertEquals(
            512L,
            detectedStreamSize(
                headers = mapOf("content-range" to listOf("bytes 0-511/512")),
                contentLength = 512L,
                position = 0L,
                requestedLength = 512L,
            ),
        )
    }
}
