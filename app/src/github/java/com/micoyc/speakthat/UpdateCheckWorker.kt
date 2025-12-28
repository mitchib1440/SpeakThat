package com.micoyc.speakthat

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!BuildConfig.ENABLE_AUTO_UPDATER || BuildConfig.DISTRIBUTION_CHANNEL != "github") return Result.success()

        val mainPrefs = applicationContext.getSharedPreferences(
            applicationContext.getString(R.string.prefs_speakthat),
            Context.MODE_PRIVATE
        )

        val autoUpdateEnabled = mainPrefs.getBoolean("auto_update_enabled", true)
        if (!autoUpdateEnabled) return Result.success()

        val updateManager = UpdateManager.getInstance(applicationContext)

        // Respect frequency and last check interval
        if (!updateManager.shouldCheckForUpdates()) return Result.success()

        // Skip if sourced from Play or repos
        if (updateManager.isInstalledFromGooglePlay() || updateManager.isInstalledFromRepository()) {
            return Result.success()
        }

        // Skip if app is active; foreground banner will cover this
        if (UpdateAppForegroundTracker.isAppInForeground()) return Result.success()

        return runCatching {
            val updateInfo = updateManager.checkForUpdates()
            if (updateInfo != null) {
                UpdateAvailabilityCache.save(applicationContext, updateInfo)
                if (!updateManager.hasNotifiedAboutVersion(updateInfo.versionName)) {
                    UpdateNotifier.maybeNotify(applicationContext, updateInfo)
                    updateManager.markVersionNotified(updateInfo.versionName)
                }
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        fun frequencyHours(frequency: String): Long {
            return when (frequency) {
                "daily" -> 24L
                "weekly" -> 24L * 7
                "monthly" -> 24L * 30
                else -> 24L
            }
        }

        fun repeatIntervalMillis(frequency: String): Long {
            val hours = frequencyHours(frequency)
            if (hours == Long.MAX_VALUE) return Long.MAX_VALUE
            return TimeUnit.HOURS.toMillis(hours)
        }
    }
}

