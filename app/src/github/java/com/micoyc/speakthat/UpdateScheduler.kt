package com.micoyc.speakthat

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object UpdateScheduler {
    fun schedule(context: Context) {
        if (!BuildConfig.ENABLE_AUTO_UPDATER || BuildConfig.DISTRIBUTION_CHANNEL != "github") return

        val prefs = context.getSharedPreferences(
            context.getString(R.string.prefs_speakthat),
            Context.MODE_PRIVATE
        )
        val autoUpdateEnabled = prefs.getBoolean("auto_update_enabled", true)
        if (!autoUpdateEnabled) {
            cancel(context)
            return
        }

        val frequency = prefs.getString("update_check_frequency", "weekly") ?: "weekly"
        if (frequency == "never") {
            prefs.edit().putString("update_check_frequency", "weekly").apply()
        }

        val hours = UpdateCheckWorker.frequencyHours(prefs.getString("update_check_frequency", "weekly") ?: "weekly")
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(hours, TimeUnit.HOURS)
            .addTag(context.getString(R.string.work_update_check_tag))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            context.getString(R.string.work_update_check_name),
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(
            context.getString(R.string.work_update_check_name)
        )
    }
}

