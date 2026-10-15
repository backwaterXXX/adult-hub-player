package com.adulthub.tv.di

import com.adulthub.tv.data.resolver.ResolverApiClient
import org.junit.Assert.assertNotNull
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
}
