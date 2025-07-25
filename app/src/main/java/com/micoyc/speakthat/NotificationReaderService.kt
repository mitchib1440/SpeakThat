package com.micoyc.speakthat

import android.app.Notification
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
import com.micoyc.speakthat.VoiceSettingsActivity
import com.micoyc.speakthat.BehaviorSettingsActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashMap
import java.util.HashSet
import java.util.Locale
import kotlin.collections.ArrayList
import android.app.NotificationManager
import android.content.Context

class NotificationReaderService : NotificationListenerService(), TextToSpeech.OnInitListener, SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {
    
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private lateinit var sharedPreferences: SharedPreferences
    
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
    
    // Filter system
    private var appListMode = "none" // "none", "whitelist", "blacklist"
    private var appList = HashSet<String>()
    private var privateApps = HashSet<String>()
    private var blockedWords = HashSet<String>()
    private var privateWords = HashSet<String>()
    private var wordReplacements = HashMap<String, String>()
    
    // Behavior settings
    private var notificationBehavior = "interrupt" // "interrupt", "queue", "skip", "smart"
    private var priorityApps = HashSet<String>()
    
    // Media behavior settings
    private var mediaBehavior = "ignore" // "ignore", "pause", "duck", "silence"
    private var duckingVolume = 30 // Volume percentage when ducking
    private var originalMusicVolume = -1 // Store original volume for restoration
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    
    // Media notification filtering
    private var mediaFilterPreferences = MediaNotificationDetector.MediaFilterPreferences()
    
    // Persistent/silent notification filtering
    private var isPersistentFilteringEnabled = false
    private var filterPersistent = true
    private var filterSilent = true
    private var filterForegroundServices = true
    private var filterLowPriority = false
    private var filterSystemNotifications = false
    
    // Delay settings
    private var delayBeforeReadout = 0 // Delay in seconds before starting TTS
    private var delayHandler: android.os.Handler? = null
    private var pendingReadoutRunnable: Runnable? = null
    
    // TTS queue for different behavior modes
    private val notificationQueue = mutableListOf<QueuedNotification>()
    private var isCurrentlySpeaking = false
    
