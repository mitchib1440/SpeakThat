package com.micoyc.speakthat.rules

import android.content.Context
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.utils.WifiCapabilityChecker

/**
 * Rule evaluation engine
 * This class handles the evaluation of rules, triggers, actions, and exceptions
 */

// ============================================================================
// EVALUATION RESULTS
// ============================================================================

/**
 * Result of evaluating a single trigger, action, or exception
 */
data class EvaluationResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any> = emptyMap()
) {
    fun getLogMessage(): String {
        return "Evaluation: ${if (success) "SUCCESS" else "FAILED"} - $message"
    }
}

/**
 * Result of evaluating a complete rule
 */
data class RuleEvaluationResult(
    val ruleId: String,
    val ruleName: String,
    val triggersMet: Boolean,
    val exceptionsMet: Boolean,
    val shouldExecute: Boolean,
    val triggerResults: List<EvaluationResult>,
    val exceptionResults: List<EvaluationResult>,
    val message: String
) {
    fun getLogMessage(): String {
        return "Rule[$ruleId]: '$ruleName' - " +
               "Triggers: ${if (triggersMet) "MET" else "NOT_MET"}, " +
               "Exceptions: ${if (exceptionsMet) "MET" else "NOT_MET"}, " +
               "Execute: ${if (shouldExecute) "YES" else "NO"} - $message"
    }
}

// ============================================================================
// RULE EVALUATOR
// ============================================================================

/**
 * Main rule evaluation engine
 * Handles the evaluation of rules with triggers, actions, and exceptions
 */
class RuleEvaluator(private val context: Context) {
    
    companion object {
        private const val TAG = "RuleEvaluator"
    }
    
    /**
     * Evaluates a single rule and returns whether it should execute
     */
    fun evaluateRule(rule: Rule): RuleEvaluationResult {
        InAppLogger.logDebug(TAG, "Evaluating rule: ${rule.getLogMessage()}")
        
        // Skip disabled rules
        if (!rule.enabled) {
            InAppLogger.logDebug(TAG, "Rule '${rule.name}' is disabled, skipping evaluation")
            return RuleEvaluationResult(
                ruleId = rule.id,
                ruleName = rule.name,
                triggersMet = false,
                exceptionsMet = false,
                shouldExecute = false,
                triggerResults = emptyList(),
                exceptionResults = emptyList(),
                message = "Rule is disabled"
            )
        }
        
        // Evaluate triggers
        val triggerResults = evaluateTriggers(rule.triggers, rule.triggerLogic)
        val triggersMet = triggerResults.all { it.success }
        
        InAppLogger.logDebug(TAG, "Trigger evaluation for '${rule.name}': ${if (triggersMet) "MET" else "NOT_MET"}")
        
        // If triggers aren't met, no need to check exceptions
        if (!triggersMet) {
            return RuleEvaluationResult(
                ruleId = rule.id,
                ruleName = rule.name,
                triggersMet = false,
                exceptionsMet = false,
                shouldExecute = false,
                triggerResults = triggerResults,
                exceptionResults = emptyList(),
                message = "Triggers not met"
            )
        }
        
        // Evaluate exceptions
        val exceptionResults = evaluateExceptions(rule.exceptions, rule.exceptionLogic)
        val exceptionsMet = exceptionResults.any { it.success } // Any exception met = rule blocked
        
        InAppLogger.logDebug(TAG, "Exception evaluation for '${rule.name}': ${if (exceptionsMet) "MET (BLOCKED)" else "NOT_MET (ALLOWED)"}")
        
        // Determine if rule should execute
        val shouldExecute = triggersMet && !exceptionsMet
        
        val message = when {
            !triggersMet -> "Triggers not met"
            exceptionsMet -> "Exceptions met (rule blocked)"
            else -> "Rule should execute"
        }
        
        val result = RuleEvaluationResult(
            ruleId = rule.id,
            ruleName = rule.name,
            triggersMet = triggersMet,
            exceptionsMet = exceptionsMet,
            shouldExecute = shouldExecute,
            triggerResults = triggerResults,
            exceptionResults = exceptionResults,
            message = message
        )
        
        InAppLogger.logDebug(TAG, result.getLogMessage())
        return result
    }
    
