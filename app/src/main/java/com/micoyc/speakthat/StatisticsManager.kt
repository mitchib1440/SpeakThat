package com.micoyc.speakthat

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Statistics Manager for tracking notification statistics
 * 
 * This class handles tracking notification statistics including:
 * - Total notifications received
 * - Total notifications read
 * - Filter reasons (why notifications were not read)
 * - Apps that have been read
 * 
 * Features:
 * - Battery-efficient storage using SharedPreferences
 * - Offline-only (no network access)
 * - Minimal memory footprint
 * - Async writes using apply() to minimize battery impact
 */
class StatisticsManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "StatisticsManager"
        private const val PREFS_NAME = "NotificationStatistics"
        
        // Keys for statistics
        private const val KEY_NOTIFICATIONS_RECEIVED = "notifications_received"
        private const val KEY_NOTIFICATIONS_READ = "notifications_read"
        private const val KEY_READOUTS_INTERRUPTED = "readouts_interrupted"
        private const val KEY_FILTER_REASONS = "filter_reasons" // JSON map of reason -> count
        private const val KEY_APPS_READ = "apps_read" // JSON set of app names
        private const val KEY_LISTENER_REBINDS = "listener_rebinds"
        private const val KEY_LISTENER_REBINDS_SKIPPED = "listener_rebinds_skipped"
        private const val KEY_LISTENER_REBINDS_RECOVERED = "listener_rebinds_recovered"
        
        // Filter reason constants
        const val FILTER_MASTER_SWITCH = "master_switch"
        const val FILTER_DND = "do_not_disturb"
        const val FILTER_AUDIO_MODE = "audio_mode"
        const val FILTER_PHONE_CALLS = "phone_calls"
        const val FILTER_WORD_FILTERS = "word_filters"
        const val FILTER_CONDITIONAL_RULES = "conditional_rules"
        const val FILTER_MEDIA_BEHAVIOR = "media_behavior"
        const val FILTER_SKIP_MODE = "skip_mode"
        const val FILTER_APP_LIST = "app_list"
        const val FILTER_DEDUPLICATION = "deduplication"
        const val FILTER_DISMISSAL_MEMORY = "dismissal_memory"
        const val FILTER_GROUP_SUMMARY = "group_summary"
        const val FILTER_SELF_PACKAGE = "self_package"
        
        // Use WeakReference to prevent memory leaks
        private var instanceRef: java.lang.ref.WeakReference<StatisticsManager>? = null
        
        fun getInstance(context: Context): StatisticsManager {
            val instance = instanceRef?.get()
            if (instance != null) {
                return instance
            }
            
            val newInstance = StatisticsManager(context.applicationContext)
            instanceRef = java.lang.ref.WeakReference(newInstance)
            return newInstance
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * Increment the count of notifications received
     */
    fun incrementReceived() {
        val currentCount = prefs.getInt(KEY_NOTIFICATIONS_RECEIVED, 0)
        prefs.edit().putInt(KEY_NOTIFICATIONS_RECEIVED, currentCount + 1).apply()
        
        Log.d(TAG, "Notifications received incremented: ${currentCount + 1}")
    }
    
    /**
     * Increment the count of notifications read and track the app
     */
    fun incrementRead(appName: String) {
        // Increment read count
        val currentCount = prefs.getInt(KEY_NOTIFICATIONS_READ, 0)
        prefs.edit().putInt(KEY_NOTIFICATIONS_READ, currentCount + 1).apply()
        
        // Track app name
        val appsRead = getAppsRead().toMutableSet()
        appsRead.add(appName)
        saveAppsRead(appsRead)
        
        Log.d(TAG, "Notifications read incremented: ${currentCount + 1}, app: $appName")
    }
    
    /**
     * Increment the count of readouts interrupted
     */
    fun incrementReadoutsInterrupted() {
        val currentCount = prefs.getInt(KEY_READOUTS_INTERRUPTED, 0)
        prefs.edit().putInt(KEY_READOUTS_INTERRUPTED, currentCount + 1).apply()
        
        Log.d(TAG, "Readouts interrupted incremented: ${currentCount + 1}")
    }
    
    /**
     * Increment the count for a specific filter reason
     */
    fun incrementFilterReason(reason: String) {
        val filterReasons = getFilterReasons().toMutableMap()
        val currentCount = filterReasons.getOrDefault(reason, 0)
        filterReasons[reason] = currentCount + 1
        saveFilterReasons(filterReasons)
        
        Log.d(TAG, "Filter reason incremented: $reason = ${currentCount + 1}")
    }
    
    /**
     * Get total notifications received
     */
    fun getNotificationsReceived(): Int {
        return prefs.getInt(KEY_NOTIFICATIONS_RECEIVED, 0)
    }
    
    /**
     * Get total notifications read
     */
    fun getNotificationsRead(): Int {
        return prefs.getInt(KEY_NOTIFICATIONS_READ, 0)
    }
    
    /**
     * Get total readouts interrupted
     */
    fun getReadoutsInterrupted(): Int {
        return prefs.getInt(KEY_READOUTS_INTERRUPTED, 0)
    }
    
    /**
     * Get percentage of notifications read (0-100)
     */
    fun getPercentageRead(): Double {
        val received = getNotificationsReceived()
        if (received == 0) return 0.0
        
        val read = getNotificationsRead()
        return (read.toDouble() / received.toDouble()) * 100.0
    }
    
    /**
     * Get filter reasons map (reason -> count)
     */
    fun getFilterReasons(): Map<String, Int> {
        val json = prefs.getString(KEY_FILTER_REASONS, null)
        return if (json != null && json.isNotEmpty()) {
            try {
                val type = object : TypeToken<Map<String, Int>>() {}.type
                gson.fromJson(json, type) ?: emptyMap()
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing filter reasons JSON", e)
                emptyMap()
            }
        } else {
            emptyMap()
        }
    }
    
    /**
     * Get set of apps that have been read
     */
    fun getAppsRead(): Set<String> {
        val json = prefs.getString(KEY_APPS_READ, null)
        return if (json != null && json.isNotEmpty()) {
            try {
                val type = object : TypeToken<Set<String>>() {}.type
                gson.fromJson(json, type) ?: emptySet()
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing apps read JSON", e)
                emptySet()
            }
        } else {
            emptySet()
        }
    }
    
    /**
     * Get all statistics as a map for easy access
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "notifications_received" to getNotificationsReceived(),
            "notifications_read" to getNotificationsRead(),
            "readouts_interrupted" to getReadoutsInterrupted(),
            "listener_rebinds" to getListenerRebinds(),
            "listener_rebinds_skipped" to getListenerRebindsSkipped(),
            "listener_rebinds_recovered" to getListenerRebindsRecovered(),
            "percentage_read" to getPercentageRead(),
            "filter_reasons" to getFilterReasons(),
            "apps_read" to getAppsRead()
        )
    }

    fun incrementListenerRebindRequested() {
        val currentCount = prefs.getInt(KEY_LISTENER_REBINDS, 0)
        prefs.edit().putInt(KEY_LISTENER_REBINDS, currentCount + 1).apply()
        Log.d(TAG, "Listener rebind requested count: ${currentCount + 1}")
    }

    fun incrementListenerRebindSkipped() {
        val currentCount = prefs.getInt(KEY_LISTENER_REBINDS_SKIPPED, 0)
        prefs.edit().putInt(KEY_LISTENER_REBINDS_SKIPPED, currentCount + 1).apply()
        Log.d(TAG, "Listener rebind skipped count: ${currentCount + 1}")
    }

    fun incrementListenerRebindRecovered() {
        val currentCount = prefs.getInt(KEY_LISTENER_REBINDS_RECOVERED, 0)
        prefs.edit().putInt(KEY_LISTENER_REBINDS_RECOVERED, currentCount + 1).apply()
        Log.d(TAG, "Listener rebind recovered count: ${currentCount + 1}")
    }

    fun getListenerRebinds(): Int {
        return prefs.getInt(KEY_LISTENER_REBINDS, 0)
    }

    fun getListenerRebindsSkipped(): Int {
        return prefs.getInt(KEY_LISTENER_REBINDS_SKIPPED, 0)
    }

    fun getListenerRebindsRecovered(): Int {
        return prefs.getInt(KEY_LISTENER_REBINDS_RECOVERED, 0)
    }
    
    /**
     * Reset all statistics
     */
    fun resetStats() {
        prefs.edit()
            .remove(KEY_NOTIFICATIONS_RECEIVED)
            .remove(KEY_NOTIFICATIONS_READ)
            .remove(KEY_READOUTS_INTERRUPTED)
            .remove(KEY_FILTER_REASONS)
            .remove(KEY_APPS_READ)
            .remove(KEY_LISTENER_REBINDS)
            .remove(KEY_LISTENER_REBINDS_SKIPPED)
            .remove(KEY_LISTENER_REBINDS_RECOVERED)
            .apply()
        
        Log.d(TAG, "Statistics reset")
        InAppLogger.log("Statistics", "Statistics reset")
    }
    
    /**
     * Save filter reasons map
     */
    private fun saveFilterReasons(reasons: Map<String, Int>) {
        val json = gson.toJson(reasons)
        prefs.edit().putString(KEY_FILTER_REASONS, json).apply()
    }
    
    /**
     * Save apps read set
     */
    private fun saveAppsRead(apps: Set<String>) {
        val json = gson.toJson(apps)
        prefs.edit().putString(KEY_APPS_READ, json).apply()
    }
}

