package com.techrush_app.goldrate

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

/**
 * Runs each morning: scrapes the latest gold rate, posts a notification, then
 * schedules itself for the next day. Rescheduling here (rather than relying on a
 * periodic request) keeps the notification anchored to a fixed time of day.
 */
class GoldNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        val data = fetchData()
        if (data != null) {
            showGoldNotification(applicationContext, data)
        }
        // Queue tomorrow's run regardless of whether today's fetch succeeded.
        scheduleDailyGoldNotification(applicationContext)
        return ListenableWorker.Result.success()
    }
}
