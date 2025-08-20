package com.micoyc.speakthat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.micoyc.speakthat.VoiceSettingsActivity
import com.micoyc.speakthat.BehaviorSettingsActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashMap
import java.util.HashSet
import java.util.Locale
import kotlin.collections.ArrayList
import com.micoyc.speakthat.rules.RuleManager

class NotificationReaderService : NotificationListenerService(), TextToSpeech.OnInitListener, SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {
    
    private var sharedPreferences: SharedPreferences? = null
    private var voiceSettingsPrefs: SharedPreferences? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isCurrentlySpeaking = false
    private var currentSpeechText = ""
    private var currentAppName = ""
    
    // Cached system services for performance
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val cachedPackageManager by lazy { packageManager }
    
    // Filter settings (loaded once and cached)
    private var appListMode = "none"
    private var appList: Set<String> = emptySet()
    private var privateApps: Set<String> = emptySet()
    private var blockedWords: Set<String> = emptySet()
    private var privateWords: Set<String> = emptySet()
    private var wordReplacements: Map<String, String> = emptyMap()
    private var priorityApps: Set<String> = emptySet()
    private var notificationBehavior = "interrupt"
    private var mediaBehavior = "ignore"
    private var duckingVolume = 30
    private var delayBeforeReadout = 0
    private var isPersistentFilteringEnabled = true
    
    // Shake detection
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isShakeToStopEnabled = false
    private var shakeThreshold = 12.0f
    private var shakeTimeoutSeconds = 30
    
    // Wave detection
    private var proximitySensor: Sensor? = null
    private var isWaveToStopEnabled = false
    private var waveThreshold = 5.0f
    private var waveTimeoutSeconds = 30
    private var isPocketModeEnabled = false
    private var wasSensorCoveredAtStart = false
    private var isSensorCurrentlyCovered = false
    private var hasSensorBeenUncovered = false
    
    // Media behavior settings
    private var originalMusicVolume = -1 // Store original volume for restoration
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    
    // Media notification filtering
    private var mediaFilterPreferences = MediaNotificationDetector.MediaFilterPreferences()
    
    // Persistent/silent notification filtering
    private var filterPersistent = true
    private var filterSilent = true
    private var filterForegroundServices = true
    private var filterLowPriority = false
    private var filterSystemNotifications = false
    
    // Delay settings
    private var delayHandler: android.os.Handler? = null
    private var pendingReadoutRunnable: Runnable? = null
    
    // Rule system
    private lateinit var ruleManager: RuleManager
    
    // Speech template settings
    private var speechTemplate = "{app} notified you: {content}"
    
    // Varied format options for random selection
    private val variedFormats = arrayOf(
        "{app} notified you: {content}",
        "{app} reported: {content}",
        "Notification from {app}, saying {content}",
        "Notification from {app}: {content}",
        "{app} alerts you: {content}",
        "Update from {app}: {content}",
        "{app} says: {content}",
        "{app} notification: {content}",
        "New notification: {app}: {content}",
        "New from {app}: {content}",
        "{app} said: {content}",
        "{app} updated you: {content}",
        "New notification from {app}: saying: {content}",
        "New update from {app}: {content}",
        "{app}: {content}"
    )
    
    // TTS queue for different behavior modes
    private val notificationQueue = mutableListOf<QueuedNotification>()
    
    // Batch processing for database operations
    private val historyBatchQueue = mutableListOf<NotificationData>()
    private val batchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    // Notification manager for SpeakThat's own notifications
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val batchRunnable = Runnable { processHistoryBatch() }
    private val BATCH_DELAY_MS = 5000L // 5 seconds
    private val MAX_BATCH_SIZE = 10
    
    // Cooldown tracking
    private val appCooldownTimestamps = HashMap<String, Long>() // packageName -> last notification timestamp
    private val appCooldownSettings = HashMap<String, Int>() // packageName -> cooldown seconds
    
    // Deduplication tracking - prevent same notification from being processed multiple times
    private val recentNotificationKeys = HashMap<String, Long>() // notificationKey -> timestamp
    
    // TTS Recovery tracking
    private var ttsRecoveryAttempts = 0
    private var ttsRecoveryHandler: android.os.Handler? = null
    private var ttsRecoveryRunnable: Runnable? = null
    private var lastTtsFailureTime = 0L
    private var consecutiveTtsFailures = 0
    
