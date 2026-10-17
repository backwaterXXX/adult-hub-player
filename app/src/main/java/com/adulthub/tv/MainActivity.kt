package com.adulthub.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.adulthub.tv.ui.navigation.AppNavigation
import com.adulthub.tv.ui.navigation.MobileAppNavigation
import com.adulthub.tv.ui.theme.AdultHubMobileTheme
import com.adulthub.tv.ui.theme.AdultHubTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        )
        setContent {
            if (BuildConfig.FLAVOR == "mobile") {
                AdultHubMobileTheme {
                    AdultHubTvTheme { MobileAppNavigation() }
                }
            } else {
                AdultHubTvTheme { AppNavigation() }
            }
        }
    }
}
