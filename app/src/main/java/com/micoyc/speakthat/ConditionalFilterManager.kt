package com.micoyc.speakthat

import android.content.Context
import android.util.Log
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.conditions.BluetoothCondition
import com.micoyc.speakthat.conditions.ScreenStateCondition
import com.micoyc.speakthat.conditions.TimeScheduleCondition
import com.micoyc.speakthat.conditions.WifiNetworkCondition

/**
 * Core interface for all condition types
 * Each condition type must implement this interface
 */
interface ConditionChecker {
    fun shouldAllowNotification(context: Context): Boolean
    fun getConditionName(): String
    fun getConditionDescription(): String
    fun isEnabled(): Boolean
    fun getLogMessage(): String
}

/**
 * Base data class for all conditions
 * Provides common structure for all condition types
 */
abstract class BaseCondition(
    open val enabled: Boolean = false,
    open val conditionType: String
) {
    abstract fun createChecker(context: Context): ConditionChecker
}

/**
 * Result of condition evaluation for logging and debugging
 */
data class ConditionResult(
    val allowed: Boolean,
    val reason: String,
    val conditionType: String
)

/**
 * Central manager for all conditional filtering
 * Coordinates all condition types and provides fail-safe behavior
 */
class ConditionalFilterManager(private val context: Context) {
    private val sharedPreferences = context.getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "ConditionalFilter"
        