    // Periodic health check
    private var healthCheckHandler: android.os.Handler? = null
    private var healthCheckRunnable: Runnable? = null
    private val HEALTH_CHECK_INTERVAL_MS = 300000L // 5 minutes
    

    
    // Voice settings listener
    private val voiceSettingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "speech_rate", "pitch", "voice_name", "language", "audio_usage", "content_type" -> {
                InAppLogger.log("Service", "Voice settings changed: $key - applying to service TTS")
                applyVoiceSettings()
                Log.d(TAG, "Voice settings updated: $key")
            }
        }
    }
    
    companion object {
        private const val TAG = "NotificationReader"
        private val notificationHistory = ArrayList<NotificationData>()
        private const val MAX_HISTORY_SIZE = 20
        private const val PREFS_NAME = "SpeakThatPrefs"
        private const val KEY_SHAKE_TO_STOP_ENABLED = "shake_to_stop_enabled"
        private const val KEY_SHAKE_THRESHOLD = "shake_threshold"
        private const val KEY_SHAKE_TIMEOUT_SECONDS = "shake_timeout_seconds"
        private const val KEY_WAVE_TIMEOUT_SECONDS = "wave_timeout_seconds"
        
        // Notification IDs
        private const val PERSISTENT_NOTIFICATION_ID = 1001
        private const val READING_NOTIFICATION_ID = 1002
        
        // Deduplication settings
        private const val DEDUPLICATION_WINDOW_MS = 10000L // 10 seconds window for deduplication (increased from 5s)
        
        // Filter system keys
        private const val KEY_APP_LIST_MODE = "app_list_mode"
        private const val KEY_APP_LIST = "app_list"
        private const val KEY_APP_PRIVATE_FLAGS = "app_private_flags"
        private const val KEY_WORD_BLACKLIST = "word_blacklist"
        private const val KEY_WORD_BLACKLIST_PRIVATE = "word_blacklist_private"
        private const val KEY_WORD_REPLACEMENTS = "word_replacements"
        
                // Behavior settings
        private const val KEY_NOTIFICATION_BEHAVIOR = "notification_behavior"
        private const val KEY_PRIORITY_APPS = "priority_apps"
        
        // Media behavior settings
        private const val KEY_MEDIA_BEHAVIOR = "media_behavior"
        private const val KEY_DUCKING_VOLUME = "ducking_volume"
        
        // Delay settings
        private const val KEY_DELAY_BEFORE_READOUT = "delay_before_readout"
        
        // Speech template settings
        private const val KEY_SPEECH_TEMPLATE = "speech_template"
        
        // Conditional rules settings
        private const val KEY_CONDITIONAL_RULES = "conditional_rules"
        
        // Notification settings
        private const val KEY_PERSISTENT_NOTIFICATION = "persistent_notification"
        private const val KEY_NOTIFICATION_WHILE_READING = "notification_while_reading"
        
        // Media notification filtering settings
        private const val KEY_MEDIA_FILTERING_ENABLED = "media_filtering_enabled"
        private const val KEY_MEDIA_FILTER_EXCEPTED_APPS = "media_filter_excepted_apps"
        private const val KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS = "media_filter_important_keywords"
        private const val KEY_MEDIA_FILTERED_APPS = "media_filtered_apps"
        private const val KEY_MEDIA_FILTERED_APPS_PRIVATE = "media_filtered_apps_private"
        
        // Persistent/silent notification filtering settings
        private const val KEY_PERSISTENT_FILTERING_ENABLED = "persistent_filtering_enabled"
        
        // Cooldown settings
        private const val KEY_COOLDOWN_APPS = "cooldown_apps"
        
        // TTS Recovery settings
        private const val MAX_TTS_RECOVERY_ATTEMPTS = 3
        private const val TTS_RECOVERY_DELAY_MS = 2000L // 2 seconds between attempts
        
        @JvmStatic
        fun getRecentNotifications(): List<NotificationData> {
            return notificationHistory.toList()
        }
    }
    
    data class NotificationData(
        val appName: String,
        val packageName: String,
        val text: String,
        val timestamp: String
    )
    
    data class QueuedNotification(
        val appName: String,
        val text: String,
        val isPriority: Boolean = false,
        val conditionalDelaySeconds: Int = -1,
        val sbn: StatusBarNotification? = null
    )
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationReaderService created")
        InAppLogger.log("Service", "NotificationReaderService started")
        
        // Clear deduplication cache on service start to prevent stale entries
        recentNotificationKeys.clear()
        Log.d(TAG, "Cleared deduplication cache on service start")
        
        try {
            Log.d(TAG, "Starting service initialization...")
            InAppLogger.log("Service", "Starting service initialization")
            
            // Initialize SharedPreferences
            Log.d(TAG, "Initializing SharedPreferences...")
            sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            Log.d(TAG, "SharedPreferences initialized")
            
            // Register preference change listener to automatically reload settings
            Log.d(TAG, "Registering preference change listener...")
            sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            Log.d(TAG, "Preference change listener registered")
            
            // Initialize and register voice settings listener
            Log.d(TAG, "Initializing voice settings...")
            voiceSettingsPrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
            voiceSettingsPrefs?.registerOnSharedPreferenceChangeListener(voiceSettingsListener)
            Log.d(TAG, "Voice settings initialized")
            
            // Initialize components with error handling
            Log.d(TAG, "Initializing TextToSpeech...")
            try {
                initializeTextToSpeech()
                Log.d(TAG, "TextToSpeech initialization call completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing TextToSpeech", e)
                InAppLogger.logError("Service", "TextToSpeech initialization failed: " + e.message)
            }
            
            Log.d(TAG, "Initializing shake detection...")
            try {
                initializeShakeDetection()
                Log.d(TAG, "Shake detection initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing shake detection", e)
                InAppLogger.logError("Service", "Shake detection initialization failed: " + e.message)
            }
            
            Log.d(TAG, "Loading filter settings...")
            try {
                loadFilterSettings()
                Log.d(TAG, "Filter settings loaded")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading filter settings", e)
                InAppLogger.logError("Service", "Filter settings loading failed: " + e.message)
            }
            
            Log.d(TAG, "Initializing rule manager...")
            try {
                ruleManager = RuleManager(this)
                Log.d(TAG, "Rule manager initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing rule manager", e)
                InAppLogger.logError("Service", "Rule manager initialization failed: " + e.message)
            }
            
            // Initialize handlers (pre-created for consistent timing and battery efficiency)
            Log.d(TAG, "Initializing handlers...")
            delayHandler = android.os.Handler(android.os.Looper.getMainLooper())
            sensorTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
            Log.d(TAG, "Handlers initialized")
            
            // Start periodic TTS health check
            Log.d(TAG, "Starting periodic TTS health check...")
            startPeriodicHealthCheck()
            Log.d(TAG, "Periodic TTS health check started")
            
            Log.d(TAG, "NotificationReaderService initialization completed successfully")
            InAppLogger.log("Service", "Service initialization completed successfully")
            
            // Show persistent notification if enabled and master switch is on
            checkAndShowPersistentNotification()
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during service initialization", e)
            InAppLogger.logError("Service", "Critical initialization error: " + e.message)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        Log.d(TAG, "NotificationReaderService being destroyed")
        InAppLogger.log("Service", "NotificationReaderService being destroyed")
        
        try {
            // Process any remaining batch operations
            if (historyBatchQueue.isNotEmpty()) {
                Log.d(TAG, "Processing final batch of ${historyBatchQueue.size} notifications before shutdown")
                processHistoryBatch()
            }
            
            // Cancel any pending batch operations
            batchHandler.removeCallbacks(batchRunnable)
            
            // Stop any ongoing TTS
            if (isCurrentlySpeaking) {
                textToSpeech?.stop()
                isCurrentlySpeaking = false
            }
            
            // Clean up enhanced ducking if active
            cleanupMediaBehavior()
            
            // Clean up VolumeShaper resources if active
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && isUsingVolumeShaper) {
                cleanupVolumeShaper()
            }
            
            // Shutdown TTS
            textToSpeech?.shutdown()
            textToSpeech = null
            
            // Unregister sensors
            sensorManager?.let { manager ->
                if (isShakeToStopEnabled && accelerometer != null) {
                    manager.unregisterListener(this, accelerometer)
                }
                if (isWaveToStopEnabled && proximitySensor != null) {
                    manager.unregisterListener(this, proximitySensor)
                }
            }
            
            // Cancel any pending handlers
            delayHandler?.removeCallbacksAndMessages(null)
            sensorTimeoutHandler?.removeCallbacksAndMessages(null)
            
            // Unregister preference change listener
            sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
            
            // Unregister voice settings listener
            voiceSettingsPrefs?.unregisterOnSharedPreferenceChangeListener(voiceSettingsListener)
            
            // Clear deduplication cache
            recentNotificationKeys.clear()
            Log.d(TAG, "Cleared deduplication cache during cleanup")
            
            // Hide all SpeakThat notifications
            hidePersistentNotification()
            hideReadingNotification()
            
            Log.d(TAG, "NotificationReaderService cleanup completed")
            InAppLogger.log("Service", "Service cleanup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during service cleanup", e)
            InAppLogger.logError("Service", "Error during service cleanup: " + e.message)
        }
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListener connected")
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "NotificationListener disconnected")
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        try {
            try {
                val notification = sbn.notification
                val packageName = sbn.packageName
                
                // Skip our own notifications
                if (packageName == this.packageName) {
                    return
                }
                
                // Check master switch first - if disabled, don't process any notifications
                if (!MainActivity.isMasterSwitchEnabled(this)) {
                    Log.d(TAG, "Master switch disabled - ignoring notification from $packageName")
                    InAppLogger.log("MasterSwitch", "Notification ignored due to master switch being disabled")
                    return
                }

                // Check Do Not Disturb mode - if enabled and honouring DND, don't process notifications
                if (BehaviorSettingsActivity.shouldHonourDoNotDisturb(this)) {
                    Log.d(TAG, "Do Not Disturb mode enabled - ignoring notification from $packageName")
                    InAppLogger.log("DoNotDisturb", "Notification ignored due to Do Not Disturb mode")
                    return
                }
                
                // Check audio mode - if not in Sound mode and honouring audio mode, don't process notifications
                if (BehaviorSettingsActivity.shouldHonourAudioMode(this)) {
                    val ringerMode = audioManager.ringerMode
                    val modeName = when (ringerMode) {
                        AudioManager.RINGER_MODE_SILENT -> "Silent"
                        AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                        AudioManager.RINGER_MODE_NORMAL -> "Sound"
                        else -> "Unknown"
                    }
                    Log.d(TAG, "Audio mode check failed - device is in $modeName mode, ignoring notification from $packageName")
                    InAppLogger.log("AudioMode", "Notification ignored due to audio mode: $modeName")
                    return
                } else {
                    // Log when audio mode check passes (for debugging)
                    val ringerMode = audioManager.ringerMode
                    val modeName = when (ringerMode) {
                        AudioManager.RINGER_MODE_SILENT -> "Silent"
                        AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                        AudioManager.RINGER_MODE_NORMAL -> "Sound"
                        else -> "Unknown"
                    }
                    Log.d(TAG, "Audio mode check passed - device is in $modeName mode, proceeding with notification from $packageName")
                    InAppLogger.log("AudioMode", "Audio mode check passed: $modeName")
                }
                
                // Check phone calls - if on a call and honouring phone calls, don't process notifications
                if (BehaviorSettingsActivity.shouldHonourPhoneCalls(this)) {
                    Log.d(TAG, "Phone call check failed - device is on a call, ignoring notification from $packageName")
                    InAppLogger.log("PhoneCalls", "Notification ignored due to active phone call")
                    return
                } else {
                    // Log when phone call check passes (for debugging)
                    Log.d(TAG, "Phone call check passed - device is not on a call, proceeding with notification from $packageName")
                    InAppLogger.log("PhoneCalls", "Phone call check passed: no active call")
                }
                
                // Get app name
                val appName = getAppName(packageName)
                
                // Extract notification text
                val notificationText = extractNotificationText(notification)
                
                // Check for duplicate notifications (only if deduplication is enabled)
                val isDeduplicationEnabled = sharedPreferences?.getBoolean("notification_deduplication", false) ?: false
                if (isDeduplicationEnabled) {
                    val notificationKey = generateNotificationKey(packageName, sbn.id, notificationText)
                    val currentTime = System.currentTimeMillis()
                    
                    // Clean up old entries from deduplication map
                    val cleanupCount = recentNotificationKeys.entries.removeIf { (_, timestamp) ->
                        currentTime - timestamp > DEDUPLICATION_WINDOW_MS
                    }
                    if (cleanupCount) {
                        Log.d(TAG, "Cleaned up $cleanupCount old deduplication entries")
                    }
                    
                    // Check if this notification was recently processed
                    val lastProcessedTime = recentNotificationKeys[notificationKey]
                    if (lastProcessedTime != null && currentTime - lastProcessedTime < DEDUPLICATION_WINDOW_MS) {
                        val timeSinceLastProcessed = currentTime - lastProcessedTime
                        Log.d(TAG, "Duplicate notification detected from $appName - skipping (processed ${timeSinceLastProcessed}ms ago)")
                        Log.d(TAG, "Deduplication key: $notificationKey")
                        InAppLogger.logFilter("Duplicate notification from $appName - skipping (processed ${timeSinceLastProcessed}ms ago)")
                        return
                    }
                    
                    // Mark this notification as processed
                    recentNotificationKeys[notificationKey] = currentTime
                    Log.d(TAG, "Deduplication: Added notification key for $appName (key: $notificationKey)")
                    Log.d(TAG, "Deduplication map size: ${recentNotificationKeys.size}")
                    
                    // Additional content-based deduplication for problematic apps
                    if (isProblematicApp(packageName)) {
                        val contentKey = generateContentKey(packageName, notificationText)
                        val lastContentTime = recentNotificationKeys[contentKey]
                        if (lastContentTime != null && currentTime - lastContentTime < DEDUPLICATION_WINDOW_MS) {
                            val timeSinceLastContent = currentTime - lastContentTime
                            Log.d(TAG, "Content-based duplicate detected from $appName - skipping (processed ${timeSinceLastContent}ms ago)")
                            InAppLogger.logFilter("Content-based duplicate from $appName - skipping (processed ${timeSinceLastContent}ms ago)")
                            return
                        }
                        recentNotificationKeys[contentKey] = currentTime
                    }
                } else {
                    Log.d(TAG, "Deduplication is disabled - processing all notifications")
                }
                
                if (notificationText.isNotEmpty()) {
                    // SECURITY: Check sensitive data logging setting once for this notification
                    val isSensitiveDataLoggingEnabled = sharedPreferences?.getBoolean("log_sensitive_data", false) ?: false
                    
                    // SECURITY: Don't log original content for private apps (unless sensitive data logging is enabled for testing)
                    val isAppPrivate = privateApps.contains(packageName)
                    
                    if (isAppPrivate) {
                        if (isSensitiveDataLoggingEnabled) {
                            // SECURITY EXCEPTION: Only for development/testing with explicit user consent
                            Log.d(TAG, "New notification from $appName: $notificationText [SENSITIVE DATA LOGGING ENABLED]")
                            InAppLogger.logNotification("Processing private notification from $appName: $notificationText [SENSITIVE DATA LOGGING ENABLED]")
                        } else {
                            Log.d(TAG, "New notification from $appName: [PRIVATE CONTENT - NOT LOGGED]")
                            InAppLogger.logNotification("Processing private notification from $appName: [PRIVATE CONTENT - NOT LOGGED]")
                        }
                    } else {
                        Log.d(TAG, "New notification from $appName: $notificationText")
                        InAppLogger.logNotification("Processing notification from $appName: $notificationText")
                    }
                    
                    // Apply filtering
                    val filterResult = applyFilters(packageName, appName, notificationText, sbn)
                    
                    if (filterResult.shouldSpeak) {
                        // Determine final app name (private apps become "An app")
                        val finalAppName = if (isAppPrivate) "An app" else appName
                        
                        // Add to history
                        addToHistory(finalAppName, packageName, filterResult.processedText)
                        
                        // Handle notification based on behavior mode (pass conditional delay info)
                        handleNotificationBehavior(packageName, finalAppName, filterResult.processedText, filterResult.conditionalDelaySeconds, sbn)
                    } else {
                        // SECURITY: Don't log specific reasons for blocked notifications to avoid information leakage (unless sensitive data logging is enabled for testing)
                        if (isSensitiveDataLoggingEnabled) {
                            // SECURITY EXCEPTION: Only for development/testing with explicit user consent
                            Log.d(TAG, "Notification blocked from $appName: ${filterResult.reason} [SENSITIVE DATA LOGGING ENABLED]")
                            InAppLogger.logFilter("Blocked notification from $appName: ${filterResult.reason} [SENSITIVE DATA LOGGING ENABLED]")
                        } else {
                            Log.d(TAG, "Notification blocked from $appName: [BLOCKED - REASON NOT LOGGED]")
                            InAppLogger.logFilter("Blocked notification from $appName: [BLOCKED - REASON NOT LOGGED]")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification (inner)", e)
                InAppLogger.logError("Service", "Error processing notification: " + e.message)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Critical error in onNotificationPosted", e)
            InAppLogger.logError("Service", "Critical error in onNotificationPosted: " + e.message)
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // We don't need to do anything when notifications are removed for now
    }
    
    private fun initializeTextToSpeech() {
        try {
            Log.d(TAG, "Starting TTS initialization...")
            InAppLogger.log("Service", "Starting TTS initialization")
            
            textToSpeech = TextToSpeech(this, this)
            
            // Add a timeout to detect if TTS initialization hangs
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!isTtsInitialized) {
                    Log.e(TAG, "TTS initialization timed out after 10 seconds")
                    InAppLogger.logError("Service", "TTS initialization timed out - attempting recovery")
                    
                    // Try to reinitialize TTS
                    try {
                        textToSpeech?.shutdown()
                        textToSpeech = null
                        textToSpeech = TextToSpeech(this, this)
                        Log.d(TAG, "TTS reinitialization attempted")
                        InAppLogger.log("Service", "TTS reinitialization attempted")
                    } catch (e: Exception) {
                        Log.e(TAG, "TTS reinitialization failed", e)
                        InAppLogger.logError("Service", "TTS reinitialization failed: " + e.message)
                    }
                }
            }, 10000) // 10 second timeout
            
            Log.d(TAG, "TTS initialization started")
            InAppLogger.log("Service", "TTS initialization started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in initializeTextToSpeech", e)
            InAppLogger.logError("Service", "TTS initialization error: " + e.message)
        }
    }
    
    /**
     * Comprehensive TTS recovery system that can detect and fix various TTS issues
     */
    private fun attemptTtsRecovery(reason: String) {
        val currentTime = System.currentTimeMillis()
        
        // Reset recovery attempts if it's been more than 5 minutes since last failure
        if (currentTime - lastTtsFailureTime > 300000) { // 5 minutes
            ttsRecoveryAttempts = 0
            consecutiveTtsFailures = 0
        }
        
        lastTtsFailureTime = currentTime
        consecutiveTtsFailures++
        
        if (ttsRecoveryAttempts >= MAX_TTS_RECOVERY_ATTEMPTS) {
            Log.e(TAG, "TTS recovery failed after $MAX_TTS_RECOVERY_ATTEMPTS attempts - giving up")
            InAppLogger.logError("Service", "TTS recovery failed after $MAX_TTS_RECOVERY_ATTEMPTS attempts - giving up")
            return
        }
        
        ttsRecoveryAttempts++
        Log.w(TAG, "Attempting TTS recovery #$ttsRecoveryAttempts (reason: $reason)")
        InAppLogger.log("Service", "Attempting TTS recovery #$ttsRecoveryAttempts (reason: $reason)")
        
        // Cancel any existing recovery attempt
        ttsRecoveryRunnable?.let { runnable ->
            ttsRecoveryHandler?.removeCallbacks(runnable)
        }
        
        // Schedule recovery attempt
        ttsRecoveryHandler = android.os.Handler(android.os.Looper.getMainLooper())
        ttsRecoveryRunnable = Runnable {
            try {
                Log.d(TAG, "Executing TTS recovery attempt #$ttsRecoveryAttempts")
                InAppLogger.log("Service", "Executing TTS recovery attempt #$ttsRecoveryAttempts")
                
                // 1. Shutdown existing TTS
                try {
                    textToSpeech?.shutdown()
                    Log.d(TAG, "Existing TTS shutdown completed")
                } catch (e: Exception) {
                    Log.w(TAG, "Error shutting down existing TTS", e)
                }
                
                // 2. Clear TTS state
                textToSpeech = null
                isTtsInitialized = false
                
                // 3. Wait a moment for cleanup
                Thread.sleep(500)
                
                // 4. Reinitialize TTS
                textToSpeech = TextToSpeech(this, this)
                
                // 5. Set up another timeout for this recovery attempt
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (!isTtsInitialized) {
                        Log.e(TAG, "TTS recovery attempt #$ttsRecoveryAttempts timed out")
                        InAppLogger.logError("Service", "TTS recovery attempt #$ttsRecoveryAttempts timed out")
                        
                        // Try again if we haven't exceeded max attempts
                        if (ttsRecoveryAttempts < MAX_TTS_RECOVERY_ATTEMPTS) {
                            attemptTtsRecovery("Recovery timeout")
                        }
                    } else {
                        Log.d(TAG, "TTS recovery successful!")
                        InAppLogger.log("Service", "TTS recovery successful!")
                        ttsRecoveryAttempts = 0
                        consecutiveTtsFailures = 0
                    }
                }, 10000) // 10 second timeout
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during TTS recovery attempt #$ttsRecoveryAttempts", e)
                InAppLogger.logError("Service", "Error during TTS recovery attempt #$ttsRecoveryAttempts: " + e.message)
                
                // Try again if we haven't exceeded max attempts
                if (ttsRecoveryAttempts < MAX_TTS_RECOVERY_ATTEMPTS) {
                    attemptTtsRecovery("Recovery error")
                }
            }
        }
        
        ttsRecoveryHandler?.postDelayed(ttsRecoveryRunnable!!, TTS_RECOVERY_DELAY_MS)
    }
    
    /**
     * Check if TTS is healthy and attempt recovery if needed
     */
    private fun checkTtsHealth(): Boolean {
        if (textToSpeech == null) {
            Log.w(TAG, "TTS is null - attempting recovery")
            attemptTtsRecovery("TTS is null")
            return false
        }
        
        if (!isTtsInitialized) {
            Log.w(TAG, "TTS is not initialized - attempting recovery")
            attemptTtsRecovery("TTS not initialized")
            return false
        }
        
        // Check if TTS is available
        try {
            val isAvailable = textToSpeech?.isLanguageAvailable(Locale.getDefault()) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (isAvailable == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS language not available - attempting recovery")
                attemptTtsRecovery("Language not available")
                return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking TTS availability - attempting recovery", e)
            attemptTtsRecovery("TTS availability check failed")
            return false
        }
        
        return true
    }
    
    /**
     * Start periodic TTS health checks
     */
    private fun startPeriodicHealthCheck() {
        healthCheckHandler = android.os.Handler(android.os.Looper.getMainLooper())
        healthCheckRunnable = Runnable {
            try {
                Log.d(TAG, "Running periodic TTS health check")
                InAppLogger.log("Service", "Running periodic TTS health check")
                
                // Only run health check if we're not currently speaking
                if (!isCurrentlySpeaking) {
                    checkTtsHealth()
                }
                
                // Schedule next health check
                healthCheckHandler?.postDelayed(healthCheckRunnable!!, HEALTH_CHECK_INTERVAL_MS)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during periodic health check", e)
                InAppLogger.logError("Service", "Error during periodic health check: " + e.message)
                
                // Still schedule next health check even if this one failed
                healthCheckHandler?.postDelayed(healthCheckRunnable!!, HEALTH_CHECK_INTERVAL_MS)
            }
        }
        
        // Start the first health check
        healthCheckHandler?.postDelayed(healthCheckRunnable!!, HEALTH_CHECK_INTERVAL_MS)
        Log.d(TAG, "Periodic TTS health check started (every ${HEALTH_CHECK_INTERVAL_MS / 1000} seconds)")
        InAppLogger.log("Service", "Periodic TTS health check started (every ${HEALTH_CHECK_INTERVAL_MS / 1000} seconds)")
    }
    
    /**
     * Stop periodic TTS health checks
     */
    private fun stopPeriodicHealthCheck() {
        healthCheckRunnable?.let { runnable ->
            healthCheckHandler?.removeCallbacks(runnable)
            healthCheckRunnable = null
        }
        healthCheckHandler = null
        Log.d(TAG, "Periodic TTS health check stopped")
        InAppLogger.log("Service", "Periodic TTS health check stopped")
    }
    
    override fun onInit(status: Int) {
        try {
            Log.d(TAG, "TTS onInit called with status: $status")
            InAppLogger.log("Service", "TTS onInit called with status: $status")
            
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "TTS initialization successful, voice settings will be applied next")
                InAppLogger.log("Service", "TTS initialization successful, voice settings will be applied next")
                
                // Set audio stream to assistant usage to avoid triggering media detection
                try {
                    textToSpeech?.setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    Log.d(TAG, "Audio attributes set successfully")
                    InAppLogger.log("Service", "Audio attributes set successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting audio attributes", e)
                    InAppLogger.logError("Service", "Error setting audio attributes: " + e.message)
                }
                
                // CRITICAL: Set TTS as initialized BEFORE applying voice settings
                // This prevents the "Cannot apply voice settings - TTS not initialized" error
                // The applyVoiceSettings() method checks isTtsInitialized, so order matters
                isTtsInitialized = true
                
                // CRITICAL: Apply saved voice settings during TTS initialization
                // This ensures the service starts with the correct voice settings
                // The voice settings will respect the override logic (specific voice > language)
                try {
                    InAppLogger.log("Service", "About to apply voice settings during TTS initialization")
                    applyVoiceSettings()
                    Log.d(TAG, "Voice settings applied successfully")
                    InAppLogger.log("Service", "Voice settings applied successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error applying voice settings", e)
                    InAppLogger.logError("Service", "Error applying voice settings: " + e.message)
                }
                Log.d(TAG, "TextToSpeech initialized successfully")
                InAppLogger.log("Service", "TextToSpeech initialized successfully")
                
                // Reset recovery counters on successful initialization
                ttsRecoveryAttempts = 0
                consecutiveTtsFailures = 0
                
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status: $status")
                InAppLogger.logError("Service", "TextToSpeech initialization failed with status: $status")
                
                // Attempt recovery for initialization failures
                attemptTtsRecovery("Initialization failed with status: $status")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in TTS onInit", e)
            InAppLogger.logError("Service", "Error in TTS onInit: " + e.message)
            
            // Attempt recovery for exceptions
            attemptTtsRecovery("onInit exception: " + e.message)
        }
    }
    
    private fun getAppName(packageName: String): String {
        // First check for custom app name
        val customAppName = getCustomAppName(packageName)
        if (customAppName != null) {
            Log.d(TAG, "Using custom app name for $packageName: $customAppName")
            InAppLogger.log("AppName", "Using custom app name for $packageName: $customAppName")
            return customAppName
        }
        
        // Standard package resolution
        return try {
            val appInfo = cachedPackageManager.getApplicationInfo(packageName, 0)
            val appName = cachedPackageManager.getApplicationLabel(appInfo).toString()
            Log.d(TAG, "Successfully resolved app name for $packageName: $appName")
            appName
        } catch (e: Exception) {
            Log.w(TAG, "Error getting app name for $packageName: ${e.message}")
            packageName
        }
    }


    private fun getCustomAppName(packageName: String): String? {
        return try {
            val customAppNamesJson = sharedPreferences?.getString("custom_app_names", "[]") ?: "[]"
            val jsonArray = org.json.JSONArray(customAppNamesJson)
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val storedPackageName = jsonObject.getString("packageName")
                if (storedPackageName == packageName) {
                    return jsonObject.getString("customName")
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting custom app name for $packageName", e)
            null
        }
    }
    
    /**
     * Generate a unique key for a notification to enable deduplication
     * Uses package name, notification ID, content hash, and timestamp to identify duplicates
     * Improved to handle cases where apps reuse notification IDs
     */
    private fun generateNotificationKey(packageName: String, notificationId: Int, content: String): String {
        // Create a more robust hash of the content using SHA-256
        val contentHash = try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(content.toByteArray())
            hashBytes.take(8).joinToString("") { "%02x".format(it) } // Use first 8 bytes as hex string
        } catch (e: Exception) {
            Log.e(TAG, "Error generating content hash, falling back to hashCode", e)
            content.hashCode().toString()
        }
        
        // Include timestamp to handle cases where apps reuse notification IDs
        val timestamp = System.currentTimeMillis() / 1000 // Use seconds to group similar timestamps
        return "${packageName}_${notificationId}_${contentHash}_${timestamp}"
    }
    
    /**
     * Generate a content-based key for additional deduplication
     * Used for problematic apps that reuse notification IDs frequently
     */
    private fun generateContentKey(packageName: String, content: String): String {
        val contentHash = try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(content.toByteArray())
            hashBytes.take(4).joinToString("") { "%02x".format(it) } // Use first 4 bytes as hex string
        } catch (e: Exception) {
            Log.e(TAG, "Error generating content hash, falling back to hashCode", e)
            content.hashCode().toString()
        }
        return "content_${packageName}_${contentHash}"
    }
    
    /**
     * Check if an app is known to have problematic duplicate notifications
     */
    private fun isProblematicApp(packageName: String): Boolean {
        return packageName == "com.google.android.gm" || // Gmail
               packageName == "com.google.android.apps.messaging" || // Messages
               packageName == "com.whatsapp" || // WhatsApp
               packageName == "com.tencent.mm" || // WeChat
               packageName == "com.instagram.android" || // Instagram
               packageName == "com.twitter.android" // Twitter
    }
    
    private fun extractNotificationText(notification: Notification): String {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() ?: ""
        
        // Log the available notification content for debugging
        Log.d(TAG, "Notification content - Title: '$title', Text: '$text', BigText: '$bigText', Summary: '$summaryText', Info: '$infoText'")
        
        // Enhanced logic to ensure title is always included when available
        // This fixes the issue where notifications with both title and bigText would lose the title
        // The previous logic prioritized bigText over title, but bigText often doesn't include the title
        return when {
            title.isNotEmpty() && bigText.isNotEmpty() -> "$title: $bigText"
            title.isNotEmpty() && text.isNotEmpty() -> "$title: $text"
            title.isNotEmpty() && summaryText.isNotEmpty() -> "$title: $summaryText"
            title.isNotEmpty() && infoText.isNotEmpty() -> "$title: $infoText"
            bigText.isNotEmpty() -> bigText
            title.isNotEmpty() -> title
            text.isNotEmpty() -> text
            summaryText.isNotEmpty() -> summaryText
            infoText.isNotEmpty() -> infoText
            else -> ""
        }
    }
    
    private fun addToHistory(appName: String, packageName: String, text: String) {
        try {
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val notificationData = NotificationData(appName, packageName, text, timestamp)
            
            // Add to batch queue instead of immediate database write
            historyBatchQueue.add(notificationData)
            
            // Schedule batch processing if not already scheduled
            if (historyBatchQueue.size == 1) {
                batchHandler.postDelayed(batchRunnable, BATCH_DELAY_MS)
            }
            
            // Process immediately if batch is full
            if (historyBatchQueue.size >= MAX_BATCH_SIZE) {
                batchHandler.removeCallbacks(batchRunnable)
                processHistoryBatch()
            }
            
            // Keep history list in memory for immediate access
            notificationHistory.add(notificationData)
            
            // Maintain history size limit
            if (notificationHistory.size > MAX_HISTORY_SIZE) {
                notificationHistory.removeAt(0)
            }
            
            Log.d(TAG, "Added notification to history batch queue: $appName")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding notification to history batch", e)
            InAppLogger.logError("Service", "Error adding notification to history batch: " + e.message)
        }
    }
    
    private fun processHistoryBatch() {
        if (historyBatchQueue.isEmpty()) {
            return
        }
        
        try {
            val batchToProcess = historyBatchQueue.toList()
            historyBatchQueue.clear()
            
            // Process all notifications in the batch
            for (notificationData in batchToProcess) {
                // This would be the actual database write operation
                // For now, we just log it since the actual database implementation isn't shown
                Log.d(TAG, "Batch processing notification: ${notificationData.appName}")
            }
            
            Log.d(TAG, "Processed ${batchToProcess.size} notifications in batch")
            InAppLogger.log("Service", "Processed ${batchToProcess.size} notifications in batch")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing history batch", e)
            InAppLogger.logError("Service", "Error processing history batch: " + e.message)
            
            // On error, add items back to queue for retry
            historyBatchQueue.addAll(historyBatchQueue)
        }
    }
    
    // This method is now replaced by speakNotificationImmediate and handleNotificationBehavior
    // Keeping for backward compatibility but redirecting to new behavior system
    private fun speakNotification(appName: String, text: String) {
        speakNotificationImmediate(appName, text) // No conditional delay for deprecated method
    }
    
    private fun initializeShakeDetection() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        
        // Load shake and wave settings
        loadShakeSettings()
        loadWaveSettings()
        
        // Don't register listener here - only register when actually speaking
        Log.d(TAG, "Shake and wave detection initialized (listener will register during TTS)")
    }
    
    private fun loadShakeSettings() {
        isShakeToStopEnabled = sharedPreferences?.getBoolean(KEY_SHAKE_TO_STOP_ENABLED, true) ?: true
        shakeThreshold = sharedPreferences?.getFloat(KEY_SHAKE_THRESHOLD, 12.0f) ?: 12.0f
        
        // Safety validation: ensure timeout is within valid range (0 or 5-300)
        var timeout = sharedPreferences?.getInt(KEY_SHAKE_TIMEOUT_SECONDS, 30) ?: 30
        if (timeout < 0 || (timeout > 0 && timeout < 5) || timeout > 300) {
            timeout = 30 // Reset to safe default
            Log.w(TAG, "Invalid shake timeout value detected ($timeout), resetting to 30 seconds")
            InAppLogger.logWarning(TAG, "Invalid shake timeout value detected, resetting to 30 seconds")
            // Save the corrected value
            sharedPreferences?.edit()?.putInt(KEY_SHAKE_TIMEOUT_SECONDS, timeout)?.apply()
        }
        shakeTimeoutSeconds = timeout
        
        Log.d(TAG, "Shake settings loaded - enabled: $isShakeToStopEnabled, threshold: $shakeThreshold, timeout: ${shakeTimeoutSeconds}s")
    }

    private fun loadWaveSettings() {
        isWaveToStopEnabled = sharedPreferences?.getBoolean("wave_to_stop_enabled", false) ?: false
        // Use calibrated threshold if available, otherwise fall back to old system
        waveThreshold = if (sharedPreferences?.contains("wave_threshold_v1") == true) {
            sharedPreferences?.getFloat("wave_threshold_v1", 3.0f) ?: 3.0f
        } else {
            sharedPreferences?.getFloat("wave_threshold", 3.0f) ?: 3.0f
        }
        
        // Safety validation: ensure timeout is within valid range (0 or 5-300)
        var timeout = sharedPreferences?.getInt(KEY_WAVE_TIMEOUT_SECONDS, 30) ?: 30
        if (timeout < 0 || (timeout > 0 && timeout < 5) || timeout > 300) {
            timeout = 30 // Reset to safe default
            Log.w(TAG, "Invalid wave timeout value detected ($timeout), resetting to 30 seconds")
            InAppLogger.logWarning(TAG, "Invalid wave timeout value detected, resetting to 30 seconds")
            // Save the corrected value
            sharedPreferences?.edit()?.putInt(KEY_WAVE_TIMEOUT_SECONDS, timeout)?.apply()
        }
        waveTimeoutSeconds = timeout
        
        // Load pocket mode setting
        isPocketModeEnabled = sharedPreferences?.getBoolean("pocket_mode_enabled", false) ?: false
        
        Log.d(TAG, "Wave settings loaded - enabled: $isWaveToStopEnabled, threshold: $waveThreshold, timeout: ${waveTimeoutSeconds}s, pocket mode: $isPocketModeEnabled")
    }
    
    // Sensor timeout for safety
    private var sensorTimeoutHandler: android.os.Handler? = null
    private var sensorTimeoutRunnable: Runnable? = null

    private fun registerShakeListener() {
        if (isShakeToStopEnabled && accelerometer != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Shake listener registered (TTS active)")
            InAppLogger.logSystemEvent("Shake listener started", "TTS playback active")
        }
        if (isWaveToStopEnabled && proximitySensor != null) {
            sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Wave listener registered (TTS active)")
            InAppLogger.logSystemEvent("Wave listener started", "TTS playback active")
        }
        
        // Start timeout only if enabled (timeout > 0)
        if (shakeTimeoutSeconds > 0 || waveTimeoutSeconds > 0) {
            // Use the longer timeout if both are enabled, otherwise use the enabled one
            val effectiveTimeout = when {
                shakeTimeoutSeconds > 0 && waveTimeoutSeconds > 0 -> maxOf(shakeTimeoutSeconds, waveTimeoutSeconds)
                shakeTimeoutSeconds > 0 -> shakeTimeoutSeconds
                waveTimeoutSeconds > 0 -> waveTimeoutSeconds
                else -> 30 // Fallback (shouldn't reach here)
            }
            
            // Cancel any previous timeout
            sensorTimeoutRunnable?.let { sensorTimeoutHandler?.removeCallbacks(it) }
            sensorTimeoutRunnable = Runnable {
                Log.w(TAG, "Sensor listener timeout reached after ${effectiveTimeout}s! Forcibly unregistering sensors.")
                InAppLogger.logWarning(TAG, "Sensor listener timeout reached after ${effectiveTimeout}s! Forcibly unregistering sensors.")
                unregisterShakeListener()
            }
            sensorTimeoutHandler?.postDelayed(sensorTimeoutRunnable!!, (effectiveTimeout * 1000).toLong())
            Log.d(TAG, "Sensor timeout scheduled for ${effectiveTimeout} seconds")
        } else {
            Log.d(TAG, "Sensor timeout disabled by user settings")
        }
    }

    private fun unregisterShakeListener() {
        sensorManager?.unregisterListener(this)
        Log.d(TAG, "Shake and wave listeners unregistered (TTS inactive)")
        InAppLogger.logSystemEvent("Shake and wave listeners stopped", "TTS playback finished")
        // Cancel timeout
        sensorTimeoutRunnable?.let { sensorTimeoutHandler?.removeCallbacks(it) }
        sensorTimeoutRunnable = null
    }
    
    // Call this method when settings change
    fun refreshSettings() {
        // Only unregister if we're not currently speaking
        if (!isCurrentlySpeaking) {
            unregisterShakeListener()
        }
        loadShakeSettings()
        loadWaveSettings()
        
        // If currently speaking and shake or wave is now enabled, register listener
        if (isCurrentlySpeaking && ((isShakeToStopEnabled && accelerometer != null) || (isWaveToStopEnabled && proximitySensor != null))) {
            registerShakeListener()
        }
    }
    
    private fun loadCooldownSettings() {
        appCooldownSettings.clear()
        try {
            val cooldownAppsJson = sharedPreferences?.getString(KEY_COOLDOWN_APPS, "[]") ?: "[]"
            val jsonArray = org.json.JSONArray(cooldownAppsJson)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val packageName = jsonObject.getString("packageName")
                val cooldownSeconds = jsonObject.optInt("cooldownSeconds", 5)
                appCooldownSettings[packageName] = cooldownSeconds
            }
            Log.d(TAG, "Loaded cooldown settings for ${appCooldownSettings.size} apps")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cooldown settings", e)
        }
    }
    
    private fun checkCooldown(packageName: String): FilterResult {
        val cooldownSeconds = appCooldownSettings[packageName] ?: return FilterResult(true, "", "No cooldown set for app")
        if (cooldownSeconds <= 0) {
            return FilterResult(true, "", "Cooldown disabled for app")
        }
        val currentTime = System.currentTimeMillis()
        val lastNotificationTime = appCooldownTimestamps[packageName] ?: 0L
        val timeSinceLastNotification = (currentTime - lastNotificationTime) / 1000 // Convert to seconds
        if (timeSinceLastNotification < cooldownSeconds) {
            val remainingSeconds = cooldownSeconds - timeSinceLastNotification
            Log.d(TAG, "Cooldown active for $packageName - $remainingSeconds seconds remaining")
            InAppLogger.logFilter("Cooldown active for $packageName - $remainingSeconds seconds remaining")
            return FilterResult(false, "", "Cooldown active - $remainingSeconds seconds remaining")
        }
        // Update timestamp for this app
        appCooldownTimestamps[packageName] = currentTime
        return FilterResult(true, "", "Cooldown passed")
    }
    
    private fun loadFilterSettings() {
        appListMode = sharedPreferences?.getString(KEY_APP_LIST_MODE, "none") ?: "none"
        appList = HashSet(sharedPreferences?.getStringSet(KEY_APP_LIST, HashSet()) ?: HashSet())
        privateApps = HashSet(sharedPreferences?.getStringSet(KEY_APP_PRIVATE_FLAGS, HashSet()) ?: HashSet())
        blockedWords = HashSet(sharedPreferences?.getStringSet(KEY_WORD_BLACKLIST, HashSet()) ?: HashSet())
        privateWords = HashSet(sharedPreferences?.getStringSet(KEY_WORD_BLACKLIST_PRIVATE, HashSet()) ?: HashSet())
        
        // Load word swaps
        val replacementData = sharedPreferences?.getString(KEY_WORD_REPLACEMENTS, "") ?: ""
        val newWordReplacements = HashMap<String, String>()
        if (replacementData.isNotEmpty()) {
            val pairs = replacementData.split("|")
            for (pair in pairs) {
                val parts = pair.split(":", limit = 2)
                if (parts.size == 2) {
                    newWordReplacements[parts[0]] = parts[1]
                }
            }
        }
        wordReplacements = newWordReplacements
        Log.d(TAG, "Loaded word replacements: $wordReplacements")
        
        // Load behavior settings  
        notificationBehavior = sharedPreferences?.getString(KEY_NOTIFICATION_BEHAVIOR, "interrupt") ?: "interrupt"
        priorityApps = HashSet(sharedPreferences?.getStringSet(KEY_PRIORITY_APPS, HashSet()) ?: HashSet())
        
        // Load media behavior settings
        mediaBehavior = sharedPreferences?.getString(KEY_MEDIA_BEHAVIOR, "ignore") ?: "ignore"
        duckingVolume = sharedPreferences?.getInt(KEY_DUCKING_VOLUME, 30) ?: 30
        
        // Load delay settings
        delayBeforeReadout = sharedPreferences?.getInt(KEY_DELAY_BEFORE_READOUT, 0) ?: 0
        
        // Load media notification filtering settings
        val isMediaFilteringEnabled = sharedPreferences?.getBoolean(KEY_MEDIA_FILTERING_ENABLED, false) ?: false
        val exceptedApps = HashSet(sharedPreferences?.getStringSet(KEY_MEDIA_FILTER_EXCEPTED_APPS, HashSet()) ?: HashSet())
        val importantKeywords = HashSet(sharedPreferences?.getStringSet(KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS, HashSet()) ?: HashSet())
        val filteredMediaApps = HashSet(sharedPreferences?.getStringSet(KEY_MEDIA_FILTERED_APPS, HashSet()) ?: HashSet())
        
        // If no custom important keywords are set, use defaults
        if (importantKeywords.isEmpty()) {
            importantKeywords.addAll(MediaNotificationDetector.MediaFilterPreferences().importantKeywords)
        }
        
        mediaFilterPreferences = MediaNotificationDetector.MediaFilterPreferences(
            isMediaFilteringEnabled = isMediaFilteringEnabled,
            exceptedApps = exceptedApps,
            importantKeywords = importantKeywords,
            filteredMediaApps = filteredMediaApps
        )
        
        // Load persistent filtering settings
        isPersistentFilteringEnabled = sharedPreferences?.getBoolean(KEY_PERSISTENT_FILTERING_ENABLED, true) ?: true
        filterPersistent = sharedPreferences?.getBoolean("filter_persistent", true) ?: true
        filterSilent = sharedPreferences?.getBoolean("filter_silent", true) ?: true
        filterForegroundServices = sharedPreferences?.getBoolean("filter_foreground_services", true) ?: true
        filterLowPriority = sharedPreferences?.getBoolean("filter_low_priority", false) ?: false
        filterSystemNotifications = sharedPreferences?.getBoolean("filter_system_notifications", false) ?: false
        
        // Load cooldown settings
        loadCooldownSettings()
        
        // Load speech template
        speechTemplate = sharedPreferences?.getString(KEY_SPEECH_TEMPLATE, "{app} notified you: {content}") ?: "{app} notified you: {content}"
        
        // Load notification settings
        val isPersistentNotificationEnabled = sharedPreferences?.getBoolean(KEY_PERSISTENT_NOTIFICATION, false) ?: false
        val isNotificationWhileReadingEnabled = sharedPreferences?.getBoolean(KEY_NOTIFICATION_WHILE_READING, false) ?: false
        
        Log.d(TAG, "Filter settings loaded - appMode: $appListMode, apps: ${appList.size}, blocked words: ${blockedWords.size}, replacements: ${wordReplacements.size}")
        Log.d(TAG, "Behavior settings loaded - mode: $notificationBehavior, priority apps: ${priorityApps.size}")
        Log.d(TAG, "Media behavior settings loaded - mode: $mediaBehavior, ducking volume: $duckingVolume%")
        Log.d(TAG, "Delay settings loaded - delay: ${delayBeforeReadout}s")
        Log.d(TAG, "Media filtering settings loaded - enabled: $isMediaFilteringEnabled, excepted apps: ${exceptedApps.size}, important keywords: ${importantKeywords.size}")
        Log.d(TAG, "Persistent filtering enabled: $isPersistentFilteringEnabled")
        InAppLogger.log("Service", "Settings loaded - Filter mode: $appListMode, Behavior: $notificationBehavior, Media: $mediaBehavior, Delay: ${delayBeforeReadout}s, Media filtering: $isMediaFilteringEnabled, Persistent filtering: $isPersistentFilteringEnabled")
    }
    
    data class FilterResult(
        val shouldSpeak: Boolean,
        val processedText: String,
        val reason: String = "",
        val conditionalDelaySeconds: Int = -1 // -1 means no conditional delay
    )
    
    private fun applyFilters(packageName: String, appName: String, text: String, sbn: StatusBarNotification? = null): FilterResult {
        // 1. Check app filtering
        val appFilterResult = checkAppFilter(packageName)
        if (!appFilterResult.shouldSpeak) {
            return appFilterResult
        }
        // 2. Check cooldown
        val cooldownResult = checkCooldown(packageName)
        if (!cooldownResult.shouldSpeak) {
            return cooldownResult
        }
        
        // 3. Apply media notification filtering (if StatusBarNotification is available)
        if (sbn != null) {
            val mediaFilterResult = applyMediaFiltering(sbn)
            if (!mediaFilterResult.shouldSpeak) {
                return mediaFilterResult
            }
        }
        
        // 4. Apply persistent/silent notification filtering
        if (sbn != null && isPersistentFilteringEnabled) {
            val persistentFilterResult = applyPersistentFiltering(sbn)
            if (!persistentFilterResult.shouldSpeak) {
                return persistentFilterResult
            }
        }
        
        // 5. Apply word filtering and replacements
        val wordFilterResult = applyWordFiltering(text, appName, packageName)
        if (!wordFilterResult.shouldSpeak) {
            return wordFilterResult
        }
        
        // 6. Apply conditional rules (Smart Rules system)
        val conditionalResult = applyConditionalFiltering(packageName, appName, wordFilterResult.processedText)
        if (!conditionalResult.shouldSpeak) {
            return conditionalResult
        }
        
        return FilterResult(
            shouldSpeak = true,
            processedText = conditionalResult.processedText,
            reason = "Passed all filters"
        )
    }
    
    private fun checkAppFilter(packageName: String): FilterResult {
        return when (appListMode) {
            "whitelist" -> {
                if (appList.contains(packageName)) {
                    FilterResult(true, "", "App whitelisted")
                } else {
                    FilterResult(false, "", "App not in whitelist")
                }
            }
            "blacklist" -> {
                if (appList.contains(packageName)) {
                    // Check if this app has private mode enabled
                    if (privateApps.contains(packageName)) {
                        // Allow through for private mode processing
                        FilterResult(true, "", "App blacklisted but private mode enabled")
                    } else {
                        FilterResult(false, "", "App blacklisted")
                    }
                } else {
                    FilterResult(true, "", "App not blacklisted")
                }
            }
            else -> FilterResult(true, "", "No app filtering")
        }
    }
    
    private fun applyWordFiltering(text: String, appName: String, packageName: String = ""): FilterResult {
        var processedText = text
        
        // SECURITY: Check if this app is in private mode FIRST (highest priority)
        // This ensures private apps are never processed for word filtering that might reveal content
        if (packageName.isNotEmpty() && privateApps.contains(packageName)) {
            processedText = getLocalizedTemplate("private_notification", appName, "")
            Log.d(TAG, "App '$appName' is in private mode - entire notification made private (SECURITY: bypassing all other filters)")
            InAppLogger.logFilter("Made notification private due to app privacy setting (SECURITY: bypassing all other filters)")
            return FilterResult(true, processedText, "App-level privacy applied")
        }
        
        // Early exit if no word filters are configured
        if (blockedWords.isEmpty() && privateWords.isEmpty() && wordReplacements.isEmpty()) {
            return FilterResult(true, processedText, "No word filters configured")
        }
        
        // 1. Check for blocked words (including smart filters) - EARLY EXIT on first match
        for (blockedWord in blockedWords) {
            if (matchesWordFilter(processedText, blockedWord)) {
                return FilterResult(false, "", "Blocked by filter: $blockedWord")
            }
        }
        
        // 2. Check for private words and replace entire notification with [PRIVATE] - EARLY EXIT on first match
        for (privateWord in privateWords) {
            if (matchesWordFilter(processedText, privateWord)) {
                // When any private word is detected, replace the entire notification text with a private message
                // This ensures complete privacy - no partial content is revealed
                processedText = getLocalizedTemplate("private_notification", appName, "")
                Log.d(TAG, "Private word '$privateWord' detected - entire notification made private")
                InAppLogger.logFilter("Made notification private due to word: $privateWord")
                break // Exit loop since entire text is now private
            }
        }
        
        // 3. Apply word swaps (only for non-private notifications and only if there are replacements)
        if (wordReplacements.isNotEmpty()) {
            Log.d(TAG, "Word replacements available: ${wordReplacements.size} items")
            for ((from, to) in wordReplacements) {
                val originalText = processedText
                processedText = processedText.replace(from, to, ignoreCase = true)
                if (originalText != processedText) {
                    Log.d(TAG, "Word replacement applied: '$from' -> '$to'")
                    InAppLogger.logFilter("Word replacement applied: '$from' -> '$to'")
                } else {
                    Log.d(TAG, "Word replacement not found: '$from' in text: '$processedText'")
                    InAppLogger.logFilter("Word replacement not found: '$from' in text: '$processedText'")
                }
            }
        }
        
        return FilterResult(true, processedText, "Word filtering applied")
    }
    
    private fun applyConditionalFiltering(packageName: String, appName: String, text: String): FilterResult {
        try {
            // Check if rules system is enabled
            if (!::ruleManager.isInitialized) {
                InAppLogger.logFilter("Rule manager not initialized, allowing notification")
                return FilterResult(true, text, "Rule manager not initialized")
            }
            
            // Check if any rules should block this notification
            val shouldBlock = ruleManager.shouldBlockNotification()
            
            if (shouldBlock) {
                val blockingRules = ruleManager.getBlockingRuleNames()
                val reason = "Rules blocking: ${blockingRules.joinToString(", ")}"
                InAppLogger.logFilter("Rules blocked notification: $reason")
                return FilterResult(false, "", reason)
            } else {
                InAppLogger.logFilter("Rules passed: no blocking rules active")
                return FilterResult(true, text, "Rules passed")
            }
            
        } catch (e: Exception) {
            // Fail-safe: if rules system fails, allow the notification
            InAppLogger.logError("Service", "Error in rule evaluation: ${e.message}")
            return FilterResult(true, text, "Rule evaluation failed, allowing notification")
        }
    }
    
    private fun applyMediaFiltering(sbn: StatusBarNotification): FilterResult {
        // Use unified detection logic
        if (!mediaFilterPreferences.isMediaFilteringEnabled) {
            return FilterResult(true, "", "Media filtering disabled")
        }
        if (MediaNotificationDetector.isMediaNotification(sbn)) {
            val reason = MediaNotificationDetector.getMediaDetectionReason(sbn)
            Log.d(TAG, "Media notification filtered out (unified logic): $reason")
            InAppLogger.logFilter("Blocked media notification from ${sbn.packageName}: $reason (unified logic)")
            // Log all extras for debugging
            val extras = sbn.notification.extras
            Log.d(TAG, "[UnifiedFilter] Notification extras: $extras")
            return FilterResult(false, "", "Media notification filtered: $reason (unified logic)")
        }
        return FilterResult(true, "", "Not a media notification or media filtering not applicable (unified logic)")
    }

    private fun applyPersistentFiltering(sbn: StatusBarNotification): FilterResult {
        val notification = sbn.notification
        val flags = notification.flags
        
        // Check for persistent notifications (ongoing notifications that don't auto-dismiss)
        val isPersistent = (flags and Notification.FLAG_ONGOING_EVENT) != 0 || 
                          (flags and Notification.FLAG_NO_CLEAR) != 0
        
        // Improved silent notification detection
        val isSilent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // For Android O and above, check notification channel settings
            val channel = try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.getNotificationChannel(notification.channelId)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting notification channel", e)
                null
            }
            
            // Consider a notification silent if:
            // 1. Channel is muted (importance NONE) OR
            // 2. Channel has no sound AND no vibration AND importance <= LOW
            channel?.let {
                it.importance == NotificationManager.IMPORTANCE_NONE ||
                (it.sound == null && !it.shouldVibrate() && it.importance <= NotificationManager.IMPORTANCE_LOW)
            } ?: run {
                // Fallback if channel not found: check notification flags and importance
                val hasNoAlerts = notification.sound == null && 
                                 (notification.vibrate == null || notification.vibrate.isEmpty()) &&
                                 (notification.defaults and (Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)) == 0
                val importance = notification.priority
                hasNoAlerts && importance <= Notification.PRIORITY_LOW
            }
        } else {
            // For pre-Oreo devices, check notification flags and settings
            val hasNoAlerts = notification.sound == null && 
                            (notification.vibrate == null || notification.vibrate.isEmpty()) &&
                            (notification.defaults and (Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)) == 0
            val lowPriority = notification.priority <= Notification.PRIORITY_LOW
            
            // Only consider truly silent if it has no alerts and low priority
            hasNoAlerts && lowPriority
        }
        
        // Check for foreground service notifications (ongoing services)
        val isForegroundService = (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        
        // Check notification priority level
        val priority = notification.priority
        val isLowPriorityLevel = priority == Notification.PRIORITY_MIN || 
                                priority == Notification.PRIORITY_LOW
        
        // Check for system notifications (from system apps)
        val isSystemNotification = sbn.packageName.startsWith("com.android.") ||
                                 sbn.packageName.startsWith("android.") ||
                                 sbn.packageName == "com.google.android.apps.messaging" ||
                                 sbn.packageName == "com.android.phone"
        
        // Check each category individually based on user settings
        val reasons = mutableListOf<String>()
        
        if (filterPersistent && isPersistent) {
            reasons.add("persistent")
        }
        
        if (filterSilent && isSilent) {
            reasons.add("silent")
        }
        
        if (filterForegroundServices && isForegroundService) {
            reasons.add("foreground service")
        }
        
        if (filterLowPriority && isLowPriorityLevel) {
            reasons.add("low priority")
        }
        
        if (filterSystemNotifications && isSystemNotification) {
            reasons.add("system notification")
        }
        
        // If any category is enabled and matches, filter the notification
        if (reasons.isNotEmpty()) {
            val reason = reasons.joinToString(", ")
            Log.d(TAG, "Persistent/silent notification filtered: $reason from ${sbn.packageName}")
            InAppLogger.logFilter("Blocked persistent/silent notification from ${sbn.packageName}: $reason")
            
            // Add more detailed logging for silent notifications to help debugging
            if (reasons.contains("silent")) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val channel = try {
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.getNotificationChannel(notification.channelId)
                    } catch (e: Exception) {
                        null
                    }
                    Log.d(TAG, "Silent notification details - Channel: ${notification.channelId}, " +
                              "Channel importance: ${channel?.importance}, " +
                              "Channel sound: ${channel?.sound}, " +
                              "Channel vibration: ${channel?.shouldVibrate()}")
                } else {
                    Log.d(TAG, "Silent notification details - Priority: ${notification.priority}, " +
                              "Sound: ${notification.sound}, " +
                              "Defaults: ${notification.defaults}, " +
                              "Vibration: ${notification.vibrate?.isNotEmpty()}")
                }
            }
            
            return FilterResult(false, "", "Persistent/silent notification: $reason")
        }
        
        return FilterResult(true, "", "Not a persistent/silent notification or category not enabled")
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && isShakeToStopEnabled) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            // Calculate total acceleration (subtract gravity)
            val shakeValue = kotlin.math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
            
            // Check if shake threshold exceeded
            if (shakeValue >= shakeThreshold) {
                Log.d(TAG, "Shake detected! Stopping TTS. Shake value: $shakeValue, threshold: $shakeThreshold")
                stopSpeaking("shake")
            }
        } else if (event.sensor.type == Sensor.TYPE_PROXIMITY && isWaveToStopEnabled) {
            // Proximity sensor returns distance in cm
            val proximityValue = event.values[0]
            
            // Handle different proximity sensor behaviors:
            // Some sensors return 0 when close, others return actual distance
            val isTriggered = if (proximityValue == 0f) {
                // Sensor returns 0 when object is very close (most common)
                true
            } else {
                // Sensor returns actual distance, check if closer than threshold
                // Use < instead of <= to avoid triggering when sensor is at max range
                // ADDITIONAL SAFETY: Only trigger if the value is significantly different from max range
                // This prevents false triggers on devices like Pixel 2 XL that read ~5cm when uncovered
                val maxRange = proximitySensor?.maximumRange ?: 5.0f
                val significantChange = maxRange * 0.3f // Require at least 30% change from max
                val distanceFromMax = maxRange - proximityValue
                
                proximityValue < waveThreshold && distanceFromMax > significantChange
            }
            
            // Track sensor state changes for pocket mode
            val wasCovered = isSensorCurrentlyCovered
            isSensorCurrentlyCovered = isTriggered
            
            // If sensor was uncovered and is now covered, mark that it has been uncovered
            if (!wasCovered && isSensorCurrentlyCovered) {
                hasSensorBeenUncovered = true
                Log.d(TAG, "Pocket mode: Sensor covered - has been uncovered: $hasSensorBeenUncovered")
            }
            
            // Pocket mode logic: if enabled, check if sensor was already covered when readout started
            if (isTriggered) {
                if (isPocketModeEnabled && wasSensorCoveredAtStart && !hasSensorBeenUncovered) {
                    // In pocket mode, if sensor was covered at start and hasn't been uncovered yet, continue readout
                    Log.d(TAG, "Pocket mode: Sensor covered but was already covered at start and hasn't been uncovered - continuing readout")
                    return
                } else {
                    Log.d(TAG, "Wave detected! Stopping TTS. Proximity value: $proximityValue cm, threshold: $waveThreshold cm, pocket mode: $isPocketModeEnabled, was covered at start: $wasSensorCoveredAtStart, has been uncovered: $hasSensorBeenUncovered")
                    stopSpeaking("wave")
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
    
    private fun stopSpeaking(triggerType: String = "unknown") {
        textToSpeech?.stop()
        isCurrentlySpeaking = false
        
        // Unregister shake listener since we're no longer speaking
        unregisterShakeListener()
        
        // Clear any queued notifications since user wants to stop
        notificationQueue.clear()
        
        // Cancel any pending delayed readouts
        pendingReadoutRunnable?.let { runnable ->
            delayHandler?.removeCallbacks(runnable)
            pendingReadoutRunnable = null
            Log.d(TAG, "Cancelled pending delayed readout due to $triggerType")
        }
        
        // Clean up media behavior effects
        cleanupMediaBehavior()
        
        // Hide reading notification since we're no longer speaking
        hideReadingNotification()
        
        Log.d(TAG, "TTS stopped due to $triggerType")
        InAppLogger.logTTSEvent("TTS stopped by $triggerType", "User interrupted speech")
    }
    
    /**
     * Stop current TTS speech without clearing the queue.
     * Used internally by audio focus handlers.
     */
    private fun stopCurrentSpeech() {
        textToSpeech?.stop()
        isCurrentlySpeaking = false
        unregisterShakeListener()
        
        // Clean up media behavior effects
        cleanupMediaBehavior()
        
        // Hide reading notification since we're no longer speaking
        hideReadingNotification()
        
        Log.d(TAG, "Current TTS speech stopped by audio focus change")
        InAppLogger.logTTSEvent("TTS stopped by audio focus", "Focus loss interrupted speech")
    }
    
    // Call this when settings change
    fun refreshAllSettings() {
        loadFilterSettings()
        refreshSettings() // This calls the shake settings refresh
    }

    /**
     * MEDIA BEHAVIOR HANDLING (ignore, pause, duck, silence)
     *
     * IMPORTANT DEVELOPER NOTE:
     * - Android's audio focus and stream routing APIs are inconsistent across devices and OS versions.
     * - 'Pause' mode requests transient audio focus, but many devices/ROMs (especially on Android 13+) will not grant it to notification listeners or background services, so media may not pause.
     * - 'Duck' mode attempts to lower only the media stream, but on many devices, TTS is also routed through the media stream, so both are ducked together. The app recommends using the Notification stream for TTS, which works best for most users.
     * - All user-facing warnings and help text are based on extensive real-world testing and user feedback.
     * - If you revisit this code, check for new Android APIs or device-specific workarounds, but be aware that most limitations are outside app control.
     * - See BehaviorSettingsActivity and VoiceSettingsActivity for user guidance and warnings.
     */
    private fun handleMediaBehavior(appName: String, text: String, sbn: StatusBarNotification? = null): Boolean {
        val isMusicActive = audioManager.isMusicActive
        Log.d(TAG, "Media behavior check - isMusicActive: $isMusicActive, mediaBehavior: $mediaBehavior, isCurrentlySpeaking: $isCurrentlySpeaking")
        
        if (!isMusicActive) {
            Log.d(TAG, "No media currently playing, proceeding normally")
            return true
        }
        
        // If we're currently speaking, this might be our own TTS triggering media detection
        if (isCurrentlySpeaking) {
            Log.d(TAG, "Media detected while TTS is speaking - this is likely our own TTS, proceeding normally")
            InAppLogger.log("MediaBehavior", "Media detected while TTS is speaking - ignoring (likely our own TTS)")
            return true
        }
        
        Log.d(TAG, "Media is playing, applying media behavior: $mediaBehavior")
        InAppLogger.log("MediaBehavior", "Media detected, applying behavior: $mediaBehavior")
        // REFINED FAIL-SAFE: Only block true media control notifications (not all notifications from media apps)
        if (sbn != null && MediaNotificationDetector.isMediaNotification(sbn)) {
            val reason = MediaNotificationDetector.getMediaDetectionReason(sbn)
            Log.d(TAG, "REFINED FAIL-SAFE: Notification detected as a media control. Blocking speech. Reason: $reason")
            InAppLogger.logFilter("REFINED FAIL-SAFE: Blocked media control notification from $appName due to media control detection: $reason")
            // Log all extras for debugging
            val extras = sbn.notification.extras
            Log.d(TAG, "[RefinedFailSafe] Notification extras: $extras")
            return false // Block speech - never read out for true media controls
        }
        when (mediaBehavior) {
            "ignore" -> {
                Log.d(TAG, "Media behavior: IGNORE - proceeding normally")
                return true
            }
            "pause" -> {
                Log.d(TAG, "Media behavior: PAUSE - requesting audio focus")
                val focusGranted = requestAudioFocusForSpeech()
                // Always allow notification to be spoken after requesting audio focus, unless it's a media control notification
                if (!focusGranted) {
                    Log.w(TAG, "Audio focus not granted, but proceeding to speak notification anyway (pause mode)")
                    InAppLogger.log("MediaBehavior", "Audio focus not granted in pause mode. Proceeding to speak notification anyway.")
                }
                // Log device and audio info for diagnostics
                val voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
                val ttsUsage = voicePrefs.getInt("audio_usage", android.media.AudioAttributes.USAGE_ASSISTANT)
                val ttsContent = voicePrefs.getInt("content_type", android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                InAppLogger.log("MediaBehavior", "Pause mode triggered. Device: ${android.os.Build.MODEL}, Android: ${android.os.Build.VERSION.SDK_INT}, TTS usage: $ttsUsage, TTS content: $ttsContent, Media volume: $currentVolume/$maxVolume")
                return true
            }
            "duck" -> {
                Log.d(TAG, "Media behavior: DUCK - attempting enhanced ducking approach")
                InAppLogger.log("MediaBehavior", "Enhanced ducking initiated - trying system ducking first")
                
                // Step 1: Try enhanced ducking with system audio focus
                val enhancedDuckSuccess = tryEnhancedDucking()
                
                if (enhancedDuckSuccess) {
                    Log.d(TAG, "Enhanced ducking successful - system is handling media ducking")
                    InAppLogger.log("MediaBehavior", "System ducking activated successfully")
                    return true
                        } else {
            Log.d(TAG, "Enhanced ducking not available - falling back to manual volume control")
            InAppLogger.log("MediaBehavior", "Falling back to manual ducking")
                    
                    // Step 2: Fall back to manual ducking (existing implementation)
                    val ducked = duckMediaVolume()
                    
                    // Log device and audio info for diagnostics
                    val voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
                    val ttsUsage = voicePrefs.getInt("audio_usage", android.media.AudioAttributes.USAGE_ASSISTANT)
                    val ttsContent = voicePrefs.getInt("content_type", android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    InAppLogger.log("MediaBehavior", "Manual duck mode triggered. Device: ${android.os.Build.MODEL}, Android: ${android.os.Build.VERSION.SDK_INT}, TTS usage: $ttsUsage, TTS content: $ttsContent, Media volume: $currentVolume/$maxVolume")
                    
                    // Warn if TTS is routed through the same stream as media
                    if (ttsUsage == android.media.AudioAttributes.USAGE_MEDIA || ttsUsage == android.media.AudioAttributes.USAGE_UNKNOWN) {
                        Log.w(TAG, "TTS may be routed through the same stream as media. Ducking may lower TTS volume as well.")
                        InAppLogger.log("MediaBehavior", "Warning: TTS may be affected by ducking on this device. TTS usage: $ttsUsage")
                    }
                    
                    return ducked
                }
            }
            "silence" -> {
                Log.d(TAG, "Media behavior: SILENCE - not speaking due to active media")
                InAppLogger.log("MediaBehavior", "Silenced notification from $appName due to active media")
                return false
            }
            else -> {
                Log.d(TAG, "Unknown media behavior: $mediaBehavior, defaulting to ignore")
                return true
            }
        }
    }
    
    private fun requestAudioFocusForSpeech(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Use AudioFocusRequest for API 26+
            audioFocusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .build()
            
            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            // Use deprecated method for older versions
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                { focusChange -> handleAudioFocusChange(focusChange) },
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
    
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained - media should resume")
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS,
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost - media paused")
            }
        }
    }
    
    private fun duckMediaVolume(): Boolean {
        try {
            // Get current media volume
            val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            
            // Store original volume for restoration
            if (originalMusicVolume == -1) {
                originalMusicVolume = currentVolume
            }
            
            // Calculate ducked volume
            val duckedVolume = (maxVolume * duckingVolume / 100).coerceAtLeast(1)
            
            // Try VolumeShaper for smooth transitions on Android 8.0+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val volumeShaperResult = tryVolumeShaperDuck(currentVolume.toFloat(), duckedVolume.toFloat(), maxVolume)
                if (volumeShaperResult) {
                    Log.d(TAG, "VolumeShaper ducking applied from $currentVolume to $duckedVolume")
                    InAppLogger.log("MediaBehavior", "Smooth ducking applied to $duckingVolume% using VolumeShaper")
                    return true
                } else {
                    Log.d(TAG, "VolumeShaper ducking failed - falling back to manual volume control")
                }
            }
            
            // Fallback to abrupt volume change
            audioManager.setStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                duckedVolume,
                0
            )
            
            Log.d(TAG, "Media volume ducked from $currentVolume to $duckedVolume (max: $maxVolume)")
            InAppLogger.log("MediaBehavior", "Ducked media volume to $duckingVolume%")
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to duck media volume", e)
            return true // Proceed anyway
        }
    }
    
    private fun restoreMediaVolume() {
        if (originalMusicVolume != -1) {
            try {
                val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                
                Log.d(TAG, "Restoring media volume from $currentVolume to $originalMusicVolume (max: $maxVolume)")
                
                // Try smooth restoration on Android 8.0+ if we were using smooth ducking
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && isUsingVolumeShaper) {
                    Log.d(TAG, "Attempting smooth volume restoration from $currentVolume to $originalMusicVolume")
                    InAppLogger.log("MediaBehavior", "Smooth volume restoration using VolumeShaper")
                    
                    val volumeShaperResult = tryVolumeShaperRestore(currentVolume.toFloat(), originalMusicVolume.toFloat(), maxVolume)
                    if (volumeShaperResult) {
                        Log.d(TAG, "Smooth restoration started - will complete automatically")
                        // Don't reset originalMusicVolume here - let the smooth restoration handle it
                        return
                    } else {
                        Log.d(TAG, "Smooth restoration failed - falling back to immediate restoration")
                    }
                }
                
                // Immediate restoration (fallback or default)
                audioManager.setStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC,
                    originalMusicVolume,
                    0
                )
                Log.d(TAG, "Media volume immediately restored to $originalMusicVolume")
                InAppLogger.log("MediaBehavior", "Restored media volume")
                originalMusicVolume = -1
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore media volume", e)
                originalMusicVolume = -1 // Reset on error to prevent stuck state
            }
        } else {
            Log.d(TAG, "No original volume to restore (originalMusicVolume = -1)")
        }
    }
    
    private fun releaseAudioFocus() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
                audioFocusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus { focusChange ->
                handleAudioFocusChange(focusChange)
            }
        }
        
        Log.d(TAG, "Audio focus released")
    }
    
    // Enhanced ducking variables for tracking system ducking state
    private var enhancedDuckingFocusRequest: android.media.AudioFocusRequest? = null
    private var isUsingEnhancedDucking = false
    
    // Smooth ducking for gradual transitions (Android 8.0+)
    private var volumeShaper: android.media.VolumeShaper? = null // Kept for compatibility
    private var isUsingVolumeShaper = false // Actually tracks smooth ducking state
    
    /**
     * ENHANCED DUCKING APPROACH
     * 
     * This method attempts to use modern Android audio focus APIs to achieve better ducking behavior.
     * It requests AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK which tells the system that we want temporary
     * focus and that other apps should duck (lower their volume) rather than pause completely.
     * 
     * Benefits:
     * - System handles the ducking automatically
     * - More consistent timing across devices
     * - Better integration with Android's audio policy
     * - Can extend ducking duration to match TTS length
     * 
     * Falls back to manual ducking if system ducking is not supported or fails.
     */
    private fun tryEnhancedDucking(): Boolean {
        // Only attempt enhanced ducking on Android 8.0+ where the APIs are more reliable
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            Log.d(TAG, "Enhanced ducking not available on Android < 8.0")
            return false
        }
        
        try {
            // Get TTS audio attributes from voice settings
            val voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
            val ttsUsageIndex = voicePrefs.getInt("audio_usage", 4) // Default to ASSISTANT index
            val ttsContentIndex = voicePrefs.getInt("content_type", 0) // Default to SPEECH index
            
            // Convert index to actual usage constant (matching VoiceSettingsActivity)
            val ttsUsage = when (ttsUsageIndex) {
                0 -> android.media.AudioAttributes.USAGE_MEDIA
                1 -> android.media.AudioAttributes.USAGE_NOTIFICATION
                2 -> android.media.AudioAttributes.USAGE_ALARM
                3 -> android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
                4 -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                else -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE // Safe fallback
            }
            
            val ttsContent = when (ttsContentIndex) {
                0 -> android.media.AudioAttributes.CONTENT_TYPE_SPEECH
                1 -> android.media.AudioAttributes.CONTENT_TYPE_MUSIC
                2 -> android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION
                3 -> android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION
                else -> android.media.AudioAttributes.CONTENT_TYPE_SPEECH // Safe fallback
            }
            
            Log.d(TAG, "Enhanced ducking - TTS usage index: $ttsUsageIndex -> usage constant: $ttsUsage")
            InAppLogger.log("MediaBehavior", "Enhanced ducking - TTS usage: $ttsUsage (index: $ttsUsageIndex)")
            
            // Create audio attributes for our TTS
            val ttsAudioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(ttsUsage)
                .setContentType(ttsContent)
                .build()
            
            // Create focus request for enhanced ducking
            enhancedDuckingFocusRequest = android.media.AudioFocusRequest.Builder(
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
                .setAudioAttributes(ttsAudioAttributes)
                .setOnAudioFocusChangeListener { focusChange ->
                    handleEnhancedDuckingFocusChange(focusChange)
                }
                .setAcceptsDelayedFocusGain(false) // We want immediate response for notifications
                .setWillPauseWhenDucked(false) // We don't want our TTS to be ducked
                .build()
            
            // Check if enhanced ducking is viable for this TTS usage
            // Note: Some devices reject enhanced ducking for certain usage types
            if (ttsUsage == android.media.AudioAttributes.USAGE_MEDIA || 
                ttsUsage == android.media.AudioAttributes.USAGE_UNKNOWN ||
                ttsUsage == android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) {
                Log.d(TAG, "Enhanced ducking skipped - TTS usage ($ttsUsage) may be ducked by system or rejected by device")
                InAppLogger.log("MediaBehavior", "Enhanced ducking skipped - TTS usage $ttsUsage may be affected by system ducking or rejected by device audio policy")
                return false
            }
            
            // Allow Notification stream to try enhanced ducking since some users report it works better
            if (ttsUsage == android.media.AudioAttributes.USAGE_NOTIFICATION) {
                Log.d(TAG, "Enhanced ducking with Notification stream - may work on some devices despite potential conflicts")
                InAppLogger.log("MediaBehavior", "Attempting enhanced ducking with Notification stream (usage: $ttsUsage) - device-dependent behavior")
            }
            
            // Request audio focus
            val result = audioManager.requestAudioFocus(enhancedDuckingFocusRequest!!)
            
            // Debug logging to understand the result codes  
            val resultText = when (result) {
                android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "GRANTED"
                android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "FAILED" 
                android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "DELAYED"
                else -> "UNKNOWN($result)"
            }
            Log.d(TAG, "Enhanced ducking audio focus result: $resultText ($result)")
            InAppLogger.log("MediaBehavior", "Enhanced ducking audio focus result: $resultText (device: ${android.os.Build.MODEL}, usage: $ttsUsage)")
            
            if (result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                isUsingEnhancedDucking = true
                Log.d(TAG, "Enhanced ducking focus GRANTED - system should duck other media automatically")
                InAppLogger.log("MediaBehavior", "Enhanced ducking GRANTED - system handling media volume reduction")
                return true
            } else {
                Log.d(TAG, "Enhanced ducking focus request $resultText - falling back to manual ducking")
                InAppLogger.log("MediaBehavior", "Enhanced ducking $resultText on ${android.os.Build.MODEL} - this device may have restrictive audio focus policies")
                cleanupEnhancedDucking()
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Enhanced ducking failed with exception", e)
            InAppLogger.logError("MediaBehavior", "Enhanced ducking exception: ${e.message}")
            cleanupEnhancedDucking()
            return false
        }
    }
    
    /**
     * Handle audio focus changes during enhanced ducking.
     * This is called by the system when audio focus state changes.
     */
    private fun handleEnhancedDuckingFocusChange(focusChange: Int) {
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Enhanced ducking: Audio focus gained")
                // TTS should continue/start speaking
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Enhanced ducking: Audio focus lost permanently")
                InAppLogger.log("MediaBehavior", "Enhanced ducking focus lost - cleaning up")
                cleanupEnhancedDucking()
                // Stop TTS if it's currently speaking
                if (isCurrentlySpeaking) {
                    stopCurrentSpeech()
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Enhanced ducking: Audio focus lost temporarily")
                // Pause TTS temporarily - it should resume when focus is regained
                if (isCurrentlySpeaking) {
                    textToSpeech?.stop()
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Enhanced ducking: Can duck (should not happen with our focus request)")
                // This shouldn't happen with our setup, but handle gracefully
            }
        }
    }
    
    /**
     * Release enhanced ducking focus and clean up state.
     * This should be called when TTS completes or is interrupted.
     */
    private fun releaseEnhancedDucking() {
        if (isUsingEnhancedDucking && enhancedDuckingFocusRequest != null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    audioManager.abandonAudioFocusRequest(enhancedDuckingFocusRequest!!)
                    Log.d(TAG, "Enhanced ducking focus released - media should return to normal volume")
                    InAppLogger.log("MediaBehavior", "Enhanced ducking released - system restoring media volume")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing enhanced ducking focus", e)
                InAppLogger.logError("MediaBehavior", "Error releasing enhanced ducking: ${e.message}")
            }
            
            cleanupEnhancedDucking()
        }
    }
    
    /**
     * Clean up enhanced ducking state without focus operations.
     * Used for error handling and state reset.
     */
    private fun cleanupEnhancedDucking() {
        enhancedDuckingFocusRequest = null
        isUsingEnhancedDucking = false
    }
    
    /**
     * SMOOTH DUCKING APPROACH
     * 
     * Provides smooth, gradual volume transitions instead of abrupt changes.
     * This creates a much more professional audio experience similar to professional audio software.
     * Available on Android 8.0+ (API 26+).
     * 
     * Note: Originally designed for VolumeShaper, but VolumeShaper requires access to specific
     * AudioTrack/MediaPlayer instances which aren't available for the media stream directly.
     * Instead, we use smooth manual volume transitions with Handler-based stepping.
     */
    
    /**
     * Apply smooth volume ducking using gradual volume changes.
     * Creates a smooth fade-down curve over 500ms.
     * 
     * @param fromVolume Current volume level (0.0 to maxVolume)
     * @param toVolume Target ducked volume level (0.0 to maxVolume)  
     * @param maxVolume Maximum volume for the stream
     * @return true if smooth ducking was successfully started, false otherwise
     */
    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
    private fun tryVolumeShaperDuck(fromVolume: Float, toVolume: Float, maxVolume: Int): Boolean {
        return try {
            // Clean up any existing smooth ducking
            cleanupVolumeShaper()
            
            val fromVolumeInt = fromVolume.toInt()
            val toVolumeInt = toVolume.toInt()
            
            if (fromVolumeInt == toVolumeInt) {
                Log.d(TAG, "Smooth ducking skipped - volumes are the same ($fromVolumeInt)")
                return false
            }
            
            isUsingVolumeShaper = true
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val durationMs = 500L
            val steps = 10
            val stepDelay = durationMs / steps
            val volumeStep = (toVolumeInt - fromVolumeInt).toFloat() / steps
            
            Log.d(TAG, "Smooth ducking started: $fromVolumeInt → $toVolumeInt over ${durationMs}ms")
            
            for (i in 1..steps) {
                val delay = stepDelay * i
                
                handler.postDelayed({
                    if (isUsingVolumeShaper) { // Check if still active
                        val currentVolume = (fromVolumeInt + (volumeStep * i)).toInt()
                            .coerceIn(0, maxVolume)
                        
                        try {
                            audioManager.setStreamVolume(
                                android.media.AudioManager.STREAM_MUSIC,
                                currentVolume,
                                0
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Smooth ducking step $i failed", e)
                        }
                    }
                }, delay)
            }
            
            true
            
        } catch (e: Exception) {
            Log.d(TAG, "Smooth ducking failed: ${e.message}")
            cleanupVolumeShaper()
            false
        }
    }
    
    /**
     * Apply smooth volume restoration using gradual volume changes.
     * Creates a smooth fade-up curve over 300ms.
     * 
     * @param fromVolume Current volume level (0.0 to maxVolume)
     * @param toVolume Target restored volume level (0.0 to maxVolume)
     * @param maxVolume Maximum volume for the stream
     * @return true if smooth restoration was successfully started, false otherwise
     */
    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
    private fun tryVolumeShaperRestore(fromVolume: Float, toVolume: Float, maxVolume: Int): Boolean {
        return try {
            val fromVolumeInt = fromVolume.toInt()
            val toVolumeInt = toVolume.toInt()
            
            if (fromVolumeInt == toVolumeInt) {
                Log.d(TAG, "Smooth restoration skipped - volumes are the same ($fromVolumeInt)")
                originalMusicVolume = -1 // Clear since no restoration needed
                cleanupVolumeShaper()
                return false
            }
            
            // Keep smooth ducking active for restoration
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val durationMs = 300L // Faster restoration than ducking
            val steps = 8
            val stepDelay = durationMs / steps
            val volumeStep = (toVolumeInt - fromVolumeInt).toFloat() / steps
            
            Log.d(TAG, "Smooth restoration: $fromVolumeInt → $toVolumeInt over ${durationMs}ms")
            
            // Set up timeout failsafe (in case smooth restoration fails)
            handler.postDelayed({
                if (originalMusicVolume != -1 && isUsingVolumeShaper) {
                    Log.w(TAG, "Smooth restoration timeout - forcing immediate restoration to $originalMusicVolume")
                    try {
                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, originalMusicVolume, 0)
                        originalMusicVolume = -1
                        cleanupVolumeShaper()
                    } catch (e: Exception) {
                        Log.e(TAG, "Timeout restoration failed", e)
                    }
                }
            }, durationMs + 200) // Timeout slightly after expected completion
            
            for (i in 1..steps) {
                val delay = stepDelay * i
                
                handler.postDelayed({
                    if (isUsingVolumeShaper && originalMusicVolume != -1) { // Check if still active and not cancelled
                        val currentVolume = (fromVolumeInt + (volumeStep * i)).toInt()
                            .coerceIn(0, maxVolume)
                        
                        try {
                            audioManager.setStreamVolume(
                                android.media.AudioManager.STREAM_MUSIC,
                                currentVolume,
                                0
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Smooth restoration step $i failed", e)
                        }
                        
                        // Final cleanup after last step
                        if (i == steps) {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                if (originalMusicVolume != -1) { // Only clean up if not already cleaned by timeout
                                    Log.d(TAG, "Smooth restoration completed - final volume: $currentVolume")
                                    originalMusicVolume = -1 // Clear the stored volume
                                    cleanupVolumeShaper() // Clean up smooth ducking state
                                }
                            }, 25) // Small delay to ensure volume is set
                        }
                    }
                }, delay)
            }
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Smooth restoration failed: ${e.message}")
            originalMusicVolume = -1
            cleanupVolumeShaper()
            false
        }
    }
    
    /**
     * Clean up smooth ducking resources and reset state.
     * Should be called when transitioning to manual volume control or when done with smooth transitions.
     */
    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
    private fun cleanupVolumeShaper() {
        try {
            // Cancel any pending smooth ducking operations
            volumeShaper?.close()
            volumeShaper = null
            isUsingVolumeShaper = false
            Log.d(TAG, "Smooth ducking cleaned up")
        } catch (e: Exception) {
            Log.d(TAG, "Smooth ducking cleanup error: ${e.message}")
        }
    }
    
    private fun cleanupMediaBehavior() {
        when (mediaBehavior) {
            "pause" -> {
                // Release audio focus to allow media to resume
                releaseAudioFocus()
            }
            "duck" -> {
                // Clean up enhanced ducking first if it was used
                if (isUsingEnhancedDucking) {
                    releaseEnhancedDucking()
                } else {
                    // Restore original media volume for manual ducking
                    restoreMediaVolume()
                }
                
                // Clean up smooth ducking if it was used
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && isUsingVolumeShaper) {
                    Log.d(TAG, "Cleaning up smooth ducking and ensuring volume restoration")
                    // Force immediate restoration if smooth restoration didn't complete
                    if (originalMusicVolume != -1) {
                        try {
                            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, originalMusicVolume, 0)
                            Log.d(TAG, "Force-restored volume to $originalMusicVolume during cleanup")
                            originalMusicVolume = -1
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to force-restore volume during cleanup", e)
                        }
                    }
                    cleanupVolumeShaper()
                }
            }
            // "ignore" and "silence" don't need cleanup
        }
    }
    
    /**
     * Clean up media behavior with delayed VolumeShaper cleanup to allow smooth restoration to complete.
     * Used when TTS completes normally to avoid race condition between restoration and cleanup.
     */
    private fun cleanupMediaBehaviorDelayed() {
        when (mediaBehavior) {
            "pause" -> {
                // Release audio focus to allow media to resume
                releaseAudioFocus()
            }
            "duck" -> {
                // Clean up enhanced ducking first if it was used
                if (isUsingEnhancedDucking) {
                    releaseEnhancedDucking()
                } else {
                    // Restore original media volume for manual ducking
                    restoreMediaVolume()
                }
                
                // Delay VolumeShaper cleanup to allow smooth restoration to complete
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && isUsingVolumeShaper) {
                    Log.d(TAG, "Scheduling delayed VolumeShaper cleanup to allow smooth restoration")
                    
                    // Schedule cleanup after smooth restoration should complete (300ms + safety buffer)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "Executing delayed VolumeShaper cleanup")
                        
                        // Final safety net - force immediate restoration if smooth restoration didn't complete
                        if (originalMusicVolume != -1) {
                            try {
                                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, originalMusicVolume, 0)
                                Log.d(TAG, "Force-restored volume to $originalMusicVolume during delayed cleanup")
                                originalMusicVolume = -1
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to force-restore volume during delayed cleanup", e)
                            }
                        }
                        cleanupVolumeShaper()
                    }, 800) // Wait longer than restoration duration (300ms) + safety buffer
                }
            }
            // "ignore" and "silence" don't need cleanup
        }
    }

    private fun handleNotificationBehavior(packageName: String, appName: String, text: String, conditionalDelaySeconds: Int = -1, sbn: StatusBarNotification? = null) {
        val isPriorityApp = priorityApps.contains(packageName)
        val queuedNotification = QueuedNotification(appName, text, isPriorityApp, conditionalDelaySeconds, sbn)
        
        Log.d(TAG, "Handling notification behavior - Mode: $notificationBehavior, App: $appName, Currently speaking: $isCurrentlySpeaking, Queue size: ${notificationQueue.size}")
        InAppLogger.logNotification("Processing notification from $appName (mode: $notificationBehavior, speaking: $isCurrentlySpeaking)")
        
        // Check media behavior first, now with sbn for strict filtering
        if (!handleMediaBehavior(appName, text, sbn)) {
            Log.d(TAG, "Media behavior blocked notification from $appName (sbn: ${sbn?.packageName})")
            InAppLogger.logFilter("Media behavior blocked notification from $appName (sbn: ${sbn?.packageName})")
            return
        }
        
        when (notificationBehavior) {
            "interrupt" -> {
                Log.d(TAG, "INTERRUPT mode: Speaking immediately and interrupting any current speech")
                speakNotificationImmediate(appName, text, conditionalDelaySeconds, sbn)
            }
            "queue" -> {
                Log.d(TAG, "QUEUE mode: Adding to queue")
                notificationQueue.add(queuedNotification)
                Log.d(TAG, "Added to queue. New queue size: ${notificationQueue.size}")
                processNotificationQueue()
            }
            "skip" -> {
                if (!isCurrentlySpeaking) {
                    Log.d(TAG, "SKIP mode: Not currently speaking, will speak now")
                    speakNotificationImmediate(appName, text, conditionalDelaySeconds, sbn)
                } else {
                    Log.d(TAG, "SKIP mode: Currently speaking, skipping notification from $appName")
                }
            }
            "smart" -> {
                if (isPriorityApp) {
                    Log.d(TAG, "SMART mode: Priority app $appName - interrupting")
                    speakNotificationImmediate(appName, text, conditionalDelaySeconds, sbn)
                } else {
                    Log.d(TAG, "SMART mode: Regular app $appName - adding to queue")
                    notificationQueue.add(queuedNotification)
                    processNotificationQueue()
                }
            }
            else -> {
                Log.d(TAG, "UNKNOWN mode '$notificationBehavior': Defaulting to interrupt")
                speakNotificationImmediate(appName, text, conditionalDelaySeconds, sbn)
            }
        }
    }

    private fun processNotificationQueue() {
        Log.d(TAG, "Processing queue - Currently speaking: $isCurrentlySpeaking, Queue size: ${notificationQueue.size}")
        if (!isCurrentlySpeaking && notificationQueue.isNotEmpty()) {
            val queuedNotification = notificationQueue.removeAt(0)
            Log.d(TAG, "Processing next queued notification from ${queuedNotification.appName}")
            speakNotificationImmediate(queuedNotification.appName, queuedNotification.text, queuedNotification.conditionalDelaySeconds, queuedNotification.sbn)
        } else if (isCurrentlySpeaking) {
            Log.d(TAG, "Still speaking, queue will be processed when current speech finishes")
        } else {
            Log.d(TAG, "Queue is empty")
        }
    }

    private fun speakNotificationImmediate(appName: String, text: String, conditionalDelaySeconds: Int = -1, sbn: StatusBarNotification? = null) {
        if (!isTtsInitialized || textToSpeech == null) {
            Log.w(TAG, "TTS not initialized, cannot speak notification")
            return
        }
        
        // Cancel any existing pending readout
        pendingReadoutRunnable?.let { runnable ->
            delayHandler?.removeCallbacks(runnable)
            pendingReadoutRunnable = null
        }
        
        // Format: "AppName notified you: notification content" (unless it's already a private message)
        val speechText = if (text.startsWith("You received a private notification from") || 
                            text.startsWith("Hai ricevuto una notifica privata da") ||
                            text.startsWith("Du hast eine private Benachrichtigung von")) {
            text // Private messages already include the app name, so don't add prefix
        } else {
            formatSpeechText(appName, text, packageName, sbn)
        }
        
        // Determine which delay to use (conditional delay overrides global delay)
        val effectiveDelay = if (conditionalDelaySeconds > 0) {
            Log.d(TAG, "Using conditional delay: ${conditionalDelaySeconds}s (overrides global delay)")
            conditionalDelaySeconds
        } else {
            delayBeforeReadout
        }
        
        if (effectiveDelay > 0) {
            Log.d(TAG, "Delaying speech by ${effectiveDelay}s")
            pendingReadoutRunnable = Runnable {
                executeSpeech(speechText)
            }
            delayHandler?.postDelayed(pendingReadoutRunnable!!, (effectiveDelay * 1000).toLong())
        } else {
            executeSpeech(speechText)
        }
    }
    
    private fun executeSpeech(speechText: String) {
        Log.d(TAG, "Executing speech: $speechText")
        InAppLogger.log("Service", "Executing speech: ${speechText.take(50)}...")
        
        // CRITICAL: Force refresh voice settings before each speech to ensure they're applied
        // This prevents issues where voice settings might not be current
        // The voice settings will respect the override logic (specific voice > language)
        InAppLogger.log("Service", "Refreshing voice settings before speech execution")
        applyVoiceSettings()
        
        // Check TTS health before attempting to speak
        if (!checkTtsHealth()) {
            Log.e(TAG, "TTS health check failed - cannot speak")
            InAppLogger.logError("Service", "TTS health check failed - cannot speak")
            return
        }
        
        isCurrentlySpeaking = true
        
        // Show reading notification if enabled
        showReadingNotification(currentAppName, currentSpeechText)
        
        // Set pocket mode tracking variables
        if (isPocketModeEnabled) {
            wasSensorCoveredAtStart = isSensorCurrentlyCovered
            hasSensorBeenUncovered = false
            Log.d(TAG, "Pocket mode: Readout starting - was sensor covered at start: $wasSensorCoveredAtStart")
        }
        
        // Register shake listener now that we're about to speak
        registerShakeListener()
        
        // Speak with queue mode FLUSH to interrupt any previous speech
        Log.d(TAG, "Calling TTS.speak()...")
        val speakResult = textToSpeech?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "notification_utterance")
        Log.d(TAG, "TTS.speak() returned: $speakResult")
        InAppLogger.log("Service", "TTS.speak() called, result: $speakResult")
        
        // Check if speak() failed
        if (speakResult == TextToSpeech.ERROR) {
            Log.e(TAG, "TTS.speak() returned ERROR - attempting recovery")
            InAppLogger.logError("Service", "TTS.speak() returned ERROR - attempting recovery")
            attemptTtsRecovery("speak() returned ERROR")
            isCurrentlySpeaking = false
            unregisterShakeListener()
            return
        }
        
        // Set up completion listener
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // TTS started
                Log.d(TAG, "TTS utterance started: $utteranceId")
                InAppLogger.logTTSEvent("TTS started", speechText.take(50))
            }
            
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "notification_utterance") {
                    Log.d(TAG, "TTS utterance completed: $utteranceId")
                    isCurrentlySpeaking = false
                    
                    // Hide reading notification
                    hideReadingNotification()
                    
                    // Unregister shake listener since we're done speaking
                    unregisterShakeListener()
                    
                    InAppLogger.logTTSEvent("TTS completed", "Utterance finished")
                    
                    // Clean up media behavior effects (with delay for smooth restoration)
                    cleanupMediaBehaviorDelayed()
                    
                    // Process next item in queue if any
                    processNotificationQueue()
                }
            }
            
            override fun onError(utteranceId: String?) {
                if (utteranceId == "notification_utterance") {
                    Log.e(TAG, "TTS utterance error: $utteranceId")
                    isCurrentlySpeaking = false
                    
                    // Hide reading notification
                    hideReadingNotification()
                    
                    unregisterShakeListener()
                    Log.e(TAG, "TTS error occurred")
                    InAppLogger.logTTSEvent("TTS error", "Utterance failed")
                    
                    // Attempt recovery for utterance errors
                    attemptTtsRecovery("Utterance error: $utteranceId")
                    
                    // Clean up media behavior effects
                    cleanupMediaBehavior()
                    
                    // Process next item in queue if any
                    processNotificationQueue()
                }
            }
        })
    }

    /**
     * CRITICAL: Applies voice settings to the service's TTS instance.
     * This method is called:
     * 1. During TTS initialization
     * 2. When voice settings change via preference listener
     * 3. Before each notification (to ensure settings are current)
     * 
     * The voice settings will respect the override logic (specific voice > language).
     */
    private fun applyVoiceSettings() {
        if (isTtsInitialized && textToSpeech != null) {
            InAppLogger.log("Service", "Applying voice settings to service TTS instance")
            VoiceSettingsActivity.applyVoiceSettings(textToSpeech!!, voiceSettingsPrefs)
            Log.d(TAG, "Voice settings applied")
            InAppLogger.log("Service", "Voice settings applied to service TTS instance")
        } else {
            InAppLogger.log("Service", "Cannot apply voice settings - TTS not initialized or null")
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(TAG, "Settings changed: $key")
        
        when (key) {
            KEY_NOTIFICATION_BEHAVIOR, KEY_PRIORITY_APPS -> {
                // Reload behavior settings
                notificationBehavior = sharedPreferences?.getString(KEY_NOTIFICATION_BEHAVIOR, "interrupt") ?: "interrupt"
                priorityApps = HashSet(sharedPreferences?.getStringSet(KEY_PRIORITY_APPS, HashSet()) ?: HashSet())
                Log.d(TAG, "Behavior settings updated - mode: $notificationBehavior, priority apps: ${priorityApps.size}")
            }
            KEY_MEDIA_BEHAVIOR, KEY_DUCKING_VOLUME -> {
                // Reload media behavior settings
                mediaBehavior = sharedPreferences?.getString(KEY_MEDIA_BEHAVIOR, "ignore") ?: "ignore"
                duckingVolume = sharedPreferences?.getInt(KEY_DUCKING_VOLUME, 30) ?: 30
                Log.d(TAG, "Media behavior settings updated - mode: $mediaBehavior, ducking volume: $duckingVolume%")
            }
            KEY_APP_LIST_MODE, KEY_APP_LIST, KEY_APP_PRIVATE_FLAGS, 
            KEY_WORD_BLACKLIST, KEY_WORD_BLACKLIST_PRIVATE, KEY_WORD_REPLACEMENTS -> {
                // Reload filter settings
                loadFilterSettings()
                Log.d(TAG, "Filter settings updated")
            }
            KEY_SHAKE_TO_STOP_ENABLED, KEY_SHAKE_THRESHOLD, KEY_SHAKE_TIMEOUT_SECONDS, KEY_WAVE_TIMEOUT_SECONDS, "pocket_mode_enabled" -> {
                // Reload shake and wave settings
                refreshSettings()
                Log.d(TAG, "Shake/wave settings updated")
            }
            KEY_DELAY_BEFORE_READOUT -> {
                // Reload delay settings
                delayBeforeReadout = sharedPreferences?.getInt(KEY_DELAY_BEFORE_READOUT, 0) ?: 0
                Log.d(TAG, "Delay settings updated - delay: ${delayBeforeReadout}s")
            }
            KEY_MEDIA_FILTERING_ENABLED, KEY_MEDIA_FILTER_EXCEPTED_APPS, KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS, KEY_MEDIA_FILTERED_APPS, KEY_MEDIA_FILTERED_APPS_PRIVATE -> {
                // Reload media filtering settings
                loadFilterSettings()
                Log.d(TAG, "Media filtering settings updated")
            }
            KEY_PERSISTENT_FILTERING_ENABLED -> {
                // Reload persistent filtering settings
                isPersistentFilteringEnabled = sharedPreferences?.getBoolean(KEY_PERSISTENT_FILTERING_ENABLED, true) ?: true
                Log.d(TAG, "Persistent filtering enabled updated: $isPersistentFilteringEnabled")
            }
            "filter_persistent", "filter_silent", "filter_foreground_services", "filter_low_priority", "filter_system_notifications" -> {
                // Reload persistent filtering category settings
                filterPersistent = sharedPreferences?.getBoolean("filter_persistent", true) ?: true
                filterSilent = sharedPreferences?.getBoolean("filter_silent", true) ?: true
                filterForegroundServices = sharedPreferences?.getBoolean("filter_foreground_services", true) ?: true
                filterLowPriority = sharedPreferences?.getBoolean("filter_low_priority", false) ?: false
                filterSystemNotifications = sharedPreferences?.getBoolean("filter_system_notifications", false) ?: false
                Log.d(TAG, "Persistent filtering category settings updated - persistent: $filterPersistent, silent: $filterSilent, foreground: $filterForegroundServices, low: $filterLowPriority, system: $filterSystemNotifications")
            }
            KEY_COOLDOWN_APPS -> {
                loadCooldownSettings()
                Log.d(TAG, "Cooldown settings updated - apps: ${appCooldownSettings.size}")
            }
            KEY_SPEECH_TEMPLATE -> {
                // Reload speech template
                speechTemplate = sharedPreferences?.getString(KEY_SPEECH_TEMPLATE, "{app} notified you: {content}") ?: "{app} notified you: {content}"
                Log.d(TAG, "Speech template updated")
            }
            KEY_PERSISTENT_NOTIFICATION -> {
                // Handle persistent notification setting change
                val isPersistentNotificationEnabled = sharedPreferences?.getBoolean(KEY_PERSISTENT_NOTIFICATION, false) ?: false
                if (isPersistentNotificationEnabled) {
                    checkAndShowPersistentNotification()
                } else {
                    hidePersistentNotification()
                }
                Log.d(TAG, "Persistent notification setting updated: $isPersistentNotificationEnabled")
            }
            KEY_NOTIFICATION_WHILE_READING -> {
                // Handle notification while reading setting change
                val isNotificationWhileReadingEnabled = sharedPreferences?.getBoolean(KEY_NOTIFICATION_WHILE_READING, false) ?: false
                Log.d(TAG, "Notification while reading setting updated: $isNotificationWhileReadingEnabled")
                // Note: Reading notifications are shown/hidden dynamically during TTS, so no immediate action needed here
            }
        }
    }

    private fun matchesWordFilter(text: String, word: String): Boolean {
        return text.contains(word, ignoreCase = true)
    }
    
    /**
     * Format speech text using the custom template with placeholders
     */
    private fun formatSpeechText(appName: String, text: String, packageName: String, sbn: StatusBarNotification?): String {
        // Extract notification components from StatusBarNotification if available
        val title = sbn?.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val notificationText = sbn?.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = sbn?.notification?.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val summaryText = sbn?.notification?.extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
        val infoText = sbn?.notification?.extras?.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() ?: ""
        
        // Get current time and date
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val date = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date())
        val timestamp = SimpleDateFormat("HH:mm MMM dd", Locale.getDefault()).format(Date())
        
        // Handle app name with custom names and privacy settings
        val appDisplayName = when {
            privateApps.contains(packageName) -> "An app"
            else -> getCustomAppName(packageName) ?: appName
        }
        
        // Get notification metadata
        val priority = when (sbn?.notification?.priority ?: 0) {
            Notification.PRIORITY_MIN -> "Min"
            Notification.PRIORITY_LOW -> "Low"
            Notification.PRIORITY_DEFAULT -> "Default"
            Notification.PRIORITY_HIGH -> "High"
            Notification.PRIORITY_MAX -> "Max"
            else -> "Default"
        }
        
        val category = sbn?.notification?.category ?: "Unknown"
        val channel = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            sbn?.notification?.channelId ?: "Unknown"
        } else {
            "Unknown"
        }
        
        // Handle varied template by randomly selecting a format
        val templateToUse = if (speechTemplate == "VARIED") {
            getLocalizedVariedFormatsImproved().random()
        } else {
            speechTemplate
        }
        
        // Process the template with all available placeholders
        var processedTemplate = templateToUse
            .replace("{app}", appDisplayName)
            .replace("{package}", packageName)
            .replace("{content}", text)
            .replace("{title}", title)
            .replace("{text}", notificationText)
            .replace("{bigtext}", bigText)
            .replace("{summary}", summaryText)
            .replace("{info}", infoText)
            .replace("{time}", time)
            .replace("{date}", date)
            .replace("{timestamp}", timestamp)
            .replace("{priority}", priority)
            .replace("{category}", category)
            .replace("{channel}", channel)
        

        
        return processedTemplate.trim()
    }

    /**
     * Get localized TTS string based on user's TTS language setting
     */
    private fun getLocalizedTtsString(stringResId: Int): String {
        val ttsLanguageCode = voiceSettingsPrefs?.getString("tts_language", "system") ?: "system"
        return TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, stringResId)
    }

    /**
     * Get localized template string with placeholders replaced
     * This is the new improved method for template-based localization
     */
    private fun getLocalizedTemplate(templateKey: String, appName: String, content: String): String {
        val templateResId = getTemplateResourceId(templateKey)
        val template = getLocalizedTtsString(templateResId)
        
        // Replace placeholders with actual values
        val result = template
            .replace("{app}", appName)
            .replace("{content}", content)
        
        Log.d(TAG, "Template localization - Key: $templateKey, App: $appName, Result: $result")
        return result
    }

    /**
     * Get resource ID for template strings
     */
    private fun getTemplateResourceId(templateKey: String): Int {
        return when (templateKey) {
            "notified_you" -> R.string.tts_template_notified_you
            "reported" -> R.string.tts_template_reported
            "saying" -> R.string.tts_template_saying
            "alerts_you" -> R.string.tts_template_alerts_you
            "update_from" -> R.string.tts_template_update_from
            "notification" -> R.string.tts_template_notification
            "new_notification" -> R.string.tts_template_new_notification
            "new_from" -> R.string.tts_template_new_from
            "said" -> R.string.tts_template_said
            "updated_you" -> R.string.tts_template_updated_you
            "new_notification_from" -> R.string.tts_template_new_notification_from
            "new_update_from" -> R.string.tts_template_new_update_from
            "notification_from" -> R.string.tts_template_notification_from
            "says" -> R.string.tts_template_says
            "private_notification" -> R.string.tts_template_private_notification
            else -> R.string.tts_template_notified_you // Default fallback
        }
    }

    /**
     * Get localized varied formats using template-based approach
     * This is the new improved method that uses complete sentence templates
     */
    private fun getLocalizedVariedFormatsImproved(): Array<String> {
        return arrayOf(
            getLocalizedTemplate("notified_you", "{app}", "{content}"),
            getLocalizedTemplate("reported", "{app}", "{content}"),
            getLocalizedTemplate("saying", "{app}", "{content}"),
            getLocalizedTemplate("notification_from", "{app}", "{content}"),
            getLocalizedTemplate("alerts_you", "{app}", "{content}"),
            getLocalizedTemplate("update_from", "{app}", "{content}"),
            getLocalizedTemplate("says", "{app}", "{content}"),
            getLocalizedTemplate("notification", "{app}", "{content}"),
            getLocalizedTemplate("new_notification", "{app}", "{content}"),
            getLocalizedTemplate("new_from", "{app}", "{content}"),
            getLocalizedTemplate("said", "{app}", "{content}"),
            getLocalizedTemplate("updated_you", "{app}", "{content}"),
            getLocalizedTemplate("new_notification_from", "{app}", "{content}"),
            getLocalizedTemplate("new_update_from", "{app}", "{content}"),
            "{app}: {content}" // Simple fallback
        )
    }

    // MARK: - SpeakThat Notification Methods
    
    /**
     * Check if persistent notification should be shown and show it if conditions are met
     */
    private fun checkAndShowPersistentNotification() {
        val isPersistentNotificationEnabled = sharedPreferences?.getBoolean(KEY_PERSISTENT_NOTIFICATION, false) ?: false
        val isMasterSwitchEnabled = MainActivity.isMasterSwitchEnabled(this)
        
        Log.d(TAG, "Checking persistent notification: enabled=$isPersistentNotificationEnabled, masterSwitch=$isMasterSwitchEnabled")
        
        if (isPersistentNotificationEnabled && isMasterSwitchEnabled) {
            showPersistentNotification()
        } else {
            Log.d(TAG, "Persistent notification conditions not met: enabled=$isPersistentNotificationEnabled, masterSwitch=$isMasterSwitchEnabled")
        }
    }
    
    /**
     * Show persistent notification when SpeakThat is active
     */
    private fun showPersistentNotification() {
        try {
            val isPersistentNotificationEnabled = sharedPreferences?.getBoolean(KEY_PERSISTENT_NOTIFICATION, false) ?: false
            if (!isPersistentNotificationEnabled) {
                Log.d(TAG, "Persistent notification disabled in settings")
                return
            }
            
            // Check notification permission for Android 13+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted - cannot show persistent notification")
                    InAppLogger.logError("Notifications", "POST_NOTIFICATIONS permission not granted")
                    return
                }
            }
            
            // Create notification channel for Android O+
            createNotificationChannel()
            
            // Create intent for opening SpeakThat
            val openAppIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Build notification
            val notification = NotificationCompat.Builder(this, "SpeakThat_Channel")
                .setContentTitle("SpeakThat Active")
                .setContentText("Tap to open SpeakThat settings")
                .setSmallIcon(R.drawable.speakthaticon)
                .setOngoing(true) // Persistent notification
                .setSilent(true) // Silent notification
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openAppPendingIntent)
                .addAction(
                    R.drawable.speakthaticon,
                    "Open SpeakThat!",
                    openAppPendingIntent
                )
                .build()
            
            // Show notification
            notificationManager.notify(PERSISTENT_NOTIFICATION_ID, notification)
            Log.d(TAG, "Persistent notification shown with ID: $PERSISTENT_NOTIFICATION_ID")
            InAppLogger.log("Notifications", "Persistent notification shown with ID: $PERSISTENT_NOTIFICATION_ID")
            
            // Verify notification was actually posted
            val activeNotifications = notificationManager.activeNotifications
            val ourNotification = activeNotifications.find { it.id == PERSISTENT_NOTIFICATION_ID }
            if (ourNotification != null) {
                Log.d(TAG, "Persistent notification verified as active")
                InAppLogger.log("Notifications", "Persistent notification verified as active")
            } else {
                Log.w(TAG, "Persistent notification not found in active notifications")
                InAppLogger.logError("Notifications", "Persistent notification not found in active notifications")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing persistent notification", e)
            InAppLogger.logError("Notifications", "Error showing persistent notification: ${e.message}")
        }
    }
    
    /**
     * Hide persistent notification
     */
    private fun hidePersistentNotification() {
        try {
            notificationManager.cancel(PERSISTENT_NOTIFICATION_ID)
            Log.d(TAG, "Persistent notification hidden")
            InAppLogger.log("Notifications", "Persistent notification hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding persistent notification", e)
            InAppLogger.logError("Notifications", "Error hiding persistent notification: ${e.message}")
        }
    }
    
    /**
     * Show notification while TTS is reading
     */
    private fun showReadingNotification(appName: String, content: String) {
        try {
            val isNotificationWhileReadingEnabled = sharedPreferences?.getBoolean(KEY_NOTIFICATION_WHILE_READING, false) ?: false
            if (!isNotificationWhileReadingEnabled) {
                Log.d(TAG, "Reading notification disabled in settings")
                return
            }
            
            // Check notification permission for Android 13+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted - cannot show reading notification")
                    InAppLogger.logError("Notifications", "POST_NOTIFICATIONS permission not granted")
                    return
                }
            }
            
            // Create notification channel for Android O+
            createNotificationChannel()
            
            // Create intent for opening SpeakThat
            val openAppIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Create intent for stopping TTS
            val stopTtsIntent = Intent(this, NotificationReaderService::class.java).apply {
                action = "STOP_TTS"
            }
            val stopTtsPendingIntent = PendingIntent.getService(
                this, 0, stopTtsIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Build notification
            val notification = NotificationCompat.Builder(this, "SpeakThat_Channel")
                .setContentTitle("SpeakThat Reading")
                .setContentText("$appName: $content")
                .setSmallIcon(R.drawable.speakthaticon)
                .setOngoing(false) // Temporary notification
                .setSilent(true) // Silent notification
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openAppPendingIntent)
                .addAction(
                    R.drawable.speakthaticon,
                    "Shut Up!",
                    stopTtsPendingIntent
                )
                .addAction(
                    R.drawable.speakthaticon,
                    "Open SpeakThat!",
                    openAppPendingIntent
                )
                .setAutoCancel(true) // Auto-dismiss when tapped
                .build()
            
            // Show notification
            notificationManager.notify(READING_NOTIFICATION_ID, notification)
            Log.d(TAG, "Reading notification shown for $appName with ID: $READING_NOTIFICATION_ID")
            InAppLogger.log("Notifications", "Reading notification shown for $appName with ID: $READING_NOTIFICATION_ID")
            
            // Verify notification was actually posted
            val activeNotifications = notificationManager.activeNotifications
            val ourNotification = activeNotifications.find { it.id == READING_NOTIFICATION_ID }
            if (ourNotification != null) {
                Log.d(TAG, "Reading notification verified as active")
                InAppLogger.log("Notifications", "Reading notification verified as active")
            } else {
                Log.w(TAG, "Reading notification not found in active notifications")
                InAppLogger.logError("Notifications", "Reading notification not found in active notifications")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing reading notification", e)
            InAppLogger.logError("Notifications", "Error showing reading notification: ${e.message}")
        }
    }
    
    /**
     * Hide reading notification
     */
    private fun hideReadingNotification() {
        try {
            notificationManager.cancel(READING_NOTIFICATION_ID)
            Log.d(TAG, "Reading notification hidden")
            InAppLogger.log("Notifications", "Reading notification hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding reading notification", e)
            InAppLogger.logError("Notifications", "Error hiding reading notification: ${e.message}")
        }
    }
    
    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    "SpeakThat_Channel",
                    "SpeakThat Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications from SpeakThat app"
                    setSound(null, null) // No sound
                    enableVibration(false) // No vibration
                    setShowBadge(false) // No badge
                }
                
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created with IMPORTANCE_DEFAULT")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating notification channel", e)
            }
        }
    }
    
    /**
     * Handle notification actions
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP_TTS" -> {
                Log.d(TAG, "Stop TTS action received")
                InAppLogger.log("Notifications", "Stop TTS action received from notification")
                stopTts()
                return START_STICKY
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }
    
    /**
     * Stop TTS and hide reading notification
     */
    private fun stopTts() {
        try {
            // Stop current TTS
            textToSpeech?.stop()
            isCurrentlySpeaking = false
            currentSpeechText = ""
            currentAppName = ""
            
            // Clear notification queue
            notificationQueue.clear()
            
            // Hide reading notification
            hideReadingNotification()
            
            Log.d(TAG, "TTS stopped via notification action")
            InAppLogger.log("Notifications", "TTS stopped via notification action")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
            InAppLogger.logError("Notifications", "Error stopping TTS: ${e.message}")
        }
    }

}