    // Cooldown tracking
    private val appCooldownTimestamps = HashMap<String, Long>() // packageName -> last notification timestamp
    private val appCooldownSettings = HashMap<String, Int>() // packageName -> cooldown seconds
    
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
    private var voiceSettingsPrefs: SharedPreferences? = null
    private val voiceSettingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "speech_rate", "pitch", "voice_name", "language", "audio_usage", "content_type" -> {
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
        
        // Conditional rules settings
        private const val KEY_CONDITIONAL_RULES = "conditional_rules"
        
        // Media notification filtering settings
        private const val KEY_MEDIA_FILTERING_ENABLED = "media_filtering_enabled"
        private const val KEY_MEDIA_FILTER_EXCEPTED_APPS = "media_filter_excepted_apps"
        private const val KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS = "media_filter_important_keywords"
        
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
        
        try {
            Log.d(TAG, "Starting service initialization...")
            InAppLogger.log("Service", "Starting service initialization")
            
            // Initialize SharedPreferences
            Log.d(TAG, "Initializing SharedPreferences...")
            sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            Log.d(TAG, "SharedPreferences initialized")
            
            // Register preference change listener to automatically reload settings
            Log.d(TAG, "Registering preference change listener...")
            sharedPreferences.registerOnSharedPreferenceChangeListener(this)
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
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during service initialization", e)
            InAppLogger.logError("Service", "Critical initialization error: " + e.message)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "NotificationReaderService destroyed")
        
        // Unregister preference change listener
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        
        // Unregister voice settings listener
        voiceSettingsPrefs?.unregisterOnSharedPreferenceChangeListener(voiceSettingsListener)
        
        textToSpeech?.shutdown()
        
        // CRITICAL: Force unregister shake listener to prevent battery drain
        unregisterShakeListener()
        
        // Additional safety: Force unregister any remaining sensor listeners
        try {
            sensorManager?.unregisterListener(this)
            Log.d(TAG, "Force unregistered all sensor listeners as safety measure")
        } catch (e: Exception) {
            Log.e(TAG, "Error force unregistering sensors", e)
        }
        
        // Clean up delay handler
        pendingReadoutRunnable?.let { runnable ->
            delayHandler?.removeCallbacks(runnable)
            pendingReadoutRunnable = null
        }
        
        // Clean up TTS recovery system
        ttsRecoveryRunnable?.let { runnable ->
            ttsRecoveryHandler?.removeCallbacks(runnable)
            ttsRecoveryRunnable = null
        }
        ttsRecoveryHandler = null
        
        // Stop periodic health check
        stopPeriodicHealthCheck()
        
        // Clean up media behavior effects
        cleanupMediaBehavior()
        
        InAppLogger.log("Service", "NotificationReaderService fully destroyed with battery optimizations")
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
                
                // Get app name
                val appName = getAppName(packageName)
                
                // Extract notification text
                val notificationText = extractNotificationText(notification)
                
                if (notificationText.isNotEmpty()) {
                    Log.d(TAG, "New notification from $appName: $notificationText")
                    InAppLogger.logNotification("Processing notification from $appName: $notificationText")
                    
                    // Apply filtering
                    val filterResult = applyFilters(packageName, appName, notificationText, sbn)
                    
                    if (filterResult.shouldSpeak) {
                        // Determine final app name (private apps become "An app")
                        val isAppPrivate = privateApps.contains(packageName)
                        val finalAppName = if (isAppPrivate) "An app" else appName
                        
                        // Add to history
                        addToHistory(finalAppName, packageName, filterResult.processedText)
                        
                        // Handle notification based on behavior mode (pass conditional delay info)
                        handleNotificationBehavior(packageName, finalAppName, filterResult.processedText, filterResult.conditionalDelaySeconds, sbn)
                    } else {
                        Log.d(TAG, "Notification filtered out: ${filterResult.reason}")
                        InAppLogger.logFilter("Blocked notification from $appName: ${filterResult.reason}")
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
                Log.d(TAG, "TTS initialization successful, setting up language...")
                InAppLogger.log("Service", "TTS initialization successful, setting up language")
                
                val result = textToSpeech?.setLanguage(Locale.getDefault())
                Log.d(TAG, "Language set result: $result")
                
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported for TTS, falling back to US English")
                    InAppLogger.log("Service", "Language not supported, falling back to US English")
                    // Fallback to English US
                    textToSpeech?.setLanguage(Locale.US)
                }
                
                // Set audio stream to assistant usage to avoid triggering media detection
                try {
                    val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
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
                
                // Apply saved voice settings
                try {
                    applyVoiceSettings()
                    Log.d(TAG, "Voice settings applied successfully")
                    InAppLogger.log("Service", "Voice settings applied successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error applying voice settings", e)
                    InAppLogger.logError("Service", "Error applying voice settings: " + e.message)
                }
                
                isTtsInitialized = true
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
            val packageManager = packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            Log.d(TAG, "Successfully resolved app name for $packageName: $appName")
            appName
        } catch (e: Exception) {
            Log.w(TAG, "Error getting app name for $packageName: ${e.message}")
            packageName
        }
    }


    private fun getCustomAppName(packageName: String): String? {
        return try {
            val customAppNamesJson = sharedPreferences.getString("custom_app_names", "[]") ?: "[]"
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
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val notificationData = NotificationData(appName, packageName, text, timestamp)
        
        synchronized(notificationHistory) {
            notificationHistory.add(0, notificationData) // Add to beginning
            
            // Keep only recent notifications
            if (notificationHistory.size > MAX_HISTORY_SIZE) {
                notificationHistory.removeAt(notificationHistory.size - 1)
            }
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
        isShakeToStopEnabled = sharedPreferences.getBoolean(KEY_SHAKE_TO_STOP_ENABLED, true)
        shakeThreshold = sharedPreferences.getFloat(KEY_SHAKE_THRESHOLD, 12.0f)
        
        // Safety validation: ensure timeout is within valid range (0 or 5-300)
        var timeout = sharedPreferences.getInt(KEY_SHAKE_TIMEOUT_SECONDS, 30)
        if (timeout < 0 || (timeout > 0 && timeout < 5) || timeout > 300) {
            timeout = 30 // Reset to safe default
            Log.w(TAG, "Invalid shake timeout value detected ($timeout), resetting to 30 seconds")
            InAppLogger.logWarning(TAG, "Invalid shake timeout value detected, resetting to 30 seconds")
            // Save the corrected value
            sharedPreferences.edit().putInt(KEY_SHAKE_TIMEOUT_SECONDS, timeout).apply()
        }
        shakeTimeoutSeconds = timeout
        
        Log.d(TAG, "Shake settings loaded - enabled: $isShakeToStopEnabled, threshold: $shakeThreshold, timeout: ${shakeTimeoutSeconds}s")
    }

    private fun loadWaveSettings() {
        isWaveToStopEnabled = sharedPreferences.getBoolean("wave_to_stop_enabled", false)
        // Use calibrated threshold if available, otherwise fall back to old system
        waveThreshold = if (sharedPreferences.contains("wave_threshold_v1")) {
            sharedPreferences.getFloat("wave_threshold_v1", 3.0f)
        } else {
            sharedPreferences.getFloat("wave_threshold", 3.0f)
        }
        
        // Safety validation: ensure timeout is within valid range (0 or 5-300)
        var timeout = sharedPreferences.getInt(KEY_WAVE_TIMEOUT_SECONDS, 30)
        if (timeout < 0 || (timeout > 0 && timeout < 5) || timeout > 300) {
            timeout = 30 // Reset to safe default
            Log.w(TAG, "Invalid wave timeout value detected ($timeout), resetting to 30 seconds")
            InAppLogger.logWarning(TAG, "Invalid wave timeout value detected, resetting to 30 seconds")
            // Save the corrected value
            sharedPreferences.edit().putInt(KEY_WAVE_TIMEOUT_SECONDS, timeout).apply()
        }
        waveTimeoutSeconds = timeout
        
        // Load pocket mode setting
        isPocketModeEnabled = sharedPreferences.getBoolean("pocket_mode_enabled", false)
        
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
            val cooldownAppsJson = sharedPreferences.getString(KEY_COOLDOWN_APPS, "[]") ?: "[]"
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
        appListMode = sharedPreferences.getString(KEY_APP_LIST_MODE, "none") ?: "none"
        appList = HashSet(sharedPreferences.getStringSet(KEY_APP_LIST, HashSet()) ?: HashSet())
        privateApps = HashSet(sharedPreferences.getStringSet(KEY_APP_PRIVATE_FLAGS, HashSet()) ?: HashSet())
        blockedWords = HashSet(sharedPreferences.getStringSet(KEY_WORD_BLACKLIST, HashSet()) ?: HashSet())
        privateWords = HashSet(sharedPreferences.getStringSet(KEY_WORD_BLACKLIST_PRIVATE, HashSet()) ?: HashSet())
        
        // Load word swaps
        val replacementData = sharedPreferences.getString(KEY_WORD_REPLACEMENTS, "") ?: ""
        wordReplacements.clear()
        if (replacementData.isNotEmpty()) {
            val pairs = replacementData.split("\\|".toRegex())
            for (pair in pairs) {
                val parts = pair.split(":", limit = 2)
                if (parts.size == 2) {
                    wordReplacements[parts[0]] = parts[1]
                }
            }
        }
        
        // Load behavior settings  
        notificationBehavior = sharedPreferences.getString(KEY_NOTIFICATION_BEHAVIOR, "interrupt") ?: "interrupt"
        priorityApps = HashSet(sharedPreferences.getStringSet(KEY_PRIORITY_APPS, HashSet()) ?: HashSet())
        
        // Load media behavior settings
        mediaBehavior = sharedPreferences.getString(KEY_MEDIA_BEHAVIOR, "ignore") ?: "ignore"
        duckingVolume = sharedPreferences.getInt(KEY_DUCKING_VOLUME, 30)
        
        // Load delay settings
        delayBeforeReadout = sharedPreferences.getInt(KEY_DELAY_BEFORE_READOUT, 0)
        
        // Load media notification filtering settings
        val isMediaFilteringEnabled = sharedPreferences.getBoolean(KEY_MEDIA_FILTERING_ENABLED, false)
        val exceptedApps = HashSet(sharedPreferences.getStringSet(KEY_MEDIA_FILTER_EXCEPTED_APPS, HashSet()) ?: HashSet())
        val importantKeywords = HashSet(sharedPreferences.getStringSet(KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS, HashSet()) ?: HashSet())
        
        // If no custom important keywords are set, use defaults
        if (importantKeywords.isEmpty()) {
            importantKeywords.addAll(MediaNotificationDetector.MediaFilterPreferences().importantKeywords)
        }
        
        mediaFilterPreferences = MediaNotificationDetector.MediaFilterPreferences(
            isMediaFilteringEnabled = isMediaFilteringEnabled,
            exceptedApps = exceptedApps,
            importantKeywords = importantKeywords
        )
        
        // Load persistent filtering settings
        isPersistentFilteringEnabled = sharedPreferences.getBoolean(KEY_PERSISTENT_FILTERING_ENABLED, false)
        filterPersistent = sharedPreferences.getBoolean("filter_persistent", true)
        filterSilent = sharedPreferences.getBoolean("filter_silent", true)
        filterForegroundServices = sharedPreferences.getBoolean("filter_foreground_services", true)
        filterLowPriority = sharedPreferences.getBoolean("filter_low_priority", false)
        filterSystemNotifications = sharedPreferences.getBoolean("filter_system_notifications", false)
        
        // Load cooldown settings
        loadCooldownSettings()
        
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
        val wordFilterResult = applyWordFiltering(text, appName)
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
                    FilterResult(false, "", "App blacklisted")
                } else {
                    FilterResult(true, "", "App not blacklisted")
                }
            }
            else -> FilterResult(true, "", "No app filtering")
        }
    }
    
