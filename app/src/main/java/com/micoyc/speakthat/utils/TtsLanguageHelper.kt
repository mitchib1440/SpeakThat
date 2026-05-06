/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.speech.tts.TextToSpeech
import com.micoyc.speakthat.InAppLogger
import java.util.Locale

/** VoiceSettings prefs; keep in sync with [com.micoyc.speakthat.VoiceSettingsActivity]. */
object TtsLanguageHelper {

    const val PREFS_VOICE_SETTINGS = "VoiceSettings"
    const val KEY_AUTO_DETECT_LANGUAGE = "auto_detect_language"
    const val KEY_LANGUAGE = "language"
    private const val DEFAULT_LANGUAGE = "en_US"

    data class AutoLanguageApplyResult(
        val overrideApplied: Boolean,
        val autoDetectEnabled: Boolean,
        val skippedLowApi: Boolean,
        /** True when classifier returned a hypothesis that did not meet confidence / und guard. */
        val rejectedClassifier: Boolean,
        /** True when we had an accepted classification but skipped apply to preserve global accent. */
        val accentPreservationSkipped: Boolean,
        val topDetectedTag: String?,
        val topConfidence: Float?,
        val acceptedForAutoDetect: Boolean,
        /** Tag that would apply if accent allowed (same as top when accepted). */
        val effectiveDetectedTag: String?
    )

    /**
     * Must be called after global [com.micoyc.speakthat.tts.SpeakThatTtsManager.applyVoiceSettings].
     * Optionally applies temporary [TextToSpeech.setLanguage] when auto-detect is on (Q+) and accents differ.
     */
    fun tryApplyAutoDetectLanguage(
        context: Context,
        voiceSettingsPrefs: SharedPreferences,
        tts: TextToSpeech?,
        speechText: String
    ): AutoLanguageApplyResult {
        val autoDetectEnabled = voiceSettingsPrefs.getBoolean(KEY_AUTO_DETECT_LANGUAGE, false)
        if (tts == null) {
            return emptyResult(autoDetectEnabled, skippedLowApi = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        }
        if (!autoDetectEnabled) {
            return emptyResult(autoDetectEnabled, skippedLowApi = false)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return emptyResult(autoDetectEnabled, skippedLowApi = true)
        }

        val details = LanguageDetectorUtil.detectLanguageDetails(context, speechText)
        val accepted = details.acceptedForAutoDetect
        val tag = details.topLocaleTag
        val confidence = details.topConfidence

        if (!accepted || tag.isNullOrBlank()) {
            return AutoLanguageApplyResult(
                overrideApplied = false,
                autoDetectEnabled = true,
                skippedLowApi = false,
                rejectedClassifier = true,
                accentPreservationSkipped = false,
                topDetectedTag = tag,
                topConfidence = confidence,
                acceptedForAutoDetect = false,
                effectiveDetectedTag = null
            )
        }

        val globalLangPref = voiceSettingsPrefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        val globalBaseLang = globalLangPref.split("_", "-").firstOrNull()?.lowercase().orEmpty()
        val detectedBaseLang = tag.split("_", "-").firstOrNull()?.lowercase().orEmpty()

        if (detectedBaseLang == globalBaseLang) {
            return AutoLanguageApplyResult(
                overrideApplied = false,
                autoDetectEnabled = true,
                skippedLowApi = false,
                rejectedClassifier = false,
                accentPreservationSkipped = true,
                topDetectedTag = tag,
                topConfidence = confidence,
                acceptedForAutoDetect = true,
                effectiveDetectedTag = tag
            )
        }

        val applied = applyTemporaryLanguageTag(tts, tag)
        return AutoLanguageApplyResult(
            overrideApplied = applied,
            autoDetectEnabled = true,
            skippedLowApi = false,
            rejectedClassifier = false,
            accentPreservationSkipped = false,
            topDetectedTag = tag,
            topConfidence = confidence,
            acceptedForAutoDetect = true,
            effectiveDetectedTag = tag
        )
    }

    private fun emptyResult(autoDetectEnabled: Boolean, skippedLowApi: Boolean) = AutoLanguageApplyResult(
        overrideApplied = false,
        autoDetectEnabled = autoDetectEnabled,
        skippedLowApi = skippedLowApi,
        rejectedClassifier = false,
        accentPreservationSkipped = false,
        topDetectedTag = null,
        topConfidence = null,
        acceptedForAutoDetect = false,
        effectiveDetectedTag = null
    )

    /** Language-only temporary override ([voiceName] "" semantics). Mirrors NotificationReaderService.parse + setLanguage. */
    fun applyTemporaryLanguageTag(tts: TextToSpeech, languageTag: String): Boolean {
        val targetLocale = parseTemporaryLanguageLocale(languageTag) ?: return false
        val languageResult = tts.setLanguage(targetLocale)
        InAppLogger.log(
            "TtsLanguageHelper",
            "Applied temporary language $languageTag (result=$languageResult)"
        )
        return true
    }

    private fun parseTemporaryLanguageLocale(languageCode: String): Locale? {
        val normalized = languageCode.trim()
        if (normalized.isEmpty()) return null
        val languageTag = normalized.replace('_', '-')
        val locale = Locale.forLanguageTag(languageTag)
        return if (locale.language.isNullOrBlank()) null else locale
    }
}
