/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess


object InAppLogger {
    private val logs = CopyOnWriteArrayList<LogEntry>()
    private const val MAX_LOGS = 500 // Keep last 500 log entries
    private const val CRASH_LOG_FILENAME = "crash_logs.txt"
    private const val PERSISTENT_LOG_FILENAME = "persistent_logs.txt"
    private const val PERSISTENT_LOG_TEMP_FILENAME = "persistent_logs.txt.tmp"

    // Hard cap on a single entry's message. Some diagnostics (e.g. full rule JSON, code point
    // dumps) can be several KB each; without a cap 500 entries can grow to megabytes.
    private const val MAX_MESSAGE_LENGTH = 2000

    // The persistent file is rewritten (never appended to) from the in-memory buffer, so it is
    // naturally bounded to roughly MAX_LOGS * MAX_MESSAGE_LENGTH. Anything bigger than this on
    // disk is stale or corrupt (e.g. left behind by an older version) and is discarded unread.
    private const val MAX_PERSISTENT_LOG_FILE_BYTES = 4L * 1024 * 1024

    // Save to disk after this many new log entries (coalesced, see requestPersistentSave).
    private const val SAVE_EVERY_N_ENTRIES = 100
    private const val MAX_RECOVERED_LOGS = MAX_LOGS / 2
    private const val TRUNCATION_SUFFIX = "... [truncated]"
    private val RECOVERY_PREFIX_REGEX = Regex("^\\[[^\\]]*\\] I/Recovery: Recovered: ")

    private val entriesSinceLastSave = AtomicInteger(0)
    private val saveScheduled = AtomicBoolean(false)

