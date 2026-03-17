/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

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

