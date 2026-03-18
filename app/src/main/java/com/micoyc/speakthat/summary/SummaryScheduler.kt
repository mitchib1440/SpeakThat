package com.micoyc.speakthat.summary

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.micoyc.speakthat.InAppLogger
import java.util.Calendar

/**
 * Schedules/cancels daily exact alarms for summaries.
 */
object SummaryScheduler {

    private const val TAG = "SummaryScheduler"

    fun schedule(context: Context): Boolean {
        val prefs = SummarySettingsGate.prefs(context)
        if (!SummarySettingsGate.canSchedule(context)) {
            cancelAlarmOnly(context)
            InAppLogger.log(TAG, "Schedule skipped; scheduler or global prerequisites not satisfied")
            return false
        }
        val hourOfDay = prefs.getInt(SummaryConstants.KEY_HOUR_OF_DAY, 8)
        val minute = prefs.getInt(SummaryConstants.KEY_MINUTE, 0)
        if (hourOfDay !in 0..23 || minute !in 0..59) {
            InAppLogger.logError(TAG, "Invalid schedule time: $hourOfDay:$minute")
            return false
        }

        return scheduleExactAtNextOccurrence(context, hourOfDay, minute)
    }

    fun cancel(context: Context) {
        cancelAlarmOnly(context)
        InAppLogger.log(TAG, "Daily summary alarm cancelled")
    }

    // Compatibility wrappers for older call sites.
    fun scheduleDaily(context: Context, hourOfDay: Int, minute: Int): Boolean {
        if (hourOfDay !in 0..23 || minute !in 0..59) {
            InAppLogger.logError(TAG, "Invalid schedule time: $hourOfDay:$minute")
            return false
        }
        context.getSharedPreferences(SummaryConstants.SUMMARY_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(SummaryConstants.KEY_HOUR_OF_DAY, hourOfDay)
            .putInt(SummaryConstants.KEY_MINUTE, minute)
            .apply()
        return schedule(context)
    }

    fun updateDaily(context: Context, hourOfDay: Int, minute: Int): Boolean {
        cancel(context)
        return scheduleDaily(context, hourOfDay, minute)
    }

    fun cancelDaily(context: Context) {
        cancel(context)
    }

    /**
     * Rebuilds the exact alarm schedule after a reboot.
     */
    @JvmStatic
    fun rescheduleIfEnabled(context: Context): Boolean {
        SummarySettingsGate.prefs(context)
        if (!SummarySettingsGate.canSchedule(context)) {
            cancelAlarmOnly(context)
            InAppLogger.log(TAG, "Boot reschedule skipped; scheduler/global prerequisites not satisfied")
            return false
        }
        return schedule(context)
    }

    private fun scheduleExactAtNextOccurrence(context: Context, hourOfDay: Int, minute: Int): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!canScheduleExactAlarms(alarmManager)) {
            InAppLogger.logError(TAG, "Exact alarms not permitted; skipping schedule")
            return false
        }

        val triggerAtMillis = computeNextTriggerTimeMillis(hourOfDay, minute)
        val pendingIntent = buildAlarmPendingIntent(context)

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            else -> {
                @Suppress("DEPRECATION")
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }

        InAppLogger.log(TAG, "Scheduled daily summary alarm at $hourOfDay:$minute (next=$triggerAtMillis)")
        return true
    }

    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        return alarmManager.canScheduleExactAlarms()
    }

    private fun cancelAlarmOnly(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildAlarmPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SummaryTriggerReceiver::class.java).apply {
            action = SummaryConstants.ACTION_SUMMARY_ALARM
            putExtra(
                SummaryConstants.EXTRA_TRIGGER_SOURCE,
                SummaryConstants.TRIGGER_SOURCE_SCHEDULED_ALARM
            )
        }
        return PendingIntent.getBroadcast(
            context,
            SummaryConstants.ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun computeNextTriggerTimeMillis(hourOfDay: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return next.timeInMillis
    }
}
