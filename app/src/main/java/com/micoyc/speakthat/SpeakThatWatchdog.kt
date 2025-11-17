package com.micoyc.speakthat

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Lightweight watchdog that periodically checks whether the notification listener
 * has received any events recently. If it detects a stale listener, it requests
 * a soft rebind through the supplied callback while respecting strict cooldowns.
 */
class SpeakThatWatchdog(
    private val callback: Callback,
    private val lastActivityProvider: () -> Long,
    private val checkIntervalMs: Long = DEFAULT_CHECK_INTERVAL_MS,
    private val staleThresholdMs: Long = DEFAULT_STALE_THRESHOLD_MS,
    private val rebindCooldownMs: Long = DEFAULT_REBIND_COOLDOWN_MS
) {

    interface Callback {
        fun isWatchdogAllowed(): Boolean
        fun onWatchdogStale(idleMs: Long): Boolean
    }

    companion object {
        private const val TAG = "SpeakThatWatchdog"
        private const val DEFAULT_CHECK_INTERVAL_MS = 45_000L
        private const val DEFAULT_STALE_THRESHOLD_MS = 3 * 60 * 1000L
        private const val DEFAULT_REBIND_COOLDOWN_MS = 2 * 60 * 1000L
        private const val STALE_LOG_THROTTLE_MS = 60_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = Runnable { performCheck() }

    @Volatile
    private var isRunning = false

    @Volatile
    private var lastInterventionTimestamp = 0L

    @Volatile
    private var lastLogTimestamp = 0L

    fun start(reason: String) {
        if (isRunning) {
            Log.d(TAG, "Watchdog already running - ignoring start request ($reason)")
            return
        }
        Log.d(TAG, "Starting watchdog ($reason)")
        isRunning = true
        scheduleNextCheck()
    }

    fun stop(reason: String) {
        if (!isRunning) {
            Log.d(TAG, "Watchdog already stopped - ignoring stop request ($reason)")
            return
        }
        Log.d(TAG, "Stopping watchdog ($reason)")
        isRunning = false
        handler.removeCallbacks(checkRunnable)
    }

    /**
     * Records manual interventions (e.g., scheduled rebinds) so the watchdog
     * does not immediately issue another request before the previous one finishes.
     */
    fun recordExternalIntervention(reason: String) {
        lastInterventionTimestamp = System.currentTimeMillis()
        Log.d(TAG, "Watchdog intervention recorded ($reason)")
    }

    private fun performCheck() {
        if (!isRunning) {
            return
        }

        if (!callback.isWatchdogAllowed()) {
            scheduleNextCheck()
            return
        }

        val now = System.currentTimeMillis()
        val lastActivity = lastActivityProvider().takeIf { it > 0L } ?: now
        val idleMs = now - lastActivity

        if (idleMs >= staleThresholdMs) {
            maybeLogStaleState(idleMs, now)

            if (now - lastInterventionTimestamp >= rebindCooldownMs) {
                val requested = callback.onWatchdogStale(idleMs)
                lastInterventionTimestamp = now
                val outcome = if (requested) "requested" else "skipped"
                Log.w(TAG, "Watchdog $outcome rebind after ${idleMs}ms idle")
                if (requested) {
                    InAppLogger.log("Watchdog", "Requested listener rebind after ${idleMs}ms idle")
                } else {
                    InAppLogger.logWarning("Watchdog", "Rebind skipped (idle=${idleMs}ms)")
                }
            } else {
                Log.d(TAG, "Watchdog idle=${idleMs}ms but waiting for cooldown (${rebindCooldownMs}ms)")
            }
        }

        scheduleNextCheck()
    }

    private fun maybeLogStaleState(idleMs: Long, now: Long) {
        if (now - lastLogTimestamp < STALE_LOG_THROTTLE_MS) {
            return
        }
        lastLogTimestamp = now
        Log.w(TAG, "Watchdog detected idle listener for ${idleMs}ms (threshold=${staleThresholdMs}ms)")
        InAppLogger.logWarning(
            "Watchdog",
            "Listener idle for ${idleMs / 1000}s (threshold=${staleThresholdMs / 1000}s)"
        )
    }

    private fun scheduleNextCheck() {
        handler.removeCallbacks(checkRunnable)
        if (isRunning) {
            handler.postDelayed(checkRunnable, checkIntervalMs)
        }
    }
}


