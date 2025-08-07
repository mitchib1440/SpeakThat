package com.micoyc.speakthat.rules

import com.micoyc.speakthat.InAppLogger

/**
 * Core data structures for the rule system
 * This file contains all the data classes and enums needed to represent rules
 */

// ============================================================================
// ENUMS
// ============================================================================

/**
 * Logic gates for combining multiple triggers or exceptions
 */
enum class LogicGate(val displayName: String, val symbol: String) {
    AND("AND", "∧"),
    OR("OR", "∨"), 
    XOR("XOR", "⊕");
    
    companion object {
        fun fromDisplayName(name: String): LogicGate {
            return values().find { it.displayName == name } ?: AND
        }
    }
}

/**
 * Types of triggers that can activate a rule
 */
enum class TriggerType(val displayName: String, val description: String) {
    BLUETOOTH_DEVICE("Bluetooth Device Connected", "When a specific Bluetooth device is connected"),
    SCREEN_STATE("Screen State (On/Off)", "When the screen is on or off"),
    TIME_SCHEDULE("Time Schedule", "During specific time periods"),
    WIFI_NETWORK("WiFi Network Connected", "When connected to a specific WiFi network");
    
    companion object {
        fun fromDisplayName(name: String): TriggerType {
            return values().find { it.displayName == name } ?: BLUETOOTH_DEVICE
        }
    }
}

/**
 * Types of actions that can be performed when a rule is triggered
 */
enum class ActionType(val displayName: String, val description: String) {
    DISABLE_SPEAKTHAT("Skip this notification", "Don't read this notification aloud"),
    ENABLE_APP_FILTER("Enable Specific App Filter", "Enable filtering for a specific app"),
    DISABLE_APP_FILTER("Disable Specific App Filter", "Disable filtering for a specific app"),
    CHANGE_VOICE_SETTINGS("Change Voice Settings", "Modify voice parameters");
    
    companion object {
        fun fromDisplayName(name: String): ActionType {
            return values().find { it.displayName == name } ?: DISABLE_SPEAKTHAT
        }
    }
}

/**
 * Types of exceptions that can override a rule
 */
enum class ExceptionType(val displayName: String, val description: String) {
    BLUETOOTH_DEVICE("Bluetooth Device Connected", "When a specific Bluetooth device is connected"),
    SCREEN_STATE("Screen State (On/Off)", "When the screen is on or off"),
    TIME_SCHEDULE("Time Schedule", "During specific time periods"),
    WIFI_NETWORK("WiFi Network Connected", "When connected to a specific WiFi network");
    
    companion object {
        fun fromDisplayName(name: String): ExceptionType {
            return values().find { it.displayName == name } ?: BLUETOOTH_DEVICE
        }
    }
}

// ============================================================================
// DATA CLASSES
// ============================================================================

/**
 * Represents a single trigger condition
 */
data class Trigger(
    val id: String = generateId(),
    val type: TriggerType,
    val enabled: Boolean = true,
    val inverted: Boolean = false, // NEW: Whether this trigger should be inverted
    val data: Map<String, Any> = emptyMap(), // Flexible data storage for different trigger types
    val description: String = ""
) {
    companion object {
        private fun generateId(): String = "trigger_${System.currentTimeMillis()}_${(0..999).random()}"
        
        fun fromJson(json: String): Trigger? {
            return try {
                com.google.gson.Gson().fromJson(json, Trigger::class.java)
            } catch (e: Throwable) {
                null
            }
        }
    }
    
    fun getLogMessage(): String {
        val inversionText = if (inverted) " (INVERTED)" else ""
        return "Trigger[$id]: ${type.displayName}$inversionText - ${if (enabled) "ENABLED" else "DISABLED"} - $description"
    }
    
    fun toJson(): String {
        return com.google.gson.Gson().toJson(this)
    }
}

/**
 * Represents a single action to be performed
 */
data class Action(
    val id: String = generateId(),
    val type: ActionType,
    val enabled: Boolean = true,
    val data: Map<String, Any> = emptyMap(), // Flexible data storage for different action types
    val description: String = ""
) {
    companion object {
        private fun generateId(): String = "action_${System.currentTimeMillis()}_${(0..999).random()}"
        
        fun fromJson(json: String): Action? {
            return try {
                com.google.gson.Gson().fromJson(json, Action::class.java)
            } catch (e: Throwable) {
                null
            }
        }
    }
    
    fun getLogMessage(): String {
        return "Action[$id]: ${type.displayName} - ${if (enabled) "ENABLED" else "DISABLED"} - $description"
    }
    
    fun toJson(): String {
        return com.google.gson.Gson().toJson(this)
    }
}

