package com.micoyc.speakthat.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.micoyc.speakthat.InAppLogger

/**
 * Utility class to check WiFi capabilities and limitations
 */
object WifiCapabilityChecker {
    
    private const val TAG = "WifiCapabilityChecker"
    
    /**
     * Check if the device can resolve WiFi SSIDs
     * @param context Application context
     * @return true if permissions and system settings allow SSID resolution,
     * false if limited by Android security or system state
     */
    fun canResolveWifiSSID(context: Context): Boolean {
        try {
            val hasFineLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasFineLocation) {
                InAppLogger.logDebug(TAG, "SSID resolution blocked: missing ACCESS_FINE_LOCATION")
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasNearbyWifi = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasNearbyWifi) {
                    InAppLogger.logDebug(TAG, "SSID resolution blocked: missing NEARBY_WIFI_DEVICES")
                    return false
                }
            }

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!locationManager.isLocationEnabled) {
                InAppLogger.logDebug(TAG, "SSID resolution blocked: location disabled")
                return false
            }

            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wifiManager.isWifiEnabled) {
                InAppLogger.logDebug(TAG, "SSID resolution blocked: WiFi disabled")
                return false
            }

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            val isWifiConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            InAppLogger.logDebug(TAG, "SSID resolution prerequisites OK. WiFi connected=$isWifiConnected")

            // Permissions and system settings allow SSID resolution; actual SSID may still be transiently unavailable.
            return true
        } catch (e: Throwable) {
            InAppLogger.logDebug(TAG, "Error checking SSID resolution capability: ${e.message}")
            return false
        }
    }
}
