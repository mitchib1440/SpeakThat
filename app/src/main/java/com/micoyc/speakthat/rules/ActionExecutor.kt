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
        
        return executeDisableSpeakThat(action)
    }
    
    // ============================================================================
    // ACTION IMPLEMENTATIONS
    // ============================================================================
    
    private fun executeDisableSpeakThat(action: Action): ActionExecutionResult {
        try {
            // Set the main SpeakThat toggle to disabled
            sharedPreferences.edit().putBoolean("speakthat_enabled", false).apply()
            
            InAppLogger.logDebug(TAG, "SpeakThat disabled via rule action")
            
            return ActionExecutionResult(
                actionId = action.id,
                actionType = action.type,
                success = true,
                message = "SpeakThat disabled"
            )
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error disabling SpeakThat: ${e.message}")
            return ActionExecutionResult(
                actionId = action.id,
                actionType = action.type,
                success = false,
                message = "Failed to disable SpeakThat: ${e.message}"
            )
        }
    }
    
    // Removed other actions; Skip this notification is the sole action.
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