        // SharedPreferences keys
        private const val KEY_CONDITIONAL_FILTERING_ENABLED = "conditional_filtering_enabled"
        private const val KEY_BLUETOOTH_CONDITION_ENABLED = "bluetooth_condition_enabled"
        private const val KEY_SCREEN_STATE_CONDITION_ENABLED = "screen_state_condition_enabled"
        private const val KEY_TIME_SCHEDULE_CONDITION_ENABLED = "time_schedule_condition_enabled"
        private const val KEY_WIFI_NETWORK_CONDITION_ENABLED = "wifi_network_condition_enabled"
    }
    
    // Master toggle for entire system
    private val isConditionalFilteringEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_CONDITIONAL_FILTERING_ENABLED, false)
    
    // Individual condition toggles
    private val isBluetoothConditionEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_BLUETOOTH_CONDITION_ENABLED, false)
    
    private val isScreenStateConditionEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_SCREEN_STATE_CONDITION_ENABLED, false)
    
    private val isTimeScheduleConditionEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_TIME_SCHEDULE_CONDITION_ENABLED, false)
    
    private val isWifiNetworkConditionEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_WIFI_NETWORK_CONDITION_ENABLED, false)
    
    /**
     * Main entry point for conditional filtering
     * Returns whether the notification should be allowed based on all enabled conditions
     */
    fun shouldAllowNotification(): ConditionResult {
        // SAFETY: If master toggle is off, allow everything
        if (!isConditionalFilteringEnabled) {
            return ConditionResult(true, "Conditional filtering disabled", "master_toggle")
        }
        
        try {
            // Check each enabled condition
            val conditions = getEnabledConditions()
            
            if (conditions.isEmpty()) {
                return ConditionResult(true, "No conditions enabled", "no_conditions")
            }
            
            for (condition in conditions) {
                val checker = condition.createChecker(context)
                if (!checker.shouldAllowNotification(context)) {
                    val result = ConditionResult(false, checker.getLogMessage(), condition.conditionType)
                    Log.d(TAG, "Condition blocked: ${result.reason}")
                    InAppLogger.logFilter("Conditional filtering blocked: ${result.reason}")
                    return result
                }
            }
            
            val result = ConditionResult(true, "All conditions passed", "all_conditions")
            Log.d(TAG, "All conditions passed")
            InAppLogger.logFilter("Conditional filtering: All conditions passed")
            return result
            
        } catch (e: Exception) {
            // CRITICAL SAFETY: Any exception defaults to allowing
            Log.e(TAG, "Exception in conditional filtering", e)
            InAppLogger.logError("ConditionalFilter", "Exception: ${e.message} - allowing notification")
            return ConditionResult(true, "Exception occurred - allowing as safety measure", "exception")
        }
    }
    
    /**
     * Get all currently enabled conditions
     */
    private fun getEnabledConditions(): List<BaseCondition> {
        val conditions = mutableListOf<BaseCondition>()
        
        if (isBluetoothConditionEnabled) {
            try {
                conditions.add(loadBluetoothCondition())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Bluetooth condition", e)
                InAppLogger.logError("ConditionalFilter", "Failed to load Bluetooth condition: ${e.message}")
            }
        }
        
        if (isScreenStateConditionEnabled) {
            try {
                conditions.add(loadScreenStateCondition())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load screen state condition", e)
                InAppLogger.logError("ConditionalFilter", "Failed to load screen state condition: ${e.message}")
            }
        }
        
        if (isTimeScheduleConditionEnabled) {
            try {
                conditions.add(loadTimeScheduleCondition())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load time schedule condition", e)
                InAppLogger.logError("ConditionalFilter", "Failed to load time schedule condition: ${e.message}")
            }
        }
        
        if (isWifiNetworkConditionEnabled) {
            try {
                conditions.add(loadWifiNetworkCondition())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load WiFi network condition", e)
                InAppLogger.logError("ConditionalFilter", "Failed to load WiFi network condition: ${e.message}")
            }
        }
        
        return conditions
    }
    
    /**
     * Load Bluetooth condition from SharedPreferences
     */
    private fun loadBluetoothCondition(): BluetoothCondition {
        val allowedDevices = sharedPreferences.getStringSet("bluetooth_allowed_devices", emptySet()) ?: emptySet()
        val requireConnected = sharedPreferences.getBoolean("bluetooth_require_connected", true)
        return BluetoothCondition(isBluetoothConditionEnabled, allowedDevices, requireConnected)
    }
    
    /**
     * Load screen state condition from SharedPreferences
     */
    private fun loadScreenStateCondition(): ScreenStateCondition {
        val onlyWhenScreenOff = sharedPreferences.getBoolean("screen_only_when_off", true)
        return ScreenStateCondition(isScreenStateConditionEnabled, onlyWhenScreenOff)
    }
    
    /**
     * Load time schedule condition from SharedPreferences
     */
    private fun loadTimeScheduleCondition(): TimeScheduleCondition {
        val startHour = sharedPreferences.getInt("time_start_hour", 0)
        val startMinute = sharedPreferences.getInt("time_start_minute", 0)
        val endHour = sharedPreferences.getInt("time_end_hour", 23)
        val endMinute = sharedPreferences.getInt("time_end_minute", 59)
        val daysOfWeek = sharedPreferences.getStringSet("time_days_of_week", setOf("0","1","2","3","4","5","6"))?.map { it.toInt() }?.toSet() ?: setOf(0,1,2,3,4,5,6)
        
        return TimeScheduleCondition(
            enabled = isTimeScheduleConditionEnabled,
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            daysOfWeek = daysOfWeek
        )
    }
    
    /**
     * Load WiFi network condition from SharedPreferences
     */
    private fun loadWifiNetworkCondition(): WifiNetworkCondition {
        val allowedNetworks = sharedPreferences.getStringSet("wifi_allowed_networks", emptySet()) ?: emptySet()
        val requireConnected = sharedPreferences.getBoolean("wifi_require_connected", true)
        return WifiNetworkCondition(isWifiNetworkConditionEnabled, allowedNetworks, requireConnected)
    }
    
    /**
     * Check if any conditions are currently enabled
     */
    fun hasEnabledConditions(): Boolean {
        return isConditionalFilteringEnabled && (
            isBluetoothConditionEnabled ||
            isScreenStateConditionEnabled ||
            isTimeScheduleConditionEnabled ||
            isWifiNetworkConditionEnabled
        )
    }
    
    /**
     * Get count of enabled conditions
     */
    fun getEnabledConditionCount(): Int {
        if (!isConditionalFilteringEnabled) return 0
        
        var count = 0
        if (isBluetoothConditionEnabled) count++
        if (isScreenStateConditionEnabled) count++
        if (isTimeScheduleConditionEnabled) count++
        if (isWifiNetworkConditionEnabled) count++
        return count
    }
} 