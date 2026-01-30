package com.micoyc.speakthat.rules

import android.content.Context
import com.micoyc.speakthat.AccessibilityUtils
import com.micoyc.speakthat.ForegroundAppTracker
import com.micoyc.speakthat.InAppLogger
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager

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
    val rawTriggerResults: List<EvaluationResult>,
    val rawExceptionResults: List<EvaluationResult>,
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
        
        // Throttling for repetitive Bluetooth logs
        private var lastBluetoothLogTime: Long = 0L
        private const val BLUETOOTH_LOG_THROTTLE_MS = 30000L // Only log detailed Bluetooth info every 30 seconds
        
        private fun shouldLogBluetoothDetails(): Boolean {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBluetoothLogTime > BLUETOOTH_LOG_THROTTLE_MS) {
                lastBluetoothLogTime = currentTime
                return true
            }
            return false
        }
    }
    
    /**
     * Evaluates a single rule and returns whether it should execute
     */
    fun evaluateRule(rule: Rule, notificationContext: NotificationContext): RuleEvaluationResult {
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
                rawTriggerResults = emptyList(),
                rawExceptionResults = emptyList(),
                message = "Rule is disabled"
            )
        }
        
        // Evaluate triggers
        val triggerEvaluation = evaluateTriggers(rule.triggers, rule.triggerLogic, notificationContext)
        val triggerResults = triggerEvaluation.gatedResults
        val triggersMet = triggerEvaluation.finalResult
        
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
                rawTriggerResults = triggerEvaluation.rawResults,
                rawExceptionResults = emptyList(),
                message = "Triggers not met"
            )
        }
        
        // Evaluate exceptions
        val exceptionEvaluation = evaluateExceptions(rule.exceptions, rule.exceptionLogic, notificationContext)
        val exceptionResults = exceptionEvaluation.gatedResults
        val exceptionsMet = exceptionEvaluation.finalResult // Any exception met = rule blocked
        
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
            rawTriggerResults = triggerEvaluation.rawResults,
            rawExceptionResults = exceptionEvaluation.rawResults,
            message = message
        )
        
        InAppLogger.logDebug(TAG, result.getLogMessage())
        return result
    }
    
    /**
     * Evaluates a list of triggers using the specified logic gate
     */
    private fun evaluateTriggers(
        triggers: List<Trigger>,
        logicGate: LogicGate,
        notificationContext: NotificationContext
    ): LogicGateEvaluation {
        if (triggers.isEmpty()) {
            InAppLogger.logDebug(TAG, "No triggers to evaluate")
            return LogicGateEvaluation(emptyList(), emptyList(), false)
        }
        
        InAppLogger.logDebug(TAG, "Evaluating ${triggers.size} triggers with logic gate: ${logicGate.displayName}")
        
        val rawResults = triggers.map { trigger ->
            evaluateTrigger(trigger, notificationContext)
        }
        
        // Apply logic gate
        val finalResult = when (logicGate) {
            LogicGate.AND -> {
                val allSuccess = rawResults.all { it.success }
                InAppLogger.logDebug(TAG, "AND logic: all triggers must succeed = $allSuccess")
                allSuccess
            }
            LogicGate.OR -> {
                val anySuccess = rawResults.any { it.success }
                InAppLogger.logDebug(TAG, "OR logic: any trigger can succeed = $anySuccess")
                anySuccess
            }
            LogicGate.XOR -> {
                val successCount = rawResults.count { it.success }
                val exactlyOne = successCount == 1
                InAppLogger.logDebug(TAG, "XOR logic: exactly one trigger must succeed = $exactlyOne (success count: $successCount)")
                exactlyOne
            }
        }
        
        // Update results to reflect logic gate
        val gatedResults = rawResults.map { result ->
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
        return LogicGateEvaluation(rawResults, gatedResults, finalResult)
    }
    
    /**
     * Evaluates a single trigger
     */
    private fun evaluateTrigger(trigger: Trigger, notificationContext: NotificationContext): EvaluationResult {
        if (!trigger.enabled) {
            return EvaluationResult(
                success = false,
                message = "Trigger is disabled"
            )
        }
        
        InAppLogger.logDebug(TAG, "Evaluating trigger: ${trigger.getLogMessage()}")
        
        // Get the base evaluation result
        val baseResult = when (trigger.type) {
            TriggerType.BATTERY_PERCENTAGE -> evaluateBatteryPercentageTrigger(trigger)
            TriggerType.CHARGING_STATUS -> evaluateChargingStatusTrigger(trigger)
            TriggerType.DEVICE_UNLOCKED -> evaluateDeviceUnlockedTrigger(trigger)
            TriggerType.NOTIFICATION_CONTAINS -> evaluateNotificationContainsTrigger(trigger, notificationContext)
            TriggerType.NOTIFICATION_FROM -> evaluateNotificationFromTrigger(trigger, notificationContext)
            TriggerType.FOREGROUND_APP -> evaluateForegroundAppTrigger(trigger)
            TriggerType.SCREEN_ORIENTATION -> evaluateScreenOrientationTrigger(trigger)
            TriggerType.BLUETOOTH_DEVICE -> evaluateBluetoothTrigger(trigger)
            TriggerType.WIRED_HEADPHONES -> evaluateWiredHeadphonesTrigger(trigger)
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
    private fun evaluateExceptions(
        exceptions: List<Exception>,
        logicGate: LogicGate,
        notificationContext: NotificationContext
    ): LogicGateEvaluation {
        if (exceptions.isEmpty()) {
            InAppLogger.logDebug(TAG, "No exceptions to evaluate")
            return LogicGateEvaluation(emptyList(), emptyList(), false)
        }
        
        InAppLogger.logDebug(TAG, "Evaluating ${exceptions.size} exceptions with logic gate: ${logicGate.displayName}")
        
        val rawResults = exceptions.map { exception ->
            evaluateException(exception, notificationContext)
        }
        
        // Apply logic gate
        val finalResult = when (logicGate) {
            LogicGate.AND -> {
                val allSuccess = rawResults.all { it.success }
                InAppLogger.logDebug(TAG, "AND logic: all exceptions must be met = $allSuccess")
                allSuccess
            }
            LogicGate.OR -> {
                val anySuccess = rawResults.any { it.success }
                InAppLogger.logDebug(TAG, "OR logic: any exception can be met = $anySuccess")
                anySuccess
            }
            LogicGate.XOR -> {
                val successCount = rawResults.count { it.success }
                val exactlyOne = successCount == 1
                InAppLogger.logDebug(TAG, "XOR logic: exactly one exception must be met = $exactlyOne (success count: $successCount)")
                exactlyOne
            }
        }
        
        // Update results to reflect logic gate
        val gatedResults = rawResults.map { result ->
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
        return LogicGateEvaluation(rawResults, gatedResults, finalResult)
    }
    
    /**
     * Evaluates a single exception
     */
    private fun evaluateException(exception: Exception, notificationContext: NotificationContext): EvaluationResult {
        if (!exception.enabled) {
            return EvaluationResult(
                success = false,
                message = "Exception is disabled"
            )
        }
        
        InAppLogger.logDebug(TAG, "Evaluating exception: ${exception.getLogMessage()}")
        
        // Get the base evaluation result
        val baseResult = when (exception.type) {
            ExceptionType.BATTERY_PERCENTAGE -> evaluateBatteryPercentageException(exception)
            ExceptionType.CHARGING_STATUS -> evaluateChargingStatusException(exception)
            ExceptionType.DEVICE_UNLOCKED -> evaluateDeviceUnlockedException(exception)
            ExceptionType.NOTIFICATION_CONTAINS -> evaluateNotificationContainsException(exception, notificationContext)
            ExceptionType.NOTIFICATION_FROM -> evaluateNotificationFromException(exception, notificationContext)
            ExceptionType.FOREGROUND_APP -> evaluateForegroundAppException(exception)
            ExceptionType.SCREEN_ORIENTATION -> evaluateScreenOrientationException(exception)
            ExceptionType.BLUETOOTH_DEVICE -> evaluateBluetoothException(exception)
            ExceptionType.WIRED_HEADPHONES -> evaluateWiredHeadphonesException(exception)
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
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter
            
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
            if (shouldLogBluetoothDetails()) {
                InAppLogger.logDebug(TAG, "Required devices from trigger data: $requiredDevices")
            }
            
            // Step 4: Handle "any device" vs "specific device" logic
            if (requiredDevices.isEmpty()) {
                if (shouldLogBluetoothDetails()) {
                    InAppLogger.logDebug(TAG, "No specific devices required - checking if ANY Bluetooth device is connected")
                }
                return evaluateAnyBluetoothDevice()
            } else {
                if (shouldLogBluetoothDetails()) {
                    InAppLogger.logDebug(TAG, "Specific devices required - checking for: $requiredDevices")
                }
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

    private fun evaluateWiredHeadphonesTrigger(trigger: Trigger): EvaluationResult {
        return try {
            InAppLogger.logDebug(TAG, "Evaluating wired headphones trigger: ${trigger.getLogMessage()}")
            InAppLogger.logDebug(TAG, "Trigger data: ${trigger.data}")

            val requiredState = (trigger.data["connection_state"] as? String)?.lowercase() ?: "disconnected"
            val shouldBeConnected = requiredState == "connected"

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

            // Only outputs: this matches what users mean by "headphones connected"
            val outputDevices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)

            val matchingOutputs = outputDevices.filter { device ->
                when (device.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_USB_HEADSET
                        -> true
                    else -> false
                }
            }

            val hasWiredHeadphones = matchingOutputs.isNotEmpty()

            // Optional: more helpful logs
            if (matchingOutputs.isNotEmpty()) {
                matchingOutputs.forEach { d ->
                    InAppLogger.logDebug(
                        TAG,
                        "Wired output device: type=${d.type}, name=${d.productName}, isSink=${d.isSink}, isSource=${d.isSource}"
                    )
                }
            }

            InAppLogger.logDebug(TAG, "Wired headphones check - Required state: $requiredState, Connected: $hasWiredHeadphones")
            InAppLogger.logDebug(TAG, "Output devices: ${outputDevices.map { "type=${it.type}, name=${it.productName}" }}")
            InAppLogger.logDebug(TAG, "Matching wired output devices: ${matchingOutputs.size}")

            val success = (hasWiredHeadphones == shouldBeConnected)

            EvaluationResult(
                success = success,
                message = if (success) {
                    "Wired headphones are $requiredState"
                } else {
                    "Wired headphones are not $requiredState"
                },
                data = mapOf(
                    "connection_state" to requiredState,
                    "is_connected" to hasWiredHeadphones,
                    "matching_device_types" to matchingOutputs.map { it.type }
                )
            )
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error evaluating wired headphones trigger: ${e.message}")
            EvaluationResult(
                success = false,
                message = "Wired headphones evaluation error: ${e.message}"
            )
        }
    }

    
    private fun evaluateBatteryPercentageTrigger(trigger: Trigger): EvaluationResult {
        try {
            val batteryInfo = getBatteryInfo()
            if (batteryInfo == null) {
                return EvaluationResult(
                    success = false,
                    message = "Battery info unavailable"
                )
            }

            val mode = trigger.data["mode"] as? String ?: "above"
            val thresholdRaw = trigger.data["percentage"]
            val threshold = when (thresholdRaw) {
                is Int -> thresholdRaw
                is Long -> thresholdRaw.toInt()
                is Double -> thresholdRaw.toInt()
                is Float -> thresholdRaw.toInt()
                is Number -> thresholdRaw.toInt()
                is String -> thresholdRaw.toIntOrNull()
                else -> null
            } ?: 0

            val isAbove = batteryInfo.percentage >= threshold
            val success = if (mode == "below") !isAbove else isAbove

            return EvaluationResult(
                success = success,
                message = if (success) {
                    "Battery ${if (mode == "below") "below" else "above"} $threshold%"
                } else {
                    "Battery not ${if (mode == "below") "below" else "above"} $threshold%"
                },
                data = mapOf(
                    "battery_percentage" to batteryInfo.percentage,
                    "threshold" to threshold,
                    "mode" to mode
                )
            )
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error evaluating battery percentage trigger: ${e.message}")
            return EvaluationResult(
                success = false,
                message = "Battery percentage evaluation error: ${e.message}"
            )
        }
    }

    private fun evaluateChargingStatusTrigger(trigger: Trigger): EvaluationResult {
        try {
            val batteryInfo = getBatteryInfo()
            if (batteryInfo == null) {
                return EvaluationResult(
                    success = false,
                    message = "Battery info unavailable"
                )
            }

            val status = trigger.data["status"] as? String ?: "charging"
            val shouldBeCharging = status != "discharging"
            val success = batteryInfo.isCharging == shouldBeCharging

            return EvaluationResult(
                success = success,
                message = if (success) {
                    "Battery is ${if (shouldBeCharging) "charging" else "discharging"}"
                } else {
                    "Battery is not ${if (shouldBeCharging) "charging" else "discharging"}"
                },
                data = mapOf(
                    "battery_percentage" to batteryInfo.percentage,
                    "is_charging" to batteryInfo.isCharging,
                    "status" to status
                )
            )
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error evaluating charging status trigger: ${e.message}")
            return EvaluationResult(
                success = false,
                message = "Charging status evaluation error: ${e.message}"
            )
        }
    }

    private fun evaluateDeviceUnlockedTrigger(trigger: Trigger): EvaluationResult {
        try {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            val isLocked = keyguardManager.isDeviceLocked
            val mode = trigger.data["mode"] as? String ?: "unlocked"
            val shouldBeUnlocked = mode != "locked"
            val success = (!isLocked) == shouldBeUnlocked

            return EvaluationResult(
                success = success,
                message = if (success) {
                    "Device is ${if (shouldBeUnlocked) "unlocked" else "locked"}"
                } else {
                    "Device is not ${if (shouldBeUnlocked) "unlocked" else "locked"}"
                },
                data = mapOf(
                    "is_locked" to isLocked,
                    "mode" to mode
                )
            )
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error evaluating device unlocked trigger: ${e.message}")
            return EvaluationResult(
                success = false,
                message = "Device unlocked evaluation error: ${e.message}"
            )
        }
    }

    private fun evaluateScreenOrientationTrigger(trigger: Trigger): EvaluationResult {
        try {
            val mode = trigger.data["mode"] as? String ?: "portrait"
            val orientation = context.resources.configuration.orientation
            val isPortrait = orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
            val shouldBePortrait = mode != "landscape"
            val success = isPortrait == shouldBePortrait

            return EvaluationResult(
                success = success,
                message = if (success) {
                    "Screen is ${if (shouldBePortrait) "portrait" else "landscape"}"
                } else {
                    "Screen is not ${if (shouldBePortrait) "portrait" else "landscape"}"
                },
                data = mapOf(
                    "orientation" to orientation,
                    "mode" to mode
                )
            )
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error evaluating screen orientation trigger: ${e.message}")
            return EvaluationResult(
                success = false,
                message = "Screen orientation evaluation error: ${e.message}"
            )
        }
    }

    private fun evaluateNotificationContainsTrigger(
        trigger: Trigger,
        notificationContext: NotificationContext
    ): EvaluationResult {
        try {
            val phrase = trigger.data["phrase"] as? String ?: ""
            val caseSensitive = trigger.data["case_sensitive"] as? Boolean ?: false

            if (phrase.isBlank()) {
                return EvaluationResult(
                    success = false,
                    message = "Notification contains: phrase is empty"
                )
            }

            val haystack = buildNotificationSearchText(notificationContext)
            if (haystack.isBlank()) {
                return EvaluationResult(
                    success = false,
                    message = "Notification contains: no text available"
                )
            }

            val success = if (caseSensitive) {
                haystack.contains(phrase)
            } else {
                haystack.lowercase().contains(phrase.lowercase())
            }

            return EvaluationResult(
                success = success,
                message = if (success) "Phrase found in notification" else "Phrase not found in notification",
                data = mapOf(
                    "phrase" to phrase,
                    "case_sensitive" to caseSensitive
                )
            )
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error evaluating notification contains trigger: ${e.message}")
            return EvaluationResult(
                success = false,
                message = "Notification contains evaluation error: ${e.message}"
            )
        }
    }

    private fun evaluateNotificationFromTrigger(
        trigger: Trigger,
        notificationContext: NotificationContext
    ): EvaluationResult {
        InAppLogger.logDebug(TAG, "Evaluating Notification From trigger: ${trigger.getLogMessage()}")
        val packagesData = trigger.data["app_packages"]
        val packages = when (packagesData) {
            is Set<*> -> packagesData.filterIsInstance<String>()
            is List<*> -> packagesData.filterIsInstance<String>()
            else -> emptyList()
        }

        if (packages.isEmpty()) {
            InAppLogger.logDebug(TAG, "Notification From trigger has no selected packages")
            return EvaluationResult(false, "No notification apps selected")
        }

        val incomingPackage = notificationContext.packageName
        val isMatch = packages.any { it.equals(incomingPackage, ignoreCase = true) }
        val message = if (isMatch) {
            "Notification from matched app: $incomingPackage"
        } else {
            "Notification from app does not match: $incomingPackage"
        }

        return EvaluationResult(
            isMatch,
            message,
            mapOf<String, Any>(
                "notification_package" to incomingPackage,
                "selected_packages" to packages
            )
        )
    }

    private fun evaluateForegroundAppTrigger(trigger: Trigger): EvaluationResult {
        InAppLogger.logDebug(TAG, "Evaluating Foreground App trigger: ${trigger.getLogMessage()}")
        val packagesData = trigger.data["app_packages"]
        val packages = when (packagesData) {
            is Set<*> -> packagesData.filterIsInstance<String>()
            is List<*> -> packagesData.filterIsInstance<String>()
            else -> emptyList()
        }

        if (packages.isEmpty()) {
            InAppLogger.logDebug(TAG, "Foreground app trigger has no selected packages")
            return EvaluationResult(false, "No foreground apps selected")
        }

        if (!AccessibilityUtils.isAccessibilityServiceEnabled(context)) {
            InAppLogger.logDebug(TAG, "Accessibility service not enabled for foreground app detection")
            return EvaluationResult(false, "Accessibility service not enabled for foreground app detection")
        }

        val currentPackage = ForegroundAppTracker.getCurrentPackage()
        val effectivePackage = ForegroundAppTracker.getEffectivePackage(15000L)
        if (effectivePackage.isNullOrBlank()) {
            InAppLogger.logDebug(TAG, "Foreground app package is unknown (no accessibility events yet)")
            return EvaluationResult(false, "Foreground app unknown")
        }

        val lastUpdatedAt = ForegroundAppTracker.getLastUpdatedAt()
        val ageMs = System.currentTimeMillis() - lastUpdatedAt
        InAppLogger.logDebug(
            TAG,
            "Foreground app detected: current=$currentPackage effective=$effectivePackage (age=${ageMs}ms)"
        )

        val isMatch = packages.any { it.equals(effectivePackage, ignoreCase = true) }
        val message = if (isMatch) {
            "Foreground app matches: $effectivePackage"
        } else {
            "Foreground app does not match: $effectivePackage"
        }
        return EvaluationResult(
            isMatch,
            message,
            mapOf<String, Any>(
                "current_package" to (currentPackage ?: ""),
                "effective_package" to effectivePackage,
                "foreground_age_ms" to ageMs
            )
        )
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
        
        InAppLogger.logDebug(TAG, "ALL METHODS FAILED: No Bluetooth device is actively connected")
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
        if (shouldLogBluetoothDetails()) {
            InAppLogger.logDebug(TAG, "=== EVALUATING SPECIFIC BLUETOOTH DEVICES ===")
            InAppLogger.logDebug(TAG, "Required devices: $requiredDevices")
        }
        
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
        
        InAppLogger.logDebug(TAG, "ALL METHODS FAILED: Required Bluetooth device is not connected")
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
                    if (shouldLogBluetoothDetails()) {
                        InAppLogger.logDebug(TAG, "Profile $profile not supported: ${e.message}")
                    }
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
            val audioMode = audioManager.mode
            val isCallOrCommMode = isCallOrCommunicationMode(audioMode)
            val hasBluetoothOutput = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                .any { device ->
                    device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        device.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        device.type == android.media.AudioDeviceInfo.TYPE_HEARING_AID
                }
            val hasAudioRoute = hasBluetoothOutput
            
            InAppLogger.logDebug(
                TAG,
                "Audio routing check - Bluetooth output: $hasBluetoothOutput, Mode: $audioMode, Call/Comm mode: $isCallOrCommMode"
            )
            
            // Conservative fallback: require A2DP, or SCO while in a call/comm mode.
            if (!hasAudioRoute) {
                InAppLogger.logDebug(TAG, "Conservative fallback: no audio route to Bluetooth detected (treating as disconnected)")
                return false
            }
            
            InAppLogger.logDebug(TAG, "Conservative fallback: audio route to Bluetooth detected")
            return true
            
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
            val audioMode = audioManager.mode
            val isCallOrCommMode = isCallOrCommunicationMode(audioMode)
            val hasBluetoothOutput = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                .any { device ->
                    device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        device.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        device.type == android.media.AudioDeviceInfo.TYPE_HEARING_AID
                }
            val hasAudioRoute = hasBluetoothOutput
            
            InAppLogger.logDebug(
                TAG,
                "Audio routing check - Bluetooth output: $hasBluetoothOutput, Mode: $audioMode, Call/Comm mode: $isCallOrCommMode"
            )
            
            // Conservative fallback: require A2DP, or SCO while in a call/comm mode.
            if (!hasAudioRoute) {
                InAppLogger.logDebug(TAG, "Conservative fallback: audio route to required device not confirmed (treating as disconnected)")
                return false
            }
            
            InAppLogger.logDebug(TAG, "Specific device audio routing check result (conservative): $hasAudioRoute")
            return true
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error checking specific device audio routing: ${e.message}")
            return false
        }
    }
    
    /**
     * Helper to detect whether the current audio mode is indicative of call/communication.
     * Avoids treating stray SCO flags in MODE_NORMAL as a valid Bluetooth connection.
     */
    private fun isCallOrCommunicationMode(mode: Int): Boolean {
        return mode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
               mode == android.media.AudioManager.MODE_IN_CALL ||
               mode == android.media.AudioManager.MODE_CALL_SCREENING
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
            val daysOfWeek = normalizeDaysOfWeek(trigger.data["days_of_week"])
            
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

    /**
     * Normalizes the days_of_week payload that comes back from JSON (Lists) or in-memory (Sets)
     * into the 0-based Set<Int> that the evaluator expects.
     */
    private fun normalizeDaysOfWeek(daysRaw: Any?): Set<Int> {
        if (daysRaw == null) {
            return emptySet()
        }

        fun convert(value: Any?): Int? {
            return when (value) {
                is Int -> value
                is Long -> value.toInt()
                is Double -> value.toInt()
                is Float -> value.toInt()
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        }

        val normalized = when (daysRaw) {
            is Set<*> -> daysRaw.mapNotNull { convert(it) }
            is List<*> -> daysRaw.mapNotNull { convert(it) }
            is Array<*> -> daysRaw.mapNotNull { convert(it) }
            else -> emptyList()
        }.toSet()

        InAppLogger.logDebug(TAG, "normalizeDaysOfWeek -> raw=$daysRaw (${daysRaw.javaClass?.simpleName}) normalized=$normalized")

        return normalized
    }
    

    
    private fun evaluateWifiNetworkTrigger(trigger: Trigger): EvaluationResult {
        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            val isWifiConnected = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            if (!wifiManager.isWifiEnabled) {
                InAppLogger.logDebug(TAG, "WiFi is disabled")
                return EvaluationResult(
                    success = false,
                    message = "WiFi is disabled"
                )
            }

            val wifiInfoFromTransport = networkCapabilities?.transportInfo as? WifiInfo
            val rawSSID = wifiInfoFromTransport?.ssid ?: run {
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo?.ssid
            }
            val currentSSID = rawSSID?.removeSurrounding("\"") ?: ""
            val isSsidKnown = currentSSID.isNotBlank() &&
                !currentSSID.equals("<unknown ssid>", ignoreCase = true) &&
                currentSSID != "0x"

            InAppLogger.logDebug(
                TAG,
                "WiFi detection - Connected=$isWifiConnected, Raw SSID='$rawSSID', Cleaned SSID='$currentSSID', SSID known=$isSsidKnown"
            )
            
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
                InAppLogger.logDebug(TAG, "No specific network required, WiFi connected: $isWifiConnected (SSID: $currentSSID)")
                
                return EvaluationResult(
                    success = isWifiConnected,
                    message = if (isWifiConnected) "Connected to WiFi network" else "Not connected to WiFi",
                    data = mapOf(
                        "current_ssid" to currentSSID,
                        "ssid_known" to isSsidKnown,
                        "wifi_connected" to isWifiConnected
                    )
                )
            } else {
                if (isSsidKnown) {
                    // We can resolve SSIDs, so check for specific networks
                    val isConnectedToRequired = currentSSID in requiredNetworks
                    
                    InAppLogger.logDebug(TAG, "SSID resolution possible. Required networks: $requiredNetworks, Current: $currentSSID, Match: $isConnectedToRequired")
                    
                    return EvaluationResult(
                        success = isConnectedToRequired,
                        message = if (isConnectedToRequired) "Connected to required WiFi network" else "Not connected to required WiFi network",
                        data = mapOf(
                            "current_ssid" to currentSSID,
                            "ssid_known" to true,
                            "wifi_connected" to isWifiConnected
                        )
                    )
                } else {
                    InAppLogger.logDebug(TAG, "SSID unavailable; cannot verify required networks. WiFi connected: $isWifiConnected")

                    return EvaluationResult(
                        success = false,
                        message = if (isWifiConnected) "Connected to WiFi but SSID unavailable" else "Not connected to WiFi",
                        data = mapOf(
                            "required_networks" to requiredNetworks,
                            "current_ssid" to currentSSID,
                            "ssid_known" to false,
                            "wifi_connected" to isWifiConnected
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
    
    private fun evaluateWiredHeadphonesException(exception: Exception): EvaluationResult {
        // Use the same logic as wired headphones trigger
        return evaluateWiredHeadphonesTrigger(Trigger(
            type = TriggerType.WIRED_HEADPHONES,
            data = exception.data
        ))
    }
    
    private fun evaluateBatteryPercentageException(exception: Exception): EvaluationResult {
        return evaluateBatteryPercentageTrigger(Trigger(
            type = TriggerType.BATTERY_PERCENTAGE,
            data = exception.data
        ))
    }

    private fun evaluateChargingStatusException(exception: Exception): EvaluationResult {
        return evaluateChargingStatusTrigger(Trigger(
            type = TriggerType.CHARGING_STATUS,
            data = exception.data
        ))
    }

    private fun evaluateDeviceUnlockedException(exception: Exception): EvaluationResult {
        return evaluateDeviceUnlockedTrigger(Trigger(
            type = TriggerType.DEVICE_UNLOCKED,
            data = exception.data
        ))
    }

    private fun evaluateScreenOrientationException(exception: Exception): EvaluationResult {
        return evaluateScreenOrientationTrigger(Trigger(
            type = TriggerType.SCREEN_ORIENTATION,
            data = exception.data
        ))
    }

    private fun evaluateNotificationContainsException(
        exception: Exception,
        notificationContext: NotificationContext
    ): EvaluationResult {
        return evaluateNotificationContainsTrigger(Trigger(
            type = TriggerType.NOTIFICATION_CONTAINS,
            data = exception.data
        ), notificationContext)
    }

    private fun evaluateNotificationFromException(
        exception: Exception,
        notificationContext: NotificationContext
    ): EvaluationResult {
        return evaluateNotificationFromTrigger(Trigger(
            type = TriggerType.NOTIFICATION_FROM,
            data = exception.data
        ), notificationContext)
    }

    private fun evaluateForegroundAppException(exception: Exception): EvaluationResult {
        InAppLogger.logDebug(TAG, "Evaluating Foreground App exception: ${exception.getLogMessage()}")
        val packagesData = exception.data["app_packages"]
        val packages = when (packagesData) {
            is Set<*> -> packagesData.filterIsInstance<String>()
            is List<*> -> packagesData.filterIsInstance<String>()
            else -> emptyList()
        }

        if (packages.isEmpty()) {
            InAppLogger.logDebug(TAG, "Foreground app exception has no selected packages")
            return EvaluationResult(false, "No foreground apps selected")
        }

        if (!AccessibilityUtils.isAccessibilityServiceEnabled(context)) {
            InAppLogger.logDebug(TAG, "Accessibility service not enabled for foreground app detection")
            return EvaluationResult(false, "Accessibility service not enabled for foreground app detection")
        }

        val currentPackage = ForegroundAppTracker.getCurrentPackage()
        val effectivePackage = ForegroundAppTracker.getEffectivePackage(15000L)
        if (effectivePackage.isNullOrBlank()) {
            InAppLogger.logDebug(TAG, "Foreground app package is unknown (no accessibility events yet)")
            return EvaluationResult(false, "Foreground app unknown")
        }

        val lastUpdatedAt = ForegroundAppTracker.getLastUpdatedAt()
        val ageMs = System.currentTimeMillis() - lastUpdatedAt
        InAppLogger.logDebug(
            TAG,
            "Foreground app detected: current=$currentPackage effective=$effectivePackage (age=${ageMs}ms)"
        )

        val isMatch = packages.any { it.equals(effectivePackage, ignoreCase = true) }
        val message = if (isMatch) {
            "Foreground app matches: $effectivePackage"
        } else {
            "Foreground app does not match: $effectivePackage"
        }
        return EvaluationResult(
            isMatch,
            message,
            mapOf<String, Any>(
                "current_package" to (currentPackage ?: ""),
                "effective_package" to effectivePackage,
                "foreground_age_ms" to ageMs
            )
        )
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

    private data class BatteryInfo(
        val percentage: Int,
        val isCharging: Boolean
    )

    private fun getBatteryInfo(): BatteryInfo? {
        val intent = context.registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        ) ?: return null

        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            return null
        }

        val percentage = ((level / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
        val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
            status == android.os.BatteryManager.BATTERY_STATUS_FULL

        return BatteryInfo(percentage, isCharging)
    }

    private fun buildNotificationSearchText(notificationContext: NotificationContext): String {
        val parts = listOf(
            notificationContext.title,
            notificationContext.text,
            notificationContext.bigText,
            notificationContext.ticker
        )
        return parts.joinToString(separator = " ") { it?.toString().orEmpty() }.trim()
    }

    private data class LogicGateEvaluation(
        val rawResults: List<EvaluationResult>,
        val gatedResults: List<EvaluationResult>,
        val finalResult: Boolean
    )
} 