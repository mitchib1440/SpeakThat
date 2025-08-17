package com.micoyc.speakthat.utils

import android.content.Context
import android.net.wifi.WifiManager
import com.micoyc.speakthat.InAppLogger

/**
 * Utility class to check WiFi capabilities and limitations
 */
object WifiCapabilityChecker {
    
    private const val TAG = "WifiCapabilityChecker"
    
    /**
     * Check if the device can resolve WiFi SSIDs
     * @param context Application context
     * @return true if SSID resolution is possible, false if limited by Android security
     */
    fun canResolveWifiSSID(context: Context): Boolean {
        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wifiManager.isWifiEnabled) return false
            
            val connectionInfo = wifiManager.connectionInfo
            val ssid = connectionInfo.ssid?.removeSurrounding("\"") ?: ""
            
            // If we can get a valid SSID, we can resolve WiFi networks
            val canResolve = ssid.isNotEmpty() && ssid != "<unknown ssid>" && ssid != "0x"
            
            InAppLogger.logDebug(TAG, "SSID resolution check - Raw SSID: '$ssid', Can resolve: $canResolve")
            
            return canResolve
        } catch (e: Throwable) {
            InAppLogger.logDebug(TAG, "Error checking SSID resolution capability: ${e.message}")
            return false
        }
    }
}
