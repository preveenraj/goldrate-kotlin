package com.techrush_app.goldrate

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

const val GOLD_CHANNEL_ID = "daily_gold_rate"
const val DAILY_WORK_NAME = "daily-gold-notification"
private const val NOTIFY_HOUR = 11
private const val NOTIFY_MINUTE = 0
private const val NOTIFICATION_ID = 1001

/** Creates the notification channel (no-op below Android O). Safe to call repeatedly. */
fun createGoldNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            GOLD_CHANNEL_ID,
            "Daily gold rate",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A morning update of the 22K gold rate."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/**
 * (Re)schedules the daily notification worker for the next [NOTIFY_HOUR]:[NOTIFY_MINUTE].
 * Uses a chained one-time request (the worker reschedules itself) so it fires at a
 * precise time each day rather than drifting like a periodic request. REPLACE keeps
 * a single pending job even if this is called on every app launch.
 */
fun scheduleDailyGoldNotification(context: Context) {
    val request = OneTimeWorkRequestBuilder<GoldNotificationWorker>()
        .setInitialDelay(millisUntilNext(NOTIFY_HOUR, NOTIFY_MINUTE), TimeUnit.MILLISECONDS)
        .setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        )
        .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork(DAILY_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
}

private fun millisUntilNext(hour: Int, minute: Int): Long {
    val now = Calendar.getInstance()
    val next = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
    }
    return next.timeInMillis - now.timeInMillis
}

/** Posts (or updates) the morning gold-rate notification. Silently returns if the
 *  user hasn't granted the notification permission. */
fun showGoldNotification(context: Context, data: Result) {
    createGoldNotificationChannel(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val arrow = when {
        data.change > 0 -> "▲ +₹"
        data.change < 0 -> "▼ -₹"
        else -> "▬ ₹"
    }
    val changeText = arrow + formatINR(data.change.absoluteValue)
    val title = "Gold today · ₹${formatINR(data.rate)}/g (22K)"
    val text = "Pavan (8g) ₹${formatINR(data.rate * 8)}  ·  $changeText vs last reading"

    val launch = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pending = PendingIntent.getActivity(
        context,
        0,
        launch,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val notification = NotificationCompat.Builder(context, GOLD_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_gold)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setColor(0xFFC8A12B.toInt())
        .setContentIntent(pending)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    try {
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    } catch (e: SecurityException) {
        // Permission revoked between the check and the post; nothing to do.
    }
}
