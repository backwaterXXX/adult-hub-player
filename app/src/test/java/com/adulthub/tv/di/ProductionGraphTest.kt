package com.adulthub.tv.di

import android.app.Application
import android.content.Context
import com.adulthub.tv.data.jellyfin.JellyfinClientManager
import com.adulthub.tv.data.local.AccountKeyProvider
import com.adulthub.tv.data.local.AccountScope
import com.adulthub.tv.data.local.AuthDataStore
import com.adulthub.tv.data.local.PhashJobStore
import com.adulthub.tv.data.local.SearchHistoryStore
import com.adulthub.tv.data.local.SettingsDataStore
import com.adulthub.tv.data.local.TelegramProgressStore
import com.adulthub.tv.data.local.UserScopedDataMigration
import com.adulthub.tv.data.local.WatchHistoryStore
import com.adulthub.tv.data.repository.AuthRepository
import com.adulthub.tv.data.repository.ChannelRepository
import com.adulthub.tv.data.repository.LibraryRepository
import com.adulthub.tv.data.repository.PersonRepository
import com.adulthub.tv.data.repository.PlaybackRepository
import com.adulthub.tv.data.repository.StudioRepository
import com.adulthub.tv.data.resolver.ResolverApi
import com.adulthub.tv.data.update.AppUpdateManager
import com.adulthub.tv.data.update.AppUpdateRepository
import com.adulthub.tv.player.PhashCoordinator
import com.adulthub.tv.player.PlaybackReporter
import com.adulthub.tv.ui.auth.AuthViewModel
import com.adulthub.tv.ui.detail.ItemDetailViewModel
import com.adulthub.tv.ui.home.HomeViewModel
import com.adulthub.tv.ui.library.LibraryViewModel
import com.adulthub.tv.ui.person.PersonDetailViewModel
import com.adulthub.tv.ui.player.PlayerViewModel
import com.adulthub.tv.ui.search.SearchViewModel
import com.adulthub.tv.ui.settings.SettingsViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf

/**
 * Resolves the production dependency graph.
 *
 * Starting Koin alone proves nothing: the definitions are lazy and their
 * dependencies are only looked up when a definition is created. This test
 * resolves every store, repository, service and view model of the production
 * modules, so a missing binding fails here instead of at runtime.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductionGraphTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var koin: Koin

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val filesDir = tmpFolder.newFolder("files")
        // One context that returns itself as the application context, so DataStore
        // never reaches an unstubbed context and can resolve its file.
        val context = mockk<Application>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.filesDir } returns filesDir
        every { context.packageName } returns "com.adulthub.tv"

        koin = startKoin {
            androidContext(context)
            modules(appModule, repositoryModule, viewModelModule)
        }.koin
    }

    @After
    fun tearDown() {
        // The application scope keeps background work of the production graph alive.
        runCatching { koin.get<kotlinx.coroutines.CoroutineScope>().cancel() }
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `every production store resolves with its dependencies`() {
        assertNotNull(koin.get<AuthDataStore>())
        assertNotNull(koin.get<SettingsDataStore>())
        assertNotNull(koin.get<WatchHistoryStore>())
        assertNotNull(koin.get<SearchHistoryStore>())
        assertNotNull(koin.get<TelegramProgressStore>())
        assertNotNull(koin.get<PhashJobStore>())
        assertNotNull(koin.get<UserScopedDataMigration>())
    }

    @Test
    fun `the account scope is one shared instance for the interface and the class`() {
        val byInterface = koin.get<AccountKeyProvider>()
        val byClass = koin.get<AccountScope>()

        assertSame(byClass, byInterface)
    }

    @Test
    fun `every production repository and service resolves`() {
        assertNotNull(koin.get<JellyfinClientManager>())
        assertNotNull(koin.get<ResolverApi>())
        assertNotNull(koin.get<AuthRepository>())
        assertNotNull(koin.get<LibraryRepository>())
        assertNotNull(koin.get<PersonRepository>())
        assertNotNull(koin.get<PlaybackRepository>())
        assertNotNull(koin.get<StudioRepository>())
        assertNotNull(koin.get<ChannelRepository>())
        assertNotNull(koin.get<AppUpdateRepository>())
        assertNotNull(koin.get<AppUpdateManager>())
        assertNotNull(koin.get<PlaybackReporter>())
        assertNotNull(koin.get<PhashCoordinator>())
    }

    @Test
    fun `every production view model resolves with its optional dependencies`() {
        assertNotNull(koin.get<AuthViewModel>())
        assertNotNull(koin.get<HomeViewModel>())
        assertNotNull(koin.get<ItemDetailViewModel>())
        assertNotNull(koin.get<PersonDetailViewModel>())
        assertNotNull(koin.get<SearchViewModel>())
        assertNotNull(koin.get<SettingsViewModel>())

        // The library view model takes a saved state handle. It comes from the view model
        // owner at runtime, so the test provides it as a creation parameter.
        val savedState = androidx.lifecycle.SavedStateHandle(mapOf<String, Any?>())
        assertNotNull(koin.get<LibraryViewModel> { parametersOf(savedState) })

        // The player view model declares optional dependencies. They must not be
        // silently null when the production graph is used.
        val player = koin.get<PlayerViewModel>()
        assertNotNull(player)
        assertNotNull(player.channelRepository)
        assertNotNull(player.telegramProgressStore)
        assertNotNull(player.watchHistoryStore)
    }
}
