package com.micoyc.speakthat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * Handles persistence and side-effects for the master switch so any component
 * (UI, Tile, BroadcastReceiver) can toggle SpeakThat safely.
 */
object MasterSwitchController {

    private const val TAG = "MasterSwitchController"
    private const val PREFS_NAME = "SpeakThatPrefs"
    private const val KEY_MASTER_SWITCH_ENABLED = "master_switch_enabled"
    private const val KEY_PERSISTENT_NOTIFICATION = "persistent_notification"
    private const val CHANNEL_ID = "SpeakThat_Channel"
    private const val CHANNEL_NAME = "SpeakThat Notifications"
    private const val NOTIFICATION_ID_PERSISTENT = 1001
    private const val NOTIFICATION_ID_READING = 1002

    fun setEnabled(context: Context, enabled: Boolean, source: String? = null): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previous = prefs.getBoolean(KEY_MASTER_SWITCH_ENABLED, true)
        if (previous == enabled) {
            InAppLogger.logDebug(
                "MasterSwitch",
                "Ignoring request from ${source ?: "unknown"}; already ${if (enabled) "enabled" else "disabled"}"
            )
            return false
        }

        prefs.edit().putBoolean(KEY_MASTER_SWITCH_ENABLED, enabled).apply()
        manageNotifications(context, enabled, prefs)

        InAppLogger.logSettingsChange("Master Switch", previous.toString(), enabled.toString())
        InAppLogger.log(
            "MasterSwitch",
            "Master switch ${if (enabled) "enabled" else "disabled"} via ${source ?: "unknown"}"
        )

        if (source?.startsWith("AutomationIntent:") == true) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    if (enabled) {
                        context.getString(R.string.master_switch_enabled_toast_automation)
                    } else {
                        context.getString(R.string.master_switch_disabled_toast_automation)
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        return true
    }

    fun manageNotifications(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        manageNotifications(context, enabled, prefs)
    }

    private fun manageNotifications(
        context: Context,
        enabled: Boolean,
        prefs: SharedPreferences
    ) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (enabled) {
                val persistentEnabled = prefs.getBoolean(KEY_PERSISTENT_NOTIFICATION, false)
                if (!persistentEnabled) {
                    return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Notifications from SpeakThat app"
                        setSound(null, null)
                        enableVibration(false)
                        setShowBadge(false)
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle(context.getString(R.string.main_notification_title_active))
                    .setContentText(context.getString(R.string.main_notification_content_tap_settings))
                    .setSmallIcon(R.drawable.speakthaticon)
                    .setOngoing(true)
                    .setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .addAction(R.drawable.speakthaticon, "Open SpeakThat!", pendingIntent)
                    .build()

                notificationManager.notify(NOTIFICATION_ID_PERSISTENT, notification)
                Log.d(TAG, "Persistent notification shown (master switch enabled)")
                InAppLogger.log("Notifications", "Persistent notification shown due to master switch enabled")
            } else {
                notificationManager.cancel(NOTIFICATION_ID_PERSISTENT)
                notificationManager.cancel(NOTIFICATION_ID_READING)
                Log.d(TAG, "Persistent notifications hidden (master switch disabled)")
                InAppLogger.log("Notifications", "All SpeakThat notifications hidden due to master switch disabled")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error managing notifications", t)
            InAppLogger.logError("Notifications", "Error managing notifications for master switch: ${t.message}")
        }
    }
}

