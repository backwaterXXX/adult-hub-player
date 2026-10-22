package com.adulthub.tv.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adulthub.tv.R
import com.adulthub.tv.data.network.LocalNetworkAccessPolicy
import com.adulthub.tv.ui.theme.NeonPink
import com.adulthub.tv.ui.theme.SurfaceDark
import com.adulthub.tv.ui.theme.TextSecondary

/**
 * The permission state of the current screen. Every screen that starts a connection reads it
 * through [LocalNetworkPermission] and calls [ensureGranted] instead of connecting directly,
 * so a missing grant opens the rationale dialog instead of a doomed request that ends in a
 * timeout.
 */
@Stable
class LocalNetworkPermissionController(
    isRequired: Boolean = false,
    isGranted: Boolean = true,
) {
    var isRequired by mutableStateOf(isRequired)
        internal set
    var isGranted by mutableStateOf(isGranted)
        internal set
    var isPromptVisible by mutableStateOf(false)
        internal set
    var isPermanentlyDenied by mutableStateOf(false)
        internal set

    private var pendingAction: (() -> Unit)? = null
    internal var launchRequest: (() -> Unit)? = null

    val isBlocking: Boolean
        get() = isRequired && !isGranted

    /** Runs [action] now, or asks for the permission first when Android 17 blocks the LAN. */
    fun ensureGranted(targetUrl: String? = null, action: () -> Unit) {
        if (!LocalNetworkAccessPolicy.requiresPermission(Build.VERSION.SDK_INT, isGranted, targetUrl)) {
            action()
            return
        }
        pendingAction = action
        isPromptVisible = true
    }

    internal fun allow(context: Context) {
        launchRequest?.invoke()
        if (launchRequest == null) openAppSettings(context)
    }

    fun dismiss() {
        isPromptVisible = false
        pendingAction = null
    }

    internal fun onRequestResult(granted: Boolean, activity: Activity?) {
        isGranted = granted
        isPromptVisible = false
        isPermanentlyDenied = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, LocalNetworkAccessPolicy.PERMISSION)
        if (granted) {
            val action = pendingAction
            pendingAction = null
            action?.invoke()
        } else {
            pendingAction = null
        }
    }

    fun openAppSettings(context: Context) {
        isPromptVisible = false
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/**
 * Default is a controller that never asks, so a screen rendered outside of the host keeps
 * working unchanged (the TV runs Android 12, which does not enforce the protection).
 */
val LocalNetworkPermission = compositionLocalOf { LocalNetworkPermissionController() }

/**
 * Hosts the permission request for the whole app: it tracks the current grant, shows the
 * rationale dialog once after launch, and runs the action that was waiting for the grant.
 */
@Composable
fun LocalNetworkPermissionHost(
    autoPrompt: Boolean = true,
    touchUi: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val controller = remember {
        LocalNetworkPermissionController(
            isRequired = LocalNetworkAccessPolicy.isEnforced(Build.VERSION.SDK_INT),
            isGranted = isPermissionGranted(context),
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> controller.onRequestResult(granted, activity) }

    SideEffect {
        controller.launchRequest = { launcher.launch(LocalNetworkAccessPolicy.PERMISSION) }
        // Re-read on every recomposition: the user may have changed the grant in Settings.
        controller.isRequired = LocalNetworkAccessPolicy.isEnforced(Build.VERSION.SDK_INT)
        controller.isGranted = isPermissionGranted(context)
    }

    LaunchedEffect(Unit) {
        if (autoPrompt && controller.isBlocking) controller.isPromptVisible = true
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalNetworkPermission provides controller,
        LocalTouchUi provides touchUi,
    ) {
        content()

        if (controller.isPromptVisible) {
            LocalNetworkPermissionDialog(
                isPermanentlyDenied = controller.isPermanentlyDenied,
                onAllow = { controller.allow(context) },
                onOpenSettings = { controller.openAppSettings(context) },
                onDismiss = { controller.dismiss() },
            )
        }
    }
}

private fun isPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, LocalNetworkAccessPolicy.PERMISSION) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun LocalNetworkPermissionDialog(
    isPermanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .widthIn(max = 680.dp)
                .background(SurfaceDark, RoundedCornerShape(18.dp))
                .padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.local_network_permission_title),
                style = MaterialTheme.typography.headlineSmall,
                color = NeonPink,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    if (isPermanentlyDenied) R.string.local_network_permission_blocked
                    else R.string.local_network_permission_message,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            Spacer(Modifier.height(26.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TvSecondaryButton(onClick = onDismiss) {
                    Text(stringResource(R.string.local_network_permission_later))
                }
                Spacer(Modifier.width(14.dp))
                if (isPermanentlyDenied) {
                    TvPrimaryButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.local_network_permission_settings))
                    }
                } else {
                    TvPrimaryButton(onClick = onAllow) {
                        Text(stringResource(R.string.local_network_permission_allow))
                    }
                }
            }
        }
    }
}
