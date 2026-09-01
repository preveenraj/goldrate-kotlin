package com.techrush_app.goldrate

import android.app.Activity
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.techrush_app.goldrate.ui.theme.CardBg
import com.techrush_app.goldrate.ui.theme.Gold
import com.techrush_app.goldrate.ui.theme.TextPrimary

/**
 * Play in-app updates.
 *
 * The rates feed handles the failure the app actually has — a source changing
 * shape — without shipping anything. This covers the other half: a bug in the
 * app's own code, which only a new build can fix. Without it a fix waits on
 * Play's own update schedule, which can be days.
 *
 * Two flows, chosen by the priority set when the release is published (see
 * `inAppUpdatePriority` in `.github/workflows/release.yml`):
 *
 * - priority >= [IMMEDIATE_PRIORITY]: a blocking, full-screen Play update. For
 *   a release that fixes something broken enough that using the old build is
 *   worse than the interruption.
 * - anything lower: a flexible update, downloaded in the background while the
 *   app stays usable, then a quiet prompt to restart.
 */
private const val IMMEDIATE_PRIORITY = 4

/** Wraps the Play update manager so [MainActivity] stays about the app. */
class AppUpdateController(private val activity: Activity) {

    private val manager: AppUpdateManager by lazy { AppUpdateManagerFactory.create(activity) }

    /** True once a flexible update has finished downloading and needs a restart. */
    var updateReady: () -> Unit = {}

    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) updateReady()
    }

    fun start(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        manager.registerListener(listener)
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return@addOnSuccessListener
                val immediate = info.updatePriority() >= IMMEDIATE_PRIORITY
                val type = if (immediate) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE
                if (!info.isUpdateTypeAllowed(type)) return@addOnSuccessListener
                Log.i("appUpdate", "Update available, priority ${info.updatePriority()}")
                runCatching {
                    manager.startUpdateFlowForResult(
                        info,
                        launcher,
                        com.google.android.play.core.appupdate.AppUpdateOptions
                            .newBuilder(type).build(),
                    )
                }.onFailure { Log.e("appUpdate", "Could not start the update flow", it) }
            }
            .addOnFailureListener {
                // No Play Store, sideloaded, offline — none of it should be
                // visible to the user, so this only ever logs.
                Log.i("appUpdate", "Update check unavailable: ${it.message}")
            }
    }

    /**
     * Re-checks on resume. Catches two cases: an immediate update the user
     * backed out of mid-way (Play leaves it DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
     * and it must be resumed), and a flexible download that finished while the
     * app was in the background.
     */
    fun onResume(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() ==
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            ) {
                runCatching {
                    manager.startUpdateFlowForResult(
                        info,
                        launcher,
                        com.google.android.play.core.appupdate.AppUpdateOptions
                            .newBuilder(AppUpdateType.IMMEDIATE).build(),
                    )
                }.onFailure { Log.e("appUpdate", "Could not resume the update", it) }
            }
            if (info.installStatus() == InstallStatus.DOWNLOADED) updateReady()
        }
    }

    fun stop() = runCatching { manager.unregisterListener(listener) }

    /** Restarts into the downloaded update. */
    fun install() = manager.completeUpdate()
}

/**
 * The prompt shown once a flexible update has downloaded. Deliberately small
 * and dismissible-by-ignoring: the update installs on the next natural restart
 * anyway, so this is an offer, not a demand.
 */
@Composable
fun UpdateReadyBanner(onInstall: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().systemBarsPadding().padding(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardBg)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Update ready",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onInstall) {
                Text("Restart", color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
    Spacer(Modifier.height(0.dp))
}
