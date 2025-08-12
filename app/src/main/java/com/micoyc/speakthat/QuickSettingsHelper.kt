package com.micoyc.speakthat

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Helper class for Quick Settings tile functionality
 * 
 * This class provides safe access to Quick Settings features
 * and handles compatibility with different Android versions.
 * 
 * Quick Settings tiles are available on Android 7.0+ (API 24+)
 * but this class provides graceful fallbacks for older versions.
 */
object QuickSettingsHelper {
    
    private const val TAG = "QuickSettingsHelper"
    
    /**
     * Check if Quick Settings tiles are supported on this device
     * 
     * @return true if Quick Settings tiles are available (Android 7.0+)
     */
    fun isQuickSettingsSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }
    
    /**
     * Check if the SpeakThat Quick Settings tile is available
     * 
     * This checks both Android version support and whether
     * the tile service is properly registered.
     * 
     * @param context Application context
     * @return true if the tile is available and functional
     */
    fun isSpeakThatTileAvailable(context: Context): Boolean {
        if (!isQuickSettingsSupported()) {
            Log.d(TAG, "Quick Settings tiles not supported on this Android version")
            return false
        }
        
        try {
            // Check if our tile service is available
            val packageManager = context.packageManager
            val tileServiceInfo = packageManager.getServiceInfo(
                android.content.ComponentName(context, SpeakThatTileService::class.java),
                0
            )
            
            // Check if the service has the required permission
            val hasPermission = tileServiceInfo.permission == "android.permission.BIND_QUICK_SETTINGS_TILE"
            
            Log.d(TAG, "SpeakThat tile service available: $hasPermission")
            return hasPermission
            
        } catch (e: Exception) {
            Log.w(TAG, "Error checking SpeakThat tile availability", e)
            return false
        }
    }
    
    /**
     * Get a user-friendly message about Quick Settings tile availability
     * 
     * @param context Application context
     * @return Localized message explaining tile availability
     */
    fun getTileAvailabilityMessage(context: Context): String {
        return when {
            !isQuickSettingsSupported() -> {
                "Quick Settings tiles require Android 7.0 or higher"
            }
            !isSpeakThatTileAvailable(context) -> {
                "SpeakThat Quick Settings tile is not available"
            }
            else -> {
                "SpeakThat Quick Settings tile is available"
            }
        }
    }
    
    /**
     * Log Quick Settings tile status for debugging
     * 
     * @param context Application context
     */
    fun logTileStatus(context: Context) {
        val isSupported = isQuickSettingsSupported()
        val isAvailable = isSpeakThatTileAvailable(context)
        
        Log.d(TAG, "Quick Settings tile status:")
        Log.d(TAG, "  Android version supported: $isSupported")
        Log.d(TAG, "  SpeakThat tile available: $isAvailable")
        Log.d(TAG, "  Android SDK: ${Build.VERSION.SDK_INT}")
        Log.d(TAG, "  Android version: ${Build.VERSION.RELEASE}")
        
        InAppLogger.log("QuickSettings", "Tile status - Supported: $isSupported, Available: $isAvailable")
    }
} 