package com.micoyc.speakthat.rules

import android.content.Context
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.micoyc.speakthat.AppListManager
import com.micoyc.speakthat.InAppLogger
import java.lang.reflect.Type
import kotlin.math.abs

/**
 * Custom type adapter for Map<String, Any> to handle proper serialization/deserialization
 * This ensures that numeric types (Long, Int, Double) are preserved correctly
 */
class MapStringAnyTypeAdapter : JsonSerializer<Map<String, Any>>, JsonDeserializer<Map<String, Any>> {
    
    override fun serialize(src: Map<String, Any>?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        if (src == null) return JsonNull.INSTANCE
        
        val jsonObject = JsonObject()
        for ((key, value) in src) {
            when {
                value is String -> jsonObject.addProperty(key, value)
                value is Number -> jsonObject.addProperty(key, value)
                value is Boolean -> jsonObject.addProperty(key, value)
                value is List<*> -> {
                    val jsonArray = JsonArray()
                    for (item in value) {
                        when (item) {
                            null -> jsonArray.add(JsonNull.INSTANCE)
                            is String -> jsonArray.add(item)
                            is Number -> jsonArray.add(item)
                            is Boolean -> jsonArray.add(item)
                            else -> jsonArray.add(Gson().toJsonTree(item))
                        }
                    }
                    jsonObject.add(key, jsonArray)
                }
                value is Set<*> -> {
                    val jsonArray = JsonArray()
                    for (item in value) {
                        when (item) {
                            null -> jsonArray.add(JsonNull.INSTANCE)
                            is String -> jsonArray.add(item)
                            is Number -> jsonArray.add(item)
                            is Boolean -> jsonArray.add(item)
                            else -> jsonArray.add(Gson().toJsonTree(item))
                        }
                    }
                    jsonObject.add(key, jsonArray)
                }
                else -> jsonObject.add(key, Gson().toJsonTree(value))
            }
        }
        return jsonObject
    }
    
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Map<String, Any> {
        if (json == null || json.isJsonNull) return emptyMap()
        
        val result = mutableMapOf<String, Any>()
        val jsonObject = json.asJsonObject
        
        for ((key, value) in jsonObject.entrySet()) {
            if (value.isJsonNull) {
                continue
            }

            result[key] = when {
                value.isJsonPrimitive -> {
                    val primitive = value.asJsonPrimitive
                    when {
                        primitive.isString -> primitive.asString
                        primitive.isNumber -> {
                            val number = primitive.asNumber
                            InAppLogger.logDebug("MapStringAnyTypeAdapter", "Processing number: $number (${number.javaClass.simpleName})")
                            InAppLogger.logDebug("MapStringAnyTypeAdapter", "  toLong(): ${number.toLong()}")
                            InAppLogger.logDebug("MapStringAnyTypeAdapter", "  toDouble(): ${number.toDouble()}")
                            InAppLogger.logDebug("MapStringAnyTypeAdapter", "  toInt(): ${number.toInt()}")
                            InAppLogger.logDebug("MapStringAnyTypeAdapter", "  abs(toLong() - toDouble()): ${abs(number.toLong() - number.toDouble())}")
                            InAppLogger.logDebug("MapStringAnyTypeAdapter", "  abs(toInt() - toDouble()): ${abs(number.toInt() - number.toDouble())}")
                            
                            val convertedValue = when {
                                abs(number.toLong() - number.toDouble()) < 0.001 -> {
                                    InAppLogger.logDebug("MapStringAnyTypeAdapter", "  -> Converting to Long: ${number.toLong()}")
                                    number.toLong()
                                }
                                abs(number.toInt() - number.toDouble()) < 0.001 -> {
                                    InAppLogger.logDebug("MapStringAnyTypeAdapter", "  -> Converting to Int: ${number.toInt()}")
                                    number.toInt()
                                }
                                else -> {
                                    InAppLogger.logDebug("MapStringAnyTypeAdapter", "  -> Converting to Double: ${number.toDouble()}")
                                    number.toDouble()
                                }
                            }
                            InAppLogger.logDebug("MapStringAnyTypeAdapter", "  Final result: $convertedValue (${convertedValue.javaClass.simpleName})")
                            convertedValue
                        }
                        primitive.isBoolean -> primitive.asBoolean
                        else -> primitive.asString
                    }
                }
                value.isJsonArray -> {
                    val array = value.asJsonArray
                    val list = mutableListOf<Any>()
                    for (element in array) {
                        if (element.isJsonNull) {
                            continue
                        }
                        when {
                            element.isJsonPrimitive -> {
                                val primitive = element.asJsonPrimitive
                                list.add(when {
                                    primitive.isString -> primitive.asString
                                    primitive.isNumber -> {
                                        val number = primitive.asNumber
                                        InAppLogger.logDebug("MapStringAnyTypeAdapter", "Array Processing number: $number (${number.javaClass.simpleName})")
                                        InAppLogger.logDebug("MapStringAnyTypeAdapter", "  toLong(): ${number.toLong()}")
                                        InAppLogger.logDebug("MapStringAnyTypeAdapter", "  toDouble(): ${number.toDouble()}")
                                        InAppLogger.logDebug("MapStringAnyTypeAdapter", "  toInt(): ${number.toInt()}")
                                        InAppLogger.logDebug("MapStringAnyTypeAdapter", "  abs(toLong() - toDouble()): ${abs(number.toLong() - number.toDouble())}")
                                        InAppLogger.logDebug("MapStringAnyTypeAdapter", "  abs(toInt() - toDouble()): ${abs(number.toInt() - number.toDouble())}")
                                        
                                        val convertedValue = when {
                                            abs(number.toLong() - number.toDouble()) < 0.001 -> {
                                                InAppLogger.logDebug("MapStringAnyTypeAdapter", "  -> Converting to Long: ${number.toLong()}")
                                                number.toLong()
                                            }
                                            abs(number.toInt() - number.toDouble()) < 0.001 -> {
                                                InAppLogger.logDebug("MapStringAnyTypeAdapter", "  -> Converting to Int: ${number.toInt()}")
                                                number.toInt()
                                            }
                                            else -> {
                                                InAppLogger.logDebug("MapStringAnyTypeAdapter", "  -> Converting to Double: ${number.toDouble()}")
                                                number.toDouble()
                                            }
                                        }
                                        InAppLogger.logDebug("MapStringAnyTypeAdapter", "  Final result: $convertedValue (${convertedValue.javaClass.simpleName})")
                                        convertedValue
                                    }
                                    primitive.isBoolean -> primitive.asBoolean
                                    else -> primitive.asString
                                })
                            }
                                                          else -> list.add(context?.deserialize(element, Any::class.java) ?: "")
                        }
                    }
                    list
                }
                else -> Gson().fromJson(value, Any::class.java)
            }
        }
        
        return result
    }
}

