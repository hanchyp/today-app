package com.farhan.today

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farhan.today.reminder.EXTRA_ROUTE
import com.farhan.today.ui.TodayApp
import com.farhan.today.ui.theme.TodayTheme

class MainActivity : ComponentActivity() {
    /** Rute dari notifikasi yang belum dibuka. */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
