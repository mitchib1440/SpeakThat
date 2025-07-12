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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashMap
import java.util.HashSet
import java.util.Locale
import kotlin.collections.ArrayList
import org.json.JSONArray
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
    
    // Wave detection
    private var proximitySensor: Sensor? = null
    private var isWaveToStopEnabled = false
    private var waveThreshold = 5.0f
    
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
    
    // Conditional filtering (foundation for future advanced rules)
    private var conditionalFilterManager: ConditionalFilterManager? = null
    
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
            // Initialize SharedPreferences
            sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            
            // Register preference change listener to automatically reload settings
            sharedPreferences.registerOnSharedPreferenceChangeListener(this)
            
            // Initialize and register voice settings listener
            voiceSettingsPrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
            voiceSettingsPrefs?.registerOnSharedPreferenceChangeListener(voiceSettingsListener)
            
            // Initialize components with error handling
            try {
                initializeTextToSpeech()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing TextToSpeech", e)
                InAppLogger.logError("Service", "TextToSpeech initialization failed: " + e.message)
            }
            
            try {
                initializeShakeDetection()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing shake detection", e)
                InAppLogger.logError("Service", "Shake detection initialization failed: " + e.message)
            }
            
            try {
                loadFilterSettings()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading filter settings", e)
                InAppLogger.logError("Service", "Filter settings loading failed: " + e.message)
            }
            
            // Initialize conditional filter manager (foundation for future features)
            try {
                conditionalFilterManager = ConditionalFilterManager(this)
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing conditional filter manager", e)
                InAppLogger.logError("Service", "Conditional filter manager initialization failed: " + e.message)
            }
            
            // Initialize delay handler
            delayHandler = android.os.Handler(android.os.Looper.getMainLooper())
            
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
        textToSpeech = TextToSpeech(this, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported for TTS")
                // Fallback to English US
                textToSpeech?.setLanguage(Locale.US)
            }
            
            // Set audio stream to media volume
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            textToSpeech?.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            
            // Apply saved voice settings
            applyVoiceSettings()
            
            isTtsInitialized = true
            Log.d(TAG, "TextToSpeech initialized successfully")
        } else {
            Log.e(TAG, "TextToSpeech initialization failed")
        }
    }
    
    private fun getAppName(packageName: String): String {
        // First check for custom app name
        val customAppName = getCustomAppName(packageName)
        if (customAppName != null) {
            Log.d(TAG, "Using custom app name for $packageName: $customAppName")
            return customAppName
        }
        
        // Fall back to system app name
        return try {
            val packageManager = packageManager
            val appInfo: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "App name not found for package: $packageName")
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
        isShakeToStopEnabled = sharedPreferences.getBoolean(KEY_SHAKE_TO_STOP_ENABLED, false)
        shakeThreshold = sharedPreferences.getFloat(KEY_SHAKE_THRESHOLD, 12.0f)
        Log.d(TAG, "Shake settings loaded - enabled: $isShakeToStopEnabled, threshold: $shakeThreshold")
    }

    private fun loadWaveSettings() {
        isWaveToStopEnabled = sharedPreferences.getBoolean("wave_to_stop_enabled", false)
        waveThreshold = sharedPreferences.getFloat("wave_threshold", 5.0f)
        Log.d(TAG, "Wave settings loaded - enabled: $isWaveToStopEnabled, threshold: $waveThreshold")
    }
    
    private fun registerShakeListener() {
        if (isShakeToStopEnabled && accelerometer != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            Log.d(TAG, "Shake listener registered (TTS active)")
            InAppLogger.logSystemEvent("Shake listener started", "TTS playback active")
        }
        
        if (isWaveToStopEnabled && proximitySensor != null) {
            sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI)
            Log.d(TAG, "Wave listener registered (TTS active)")
            InAppLogger.logSystemEvent("Wave listener started", "TTS playback active")
        }
    }
    
    private fun unregisterShakeListener() {
        sensorManager?.unregisterListener(this)
        Log.d(TAG, "Shake and wave listeners unregistered (TTS inactive)")
        InAppLogger.logSystemEvent("Shake and wave listeners stopped", "TTS playback finished")
    }
    
    // Call this method when settings might have changed
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
    
    private fun loadFilterSettings() {
        appListMode = sharedPreferences.getString(KEY_APP_LIST_MODE, "none") ?: "none"
        appList = HashSet(sharedPreferences.getStringSet(KEY_APP_LIST, HashSet()) ?: HashSet())
        privateApps = HashSet(sharedPreferences.getStringSet(KEY_APP_PRIVATE_FLAGS, HashSet()) ?: HashSet())
        blockedWords = HashSet(sharedPreferences.getStringSet(KEY_WORD_BLACKLIST, HashSet()) ?: HashSet())
        privateWords = HashSet(sharedPreferences.getStringSet(KEY_WORD_BLACKLIST_PRIVATE, HashSet()) ?: HashSet())
        
        // Load word replacements
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
        
        // 2. Apply media notification filtering (if StatusBarNotification is available)
        if (sbn != null) {
            val mediaFilterResult = applyMediaFiltering(sbn)
            if (!mediaFilterResult.shouldSpeak) {
                return mediaFilterResult
            }
        }
        
        // 3. Apply persistent/silent notification filtering
        if (sbn != null && isPersistentFilteringEnabled) {
            val persistentFilterResult = applyPersistentFiltering(sbn)
            if (!persistentFilterResult.shouldSpeak) {
                return persistentFilterResult
            }
        }
        
        // 4. Apply word filtering and replacements
        val wordFilterResult = applyWordFiltering(text, appName)
        if (!wordFilterResult.shouldSpeak) {
            return wordFilterResult
        }
        
        // 5. Apply conditional rules (Smart Rules system)
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
        
        // 3. Apply word replacements
        for ((from, to) in wordReplacements) {
            processedText = processedText.replace(from, to, ignoreCase = true)
        }
        
        return FilterResult(true, processedText, "Word filtering applied")
    }
    
    /**
     * Apply conditional filtering rules to notifications
     * 
     * ═══════════════════════════════════════════════════════════════════════════════════
     * 📋 SESSION NOTES: SMART RULES INTEGRATION POINT
     * ═══════════════════════════════════════════════════════════════════════════════════
     * 
     * 🎯 STATUS: INTEGRATION HOOK READY (Dec 2024)
     * ✅ ConditionalFilterManager instance created in onCreate()
     * ✅ Integration point identified in applyFilters() method
     * ✅ Placeholder method created with detailed implementation plan
     * 
     * 🚀 NEXT SESSION: Remove TODO and implement real integration
     * 1. Uncomment the integration code below
     * 2. Test with example rules from ConditionalFilterManager
     * 3. Verify rule priority system works correctly
     * 4. Add logging for debugging rule application
     * 
     * 💡 IMPLEMENTATION PLAN:
     * - This method will be called for every notification
     * - ConditionalResult will modify notification behavior
     * - Rules are applied in priority order (high to low)
     * - Early exit optimization prevents unnecessary processing
     * 
     * This is a placeholder for the next development session
     */
    private fun applyConditionalFiltering(packageName: String, appName: String, text: String): FilterResult {
        // Create notification context for rule evaluation
        val context = ConditionalFilterManager.NotificationContext(appName, packageName, text)
        val conditionalResult = conditionalFilterManager?.applyConditionalRules(context)
        
        // Apply conditional rules if any were triggered
        if (conditionalResult?.hasChanges == true) {
            Log.d(TAG, "Conditional rules applied: ${conditionalResult.appliedRules}")
            InAppLogger.log("Conditional", "Applied rules: ${conditionalResult.appliedRules}")
            
            // Check if notification should be blocked
            if (conditionalResult.shouldBlock) {
                return FilterResult(false, text, "Blocked by conditional rule: ${conditionalResult.appliedRules}")
            }
            
            // Apply text modifications
            var modifiedText = conditionalResult.modifiedText ?: text
            if (conditionalResult.shouldMakePrivate) {
                modifiedText = "[PRIVATE]"
            }
            
            // Handle delay (will be processed in handleNotificationBehavior)
            if (conditionalResult.delaySeconds > 0) {
                Log.d(TAG, "Conditional rule applied ${conditionalResult.delaySeconds}s delay")
                InAppLogger.log("Conditional", "Applied ${conditionalResult.delaySeconds}s delay from rule")
            }
            
            return FilterResult(true, modifiedText, "Modified by conditional rules: ${conditionalResult.appliedRules}", conditionalResult.delaySeconds)
        }
        
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
                proximityValue <= waveThreshold
            }
            
            if (isTriggered) {
                Log.d(TAG, "Wave detected! Stopping TTS. Proximity value: $proximityValue cm, threshold: $waveThreshold cm")
                stopSpeaking("wave")
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

    private fun handleMediaBehavior(appName: String, text: String, sbn: StatusBarNotification? = null): Boolean {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val isMusicActive = audioManager.isMusicActive
        if (!isMusicActive) {
            Log.d(TAG, "No media currently playing, proceeding normally")
            return true
        }
        Log.d(TAG, "Media is playing, applying media behavior: $mediaBehavior")
        InAppLogger.log("MediaBehavior", "Media detected, applying behavior: $mediaBehavior")
        // UNIFIED FAIL-SAFE: If this is a media notification, block it no matter what
        if (sbn != null && MediaNotificationDetector.isMediaNotification(sbn)) {
            val reason = MediaNotificationDetector.getMediaDetectionReason(sbn)
            Log.d(TAG, "FAIL-SAFE: Notification detected as media during ducking. Blocking speech. Reason: $reason")
            InAppLogger.logFilter("FAIL-SAFE: Blocked notification from $appName due to ducking/media detection: $reason")
            // Log all extras for debugging
            val extras = sbn.notification.extras
            Log.d(TAG, "[FailSafe] Notification extras: $extras")
            return false // Block speech - never read out
        }
        when (mediaBehavior) {
            "ignore" -> {
                Log.d(TAG, "Media behavior: IGNORE - proceeding normally")
                return true
            }
            "pause" -> {
                Log.d(TAG, "Media behavior: PAUSE - requesting audio focus")
                return requestAudioFocusForSpeech()
            }
            "duck" -> {
                Log.d(TAG, "Media behavior: DUCK - lowering media volume to $duckingVolume%")
                InAppLogger.log("MediaBehavior", "Ducking media volume - notification will be spoken")
                return duckMediaVolume()
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
        isCurrentlySpeaking = true
        
        // Register shake listener now that we're about to speak
        registerShakeListener()
        
        // Speak with queue mode FLUSH to interrupt any previous speech
        textToSpeech?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "notification_utterance")
        
        // Set up completion listener
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // TTS started
                InAppLogger.logTTSEvent("TTS started", speechText.take(50))
            }
            
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "notification_utterance") {
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
                    isCurrentlySpeaking = false
                    unregisterShakeListener()
                    Log.e(TAG, "TTS error occurred")
                    InAppLogger.logTTSEvent("TTS error", "Utterance failed")
                    
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
            KEY_SHAKE_TO_STOP_ENABLED, KEY_SHAKE_THRESHOLD -> {
                // Reload shake settings
                refreshSettings()
                Log.d(TAG, "Shake settings updated")
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
        }
    }

    private fun matchesWordFilter(text: String, word: String): Boolean {
        return text.contains(word, ignoreCase = true)
    }
}