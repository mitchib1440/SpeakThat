package com.micoyc.speakthat

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.viewpager2.widget.ViewPager2
import com.micoyc.speakthat.databinding.ActivityOnboardingBinding
import java.util.Locale

class OnboardingActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var adapter: OnboardingPagerAdapterNew
    
    // App Picker Launcher
    private val appPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedPackages = result.data?.getStringArrayListExtra(AppPickerActivity.EXTRA_SELECTED_PACKAGES)
            val privatePackages = result.data?.getStringArrayListExtra(AppPickerActivity.EXTRA_PRIVATE_PACKAGES)
            
            if (selectedPackages != null) {
                val prefs = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE)
                prefs.edit()
                    .putStringSet("app_list", selectedPackages.toSet())
                    .putStringSet("app_private_flags", privatePackages?.toSet() ?: emptySet())
                    .apply()
                
                InAppLogger.log(TAG, "App selection saved: ${selectedPackages.size} apps")
                
                // Update the ViewHolder
                adapter.onAppPickerResult()
            }
        }
    }
    
    // TTS support
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var voiceSettingsPrefs: SharedPreferences? = null
    private var isMuted = false
    private val voiceSettingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "speech_rate", "pitch", "voice_name", "audio_usage", "content_type" -> {
                applyVoiceSettings()
            }
            "language", "tts_language" -> {
                // For language changes, we need to reinitialize TTS to ensure it uses the new language
                InAppLogger.log(TAG, "Language settings changed - reinitializing TTS")
                reinitializeTtsWithCurrentSettings()
            }
        }
    }
    

    

    
    companion object {
        // Use separate SharedPreferences file for onboarding state
        // This file is excluded from backups so new installs always show onboarding
        private const val PREFS_NAME = "OnboardingState"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val TAG = "OnboardingActivity"
        
        fun hasSeenOnboarding(context: android.content.Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force dark mode - light mode is not officially supported
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        // Initialize voice settings listener
        voiceSettingsPrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
        voiceSettingsPrefs?.registerOnSharedPreferenceChangeListener(voiceSettingsListener)
        
        // Initialize view binding
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configure system UI for proper insets handling
        configureSystemUI()

        // Set up the new onboarding flow
        setupOnboarding()
        
        // Initialize TTS after setup is complete to ensure language settings are loaded
        initializeTextToSpeech()
    }

    
    override fun onResume() {
        super.onResume()
        
        // Force a full rebind to ensure UI is up to date
        adapter.notifyDataSetChanged()
        
        val currentPage = binding.viewPager.currentItem
        updateButtonStates(currentPage, adapter.itemCount)
        
        // Refresh UI text to ensure it's in the correct language
        adapter.refreshUIText()
        
        // Update mute button icon to ensure it's correct
        updateMuteButtonIcon()
        
        // Update permission status if we're on the permission page
        checkAndUpdatePermissionStatus()
        
        // Speak current page content when resuming (only if not muted)
        speakCurrentPageContent()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save current page position for language change recreation
        outState.putInt("current_page", binding.viewPager.currentItem)
        outState.putBoolean("is_muted", isMuted)
    }
    
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Restore current page position after language change recreation
        val savedPage = savedInstanceState.getInt("current_page", 0)
        val savedMuted = savedInstanceState.getBoolean("is_muted", false)
        
        binding.viewPager.setCurrentItem(savedPage, false)
        InAppLogger.log(TAG, "Restored onboarding page position: $savedPage")
        
        // Restore mute state
        isMuted = savedMuted
        updateMuteButtonIcon()
    }
    
    override fun onPause() {
        super.onPause()
        // Stop TTS when leaving the activity
        textToSpeech?.stop()
    }
    
    private fun initializeTextToSpeech() {
        // Get selected TTS engine from preferences
        val voiceSettingsPrefs = getSharedPreferences("VoiceSettings", android.content.Context.MODE_PRIVATE)
        val selectedEngine = voiceSettingsPrefs.getString("tts_engine_package", "")
        
        if (selectedEngine.isNullOrEmpty()) {
            // Use system default engine
            textToSpeech = TextToSpeech(this, this)
            InAppLogger.log(TAG, "Using system default TTS engine")
        } else {
            // Use selected custom engine
            textToSpeech = TextToSpeech(this, this, selectedEngine)
            InAppLogger.log(TAG, "Using custom TTS engine: $selectedEngine")
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
            InAppLogger.log(TAG, "Reinitializing with system default TTS engine")
        } else {
            // Use selected custom engine
            textToSpeech = TextToSpeech(this, this, selectedEngine)
            InAppLogger.log(TAG, "Reinitializing with custom TTS engine: $selectedEngine")
        }
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            InAppLogger.log(TAG, "TTS onInit called with status: $status")
            
            // Set audio stream to assistant usage to avoid triggering media detection
            textToSpeech?.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            
            // Apply saved voice settings (which will handle language/voice selection)
            applyVoiceSettings()
            
            isTtsInitialized = true
            InAppLogger.log(TAG, "TTS initialized successfully for onboarding")
            
            // Speak language/theme page message
            speakText(getLocalizedTtsString(R.string.tts_onboarding_language_theme))
        } else {
            InAppLogger.log(TAG, "TTS initialization failed with status: $status")
        }
    }
    
    private fun applyVoiceSettings() {
        textToSpeech?.let { tts ->
            voiceSettingsPrefs?.let { prefs ->
                // Log the current language settings before applying
                val currentLanguage = prefs.getString("language", "en_US")
                val currentTtsLanguage = prefs.getString("tts_language", "en_US")
                InAppLogger.log(TAG, "Applying voice settings - UI Language: $currentLanguage, TTS Language: $currentTtsLanguage")
                
                VoiceSettingsActivity.applyVoiceSettings(tts, prefs)
                
                // Log the result after applying
                InAppLogger.log(TAG, "Voice settings applied successfully")
            }
        }
    }
    
    private fun speakText(text: String) {
        if (isTtsInitialized && textToSpeech != null && !isMuted) {
            // Stop any current speech first
            textToSpeech?.stop()
            
            // CRITICAL: Apply audio attributes to TTS instance before creating volume bundle
            // This ensures the audio usage matches what we pass to createVolumeBundle
            val ttsVolume = voiceSettingsPrefs?.getFloat("tts_volume", 1.0f) ?: 1.0f
            val ttsUsageIndex = voiceSettingsPrefs?.getInt("audio_usage", 4) ?: 4 // Default to ASSISTANT index
            val contentTypeIndex = voiceSettingsPrefs?.getInt("content_type", 0) ?: 0 // Default to SPEECH
            val speakerphoneEnabled = voiceSettingsPrefs?.getBoolean("speakerphone_enabled", false) ?: false
            
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
            InAppLogger.log("OnboardingActivity", "Audio attributes applied - Usage: $ttsUsage, Content: $contentType")
            
            val volumeParams = VoiceSettingsActivity.createVolumeBundle(ttsVolume, ttsUsage, speakerphoneEnabled)
            
            // Then speak the new text
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, volumeParams, "onboarding_utterance")
            InAppLogger.log(TAG, "Speaking: ${text.take(50)}...")
        }
    }
    
    private fun toggleMute() {
        isMuted = !isMuted
        updateMuteButtonIcon()
        
        if (isMuted) {
            // Stop any current speech when muting
            textToSpeech?.stop()
            InAppLogger.log(TAG, "Onboarding TTS muted")
        } else {
            InAppLogger.log(TAG, "Onboarding TTS unmuted")
            // Speak the current page content when unmuting
            speakCurrentPageContent()
        }
    }
    
    private fun updateMuteButtonIcon() {
        if (isMuted) {
            binding.buttonMute.setImageResource(R.drawable.ic_volume_off_24)
        } else {
            binding.buttonMute.setImageResource(R.drawable.ic_volume_up_24)
        }
    }
    
    private fun speakCurrentPageContent() {
        if (!isTtsInitialized) return
        
        val currentPage = binding.viewPager.currentItem
        val pageContent = when (currentPage) {
            0 -> getLocalizedTtsString(R.string.tts_onboarding_language_new)
            1 -> getLocalizedTtsString(R.string.tts_onboarding_welcome_new)
            2 -> getLocalizedTtsString(R.string.tts_onboarding_privacy_new)
            3 -> getLocalizedTtsString(R.string.tts_onboarding_permission_new)
            4 -> getLocalizedTtsString(R.string.tts_onboarding_system_check)
            5 -> getLocalizedTtsString(R.string.tts_onboarding_kill_noise)
            6 -> getLocalizedTtsString(R.string.tts_onboarding_select_apps)
            7 -> getLocalizedTtsString(R.string.tts_onboarding_when_to_read)
            8 -> getLocalizedTtsString(R.string.tts_onboarding_filter_words)
            9 -> getLocalizedTtsString(R.string.tts_onboarding_all_set)
            else -> ""
        }
        
        if (pageContent.isNotEmpty()) {
            speakText(pageContent)
        }
    }
    
    private fun getLocalizedTtsString(stringResId: Int): String {
        // Get the user's selected TTS language from voice settings
        val ttsLanguageCode = voiceSettingsPrefs?.getString("tts_language", null)
        
        // Use TtsLanguageManager to get the localized string
        return TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, stringResId)
    }
    




    private fun setupOnboarding() {
        // Set up ViewPager2 with new 10-page adapter
        adapter = OnboardingPagerAdapterNew()
        
        // Set callback for permission status changes
        adapter.onPermissionStatusChanged = {
            val currentPage = binding.viewPager.currentItem
            updateButtonStates(currentPage, adapter.itemCount)
            // Also update swipe control in case permission changed
            updateSwipeControl(currentPage)
        }
        
        // Set callback for navigation control (enable/disable next/back during system check)
        adapter.onNavigationControlChanged = { nextEnabled, backEnabled ->
            binding.buttonNext.isEnabled = nextEnabled
            binding.buttonNext.alpha = if (nextEnabled) 1.0f else 0.3f
            binding.buttonBack.isEnabled = backEnabled
            binding.buttonBack.alpha = if (backEnabled) 1.0f else 0.3f
        }
        
        // Set callback for swipe control (enable/disable swiping)
        adapter.onSwipeControlChanged = { swipeEnabled ->
            binding.viewPager.isUserInputEnabled = swipeEnabled
            InAppLogger.log(TAG, "Swipe control changed: enabled=$swipeEnabled")
        }
        
        // Set callback to stop TTS
        adapter.onStopTts = {
            textToSpeech?.stop()
            InAppLogger.log(TAG, "TTS stopped via adapter callback")
        }
        
        // Callback to skip to a specific page
        adapter.onSkipToPage = { pageIndex ->
            binding.viewPager.currentItem = pageIndex
            InAppLogger.log(TAG, "Skipped to page ${pageIndex + 1}")
        }
        
        // Callback to launch app picker
        adapter.onLaunchAppPicker = { intent ->
            appPickerLauncher.launch(intent)
            InAppLogger.log(TAG, "Launching app picker")
        }
        
        // Callback to request Bluetooth permission
        adapter.onRequestBluetoothPermission = {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT),
                    OnboardingPagerAdapterNew.REQUEST_BLUETOOTH_CONNECT
                )
                InAppLogger.log(TAG, "Requesting BLUETOOTH_CONNECT permission")
            }
        }
        
        // Set callback for system check completion
        adapter.onSystemCheckCompleted = { success ->
            if (success) {
                // Move to next page on success
                if (binding.viewPager.currentItem < adapter.itemCount - 1) {
                    binding.viewPager.currentItem += 1
                }
            }
        }
        
        binding.viewPager.adapter = adapter
        
        // Set up page indicator dots (9 dots for pages 1-9, page 10 is the completion page without a dot)
        setupPageIndicator(9)
        
        // Set up button listeners
        binding.buttonSkip.setOnClickListener {
            completeOnboarding()
        }
        
        binding.buttonMute.setOnClickListener {
            toggleMute()
        }
        
        binding.buttonBack.setOnClickListener {
            if (binding.viewPager.currentItem > 0) {
                binding.viewPager.currentItem -= 1
            }
        }
        
        binding.buttonNext.setOnClickListener {
            val currentPage = binding.viewPager.currentItem
            
            // Prevent navigation from permission page if permission not granted
            if (currentPage == 3 && !isNotificationServiceEnabled()) {
                InAppLogger.log(TAG, "Blocked Next button - permission not granted")
                // Ensure UI state is correct even though we're not navigating
                updateButtonStates(currentPage, adapter.itemCount)
                updateSwipeControl(currentPage)
                return@setOnClickListener
            }
            
            if (currentPage < adapter.itemCount - 1) {
                binding.viewPager.currentItem += 1
            } else {
                completeOnboarding()
            }
        }
        
        // Register page change callback
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateButtonStates(position, adapter.itemCount)
                updatePageIndicator(position)
                
                // Update swipe control based on current page
                updateSwipeControl(position)
                
                // Speak the content of the new page immediately
                speakCurrentPageContent()
            }
        })
        
        // Enable user swiping
        binding.viewPager.isUserInputEnabled = true
        
        // Set initial button states
        updateButtonStates(0, adapter.itemCount)
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
    
    private fun setupPageIndicator(pageCount: Int) {
        binding.pageIndicator.removeAllViews()
        for (i in 0 until pageCount) {
            val dot = android.widget.ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(16, 16).apply {
                    setMargins(0, 0, 8, 0) // left, top, right, bottom
                }
                setImageResource(R.drawable.tab_selector)
                isClickable = false
                isFocusable = false
                // Start with full opacity - dots will be faded as pages are completed
                alpha = 1.0f
            }
            binding.pageIndicator.addView(dot)
        }
        updatePageIndicator(0)
    }
    
    private fun updatePageIndicator(currentPage: Int) {
        for (i in 0 until binding.pageIndicator.childCount) {
            val dot = binding.pageIndicator.getChildAt(i) as android.widget.ImageView
            
            if (i < currentPage) {
                // Pages before current page are completed - fade to 15% opacity
                dot.isSelected = false
                dot.alpha = 0.15f
            } else if (i == currentPage) {
                // Current page is selected
                dot.isSelected = true
                dot.alpha = 1.0f
            } else {
                // Pages after current page are not yet completed
                dot.isSelected = false
                dot.alpha = 1.0f
            }
        }
    }
    
    private fun updateSwipeControl(currentPage: Int) {
        // Page 4 (index 3) is the permission page - disable swiping if permission not granted
        if (currentPage == 3) {
            val hasPermission = isNotificationServiceEnabled()
            binding.viewPager.isUserInputEnabled = hasPermission
            InAppLogger.log(TAG, "Permission page - swipe ${if (hasPermission) "enabled" else "disabled"}")
        } else if (currentPage != 4) {
            // For all pages except system check (page 5, index 4), enable swiping
            // System check manages its own swipe state via the callback
            binding.viewPager.isUserInputEnabled = true
        }
        // Note: Page 5 (system check) controls swiping via onSwipeControlChanged callback
    }
    
    private fun updateButtonStates(currentPage: Int, totalPages: Int) {
        // Back button is disabled on first page
        binding.buttonBack.isEnabled = currentPage > 0
        binding.buttonBack.alpha = if (currentPage > 0) 1.0f else 0.3f
        
        // Next button is disabled on permission page (page 3, index 3) if permission not granted
        val isPermissionPage = currentPage == 3
        val hasPermission = isNotificationServiceEnabled()
        
        if (isPermissionPage && !hasPermission) {
            binding.buttonNext.isEnabled = false
            binding.buttonNext.alpha = 0.3f
            InAppLogger.log(TAG, "Next button disabled - permission not granted")
        } else {
            binding.buttonNext.isEnabled = true
            binding.buttonNext.alpha = 1.0f
        }
    }
    
    private fun checkAndUpdatePermissionStatus() {
        val currentPage = binding.viewPager.currentItem
        if (currentPage == 3) {
            // We're on the permission page - update button states
            updateButtonStates(currentPage, adapter.itemCount)
            InAppLogger.log(TAG, "Checked permission status on resume")
        }
    }
    
    internal fun isNotificationServiceEnabled(): Boolean {
        val packageName = packageName
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!android.text.TextUtils.isEmpty(flat)) {
            val names = flat.split(":").toTypedArray()
            for (name in names) {
                val componentName = android.content.ComponentName.unflattenFromString(name)
                val nameMatch = android.text.TextUtils.equals(packageName, componentName?.packageName)
                if (nameMatch) {
                    return true
                }
            }
        }
        return false
    }
    private fun completeOnboarding() {
        // Mark onboarding as completed
        sharedPreferences.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, true).apply()
        
        // Start main activity
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            OnboardingPagerAdapterNew.REQUEST_POST_NOTIFICATIONS -> {
                val granted = grantResults.isNotEmpty() && 
                              grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
                
                InAppLogger.log(TAG, "POST_NOTIFICATIONS permission result: granted=$granted")
                adapter.onPostNotificationsPermissionResult(granted)
            }
            OnboardingPagerAdapterNew.REQUEST_BLUETOOTH_CONNECT -> {
                val granted = grantResults.isNotEmpty() && 
                              grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
                
                InAppLogger.log(TAG, "BLUETOOTH_CONNECT permission result: granted=$granted")
                adapter.onBluetoothPermissionResult(granted)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up TTS resources
        if (textToSpeech != null) {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        
        // Unregister voice settings listener
        voiceSettingsPrefs?.unregisterOnSharedPreferenceChangeListener(voiceSettingsListener)
    }
} 