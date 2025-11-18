package com.micoyc.speakthat

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.TextUtils
import android.util.Log

/**
 * Centralized helper that keeps the NotificationListenerService bound.
 *
 * It tracks connection/disconnection timestamps plus throttles rebind requests so
 * OEM-killed services can restart automatically without spamming the system.
 */
object NotificationListenerRecovery {

    private const val TAG = "ListenerRecovery"
    private const val PREFS_NAME = "notification_listener_recovery"

    private const val KEY_LAST_CONNECT = "last_listener_connect"
    private const val KEY_LAST_DISCONNECT = "last_listener_disconnect"
    private const val KEY_LAST_REBIND_ATTEMPT = "last_rebind_attempt"
    private const val KEY_LAST_REBIND_SUCCESS = "last_rebind_success"
    private const val KEY_LAST_REBIND_REASON = "last_rebind_reason"
    private const val KEY_LAST_HEARTBEAT = "last_listener_heartbeat"
    private const val KEY_CURRENT_BACKOFF = "current_rebind_backoff"
    private const val KEY_ATTEMPT_WINDOW_START = "attempt_window_start"
    private const val KEY_ATTEMPTS_IN_WINDOW = "attempts_in_window"

    private const val BASE_BACKOFF_MS = 15_000L
    private const val MAX_BACKOFF_MS = 15 * 60 * 1000L
    private const val ATTEMPT_WINDOW_MS = 30 * 60 * 1000L
    private const val MAX_ATTEMPTS_PER_WINDOW = 6
    private const val REBIND_SUCCESS_WINDOW_MS = 10 * 60 * 1000L
    private const val DEFAULT_STALE_THRESHOLD_MS = 5 * 60 * 1000L

    @JvmStatic
    fun recordConnection(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong(KEY_LAST_CONNECT, now)
            .putLong(KEY_CURRENT_BACKOFF, BASE_BACKOFF_MS)
            .putLong(KEY_ATTEMPT_WINDOW_START, 0L)
            .putInt(KEY_ATTEMPTS_IN_WINDOW, 0)
            .putLong(KEY_LAST_REBIND_SUCCESS, now)
            .putString(KEY_LAST_REBIND_REASON, "connected")
            .putLong(KEY_LAST_HEARTBEAT, now)
            .apply()

        val lastAttempt = prefs.getLong(KEY_LAST_REBIND_ATTEMPT, 0L)
        if (lastAttempt != 0L && now - lastAttempt <= REBIND_SUCCESS_WINDOW_MS) {
            try {
                StatisticsManager.getInstance(context).incrementListenerRebindRecovered()
            } catch (e: Exception) {
                Log.w(TAG, "Unable to record rebind recovery metric", e)
            }
        }
        InAppLogger.log("ServiceRebind", "Notification listener connected; counters reset")
    }