/**
 * Custom type adapters for Rule system classes to ensure consistent serialization/deserialization
 */

class RuleTypeAdapter : JsonSerializer<Rule>, JsonDeserializer<Rule> {
    private val gson = GsonBuilder()
        .registerTypeAdapter(object : TypeToken<Map<String, Any>>() {}.type, MapStringAnyTypeAdapter())
        .create()
    
    override fun serialize(src: Rule?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return gson.toJsonTree(src)
    }
    
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Rule {
        return gson.fromJson(json, Rule::class.java)
    }
}

class TriggerTypeAdapter : JsonSerializer<Trigger>, JsonDeserializer<Trigger> {
    private val gson = GsonBuilder()
        .registerTypeAdapter(object : TypeToken<Map<String, Any>>() {}.type, MapStringAnyTypeAdapter())
        .create()
    
    override fun serialize(src: Trigger?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return gson.toJsonTree(src)
    }
    
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Trigger {
        return gson.fromJson(json, Trigger::class.java)
    }
}

class ActionTypeAdapter : JsonSerializer<Action>, JsonDeserializer<Action> {
    private val gson = GsonBuilder()
        .registerTypeAdapter(object : TypeToken<Map<String, Any>>() {}.type, MapStringAnyTypeAdapter())
        .create()
    