    private fun applyWordFiltering(text: String, appName: String): FilterResult {
        var processedText = text
        
        // 1. Check for blocked words (including smart filters)
        for (blockedWord in blockedWords) {
            if (matchesWordFilter(processedText, blockedWord)) {
                return FilterResult(false, "", "Blocked by filter: $blockedWord")
            }
        }
        
        // 2. Check for private words and replace entire notification with [PRIVATE] (including smart filters)
        for (privateWord in privateWords) {
            if (matchesWordFilter(processedText, privateWord)) {
                // When any private word is detected, replace the entire notification text with a private message
                // This ensures complete privacy - no partial content is revealed
                processedText = "You received a private notification from $appName"
                Log.d(TAG, "Private word '$privateWord' detected - entire notification made private")
                InAppLogger.logFilter("Made notification private due to word: $privateWord")
                break // Exit loop since entire text is now private
            }
        }
        
        // 3. Apply word swaps
        for ((from, to) in wordReplacements) {
            processedText = processedText.replace(from, to, ignoreCase = true)
        }
        
        return FilterResult(true, processedText, "Word filtering applied")
    }
    
    private fun applyConditionalFiltering(packageName: String, appName: String, text: String): FilterResult {
        // Placeholder for future conditional filtering features
        return FilterResult(true, text, "No conditional rules applied")
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
        
        Log.d(TAG, "TTS stopped due to $triggerType")
        InAppLogger.logTTSEvent("TTS stopped by $triggerType", "User interrupted speech")
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
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
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
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                val voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
                val ttsUsage = voicePrefs.getInt("audio_usage", android.media.AudioAttributes.USAGE_ASSISTANT)
                val ttsContent = voicePrefs.getInt("content_type", android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                InAppLogger.log("MediaBehavior", "Pause mode triggered. Device: ${android.os.Build.MODEL}, Android: ${android.os.Build.VERSION.SDK_INT}, TTS usage: $ttsUsage, TTS content: $ttsContent, Media volume: $currentVolume/$maxVolume")
                return true
            }
            "duck" -> {
                Log.d(TAG, "Media behavior: DUCK - lowering media volume to $duckingVolume%")
                InAppLogger.log("MediaBehavior", "Ducking media volume - notification will be spoken")
                val ducked = duckMediaVolume()
                // Log device and audio info for diagnostics
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                val voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
                val ttsUsage = voicePrefs.getInt("audio_usage", android.media.AudioAttributes.USAGE_ASSISTANT)
                val ttsContent = voicePrefs.getInt("content_type", android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                InAppLogger.log("MediaBehavior", "Duck mode triggered. Device: ${android.os.Build.MODEL}, Android: ${android.os.Build.VERSION.SDK_INT}, TTS usage: $ttsUsage, TTS content: $ttsContent, Media volume: $currentVolume/$maxVolume")
                // Warn if TTS is routed through the same stream as media
                if (ttsUsage == android.media.AudioAttributes.USAGE_MEDIA || ttsUsage == android.media.AudioAttributes.USAGE_UNKNOWN) {
                    Log.w(TAG, "TTS may be routed through the same stream as media. Ducking may lower TTS volume as well.")
                    InAppLogger.log("MediaBehavior", "Warning: TTS may be affected by ducking on this device. TTS usage: $ttsUsage")
                }
                return ducked
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
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
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
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
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
            
            // Set ducked volume
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
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            try {
                audioManager.setStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC,
                    originalMusicVolume,
                    0
                )
                Log.d(TAG, "Media volume restored to $originalMusicVolume")
                InAppLogger.log("MediaBehavior", "Restored media volume")
                originalMusicVolume = -1
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore media volume", e)
            }
        }
    }
    
