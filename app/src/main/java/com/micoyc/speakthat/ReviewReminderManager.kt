package com.micoyc.speakthat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.micoyc.speakthat.InAppLogger

/**
 * Review Reminder Manager for Google Play Store installations
 * 
 * This class handles showing review reminders to users who installed the app from the Play Store.
 * It only works in the 'store' build variant and respects user preferences.
 * 
 * Features:
 * - Only shows for Play Store installations
 * - Tracks usage milestones to determine when to show
 * - Allows users to dismiss permanently
 * - Provides direct link to Play Store review page
 * - Respects user privacy and preferences
 */
class ReviewReminderManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ReviewReminderManager"
        private const val PREFS_NAME = "ReviewReminderPrefs"
        private const val KEY_REMINDER_SHOWN = "reminder_shown"
        private const val KEY_REMINDER_DISMISSED = "reminder_dismissed"
        private const val KEY_APP_SESSIONS = "app_sessions"
        private const val KEY_NOTIFICATIONS_READ = "notifications_read"
        private const val KEY_LAST_REMINDER_VERSION = "last_reminder_version"
        
        // Milestones for showing the reminder
        private const val MIN_APP_SESSIONS = 3
        private const val MIN_NOTIFICATIONS_READ = 10
        
        // Use WeakReference to prevent memory leaks
        private var instanceRef: java.lang.ref.WeakReference<ReviewReminderManager>? = null
        
        fun getInstance(context: Context): ReviewReminderManager {
            val instance = instanceRef?.get()
            if (instance != null) {
                return instance
            }
            
            val newInstance = ReviewReminderManager(context.applicationContext)
            instanceRef = java.lang.ref.WeakReference(newInstance)
            return newInstance
        }
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Check if review reminder should be shown
     * @return true if reminder should be shown, false otherwise
     */
    fun shouldShowReminder(): Boolean {
        // Only show in store build variant
        if (BuildConfig.DISTRIBUTION_CHANNEL != "store") {
            Log.d(TAG, "Not showing reminder - not store build variant")
            return false
        }
        
        // Check if already shown or dismissed
        if (prefs.getBoolean(KEY_REMINDER_SHOWN, false) || 
            prefs.getBoolean(KEY_REMINDER_DISMISSED, false)) {
            Log.d(TAG, "Not showing reminder - already shown or dismissed")
            return false
        }
        
        // Check if installed from Play Store
        if (!isInstalledFromPlayStore()) {
            Log.d(TAG, "Not showing reminder - not installed from Play Store")
            return false
        }
        
        // Check usage milestones
        val appSessions = prefs.getInt(KEY_APP_SESSIONS, 0)
        val notificationsRead = prefs.getInt(KEY_NOTIFICATIONS_READ, 0)
        
        val shouldShow = appSessions >= MIN_APP_SESSIONS || notificationsRead >= MIN_NOTIFICATIONS_READ
        
        Log.d(TAG, "Review reminder check - sessions: $appSessions, notifications: $notificationsRead, should show: $shouldShow")
        InAppLogger.log("ReviewReminder", "Reminder check - sessions: $appSessions, notifications: $notificationsRead, should show: $shouldShow")
        
        return shouldShow
    }
    
    /**
     * Show the review reminder dialog
     * @param activityContext The Activity context needed for showing the AlertDialog
     */
    fun showReminderDialog(activityContext: android.app.Activity) {
        if (!shouldShowReminder()) {
            return
        }
        
        Log.i(TAG, "Showing review reminder dialog")
        InAppLogger.logUserAction("Review reminder dialog shown")
        
        AlertDialog.Builder(activityContext)
            .setTitle(activityContext.getString(R.string.review_reminder_title))
            .setMessage(activityContext.getString(R.string.review_reminder_message))
            .setPositiveButton(activityContext.getString(R.string.review_reminder_rate_now)) { _, _ ->
                openPlayStoreReview()
                markReminderShown()
            }
            .setNegativeButton(activityContext.getString(R.string.review_reminder_later)) { _, _ ->
                // Just dismiss for now, will show again later
                Log.d(TAG, "User chose 'Later' for review reminder")
                InAppLogger.logUserAction("Review reminder - chose later")
            }
            .setNeutralButton(activityContext.getString(R.string.review_reminder_never)) { _, _ ->
                markReminderDismissed()
                Log.d(TAG, "User chose 'Never' for review reminder")
                InAppLogger.logUserAction("Review reminder - chose never")
            }
            .setCancelable(true)
            .setOnCancelListener {
                Log.d(TAG, "Review reminder dialog cancelled")
                InAppLogger.logUserAction("Review reminder - cancelled")
            }
            .show()
    }
    
    /**
     * Open Play Store review page
     */
    private fun openPlayStoreReview() {
        try {
            val packageName = context.packageName
            val playStoreUri = Uri.parse("market://details?id=$packageName")
            val intent = Intent(Intent.ACTION_VIEW, playStoreUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Check if Play Store is available
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.i(TAG, "Opened Play Store review page")
                InAppLogger.logUserAction("Opened Play Store review page")
            } else {
                // Fallback to web browser
                val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                Log.i(TAG, "Opened Play Store review page in browser")
                InAppLogger.logUserAction("Opened Play Store review page in browser")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Play Store review page", e)
            InAppLogger.logError("ReviewReminder", "Error opening Play Store: ${e.message}")
        }
    }
    
    /**
     * Mark that the reminder has been shown
     */
    private fun markReminderShown() {
        prefs.edit()
            .putBoolean(KEY_REMINDER_SHOWN, true)
            .putString(KEY_LAST_REMINDER_VERSION, getCurrentVersion())
            .apply()
        
        Log.d(TAG, "Review reminder marked as shown")
    }
    
    /**
     * Mark that the user has dismissed the reminder permanently
     */
    private fun markReminderDismissed() {
        prefs.edit()
            .putBoolean(KEY_REMINDER_DISMISSED, true)
            .apply()
        
        Log.d(TAG, "Review reminder marked as dismissed permanently")
    }
    
    /**
     * Increment app session count
     */
    fun incrementAppSession() {
        val currentSessions = prefs.getInt(KEY_APP_SESSIONS, 0)
        prefs.edit().putInt(KEY_APP_SESSIONS, currentSessions + 1).apply()
        
        Log.d(TAG, "App session incremented: ${currentSessions + 1}")
    }
    
    /**
     * Increment notifications read count
     */
    fun incrementNotificationsRead() {
        val currentCount = prefs.getInt(KEY_NOTIFICATIONS_READ, 0)
        prefs.edit().putInt(KEY_NOTIFICATIONS_READ, currentCount + 1).apply()
        
        Log.d(TAG, "Notifications read incremented: ${currentCount + 1}")
    }
    
    /**
     * Check if app was installed from Play Store
     * Uses the same logic as UpdateManager
     */
    private fun isInstalledFromPlayStore(): Boolean {
        return try {
            val installerPackage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            
            val isGooglePlay = installerPackage == "com.android.vending"
            
            Log.d(TAG, "Play Store detection - installer: $installerPackage, isGooglePlay: $isGooglePlay")
            return isGooglePlay
            
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting Play Store installation", e)
            false
        }
    }
    
    /**
     * Get current app version
     */
    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    /**
     * Reset reminder state (for testing purposes)
     */
    fun resetReminderState() {
        prefs.edit()
            .remove(KEY_REMINDER_SHOWN)
            .remove(KEY_REMINDER_DISMISSED)
            .remove(KEY_APP_SESSIONS)
            .remove(KEY_NOTIFICATIONS_READ)
            .remove(KEY_LAST_REMINDER_VERSION)
            .apply()
        
        Log.d(TAG, "Review reminder state reset")
        InAppLogger.log("ReviewReminder", "Reminder state reset for testing")
    }
    
    /**
     * Get reminder statistics for debugging
     */
    fun getReminderStats(): Map<String, Any> {
        return mapOf(
            "build_variant" to BuildConfig.DISTRIBUTION_CHANNEL,
            "is_play_store_installation" to isInstalledFromPlayStore(),
            "app_sessions" to prefs.getInt(KEY_APP_SESSIONS, 0),
            "notifications_read" to prefs.getInt(KEY_NOTIFICATIONS_READ, 0),
            "reminder_shown" to prefs.getBoolean(KEY_REMINDER_SHOWN, false),
            "reminder_dismissed" to prefs.getBoolean(KEY_REMINDER_DISMISSED, false),
            "last_reminder_version" to (prefs.getString(KEY_LAST_REMINDER_VERSION, "none") ?: "none"),
            "should_show_reminder" to shouldShowReminder()
        )
    }
}
