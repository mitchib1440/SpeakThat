package com.micoyc.speakthat.automation

/**
 * Represents the mutually exclusive automation strategies SpeakThat can use.
 *
 * OFF                -> No automation touches the master switch.
 * CONDITIONAL_RULES  -> Use the built-in Conditional Rules engine.
 * EXTERNAL_AUTOMATION-> Accept intents from automation apps (MacroDroid, Tasker, etc.).
 */
enum class AutomationMode(val prefValue: String) {
    OFF("off"),
    CONDITIONAL_RULES("conditional_rules"),
    EXTERNAL_AUTOMATION("external_automation");

    companion object {
        fun fromPrefValue(value: String?): AutomationMode {
            return entries.firstOrNull { it.prefValue == value } ?: OFF
        }
    }
}

