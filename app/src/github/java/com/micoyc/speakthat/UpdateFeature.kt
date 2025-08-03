package com.micoyc.speakthat

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * GitHub-specific version of UpdateFeature without dialog functionality
 * This file only exists in the GitHub flavor
 */
object UpdateFeature {
    
    /**
     * Check if auto-updater is enabled for this build variant
     * This is determined at compile time by the build flavor
     */
    fun isEnabled(): Boolean {
        return BuildConfig.ENABLE_AUTO_UPDATER
    }
    
    /**
     * Get the distribution channel for this build variant
     */
    fun getDistributionChannel(): String {
        return BuildConfig.DISTRIBUTION_CHANNEL
    }
    
    /**
     * Start update activity if enabled, otherwise show store message
     * 
     * This is the key method that demonstrates conditional behavior:
     * - GitHub flavor: Shows UpdateActivity
     * - Store flavor: Shows "updates via store" message
     */
    fun startUpdateActivity(context: Context, forceCheck: Boolean = false) {
        if (isEnabled()) {
            // This code only runs in the GitHub flavor
            // The UpdateActivity class exists in both flavors but is only used here
            UpdateActivity.start(context, forceCheck)
        } else {
            // This code only runs in the Store flavor
            showStoreUpdateMessage(context)
        }
    }
    
    /**
     * Check for updates if enabled
     * 
     * This method demonstrates how the same code can have different behavior:
     * - GitHub flavor: Actually checks for updates
     * - Store flavor: Does nothing (silently skips)
     */
    fun checkForUpdatesIfEnabled(context: Context) {
        if (isEnabled()) {
            // This code only runs in the GitHub flavor
            val updateManager = UpdateManager.getInstance(context)
            
            // Check if auto-update is enabled in settings
            val sharedPreferences = context.getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
            val autoUpdateEnabled = sharedPreferences.getBoolean("auto_update_enabled", true)
            
            if (!autoUpdateEnabled) {
                return
            }
            
            // Check if enough time has passed since last check
            val shouldCheck = updateManager.shouldCheckForUpdates()
            if (!shouldCheck) {
                return
            }
            
            // Check if we've already notified about the current version
            val lastCheckedVersion = sharedPreferences.getString("last_checked_version", "")
            if (lastCheckedVersion != null && updateManager.hasNotifiedAboutVersion(lastCheckedVersion)) {
                return
            }
            
            // Check installation source
            if (updateManager.isInstalledFromGooglePlay() || updateManager.isInstalledFromRepository()) {
                return
            }
            
            // Perform the update check
            GlobalScope.launch {
                try {
                    val updateInfo = updateManager.checkForUpdates()
                    if (updateInfo != null) {
                        // Show update notification (you can implement this)
                        android.util.Log.i("UpdateFeature", "Update available: ${updateInfo.versionName}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("UpdateFeature", "Update check failed: ${e.message}")
                }
            }
        } else {
            // This code only runs in the Store flavor
            // No update checking - stores handle updates themselves
        }
    }
    
    /**
     * Show message for store variant users (GitHub version - just a toast)
     * This method exists only in the GitHub flavor and should never be called
     */
    private fun showStoreUpdateMessage(context: Context) {
        // This should never be called in GitHub variant, but just in case
        Toast.makeText(
            context,
            "Updates are handled by your app store",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    /**
     * Example of a method that works the same in both flavors
     * This demonstrates automatic code sharing
     */
    fun getAppVersion(): String {
        // This code is identical in both flavors
        // Any bug fixes here automatically apply to both
        return BuildConfig.VERSION_NAME
    }
    
    /**
     * Example of a method that has different behavior per flavor
     * but uses the same source code
     */
    fun getUpdateInfo(): String {
        return if (isEnabled()) {
            "Auto-updates enabled via GitHub"
        } else {
            "Updates via app store"
        }
    }
} 