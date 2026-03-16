package com.micoyc.speakthat.summary

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.micoyc.speakthat.InAppLogger
import java.util.Calendar

/**
 * Schedules/cancels daily exact alarms for proactive summaries.
 */
object SummaryScheduler {

    private const val TAG = "SummaryScheduler"

    fun scheduleDaily(context: Context, hourOfDay: Int, minute: Int): Boolean {
        if (hourOfDay !in 0..23 || minute !in 0..59) {
            InAppLogger.logError(TAG, "Invalid schedule time: $hourOfDay:$minute")
            return false
        }

        persistSchedule(context, enabled = true, hourOfDay = hourOfDay, minute = minute)
        return scheduleExactAtNextOccurrence(context, hourOfDay, minute)
    }

    fun updateDaily(context: Context, hourOfDay: Int, minute: Int): Boolean {
        cancelAlarmOnly(context)
        return scheduleDaily(context, hourOfDay, minute)
    }

    fun cancelDaily(context: Context) {
        cancelAlarmOnly(context)
        persistSchedule(context, enabled = false, hourOfDay = 0, minute = 0)
        InAppLogger.log(TAG, "Daily proactive summary alarm cancelled")
    }

    /**
     * Rebuilds the exact alarm schedule after a reboot.
     */
    @JvmStatic
    fun rescheduleIfEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(SummaryConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(SummaryConstants.KEY_ENABLED, false)
        if (!enabled) {
            InAppLogger.log(TAG, "Boot reschedule skipped; summary scheduler is disabled")
            return false
        }

        val hourOfDay = prefs.getInt(SummaryConstants.KEY_HOUR_OF_DAY, 8)
        val minute = prefs.getInt(SummaryConstants.KEY_MINUTE, 0)
        return scheduleExactAtNextOccurrence(context, hourOfDay, minute)
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

        val message = "Scheduled daily summary alarm at $hourOfDay:$minute (next=$triggerAtMillis)"
        Log.d(TAG, message)
        InAppLogger.log(TAG, message)
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
            action = SummaryConstants.ACTION_TRIGGER_SUMMARY
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

    private fun persistSchedule(
        context: Context,
        enabled: Boolean,
        hourOfDay: Int,
        minute: Int
    ) {
        context.getSharedPreferences(SummaryConstants.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SummaryConstants.KEY_ENABLED, enabled)
            .putInt(SummaryConstants.KEY_HOUR_OF_DAY, hourOfDay)
            .putInt(SummaryConstants.KEY_MINUTE, minute)
            .apply()
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
