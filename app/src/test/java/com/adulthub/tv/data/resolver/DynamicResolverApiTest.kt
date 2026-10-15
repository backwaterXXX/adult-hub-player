package com.adulthub.tv.data.resolver

import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.network.ServiceUrl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DynamicResolverApiTest {

    @Test
    fun `each request uses the currently configured resolver origin`() = runBlocking {
        val settings = mockk<SettingsDataStore>()
        val client = mockk<ResolverApiClient>()
        val firstApi = mockk<ResolverApi>()
        val secondApi = mockk<ResolverApi>()
        var currentUrl = "https://resolver-a.example"

        coEvery { settings.getResolverBaseUrl() } answers { currentUrl }
        coEvery { settings.requireAllowedServiceUrl(any()) } coAnswers {
            val url = firstArg<String>()
            ServiceUrl(normalized = url, origin = url, usesCleartext = false)
        }
        every { client.create("https://resolver-a.example") } returns firstApi
        every { client.create("https://resolver-b.example") } returns secondApi
        coEvery { firstApi.getCategories() } returns listOf(
            CategoryDto(id = "a", title = "A", slug = "a"),
        )
        coEvery { secondApi.getCategories() } returns listOf(
            CategoryDto(id = "b", title = "B", slug = "b"),
        )
        val dynamicApi = DynamicResolverApi(client, settings)

        assertEquals("a", dynamicApi.getCategories().single().id)
        currentUrl = "https://resolver-b.example"
        assertEquals("b", dynamicApi.getCategories().single().id)

        coVerify(exactly = 1) { firstApi.getCategories() }
        coVerify(exactly = 1) { secondApi.getCategories() }
        assertSame(firstApi, client.create("https://resolver-a.example"))
        assertSame(secondApi, client.create("https://resolver-b.example"))
    }

    @Test
    fun `blank resolver url fails before creating an api client`() = runBlocking {
        val settings = mockk<SettingsDataStore>()
        val client = mockk<ResolverApiClient>()
        coEvery { settings.getResolverBaseUrl() } returns "  "
        val dynamicApi = DynamicResolverApi(client, settings)

        val result = runCatching { dynamicApi.getCategories() }

        assertEquals(
            "Set a Resolver URL in Settings before using channel features",
            result.exceptionOrNull()?.message,
        )
        io.mockk.verify(exactly = 0) { client.create(any()) }
    }
}
