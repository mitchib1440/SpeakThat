package com.micoyc.speakthat.rules

import android.content.Context
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.R

/**
 * Rule Templates
 * Pre-configured rule templates for common use cases with user-friendly language
 */

data class RuleTemplate(
    val id: String,
    val name: String,
    val description: String,
    val iconDrawable: Int,
    val triggers: List<TriggerTemplate>,
    val actions: List<ActionTemplate>,
    val requiresDeviceSelection: Boolean = false,
    val deviceType: String? = null
)

data class TriggerTemplate(
    val type: TriggerType,
    val inverted: Boolean = false,
    val data: Map<String, Any> = emptyMap(),
    val description: String
)

data class ActionTemplate(
    val type: ActionType,
    val data: Map<String, Any> = emptyMap(),
    val description: String
)

object RuleTemplates {
    
    private const val TAG = "RuleTemplates"
    
    /**
     * Get all available rule templates
     */
    fun getAllTemplates(context: Context): List<RuleTemplate> {
        return listOf(
            // Essential templates for common use cases
            createHeadphonesOnlyTemplate(context),
            createScreenOnTemplate(context),
            createHomeNetworkTemplate(context),
            createTimeScheduleTemplate(context)
        )
    }
    
    /**
     * Create a rule from a template
     */
    fun createRuleFromTemplate(template: RuleTemplate, customData: Map<String, Any> = emptyMap()): Rule {
        InAppLogger.logDebug(TAG, "Creating rule from template: ${template.name}")
        InAppLogger.logDebug(TAG, "Custom data received: $customData")
        
        // Convert template triggers to actual triggers
        val triggers = template.triggers.map { triggerTemplate ->
            val processedData = processTriggerData(triggerTemplate.type, triggerTemplate.data + customData)
            InAppLogger.logDebug(TAG, "Trigger ${triggerTemplate.type}: processed data = $processedData")
            Trigger(
                type = triggerTemplate.type,
                inverted = triggerTemplate.inverted,
                data = processedData,
                description = triggerTemplate.description
            )
        }
        
        // Convert template actions to actual actions
        val actions = template.actions.map { actionTemplate ->
            Action(
                type = actionTemplate.type,
                data = actionTemplate.data + customData,
                description = actionTemplate.description
            )
        }
        
        val rule = Rule(
            name = template.name,
            triggers = triggers,
            actions = actions
        )
        
        InAppLogger.logDebug(TAG, "Created rule: ${rule.name} with ${triggers.size} triggers")
        triggers.forEach { trigger ->
            InAppLogger.logDebug(TAG, "  Trigger ${trigger.type}: data = ${trigger.data}")
        }
        
        return rule
    }
    
    /**
     * Process trigger data to convert template format to actual trigger format
     */
    private fun processTriggerData(triggerType: TriggerType, data: Map<String, Any>): Map<String, Any> {
        return when (triggerType) {
            TriggerType.TIME_SCHEDULE -> {
                // Convert hour/minute format to milliseconds format
                val startHour = data["startHour"] as? Int ?: 0
                val startMinute = data["startMinute"] as? Int ?: 0
                val endHour = data["endHour"] as? Int ?: 0
                val endMinute = data["endMinute"] as? Int ?: 0
                val selectedDays = (data["selectedDays"] as? Collection<*>)
                    ?.mapNotNull { it as? Int }
                    ?.toSet()
                    ?: emptySet()
                
                // Convert 1-based days (Monday=1, Tuesday=2, ..., Sunday=7) to 0-based days (Sunday=0, Monday=1, ..., Saturday=6)
                val convertedDays = selectedDays.map { day ->
                    when (day) {
                        1 -> 1  // Monday stays 1
                        2 -> 2  // Tuesday stays 2
                        3 -> 3  // Wednesday stays 3
                        4 -> 4  // Thursday stays 4
                        5 -> 5  // Friday stays 5
                        6 -> 6  // Saturday stays 6
                        7 -> 0  // Sunday becomes 0
                        else -> day
                    }
                }.toSet()
                
                val startTimeMillis = (startHour * 60 * 60 * 1000L) + (startMinute * 60 * 1000L)
                val endTimeMillis = (endHour * 60 * 60 * 1000L) + (endMinute * 60 * 1000L)
                
                InAppLogger.logDebug(TAG, "Converting time data: ${startHour}:${startMinute} -> ${startTimeMillis}ms, ${endHour}:${endMinute} -> ${endTimeMillis}ms, original days: $selectedDays, converted days: $convertedDays")
                
                mapOf(
                    "start_time" to startTimeMillis,
                    "end_time" to endTimeMillis,
                    "days_of_week" to convertedDays
                )
            }
            TriggerType.WIFI_NETWORK -> {
                // Convert ssid field to network_ssids format
                val ssid = data["ssid"] as? String
                val networkId = data["networkId"] as? Int
                
                InAppLogger.logDebug(TAG, "Converting WiFi data: ssid=$ssid, networkId=$networkId")
                
                if (ssid != null && ssid.isNotEmpty()) {
                    mapOf("network_ssids" to setOf(ssid))
                } else {
                    emptyMap<String, Any>()
                }
            }
            else -> data
        }
    }
    