    // Single background writer so there can only ever be one save in flight.
    private val writerExecutor by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "InAppLogger-Writer").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        }
    }
    
    private var appContext: Context? = null
    private var isInitialized = false
    
    @JvmField
    var verboseMode = true
    
    @JvmField
    var logFilters = true
    
    @JvmField
    var logNotifications = true
    
    @JvmField
    var logUserActions = true
    
    @JvmField
    var logSystemEvents = true
    
    private data class LogEntry(
        val timestamp: String,
        val tag: String,
        val message: String,
        val level: String
    ) {
        constructor(tag: String, message: String, level: String) : this(
            timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
            tag = tag,
            message = message,
            level = level
        )
        
        override fun toString(): String {
            return "[$timestamp] $level/$tag: $message"
        }
    }
    
    @JvmStatic
    @Synchronized
    fun initialize(context: Context) {
        if (!isInitialized) {
            val applicationContext = context.applicationContext

            // Recover logs from the previous session BEFORE publishing appContext. While
            // appContext is null, persistent saves are no-ops, so no background writer can touch
            // the file while we are reading and replacing it.
            loadPersistentLogs(applicationContext)

            appContext = applicationContext
            isInitialized = true

            // Set up uncaught exception handler for crash logging
            setupCrashHandler()
            
            // Apply stored logging preferences so switches persist across restarts
            loadStoredLoggingPrefs(context.applicationContext)
            
            log("Logger", "InAppLogger initialized with crash persistence")
        }
    }
    
    private fun loadStoredLoggingPrefs(context: Context) {
        val prefs = context.getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
        verboseMode = prefs.getBoolean("verbose_logging", verboseMode)
        logFilters = prefs.getBoolean("log_filters", logFilters)
        logNotifications = prefs.getBoolean("log_notifications", logNotifications)
        logUserActions = prefs.getBoolean("log_user_actions", logUserActions)
        logSystemEvents = prefs.getBoolean("log_system_events", logSystemEvents)
        
        log(
            "Logger",
            "Logging prefs loaded: Verbose=$verboseMode, Filters=$logFilters, Notifications=$logNotifications, UserActions=$logUserActions, SystemEvents=$logSystemEvents"
        )
    }
    
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                // Log the crash
                logCrash(exception, "Uncaught exception in thread: ${thread.name}")
                
                // Save all logs immediately before crash. Skipped for OutOfMemoryError: the heap
                // is exhausted, the crash report below already contains the logs, and extra work
                // here only risks a second failure.
                if (exception !is OutOfMemoryError) {
                    writePersistentLogsNow()
                }
                
                // Save crash-specific log
                saveCrashLog(exception, thread)
                
                log("Crash", "Crash logs saved successfully")
                
            } catch (e: Throwable) {
                // Throwable (not Exception) so a secondary OutOfMemoryError while reporting
                // cannot prevent the original handler from running.
                try {
                    Log.e("SpeakThat_CrashHandler", "Failed to save crash logs", e)
                } catch (ignored: Throwable) {
                }
            } finally {
                // Call the original handler to maintain normal crash behavior
                defaultHandler?.uncaughtException(thread, exception)
            }
        }
    }
    
    private fun saveCrashLog(exception: Throwable, thread: Thread) {
        appContext?.let { context ->
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val timestampForFilename = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                
                // Create comprehensive crash log content
                val crashLogContent = buildString {
                    appendLine("SpeakThat! Crash Report")
                    appendLine("Generated: $timestamp")
                    appendLine("App Version: ${getAppVersionInfo(context)}")
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                    appendLine("===========================================")
                    appendLine()
                    appendLine("=== CRASH DETAILS ===")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Exception: ${exception.javaClass.simpleName}")
                    appendLine("Message: ${exception.message}")
                    appendLine("Stack Trace:")
                    exception.stackTrace.forEach { element ->
                        appendLine("  at $element")
                    }
                    appendLine()
                    appendLine("=== APP LOGS AT CRASH ===")
                    appendLine(getAllLogs())
                    appendLine("=== END CRASH LOG ===")
                }
                
                // Save to internal storage (existing behavior)
                val logsDir = File(context.filesDir, "logs")
                if (!logsDir.exists()) {
                    logsDir.mkdirs()
                }
                
                val internalCrashFile = File(logsDir, CRASH_LOG_FILENAME)
                val internalWriter = FileWriter(internalCrashFile, true) // Append mode
                internalWriter.write("\n$crashLogContent\n")
                internalWriter.close()
                
                // Also save to external storage for easy user access
                val externalCrashFilename = "SpeakThat_Crash_$timestampForFilename.txt"
                val externalFile = FileExportHelper.createExportFile(context, "crashes", externalCrashFilename, crashLogContent)
                
                if (externalFile != null) {
                    // Log success for debugging
                    Log.i("SpeakThat_CrashHandler", "Crash log exported to external storage: ${externalFile.absolutePath}")
                } else {
                    Log.w("SpeakThat_CrashHandler", "Failed to export crash log to external storage, but internal log saved")
                }
                
            } catch (e: IOException) {
                Log.e("SpeakThat_CrashHandler", "Failed to write crash log", e)
            }
        }
    }
    
    
    /**
     * Requests an asynchronous save of the in-memory log buffer.
     *
     * Requests are coalesced: if a save is already queued but has not started yet, this is a
     * no-op. A single background thread performs all writes, so there can never be a pile of
     * concurrent writers (which previously exhausted the heap).
     */
    private fun requestPersistentSave() {
        if (appContext == null) return
        if (!saveScheduled.compareAndSet(false, true)) return
        try {
            writerExecutor.execute {
                // Clear the flag first so entries logged while we write schedule another save.
                saveScheduled.set(false)
                writePersistentLogsNow()
            }
        } catch (e: RejectedExecutionException) {
            saveScheduled.set(false)
        } catch (e: Throwable) {
            saveScheduled.set(false)
        }
    }

    /**
     * Writes the current in-memory buffer to disk, replacing the previous file.
     *
     * The file is streamed line by line (no large intermediate strings), written to a temp file
     * and renamed into place so a failure or process death mid-write never leaves a corrupt or
     * oversized file. Never throws: a failed diagnostic write must not crash the app.
     */
    private fun writePersistentLogsNow() {
        val context = appContext ?: return
        try {
            val logsDir = File(context.filesDir, "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }

            val persistentFile = File(logsDir, PERSISTENT_LOG_FILENAME)
            val tempFile = File(logsDir, PERSISTENT_LOG_TEMP_FILENAME)

            // Snapshot to avoid concurrent modification while writing.
            val snapshot = logs.toList()

            BufferedWriter(OutputStreamWriter(FileOutputStream(tempFile, false), Charsets.UTF_8)).use { writer ->
                for (entry in snapshot) {
                    writer.write(entry.toString())
                    writer.newLine()
                }
            }

            if (!tempFile.renameTo(persistentFile)) {
                // Some filesystems refuse to rename over an existing file.
                persistentFile.delete()
                if (!tempFile.renameTo(persistentFile)) {
                    tempFile.delete()
                }
            }
        } catch (e: Throwable) {
            // Includes OutOfMemoryError: drop this save rather than taking the process down.
            try {
                Log.e("SpeakThat_PersistentLogs", "Failed to save persistent logs", e)
            } catch (ignored: Throwable) {
            }
        }
    }

    /**
     * Restores log entries saved by the previous session.
     *
     * Safe against oversized/corrupt files: the file size is checked before any read, only the
     * tail is kept in memory while streaming, and the file is removed before entries are
     * replayed. Any failure here is swallowed so logger initialization can never crash the app.
     */
    private fun loadPersistentLogs(context: Context) {
        try {
            val logsDir = File(context.filesDir, "logs")
            val persistentFile = File(logsDir, PERSISTENT_LOG_FILENAME)

            // A leftover temp file is from an interrupted write and is never trusted.
            File(logsDir, PERSISTENT_LOG_TEMP_FILENAME).takeIf { it.exists() }?.delete()

            if (!persistentFile.exists()) return

            val fileSize = persistentFile.length()
            if (fileSize > MAX_PERSISTENT_LOG_FILE_BYTES) {
                // Stale or corrupt (e.g. produced by an older version that appended without
                // bound). Reading it could exhaust the heap, so discard it without reading.
                persistentFile.delete()
                addEntry(
                    "Logger",
                    "Discarded oversized persistent log from previous session ($fileSize bytes)",
                    "W"
                )
                return
            }

            // Stream the file, keeping only the last MAX_RECOVERED_LOGS lines in memory.
            val tail = ArrayDeque<String>(MAX_RECOVERED_LOGS + 1)
            persistentFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    tail.addLast(line)
                    if (tail.size > MAX_RECOVERED_LOGS) {
                        tail.removeFirst()
                    }
                }
            }

            // Remove the file before replaying so a failure below cannot cause a repeat loop.
            persistentFile.delete()

            var recovered = 0
            for (rawLine in tail) {
                if (!rawLine.startsWith("[") || !rawLine.contains("]")) continue

                // Strip wrappers added by earlier recoveries so they don't nest forever.
                var line = rawLine
                while (true) {
                    val stripped = RECOVERY_PREFIX_REGEX.replaceFirst(line, "")
                    if (stripped == line) break
                    line = stripped
                }

                // Added directly (not via log()) so replaying doesn't trigger disk saves.
                addEntry("Recovery", "Recovered: $line", "I")
                recovered++
            }

            addEntry("Logger", "Recovered $recovered log entries from previous session", "I")
        } catch (e: Throwable) {
            try {
                Log.e("SpeakThat_PersistentLogs", "Failed to load persistent logs", e)
            } catch (ignored: Throwable) {
            }
        }
    }
    
    @JvmStatic
    fun getCrashLogs(): String {
        return appContext?.let { context ->
            try {
                val logsDir = File(context.filesDir, "logs")
                val crashFile = File(logsDir, CRASH_LOG_FILENAME)
                
                if (crashFile.exists()) {
                    crashFile.readText()
                } else {
                    "No crash logs found"
                }
            } catch (e: IOException) {
                Log.e("SpeakThat_CrashLogs", "Failed to read crash logs", e)
                "Error reading crash logs: ${e.message}"
            }
        } ?: "No crash logs found"
    }
    
    @JvmStatic
    fun clearCrashLogs() {
        appContext?.let { context ->
            try {
                // Clear internal crash logs
                val logsDir = File(context.filesDir, "logs")
                val crashFile = File(logsDir, CRASH_LOG_FILENAME)
                
                if (crashFile.exists()) {
                    crashFile.delete()
                    log("Logger", "Internal crash logs cleared")
                }
                
                // Clear external crash logs
                clearExternalCrashLogs()
                
            } catch (e: IOException) {
                Log.e("SpeakThat_CrashLogs", "Failed to clear crash logs", e)
            }
            Unit // Explicit return value for let block
        }
    }
    
    @JvmStatic
    fun clearExternalCrashLogs(): Boolean {
        return appContext?.let { context ->
            try {
                val externalDir = context.getExternalFilesDir(null)
                if (externalDir != null) {
                    val crashesDir = File(externalDir, "crashes")
                    if (crashesDir.exists()) {
                        val crashFiles = crashesDir.listFiles()?.filter { it.isFile && it.name.startsWith("SpeakThat_Crash_") }
                        var deletedCount = 0
                        crashFiles?.forEach { file ->
                            if (file.delete()) {
                                deletedCount++
                            }
                        }
                        log("Logger", "External crash logs cleared: $deletedCount files deleted")
                        deletedCount > 0
                    } else {
                        false
                    }
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e("SpeakThat_CrashLogs", "Failed to clear external crash logs", e)
                false
            }
        } ?: false
    }
    
    @JvmStatic
    fun hasCrashLogs(): Boolean {
        return appContext?.let { context ->
            val logsDir = File(context.filesDir, "logs")
            val crashFile = File(logsDir, CRASH_LOG_FILENAME)
            crashFile.exists() && crashFile.length() > 0
        } ?: false
    }
    
    @JvmStatic
    fun hasExternalCrashLogs(): Boolean {
        return appContext?.let { context ->
            try {
                val externalDir = context.getExternalFilesDir(null)
                if (externalDir != null) {
                    val crashesDir = File(externalDir, "crashes")
                    crashesDir.exists() && crashesDir.listFiles()?.isNotEmpty() == true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        } ?: false
    }
    
    @JvmStatic
    fun getExternalCrashLogs(): List<File> {
        return appContext?.let { context ->
            try {
                val externalDir = context.getExternalFilesDir(null)
                if (externalDir != null) {
                    val crashesDir = File(externalDir, "crashes")
                    if (crashesDir.exists()) {
                        crashesDir.listFiles()?.filter { it.isFile && it.name.startsWith("SpeakThat_Crash_") }?.sortedByDescending { it.lastModified() } ?: emptyList()
                    } else {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }
    
    @JvmStatic
    fun getExternalCrashLogsDirectory(): String? {
        return appContext?.let { context ->
            try {
                val externalDir = context.getExternalFilesDir(null)
                if (externalDir != null) {
                    val crashesDir = File(externalDir, "crashes")
                    crashesDir.absolutePath
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    @JvmStatic
    fun log(tag: String, message: String) {
        log(tag, message, "I")
    }
    
    @JvmStatic
    fun logDebug(tag: String, message: String) {
        if (verboseMode) {
            log(tag, message, "D")
        }
    }
    
    @JvmStatic
    fun logWarning(tag: String, message: String) {
        log(tag, message, "W")
    }
    
    @JvmStatic
    fun logError(tag: String, message: String) {
        log(tag, message, "E")
    }
    
    @JvmStatic
    fun logFilter(message: String) {
        if (logFilters) {
            log("Filter", message, "F")
        }
    }
    
    @JvmStatic
    fun logNotification(message: String) {
        if (logNotifications) {
            log("Notification", message, "N")
        }
    }
    
    @JvmStatic
    fun logUserAction(action: String, details: String = "") {
        if (logUserActions) {
            val message = if (details.isNotEmpty()) "$action - $details" else action
            log("UserAction", message, "U")
        }
    }
    
    @JvmStatic
    fun logSystemEvent(event: String, details: String = "") {
        if (logSystemEvents) {
            val message = if (details.isNotEmpty()) "$event - $details" else event
            log("SystemEvent", message, "S")
        }
    }
    
    @JvmStatic
    fun logTTSEvent(event: String, details: String = "") {
        val message = if (details.isNotEmpty()) "$event - $details" else event
        log("TTS", message, "T")
    }
    
    @JvmStatic
    fun logSettingsChange(setting: String, oldValue: String, newValue: String) {
        if (logUserActions) {
            log("Settings", "$setting changed from '$oldValue' to '$newValue'", "C")
        }
    }
    
    @JvmStatic
    fun logPermissionEvent(permission: String, granted: Boolean) {
        if (logSystemEvents) {
            log("Permission", "$permission ${if (granted) "granted" else "denied"}", "P")
        }
    }
    
    @JvmStatic
    fun logAppLifecycle(event: String, activity: String = "") {
        if (logSystemEvents) {
            val message = if (activity.isNotEmpty()) "$event - $activity" else event
            log("Lifecycle", message, "L")
        }
    }
    
    @JvmStatic
    fun logCrash(exception: Throwable, context: String = "") {
        val message = if (context.isNotEmpty()) {
            "$context - ${exception.javaClass.simpleName}: ${exception.message}"
        } else {
            "${exception.javaClass.simpleName}: ${exception.message}"
        }
        log("Crash", message, "CRASH")
    }
    
    @JvmStatic
    fun logPerformance(operation: String, durationMs: Long) {
        if (verboseMode) {
            log("Performance", "$operation took ${durationMs}ms", "PERF")
        }
    }
    
    /**
     * Adds an entry to the in-memory buffer (bounded in both count and per-entry size) without
     * triggering a disk save. Returns the (possibly truncated) message that was stored.
     */
    private fun addEntry(tag: String, message: String, level: String): String {
        val boundedMessage = if (message.length > MAX_MESSAGE_LENGTH) {
            message.substring(0, MAX_MESSAGE_LENGTH - TRUNCATION_SUFFIX.length) + TRUNCATION_SUFFIX
        } else {
            message
        }

        synchronized(logs) {
            logs.add(LogEntry(tag, boundedMessage, level))

            // Remove old logs if we exceed the limit
            while (logs.size > MAX_LOGS) {
                logs.removeAt(0)
            }
        }
        return boundedMessage
    }

    private fun log(tag: String, message: String, level: String) {
        val boundedMessage = addEntry(tag, message, level)
        
        // Save logs periodically. A dedicated counter is used rather than logs.size: once the
        // buffer is full its size is pinned at MAX_LOGS, so a size-based check would fire on
        // every single call.
        if (entriesSinceLastSave.incrementAndGet() >= SAVE_EVERY_N_ENTRIES) {
            entriesSinceLastSave.set(0)
            requestPersistentSave()
        }
        
        // Also log to Android's system log for development
        when (level) {
            "D" -> Log.d("SpeakThat_$tag", boundedMessage)
            "W" -> Log.w("SpeakThat_$tag", boundedMessage)
            "E" -> Log.e("SpeakThat_$tag", boundedMessage)
            "F" -> Log.i("SpeakThat_Filter", boundedMessage)
            "N" -> Log.i("SpeakThat_Notification", boundedMessage)
            "U" -> Log.i("SpeakThat_UserAction", boundedMessage)
            "S" -> Log.i("SpeakThat_SystemEvent", boundedMessage)
            "T" -> Log.i("SpeakThat_TTS", boundedMessage)
            "C" -> Log.i("SpeakThat_Settings", boundedMessage)
            "P" -> Log.i("SpeakThat_Permission", boundedMessage)
            "L" -> Log.i("SpeakThat_Lifecycle", boundedMessage)
            "CRASH" -> Log.e("SpeakThat_Crash", boundedMessage)
            "PERF" -> Log.d("SpeakThat_Performance", boundedMessage)
            else -> Log.i("SpeakThat_$tag", boundedMessage)
        }
    }
    
    @JvmStatic
    fun getRecentLogs(count: Int): String {
        val start = maxOf(0, logs.size - count)
        return logs.drop(start).joinToString("\n")
    }
    
    @JvmStatic
    fun getAllLogs(): String {
        return logs.joinToString("\n")
    }
    
    @JvmStatic
    fun getLogsForSupport(): String {
        // Get all logs for support purposes
        return logs.joinToString("\n")
    }
    
    @JvmStatic
    fun getLogsAsJsonArray(): org.json.JSONArray {
        val jsonArray = org.json.JSONArray()
        for (log in logs) {
            val logObj = org.json.JSONObject()
            logObj.put("timestamp", log.timestamp)
            logObj.put("tag", log.tag)
            logObj.put("message", log.message)
            logObj.put("level", log.level)
            jsonArray.put(logObj)
        }
        return jsonArray
    }
    
    @JvmStatic
    fun clear() {
        logs.clear()
        log("Logger", "Logs cleared")
    }
    
    @JvmStatic
    fun setVerboseMode(enabled: Boolean) {
        verboseMode = enabled
        log("Logger", "Verbose mode ${if (enabled) "enabled" else "disabled"}")
    }
    
    @JvmStatic
    fun setLogFilters(enabled: Boolean) {
        logFilters = enabled
        log("Logger", "Filter logging ${if (enabled) "enabled" else "disabled"}")
    }
    
    @JvmStatic
    fun setLogNotifications(enabled: Boolean) {
        logNotifications = enabled
        log("Logger", "Notification logging ${if (enabled) "enabled" else "disabled"}")
    }
    
    @JvmStatic
    fun setLogUserActions(enabled: Boolean) {
        logUserActions = enabled
        log("Logger", "User action logging ${if (enabled) "enabled" else "disabled"}")
    }
    
    @JvmStatic
    fun setLogSystemEvents(enabled: Boolean) {
        logSystemEvents = enabled
        log("Logger", "System event logging ${if (enabled) "enabled" else "disabled"}")
    }
    
    @JvmStatic
    fun getLogCount(): Int {
        return logs.size
    }
    
    @JvmStatic
    @Suppress("DEPRECATION")
    fun getInstallationSource(context: android.content.Context): String {
        return try {
            val installerPackageName = context.packageManager.getInstallerPackageName(context.packageName)
            
            when {
                installerPackageName == "com.android.vending" -> "Google Play Store"
                installerPackageName == "com.amazon.venezia" -> "Amazon Appstore"
                installerPackageName == "com.samsung.android.galaxy.store" -> "Samsung Galaxy Store"
                installerPackageName == "com.huawei.appmarket" -> "Huawei AppGallery"
                installerPackageName == "com.sec.android.app.samsungapps" -> "Samsung Galaxy Store (Legacy)"
                installerPackageName == "com.xiaomi.mipicks" -> "Xiaomi GetApps"
                installerPackageName == "com.oppo.market" -> "OPPO App Market"
                installerPackageName == "com.vivo.appstore" -> "vivo App Store"
                installerPackageName == "com.oneplus.filemanager" -> "OnePlus App Store"
                installerPackageName == "com.mi.globalbrowser" -> "Mi Browser"
                installerPackageName == "com.android.packageinstaller" -> "Direct APK Installation"
                installerPackageName == "com.google.android.packageinstaller" -> "Direct APK Installation"
                installerPackageName == null -> "Unknown/Manual Installation"
                else -> "Other Store: $installerPackageName"
            }
        } catch (e: Exception) {
            "Unknown/Error detecting source"
        }
    }
    
    @JvmStatic
    @Suppress("DEPRECATION")
    fun getAppVersionInfo(context: android.content.Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = packageInfo.versionCode // Use versionCode instead of longVersionCode for API compatibility
            
            // Get build variant information
            val buildVariant = when (BuildConfig.DISTRIBUTION_CHANNEL) {
                "github" -> "GitHub"
                "store" -> "Store"
                "play" -> "Play"
                else -> "Unknown"
            }
            
            "SpeakThat! v$versionName ($buildVariant) (Build $versionCode)"
        } catch (e: Exception) {
            "SpeakThat! (Version Unknown)"
        }
    }
    
    @JvmStatic
    fun getBuildVariantInfo(): String {
        return when (BuildConfig.DISTRIBUTION_CHANNEL) {
            "github" -> "GitHub"
            "store" -> "Store"
            "play" -> "Play"
            else -> "Unknown"
        }
    }
    
    @JvmStatic
    fun getSystemInfo(context: android.content.Context): String {
        val statsSection = buildStatisticsInfo(context)
        return buildString {
            appendLine("=== SYSTEM INFORMATION ===")
            appendLine("Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("App Version: ${getAppVersionInfo(context)}")
            appendLine("Build Variant: ${getBuildVariantInfo()}")
            appendLine("Installation Source: ${getInstallationSource(context)}")
            appendLine("Logging Settings: Verbose=$verboseMode, Filters=$logFilters, Notifications=$logNotifications, UserActions=$logUserActions, SystemEvents=$logSystemEvents")
            appendLine("Total Log Entries: ${logs.size}")
            appendLine("Crash Logs Available: ${hasCrashLogs()}")
            appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("===========================")
            if (statsSection.isNotBlank()) {
                appendLine()
                append(statsSection)
            }
        }.trimEnd()
    }

    private fun buildStatisticsInfo(context: android.content.Context): String {
        return try {
            val statsManager = StatisticsManager.getInstance(context)
            val received = statsManager.getNotificationsReceived()
            val read = statsManager.getNotificationsRead()
            val readoutsInterrupted = statsManager.getReadoutsInterrupted()
            val percentage = statsManager.getPercentageRead()
            val filterReasons = statsManager.getFilterReasons()
            val appsReadList = statsManager.getAppsRead().toList().sorted()
            val appsReadCount = appsReadList.size
            val listenerRebindRequests = statsManager.getListenerRebinds()
            val listenerRebindSkips = statsManager.getListenerRebindsSkipped()
            val listenerRebindRecoveries = statsManager.getListenerRebindsRecovered()
            val logoTaps = statsManager.getLogoTaps()

            buildString {
                appendLine("=== ${context.getString(R.string.statistics_title)} ===")
                appendLine("${context.getString(R.string.statistics_notifications_received)}: $received")
                appendLine("${context.getString(R.string.statistics_notifications_read_label)}: $read")
                appendLine("${context.getString(R.string.statistics_readouts_interrupted)}: $readoutsInterrupted")
                appendLine("${context.getString(R.string.statistics_logo_taps_label)}: $logoTaps")
                appendLine(
                    "${context.getString(R.string.statistics_percentage_read)}: ${
                        String.format(
                            Locale.getDefault(),
                            context.getString(R.string.statistics_percentage_format),
                            percentage
                        )
                    }"
                )
                appendLine("${context.getString(R.string.statistics_listener_rebind_requests)}: $listenerRebindRequests")
                appendLine("${context.getString(R.string.statistics_listener_rebind_skips)}: $listenerRebindSkips")
                appendLine("${context.getString(R.string.statistics_listener_rebind_recoveries)}: $listenerRebindRecoveries")
                appendLine("${context.getString(R.string.statistics_apps_read)}: $appsReadCount")
                if (appsReadList.isNotEmpty()) {
                    appendLine(context.getString(R.string.statistics_apps_read_list_label))
                    appsReadList.forEach { appName ->
                        appendLine("- $appName")
                    }
                } else {
                    appendLine(context.getString(R.string.statistics_no_apps_read))
                }
                if (filterReasons.isNotEmpty()) {
                    appendLine()
                    appendLine("${context.getString(R.string.statistics_filter_reasons)}:")
                    filterReasons.entries
                        .sortedByDescending { it.value }
                        .forEach { (reason, count) ->
                            val reasonLabel = getFilterReasonLabel(context, reason)
                            appendLine(
                                String.format(
                                    Locale.getDefault(),
                                    context.getString(R.string.statistics_filter_count_format),
                                    reasonLabel,
                                    count
                                )
                            )
                        }
                } else {
                    appendLine()
                    appendLine(context.getString(R.string.statistics_no_filter_reasons))
                }
            }.trimEnd()
        } catch (e: Exception) {
            Log.e("InAppLogger", "Failed to build statistics info", e)
            ""
        }
    }

    private fun getFilterReasonLabel(context: android.content.Context, reason: String): String {
        return when (reason) {
            StatisticsManager.FILTER_MASTER_SWITCH -> context.getString(R.string.statistics_filter_reason_master_switch)
            StatisticsManager.FILTER_DND -> context.getString(R.string.statistics_filter_reason_dnd)
            StatisticsManager.FILTER_AUDIO_MODE -> context.getString(R.string.statistics_filter_reason_audio_mode)
            StatisticsManager.FILTER_PHONE_CALLS -> context.getString(R.string.statistics_filter_reason_phone_calls)
            StatisticsManager.FILTER_WORD_FILTERS -> context.getString(R.string.statistics_filter_reason_word_filters)
            StatisticsManager.FILTER_CONDITIONAL_RULES -> context.getString(R.string.statistics_filter_reason_conditional_rules)
            StatisticsManager.FILTER_MEDIA_BEHAVIOR -> context.getString(R.string.statistics_filter_reason_media_behavior)
            StatisticsManager.FILTER_SKIP_MODE -> context.getString(R.string.statistics_filter_reason_skip_mode)
            StatisticsManager.FILTER_APP_LIST -> context.getString(R.string.statistics_filter_reason_app_list)
            StatisticsManager.FILTER_DEDUPLICATION -> context.getString(R.string.statistics_filter_reason_deduplication)
            StatisticsManager.FILTER_DISMISSAL_MEMORY -> context.getString(R.string.statistics_filter_reason_dismissal_memory)
            StatisticsManager.FILTER_GROUP_SUMMARY -> context.getString(R.string.statistics_filter_reason_group_summary)
            StatisticsManager.FILTER_SELF_PACKAGE -> context.getString(R.string.statistics_filter_reason_self_package)
            else -> reason
        }
    }
} 
