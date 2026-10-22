package com.adulthub.tv.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which image origins may receive the Jellyfin access token.
 *
 * The Resolver builds the thumbnails of the video matching, and the image
 * loader fetches them without a header of its own. The token must reach the
 * Resolver and the Jellyfin server, and no other image host.
 */
class ImageTokenOriginPolicyTest {
    private val jellyfin = "http://192.168.1.111:8096"
    private val resolver = "https://adult.ferges.eu"
    private val trusted = listOf(jellyfin, resolver)

    @Test
    fun `allows the Jellyfin origin`() {
        assertTrue(
            ImageTokenOriginPolicy.allows(
                "http://192.168.1.111:8096/Items/abc/Images/Primary?tag=1",
                trusted,
            )
        )
    }

    @Test
    fun `allows the Resolver origin of a video matching thumbnail`() {
        assertTrue(
            ImageTokenOriginPolicy.allows(
                "https://adult.ferges.eu/admin/api/v2/video-phash/thumbnail" +
                    "?video_id=telegram%3A-1001140295673%3A799%3A6048558423836984833",
                trusted,
            )
        )
    }

    @Test
    fun `refuses the image host of a third party`() {
        assertFalse(
            ImageTokenOriginPolicy.allows("https://image.tmdb.org/t/p/w500/poster.jpg", trusted)
        )
    }

    @Test
    fun `refuses the same host on another port`() {
        assertFalse(
            ImageTokenOriginPolicy.allows("https://adult.ferges.eu:8443/admin/api/v2/x", trusted)
        )
    }

    @Test
    fun `treats an implicit default port like an explicit one`() {
        assertTrue(
            ImageTokenOriginPolicy.allows(
                "https://adult.ferges.eu:443/admin/api/v2/video-phash/thumbnail?video_id=x",
                listOf("https://adult.ferges.eu"),
            )
        )
    }

    @Test
    fun `ignores a trailing slash and a path of the trusted address`() {
        assertTrue(
            ImageTokenOriginPolicy.allows(
                "https://adult.ferges.eu/admin/api/v2/video-phash/thumbnail?video_id=x",
                listOf("https://adult.ferges.eu/"),
            )
        )
        assertTrue(
            ImageTokenOriginPolicy.allows(
                "https://adult.ferges.eu/admin/api/v2/video-phash/thumbnail?video_id=x",
                listOf("https://adult.ferges.eu/admin"),
            )
        )
    }

    @Test
    fun `compares the host regardless of letter case`() {
        assertTrue(
            ImageTokenOriginPolicy.allows(
                "https://Adult.Ferges.EU/admin/api/v2/x",
                listOf("https://adult.ferges.eu"),
            )
        )
    }

    @Test
    fun `refuses every origin when no trusted address is known`() {
        assertFalse(
            ImageTokenOriginPolicy.allows(
                "https://adult.ferges.eu/admin/api/v2/video-phash/thumbnail?video_id=x",
                listOf(null, "", "   "),
            )
        )
    }

    @Test
    fun `refuses an unusable request address`() {
        assertFalse(ImageTokenOriginPolicy.allows("", trusted))
        assertFalse(ImageTokenOriginPolicy.allows("not a url", trusted))
    }

    @Test
    fun `reduces an address to its origin`() {
        assertEquals("https://adult.ferges.eu:443", ImageTokenOriginPolicy.originOf("https://adult.ferges.eu/a?b=c"))
        assertEquals("http://192.168.1.111:8096", ImageTokenOriginPolicy.originOf("http://192.168.1.111:8096"))
        assertNull(ImageTokenOriginPolicy.originOf(null))
        assertNull(ImageTokenOriginPolicy.originOf("ftp://adult.ferges.eu"))
    }
}
