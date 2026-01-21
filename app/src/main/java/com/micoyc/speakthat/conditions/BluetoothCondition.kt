package com.micoyc.speakthat.conditions

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.micoyc.speakthat.BaseCondition
import com.micoyc.speakthat.ConditionChecker
import com.micoyc.speakthat.InAppLogger

/**
 * Bluetooth device condition
 * Allows notifications only when connected to specific Bluetooth devices
 */
data class BluetoothCondition(
    override val enabled: Boolean = false,
    val allowedDevices: Set<String> = emptySet(), // Device addresses
    val requireConnected: Boolean = true
) : BaseCondition(enabled, "bluetooth") {
    
    override fun createChecker(context: Context): ConditionChecker {
        return BluetoothConditionChecker(this, context)
    }
}

/**
 * Bluetooth condition checker implementation
 */
class BluetoothConditionChecker(
    private val condition: BluetoothCondition,
    private val context: Context
) : ConditionChecker {
    
    companion object {
        private const val TAG = "BluetoothCondition"
    }
    
    override fun shouldAllowNotification(context: Context): Boolean {
        if (!condition.enabled) {
            return true // No restriction if disabled
        }
        
        if (!condition.requireConnected) {
            return true // No Bluetooth requirement
        }
        
        if (condition.allowedDevices.isEmpty()) {
            // If no devices are specified, allow notifications
            Log.d(TAG, "No Bluetooth devices specified - allowing notification")
            InAppLogger.logFilter("Bluetooth condition: No devices selected - allowing notification")
            return true
        }
        
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            
            if (bluetoothAdapter == null) {
                Log.w(TAG, "Bluetooth not available on this device")
                InAppLogger.logFilter("Bluetooth condition: Bluetooth not available")
                return false
            }
            
            if (!bluetoothAdapter.isEnabled) {
                Log.d(TAG, "Bluetooth is disabled")
                InAppLogger.logFilter("Bluetooth condition: Bluetooth disabled")
                return false
            }
            
            // Check if any of the allowed devices are connected using robust detection
            val isAllowedDeviceConnected = checkIfAllowedDevicesConnected(condition.allowedDevices)
            
            if (isAllowedDeviceConnected) {
                Log.d(TAG, "Allowed Bluetooth device is connected")
                InAppLogger.logFilter("Bluetooth condition: Allowed device connected")
                return true
            } else {
                Log.d(TAG, "No allowed Bluetooth devices are connected")
                InAppLogger.logFilter("Bluetooth condition: No allowed devices connected")
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth condition", e)
            InAppLogger.logError("BluetoothCondition", "Error checking Bluetooth: ${e.message}")
            // Fail-safe: allow notification if we can't check Bluetooth
            return true
        }
    }
    
    /**
     * Check if any of the allowed devices are connected using multiple detection methods
     */
    private fun checkIfAllowedDevicesConnected(allowedDevices: Set<String>): Boolean {
        InAppLogger.logDebug(TAG, "Checking if allowed devices are connected: $allowedDevices")
        
        // Method 1: Try to get actively connected devices via BluetoothManager
        val connectedDevices = getActivelyConnectedDevices()
        val connectedAddresses = connectedDevices.map { it.address }.toSet()
        
        // Check if any allowed device is actively connected
        val specificDeviceConnected = allowedDevices.any { it in connectedAddresses }
        if (specificDeviceConnected) {
            val connectedAllowedDevices = allowedDevices.intersect(connectedAddresses)
            InAppLogger.logDebug(TAG, "Method 1 SUCCESS: Allowed device(s) actively connected: $connectedAllowedDevices")
            return true
        }
        
        // Method 2: Check if any allowed device is bonded and has active audio routing
        val specificDeviceConnectedViaAudio = checkSpecificBondedDeviceConnected(allowedDevices)
        if (specificDeviceConnectedViaAudio) {
            InAppLogger.logDebug(TAG, "Method 2 SUCCESS: Allowed device connected via audio routing")
            return true
        }
        
        InAppLogger.logDebug(TAG, "ALL METHODS FAILED: No allowed Bluetooth devices are connected")
        return false
    }
    
    /**
     * Get actively connected Bluetooth devices using multiple detection methods
     */
    private fun getActivelyConnectedDevices(): Set<BluetoothDevice> {
        InAppLogger.logDebug(TAG, "Getting actively connected devices")
        
        val allConnectedDevices = mutableSetOf<BluetoothDevice>()
        
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            
            // Try multiple Bluetooth profiles to catch different types of devices
            val profiles = listOf(
                android.bluetooth.BluetoothProfile.A2DP,      // Audio devices (music, podcasts)
                android.bluetooth.BluetoothProfile.HEADSET    // Headset devices (calls)
            )
            
            var profilesSupported = false
            for (profile in profiles) {
                try {
                    val devices = bluetoothManager.getConnectedDevices(profile)
                    allConnectedDevices.addAll(devices)
                    profilesSupported = true
                    InAppLogger.logDebug(TAG, "Profile $profile: ${devices.map { it.address }}")
                    if (devices.isNotEmpty()) {
                        InAppLogger.logDebug(TAG, "Profile $profile devices: ${devices.map { "${it.name} (${it.address})" }}")
                    }
                } catch (e: Throwable) {
                    InAppLogger.logDebug(TAG, "Profile $profile not supported: ${e.message}")
                }
            }
            
            if (!profilesSupported) {
                InAppLogger.logDebug(TAG, "WARNING: No Bluetooth profiles are supported on this device")
                InAppLogger.logDebug(TAG, "This is a device limitation - will use fallback detection methods")
            }
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error getting connected devices via BluetoothManager: ${e.message}")
        }
        
        InAppLogger.logDebug(TAG, "Total actively connected devices found: ${allConnectedDevices.size}")
        return allConnectedDevices
    }
    
    /**
     * Check if specific bonded devices are connected via audio routing
     */
    private fun checkSpecificBondedDeviceConnected(requiredDevices: Set<String>): Boolean {
        InAppLogger.logDebug(TAG, "Checking if specific bonded devices are connected via audio routing")
        
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter
            val bondedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
            
            // Check if any required device is in the bonded list
            val matchingBondedDevices = bondedDevices.filter { device ->
                requiredDevices.contains(device.address)
            }
            
            if (matchingBondedDevices.isEmpty()) {
                InAppLogger.logDebug(TAG, "No required devices found in bonded devices list")
                return false
            }
            
            InAppLogger.logDebug(TAG, "Found ${matchingBondedDevices.size} matching bonded devices: ${matchingBondedDevices.map { "${it.name} (${it.address})" }}")
            
            // Check if audio is being routed to Bluetooth
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val hasBluetoothOutput = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                .any { device ->
                    device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        device.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        device.type == android.media.AudioDeviceInfo.TYPE_HEARING_AID
                }
            val audioMode = audioManager.mode
            
            InAppLogger.logDebug(TAG, "Audio routing check - Bluetooth output: $hasBluetoothOutput, Mode: $audioMode")
            
            // Consider connected if audio is being routed to Bluetooth
            val isConnected = hasBluetoothOutput ||
                             audioMode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
                             audioMode == android.media.AudioManager.MODE_IN_CALL
            
            InAppLogger.logDebug(TAG, "Specific device audio routing check result: $isConnected")
            return isConnected
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error checking specific device audio routing: ${e.message}")
            return false
        }
    }
    
    override fun getConditionName(): String {
        return "Bluetooth Device"
    }
    
    override fun getConditionDescription(): String {
        return if (condition.allowedDevices.isEmpty()) {
            "Only when connected to Bluetooth devices (no devices selected)"
        } else {
            "Only when connected to ${condition.allowedDevices.size} selected Bluetooth device(s)"
        }
    }
    
    override fun isEnabled(): Boolean {
        return condition.enabled
    }
    
    override fun getLogMessage(): String {
        return if (condition.allowedDevices.isEmpty()) {
            "Bluetooth condition: No devices selected"
        } else {
            "Bluetooth condition: Not connected to any of ${condition.allowedDevices.size} allowed devices"
        }
    }
} 