/**
 * Represents a single exception condition
 */
data class Exception(
    val id: String = generateId(),
    val type: ExceptionType,
    val enabled: Boolean = true,
    val inverted: Boolean = false, // NEW: Whether this exception should be inverted
    val data: Map<String, Any> = emptyMap(), // Flexible data storage for different exception types
    val description: String = ""
) {
    companion object {
        private fun generateId(): String = "exception_${System.currentTimeMillis()}_${(0..999).random()}"

        fun fromJson(json: String): Exception? {
            return try {
                com.google.gson.Gson().fromJson(json, Exception::class.java)
            } catch (e: Throwable) {
                null
            }
        }
    }

    fun getLogMessage(): String {
        val inversionText = if (inverted) " (INVERTED)" else ""
        return "Exception[$id]: ${type.displayName}$inversionText - ${if (enabled) "ENABLED" else "DISABLED"} - $description"
    }

    fun toJson(): String {
        return com.google.gson.Gson().toJson(this)
    }
}

/**
 * Represents a complete rule with triggers, actions, and exceptions
 */
data class Rule(
    val id: String = generateId(),
    val name: String,
    val enabled: Boolean = true,
    val triggers: List<Trigger> = emptyList(),
    val actions: List<Action> = emptyList(),
    val exceptions: List<Exception> = emptyList(),
    val triggerLogic: LogicGate = LogicGate.AND,
    val exceptionLogic: LogicGate = LogicGate.AND,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
) {
    companion object {
        private fun generateId(): String = "rule_${System.currentTimeMillis()}_${(0..999).random()}"
        
        fun fromJson(json: String): Rule? {
            return try {
                com.google.gson.Gson().fromJson(json, Rule::class.java)
            } catch (e: Throwable) {
                null
            }
        }
    }
    
    fun getLogMessage(): String {
        return "Rule[$id]: '$name' - ${if (enabled) "ENABLED" else "DISABLED"} - " +
               "${triggers.size} triggers (${triggerLogic.displayName}), " +
               "${actions.size} actions, " +
               "${exceptions.size} exceptions (${exceptionLogic.displayName})"
    }
    
    fun getSummary(): String {
        val triggerSummary = if (triggers.isEmpty()) "No triggers" else "${triggers.size} trigger(s)"
        val actionSummary = if (actions.isEmpty()) "No actions" else "${actions.size} action(s)"
        val exceptionSummary = if (exceptions.isEmpty()) "No exceptions" else "${exceptions.size} exception(s)"
        
        return "$triggerSummary → $actionSummary (exceptions: $exceptionSummary)"
    }
    
    /**
     * Generates a natural language description of the rule
     * Format: "When <trigger>, <rule name> will <action> unless <exception>"
     */
    fun getNaturalLanguageDescription(context: android.content.Context): String {
        val triggerDesc = getTriggerDescription(context)
        val actionDesc = getActionDescription(context)
        val exceptionDesc = getExceptionDescription(context)
        
        return when {
            exceptions.isEmpty() -> {
                context.getString(
                    com.micoyc.speakthat.R.string.rule_format_when_trigger_will_action,
                    triggerDesc,
                    "this rule",
                    actionDesc
                )
            }
            exceptions.size == 1 -> {
                context.getString(
                    com.micoyc.speakthat.R.string.rule_format_when_trigger_will_action_unless_exception,
                    triggerDesc,
                    "this rule",
                    actionDesc,
                    exceptionDesc
                )
            }
            else -> {
                context.getString(
                    com.micoyc.speakthat.R.string.rule_format_when_trigger_will_action_unless_exceptions,
                    triggerDesc,
                    "this rule",
                    actionDesc,
                    exceptionDesc
                )
            }
        }
    }
    
    private fun getTriggerDescription(context: android.content.Context): String {
        if (triggers.isEmpty()) {
            return context.getString(com.micoyc.speakthat.R.string.rule_trigger_multiple)
        }
        
        if (triggers.size == 1) {
            return getSingleTriggerDescription(context, triggers[0])
        }
        
        // Multiple triggers - combine with logic gate
        val triggerDescriptions = triggers.map { getSingleTriggerDescription(context, it) }
        return combineDescriptions(context, triggerDescriptions, triggerLogic)
    }
    
    private fun getSingleTriggerDescription(context: android.content.Context, trigger: Trigger): String {
        if (!trigger.enabled) return ""
        
        return when (trigger.type) {
            TriggerType.BLUETOOTH_DEVICE -> {
                val deviceAddresses = trigger.data["device_addresses"] as? Set<String> ?: emptySet()
                val deviceName = if (deviceAddresses.isEmpty()) "any Bluetooth device" else "Bluetooth device"
                if (trigger.inverted) {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_bluetooth_disconnected)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_bluetooth_connected)
                }
            }
            TriggerType.SCREEN_STATE -> {
                val screenState = trigger.data["screen_state"] as? String ?: "on"
                val screenOn = screenState == "on"
                if (screenOn xor trigger.inverted) {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_screen_on)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_screen_off)
                }
            }
            TriggerType.TIME_SCHEDULE -> {
                // Handle different number types that might be stored
                val startTimeRaw = trigger.data["start_time"]
                val endTimeRaw = trigger.data["end_time"]
                
                val startTimeMillis = when (startTimeRaw) {
                    is Long -> startTimeRaw
                    is Double -> startTimeRaw.toLong()
                    is Int -> startTimeRaw.toLong()
                    else -> 0L
                }
                
                val endTimeMillis = when (endTimeRaw) {
                    is Long -> endTimeRaw
                    is Double -> endTimeRaw.toLong()
                    is Int -> endTimeRaw.toLong()
                    else -> 0L
                }
                
                // Fix time conversion logic - handle case where time is 0 (00:00)
                val startHour = (startTimeMillis / (60 * 60 * 1000)).toInt()
                val startMinute = ((startTimeMillis % (60 * 60 * 1000)) / (60 * 1000)).toInt()
                val endHour = (endTimeMillis / (60 * 60 * 1000)).toInt()
                val endMinute = ((endTimeMillis % (60 * 60 * 1000)) / (60 * 1000)).toInt()
                
                val startTime = String.format("%02d:%02d", startHour, startMinute)
                val endTime = String.format("%02d:%02d", endHour, endMinute)
                
                context.getString(
                    com.micoyc.speakthat.R.string.rule_trigger_time_between,
                    startTime,
                    endTime
                )
            }
            TriggerType.WIFI_NETWORK -> {
                val networkSSIDs = trigger.data["network_ssids"] as? Set<String> ?: emptySet()
                val networkName = if (networkSSIDs.isEmpty()) "any WiFi network" else networkSSIDs.first()
                if (trigger.inverted) {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_wifi_disconnected, networkName)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_wifi_connected, networkName)
                }
            }
        }
    }
    
    private fun getActionDescription(context: android.content.Context): String {
        if (actions.isEmpty()) {
            return context.getString(com.micoyc.speakthat.R.string.rule_action_multiple)
        }
        
        if (actions.size == 1) {
            return getSingleActionDescription(context, actions[0])
        }
        
        // Multiple actions
        val actionDescriptions = actions.map { getSingleActionDescription(context, it) }
        return combineDescriptions(context, actionDescriptions, LogicGate.AND)
    }
    
    private fun getSingleActionDescription(context: android.content.Context, action: Action): String {
        if (!action.enabled) return ""
        
        return when (action.type) {
            ActionType.DISABLE_SPEAKTHAT -> {
                context.getString(com.micoyc.speakthat.R.string.rule_action_skip_notification)
            }
            ActionType.ENABLE_APP_FILTER -> {
                val appPackage = action.data["app_package"] as? String ?: "app"
                context.getString(com.micoyc.speakthat.R.string.rule_action_enable_app_filter, appPackage)
            }
            ActionType.DISABLE_APP_FILTER -> {
                val appPackage = action.data["app_package"] as? String ?: "app"
                context.getString(com.micoyc.speakthat.R.string.rule_action_disable_app_filter, appPackage)
            }
            ActionType.CHANGE_VOICE_SETTINGS -> {
                context.getString(com.micoyc.speakthat.R.string.rule_action_change_voice_settings)
            }
        }
    }
    
    private fun getExceptionDescription(context: android.content.Context): String {
        if (exceptions.isEmpty()) {
            return ""
        }
        
        if (exceptions.size == 1) {
            return getSingleExceptionDescription(context, exceptions[0])
        }
        
        // Multiple exceptions - combine with logic gate
        val exceptionDescriptions = exceptions.map { getSingleExceptionDescription(context, it) }
        return combineDescriptions(context, exceptionDescriptions, exceptionLogic)
    }
    
    private fun getSingleExceptionDescription(context: android.content.Context, exception: Exception): String {
        if (!exception.enabled) return ""
        
        return when (exception.type) {
            ExceptionType.BLUETOOTH_DEVICE -> {
                val deviceAddresses = exception.data["device_addresses"] as? Set<String> ?: emptySet()
                val deviceName = if (deviceAddresses.isEmpty()) "any Bluetooth device" else "Bluetooth device"
                if (exception.inverted) {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_bluetooth_disconnected)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_bluetooth_connected)
                }
            }
            ExceptionType.SCREEN_STATE -> {
                val screenState = exception.data["screen_state"] as? String ?: "on"
                val screenOn = screenState == "on"
                if (screenOn xor exception.inverted) {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_screen_on)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_screen_off)
                }
            }
            ExceptionType.TIME_SCHEDULE -> {
                // Handle different number types that might be stored
                val startTimeRaw = exception.data["start_time"]
                val endTimeRaw = exception.data["end_time"]
                
                val startTimeMillis = when (startTimeRaw) {
                    is Long -> startTimeRaw
                    is Double -> startTimeRaw.toLong()
                    is Int -> startTimeRaw.toLong()
                    else -> 0L
                }
                
                val endTimeMillis = when (endTimeRaw) {
                    is Long -> endTimeRaw
                    is Double -> endTimeRaw.toLong()
                    is Int -> endTimeRaw.toLong()
                    else -> 0L
                }
                
                // Fix time conversion logic - handle case where time is 0 (00:00)
                val startHour = (startTimeMillis / (60 * 60 * 1000)).toInt()
                val startMinute = ((startTimeMillis % (60 * 60 * 1000)) / (60 * 1000)).toInt()
                val endHour = (endTimeMillis / (60 * 60 * 1000)).toInt()
                val endMinute = ((endTimeMillis % (60 * 60 * 1000)) / (60 * 1000)).toInt()
                
                val startTime = String.format("%02d:%02d", startHour, startMinute)
                val endTime = String.format("%02d:%02d", endHour, endMinute)
                
                context.getString(
                    com.micoyc.speakthat.R.string.rule_exception_time_between,
                    startTime,
                    endTime
                )
            }
            ExceptionType.WIFI_NETWORK -> {
                val networkSSIDs = exception.data["network_ssids"] as? Set<String> ?: emptySet()
                val networkName = if (networkSSIDs.isEmpty()) "any WiFi network" else networkSSIDs.first()
                if (exception.inverted) {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_wifi_disconnected, networkName)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_wifi_connected, networkName)
                }
            }
        }
    }
    
    private fun combineDescriptions(context: android.content.Context, descriptions: List<String>, logic: LogicGate): String {
        val validDescriptions = descriptions.filter { it.isNotEmpty() }
        if (validDescriptions.isEmpty()) return ""
        if (validDescriptions.size == 1) return validDescriptions[0]
        
        val separator = context.getString(com.micoyc.speakthat.R.string.rule_logic_separator)
        val finalSeparator = context.getString(com.micoyc.speakthat.R.string.rule_logic_final_separator)
        
        return when (logic) {
            LogicGate.AND -> {
                if (validDescriptions.size == 2) {
                    validDescriptions[0] + finalSeparator + validDescriptions[1]
                } else {
                    validDescriptions.dropLast(1).joinToString(separator) + finalSeparator + validDescriptions.last()
                }
            }
            LogicGate.OR -> {
                validDescriptions.joinToString(" ${context.getString(com.micoyc.speakthat.R.string.rule_logic_or)} ")
            }
            LogicGate.XOR -> {
                validDescriptions.joinToString(" ${context.getString(com.micoyc.speakthat.R.string.rule_logic_xor)} ")
            }
        }
    }
    
    fun isValid(): Boolean {
        // A rule must have at least one trigger and one action to be valid
        val hasTriggers = triggers.isNotEmpty() && triggers.any { it.enabled }
        val hasActions = actions.isNotEmpty() && actions.any { it.enabled }
        
        InAppLogger.logDebug("RuleData", "Rule validation: $name - hasTriggers=$hasTriggers, hasActions=$hasActions")
        
        return hasTriggers && hasActions
    }

    fun toJson(): String {
        return com.google.gson.Gson().toJson(this)
    }
} 