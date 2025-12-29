package com.micoyc.speakthat

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Store-specific version of UpdateFeature with dialog functionality
 * This file only exists in the Store flavor
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
    
    @JvmStatic
    fun onAppStart(application: Application) {
        // No-op in store build
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

    fun getCachedUpdateInfo(context: Context): UpdateManager.UpdateInfo? {
        return null
    }

    @JvmStatic
    fun onAutoUpdatePreferenceChanged(context: Context) {
        // No-op in store build
    }
    
    /**
     * Show dialog for store variant users with helpful instructions
     * This method exists only in the Store flavor
     */
    private fun showStoreUpdateMessage(context: Context) {
        // Create dialog with custom layout
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_store_update, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Set up button click listeners
        dialogView.findViewById<MaterialButton>(R.id.btnVisitGitHub).setOnClickListener {
            // Open GitHub releases page
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mitchib1440/SpeakThat/releases"))
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Unable to open GitHub", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnDismiss).setOnClickListener {
            dialog.dismiss()
        }

        // Show the dialog
        dialog.show()
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