package com.micoyc.speakthat

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
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
data class StatsSnapshot(
    val notificationsReceived: Int,
    val notificationsRead: Int,
    val readoutsInterrupted: Int,
    val listenerRebinds: Int,
    val listenerRebindsSkipped: Int,
    val listenerRebindsRecovered: Int,
    val logoTaps: Int,
    val filterReasons: Map<String, Int>,
    val appsRead: Set<String>
)

class StatisticsManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "StatisticsManager"
        private const val PREFS_NAME = "SpeakThatPrefs"
        private const val LEGACY_PREFS_NAME = "NotificationStatistics"
        private const val KEY_STATS_MIGRATED = "stats_migrated_from_notificationstatistics"
        
        // Keys for statistics
        private const val KEY_NOTIFICATIONS_RECEIVED = "notifications_received"
        private const val KEY_NOTIFICATIONS_READ = "notifications_read"
        private const val KEY_READOUTS_INTERRUPTED = "readouts_interrupted"
        private const val KEY_FILTER_REASONS = "filter_reasons" // JSON map of reason -> count
        private const val KEY_APPS_READ = "apps_read" // JSON set of app names
        private const val KEY_LISTENER_REBINDS = "listener_rebinds"
        private const val KEY_LISTENER_REBINDS_SKIPPED = "listener_rebinds_skipped"
        private const val KEY_LISTENER_REBINDS_RECOVERED = "listener_rebinds_recovered"
        private const val KEY_LOGO_TAPS = "logo_tap_count"
        
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

        @JvmStatic
        fun exportSnapshot(context: Context): StatsSnapshot {
            return getInstance(context).getSnapshot()
        }

        @JvmStatic
        fun importSnapshot(context: Context, snapshot: StatsSnapshot) {
            getInstance(context).overwriteStats(snapshot)
        }

        @JvmStatic
        fun snapshotToJson(snapshot: StatsSnapshot): JSONObject {
            val json = JSONObject()
            json.put("notificationsReceived", snapshot.notificationsReceived)
            json.put("notificationsRead", snapshot.notificationsRead)
            json.put("readoutsInterrupted", snapshot.readoutsInterrupted)
            json.put("listenerRebinds", snapshot.listenerRebinds)
            json.put("listenerRebindsSkipped", snapshot.listenerRebindsSkipped)
            json.put("listenerRebindsRecovered", snapshot.listenerRebindsRecovered)
            json.put("logoTaps", snapshot.logoTaps)
            json.put("filterReasons", JSONObject(snapshot.filterReasons))
            json.put("appsRead", JSONArray(snapshot.appsRead))
            return json
        }

        @JvmStatic
        fun snapshotFromJson(json: JSONObject): StatsSnapshot {
            val filterReasonsObj = json.optJSONObject("filterReasons")
            val filterReasons = mutableMapOf<String, Int>()
            if (filterReasonsObj != null) {
                val keys = filterReasonsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = filterReasonsObj.optInt(key, 0)
                    filterReasons[key] = value
                }
            }

            val appsReadArray = json.optJSONArray("appsRead")
            val appsRead = mutableSetOf<String>()
            if (appsReadArray != null) {
                for (i in 0 until appsReadArray.length()) {
                    val value = appsReadArray.optString(i, null)
                    if (!value.isNullOrEmpty()) {
                        appsRead.add(value)
                    }
                }
            }

            return StatsSnapshot(
                notificationsReceived = json.optInt("notificationsReceived", 0),
                notificationsRead = json.optInt("notificationsRead", 0),
                readoutsInterrupted = json.optInt("readoutsInterrupted", 0),
                listenerRebinds = json.optInt("listenerRebinds", 0),
                listenerRebindsSkipped = json.optInt("listenerRebindsSkipped", 0),
                listenerRebindsRecovered = json.optInt("listenerRebindsRecovered", 0),
                logoTaps = json.optInt("logoTaps", 0),
                filterReasons = filterReasons,
                appsRead = appsRead
            )
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        migrateLegacyPrefsIfNeeded()
    }
    
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
        
        val updatedCount = currentCount + 1
        Log.d(TAG, "Notifications read incremented: $updatedCount, app: $appName")
        InAppLogger.log("Statistics", "Notification read from $appName (total reads: $updatedCount)")
    }

    /**
     * Increment total SpeakThat logo taps and return updated count
     */
    fun incrementLogoTaps(): Int {
        val currentCount = prefs.getInt(KEY_LOGO_TAPS, 0) + 1
        prefs.edit().putInt(KEY_LOGO_TAPS, currentCount).apply()
        
        Log.d(TAG, "Logo taps incremented: $currentCount")
        InAppLogger.log("Statistics", "SpeakThat logo tapped (total taps: $currentCount)")
        return currentCount
    }

    /**
     * Get total SpeakThat logo taps
     */
    fun getLogoTaps(): Int {
        return prefs.getInt(KEY_LOGO_TAPS, 0)
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
        val snapshot = getSnapshot()
        return mapOf(
            "notifications_received" to snapshot.notificationsReceived,
            "notifications_read" to snapshot.notificationsRead,
            "readouts_interrupted" to snapshot.readoutsInterrupted,
            "listener_rebinds" to snapshot.listenerRebinds,
            "listener_rebinds_skipped" to snapshot.listenerRebindsSkipped,
            "listener_rebinds_recovered" to snapshot.listenerRebindsRecovered,
            "percentage_read" to getPercentageRead(),
            "logo_taps" to snapshot.logoTaps,
            "filter_reasons" to snapshot.filterReasons,
            "apps_read" to snapshot.appsRead
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
            .remove(KEY_LOGO_TAPS)
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

    private fun migrateLegacyPrefsIfNeeded() {
        val alreadyMigrated = prefs.getBoolean(KEY_STATS_MIGRATED, false)
        if (alreadyMigrated) return

        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val hasLegacyData = legacyPrefs.contains(KEY_NOTIFICATIONS_RECEIVED) ||
            legacyPrefs.contains(KEY_NOTIFICATIONS_READ) ||
            legacyPrefs.contains(KEY_READOUTS_INTERRUPTED) ||
            legacyPrefs.contains(KEY_FILTER_REASONS) ||
            legacyPrefs.contains(KEY_APPS_READ) ||
            legacyPrefs.contains(KEY_LISTENER_REBINDS) ||
            legacyPrefs.contains(KEY_LISTENER_REBINDS_SKIPPED) ||
            legacyPrefs.contains(KEY_LISTENER_REBINDS_RECOVERED) ||
            legacyPrefs.contains(KEY_LOGO_TAPS)

        if (!hasLegacyData) {
            prefs.edit().putBoolean(KEY_STATS_MIGRATED, true).apply()
            return
        }

        val legacySnapshot = readSnapshotFromPrefs(legacyPrefs)
        overwriteStats(legacySnapshot)
        prefs.edit().putBoolean(KEY_STATS_MIGRATED, true).apply()
        legacyPrefs.edit().clear().apply()

        InAppLogger.log("Statistics", "Migrated statistics from legacy NotificationStatistics prefs to SpeakThatPrefs")
    }

    private fun getSnapshot(): StatsSnapshot {
        return readSnapshotFromPrefs(prefs)
    }

    private fun overwriteStats(snapshot: StatsSnapshot) {
        val editor = prefs.edit()
        editor.putInt(KEY_NOTIFICATIONS_RECEIVED, snapshot.notificationsReceived)
        editor.putInt(KEY_NOTIFICATIONS_READ, snapshot.notificationsRead)
        editor.putInt(KEY_READOUTS_INTERRUPTED, snapshot.readoutsInterrupted)
        editor.putInt(KEY_LISTENER_REBINDS, snapshot.listenerRebinds)
        editor.putInt(KEY_LISTENER_REBINDS_SKIPPED, snapshot.listenerRebindsSkipped)
        editor.putInt(KEY_LISTENER_REBINDS_RECOVERED, snapshot.listenerRebindsRecovered)
        editor.putInt(KEY_LOGO_TAPS, snapshot.logoTaps)

        val filterReasonsJson = gson.toJson(snapshot.filterReasons)
        editor.putString(KEY_FILTER_REASONS, filterReasonsJson)

        val appsReadJson = gson.toJson(snapshot.appsRead)
        editor.putString(KEY_APPS_READ, appsReadJson)

        editor.apply()
    }

    private fun readSnapshotFromPrefs(sourcePrefs: SharedPreferences): StatsSnapshot {
        val filterReasons = try {
            val json = sourcePrefs.getString(KEY_FILTER_REASONS, null)
            if (json.isNullOrEmpty()) {
                emptyMap()
            } else {
                val type = object : TypeToken<Map<String, Int>>() {}.type
                gson.fromJson<Map<String, Int>>(json, type) ?: emptyMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing filter reasons JSON during snapshot read", e)
            emptyMap()
        }

        val appsRead = try {
            val json = sourcePrefs.getString(KEY_APPS_READ, null)
            if (json.isNullOrEmpty()) {
                emptySet()
            } else {
                val type = object : TypeToken<Set<String>>() {}.type
                gson.fromJson<Set<String>>(json, type) ?: emptySet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing apps read JSON during snapshot read", e)
            emptySet()
        }

        return StatsSnapshot(
            notificationsReceived = sourcePrefs.getInt(KEY_NOTIFICATIONS_RECEIVED, 0),
            notificationsRead = sourcePrefs.getInt(KEY_NOTIFICATIONS_READ, 0),
            readoutsInterrupted = sourcePrefs.getInt(KEY_READOUTS_INTERRUPTED, 0),
            listenerRebinds = sourcePrefs.getInt(KEY_LISTENER_REBINDS, 0),
            listenerRebindsSkipped = sourcePrefs.getInt(KEY_LISTENER_REBINDS_SKIPPED, 0),
            listenerRebindsRecovered = sourcePrefs.getInt(KEY_LISTENER_REBINDS_RECOVERED, 0),
            logoTaps = sourcePrefs.getInt(KEY_LOGO_TAPS, 0),
            filterReasons = filterReasons,
            appsRead = appsRead
        )
    }
}

