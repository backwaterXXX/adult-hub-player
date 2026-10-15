package com.adulthub.tv.data.resolver

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolverDtoTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun `video views accepts descriptive counts without failing the page`() {
        val videos = json.decodeFromString<List<VideoDto>>(
            """[
                {"id":"video-1","title":"Video One","views":"1.2k"},
                {"id":"video-2","title":"Video Two","views":"1200"}
            ]"""
        )

        assertEquals(listOf("1.2k", "1200"), videos.map { it.views })
    }
}
