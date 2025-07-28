package com.micoyc.speakthat.conditions

import android.content.Context
import android.util.Log
import android.view.WindowManager
import com.micoyc.speakthat.BaseCondition
import com.micoyc.speakthat.ConditionChecker
import com.micoyc.speakthat.InAppLogger

/**
 * Screen state condition
 * Allows notifications only when screen is in a specific state (on/off)
 */
data class ScreenStateCondition(
    override val enabled: Boolean = false,
    val onlyWhenScreenOff: Boolean = true
) : BaseCondition(enabled, "screen_state") {
    
    override fun createChecker(context: Context): ConditionChecker {
        return ScreenStateConditionChecker(this, context)
    }
}

/**
 * Screen state condition checker implementation
 */
class ScreenStateConditionChecker(
    private val condition: ScreenStateCondition,
    private val context: Context
) : ConditionChecker {
    
    companion object {
        private const val TAG = "ScreenStateCondition"
    }
    
    override fun shouldAllowNotification(context: Context): Boolean {
        if (!condition.enabled) {
            return true // No restriction if disabled
        }
        
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            
            // Check if screen is on
            val isScreenOn = display.state == android.view.Display.STATE_ON
            
            val shouldAllow = if (condition.onlyWhenScreenOff) {
                !isScreenOn // Allow only when screen is OFF
            } else {
                isScreenOn // Allow only when screen is ON
            }
            
            if (shouldAllow) {
                Log.d(TAG, "Screen state condition passed: screen is ${if (isScreenOn) "ON" else "OFF"}")
                InAppLogger.logFilter("Screen state condition: Screen is ${if (isScreenOn) "ON" else "OFF"}")
            } else {
                Log.d(TAG, "Screen state condition failed: screen is ${if (isScreenOn) "ON" else "OFF"}")
                InAppLogger.logFilter("Screen state condition: Screen is ${if (isScreenOn) "ON" else "OFF"}")
            }
            
            return shouldAllow
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking screen state condition", e)
            InAppLogger.logError("ScreenStateCondition", "Error checking screen state: ${e.message}")
            // Fail-safe: allow notification if we can't check screen state
            return true
        }
    }
    
    override fun getConditionName(): String {
        return "Screen State"
    }
    
    override fun getConditionDescription(): String {
        return if (condition.onlyWhenScreenOff) {
            "Only when screen is off"
        } else {
            "Only when screen is on"
        }
    }
    
    override fun isEnabled(): Boolean {
        return condition.enabled
    }
    
    override fun getLogMessage(): String {
        return if (condition.onlyWhenScreenOff) {
            "Screen state condition: Screen is on (only allowed when off)"
        } else {
            "Screen state condition: Screen is off (only allowed when on)"
        }
    }
} 