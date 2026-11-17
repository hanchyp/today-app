package com.farhan.today

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farhan.today.reminder.EXTRA_ROUTE
import com.farhan.today.ui.TodayApp
import com.farhan.today.ui.theme.TodayTheme

class MainActivity : ComponentActivity() {
    /** Rute dari notifikasi yang belum dibuka. */
    private var pendingRoute by mutableStateOf<String?>(null)

    /** Splash ditahan sampai preferensi onboarding terbaca (maks. ~1 detik di HP lambat). */
    private var prefsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !prefsReady }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            withTimeoutOrNull(1_500) { appContainer.prefs.onboarded.first() }
            prefsReady = true
        }
        if (savedInstanceState == null) pendingRoute = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            TodayTheme {
                TodayApp(deepLink = pendingRoute, onDeepLinkHandled = { pendingRoute = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute = it }
    }
}
