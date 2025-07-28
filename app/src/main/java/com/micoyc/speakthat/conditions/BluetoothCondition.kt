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
            
            // Get connected devices
            val connectedDevices = bluetoothAdapter.bondedDevices
            
            // Check if any of the allowed devices are connected
            val isAllowedDeviceConnected = connectedDevices.any { device ->
                condition.allowedDevices.contains(device.address)
            }
            
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