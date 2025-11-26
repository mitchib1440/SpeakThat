package com.micoyc.speakthat

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
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
import androidx.core.app.NotificationCompat
import com.micoyc.speakthat.VoiceSettingsActivity
import com.micoyc.speakthat.automation.AutomationMode
import com.micoyc.speakthat.automation.AutomationModeManager
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
import android.graphics.drawable.AnimatedVectorDrawable
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.animation.ObjectAnimator

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener, SensorEventListener {
    
    private lateinit var binding: ActivityMainBinding
    private var sharedPreferences: SharedPreferences? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isFirstLogoTap = true
    private val easterEggLines = mutableListOf<String>()
    private var animatedLogo: AnimatedVectorDrawable? = null
    
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
            "speech_rate", "pitch", "voice_name", "audio_usage", "content_type" -> {
                applyVoiceSettings()
                Log.d(TAG, "MainActivity voice settings updated: $key")
            }
            "language", "tts_language" -> {
                // For language changes, we need to reinitialize TTS to ensure it uses the new language
                InAppLogger.log("MainActivity", "Language settings changed - reinitializing TTS")
                reinitializeTtsWithCurrentSettings()
            }
        }
    }
    
    /**
     * SharedPreferences listener to sync UI when Quick Settings tile changes the master switch
     * This ensures the main app's UI stays in sync even when the app is in the background
     */
    private val masterSwitchListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_MASTER_SWITCH_ENABLED) {
            // Check if we need to update the UI
            val currentState = isMasterSwitchEnabled(this)
            val switchState = binding.switchMasterControl.isChecked
            
            if (currentState != switchState) {
                Log.d(TAG, "MainActivity: Detected master switch change via SharedPreferences - current: $currentState, switch: $switchState")
                
                // Update UI on main thread
                runOnUiThread {
                    // Temporarily remove listener to prevent infinite loop
                    binding.switchMasterControl.setOnCheckedChangeListener(null)
                    
                    // Update switch to match current state
                    binding.switchMasterControl.isChecked = currentState
                    
                    // Update status text
                    if (currentState) {
                        binding.textMasterSwitchStatus.text = getString(R.string.main_master_switch_enabled)
                        binding.textMasterSwitchStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.purple_200))
                    } else {
                        binding.textMasterSwitchStatus.text = getString(R.string.main_master_switch_disabled)
                        binding.textMasterSwitchStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.red_200))
                    }
                    
                    // Restore listener
                    binding.switchMasterControl.setOnCheckedChangeListener { _, isChecked ->
                        handleMasterSwitchToggle(isChecked)
                    }
                    
                    Log.d(TAG, "MainActivity: UI synced via SharedPreferences listener")
                    InAppLogger.log("QuickSettingsSync", "MainActivity UI synced via SharedPreferences: $currentState")
                }
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
        private const val KEY_WAVE_TO_STOP_ENABLED = "wave_to_stop_enabled"
        private const val KEY_SHAKE_THRESHOLD = "shake_threshold"
        private const val KEY_MASTER_SWITCH_ENABLED = "master_switch_enabled"
        private const val KEY_LAST_EASTER_EGG = "last_easter_egg_line"
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
        // TRANSLATION BANNER - REMOVE WHEN NO LONGER NEEDED
    
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
        
        // Register master switch listener for Quick Settings tile sync
        sharedPreferences?.registerOnSharedPreferenceChangeListener(masterSwitchListener)
        
        // Apply saved theme first
        applySavedTheme()
        
        // Apply saved language/locale setting
        applySavedLanguage()
        
        // Initialize view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get version number with build variant
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val buildVariant = InAppLogger.getBuildVariantInfo()
        val versionText = getString(R.string.version_format_with_variant, packageInfo.versionName, buildVariant)
        findViewById<TextView>(R.id.versionnumber).text = versionText

        // Configure system UI for proper insets handling
        configureSystemUI()
        
        // Log app lifecycle and build variant
        InAppLogger.logAppLifecycle("MainActivity created")
        InAppLogger.logSystemEvent("App started", "MainActivity")
        InAppLogger.logSystemEvent("Build variant", InAppLogger.getBuildVariantInfo())
        
        // Log Quick Settings tile status
        QuickSettingsHelper.logTileStatus(this)
        
        // Initialize components
        initializeShakeDetection()
        initializeTextToSpeech()
        loadEasterEggs()
        
        // Load logo tap state
        isFirstLogoTap = sharedPreferences?.getBoolean(KEY_FIRST_LOGO_TAP, true) ?: true
        
        // Set up UI
        setupUI()
        updateServiceStatus()
        
        // Check for updates automatically (if enabled)
        Log.d(TAG, "About to check for updates automatically")
        checkForUpdatesIfEnabled()
        
        // Check for crash logs and notify user if available
        checkForCrashLogs()
        
        // Initialize review reminder and track app session
        initializeReviewReminder()
    }
    

    
    override fun onPause() {
        super.onPause()
        // Stop shake listening if active
        stopShakeListening()
        InAppLogger.logAppLifecycle("MainActivity paused")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Unregister voice settings listener
        voiceSettingsPrefs?.unregisterOnSharedPreferenceChangeListener(voiceSettingsListener)
        
        // Unregister master switch listener (safely handle null case)
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(masterSwitchListener)
        
        // Clean up TTS
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        
        // Clean up sensors
        sensorManager?.unregisterListener(this)
        
        InAppLogger.logAppLifecycle("MainActivity destroyed")
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
        
        // Update statistics display
        updateStatisticsDisplay()

    }
    
    private fun setupUI() {
        setupClickListeners()
        setupAnimatedLogo()
        // TRANSLATION BANNER - REMOVE WHEN NO LONGER NEEDED

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
        

    }
    
    private fun setupAnimatedLogo() {
        try {
            // Initialize the animated logo drawable
            animatedLogo = ContextCompat.getDrawable(this, R.drawable.logo_speakthat_animated) as? AnimatedVectorDrawable
            if (animatedLogo != null) {
                Log.d(TAG, "Animated logo initialized successfully")
                InAppLogger.log("MainActivity", "Animated logo setup successful")
            } else {
                Log.w(TAG, "Failed to initialize animated logo")
                InAppLogger.logError("MainActivity", "Failed to initialize animated logo")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up animated logo", e)
            InAppLogger.logError("MainActivity", "Error setting up animated logo: ${e.message}")
        }
    }
    
    private fun triggerLogoAnimation() {
        try {
            // Randomly select one of several animated logo variants for variety
            val animatedLogoVariants = arrayOf(
                R.drawable.logo_speakthat_animated,
                R.drawable.logo_speakthat_animated_variant1,
                R.drawable.logo_speakthat_animated_variant2,
                R.drawable.logo_speakthat_animated_variant3
            )
            
            val randomVariant = animatedLogoVariants.random()
            val animatedLogo = ContextCompat.getDrawable(this, randomVariant) as? AnimatedVectorDrawable
            
            if (animatedLogo != null) {
                // Set the animated logo as the ImageView's drawable
                binding.logoSpeakThat.setImageDrawable(animatedLogo)
                
                // Start the animation
                animatedLogo.start()
                
                // Log the animation start
                Log.d(TAG, "Logo animation started with bright colors (variant: $randomVariant)")
                InAppLogger.log("MainActivity", "Logo animation triggered with variant: $randomVariant")
                
                // Reset to static logo after animation completes (approximately 2 seconds)
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        binding.logoSpeakThat.setImageResource(R.drawable.logo_speakthat)
                        Log.d(TAG, "Logo animation completed, reset to static")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error resetting logo to static", e)
                    }
                }, 2000) // 2 seconds delay
            } else {
                Log.w(TAG, "Animated logo is null, cannot trigger animation")
                InAppLogger.logError("MainActivity", "Animated logo is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering logo animation", e)
            InAppLogger.logError("MainActivity", "Error triggering logo animation: ${e.message}")
        }
    }
    
    private fun updateServiceStatus() {
        val isEnabled = isNotificationServiceEnabled()
        val isMasterEnabled = sharedPreferences?.getBoolean(KEY_MASTER_SWITCH_ENABLED, true) ?: true
        
        // Update master switch state
        binding.switchMasterControl.setOnCheckedChangeListener(null) // Prevent infinite loop
        binding.switchMasterControl.isChecked = isMasterEnabled
        binding.switchMasterControl.setOnCheckedChangeListener { _, isChecked ->
            handleMasterSwitchToggle(isChecked)
        }
        
        // Update master switch status text
        if (isMasterEnabled) {
            binding.textMasterSwitchStatus.text = getString(R.string.main_master_switch_enabled)
            binding.textMasterSwitchStatus.setTextColor(ContextCompat.getColor(this, R.color.purple_card_text_secondary))
        } else {
            binding.textMasterSwitchStatus.text = getString(R.string.main_master_switch_disabled)
            binding.textMasterSwitchStatus.setTextColor(ContextCompat.getColor(this, R.color.purple_card_text_secondary))
        }
        
        // Update permission status with icon indicators
        if (isEnabled) {
            // Service is enabled - use check icon
            binding.iconPermissionStatus.setImageResource(R.drawable.check_24px)
            binding.textPermissionStatus.text = getString(R.string.main_notification_access_granted)
        } else {
            // Service is disabled - use close icon
            binding.iconPermissionStatus.setImageResource(R.drawable.close_24px)
            binding.textPermissionStatus.text = getString(R.string.permission_description)
        }
        
        // Always show "Permission Settings" for consistent button sizing
        binding.buttonEnablePermission.text = getString(R.string.open_settings)

    }
    
    private fun updateStatisticsDisplay() {
        try {
            val statsManager = StatisticsManager.getInstance(this)
            val notificationsRead = statsManager.getNotificationsRead()
            binding.textStatistics.text = getString(R.string.statistics_notifications_read, notificationsRead)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating statistics display", e)
            binding.textStatistics.text = ""
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
        // Update persistence and notification side-effects
        MasterSwitchController.setEnabled(this, isEnabled, "MainActivityToggle")
        
        // Update UI immediately
        updateServiceStatus()
        
        // Show feedback to user if toast is enabled
        val prefs = getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
        val showToast = prefs.getBoolean(MasterSwitchController.KEY_TOAST_MAIN_APP, true)
        if (showToast) {
            val message = if (isEnabled) {
                "SpeakThat enabled - notifications will be read"
            } else {
                "SpeakThat disabled - notifications will be silent"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        
        Log.d(TAG, "Master switch toggled: $isEnabled")
    }
    
    // showNotificationHistory moved to DevelopmentSettingsActivity
    
    private fun applySavedTheme() {
        val isDarkMode = sharedPreferences?.getBoolean(KEY_DARK_MODE, false) ?: false // Default to light mode
        
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
    
    private fun applySavedLanguage() {
        // Get saved language from VoiceSettings preferences
        val voiceSettingsPrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
        val savedLanguage = voiceSettingsPrefs.getString("language", "en_US") ?: "en_US"
        
        try {
            val targetLocale = parseLocalePreference(savedLanguage)
            
            // Check if we need to change the locale
            val currentConfig = resources.configuration
            val currentLocale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                currentConfig.locales.get(0)
            } else {
                @Suppress("DEPRECATION")
                currentConfig.locale
            }
            
            if (currentLocale == null || 
                !targetLocale.language.equals(currentLocale.language) ||
                !targetLocale.country.equals(currentLocale.country)) {
                
                // Update the app's locale configuration
                val config = android.content.res.Configuration(resources.configuration)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    config.setLocale(targetLocale)
                } else {
                    @Suppress("DEPRECATION")
                    config.locale = targetLocale
                }
                
                // Apply the new configuration
                @Suppress("DEPRECATION")
                resources.updateConfiguration(config, resources.displayMetrics)
                
                // Also update the default locale for this session
                Locale.setDefault(targetLocale)
                
                // Show language change dialog instead of immediate recreate
                showLanguageChangeDialog(targetLocale)
                
                com.micoyc.speakthat.InAppLogger.log("MainActivity", 
                    "Applied saved language: ${targetLocale} (from: $savedLanguage)")
            }
        } catch (e: Exception) {
            com.micoyc.speakthat.InAppLogger.log("MainActivity", 
                "Error applying saved language: ${e.message}")
        }
    }

    private fun parseLocalePreference(languageTag: String): Locale {
        return try {
            val normalizedTag = languageTag.replace('_', '-')
            val locale = Locale.forLanguageTag(normalizedTag)
            if (locale.language.isBlank()) Locale.getDefault() else locale
        } catch (e: Exception) {
            Locale.getDefault()
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
        // Get selected TTS engine from preferences
        val voiceSettingsPrefs = getSharedPreferences("VoiceSettings", android.content.Context.MODE_PRIVATE)
        val selectedEngine = voiceSettingsPrefs.getString("tts_engine_package", "")
        
        if (selectedEngine.isNullOrEmpty()) {
            // Use system default engine
            textToSpeech = TextToSpeech(this, this)
            Log.d(TAG, "Using system default TTS engine")
        } else {
            // Use selected custom engine
            textToSpeech = TextToSpeech(this, this, selectedEngine)
            Log.d(TAG, "Using custom TTS engine: $selectedEngine")
        }
    }
    
    private fun reinitializeTtsWithCurrentSettings() {
        // Shutdown existing TTS instance
        textToSpeech?.shutdown()
        isTtsInitialized = false
        
        // Create new TTS instance with current settings
        // Get selected TTS engine from preferences
        val voiceSettingsPrefs = getSharedPreferences("VoiceSettings", android.content.Context.MODE_PRIVATE)
        val selectedEngine = voiceSettingsPrefs.getString("tts_engine_package", "")
        
        if (selectedEngine.isNullOrEmpty()) {
            // Use system default engine
            textToSpeech = TextToSpeech(this, this)
            Log.d(TAG, "Reinitializing with system default TTS engine")
        } else {
            // Use selected custom engine
            textToSpeech = TextToSpeech(this, this, selectedEngine)
            Log.d(TAG, "Reinitializing with custom TTS engine: $selectedEngine")
        }
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
            val errorMessage = getTtsErrorMessage(status)
            Log.e(TAG, "TextToSpeech initialization failed with status: $status - $errorMessage")
            InAppLogger.logTTSEvent("TTS initialization failed", "Status: $status - $errorMessage")
            
            // Check if we were trying to use a custom engine
            val voiceSettingsPrefs = getSharedPreferences("VoiceSettings", android.content.Context.MODE_PRIVATE)
            val selectedEngine = voiceSettingsPrefs.getString("tts_engine_package", "")
            
            if (!selectedEngine.isNullOrEmpty()) {
                // Custom engine failed - log it and revert to default
                Log.e(TAG, "Selected TTS engine failed: $selectedEngine")
                InAppLogger.logError("MainActivity", "Selected TTS engine failed: $selectedEngine")
                
                // Clear the saved engine and reinitialize with default
                voiceSettingsPrefs.edit().putString("tts_engine_package", "").apply()
                
                // Show visual warning dialog
                showTtsEngineFailureDialog()
                
                // Reinitialize with default engine
                textToSpeech = TextToSpeech(this, this)
                Log.d(TAG, "Reverting to system default TTS engine")
                return
            }
            
            // Log device info for debugging
            val deviceInfo = "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, " +
                           "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
            Log.e(TAG, "Device info: $deviceInfo")
            InAppLogger.logError("MainActivity", "TTS init failed on $deviceInfo - $errorMessage")
            
            // Show appropriate user feedback based on error type
            showTtsErrorFeedback(status, errorMessage)
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
            -1 -> if (android.os.Build.VERSION.SDK_INT >= 35) {
                "TTS service not accessible (Android 15 restriction)"
            } else {
                "TTS service not accessible"
            }
            else -> "Unknown TTS error (status: $status)"
        }
    }
    
    /**
     * Show appropriate user feedback based on TTS error type
     */
    private fun showTtsErrorFeedback(status: Int, @Suppress("UNUSED_PARAMETER") errorMessage: String) {
        val message = when (status) {
            -1 -> if (android.os.Build.VERSION.SDK_INT >= 35) {
                getString(R.string.tts_init_failed_android15)
            } else {
                getString(R.string.tts_init_failed_service_unavailable)
            }
            TextToSpeech.ERROR_NOT_INSTALLED_YET -> getString(R.string.tts_init_failed_service_unavailable)
            else -> getString(R.string.tts_init_failed_generic)
        }
        
        // Show toast with error message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        InAppLogger.logUserAction("TTS error toast shown: $message")
        
        // For Android 15 and critical errors, show a dialog with troubleshooting info
        if (android.os.Build.VERSION.SDK_INT >= 35 || status == -1 || status == TextToSpeech.ERROR_NOT_INSTALLED_YET) {
            showTtsTroubleshootingDialog()
        }
    }
    
    /**
     * Show a dialog with TTS troubleshooting information
     */
    private fun showTtsTroubleshootingDialog() {
        try {
            android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.tts_troubleshooting_title))
                .setMessage(getString(R.string.tts_troubleshooting_message))
                .setPositiveButton("OK") { dialog, _ -> 
                    InAppLogger.logUserAction("TTS troubleshooting dialog dismissed")
                    dialog.dismiss() 
                }
                .setNegativeButton("Settings") { _, _ ->
                    InAppLogger.logUserAction("TTS troubleshooting dialog - opening accessibility settings")
                    // Open TTS settings
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not open accessibility settings", e)
                        InAppLogger.logError("MainActivity", "Could not open accessibility settings: ${e.message}")
                        Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
                        InAppLogger.logUserAction("TTS settings access failed - user feedback shown")
                    }
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing TTS troubleshooting dialog", e)
            InAppLogger.logError("MainActivity", "Error showing TTS troubleshooting dialog: ${e.message}")
            // Fallback to just showing the toast
            Toast.makeText(this, getString(R.string.tts_troubleshooting_message), Toast.LENGTH_LONG).show()
            InAppLogger.logUserAction("TTS troubleshooting dialog fallback - toast shown")
        }
    }
    
    private fun handleLogoClick() {
        // Trigger the animated logo flashing effect
        triggerLogoAnimation()
        
        // Log TTS status for debugging
        val ttsStatus = checkTtsStatus()
        InAppLogger.log("MainActivity", "Logo clicked - $ttsStatus")
        
        // Log wave-to-stop settings for debugging
        val waveEnabled = sharedPreferences?.getBoolean(KEY_WAVE_TO_STOP_ENABLED, false) ?: false
        val waveThreshold = sharedPreferences?.getFloat("wave_threshold", 3.0f) ?: 3.0f
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
            Toast.makeText(this, getString(R.string.main_tts_not_ready), Toast.LENGTH_SHORT).show()
            InAppLogger.logUserAction("TTS not ready toast shown")
            return
        }
        
        if (isFirstLogoTap) {
            // First tap: Play instructional message
            val baseText = "This is SpeakThat! The notification reader. This is how incoming notifications will be announced."
            
            // Check if shake-to-stop and wave-to-stop are enabled
            val isShakeToStopEnabled = sharedPreferences?.getBoolean(KEY_SHAKE_TO_STOP_ENABLED, false) ?: false
            val isWaveToStopEnabled = sharedPreferences?.getBoolean(KEY_WAVE_TO_STOP_ENABLED, false) ?: false
            
            val instructionText = when {
                isShakeToStopEnabled && isWaveToStopEnabled -> {
                    "$baseText This is a great opportunity to test your Shake-to-Stop and Wave-to-Stop settings. Go ahead and shake your device or wave your hand to stop me talking."
                }
                isShakeToStopEnabled -> {
                    "$baseText This is a great opportunity to test your Shake-to-Stop settings. Go ahead and shake your device to stop me talking."
                }
                isWaveToStopEnabled -> {
                    "$baseText This is a great opportunity to test your Wave-to-Stop settings. Go ahead and wave your hand to stop me talking."
                }
                else -> {
                    baseText
                }
            }
            
            speakText(instructionText)
            
            // Mark that first tap has occurred
            isFirstLogoTap = false
            sharedPreferences?.edit()?.putBoolean(KEY_FIRST_LOGO_TAP, false)?.apply()
            
        } else {
            // Subsequent taps: Play random easter egg (avoiding repeats)
            if (easterEggLines.isNotEmpty()) {
                val availableLines = getAvailableEasterEggs()
                if (availableLines.isNotEmpty()) {
                    val selectedLine = selectNonRepeatingEasterEgg(availableLines)
                    val processedLine = processDynamicEasterEgg(selectedLine)
                    speakText(processedLine)
                    
                    // Store the original line (before processing) to prevent repeats
                    sharedPreferences?.edit()?.putString(KEY_LAST_EASTER_EGG, selectedLine)?.apply()
                    
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
                
                @Suppress("DEPRECATION")
                override fun onDone(utteranceId: String?) {
                    // Stop shake listening when TTS completes
                    stopShakeListening()
                    InAppLogger.logTTSEvent("MainActivity TTS completed", "Easter egg finished")
                }
                
                @Suppress("DEPRECATION")
                override fun onError(utteranceId: String?) {
                    // Stop shake listening even on error
                    stopShakeListening()
                    InAppLogger.logTTSEvent("MainActivity TTS error", "Easter egg failed")
                    Log.e(TAG, "TTS error during easter egg playback")
                }
            })
            
            // CRITICAL: Apply audio attributes to TTS instance before creating volume bundle
            // This ensures the audio usage matches what we pass to createVolumeBundle
            val voiceSettingsPrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
            val ttsVolume = voiceSettingsPrefs.getFloat("tts_volume", 1.0f)
            val ttsUsageIndex = voiceSettingsPrefs.getInt("audio_usage", 4) // Default to ASSISTANT index
            val contentTypeIndex = voiceSettingsPrefs.getInt("content_type", 0) // Default to SPEECH
            val speakerphoneEnabled = voiceSettingsPrefs.getBoolean("speakerphone_enabled", false)
            
            val ttsUsage = when (ttsUsageIndex) {
                0 -> android.media.AudioAttributes.USAGE_MEDIA
                1 -> android.media.AudioAttributes.USAGE_NOTIFICATION
                2 -> android.media.AudioAttributes.USAGE_ALARM
                3 -> android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
                4 -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                else -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            }
            
            val contentType = when (contentTypeIndex) {
                0 -> android.media.AudioAttributes.CONTENT_TYPE_SPEECH
                1 -> android.media.AudioAttributes.CONTENT_TYPE_MUSIC
                2 -> android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION
                3 -> android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION
                else -> android.media.AudioAttributes.CONTENT_TYPE_SPEECH
            }
            
            // Apply audio attributes to TTS instance
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(ttsUsage)
                .setContentType(contentType)
                .build()
                
            textToSpeech?.setAudioAttributes(audioAttributes)
            InAppLogger.log("MainActivity", "Audio attributes applied - Usage: $ttsUsage, Content: $contentType")
            
            val volumeParams = VoiceSettingsActivity.createVolumeBundle(ttsVolume, ttsUsage, speakerphoneEnabled)
            
            val result = textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, volumeParams, "easter_egg")
            if (result == TextToSpeech.ERROR) {
                Log.e(TAG, "TTS speak() returned ERROR")
                InAppLogger.logError("MainActivity", "TTS speak() failed")
                Toast.makeText(this, getString(R.string.main_tts_playback_failed), Toast.LENGTH_SHORT).show()
                InAppLogger.logUserAction("TTS playback failed toast shown")
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
        isShakeToStopEnabled = sharedPreferences?.getBoolean(KEY_SHAKE_TO_STOP_ENABLED, true) ?: true
        shakeThreshold = sharedPreferences?.getFloat(KEY_SHAKE_THRESHOLD, 12.0f) ?: 12.0f
        Log.d(TAG, "MainActivity shake settings - enabled: $isShakeToStopEnabled, threshold: $shakeThreshold")
    }

    private fun loadWaveSettings() {
        isWaveToStopEnabled = sharedPreferences?.getBoolean(KEY_WAVE_TO_STOP_ENABLED, false) ?: false
        // Use calibrated threshold if available, otherwise fall back to old system
        waveThreshold = if (sharedPreferences?.contains("wave_threshold_v1") == true) {
            sharedPreferences?.getFloat("wave_threshold_v1", 3.0f) ?: 3.0f
        } else {
            sharedPreferences?.getFloat("wave_threshold", 3.0f) ?: 3.0f
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
                val distanceFromMax = maxRange - proximityValue
                
                Log.d(TAG, "Wave detected in MainActivity! Stopping TTS. Proximity: $proximityValue cm, threshold: $waveThreshold cm, maxRange: $maxRange cm, distanceFromMax: $distanceFromMax cm")
                InAppLogger.log("MainActivity", "Wave detected - proximity: ${proximityValue}cm, threshold: ${waveThreshold}cm, maxRange: ${maxRange}cm")
                stopSpeaking("wave")
            } else {
                // Log proximity values for debugging (but not too frequently)
                if (System.currentTimeMillis() % 1000 < 100) { // Log ~10% of the time
                    val maxRange = proximitySensor?.maximumRange ?: 5.0f
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
        val delayEnabled = (sharedPreferences?.getInt("delay_before_readout", 2) ?: 2) > 0
        val shakeEnabled = sharedPreferences?.getBoolean(KEY_SHAKE_TO_STOP_ENABLED, false) ?: false
        val waveEnabled = sharedPreferences?.getBoolean(KEY_WAVE_TO_STOP_ENABLED, false) ?: false
        
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
        val lastEasterEgg = sharedPreferences?.getString(KEY_LAST_EASTER_EGG, null)
        
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
        val delaySeconds = sharedPreferences?.getInt("delay_before_readout", 2) ?: 2
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
            R.id.action_diagnostics -> {
                InAppLogger.logUserAction("SelfTest launched from menu")
                startActivity(Intent(this, SelfTestActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    /**
     * Show quick diagnostics dialog with system status checks
     */
    private fun showQuickDiagnosticsDialog() {
        try {
            // Run all diagnostic checks
            val notificationAccess = checkNotificationAccess()
            val ttsInitialization = checkTtsInitialization()
            val ttsLanguage = checkTtsLanguage()
            val outputStream = checkOutputStream()
            val shouldRead = checkShouldReadNotifications()
            val ttsEngine = checkTtsEngine()
            
            // Build diagnostic report
            val report = buildDiagnosticReport(
                notificationAccess, ttsInitialization, ttsLanguage,
                outputStream, shouldRead, ttsEngine
            )
            
            // Create and show dialog
            val dialog = AlertDialog.Builder(this)
                .setTitle(getString(R.string.diagnostics_title))
                .setMessage(report)
                .setPositiveButton(getString(R.string.diagnostics_copy_clipboard)) { _, _ ->
                    copyDiagnosticReportToClipboard(report)
                }
                .setNeutralButton(getString(R.string.diagnostics_reinitialize_tts)) { _, _ ->
                    reinitializeTtsAndShowDiagnostics()
                }
                .setNegativeButton(getString(R.string.close)) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
            
            dialog.show()
            
            // Log diagnostic action
            InAppLogger.log("Diagnostics", "Quick diagnostics dialog shown")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing diagnostics dialog", e)
            InAppLogger.logError("Diagnostics", "Error showing diagnostics dialog: ${e.message}")
            Toast.makeText(this, "Error running diagnostics", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Check notification access status
     */
    private fun checkNotificationAccess(): DiagnosticResult {
        return try {
            val isEnabled = isNotificationServiceEnabled()
            if (isEnabled) {
                DiagnosticResult("OK", getString(R.string.diagnostics_notification_access_ok))
            } else {
                DiagnosticResult("ERR", getString(R.string.diagnostics_notification_access_err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification access", e)
            DiagnosticResult("ERR", "Error: ${e.message}")
        }
    }
    
    /**
     * Check TTS initialization status
     */
    private fun checkTtsInitialization(): DiagnosticResult {
        return try {
            when {
                textToSpeech == null -> DiagnosticResult("-", getString(R.string.diagnostics_tts_init_dash))
                isTtsInitialized -> DiagnosticResult("OK", getString(R.string.diagnostics_tts_init_ok))
                else -> DiagnosticResult("ERR", getString(R.string.diagnostics_tts_init_err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking TTS initialization", e)
            DiagnosticResult("ERR", "Error: ${e.message}")
        }
    }
    
    /**
     * Check TTS language availability
     */
    private fun checkTtsLanguage(): DiagnosticResult {
        return try {
            if (textToSpeech == null || !isTtsInitialized) {
                return DiagnosticResult("-", getString(R.string.diagnostics_tts_language_dash))
            }
            
            // Get current language from voice settings
            val voiceSettingsPrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
            val savedLanguage = voiceSettingsPrefs.getString("language", "en_US") ?: "en_US"
            
            val targetLocale = parseLocalePreference(savedLanguage)
            
            val availability = textToSpeech?.isLanguageAvailable(targetLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            when (availability) {
                TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> 
                    DiagnosticResult("OK", getString(R.string.diagnostics_tts_language_ok))
                else -> DiagnosticResult("ERR", getString(R.string.diagnostics_tts_language_err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking TTS language", e)
            DiagnosticResult("ERR", "Error: ${e.message}")
        }
    }
    
    /**
     * Check output stream accessibility
     */
    private fun checkOutputStream(): DiagnosticResult {
        return try {
            val voiceSettingsPrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
            val ttsUsageIndex = voiceSettingsPrefs.getInt("audio_usage", 4) // Default to ASSISTANT
            val contentTypeIndex = voiceSettingsPrefs.getInt("content_type", 0) // Default to SPEECH
            
            val ttsUsage = when (ttsUsageIndex) {
                0 -> android.media.AudioAttributes.USAGE_MEDIA
                1 -> android.media.AudioAttributes.USAGE_NOTIFICATION
                2 -> android.media.AudioAttributes.USAGE_ALARM
                3 -> android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
                4 -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                else -> android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            }
            
            val contentType = when (contentTypeIndex) {
                0 -> android.media.AudioAttributes.CONTENT_TYPE_SPEECH
                1 -> android.media.AudioAttributes.CONTENT_TYPE_MUSIC
                2 -> android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION
                3 -> android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION
                else -> android.media.AudioAttributes.CONTENT_TYPE_SPEECH
            }
            
            // Test if we can create audio attributes
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(ttsUsage)
                .setContentType(contentType)
                .build()
            
            // Test audio manager
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val isMusicActive = audioManager.isMusicActive
            val isSpeakerphoneOn = audioManager.isSpeakerphoneOn
            
            DiagnosticResult("OK", getString(R.string.diagnostics_output_stream_ok))
        } catch (e: Exception) {
            Log.e(TAG, "Error checking output stream", e)
            DiagnosticResult("ERR", getString(R.string.diagnostics_output_stream_err))
        }
    }
    
    /**
     * Check if notifications should be read based on current settings
     */
    private fun checkShouldReadNotifications(): DiagnosticResult {
        return try {
            // Check master switch
            if (!MainActivity.isMasterSwitchEnabled(this)) {
                return DiagnosticResult("N", "Master switch disabled")
            }
            
            // Check Do Not Disturb if honor DND is enabled
            val voiceSettingsPrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
            val honorDnd = voiceSettingsPrefs.getBoolean("honor_dnd", false)
            if (honorDnd) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val dndMode = notificationManager.currentInterruptionFilter
                    if (dndMode == android.app.NotificationManager.INTERRUPTION_FILTER_NONE) {
                        return DiagnosticResult("N", "Do Not Disturb is active")
                    }
                }
            }
            
            // Check if any rules are blocking (simplified check)
            val automationMode = AutomationModeManager(this).getMode()
            when (automationMode) {
                AutomationMode.CONDITIONAL_RULES -> {
                    return DiagnosticResult(
                        "Y",
                        getString(R.string.diagnostics_should_read_yes_conditional)
                    )
                }
                AutomationMode.EXTERNAL_AUTOMATION -> {
                    return DiagnosticResult(
                        "Y",
                        getString(R.string.diagnostics_should_read_yes_external)
                    )
                }
                else -> {
                    // fall through
                }
            }
            
            DiagnosticResult("Y", getString(R.string.diagnostics_should_read_yes))
        } catch (e: Exception) {
            Log.e(TAG, "Error checking should read notifications", e)
            DiagnosticResult("ERR", getString(R.string.diagnostics_should_read_err))
        }
    }
    
    /**
     * Check TTS engine status
     */
    private fun checkTtsEngine(): DiagnosticResult {
        return try {
            val voiceSettingsPrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
            val selectedEngine = voiceSettingsPrefs.getString("tts_engine_package", "")
            
            if (selectedEngine.isNullOrEmpty()) {
                DiagnosticResult("-", getString(R.string.diagnostics_tts_engine_dash))
            } else {
                // Check if the selected engine package exists
                try {
                    packageManager.getPackageInfo(selectedEngine, 0)
                    if (isTtsInitialized && textToSpeech != null) {
                        DiagnosticResult("OK", getString(R.string.diagnostics_tts_engine_ok))
                    } else {
                        DiagnosticResult("ERR", getString(R.string.diagnostics_tts_engine_err))
                    }
                } catch (e: Exception) {
                    DiagnosticResult("ERR", "Engine package not found: $selectedEngine")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking TTS engine", e)
            DiagnosticResult("ERR", "Error: ${e.message}")
        }
    }
    
    /**
     * Build formatted diagnostic report
     */
    private fun buildDiagnosticReport(
        notificationAccess: DiagnosticResult,
        ttsInitialization: DiagnosticResult,
        ttsLanguage: DiagnosticResult,
        outputStream: DiagnosticResult,
        shouldRead: DiagnosticResult,
        ttsEngine: DiagnosticResult
    ): String {
        return buildString {
            appendLine("${getString(R.string.diagnostics_notification_access)} ${notificationAccess.status}")
            appendLine()
            
            appendLine("${getString(R.string.diagnostics_tts_initialization)} ${ttsInitialization.status}")
            appendLine()
            
            appendLine("${getString(R.string.diagnostics_tts_language)} ${ttsLanguage.status}")
            appendLine()
            
            appendLine("${getString(R.string.diagnostics_output_stream)} ${outputStream.status}")
            appendLine()
            
            appendLine("${getString(R.string.diagnostics_should_read)} ${shouldRead.status}")
            appendLine()
            
            appendLine("${getString(R.string.diagnostics_tts_engine)} ${ttsEngine.status}")
        }
    }
    
    /**
     * Copy diagnostic report to clipboard
     */
    private fun copyDiagnosticReportToClipboard(report: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("SpeakThat Diagnostics", report)
            clipboard.setPrimaryClip(clip)
            
            Toast.makeText(this, getString(R.string.diagnostics_clipboard_copied), Toast.LENGTH_SHORT).show()
            InAppLogger.log("Diagnostics", "Diagnostic report copied to clipboard")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying to clipboard", e)
            InAppLogger.logError("Diagnostics", "Error copying to clipboard: ${e.message}")
            Toast.makeText(this, "Error copying to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Reinitialize TTS and show updated diagnostics
     */
    private fun reinitializeTtsAndShowDiagnostics() {
        try {
            InAppLogger.log("Diagnostics", "Reinitializing TTS as requested by user")
            reinitializeTtsWithCurrentSettings()
            
            Toast.makeText(this, getString(R.string.diagnostics_tts_reinitialized), Toast.LENGTH_SHORT).show()
            
            // Show diagnostics again after a short delay to allow TTS to initialize
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                showQuickDiagnosticsDialog()
            }, 2000)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reinitializing TTS", e)
            InAppLogger.logError("Diagnostics", "Error reinitializing TTS: ${e.message}")
            Toast.makeText(this, "Error reinitializing TTS", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Data class for diagnostic results
     */
    private data class DiagnosticResult(
        val status: String,
        val message: String
    )

    /**
     * Check for updates automatically if enabled in settings
     * This runs silently in the background without user interaction
     */
    private fun checkForUpdatesIfEnabled() {
        Log.d(TAG, "checkForUpdatesIfEnabled() called")
        
        // Use the wrapper to handle conditional update checking
        UpdateFeature.checkForUpdatesIfEnabled(this)
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
                InAppLogger.logUserAction("Update dialog - download update button clicked")
                // Start update activity using wrapper
                UpdateFeature.startUpdateActivity(this, forceCheck = true)
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                InAppLogger.logUserAction("Update dialog - cancel button clicked")
            }
            .setNeutralButton(getString(R.string.view_release_notes)) { _, _ ->
                InAppLogger.logUserAction("Update dialog - view release notes button clicked")
                // Show release notes
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.release_notes))
                    .setMessage(updateInfo.releaseNotes.ifEmpty { getString(R.string.no_release_notes) })
                    .setPositiveButton(getString(R.string.ok)) { _, _ ->
                        InAppLogger.logUserAction("Release notes dialog dismissed")
                    }
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
     * Check for crash logs and notify user if available
     */
    private fun checkForCrashLogs() {
        try {
            if (InAppLogger.hasExternalCrashLogs()) {
                val crashLogs = InAppLogger.getExternalCrashLogs()
                if (crashLogs.isNotEmpty()) {
                    val latestCrash = crashLogs.first()
                    val crashTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(latestCrash.lastModified()))
                    
                    // Show notification about crash logs
                    showCrashLogNotification(crashLogs.size, crashTime)
                    
                    InAppLogger.log("CrashLogs", "Found ${crashLogs.size} crash log(s), showing notification")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for crash logs", e)
            InAppLogger.logError("CrashLogs", "Error checking for crash logs: ${e.message}")
        }
    }
    
    /**
     * Show notification about available crash logs
     */
    private fun showCrashLogNotification(crashCount: Int, latestCrashTime: String) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            // Check notification permission for Android 13+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted - cannot show crash log notification")
                    return
                }
            }
            
            // Create notification channel for Android O+
            createCrashLogNotificationChannel()
            
            // Create intent for opening development settings
            val openDevSettingsIntent = Intent(this, DevelopmentSettingsActivity::class.java)
            val openDevSettingsPendingIntent = PendingIntent.getActivity(
                this, 0, openDevSettingsIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Create intent for opening file manager to crash logs directory
            val crashLogsDir = InAppLogger.getExternalCrashLogsDirectory()
            val openFileManagerIntent = Intent(Intent.ACTION_VIEW).apply {
                if (crashLogsDir != null) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this@MainActivity,
                        packageName + ".fileprovider",
                        java.io.File(crashLogsDir)
                    )
                    setDataAndType(uri, "resource/folder")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            val openFileManagerPendingIntent = PendingIntent.getActivity(
                this, 1, openFileManagerIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = androidx.core.app.NotificationCompat.Builder(this, "SpeakThat_CrashLogs_Channel")
                .setContentTitle("SpeakThat! Crash Detected")
                .setContentText("$crashCount crash log(s) found. Latest: $latestCrashTime")
                .setSmallIcon(R.drawable.speakthaticon)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openDevSettingsPendingIntent)
                .setAutoCancel(true)
                .addAction(
                    R.drawable.speakthaticon,
                    "View Logs",
                    openDevSettingsPendingIntent
                )
                .addAction(
                    R.drawable.speakthaticon,
                    "Open Folder",
                    openFileManagerPendingIntent
                )
                .build()
            
            // Show notification with unique ID
            notificationManager.notify(2001, notification)
            Log.d(TAG, "Crash log notification shown")
            InAppLogger.log("CrashLogs", "Crash log notification shown for $crashCount crash(es)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing crash log notification", e)
            InAppLogger.logError("CrashLogs", "Error showing crash log notification: ${e.message}")
        }
    }
    
    /**
     * Create notification channel for crash log notifications
     */
    private fun createCrashLogNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            val channel = android.app.NotificationChannel(
                "SpeakThat_CrashLogs_Channel",
                "SpeakThat! Crash Logs",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications about SpeakThat! crash logs"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
            }
            
            notificationManager.createNotificationChannel(channel)
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
    
    /**
     * Show repository update message to users who installed from F-Droid/IzzyOnDroid
     * This explains why GitHub updates are disabled and directs them to their repository
     */
    private fun showRepositoryUpdateMessage() {
        AlertDialog.Builder(this)
            .setTitle("Updates via Repository")
            .setMessage("You installed SpeakThat from a repository (F-Droid, IzzyOnDroid, etc.). " +
                "For security and to maintain the repository's screening process, automatic updates are handled through your repository.\n\n" +
                "To update the app, please use your repository's update system (F-Droid, IzzyOnDroid client, etc.).\n\n" +
                "This ensures you receive updates that have been screened and verified by the repository maintainers.")
            .setPositiveButton("OK", null)
            .setCancelable(true)
            .show()
        
        Log.i(TAG, "Showed repository update message to user")
        InAppLogger.logSystemEvent("Repository update message shown to user", "MainActivity")
    }
    
    /**
     * Request notification permission for Android 13+ when needed
     * This is called when user enables notification features
     */
    fun requestNotificationPermissionIfNeeded(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting POST_NOTIFICATIONS permission")
                InAppLogger.log("Permissions", "Requesting POST_NOTIFICATIONS permission")
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATION_PERMISSION)
                return false // Permission not yet granted
            } else {
                Log.d(TAG, "POST_NOTIFICATIONS permission already granted")
                InAppLogger.log("Permissions", "POST_NOTIFICATIONS permission already granted")
                return true // Permission already granted
            }
        }
        return true // No permission needed for older Android versions
    }
    
    /**
     * Check if notification permission is granted
     */
    fun isNotificationPermissionGranted(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // No permission needed for older Android versions
        }
    }
    
    /**
     * Show language change dialog with options for user
     */
    private fun showLanguageChangeDialog(newLocale: java.util.Locale) {
        val languageName = newLocale.getDisplayLanguage(newLocale)
        val currentLanguageName = java.util.Locale.getDefault().getDisplayLanguage(java.util.Locale.getDefault())
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_language_changed))
            .setMessage(getString(R.string.dialog_message_language_changed, currentLanguageName, languageName))
            .setPositiveButton(getString(R.string.button_restart_app)) { _, _ ->
                // Restart the entire app to ensure all components use new language
                restartApp()
            }
            .setNegativeButton(getString(R.string.button_apply_now)) { _, _ ->
                // Just recreate current activity
                recreate()
            }
            .setNeutralButton(getString(R.string.button_later)) { _, _ ->
                // Do nothing - user can manually restart later
                InAppLogger.logUserAction("Language change dialog - later button clicked")
                Toast.makeText(this, getString(R.string.toast_language_manual_restart), Toast.LENGTH_SHORT).show()
                InAppLogger.logUserAction("Language manual restart toast shown")
            }
            .setCancelable(false)
            .show()
        
        InAppLogger.log("MainActivity", "Language change dialog shown: $currentLanguageName → $languageName")
    }
    
    /**
     * Restart the entire app to ensure all components use the new language
     */
    private fun restartApp() {
        try {
            // Create intent to restart the app
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            
            // Show a brief message
            Toast.makeText(this, getString(R.string.toast_language_restarting), Toast.LENGTH_SHORT).show()
            InAppLogger.logUserAction("Language restarting toast shown")
            
            // Start the new instance and finish current one
            startActivity(intent)
            finish()
            
            InAppLogger.log("MainActivity", "App restart initiated for language change")
        } catch (e: Exception) {
            InAppLogger.logError("MainActivity", "Failed to restart app: ${e.message}")
            // Fallback to recreate if restart fails
            recreate()
        }
    }
    
    /**
     * Handle permission request results
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_NOTIFICATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "POST_NOTIFICATIONS permission granted")
                    InAppLogger.log("Permissions", "POST_NOTIFICATIONS permission granted")
                    Toast.makeText(this, getString(R.string.main_notification_permission_granted), Toast.LENGTH_SHORT).show()
                    InAppLogger.logUserAction("Notification permission granted toast shown")
                } else {
                    Log.w(TAG, "POST_NOTIFICATIONS permission denied")
                    InAppLogger.log("Permissions", "POST_NOTIFICATIONS permission denied")
                    Toast.makeText(this, getString(R.string.main_notification_permission_denied), Toast.LENGTH_LONG).show()
                    InAppLogger.logUserAction("Notification permission denied toast shown")
                }
            }
        }
    }
    
    /**
     * Initialize review reminder and track app session
     */
    private fun initializeReviewReminder() {
        val reviewManager = ReviewReminderManager.getInstance(this)
        
        // Track this app session
        reviewManager.incrementAppSession()
        
        // Check if we should show the reminder (with a small delay to avoid showing immediately)
        Handler(Looper.getMainLooper()).postDelayed({
            if (reviewManager.shouldShowReminder()) {
                reviewManager.showReminderDialog(this@MainActivity)
            }
        }, 2000) // 2 second delay to let the app settle
    }
    
    /**
     * Track notification read for review reminder
     * This should be called when a notification is successfully read aloud
     */
    fun trackNotificationRead() {
        val reviewManager = ReviewReminderManager.getInstance(this)
        reviewManager.incrementNotificationsRead()
    }

    /**
     * Show TTS Engine Failure Dialog
     */
    private fun showTtsEngineFailureDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.tts_engine_failure_title))
            .setMessage(getString(R.string.tts_engine_failure_message))
            .setPositiveButton("Open Voice Settings") { _, _ ->
                // Open Voice Settings
                val intent = android.content.Intent(this, VoiceSettingsActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("Dismiss", null)
            .show()
    }

} 