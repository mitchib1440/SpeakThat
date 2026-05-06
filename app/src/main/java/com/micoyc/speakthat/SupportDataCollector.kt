/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SupportDataCollector {

    private val PREF_FILES = listOf(
        "SpeakThatPrefs",
        "VoiceSettings",
        "RuleMigrationPrefs",
        "UpdatePrefs",
        "play_donations",
        "SpeakThatRules",
        "SummarySettings",
        "SummaryOverlayPrefs",
        "StatisticsSettings",
        "StatisticsLegacy",
        "SpeakThatBehavior"
    )

    fun collectSupportData(context: Context): String {
        val rootObject = JSONObject()

        // 1. System Info
        rootObject.put("system_info", buildSystemInfoJson(context))

        // 2. SharedPreferences (Sanitized)
        rootObject.put("shared_preferences", buildSharedPreferencesJson(context))

        // 3. Event Logs
        rootObject.put("event_logs", InAppLogger.getLogsAsJsonArray())

        return rootObject.toString(2)
    }

    private fun buildSystemInfoJson(context: Context): JSONObject {
        val systemInfo = JSONObject()
        systemInfo.put("android_version", Build.VERSION.RELEASE)
        systemInfo.put("api_level", Build.VERSION.SDK_INT)
        systemInfo.put("device_manufacturer", Build.MANUFACTURER)
        systemInfo.put("device_model", Build.MODEL)
        
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            systemInfo.put("app_version", packageInfo.versionName)
            systemInfo.put("app_version_code", packageInfo.versionCode)
        } catch (e: Exception) {
            systemInfo.put("app_version", "Unknown")
        }

        systemInfo.put("build_variant", BuildConfig.DISTRIBUTION_CHANNEL)
        systemInfo.put("installation_source", InAppLogger.getInstallationSource(context))
        systemInfo.put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        
        return systemInfo
    }

    private fun buildSharedPreferencesJson(context: Context): JSONObject {
        val prefsObject = JSONObject()

        for (prefFileName in PREF_FILES) {
            val prefs = context.getSharedPreferences(prefFileName, Context.MODE_PRIVATE)
            val allEntries = prefs.all

            if (allEntries.isEmpty()) {
                continue // Skip empty or missing files
            }

            val fileObject = JSONObject()
            for ((key, value) in allEntries) {
                fileObject.put(key, sanitizeValue(key, value))
            }
            prefsObject.put(prefFileName, fileObject)
        }

        return prefsObject
    }

    private fun sanitizeValue(key: String, value: Any?): Any? {
        if (value == null) return null

        val lowerKey = key.lowercase()

        // 1. Fixing the Mode mix-up
        if (lowerKey.contains("mode")) {
            return value
        }

        // 2. Key Name Matching for Lists & Replacements
        if (lowerKey.contains("blacklist") || 
            lowerKey.contains("whitelist") ||
            lowerKey.contains("custom_app") ||
            lowerKey.contains("app_private") ||
            lowerKey.contains("replacements") ||
            lowerKey.contains("app_list") ||
            lowerKey.contains("cooldown") ||
            lowerKey.contains("priority")) {
            
            if (value is Set<*>) {
                return "[REDACTED - ${value.size} items]"
            } else if (value is String) {
                try {
                    val jsonArray = JSONArray(value)
                    return "[REDACTED - ${jsonArray.length()} items]"
                } catch (e: Exception) {
                    val count = value.split(",").filter { it.isNotBlank() }.size
                    return "[REDACTED - $count items]"
                }
            }
        }

        // 3. Deep Parsing for SpeakThatRules
        if (key == "rules_list" && value is String) {
            try {
                val rulesArray = JSONArray(value)
                for (i in 0 until rulesArray.length()) {
                    val ruleObj = rulesArray.getJSONObject(i)
                    if (ruleObj.has("name")) {
                        ruleObj.put("name", "[REDACTED]")
                    }
                    
                    if (ruleObj.has("triggers")) {
                        val triggersArray = ruleObj.getJSONArray("triggers")
                        for (j in 0 until triggersArray.length()) {
                            val triggerObj = triggersArray.getJSONObject(j)
                            if (triggerObj.has("data")) {
                                val dataObj = triggerObj.getJSONObject("data")
                                if (dataObj.has("phrase")) {
                                    dataObj.put("phrase", "[REDACTED]")
                                }
                            }
                        }
                    }
                    
                    if (ruleObj.has("exceptions")) {
                        val exceptionsArray = ruleObj.getJSONArray("exceptions")
                        for (j in 0 until exceptionsArray.length()) {
                            val exceptionObj = exceptionsArray.getJSONObject(j)
                            if (exceptionObj.has("data")) {
                                val dataObj = exceptionObj.getJSONObject("data")
                                if (dataObj.has("phrase")) {
                                    dataObj.put("phrase", "[REDACTED]")
                                }
                            }
                        }
                    }
                }
                return rulesArray.toString()
            } catch (e: Exception) {
                return "[REDACTED - Parsing Failed]"
            }
        }

        // Return the original value for safe keys (booleans, ints, safe strings like theme_mode)
        return value
    }
}
