package com.adulthub.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDataStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val context = mockk<Context>(relaxed = true)
    private val crypto = mockk<KeyStoreCrypto>()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settingsDataStore: SettingsDataStore

    /** Namespace of the account the store is bound to in this test. */
    private val accountKey = AccountScope.ANONYMOUS_KEY

    @Before
    fun setUp() {
        coEvery { crypto.encrypt(any()) } answers { "enc_" + firstArg<String>() }
        coEvery { crypto.decrypt(any()) } answers {
            val arg = firstArg<String>()
            if (arg.startsWith("enc_")) arg.removePrefix("enc_") else arg
        }

        val testFile = tmpFolder.newFile("test_settings.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile },
        )
        settingsDataStore = SettingsDataStore(
            context = context,
            crypto = crypto,
            dataStore = dataStore,
        )
    }

    @Test
    fun `default values when store is uninitialized`() = runTest(timeout = 120.seconds) {
        assertEquals(SettingsDataStore.CURRENT_SCHEMA_VERSION, settingsDataStore.schemaVersion.first())
        assertNull(settingsDataStore.getSelectedLibraryId())
        assertNull(settingsDataStore.getSelectedLibraryName())
        assertEquals(SettingsDataStore.DEFAULT_RESOLVER_URL, settingsDataStore.getResolverBaseUrl())
        assertEquals(SettingsDataStore.DEFAULT_CACHE_LIMIT_MB, settingsDataStore.cacheLimitMb.first())
        assertFalse(settingsDataStore.phashOnPlayback.first())
        assertTrue(settingsDataStore.approvedCleartextOrigins.first().isEmpty())
    }

    @Test
    fun `setSelectedLibrary saves encrypted id and name in the account namespace`() = runTest(timeout = 120.seconds) {
        settingsDataStore.setSelectedLibrary("lib-uuid-42", "Adult Videos")

        assertEquals("lib-uuid-42", settingsDataStore.getSelectedLibraryId())
        assertEquals("Adult Videos", settingsDataStore.getSelectedLibraryName())

        // Verify it was persisted in encrypted form under the account namespace
        val prefs = dataStore.data.first()
        val rawId = prefs[stringPreferencesKey("selected_library_id__$accountKey")]
        val rawName = prefs[stringPreferencesKey("selected_library_name__$accountKey")]
        assertEquals("enc:v1:enc_lib-uuid-42", rawId)
        assertEquals("enc:v1:enc_Adult Videos", rawName)
        assertNull(prefs[stringPreferencesKey("selected_library_id")])
    }

    @Test
    fun `setResolverBaseUrl sanitizes trailing slashes and blank strings`() = runTest(timeout = 120.seconds) {
        settingsDataStore.setResolverBaseUrl("https://resolver.example.com/api/")
        assertEquals("https://resolver.example.com/api", settingsDataStore.getResolverBaseUrl())

        // Blank clears the preference
        settingsDataStore.setResolverBaseUrl("   ")
        assertEquals(SettingsDataStore.DEFAULT_RESOLVER_URL, settingsDataStore.getResolverBaseUrl())
    }

    @Test
    fun `the resolver address source tells a configured address from an unset one`() = runTest(timeout = 120.seconds) {
        assertEquals(
            ResolverUrlSource.DEFAULT,
            settingsDataStore.resolverBaseUrlSource.first(),
        )

        settingsDataStore.setResolverBaseUrl("https://resolver.example.com")

        assertEquals(
            ResolverUrlSource.CONFIGURED,
            settingsDataStore.resolverBaseUrlSource.first(),
        )
        assertEquals("https://resolver.example.com", settingsDataStore.getResolverBaseUrl())
    }

    @Test
    fun `cache limit and phash settings update flows`() = runTest(timeout = 120.seconds) {
        settingsDataStore.setCacheLimitMb(512)
        assertEquals(512, settingsDataStore.cacheLimitMb.first())

        settingsDataStore.setPhashOnPlayback(true)
        assertTrue(settingsDataStore.phashOnPlayback.first())

        settingsDataStore.setPhashOnPlayback(false)
        assertFalse(settingsDataStore.phashOnPlayback.first())
    }

    @Test
    fun `approve and revoke cleartext HTTP origin`() = runTest(timeout = 120.seconds) {
        val httpUrl = "http://192.168.1.200:8096"
        assertFalse(settingsDataStore.isCleartextOriginApproved(httpUrl))

        settingsDataStore.approveCleartextOrigin(httpUrl)
        assertTrue(settingsDataStore.isCleartextOriginApproved(httpUrl))
        assertTrue(settingsDataStore.approvedCleartextOrigins.first().contains("http://192.168.1.200:8096"))

        // HTTPS origin is always approved without explicit consent
        assertTrue(settingsDataStore.isCleartextOriginApproved("https://jellyfin.example.com"))

        settingsDataStore.revokeCleartextOrigin("http://192.168.1.200:8096")
        assertFalse(settingsDataStore.isCleartextOriginApproved(httpUrl))
    }

    @Test
    fun `migrateSensitiveValues converts legacy plaintext to encrypted and sets schema version`() = runTest(timeout = 120.seconds) {
        // Manually write plaintext values simulating legacy version of the app
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("selected_library_id")] = "plaintext-lib-id"
            prefs[stringPreferencesKey("selected_library_name")] = "Plain Library"
            prefs[stringPreferencesKey("resolver_base_url")] = "https://resolver.legacy.internal"
            prefs[stringSetPreferencesKey("approved_cleartext_origins")] = setOf("http://192.168.1.50:8000")
        }

        // Execute migration
        settingsDataStore.migrateSensitiveValues()

        // Check raw storage to confirm it is encrypted now and moved into the account namespace
        val prefsAfter = dataStore.data.first()
        assertEquals(
            "enc:v1:enc_plaintext-lib-id",
            prefsAfter[stringPreferencesKey("selected_library_id__$accountKey")],
        )
        assertEquals(
            "enc:v1:enc_Plain Library",
            prefsAfter[stringPreferencesKey("selected_library_name__$accountKey")],
        )
        assertNull(prefsAfter[stringPreferencesKey("selected_library_id")])
        assertNull(prefsAfter[stringPreferencesKey("selected_library_name")])
        assertEquals("enc:v1:enc_https://resolver.legacy.internal", prefsAfter[stringPreferencesKey("resolver_base_url")])
        assertEquals(SettingsDataStore.CURRENT_SCHEMA_VERSION, settingsDataStore.schemaVersion.first())

        // And getters still return decrypted values
        assertEquals("plaintext-lib-id", settingsDataStore.getSelectedLibraryId())
        assertEquals("Plain Library", settingsDataStore.getSelectedLibraryName())
        assertEquals("https://resolver.legacy.internal", settingsDataStore.getResolverBaseUrl())
    }

    @Test
    fun `migration leaves encrypted origins untouched on later starts`() = runTest(timeout = 120.seconds) {
        // Real encryption uses a random IV, so equal plaintext encrypts to a different
        // value every time. The mock reproduces that to catch needless rewrites.
        var counter = 0
        coEvery { crypto.encrypt(any()) } answers { "enc_${counter++}_" + firstArg<String>() }
        coEvery { crypto.decrypt(any()) } answers {
            val arg = firstArg<String>()
            if (arg.startsWith("enc_")) arg.removePrefix("enc_").substringAfter('_') else arg
        }

        dataStore.edit { prefs ->
            prefs[stringSetPreferencesKey("approved_cleartext_origins")] =
                setOf("http://192.168.1.50:8000", "https://example.org")
        }

        settingsDataStore.migrateSensitiveValues()
        val afterFirstRun = dataStore.data.first()[stringSetPreferencesKey("approved_cleartext_origins")].orEmpty()
        assertEquals(2, afterFirstRun.size)
        assertTrue(afterFirstRun.all { it.startsWith("enc:v1:") })

        settingsDataStore.migrateSensitiveValues()
        val afterSecondRun = dataStore.data.first()[stringSetPreferencesKey("approved_cleartext_origins")].orEmpty()

        assertEquals(afterFirstRun, afterSecondRun)
        assertEquals(
            setOf("http://192.168.1.50:8000", "https://example.org"),
            settingsDataStore.approvedCleartextOrigins.first(),
        )
    }

    @Test
    fun `export and import non-secret configuration roundtrip`() = runTest(timeout = 120.seconds) {
        settingsDataStore.setSelectedLibrary("exported-lib", "Exported Name")
        settingsDataStore.setResolverBaseUrl("https://resolver.export.test")
        settingsDataStore.setCacheLimitMb(128)
        settingsDataStore.setPhashOnPlayback(true)

        settingsDataStore.setBufferProfile(BufferProfile.LARGE)

        val exportedJson = settingsDataStore.exportNonSecretConfigJson()
        assertTrue(exportedJson.contains("exported-lib"))
        assertTrue(exportedJson.contains("https://resolver.export.test"))
        assertTrue(exportedJson.contains("128"))
        assertTrue(exportedJson.contains("large"))

        // Create a new fresh SettingsDataStore instance
        val newFile = tmpFolder.newFile("new_settings.preferences_pb")
        val newDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { newFile },
        )
        val newSettingsDataStore = SettingsDataStore(
            context = context,
            crypto = crypto,
            dataStore = newDataStore,
        )

        // Import the config
        newSettingsDataStore.importNonSecretConfigJson(exportedJson)

        assertEquals("exported-lib", newSettingsDataStore.getSelectedLibraryId())
        assertEquals("Exported Name", newSettingsDataStore.getSelectedLibraryName())
        assertEquals("https://resolver.export.test", newSettingsDataStore.getResolverBaseUrl())
        assertEquals(128, newSettingsDataStore.cacheLimitMb.first())
        assertTrue(newSettingsDataStore.phashOnPlayback.first())
        assertEquals(BufferProfile.LARGE, newSettingsDataStore.bufferProfile.first())
    }

    @Test
    fun `buffer profile flow updates correctly`() = runTest(timeout = 120.seconds) {
        assertEquals(BufferProfile.STANDARD, settingsDataStore.bufferProfile.first())
        settingsDataStore.setBufferProfile(BufferProfile.FAST_START)
        assertEquals(BufferProfile.FAST_START, settingsDataStore.bufferProfile.first())
        settingsDataStore.setBufferProfile(BufferProfile.LARGE)
        assertEquals(BufferProfile.LARGE, settingsDataStore.bufferProfile.first())
    }

    @Test
    fun `low ram mode flow updates and roundtrips through export import`() = runTest(timeout = 120.seconds) {
        assertFalse(settingsDataStore.lowRamMode.first())
        settingsDataStore.setLowRamMode(true)
        assertTrue(settingsDataStore.lowRamMode.first())

        val exported = settingsDataStore.exportNonSecretConfig()
        assertTrue(exported.lowRamMode)

        val newFile = tmpFolder.newFile("low_ram_test.preferences_pb")
        val newDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { newFile },
        )
        val newSettings = SettingsDataStore(context, crypto, newDataStore)
        assertFalse(newSettings.lowRamMode.first())
        newSettings.importNonSecretConfig(exported)
        assertTrue(newSettings.lowRamMode.first())
    }

    @Test
    fun `seek step and autoplay next update flows and export import roundtrip`() = runTest(timeout = 120.seconds) {
        assertEquals(15, settingsDataStore.seekStepSec.first())
        assertFalse(settingsDataStore.autoplayNext.first())

        settingsDataStore.setSeekStepSec(30)
        settingsDataStore.setAutoplayNext(true)

        assertEquals(30, settingsDataStore.seekStepSec.first())
        assertTrue(settingsDataStore.autoplayNext.first())

        val exported = settingsDataStore.exportNonSecretConfig()
        assertEquals(30, exported.seekStepSec)
        assertTrue(exported.autoplayNext)

        val newFile = tmpFolder.newFile("playback_ctrl_test.preferences_pb")
        val newDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { newFile },
        )
        val newSettings = SettingsDataStore(context, crypto, newDataStore)
        newSettings.importNonSecretConfig(exported)
        assertEquals(30, newSettings.seekStepSec.first())
        assertTrue(newSettings.autoplayNext.first())
    }
}