    @JvmStatic
    fun recordDisconnect(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_DISCONNECT, System.currentTimeMillis())
            .putString(KEY_LAST_REBIND_REASON, "disconnected")
            .apply()
        InAppLogger.logWarning("ServiceRebind", "Notification listener disconnected")
    }

    @JvmStatic
    fun recordHeartbeat(context: Context, source: String) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_HEARTBEAT, now)
            .apply()
        InAppLogger.log("ServiceHeartbeat", "Heartbeat recorded from $source")
    }

    @JvmStatic
    fun requestRebind(context: Context, reason: String, force: Boolean = false): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        if (!isNotificationAccessGranted(context)) {
            Log.w(TAG, "Rebind skipped - permission missing (reason: $reason)")
            InAppLogger.logWarning("ServiceRebind", "Rebind skipped - permission missing (reason=$reason)")
            prefs.edit()
                .putLong(KEY_LAST_REBIND_ATTEMPT, now)
                .putString(KEY_LAST_REBIND_REASON, "permission_missing")
                .apply()
            try {
                StatisticsManager.getInstance(context).incrementListenerRebindSkipped()
            } catch (ignored: Exception) {
                Log.w(TAG, "Unable to record skipped rebind metric", ignored)
            }
            return false
        }

        var backoff = prefs.getLong(KEY_CURRENT_BACKOFF, BASE_BACKOFF_MS).coerceAtLeast(BASE_BACKOFF_MS)
        var windowStart = prefs.getLong(KEY_ATTEMPT_WINDOW_START, 0L)
        var attemptsInWindow = prefs.getInt(KEY_ATTEMPTS_IN_WINDOW, 0)
        val lastAttempt = prefs.getLong(KEY_LAST_REBIND_ATTEMPT, 0L)

        if (!force) {
            if (windowStart == 0L || now - windowStart > ATTEMPT_WINDOW_MS) {
                windowStart = now
                attemptsInWindow = 0
            }

            if (attemptsInWindow >= MAX_ATTEMPTS_PER_WINDOW) {
                Log.w(TAG, "Rebind suppressed: attempt limit reached (reason: $reason)")
                InAppLogger.logWarning("ServiceRebind", "Rebind skipped (limit reached, reason=$reason)")
                try {
                    StatisticsManager.getInstance(context).incrementListenerRebindSkipped()
                } catch (ignored: Exception) {
                    Log.w(TAG, "Unable to record skipped rebind metric", ignored)
                }
                prefs.edit().putString(KEY_LAST_REBIND_REASON, "limit_reached").apply()
                return false
            }

            if (lastAttempt != 0L && now - lastAttempt < backoff) {
                val remaining = backoff - (now - lastAttempt)
                Log.d(TAG, "Rebind suppressed for ${remaining}ms (cool-down, reason: $reason)")
                InAppLogger.log("ServiceRebind", "Rebind skipped (cool-down ${remaining}ms, reason=$reason)")
                try {
                    StatisticsManager.getInstance(context).incrementListenerRebindSkipped()
                } catch (ignored: Exception) {
                    Log.w(TAG, "Unable to record skipped rebind metric", ignored)
                }
                prefs.edit().putString(KEY_LAST_REBIND_REASON, "cooldown").apply()
                return false
            }
        }

        return try {
            val componentName = ComponentName(context, NotificationReaderService::class.java)
            NotificationListenerService.requestRebind(componentName)
            try {
                StatisticsManager.getInstance(context).incrementListenerRebindRequested()
            } catch (ignored: Exception) {
                Log.w(TAG, "Unable to record rebind metric", ignored)
            }

            val nextBackoff = if (force) BASE_BACKOFF_MS else (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)

            prefs.edit()
                .putLong(KEY_LAST_REBIND_ATTEMPT, now)
                .putLong(KEY_CURRENT_BACKOFF, nextBackoff)
                .putLong(KEY_ATTEMPT_WINDOW_START, windowStart)
                .putInt(KEY_ATTEMPTS_IN_WINDOW, attemptsInWindow + 1)
                .putString(KEY_LAST_REBIND_REASON, reason)
                .apply()

            InAppLogger.log(
                "ServiceRebind",
                "Rebind requested (reason=$reason, attempt=${attemptsInWindow + 1}, nextBackoff=${nextBackoff}ms, force=$force)"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "requestRebind failed", e)
            InAppLogger.logError("ServiceRebind", "Rebind request failed: ${e.message}")
            false
        }
    }

    @JvmStatic
    fun isNotificationAccessGranted(context: Context): Boolean {
        return try {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (enabled.isNullOrEmpty()) {
                false
            } else {
                enabled.split(":").any { entry ->
                    val component = ComponentName.unflattenFromString(entry)
                    component != null && TextUtils.equals(component.packageName, context.packageName)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to read notification listener settings", e)
            false
        }
    }

    @JvmStatic
    fun getLastListenerConnectTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_CONNECT, 0L)
    }

    @JvmStatic
    fun getLastListenerDisconnectTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_DISCONNECT, 0L)
    }

    @JvmStatic
    fun getLastRebindAttemptTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_REBIND_ATTEMPT, 0L)
    }

    @JvmStatic
    fun getLastRebindSuccessTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_REBIND_SUCCESS, 0L)
    }

    @JvmStatic
    fun getLastHeartbeatTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_HEARTBEAT, 0L)
    }

    @JvmStatic
    fun getListenerStatus(context: Context): ListenerStatus {
        return getListenerStatus(context, DEFAULT_STALE_THRESHOLD_MS)
    }

    @JvmStatic
    fun getListenerStatus(context: Context, staleThresholdMs: Long): ListenerStatus {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastConnect = prefs.getLong(KEY_LAST_CONNECT, 0L)
        val lastDisconnect = prefs.getLong(KEY_LAST_DISCONNECT, 0L)
        val lastRebind = prefs.getLong(KEY_LAST_REBIND_ATTEMPT, 0L)
        val lastRebindSuccess = prefs.getLong(KEY_LAST_REBIND_SUCCESS, 0L)
        val lastReason = prefs.getString(KEY_LAST_REBIND_REASON, null)
        val lastHeartbeat = prefs.getLong(KEY_LAST_HEARTBEAT, 0L)
        val permissionGranted = isNotificationAccessGranted(context)
        val now = System.currentTimeMillis()
        val isDisconnected = permissionGranted && lastDisconnect > lastConnect && lastDisconnect != 0L
        val lastHealthy = maxOf(lastConnect, lastHeartbeat)
        val isStale = permissionGranted && !isDisconnected &&
            lastHealthy != 0L && now - lastHealthy > staleThresholdMs

        return ListenerStatus(
            permissionGranted = permissionGranted,
            lastConnect = lastConnect,
            lastDisconnect = lastDisconnect,
            lastRebindAttempt = lastRebind,
            lastRebindSuccess = lastRebindSuccess,
            lastRebindReason = lastReason,
            lastHeartbeat = lastHeartbeat,
            isDisconnected = isDisconnected,
            isStale = isStale
        )
    }

    data class ListenerStatus(
        val permissionGranted: Boolean,
        val lastConnect: Long,
        val lastDisconnect: Long,
        val lastRebindAttempt: Long,
        val lastRebindSuccess: Long,
        val lastRebindReason: String?,
        val lastHeartbeat: Long,
        val isDisconnected: Boolean,
        val isStale: Boolean
    )
}