    /**
     * Evaluates a list of triggers using the specified logic gate
     */
    private fun evaluateTriggers(triggers: List<Trigger>, logicGate: LogicGate): List<EvaluationResult> {
        if (triggers.isEmpty()) {
            InAppLogger.logDebug(TAG, "No triggers to evaluate")
            return emptyList()
        }
        
        InAppLogger.logDebug(TAG, "Evaluating ${triggers.size} triggers with logic gate: ${logicGate.displayName}")
        
        val results = triggers.map { trigger ->
            evaluateTrigger(trigger)
        }
        
        // Apply logic gate
        val finalResult = when (logicGate) {
            LogicGate.AND -> {
                val allSuccess = results.all { it.success }
                InAppLogger.logDebug(TAG, "AND logic: all triggers must succeed = $allSuccess")
                allSuccess
            }
            LogicGate.OR -> {
                val anySuccess = results.any { it.success }
                InAppLogger.logDebug(TAG, "OR logic: any trigger can succeed = $anySuccess")
                anySuccess
            }
            LogicGate.XOR -> {
                val successCount = results.count { it.success }
                val exactlyOne = successCount == 1
                InAppLogger.logDebug(TAG, "XOR logic: exactly one trigger must succeed = $exactlyOne (success count: $successCount)")
                exactlyOne
            }
        }
        
        // Update results to reflect logic gate
        return results.map { result ->
            if (finalResult) {
                result
            } else {
                EvaluationResult(
                    success = false,
                    message = "Logic gate ${logicGate.displayName} not satisfied: ${result.message}",
                    data = result.data
                )
            }
        }
    }
    
    /**
     * Evaluates a single trigger
     */
    private fun evaluateTrigger(trigger: Trigger): EvaluationResult {
        if (!trigger.enabled) {
            return EvaluationResult(
                success = false,
                message = "Trigger is disabled"
            )
        }
        
        InAppLogger.logDebug(TAG, "Evaluating trigger: ${trigger.getLogMessage()}")
        
        // Get the base evaluation result
        val baseResult = when (trigger.type) {
            TriggerType.BLUETOOTH_DEVICE -> evaluateBluetoothTrigger(trigger)
            TriggerType.SCREEN_STATE -> evaluateScreenStateTrigger(trigger)
            TriggerType.TIME_SCHEDULE -> evaluateTimeScheduleTrigger(trigger)
            TriggerType.WIFI_NETWORK -> evaluateWifiNetworkTrigger(trigger)
        }
        
        // Apply inversion if needed
        return if (trigger.inverted) {
            InAppLogger.logDebug(TAG, "Inverting trigger result: ${baseResult.success} -> ${!baseResult.success}")
            EvaluationResult(
                success = !baseResult.success,
                message = "INVERTED: ${baseResult.message}",
                data = baseResult.data
            )
        } else {
            baseResult
        }
    }
    
    /**
     * Evaluates a list of exceptions using the specified logic gate
     */
    private fun evaluateExceptions(exceptions: List<Exception>, logicGate: LogicGate): List<EvaluationResult> {
        if (exceptions.isEmpty()) {
            InAppLogger.logDebug(TAG, "No exceptions to evaluate")
            return emptyList()
        }
        
        InAppLogger.logDebug(TAG, "Evaluating ${exceptions.size} exceptions with logic gate: ${logicGate.displayName}")
        
        val results = exceptions.map { exception ->
            evaluateException(exception)
        }
        
        // Apply logic gate
        val finalResult = when (logicGate) {
            LogicGate.AND -> {
                val allSuccess = results.all { it.success }
                InAppLogger.logDebug(TAG, "AND logic: all exceptions must be met = $allSuccess")
                allSuccess
            }
            LogicGate.OR -> {
                val anySuccess = results.any { it.success }
                InAppLogger.logDebug(TAG, "OR logic: any exception can be met = $anySuccess")
                anySuccess
            }
            LogicGate.XOR -> {
                val successCount = results.count { it.success }
                val exactlyOne = successCount == 1
                InAppLogger.logDebug(TAG, "XOR logic: exactly one exception must be met = $exactlyOne (success count: $successCount)")
                exactlyOne
            }
        }
        
        // Update results to reflect logic gate
        return results.map { result ->
            if (finalResult) {
                result
            } else {
                EvaluationResult(
                    success = false,
                    message = "Logic gate ${logicGate.displayName} not satisfied: ${result.message}",
                    data = result.data
                )
            }
        }
    }
    
