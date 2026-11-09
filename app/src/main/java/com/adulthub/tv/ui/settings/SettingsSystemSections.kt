package com.adulthub.tv.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.BuildConfig
import com.adulthub.tv.R
import com.adulthub.tv.data.update.AppUpdateManager
import com.adulthub.tv.data.update.AppUpdateRepository
import com.adulthub.tv.data.update.AppUpdateState
import com.adulthub.tv.data.update.SigningIdentity
import com.adulthub.tv.diagnostics.DeviceDiagnostics
import com.adulthub.tv.ui.components.TvChoiceChip
import com.adulthub.tv.ui.components.TvSecondaryButton
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.SurfaceDark
import com.adulthub.tv.ui.theme.TextPrimary
import com.adulthub.tv.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** App version, update check, automatic updates and the install action. */
@Composable
internal fun SettingsUpdatesCard(
    updateState: AppUpdateState,
    updateManager: AppUpdateManager,
    updateRepository: AppUpdateRepository
) {
    val context = LocalContext.current
    val updateScope = rememberCoroutineScope()
    SettingsCard(title = stringResource(R.string.settings_updates_title)) {
        Column {
            Text(
                text = stringResource(
                    R.string.settings_current_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                ),
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = updateState.message.ifBlank { stringResource(R.string.settings_updates_desc) },
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
            if (SigningIdentity.requiresFreshInstall(context)) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = SigningIdentity.FRESH_INSTALL_REQUIRED_MESSAGE,
                    style = MaterialTheme.typography.bodyMedium.copy(color = NeonPink)
                )
            }
            if (updateState.isDownloading) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.settings_updates_downloaded,
                        (updateState.downloadProgress * 100).toInt()
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(color = NeonPink)
                )
            }
            updateState.update?.let { update ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.settings_updates_available,
                        update.manifest.versionName,
                        update.manifest.versionCode
                    ) + update.manifest.releaseNotes
                        ?.let { stringResource(R.string.settings_updates_release_notes, it) }
                        .orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TvSecondaryButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                            )
                        } else {
                            val apkUri = updateRepository.installerUri(update.apkFile)
                            val installIntent = Intent(Intent.ACTION_VIEW)
                                .setDataAndType(apkUri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(installIntent)
                        }
                    },
                    modifier = Modifier.height(40.dp)
                ) { Text(stringResource(R.string.settings_install_update), fontWeight = FontWeight.Medium) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvChoiceChip(
                    label = stringResource(R.string.settings_auto_update_on),
                    isSelected = updateState.enabled,
                    onClick = { updateScope.launch { updateManager.setEnabled(true) } },
                    height = 38.dp,
                    fontSize = 13.sp
                )
                TvChoiceChip(
                    label = stringResource(R.string.settings_auto_update_off),
                    isSelected = !updateState.enabled,
                    onClick = { updateScope.launch { updateManager.setEnabled(false) } },
                    height = 38.dp,
                    fontSize = 13.sp
                )
                TvSecondaryButton(
                    onClick = updateManager::checkNow,
                    enabled = !updateState.isChecking && !updateState.isDownloading,
                    modifier = Modifier.height(38.dp)
                ) { Text(stringResource(R.string.settings_check_now), fontWeight = FontWeight.Medium) }
            }
        }
    }
}

/** Export of the diagnostics report. The screen opens the file picker. */
@Composable
internal fun SettingsDiagnosticsCard(onExportDiagnostics: () -> Unit) {
    val diagnosticsTitle = stringResource(R.string.settings_diagnostics_title)
    val diagnosticsDescription = stringResource(R.string.settings_diagnostics_description)
    val exportDiagnosticsLabel = stringResource(R.string.settings_export_diagnostics)
    SettingsCard(title = diagnosticsTitle) {
        Text(
            text = diagnosticsDescription,
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )
        Spacer(modifier = Modifier.height(12.dp))
        TvSecondaryButton(
            onClick = onExportDiagnostics,
            modifier = Modifier.height(40.dp)
        ) {
            Text(
                exportDiagnosticsLabel,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** Model, Android version, memory and the codec support reported by the device. */
@Composable
internal fun SettingsDeviceInfoCard(diagnostics: DeviceDiagnostics) {
    SettingsCard(title = stringResource(R.string.settings_device_title)) {
        Column {
            SettingsInfoRow(
                label = stringResource(R.string.settings_device_model_label),
                value = diagnostics.deviceModel
            )
            Spacer(modifier = Modifier.height(6.dp))
            SettingsInfoRow(
                label = stringResource(R.string.settings_device_android_label),
                value = diagnostics.androidVersion
            )
            Spacer(modifier = Modifier.height(6.dp))
            SettingsInfoRow(
                label = stringResource(R.string.settings_device_ram_label),
                value = stringResource(
                    R.string.settings_diagnostics_ram,
                    diagnostics.totalRamMb,
                    diagnostics.availableRamMb
                )
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.settings_codecs_header),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                diagnostics.codecs.forEach { codec ->
                    val statusText = stringResource(
                        when {
                            !codec.isSupported -> R.string.settings_codec_unsupported
                            codec.isHardware -> R.string.settings_codec_hardware
                            else -> R.string.settings_codec_software
                        }
                    )
                    val badgeBorder = if (codec.isSupported) NeonPink else Color.Gray.copy(alpha = 0.4f)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark)
                            .border(1.dp, badgeBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_codec_badge, codec.name, statusText),
                            fontSize = 12.sp,
                            color = if (codec.isSupported) Color.White else Color.Gray,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

/** App name, version and the open source licenses dialog. */
@Composable
internal fun SettingsAboutCard(onShowLicenses: () -> Unit) {
    SettingsCard(title = stringResource(R.string.settings_about_title)) {
        Column {
            Text(
                text = stringResource(
                    R.string.settings_about_summary,
                    BuildConfig.CLIENT_NAME,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE
                ),
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
            Spacer(modifier = Modifier.height(14.dp))
            TvSecondaryButton(
                onClick = onShowLicenses,
                modifier = Modifier.height(40.dp)
            ) {
                Text(stringResource(R.string.settings_licenses_button), fontWeight = FontWeight.Medium)
            }
        }
    }
}
