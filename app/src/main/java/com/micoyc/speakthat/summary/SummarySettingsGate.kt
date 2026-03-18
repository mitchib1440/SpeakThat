package com.micoyc.speakthat.summary

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings

/**
 * Centralized preference and runtime gating rules for summaries.
 */
object SummarySettingsGate {

    fun prefs(context: Context): SharedPreferences {
        val prefs = context.getSharedPreferences(
            SummaryConstants.SUMMARY_SETTINGS_PREFS_NAME,
            Context.MODE_PRIVATE
        )
        migrateLegacyIfNeeded(prefs)
        return prefs
    }

    fun migrateLegacyIfNeeded(prefs: SharedPreferences) {
        val hasGlobal = prefs.contains(SummaryConstants.KEY_GLOBAL_ENABLED)
        val hasScheduler = prefs.contains(SummaryConstants.KEY_SCHEDULER_ENABLED)
        if (hasGlobal && hasScheduler) {
            return
        }

        val legacyEnabled = if (prefs.contains(SummaryConstants.KEY_ENABLED)) {
            prefs.getBoolean(SummaryConstants.KEY_ENABLED, false)
        } else {
            false
        }

        val editor = prefs.edit()
        if (!hasGlobal) {
            editor.putBoolean(SummaryConstants.KEY_GLOBAL_ENABLED, legacyEnabled)
        }
        if (!hasScheduler) {
            editor.putBoolean(SummaryConstants.KEY_SCHEDULER_ENABLED, legacyEnabled)
        }
        editor.apply()
    }

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun isGlobalEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(SummaryConstants.KEY_GLOBAL_ENABLED, false)
    }

    fun isSchedulerEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(SummaryConstants.KEY_SCHEDULER_ENABLED, false)
    }

    fun isGlobalGateOpen(context: Context): Boolean {
        return isOverlayPermissionGranted(context) && isGlobalEnabled(context)
    }

    fun canSchedule(context: Context): Boolean {
        return isGlobalGateOpen(context) && isSchedulerEnabled(context)
    }
}
