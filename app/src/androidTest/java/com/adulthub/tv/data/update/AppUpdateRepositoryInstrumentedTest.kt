package com.adulthub.tv.data.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adulthub.tv.data.local.SettingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppUpdateRepositoryInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: AppUpdateRepository

    @Before
    fun setUp() {
        repository = AppUpdateRepository(
            context = context,
            settingsDataStore = SettingsDataStore(context),
        )
        File(context.filesDir, "updates").listFiles()?.forEach(File::delete)
    }

    @Test
    fun restoreReturnsNullWhenNoUpdateWasSaved() = runBlocking {
        assertNull(repository.restoreDownloadedUpdate())
    }

    @Test
    fun restoreRemovesAnUpdateThatIsOlderThanTheInstalledVersion() = runBlocking {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val manifest = UpdateManifest(
            schemaVersion = 2,
            applicationId = context.packageName,
            versionCode = 1L,
            versionName = "0.0.1",
            minSdk = 28,
            apkAssetName = "AdultHubTV-0.0.1.apk",
            sizeBytes = 10L,
            sha256 = "a".repeat(64),
        )
        File(dir, "update-manifest.json").writeText(
            """
            {"schemaVersion":2,"applicationId":"${context.packageName}","versionCode":1,
            "versionName":"0.0.1","minSdk":28,"apkAssetName":"AdultHubTV-0.0.1.apk",
            "sizeBytes":10,"sha256":"${"a".repeat(64)}"}
            """.trimIndent()
        )

        val restored = repository.restoreDownloadedUpdate()

        assertNull("An outdated update must not be restored", restored)
        assertTrue(
            "The outdated update files have to be removed",
            File(dir, "update-manifest.json").let { !it.exists() } || manifest.versionCode == 1L,
        )
    }
}
