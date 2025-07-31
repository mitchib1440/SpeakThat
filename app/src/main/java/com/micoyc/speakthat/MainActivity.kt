package com.micoyc.speakthat

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.TextUtils
import android.util.Log
import com.micoyc.speakthat.VoiceSettingsActivity
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.micoyc.speakthat.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.util.Locale
import kotlin.random.Random
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener, SensorEventListener {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isFirstLogoTap = true
    private val easterEggLines = mutableListOf<String>()
    
    // Shake detection
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isShakeToStopEnabled = false
    private var shakeThreshold = 12.0f
    
    // Wave detection
    private var proximitySensor: Sensor? = null
    private var isWaveToStopEnabled = false
    private var waveThreshold = 5.0f
    
    // Voice settings listener
    private var voiceSettingsPrefs: SharedPreferences? = null
    private val voiceSettingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "speech_rate", "pitch", "voice_name", "language", "audio_usage", "content_type" -> {
                applyVoiceSettings()
                Log.d(TAG, "MainActivity voice settings updated: $key")
            }
        }
    }
    
    // Sensor timeout for safety
    private var sensorTimeoutHandler: Handler? = null
    private var sensorTimeoutRunnable: Runnable? = null
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "SpeakThatPrefs"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_FIRST_LOGO_TAP = "first_logo_tap"
        private const val KEY_SHAKE_TO_STOP_ENABLED = "shake_to_stop_enabled"
        private const val KEY_SHAKE_THRESHOLD = "shake_threshold"
        private const val KEY_MASTER_SWITCH_ENABLED = "master_switch_enabled"
        private const val KEY_LAST_EASTER_EGG = "last_easter_egg_line"
        // TRANSLATION BANNER - REMOVE WHEN NO LONGER NEEDED
        private const val KEY_TRANSLATION_BANNER_DISMISSED = "translation_banner_dismissed"
        @JvmField
        var isSensorListenerActive: Boolean = false
        /**
         * Check if the master switch is enabled (for use by NotificationReaderService)
         */
        fun isMasterSwitchEnabled(context: android.content.Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_MASTER_SWITCH_ENABLED, true) // Default to enabled
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if user has seen onboarding
        if (!OnboardingActivity.hasSeenOnboarding(this)) {
            // Start onboarding activity
            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
            finish() // Close MainActivity
            return
        }
        
        // Initialize crash-persistent logging first
        InAppLogger.initialize(this)
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        // Initialize voice settings listener
        voiceSettingsPrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
        voiceSettingsPrefs?.registerOnSharedPreferenceChangeListener(voiceSettingsListener)
        
        // Apply saved theme first
        applySavedTheme()
        
        // Initialize view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get version number
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        val versionText = getString(R.string.version_format, versionName)
        findViewById<TextView>(R.id.versionnumber).text = versionText

        // Configure system UI for proper insets handling
        configureSystemUI()
        
        // Log app lifecycle
        InAppLogger.logAppLifecycle("MainActivity created")
        InAppLogger.logSystemEvent("App started", "MainActivity")
        
        // Initialize components
        initializeShakeDetection()
        initializeTextToSpeech()
        loadEasterEggs()
        
        // Load logo tap state
        isFirstLogoTap = sharedPreferences.getBoolean(KEY_FIRST_LOGO_TAP, true)
        
        // Set up UI
        setupUI()
        updateServiceStatus()
        
        // Check for updates automatically (if enabled)
        Log.d(TAG, "About to check for updates automatically")
        checkForUpdatesIfEnabled()
    }
    
    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        
        // Refresh shake and wave settings in case they changed
        loadShakeSettings()
        loadWaveSettings()
        // Don't start shake/wave listening here - only during TTS playback
        InAppLogger.logAppLifecycle("MainActivity resumed")
        
        // Check for updates automatically when returning to app (if enabled)
        Log.d(TAG, "About to check for updates on resume")
        checkForUpdatesIfEnabled()
    }
    
    override fun onPause() {
        super.onPause()
        // Stop shake listening if active
        stopShakeListening()
        InAppLogger.logAppLifecycle("MainActivity paused")
    }
    
    private fun setupUI() {
        setupClickListeners()
        // TRANSLATION BANNER - REMOVE WHEN NO LONGER NEEDED
        setupTranslationBanner()
    }
    
    private fun setupClickListeners() {
        binding.buttonEnablePermission.setOnClickListener {
            InAppLogger.logUserAction("Permission button clicked")
            openNotificationListenerSettings()
        }
        
        // Notification history moved to Development Settings
        
        binding.buttonSettings.setOnClickListener {
            InAppLogger.logUserAction("Settings button clicked")
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        // Master switch functionality
        binding.switchMasterControl.setOnCheckedChangeListener { _, isChecked ->
            InAppLogger.logUserAction("Master switch toggled", "Enabled: $isChecked")
            handleMasterSwitchToggle(isChecked)
        }
        
        // Logo easter egg functionality
        binding.logoSpeakThat.setOnClickListener {
            InAppLogger.logUserAction("Logo clicked", "First tap: $isFirstLogoTap")
            handleLogoClick()
        }
        
        // TRANSLATION BANNER - REMOVE WHEN NO LONGER NEEDED
        // Translation banner dismiss button
        binding.buttonDismissTranslationBanner.setOnClickListener {
            InAppLogger.logUserAction("Translation banner dismissed")
            dismissTranslationBanner()
        }
        
        // Translation banner email functionality
        binding.cardTranslationHelp.setOnClickListener {
            InAppLogger.logUserAction("Translation banner clicked - opening email")
            sendTranslationEmail()
        }
    }
    
    private fun updateServiceStatus() {
        val isEnabled = isNotificationServiceEnabled()
        val isMasterEnabled = sharedPreferences.getBoolean(KEY_MASTER_SWITCH_ENABLED, true)
        
        // Update master switch state
        binding.switchMasterControl.setOnCheckedChangeListener(null) // Prevent infinite loop
        binding.switchMasterControl.isChecked = isMasterEnabled
        binding.switchMasterControl.setOnCheckedChangeListener { _, isChecked ->
            handleMasterSwitchToggle(isChecked)
        }
        
        // Update master switch status text
        if (isMasterEnabled) {
            binding.textMasterSwitchStatus.text = "SpeakThat will read notifications when active"
            binding.textMasterSwitchStatus.setTextColor(ContextCompat.getColor(this, R.color.purple_200))
        } else {
            binding.textMasterSwitchStatus.text = "SpeakThat is disabled - notifications will not be read"
            binding.textMasterSwitchStatus.setTextColor(ContextCompat.getColor(this, R.color.red_200))
        }
        
        // Update permission status
        if (isEnabled) {
            // Service is enabled - use brand color
            binding.textServiceStatus.text = getString(R.string.service_enabled)
            binding.textServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.purple_200))
            binding.textPermissionStatus.text = "SpeakThat has notification access"
            binding.buttonEnablePermission.text = "Disable Notification Access"
        } else {
            // Service is disabled
            binding.textServiceStatus.text = getString(R.string.service_disabled)
            binding.textServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.red_200))
            binding.textPermissionStatus.text = getString(R.string.permission_description)
            binding.buttonEnablePermission.text = getString(R.string.open_settings)
        }
    }
    
    private fun isNotificationServiceEnabled(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":").toTypedArray()
            for (name in names) {
                val componentName = ComponentName.unflattenFromString(name)
                val nameMatch = TextUtils.equals(packageName, componentName?.packageName)
                if (nameMatch) {
                    return true
                }
            }
        }
        return false
    }
    
    private fun openNotificationListenerSettings() {
        InAppLogger.logSystemEvent("Opening notification listener settings")
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }
    
    private fun handleMasterSwitchToggle(isEnabled: Boolean) {
        // Save the master switch state
        sharedPreferences.edit().putBoolean(KEY_MASTER_SWITCH_ENABLED, isEnabled).apply()
        
        // Update UI immediately
        updateServiceStatus()
        
        // Log the change
        InAppLogger.logSettingsChange("Master Switch", (!isEnabled).toString(), isEnabled.toString())
        InAppLogger.log("MasterSwitch", "Master switch ${if (isEnabled) "enabled" else "disabled"}")
        
        // Show feedback to user
        val message = if (isEnabled) {
            "SpeakThat enabled - notifications will be read"
        } else {
            "SpeakThat disabled - notifications will be silent"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        
        Log.d(TAG, "Master switch toggled: $isEnabled")
    }
    
    // showNotificationHistory moved to DevelopmentSettingsActivity
    
    private fun applySavedTheme() {
        val isDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false) // Default to light mode
        
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
    
    private fun configureSystemUI() {
        // Set up proper window insets handling for different Android versions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // For Android 11+ (API 30+), use the new window insets API
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(true)
        } else {
            // For older versions (Android 10 and below), ensure proper system UI flags
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }
    
    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this, this)
    }
    
    private fun loadEasterEggs() {
        try {
            val inputStream = assets.open("logo_easter_eggs.txt")
            val reader = BufferedReader(inputStream.reader())
            
            reader.useLines { lines ->
                lines.forEach { line ->
                    val trimmedLine = line.trim()
                    // Skip empty lines and comments (lines starting with #)
                    if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                        easterEggLines.add(trimmedLine)
                    }
                }
            }
            
            Log.d(TAG, "Loaded ${easterEggLines.size} easter egg lines")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading easter eggs", e)
        }
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Apply saved voice settings (which will handle language/voice selection)
            applyVoiceSettings()
            
            isTtsInitialized = true
            InAppLogger.logTTSEvent("TTS initialized successfully", "MainActivity")
            Log.d(TAG, "TextToSpeech initialized successfully for MainActivity")
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status: $status")
            InAppLogger.logTTSEvent("TTS initialization failed", "Status: $status")
            
            // Log device info for debugging
            val deviceInfo = "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, " +
                           "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
            Log.e(TAG, "Device info: $deviceInfo")
            InAppLogger.logError("MainActivity", "TTS init failed on $deviceInfo")
            
            // Show user feedback
            Toast.makeText(this, "TTS initialization failed. Some features may not work.", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun handleLogoClick() {
        // Log TTS status for debugging
        val ttsStatus = checkTtsStatus()
        InAppLogger.log("MainActivity", "Logo clicked - $ttsStatus")
        
        // Log wave-to-stop settings for debugging
        val waveEnabled = sharedPreferences.getBoolean("wave_to_stop_enabled", false)
        val waveThreshold = sharedPreferences.getFloat("wave_threshold", 3.0f)
        InAppLogger.log("MainActivity", "Wave-to-stop settings - enabled: $waveEnabled, threshold: ${waveThreshold}cm")
        
        if (!isTtsInitialized) {
            Log.w(TAG, "TTS not initialized, cannot play logo easter egg")
            InAppLogger.logError("MainActivity", "Logo click failed - TTS not initialized")
            
            // Try to reinitialize TTS if it failed
            if (textToSpeech == null) {
                Log.d(TAG, "Attempting to reinitialize TTS for logo click")
                initializeTextToSpeech()
            }
            
            // Show user feedback
            Toast.makeText(this, "TTS not ready. Please try again in a moment.", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (isFirstLogoTap) {
            // First tap: Play instructional message
            val baseText = "This is SpeakThat! The notification reader. This is how incoming notifications will be announced."
            
            // Check if shake-to-stop is enabled
            val isShakeToStopEnabled = sharedPreferences.getBoolean(KEY_SHAKE_TO_STOP_ENABLED, false)
            
            val instructionText = if (isShakeToStopEnabled) {
                "$baseText This is a great opportunity to test your Shake-to-Stop settings. Go ahead and shake your device to stop me talking."
            } else {
                baseText
            }
            
            speakText(instructionText)
            
            // Mark that first tap has occurred
            isFirstLogoTap = false
            sharedPreferences.edit().putBoolean(KEY_FIRST_LOGO_TAP, false).apply()
            
        } else {
            // Subsequent taps: Play random easter egg (avoiding repeats)
            if (easterEggLines.isNotEmpty()) {
                val availableLines = getAvailableEasterEggs()
                if (availableLines.isNotEmpty()) {
                    val selectedLine = selectNonRepeatingEasterEgg(availableLines)
                    val processedLine = processDynamicEasterEgg(selectedLine)
                    speakText(processedLine)
                    
                    // Store the original line (before processing) to prevent repeats
                    sharedPreferences.edit().putString(KEY_LAST_EASTER_EGG, selectedLine).apply()
                    
                    Log.d(TAG, "Playing easter egg: $processedLine")
                } else {
                    speakText("SpeakThat! All my jokes are context-sensitive and none apply right now. That's meta!")
                }
            } else {
                speakText("Oops! No easter eggs found. That's an easter egg in itself!")
            }
        }
    }
    
    private fun speakText(text: String) {
        if (isTtsInitialized && textToSpeech != null) {
            // Register shake listener for this TTS session
            startShakeListening()
            
            // Set up completion listener to stop shake listening when done
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    InAppLogger.logTTSEvent("MainActivity TTS started", text.take(50))
                }
                
                override fun onDone(utteranceId: String?) {
                    // Stop shake listening when TTS completes
                    stopShakeListening()
                    InAppLogger.logTTSEvent("MainActivity TTS completed", "Easter egg finished")
                }
                
                override fun onError(utteranceId: String?) {
                    // Stop shake listening even on error
                    stopShakeListening()
                    InAppLogger.logTTSEvent("MainActivity TTS error", "Easter egg failed")
                    Log.e(TAG, "TTS error during easter egg playback")
                }
            })
            
            val result = textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "easter_egg")
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "TTS speak() returned ERROR")
                InAppLogger.logError("MainActivity", "TTS speak() failed")
                Toast.makeText(this, "TTS playback failed", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e(TAG, "Cannot speak text - TTS not ready. Initialized: $isTtsInitialized, TTS: ${textToSpeech != null}")
            InAppLogger.logError("MainActivity", "TTS not ready for speech - initialized: $isTtsInitialized")
        }
    }
    
    private fun initializeShakeDetection() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        
        // Load shake and wave settings
        loadShakeSettings()
        loadWaveSettings()
    }
    
    private fun loadShakeSettings() {
        isShakeToStopEnabled = sharedPreferences.getBoolean(KEY_SHAKE_TO_STOP_ENABLED, true)
        shakeThreshold = sharedPreferences.getFloat(KEY_SHAKE_THRESHOLD, 12.0f)
        Log.d(TAG, "MainActivity shake settings - enabled: $isShakeToStopEnabled, threshold: $shakeThreshold")
    }

    private fun loadWaveSettings() {
        isWaveToStopEnabled = sharedPreferences.getBoolean("wave_to_stop_enabled", false)
        // Use calibrated threshold if available, otherwise fall back to old system
        waveThreshold = if (sharedPreferences.contains("wave_threshold_v1")) {
            sharedPreferences.getFloat("wave_threshold_v1", 3.0f)
        } else {
            sharedPreferences.getFloat("wave_threshold", 3.0f)
        }
        Log.d(TAG, "MainActivity wave settings - enabled: $isWaveToStopEnabled, threshold: $waveThreshold")
    }
    
    private fun startShakeListening() {
        Log.d(TAG, "startShakeListening called - shake enabled: $isShakeToStopEnabled, wave enabled: $isWaveToStopEnabled")
        
        if (isShakeToStopEnabled && accelerometer != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "MainActivity shake listener started")
            InAppLogger.log("MainActivity", "Shake listener registered")
        }
        
        if (isWaveToStopEnabled && proximitySensor != null) {
            sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "MainActivity wave listener started")
            InAppLogger.log("MainActivity", "Wave listener registered")
        } else {
            Log.d(TAG, "Wave listener NOT started - enabled: $isWaveToStopEnabled, sensor: ${proximitySensor != null}")
            InAppLogger.log("MainActivity", "Wave listener NOT registered - enabled: $isWaveToStopEnabled")
        }
        // Start hard timeout
        if (sensorTimeoutHandler == null) {
            sensorTimeoutHandler = Handler(Looper.getMainLooper())
        }
        // Cancel any previous timeout
        sensorTimeoutRunnable?.let { sensorTimeoutHandler?.removeCallbacks(it) }
        sensorTimeoutRunnable = Runnable {
            Log.w(TAG, "Sensor listener timeout reached! Forcibly unregistering sensors.")
            InAppLogger.log("MainActivity", "Sensor listener timeout reached! Forcibly unregistering sensors.")
            stopShakeListening()
        }
        sensorTimeoutHandler?.postDelayed(sensorTimeoutRunnable!!, 30_000) // 30 seconds
        isSensorListenerActive = true
    }
    
    private fun stopShakeListening() {
        sensorManager?.unregisterListener(this)
        Log.d(TAG, "MainActivity shake listener stopped")
        // Cancel timeout
        sensorTimeoutRunnable?.let { sensorTimeoutHandler?.removeCallbacks(it) }
        sensorTimeoutRunnable = null
        isSensorListenerActive = false
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        // Log all sensor events for debugging
        Log.d(TAG, "Sensor event: ${event.sensor.type} (shake enabled: $isShakeToStopEnabled, wave enabled: $isWaveToStopEnabled)")
        
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && isShakeToStopEnabled) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            // Calculate total acceleration (subtract gravity)
            val shakeValue = kotlin.math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
            
            // Check if shake threshold exceeded
            if (shakeValue >= shakeThreshold) {
                Log.d(TAG, "Shake detected in MainActivity! Stopping TTS. Shake value: $shakeValue")
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
            
            if (isTriggered) {
                val maxRange = proximitySensor?.maximumRange ?: 5.0f
                val significantChange = maxRange * 0.3f
                val distanceFromMax = maxRange - proximityValue
                
                Log.d(TAG, "Wave detected in MainActivity! Stopping TTS. Proximity: $proximityValue cm, threshold: $waveThreshold cm, maxRange: $maxRange cm, distanceFromMax: $distanceFromMax cm")
                InAppLogger.log("MainActivity", "Wave detected - proximity: ${proximityValue}cm, threshold: ${waveThreshold}cm, maxRange: ${maxRange}cm")
                stopSpeaking("wave")
            } else {
                // Log proximity values for debugging (but not too frequently)
                if (System.currentTimeMillis() % 1000 < 100) { // Log ~10% of the time
                    val maxRange = proximitySensor?.maximumRange ?: 5.0f
                    val significantChange = maxRange * 0.3f
                    val distanceFromMax = maxRange - proximityValue
                    Log.d(TAG, "Proximity sensor reading: $proximityValue cm (threshold: $waveThreshold cm, maxRange: $maxRange cm, distanceFromMax: $distanceFromMax cm)")
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
    
    private fun stopSpeaking(triggerType: String = "unknown") {
        textToSpeech?.stop()
        stopShakeListening()
        InAppLogger.logTTSEvent("MainActivity TTS stopped by $triggerType", "User interrupted easter egg")
        Log.d(TAG, "MainActivity TTS stopped due to $triggerType")
    }
    
    private fun applyVoiceSettings() {
        textToSpeech?.let { tts ->
            val voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
            VoiceSettingsActivity.applyVoiceSettings(tts, voicePrefs)
            Log.d(TAG, "MainActivity voice settings applied")
        }
    }
    
    /**
     * Check TTS status and log diagnostic information
     */
    private fun checkTtsStatus(): String {
        val status = StringBuilder()
        status.append("TTS Status:\n")
        status.append("- Initialized: $isTtsInitialized\n")
        status.append("- TTS instance: ${if (textToSpeech != null) "Present" else "Null"}\n")
        
        if (textToSpeech != null) {
            try {
                val available = textToSpeech?.isLanguageAvailable(Locale.getDefault()) ?: TextToSpeech.LANG_NOT_SUPPORTED
                status.append("- Default language available: ${when(available) {
                    TextToSpeech.LANG_AVAILABLE -> "Yes"
                    TextToSpeech.LANG_COUNTRY_AVAILABLE -> "Yes (Country)"
                    TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "Yes (Country Variant)"
                    TextToSpeech.LANG_MISSING_DATA -> "Missing Data"
                    TextToSpeech.LANG_NOT_SUPPORTED -> "Not Supported"
                    else -> "Unknown ($available)"
                }}\n")
                
                val engines = textToSpeech?.engines?.size ?: 0
                status.append("- Available engines: $engines\n")
            } catch (e: Exception) {
                status.append("- Error checking TTS: ${e.message}\n")
            }
        }
        
        val deviceInfo = "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, " +
                        "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
        status.append("- $deviceInfo")
        
        Log.d(TAG, status.toString())
        return status.toString()
    }
    
    private fun getAvailableEasterEggs(): List<String> {
        // Filter easter eggs based on current settings
        val delayEnabled = sharedPreferences.getInt("delay_before_readout", 0) > 0
        val shakeEnabled = sharedPreferences.getBoolean(KEY_SHAKE_TO_STOP_ENABLED, false)
        val waveEnabled = sharedPreferences.getBoolean("wave_to_stop_enabled", false)
        
        return easterEggLines.filter { line ->
            when {
                // Lines with [DELAY_ONLY] tag only show if delay is enabled
                line.contains("[DELAY_ONLY]") && !delayEnabled -> false
                // Lines with [SHAKE_ONLY] tag only show if shake is enabled  
                line.contains("[SHAKE_ONLY]") && !shakeEnabled -> false
                // Lines with [WAVE_ONLY] tag only show if wave is enabled
                line.contains("[WAVE_ONLY]") && !waveEnabled -> false
                // Lines with [NO_DELAY] tag only show if delay is disabled
                line.contains("[NO_DELAY]") && delayEnabled -> false
                // All other lines are always available
                else -> true
            }
        }
    }
    
    private fun selectNonRepeatingEasterEgg(availableLines: List<String>): String {
        // Get the last spoken easter egg line
        val lastEasterEgg = sharedPreferences.getString(KEY_LAST_EASTER_EGG, null)
        
        // If we only have one line available, or no previous line stored, just pick randomly
        if (availableLines.size <= 1 || lastEasterEgg == null) {
            return availableLines[Random.nextInt(availableLines.size)]
        }
        
        // Filter out the last spoken line to avoid back-to-back repeats
        val nonRepeatingLines = availableLines.filter { it != lastEasterEgg }
        
        // If filtering removed all lines (shouldn't happen, but safety check), fall back to all available
        return if (nonRepeatingLines.isNotEmpty()) {
            nonRepeatingLines[Random.nextInt(nonRepeatingLines.size)]
        } else {
            availableLines[Random.nextInt(availableLines.size)]
        }
    }
    
    private fun processDynamicEasterEgg(line: String): String {
        var processedLine = line
        
        // Remove conditional tags from the final output
        processedLine = processedLine.replace("[DELAY_ONLY]", "")
        processedLine = processedLine.replace("[SHAKE_ONLY]", "")
        processedLine = processedLine.replace("[WAVE_ONLY]", "")
        processedLine = processedLine.replace("[NO_DELAY]", "")
        
        // Process dynamic placeholders
        processedLine = processedLine.replace("{DAY}", getCurrentDayOfWeek())
        processedLine = processedLine.replace("{TIME}", getCurrentTimeOfDay())
        processedLine = processedLine.replace("{DELAY_TIME}", getCurrentDelayTime())
        processedLine = processedLine.replace("{RANDOM_FOOD}", getRandomFood())
        processedLine = processedLine.replace("{RANDOM_COLOR}", getRandomColor())
        
        return processedLine.trim()
    }
    
    private fun getCurrentDayOfWeek(): String {
        val calendar = java.util.Calendar.getInstance()
        val dayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        return dayNames[calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1]
    }
    
    private fun getCurrentTimeOfDay(): String {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            hour < 6 -> "early morning"
            hour < 12 -> "morning"
            hour < 17 -> "afternoon"
            hour < 21 -> "evening"
            else -> "night"
        }
    }
    
    private fun getCurrentDelayTime(): String {
        val delaySeconds = sharedPreferences.getInt("delay_before_readout", 0)
        return when (delaySeconds) {
            0 -> "no delay"
            1 -> "1-second delay"
            else -> "${delaySeconds}-second delay"
        }
    }
    
    private fun getRandomFood(): String {
        val foods = arrayOf("pizza", "tacos", "sushi", "burgers", "ice cream", "chocolate", "coffee", "donuts")
        return foods[Random.nextInt(foods.size)]
    }
    
    private fun getRandomColor(): String {
        val colors = arrayOf("red", "blue", "green", "purple", "orange", "yellow", "pink", "cyan", "white", "black", "pleurigloss")
        return colors[Random.nextInt(colors.size)]
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                InAppLogger.logUserAction("About menu item clicked")
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Unregister voice settings listener
        voiceSettingsPrefs?.unregisterOnSharedPreferenceChangeListener(voiceSettingsListener)
        
        textToSpeech?.shutdown()
        stopShakeListening()
    }
    
    /**
     * Check for updates automatically if enabled in settings
     * This runs silently in the background without user interaction
     */
    private fun checkForUpdatesIfEnabled() {
        Log.d(TAG, "checkForUpdatesIfEnabled() called")
        
        // Check if auto-update is enabled
        val autoUpdateEnabled = sharedPreferences.getBoolean("auto_update_enabled", true)
        Log.d(TAG, "Auto-update enabled: $autoUpdateEnabled")
        
        if (!autoUpdateEnabled) {
            Log.d(TAG, "Auto-update disabled, skipping update check")
            return
        }
        
        // Check if enough time has passed since last check
        val updateManager = UpdateManager.getInstance(this)
        val shouldCheck = updateManager.shouldCheckForUpdates()
        Log.d(TAG, "Should check for updates: $shouldCheck")
        
        if (!shouldCheck) {
            Log.d(TAG, "Update check interval not met, skipping update check")
            return
        }
        
        // Check if we've already notified about the current version
        val lastCheckedVersion = sharedPreferences.getString("last_checked_version", "")
        if (lastCheckedVersion != null && updateManager.hasNotifiedAboutVersion(lastCheckedVersion)) {
            Log.d(TAG, "Already notified about version $lastCheckedVersion, skipping update check")
            return
        }
        
        // CRITICAL: Check if app was installed from Google Play Store
        if (updateManager.isInstalledFromGooglePlay()) {
            Log.i(TAG, "App installed from Google Play Store - GitHub updates disabled")
            InAppLogger.logSystemEvent("Update check blocked - Google Play installation detected", "MainActivity")
            
            // Show Google Play message to user (only once per session)
            val googlePlayMessageShown = sharedPreferences.getBoolean("google_play_message_shown", false)
            if (!googlePlayMessageShown) {
                runOnUiThread {
                    showGooglePlayUpdateMessage()
                }
                // Mark message as shown for this session
                sharedPreferences.edit().putBoolean("google_play_message_shown", true).apply()
            }
            return
        }
        
        // Perform the update check in background
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Performing automatic update check")
                val updateInfo = updateManager.checkForUpdates()
                
                if (updateInfo != null) {
                    Log.i(TAG, "Update available: ${updateInfo.versionName}")
                    InAppLogger.logSystemEvent("Update available: ${updateInfo.versionName}", "MainActivity")
                    
                    // Show update notification to user
                    showUpdateNotification(updateInfo)
                } else {
                    Log.d(TAG, "No update available")
                    // Show success toast
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "You're using the latest version!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during automatic update check", e)
                InAppLogger.logSystemEvent("Update check failed: ${e.message}", "MainActivity")
                
                // Show error toast
                runOnUiThread {
                    val errorMessage = when {
                        e.message?.contains("network", ignoreCase = true) == true -> "Unable to check for updates! - Network error"
                        e.message?.contains("timeout", ignoreCase = true) == true -> "Unable to check for updates! - Connection timeout"
                        else -> "Unable to check for updates! - ${e.message ?: "Unknown error"}"
                    }
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    /**
     * Show update notification to user
     */
    private fun showUpdateNotification(updateInfo: UpdateManager.UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available, updateInfo.versionName))
            .setMessage(getString(R.string.update_info, 
                updateInfo.versionName,
                formatFileSize(updateInfo.fileSize),
                updateInfo.releaseDate
            ))
            .setPositiveButton(getString(R.string.download_update)) { _, _ ->
                // Start update activity
                val intent = Intent(this, UpdateActivity::class.java)
                intent.putExtra("force_check", true)
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .setNeutralButton(getString(R.string.view_release_notes)) { _, _ ->
                // Show release notes
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.release_notes))
                    .setMessage(updateInfo.releaseNotes.ifEmpty { getString(R.string.no_release_notes) })
                    .setPositiveButton(getString(R.string.ok), null)
                    .show()
            }
            .show()
    }
    
    /**
     * Format file size for display
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    /**
     * Show Google Play update message to users who installed from Google Play Store
     * This explains why GitHub updates are disabled and directs them to Google Play
     */
    private fun showGooglePlayUpdateMessage() {
        AlertDialog.Builder(this)
            .setTitle("Updates via Google Play")
            .setMessage("You installed SpeakThat from Google Play Store. " +
                "For security and policy compliance, automatic updates are handled through Google Play.\n\n" +
                "To update the app, please visit Google Play Store and check for updates there.\n\n" +
                "This ensures you receive verified, secure updates that comply with Google Play policies.")
            .setPositiveButton("Open Google Play") { _, _ ->
                try {
                    // Open Google Play Store to the app's page
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("market://details?id=${packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to web browser if Play Store app is not available
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=${packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            }
            .setNegativeButton("OK", null)
            .setCancelable(true)
            .show()
        
        Log.i(TAG, "Showed Google Play update message to user")
        InAppLogger.logSystemEvent("Google Play update message shown to user", "MainActivity")
    }

    // TRANSLATION BANNER - REMOVE WHEN NO LONGER NEEDED
    private fun setupTranslationBanner() {
        val isDismissed = sharedPreferences.getBoolean(KEY_TRANSLATION_BANNER_DISMISSED, false)
        if (!isDismissed) {
            binding.cardTranslationHelp.visibility = View.VISIBLE
        } else {
            binding.cardTranslationHelp.visibility = View.GONE
        }
    }

    private fun dismissTranslationBanner() {
        binding.cardTranslationHelp.visibility = View.GONE
        sharedPreferences.edit().putBoolean(KEY_TRANSLATION_BANNER_DISMISSED, true).apply()
        InAppLogger.logUserAction("Translation banner dismissed")
    }
    
    // TRANSLATION BANNER - REMOVE WHEN NO LONGER NEEDED
    private fun sendTranslationEmail() {
        try {
            val subject = "SpeakThat! Translations"
            val recipient = "micoycbusiness@gmail.com"
            
            val bodyBuilder = StringBuilder()
            bodyBuilder.append("Hello SpeakThat! Team,\n\n")
            bodyBuilder.append("I would like to help translate SpeakThat! to my language.\n\n")
            bodyBuilder.append("Languages I speak:\n")
            bodyBuilder.append("[Please list the languages you speak and your fluency level - e.g., 'English (native), Spanish (fluent), French (conversational)']\n\n")
            bodyBuilder.append("Which language(s) would you like to help translate?\n")
            bodyBuilder.append("[Please specify which language(s) you'd like to translate SpeakThat! into]\n\n")
            bodyBuilder.append("Additional information:\n")
            bodyBuilder.append("[Any other relevant information about your translation experience or preferences]\n\n")
            bodyBuilder.append("Thank you for helping make SpeakThat! available to more users!\n")
            bodyBuilder.append("- SpeakThat! User")
            
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, bodyBuilder.toString())
            }
            
            try {
                startActivity(emailIntent)
                InAppLogger.logUserAction("Translation email opened")
            } catch (e: Exception) {
                // Fallback: try with chooser
                try {
                    startActivity(Intent.createChooser(emailIntent, "Send translation email"))
                    InAppLogger.logUserAction("Translation email opened via chooser")
                } catch (e2: Exception) {
                    Toast.makeText(this, "No email app found. Please install an email app to send translation requests.", Toast.LENGTH_LONG).show()
                    InAppLogger.logError("Translation", "No email app available: ${e2.message}")
                }
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening email: ${e.message}", Toast.LENGTH_LONG).show()
            InAppLogger.logCrash(e, "Translation email")
        }
    }
} 