package com.adulthub.tv.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import androidx.core.content.FileProvider
import com.adulthub.tv.BuildConfig
import com.adulthub.tv.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Serializable
data class UpdateManifest(
    val schemaVersion: Int,
    val applicationId: String,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val apkAssetName: String,
    val sizeBytes: Long,
    val sha256: String,
    val releaseNotes: String? = null,
)

@Serializable
private data class GitHubRelease(val assets: List<GitHubReleaseAsset> = emptyList())

@Serializable
private data class GitHubReleaseAsset(val id: Long, val name: String, val size: Long)

data class AvailableUpdate(val manifest: UpdateManifest, val apkFile: File)

sealed interface UpdateCheckResult {
    data class UpdateReady(val update: AvailableUpdate) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

class AppUpdateRepository(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkAndDownload(onProgress: (Long, Long) -> Unit = { _, _ -> }): UpdateCheckResult {
        if (BuildConfig.UPDATE_GITHUB_OWNER.isBlank()) {
            return UpdateCheckResult.Failed("GitHub repository owner is not configured")
        }
        return try {
            val releaseUrl = "$GITHUB_API/repos/${BuildConfig.UPDATE_GITHUB_OWNER}/${BuildConfig.UPDATE_GITHUB_REPO}/releases/latest"
            val release = json.decodeFromString<GitHubRelease>(fetchBytes(releaseUrl, MAX_RELEASE_BYTES).toString(Charsets.UTF_8))
        val manifestAssetName = BuildConfig.UPDATE_MANIFEST_ASSET
        val manifestAsset = release.assets.singleOrNull { it.name == manifestAssetName }
            ?: throw IllegalStateException("Latest GitHub release has no $manifestAssetName asset")
            val manifestBody = fetchBytes(assetUrl(manifestAsset.id), MAX_MANIFEST_BYTES, OCTET_STREAM)
            val manifest = json.decodeFromString<UpdateManifest>(manifestBody.toString(Charsets.UTF_8))
            validateManifest(manifest)
            if (manifest.versionCode <= BuildConfig.VERSION_CODE) return UpdateCheckResult.UpToDate

            val apkName = manifest.apkAssetName
            val apkAsset = release.assets.singleOrNull { it.name == apkName }
                ?: throw IllegalStateException("Latest GitHub release has no APK asset named $apkName")
            require(apkAsset.size == manifest.sizeBytes) { "GitHub APK size does not match the manifest" }
            val apk = downloadApk(assetUrl(apkAsset.id), manifest, onProgress)
            try {
                validateApk(apk, manifest)
                persistManifest(manifest)
            } catch (error: Exception) {
                apk.delete()
                throw error
            }
            UpdateCheckResult.UpdateReady(AvailableUpdate(manifest, apk))
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            UpdateCheckResult.Failed(error.message ?: "Could not check GitHub releases")
        }
    }

    fun installerUri(file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.updates",
        file,
    )

    suspend fun restoreDownloadedUpdate(): AvailableUpdate? {
        val dir = File(context.filesDir, "updates")
        val manifestFile = File(dir, MANIFEST_FILE_NAME)
        if (!manifestFile.isFile) return null
        return try {
            val manifest = json.decodeFromString<UpdateManifest>(manifestFile.readText())
            validateManifest(manifest)
            if (manifest.versionCode <= BuildConfig.VERSION_CODE) {
                dir.listFiles()?.filter { it.name.startsWith("update-") || it.name == MANIFEST_FILE_NAME }?.forEach(File::delete)
                return null
            }
            val apk = File(dir, "update-${manifest.versionCode}.apk")
            require(apk.isFile && apk.length() == manifest.sizeBytes) { "Saved update file is incomplete" }
            require(sha256(apk).equals(manifest.sha256, true)) { "Saved update checksum is invalid" }
            validateApk(apk, manifest)
            AvailableUpdate(manifest, apk)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            manifestFile.delete()
            dir.listFiles()?.filter { it.name.startsWith("update-") }?.forEach(File::delete)
            null
        }
    }

    fun validateManifest(manifest: UpdateManifest) {
        validateUpdateManifest(
            manifest = manifest,
            packageName = context.packageName,
            sdkInt = Build.VERSION.SDK_INT,
        )
    }

    private suspend fun downloadApk(url: String, manifest: UpdateManifest, onProgress: (Long, Long) -> Unit): File {
        val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
        val freeBytes = StatFs(context.filesDir.path).availableBytes
        require(freeBytes >= manifest.sizeBytes * 2 + MIN_FREE_SPACE_BYTES) { "Not enough free storage for the update and installation" }
        val partial = File(updatesDir, "update.apk.part")
        val ready = File(updatesDir, "update-${manifest.versionCode}.apk")
        partial.delete()

        try {
            val response = request(url, OCTET_STREAM)
            response.use { result ->
                require(result.code == 200) { "APK download returned HTTP ${result.code}" }
                val body = requireNotNull(result.body) { "APK response is empty" }
                val declaredLength = body.contentLength()
                require(declaredLength == -1L || declaredLength == manifest.sizeBytes) { "APK size does not match the manifest" }
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                FileOutputStream(partial).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= manifest.sizeBytes && total <= MAX_APK_BYTES) { "APK is larger than expected" }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            onProgress(total, manifest.sizeBytes)
                        }
                    }
                    output.fd.sync()
                }
                require(total == manifest.sizeBytes) { "APK size does not match the manifest" }
                require(digest.digest().joinToString("") { "%02x".format(it) }.equals(manifest.sha256, true)) {
                    "APK checksum does not match the manifest"
                }
            }
            check(partial.renameTo(ready)) { "Could not finalize the downloaded APK" }
            return ready
        } catch (error: Exception) {
            partial.delete()
            ready.delete()
            throw error
        }
    }

    private fun validateApk(file: File, manifest: UpdateManifest) {
        val info = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IllegalArgumentException("Downloaded file is not a valid APK")
        require(info.packageName == context.packageName) { "Downloaded APK belongs to a different app" }
        require(info.longVersionCode == manifest.versionCode) { "APK version does not match the manifest" }
        require(info.applicationInfo?.minSdkVersion == manifest.minSdk) { "APK Android requirement does not match the manifest" }

        val installed = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val installedSigners = installed.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray().toList() }.toSet()
        val updateSigners = info.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray().toList() }.toSet()
        require(sameSigningCertificates(installedSigners, updateSigners)) {
            "APK signing certificate does not match the installed app"
        }
    }

    private fun persistManifest(manifest: UpdateManifest) {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val temp = File(dir, "$MANIFEST_FILE_NAME.tmp")
        temp.writeText(json.encodeToString(manifest))
        check(temp.renameTo(File(dir, MANIFEST_FILE_NAME))) { "Could not save update information" }
    }

    private fun sha256(file: File): String = computeSha256(file)

    private suspend fun fetchBytes(url: String, maximum: Long, accept: String = GITHUB_JSON): ByteArray {
        request(url, accept).use { response ->
            require(response.code == 200) { githubError(response.code) }
            val body = requireNotNull(response.body) { "Update manifest is empty" }
            require(body.contentLength() in -1L..maximum) { "Update manifest is too large" }
            val output = java.io.ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size().toLong() + count <= maximum) { "Update manifest is too large" }
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        }
    }

    private suspend fun request(initialUrl: String, accept: String): okhttp3.Response {
        var current = initialUrl
        for (redirectCount in 0..MAX_REDIRECTS) {
            val uri = URI(current)
            require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()) { "GitHub update requests require HTTPS" }
            val serviceUrl = settingsDataStore.requireAllowedServiceUrl(current, allowQuery = true)
            val builder = Request.Builder().url(serviceUrl.normalized)
                .header("Cache-Control", "no-cache")
                .header("Accept", accept)
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .header("User-Agent", "AdultHubTV/${BuildConfig.VERSION_NAME}")
            val response = runInterruptible(Dispatchers.IO) { client.newCall(builder.build()).execute() }
            if (response.code !in REDIRECT_CODES) return response
            val location = response.header("Location")
            response.close()
            require(redirectCount < MAX_REDIRECTS && !location.isNullOrBlank()) { "Too many or invalid GitHub redirects" }
            current = resolveRedirectTarget(current, location)
        }
        error("Too many GitHub redirects")
    }

    private fun assetUrl(assetId: Long) = "$GITHUB_API/repos/${BuildConfig.UPDATE_GITHUB_OWNER}/${BuildConfig.UPDATE_GITHUB_REPO}/releases/assets/$assetId"

    private fun githubError(code: Int): String = when (code) {
        404 -> "GitHub release was not found"
        else -> "GitHub update request returned HTTP $code"
    }

    companion object {
        private const val GITHUB_API = "https://api.github.com"
        private const val GITHUB_API_VERSION = "2026-03-10"
        private const val GITHUB_JSON = "application/vnd.github+json"
        private const val OCTET_STREAM = "application/octet-stream"
        private const val MANIFEST_FILE_NAME = "update-manifest.json"
        private const val MAX_RELEASE_BYTES = 1024 * 1024L
        private const val MAX_MANIFEST_BYTES = 64 * 1024L
        private const val MIN_FREE_SPACE_BYTES = 32L * 1024 * 1024
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

/**
 * Validates an update manifest without touching the Android framework, so that the rules can
 * be covered by plain unit tests.
 */
internal fun validateUpdateManifest(manifest: UpdateManifest, packageName: String, sdkInt: Int) {
    require(manifest.schemaVersion == SUPPORTED_SCHEMA) { "Unsupported update manifest version" }
    require(manifest.applicationId == packageName) { "This update is for a different app" }
    require(manifest.versionCode > 0 && manifest.versionName.isNotBlank()) { "Invalid update version" }
    require(manifest.minSdk in 1..sdkInt) { "This update needs a newer Android version" }
    require(manifest.sizeBytes in 1L..MAX_APK_BYTES) { "APK size is outside the allowed limit" }
    require(manifest.sha256.matches(Regex("[A-Fa-f0-9]{64}"))) { "Invalid APK checksum" }
    require(manifest.apkAssetName.matches(Regex("[A-Za-z0-9._+-]+\\.apk"))) {
        "Invalid APK asset name in update manifest"
    }
}

/** Computes the SHA-256 checksum of a file as a lower case hex string. */
internal fun computeSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Compares the signing certificates of the installed app and of a downloaded update.
 * The sets have to be equal, so an update signed by a different identity is rejected.
 */
internal fun sameSigningCertificates(
    installed: Set<List<Byte>>,
    update: Set<List<Byte>>,
): Boolean = installed.isNotEmpty() && installed == update

/** Resolves a redirect target and rejects anything that is not served over HTTPS. */
internal fun resolveRedirectTarget(currentUrl: String, location: String): String {
    val next = URI(currentUrl).resolve(location).toString()
    require(URI(next).scheme.equals("https", true)) { "GitHub attempted an insecure redirect" }
    return next
}

private const val SUPPORTED_SCHEMA = 2
private const val MAX_APK_BYTES = 300L * 1024 * 1024
