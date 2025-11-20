package com.micoyc.speakthat

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Quick Settings tile service for SpeakThat
 * 
 * This tile allows users to quickly toggle SpeakThat's master switch
 * from the Quick Settings panel without opening the app.
 * 
 * Features:
 * - Toggles the master switch on/off
 * - Syncs with the main app's toggle state
 * - Uses the SpeakThat logo as the tile icon
 * - Battery-efficient with minimal background processing
 * - Compatible with Android 7.0+ (API 24+)
 * 
 * The tile state reflects the current master switch status:
 * - ACTIVE: SpeakThat is enabled and reading notifications
 * - INACTIVE: SpeakThat is disabled and not reading notifications
 */
@RequiresApi(Build.VERSION_CODES.N)
class SpeakThatTileService : TileService() {
    
    companion object {
        private const val TAG = "SpeakThatTileService"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SpeakThatTileService created")
    }
    
    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "Quick Settings tile started listening")
        updateTileState()
    }
    
    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "Quick Settings tile stopped listening")
    }
    
    override fun onClick() {
        super.onClick()
        
        try {
            // Get current master switch state
            val isCurrentlyEnabled = MainActivity.isMasterSwitchEnabled(this)
            val newState = !isCurrentlyEnabled
            
            // Update the master switch state
            MasterSwitchController.setEnabled(this, newState, "QuickSettingsTile")
            
            // Update tile state immediately
            updateTileState()
            
            // Log the change for debugging
            Log.d(TAG, "Quick Settings tile toggled master switch: $isCurrentlyEnabled -> $newState")
            InAppLogger.logSettingsChange("Quick Settings Tile", isCurrentlyEnabled.toString(), newState.toString())
            InAppLogger.log("QuickSettingsTile", "Master switch ${if (newState) "enabled" else "disabled"} via Quick Settings tile")
            
            // Show user feedback
            val message = if (newState) {
                getString(R.string.quick_settings_tile_enabled_toast)
            } else {
                getString(R.string.quick_settings_tile_disabled_toast)
            }
            
            // Use a brief toast to confirm the action
            // Note: We use a short duration to minimize battery impact
            val toast = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT)
            toast.show()
            

            
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling master switch via Quick Settings tile", e)
            InAppLogger.logError("QuickSettingsTile", "Error toggling master switch: ${e.message}")
            
            // Show error feedback to user
            android.widget.Toast.makeText(
                this, 
                getString(R.string.quick_settings_tile_error_toast), 
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    /**
     * Update the Quick Settings tile state to reflect the current master switch status
     */
    private fun updateTileState() {
        try {
            val qsTile = qsTile ?: return
            
            // Get current master switch state
            val isMasterEnabled = MainActivity.isMasterSwitchEnabled(this)
            
            // Update tile state
            qsTile.state = if (isMasterEnabled) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            
            // Update tile label
            qsTile.label = getString(R.string.quick_settings_tile_label)
            
            // Update tile subtitle to show current status (API 29+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                qsTile.subtitle = if (isMasterEnabled) {
                    getString(R.string.quick_settings_tile_enabled)
                } else {
                    getString(R.string.quick_settings_tile_disabled)
                }
            }
            
            // Update tile icon (using the SpeakThat logo)
            qsTile.icon = Icon.createWithResource(
                this, 
                R.drawable.logo_speakthat
            )
            
            // Update the tile
            qsTile.updateTile()
            
            Log.d(TAG, "Quick Settings tile updated - Master switch: $isMasterEnabled")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Quick Settings tile state", e)
            InAppLogger.logError("QuickSettingsTile", "Error updating tile state: ${e.message}")
        }
    }
} 