    override fun serialize(src: Action?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return gson.toJsonTree(src)
    }
    
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Action {
        return gson.fromJson(json, Action::class.java)
    }
}

class ExceptionTypeAdapter : JsonSerializer<Exception>, JsonDeserializer<Exception> {
    private val gson = GsonBuilder()
        .registerTypeAdapter(object : TypeToken<Map<String, Any>>() {}.type, MapStringAnyTypeAdapter())
        .create()
    
    override fun serialize(src: Exception?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return gson.toJsonTree(src)
    }
    
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Exception {
        return gson.fromJson(json, Exception::class.java)
    }
}

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
enum class TriggerType(
    val displayName: String,
    val description: String,
    val isCacheable: Boolean
) {
    BLUETOOTH_DEVICE("Bluetooth Device Connected", "When a specific Bluetooth device is connected", true),
    WIRED_HEADPHONES("Wired Headphones", "When headphones are connected to your device via a cable", false),
    BATTERY_PERCENTAGE("Battery Percentage", "When battery is above or below a percentage", false),
    CHARGING_STATUS("Charging Status", "When the device is charging or discharging", true),
    DEVICE_UNLOCKED("Device Unlocked", "When the device is locked or unlocked", true),
    NOTIFICATION_CONTAINS("Notification Contains", "When notification contains a word or phrase", false),
    NOTIFICATION_FROM("Notification From", "When notification is from a selected app", false),
    FOREGROUND_APP("Foreground App", "When a specific app is in the foreground", false),
    SCREEN_ORIENTATION("Screen Orientation", "When the screen is portrait or landscape", true),
    SCREEN_STATE("Screen State (On/Off)", "When the screen is on or off", true),
    TIME_SCHEDULE("Time Schedule", "During specific time periods", true),
    WIFI_NETWORK("WiFi Network Connected", "When connected to a specific WiFi network", true);
    
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
    APPLY_CUSTOM_SPEECH_FORMAT("Apply custom speech format", "Read this notification using a custom speech format"),
    FORCE_PRIVATE("Force private", "Read the app name, but not the notification content"),
    OVERRIDE_PRIVATE("Override private", "Read the entire notification, even if it would normally be private"),
    SKIP_NOTIFICATION("Skip this notification", "Don't read this notification aloud"),
    SET_MASTER_SWITCH("Set master switch", "Enable or disable SpeakThat globally"),
    DISABLE_SPEAKTHAT("Legacy: Skip this notification", "Legacy action migrated to Skip this notification");
    
    companion object {
        fun fromDisplayName(name: String): ActionType {
            return values().find { it.displayName == name } ?: SKIP_NOTIFICATION
        }
    }
}

/**
 * Types of exceptions that can override a rule
 */