    /**
     * Evaluates a single exception
     */
    private fun evaluateException(exception: Exception): EvaluationResult {
        if (!exception.enabled) {
            return EvaluationResult(
                success = false,
                message = "Exception is disabled"
            )
        }
        
        InAppLogger.logDebug(TAG, "Evaluating exception: ${exception.getLogMessage()}")
        
        // Get the base evaluation result
        val baseResult = when (exception.type) {
            ExceptionType.BLUETOOTH_DEVICE -> evaluateBluetoothException(exception)
            ExceptionType.SCREEN_STATE -> evaluateScreenStateException(exception)
            ExceptionType.TIME_SCHEDULE -> evaluateTimeScheduleException(exception)
            ExceptionType.WIFI_NETWORK -> evaluateWifiNetworkException(exception)
        }
        
        // Apply inversion if needed
        return if (exception.inverted) {
            InAppLogger.logDebug(TAG, "Inverting exception result: ${baseResult.success} -> ${!baseResult.success}")
            EvaluationResult(
                success = !baseResult.success,
                message = "INVERTED: ${baseResult.message}",
                data = baseResult.data
            )
        } else {
            baseResult
        }
    }
    
    // ============================================================================
    // TRIGGER EVALUATORS
    // ============================================================================
    
    private fun evaluateBluetoothTrigger(trigger: Trigger): EvaluationResult {
        try {
            InAppLogger.logDebug(TAG, "Evaluating Bluetooth trigger: ${trigger.getLogMessage()}")
            InAppLogger.logDebug(TAG, "Trigger data: ${trigger.data}")
            
            // Step 1: Check Bluetooth availability and permissions
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            
            if (bluetoothAdapter == null) {
                InAppLogger.logDebug(TAG, "Bluetooth not available on this device")
                return EvaluationResult(
                    success = false,
                    message = "Bluetooth not available on this device"
                )
            }
            
            if (!bluetoothAdapter.isEnabled) {
                InAppLogger.logDebug(TAG, "Bluetooth is disabled")
                return EvaluationResult(
                    success = false,
                    message = "Bluetooth is disabled"
                )
            }
            
            // Step 2: Check Bluetooth permissions
            val hasPermissions = checkBluetoothPermissions()
            if (!hasPermissions) {
                InAppLogger.logDebug(TAG, "Bluetooth permissions not granted - requesting permissions")
                requestBluetoothPermissions()
                return EvaluationResult(
                    success = false,
                    message = "Bluetooth permissions required"
                )
            }
            
            // Step 3: Get required device addresses from trigger data
            val deviceAddressesData = trigger.data["device_addresses"]
            val requiredDevices = when (deviceAddressesData) {
                is Set<*> -> deviceAddressesData.mapNotNull { it as? String }.toSet()
                is List<*> -> deviceAddressesData.mapNotNull { it as? String }.toSet()
                else -> emptySet<String>()
            }
            InAppLogger.logDebug(TAG, "Required devices from trigger data: $requiredDevices")
            
            // Step 4: Handle "any device" vs "specific device" logic
            if (requiredDevices.isEmpty()) {
                InAppLogger.logDebug(TAG, "No specific devices required - checking if ANY Bluetooth device is connected")
                return evaluateAnyBluetoothDevice()
            } else {
                InAppLogger.logDebug(TAG, "Specific devices required - checking for: $requiredDevices")
                return evaluateSpecificBluetoothDevices(requiredDevices)
            }
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error evaluating Bluetooth trigger: ${e.message}")
            return EvaluationResult(
                success = false,
                message = "Bluetooth evaluation error: ${e.message}"
            )
        }
    }
    
