package com.micoyc.speakthat.conditions

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import com.micoyc.speakthat.BaseCondition
import com.micoyc.speakthat.ConditionChecker
import com.micoyc.speakthat.InAppLogger

/**
 * WiFi network condition
 * Allows notifications only when connected to specific WiFi networks
 */
data class WifiNetworkCondition(
    override val enabled: Boolean = false,
    val allowedNetworks: Set<String> = emptySet(), // SSIDs
    val requireConnected: Boolean = true
) : BaseCondition(enabled, "wifi_network") {
    
    override fun createChecker(context: Context): ConditionChecker {
        return WifiNetworkConditionChecker(this, context)
    }
}

/**
 * WiFi network condition checker implementation
 */
class WifiNetworkConditionChecker(
    private val condition: WifiNetworkCondition,
    private val context: Context
) : ConditionChecker {
    
    companion object {
        private const val TAG = "WifiNetworkCondition"
    }
    
    override fun shouldAllowNotification(context: Context): Boolean {
        if (!condition.enabled) {
            return true // No restriction if disabled
        }
        
        if (!condition.requireConnected) {
            return true // No WiFi requirement
        }
        
        if (condition.allowedNetworks.isEmpty()) {
            // If no networks are specified, allow notifications
            Log.d(TAG, "No WiFi networks specified - allowing notification")
            return true
        }
        
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            
            // Check if WiFi is enabled
            if (!wifiManager.isWifiEnabled) {
                Log.d(TAG, "WiFi is disabled")
                InAppLogger.logFilter("WiFi condition: WiFi disabled")
                return false
            }
            
            // Get current network
            val network = connectivityManager.activeNetwork
            if (network == null) {
                Log.d(TAG, "No active network")
                InAppLogger.logFilter("WiFi condition: No active network")
                return false
            }
            
            // Check if current network is WiFi
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            if (networkCapabilities == null || !networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                Log.d(TAG, "Not connected to WiFi")
                InAppLogger.logFilter("WiFi condition: Not connected to WiFi")
                return false
            }
            
            // Get current WiFi SSID
            val wifiInfo = wifiManager.connectionInfo
            val currentSSID = wifiInfo.ssid
            
            // Remove quotes from SSID if present
            val cleanSSID = currentSSID?.removeSurrounding("\"") ?: ""
            
            // Check if current SSID is in allowed list
            val isAllowedNetwork = condition.allowedNetworks.any { allowedSSID ->
                allowedSSID.equals(cleanSSID, ignoreCase = true)
            }
            
            if (isAllowedNetwork) {
                Log.d(TAG, "Connected to allowed WiFi network: $cleanSSID")
                InAppLogger.logFilter("WiFi condition: Connected to allowed network: $cleanSSID")
                return true
            } else {
                Log.d(TAG, "Connected to non-allowed WiFi network: $cleanSSID")
                InAppLogger.logFilter("WiFi condition: Connected to non-allowed network: $cleanSSID")
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WiFi network condition", e)
            InAppLogger.logError("WifiNetworkCondition", "Error checking WiFi: ${e.message}")
            // Fail-safe: allow notification if we can't check WiFi
            return true
        }
    }
    
    override fun getConditionName(): String {
        return "WiFi Network"
    }
    
    override fun getConditionDescription(): String {
        return if (condition.allowedNetworks.isEmpty()) {
            "Only when connected to WiFi networks (no networks selected)"
        } else {
            "Only when connected to ${condition.allowedNetworks.size} selected WiFi network(s)"
        }
    }
    
    override fun isEnabled(): Boolean {
        return condition.enabled
    }
    
    override fun getLogMessage(): String {
        return if (condition.allowedNetworks.isEmpty()) {
            "WiFi condition: No networks selected"
        } else {
            "WiFi condition: Not connected to any of ${condition.allowedNetworks.size} allowed networks"
        }
    }
} 