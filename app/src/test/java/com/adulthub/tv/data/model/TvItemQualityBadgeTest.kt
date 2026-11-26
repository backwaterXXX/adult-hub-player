package com.adulthub.tv.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvItemQualityBadgeTest {

    private fun item(height: Int? = null, quality: String? = null) = TvItem(
        id = "telegram:-1001618765010:1:2",
        title = "Scene",
        height = height,
        quality = quality
    )

    @Test
    fun `a real 4K resolution earns the 4K badge`() {
        assertEquals(QualityBadge.UHD, item(height = 2160, quality = "high").qualityBadge)
    }

    @Test
    fun `a high tier without the resolution stays HQ`() {
        assertEquals(QualityBadge.HQ, item(height = 1080, quality = "high").qualityBadge)
        assertEquals(QualityBadge.HQ, item(quality = "high").qualityBadge)
    }

    @Test
    fun `the medium tier is HD`() {
        assertEquals(QualityBadge.HD, item(height = 720, quality = "medium").qualityBadge)
    }

    @Test
    fun `low and unknown quality stay unlabelled`() {
        assertNull(item(height = 480, quality = "low").qualityBadge)
        assertNull(item(height = 1080).qualityBadge)
        assertNull(item().qualityBadge)
    }

    @Test
    fun `the tier comparison ignores the case`() {
        assertEquals(QualityBadge.HQ, item(quality = "HIGH").qualityBadge)
    }
}
