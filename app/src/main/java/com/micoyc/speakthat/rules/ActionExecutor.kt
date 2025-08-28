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
            ActionType.DISABLE_SPEAKTHAT -> executeDisableSpeakThat(action)
            ActionType.ENABLE_APP_FILTER -> executeEnableAppFilter(action)
            ActionType.DISABLE_APP_FILTER -> executeDisableAppFilter(action)
            ActionType.CHANGE_VOICE_SETTINGS -> executeChangeVoiceSettings(action)
        }
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
    
    private fun executeEnableAppFilter(action: Action): ActionExecutionResult {
        try {
            val appPackageName = action.data["app_package"] as? String
            
            if (appPackageName.isNullOrEmpty()) {
                return ActionExecutionResult(
                    actionId = action.id,
                    actionType = action.type,
                    success = false,
                    message = "No app package specified"
                )
            }
            
            // Get current app list
            val currentApps = sharedPreferences.getStringSet("app_list", emptySet()) ?: emptySet()
            
            val newApps = currentApps.toMutableSet()
            newApps.add(appPackageName)
            
            // Update preferences
            sharedPreferences.edit()
                .putString("app_list_mode", "whitelist")
                .putStringSet("app_list", newApps)
                .apply()
            
            InAppLogger.logDebug(TAG, "App filter enabled for: $appPackageName")
            
            return ActionExecutionResult(
                actionId = action.id,
                actionType = action.type,
                success = true,
                message = "App filter enabled for $appPackageName",
                data = mapOf("app_package" to appPackageName)
            )
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error enabling app filter: ${e.message}")
            return ActionExecutionResult(
                actionId = action.id,
                actionType = action.type,
                success = false,
                message = "Failed to enable app filter: ${e.message}"
            )
        }
    }
    
    private fun executeDisableAppFilter(action: Action): ActionExecutionResult {
        try {
            val appPackageName = action.data["app_package"] as? String
            
            if (appPackageName.isNullOrEmpty()) {
                return ActionExecutionResult(
                    actionId = action.id,
                    actionType = action.type,
                    success = false,
                    message = "No app package specified"
                )
            }
            
            // Get current app list
            val currentApps = sharedPreferences.getStringSet("app_list", emptySet()) ?: emptySet()
            
            val newApps = currentApps.toMutableSet()
            newApps.remove(appPackageName)
            
            // Update preferences
            sharedPreferences.edit()
                .putStringSet("app_list", newApps)
                .apply()
            
            InAppLogger.logDebug(TAG, "App filter disabled for: $appPackageName")
            
            return ActionExecutionResult(
                actionId = action.id,
                actionType = action.type,
                success = true,
                message = "App filter disabled for $appPackageName",
                data = mapOf("app_package" to appPackageName)
            )
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error disabling app filter: ${e.message}")
            return ActionExecutionResult(
                actionId = action.id,
                actionType = action.type,
                success = false,
                message = "Failed to disable app filter: ${e.message}"
            )
        }
    }
    
    private fun executeChangeVoiceSettings(action: Action): ActionExecutionResult {
        try {
            val voiceSettings = action.data["voice_settings"] as? Map<String, Any>
            
            if (voiceSettings.isNullOrEmpty()) {
                return ActionExecutionResult(
                    actionId = action.id,
                    actionType = action.type,
                    success = false,
                    message = "No voice settings specified"
                )
            }
            
            // Apply voice settings
            val voicePrefs = context.getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE)
            val editor = voicePrefs.edit()
            
            voiceSettings.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Long -> editor.putLong(key, value)
                }
            }
            
            editor.apply()
            
            InAppLogger.logDebug(TAG, "Voice settings updated: $voiceSettings")
            
            return ActionExecutionResult(
                actionId = action.id,
                actionType = action.type,
                success = true,
                message = "Voice settings updated",
                data = mapOf("voice_settings" to voiceSettings)
            )
            
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error changing voice settings: ${e.message}")
            return ActionExecutionResult(
                actionId = action.id,
                actionType = action.type,
                success = false,
                message = "Failed to change voice settings: ${e.message}"
            )
        }
    }
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