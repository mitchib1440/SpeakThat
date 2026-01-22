package com.micoyc.speakthat

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Centralizes logic for managing the persistent notification indicator.
 */
object PersistentIndicatorManager {
    private const val TAG = "PersistentIndicator"
    private const val PREFS_NAME = "SpeakThatPrefs"
    private const val KEY_PERSISTENT_NOTIFICATION = "persistent_notification"

    const val ACTION_START = "com.micoyc.speakthat.action.START_PERSISTENT_INDICATOR"
    const val ACTION_STOP = "com.micoyc.speakthat.action.STOP_PERSISTENT_INDICATOR"
    const val NOTIFICATION_ID = 1001

    fun shouldRun(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val persistentEnabled = prefs.getBoolean(KEY_PERSISTENT_NOTIFICATION, false)
        val masterEnabled = MainActivity.isMasterSwitchEnabled(context)
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        Log.d(TAG, "shouldRun? persistent=$persistentEnabled master=$masterEnabled permission=$hasPermission")
        return persistentEnabled && masterEnabled && hasPermission
    }

    fun requestStart(context: Context) {
        val intent = Intent(context, PersistentIndicatorService::class.java).apply {
            action = ACTION_START
        }
        ContextCompat.startForegroundService(context.applicationContext, intent)
    }

    fun requestStop(context: Context) {
        val intent = Intent(context, PersistentIndicatorService::class.java)
        context.applicationContext.stopService(intent)
    }

    fun buildNotification(context: Context): Notification {
        SpeakThatNotificationChannel.ensureExists(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.main_notification_title_active)
        val content = context.getString(R.string.main_notification_content_tap_settings)
        val actionLabel = context.getString(R.string.main_notification_action_open_app)

        val notification = NotificationCompat.Builder(context, SpeakThatNotificationChannel.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.speakthaticon)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(R.drawable.speakthaticon, actionLabel, openAppPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
        }

        return notification
    }
}

