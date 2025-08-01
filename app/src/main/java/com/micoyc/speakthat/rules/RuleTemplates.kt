package com.micoyc.speakthat.rules

import com.micoyc.speakthat.InAppLogger

/**
 * Rule Templates
 * Pre-configured rule templates for common use cases with user-friendly language
 */

data class RuleTemplate(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
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
    fun getAllTemplates(): List<RuleTemplate> {
        return listOf(
            // Essential templates for common use cases
            createHeadphonesOnlyTemplate(),
            createScreenOnTemplate(),
            createHomeNetworkTemplate(),
            createTimeScheduleTemplate()
        )
    }
    
    /**
     * Create a rule from a template
     */
    fun createRuleFromTemplate(template: RuleTemplate, customData: Map<String, Any> = emptyMap()): Rule {
        InAppLogger.logDebug(TAG, "Creating rule from template: ${template.name}")
        
        // Convert template triggers to actual triggers
        val triggers = template.triggers.map { triggerTemplate ->
            Trigger(
                type = triggerTemplate.type,
                inverted = triggerTemplate.inverted,
                data = triggerTemplate.data + customData,
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
        
        return Rule(
            name = template.name,
            triggers = triggers,
            actions = actions
        )
    }
    
    // ============================================================================
    // TEMPLATE DEFINITIONS
    // ============================================================================
    
    private fun createHeadphonesOnlyTemplate(): RuleTemplate {
        return RuleTemplate(
            id = "headphones_only",
            name = "Only read notifications when Bluetooth headphones are connected",
            description = "Skip notifications when your Bluetooth headphones are disconnected",
            icon = "🎧",
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
                    description = "When Bluetooth headphones are disconnected"
                )
            ),
            actions = listOf(
                ActionTemplate(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Skip reading this notification"
                )
            )
        )
    }
    

    
    private fun createTimeScheduleTemplate(): RuleTemplate {
        return RuleTemplate(
            id = "time_schedule",
            name = "Don't read notifications between specific times",
            description = "Skip notifications during your specified time range",
            icon = "⏰",
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
                    description = "Between 10:00 PM and 8:00 AM"
                )
            ),
            actions = listOf(
                ActionTemplate(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Skip reading this notification"
                )
            )
        )
    }
    

    
    private fun createScreenOnTemplate(): RuleTemplate {
        return RuleTemplate(
            id = "screen_off",
            name = "Only read notifications when my screen is off",
            description = "Skip notifications when your phone screen is turned on",
            icon = "📱",
            triggers = listOf(
                TriggerTemplate(
                    type = TriggerType.SCREEN_STATE,
                    inverted = false, // Not inverted - trigger when screen is ON (to skip notifications)
                    data = mapOf("screenOn" to true),
                    description = "When screen is turned on"
                )
            ),
            actions = listOf(
                ActionTemplate(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Skip reading this notification"
                )
            )
        )
    }
    
    private fun createHomeNetworkTemplate(): RuleTemplate {
        return RuleTemplate(
            id = "home_network",
            name = "Only read notifications when connected to Home WiFi",
            description = "Skip notifications when not connected to your home WiFi network",
            icon = "🏠",
            requiresDeviceSelection = true,
            deviceType = "wifi",
            triggers = listOf(
                TriggerTemplate(
                    type = TriggerType.WIFI_NETWORK,
                    inverted = true,
                    description = "When not connected to home WiFi"
                )
            ),
            actions = listOf(
                ActionTemplate(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Skip reading this notification"
                )
            )
        )
    }
    

} 