package com.techrush_app.goldrate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.techrush_app.goldrate.ui.theme.GoldRateTheme
import com.techrush_app.goldrate.ui.theme.ScreenBgTop

class MainActivity : ComponentActivity() {

    // Result is ignored: we schedule the daily job either way, and the notification
    // simply no-ops if the user declines.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        createGoldNotificationChannel(this)
        scheduleDailyGoldNotification(this)
        ensureNotificationPermission()

        setContent {
            GoldRateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ScreenBgTop,
                ) {
                    Container()
                }
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
