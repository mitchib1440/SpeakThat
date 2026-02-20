package com.micoyc.speakthat

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
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
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import androidx.core.app.NotificationCompat
import com.micoyc.speakthat.VoiceSettingsActivity
import com.micoyc.speakthat.settings.BehaviorSettingsActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashMap
import java.util.HashSet
import java.util.Locale
import kotlin.collections.ArrayList
import com.micoyc.speakthat.rules.RuleManager
import com.micoyc.speakthat.AccessibilityUtils

class NotificationReaderService : NotificationListenerService(), TextToSpeech.OnInitListener, SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {
    
    private var sharedPreferences: SharedPreferences? = null
    private var voiceSettingsPrefs: SharedPreferences? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isCurrentlySpeaking = false
    private var currentSpeechText = ""
    private var currentAppName = ""
    private var currentOriginalAppName = "" // Store original app name for statistics (before privacy modification)
    private var currentTtsText = ""
    private var shouldShowEngineFailureWarning = false
    
    // Cached system services for performance
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val cachedPackageManager by lazy { packageManager }
    
    // Filter settings (loaded once and cached)
    private var appListMode = "none"
    private var appList: Set<String> = emptySet()
    private var privateApps: Set<String> = emptySet()
    private var wordListMode = "blacklist" // Default to blacklist for backward compatibility
    private var blockedWords: Set<String> = emptySet()
    private var privateWords: Set<String> = emptySet()
    private var wordReplacements: Map<String, String> = emptyMap()
    private var urlHandlingMode = DEFAULT_URL_HANDLING_MODE
    private var urlReplacementText = DEFAULT_URL_REPLACEMENT_TEXT
    private var tidySpeechRemoveEmojisEnabled = false
    private var contentCapMode = DEFAULT_CONTENT_CAP_MODE
    private var contentCapWordCount = DEFAULT_CONTENT_CAP_WORD_COUNT
    private var contentCapSentenceCount = DEFAULT_CONTENT_CAP_SENTENCE_COUNT
    private var contentCapTimeLimit = DEFAULT_CONTENT_CAP_TIME_LIMIT
    private var priorityApps: Set<String> = emptySet()
    private var notificationBehavior = "interrupt"
    private var mediaBehavior = "ignore"
    private var duckingVolume = 30
    private var duckingFallbackStrategy = "manual"
    private var delayBeforeReadout = 0
    private var isPersistentFilteringEnabled = true
    private var legacyDuckingEnabled = false
    
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
    private var waveHoldDurationMs = 150L
    private var isPocketModeEnabled = false
    private var wasSensorCoveredAtStart = false
    private var isSensorCurrentlyCovered = false
    private var hasSensorBeenUncovered = false
    private var hasCapturedStartProximity = false
    private var speechStartTimestamp = 0L
    private var lastProximityValue = Float.NaN
    private var lastProximityTimestamp = 0L
    private var lastProximityIsNear = false
    private var lastFarTimestamp = 0L
    private var nearStableStartTimestamp = 0L
    private var waveEventCount = 0
    private var lastWaveDebugLogTime = 0L
    private var waveNoEventRunnable: Runnable? = null
    private var pendingWaveTriggerRunnable: Runnable? = null
    
    // Media behavior settings
    private var originalMusicVolume = -1 // Store original volume for restoration
    private var originalAudioMode = -1 // Store original audio mode for restoration
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private val pausedMediaSessions = mutableListOf<MediaController>() // Track paused sessions when legacy ducking is enabled
    
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
    private var contentCapTimerRunnable: Runnable? = null
    private var speechSafetyTimeoutRunnable: Runnable? = null
    
    // Rule system
    private lateinit var ruleManager: RuleManager
    
    // Speech template settings
    private var speechTemplate = "{app} notified you: {content}"
    private var speechTemplateKey: String = SpeechTemplateConstants.DEFAULT_TEMPLATE_KEY
    
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
    
    // Dismissal memory tracking - prevent re-reading dismissed notifications
    private val dismissedNotificationKeys = HashMap<String, Long>() // contentHash -> dismissal timestamp
    private var dismissalMemoryCleanupHandler: android.os.Handler? = null
    private var dismissalMemoryCleanupRunnable: Runnable? = null
    private val DISMISSAL_MEMORY_CLEANUP_INTERVAL_MS = 300000L // 5 minutes
    private val MAX_DISMISSAL_MEMORY_ENTRIES = 1000 // Prevent memory bloat
    
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
    
    // Throttling for repetitive logs
    private var lastTtsVolumeLogTime: Long = 0L
    private val TTS_VOLUME_LOG_THROTTLE_MS = 10000L // Only log TTS volume maintenance every 10 seconds
    
    // Listener reliability components
    @Volatile
    private var lastListenerEventTimestamp: Long = 0L
    private var listenerWatchdog: SpeakThatWatchdog? = null
    private var listenerRebindHandler: android.os.Handler? = null
    private var listenerRebindRunnable: Runnable? = null
    private val watchdogCallback = object : SpeakThatWatchdog.Callback {
        override fun isWatchdogAllowed(): Boolean {
            val masterEnabled = MainActivity.isMasterSwitchEnabled(this@NotificationReaderService)
            val permissionGranted = NotificationListenerRecovery.isNotificationAccessGranted(this@NotificationReaderService)
            val shouldMonitor = masterEnabled && permissionGranted
            if (!shouldMonitor) {
                Log.d(TAG, "Watchdog disabled (master=$masterEnabled, permission=$permissionGranted)")
            }
            return shouldMonitor
        }

        override fun onWatchdogStale(idleMs: Long): Boolean {
            return handleWatchdogStale(idleMs)
        }
    }
    

    
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
        private const val KEY_WAVE_HOLD_DURATION_MS = "wave_hold_duration_ms"
        private const val KEY_MASTER_SWITCH_ENABLED = "master_switch_enabled"
        
        // Notification IDs
        private const val FOREGROUND_SERVICE_ID = 1003
        
        // Deduplication settings
        private const val DEDUPLICATION_WINDOW_MS = 30000L // 30 seconds window for deduplication (increased to handle notification updates)
        
        // Dismissal memory settings
        private const val KEY_DISMISSAL_MEMORY_ENABLED = "dismissal_memory_enabled"
        private const val KEY_DISMISSAL_MEMORY_TIMEOUT = "dismissal_memory_timeout"
        private const val DEFAULT_DISMISSAL_MEMORY_ENABLED = true
        private const val DEFAULT_DISMISSAL_MEMORY_TIMEOUT_MINUTES = 15
        private const val LISTENER_HEALTH_THRESHOLD_MS = 5 * 60 * 1000L
        private const val WATCHDOG_INTERVAL_MS = 45_000L
        private const val WATCHDOG_STALE_THRESHOLD_MS = 3 * 60 * 1000L
        private const val WATCHDOG_REBIND_COOLDOWN_MS = 2 * 60 * 1000L
        private const val LISTENER_REBIND_DELAY_MS = 7_500L
        private const val LEGACY_COMPONENT_REENABLE_DELAY_MS = 2_000L
        private const val DEFAULT_TTS_USAGE_INDEX = 4
        private const val SAFETY_TIMEOUT_MIN_MS = 15_000L
        private const val SAFETY_TIMEOUT_MAX_MS = 180_000L
        private const val SAFETY_TIMEOUT_BUFFER_MS = 8_000L
        
        // Filter system keys
        private const val KEY_APP_LIST_MODE = "app_list_mode"
        private const val KEY_APP_LIST = "app_list"
        private const val KEY_APP_PRIVATE_FLAGS = "app_private_flags"
        private const val KEY_WORD_LIST_MODE = "word_list_mode"
        private const val KEY_WORD_BLACKLIST = "word_blacklist"
        private const val KEY_WORD_BLACKLIST_PRIVATE = "word_blacklist_private"
        private const val KEY_WORD_REPLACEMENTS = "word_replacements"
        
                // Behavior settings
        private const val KEY_NOTIFICATION_BEHAVIOR = "notification_behavior"
        private const val KEY_PRIORITY_APPS = "priority_apps"
        
        // Media behavior settings
        private const val KEY_MEDIA_BEHAVIOR = "media_behavior"
        private const val KEY_DUCKING_VOLUME = "ducking_volume"
        private const val KEY_DUCKING_FALLBACK_STRATEGY = "ducking_fallback_strategy"
        
        // Delay settings
        private const val KEY_DELAY_BEFORE_READOUT = "delay_before_readout"
        
        // Speech template settings
        private const val KEY_SPEECH_TEMPLATE = "speech_template"
        private const val KEY_SPEECH_TEMPLATE_KEY = "speech_template_key"
        
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
        
        // URL handling constants
        private const val KEY_URL_HANDLING_MODE = "url_handling_mode"
        private const val KEY_URL_REPLACEMENT_TEXT = "url_replacement_text"
        private const val KEY_TIDY_SPEECH_REMOVE_EMOJIS = "tidy_speech_remove_emojis"
        private const val DEFAULT_URL_HANDLING_MODE = "domain_only"
        private const val DEFAULT_URL_REPLACEMENT_TEXT = ""
        
        // Content Cap settings
        private const val KEY_CONTENT_CAP_MODE = "content_cap_mode"
        private const val KEY_CONTENT_CAP_WORD_COUNT = "content_cap_word_count"
        private const val KEY_CONTENT_CAP_SENTENCE_COUNT = "content_cap_sentence_count"
        private const val KEY_CONTENT_CAP_TIME_LIMIT = "content_cap_time_limit"
        private const val DEFAULT_CONTENT_CAP_MODE = "disabled"
        private const val DEFAULT_CONTENT_CAP_WORD_COUNT = 6
        private const val DEFAULT_CONTENT_CAP_SENTENCE_COUNT = 1
        private const val DEFAULT_CONTENT_CAP_TIME_LIMIT = 10

        // Wave detection reliability settings
        private const val DEFAULT_WAVE_HOLD_DURATION_MS = 150L
        private const val MIN_WAVE_HOLD_DURATION_MS = 0L
        private const val MAX_WAVE_HOLD_DURATION_MS = 500L
        private const val WAVE_NEAR_FAR_NEAR_WINDOW_MS = 1500L
        private const val PROXIMITY_START_SNAPSHOT_MAX_AGE_MS = 2000L
        private const val WAVE_STARTUP_GRACE_MS = 400L
        
        // TTS Recovery settings - Enhanced for Android 15 compatibility
        private const val MAX_TTS_RECOVERY_ATTEMPTS = 5 // Increased for Android 15
        private const val TTS_RECOVERY_DELAY_MS = 2000L // 2 seconds between attempts
        private const val TTS_RECOVERY_DELAY_ANDROID15_MS = 5000L // 5 seconds for Android 15
        private const val TTS_INIT_TIMEOUT_MS = 10000L // 10 seconds timeout
        private const val TTS_INIT_TIMEOUT_ANDROID15_MS = 15000L // 15 seconds for Android 15
        
        @JvmStatic
        fun getRecentNotifications(): List<NotificationData> {
            return notificationHistory.toList()
        }
        
        /**
         * Check if media behavior fallback is disabled in development settings
         */
        fun isMediaFallbackDisabled(context: Context): Boolean {
            return try {
                val prefs = context.getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
                prefs.getBoolean("disable_media_fallback", false)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking media fallback setting", e)
                false // Default to enabled if error
            }
        }
        
        // Pre-compiled regex for URL detection (includes http/https/www URLs, bare domains with TLD, IP addresses, and IPv6)
        private val URL_PATTERN = Regex("""(?i)(?:https?://[^\s]+|www\.[^\s]+|(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.(?:[a-zA-Z]{2,}|[0-9]+)|\[[0-9a-fA-F:]+\])(?::[0-9]+)?(?:/[^\s]*)?)""")
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
        val sbn: StatusBarNotification? = null,
        val originalAppName: String? = null, // Original app name for statistics (before privacy modification)
        val speechTemplateOverride: SpeechTemplateOverride? = null
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
            legacyDuckingEnabled = sharedPreferences?.getBoolean("enable_legacy_ducking", false) ?: false
            Log.d(
                TAG,
                "Legacy ducking ${if (legacyDuckingEnabled) "enabled" else "disabled"} at startup"
            )
            
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
            Log.d(TAG, "Handlers initialized - delayHandler: ${delayHandler != null}, sensorTimeoutHandler: ${sensorTimeoutHandler != null}")
            
            // Start periodic TTS health check
            Log.d(TAG, "Starting periodic TTS health check...")
            startPeriodicHealthCheck()
            Log.d(TAG, "Periodic TTS health check started")
            
            // Start dismissal memory cleanup
            startDismissalMemoryCleanup()

            // Initialize listener watchdog to keep NotificationListenerService healthy
            ensureListenerReliabilityComponents()
            recordListenerEvent("service_create")
            startListenerWatchdog("service_create")
            
            Log.d(TAG, "NotificationReaderService initialization completed successfully")
            InAppLogger.log("Service", "Service initialization completed successfully")
            
            // Show persistent notification if enabled and master switch is on
            checkAndShowPersistentNotification()
            
            // Register broadcast receiver for accessibility service
            registerAccessibilityBroadcastReceiver()
            
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
            
            // Unregister accessibility broadcast receiver
            unregisterAccessibilityBroadcastReceiver()
            
            // Clear deduplication cache
            recentNotificationKeys.clear()
            Log.d(TAG, "Cleared deduplication cache during cleanup")
            
            // Clear dismissal memory cache
            dismissedNotificationKeys.clear()
            Log.d(TAG, "Cleared dismissal memory cache during cleanup")
            
            // Stop dismissal memory cleanup
            stopDismissalMemoryCleanup()

            // Stop listener watchdog and pending rebind operations
            listenerRebindRunnable?.let { runnable ->
                listenerRebindHandler?.removeCallbacks(runnable)
            }
            listenerRebindRunnable = null
            listenerRebindHandler = null
            stopListenerWatchdog("service_destroy")
            listenerWatchdog = null
            
            // Hide all SpeakThat notifications
            PersistentIndicatorManager.requestStop(this)
            // hideReadingNotification()
            
