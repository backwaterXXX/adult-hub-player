package com.adulthub.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.adulthub.tv.diagnostics.AppLog as Log
import com.adulthub.tv.ui.components.LocalNetworkPermissionHost
import com.adulthub.tv.ui.navigation.AppNavigation
import com.adulthub.tv.ui.navigation.MobileAppNavigation
import com.adulthub.tv.ui.player.PlayerScreenState
import com.adulthub.tv.ui.theme.AdultHubMobileTheme
import com.adulthub.tv.ui.theme.AdultHubTvTheme

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        )
        setContent {
            LocalNetworkPermissionHost(autoPrompt = true, touchUi = BuildConfig.FLAVOR == "mobile") {
                if (BuildConfig.FLAVOR == "mobile") {
                    AdultHubMobileTheme {
                        MobileAppNavigation()
                    }
                } else {
                    AdultHubTvTheme { AppNavigation() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The player screen owns the keep screen on flag, it is restored after a pause.
        if (PlayerScreenState.isPlayerScreenVisible) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        Log.i(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        // Playback is paused by the player screen through the lifecycle observer, the flag is
        // released here so that a backgrounded app does not keep the screen awake.
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.i(TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.i(TAG, "onStop")
    }
}
