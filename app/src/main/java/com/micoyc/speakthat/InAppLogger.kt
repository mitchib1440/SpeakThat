package com.micoyc.speakthat

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.system.exitProcess


object InAppLogger {
    private val logs = CopyOnWriteArrayList<LogEntry>()
    private const val MAX_LOGS = 500 // Keep last 500 log entries
    private const val CRASH_LOG_FILENAME = "crash_logs.txt"
    private const val PERSISTENT_LOG_FILENAME = "persistent_logs.txt"
    private const val MAX_PERSISTENT_LOG_SIZE = 1024 * 1024 // 1MB max
    
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
    
    @JvmField
    var logSensitiveData = false // For privacy-sensitive information
    
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
    fun initialize(context: Context) {
        if (!isInitialized) {
            appContext = context.applicationContext
            isInitialized = true
            
            // Set up uncaught exception handler for crash logging
            setupCrashHandler()
            
            // Load any existing persistent logs
            loadPersistentLogs()
            
            log("Logger", "InAppLogger initialized with crash persistence")
        }
    }
    
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                // Log the crash
                logCrash(exception, "Uncaught exception in thread: ${thread.name}")
                
                // Save all logs immediately before crash
                savePersistentLogs()
                
                // Save crash-specific log
                saveCrashLog(exception, thread)
                
                log("Crash", "Crash logs saved successfully")
                
            } catch (e: Exception) {
                Log.e("SpeakThat_CrashHandler", "Failed to save crash logs", e)
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
    
    
    private fun savePersistentLogs() {
        appContext?.let { context ->
            try {
                val logsDir = File(context.filesDir, "logs")
                if (!logsDir.exists()) {
                    logsDir.mkdirs()
                }
                
                val persistentFile = File(logsDir, PERSISTENT_LOG_FILENAME)
                
                // Check file size and truncate if too large
                if (persistentFile.exists() && persistentFile.length() > MAX_PERSISTENT_LOG_SIZE) {
                    // Keep only the last 50% of the file
                    val content = persistentFile.readText()
                    val lines = content.lines()
                    val keepLines = lines.takeLast(lines.size / 2)
                    persistentFile.writeText(keepLines.joinToString("\n"))
                }
                
                val writer = FileWriter(persistentFile, true) // Append mode
                
                // Write new logs
                logs.forEach { logEntry ->
                    writer.write("${logEntry}\n")
                }
                
                writer.close()
                
            } catch (e: IOException) {
                Log.e("SpeakThat_PersistentLogs", "Failed to save persistent logs", e)
            }
            Unit // Explicit return value for let block
        }
    }
    
    private fun loadPersistentLogs() {
        appContext?.let { context ->
            try {
                val logsDir = File(context.filesDir, "logs")
                val persistentFile = File(logsDir, PERSISTENT_LOG_FILENAME)
                
                if (persistentFile.exists()) {
                    val content = persistentFile.readText()
                    val lines = content.lines().filter { it.isNotBlank() }
                    
                    // Parse and add to current logs (up to MAX_LOGS limit)
                    val recoveredLogs = lines.takeLast(MAX_LOGS / 2) // Leave room for new logs
                    
                    recoveredLogs.forEach { line ->
                        if (line.startsWith("[") && line.contains("]")) {
                            // This is a recovered log entry - add it with a special marker
                            log("Recovery", "Recovered: $line")
                        }
                    }
                    
                    // Clear the persistent file after recovery
                    persistentFile.delete()
                    
                    log("Logger", "Recovered ${recoveredLogs.size} log entries from previous session")
                }
                
            } catch (e: IOException) {
                Log.e("SpeakThat_PersistentLogs", "Failed to load persistent logs", e)
            }
            Unit // Explicit return value for let block
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
    fun logSensitive(tag: String, message: String) {
        if (logSensitiveData) {
            log(tag, message, "SENSITIVE")
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
    
    private fun log(tag: String, message: String, level: String) {
        // Add new log entry
        logs.add(LogEntry(tag, message, level))
        
        // Remove old logs if we exceed the limit
        while (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
        
        // Save logs periodically (every 100 entries for performance)
        if (logs.size % 100 == 0) {
            savePersistentLogs()
        }
        
        // Also log to Android's system log for development
        when (level) {
            "D" -> Log.d("SpeakThat_$tag", message)
            "W" -> Log.w("SpeakThat_$tag", message)
            "E" -> Log.e("SpeakThat_$tag", message)
            "F" -> Log.i("SpeakThat_Filter", message)
            "N" -> Log.i("SpeakThat_Notification", message)
            "U" -> Log.i("SpeakThat_UserAction", message)
            "S" -> Log.i("SpeakThat_SystemEvent", message)
            "T" -> Log.i("SpeakThat_TTS", message)
            "C" -> Log.i("SpeakThat_Settings", message)
            "P" -> Log.i("SpeakThat_Permission", message)
            "L" -> Log.i("SpeakThat_Lifecycle", message)
            "SENSITIVE" -> Log.i("SpeakThat_Sensitive", message)
            "CRASH" -> Log.e("SpeakThat_Crash", message)
            "PERF" -> Log.d("SpeakThat_Performance", message)
            else -> Log.i("SpeakThat_$tag", message)
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
        // Get logs excluding sensitive data for support purposes
        return logs.filter { it.level != "SENSITIVE" }.joinToString("\n")
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
    fun setLogSensitiveData(enabled: Boolean) {
        logSensitiveData = enabled
        log("Logger", "Sensitive data logging ${if (enabled) "enabled" else "disabled"}")
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
            val buildVariant = when {
                BuildConfig.DISTRIBUTION_CHANNEL == "github" -> "GitHub"
                BuildConfig.DISTRIBUTION_CHANNEL == "store" -> "Store"
                else -> "Unknown"
            }
            
            "SpeakThat! v$versionName ($buildVariant) (Build $versionCode)"
        } catch (e: Exception) {
            "SpeakThat! (Version Unknown)"
        }
    }
    
    @JvmStatic
    fun getBuildVariantInfo(): String {
        return when {
            BuildConfig.DISTRIBUTION_CHANNEL == "github" -> "GitHub"
            BuildConfig.DISTRIBUTION_CHANNEL == "store" -> "Store"
            else -> "Unknown"
        }
    }
    
    @JvmStatic
    fun getSystemInfo(context: android.content.Context): String {
        return """
            |=== SYSTEM INFORMATION ===
            |Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
            |Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
            |App Version: ${getAppVersionInfo(context)}
            |Build Variant: ${getBuildVariantInfo()}
            |Installation Source: ${getInstallationSource(context)}
            |Logging Settings: Verbose=$verboseMode, Filters=$logFilters, Notifications=$logNotifications, UserActions=$logUserActions, SystemEvents=$logSystemEvents, Sensitive=$logSensitiveData
            |Total Log Entries: ${logs.size}
            |Crash Logs Available: ${hasCrashLogs()}
            |Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}
            |===========================
        """.trimMargin()
    }
} 