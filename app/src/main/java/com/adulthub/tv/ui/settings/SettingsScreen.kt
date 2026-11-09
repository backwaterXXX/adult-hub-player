package com.adulthub.tv.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.adulthub.tv.BuildConfig
import com.adulthub.tv.R
import com.adulthub.tv.data.update.AppUpdateManager
import com.adulthub.tv.data.update.AppUpdateRepository
import com.adulthub.tv.diagnostics.DeviceDiagnosticsHelper
import com.adulthub.tv.ui.components.CleartextHttpConsentDialog
import com.adulthub.tv.ui.components.ConfirmationDialog
import com.adulthub.tv.ui.components.LicensesDialog
import com.adulthub.tv.ui.components.LocalTouchUi
import com.adulthub.tv.ui.components.TvPageHeader
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.OledBlack
import com.adulthub.tv.ui.theme.SurfaceDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * The settings screen shell: shared state, the header, the status banner, the list of
 * sections and the dialogs. Each section lives in its own file, grouped by topic.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    isMobile: Boolean = false,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val updateManager: AppUpdateManager = koinInject()
    val updateRepository: AppUpdateRepository = koinInject()
    val updateState by updateManager.state.collectAsStateWithLifecycle()
    var showLicensesDialog by rememberSaveable { mutableStateOf(false) }
    var pendingClearType by rememberSaveable { mutableStateOf<ClearDataType?>(null) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val diagnostics by produceState(
        initialValue = com.adulthub.tv.diagnostics.DeviceDiagnostics(stringResource(R.string.settings_loading), "", 0L, 0L, emptyList()),
        key1 = context
    ) {
        value = withContext(Dispatchers.IO) { DeviceDiagnosticsHelper.getDiagnostics(context) }
    }
    val diagnosticsExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
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
                .background(OledBlack)
        ) {
            TvPageHeader(
                title = stringResource(R.string.nav_settings),
                subtitle = stringResource(R.string.settings_version_subtitle, BuildConfig.CLIENT_NAME, BuildConfig.VERSION_NAME),
                onBack = onNavigateBack,
                isMobile = isMobile
            )

            // Notification Banner
            AnimatedVisibility(
                modifier = Modifier.padding(horizontal = contentHorizontalPadding),
                visible = uiState.statusMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                uiState.statusMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDark)
                            .border(1.dp, NeonPink, RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_status_message, msg),
                            color = NeonPink,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.padding(horizontal = contentHorizontalPadding).height(if (isMobile) 8.dp else 20.dp))

            // Settings Content List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(if (isMobile) 8.dp else 20.dp),
                contentPadding = PaddingValues(bottom = if (isMobile) 20.dp else 40.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = contentHorizontalPadding)
            ) {
                item(key = "section_session") {
                    SettingsSessionCard(uiState = uiState, viewModel = viewModel)
                }
                item(key = "section_library") {
                    SettingsLibraryCard(uiState = uiState, viewModel = viewModel)
                }
                item(key = "section_home_screen") {
                    SettingsHomeScreenCard(uiState = uiState, viewModel = viewModel)
                }
                item(key = "section_cache") {
                    SettingsCacheCard(uiState = uiState, viewModel = viewModel)
                }
                item(key = "section_buffer_profile") {
                    SettingsBufferProfileCard(uiState = uiState, viewModel = viewModel, isMobile = isMobile)
                }
                item(key = "section_low_ram") {
                    SettingsPerformanceCard(uiState = uiState, viewModel = viewModel, diagnostics = diagnostics)
                }
                item(key = "section_playback_controls") {
                    SettingsPlaybackControlsCard(uiState = uiState, viewModel = viewModel)
                }
                item(key = "section_resolver") {
                    SettingsResolverCard(uiState = uiState, viewModel = viewModel, isMobile = isMobile)
                }
                item(key = "section_app_updates") {
                    SettingsUpdatesCard(
                        updateState = updateState,
                        updateManager = updateManager,
                        updateRepository = updateRepository
                    )
                }
                item(key = "section_history_import") {
                    SettingsHistoryImportCard(uiState = uiState, viewModel = viewModel)
                }
                item(key = "section_http_consent") {
                    SettingsHttpConsentCard(uiState = uiState, viewModel = viewModel)
                }
                item(key = "section_phash") {
                    SettingsPhashCard(uiState = uiState, viewModel = viewModel)
                }
                item(key = "section_diagnostics") {
                    SettingsDiagnosticsCard(
                        onExportDiagnostics = { diagnosticsExportLauncher.launch("adult-hub-diagnostics.txt") }
                    )
                }
                item(key = "section_privacy_data") {
                    SettingsPrivacyCard(onRequestClear = { pendingClearType = it })
                }
                item(key = "section_content_filters") {
                    SettingsContentFiltersCard(uiState = uiState, viewModel = viewModel)
                }
                item(key = "section_device_info") {
                    SettingsDeviceInfoCard(diagnostics = diagnostics)
                }
                item(key = "section_about_licenses") {
                    SettingsAboutCard(onShowLicenses = { showLicensesDialog = true })
                }
            }
        }

        uiState.pendingHttpConsentUrl?.let { url ->
            CleartextHttpConsentDialog(
                url = url,
                onApprove = viewModel::confirmHttpConsent,
                onDismiss = viewModel::dismissHttpConsent
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
                onDismiss = { pendingClearType = null }
            )
        }
    }
}
