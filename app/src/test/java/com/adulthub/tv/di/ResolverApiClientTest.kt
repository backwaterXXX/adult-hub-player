package com.adulthub.tv.di

import com.adulthub.tv.data.resolver.ResolverApiClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class ResolverApiClientTest {

    @Test
    fun `ResolverApiClient creates ResolverApi interface successfully`() {
        val client = ResolverApiClient()
        val api = client.create("https://resolver.example.com")
        assertNotNull(api)
    }

    @Test
    fun `ResolverApiClient normalizes URL without trailing slash`() {
        val client = ResolverApiClient()
        val apiWithSlash = client.create("https://resolver.example.com/")
        val apiWithoutSlash = client.create("https://resolver.example.com")
        assertNotNull(apiWithSlash)
        assertSame(apiWithSlash, apiWithoutSlash)
    }

    @Test
    fun `ResolverApiClient bounds the number of cached clients`() {
        val client = ResolverApiClient()
        val first = client.create("https://resolver-0.example.com")
        repeat(ResolverApiClient.MAX_CACHED_CLIENTS + 4) { index ->
            client.create("https://resolver-${index + 1}.example.com")
        }
        // The least recently used entry is dropped, the newest one is still cached.
        assertNotSame(first, client.create("https://resolver-0.example.com"))
        assertSame(
            client.create("https://resolver-${ResolverApiClient.MAX_CACHED_CLIENTS + 3}.example.com"),
            client.create("https://resolver-${ResolverApiClient.MAX_CACHED_CLIENTS + 3}.example.com"),
        )
    }
}
