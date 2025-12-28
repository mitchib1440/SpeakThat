package com.micoyc.speakthat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object UpdateNotifier {
    private const val NOTIFICATION_ID = 2048

    fun maybeNotify(context: Context, updateInfo: UpdateManager.UpdateInfo) {
        if (!BuildConfig.ENABLE_AUTO_UPDATER || BuildConfig.DISTRIBUTION_CHANNEL != "github") return

        // Permission gate for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return
        }

        val manager = NotificationManagerCompat.from(context)
        val channelId = context.getString(R.string.update_notification_channel_id)
        val channelName = context.getString(R.string.update_notification_channel_name)
        ensureChannel(context, channelId, channelName)

        val intent = PendingIntent.getActivity(
            context,
            0,
            UpdateActivity.createIntent(context, forceCheck = true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.update_notification_title, updateInfo.versionName)
        val body = context.getString(R.string.update_notification_text)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context, channelId: String, channelName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = context.getString(R.string.update_notification_channel_description)
        manager.createNotificationChannel(channel)
    }
}

