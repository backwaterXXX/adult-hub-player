package com.adulthub.tv.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.BuildConfig
import com.adulthub.tv.data.local.BufferProfile
import com.adulthub.tv.data.local.ResolverUrlSource
import com.adulthub.tv.data.update.AppUpdateManager
import com.adulthub.tv.data.update.AppUpdateRepository
import com.adulthub.tv.data.update.SigningIdentity
import com.adulthub.tv.diagnostics.DeviceDiagnosticsHelper
import com.adulthub.tv.player.STASH_COMPATIBLE_PHASH_FRAME_COUNT
import com.adulthub.tv.R
import com.adulthub.tv.ui.components.ConfirmationDialog
import com.adulthub.tv.ui.components.CleartextHttpConsentDialog
import com.adulthub.tv.ui.components.LicensesDialog
import com.adulthub.tv.ui.components.TvPageHeader
import com.adulthub.tv.ui.components.TvChoiceChip
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.components.TvTextField
import com.adulthub.tv.ui.components.LocalTouchUi
import com.adulthub.tv.ui.theme.CardSurface
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.OledBlack
import com.adulthub.tv.ui.theme.SurfaceDark
import com.adulthub.tv.ui.theme.TextPrimary
import com.adulthub.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private enum class ClearDataType(val titleRes: Int) {
    WATCH_HISTORY(R.string.settings_clear_history),
    SEARCH_HISTORY(R.string.settings_clear_search),
    RESUME_PROGRESS(R.string.settings_clear_resume),
}

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    isMobile: Boolean = false,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val diagnosticsTitle = stringResource(com.adulthub.tv.R.string.settings_diagnostics_title)
    val diagnosticsDescription = stringResource(com.adulthub.tv.R.string.settings_diagnostics_description)
    val exportDiagnosticsLabel = stringResource(com.adulthub.tv.R.string.settings_export_diagnostics)
    val updateManager: AppUpdateManager = koinInject()
    val updateRepository: AppUpdateRepository = koinInject()
    val updateState by updateManager.state.collectAsStateWithLifecycle()
    val updateScope = rememberCoroutineScope()
    var showLicensesDialog by rememberSaveable { mutableStateOf(false) }
    var pendingClearType by rememberSaveable { mutableStateOf<ClearDataType?>(null) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val diagnostics by produceState(
        initialValue = com.adulthub.tv.diagnostics.DeviceDiagnostics("Loading…", "", 0L, 0L, emptyList()),
        key1 = context,
    ) {
        value = withContext(Dispatchers.IO) { DeviceDiagnosticsHelper.getDiagnostics(context) }
    }
    val diagnosticsExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) viewModel.exportDiagnostics(context, uri)
    }
    val contentHorizontalPadding = if (isMobile) 16.dp else 48.dp

    // Handle logout navigation
    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) {
            onSignOut()
        }
    }

    LaunchedEffect(Unit) { updateManager.checkAutomaticallyIfDue() }

    // Auto-dismiss status message after 4s
    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null) {
            delay(4000L)
            viewModel.clearStatusMessage()
        }
    }

    CompositionLocalProvider(LocalTouchUi provides isMobile) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack),
    ) {
        TvPageHeader(
            title = "Settings",
            subtitle = "${BuildConfig.CLIENT_NAME} v${BuildConfig.VERSION_NAME}",
            onBack = onNavigateBack,
            isMobile = isMobile,
        )

        // Notification Banner
        AnimatedVisibility(
            modifier = Modifier.padding(horizontal = contentHorizontalPadding),
            visible = uiState.statusMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            uiState.statusMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                        .border(1.dp, NeonPink, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "✓ $msg",
                        color = NeonPink,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.padding(horizontal = contentHorizontalPadding).height(if (isMobile) 8.dp else 20.dp))

        // Settings Content List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(if (isMobile) 8.dp else 20.dp),
            contentPadding = PaddingValues(bottom = if (isMobile) 20.dp else 40.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentHorizontalPadding),
        ) {
            // 1. Session & Server Section
            item(key = "section_session") {
                SettingsCard(title = "Account & Server") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            SettingsInfoRow(label = "Server URL", value = uiState.serverUrl.ifEmpty { "Not connected" })
                            Spacer(modifier = Modifier.height(6.dp))
                            SettingsInfoRow(label = "Server Name", value = uiState.serverName.ifEmpty { "Jellyfin" })
                            Spacer(modifier = Modifier.height(6.dp))
                            SettingsInfoRow(label = "Username", value = uiState.username.ifEmpty { "Guest" })
                            Spacer(modifier = Modifier.height(6.dp))
                            SettingsInfoRow(label = "Playback", value = "Jellyfin + Adult Hub streams")
                        }

                        TvSecondaryButton(
                            onClick = { viewModel.logout(context) },
                            modifier = Modifier.height(44.dp),
                        ) {
                            Text("Sign out", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // 2. Active Library Selection
            item(key = "section_library") {
                SettingsCard(title = "Active Library") {
                    Column {
                        Text(
                            text = "Currently selected: ${uiState.selectedLibraryName}",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (uiState.availableLibraries.isNotEmpty()) {
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp, vertical = 4.dp),
                            ) {
                                uiState.availableLibraries.forEach { library ->
                                    val isSelected = library.id == uiState.selectedLibraryId
                                    TvChoiceChip(
                                        label = library.name,
                                        isSelected = isSelected,
                                        onClick = { viewModel.selectLibrary(library) },
                                        height = 40.dp,
                                        fontSize = 14.sp,
                                        cornerRadius = 12.dp,
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No additional libraries found",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                            )
                        }
                    }
                }
            }

            item(key = "section_home_screen") {
                SettingsCard(title = "Home screen") {
                    Column {
                        Text(
                            text = "Choose which categories appear on the home screen. Selected categories are shown as separate rows.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (uiState.availableHomeCategories.isEmpty()) {
                            Text(
                                text = "No categories found in the active library.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                            )
                        } else {
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp, vertical = 4.dp),
                            ) {
                                uiState.availableHomeCategories.forEach { category ->
                                    TvChoiceChip(
                                        label = category.name,
                                        isSelected = category.id in uiState.selectedHomeCategoryIds,
                                        onClick = { viewModel.toggleHomeCategory(category.id) },
                                        height = 38.dp,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Cache & Storage Section
            item(key = "section_cache") {
                SettingsCard(title = "Cache & Storage") {
                    Column {
                        Text(
                            text = "Configure disk cache for thumbnails and poster artwork. Changes apply after restarting the app.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Cache size choices
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                val options = listOf(
                                    0 to "Off",
                                    128 to "128 MB",
                                    256 to "256 MB",
                                    512 to "512 MB",
                                    1024 to "1024 MB",
                                )
                                options.forEach { (limit, label) ->
                                    val isSelected = uiState.cacheLimitMb == limit
                                    TvChoiceChip(
                                        label = label,
                                        isSelected = isSelected,
                                        onClick = { viewModel.setCacheLimit(limit) },
                                        height = 38.dp,
                                        fontSize = 13.sp,
                                    )
                                }
                            }

                            // Clear Cache Button
                            TvSecondaryButton(
                                onClick = { viewModel.clearCache(context) },
                                modifier = Modifier.height(40.dp),
                            ) {
                                Text("Clear cache", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // 3b. Network Buffer Profile
            item(key = "section_buffer_profile") {
                SettingsCard(title = "Network buffer profile") {
                    Column {
                        Text(
                            text = "Configure ExoPlayer buffer thresholds for playback stability vs initial start latency. Standard is recommended for most networks.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (isMobile) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BufferProfile.entries.forEach { profile ->
                                val selected = uiState.bufferProfile == profile
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) NeonPink.copy(alpha = 0.14f) else SurfaceDark)
                                        .border(1.dp, if (selected) NeonPink else Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                        .clickable { viewModel.setBufferProfile(profile) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(profile.label.substringBefore(" ("), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                        Text("${profile.minBufferMs / 1000}s minimum · ${profile.maxBufferMs / 1000}s maximum", color = TextSecondary, fontSize = 12.sp)
                                    }
                                    Text(if (selected) "✓" else "", color = NeonPink, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            BufferProfile.entries.forEach { profile ->
                                val isSelected = uiState.bufferProfile == profile
                                TvChoiceChip(
                                    label = profile.label,
                                    isSelected = isSelected,
                                    onClick = { viewModel.setBufferProfile(profile) },
                                    height = 38.dp,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }

            // 3c. Low-Memory Mode (Performance)
            item(key = "section_low_ram") {
                val isLowMemoryDevice = diagnostics.totalRamMb in 1..2048
                SettingsCard(title = "Performance & Memory") {
                    Column {
                        Text(
                            text = "Low-memory mode downsizes thumbnail decoding and disables card scale animations to improve scrolling responsiveness and prevent out-of-memory errors on lower-end devices.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                        )

                        if (isLowMemoryDevice) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "💡 Recommended for this device (${diagnostics.totalRamMb} MB RAM detected, e.g. Chromecast Sabrina).",
                                style = MaterialTheme.typography.bodySmall.copy(color = NeonPink),
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            TvChoiceChip(
                                label = "Off (Default)",
                                isSelected = !uiState.lowRamMode,
                                onClick = { viewModel.setLowRamMode(false) },
                                height = 38.dp,
                                fontSize = 13.sp,
                            )
                            TvChoiceChip(
                                label = "On (Low-memory)",
                                isSelected = uiState.lowRamMode,
                                onClick = { viewModel.setLowRamMode(true) },
                                height = 38.dp,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            // 3d. Playback Controls & Navigation
            item(key = "section_playback_controls") {
                SettingsCard(title = "Playback controls") {
                    Column {
                        Text(
                            text = "Seek step size for D-pad navigation and remote skip buttons.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            listOf(
                                10 to "10 sec",
                                15 to "15 sec (Default)",
                                30 to "30 sec",
                                60 to "60 sec",
                            ).forEach { (step, label) ->
                                TvChoiceChip(
                                    label = label,
                                    isSelected = uiState.seekStepSec == step,
                                    onClick = { viewModel.setSeekStepSec(step) },
                                    height = 38.dp,
                                    fontSize = 13.sp,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Automatically play the next video with a 10-second countdown when a video ends.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            TvChoiceChip(
                                label = "Off (Default)",
                                isSelected = !uiState.autoplayNext,
                                onClick = { viewModel.setAutoplayNext(false) },
                                height = 38.dp,
                                fontSize = 13.sp,
                            )
                            TvChoiceChip(
                                label = "On (Auto-next)",
                                isSelected = uiState.autoplayNext,
                                onClick = { viewModel.setAutoplayNext(true) },
                                height = 38.dp,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            // 4. Resolver API Origin
            item(key = "section_resolver") {
                SettingsCard(title = "Resolver API") {
                    Column {
                        if (isMobile) {
                            Text(
                                text = "By default, this origin follows the Jellyfin server address. Enter a different URL only if your Resolver service is hosted separately.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        SettingsInfoRow(label = "Current origin", value = uiState.resolverBaseUrl.ifBlank { "Not configured" })
                        Spacer(modifier = Modifier.height(6.dp))
                        SettingsInfoRow(label = "Origin source", value = resolverUrlSourceLabel(uiState.resolverBaseUrlSource))
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsInfoRow(label = "Jellyfin", value = uiState.jellyfinConnectionStatus)
                        Spacer(modifier = Modifier.height(6.dp))
                        SettingsInfoRow(label = "Resolver", value = uiState.resolverConnectionStatus)
                        Spacer(modifier = Modifier.height(12.dp))
                        TvSecondaryButton(
                            onClick = viewModel::checkConnections,
                            enabled = !uiState.isCheckingConnections,
                            modifier = Modifier.height(40.dp),
                        ) {
                            Text(
                                if (uiState.isCheckingConnections) "Checking connections…" else "Check connections",
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        TvTextField(
                            value = uiState.resolverUrlDraft,
                            onValueChange = viewModel::onResolverUrlDraftChanged,
                            label = "Resolver base URL",
                            hint = "https://resolver.example.com or http://192.168.1.10:8099",
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { viewModel.setResolverUrl(uiState.resolverUrlDraft) },
                            ),
                            modifier = Modifier.fillMaxWidth(0.7f),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvSecondaryButton(
                                onClick = { viewModel.setResolverUrl(uiState.resolverUrlDraft) },
                                modifier = Modifier.height(40.dp),
                            ) {
                                Text("Save Resolver URL", fontWeight = FontWeight.Medium)
                            }
                            if (uiState.resolverBaseUrl.isNotBlank()) {
                                TvSecondaryButton(
                                    onClick = { viewModel.setResolverUrl("") },
                                    modifier = Modifier.height(40.dp),
                                ) {
                                    Text("Use Jellyfin Server URL", fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "section_app_updates") {
                SettingsCard(title = "App updates") {
                    Column {
                        Text(
                            text = "Current version: ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = updateState.message.ifBlank { "Updates are checked from the latest GitHub release." },
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                        )
                        if (SigningIdentity.requiresFreshInstall(context)) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = SigningIdentity.FRESH_INSTALL_REQUIRED_MESSAGE,
                                style = MaterialTheme.typography.bodyMedium.copy(color = NeonPink),
                            )
                        }
                        if (updateState.isDownloading) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${(updateState.downloadProgress * 100).toInt()}% downloaded",
                                style = MaterialTheme.typography.bodyMedium.copy(color = NeonPink),
                            )
                        }
                        updateState.update?.let { update ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Available: ${update.manifest.versionName} (code ${update.manifest.versionCode})${update.manifest.releaseNotes?.let { " — $it" }.orEmpty()}",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TvSecondaryButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                                        context.startActivity(
                                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                                        )
                                    } else {
                                        val apkUri = updateRepository.installerUri(update.apkFile)
                                        val installIntent = Intent(Intent.ACTION_VIEW)
                                            .setDataAndType(apkUri, "application/vnd.android.package-archive")
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        context.startActivity(installIntent)
                                    }
                                },
                                modifier = Modifier.height(40.dp),
                            ) { Text("Install update", fontWeight = FontWeight.Medium) }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvChoiceChip(
                                label = "Auto update On",
                                isSelected = updateState.enabled,
                                onClick = { updateScope.launch { updateManager.setEnabled(true) } },
                                height = 38.dp,
                                fontSize = 13.sp,
                            )
                            TvChoiceChip(
                                label = "Auto update Off",
                                isSelected = !updateState.enabled,
                                onClick = { updateScope.launch { updateManager.setEnabled(false) } },
                                height = 38.dp,
                                fontSize = 13.sp,
                            )
                            TvSecondaryButton(
                                onClick = updateManager::checkNow,
                                enabled = !updateState.isChecking && !updateState.isDownloading,
                                modifier = Modifier.height(38.dp),
                            ) { Text("Check now", fontWeight = FontWeight.Medium) }
                        }
                    }
                }
            }

            item(key = "section_http_consent") {
                SettingsCard(title = "Trusted LAN HTTP") {
                    Text(
                        text = "HTTP consent is saved per host and port. Connections remain unencrypted; revoke hosts you no longer trust.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (uiState.approvedCleartextOrigins.isEmpty()) {
                        Text(
                            text = "No HTTP hosts approved",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        )
                    } else {
                        uiState.approvedCleartextOrigins.sorted().forEach { origin ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(origin, color = TextPrimary)
                                TvSecondaryButton(
                                    onClick = { viewModel.revokeCleartextConsent(origin) },
                                    modifier = Modifier.height(40.dp),
                                ) {
                                    Text("Revoke", fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "section_phash") {
                SettingsCard(title = "Video matching") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "When enabled, the Resolver queues server-side pHash generation after playback starts. The server also creates or repairs the video thumbnail. Work continues if you switch to another video. Off by default.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                            )
                        }
                        TvChoiceChip(
                            label = if (uiState.phashOnPlayback) "On" else "Off",
                            isSelected = uiState.phashOnPlayback,
                            onClick = { viewModel.setPhashOnPlayback(!uiState.phashOnPlayback) },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Frames per video",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        )
                        Text(
                            text = "Server-side · StashDB-compatible",
                            style = MaterialTheme.typography.bodyMedium.copy(color = NeonPink),
                        )
                    }
                }
            }

            item(key = "section_diagnostics") {
                SettingsCard(title = diagnosticsTitle) {
                    Text(
                        text = diagnosticsDescription,
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TvSecondaryButton(
                        onClick = { diagnosticsExportLauncher.launch("adult-hub-diagnostics.txt") },
                        modifier = Modifier.height(40.dp),
                    ) {
                        Text(
                            exportDiagnosticsLabel,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            item(key = "section_privacy_data") {
                SettingsCard(title = stringResource(R.string.settings_privacy_title)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Watch history
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_clear_history),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.settings_clear_history_desc),
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                )
                            }
                            TvSecondaryButton(
                                onClick = { pendingClearType = ClearDataType.WATCH_HISTORY },
                                modifier = Modifier.height(38.dp),
                            ) {
                                Text(stringResource(R.string.action_confirm_clear))
                            }
                        }

                        // Search history
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_clear_search),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.settings_clear_search_desc),
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                )
                            }
                            TvSecondaryButton(
                                onClick = { pendingClearType = ClearDataType.SEARCH_HISTORY },
                                modifier = Modifier.height(38.dp),
                            ) {
                                Text(stringResource(R.string.action_confirm_clear))
                            }
                        }

                        // Resume progress
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_clear_resume),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.settings_clear_resume_desc),
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                )
                            }
                            TvSecondaryButton(
                                onClick = { pendingClearType = ClearDataType.RESUME_PROGRESS },
                                modifier = Modifier.height(38.dp),
                            ) {
                                Text(stringResource(R.string.action_confirm_clear))
                            }
                        }
                    }
                }
            }

            item(key = "section_content_filters") {
                SettingsCard(title = stringResource(R.string.settings_content_filters_title)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Excluded tags
                        Column {
                            Text(
                                text = stringResource(R.string.settings_excluded_tags),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (uiState.excludedTags.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.settings_excluded_tags_empty),
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                )
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                ) {
                                    items(items = uiState.excludedTags.toList(), key = { it }) { tag ->
                                        TvSecondaryButton(
                                            onClick = { viewModel.removeExcludedTag(tag) },
                                            modifier = Modifier.height(34.dp),
                                        ) {
                                            Text(text = "$tag  ✕", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Hidden channels
                        Column {
                            Text(
                                text = stringResource(R.string.settings_hidden_channels),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (uiState.hiddenChannelIds.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.settings_hidden_channels_empty),
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                )
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                ) {
                                    items(items = uiState.hiddenChannelIds.toList(), key = { it }) { channelId ->
                                        TvSecondaryButton(
                                            onClick = { viewModel.unhideChannel(channelId) },
                                            modifier = Modifier.height(34.dp),
                                        ) {
                                            Text(text = "$channelId  ✕", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "section_device_info") {
                SettingsCard(title = "Device & hardware diagnostics") {
                    Column {
                        SettingsInfoRow(label = "Model", value = diagnostics.deviceModel)
                        Spacer(modifier = Modifier.height(6.dp))
                        SettingsInfoRow(label = "Android", value = diagnostics.androidVersion)
                        Spacer(modifier = Modifier.height(6.dp))
                        SettingsInfoRow(
                            label = "System RAM",
                            value = "Total: ${diagnostics.totalRamMb} MB · Available: ${diagnostics.availableRamMb} MB",
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Video Codec Decoders:",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            diagnostics.codecs.forEach { codec ->
                                val statusText = when {
                                    !codec.isSupported -> "Unsupported"
                                    codec.isHardware -> "Hardware"
                                    else -> "Software"
                                }
                                val badgeBorder = if (codec.isSupported) NeonPink else Color.Gray.copy(alpha = 0.4f)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceDark)
                                        .border(1.dp, badgeBorder, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = "${codec.name}: $statusText",
                                        fontSize = 12.sp,
                                        color = if (codec.isSupported) Color.White else Color.Gray,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "section_about_licenses") {
                SettingsCard(title = "About & Licenses") {
                    Column {
                        Text(
                            text = "${BuildConfig.CLIENT_NAME} v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}). Built with Jetpack Compose, Jellyfin Kotlin SDK, and Media3 ExoPlayer.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        TvSecondaryButton(
                            onClick = { showLicensesDialog = true },
                            modifier = Modifier.height(40.dp),
                        ) {
                            Text("Third-party open-source licenses", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    uiState.pendingHttpConsentUrl?.let { url ->
        CleartextHttpConsentDialog(
            url = url,
            onApprove = viewModel::confirmHttpConsent,
            onDismiss = viewModel::dismissHttpConsent,
        )
    }

    if (showLicensesDialog) {
        LicensesDialog(onDismiss = { showLicensesDialog = false })
    }

    pendingClearType?.let { clearType ->
        val title = stringResource(R.string.dialog_confirm_clear_title, stringResource(clearType.titleRes))
        val message = stringResource(R.string.dialog_confirm_clear_message)
        ConfirmationDialog(
            title = title,
            message = message,
            confirmText = stringResource(R.string.action_confirm_clear),
            cancelText = stringResource(R.string.action_cancel),
            onConfirm = {
                when (clearType) {
                    ClearDataType.WATCH_HISTORY -> viewModel.clearWatchHistory()
                    ClearDataType.SEARCH_HISTORY -> viewModel.clearSearchHistory()
                    ClearDataType.RESUME_PROGRESS -> viewModel.clearResumeProgress()
                }
                pendingClearType = null
            },
            onDismiss = { pendingClearType = null },
        )
    }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(if (LocalTouchUi.current) 12.dp else 24.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            ),
        )
        Spacer(modifier = Modifier.height(if (LocalTouchUi.current) 8.dp else 14.dp))
        content()
    }
}

/**
 * Describes where the effective Resolver address comes from.
 *
 * The address can be configured explicitly, inherited from the Jellyfin server
 * address, or still empty. The source tells the user which service the app
 * actually talks to.
 */
private fun resolverUrlSourceLabel(source: ResolverUrlSource): String = when (source) {
    ResolverUrlSource.CONFIGURED -> "Configured in Adult Hub"
    ResolverUrlSource.SERVER_FALLBACK -> "Jellyfin server address"
    ResolverUrlSource.DEFAULT -> "Not configured"
}

@Composable
private fun SettingsInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}