    // ============================================================================
    // TEMPLATE DEFINITIONS
    // ============================================================================
    
    private fun createHeadphonesOnlyTemplate(context: Context): RuleTemplate {
        return RuleTemplate(
            id = "headphones_only",
            name = context.getString(R.string.template_headphones_only_name),
            description = context.getString(R.string.template_headphones_only_description),
            iconDrawable = R.drawable.ic_bluetooth_24,
            requiresDeviceSelection = true,
            deviceType = "bluetooth",
            triggers = listOf(
                TriggerTemplate(
                    type = TriggerType.BLUETOOTH_DEVICE,
                    inverted = true, // Inverted means "when NOT connected"
                    data = mapOf(
                        "device_addresses" to emptySet<String>(),
                        "device_display_text" to ""
                    ),
                    description = context.getString(R.string.template_trigger_headphones_disconnected)
                )
            ),
            actions = listOf(
                ActionTemplate(
                    type = ActionType.SKIP_NOTIFICATION,
                    description = context.getString(R.string.template_action_skip_notification)
                )
            )
        )
    }
    

    
    private fun createTimeScheduleTemplate(context: Context): RuleTemplate {
        return RuleTemplate(
            id = "time_schedule",
            name = context.getString(R.string.template_time_schedule_name),
            description = context.getString(R.string.template_time_schedule_description),
            iconDrawable = R.drawable.ic_schedule_24,
            requiresDeviceSelection = true,
            deviceType = "time_schedule",
            triggers = listOf(
                TriggerTemplate(
                    type = TriggerType.TIME_SCHEDULE,
                    data = mapOf(
                        "startHour" to 22,
                        "startMinute" to 0,
                        "endHour" to 8,
                        "endMinute" to 0
                    ),
                    description = context.getString(R.string.template_trigger_time_between)
                )
            ),
            actions = listOf(
                ActionTemplate(
                    type = ActionType.SKIP_NOTIFICATION,
                    description = context.getString(R.string.template_action_skip_notification)
                )
            )
        )
    }
    

    
    private fun createScreenOnTemplate(context: Context): RuleTemplate {
        return RuleTemplate(
            id = "screen_off",
            name = context.getString(R.string.template_screen_off_name),
            description = context.getString(R.string.template_screen_off_description),
            iconDrawable = R.drawable.ic_backlight_24,
            triggers = listOf(
                TriggerTemplate(
                    type = TriggerType.SCREEN_STATE,
                    inverted = false, // Not inverted - trigger when screen is ON (to skip notifications)
                    data = mapOf("screenOn" to true),
                    description = context.getString(R.string.template_trigger_screen_on)
                )
            ),
            actions = listOf(
                ActionTemplate(
                    type = ActionType.SKIP_NOTIFICATION,
                    description = context.getString(R.string.template_action_skip_notification)
                )
            )
        )
    }
    
    private fun createHomeNetworkTemplate(context: Context): RuleTemplate {
        return RuleTemplate(
            id = "home_network",
            name = context.getString(R.string.template_home_network_name),
            description = context.getString(R.string.template_home_network_description),
            iconDrawable = R.drawable.ic_wifi_24,
            requiresDeviceSelection = true,
            deviceType = "wifi",
            triggers = listOf(
                TriggerTemplate(
                    type = TriggerType.WIFI_NETWORK,
                    inverted = true,
                    description = context.getString(R.string.template_trigger_not_home_wifi)
                )
            ),
            actions = listOf(
                ActionTemplate(
                    type = ActionType.SKIP_NOTIFICATION,
                    description = context.getString(R.string.template_action_skip_notification)
                )
            )
        )
    }
    

} 