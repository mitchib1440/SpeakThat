package com.micoyc.speakthat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Utility that ensures SpeakThat's notification channel exists.
 * Both the main notification listener and the persistent indicator share this channel.
 */
object SpeakThatNotificationChannel {
    private const val TAG = "SpeakThatChannel"
    const val CHANNEL_ID = "SpeakThat_Channel"
    private const val CHANNEL_NAME = "SpeakThat! Notifications"
    private const val CHANNEL_DESCRIPTION = "Notifications from SpeakThat!"

    fun ensureExists(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) {
                return
            }

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel ensured")
        } catch (e: Exception) {
            Log.e(TAG, "Unable to create notification channel", e)
        }
    }
}