enum class ExceptionType(
    val displayName: String,
    val description: String,
    val isCacheable: Boolean
) {
    BLUETOOTH_DEVICE("Bluetooth Device Connected", "When a specific Bluetooth device is connected", true),
    WIRED_HEADPHONES("Wired Headphones", "When headphones are connected to your device via a cable", false),
    BATTERY_PERCENTAGE("Battery Percentage", "When battery is above or below a percentage", false),
    CHARGING_STATUS("Charging Status", "When the device is charging or discharging", true),
    DEVICE_UNLOCKED("Device Unlocked", "When the device is locked or unlocked", true),
    NOTIFICATION_CONTAINS("Notification Contains", "When notification contains a word or phrase", false),
    NOTIFICATION_FROM("Notification From", "When notification is from a selected app", false),
    FOREGROUND_APP("Foreground App", "When a specific app is in the foreground", false),
    SCREEN_ORIENTATION("Screen Orientation", "When the screen is portrait or landscape", true),
    SCREEN_STATE("Screen State (On/Off)", "When the screen is on or off", true),
    TIME_SCHEDULE("Time Schedule", "During specific time periods", true),
    WIFI_NETWORK("WiFi Network Connected", "When connected to a specific WiFi network", true);
    
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
                val gson = GsonBuilder()
                    .registerTypeAdapter(object : TypeToken<Map<String, Any>>() {}.type, MapStringAnyTypeAdapter())
                    .create()
                gson.fromJson(json, Trigger::class.java)
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
        val gson = GsonBuilder()
            .registerTypeAdapter(object : TypeToken<Map<String, Any>>() {}.type, MapStringAnyTypeAdapter())
            .create()
        return gson.toJson(this)
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
                val gson = GsonBuilder()
                    .registerTypeAdapter(object : TypeToken<Map<String, Any>>() {}.type, MapStringAnyTypeAdapter())
                    .create()
                gson.fromJson(json, Action::class.java)
            } catch (e: Throwable) {
                null
            }
        }
    }
    
    fun getLogMessage(): String {
        return "Action[$id]: ${type.displayName} - ${if (enabled) "ENABLED" else "DISABLED"} - $description"
    }
    
    fun toJson(): String {
        val gson = GsonBuilder()
            .registerTypeAdapter(object : TypeToken<Map<String, Any>>() {}.type, MapStringAnyTypeAdapter())
            .create()
        return gson.toJson(this)
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
                val gson = GsonBuilder()
                    .registerTypeAdapter(object : TypeToken<Map<String, Any>>() {}.type, MapStringAnyTypeAdapter())
                    .create()
                gson.fromJson(json, Exception::class.java)
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
        val gson = GsonBuilder()
            .registerTypeAdapter(object : TypeToken<Map<String, Any>>() {}.type, MapStringAnyTypeAdapter())
            .create()
        return gson.toJson(this)
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
                val gson = GsonBuilder()
                    .registerTypeAdapter(object : TypeToken<Map<String, Any>>() {}.type, MapStringAnyTypeAdapter())
                    .create()
                gson.fromJson(json, Rule::class.java)
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
            TriggerType.BATTERY_PERCENTAGE -> {
                val mode = trigger.data["mode"] as? String ?: "above"
                val percentageRaw = trigger.data["percentage"]
                val percentage = when (percentageRaw) {
                    is Int -> percentageRaw
                    is Long -> percentageRaw.toInt()
                    is Double -> percentageRaw.toInt()
                    is Float -> percentageRaw.toInt()
                    is Number -> percentageRaw.toInt()
                    is String -> percentageRaw.toIntOrNull()
                    else -> null
                } ?: 0

                if (mode == "below") {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_battery_below, percentage)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_battery_above, percentage)
                }
            }
            TriggerType.CHARGING_STATUS -> {
                val status = trigger.data["status"] as? String ?: "charging"
                if (status == "discharging") {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_battery_discharging)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_battery_charging)
                }
            }
            TriggerType.DEVICE_UNLOCKED -> {
                val mode = trigger.data["mode"] as? String ?: "unlocked"
                if (mode == "locked") {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_device_locked)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_device_unlocked)
                }
            }
            TriggerType.NOTIFICATION_CONTAINS -> {
                val phrase = trigger.data["phrase"] as? String ?: ""
                val caseSensitive = trigger.data["case_sensitive"] as? Boolean ?: false
                val caseSuffix = if (caseSensitive) {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_case_sensitive_suffix)
                } else {
                    ""
                }
                if (trigger.inverted) {
                    context.getString(
                        com.micoyc.speakthat.R.string.rule_trigger_notification_not_contains,
                        phrase
                    ) + caseSuffix
                } else {
                    context.getString(
                        com.micoyc.speakthat.R.string.rule_trigger_notification_contains,
                        phrase
                    ) + caseSuffix
                }
            }
            TriggerType.NOTIFICATION_FROM -> {
                val packagesData = trigger.data["app_packages"]
                val packages = when (packagesData) {
                    is Set<*> -> packagesData.filterIsInstance<String>()
                    is List<*> -> packagesData.filterIsInstance<String>()
                    else -> emptyList()
                }
                val description = if (packages.size == 1) {
                    val packageName = packages.first()
                    val appName = AppListManager.findAppByPackage(context, packageName)?.displayName ?: packageName
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_notification_from_single, appName)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_notification_from_multiple, packages.size)
                }
                if (trigger.inverted) {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_notification_from_not, description)
                } else {
                    description
                }
            }
            TriggerType.FOREGROUND_APP -> {
                val packagesData = trigger.data["app_packages"]
                val packages = when (packagesData) {
                    is Set<*> -> packagesData.filterIsInstance<String>()
                    is List<*> -> packagesData.filterIsInstance<String>()
                    else -> emptyList()
                }
                val description = if (packages.size == 1) {
                    val packageName = packages.first()
                    val appName = AppListManager.findAppByPackage(context, packageName)?.displayName ?: packageName
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_foreground_app_single, appName)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_foreground_app_multiple, packages.size)
                }
                if (trigger.inverted) {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_foreground_app_not, description)
                } else {
                    description
                }
            }
            TriggerType.BLUETOOTH_DEVICE -> {
                if (trigger.inverted) {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_bluetooth_disconnected)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_bluetooth_connected)
                }
            }
            TriggerType.WIRED_HEADPHONES -> {
                val connectionState = trigger.data["connection_state"] as? String ?: "disconnected"
                if (connectionState == "connected") {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_wired_headphones_connected)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_wired_headphones_disconnected)
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
                // Handle both Set<String> and List<String> since JSON serialization converts Sets to Lists
                val networkSSIDsData = trigger.data["network_ssids"]
                val networkSSIDs = when (networkSSIDsData) {
                    is Set<*> -> networkSSIDsData.filterIsInstance<String>().toSet()
                    is List<*> -> networkSSIDsData.filterIsInstance<String>().toSet()
                    else -> emptySet<String>()
                }
                
                // Android 15+ limitation: We can't detect specific network names
                if (networkSSIDs.isNotEmpty()) {
                    // Show warning about Android 15+ limitation
                    if (trigger.inverted) {
                        context.getString(com.micoyc.speakthat.R.string.rule_trigger_wifi_disconnected_android15, "any WiFi network")
                    } else {
                        context.getString(com.micoyc.speakthat.R.string.rule_trigger_wifi_connected_android15, "any WiFi network")
                    }
                } else {
                    // No specific networks selected - use generic WiFi state
                    if (trigger.inverted) {
                        context.getString(com.micoyc.speakthat.R.string.rule_trigger_wifi_disconnected, "any WiFi network")
                    } else {
                        context.getString(com.micoyc.speakthat.R.string.rule_trigger_wifi_connected, "any WiFi network")
                    }
                }
            }
            TriggerType.SCREEN_ORIENTATION -> {
                val mode = trigger.data["mode"] as? String ?: "portrait"
                if (mode == "landscape") {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_orientation_landscape)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_trigger_orientation_portrait)
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
            ActionType.APPLY_CUSTOM_SPEECH_FORMAT -> {
                context.getString(com.micoyc.speakthat.R.string.rule_action_custom_speech_format)
            }
            ActionType.FORCE_PRIVATE -> {
                context.getString(com.micoyc.speakthat.R.string.rule_action_force_private)
            }
            ActionType.OVERRIDE_PRIVATE -> {
                context.getString(com.micoyc.speakthat.R.string.rule_action_override_private)
            }
            ActionType.SKIP_NOTIFICATION,
            ActionType.DISABLE_SPEAKTHAT -> {
                context.getString(com.micoyc.speakthat.R.string.rule_action_skip_notification)
            }
            ActionType.SET_MASTER_SWITCH -> {
                context.getString(com.micoyc.speakthat.R.string.rule_action_master_switch)
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
            ExceptionType.BATTERY_PERCENTAGE -> {
                val mode = exception.data["mode"] as? String ?: "above"
                val percentageRaw = exception.data["percentage"]
                val percentage = when (percentageRaw) {
                    is Int -> percentageRaw
                    is Long -> percentageRaw.toInt()
                    is Double -> percentageRaw.toInt()
                    is Float -> percentageRaw.toInt()
                    is Number -> percentageRaw.toInt()
                    is String -> percentageRaw.toIntOrNull()
                    else -> null
                } ?: 0

                if (mode == "below") {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_battery_below, percentage)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_battery_above, percentage)
                }
            }
            ExceptionType.CHARGING_STATUS -> {
                val status = exception.data["status"] as? String ?: "charging"
                if (status == "discharging") {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_battery_discharging)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_battery_charging)
                }
            }
            ExceptionType.DEVICE_UNLOCKED -> {
                val mode = exception.data["mode"] as? String ?: "unlocked"
                if (mode == "locked") {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_device_locked)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_device_unlocked)
                }
            }
            ExceptionType.NOTIFICATION_CONTAINS -> {
                val phrase = exception.data["phrase"] as? String ?: ""
                val caseSensitive = exception.data["case_sensitive"] as? Boolean ?: false
                val caseSuffix = if (caseSensitive) {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_case_sensitive_suffix)
                } else {
                    ""
                }
                if (exception.inverted) {
                    context.getString(
                        com.micoyc.speakthat.R.string.rule_exception_notification_not_contains,
                        phrase
                    ) + caseSuffix
                } else {
                    context.getString(
                        com.micoyc.speakthat.R.string.rule_exception_notification_contains,
                        phrase
                    ) + caseSuffix
                }
            }
            ExceptionType.NOTIFICATION_FROM -> {
                val packagesData = exception.data["app_packages"]
                val packages = when (packagesData) {
                    is Set<*> -> packagesData.filterIsInstance<String>()
                    is List<*> -> packagesData.filterIsInstance<String>()
                    else -> emptyList()
                }
                val description = if (packages.size == 1) {
                    val packageName = packages.first()
                    val appName = AppListManager.findAppByPackage(context, packageName)?.displayName ?: packageName
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_notification_from_single, appName)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_notification_from_multiple, packages.size)
                }
                if (exception.inverted) {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_notification_from_not, description)
                } else {
                    description
                }
            }
            ExceptionType.FOREGROUND_APP -> {
                val packagesData = exception.data["app_packages"]
                val packages = when (packagesData) {
                    is Set<*> -> packagesData.filterIsInstance<String>()
                    is List<*> -> packagesData.filterIsInstance<String>()
                    else -> emptyList()
                }
                val description = if (packages.size == 1) {
                    val packageName = packages.first()
                    val appName = AppListManager.findAppByPackage(context, packageName)?.displayName ?: packageName
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_foreground_app_single, appName)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_foreground_app_multiple, packages.size)
                }
                if (exception.inverted) {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_foreground_app_not, description)
                } else {
                    description
                }
            }
            ExceptionType.BLUETOOTH_DEVICE -> {
                if (exception.inverted) {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_bluetooth_disconnected)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_bluetooth_connected)
                }
            }
            ExceptionType.WIRED_HEADPHONES -> {
                val connectionState = exception.data["connection_state"] as? String ?: "disconnected"
                if (connectionState == "connected") {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_wired_headphones_connected)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_wired_headphones_disconnected)
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
                // Handle both Set<String> and List<String> since JSON serialization converts Sets to Lists
                val networkSSIDsData = exception.data["network_ssids"]
                val networkSSIDs = when (networkSSIDsData) {
                    is Set<*> -> networkSSIDsData.filterIsInstance<String>().toSet()
                    is List<*> -> networkSSIDsData.filterIsInstance<String>().toSet()
                    else -> emptySet<String>()
                }
                val networkName = if (networkSSIDs.isEmpty()) "any WiFi network" else networkSSIDs.first()
                if (exception.inverted) {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_wifi_disconnected, networkName)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_wifi_connected, networkName)
                }
            }
            ExceptionType.SCREEN_ORIENTATION -> {
                val mode = exception.data["mode"] as? String ?: "portrait"
                if (mode == "landscape") {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_orientation_landscape)
                } else {
                    context.getString(com.micoyc.speakthat.R.string.rule_exception_orientation_portrait)
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
        val gson = GsonBuilder()
            .registerTypeAdapter(object : TypeToken<Map<String, Any>>() {}.type, MapStringAnyTypeAdapter())
            .create()
        return gson.toJson(this)
    }
} 