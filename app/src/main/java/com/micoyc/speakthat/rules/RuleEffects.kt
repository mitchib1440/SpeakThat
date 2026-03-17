/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat.rules

/**
 * Effects produced by rule evaluation.
 * These are applied per-notification unless stated otherwise.
 */
sealed class Effect {
    data object SkipNotification : Effect()
    data object ForcePrivate : Effect()
    data object OverridePrivate : Effect()
    data class OverrideTtsVoice(
        val language: String,
        val voiceName: String? = null
    ) : Effect()
    data class SetSpeechTemplate(
        val template: String,
        val templateKey: String? = null
    ) : Effect()
    data class SetMediaBehavior(val mode: MediaBehavior) : Effect()
    data class SetGestureEnabled(val gesture: Gesture, val enabled: Boolean) : Effect()
    data class SetMasterSwitch(val enabled: Boolean) : Effect()
}

data class EvaluationOutcome(
    val effects: List<Effect>,
    val matchedRules: List<String>
)

enum class MediaBehavior {
    IGNORE,
    PAUSE,
    DUCK,
    SILENCE
}

enum class Gesture {
    SHAKE,
    WAVE,
    PRESS
}
