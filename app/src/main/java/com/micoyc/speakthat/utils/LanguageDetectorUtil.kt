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
    fun detectLanguage(context: Context, text: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }

        try {
            val tcm = context.getSystemService(TextClassificationManager::class.java) ?: return null
            val textClassifier = tcm.textClassifier
            val request = TextLanguage.Request.Builder(text).build()
            val textLanguage = textClassifier.detectLanguage(request)

            if (textLanguage.localeHypothesisCount > 0) {
                val topLocale = textLanguage.getLocale(0)
                val confidence = textLanguage.getConfidenceScore(topLocale)
                if (confidence >= 0.80f && topLocale.toLanguageTag() != "und") {
                    return topLocale.toLanguageTag()
                }
            }
        } catch (e: Exception) {
            // Ignore classification failures
        }

        return null
    }
}