    private fun releaseAudioFocus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
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
    
    private fun cleanupMediaBehavior() {
        when (mediaBehavior) {
            "pause" -> {
                // Release audio focus to allow media to resume
                releaseAudioFocus()
            }
            "duck" -> {
                // Restore original media volume
                restoreMediaVolume()
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
        val speechText = if (text.startsWith("You received a private notification from")) {
            text // Private messages already include the app name, so don't add prefix
        } else {
            "$appName notified you: $text"
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
        
        // Check TTS health before attempting to speak
        if (!checkTtsHealth()) {
            Log.e(TAG, "TTS health check failed - cannot speak")
            InAppLogger.logError("Service", "TTS health check failed - cannot speak")
            return
        }
        
        isCurrentlySpeaking = true
        
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
                    
                    // Unregister shake listener since we're done speaking
                    unregisterShakeListener()
                    
                    InAppLogger.logTTSEvent("TTS completed", "Utterance finished")
                    
                    // Clean up media behavior effects
                    cleanupMediaBehavior()
                    
                    // Process next item in queue if any
                    processNotificationQueue()
                }
            }
            
            override fun onError(utteranceId: String?) {
                if (utteranceId == "notification_utterance") {
                    Log.e(TAG, "TTS utterance error: $utteranceId")
                    isCurrentlySpeaking = false
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

    private fun applyVoiceSettings() {
        if (isTtsInitialized && textToSpeech != null) {
            VoiceSettingsActivity.applyVoiceSettings(textToSpeech!!, voiceSettingsPrefs)
            Log.d(TAG, "Voice settings applied")
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
            KEY_MEDIA_FILTERING_ENABLED, KEY_MEDIA_FILTER_EXCEPTED_APPS, KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS -> {
                // Reload media filtering settings
                loadFilterSettings()
                Log.d(TAG, "Media filtering settings updated")
            }
            KEY_PERSISTENT_FILTERING_ENABLED -> {
                // Reload persistent filtering settings
                isPersistentFilteringEnabled = sharedPreferences?.getBoolean(KEY_PERSISTENT_FILTERING_ENABLED, false) ?: false
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
        }
    }

    private fun matchesWordFilter(text: String, word: String): Boolean {
        return text.contains(word, ignoreCase = true)
    }
}