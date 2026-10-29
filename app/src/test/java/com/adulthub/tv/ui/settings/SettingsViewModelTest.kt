package com.adulthub.tv.ui.settings

import android.content.Context
import com.adulthub.tv.R
import com.adulthub.tv.data.local.AccountKeyProvider
import com.adulthub.tv.data.local.SearchHistoryStore
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.local.TelegramProgressStore
import com.adulthub.tv.data.local.WatchHistoryStore
import com.adulthub.tv.data.model.AuthState
import com.adulthub.tv.data.repository.AuthRepository
import com.adulthub.tv.data.repository.ChannelRepository
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.TvLibrary
import com.adulthub.tv.data.model.TvChannel
import com.adulthub.tv.data.resolver.ResolverApi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeAuthRepo = FakeSettingsAuthRepo()
    private val fakeLibraryRepo = FakeSettingsLibraryRepo()
    private val fakeDataStore = FakeSettingsDataStore()
    private val context: Context = mockk(relaxed = true)

    private val testLibrary = TvLibrary(id = "lib-1", name = "Adult Movies")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeLibraryRepo.librariesResult = Result.success(listOf(testLibrary))
        every { context.getString(R.string.settings_connection_connected) } returns "Connected"
        every { context.getString(R.string.settings_connection_unavailable, "Resolver timeout") } returns
            "Unavailable: Resolver timeout"
        every { context.getString(R.string.settings_clear_history_done) } returns "Watch history cleared"
        every { context.getString(R.string.settings_clear_resume_done) } returns "Resume progress cleared"
        every { context.getString(R.string.settings_clear_search_done) } returns "Search history cleared"
        every { context.getString(R.string.settings_buffer_large) } returns "Large buffer (30s / 120s)"
        every {
            context.getString(R.string.settings_buffer_profile_set, "Large buffer (30s / 120s)")
        } returns "Buffer profile set to: Large buffer (30s / 120s)"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadSettings populates authenticated state and preferences`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            context = context,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("https://jf.example.com", state.serverUrl)
        assertEquals("Main Jellyfin", state.serverName)
        assertEquals("admin", state.username)
        assertEquals("user-123", state.userId)
        assertEquals("Adult Movies", state.selectedLibraryName)
        assertEquals("", state.resolverBaseUrl)
        assertEquals(512, state.cacheLimitMb)
        assertEquals(1, state.availableLibraries.size)
        assertEquals("lib-1", state.availableLibraries.first().id)
    }

    @Test
    fun `selectLibrary updates DataStore and state message`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            context = context,
        )
        advanceUntilIdle()

        val newLibrary = TvLibrary(id = "lib-2", name = "New Releases")
        viewModel.selectLibrary(newLibrary)
        advanceUntilIdle()

        assertEquals("lib-2", fakeDataStore.lastSavedLibraryId)
        assertEquals("New Releases", fakeDataStore.lastSavedLibraryName)
        assertEquals("lib-2", viewModel.uiState.value.selectedLibraryId)
        assertEquals("New Releases", viewModel.uiState.value.selectedLibraryName)
        assertNotNull(viewModel.uiState.value.statusMessage)
    }

    @Test
    fun `rapid home category toggles keep both selections`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            context = context,
        )
        advanceUntilIdle()
        val saveGate = CompletableDeferred<Unit>()
        fakeDataStore.homeCategorySaveGate = saveGate

        viewModel.toggleHomeCategory("a")
        runCurrent()
        viewModel.toggleHomeCategory("b")
        runCurrent()
        saveGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(setOf("a", "b"), fakeDataStore.selectedHomeCategoryIds.value)
        assertEquals(setOf("a", "b"), viewModel.uiState.value.selectedHomeCategoryIds)
    }

    @Test
    fun `selectLibrary replaces category selections that do not exist in the new library`() = runTest(testDispatcher) {
        fakeDataStore.selectedHomeCategoryIds.value = setOf("old-library-category")
        fakeLibraryRepo.genresResult = Result.success(
            listOf(
                com.adulthub.tv.data.model.TvCategory("new-a", "A"),
                com.adulthub.tv.data.model.TvCategory("new-b", "B"),
                com.adulthub.tv.data.model.TvCategory("new-c", "C"),
            )
        )
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.selectLibrary(TvLibrary(id = "lib-2", name = "New Library"))
        advanceUntilIdle()

        assertEquals(setOf("new-a", "new-b"), fakeDataStore.selectedHomeCategoryIds.value)
        assertEquals(setOf("new-a", "new-b"), viewModel.uiState.value.selectedHomeCategoryIds)
    }

    @Test
    fun `setCacheLimit updates DataStore`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.setCacheLimit(256)
        advanceUntilIdle()

        assertEquals(256, fakeDataStore.lastSavedCacheLimit)
        assertEquals(256, viewModel.uiState.value.cacheLimitMb)
    }

    @Test
    fun `setResolverUrl sanitizes trailing slash and updates DataStore`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.setResolverUrl("https://custom.resolver.com/ ")
        advanceUntilIdle()

        assertEquals("https://custom.resolver.com", fakeDataStore.lastSavedResolverUrl)
        assertEquals("https://custom.resolver.com", viewModel.uiState.value.resolverBaseUrl)
    }

    @Test
    fun `private LAN HTTP resolver requires consent before saving`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.setResolverUrl("http://192.168.1.20:8099/")
        advanceUntilIdle()

        assertEquals("", fakeDataStore.lastSavedResolverUrl)
        assertEquals("http://192.168.1.20:8099", viewModel.uiState.value.pendingHttpConsentUrl)

        viewModel.confirmHttpConsent()
        advanceUntilIdle()

        assertEquals("http://192.168.1.20:8099", fakeDataStore.lastSavedResolverUrl)
        assertTrue("http://192.168.1.20:8099" in fakeDataStore.approvedCleartextOrigins.value)
    }

    @Test
    fun `public HTTP resolver is rejected`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.setResolverUrl("http://resolver.example.com:8099")
        advanceUntilIdle()

        assertEquals("", fakeDataStore.lastSavedResolverUrl)
        assertEquals(null, viewModel.uiState.value.pendingHttpConsentUrl)
        assertTrue(viewModel.uiState.value.statusMessage.orEmpty().contains("HTTPS"))
    }

    @Test
    fun `setLowRamMode updates DataStore and state`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            context = context,
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.lowRamMode)
        viewModel.setLowRamMode(true)
        advanceUntilIdle()

        assertTrue(fakeDataStore.lastSavedLowRamMode)
        assertTrue(viewModel.uiState.value.lowRamMode)
    }

    @Test
    fun `logout clears auth session and marks loggedOut`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertTrue(fakeAuthRepo.loggedOut)
        assertTrue(viewModel.uiState.value.isLoggedOut)
    }

    @Test
    fun `connection check reports Jellyfin and resolver independently`() = runTest(testDispatcher) {
        val resolverRepository = FakeSettingsChannelRepo().apply {
            categoriesResult = Result.failure(RuntimeException("Resolver timeout"))
        }
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            channelRepository = resolverRepository,
            context = context,
        )
        advanceUntilIdle()

        viewModel.checkConnections()
        advanceUntilIdle()

        assertEquals("Connected", viewModel.uiState.value.jellyfinConnectionStatus)
        assertTrue(viewModel.uiState.value.resolverConnectionStatus.contains("Resolver timeout"))
        assertEquals(false, viewModel.uiState.value.isCheckingConnections)
    }
    @Test
    fun `setBufferProfile updates setting and status message`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.setBufferProfile(com.adulthub.tv.data.local.BufferProfile.LARGE)
        advanceUntilIdle()

        assertEquals(com.adulthub.tv.data.local.BufferProfile.LARGE, viewModel.uiState.value.bufferProfile)
        assertEquals(com.adulthub.tv.data.local.BufferProfile.LARGE, fakeDataStore.lastSavedBufferProfile)
        assertTrue(viewModel.uiState.value.statusMessage?.contains("Large buffer") == true)
    }

    @Test
    fun `setSeekStepSec updates DataStore and state`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.setSeekStepSec(30)
        advanceUntilIdle()

        assertEquals(30, fakeDataStore.lastSavedSeekStepSec)
        assertEquals(30, viewModel.uiState.value.seekStepSec)
    }

    @Test
    fun `setAutoplayNext updates DataStore and state`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.setAutoplayNext(true)
        advanceUntilIdle()

        assertTrue(fakeDataStore.lastSavedAutoplayNext)
        assertTrue(viewModel.uiState.value.autoplayNext)
    }

    @Test
    fun `clearWatchHistory delegates to store and updates statusMessage`() = runTest(testDispatcher) {
        val fakeWatchHistoryStore = FakeWatchHistoryStore()
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            watchHistoryStore = fakeWatchHistoryStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.clearWatchHistory()
        advanceUntilIdle()

        assertEquals(1, fakeWatchHistoryStore.clearCount)
        assertEquals("Watch history cleared", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun `clearResumeProgress delegates to store and updates statusMessage`() = runTest(testDispatcher) {
        val fakeProgressStore = FakeTelegramProgressStore()
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            telegramProgressStore = fakeProgressStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.clearResumeProgress()
        advanceUntilIdle()

        assertEquals(1, fakeProgressStore.clearCount)
        assertEquals("Resume progress cleared", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun `clearSearchHistory delegates to store and updates statusMessage`() = runTest(testDispatcher) {
        val fakeSearchStore = FakeSearchHistoryStore()
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            searchHistoryStore = fakeSearchStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.clearSearchHistory()
        advanceUntilIdle()

        assertEquals(1, fakeSearchStore.clearCount)
        assertEquals("Search history cleared", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun `excludeTag and removeExcludedTag update dataStore and state`() = runTest(testDispatcher) {
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            context = context,
        )
        advanceUntilIdle()

        viewModel.excludeTag("pov")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.excludedTags.contains("pov"))

        viewModel.removeExcludedTag("pov")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.excludedTags.contains("pov"))
    }

    @Test
    fun `unhideChannel removes channel from hiddenChannelIds`() = runTest(testDispatcher) {
        fakeDataStore.hiddenChannelIds.value = setOf("channel-1", "channel-2")
        val viewModel = SettingsViewModel(
            authRepository = fakeAuthRepo,
            libraryRepository = fakeLibraryRepo,
            settingsDataStore = fakeDataStore,
            context = context,
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hiddenChannelIds.contains("channel-1"))
        viewModel.unhideChannel("channel-1")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hiddenChannelIds.contains("channel-1"))
        assertTrue(viewModel.uiState.value.hiddenChannelIds.contains("channel-2"))
    }
}

private class FakeWatchHistoryStore : WatchHistoryStore(
    context = mockk(relaxed = true),
    accountKeyProvider = AccountKeyProvider.ANONYMOUS,
    dataStore = mockk(relaxed = true),
) {
    var clearCount = 0
    override suspend fun clearHistory() {
        clearCount++
    }
}

private class FakeTelegramProgressStore : TelegramProgressStore(
    context = mockk(relaxed = true),
    accountKeyProvider = AccountKeyProvider.ANONYMOUS,
    dataStore = mockk(relaxed = true),
) {
    var clearCount = 0
    override suspend fun clearAll() {
        clearCount++
    }
}

private class FakeSearchHistoryStore : SearchHistoryStore(
    context = mockk(relaxed = true),
    accountKeyProvider = AccountKeyProvider.ANONYMOUS,
    dataStore = mockk(relaxed = true),
) {
    var clearCount = 0
    override suspend fun clearHistory() {
        clearCount++
    }
}

private class FakeSettingsAuthRepo : AuthRepository(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)) {
    val authStateFlow = MutableStateFlow<AuthState>(
        AuthState.Authenticated(
            serverUrl = "https://jf.example.com",
            userId = "user-123",
            accessToken = "tok-abc",
            username = "admin",
            serverName = "Main Jellyfin",
        )
    )
    override val authState: Flow<AuthState> = authStateFlow
    var loggedOut = false

    var logoutResult: Result<Unit> = Result.success(Unit)

    override suspend fun logout(): Result<Unit> {
        loggedOut = true
        return logoutResult
    }
}

private class FakeSettingsLibraryRepo : LibraryRepository(mockk(relaxed = true), mockk(relaxed = true)) {
    var librariesResult: Result<List<TvLibrary>> = Result.success(emptyList())
    var genresResult: Result<List<com.adulthub.tv.data.model.TvCategory>> = Result.success(emptyList())

    override suspend fun getLibraries(): Result<List<TvLibrary>> {
        return librariesResult
    }

    override suspend fun getGenres(parentId: String?, limit: Int): Result<List<com.adulthub.tv.data.model.TvCategory>> =
        genresResult
}

private class FakeSettingsChannelRepo : ChannelRepository(mockk<ResolverApi>(relaxed = true)) {
    var categoriesResult: Result<List<TvChannel>> = Result.success(emptyList())

    override suspend fun getCategories(): Result<List<TvChannel>> = categoriesResult
}

private class FakeSettingsDataStore : SettingsDataStore(mockk(relaxed = true)) {
    var lastSavedLibraryId: String? = "lib-1"
    var lastSavedLibraryName: String? = "Adult Movies"
    var lastSavedResolverUrl: String = ""
    var lastSavedCacheLimit: Int = 512
    var lastSavedPhashOnPlayback: Boolean = false
    var lastSavedBufferProfile: com.adulthub.tv.data.local.BufferProfile = com.adulthub.tv.data.local.BufferProfile.STANDARD
    var lastSavedLowRamMode: Boolean = false
    var lastSavedSeekStepSec: Int = 15
    var lastSavedAutoplayNext: Boolean = false

    override val selectedLibraryId = MutableStateFlow<String?>("lib-1")
    override val selectedLibraryName = MutableStateFlow<String?>("Adult Movies")
    override val resolverBaseUrl = MutableStateFlow("")
    override val resolverBaseUrlSource = MutableStateFlow(com.adulthub.tv.data.local.ResolverUrlSource.DEFAULT)
    override val approvedCleartextOrigins = MutableStateFlow<Set<String>>(emptySet())
    override val cacheLimitMb = MutableStateFlow(512)
    override val phashOnPlayback = MutableStateFlow(false)
    override val bufferProfile = MutableStateFlow(com.adulthub.tv.data.local.BufferProfile.STANDARD)
    override val lowRamMode = MutableStateFlow(false)
    override val seekStepSec = MutableStateFlow(15)
    override val autoplayNext = MutableStateFlow(false)
    override val selectedHomeCategoryIds = MutableStateFlow<Set<String>?>(null)
    override val hiddenChannelIds = MutableStateFlow<Set<String>>(emptySet())
    override val excludedTags = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun hideChannel(channelId: String) {
        hiddenChannelIds.value = hiddenChannelIds.value + channelId
    }

    override suspend fun unhideChannel(channelId: String) {
        hiddenChannelIds.value = hiddenChannelIds.value - channelId
    }

    override suspend fun excludeTag(tag: String) {
        excludedTags.value = excludedTags.value + tag
    }

    override suspend fun removeExcludedTag(tag: String) {
        excludedTags.value = excludedTags.value - tag
    }

    override suspend fun isCleartextOriginApproved(url: String): Boolean =
        url.startsWith("https://") || url in approvedCleartextOrigins.value

    override suspend fun approveCleartextOrigin(url: String) {
        approvedCleartextOrigins.value = approvedCleartextOrigins.value + url
    }

    override suspend fun setSelectedLibrary(libraryId: String, libraryName: String) {
        lastSavedLibraryId = libraryId
        lastSavedLibraryName = libraryName
        selectedLibraryId.value = libraryId
        selectedLibraryName.value = libraryName
    }

    override suspend fun setResolverBaseUrl(url: String) {
        lastSavedResolverUrl = url
        resolverBaseUrl.value = url
    }

    override suspend fun setCacheLimitMb(limitMb: Int) {
        lastSavedCacheLimit = limitMb
        cacheLimitMb.value = limitMb
    }

    override suspend fun setPhashOnPlayback(enabled: Boolean) {
        lastSavedPhashOnPlayback = enabled
        phashOnPlayback.value = enabled
    }

    override suspend fun setBufferProfile(profile: com.adulthub.tv.data.local.BufferProfile) {
        lastSavedBufferProfile = profile
        bufferProfile.value = profile
    }

    override suspend fun setLowRamMode(enabled: Boolean) {
        lastSavedLowRamMode = enabled
        lowRamMode.value = enabled
    }

    override suspend fun setSeekStepSec(seconds: Int) {
        lastSavedSeekStepSec = seconds
        seekStepSec.value = seconds
    }

    override suspend fun setAutoplayNext(enabled: Boolean) {
        lastSavedAutoplayNext = enabled
        autoplayNext.value = enabled
    }

    var homeCategorySaveGate: CompletableDeferred<Unit>? = null

    override suspend fun setSelectedHomeCategoryIds(ids: Set<String>) {
        homeCategorySaveGate?.await()
        selectedHomeCategoryIds.value = ids
    }
}
