package com.micoyc.speakthat

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Lightweight foreground service that owns the persistent notification indicator.
 */
class PersistentIndicatorService : Service() {

    private var isForegroundActive = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PersistentIndicatorService created")
        InAppLogger.log("PersistentService", "PersistentIndicatorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "PersistentIndicatorService onStartCommand action=$action")

        if (action == PersistentIndicatorManager.ACTION_STOP) {
            stopIndicator("Explicit stop action")
            return START_NOT_STICKY
        }

        if (!PersistentIndicatorManager.shouldRun(this)) {
            stopIndicator("Requirements not met (toggle or master switch off)")
            return START_NOT_STICKY
        }

        val notification = PersistentIndicatorManager.buildNotification(this)
        startForeground(PersistentIndicatorManager.NOTIFICATION_ID, notification)
        isForegroundActive = true
        Log.d(TAG, "Persistent indicator started")
        InAppLogger.log("PersistentService", "Persistent foreground indicator active")

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "PersistentIndicatorService destroyed")
        InAppLogger.log("PersistentService", "PersistentIndicatorService destroyed")
        if (isForegroundActive) {
            stopForeground(true)
            isForegroundActive = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopIndicator(reason: String) {
        Log.d(TAG, "Stopping persistent indicator: $reason")
        InAppLogger.log("PersistentService", "Stopping persistent indicator: $reason")
        if (isForegroundActive) {
            stopForeground(true)
            isForegroundActive = false
        }
        stopSelf()
    }

    companion object {
        private const val TAG = "PersistentIndicatorSvc"
    }
}

