package com.adulthub.tv.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.adulthub.tv.R
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.AccountKeyProvider
import com.adulthub.tv.data.local.AccountScope
import com.adulthub.tv.data.local.AuthDataStore
import com.adulthub.tv.data.local.PhashJobStore
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.model.AuthState
import com.adulthub.tv.data.model.TvItem
import com.adulthub.tv.data.network.ServiceUrl
import com.adulthub.tv.data.resolver.PhashGenerationDto
import com.adulthub.tv.data.resolver.PhashJobDto
import com.adulthub.tv.data.resolver.PhashJobStatusDto
import com.adulthub.tv.data.resolver.ResolverApi
import com.adulthub.tv.data.resolver.ResolverApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.seconds

/**
 * A resolver task belongs to the session that started it.
 *
 * The task must not continue with the token or the Resolver of a newer session,
 * and its state must not be written into the account of the newer session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhashSessionTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var jobStore: PhashJobStore
    private lateinit var authState: MutableStateFlow<AuthState>
    private lateinit var accountScope: AccountScope

    private val firstSession = AuthState.Authenticated(
        serverUrl = "https://jellyfin.example.org",
        userId = "user-1",
        accessToken = "token-one",
        username = "user",
        serverName = "Jellyfin",
    )

    @Before
    fun setUp() {
        authState = MutableStateFlow(firstSession)
        accountScope = AccountScope(authDataStore(authState))
        every { context.getString(R.string.phash_timeout) } returns "Video matching timed out"
    }

    private fun createJobStore(scope: kotlinx.coroutines.CoroutineScope): PhashJobStore {
        val testFile = tmpFolder.newFile("phash_jobs.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { testFile },
        )
        jobStore = PhashJobStore(context, accountScope, dataStore)
        return jobStore
    }

    private fun authDataStore(state: MutableStateFlow<AuthState>): AuthDataStore {
        val store = mockk<AuthDataStore>()
        every { store.authState } returns state
        coEvery { store.getAuthState() } returns state.value
        return store
    }

    @Test
    fun `a task of an older session is not written into the account of the newer session`() = runTest(timeout = 120.seconds) {
        val tokenRequests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            tokenRequests += request.header("X-Emby-Token").orEmpty()
            val body = if (request.url.encodedPath.endsWith("/generate")) {
                """{"ok":true,"job_id":"job-1","state":"queued"}"""
            } else {
                """{"ok":true,"job":{"id":"job-1","video_id":"telegram:-100:1:1","state":"processing"}}"""
            }
            okhttp3.Response.Builder()
                .request(request).protocol(Protocol.HTTP_1_0).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        val authDataStore = authDataStore(authState)
        val jellyfinClientManager = mockk<JellyfinClientManager>()
        val api = mockk<org.jellyfin.sdk.api.client.ApiClient>(relaxed = true)
        every { jellyfinClientManager.getApiOrNull() } returns api
        every { api.accessToken } returns "token-one"

        val settingsDataStore = mockk<SettingsDataStore>()
        every { settingsDataStore.phashOnPlayback } returns flowOf(true)
        coEvery { settingsDataStore.getResolverBaseUrl() } returns "https://resolver.example.org"
        coEvery { settingsDataStore.requireAllowedServiceUrl(any()) } returns ServiceUrl(
            normalized = "https://resolver.example.org",
            origin = "https://resolver.example.org",
            usesCleartext = false,
        )

        val jobStore = createJobStore(backgroundScope)
        val coordinator = PhashCoordinator(
            authDataStore = authDataStore,
            jellyfinClientManager = jellyfinClientManager,
            settingsDataStore = settingsDataStore,
            apiClient = ResolverApiClient(client),
            jobStore = jobStore,
            applicationScope = backgroundScope,
            accountScope = accountScope,
            context = context,
        )

        val item = TvItem(
            id = "telegram:-100:1:1",
            title = "Video",
            providerVideoId = "-100:1:1",
            runtimeTicks = 600_000_000L,
        )

        coordinator.enqueueGeneration(item, observedDurationMs = 600_000L)
        advanceTimeBy(6_000L)

        // The task of the first session belongs to the first account.
        val firstAccountKey = AccountScope.keyOf(firstSession)
        assertNotNull(jobStore.get(item.id, firstAccountKey))

        // A new session of another account starts.
        val secondSession = AuthState.Authenticated(
            serverUrl = "https://jellyfin.example.org",
            userId = "user-2",
            accessToken = "token-two",
            username = "other",
            serverName = "Jellyfin",
        )
        authState.value = secondSession
        advanceTimeBy(6_000L)

        // The state of the old task stays with its account and does not appear for
        // the account of the new session.
        val secondAccountKey = AccountScope.keyOf(secondSession)
        assertNull(jobStore.get(item.id, secondAccountKey))
        assertNotNull(jobStore.get(item.id, firstAccountKey))

        // The status request never used the token of the new session.
        assertTrue(tokenRequests.all { it == "token-one" })
    }

    @Test
    fun `a task that used up its budget is marked as timed out and is not polled again`() = runTest(timeout = 120.seconds) {
        val api = mockk<ResolverApi>()
        coEvery { api.generatePhash(any(), any()) } returns PhashGenerationDto(
            ok = true,
            jobId = "job-1",
            state = "queued",
        )
        coEvery { api.getPhashJob(any(), any()) } returns PhashJobStatusDto(
            ok = true,
            job = PhashJobDto(
                id = "job-1",
                videoId = "telegram:-100:1:1",
                state = "processing",
            ),
        )
        val apiClient = mockk<ResolverApiClient>()
        every { apiClient.create(any()) } returns api

        val authDataStore = authDataStore(authState)
        val jellyfinClientManager = mockk<JellyfinClientManager>()
        val apiClientJellyfin = mockk<org.jellyfin.sdk.api.client.ApiClient>(relaxed = true)
        every { jellyfinClientManager.getApiOrNull() } returns apiClientJellyfin
        every { apiClientJellyfin.accessToken } returns "token-one"

        val settingsDataStore = mockk<SettingsDataStore>()
        every { settingsDataStore.phashOnPlayback } returns flowOf(true)
        coEvery { settingsDataStore.getResolverBaseUrl() } returns "https://resolver.example.org"
        coEvery { settingsDataStore.requireAllowedServiceUrl(any()) } returns ServiceUrl(
            normalized = "https://resolver.example.org",
            origin = "https://resolver.example.org",
            usesCleartext = false,
        )

        val jobStore = createJobStore(backgroundScope)
        val coordinator = PhashCoordinator(
            authDataStore = authDataStore,
            jellyfinClientManager = jellyfinClientManager,
            settingsDataStore = settingsDataStore,
            apiClient = apiClient,
            jobStore = jobStore,
            applicationScope = backgroundScope,
            accountScope = accountScope,
            // One status request is allowed, so the budget is used up with it.
            budget = PhashBudget(totalLifetimeMs = 600_000L, maxPolls = 1),
            context = context,
        )

        val item = TvItem(
            id = "telegram:-100:1:1",
            title = "Video",
            providerVideoId = "-100:1:1",
            runtimeTicks = 600_000_000L,
        )

        coordinator.enqueueGeneration(item, observedDurationMs = 600_000L)
        advanceTimeBy(6_000L)

        // The task is stored as failed with the reason of the timeout, so it is not
        // continued at the next start of the app.
        val accountKey = AccountScope.keyOf(firstSession)
        val record = jobStore.get(item.id, accountKey)
        assertEquals("failed", record?.state)
        assertEquals("timeout", record?.errorCode)

        // The status of the task is terminal and readable.
        val status = coordinator.jobStatuses.value[item.id]
        assertNotNull(status)
        assertTrue(status!!.terminal)
        assertEquals("Video matching timed out", status.message)

        // The task was polled once only.
        coVerify(exactly = 1) { api.getPhashJob(any(), any()) }
    }

    @Test
    fun `the account key of a session differs from the key of another account`() {
        val secondSession = AuthState.Authenticated(
            serverUrl = "https://jellyfin.example.org",
            userId = "user-2",
            accessToken = "token-two",
            username = "other",
            serverName = "Jellyfin",
        )

        assertTrue(AccountScope.keyOf(firstSession) != AccountScope.keyOf(secondSession))
        assertTrue(AccountScope.markerOf(firstSession) != AccountScope.markerOf(secondSession))
        assertEquals(AccountScope.markerOf(firstSession), AccountScope.markerOf(firstSession))
    }

    @Test
    fun `a store without a session writes into the anonymous namespace`() = runTest(timeout = 120.seconds) {
        val jobStore = createJobStore(backgroundScope)
        val anonymousStore = PhashJobStore(context, AccountKeyProvider.ANONYMOUS, dataStore)

        assertNull(anonymousStore.get("video-1"))
        anonymousStore.save(
            com.adulthub.tv.data.local.StoredPhashJob(videoId = "video-1", state = "queued"),
        )

        assertNotNull(anonymousStore.get("video-1"))
        assertNull(jobStore.get("video-1"))
    }
}
