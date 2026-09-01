package com.techrush_app.goldrate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.techrush_app.goldrate.ui.theme.GoldRateTheme
import com.techrush_app.goldrate.ui.theme.ScreenBgTop

class MainActivity : ComponentActivity() {

    // Result is ignored: we schedule the daily job either way, and the notification
    // simply no-ops if the user declines.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val appUpdates = AppUpdateController(this)

    // Result is ignored: declining an update is the user's call, and a failed
    // one is retried on the next resume.
    private val updateFlow =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    private var updateReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        createGoldNotificationChannel(this)
        scheduleDailyGoldNotification(this)
        ensureNotificationPermission()

        appUpdates.updateReady = { updateReady = true }
        appUpdates.start(updateFlow)

        setContent {
            GoldRateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ScreenBgTop,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Container()
                        if (updateReady) {
                            UpdateReadyBanner(
                                onInstall = { appUpdates.install() },
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appUpdates.onResume(updateFlow)
    }

    override fun onDestroy() {
        appUpdates.stop()
        super.onDestroy()
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
