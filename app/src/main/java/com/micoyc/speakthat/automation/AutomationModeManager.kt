package com.micoyc.speakthat.automation

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.rules.RuleManager

/**
 * Centralized controller that keeps the automation mode persisted, enforces the
 * mutually exclusive behavior, and toggles supporting components (rules engine
 * vs broadcast receiver).
 */
class AutomationModeManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "SpeakThatPrefs"
        private const val KEY_AUTOMATION_MODE = "automation_mode"
        private const val KEY_RULES_ENABLED_LEGACY = "rules_enabled"

        const val ACTION_ENABLE_SPEAKTHAT =
            "com.micoyc.speakthat.intent.ACTION_ENABLE_SPEAKTHAT"
        const val ACTION_DISABLE_SPEAKTHAT =
            "com.micoyc.speakthat.intent.ACTION_DISABLE_SPEAKTHAT"
    }

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val ruleManager = RuleManager(appContext)

    fun getMode(): AutomationMode {
        val stored = prefs.getString(KEY_AUTOMATION_MODE, null)
        val currentMode = if (stored.isNullOrEmpty()) {
            detectLegacyMode()
        } else {
            AutomationMode.fromPrefValue(stored)
        }
        return currentMode
    }

    fun setMode(mode: AutomationMode) {
        val previous = getMode()
        if (previous == mode) {
            // Still re-apply once to guarantee receiver + rule state (safety)
            applyMode(mode, previousChanged = false)
            return
        }

        prefs.edit()
            .putString(KEY_AUTOMATION_MODE, mode.prefValue)
            .putBoolean(KEY_RULES_ENABLED_LEGACY, mode == AutomationMode.CONDITIONAL_RULES)
            .apply()

        applyMode(mode, previousChanged = true, previous = previous)
    }

    private fun detectLegacyMode(): AutomationMode {
        val legacyFlag = prefs.getBoolean(KEY_RULES_ENABLED_LEGACY, false)
        val rulesActive = runCatching { ruleManager.isRulesEnabled() }.getOrDefault(false)
        return if (legacyFlag || rulesActive) {
            AutomationMode.CONDITIONAL_RULES
        } else {
            AutomationMode.OFF
        }
    }

    private fun applyMode(
        mode: AutomationMode,
        previousChanged: Boolean,
        previous: AutomationMode = mode
    ) {
        when (mode) {
            AutomationMode.OFF -> {
                ruleManager.setRulesEnabled(false)
                setReceiverEnabled(false)
                InAppLogger.logSystemEvent("AutomationMode", "Mode set to OFF")
            }
            AutomationMode.CONDITIONAL_RULES -> {
                ruleManager.setRulesEnabled(true)
                setReceiverEnabled(false)
                InAppLogger.logSystemEvent(
                    "AutomationMode",
                    "Mode set to CONDITIONAL_RULES - receiver disabled"
                )
            }
            AutomationMode.EXTERNAL_AUTOMATION -> {
                ruleManager.setRulesEnabled(false)
                setReceiverEnabled(true)
                InAppLogger.logSystemEvent(
                    "AutomationMode",
                    "Mode set to EXTERNAL_AUTOMATION - receiver enabled"
                )
            }
        }

        if (previousChanged) {
            InAppLogger.logSettingsChange(
                "AutomationMode",
                previous.name,
                mode.name
            )
        }
    }

    private fun setReceiverEnabled(enabled: Boolean) {
        val component = ComponentName(appContext, AutomationBroadcastReceiver::class.java)
        val packageManager = appContext.packageManager
        val desiredState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        val currentState = packageManager.getComponentEnabledSetting(component)
        if (currentState == desiredState) {
            return
        }

        packageManager.setComponentEnabledSetting(
            component,
            desiredState,
            PackageManager.DONT_KILL_APP
        )
    }
}