            Log.d(TAG, "NotificationReaderService cleanup completed")
            InAppLogger.log("Service", "Service cleanup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during service cleanup", e)
            InAppLogger.logError("Service", "Error during service cleanup: " + e.message)
        }
    }
    
    /**
     * Register broadcast receiver for accessibility service communication
     * 
     * This method registers a broadcast receiver to listen for STOP_READING broadcasts
     * from the accessibility service. The receiver is registered with RECEIVER_NOT_EXPORTED
     * flag for security, ensuring it only receives broadcasts from within the same app.
     */
    private fun registerAccessibilityBroadcastReceiver() {
        try {
            val filter = android.content.IntentFilter("com.micoyc.speakthat.STOP_READING")
            // Use RECEIVER_NOT_EXPORTED since this is for internal app communication only
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(accessibilityBroadcastReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(accessibilityBroadcastReceiver, filter)
            }
            Log.d(TAG, "Accessibility broadcast receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering accessibility broadcast receiver", e)
        }
    }
    
    /**
     * Unregister broadcast receiver for accessibility service communication
     */
    private fun unregisterAccessibilityBroadcastReceiver() {
        try {
            unregisterReceiver(accessibilityBroadcastReceiver)
            Log.d(TAG, "Accessibility broadcast receiver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering accessibility broadcast receiver", e)
        }
    }
    
    /**
     * Broadcast receiver for accessibility service communication
     * 
     * This receiver listens for broadcasts from the SpeakThatAccessibilityService
     * to handle advanced control features like "Press to Stop" functionality.
     * 
     * The receiver is registered with RECEIVER_NOT_EXPORTED flag for security,
     * ensuring it only receives broadcasts from within the same app.
     */
    private val accessibilityBroadcastReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            Log.d(TAG, "Accessibility broadcast received - Action: ${intent?.action}")
            
            when (intent?.action) {
                "com.micoyc.speakthat.STOP_READING" -> {
                    Log.d(TAG, "Received STOP_READING broadcast from accessibility service")
                    stopSpeaking("accessibility service")
                }
                else -> {
                    Log.d(TAG, "Unknown accessibility broadcast action: ${intent?.action}")
                }
            }
        }
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListener connected")
        InAppLogger.log("Service", "NotificationListener connected")
        try {
            NotificationListenerRecovery.recordConnection(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record listener connection", e)
            InAppLogger.logError("ServiceRebind", "Failed to record connection: ${e.message}")
        }
        ensureListenerReliabilityComponents()
        recordListenerEvent("listener_connected")
        startListenerWatchdog("listener_connected")
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "NotificationListener disconnected - scheduling reliability rebind")
        InAppLogger.logWarning("Service", "NotificationListener disconnected - scheduling rebind")
        try {
            NotificationListenerRecovery.recordDisconnect(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record listener disconnect", e)
            InAppLogger.logError("ServiceRebind", "Failed to record disconnect: ${e.message}")
        }

        listenerWatchdog?.recordExternalIntervention("listener_disconnected")

        try {
            scheduleListenerRebind("listener_disconnected", LISTENER_REBIND_DELAY_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule listener rebind task", e)
            InAppLogger.logError("ServiceRebind", "Failed to schedule rebind task: ${e.message}")
        }
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        try {
            try {
                val notification = sbn.notification
                val packageName = sbn.packageName
                
                // Check for SelfTest notification - bypass self-package filter if it's a test
                val isSelfTest = notification.extras.getBoolean(SelfTestHelper.EXTRA_IS_SELFTEST, false)
                if (isSelfTest) {
                    Log.d(TAG, "SelfTest notification detected - bypassing self-package filter")
                    InAppLogger.log("SelfTest", "SelfTest notification received")
                    // Process as test notification - continue with normal flow
                } else {
                    // Skip our own notifications (normal behavior)
                    if (packageName == this.packageName) {
                        // Track filter reason
                        try {
                            StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_SELF_PACKAGE)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error tracking self-package filter", e)
                        }
                        return
                    }
                }
                
                recordListenerEvent("notification_posted:$packageName")
                
                // Skip group summary notifications - only read individual notifications
                // Group summaries are "container" notifications that show "X notifications" 
                // but don't contain the actual content. Reading them causes duplicates.
                // This is especially important for Android 16's automatic notification grouping.
                if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
                    Log.d(TAG, "Skipping group summary notification from $packageName")
                    InAppLogger.logFilter("Skipped group summary notification from $packageName")
                    // Track filter reason
                    try {
                        StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_GROUP_SUMMARY)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error tracking group summary filter", e)
                    }
                    return
                }
                
                // Check master switch first - if disabled, don't process any notifications
                if (!MainActivity.isMasterSwitchEnabled(this)) {
                    Log.d(TAG, "Master switch disabled - ignoring notification from $packageName")
                    InAppLogger.log("MasterSwitch", "Notification ignored due to master switch being disabled")
                    // Track filter reason
                    try {
                        StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_MASTER_SWITCH)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error tracking master switch filter", e)
                    }
                    return
                }

                // Check Do Not Disturb mode - if enabled and honouring DND, don't process notifications
                if (BehaviorSettingsActivity.shouldHonourDoNotDisturb(this)) {
                    Log.d(TAG, "Do Not Disturb mode enabled - ignoring notification from $packageName")
                    InAppLogger.log("DoNotDisturb", "Notification ignored due to Do Not Disturb mode")
                    // Track filter reason
                    try {
                        StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_DND)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error tracking DND filter", e)
                    }
                    return
                }
                
                // Check audio mode - block according to per-mode preferences
                val audioBlockReason = BehaviorSettingsActivity.getAudioModeBlockReason(this)
                if (audioBlockReason != null) {
                    Log.d(TAG, "Audio mode check failed - device is in $audioBlockReason mode, ignoring notification from $packageName")
                    InAppLogger.log("AudioMode", "Notification ignored due to audio mode: $audioBlockReason")
                    try {
                        StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_AUDIO_MODE)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error tracking audio mode filter", e)
                    }
                    return
                } else {
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
                    // Track filter reason
                    try {
                        StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_PHONE_CALLS)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error tracking phone calls filter", e)
                    }
                    return
                } else {
                    // Log when phone call check passes (for debugging)
                    Log.d(TAG, "Phone call check passed - device is not on a call, proceeding with notification from $packageName")
                    InAppLogger.log("PhoneCalls", "Phone call check passed: no active call")
                }
                
                // Track notification received (after passing basic checks)
                try {
                    StatisticsManager.getInstance(this).incrementReceived()
                } catch (e: Exception) {
                    Log.e(TAG, "Error tracking notification received", e)
                }
                
                // Get app name
                val appName = getAppName(packageName)
                
                // Extract notification text
                val notificationText = extractNotificationText(notification)
                
                // Log notification details for debugging
                Log.d(TAG, "Processing notification - Package: $packageName, ID: ${sbn.id}, Text: '${notificationText.take(100)}...'")
                
                // Additional logging for Gmail notifications to help debug the issue
                if (packageName == "com.google.android.gm") {
                    Log.d(TAG, "Gmail notification detected - ID: ${sbn.id}, Post time: ${sbn.postTime}, Update time: ${System.currentTimeMillis()}")
                    InAppLogger.logSystemEvent("Gmail notification", "ID: ${sbn.id}, Content: '${notificationText.take(50)}...'")
                }
                
                // Check for duplicate notifications (only if deduplication is enabled)
                // Skip deduplication for SelfTest notifications
                val isDeduplicationEnabled = sharedPreferences?.getBoolean("notification_deduplication", true) ?: true
                if (isSelfTest) {
                    Log.d(TAG, "SelfTest notification - bypassing deduplication")
                    InAppLogger.log("SelfTest", "Deduplication bypassed for test notification")
                }
                if (isDeduplicationEnabled && !isSelfTest) {
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
                        // Track filter reason
                        try {
                            StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_DEDUPLICATION)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error tracking deduplication filter", e)
                        }
                        return
                    }
                    
                    // Mark this notification as processed
                    recentNotificationKeys[notificationKey] = currentTime
                    Log.d(TAG, "Deduplication: Added notification key for $appName (key: $notificationKey)")
                    Log.d(TAG, "Deduplication map size: ${recentNotificationKeys.size}")
                    
                    // Enhanced content-based deduplication for all apps (not just problematic ones)
                    // This helps catch notification updates that might have slightly different content
                    val contentKey = generateContentKey(packageName, notificationText)
                    val lastContentTime = recentNotificationKeys[contentKey]
                    if (lastContentTime != null && currentTime - lastContentTime < DEDUPLICATION_WINDOW_MS) {
                        val timeSinceLastContent = currentTime - lastContentTime
                        Log.d(TAG, "Content-based duplicate detected from $appName - skipping (processed ${timeSinceLastContent}ms ago)")
                        InAppLogger.logFilter("Content-based duplicate from $appName - skipping (processed ${timeSinceLastContent}ms ago)")
                        // Track filter reason
                        try {
                            StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_DEDUPLICATION)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error tracking deduplication filter", e)
                        }
                        return
                    }
                    
                    // Special handling for Gmail: use a more lenient deduplication approach
                    // Gmail updates notifications rather than creating new ones, so we need to be more careful
                    if (packageName == "com.google.android.gm") {
                        // For Gmail, also check if we recently processed a notification with the same ID
                        // This helps prevent re-reading the same notification when it gets updated
                        val gmailIdKey = "gmail_id_${sbn.id}"
                        val lastGmailIdTime = recentNotificationKeys[gmailIdKey]
                        if (lastGmailIdTime != null && currentTime - lastGmailIdTime < DEDUPLICATION_WINDOW_MS) {
                            val timeSinceLastGmailId = currentTime - lastGmailIdTime
                            Log.d(TAG, "Gmail notification ID recently processed - skipping (processed ${timeSinceLastGmailId}ms ago)")
                            InAppLogger.logFilter("Gmail notification ID recently processed - skipping (processed ${timeSinceLastGmailId}ms ago)")
                            // Track filter reason
                            try {
                                StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_DEDUPLICATION)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error tracking deduplication filter", e)
                            }
                            return
                        }
                        recentNotificationKeys[gmailIdKey] = currentTime
                    }
                    
                    // Only add to recent keys if we're actually going to process this notification
                    // This prevents race conditions in batch processing
                    recentNotificationKeys[contentKey] = currentTime
                    
                    // Additional app-specific deduplication for known problematic apps
                    if (isProblematicApp(packageName)) {
                        val appSpecificKey = "app_${packageName}_${sbn.id}"
                        val lastAppSpecificTime = recentNotificationKeys[appSpecificKey]
                        if (lastAppSpecificTime != null && currentTime - lastAppSpecificTime < DEDUPLICATION_WINDOW_MS) {
                            val timeSinceLastAppSpecific = currentTime - lastAppSpecificTime
                            Log.d(TAG, "App-specific duplicate detected from $appName - skipping (processed ${timeSinceLastAppSpecific}ms ago)")
                            InAppLogger.logFilter("App-specific duplicate from $appName - skipping (processed ${timeSinceLastAppSpecific}ms ago)")
                            // Track filter reason
                            try {
                                StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_DEDUPLICATION)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error tracking deduplication filter", e)
                            }
                            return
                        }
                        recentNotificationKeys[appSpecificKey] = currentTime
                    }
                } else {
                    Log.d(TAG, "Deduplication is disabled - processing all notifications")
                }
                
                // Check dismissal memory - prevent re-reading recently dismissed notifications
                val isDismissalMemoryEnabled = sharedPreferences?.getBoolean(KEY_DISMISSAL_MEMORY_ENABLED, DEFAULT_DISMISSAL_MEMORY_ENABLED) ?: DEFAULT_DISMISSAL_MEMORY_ENABLED
                if (isDismissalMemoryEnabled) {
                    try {
                        val currentTime = System.currentTimeMillis()
                        val dismissalContentHash = generateDismissalContentHash(packageName, notificationText)
                        val dismissalTimeoutMinutes = sharedPreferences?.getInt(KEY_DISMISSAL_MEMORY_TIMEOUT, DEFAULT_DISMISSAL_MEMORY_TIMEOUT_MINUTES) ?: DEFAULT_DISMISSAL_MEMORY_TIMEOUT_MINUTES
                        val dismissalTimeoutMs = dismissalTimeoutMinutes * 60 * 1000L
                        
                        // Check if this content was recently dismissed
                        val dismissalTime = dismissedNotificationKeys[dismissalContentHash]
                        if (dismissalTime != null && currentTime - dismissalTime < dismissalTimeoutMs) {
                            val timeSinceDismissal = currentTime - dismissalTime
                            val timeSinceDismissalMinutes = timeSinceDismissal / (60 * 1000)
                            Log.d(TAG, "Dismissed notification detected from $appName - skipping (dismissed ${timeSinceDismissalMinutes} minutes ago)")
                            Log.d(TAG, "Dismissal memory: Content hash: $dismissalContentHash, Timeout: ${dismissalTimeoutMinutes} minutes")
                            InAppLogger.logFilter("Dismissed notification from $appName - skipping (dismissed ${timeSinceDismissalMinutes} minutes ago)")
                            // Track filter reason
                            try {
                                StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_DISMISSAL_MEMORY)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error tracking dismissal memory filter", e)
                            }
                            return
                        }
                    } catch (e: Exception) {
                        // Graceful degradation: if dismissal memory fails, continue processing
                        Log.e(TAG, "Error in dismissal memory check - continuing with notification processing", e)
                        InAppLogger.logError("Service", "Dismissal memory check failed - continuing: " + e.message)
                    }
                } else {
                    Log.d(TAG, "Dismissal memory disabled - processing all notifications")
                }
                
                if (notificationText.isNotEmpty()) {
                    // Log the notification being processed for debugging
                    Log.d(TAG, "Processing notification from $appName: '$notificationText' (ID: ${sbn?.id}, time: ${System.currentTimeMillis()})")
                    
                    // Apply filtering first to determine final privacy status
                    val filterResult = applyFilters(packageName, appName, notificationText, sbn, isSelfTest)
                    
                    // Check if the final result is private (either app-level or word-level)
                    val isAppPrivate = privateApps.contains(packageName)
                    val isWordPrivate = filterResult.processedText.contains("private notification") || filterResult.processedText.contains("You received a private notification")
                    val isPrivateContent = isAppPrivate || isWordPrivate
                    
                    // Always log notification content (including private notifications for debugging)
                    if (isPrivateContent) {
                        Log.d(TAG, "New notification from $appName: $notificationText")
                        InAppLogger.logNotification("Processing private notification from $appName: $notificationText")
                    } else {
                        Log.d(TAG, "New notification from $appName: $notificationText")
                        InAppLogger.logNotification("Processing notification from $appName: $notificationText")
                    }
                    
                    if (filterResult.shouldSpeak) {
                        // Log for SelfTest if this is a test notification
                        if (isSelfTest) {
                            InAppLogger.log("SelfTest", "SelfTest notification passed filtering")
                            Log.d(TAG, "SelfTest notification passed filtering - will be spoken")
                        }
                        
                        // Determine final app name (private apps become "An app")
                        val finalAppName = if (isAppPrivate) "An app" else appName
                        
                        // Log the notification that will be spoken for debugging
                        Log.d(TAG, "Will speak notification from $finalAppName: '${filterResult.processedText.take(100)}...' (ID: ${sbn?.id})")
                        
                        // Add to history
                        addToHistory(finalAppName, packageName, filterResult.processedText)
                        
                        // Handle notification based on behavior mode (pass conditional delay info)
                        handleNotificationBehavior(
                            packageName,
                            finalAppName,
                            filterResult.processedText,
                            filterResult.conditionalDelaySeconds,
                            sbn,
                            filterResult.speechTemplateOverride
                        )
                    } else {
                        // Always log the full blocking reason with details
                        val reasonType = extractBlockingReasonType(filterResult.reason)
                        Log.d(TAG, "Notification blocked from $appName: Blocked: $reasonType (Details: ${filterResult.reason})")
                        InAppLogger.logFilter("Blocked notification from $appName: Blocked: $reasonType (Details: ${filterResult.reason})")
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
        
        try {
            // Skip our own notifications
            if (sbn.packageName == this.packageName) {
                return
            }
            
            // Check if dismissal memory is enabled
            val isDismissalMemoryEnabled = sharedPreferences?.getBoolean(KEY_DISMISSAL_MEMORY_ENABLED, DEFAULT_DISMISSAL_MEMORY_ENABLED) ?: DEFAULT_DISMISSAL_MEMORY_ENABLED
            if (!isDismissalMemoryEnabled) {
                Log.d(TAG, "Dismissal memory disabled - not tracking dismissed notification from ${sbn.packageName}")
                return
            }
            
            // Extract notification content for tracking
            val notificationText = extractNotificationText(sbn.notification)
            if (notificationText.isEmpty()) {
                Log.d(TAG, "Dismissed notification has empty content - not tracking")
                return
            }
            
            // Generate content hash for dismissal tracking
            val contentHash = generateDismissalContentHash(sbn.packageName, notificationText)
            val currentTime = System.currentTimeMillis()
            
            // Add to dismissed notifications map with memory management
            dismissedNotificationKeys[contentHash] = currentTime
            
            // Memory management: if we exceed the limit, remove oldest entries
            if (dismissedNotificationKeys.size > MAX_DISMISSAL_MEMORY_ENTRIES) {
                val entriesToRemove = dismissedNotificationKeys.size - MAX_DISMISSAL_MEMORY_ENTRIES
                val oldestEntries = dismissedNotificationKeys.entries.sortedBy { it.value }.take(entriesToRemove)
                oldestEntries.forEach { (key, _) ->
                    dismissedNotificationKeys.remove(key)
                }
                Log.d(TAG, "Dismissal memory limit reached - removed $entriesToRemove oldest entries")
                InAppLogger.log("Service", "Dismissal memory cleanup: removed $entriesToRemove oldest entries due to limit")
            }
            
            // Log the dismissal for debugging
            val appName = getAppName(sbn.packageName)
            Log.d(TAG, "Notification dismissed from $appName - tracking for dismissal memory (hash: $contentHash)")
            InAppLogger.logFilter("Notification dismissed from $appName - tracking for dismissal memory")
            
            // Log dismissal memory stats
            Log.d(TAG, "Dismissal memory stats - Total tracked: ${dismissedNotificationKeys.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking dismissed notification", e)
            InAppLogger.logError("Service", "Error tracking dismissed notification: ${e.message}")
        }
    }
    
    /**
     * Get a human-readable error message for TTS initialization status codes
     */
    private fun getTtsErrorMessage(status: Int): String {
        return when (status) {
            TextToSpeech.ERROR -> "TTS engine error"
            TextToSpeech.ERROR_NOT_INSTALLED_YET -> "TTS engine not installed"
            TextToSpeech.ERROR_OUTPUT -> "TTS output error"
            TextToSpeech.ERROR_SERVICE -> "TTS service error"
            TextToSpeech.ERROR_SYNTHESIS -> "TTS synthesis error"
            TextToSpeech.ERROR_INVALID_REQUEST -> "Invalid TTS request"
            TextToSpeech.ERROR_NETWORK -> "TTS network error"
            TextToSpeech.ERROR_NETWORK_TIMEOUT -> "TTS network timeout"
            -1 -> if (isAndroid15OrHigher()) {
                "TTS service not accessible (Android 15 restriction)"
            } else {
                "TTS service not accessible"
            }
            else -> "Unknown TTS error (status: $status)"
        }
    }
    
    /**
     * Check if the device is running Android 15 (API 35) or higher
     */
    private fun isAndroid15OrHigher(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= 35
    }
    
    /**
     * Get the appropriate timeout for TTS initialization based on Android version
     */
    private fun getTtsInitTimeout(): Long {
        return if (isAndroid15OrHigher()) {
            TTS_INIT_TIMEOUT_ANDROID15_MS
        } else {
            TTS_INIT_TIMEOUT_MS
        }
    }
    
    /**
     * Get the appropriate recovery delay based on Android version
     */
    private fun getTtsRecoveryDelay(): Long {
        return if (isAndroid15OrHigher()) {
            TTS_RECOVERY_DELAY_ANDROID15_MS
        } else {
            TTS_RECOVERY_DELAY_MS
        }
    }
    
    private fun initializeTextToSpeech() {
        try {
            val timeout = getTtsInitTimeout()
            val isAndroid15 = isAndroid15OrHigher()
            
            Log.d(TAG, "Starting TTS initialization... (Android ${android.os.Build.VERSION.SDK_INT}, timeout: ${timeout}ms)")
            InAppLogger.log("Service", "Starting TTS initialization (Android ${android.os.Build.VERSION.SDK_INT}, timeout: ${timeout}ms)")
            
            // Check if TTS service is available before attempting initialization
            if (isAndroid15) {
                try {
                    val ttsIntent = android.content.Intent(android.speech.tts.TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
                    val resolveInfo = packageManager.resolveService(ttsIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                    if (resolveInfo == null) {
                        Log.e(TAG, "No TTS service available on Android 15")
                        InAppLogger.logError("Service", "No TTS service available on Android 15")
                        attemptTtsRecovery("No TTS service available")
                        return
                    }
                    Log.d(TAG, "TTS service found: ${resolveInfo.serviceInfo.packageName}")
                    InAppLogger.log("Service", "TTS service found: ${resolveInfo.serviceInfo.packageName}")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not check TTS service availability", e)
                    InAppLogger.log("Service", "Could not check TTS service availability: ${e.message}")
                }
            }
            
            // Get selected TTS engine from preferences
            val voiceSettingsPrefs = getSharedPreferences("VoiceSettings", android.content.Context.MODE_PRIVATE)
            val selectedEngine = voiceSettingsPrefs.getString("tts_engine_package", "")
            
            if (selectedEngine.isNullOrEmpty()) {
                // Use system default engine
                textToSpeech = TextToSpeech(this, this)
                Log.d(TAG, "Using system default TTS engine")
                InAppLogger.log("Service", "Using system default TTS engine")
            } else {
                // Use selected custom engine
                textToSpeech = TextToSpeech(this, this, selectedEngine)
                Log.d(TAG, "Using custom TTS engine: $selectedEngine")
                InAppLogger.log("Service", "Using custom TTS engine: $selectedEngine")
            }
            
            // Add a timeout to detect if TTS initialization hangs
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!isTtsInitialized) {
                    Log.e(TAG, "TTS initialization timed out after ${timeout}ms")
                    InAppLogger.logError("Service", "TTS initialization timed out after ${timeout}ms - attempting recovery")
                    
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
                        attemptTtsRecovery("Reinitialization failed: ${e.message}")
                    }
                }
            }, timeout)
            
            Log.d(TAG, "TTS initialization started")
            InAppLogger.log("Service", "TTS initialization started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in initializeTextToSpeech", e)
            InAppLogger.logError("Service", "TTS initialization error: " + e.message)
            attemptTtsRecovery("Initialization exception: ${e.message}")
        }
    }
    
    /**
     * Comprehensive TTS recovery system that can detect and fix various TTS issues
     */
    private fun attemptTtsRecovery(reason: String) {
        val currentTime = System.currentTimeMillis()
        val isAndroid15 = isAndroid15OrHigher()
        
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
        Log.w(TAG, "Attempting TTS recovery #$ttsRecoveryAttempts (reason: $reason, Android ${android.os.Build.VERSION.SDK_INT})")
        InAppLogger.log("Service", "Attempting TTS recovery #$ttsRecoveryAttempts (reason: $reason, Android ${android.os.Build.VERSION.SDK_INT})")
        
        // Cancel any existing recovery attempt
        ttsRecoveryRunnable?.let { runnable ->
            ttsRecoveryHandler?.removeCallbacks(runnable)
        }
        
        // Calculate exponential backoff delay
        val baseDelay = getTtsRecoveryDelay()
        val exponentialDelay = baseDelay * (1 shl (ttsRecoveryAttempts - 1)) // 2^(attempt-1) * baseDelay
        val maxDelay = if (isAndroid15) 30000L else 15000L // Cap at 30s for Android 15, 15s for others
        val actualDelay = minOf(exponentialDelay, maxDelay)
        
        Log.d(TAG, "TTS recovery delay: ${actualDelay}ms (base: ${baseDelay}ms, exponential: ${exponentialDelay}ms)")
        InAppLogger.log("Service", "TTS recovery delay: ${actualDelay}ms")
        
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
                
                // 3. Wait longer for cleanup on Android 15
                val cleanupDelay = if (isAndroid15) 1000L else 500L
                Thread.sleep(cleanupDelay)
                
                // 4. Check TTS service availability on Android 15
                if (isAndroid15) {
                    try {
                        val ttsIntent = android.content.Intent(android.speech.tts.TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
                        val resolveInfo = packageManager.resolveService(ttsIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                        if (resolveInfo == null) {
                            Log.e(TAG, "No TTS service available during recovery attempt #$ttsRecoveryAttempts")
                            InAppLogger.logError("Service", "No TTS service available during recovery attempt #$ttsRecoveryAttempts")
                            
                            // Try again if we haven't exceeded max attempts
                            if (ttsRecoveryAttempts < MAX_TTS_RECOVERY_ATTEMPTS) {
                                attemptTtsRecovery("No TTS service available")
                            }
                            return@Runnable
                        }
                        Log.d(TAG, "TTS service found during recovery: ${resolveInfo.serviceInfo.packageName}")
                        InAppLogger.log("Service", "TTS service found during recovery: ${resolveInfo.serviceInfo.packageName}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not check TTS service availability during recovery", e)
                        InAppLogger.log("Service", "Could not check TTS service availability during recovery: ${e.message}")
                    }
                }
                
                // 5. Reinitialize TTS
                textToSpeech = TextToSpeech(this, this)
                
                // 6. Set up timeout for this recovery attempt
                val timeout = getTtsInitTimeout()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (!isTtsInitialized) {
                        Log.e(TAG, "TTS recovery attempt #$ttsRecoveryAttempts timed out after ${timeout}ms")
                        InAppLogger.logError("Service", "TTS recovery attempt #$ttsRecoveryAttempts timed out after ${timeout}ms")
                        
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
                }, timeout)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during TTS recovery attempt #$ttsRecoveryAttempts", e)
                InAppLogger.logError("Service", "Error during TTS recovery attempt #$ttsRecoveryAttempts: " + e.message)
                
                // Try again if we haven't exceeded max attempts
                if (ttsRecoveryAttempts < MAX_TTS_RECOVERY_ATTEMPTS) {
                    attemptTtsRecovery("Recovery error: ${e.message}")
                }
            }
        }
        
        ttsRecoveryHandler?.postDelayed(ttsRecoveryRunnable!!, actualDelay)
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
        
        // Language availability check is informational only
        // LANG_NOT_SUPPORTED means "this language isn't available" NOT "TTS is broken"
        // The engine will use its default language/voice and continue working
        try {
            val isAvailable = textToSpeech?.isLanguageAvailable(Locale.getDefault()) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (isAvailable == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Language ${Locale.getDefault()} not supported by TTS engine — engine will use defaults (non-fatal)")
                InAppLogger.log("Service", "Language ${Locale.getDefault()} not supported — using engine defaults")
            }
        } catch (e: Exception) {
            // Even if language check throws, TTS might still work - don't abort
            Log.w(TAG, "Error checking TTS language availability (non-fatal): ${e.message}", e)
            InAppLogger.log("Service", "TTS language check failed (non-fatal): ${e.message}")
        }
        
        return true  // TTS is healthy - proceed with speaking
    }

    private fun checkListenerHealth() {
        try {
            val status = NotificationListenerRecovery.getListenerStatus(this, LISTENER_HEALTH_THRESHOLD_MS)

            if (!status.permissionGranted) {
                Log.d(TAG, "Listener health check skipped - permission missing")
                return
            }

            if (!status.isDisconnected && !status.isStale) {
                return
            }

            val reason = if (status.isDisconnected) "health_monitor_disconnected" else "health_monitor_stale"
            val requested = NotificationListenerRecovery.requestRebind(this, reason, false)
            if (requested) {
                InAppLogger.log("ServiceHealth", "Listener health monitor requested rebind (reason=$reason)")
            } else {
                InAppLogger.logWarning("ServiceHealth", "Listener health monitor skipped rebind (reason=$reason)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during listener health check", e)
            InAppLogger.logError("ServiceHealth", "Listener health check failed: ${e.message}")
        }
    }

    private fun ensureListenerReliabilityComponents() {
        if (listenerRebindHandler == null) {
            listenerRebindHandler = android.os.Handler(android.os.Looper.getMainLooper())
        }
        if (listenerWatchdog == null) {
            listenerWatchdog = SpeakThatWatchdog(
                callback = watchdogCallback,
                lastActivityProvider = { lastListenerEventTimestamp },
                checkIntervalMs = WATCHDOG_INTERVAL_MS,
                staleThresholdMs = WATCHDOG_STALE_THRESHOLD_MS,
                rebindCooldownMs = WATCHDOG_REBIND_COOLDOWN_MS
            )
        }
    }

    private fun startListenerWatchdog(reason: String) {
        ensureListenerReliabilityComponents()
        listenerWatchdog?.start(reason)
    }

    private fun stopListenerWatchdog(reason: String) {
        listenerWatchdog?.stop(reason)
    }

    private fun recordListenerEvent(reason: String) {
        lastListenerEventTimestamp = System.currentTimeMillis()
        Log.v(TAG, "Listener heartbeat recorded ($reason)")
        try {
            NotificationListenerRecovery.recordHeartbeat(this, reason)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist listener heartbeat ($reason)", e)
            InAppLogger.logError("ServiceHeartbeat", "Failed to record heartbeat ($reason): ${e.message}")
        }
    }

    private fun handleWatchdogStale(idleMs: Long): Boolean {
        val reason = "watchdog_idle_${idleMs / 1000}s"
        Log.w(TAG, "Watchdog detected stale listener (idle=${idleMs}ms) - requesting rebind ($reason)")
        val requested = requestListenerRebind(reason, force = false)
        if (!requested) {
            Log.w(TAG, "Watchdog rebind request skipped or deferred ($reason)")
        }
        return requested
    }

    private fun scheduleListenerRebind(reason: String, delayMs: Long) {
        ensureListenerReliabilityComponents()

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O_MR1) {
            Log.d(TAG, "Legacy device detected - performing immediate component toggle for $reason")
            requestListenerRebind(reason, force = true)
            return
        }

        listenerRebindRunnable?.let { runnable ->
            listenerRebindHandler?.removeCallbacks(runnable)
        }

        val runnable = Runnable {
            requestListenerRebind(reason, force = false)
        }
        listenerRebindRunnable = runnable
        listenerRebindHandler?.postDelayed(runnable, delayMs)
        listenerWatchdog?.recordExternalIntervention("scheduled_$reason")
        Log.d(TAG, "Scheduled listener rebind in ${delayMs}ms (reason=$reason)")
        InAppLogger.log("ServiceRebind", "Scheduled listener rebind in ${delayMs}ms (reason=$reason)")
    }

    private fun requestListenerRebind(reason: String, force: Boolean): Boolean {
        ensureListenerReliabilityComponents()

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O_MR1) {
            triggerLegacyListenerRebind(reason)
            return true
        }

        val requested = NotificationListenerRecovery.requestRebind(this, reason, force)
        if (requested) {
            listenerWatchdog?.recordExternalIntervention(reason)
            Log.i(TAG, "NotificationListenerService rebind requested (reason=$reason, force=$force)")
            InAppLogger.log("ServiceRebind", "requestRebind submitted (reason=$reason, force=$force)")
        } else {
            Log.w(TAG, "NotificationListenerService rebind request skipped (reason=$reason, force=$force)")
            InAppLogger.logWarning("ServiceRebind", "requestRebind skipped (reason=$reason, force=$force)")
        }
        return requested
    }

    private fun triggerLegacyListenerRebind(reason: String) {
        ensureListenerReliabilityComponents()
        try {
            val componentName = ComponentName(this, NotificationReaderService::class.java)
            cachedPackageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            listenerRebindHandler?.postDelayed({
                try {
                    cachedPackageManager.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    InAppLogger.log(
                        "ServiceRebind",
                        "Legacy component toggle completed (reason=$reason)"
                    )
                } catch (enableError: Exception) {
                    Log.e(TAG, "Failed to re-enable listener component (reason=$reason)", enableError)
                    InAppLogger.logError(
                        "ServiceRebind",
                        "Legacy listener re-enable failed ($reason): ${enableError.message}"
                    )
                }
            }, LEGACY_COMPONENT_REENABLE_DELAY_MS)
            listenerWatchdog?.recordExternalIntervention("legacy_$reason")
            Log.i(TAG, "Legacy listener component toggle scheduled (reason=$reason)")
        } catch (e: Exception) {
            Log.e(TAG, "Legacy listener toggle failed (reason=$reason)", e)
            InAppLogger.logError("ServiceRebind", "Legacy listener toggle failed ($reason): ${e.message}")
        }
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

                checkListenerHealth()
                
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
    
    /**
     * Start periodic dismissal memory cleanup
     * Removes old dismissed notification entries to prevent memory bloat
     */
    private fun startDismissalMemoryCleanup() {
        try {
            // Create handler if it doesn't exist
            if (dismissalMemoryCleanupHandler == null) {
                dismissalMemoryCleanupHandler = android.os.Handler(android.os.Looper.getMainLooper())
            }
            
            // Create cleanup runnable
            dismissalMemoryCleanupRunnable = Runnable {
                try {
                    cleanupDismissalMemory()
                    
                    // Schedule next cleanup
                    dismissalMemoryCleanupHandler?.postDelayed(dismissalMemoryCleanupRunnable!!, DISMISSAL_MEMORY_CLEANUP_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in dismissal memory cleanup", e)
                    InAppLogger.logError("Service", "Error in dismissal memory cleanup: " + e.message)
                }
            }
            
            // Start the first cleanup
            dismissalMemoryCleanupHandler?.postDelayed(dismissalMemoryCleanupRunnable!!, DISMISSAL_MEMORY_CLEANUP_INTERVAL_MS)
            Log.d(TAG, "Dismissal memory cleanup started (every ${DISMISSAL_MEMORY_CLEANUP_INTERVAL_MS / 1000} seconds)")
            InAppLogger.log("Service", "Dismissal memory cleanup started (every ${DISMISSAL_MEMORY_CLEANUP_INTERVAL_MS / 1000} seconds)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting dismissal memory cleanup", e)
            InAppLogger.logError("Service", "Error starting dismissal memory cleanup: " + e.message)
        }
    }
    
    /**
     * Stop periodic dismissal memory cleanup
     */
    private fun stopDismissalMemoryCleanup() {
        dismissalMemoryCleanupRunnable?.let { runnable ->
            dismissalMemoryCleanupHandler?.removeCallbacks(runnable)
            dismissalMemoryCleanupRunnable = null
        }
        dismissalMemoryCleanupHandler = null
        Log.d(TAG, "Dismissal memory cleanup stopped")
        InAppLogger.log("Service", "Dismissal memory cleanup stopped")
    }
    
    /**
     * Clean up old dismissed notification entries
     * Removes entries that are older than the configured timeout
     */
    private fun cleanupDismissalMemory() {
        try {
            val currentTime = System.currentTimeMillis()
            val timeoutMinutes = sharedPreferences?.getInt(KEY_DISMISSAL_MEMORY_TIMEOUT, DEFAULT_DISMISSAL_MEMORY_TIMEOUT_MINUTES) ?: DEFAULT_DISMISSAL_MEMORY_TIMEOUT_MINUTES
            val timeoutMs = timeoutMinutes * 60 * 1000L
            
            // Count entries before cleanup
            val entriesBeforeCleanup = dismissedNotificationKeys.size
            
            // Remove expired entries
            dismissedNotificationKeys.entries.removeIf { (_, dismissalTime) ->
                currentTime - dismissalTime > timeoutMs
            }
            
            // Log cleanup results
            val entriesAfterCleanup = dismissedNotificationKeys.size
            val entriesRemoved = entriesBeforeCleanup - entriesAfterCleanup
            
            if (entriesRemoved > 0) {
                Log.d(TAG, "Dismissal memory cleanup: Removed $entriesRemoved expired entries (timeout: ${timeoutMinutes} minutes)")
                InAppLogger.log("Service", "Dismissal memory cleanup: Removed $entriesRemoved expired entries")
            }
            
            // Log current stats
            Log.d(TAG, "Dismissal memory stats: $entriesAfterCleanup active entries, timeout: ${timeoutMinutes} minutes")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during dismissal memory cleanup", e)
            InAppLogger.logError("Service", "Error during dismissal memory cleanup: " + e.message)
        }
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
                val errorMessage = getTtsErrorMessage(status)
                Log.e(TAG, "TextToSpeech initialization failed with status: $status - $errorMessage")
                InAppLogger.logError("Service", "TextToSpeech initialization failed with status: $status - $errorMessage")
                
                // Check if we were trying to use a custom engine
                val voiceSettingsPrefs = getSharedPreferences("VoiceSettings", android.content.Context.MODE_PRIVATE)
                val selectedEngine = voiceSettingsPrefs.getString("tts_engine_package", "")
                
                if (!selectedEngine.isNullOrEmpty()) {
                    // Custom engine failed - log it and revert to default
                    Log.e(TAG, "Selected TTS engine failed: $selectedEngine")
                    InAppLogger.logError("Service", "Selected TTS engine failed: $selectedEngine")
                    
                    // Clear the saved engine and reinitialize with default
                    voiceSettingsPrefs.edit().putString("tts_engine_package", "").apply()
                    
                    // Set flag to show error notification
                    shouldShowEngineFailureWarning = true
                    
                    // Reinitialize with default engine
                    textToSpeech = TextToSpeech(this, this)
                    Log.d(TAG, "Reverting to system default TTS engine")
                    InAppLogger.log("Service", "Reverting to system default TTS engine")
                    return
                }
                
                // Attempt recovery for initialization failures
                attemptTtsRecovery("Initialization failed with status: $status - $errorMessage")
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
     * Uses package name, notification ID, and content hash to identify duplicates
     * Removed timestamp to better handle notification updates
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
        
        // Remove timestamp to better handle notification updates
        // Use package name, notification ID, and content hash only
        return "${packageName}_${notificationId}_${contentHash}"
    }
    
    /**
     * Generate a content-based key for additional deduplication
     * Used for all apps to catch notification updates with similar content
     */
    private fun generateContentKey(packageName: String, content: String): String {
        // Normalize content for better deduplication (remove extra whitespace, normalize case)
        val normalizedContent = content.trim().replace(Regex("\\s+"), " ").lowercase()
        
        val contentHash = try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(normalizedContent.toByteArray())
            hashBytes.take(6).joinToString("") { "%02x".format(it) } // Use first 6 bytes as hex string for better uniqueness
        } catch (e: Exception) {
            Log.e(TAG, "Error generating content hash, falling back to hashCode", e)
            normalizedContent.hashCode().toString()
        }
        return "content_${packageName}_${contentHash}"
    }
    
    /**
     * Generate a content hash for dismissal memory tracking
     * Uses package name and normalized content to identify dismissed notifications
     * More aggressive normalization for better dismissal detection
     */
    private fun generateDismissalContentHash(packageName: String, content: String): String {
        try {
            // Normalize content more aggressively for dismissal tracking
            // Remove extra whitespace, normalize case, and remove common punctuation
            val normalizedContent = content.trim()
                .replace(Regex("\\s+"), " ") // Normalize whitespace
                .lowercase() // Normalize case
                .replace(Regex("[.,!?;:]"), "") // Remove common punctuation
                .trim() // Final trim
            
            // Fallback for empty content
            if (normalizedContent.isEmpty()) {
                Log.w(TAG, "Empty content after normalization - using package name only for dismissal hash")
                return "dismissal_${packageName}_empty"
            }
            
            val contentHash = try {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val hashBytes = digest.digest(normalizedContent.toByteArray())
                hashBytes.take(8).joinToString("") { "%02x".format(it) } // Use first 8 bytes as hex string
            } catch (e: Exception) {
                Log.e(TAG, "Error generating dismissal content hash, falling back to hashCode", e)
                normalizedContent.hashCode().toString()
            }
            
            // Include package name in hash for app-specific dismissal tracking
            return "dismissal_${packageName}_${contentHash}"
            
        } catch (e: Exception) {
            // Ultimate fallback: use package name and content length
            Log.e(TAG, "Critical error generating dismissal content hash, using fallback", e)
            return "dismissal_${packageName}_fallback_${content.length}"
        }
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
        
        // Special handling for Gmail notifications to better handle their update behavior
        // Gmail often updates a single notification with new content rather than creating new notifications
        if (extras.getString("android.template")?.contains("gmail") == true || 
            title.contains("Gmail", ignoreCase = true) ||
            summaryText.contains("new message", ignoreCase = true) ||
            text.contains("new message", ignoreCase = true)) {
            
            Log.d(TAG, "Detected Gmail-style notification - applying special content extraction logic")
            
            // For Gmail-style notifications, prioritize the most recent content
            // Gmail typically puts the latest email content in bigText
            return when {
                bigText.isNotEmpty() && !bigText.contains("new message", ignoreCase = true) -> {
                    // If bigText contains actual email content (not just "3 new messages"), use it
                    Log.d(TAG, "Gmail: Using bigText as primary content: '$bigText'")
                    bigText
                }
                text.isNotEmpty() && !text.contains("new message", ignoreCase = true) -> {
                    // If text contains actual email content, use it
                    Log.d(TAG, "Gmail: Using text as primary content: '$text'")
                    text
                }
                title.isNotEmpty() && text.isNotEmpty() -> {
                    // Fallback: combine title and text, but filter out generic "new messages" text
                    val combinedText = if (text.contains("new message", ignoreCase = true)) {
                        title
                    } else {
                        "$title: $text"
                    }
                    Log.d(TAG, "Gmail: Using combined title/text: '$combinedText'")
                    combinedText
                }
                else -> {
                    // Last resort: use whatever content is available
                    val fallbackContent = bigText.ifEmpty { text.ifEmpty { title.ifEmpty { summaryText.ifEmpty { infoText } } } }
                    Log.d(TAG, "Gmail: Using fallback content: '$fallbackContent'")
                    fallbackContent
                }
            }
        }
        
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
        
        // Wave hold duration (debounce)
        var holdDuration = sharedPreferences?.getInt(
            KEY_WAVE_HOLD_DURATION_MS,
            DEFAULT_WAVE_HOLD_DURATION_MS.toInt()
        ) ?: DEFAULT_WAVE_HOLD_DURATION_MS.toInt()
        if (holdDuration < MIN_WAVE_HOLD_DURATION_MS || holdDuration > MAX_WAVE_HOLD_DURATION_MS) {
            Log.w(TAG, "Invalid wave hold duration detected ($holdDuration ms), resetting to default")
            InAppLogger.logWarning(TAG, "Invalid wave hold duration detected, resetting to default")
            holdDuration = DEFAULT_WAVE_HOLD_DURATION_MS.toInt()
            sharedPreferences?.edit()?.putInt(KEY_WAVE_HOLD_DURATION_MS, holdDuration)?.apply()
        }
        waveHoldDurationMs = holdDuration.toLong()

        // Load pocket mode setting
        isPocketModeEnabled = sharedPreferences?.getBoolean("pocket_mode_enabled", false) ?: false
        
        Log.d(
            TAG,
            "Wave settings loaded - enabled: $isWaveToStopEnabled, threshold: $waveThreshold, timeout: ${waveTimeoutSeconds}s, hold: ${waveHoldDurationMs}ms, pocket mode: $isPocketModeEnabled"
        )
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
            val sensorName = proximitySensor?.name ?: "unknown"
            InAppLogger.logSystemEvent(
                "Wave listener started",
                "TTS playback active - sensor=$sensorName, maxRange=${proximitySensor?.maximumRange ?: -1f}cm"
            )
            waveEventCount = 0
            waveNoEventRunnable?.let { sensorTimeoutHandler?.removeCallbacks(it) }
            waveNoEventRunnable = Runnable {
                if (waveEventCount == 0 && isCurrentlySpeaking && isWaveToStopEnabled) {
                    Log.w(TAG, "Wave listener active but no proximity events received in 1000ms")
                    InAppLogger.logWarning(TAG, "Wave listener active but no proximity events received in 1000ms")
                }
            }
            sensorTimeoutHandler?.postDelayed(waveNoEventRunnable!!, 1000L)
        }
        
        // Start timeout only if enabled (timeout > 0)
        if (shakeTimeoutSeconds > 0 || waveTimeoutSeconds > 0) {
            // Use the longer timeout if both are enabled, otherwise use the enabled one
            val effectiveTimeout = when {
                shakeTimeoutSeconds > 0 && waveTimeoutSeconds > 0 -> maxOf(shakeTimeoutSeconds, waveTimeoutSeconds)
                shakeTimeoutSeconds > 0 -> shakeTimeoutSeconds
                waveTimeoutSeconds > 0 -> waveTimeoutSeconds
                else -> 60 // Increased fallback timeout from 30 to 60 seconds
            }
            
            // Cancel any previous timeout
            sensorTimeoutRunnable?.let { sensorTimeoutHandler?.removeCallbacks(it) }
            sensorTimeoutRunnable = Runnable {
                Log.w(TAG, "Sensor listener timeout reached after ${effectiveTimeout}s! Forcibly unregistering sensors.")
                InAppLogger.logWarning(TAG, "Sensor listener timeout reached after ${effectiveTimeout}s! Forcibly unregistering sensors.")
                InAppLogger.logSystemEvent("Sensor timeout triggered", "Sensors forcibly unregistered after ${effectiveTimeout}s")
                unregisterShakeListener()
            }
            
            // Safety check to ensure handler is initialized
            if (sensorTimeoutHandler != null) {
                sensorTimeoutHandler?.postDelayed(sensorTimeoutRunnable!!, (effectiveTimeout * 1000).toLong())
                Log.d(TAG, "Sensor timeout scheduled for ${effectiveTimeout} seconds")
            } else {
                Log.e(TAG, "Sensor timeout handler is null - cannot schedule timeout!")
                InAppLogger.logError("Service", "Sensor timeout handler is null - cannot schedule timeout")
            }
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
        waveNoEventRunnable?.let { sensorTimeoutHandler?.removeCallbacks(it) }
        waveNoEventRunnable = null
        pendingWaveTriggerRunnable?.let { sensorTimeoutHandler?.removeCallbacks(it) }
        pendingWaveTriggerRunnable = null
        
        // Log sensor state for debugging
        Log.d(TAG, "Sensor unregistration complete - shake enabled: $isShakeToStopEnabled, wave enabled: $isWaveToStopEnabled")
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
        wordListMode = sharedPreferences?.getString(KEY_WORD_LIST_MODE, "blacklist") ?: "blacklist"
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
        
        // Load URL handling settings
        urlHandlingMode = sharedPreferences?.getString(KEY_URL_HANDLING_MODE, DEFAULT_URL_HANDLING_MODE) ?: DEFAULT_URL_HANDLING_MODE
        urlReplacementText = sharedPreferences?.getString(KEY_URL_REPLACEMENT_TEXT, DEFAULT_URL_REPLACEMENT_TEXT) ?: DEFAULT_URL_REPLACEMENT_TEXT
        Log.d(TAG, "Loaded URL handling: mode=$urlHandlingMode, replacement='$urlReplacementText'")

        // Load tidy speech settings
        tidySpeechRemoveEmojisEnabled = sharedPreferences?.getBoolean(KEY_TIDY_SPEECH_REMOVE_EMOJIS, false) ?: false
        Log.d(TAG, "Loaded tidy speech settings: removeEmojis=$tidySpeechRemoveEmojisEnabled")
        
        // Load Content Cap settings
        contentCapMode = sharedPreferences?.getString(KEY_CONTENT_CAP_MODE, DEFAULT_CONTENT_CAP_MODE) ?: DEFAULT_CONTENT_CAP_MODE
        contentCapWordCount = sharedPreferences?.getInt(KEY_CONTENT_CAP_WORD_COUNT, DEFAULT_CONTENT_CAP_WORD_COUNT) ?: DEFAULT_CONTENT_CAP_WORD_COUNT
        contentCapSentenceCount = sharedPreferences?.getInt(KEY_CONTENT_CAP_SENTENCE_COUNT, DEFAULT_CONTENT_CAP_SENTENCE_COUNT) ?: DEFAULT_CONTENT_CAP_SENTENCE_COUNT
        contentCapTimeLimit = sharedPreferences?.getInt(KEY_CONTENT_CAP_TIME_LIMIT, DEFAULT_CONTENT_CAP_TIME_LIMIT) ?: DEFAULT_CONTENT_CAP_TIME_LIMIT
        Log.d(TAG, "Loaded Content Cap settings: mode=$contentCapMode, wordCount=$contentCapWordCount, sentenceCount=$contentCapSentenceCount, timeLimit=$contentCapTimeLimit")
        InAppLogger.log("Service", "Content Cap loaded: mode=$contentCapMode, wordCount=$contentCapWordCount, sentenceCount=$contentCapSentenceCount, timeLimit=${contentCapTimeLimit}s")
        
        // Load behavior settings  
        notificationBehavior = sharedPreferences?.getString(KEY_NOTIFICATION_BEHAVIOR, "interrupt") ?: "interrupt"
        priorityApps = HashSet(sharedPreferences?.getStringSet(KEY_PRIORITY_APPS, HashSet()) ?: HashSet())
        
        // Load media behavior settings
        mediaBehavior = sharedPreferences?.getString(KEY_MEDIA_BEHAVIOR, "ignore") ?: "ignore"
        duckingVolume = sharedPreferences?.getInt(KEY_DUCKING_VOLUME, 30) ?: 30
        duckingFallbackStrategy = sharedPreferences?.getString(KEY_DUCKING_FALLBACK_STRATEGY, "manual") ?: "manual"
        
        // Load delay settings
        delayBeforeReadout = sharedPreferences?.getInt(KEY_DELAY_BEFORE_READOUT, 0) ?: 0
        
        // Load media notification filtering settings
        val isMediaFilteringEnabled = sharedPreferences?.getBoolean(KEY_MEDIA_FILTERING_ENABLED, true) ?: true
        val exceptedApps = HashSet(sharedPreferences?.getStringSet(KEY_MEDIA_FILTER_EXCEPTED_APPS, HashSet()) ?: HashSet())
        val importantKeywords = HashSet(sharedPreferences?.getStringSet(KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS, HashSet()) ?: HashSet())
        val filteredMediaApps = HashSet(sharedPreferences?.getStringSet(KEY_MEDIA_FILTERED_APPS, HashSet()) ?: HashSet())
        
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
        
        // Load speech template + key
        refreshSpeechTemplateState()
        
        // Load notification settings (values used in other parts of the service)
        sharedPreferences?.getBoolean(KEY_PERSISTENT_NOTIFICATION, false) ?: false
        sharedPreferences?.getBoolean(KEY_NOTIFICATION_WHILE_READING, false) ?: false
        
        Log.d(TAG, "Filter settings loaded - appMode: $appListMode, apps: ${appList.size}, blocked words: ${blockedWords.size}, replacements: ${wordReplacements.size}")
        Log.d(TAG, "Behavior settings loaded - mode: $notificationBehavior, priority apps: ${priorityApps.size}")
        Log.d(TAG, "Media behavior settings loaded - mode: $mediaBehavior, ducking volume: $duckingVolume%, fallback: $duckingFallbackStrategy")
        Log.d(TAG, "Delay settings loaded - delay: ${delayBeforeReadout}s")
        Log.d(TAG, "Media filtering settings loaded - enabled: $isMediaFilteringEnabled, excepted apps: ${exceptedApps.size}, important keywords: ${importantKeywords.size}")
        Log.d(TAG, "Persistent filtering enabled: $isPersistentFilteringEnabled")
        InAppLogger.log("Service", "Settings loaded - Filter mode: $appListMode, Behavior: $notificationBehavior, Media: $mediaBehavior, Delay: ${delayBeforeReadout}s, Media filtering: $isMediaFilteringEnabled, Persistent filtering: $isPersistentFilteringEnabled")
    }
    
    data class FilterResult(
        val shouldSpeak: Boolean,
        val processedText: String,
        val reason: String = "",
        val conditionalDelaySeconds: Int = -1, // -1 means no conditional delay
        val speechTemplateOverride: SpeechTemplateOverride? = null
    )

    data class SpeechTemplateOverride(
        val template: String,
        val templateKey: String? = null
    )
    
    /**
     * Extract a user-friendly blocking reason type from the detailed reason string.
     * This allows us to always log why a notification was blocked without revealing sensitive details.
     * 
     * @param reason The detailed reason string from FilterResult
     * @return A user-friendly reason type (e.g., "filtered word detected", "app in blacklist")
     */
    private fun extractBlockingReasonType(reason: String): String {
        return when {
            reason.startsWith("App not in whitelist") -> "app not in whitelist"
            reason.startsWith("App blacklisted") -> "app in blacklist"
            reason.startsWith("Blocked by filter:") -> "filtered word detected"
            reason.startsWith("Cooldown active") -> "cooldown active"
            reason.startsWith("Media notification filtered:") -> "media notification filtered"
            reason.startsWith("Persistent/silent notification:") -> "persistent/silent notification"
            reason.startsWith("Rules blocking:") -> "rules blocking"
            reason.startsWith("Duplicate notification") -> "duplicate notification detected"
            reason.contains("dismissal memory", ignoreCase = true) -> "dismissal memory"
            reason.isNotEmpty() -> reason // Fallback: use the reason as-is if we don't recognize the pattern
            else -> "unknown reason"
        }
    }
    
    private fun applyFilters(
        packageName: String,
        appName: String,
        text: String,
        sbn: StatusBarNotification? = null,
        isSelfTest: Boolean = false
    ): FilterResult {
        // 1. Check app filtering
        val appFilterResult = checkAppFilter(packageName, isSelfTest)
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
        
        // 5. Evaluate conditional rules (Smart Rules system)
        val notificationContext = buildNotificationContext(packageName, text, sbn)
        val outcome = evaluateRuleEffects(notificationContext)
        val effects = outcome?.effects.orEmpty()

        if (effects.any { it is com.micoyc.speakthat.rules.Effect.SkipNotification }) {
            val blockingRules = ruleManager.getBlockingRuleNames(notificationContext)
            val reason = "Rules blocking: ${blockingRules.joinToString(", ")}"
            InAppLogger.logFilter("Rules blocked notification: $reason")
            // Track filter reason
            try {
                StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_CONDITIONAL_RULES)
            } catch (e: Exception) {
                Log.e(TAG, "Error tracking conditional rules filter", e)
            }
            return FilterResult(false, "", reason)
        }

        val overridePrivate = effects.any { it is com.micoyc.speakthat.rules.Effect.OverridePrivate }
        val forcePrivate = effects.any { it is com.micoyc.speakthat.rules.Effect.ForcePrivate }
        val speechTemplateEffect = effects
            .filterIsInstance<com.micoyc.speakthat.rules.Effect.SetSpeechTemplate>()
            .lastOrNull()
        val speechTemplateOverride = speechTemplateEffect?.let {
            SpeechTemplateOverride(it.template, it.templateKey)
        }

        // 6. Apply word filtering and replacements (optionally overriding private mode)
        val wordFilterResult = applyWordFiltering(text, appName, packageName, overridePrivate)
        if (!wordFilterResult.shouldSpeak) {
            return wordFilterResult
        }

        var processedText = wordFilterResult.processedText
        if (forcePrivate) {
            InAppLogger.logFilter("Rule effect: force private")
            processedText = getLocalizedTemplate("private_notification", appName, "")
        } else if (overridePrivate) {
            InAppLogger.logFilter("Rule effect: override private")
        }

        if (speechTemplateOverride != null) {
            InAppLogger.logFilter("Rule effect: apply custom speech format")
        }

        return FilterResult(
            shouldSpeak = true,
            processedText = processedText,
            reason = "Passed all filters",
            speechTemplateOverride = speechTemplateOverride
        )
    }
    
    private fun checkAppFilter(packageName: String, isSelfTest: Boolean = false): FilterResult {
        if (isSelfTest) {
            Log.d(TAG, "SelfTest bypass - skipping app list checks for $packageName")
            InAppLogger.log("SelfTest", "App list bypassed for SelfTest notification")
            return FilterResult(true, "", "SelfTest bypassed app list")
        }
        return when (appListMode) {
            "whitelist" -> {
                if (appList.contains(packageName)) {
                    FilterResult(true, "", "App whitelisted")
                } else {
                    // Track filter reason
                    try {
                        StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_APP_LIST)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error tracking app list filter", e)
                    }
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
                        // Track filter reason
                        try {
                            StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_APP_LIST)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error tracking app list filter", e)
                        }
                        FilterResult(false, "", "App blacklisted")
                    }
                } else {
                    FilterResult(true, "", "App not blacklisted")
                }
            }
            else -> FilterResult(true, "", "No app filtering")
        }
    }
    
    private fun applyWordFiltering(
        text: String,
        appName: String,
        packageName: String = "",
        overridePrivate: Boolean = false
    ): FilterResult {
        var processedText = text
        
        // SECURITY: Check if this app is in private mode FIRST (highest priority)
        // This ensures private apps are never processed for word filtering that might reveal content
        if (!overridePrivate && packageName.isNotEmpty() && privateApps.contains(packageName)) {
            processedText = getLocalizedTemplate("private_notification", appName, "")
            Log.d(TAG, "App '$appName' is in private mode - entire notification made private (SECURITY: bypassing all other filters)")
            InAppLogger.logFilter("Made notification private due to app privacy setting (SECURITY: bypassing all other filters)")
            return FilterResult(true, processedText, "App-level privacy applied")
        }
        
        // Note: Even if no word filters are configured, we still need to apply URL handling and Content Cap
        // So we can't early return here anymore
        
        // 1. Apply word list filtering based on mode (none/whitelist/blacklist)
        when (wordListMode) {
            "none" -> {
                // No word filtering - skip blocked words check entirely
                Log.d(TAG, "Word list filtering disabled (mode: none)")
            }
            "blacklist" -> {
                // BLACKLIST MODE: Block notifications containing these words
                for (blockedWord in blockedWords) {
                    if (matchesWordFilter(processedText, blockedWord)) {
                        // Track filter reason
                        try {
                            StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_WORD_FILTERS)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error tracking word filter", e)
                        }
                        Log.d(TAG, "Notification blocked by blacklist word: $blockedWord")
                        return FilterResult(false, "", "Blocked by blacklist word: $blockedWord")
                    }
                }
            }
            "whitelist" -> {
                // WHITELIST MODE: Only allow notifications containing these words
                if (blockedWords.isNotEmpty()) {
                    var foundMatchingWord = false
                    for (allowedWord in blockedWords) {
                        if (matchesWordFilter(processedText, allowedWord)) {
                            foundMatchingWord = true
                            Log.d(TAG, "Notification allowed by whitelist word: $allowedWord")
                            break
                        }
                    }
                    
                    if (!foundMatchingWord) {
                        // No matching word found - block the notification
                        try {
                            StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_WORD_FILTERS)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error tracking word filter", e)
                        }
                        Log.d(TAG, "Notification blocked - no whitelist word found")
                        return FilterResult(false, "", "Blocked - no whitelist word match")
                    }
                }
            }
        }
        
        // 2. Check for private words and replace entire notification with [PRIVATE]
        // This works independently of the word list mode - private words ALWAYS make notifications private
        if (!overridePrivate) {
            for (privateWord in privateWords) {
                if (matchesWordFilter(processedText, privateWord)) {
                    // When any private word is detected, replace the entire notification text with a private message
                    // This ensures complete privacy - no partial content is revealed
                    processedText = getLocalizedTemplate("private_notification", appName, "")
                    
                    // Always log private word detection for debugging
                    Log.d(TAG, "Private word '$privateWord' detected - entire notification made private")
                    InAppLogger.logFilter("Made notification private due to word: $privateWord")
                    break // Exit loop since entire text is now private
                }
            }
        }
        
        // 3. Apply word swaps (only for non-private notifications and only if there are replacements)
        val wordSwapStartText = processedText
        var wordSwapChangesMade = false
        val appliedReplacements = mutableListOf<String>()
        
        if (wordReplacements.isNotEmpty()) {
            Log.d(TAG, "Word replacements available: ${wordReplacements.size} items")
            for ((from, to) in wordReplacements) {
                val originalText = processedText
                processedText = processedText.replace(from, to, ignoreCase = true)
                if (originalText != processedText) {
                    Log.d(TAG, "Word replacement applied: '$from' -> '$to'")
                    wordSwapChangesMade = true
                    appliedReplacements.add("'$from' -> '$to'")
                } else {
                    Log.d(TAG, "Word replacement not found: '$from' in text: '$processedText'")
                }
            }
        }
        
        // Log summary to InAppLogger
        if (wordSwapChangesMade) {
            val changesSummary = appliedReplacements.joinToString(", ")
            InAppLogger.logFilter("Word swaps applied: $changesSummary | Before: '$wordSwapStartText' | After: '$processedText'")
        } else if (wordReplacements.isNotEmpty()) {
            InAppLogger.logFilter("No word swaps applied (${wordReplacements.size} rules checked) | Text unchanged: '$processedText'")
        }
        
        // Only log detailed filter processing in verbose mode
        if (InAppLogger.verboseMode) {
            Log.d(TAG, "=== FILTERING DEBUG: Word replacement section completed, about to start URL handling ===")
            InAppLogger.logFilter("=== WORD REPLACEMENT COMPLETE - Starting URL/Content Cap processing ===")
        }
        
        // 4. Apply URL handling (only for non-private notifications)
        if (urlHandlingMode != "read_full") {
            if (InAppLogger.verboseMode) {
                Log.d(TAG, "=== URL HANDLING DEBUG: About to apply URL handling ===")
                InAppLogger.logFilter("URL handling mode: $urlHandlingMode - applying URL processing")
            }
            processedText = applyUrlHandling(processedText)
        } else {
            if (InAppLogger.verboseMode) {
                Log.d(TAG, "=== URL HANDLING DEBUG: URL handling disabled (read_full mode) ===")
                InAppLogger.logFilter("URL handling: read_full mode - skipping")
            }
        }
        
        // 5. Apply Content Cap (only for non-private notifications)
        if (InAppLogger.verboseMode) {
            Log.d(TAG, "=== CONTENT CAP DEBUG: About to check content cap - mode=$contentCapMode ===")
            InAppLogger.logFilter("=== CONTENT CAP CHECK: mode=$contentCapMode ===")
        }
        if (contentCapMode != "disabled") {
            if (InAppLogger.verboseMode) {
                Log.d(TAG, "=== CONTENT CAP DEBUG: Content cap IS enabled, calling applyContentCap() ===")
                InAppLogger.logFilter("Content cap ENABLED ($contentCapMode) - calling applyContentCap()")
            }
            processedText = applyContentCap(processedText)
            if (InAppLogger.verboseMode) {
                InAppLogger.logFilter("Content cap processing completed")
            }
        } else {
            if (InAppLogger.verboseMode) {
                Log.d(TAG, "=== CONTENT CAP DEBUG: Content cap is disabled, skipping ===")
                InAppLogger.logFilter("Content cap DISABLED - skipping")
            }
        }
        
        if (InAppLogger.verboseMode) {
            InAppLogger.logFilter("=== WORD FILTERING COMPLETE - returning filtered text ===")
        }
        return FilterResult(true, processedText, "Word filtering applied")
    }
    
    
    /**
     * Handles URL processing based on user preferences
     * Supports various URL formats: http, https, www, bare domains (e.g., speakthat.app, youtu.be), localhost, IP addresses, IPv6
     */
    private fun applyUrlHandling(text: String): String {
        if (urlHandlingMode == "read_full") {
            return text // No processing needed
        }
        
        // Skip processing for very long texts to prevent performance issues
        if (text.length > 10000) {
            Log.w(TAG, "Text too long for URL processing (${text.length} chars), skipping")
            return text
        }
        
        return URL_PATTERN.replace(text) { matchResult ->
            val url = matchResult.value
            val replacement = when (urlHandlingMode) {
                "domain_only" -> extractDomain(url)
                "dont_read" -> if (urlReplacementText.isNotEmpty()) urlReplacementText else ""
                else -> url // Fallback to original URL
            }
            
            Log.d(TAG, "URL handling applied: '$url' -> '$replacement' (mode=$urlHandlingMode, replacementText='$urlReplacementText')")
            InAppLogger.logFilter("URL handling applied: '$url' -> '$replacement' (mode=$urlHandlingMode, replacementText='$urlReplacementText')")
            replacement
        }
    }
    
    /**
     * Extracts the domain from a URL, handling various formats
     * Examples:
     * - https://www.speakthat.app/subdirectory -> speakthat.app
     * - www.amazon.com/dp/B08N5WRWNW -> amazon.com
     * - speakthat.app -> speakthat.app
     * - youtu.be -> youtu.be
     * - localhost:8080 -> localhost
     * - 192.168.1.1:3000 -> 192.168.1.1
     * Note: Never includes protocol (http/https) in the result
     */
    private fun extractDomain(url: String): String {
        try {
            // Validate input
            if (url.isBlank()) {
                Log.w(TAG, "Empty URL provided to extractDomain")
                return "link"
            }
            
            // Remove protocol if present (https:// or http://)
            var cleanUrl = url.replace(Regex("^https?://"), "")
            
            // Remove www. prefix if present
            cleanUrl = cleanUrl.replace(Regex("^www\\."), "")
            
            // Split by / to get the host part
            val hostPart = cleanUrl.split("/")[0]
            
            // Split by : to remove port if present
            val domainPart = hostPart.split(":")[0]
            
            // Validate domain part
            if (domainPart.isBlank()) {
                Log.w(TAG, "Empty domain part extracted from URL '$url'")
                return "link"
            }
            
            // For localhost and IP addresses, return as-is
            if (domainPart == "localhost" || domainPart.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) {
                return domainPart
            }
            
            // For IPv6 addresses (in brackets), return as-is
            if (domainPart.startsWith("[") && domainPart.endsWith("]")) {
                return domainPart
            }
            
            // For regular domains, extract the main domain (last two parts)
            val parts = domainPart.split(".")
            return if (parts.size >= 2) {
                parts.takeLast(2).joinToString(".")
            } else {
                domainPart
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting domain from URL '$url': ${e.message}")
            // Return a safe fallback instead of original URL to avoid reading long URLs
            return "link"
        }
    }
    
    /**
     * Apply Content Cap to limit notification length
     * Supports word count, sentence count, and time limit modes
     */
    private fun applyContentCap(text: String): String {
        // Only log detailed content cap info in verbose mode to reduce noise
        if (InAppLogger.verboseMode) {
            Log.d(TAG, "=== CONTENT CAP DEBUG: applyContentCap() called ===")
            InAppLogger.log("Service", "=== APPLY CONTENT CAP CALLED ===")
            Log.d(TAG, "Content Cap mode: $contentCapMode, wordCount: $contentCapWordCount, sentenceCount: $contentCapSentenceCount")
            InAppLogger.log("Service", "Content Cap settings: mode=$contentCapMode, words=$contentCapWordCount, sentences=$contentCapSentenceCount")
            Log.d(TAG, "Input text: '$text'")
            InAppLogger.log("Service", "Input text length: ${text.length} chars")
        }
        
        // Null safety check
        if (text.isNullOrEmpty()) {
            Log.w(TAG, "Content Cap: Input text is null or empty, returning empty string")
            InAppLogger.log("Service", "Content Cap: Empty input text, returning empty")
            return ""
        }
        
        // Early return for disabled mode - zero processing overhead
        if (contentCapMode == "disabled") {
            if (InAppLogger.verboseMode) {
                Log.d(TAG, "Content Cap disabled, returning original text")
                InAppLogger.log("Service", "Content Cap: Mode is disabled, skipping")
            }
            return text
        }
        
        val originalLength = text.length
        val result = when (contentCapMode) {
            "words" -> {
                if (InAppLogger.verboseMode) {
                    Log.d(TAG, "Applying word cap...")
                    InAppLogger.log("Service", "Applying WORD cap...")
                }
                applyWordCap(text)
            }
            "sentences" -> {
                if (InAppLogger.verboseMode) {
                    Log.d(TAG, "Applying sentence cap...")
                    InAppLogger.log("Service", "Applying SENTENCE cap...")
                }
                applySentenceCap(text)
            }
            "time" -> {
                if (InAppLogger.verboseMode) {
                    Log.d(TAG, "Time cap mode - no text processing needed")
                }
                text // Time cap is handled in TTS layer, not text processing
            }
            else -> {
                Log.w(TAG, "Unknown content cap mode: $contentCapMode, returning original text")
                text
            }
        }
        
        // Ensure result is never null
        val safeResult = result ?: text
        
        // Log content cap application (only when changes are made or in verbose mode)
        if (safeResult != text) {
            Log.d(TAG, "Content Cap applied (mode=$contentCapMode): Original=${originalLength} chars, Capped=${safeResult.length} chars")
            InAppLogger.log("Service", "Content Cap applied (mode=$contentCapMode): ${originalLength} chars → ${safeResult.length} chars")
        } else if (InAppLogger.verboseMode) {
            Log.d(TAG, "Content Cap: No change needed (text within limit)")
        }
        
        if (InAppLogger.verboseMode) {
            Log.d(TAG, "=== CONTENT CAP DEBUG: Result: '${safeResult.take(150)}'")
        }
        return safeResult
    }
    
    /**
     * Apply word count limit
     * Splits text by whitespace and takes first N words
     */
    private fun applyWordCap(text: String): String {
        val words = text.split("\\s+".toRegex())
        
        // If text has fewer words than limit, return original
        if (words.size <= contentCapWordCount) {
            Log.d(TAG, "Word cap: Text has ${words.size} words, limit is $contentCapWordCount - no cap needed")
            return text
        }
        
        val cappedText = words.take(contentCapWordCount).joinToString(" ")
        Log.d(TAG, "Word cap applied: ${words.size} words → $contentCapWordCount words")
        InAppLogger.log("Service", "Word cap applied: ${words.size} words → $contentCapWordCount words")
        
        return cappedText
    }
    
    /**
     * Apply sentence count limit
     * Uses BreakIterator for proper international sentence detection
     */
    private fun applySentenceCap(text: String): String {
        try {
            InAppLogger.log("Service", "=== SENTENCE CAP START ===")
            InAppLogger.log("Service", "Text to cap: ${text.take(100)}... (${text.length} chars)")
            InAppLogger.log("Service", "Sentence limit: $contentCapSentenceCount")
            
            // Use BreakIterator for proper sentence boundary detection
            // This handles international punctuation and line breaks correctly
            val iterator = java.text.BreakIterator.getSentenceInstance(java.util.Locale.getDefault())
            iterator.setText(text)
            
            var sentenceCount = 0
            var endIndex = 0
            var start = iterator.first()
            
            InAppLogger.log("Service", "Starting sentence detection loop...")
            while (start != java.text.BreakIterator.DONE && sentenceCount < contentCapSentenceCount) {
                val end = iterator.next()
                InAppLogger.log("Service", "Loop iteration: sentenceCount=$sentenceCount, start=$start, end=$end")
                if (end == java.text.BreakIterator.DONE) {
                    endIndex = text.length
                    sentenceCount++
                    InAppLogger.log("Service", "Reached DONE - endIndex=$endIndex, sentenceCount=$sentenceCount")
                    break
                }
                endIndex = end
                sentenceCount++
                InAppLogger.log("Service", "Found sentence boundary at $end - sentenceCount=$sentenceCount")
            }
            
            InAppLogger.log("Service", "Loop complete: sentenceCount=$sentenceCount, endIndex=$endIndex, textLength=${text.length}")
            
            // If text has fewer sentences than limit, return original
            if (sentenceCount <= contentCapSentenceCount && endIndex >= text.length) {
                Log.d(TAG, "Sentence cap: Text has $sentenceCount sentences, limit is $contentCapSentenceCount - no cap needed")
                InAppLogger.log("Service", "Sentence cap: NO CAP NEEDED - Text has $sentenceCount sentences (limit: $contentCapSentenceCount)")
                return text
            }
            
            InAppLogger.log("Service", "Applying cap - will substring from 0 to $endIndex")
            val cappedText = if (endIndex > 0 && endIndex <= text.length) {
                text.substring(0, endIndex).trim()
            } else {
                InAppLogger.log("Service", "ERROR: Invalid endIndex=$endIndex, returning original")
                text
            }
            
            InAppLogger.log("Service", "Capped text: ${cappedText.take(100)}... (${cappedText.length} chars)")
            Log.d(TAG, "Sentence cap applied: Capped to $contentCapSentenceCount sentence(s)")
            InAppLogger.log("Service", "Sentence cap applied: Capped to $contentCapSentenceCount sentence(s)")
            
            return cappedText
        } catch (e: Exception) {
            Log.e(TAG, "Error applying sentence cap: ${e.message}", e)
            InAppLogger.logError("Service", "Error applying sentence cap: ${e.message}")
            // Fallback to original text on error
            return text
        }
    }
    
    private fun evaluateRuleEffects(
        notificationContext: com.micoyc.speakthat.rules.NotificationContext
    ): com.micoyc.speakthat.rules.EvaluationOutcome? {
        return try {
            if (!::ruleManager.isInitialized) {
                InAppLogger.logFilter("Rule manager not initialized, allowing notification")
                null
            } else {
                val outcome = ruleManager.evaluateNotification(notificationContext)
                applyMasterSwitchEffects(outcome.effects)
                logUnappliedRuleEffects(outcome.effects)
                outcome
            }
        } catch (e: Exception) {
            InAppLogger.logError("Service", "Error in rule evaluation: ${e.message}")
            null
        }
    }

    private fun buildNotificationContext(
        packageName: String,
        fallbackText: String,
        sbn: StatusBarNotification?
    ): com.micoyc.speakthat.rules.NotificationContext {
        val notification = sbn?.notification
        val extras = notification?.extras
        val isOngoing = notification?.let { note ->
            val flags = note.flags
            (flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
                (flags and Notification.FLAG_NO_CLEAR) != 0
        } ?: false

        return com.micoyc.speakthat.rules.NotificationContext(
            packageName = packageName,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT) ?: fallbackText,
            bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT),
            ticker = notification?.tickerText,
            category = notification?.category,
            channelId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                notification?.channelId
            } else {
                null
            },
            isOngoing = isOngoing,
            postTime = sbn?.postTime ?: System.currentTimeMillis(),
            extras = extras
        )
    }

    private fun applyMasterSwitchEffects(effects: List<com.micoyc.speakthat.rules.Effect>) {
        val masterSwitch = effects.filterIsInstance<com.micoyc.speakthat.rules.Effect.SetMasterSwitch>().lastOrNull()
            ?: return

        val sharedPreferences = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("speakthat_enabled", masterSwitch.enabled).apply()
        InAppLogger.logFilter("Rule effect: set master switch to ${masterSwitch.enabled}")
    }

    private fun logUnappliedRuleEffects(effects: List<com.micoyc.speakthat.rules.Effect>) {
        val unapplied = effects.filterNot { effect ->
            effect is com.micoyc.speakthat.rules.Effect.SkipNotification ||
                effect is com.micoyc.speakthat.rules.Effect.SetMasterSwitch ||
            effect is com.micoyc.speakthat.rules.Effect.SetSpeechTemplate ||
            effect is com.micoyc.speakthat.rules.Effect.ForcePrivate ||
            effect is com.micoyc.speakthat.rules.Effect.OverridePrivate
        }

        if (unapplied.isNotEmpty()) {
            val effectNames = unapplied.joinToString(", ") { it::class.simpleName ?: "UnknownEffect" }
            InAppLogger.logFilter("Rule effects not yet applied: $effectNames")
        }
    }
    
    private fun applyMediaFiltering(sbn: StatusBarNotification): FilterResult {
        if (!mediaFilterPreferences.isMediaFilteringEnabled) {
            return FilterResult(true, "", "Media filtering disabled")
        }

        val shouldFilter = MediaNotificationDetector.shouldFilterMediaNotification(sbn, mediaFilterPreferences)
        if (shouldFilter) {
            val reason = MediaNotificationDetector.getMediaDetectionReason(sbn)
            Log.d(TAG, "Media notification filtered out (unified logic): $reason")
            InAppLogger.logFilter("Blocked media notification from ${sbn.packageName}: $reason (unified logic)")
            // Log all extras for debugging
            val extras = sbn.notification.extras
            Log.d(TAG, "[UnifiedFilter] Notification extras: $extras")
            return FilterResult(false, "", "Media notification filtered: $reason (unified logic)")
        }

        // If it was a media-style notification but passed due to exceptions/keywords, log for traceability
        if (MediaNotificationDetector.isMediaNotification(sbn)) {
            val reason = MediaNotificationDetector.getMediaDetectionReason(sbn)
            Log.d(TAG, "Media notification allowed (unified logic): $reason")
        }

        return FilterResult(true, "", "Not filtered by media rules (unified logic)")
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
                val hasNoAlerts = hasNoAlertsLegacy(notification)
                val importance = getPriorityLegacy(notification)
                @Suppress("DEPRECATION")
                val isLow = importance <= Notification.PRIORITY_LOW
                hasNoAlerts && isLow
            }
        } else {
            // For pre-Oreo devices, check notification flags and settings
            val hasNoAlerts = hasNoAlertsLegacy(notification)
            @Suppress("DEPRECATION")
            val lowPriority = getPriorityLegacy(notification) <= Notification.PRIORITY_LOW
            
            // Only consider truly silent if it has no alerts and low priority
            hasNoAlerts && lowPriority
        }
        
        // Check for foreground service notifications (ongoing services)
        val isForegroundService = (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        
        // Check notification priority level
        val priority = getPriorityLegacy(notification)
        @Suppress("DEPRECATION")
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
                    Log.d(TAG, "Silent notification details - Priority: ${getPriorityLegacy(notification)}, " +
                              "Sound: ${getSoundLegacy(notification)}, " +
                              "Defaults: ${getDefaultsLegacy(notification)}, " +
                              "Vibration: ${getVibrateLegacy(notification)?.isNotEmpty()}")
                }
            }
            
            return FilterResult(false, "", "Persistent/silent notification: $reason")
        }
        
        return FilterResult(true, "", "Not a persistent/silent notification or category not enabled")
    }

    @Suppress("DEPRECATION")
    private fun hasNoAlertsLegacy(notification: Notification): Boolean {
        return notification.sound == null &&
            (notification.vibrate == null || notification.vibrate.isEmpty()) &&
            (notification.defaults and (Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)) == 0
    }

    @Suppress("DEPRECATION")
    private fun getPriorityLegacy(notification: Notification): Int {
        return notification.priority
    }

    @Suppress("DEPRECATION")
    private fun getSoundLegacy(notification: Notification): Uri? {
        return notification.sound
    }

    @Suppress("DEPRECATION")
    private fun getVibrateLegacy(notification: Notification): LongArray? {
        return notification.vibrate
    }

    @Suppress("DEPRECATION")
    private fun getDefaultsLegacy(notification: Notification): Int {
        return notification.defaults
    }

    private fun isSpeakerphoneEnabled(audioManager: AudioManager): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val device = audioManager.communicationDevice
            device?.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn
        }
    }

    private fun setSpeakerphoneEnabled(audioManager: AudioManager, enabled: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (enabled) {
                val speaker = audioManager.availableCommunicationDevices.firstOrNull { device ->
                    device.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speaker != null) {
                    audioManager.setCommunicationDevice(speaker)
                }
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        }
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
            val now = System.currentTimeMillis()
            val maxRange = proximitySensor?.maximumRange ?: 5.0f
            waveEventCount += 1
            
            // Log proximity sensor values for debugging (but limit frequency to avoid spam)
            if (now - lastWaveDebugLogTime > 500) {
                lastWaveDebugLogTime = now
                Log.d(
                    TAG,
                    "Wave sensor event: value=$proximityValue cm, max=$maxRange cm, threshold=$waveThreshold cm, hold=${waveHoldDurationMs}ms, speaking=$isCurrentlySpeaking"
                )
            }
            
            // Handle different proximity sensor behaviors:
            // Some sensors return 0 when close, others return actual distance
            val isNear = if (proximityValue == 0f) {
                // Sensor returns 0 when object is very close (most common)
                true
            } else {
                // Sensor returns actual distance, check if closer than threshold
                // Use < instead of <= to avoid triggering when sensor is at max range
                // ADDITIONAL SAFETY: Only trigger if the value is significantly different from max range
                // This prevents false triggers on devices like Pixel 2 XL that read ~5cm when uncovered
                val significantChange = maxRange * 0.3f // Require at least 30% change from max
                val distanceFromMax = maxRange - proximityValue
                proximityValue < waveThreshold && distanceFromMax > significantChange
            }
            
            // Track sensor state changes for pocket mode
            val wasCovered = isSensorCurrentlyCovered
            isSensorCurrentlyCovered = isNear
            lastProximityValue = proximityValue
            lastProximityTimestamp = now
            lastProximityIsNear = isNear
            if (!isSensorCurrentlyCovered) {
                lastFarTimestamp = now
                pendingWaveTriggerRunnable?.let { sensorTimeoutHandler?.removeCallbacks(it) }
                if (pendingWaveTriggerRunnable != null) {
                    Log.i(TAG, "Wave hold cancelled - sensor uncovered")
                }
                pendingWaveTriggerRunnable = null
            }
            
            // Capture initial proximity state if pocket mode is enabled and not yet captured
            if (isPocketModeEnabled && !hasCapturedStartProximity && isCurrentlySpeaking) {
                wasSensorCoveredAtStart = isNear
                hasCapturedStartProximity = true
                Log.d(TAG, "Pocket mode: Initial proximity captured during speech - covered at start: $wasSensorCoveredAtStart")
                if (isNear && now - speechStartTimestamp <= WAVE_STARTUP_GRACE_MS) {
                    Log.d(TAG, "Pocket mode: Ignoring near reading during startup grace window")
                    return
                }
            }

            // Track sensor state transitions
            if (wasCovered && !isSensorCurrentlyCovered) {
                hasSensorBeenUncovered = true
                lastFarTimestamp = now
                Log.d(TAG, "Pocket mode: Sensor uncovered - has been uncovered: $hasSensorBeenUncovered")
            } else if (!wasCovered && isSensorCurrentlyCovered) {
                nearStableStartTimestamp = now
            }

            if (!isSensorCurrentlyCovered) {
                nearStableStartTimestamp = 0L
            }
            
            // Pocket mode logic: if enabled, check if sensor was already covered when readout started
            if (isNear) {
                if (nearStableStartTimestamp == 0L) {
                    nearStableStartTimestamp = now
                }
                if (waveHoldDurationMs <= MIN_WAVE_HOLD_DURATION_MS) {
                    if (isPocketModeEnabled && wasSensorCoveredAtStart && !hasSensorBeenUncovered) {
                        Log.i(TAG, "Wave ignored - pocket mode active (covered at start)")
                        return
                    }
                    Log.d(TAG, "Wave detected! Stopping TTS (instant). Proximity value: $proximityValue cm, threshold: $waveThreshold cm, pocket mode: $isPocketModeEnabled, was covered at start: $wasSensorCoveredAtStart, has been uncovered: $hasSensorBeenUncovered")
                    InAppLogger.logSystemEvent("Wave detected", "Proximity: ${proximityValue}cm, threshold: ${waveThreshold}cm")
                    stopSpeaking("wave")
                    return
                }
                if (pendingWaveTriggerRunnable == null) {
                    Log.i(TAG, "Wave hold scheduled - hold=${waveHoldDurationMs}ms")
                    pendingWaveTriggerRunnable = Runnable {
                        val runNow = System.currentTimeMillis()
                        if (!isCurrentlySpeaking || !isWaveToStopEnabled || !isSensorCurrentlyCovered) {
                            return@Runnable
                        }
                        if (isPocketModeEnabled && wasSensorCoveredAtStart && !hasSensorBeenUncovered) {
                            Log.i(TAG, "Wave ignored - pocket mode active (covered at start)")
                            return@Runnable
                        }
                        Log.d(TAG, "Wave detected! Stopping TTS. Proximity value: ${lastProximityValue} cm, threshold: $waveThreshold cm, pocket mode: $isPocketModeEnabled, was covered at start: $wasSensorCoveredAtStart, has been uncovered: $hasSensorBeenUncovered")
                        InAppLogger.logSystemEvent("Wave detected", "Proximity: ${lastProximityValue}cm, threshold: ${waveThreshold}cm")
                        stopSpeaking("wave")
                    }
                    sensorTimeoutHandler?.postDelayed(pendingWaveTriggerRunnable!!, waveHoldDurationMs)
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
    
    private fun stopSpeaking(triggerType: String = "unknown") {
        // Track interruption if currently speaking
        val wasSpeaking = isCurrentlySpeaking
        if (wasSpeaking) {
            try {
                StatisticsManager.getInstance(this).incrementReadoutsInterrupted()
            } catch (e: Exception) {
                Log.e(TAG, "Error tracking readout interruption", e)
            }
        }
        
        textToSpeech?.stop()
        isCurrentlySpeaking = false
        
        // Clear current notification variables
        currentSpeechText = ""
        currentAppName = ""
        currentOriginalAppName = ""
        currentTtsText = ""
        
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

        cancelSpeechSafetyTimeout("stopSpeaking:$triggerType")
        
        // Disable speakerphone if it was enabled
        try {
            if (isSpeakerphoneEnabled(audioManager)) {
                setSpeakerphoneEnabled(audioManager, false)
                InAppLogger.log("Service", "Speakerphone disabled after stopping speech")
            }
        } catch (e: Exception) {
            InAppLogger.logError("Service", "Failed to disable speakerphone: ${e.message}")
        }
        
        // Clean up media behavior effects
        cleanupMediaBehavior()
        
        // Reading notification is now integrated into foreground notification
        // hideReadingNotification()
        
        // CRITICAL: Stop foreground service when TTS is manually stopped
        // This ensures the foreground service notification is properly removed
        stopForegroundService()
        
        Log.d(TAG, "TTS stopped due to $triggerType")
        InAppLogger.logTTSEvent("TTS stopped by $triggerType", "User interrupted speech")
    }
    
    /**
     * Stop current TTS speech without clearing the queue.
     * Used internally by audio focus handlers.
     */
    private fun stopCurrentSpeech() {
        // Track interruption if currently speaking
        val wasSpeaking = isCurrentlySpeaking
        if (wasSpeaking) {
            try {
                StatisticsManager.getInstance(this).incrementReadoutsInterrupted()
            } catch (e: Exception) {
                Log.e(TAG, "Error tracking readout interruption", e)
            }
        }
        
        textToSpeech?.stop()
        isCurrentlySpeaking = false
        
        // Clear current notification variables
        currentSpeechText = ""
        currentAppName = ""
        currentOriginalAppName = ""
        currentTtsText = ""
        unregisterShakeListener()

        cancelSpeechSafetyTimeout("stopCurrentSpeech")
        
        // CRITICAL: Stop foreground service when TTS is interrupted
        stopForegroundService()
        
        // Clean up media behavior effects
        cleanupMediaBehavior()
        
        // Reading notification is now integrated into foreground notification
        // hideReadingNotification()
        
        Log.d(TAG, "Current TTS speech stopped by audio focus change")
        InAppLogger.logTTSEvent("TTS stopped by audio focus", "Focus loss interrupted speech")
    }

    private fun resumeQueueAfterSpeechEnd(reason: String) {
        if (notificationQueue.isEmpty()) {
            return
        }
        if (isCurrentlySpeaking) {
            Log.d(TAG, "Queue resume skipped ($reason) - still speaking")
            return
        }
        if (!MainActivity.isMasterSwitchEnabled(this)) {
            Log.d(TAG, "Queue resume skipped ($reason) - master switch disabled")
            InAppLogger.log("Service", "Queue resume skipped ($reason) - master switch disabled")
            return
        }
        Log.d(TAG, "Resuming queue after $reason (size=${notificationQueue.size})")
        InAppLogger.log("Service", "Resuming queue after $reason (size=${notificationQueue.size})")
        processNotificationQueue()
    }

    private fun scheduleSpeechSafetyTimeout(reason: String, speechText: String) {
        cancelSpeechSafetyTimeout("reschedule:$reason")
        if (speechText.isBlank()) {
            return
        }
        val wordCount = speechText.trim().split(Regex("\\s+")).size
        // Rough estimate: ~2.5 words/sec plus a buffer, clamped to sane bounds.
        val estimatedMs = ((wordCount / 2.5f) * 1000f).toLong()
        val timeoutMs = (estimatedMs + SAFETY_TIMEOUT_BUFFER_MS)
            .coerceAtLeast(SAFETY_TIMEOUT_MIN_MS)
            .coerceAtMost(SAFETY_TIMEOUT_MAX_MS)
        speechSafetyTimeoutRunnable = Runnable {
            if (!isCurrentlySpeaking) {
                return@Runnable
            }
            Log.w(TAG, "Speech safety timeout triggered after ${timeoutMs}ms ($reason)")
            InAppLogger.logWarning("Service", "Speech safety timeout triggered ($reason)")
            stopCurrentSpeech()
            resumeQueueAfterSpeechEnd("safety timeout")
        }
        delayHandler?.postDelayed(speechSafetyTimeoutRunnable!!, timeoutMs)
        Log.d(TAG, "Speech safety timeout scheduled for ${timeoutMs}ms ($reason)")
        InAppLogger.log("Service", "Speech safety timeout scheduled (${timeoutMs}ms, reason=$reason)")
    }

    private fun cancelSpeechSafetyTimeout(reason: String) {
        speechSafetyTimeoutRunnable?.let { runnable ->
            delayHandler?.removeCallbacks(runnable)
            speechSafetyTimeoutRunnable = null
            Log.d(TAG, "Speech safety timeout cancelled ($reason)")
        }
    }
    
    // Call this when settings change
    fun refreshAllSettings() {
        loadFilterSettings()
        refreshSettings() // This calls the shake settings refresh
    }

    /**
     * MEDIA BEHAVIOR HANDLING (ignore, pause, duck, silence)
     *
     * Respect the simplified focus-only path by default, but allow legacy behavior to be toggled
     * back on from Development Settings when deeper hacks are necessary.
     */
    private fun handleMediaBehavior(appName: String, _text: String, sbn: StatusBarNotification? = null): Boolean {
        val isMusicActive = audioManager.isMusicActive
        Log.d(
            TAG,
            "Media behavior check - isMusicActive: $isMusicActive, mediaBehavior: $mediaBehavior, " +
                "isCurrentlySpeaking: $isCurrentlySpeaking, legacy=$legacyDuckingEnabled"
        )

        if (!isMusicActive) return true

        if (isCurrentlySpeaking) {
            Log.d(TAG, "Media detected while TTS is speaking - assuming it is our own playback, continuing")
            InAppLogger.log("MediaBehavior", "Ignoring media detection because SpeakThat is already speaking")
            return true
        }

        if (sbn != null && MediaNotificationDetector.isMediaNotification(sbn)) {
            val reason = MediaNotificationDetector.getMediaDetectionReason(sbn)
            Log.d(TAG, "Notification detected as media control; skipping speech. Reason: $reason")
            InAppLogger.logFilter("Blocked media control notification from $appName: $reason")
            return false
        }

        return if (legacyDuckingEnabled) {
            handleLegacyMediaBehavior(appName)
        } else {
            handleModernMediaBehavior(appName)
        }
    }

    private fun handleModernMediaBehavior(appName: String): Boolean {
        return when (mediaBehavior) {
            "ignore" -> true
            "pause" -> {
                val granted = requestPauseAudioFocus()
                if (!granted) {
                    Log.w(TAG, "Audio focus denied for pause mode; escalating to media session fallback")
                    InAppLogger.log("MediaBehavior", "Audio focus denied for pause mode, trying session fallback for $appName")

                    val pausedViaSession = tryMediaSessionPause()
                    if (pausedViaSession) {
                        InAppLogger.log("MediaBehavior", "Media session pause succeeded for $appName (modern fallback)")
                        return true
                    }

                    if (isMediaFallbackDisabled(this)) {
                        Log.w(TAG, "Media fallback disabled - continuing without forcing media to stop")
                        InAppLogger.log("MediaBehavior", "Media fallback disabled; continuing for $appName")
                        return true
                    }

                    if (trySoftPauseFallback()) {
                        InAppLogger.log("MediaBehavior", "Soft pause fallback applied for $appName (modern path)")
                        return true
                    }

                    Log.w(TAG, "All pause fallbacks failed - reading without pausing media")
                    InAppLogger.log("MediaBehavior", "Modern pause fallbacks failed for $appName - reading anyway")
                } else {
                    InAppLogger.log("MediaBehavior", "Audio focus granted for pause mode (modern path)")
                }
                true
            }
            "duck" -> {
                val granted = requestSpeechAudioFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                if (!granted) {
                    Log.w(TAG, "Audio focus denied for duck mode; applying fallback strategy: $duckingFallbackStrategy")
                    InAppLogger.log("MediaBehavior", "Audio focus denied for duck mode; fallback: $duckingFallbackStrategy")

                    if (isMediaFallbackDisabled(this)) {
                        Log.w(TAG, "Media fallback disabled - continuing without ducking")
                        InAppLogger.log("MediaBehavior", "Media fallback disabled; continuing without ducking")
                        return true
                    }

                    when (duckingFallbackStrategy) {
                        "pause" -> {
                            // Try pausing media as a fallback
                            val paused = tryMediaSessionPause()
                            if (paused) {
                                InAppLogger.log("MediaBehavior", "Fallback pause applied for $appName (modern duck)")
                                return true
                            }

                            if (trySoftPauseFallback()) {
                                InAppLogger.log("MediaBehavior", "Soft pause fallback applied for $appName (modern duck)")
                                return true
                            }

                            Log.w(TAG, "Pause fallback failed - reading without pausing media")
                            InAppLogger.log("MediaBehavior", "Pause fallback failed for $appName - reading anyway")
                        }
                        else -> {
                            // Manual ducking fallback
                            val ducked = duckMediaVolume(duckingVolume)
                            if (ducked) {
                                InAppLogger.log("MediaBehavior", "Manual ducking fallback applied for $appName at $duckingVolume%")
                            } else {
                                Log.w(TAG, "Manual ducking fallback failed - reading without ducking")
                                InAppLogger.log("MediaBehavior", "Manual ducking fallback failed for $appName - reading anyway")
                            }
                        }
                    }
                } else {
                    InAppLogger.log("MediaBehavior", "Audio focus granted for duck mode (usage=${resolveTtsUsage()})")
                }
                true
            }
            "silence" -> {
                Log.d(TAG, "Media behavior: SILENCE - not speaking due to active media")
                InAppLogger.log("MediaBehavior", "Silenced notification from $appName due to active media")
                false
            }
            else -> true
        }
    }

    private fun requestPauseAudioFocus(): Boolean {
        val usage = android.media.AudioAttributes.USAGE_MEDIA
        val contentType = android.media.AudioAttributes.CONTENT_TYPE_SPEECH

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val attributes = android.media.AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(contentType)
                .build()

            audioFocusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { handleAudioFocusChange(it) }
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .build()

            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            Log.d(TAG, "Pause audio focus request result (usage=$usage): $result")
            result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                { focusChange -> handleAudioFocusChange(focusChange) },
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            Log.d(TAG, "Legacy pause audio focus request result: $result")
            result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun handleLegacyMediaBehavior(appName: String): Boolean {
        return when (mediaBehavior) {
            "ignore" -> true
            "pause" -> {
                if (tryMediaSessionPause()) {
                    InAppLogger.log("MediaBehavior", "Legacy pause via media session for $appName")
                    return true
                }

                val focusGranted = requestSpeechAudioFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                if (focusGranted) {
                    InAppLogger.log("MediaBehavior", "Legacy pause acquired transient focus for $appName")
                    return true
                }

                if (isMediaFallbackDisabled(this)) {
                    Log.w(TAG, "Legacy pause fallback disabled - continuing without stopping media")
                    InAppLogger.log("MediaBehavior", "Legacy pause fallback disabled; continuing for $appName")
                    return true
                }

                if (trySoftPauseFallback()) {
                    InAppLogger.log("MediaBehavior", "Soft pause fallback applied for $appName")
                    return true
                }

                Log.w(TAG, "Soft pause fallback failed - reading without audio focus")
                InAppLogger.log("MediaBehavior", "Soft pause fallback failed for $appName - reading anyway")
                true
            }
            "duck" -> {
                val focusGranted = requestSpeechAudioFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                if (focusGranted) {
                    InAppLogger.log("MediaBehavior", "Legacy duck granted focus for $appName")
                    return true
                }

                if (isMediaFallbackDisabled(this)) {
                    Log.w(TAG, "Legacy duck fallback disabled - continuing without ducking")
                    InAppLogger.log("MediaBehavior", "Legacy duck fallback disabled; continuing for $appName")
                    return true
                }

                val ducked = duckMediaVolume()
                if (ducked) {
                    InAppLogger.log("MediaBehavior", "Manual ducking applied for $appName")
                } else {
                    Log.w(TAG, "Manual ducking failed - reading without ducking")
                    InAppLogger.log("MediaBehavior", "Manual ducking failed for $appName - reading anyway")
                }
                true
            }
            "silence" -> {
                Log.d(TAG, "Media behavior: SILENCE (legacy) - not speaking due to active media")
                InAppLogger.log("MediaBehavior", "Legacy mode silenced notification from $appName due to active media")
                false
            }
            else -> true
        }
    }

    private fun tryMediaSessionPause(): Boolean {
        return try {
            val mediaSessionManager =
                getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return false
            val componentName = ComponentName(this, NotificationReaderService::class.java)
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            if (controllers.isEmpty()) {
                Log.d(TAG, "No active media sessions detected for legacy pause")
                return false
            }

            pausedMediaSessions.clear()
            var pausedAny = false
            controllers.forEach { controller ->
                val playbackState = controller.playbackState
                if (playbackState != null && playbackState.state == PlaybackState.STATE_PLAYING) {
                    try {
                        controller.transportControls.pause()
                        pausedMediaSessions.add(controller)
                        pausedAny = true
                        Log.d(TAG, "Paused media session for ${controller.packageName}")
                    } catch (controllerError: Exception) {
                        Log.e(TAG, "Failed to pause media session ${controller.packageName}", controllerError)
                    }
                }
            }

            pausedAny
        } catch (security: SecurityException) {
            Log.e(TAG, "Unable to access media sessions for legacy pause (missing permission?)", security)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during media session pause", e)
            false
        }
    }

    private fun resumeMediaSessions() {
        if (pausedMediaSessions.isEmpty()) {
            return
        }

        val iterator = pausedMediaSessions.iterator()
        while (iterator.hasNext()) {
            val controller = iterator.next()
            try {
                controller.transportControls.play()
                Log.d(TAG, "Resumed media session for ${controller.packageName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume media session ${controller.packageName}", e)
            }
        }

        pausedMediaSessions.clear()
        InAppLogger.log("MediaBehavior", "Resumed paused media sessions after TTS completion")
    }

    private fun trySoftPauseFallback(): Boolean {
        return try {
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (originalMusicVolume == -1) {
                originalMusicVolume = currentVolume
                Log.d(TAG, "Stored original music volume $currentVolume for soft pause fallback")
            }

            val softVolume = (maxVolume * 0.15f).toInt().coerceAtLeast(1)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, softVolume, 0)
            Log.d(TAG, "Soft pause fallback lowered STREAM_MUSIC from $currentVolume to $softVolume (max=$maxVolume)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Soft pause fallback failed", e)
            false
        }
    }

    
    private fun resolveTtsUsage(): Int {
        val prefs = voiceSettingsPrefs ?: getSharedPreferences("VoiceSettings", MODE_PRIVATE)
        val index = prefs?.getInt("audio_usage", DEFAULT_TTS_USAGE_INDEX) ?: DEFAULT_TTS_USAGE_INDEX
        return when (index) {
            0 -> android.media.AudioAttributes.USAGE_MEDIA
            1 -> android.media.AudioAttributes.USAGE_NOTIFICATION
            2 -> android.media.AudioAttributes.USAGE_ALARM
            3 -> android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
            4 -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            else -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        }
    }

    private fun requestSpeechAudioFocus(focusGain: Int): Boolean {
        val baseUsage = resolveTtsUsage()
        val (usage, contentType) = AccessibilityUtils.getEnhancedAudioAttributes(
            this,
            baseUsage,
            android.media.AudioAttributes.CONTENT_TYPE_SPEECH
        )

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val attributes = android.media.AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(contentType)
                .build()

            audioFocusRequest = android.media.AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { handleAudioFocusChange(it) }
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .build()

            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            Log.d(TAG, "Audio focus request result (gain=$focusGain, usage=$usage): $result")
            result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            val legacyGain = if (focusGain == android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) {
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            } else {
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            }
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                { focusChange -> handleAudioFocusChange(focusChange) },
                android.media.AudioManager.STREAM_MUSIC,
                legacyGain
            )
            Log.d(TAG, "Legacy audio focus request result: $result")
            result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
    
    /**
     * Attempt direct media session control to pause active media playback.
     * This is the strongest approach for pausing media, bypassing audio focus limitations.
     * 
     * ACCESSIBILITY ENHANCEMENT: When accessibility permission is granted, this method
     * also attempts to send direct media control intents for more reliable pausing.
     */
    
    private fun requestAudioFocusForVoiceCall(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Use AudioFocusRequest for API 26+ with VOICE_COMMUNICATION usage
            audioFocusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .build()
            
            val result = audioManager.requestAudioFocus(audioFocusRequest!!)
            Log.d(TAG, "Audio focus request result for VOICE_COMMUNICATION: $result")
            InAppLogger.log("Service", "Audio focus request result for VOICE_COMMUNICATION: $result")
            
            when (result) {
                android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    Log.d(TAG, "Audio focus GRANTED for VOICE_COMMUNICATION")
                    InAppLogger.log("Service", "Audio focus GRANTED for VOICE_COMMUNICATION")
                    true
                }
                android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                    Log.d(TAG, "Audio focus FAILED for VOICE_COMMUNICATION")
                    InAppLogger.log("Service", "Audio focus FAILED for VOICE_COMMUNICATION")
                    false
                }
                android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    Log.d(TAG, "Audio focus DELAYED for VOICE_COMMUNICATION")
                    InAppLogger.log("Service", "Audio focus DELAYED for VOICE_COMMUNICATION")
                    true // Treat delayed as granted
                }
                else -> {
                    Log.d(TAG, "Audio focus UNKNOWN result for VOICE_COMMUNICATION: $result")
                    InAppLogger.log("Service", "Audio focus UNKNOWN result for VOICE_COMMUNICATION: $result")
                    false
                }
            }
        } else {
            // Use deprecated method for older versions with VOICE_CALL stream
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                { focusChange -> handleAudioFocusChange(focusChange) },
                android.media.AudioManager.STREAM_VOICE_CALL,
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
             Log.d(TAG, "Audio focus request result for STREAM_VOICE_CALL (legacy): $result")
            InAppLogger.log("Service", "Audio focus request result for STREAM_VOICE_CALL (legacy): $result")
            
            when (result) {
                android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    Log.d(TAG, "Audio focus GRANTED for STREAM_VOICE_CALL")
                    InAppLogger.log("Service", "Audio focus GRANTED for STREAM_VOICE_CALL")
                    true
                }
                android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                    Log.d(TAG, "Audio focus FAILED for STREAM_VOICE_CALL")
                    InAppLogger.log("Service", "Audio focus FAILED for STREAM_VOICE_CALL")
                    false
                }
                else -> {
                    Log.d(TAG, "Audio focus UNKNOWN result for STREAM_VOICE_CALL: $result")
                    InAppLogger.log("Service", "Audio focus UNKNOWN result for STREAM_VOICE_CALL: $result")
                    false
                }
            }
        }
    }
    
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained - media should resume")
                InAppLogger.log("MediaBehavior", "Audio focus gained - media should resume")
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost permanently")
                InAppLogger.log("MediaBehavior", "Audio focus lost permanently")
                // Stop TTS if it's currently speaking since we lost focus permanently
                if (isCurrentlySpeaking) {
                    stopCurrentSpeech()
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost temporarily")
                InAppLogger.log("MediaBehavior", "Audio focus lost temporarily")
                // Don't stop TTS for temporary loss - let it continue speaking
                // The TTS should maintain its volume and continue
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost temporarily - can duck")
                InAppLogger.log("MediaBehavior", "Audio focus lost temporarily - can duck")
                // This is the critical case - the system wants to duck our TTS
                // We need to prevent this from affecting TTS volume
                if (isCurrentlySpeaking) {
                    // Re-apply TTS volume settings to ensure it's not ducked
                    reapplyTtsVolumeSettings()
                }
            }
        }
    }
    
    /**
     * Re-apply TTS volume settings to ensure TTS volume is maintained
     * even when audio focus changes occur.
     */
    private fun reapplyTtsVolumeSettings() {
        try {
            // Get current TTS settings
            val voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
            val ttsVolume = voicePrefs.getFloat("tts_volume", 1.0f)
            val ttsUsageIndex = voicePrefs.getInt("audio_usage", 4) // Default to ASSISTANT index
            val speakerphoneEnabled = voicePrefs.getBoolean("speakerphone_enabled", false)
            
            val ttsUsage = when (ttsUsageIndex) {
                0 -> android.media.AudioAttributes.USAGE_MEDIA
                1 -> android.media.AudioAttributes.USAGE_NOTIFICATION
                2 -> android.media.AudioAttributes.USAGE_ALARM
                3 -> android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
                4 -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                else -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            }
            
            // Re-apply audio attributes to TTS instance
            // CRITICAL: Always use CONTENT_TYPE_SPEECH to prevent TTS from being ducked
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(ttsUsage)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            textToSpeech?.setAudioAttributes(audioAttributes)
            
            // Also ensure that the TTS volume is set correctly in the volume bundle
            // This helps prevent the system from overriding our volume settings
            val volumeParams = VoiceSettingsActivity.createVolumeBundle(ttsVolume, ttsUsage, speakerphoneEnabled)
            
            // Log the current TTS volume for debugging (throttled to reduce noise)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTtsVolumeLogTime > TTS_VOLUME_LOG_THROTTLE_MS) {
                Log.d(TAG, "Re-applied TTS volume settings - Volume: ${ttsVolume * 100}%, Usage: $ttsUsage")
                InAppLogger.log("MediaBehavior", "Re-applied TTS volume settings to prevent ducking - Volume: ${ttsVolume * 100}%")
                lastTtsVolumeLogTime = currentTime
            }
            
            // Additional check: ensure that the TTS volume is not being reduced by the system
            // This is a defensive measure to prevent the bug where TTS volume gets reduced
            if (ttsVolume < 0.5f) {
                Log.w(TAG, "TTS volume is low (${ttsVolume * 100}%) - this might indicate volume reduction")
                InAppLogger.log("MediaBehavior", "Warning: TTS volume is low (${ttsVolume * 100}%) - checking for volume reduction")
            }
            
            // Ensure that the TTS volume is maintained by checking if we need to re-request audio focus
            // This helps prevent the system from ducking our TTS when the app goes to background
            // BUGFIX: Don't request audio focus if media behavior is set to "ignore"
            if (audioFocusRequest == null && mediaBehavior != "ignore") {
                Log.d(TAG, "No audio focus request active - requesting new focus to maintain TTS volume")
                InAppLogger.log("MediaBehavior", "Requesting new audio focus to maintain TTS volume")
                requestSpeechAudioFocus(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            } else if (audioFocusRequest == null && mediaBehavior == "ignore") {
                Log.d(TAG, "Skipping audio focus request - media behavior is set to ignore")
                InAppLogger.log("MediaBehavior", "Skipping audio focus (media behavior: ignore)")
            }
            
            // Additional safety measure: ensure that the TTS volume is not being reduced by the system
            // This is a defensive measure to prevent the bug where TTS volume gets reduced when switching apps
            if (isCurrentlySpeaking && currentTime - lastTtsVolumeLogTime > TTS_VOLUME_LOG_THROTTLE_MS) {
                Log.d(TAG, "TTS is currently speaking - ensuring volume is maintained")
                InAppLogger.log("MediaBehavior", "TTS is currently speaking - ensuring volume is maintained")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to re-apply TTS volume settings", e)
            InAppLogger.logError("MediaBehavior", "Failed to re-apply TTS volume settings: ${e.message}")
        }
    }
    
    /**
     * Monitor and maintain TTS volume when app focus changes occur.
     * This helps prevent TTS volume from being reduced when switching apps.
     */
    private fun monitorTtsVolumeDuringFocusChanges() {
        // Set up a periodic check to ensure TTS volume is maintained
        // This is a defensive approach to handle cases where audio focus changes
        // might not be properly detected
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isCurrentlySpeaking) {
                // Check if we need to re-apply TTS volume settings
                // This is a safety mechanism to ensure TTS volume is maintained
                reapplyTtsVolumeSettings()
                
                // Also ensure that our audio focus request is still valid
                // This helps prevent the system from ducking our TTS
                // BUGFIX: Don't request audio focus if media behavior is set to "ignore"
                if (audioFocusRequest == null && mediaBehavior != "ignore") {
                    Log.d(TAG, "Audio focus request lost during TTS - requesting new focus")
                    InAppLogger.log("MediaBehavior", "Audio focus request lost during TTS - requesting new focus")
                    requestSpeechAudioFocus(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                } else if (audioFocusRequest == null && mediaBehavior == "ignore") {
                    Log.d(TAG, "Skipping audio focus request during TTS - media behavior is set to ignore")
                }
                
                // Schedule the next check
                monitorTtsVolumeDuringFocusChanges()
            }
        }, 2000) // Check every 2 seconds while TTS is speaking
    }
    
    /**
     * Ensure TTS volume is maintained when the app goes to background.
     * This is called when the app loses focus to prevent TTS volume reduction.
     */
    private fun ensureTtsVolumeOnAppBackground() {
        if (isCurrentlySpeaking) {
            Log.d(TAG, "App going to background - ensuring TTS volume is maintained")
            InAppLogger.log("MediaBehavior", "App going to background - ensuring TTS volume is maintained")
            
            // Re-apply TTS volume settings to ensure they're not affected by focus change
            reapplyTtsVolumeSettings()
            
            // Also ensure that our audio focus request is still valid
            // This helps prevent the system from ducking our TTS
            if (audioFocusRequest != null) {
                Log.d(TAG, "Audio focus request is still active - TTS should maintain volume")
                InAppLogger.log("MediaBehavior", "Audio focus request still active - TTS volume should be maintained")
            }
            
            // Request a new audio focus if needed to ensure TTS volume is maintained
            // This helps prevent the system from ducking our TTS when the app goes to background
            // BUGFIX: Don't request audio focus if media behavior is set to "ignore"
            if (audioFocusRequest == null && mediaBehavior != "ignore") {
                Log.d(TAG, "No audio focus request active - requesting new focus to maintain TTS volume")
                InAppLogger.log("MediaBehavior", "Requesting new audio focus to maintain TTS volume")
                requestSpeechAudioFocus(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            } else if (audioFocusRequest == null && mediaBehavior == "ignore") {
                Log.d(TAG, "Skipping audio focus request on app background - media behavior is set to ignore")
                InAppLogger.log("MediaBehavior", "Skipping audio focus on app background (media behavior: ignore)")
            }
        }
    }
    
    private fun duckMediaVolume(targetPercentOverride: Int = -1): Boolean {
        try {
            Log.d(TAG, "=== DUCKING DEBUG: Manual ducking method called ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Manual ducking method called ===")
            
            // Get current media volume
            val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            
            // Get TTS settings for diagnostics
            val voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
            val ttsUsageIndex = voicePrefs.getInt("audio_usage", 4) // Default to ASSISTANT index
            val duckingVolume = if (targetPercentOverride in 0..100) {
                targetPercentOverride
            } else {
                voicePrefs.getInt("ducking_volume", 50) // Default to 50%
            }
            
            Log.d(TAG, "=== DUCKING DEBUG: Manual ducking settings - Current volume: $currentVolume, Max volume: $maxVolume, Ducking volume: $duckingVolume%, TTS usage index: $ttsUsageIndex ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Manual ducking settings - Current: $currentVolume, Max: $maxVolume, Ducking: $duckingVolume%, TTS usage index: $ttsUsageIndex ===")
            val ttsUsage = when (ttsUsageIndex) {
                0 -> android.media.AudioAttributes.USAGE_MEDIA
                1 -> android.media.AudioAttributes.USAGE_NOTIFICATION
                2 -> android.media.AudioAttributes.USAGE_ALARM
                3 -> android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
                4 -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                else -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            }
            
            // Enhanced diagnostics for ducking issues
            Log.d(TAG, "=== DUCKING DEBUG: Manual ducking diagnostics - TTS usage index: $ttsUsageIndex, TTS usage constant: $ttsUsage ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Manual ducking diagnostics - TTS usage index: $ttsUsageIndex, TTS usage constant: $ttsUsage, Media volume: $currentVolume/$maxVolume ===")
            // Store original volume for restoration
            if (originalMusicVolume == -1) {
                originalMusicVolume = currentVolume
                Log.d(TAG, "=== DUCKING DEBUG: Stored original music volume: $currentVolume ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Stored original music volume: $currentVolume ===")
            } else {
                Log.d(TAG, "=== DUCKING DEBUG: Original music volume already stored: $originalMusicVolume ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Original music volume already stored: $originalMusicVolume ===")
            }
            
            // Calculate ducked volume
            val duckedVolume = (maxVolume * duckingVolume / 100).coerceAtLeast(1)
            Log.d(TAG, "=== DUCKING DEBUG: Calculated ducked volume: $duckedVolume (from max: $maxVolume, ducking: $duckingVolume%) ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Calculated ducked volume: $duckedVolume (from max: $maxVolume, ducking: $duckingVolume%) ===")
            
            // TRICK 1: Try stream-specific ducking approach
            Log.d(TAG, "=== DUCKING DEBUG: Trying stream-specific ducking approach ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Trying stream-specific ducking approach ===")
            
            val streamSpecificSuccess = tryStreamSpecificDucking(duckedVolume, maxVolume)
            if (streamSpecificSuccess) {
                Log.d(TAG, "=== DUCKING DEBUG: Stream-specific ducking SUCCESSFUL - TTS volume should be preserved ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Stream-specific ducking SUCCESSFUL - TTS volume preserved ===")
                return true
            } else {
                Log.d(TAG, "=== DUCKING DEBUG: Stream-specific ducking FAILED - trying VolumeShaper ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Stream-specific ducking FAILED - trying VolumeShaper ===")
            }
            
            // TRICK 2: Try VolumeShaper for smooth transitions on Android 8.0+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Log.d(TAG, "=== DUCKING DEBUG: Trying VolumeShaper ducking (Android 8.0+) ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Trying VolumeShaper ducking (Android 8.0+) ===")
                
                val volumeShaperResult = tryVolumeShaperDuck(currentVolume.toFloat(), duckedVolume.toFloat(), maxVolume)
                if (volumeShaperResult) {
                    Log.d(TAG, "=== DUCKING DEBUG: VolumeShaper ducking SUCCESSFUL - applied from $currentVolume to $duckedVolume ===")
                    InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: VolumeShaper ducking SUCCESSFUL - applied from $currentVolume to $duckedVolume ===")
                    
                    // TRICK 3: Apply TTS volume compensation if TTS might be affected
                    Log.d(TAG, "=== DUCKING DEBUG: Applying TTS volume compensation after VolumeShaper ===")
                    InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Applying TTS volume compensation after VolumeShaper ===")
                    applyTtsVolumeCompensation(ttsUsage)
                    return true
                } else {
                    Log.d(TAG, "=== DUCKING DEBUG: VolumeShaper ducking FAILED - falling back to manual volume control ===")
                    InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: VolumeShaper ducking FAILED - falling back to manual volume control ===")
                }
            } else {
                Log.d(TAG, "=== DUCKING DEBUG: VolumeShaper not available on Android < 8.0 ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: VolumeShaper not available on Android < 8.0 ===")
            }
            
            // Fallback to abrupt volume change
            Log.d(TAG, "=== DUCKING DEBUG: Using fallback abrupt volume change ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Using fallback abrupt volume change ===")
            
            Log.d(TAG, "=== DUCKING DEBUG: Setting stream volume from $currentVolume to $duckedVolume ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Setting stream volume from $currentVolume to $duckedVolume ===")
            
            audioManager.setStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                duckedVolume,
                0
            )
            
            Log.d(TAG, "=== DUCKING DEBUG: Media volume ducked from $currentVolume to $duckedVolume (max: $maxVolume) ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Media volume ducked from $currentVolume to $duckedVolume (max: $maxVolume) ===")
            
            // TRICK 3: Apply TTS volume compensation after manual ducking
            Log.d(TAG, "=== DUCKING DEBUG: Applying TTS volume compensation after manual ducking ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Applying TTS volume compensation after manual ducking ===")
            applyTtsVolumeCompensation(ttsUsage)
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to duck media volume", e)
            return true // Proceed anyway
        }
    }
    
    /**
     * TRICK 1: Stream-specific ducking approach
     * Instead of ducking the entire STREAM_MUSIC, try to target only specific streams
     * that media apps typically use, while preserving TTS volume.
     */
    private fun tryStreamSpecificDucking(duckedVolume: Int, maxVolume: Int): Boolean {
        return try {
            // Check if we can identify specific streams that media apps are using
            // This is a more targeted approach that might preserve TTS volume
            
            // Get current volumes for different streams
            val musicVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val notificationVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION)
            val alarmVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
            
            // Store original volumes for restoration
            if (originalMusicVolume == -1) {
                originalMusicVolume = musicVolume
            }
            
            // TRICK: Try ducking only if the music stream is actually being used for media
            // and if TTS is not using the same stream
            val voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
            val ttsUsageIndex = voicePrefs.getInt("audio_usage", 4)
            val ttsUsage = when (ttsUsageIndex) {
                0 -> android.media.AudioAttributes.USAGE_MEDIA
                1 -> android.media.AudioAttributes.USAGE_NOTIFICATION
                2 -> android.media.AudioAttributes.USAGE_ALARM
                3 -> android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
                4 -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                else -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            }
            
            // If TTS is using NOTIFICATION or ALARM stream, we can safely duck MUSIC stream
            if (ttsUsage == android.media.AudioAttributes.USAGE_NOTIFICATION || 
                ttsUsage == android.media.AudioAttributes.USAGE_ALARM) {
                
                Log.d(TAG, "TTS using isolated stream ($ttsUsage) - safe to duck MUSIC stream")
                InAppLogger.log("MediaBehavior", "Stream-specific ducking: TTS isolated, ducking MUSIC stream only")
                
                // Duck only the music stream
                audioManager.setStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC,
                    duckedVolume,
                    0
                )
                
                return true
            }
            
            // If TTS is using MEDIA stream, try a different approach
            if (ttsUsage == android.media.AudioAttributes.USAGE_MEDIA) {
                Log.d(TAG, "TTS using MEDIA stream - trying alternative ducking approach")
                InAppLogger.log("MediaBehavior", "Stream-specific ducking: TTS using MEDIA stream, trying alternative approach")
                
                // TRICK: Try ducking with a higher volume to minimize TTS impact
                val lessAggressiveDuckVolume = (maxVolume * (duckingVolume + 20) / 100).coerceAtMost(maxVolume)
                audioManager.setStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC,
                    lessAggressiveDuckVolume,
                    0
                )
                
                return true
            }
            
            // Default fallback
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "Stream-specific ducking failed", e)
            InAppLogger.logError("MediaBehavior", "Stream-specific ducking failed: ${e.message}")
            false
        }
    }
    
    /**
     * TRICK 3: TTS Volume Compensation
     * Actively compensate for TTS volume reduction by boosting it when ducking is active.
     */
    private fun applyTtsVolumeCompensation(ttsUsage: Int) {
        try {
            Log.d(TAG, "=== DUCKING DEBUG: TTS volume compensation called for usage: $ttsUsage ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: TTS volume compensation called for usage: $ttsUsage ===")
            
            // Only apply compensation if TTS might be affected by ducking
            if (ttsUsage == android.media.AudioAttributes.USAGE_MEDIA || 
                ttsUsage == android.media.AudioAttributes.USAGE_NOTIFICATION ||
                ttsUsage == android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) {
                
                Log.d(TAG, "=== DUCKING DEBUG: TTS usage $ttsUsage qualifies for volume compensation ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: TTS usage $ttsUsage qualifies for volume compensation ===")
                
                // Get current TTS settings
                val voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
                val currentTtsVolume = voicePrefs.getFloat("tts_volume", 1.0f)
                val speakerphoneEnabled = voicePrefs.getBoolean("speakerphone_enabled", false)
                val duckingVolume = voicePrefs.getInt("ducking_volume", 50)
                
                Log.d(TAG, "=== DUCKING DEBUG: TTS compensation settings - Current volume: ${currentTtsVolume * 100}%, Ducking volume: $duckingVolume%, Speakerphone: $speakerphoneEnabled ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: TTS compensation settings - Current: ${currentTtsVolume * 100}%, Ducking: $duckingVolume%, Speakerphone: $speakerphoneEnabled ===")
                
                // Calculate compensation factor based on ducking level
                val compensationFactor = when {
                    duckingVolume <= 20 -> 1.5f  // Heavy ducking - more compensation
                    duckingVolume <= 40 -> 1.3f  // Medium ducking - moderate compensation
                    else -> 1.2f                 // Light ducking - slight compensation
                }
                
                val compensatedVolume = (currentTtsVolume * compensationFactor).coerceAtMost(1.0f)
                
                Log.d(TAG, "=== DUCKING DEBUG: TTS compensation calculation - Factor: $compensationFactor, Compensated volume: ${compensatedVolume * 100}% ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: TTS compensation calculation - Factor: $compensationFactor, Compensated: ${compensatedVolume * 100}% ===")
                
                // Apply compensated volume to TTS
                Log.d(TAG, "=== DUCKING DEBUG: Creating volume bundle with compensated volume ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Creating volume bundle with compensated volume ===")
                
                val volumeParams = VoiceSettingsActivity.createVolumeBundle(compensatedVolume, ttsUsage, speakerphoneEnabled)
                
                // If TTS is currently speaking, re-apply the compensated volume
                if (isCurrentlySpeaking && textToSpeech != null) {
                    Log.d(TAG, "=== DUCKING DEBUG: TTS is currently speaking - re-applying compensated volume ===")
                    InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: TTS is currently speaking - re-applying compensated volume ===")
                    
                    // Re-apply audio attributes with compensated volume
                    val audioAttributes = android.media.AudioAttributes.Builder()
                        .setUsage(ttsUsage)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    
                    textToSpeech?.setAudioAttributes(audioAttributes)
                    
                    Log.d(TAG, "=== DUCKING DEBUG: TTS volume compensated from ${currentTtsVolume * 100}% to ${compensatedVolume * 100}% ===")
                    InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: TTS volume compensated: ${currentTtsVolume * 100}% -> ${compensatedVolume * 100}% ===")
                } else {
                    Log.d(TAG, "=== DUCKING DEBUG: TTS not currently speaking - compensation will be applied when TTS starts ===")
                    InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: TTS not currently speaking - compensation will be applied when TTS starts ===")
                }
                
                // Store compensation info for restoration later
                ttsVolumeCompensationActive = true
                originalTtsVolume = currentTtsVolume
                compensatedTtsVolume = compensatedVolume
                
                Log.d(TAG, "=== DUCKING DEBUG: Stored compensation state - Active: $ttsVolumeCompensationActive, Original: ${originalTtsVolume * 100}%, Compensated: ${compensatedTtsVolume * 100}% ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Stored compensation state - Active: $ttsVolumeCompensationActive, Original: ${originalTtsVolume * 100}%, Compensated: ${compensatedTtsVolume * 100}% ===")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply TTS volume compensation", e)
            InAppLogger.logError("MediaBehavior", "TTS volume compensation failed: ${e.message}")
        }
    }
    
    /**
     * Restore TTS volume compensation when ducking ends.
     */
    private fun restoreTtsVolumeCompensation() {
        if (ttsVolumeCompensationActive && originalTtsVolume != -1f) {
            try {
                Log.d(TAG, "Restoring TTS volume compensation: ${compensatedTtsVolume * 100}% -> ${originalTtsVolume * 100}%")
                InAppLogger.log("MediaBehavior", "Restoring TTS volume compensation")
                
                // Get current TTS settings
                val voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
                val ttsUsageIndex = voicePrefs.getInt("audio_usage", 4)
                val speakerphoneEnabled = voicePrefs.getBoolean("speakerphone_enabled", false)
                
                val ttsUsage = when (ttsUsageIndex) {
                    0 -> android.media.AudioAttributes.USAGE_MEDIA
                    1 -> android.media.AudioAttributes.USAGE_NOTIFICATION
                    2 -> android.media.AudioAttributes.USAGE_ALARM
                    3 -> android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
                    4 -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                    else -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                }
                
                // Restore original TTS volume
                val volumeParams = VoiceSettingsActivity.createVolumeBundle(originalTtsVolume, ttsUsage, speakerphoneEnabled)
                
                // If TTS is currently speaking, re-apply the original volume
                if (isCurrentlySpeaking && textToSpeech != null) {
                    val audioAttributes = android.media.AudioAttributes.Builder()
                        .setUsage(ttsUsage)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    
                    textToSpeech?.setAudioAttributes(audioAttributes)
                }
                
                // Reset compensation state
                ttsVolumeCompensationActive = false
                originalTtsVolume = -1f
                compensatedTtsVolume = -1f
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore TTS volume compensation", e)
                InAppLogger.logError("MediaBehavior", "TTS volume compensation restoration failed: ${e.message}")
            }
        }
    }
    
    /**
     * TRICK 4: Alternative audio focus strategy for ducking
     * Try different audio focus approaches that might work better for ducking on some devices.
     */
    private fun tryAlternativeDuckingFocus(): Boolean {
        // Only try on Android 8.0+ where the APIs are more reliable
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return false
        }
        
        try {
            // Get TTS audio attributes from voice settings
            val voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
            val ttsUsageIndex = voicePrefs.getInt("audio_usage", 4)
            
            val fallbackTtsUsage = when (ttsUsageIndex) {
                0 -> android.media.AudioAttributes.USAGE_MEDIA
                1 -> android.media.AudioAttributes.USAGE_NOTIFICATION
                2 -> android.media.AudioAttributes.USAGE_ALARM
                3 -> android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
                4 -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                else -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            }
            
            // ACCESSIBILITY ENHANCEMENT: Use enhanced audio attributes when accessibility permission is available
            val (ttsUsage, ttsContent) = AccessibilityUtils.getEnhancedAudioAttributes(
                this, 
                fallbackTtsUsage, 
                android.media.AudioAttributes.CONTENT_TYPE_SPEECH
            )
            
            Log.d(TAG, "Trying alternative ducking focus with TTS usage: $ttsUsage")
            InAppLogger.log("MediaBehavior", "Trying alternative ducking focus strategy")
            
            if (AccessibilityUtils.shouldUseEnhancedAudioControl(this)) {
                Log.d(TAG, "Using accessibility-enhanced alternative ducking focus")
                InAppLogger.log("MediaBehavior", "Accessibility-enhanced alternative ducking focus")
            }
            
            // TRICK: Try using USAGE_ALARM for TTS during ducking - some devices handle this better
            // But prioritize accessibility-enhanced usage if available
            val alternativeUsage = if (AccessibilityUtils.shouldUseEnhancedAudioControl(this)) {
                // When accessibility is available, use the enhanced usage directly
                ttsUsage
            } else if (ttsUsage == android.media.AudioAttributes.USAGE_MEDIA) {
                android.media.AudioAttributes.USAGE_ALARM
            } else {
                ttsUsage
            }
            
            // Create audio attributes for alternative ducking focus
            val alternativeAudioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(alternativeUsage)
                .setContentType(ttsContent)
                .build()
            
            // ACCESSIBILITY ENHANCEMENT: Use enhanced audio focus flags when accessibility permission is available
            val focusFlags = AccessibilityUtils.getEnhancedAudioFocusFlags(
                this,
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
            
            // Create focus request for alternative ducking
            val alternativeFocusRequest = android.media.AudioFocusRequest.Builder(focusFlags)
                .setAudioAttributes(alternativeAudioAttributes)
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAlternativeDuckingFocusChange(focusChange)
                }
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .build()
            
            // Request audio focus
            val result = audioManager.requestAudioFocus(alternativeFocusRequest)
            
            val resultText = when (result) {
                android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "GRANTED"
                android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "FAILED"
                android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "DELAYED"
                else -> "UNKNOWN($result)"
            }
            
            Log.d(TAG, "Alternative ducking focus result: $resultText ($result)")
            InAppLogger.log("MediaBehavior", "Alternative ducking focus result: $resultText")
            
            if (result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // Store the alternative focus request for cleanup
                enhancedDuckingFocusRequest = alternativeFocusRequest
                isUsingEnhancedDucking = true
                
                Log.d(TAG, "Alternative ducking focus GRANTED - system should duck other media")
                InAppLogger.log("MediaBehavior", "Alternative ducking focus GRANTED - system handling media ducking")
                return true
            } else {
                Log.d(TAG, "Alternative ducking focus $resultText - falling back to other methods")
                InAppLogger.log("MediaBehavior", "Alternative ducking focus $resultText - trying other methods")
                return false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Alternative ducking focus failed with exception", e)
            InAppLogger.logError("MediaBehavior", "Alternative ducking focus exception: ${e.message}")
            return false
        }
    }
    
    /**
     * Handle audio focus changes during alternative ducking.
     */
    private fun handleAlternativeDuckingFocusChange(focusChange: Int) {
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Alternative ducking: Audio focus gained")
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Alternative ducking: Audio focus lost permanently")
                InAppLogger.log("MediaBehavior", "Alternative ducking focus lost - cleaning up")
                cleanupEnhancedDucking()
                if (isCurrentlySpeaking) {
                    stopCurrentSpeech()
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Alternative ducking: Audio focus lost temporarily")
                if (isCurrentlySpeaking) {
                    textToSpeech?.stop()
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Alternative ducking: Can duck - re-applying TTS volume settings")
                InAppLogger.log("MediaBehavior", "Alternative ducking: Can duck - re-applying TTS volume settings")
                if (isCurrentlySpeaking) {
                    reapplyTtsVolumeSettings()
                }
            }
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
        
        // TRICK: Also restore TTS volume compensation when media volume is restored
        restoreTtsVolumeCompensation()
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
    
    // TTS volume compensation variables for ducking mode
    private var ttsVolumeCompensationActive = false
    private var originalTtsVolume = -1f
    private var compensatedTtsVolume = -1f
    

    
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
            
            // Convert index to actual usage constant (matching VoiceSettingsActivity)
            val fallbackTtsUsage = when (ttsUsageIndex) {
                0 -> android.media.AudioAttributes.USAGE_MEDIA
                1 -> android.media.AudioAttributes.USAGE_NOTIFICATION
                2 -> android.media.AudioAttributes.USAGE_ALARM
                3 -> android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
                4 -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                else -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE // Safe fallback
            }
            
            // ACCESSIBILITY ENHANCEMENT: Use enhanced audio attributes when accessibility permission is available
            val (ttsUsage, ttsContent) = AccessibilityUtils.getEnhancedAudioAttributes(
                this, 
                fallbackTtsUsage, 
                android.media.AudioAttributes.CONTENT_TYPE_SPEECH
            )
            
            if (AccessibilityUtils.shouldUseEnhancedAudioControl(this)) {
                Log.d(TAG, "=== DUCKING DEBUG: Using accessibility-enhanced audio attributes - Usage: $ttsUsage, Content: $ttsContent ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Accessibility-enhanced ducking - Usage: $ttsUsage ===")
            } else {
                Log.d(TAG, "=== DUCKING DEBUG: Using standard audio attributes - Usage: $ttsUsage, Content: $ttsContent ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Standard ducking - Usage: $ttsUsage ===")
            }
            
            Log.d(TAG, "=== DUCKING DEBUG: Enhanced ducking - TTS usage index: $ttsUsageIndex -> usage constant: $ttsUsage ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Enhanced ducking - TTS usage: $ttsUsage (index: $ttsUsageIndex) ===")
            
            // Create audio attributes for our TTS
            val ttsAudioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(ttsUsage)
                .setContentType(ttsContent)
                .build()
            
            Log.d(TAG, "=== DUCKING DEBUG: Created TTS audio attributes - Usage: $ttsUsage, Content: $ttsContent ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Created TTS audio attributes - Usage: $ttsUsage, Content: $ttsContent ===")
            
            // Create focus request for enhanced ducking
            Log.d(TAG, "=== DUCKING DEBUG: Creating enhanced ducking focus request ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Creating enhanced ducking focus request ===")
            
            // ACCESSIBILITY ENHANCEMENT: Use enhanced audio focus flags when accessibility permission is available
            val focusFlags = AccessibilityUtils.getEnhancedAudioFocusFlags(
                this,
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
            
            if (AccessibilityUtils.shouldUseEnhancedAudioControl(this)) {
                Log.d(TAG, "=== DUCKING DEBUG: Using accessibility-enhanced focus flags: $focusFlags ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Accessibility-enhanced focus flags: $focusFlags ===")
            }
            
            enhancedDuckingFocusRequest = android.media.AudioFocusRequest.Builder(focusFlags)
                .setAudioAttributes(ttsAudioAttributes)
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d(TAG, "=== DUCKING DEBUG: Enhanced ducking focus change listener called with: $focusChange ===")
                    InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Enhanced ducking focus change: $focusChange ===")
                    handleEnhancedDuckingFocusChange(focusChange)
                }
                .setAcceptsDelayedFocusGain(false) // We want immediate response for notifications
                .setWillPauseWhenDucked(false) // We don't want our TTS to be ducked
                .build()
            
            Log.d(TAG, "=== DUCKING DEBUG: Enhanced ducking focus request created successfully ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Enhanced ducking focus request created successfully ===")
            
            // Check if enhanced ducking is viable for this TTS usage
            // Note: Some devices reject enhanced ducking for certain usage types
            Log.d(TAG, "=== DUCKING DEBUG: Checking if enhanced ducking is viable for TTS usage: $ttsUsage ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Checking if enhanced ducking is viable for TTS usage: $ttsUsage ===")
            
            if (ttsUsage == android.media.AudioAttributes.USAGE_MEDIA || 
                ttsUsage == android.media.AudioAttributes.USAGE_UNKNOWN) {
                Log.d(TAG, "=== DUCKING DEBUG: Enhanced ducking SKIPPED - TTS usage ($ttsUsage) may be ducked by system or rejected by device ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Enhanced ducking SKIPPED - TTS usage $ttsUsage may be affected by system ducking or rejected by device audio policy ===")
                return false
            }
            
            // Allow USAGE_ASSISTANCE_NAVIGATION_GUIDANCE to try enhanced ducking
            // Some devices handle this better than others, so we'll give it a chance
            if (ttsUsage == android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) {
                Log.d(TAG, "=== DUCKING DEBUG: Enhanced ducking with ASSISTANCE_NAVIGATION_GUIDANCE - may work on some devices ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Attempting enhanced ducking with ASSISTANCE_NAVIGATION_GUIDANCE (usage: $ttsUsage) - device-dependent behavior ===")
            }
            
            // Allow Notification stream to try enhanced ducking since some users report it works better
            if (ttsUsage == android.media.AudioAttributes.USAGE_NOTIFICATION) {
                Log.d(TAG, "=== DUCKING DEBUG: Enhanced ducking with Notification stream - may work on some devices despite potential conflicts ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Attempting enhanced ducking with Notification stream (usage: $ttsUsage) - device-dependent behavior ===")
            }
            
            // TRICK: Try alternative usage types for better compatibility on some devices
            // Some devices handle USAGE_MEDIA or USAGE_ASSISTANCE_ACCESSIBILITY better for ducking scenarios
            Log.d(TAG, "=== DUCKING DEBUG: Starting alternative usage type testing ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Starting alternative usage type testing ===")
            
            val alternativeUsages = listOf(
                android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY, // 11
                android.media.AudioAttributes.USAGE_MEDIA, // 1
                ttsUsage // Original usage as fallback
            )
            
            Log.d(TAG, "=== DUCKING DEBUG: Alternative usages to try: $alternativeUsages ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Alternative usages to try: $alternativeUsages ===")
            
            for (alternativeUsage in alternativeUsages) {
                if (alternativeUsage == ttsUsage) {
                    // Try the original usage last
                    Log.d(TAG, "=== DUCKING DEBUG: Skipping original usage $alternativeUsage (will try it later) ===")
                    InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Skipping original usage $alternativeUsage (will try it later) ===")
                    break
                }
                
                Log.d(TAG, "=== DUCKING DEBUG: Trying alternative TTS usage for enhanced ducking: $alternativeUsage ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Trying alternative TTS usage for enhanced ducking: $alternativeUsage ===")
                
                // Create audio attributes with alternative usage
                val alternativeAudioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(alternativeUsage)
                    .setContentType(ttsContent)
                    .build()
                
                Log.d(TAG, "=== DUCKING DEBUG: Created alternative audio attributes - Usage: $alternativeUsage, Content: $ttsContent ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Created alternative audio attributes - Usage: $alternativeUsage, Content: $ttsContent ===")
                
                // Create focus request with alternative usage
                val alternativeFocusRequest = android.media.AudioFocusRequest.Builder(
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                    .setAudioAttributes(alternativeAudioAttributes)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d(TAG, "=== DUCKING DEBUG: Alternative usage focus change listener called with: $focusChange ===")
                        InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Alternative usage focus change: $focusChange ===")
                        handleEnhancedDuckingFocusChange(focusChange)
                    }
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .build()
                
                Log.d(TAG, "=== DUCKING DEBUG: Requesting audio focus with alternative usage: $alternativeUsage ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Requesting audio focus with alternative usage: $alternativeUsage ===")
                
                val alternativeResult = audioManager.requestAudioFocus(alternativeFocusRequest)
                Log.d(TAG, "=== DUCKING DEBUG: Alternative usage $alternativeUsage result: $alternativeResult ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Alternative usage $alternativeUsage result: $alternativeResult ===")
                
                if (alternativeResult == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    enhancedDuckingFocusRequest = alternativeFocusRequest
                    isUsingEnhancedDucking = true
                    Log.d(TAG, "=== DUCKING DEBUG: Enhanced ducking GRANTED with alternative usage $alternativeUsage ===")
                    InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Enhanced ducking GRANTED with alternative usage $alternativeUsage ===")
                    return true
                } else {
                    Log.d(TAG, "=== DUCKING DEBUG: Alternative usage $alternativeUsage FAILED - trying next ===")
                    InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Alternative usage $alternativeUsage FAILED - trying next ===")
                }
            }
            
            // Request audio focus
            Log.d(TAG, "=== DUCKING DEBUG: Requesting audio focus with original usage: $ttsUsage ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Requesting audio focus with original usage: $ttsUsage ===")
            
            val result = audioManager.requestAudioFocus(enhancedDuckingFocusRequest!!)
            
            // Debug logging to understand the result codes  
            val resultText = when (result) {
                android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "GRANTED"
                android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "FAILED" 
                android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "DELAYED"
                else -> "UNKNOWN($result)"
            }
            Log.d(TAG, "=== DUCKING DEBUG: Enhanced ducking audio focus result: $resultText ($result) ===")
            InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Enhanced ducking audio focus result: $resultText (device: ${android.os.Build.MODEL}, usage: $ttsUsage) ===")
            
            if (result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                isUsingEnhancedDucking = true
                Log.d(TAG, "=== DUCKING DEBUG: Enhanced ducking focus GRANTED - system should duck other media automatically ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Enhanced ducking GRANTED - system handling media volume reduction ===")
                return true
            } else {
                Log.d(TAG, "=== DUCKING DEBUG: Enhanced ducking focus request $resultText - attempting retry before fallback ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Enhanced ducking $resultText on ${android.os.Build.MODEL} - attempting retry ===")
                
                // TRICK: Retry once after a short delay - sometimes the first request fails but retry succeeds
                Log.d(TAG, "=== DUCKING DEBUG: Starting 100ms retry delay ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Starting 100ms retry delay ===")
                
                try {
                    Thread.sleep(100) // 100ms delay
                    Log.d(TAG, "=== DUCKING DEBUG: 100ms retry delay completed ===")
                    InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: 100ms retry delay completed ===")
                    
                    Log.d(TAG, "=== DUCKING DEBUG: Retrying audio focus request ===")
                    InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Retrying audio focus request ===")
                    
                    val retryResult = audioManager.requestAudioFocus(enhancedDuckingFocusRequest!!)
                    val retryResultText = when (retryResult) {
                        android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "GRANTED"
                        android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "FAILED" 
                        android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "DELAYED"
                        else -> "UNKNOWN($retryResult)"
                    }
                    
                    Log.d(TAG, "=== DUCKING DEBUG: Retry result: $retryResultText ($retryResult) ===")
                    InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Retry result: $retryResultText ($retryResult) ===")
                    
                    if (retryResult == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        isUsingEnhancedDucking = true
                        Log.d(TAG, "=== DUCKING DEBUG: Enhanced ducking retry GRANTED - system should duck other media automatically ===")
                        InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Enhanced ducking retry GRANTED - system handling media volume reduction ===")
                        return true
                    } else {
                        Log.d(TAG, "=== DUCKING DEBUG: Enhanced ducking retry $retryResultText - falling back to manual ducking ===")
                        InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Enhanced ducking retry $retryResultText - falling back to manual ducking ===")
                    }
                } catch (e: InterruptedException) {
                    Log.d(TAG, "=== DUCKING DEBUG: Retry delay interrupted ===")
                    InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Retry delay interrupted ===")
                }
                
                Log.d(TAG, "=== DUCKING DEBUG: Cleaning up enhanced ducking and returning false ===")
                InAppLogger.log("MediaBehavior", "=== DUCKING DEBUG: Cleaning up enhanced ducking and returning false ===")
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
                InAppLogger.log("MediaBehavior", "Enhanced ducking: Can duck - re-applying TTS volume settings")
                // This shouldn't happen with our setup, but handle gracefully
                // Re-apply TTS volume settings to ensure it's not ducked
                if (isCurrentlySpeaking) {
                    reapplyTtsVolumeSettings()
                }
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
        releaseAudioFocus()

        if (legacyDuckingEnabled || pausedMediaSessions.isNotEmpty()) {
            resumeMediaSessions()
        }

        if (legacyDuckingEnabled || originalMusicVolume != -1) {
            restoreMediaVolume()
        }

        if (originalAudioMode != -1) {
            try {
                audioManager.mode = originalAudioMode
                Log.d(TAG, "Restored audio mode to $originalAudioMode")
                InAppLogger.log("Service", "Restored audio mode to $originalAudioMode")
                originalAudioMode = -1
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore audio mode during cleanup", e)
                InAppLogger.logError("Service", "Failed to restore audio mode: ${e.message}")
            }
        }
    }

    private fun handleNotificationBehavior(
        packageName: String,
        appName: String,
        text: String,
        conditionalDelaySeconds: Int = -1,
        sbn: StatusBarNotification? = null,
        speechTemplateOverride: SpeechTemplateOverride? = null
    ) {
        val isPriorityApp = priorityApps.contains(packageName)
        
        // Get original app name for statistics tracking (before privacy modification)
        val originalAppName = getAppName(packageName)
        val queuedNotification = QueuedNotification(
            appName = appName,
            text = text,
            isPriority = isPriorityApp,
            conditionalDelaySeconds = conditionalDelaySeconds,
            sbn = sbn,
            originalAppName = originalAppName,
            speechTemplateOverride = speechTemplateOverride
        )
        
        Log.d(TAG, "Handling notification behavior - Mode: $notificationBehavior, App: $appName, Currently speaking: $isCurrentlySpeaking, Queue size: ${notificationQueue.size}")
        InAppLogger.logNotification("Processing notification from $appName (mode: $notificationBehavior, speaking: $isCurrentlySpeaking)")
        
        // Check media behavior first, now with sbn for strict filtering
        if (!handleMediaBehavior(appName, text, sbn)) {
            Log.d(TAG, "Media behavior blocked notification from $appName (sbn: ${sbn?.packageName})")
            InAppLogger.logFilter("Media behavior blocked notification from $appName (sbn: ${sbn?.packageName})")
            // Track filter reason
            try {
                StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_MEDIA_BEHAVIOR)
            } catch (e: Exception) {
                Log.e(TAG, "Error tracking media behavior filter", e)
            }
            return
        }
        
        when (notificationBehavior) {
            "interrupt" -> {
                Log.d(TAG, "INTERRUPT mode: Speaking immediately and interrupting any current speech")
                speakNotificationImmediate(appName, text, conditionalDelaySeconds, sbn, originalAppName, speechTemplateOverride)
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
                    speakNotificationImmediate(appName, text, conditionalDelaySeconds, sbn, originalAppName, speechTemplateOverride)
                } else {
                    Log.d(TAG, "SKIP mode: Currently speaking, skipping notification from $appName")
                    // Track filter reason
                    try {
                        StatisticsManager.getInstance(this).incrementFilterReason(StatisticsManager.FILTER_SKIP_MODE)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error tracking skip mode filter", e)
                    }
                }
            }
            "smart" -> {
                if (isPriorityApp) {
                    Log.d(TAG, "SMART mode: Priority app $appName - interrupting")
                    speakNotificationImmediate(appName, text, conditionalDelaySeconds, sbn, originalAppName, speechTemplateOverride)
                } else {
                    Log.d(TAG, "SMART mode: Regular app $appName - adding to queue")
                    notificationQueue.add(queuedNotification)
                    processNotificationQueue()
                }
            }
            else -> {
                Log.d(TAG, "UNKNOWN mode '$notificationBehavior': Defaulting to interrupt")
                speakNotificationImmediate(appName, text, conditionalDelaySeconds, sbn, originalAppName, speechTemplateOverride)
            }
        }
    }

    private fun processNotificationQueue() {
        if (!MainActivity.isMasterSwitchEnabled(this)) {
            Log.d(TAG, "Queue processing skipped - master switch disabled (size=${notificationQueue.size})")
            InAppLogger.log("Service", "Queue processing skipped - master switch disabled")
            return
        }
        Log.d(TAG, "Processing queue - Currently speaking: $isCurrentlySpeaking, Queue size: ${notificationQueue.size}")
        if (!isCurrentlySpeaking && notificationQueue.isNotEmpty()) {
            val queuedNotification = notificationQueue.removeAt(0)
            Log.d(TAG, "Processing next queued notification from ${queuedNotification.appName}")
            speakNotificationImmediate(
                queuedNotification.appName,
                queuedNotification.text,
                queuedNotification.conditionalDelaySeconds,
                queuedNotification.sbn,
                queuedNotification.originalAppName,
                queuedNotification.speechTemplateOverride
            )
        } else if (isCurrentlySpeaking) {
            Log.d(TAG, "Still speaking, queue will be processed when current speech finishes")
        } else {
            Log.d(TAG, "Queue is empty")
        }
    }

    private fun speakNotificationImmediate(
        appName: String,
        text: String,
        conditionalDelaySeconds: Int = -1,
        sbn: StatusBarNotification? = null,
        originalAppName: String? = null,
        speechTemplateOverride: SpeechTemplateOverride? = null
    ) {
        if (!isTtsInitialized || textToSpeech == null) {
            Log.w(TAG, "TTS not initialized, cannot speak notification")
            return
        }
        
        // Check if this is a SelfTest notification
        val isSelfTest = sbn?.notification?.extras?.getBoolean(SelfTestHelper.EXTRA_IS_SELFTEST, false) ?: false
        if (isSelfTest) {
            InAppLogger.log("SelfTest", "SelfTest notification speaking")
            Log.d(TAG, "SelfTest notification about to be spoken")
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
            formatSpeechText(appName, text, packageName, sbn, speechTemplateOverride)
        }

        val tidySpeechText = if (tidySpeechRemoveEmojisEnabled) {
            removeSpokenEmojis(speechText)
        } else {
            speechText
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
                executeSpeech(tidySpeechText, appName, text, originalAppName)
            }
            delayHandler?.postDelayed(pendingReadoutRunnable!!, (effectiveDelay * 1000).toLong())
        } else {
            executeSpeech(tidySpeechText, appName, text, originalAppName)
        }
    }
    
    private fun executeSpeech(speechText: String, appName: String, originalText: String, originalAppName: String? = null) {
        Log.d(TAG, "=== DUCKING DEBUG: executeSpeech called with text: ${speechText.take(50)}... ===")
        InAppLogger.log("Service", "=== DUCKING DEBUG: executeSpeech called with text: ${speechText.take(50)}... ===")
        
        // CRITICAL: Stop any existing TTS speech and clear the queue to prevent stale text
        Log.d(TAG, "=== DUCKING DEBUG: Stopping any existing TTS speech to prevent stale text ===")
        InAppLogger.log("Service", "=== DUCKING DEBUG: Stopping any existing TTS speech to prevent stale text ===")
        
        // Track interruption if currently speaking (new notification interrupting current one)
        val wasSpeaking = isCurrentlySpeaking
        if (wasSpeaking) {
            try {
                StatisticsManager.getInstance(this).incrementReadoutsInterrupted()
            } catch (e: Exception) {
                Log.e(TAG, "Error tracking readout interruption", e)
            }
        }
        
        textToSpeech?.stop()
        
        // Small delay to ensure TTS is fully stopped and cleared
        Thread.sleep(50)
        
        // CRITICAL: Force refresh voice settings before each speech to ensure they're applied
        // This prevents issues where voice settings might not be current
        // The voice settings will respect the override logic (specific voice > language)
        Log.d(TAG, "=== DUCKING DEBUG: Refreshing voice settings before speech execution ===")
        InAppLogger.log("Service", "=== DUCKING DEBUG: Refreshing voice settings before speech execution ===")
        applyVoiceSettings()
        
        // Add engine failure warning if needed
        var finalSpeechText = speechText
        if (shouldShowEngineFailureWarning) {
            finalSpeechText = speechText + " " + getString(R.string.tts_engine_failure_spoken)
            shouldShowEngineFailureWarning = false // Clear flag after speaking once
            Log.d(TAG, "Added TTS engine failure warning to speech")
            InAppLogger.log("Service", "Added TTS engine failure warning to speech")
        }
        
        // Check TTS health before attempting to speak
        if (!checkTtsHealth()) {
            Log.e(TAG, "TTS health check failed - cannot speak")
            InAppLogger.logError("Service", "TTS health check failed - cannot speak")
            return
        }
        
        isCurrentlySpeaking = true
        
        // Set the current app name and text for the reading notification
        currentAppName = appName
        // Store original app name for statistics (before privacy modification)
        currentOriginalAppName = originalAppName ?: appName
        currentSpeechText = originalText
        currentTtsText = speechText
        
        // CRITICAL: Promote service to foreground for audio focus compatibility on Android 12+
        // This is essential for audio focus requests to be granted when the app is not in the foreground
        promoteToForegroundService()
        
        // Add a small delay to ensure the system recognizes the foreground service status
        // This is critical for audio focus requests to be granted on Android 12+
        try {
            Thread.sleep(100) // 100ms delay
            Log.d(TAG, "=== DUCKING DEBUG: 100ms delay after foreground promotion completed ===")
            InAppLogger.log("Service", "=== DUCKING DEBUG: 100ms delay after foreground promotion completed ===")
        } catch (e: InterruptedException) {
            Log.d(TAG, "=== DUCKING DEBUG: Foreground promotion delay interrupted ===")
            InAppLogger.log("Service", "=== DUCKING DEBUG: Foreground promotion delay interrupted ===")
        }
        
        // Show reading notification if enabled (now integrated into foreground notification)
        // showReadingNotification(currentAppName, currentSpeechText)
        
        // Set wave/pocket mode tracking variables
        speechStartTimestamp = System.currentTimeMillis()
        nearStableStartTimestamp = 0L
        lastFarTimestamp = 0L
        hasCapturedStartProximity = false
        Log.d(
            TAG,
            "Wave session start - hold=${waveHoldDurationMs}ms, threshold=$waveThreshold cm, timeout=${waveTimeoutSeconds}s, pocketMode=$isPocketModeEnabled"
        )
        if (isPocketModeEnabled) {
            hasSensorBeenUncovered = false
            val hasRecentProximitySample =
                speechStartTimestamp - lastProximityTimestamp <= PROXIMITY_START_SNAPSHOT_MAX_AGE_MS
            if (hasRecentProximitySample) {
                wasSensorCoveredAtStart = lastProximityIsNear
                hasCapturedStartProximity = true
                Log.d(TAG, "Pocket mode: Readout starting - using recent proximity sample, covered at start: $wasSensorCoveredAtStart")
            } else {
                wasSensorCoveredAtStart = false
                Log.d(TAG, "Pocket mode: Readout starting - no recent proximity sample, defaulting to uncovered")
            }
        }
        
        // Register shake listener now that we're about to speak
        registerShakeListener()
        
        // Create volume bundle with proper volume parameters
        val voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
        val ttsVolume = voicePrefs.getFloat("tts_volume", 1.0f)
        val ttsUsageIndex = voicePrefs.getInt("audio_usage", 4) // Default to ASSISTANT index
        val speakerphoneEnabled = voicePrefs.getBoolean("speakerphone_enabled", false)
        val ttsUsage = when (ttsUsageIndex) {
            0 -> android.media.AudioAttributes.USAGE_MEDIA
            1 -> android.media.AudioAttributes.USAGE_NOTIFICATION
            2 -> android.media.AudioAttributes.USAGE_ALARM
            3 -> android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
            4 -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            else -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        }
        
        Log.d(TAG, "=== DUCKING DEBUG: TTS settings for speech - Volume: ${ttsVolume * 100}%, Usage index: $ttsUsageIndex, Usage constant: $ttsUsage, Speakerphone: $speakerphoneEnabled ===")
        InAppLogger.log("Service", "=== DUCKING DEBUG: TTS settings for speech - Volume: ${ttsVolume * 100}%, Usage index: $ttsUsageIndex, Usage constant: $ttsUsage, Speakerphone: $speakerphoneEnabled ===")
        
        // Check if TTS volume compensation is active
        if (ttsVolumeCompensationActive) {
            Log.d(TAG, "=== DUCKING DEBUG: TTS volume compensation is ACTIVE - Original: ${originalTtsVolume * 100}%, Compensated: ${compensatedTtsVolume * 100}% ===")
            InAppLogger.log("Service", "=== DUCKING DEBUG: TTS volume compensation is ACTIVE - Original: ${originalTtsVolume * 100}%, Compensated: ${compensatedTtsVolume * 100}% ===")
        } else {
            Log.d(TAG, "=== DUCKING DEBUG: TTS volume compensation is NOT ACTIVE ===")
            InAppLogger.log("Service", "=== DUCKING DEBUG: TTS volume compensation is NOT ACTIVE ===")
        }
        
        // Handle speakerphone routing for VOICE_CALL stream
        if (ttsUsage == android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION && speakerphoneEnabled) {
            try {
                // Log current speakerphone state and audio mode
                val currentSpeakerphoneState = isSpeakerphoneEnabled(audioManager)
                val currentAudioMode = audioManager.mode
                InAppLogger.log("Service", "Current speakerphone state: $currentSpeakerphoneState, audio mode: $currentAudioMode")
                
                // Check if we already have an audio focus request that might conflict
                if (audioFocusRequest != null) {
                    InAppLogger.log("Service", "Warning: Existing audio focus request detected - releasing it to avoid conflicts with VOICE_CALL audio focus")
                    // Release existing audio focus to avoid conflicts
                    releaseAudioFocus()
                }
                
                // Also check for enhanced ducking focus requests that might conflict
                if (enhancedDuckingFocusRequest != null) {
                    InAppLogger.log("Service", "Warning: Enhanced ducking audio focus request detected - releasing it to avoid conflicts with VOICE_CALL audio focus")
                    try {
                        audioManager.abandonAudioFocusRequest(enhancedDuckingFocusRequest!!)
                        enhancedDuckingFocusRequest = null
                        isUsingEnhancedDucking = false
                    } catch (e: Exception) {
                        InAppLogger.logError("Service", "Failed to release enhanced ducking audio focus: ${e.message}")
                    }
                }
                
                // Store original audio mode for restoration
                if (originalAudioMode == -1) {
                    originalAudioMode = currentAudioMode
                    InAppLogger.log("Service", "Stored original audio mode: $originalAudioMode")
                }
                
                // Set audio mode to MODE_IN_COMMUNICATION for proper VOICE_CALL handling
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                InAppLogger.log("Service", "Set audio mode to MODE_IN_COMMUNICATION for VOICE_CALL stream")
                
                // Add a longer delay before requesting audio focus to ensure audio mode is fully set
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    // Request audio focus with VOICE_COMMUNICATION usage to match TTS
                    val focusGranted = requestAudioFocusForVoiceCall()
                    if (focusGranted) {
                        InAppLogger.log("Service", "Audio focus granted for VOICE_CALL, enabling speakerphone")
                    } else {
                        InAppLogger.log("Service", "Audio focus not granted for VOICE_CALL, but attempting speakerphone anyway")
                    }
                    
                    // Enable speakerphone with proper error handling
                    InAppLogger.log("Service", "Attempting to enable speakerphone...")
                    setSpeakerphoneEnabled(audioManager, true)
                    InAppLogger.log("Service", "Called audioManager.isSpeakerphoneOn = true")
                    
                    // If audio focus failed, try an alternative approach for some devices
                    if (!focusGranted) {
                        InAppLogger.log("Service", "Audio focus failed - trying alternative speakerphone approach")
                        // Some devices allow speakerphone control without audio focus
                        // Try setting audio mode again and speakerphone
                        try {
                            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                setSpeakerphoneEnabled(audioManager, true)
                                InAppLogger.log("Service", "Alternative speakerphone attempt completed")
                            }, 100) // Increased delay from 50ms to 100ms
                        } catch (e: Exception) {
                            InAppLogger.logError("Service", "Alternative speakerphone approach failed: ${e.message}")
                        }
                    }
                    
                    // Check immediate state
                    val immediateState = isSpeakerphoneEnabled(audioManager)
                    InAppLogger.log("Service", "Immediate speakerphone state after setting: $immediateState")
                    
                    // Add a longer delay to allow speakerphone state to take effect
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        // Verify speakerphone was actually enabled
                        val delayedState = isSpeakerphoneEnabled(audioManager)
                        InAppLogger.log("Service", "Speakerphone state after 200ms delay: $delayedState")
                        
                        if (delayedState) {
                            InAppLogger.log("Service", "Speakerphone successfully enabled for VOICE_CALL stream")
                        } else {
                            InAppLogger.logError("Service", "Speakerphone was not enabled despite successful call")
                            // Try to understand why - check if we have the right permissions
                            try {
                                val hasModifyAudioSettings = checkSelfPermission(android.Manifest.permission.MODIFY_AUDIO_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                InAppLogger.log("Service", "MODIFY_AUDIO_SETTINGS permission: $hasModifyAudioSettings")
                                
                                // Try one more time with a different approach
                                if (!hasModifyAudioSettings) {
                                    InAppLogger.logError("Service", "Missing MODIFY_AUDIO_SETTINGS permission - speakerphone may not work")
                                } else {
                                    InAppLogger.log("Service", "Permission granted but speakerphone still not working - trying final attempt")
                                    // Try setting speakerphone again with a longer delay
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        setSpeakerphoneEnabled(audioManager, true)
                                        val finalState = isSpeakerphoneEnabled(audioManager)
                                        InAppLogger.log("Service", "Final speakerphone attempt result: $finalState")
                                    }, 300) // 300ms delay for final attempt
                                }
                            } catch (e: Exception) {
                                InAppLogger.logError("Service", "Failed to check MODIFY_AUDIO_SETTINGS permission: ${e.message}")
                            }
                        }
                    }, 200) // Increased delay from 100ms to 200ms
                }, 200) // 200ms delay before requesting audio focus to ensure audio mode is fully set
            } catch (e: Exception) {
                InAppLogger.logError("Service", "Failed to enable speakerphone: ${e.message}")
            }
        }
        
        // Log TTS settings for debugging
        InAppLogger.log("Service", "TTS settings - Volume: ${ttsVolume * 100}%, Usage: $ttsUsage, Speakerphone: $speakerphoneEnabled")
        
        val volumeParams = VoiceSettingsActivity.createVolumeBundle(ttsVolume, ttsUsage, speakerphoneEnabled)
        
        Log.d(TAG, "=== DUCKING DEBUG: Created volume bundle for TTS.speak() ===")
        InAppLogger.log("Service", "=== DUCKING DEBUG: Created volume bundle for TTS.speak() ===")
        
        // Speak with queue mode FLUSH to interrupt any previous speech
        Log.d(TAG, "=== DUCKING DEBUG: Calling TTS.speak() with volume: ${ttsVolume * 100}% ===")
        InAppLogger.log("Service", "=== DUCKING DEBUG: Calling TTS.speak() with volume: ${ttsVolume * 100}% ===")
        
        val speakResult = textToSpeech?.speak(finalSpeechText, TextToSpeech.QUEUE_FLUSH, volumeParams, "notification_utterance")
        
        Log.d(TAG, "=== DUCKING DEBUG: TTS.speak() returned: $speakResult ===")
        InAppLogger.log("Service", "=== DUCKING DEBUG: TTS.speak() returned: $speakResult ===")
        
        // Start monitoring TTS volume during focus changes
        monitorTtsVolumeDuringFocusChanges()
        
        // Ensure TTS volume is ready for any app focus changes
        ensureTtsVolumeOnAppBackground()
        
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
                Log.d(TAG, "=== DUCKING DEBUG: TTS utterance STARTED: $utteranceId ===")
                InAppLogger.log("Service", "=== DUCKING DEBUG: TTS utterance STARTED: $utteranceId ===")
                InAppLogger.logTTSEvent("TTS started", speechText.take(50))
                scheduleSpeechSafetyTimeout("utterance_start", currentTtsText)
                
                // Log current volume state when TTS starts
                val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                Log.d(TAG, "=== DUCKING DEBUG: TTS started - Music volume: $currentVolume/$maxVolume ===")
                InAppLogger.log("Service", "=== DUCKING DEBUG: TTS started - Music volume: $currentVolume/$maxVolume ===")
                
                // Apply time cap if enabled
                if (contentCapMode == "time" && contentCapTimeLimit > 0) {
                    contentCapTimerRunnable = Runnable {
                        Log.d(TAG, "Content Cap time limit reached (${contentCapTimeLimit}s) - stopping TTS")
                        InAppLogger.log("Service", "Content Cap time limit reached (${contentCapTimeLimit}s) - stopping TTS")
                        
                        // Track interruption if currently speaking
                        val wasSpeaking = isCurrentlySpeaking
                        if (wasSpeaking) {
                            try {
                                StatisticsManager.getInstance(this@NotificationReaderService).incrementReadoutsInterrupted()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error tracking readout interruption", e)
                            }
                        }
                        
                        // Stop TTS
                        textToSpeech?.stop()
                        
                        // Manually trigger cleanup since stop() doesn't always trigger onDone/onError
                        isCurrentlySpeaking = false
                        contentCapTimerRunnable = null
                        cancelSpeechSafetyTimeout("content cap")
                        
                        // Stop foreground service
                        stopForegroundService()
                        
                        // Unregister sensors
                        unregisterShakeListener()

                        // Ensure media behavior effects are cleaned up (resume paused media, restore volume)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            cleanupMediaBehavior()
                        }, 250)
                        
                        // Resume queue now that the readout has ended early
                        resumeQueueAfterSpeechEnd("content cap")

                        Log.d(TAG, "Content Cap cleanup completed")
                        InAppLogger.logTTSEvent("TTS stopped by Content Cap", "Time limit: ${contentCapTimeLimit}s")
                    }
                    delayHandler?.postDelayed(contentCapTimerRunnable!!, (contentCapTimeLimit * 1000).toLong())
                    Log.d(TAG, "Content Cap timer started: ${contentCapTimeLimit}s")
                    InAppLogger.log("Service", "Content Cap timer started: ${contentCapTimeLimit}s")
                }
            }
            
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "notification_utterance") {
                    Log.d(TAG, "=== DUCKING DEBUG: TTS utterance COMPLETED: $utteranceId ===")
                    InAppLogger.log("Service", "=== DUCKING DEBUG: TTS utterance COMPLETED: $utteranceId ===")
                    isCurrentlySpeaking = false
                    cancelSpeechSafetyTimeout("utterance_done")
                    
                    // Cancel content cap timer if active
                    contentCapTimerRunnable?.let { runnable ->
                        delayHandler?.removeCallbacks(runnable)
                        contentCapTimerRunnable = null
                        Log.d(TAG, "Content Cap timer cancelled (TTS completed naturally)")
                    }
                    
                    // Log current volume state when TTS completes
                    val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    Log.d(TAG, "=== DUCKING DEBUG: TTS completed - Music volume: $currentVolume/$maxVolume ===")
                    InAppLogger.log("Service", "=== DUCKING DEBUG: TTS completed - Music volume: $currentVolume/$maxVolume ===")
                    
                    // Hide reading notification
                    // hideReadingNotification()
                    
                    // CRITICAL: Stop foreground service when TTS completes
                    // This is essential for proper cleanup and to avoid keeping the service in foreground unnecessarily
                    stopForegroundService()
                    
                    // Unregister shake listener since we're done speaking
                    unregisterShakeListener()
                    
                    // Disable speakerphone if it was enabled
                    try {
                        if (isSpeakerphoneEnabled(audioManager)) {
                            setSpeakerphoneEnabled(audioManager, false)
                            InAppLogger.log("Service", "Speakerphone disabled after speech completion")
                        }
                    } catch (e: Exception) {
                        InAppLogger.logError("Service", "Failed to disable speakerphone: ${e.message}")
                    }
                    
                    InAppLogger.logTTSEvent("TTS completed", "Utterance finished")
                    
                    // Track notification read for review reminder
                    trackNotificationReadForReview()
                    
                    // Track notification read for statistics (use original app name, not privacy-modified one)
                    try {
                        val appNameForStats = if (currentOriginalAppName.isNotEmpty()) currentOriginalAppName else currentAppName
                        StatisticsManager.getInstance(this@NotificationReaderService).incrementRead(appNameForStats)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error tracking notification read", e)
                    }
                    
                    // Clean up media behavior effects shortly after speech completes
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        cleanupMediaBehavior()
                    }, 250)
                    
                    // Process next item in queue if any
                    processNotificationQueue()
                }
            }
            
            @Suppress("DEPRECATION")
            override fun onError(utteranceId: String?) {
                if (utteranceId == "notification_utterance") {
                    Log.e(TAG, "TTS utterance error: $utteranceId")
                    isCurrentlySpeaking = false
                    cancelSpeechSafetyTimeout("utterance_error")
                    
                    // Cancel content cap timer if active
                    contentCapTimerRunnable?.let { runnable ->
                        delayHandler?.removeCallbacks(runnable)
                        contentCapTimerRunnable = null
                        Log.d(TAG, "Content Cap timer cancelled (TTS error)")
                    }
                    
                    // Hide reading notification
                    // hideReadingNotification()
                    
                    // CRITICAL: Stop foreground service when TTS completes (even on error)
                    stopForegroundService()
                    
                    unregisterShakeListener()
                    
                    // Disable speakerphone if it was enabled
                    try {
                        if (isSpeakerphoneEnabled(audioManager)) {
                            setSpeakerphoneEnabled(audioManager, false)
                            InAppLogger.log("Service", "Speakerphone disabled after TTS error")
                        }
                    } catch (e: Exception) {
                        InAppLogger.logError("Service", "Failed to disable speakerphone: ${e.message}")
                    }
                    
                    Log.e(TAG, "TTS error occurred")
                    InAppLogger.logTTSEvent("TTS error", "Utterance failed")
                    
                    // Attempt recovery for utterance errors
                    attemptTtsRecovery("Utterance error: $utteranceId")
                    
                    // Clean up media behavior effects with delay to avoid premature volume reduction
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        cleanupMediaBehavior()
                    }, 1000) // 1 second delay to ensure cleanup doesn't interfere with any ongoing audio
                    
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
            KEY_MEDIA_BEHAVIOR, KEY_DUCKING_VOLUME, KEY_DUCKING_FALLBACK_STRATEGY -> {
                // Reload media behavior settings
                mediaBehavior = sharedPreferences?.getString(KEY_MEDIA_BEHAVIOR, "ignore") ?: "ignore"
                duckingVolume = sharedPreferences?.getInt(KEY_DUCKING_VOLUME, 30) ?: 30
                duckingFallbackStrategy = sharedPreferences?.getString(KEY_DUCKING_FALLBACK_STRATEGY, "manual") ?: "manual"
                Log.d(TAG, "Media behavior settings updated - mode: $mediaBehavior, ducking volume: $duckingVolume%, fallback: $duckingFallbackStrategy")
                InAppLogger.log("Service", "Media behavior settings updated - mode: $mediaBehavior, ducking volume: $duckingVolume%, fallback: $duckingFallbackStrategy")
            }
            KEY_APP_LIST_MODE, KEY_APP_LIST, KEY_APP_PRIVATE_FLAGS, 
            KEY_WORD_LIST_MODE, KEY_WORD_BLACKLIST, KEY_WORD_BLACKLIST_PRIVATE, KEY_WORD_REPLACEMENTS,
            KEY_URL_HANDLING_MODE, KEY_URL_REPLACEMENT_TEXT -> {
                // Reload filter settings
                loadFilterSettings()
                Log.d(TAG, "Filter settings updated")
                InAppLogger.log("Service", "Filter settings updated")
            }
            KEY_CONTENT_CAP_MODE, KEY_CONTENT_CAP_WORD_COUNT, KEY_CONTENT_CAP_SENTENCE_COUNT, KEY_CONTENT_CAP_TIME_LIMIT -> {
                // Reload Content Cap settings
                contentCapMode = sharedPreferences?.getString(KEY_CONTENT_CAP_MODE, DEFAULT_CONTENT_CAP_MODE) ?: DEFAULT_CONTENT_CAP_MODE
                contentCapWordCount = sharedPreferences?.getInt(KEY_CONTENT_CAP_WORD_COUNT, DEFAULT_CONTENT_CAP_WORD_COUNT) ?: DEFAULT_CONTENT_CAP_WORD_COUNT
                contentCapSentenceCount = sharedPreferences?.getInt(KEY_CONTENT_CAP_SENTENCE_COUNT, DEFAULT_CONTENT_CAP_SENTENCE_COUNT) ?: DEFAULT_CONTENT_CAP_SENTENCE_COUNT
                contentCapTimeLimit = sharedPreferences?.getInt(KEY_CONTENT_CAP_TIME_LIMIT, DEFAULT_CONTENT_CAP_TIME_LIMIT) ?: DEFAULT_CONTENT_CAP_TIME_LIMIT
                Log.d(TAG, "Content Cap settings updated: mode=$contentCapMode, wordCount=$contentCapWordCount, sentenceCount=$contentCapSentenceCount, timeLimit=$contentCapTimeLimit")
                InAppLogger.log("Service", "Content Cap settings updated: mode=$contentCapMode, wordCount=$contentCapWordCount, sentenceCount=$contentCapSentenceCount, timeLimit=${contentCapTimeLimit}s")
            }
            KEY_TIDY_SPEECH_REMOVE_EMOJIS -> {
                tidySpeechRemoveEmojisEnabled = sharedPreferences?.getBoolean(KEY_TIDY_SPEECH_REMOVE_EMOJIS, false) ?: false
                Log.d(TAG, "Tidy speech setting updated: removeEmojis=$tidySpeechRemoveEmojisEnabled")
                InAppLogger.log("Service", "Tidy speech setting updated: removeEmojis=$tidySpeechRemoveEmojisEnabled")
            }
            KEY_SHAKE_TO_STOP_ENABLED,
            KEY_SHAKE_THRESHOLD,
            KEY_SHAKE_TIMEOUT_SECONDS,
            KEY_WAVE_TIMEOUT_SECONDS,
            KEY_WAVE_HOLD_DURATION_MS,
            "pocket_mode_enabled" -> {
                // Reload shake and wave settings
                refreshSettings()
                Log.d(TAG, "Shake/wave settings updated")
                InAppLogger.log("Service", "Shake/wave settings updated")
            }
            KEY_DELAY_BEFORE_READOUT -> {
                // Reload delay settings
                delayBeforeReadout = sharedPreferences?.getInt(KEY_DELAY_BEFORE_READOUT, 0) ?: 0
                Log.d(TAG, "Delay settings updated - delay: ${delayBeforeReadout}s")
                InAppLogger.log("Service", "Delay settings updated - delay: ${delayBeforeReadout}s")
            }
            KEY_MEDIA_FILTERING_ENABLED, KEY_MEDIA_FILTER_EXCEPTED_APPS, KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS, KEY_MEDIA_FILTERED_APPS, KEY_MEDIA_FILTERED_APPS_PRIVATE -> {
                // Reload media filtering settings
                loadFilterSettings()
                Log.d(TAG, "Media filtering settings updated")
                InAppLogger.log("Service", "Media filtering settings updated")
            }
            KEY_PERSISTENT_FILTERING_ENABLED -> {
                // Reload persistent filtering settings
                isPersistentFilteringEnabled = sharedPreferences?.getBoolean(KEY_PERSISTENT_FILTERING_ENABLED, true) ?: true
                Log.d(TAG, "Persistent filtering enabled updated: $isPersistentFilteringEnabled")
                InAppLogger.log("Service", "Persistent filtering enabled updated: $isPersistentFilteringEnabled")
            }
            "filter_persistent", "filter_silent", "filter_foreground_services", "filter_low_priority", "filter_system_notifications" -> {
                // Reload persistent filtering category settings
                filterPersistent = sharedPreferences?.getBoolean("filter_persistent", true) ?: true
                filterSilent = sharedPreferences?.getBoolean("filter_silent", true) ?: true
                filterForegroundServices = sharedPreferences?.getBoolean("filter_foreground_services", true) ?: true
                filterLowPriority = sharedPreferences?.getBoolean("filter_low_priority", false) ?: false
                filterSystemNotifications = sharedPreferences?.getBoolean("filter_system_notifications", false) ?: false
                Log.d(TAG, "Persistent filtering category settings updated - persistent: $filterPersistent, silent: $filterSilent, foreground: $filterForegroundServices, low: $filterLowPriority, system: $filterSystemNotifications")
                InAppLogger.log("Service", "Persistent filtering category settings updated - persistent: $filterPersistent, silent: $filterSilent, foreground: $filterForegroundServices, low: $filterLowPriority, system: $filterSystemNotifications")
            }
            KEY_COOLDOWN_APPS -> {
                loadCooldownSettings()
                Log.d(TAG, "Cooldown settings updated - apps: ${appCooldownSettings.size}")
                InAppLogger.log("Service", "Cooldown settings updated - apps: ${appCooldownSettings.size}")
            }
            KEY_SPEECH_TEMPLATE -> {
                refreshSpeechTemplateState()
                Log.d(TAG, "Speech template updated - key=$speechTemplateKey")
                InAppLogger.log("Service", "Speech template updated - key=$speechTemplateKey")
            }
            KEY_SPEECH_TEMPLATE_KEY -> {
                speechTemplateKey = sharedPreferences?.getString(KEY_SPEECH_TEMPLATE_KEY, speechTemplateKey) ?: speechTemplateKey
                Log.d(TAG, "Speech template key updated: $speechTemplateKey")
                InAppLogger.log("Service", "Speech template key updated: $speechTemplateKey")
            }
            KEY_PERSISTENT_NOTIFICATION -> {
                // Handle persistent notification setting change
                val isPersistentNotificationEnabled = sharedPreferences?.getBoolean(KEY_PERSISTENT_NOTIFICATION, false) ?: false
                Log.d(TAG, "Persistent notification setting updated: $isPersistentNotificationEnabled")
                checkAndShowPersistentNotification()
            }
            KEY_MASTER_SWITCH_ENABLED -> {
                val isMasterEnabled = sharedPreferences?.getBoolean(KEY_MASTER_SWITCH_ENABLED, true) ?: true
                Log.d(TAG, "Master switch changed - enabled: $isMasterEnabled")
                if (!isMasterEnabled) {
                    if (isCurrentlySpeaking) {
                        stopSpeaking("master switch")
                    } else {
                        notificationQueue.clear()
                        pendingReadoutRunnable?.let { runnable ->
                            delayHandler?.removeCallbacks(runnable)
                            pendingReadoutRunnable = null
                            Log.d(TAG, "Cancelled pending delayed readout due to master switch")
                        }
                    }
                }
                checkAndShowPersistentNotification()
            }
            KEY_NOTIFICATION_WHILE_READING -> {
                // Handle notification while reading setting change
                val isNotificationWhileReadingEnabled = sharedPreferences?.getBoolean(KEY_NOTIFICATION_WHILE_READING, false) ?: false
                Log.d(TAG, "Notification while reading setting updated: $isNotificationWhileReadingEnabled")
                
                // If TTS is currently speaking, update the notification immediately
                if (isCurrentlySpeaking) {
                    Log.d(TAG, "TTS is currently speaking, updating notification to reflect new preference")
                    promoteToForegroundService()
                }
            }
            "enable_legacy_ducking" -> {
                legacyDuckingEnabled = sharedPreferences?.getBoolean("enable_legacy_ducking", false) ?: false
                Log.d(TAG, "Legacy ducking toggled: $legacyDuckingEnabled")
                InAppLogger.log("MediaBehavior", "Legacy ducking ${if (legacyDuckingEnabled) "enabled" else "disabled"} via dev settings")
            }
        }
    }

    private fun matchesWordFilter(text: String, word: String): Boolean {
        return text.contains(word, ignoreCase = true)
    }

    private fun isSpokenEmojiCodePoint(codePoint: Int): Boolean {
        return UCharacter.hasBinaryProperty(codePoint, UProperty.EXTENDED_PICTOGRAPHIC) ||
            UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI_PRESENTATION) ||
            UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI_MODIFIER) ||
            UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI_MODIFIER_BASE) ||
            UCharacter.hasBinaryProperty(codePoint, UProperty.REGIONAL_INDICATOR)
    }

    private fun isEmojiSequenceControl(codePoint: Int): Boolean {
        return codePoint == 0x200D || // ZERO WIDTH JOINER
            codePoint == 0xFE0F ||    // VARIATION SELECTOR-16
            codePoint == 0xFE0E       // VARIATION SELECTOR-15
    }

    private fun removeSpokenEmojis(text: String): String {
        var index = 0
        var hasEmoji = false
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (isSpokenEmojiCodePoint(codePoint) || isEmojiSequenceControl(codePoint)) {
                hasEmoji = true
                break
            }
            index += Character.charCount(codePoint)
        }
        if (!hasEmoji) {
            return text
        }
        val builder = StringBuilder(text.length)
        index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (!isSpokenEmojiCodePoint(codePoint) && !isEmojiSequenceControl(codePoint)) {
                builder.appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
        return builder.toString()
    }
    
    /**
     * Format speech text using the custom template with placeholders
     */
    private fun formatSpeechText(
        appName: String,
        text: String,
        packageName: String,
        sbn: StatusBarNotification?,
        speechTemplateOverride: SpeechTemplateOverride? = null
    ): String {
        // Extract notification components from StatusBarNotification if available
        // IMPORTANT: Apply Content Cap to all extracted fields to ensure consistent capping behavior
        // regardless of which template placeholders the user chooses to use
        val rawTitle = sbn?.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val rawNotificationText = sbn?.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val rawBigText = sbn?.notification?.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val rawSummaryText = sbn?.notification?.extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
        val rawInfoText = sbn?.notification?.extras?.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() ?: ""
        val rawTickerText = sbn?.notification?.tickerText?.toString() ?: ""
        
        // Apply URL handling to all notification text fields so URLs are processed
        // regardless of which template placeholders the user chooses to use.
        // Note: {content} (the 'text' param) is already URL-processed by applyWordFiltering(),
        // but these fields are extracted fresh from the notification extras.
        val urlProcessedTitle = if (rawTitle.isNotEmpty()) applyUrlHandling(rawTitle) else rawTitle
        val urlProcessedNotificationText = if (rawNotificationText.isNotEmpty()) applyUrlHandling(rawNotificationText) else rawNotificationText
        val urlProcessedBigText = if (rawBigText.isNotEmpty()) applyUrlHandling(rawBigText) else rawBigText
        val urlProcessedSummaryText = if (rawSummaryText.isNotEmpty()) applyUrlHandling(rawSummaryText) else rawSummaryText
        val urlProcessedInfoText = if (rawInfoText.isNotEmpty()) applyUrlHandling(rawInfoText) else rawInfoText
        val urlProcessedTickerText = if (rawTickerText.isNotEmpty()) applyUrlHandling(rawTickerText) else rawTickerText
        
        // Apply Content Cap to all notification text fields to ensure consistent behavior
        // This ensures Content Cap works regardless of which template placeholders are used
        val title = if (contentCapMode != "disabled" && urlProcessedTitle.isNotEmpty()) applyContentCap(urlProcessedTitle) else urlProcessedTitle
        val notificationText = if (contentCapMode != "disabled" && urlProcessedNotificationText.isNotEmpty()) applyContentCap(urlProcessedNotificationText) else urlProcessedNotificationText
        val bigText = if (contentCapMode != "disabled" && urlProcessedBigText.isNotEmpty()) applyContentCap(urlProcessedBigText) else urlProcessedBigText
        val summaryText = if (contentCapMode != "disabled" && urlProcessedSummaryText.isNotEmpty()) applyContentCap(urlProcessedSummaryText) else urlProcessedSummaryText
        val infoText = if (contentCapMode != "disabled" && urlProcessedInfoText.isNotEmpty()) applyContentCap(urlProcessedInfoText) else urlProcessedInfoText
        val tickerText = if (contentCapMode != "disabled" && urlProcessedTickerText.isNotEmpty()) applyContentCap(urlProcessedTickerText) else urlProcessedTickerText
        
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
        @Suppress("DEPRECATION")
        val priority = when (sbn?.notification?.let { getPriorityLegacy(it) } ?: Notification.PRIORITY_DEFAULT) {
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
        
        // Handle template localization + varied/custom modes
        val templateToUse = resolveSpeechTemplateForPlayback(speechTemplateOverride)
        
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
            .replace("{ticker}", tickerText)
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
        return TtsLanguageManager.getLocalizedTtsString(this, getCurrentTtsLanguageCode(), stringResId)
    }

    private fun getCurrentTtsLanguageCode(): String {
        return voiceSettingsPrefs?.getString("tts_language", "system") ?: "system"
    }

    private fun refreshSpeechTemplateState() {
        val template = sharedPreferences?.getString(KEY_SPEECH_TEMPLATE, "{app} notified you: {content}") ?: "{app} notified you: {content}"
        speechTemplate = template
        val storedKey = sharedPreferences?.getString(KEY_SPEECH_TEMPLATE_KEY, null)
        speechTemplateKey = when {
            !storedKey.isNullOrBlank() -> storedKey
            template == SpeechTemplateConstants.TEMPLATE_KEY_VARIED -> SpeechTemplateConstants.TEMPLATE_KEY_VARIED
            template == "VARIED" -> SpeechTemplateConstants.TEMPLATE_KEY_VARIED // Legacy literal storage
            else -> TtsLanguageManager.findMatchingStringKey(this, template, SpeechTemplateConstants.RESOURCE_TEMPLATE_KEYS)
                ?: SpeechTemplateConstants.TEMPLATE_KEY_CUSTOM
        }
        
        if (storedKey == null) {
            sharedPreferences?.edit()?.putString(KEY_SPEECH_TEMPLATE_KEY, speechTemplateKey)?.apply()
        }
        
        Log.d(TAG, "Speech template state refreshed - key=$speechTemplateKey")
    }
    
    private fun isResourceTemplateKey(key: String?): Boolean {
        if (key.isNullOrBlank()) {
            return false
        }
        return SpeechTemplateConstants.RESOURCE_TEMPLATE_KEYS.contains(key)
    }
    
    private fun resolveSpeechTemplateForPlayback(override: SpeechTemplateOverride? = null): String {
        if (override != null) {
            val overrideKey = override.templateKey
            val overrideTemplate = override.template
            return when {
                overrideKey == SpeechTemplateConstants.TEMPLATE_KEY_VARIED ||
                    overrideTemplate == SpeechTemplateConstants.TEMPLATE_KEY_VARIED ||
                    overrideTemplate == "VARIED" -> getLocalizedVariedFormatsImproved().random()
                overrideKey == SpeechTemplateConstants.TEMPLATE_KEY_CUSTOM -> overrideTemplate
                isResourceTemplateKey(overrideKey) -> {
                    val key = overrideKey ?: SpeechTemplateConstants.DEFAULT_TEMPLATE_KEY
                    TtsLanguageManager.getLocalizedTtsStringByName(this, getCurrentTtsLanguageCode(), key)
                }
                overrideTemplate.isNotBlank() -> overrideTemplate
                else -> resolveSpeechTemplateForPlayback()
            }
        }

        return when {
            speechTemplateKey == SpeechTemplateConstants.TEMPLATE_KEY_VARIED -> getLocalizedVariedFormatsImproved().random()
            speechTemplateKey == SpeechTemplateConstants.TEMPLATE_KEY_CUSTOM -> speechTemplate
            isResourceTemplateKey(speechTemplateKey) -> {
                val key = speechTemplateKey ?: SpeechTemplateConstants.DEFAULT_TEMPLATE_KEY
                TtsLanguageManager.getLocalizedTtsStringByName(this, getCurrentTtsLanguageCode(), key)
            }
            speechTemplate == SpeechTemplateConstants.TEMPLATE_KEY_VARIED || speechTemplate == "VARIED" -> getLocalizedVariedFormatsImproved().random()
            else -> speechTemplate
        }
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
        val shouldRun = PersistentIndicatorManager.shouldRun(this)
        Log.d(TAG, "Checking persistent indicator requirement: $shouldRun")
        
        if (shouldRun) {
            PersistentIndicatorManager.requestStart(this)
        } else {
            PersistentIndicatorManager.requestStop(this)
        }
    }
    
    

    

    
    /**
     * Promote service to foreground during TTS playback to enable audio focus on Android 12+
     * This is critical for audio focus requests to be granted when the app is not in the foreground.
     */
    private fun promoteToForegroundService() {
        try {
            // Check if notification while reading is enabled
            val showNotificationWhileReading = sharedPreferences?.getBoolean(KEY_NOTIFICATION_WHILE_READING, false) ?: false
            
            // Create foreground notification (detailed or minimal based on preference)
            val foregroundNotification = createForegroundNotification(
                appName = currentAppName, 
                content = currentSpeechText, 
                ttsText = currentTtsText,
                showDetails = showNotificationWhileReading
            )
            startForeground(FOREGROUND_SERVICE_ID, foregroundNotification)
            Log.d(TAG, "Service promoted to foreground for reading (id=$FOREGROUND_SERVICE_ID, showDetails=$showNotificationWhileReading)")
            InAppLogger.log("Service", "Service promoted to foreground for reading (id=$FOREGROUND_SERVICE_ID, showDetails=$showNotificationWhileReading)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to promote service to foreground", e)
            InAppLogger.logError("Service", "Failed to promote service to foreground: ${e.message}")
        }
    }
    
    /**
     * Stop foreground service when TTS completes
     */
    private fun stopForegroundService() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                stopForeground(android.app.Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            Log.d(TAG, "Service stopped from foreground")
            InAppLogger.log("Service", "Service stopped from foreground")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop foreground service", e)
            InAppLogger.logError("Service", "Failed to stop foreground service: ${e.message}")
        }
    }
    
    /**
     * Smart truncation function for notification text
     * Truncates text to a reasonable length while preserving readability
     */
    private fun truncateForNotification(text: String, maxLength: Int = 200): String {
        return if (text.length <= maxLength) {
            text
        } else {
            // Try to find a good breaking point (space, punctuation, etc.)
            val truncated = text.take(maxLength - 3) // Leave room for "..."
            val lastSpaceIndex = truncated.lastIndexOf(' ')
            val lastPunctuationIndex = truncated.lastIndexOf('.')
            val lastCommaIndex = truncated.lastIndexOf(',')
            
            // Find the best breaking point
            val breakIndex = maxOf(lastSpaceIndex, lastPunctuationIndex, lastCommaIndex)
            
            if (breakIndex > maxLength * 0.7) { // Only use if it's not too early
                text.take(breakIndex + 1) + "..."
            } else {
                truncated + "..."
            }
        }
    }
    
    /**
     * Create a notification for foreground service
     * @param showDetails If true, shows detailed content; if false, creates a minimal invisible notification
     */
    private fun createForegroundNotification(appName: String = "", content: String = "", ttsText: String = "", showDetails: Boolean = true): Notification {
        // Create notification channel for Android O+
        SpeakThatNotificationChannel.ensureExists(this)
        
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
        
        // If showDetails is false, create a minimal notification
        if (!showDetails) {
            // Create minimal, nearly invisible notification for foreground service requirement
            return NotificationCompat.Builder(this, SpeakThatNotificationChannel.CHANNEL_ID)
                .setContentTitle("SpeakThat")
                .setSmallIcon(R.drawable.speakthaticon)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN) // Minimal priority
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(false)
                .setShowWhen(false) // Don't show timestamp
                .build()
        }
        
        // Build foreground notification with detailed content
        val notificationBuilder = NotificationCompat.Builder(this, SpeakThatNotificationChannel.CHANNEL_ID)
            .setContentTitle("SpeakThat Reading")
            .setSmallIcon(R.drawable.speakthaticon)
            .setOngoing(true) // Persistent notification (required for foreground service)
            .setSilent(true) // Silent notification
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(false) // Don't auto-dismiss
        
        // Add detailed content if available
        if (appName.isNotEmpty() && ttsText.isNotEmpty()) {
            val truncatedTtsText = truncateForNotification(ttsText)
            val notificationText = "$appName: $truncatedTtsText"
            
            notificationBuilder
                .setContentText(notificationText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
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
        } else if (appName.isNotEmpty() && content.isNotEmpty()) {
            // Fallback to original content if TTS text is not available
            val truncatedContent = truncateForNotification(content)
            val notificationText = "$appName: $truncatedContent"
            
            notificationBuilder
                .setContentText(notificationText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
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
        } else {
            notificationBuilder.setContentText("Reading notifications aloud")
        }
        
        return notificationBuilder.build()
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
            currentTtsText = ""
            
            // Clear notification queue
            notificationQueue.clear()
            
            // Reading notification is now integrated into foreground notification
            // hideReadingNotification()
            
            // CRITICAL: Clean up media behavior effects (resume paused media)
            cleanupMediaBehavior()
            
            // CRITICAL: Stop foreground service when TTS is manually stopped
            // This ensures the foreground service notification is properly removed
            stopForegroundService()
            
            Log.d(TAG, "TTS stopped via notification action")
            InAppLogger.log("Notifications", "TTS stopped via notification action")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
            InAppLogger.logError("Notifications", "Error stopping TTS: ${e.message}")
        }
    }
    
    /**
     * Track notification read for review reminder
     */
    private fun trackNotificationReadForReview() {
        try {
            // Track notification read directly using ReviewReminderManager
            val reviewManager = ReviewReminderManager.getInstance(this)
            reviewManager.incrementNotificationsRead()
            
            Log.d(TAG, "Notification read tracked for review reminder")
        } catch (e: Exception) {
            Log.d(TAG, "Could not track notification read for review reminder: ${e.message}")
        }
    }

}