    /**
     * Check if the app has the necessary Bluetooth permissions
     */
    private fun checkBluetoothPermissions(): Boolean {
        return try {
            val hasConnect = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasScan = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasBluetooth = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            InAppLogger.logDebug(TAG, "Bluetooth permissions - CONNECT: $hasConnect, SCAN: $hasScan, BLUETOOTH: $hasBluetooth")
            
            // For Android 12+ (API 31+), we need BLUETOOTH_CONNECT
            // For older versions, we need BLUETOOTH
            val hasRequiredPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                hasConnect
            } else {
                hasBluetooth
            }
            
            InAppLogger.logDebug(TAG, "Required Bluetooth permissions granted: $hasRequiredPermissions")
            hasRequiredPermissions
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error checking Bluetooth permissions: ${e.message}")
            false
        }
    }
    
    /**
     * Request Bluetooth permissions (this will be handled by the UI layer)
     */
    private fun requestBluetoothPermissions() {
        InAppLogger.logDebug(TAG, "Bluetooth permissions need to be requested by the UI layer")
        // Note: This method is called from the service context, so we can't directly request permissions
        // The UI layer should handle permission requests when the user configures Bluetooth triggers
    }
    
    /**
     * Evaluate if ANY Bluetooth device is connected (when no specific devices are required)
     */
    private fun evaluateAnyBluetoothDevice(): EvaluationResult {
        InAppLogger.logDebug(TAG, "=== EVALUATING ANY BLUETOOTH DEVICE ===")
        
        // Method 1: Try to get actively connected devices via BluetoothManager
        val connectedDevices = getActivelyConnectedDevices()
        val connectedAddresses = connectedDevices.map { it.address }.toSet()
        
        if (connectedAddresses.isNotEmpty()) {
            InAppLogger.logDebug(TAG, "Method 1 SUCCESS: Found ${connectedAddresses.size} actively connected devices: $connectedAddresses")
            return EvaluationResult(
                success = true,
                message = "Bluetooth device is actively connected",
                data = mapOf(
                    "detection_method" to "active_connection",
                    "connected_devices" to connectedAddresses
                )
            )
        }
        
        // Method 2: Check if any bonded device is connected via audio routing
        val anyDeviceConnected = checkAnyBondedDeviceConnected()
        if (anyDeviceConnected) {
            InAppLogger.logDebug(TAG, "Method 2 SUCCESS: Found bonded device with active audio routing")
            return EvaluationResult(
                success = true,
                message = "Bluetooth device is connected (detected via audio routing)",
                data = mapOf(
                    "detection_method" to "audio_routing_fallback"
                )
            )
        }
        
        InAppLogger.logDebug(TAG, "ALL METHODS FAILED: No Bluetooth device is actively connected (paired/bonded devices are NOT considered connected)")
        return EvaluationResult(
            success = false,
            message = "No Bluetooth device is actively connected",
            data = mapOf(
                "detection_method" to "all_methods_failed"
            )
        )
    }
    
    /**
     * Evaluate if specific Bluetooth devices are connected
     */
    private fun evaluateSpecificBluetoothDevices(requiredDevices: Set<String>): EvaluationResult {
        InAppLogger.logDebug(TAG, "=== EVALUATING SPECIFIC BLUETOOTH DEVICES ===")
        InAppLogger.logDebug(TAG, "Required devices: $requiredDevices")
        
        // Method 1: Try to get actively connected devices via BluetoothManager
        val connectedDevices = getActivelyConnectedDevices()
        val connectedAddresses = connectedDevices.map { it.address }.toSet()
        
        // Check if any required device is actively connected
        val specificDeviceConnected = requiredDevices.any { it in connectedAddresses }
        if (specificDeviceConnected) {
            val connectedRequiredDevices = requiredDevices.intersect(connectedAddresses)
            InAppLogger.logDebug(TAG, "Method 1 SUCCESS: Required device(s) actively connected: $connectedRequiredDevices")
            return EvaluationResult(
                success = true,
                message = "Required Bluetooth device is actively connected",
                data = mapOf(
                    "detection_method" to "active_connection",
                    "connected_required_devices" to connectedRequiredDevices,
                    "all_connected_devices" to connectedAddresses
                )
            )
        }
        
        // Method 2: Check if any required device is bonded and has active audio routing
        val specificDeviceConnectedViaAudio = checkSpecificBondedDeviceConnected(requiredDevices)
        if (specificDeviceConnectedViaAudio) {
            InAppLogger.logDebug(TAG, "Method 2 SUCCESS: Required device connected via audio routing")
            return EvaluationResult(
                success = true,
                message = "Required Bluetooth device is connected (detected via audio routing)",
                data = mapOf(
                    "detection_method" to "audio_routing_fallback"
                )
            )
        }
        
        InAppLogger.logDebug(TAG, "ALL METHODS FAILED: Required Bluetooth device is not connected (paired/bonded devices are NOT considered connected)")
        return EvaluationResult(
            success = false,
            message = "Required Bluetooth device is not connected",
            data = mapOf(
                "detection_method" to "all_methods_failed",
                "required_devices" to requiredDevices,
                "connected_devices" to connectedAddresses
            )
        )
    }
    
    /**
     * Get actively connected Bluetooth devices using multiple detection methods
     */
    private fun getActivelyConnectedDevices(): Set<android.bluetooth.BluetoothDevice> {
        InAppLogger.logDebug(TAG, "--- Getting actively connected devices ---")
        
        val allConnectedDevices = mutableSetOf<android.bluetooth.BluetoothDevice>()
        
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            
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
     * Check if any bonded device is connected via audio routing
     */
    private fun checkAnyBondedDeviceConnected(): Boolean {
        InAppLogger.logDebug(TAG, "--- Checking if any bonded device is connected via audio routing ---")
        
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            
            // Check various audio routing indicators
            val isBluetoothA2dpOn = audioManager.isBluetoothA2dpOn
            val isBluetoothScoOn = audioManager.isBluetoothScoOn
            val audioMode = audioManager.mode
            
            InAppLogger.logDebug(TAG, "Audio routing check - A2DP: $isBluetoothA2dpOn, SCO: $isBluetoothScoOn, Mode: $audioMode")
            
            // Consider connected if audio is being routed to Bluetooth
            val isConnected = isBluetoothA2dpOn || isBluetoothScoOn || 
                             audioMode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
                             audioMode == android.media.AudioManager.MODE_IN_CALL
            
            InAppLogger.logDebug(TAG, "Audio routing indicates Bluetooth connection: $isConnected")
            return isConnected
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error checking audio routing: ${e.message}")
            return false
        }
    }
    
    /**
     * Check if specific bonded devices are connected via audio routing
     */
    private fun checkSpecificBondedDeviceConnected(requiredDevices: Set<String>): Boolean {
        InAppLogger.logDebug(TAG, "--- Checking if specific bonded devices are connected via audio routing ---")
        
        try {
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
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
            val isBluetoothA2dpOn = audioManager.isBluetoothA2dpOn
            val isBluetoothScoOn = audioManager.isBluetoothScoOn
            val audioMode = audioManager.mode
            
            InAppLogger.logDebug(TAG, "Audio routing check - A2DP: $isBluetoothA2dpOn, SCO: $isBluetoothScoOn, Mode: $audioMode")
            
            // Consider connected if audio is being routed to Bluetooth
            val isConnected = isBluetoothA2dpOn || isBluetoothScoOn || 
                             audioMode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
                             audioMode == android.media.AudioManager.MODE_IN_CALL
            
            InAppLogger.logDebug(TAG, "Specific device audio routing check result: $isConnected")
            return isConnected
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error checking specific device audio routing: ${e.message}")
            return false
        }
    }
    
    private fun evaluateScreenStateTrigger(trigger: Trigger): EvaluationResult {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val isScreenOn = powerManager.isInteractive
            
            // Get required screen state from trigger data
            val requiredState = trigger.data["screen_state"] as? String ?: "on"
            
            val shouldBeOn = when (requiredState.lowercase()) {
                "on" -> true
                "off" -> false
                else -> true // Default to "on"
            }
            
            val success = isScreenOn == shouldBeOn
            
            InAppLogger.logDebug(TAG, "Screen state: $isScreenOn, Required: $requiredState, Success: $success")
            
            return EvaluationResult(
                success = success,
                message = if (success) "Screen is ${requiredState}" else "Screen is not ${requiredState}",
                data = mapOf(
                    "current_state" to if (isScreenOn) "on" else "off",
                    "required_state" to requiredState
                )
            )
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error evaluating screen state trigger: ${e.message}")
            return EvaluationResult(
                success = false,
                message = "Screen state evaluation error: ${e.message}"
            )
        }
    }
    
    private fun evaluateTimeScheduleTrigger(trigger: Trigger): EvaluationResult {
        try {
            val calendar = java.util.Calendar.getInstance()
            
            // Debug: Log the entire trigger data
            InAppLogger.logDebug(TAG, "TimeSchedule trigger data: ${trigger.data}")
            InAppLogger.logDebug(TAG, "TimeSchedule trigger data types: ${trigger.data.mapValues { it.value?.javaClass?.simpleName ?: "null" }}")
            
            // Get schedule data from trigger - handle both Long and Double types
            val startTimeRaw = trigger.data["start_time"]
            val endTimeRaw = trigger.data["end_time"]
            val daysOfWeek = trigger.data["days_of_week"] as? Set<Int> ?: emptySet()
            
            // Convert start_time to Long, handling both Long and Double types
            val startTime = when (startTimeRaw) {
                is Long -> startTimeRaw
                is Double -> startTimeRaw.toLong()
                is Int -> startTimeRaw.toLong()
                else -> 0L
            }
            
            // Convert end_time to Long, handling both Long and Double types
            val endTime = when (endTimeRaw) {
                is Long -> endTimeRaw
                is Double -> endTimeRaw.toLong()
                is Int -> endTimeRaw.toLong()
                else -> 0L
            }
            
            InAppLogger.logDebug(TAG, "TimeSchedule converted times: startTime=$startTime (${startTimeRaw?.javaClass?.simpleName}), endTime=$endTime (${endTimeRaw?.javaClass?.simpleName})")
            
            // Check if current day is in allowed days
            // Calendar.DAY_OF_WEEK is 1-based (Sunday=1, Saturday=7)
            // But our days_of_week set is 0-based (Sunday=0, Saturday=6)
            val currentDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1
            val dayAllowed = daysOfWeek.isEmpty() || currentDayOfWeek in daysOfWeek
            
            if (!dayAllowed) {
                InAppLogger.logDebug(TAG, "Current day ($currentDayOfWeek) not in allowed days: $daysOfWeek")
                return EvaluationResult(
                    success = false,
                    message = "Current day not in schedule"
                )
            }
            
            // Convert current time to time-of-day milliseconds (since midnight)
            val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(java.util.Calendar.MINUTE)
            val currentTimeOfDay = (currentHour * 60 * 60 * 1000L) + (currentMinute * 60 * 1000L)
            
            // Check if current time is within schedule
            val timeInRange = if (startTime <= endTime) {
                // Same day schedule (e.g., 09:00 to 17:00)
                currentTimeOfDay >= startTime && currentTimeOfDay <= endTime
            } else {
                // Overnight schedule (e.g., 22:00 to 06:00)
                currentTimeOfDay >= startTime || currentTimeOfDay <= endTime
            }
            
            InAppLogger.logDebug(TAG, "Time check: current=${currentHour}:${currentMinute.toString().padStart(2, '0')} (${currentTimeOfDay}ms), start=${startTime}ms, end=${endTime}ms, inRange=$timeInRange")
            
            return EvaluationResult(
                success = timeInRange,
                message = if (timeInRange) "Current time is within schedule" else "Current time is outside schedule",
                data = mapOf(
                    "current_time_of_day" to currentTimeOfDay,
                    "start_time" to startTime,
                    "end_time" to endTime,
                    "days_of_week" to daysOfWeek
                )
            )
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error evaluating time schedule trigger: ${e.message}")
            return EvaluationResult(
                success = false,
                message = "Time schedule evaluation error: ${e.message}"
            )
        }
    }
    

    
    private fun evaluateWifiNetworkTrigger(trigger: Trigger): EvaluationResult {
        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            
            if (!wifiManager.isWifiEnabled) {
                InAppLogger.logDebug(TAG, "WiFi is disabled")
                return EvaluationResult(
                    success = false,
                    message = "WiFi is disabled"
                )
            }
            
            // Try multiple methods to get current WiFi SSID
            var currentSSID = ""
            
            // Method 1: Direct WiFi manager
            val connectionInfo = wifiManager.connectionInfo
            val rawSSID = connectionInfo.ssid
            currentSSID = rawSSID?.removeSurrounding("\"") ?: ""
            
            InAppLogger.logDebug(TAG, "WiFi detection Method 1 - Raw SSID: '$rawSSID', Cleaned SSID: '$currentSSID'")
            
            // Method 2: ConnectivityManager fallback if Method 1 fails
            if (currentSSID.isEmpty() || currentSSID == "<unknown ssid>" || currentSSID == "0x") {
                try {
                    val activeNetwork = connectivityManager.activeNetwork
                    if (activeNetwork != null) {
                        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                        if (networkCapabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true) {
                            // Try to get SSID from network info
                            val networkInfo = connectivityManager.getNetworkInfo(activeNetwork)
                            InAppLogger.logDebug(TAG, "WiFi detection Method 2 - NetworkInfo: $networkInfo")
                            
                            // For Android 15, we might need to use a different approach
                            // Let's try to at least detect if we're connected to WiFi
                            if (networkInfo?.isConnected == true) {
                                InAppLogger.logDebug(TAG, "WiFi detection Method 2 - Connected to WiFi but SSID unknown")
                                // We know we're connected to WiFi, but can't get the SSID
                                // This is a limitation of Android 15
                            }
                        }
                    }
                } catch (e: Throwable) {
                    InAppLogger.logDebug(TAG, "WiFi detection Method 2 failed: ${e.message}")
                }
            }
            
            // Get required networks from trigger data
            // Handle both Set<String> and List<String> since JSON serialization converts Sets to Lists
            val networkSSIDsData = trigger.data["network_ssids"]
            val requiredNetworks = when (networkSSIDsData) {
                is Set<*> -> networkSSIDsData.filterIsInstance<String>().toSet()
                is List<*> -> networkSSIDsData.filterIsInstance<String>().toSet()
                else -> emptySet<String>()
            }
            
            if (requiredNetworks.isEmpty()) {
                // Check if connected to any network
                val isConnected = currentSSID.isNotEmpty() && currentSSID != "<unknown ssid>" && currentSSID != "0x"
                
                InAppLogger.logDebug(TAG, "No specific network required, connected: $isConnected (SSID: $currentSSID)")
                
                return EvaluationResult(
                    success = isConnected,
                    message = if (isConnected) "Connected to WiFi network" else "Not connected to WiFi",
                    data = mapOf("current_ssid" to currentSSID)
                )
            } else {
                // Check if we can resolve SSIDs
                val canResolveSSID = WifiCapabilityChecker.canResolveWifiSSID(context)
                
                if (canResolveSSID) {
                    // We can resolve SSIDs, so check for specific networks
                    val isConnectedToRequired = currentSSID in requiredNetworks
                    
                    InAppLogger.logDebug(TAG, "SSID resolution possible. Required networks: $requiredNetworks, Current: $currentSSID, Match: $isConnectedToRequired")
                    
                    return EvaluationResult(
                        success = isConnectedToRequired,
                        message = if (isConnectedToRequired) "Connected to required WiFi network" else "Not connected to required WiFi network",
                        data = mapOf("current_ssid" to currentSSID)
                    )
                } else {
                    // Cannot resolve SSIDs due to Android security restrictions
                    // Fallback to WiFi connection state instead of specific networks
                    val isConnectedToWiFi = currentSSID.isNotEmpty() && currentSSID != "<unknown ssid>" && currentSSID != "0x"
                    
                    InAppLogger.logDebug(TAG, "SSID resolution not possible due to Android security restrictions. Using WiFi connection state instead.")
                    InAppLogger.logDebug(TAG, "WiFi connected: $isConnectedToWiFi, Required networks: $requiredNetworks (ignored due to SSID resolution limitation)")
                    
                    return EvaluationResult(
                        success = isConnectedToWiFi,
                        message = if (isConnectedToWiFi) "Connected to WiFi (specific network unknown due to Android security)" else "Not connected to WiFi",
                        data = mapOf(
                            "required_networks" to requiredNetworks,
                            "current_ssid" to currentSSID,
                            "ssid_resolution_limited" to true,
                            "wifi_connected" to isConnectedToWiFi
                        )
                    )
                }
            }
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error evaluating WiFi network trigger: ${e.message}")
            return EvaluationResult(
                success = false,
                message = "WiFi network evaluation error: ${e.message}"
            )
        }
    }
    
    // ============================================================================
    // EXCEPTION EVALUATORS
    // ============================================================================
    
    private fun evaluateBluetoothException(exception: Exception): EvaluationResult {
        // Use the same logic as Bluetooth trigger
        return evaluateBluetoothTrigger(Trigger(
            type = TriggerType.BLUETOOTH_DEVICE,
            data = exception.data
        ))
    }
    
    private fun evaluateScreenStateException(exception: Exception): EvaluationResult {
        // Use the same logic as screen state trigger
        return evaluateScreenStateTrigger(Trigger(
            type = TriggerType.SCREEN_STATE,
            data = exception.data
        ))
    }
    
    private fun evaluateTimeScheduleException(exception: Exception): EvaluationResult {
        // Use the same logic as time schedule trigger
        return evaluateTimeScheduleTrigger(Trigger(
            type = TriggerType.TIME_SCHEDULE,
            data = exception.data
        ))
    }
    
    private fun evaluateWifiNetworkException(exception: Exception): EvaluationResult {
        // Use the same logic as WiFi network trigger
        return evaluateWifiNetworkTrigger(Trigger(
            type = TriggerType.WIFI_NETWORK,
            data = exception.data
        ))
    }
} 