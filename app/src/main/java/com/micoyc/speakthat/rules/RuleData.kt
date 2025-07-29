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
    DISABLE_SPEAKTHAT("Disable SpeakThat", "Turn off notification reading completely"),
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