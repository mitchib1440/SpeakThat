package com.micoyc.speakthat.rules

import android.content.Context
import com.micoyc.speakthat.InAppLogger

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
        
        return when (trigger.type) {
            TriggerType.BLUETOOTH_DEVICE -> evaluateBluetoothTrigger(trigger)
            TriggerType.SCREEN_STATE -> evaluateScreenStateTrigger(trigger)
            TriggerType.TIME_SCHEDULE -> evaluateTimeScheduleTrigger(trigger)
            TriggerType.WIFI_NETWORK -> evaluateWifiNetworkTrigger(trigger)
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
        
        return when (exception.type) {
            ExceptionType.BLUETOOTH_DEVICE -> evaluateBluetoothException(exception)
            ExceptionType.SCREEN_STATE -> evaluateScreenStateException(exception)
            ExceptionType.TIME_SCHEDULE -> evaluateTimeScheduleException(exception)
            ExceptionType.WIFI_NETWORK -> evaluateWifiNetworkException(exception)
        }
    }
    
    // ============================================================================
    // TRIGGER EVALUATORS
    // ============================================================================
    
    private fun evaluateBluetoothTrigger(trigger: Trigger): EvaluationResult {
        try {
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            
            if (bluetoothAdapter == null) {
                InAppLogger.logDebug(TAG, "Bluetooth not available on this device")
                return EvaluationResult(
                    success = false,
                    message = "Bluetooth not available"
                )
            }
            
            if (!bluetoothAdapter.isEnabled) {
                InAppLogger.logDebug(TAG, "Bluetooth is disabled")
                return EvaluationResult(
                    success = false,
                    message = "Bluetooth is disabled"
                )
            }
            
            // Get required device addresses from trigger data
            val requiredDevices = trigger.data["device_addresses"] as? Set<String> ?: emptySet()
            
            if (requiredDevices.isEmpty()) {
                InAppLogger.logDebug(TAG, "No specific devices required, checking if any device is connected")
                // Check if any device is connected
                val connectedDevices = bluetoothAdapter.bondedDevices
                val hasConnectedDevice = connectedDevices.any { device ->
                    device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED
                }
                
                return EvaluationResult(
                    success = hasConnectedDevice,
                    message = if (hasConnectedDevice) "Bluetooth device connected" else "No Bluetooth device connected"
                )
            } else {
                // Check for specific devices
                val connectedDevices = bluetoothAdapter.bondedDevices
                val connectedAddresses = connectedDevices.map { it.address }.toSet()
                val requiredConnected = requiredDevices.any { it in connectedAddresses }
                
                InAppLogger.logDebug(TAG, "Required devices: $requiredDevices, Connected: $connectedAddresses, Match: $requiredConnected")
                
                return EvaluationResult(
                    success = requiredConnected,
                    message = if (requiredConnected) "Required Bluetooth device connected" else "Required Bluetooth device not connected",
                    data = mapOf(
                        "required_devices" to requiredDevices,
                        "connected_devices" to connectedAddresses
                    )
                )
            }
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error evaluating Bluetooth trigger: ${e.message}")
            return EvaluationResult(
                success = false,
                message = "Bluetooth evaluation error: ${e.message}"
            )
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
            val currentTime = calendar.timeInMillis
            
            // Get schedule data from trigger
            val startTime = trigger.data["start_time"] as? Long ?: 0L
            val endTime = trigger.data["end_time"] as? Long ?: 0L
            val daysOfWeek = trigger.data["days_of_week"] as? Set<Int> ?: emptySet()
            
            // Check if current day is in allowed days
            val currentDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
            val dayAllowed = daysOfWeek.isEmpty() || currentDayOfWeek in daysOfWeek
            
            if (!dayAllowed) {
                InAppLogger.logDebug(TAG, "Current day ($currentDayOfWeek) not in allowed days: $daysOfWeek")
                return EvaluationResult(
                    success = false,
                    message = "Current day not in schedule"
                )
            }
            
            // Check if current time is within schedule
            val timeInRange = if (startTime <= endTime) {
                // Same day schedule
                currentTime in startTime..endTime
            } else {
                // Overnight schedule (e.g., 22:00 to 06:00)
                currentTime >= startTime || currentTime <= endTime
            }
            
            InAppLogger.logDebug(TAG, "Time check: current=$currentTime, start=$startTime, end=$endTime, inRange=$timeInRange")
            
            return EvaluationResult(
                success = timeInRange,
                message = if (timeInRange) "Current time is within schedule" else "Current time is outside schedule",
                data = mapOf(
                    "current_time" to currentTime,
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
            
            if (!wifiManager.isWifiEnabled) {
                InAppLogger.logDebug(TAG, "WiFi is disabled")
                return EvaluationResult(
                    success = false,
                    message = "WiFi is disabled"
                )
            }
            
            val connectionInfo = wifiManager.connectionInfo
            val currentSSID = connectionInfo.ssid?.removeSurrounding("\"") ?: ""
            
            // Get required networks from trigger data
            val requiredNetworks = trigger.data["network_ssids"] as? Set<String> ?: emptySet()
            
            if (requiredNetworks.isEmpty()) {
                // Check if connected to any network
                val isConnected = currentSSID.isNotEmpty()
                
                InAppLogger.logDebug(TAG, "No specific network required, connected: $isConnected (SSID: $currentSSID)")
                
                return EvaluationResult(
                    success = isConnected,
                    message = if (isConnected) "Connected to WiFi network" else "Not connected to WiFi",
                    data = mapOf("current_ssid" to currentSSID)
                )
            } else {
                // Check for specific networks
                val isConnectedToRequired = currentSSID in requiredNetworks
                
                InAppLogger.logDebug(TAG, "Required networks: $requiredNetworks, Current: $currentSSID, Match: $isConnectedToRequired")
                
                return EvaluationResult(
                    success = isConnectedToRequired,
                    message = if (isConnectedToRequired) "Connected to required WiFi network" else "Not connected to required WiFi network",
                    data = mapOf(
                        "required_networks" to requiredNetworks,
                        "current_ssid" to currentSSID
                    )
                )
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