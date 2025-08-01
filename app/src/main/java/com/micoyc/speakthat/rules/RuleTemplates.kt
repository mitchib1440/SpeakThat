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
    
    private fun createHeadphonesOnlyTemplate(context: Context): RuleTemplate {
        return RuleTemplate(
            id = "headphones_only",
            name = context.getString(R.string.template_headphones_only_name),
            description = context.getString(R.string.template_headphones_only_description),
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
                    description = context.getString(R.string.template_trigger_headphones_disconnected)
                )
            ),
            actions = listOf(
                ActionTemplate(
                    type = ActionType.DISABLE_SPEAKTHAT,
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
                    description = context.getString(R.string.template_trigger_time_between)
                )
            ),
            actions = listOf(
                ActionTemplate(
                    type = ActionType.DISABLE_SPEAKTHAT,
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
            icon = "📱",
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
                    type = ActionType.DISABLE_SPEAKTHAT,
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
            icon = "🏠",
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
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = context.getString(R.string.template_action_skip_notification)
                )
            )
        )
    }
    

} 