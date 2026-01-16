package com.micoyc.speakthat.rules

import android.content.Context
import android.content.SharedPreferences
import com.micoyc.speakthat.InAppLogger

/**
 * Action Executor
 * Handles executing different types of actions when rules are triggered
 */

class ActionExecutor(private val context: Context) {
    
    companion object {
        private const val TAG = "ActionExecutor"
        private const val PREFS_NAME = "SpeakThatPrefs"
    }
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Execute a list of actions
     */
    fun executeActions(actions: List<Action>): List<ActionExecutionResult> {
        InAppLogger.logDebug(TAG, "Executing ${actions.size} actions")
        
        return actions.map { action ->
            executeAction(action)
        }
    }
    
    /**
     * Execute a single action
     */
    private fun executeAction(action: Action): ActionExecutionResult {
        if (!action.enabled) {
            return ActionExecutionResult(
                actionId = action.id,
                actionType = action.type,
                success = false,
                message = "Action is disabled"
            )
        }
        
        InAppLogger.logDebug(TAG, "Executing action: ${action.getLogMessage()}")

        return when (action.type) {
            ActionType.APPLY_CUSTOM_SPEECH_FORMAT -> {
                ActionExecutionResult(
                    actionId = action.id,
                    actionType = action.type,
                    success = true,
                    message = "Custom speech format handled by rule pipeline"
                )
            }
            ActionType.FORCE_PRIVATE -> {
                ActionExecutionResult(
                    actionId = action.id,
                    actionType = action.type,
                    success = true,
                    message = "Force private handled by rule pipeline"
                )
            }
            ActionType.OVERRIDE_PRIVATE -> {
                ActionExecutionResult(
                    actionId = action.id,
                    actionType = action.type,
                    success = true,
                    message = "Override private handled by rule pipeline"
                )
            }
            ActionType.SKIP_NOTIFICATION,
            ActionType.DISABLE_SPEAKTHAT -> {
                ActionExecutionResult(
                    actionId = action.id,
                    actionType = action.type,
                    success = true,
                    message = "Skip notification handled by rule pipeline"
                )
            }
            ActionType.SET_MASTER_SWITCH -> executeSetMasterSwitch(action)
        }
    }
    
    // ============================================================================
    // ACTION IMPLEMENTATIONS
    // ============================================================================
    
    private fun executeSetMasterSwitch(action: Action): ActionExecutionResult {
        try {
            val enabled = action.data["enabled"] as? Boolean ?: false
            sharedPreferences.edit().putBoolean("speakthat_enabled", enabled).apply()
            
            InAppLogger.logDebug(TAG, "SpeakThat master switch set to $enabled via rule action")
            
            return ActionExecutionResult(
                actionId = action.id,
                actionType = action.type,
                success = true,
                message = "SpeakThat master switch set to $enabled"
            )
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error setting SpeakThat master switch: ${e.message}")
            return ActionExecutionResult(
                actionId = action.id,
                actionType = action.type,
                success = false,
                message = "Failed to set master switch: ${e.message}"
            )
        }
    }
    
    // Removed other actions; legacy execution is now handled by the rule pipeline.
}

// ============================================================================
// SUPPORTING DATA CLASSES
// ============================================================================

/**
 * Result of executing an action
 */
data class ActionExecutionResult(
    val actionId: String,
    val actionType: ActionType,
    val success: Boolean,
    val message: String,
    val data: Map<String, Any> = emptyMap()
) {
    fun getLogMessage(): String {
        return "Action[$actionId]: ${actionType.displayName} - ${if (success) "SUCCESS" else "FAILED"} - $message"
    }
} 