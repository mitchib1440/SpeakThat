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
 * Play flavor mirrors the Store behavior (no in-app updater; handled by store).
 */
object UpdateFeature {
    
    fun isEnabled(): Boolean {
        return BuildConfig.ENABLE_AUTO_UPDATER
    }
    
    fun getDistributionChannel(): String {
        return BuildConfig.DISTRIBUTION_CHANNEL
    }
    
    fun startUpdateActivity(context: Context, forceCheck: Boolean = false) {
        if (isEnabled()) {
            UpdateActivity.start(context, forceCheck)
        } else {
            showStoreUpdateMessage(context)
        }
    }

    @JvmStatic
    fun onAppStart(application: Application) {
        // No-op in Play build
    }
    
    fun checkForUpdatesIfEnabled(context: Context) {
        if (isEnabled()) {
            val updateManager = UpdateManager.getInstance(context)
            val sharedPreferences = context.getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
            val autoUpdateEnabled = sharedPreferences.getBoolean("auto_update_enabled", true)
            if (!autoUpdateEnabled) return
            val shouldCheck = updateManager.shouldCheckForUpdates()
            if (!shouldCheck) return
            val lastCheckedVersion = sharedPreferences.getString("last_checked_version", "")
            if (lastCheckedVersion != null && updateManager.hasNotifiedAboutVersion(lastCheckedVersion)) return
            if (updateManager.isInstalledFromGooglePlay() || updateManager.isInstalledFromRepository()) return
            
            GlobalScope.launch {
                try {
                    val updateInfo = updateManager.checkForUpdates()
                    if (updateInfo != null) {
                        android.util.Log.i("UpdateFeature", "Update available: ${updateInfo.versionName}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("UpdateFeature", "Update check failed: ${e.message}")
                }
            }
        } else {
            // Play/Store rely on store-managed updates; no in-app check.
        }
    }

    fun getCachedUpdateInfo(context: Context): UpdateManager.UpdateInfo? {
        return null
    }

    @JvmStatic
    fun onAutoUpdatePreferenceChanged(context: Context) {
        // No-op in Play build
    }
    
    private fun showStoreUpdateMessage(context: Context) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_store_update, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.btnVisitGitHub).setOnClickListener {
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

        dialog.show()
    }
    
    fun getAppVersion(): String {
        return BuildConfig.VERSION_NAME
    }
    
    fun getUpdateInfo(): String {
        return if (isEnabled()) {
            "Auto-updates enabled via GitHub"
        } else {
            "Updates via app store"
        }
    }
}

