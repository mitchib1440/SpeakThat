/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat.utils

import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage

object LanguageDetectorUtil {

    const val DETECTION_CONFIDENCE_THRESHOLD = 0.80f

    data class LanguageDetectionDetails(
        val topLocaleTag: String?,
        val topConfidence: Float?,
        val acceptedForAutoDetect: Boolean,
        val localeHypothesisCount: Int
    )

    fun detectLanguage(context: Context, text: String): String? {
        val d = detectLanguageDetails(context, text)
        return if (d.acceptedForAutoDetect) d.topLocaleTag else null
    }

    /**
     * API-gated before touching Q+ text classifier APIs.
     */
    fun detectLanguageDetails(context: Context, text: String): LanguageDetectionDetails {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return LanguageDetectionDetails(
                topLocaleTag = null,
                topConfidence = null,
                acceptedForAutoDetect = false,
                localeHypothesisCount = 0
            )
        }
        return detectLanguageDetailsImpl(context, text)
    }

    private fun detectLanguageDetailsImpl(context: Context, text: String): LanguageDetectionDetails {
        try {
            val tcm = context.getSystemService(TextClassificationManager::class.java) ?: return LanguageDetectionDetails(
                null,
                null,
                false,
                0
            )
            val textClassifier = tcm.textClassifier
            val request = TextLanguage.Request.Builder(text).build()
            val textLanguage = textClassifier.detectLanguage(request)
            val count = textLanguage.localeHypothesisCount
            if (count <= 0) {
                return LanguageDetectionDetails(null, null, false, 0)
            }
            val topLocale = textLanguage.getLocale(0)
            val tag = topLocale.toLanguageTag()
            val confidence = textLanguage.getConfidenceScore(topLocale)
            val accepted = confidence >= DETECTION_CONFIDENCE_THRESHOLD && tag != "und"
            return LanguageDetectionDetails(
                topLocaleTag = tag,
                topConfidence = confidence,
                acceptedForAutoDetect = accepted,
                localeHypothesisCount = count
            )
        } catch (_: Exception) {
            return LanguageDetectionDetails(null, null, false, 0)
        }
    }
}
