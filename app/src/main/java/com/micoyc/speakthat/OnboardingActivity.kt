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
    private lateinit var adapter: OnboardingPagerAdapter
    private var skipPermissionPage = false
    
    // TTS support
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var voiceSettingsPrefs: SharedPreferences? = null
    private val voiceSettingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "speech_rate", "pitch", "voice_name", "language", "audio_usage", "content_type" -> {
                applyVoiceSettings()
            }
        }
    }
    

    

    
    companion object {
        private const val PREFS_NAME = "SpeakThatPrefs"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val TAG = "OnboardingActivity"
        
        fun hasSeenOnboarding(context: android.content.Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply saved theme FIRST before anything else
        val mainPrefs = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE)
        applySavedTheme(mainPrefs)
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        // Initialize voice settings listener
        voiceSettingsPrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
        voiceSettingsPrefs?.registerOnSharedPreferenceChangeListener(voiceSettingsListener)
        
        // Initialize TTS
        initializeTextToSpeech()
        
        // Initialize view binding
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configure system UI for proper insets handling
        configureSystemUI()

        // Determine if we should skip the permission page
        skipPermissionPage = isNotificationServiceEnabled()
        setupOnboarding()
    }

    private fun applySavedTheme(prefs: android.content.SharedPreferences) {
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Force a full rebind to ensure permission button is visible and text updates
        adapter.notifyDataSetChanged()
        val currentPage = binding.viewPager.currentItem
        updateButtonText(currentPage, adapter.itemCount)
        
        // Speak current page content when resuming
        speakCurrentPageContent()
    }
    
    override fun onPause() {
        super.onPause()
        // Stop TTS when leaving the activity
        textToSpeech?.stop()
    }
    
    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
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
            
            // Speak welcome message
            speakText(getLocalizedTtsString(R.string.tts_onboarding_welcome))
        } else {
            InAppLogger.log(TAG, "TTS initialization failed")
        }
    }
    
    private fun applyVoiceSettings() {
        textToSpeech?.let { tts ->
            voiceSettingsPrefs?.let { prefs ->
                VoiceSettingsActivity.applyVoiceSettings(tts, prefs)
            }
        }
    }
    
    private fun speakText(text: String) {
        if (isTtsInitialized && textToSpeech != null) {
            // Stop any current speech first
            textToSpeech?.stop()
            // Then speak the new text
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "onboarding_utterance")
            InAppLogger.log(TAG, "Speaking: ${text.take(50)}...")
        }
    }
    
    private fun speakCurrentPageContent() {
        if (!isTtsInitialized) return
        
        val currentPage = binding.viewPager.currentItem
        val pageContent = if (skipPermissionPage) {
            // When permission page is skipped (7 pages total)
            when (currentPage) {
                0 -> getLocalizedTtsString(R.string.tts_onboarding_welcome)
                1 -> getLocalizedTtsString(R.string.tts_onboarding_privacy)
                2 -> getLocalizedTtsString(R.string.tts_onboarding_filters)
                3 -> getLocalizedTtsString(R.string.tts_onboarding_apps)
                4 -> getLocalizedTtsString(R.string.tts_onboarding_words)
                5 -> getLocalizedTtsString(R.string.tts_onboarding_rules)
                6 -> getLocalizedTtsString(R.string.tts_onboarding_complete)
                else -> ""
            }
        } else {
            // When permission page is included (8 pages total)
            when (currentPage) {
                0 -> getLocalizedTtsString(R.string.tts_onboarding_welcome)
                1 -> getLocalizedTtsString(R.string.tts_onboarding_permission)
                2 -> getLocalizedTtsString(R.string.tts_onboarding_privacy)
                3 -> getLocalizedTtsString(R.string.tts_onboarding_filters)
                4 -> getLocalizedTtsString(R.string.tts_onboarding_apps)
                5 -> getLocalizedTtsString(R.string.tts_onboarding_words)
                6 -> getLocalizedTtsString(R.string.tts_onboarding_rules)
                7 -> getLocalizedTtsString(R.string.tts_onboarding_complete)
                else -> ""
            }
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
        // Set up ViewPager2 with or without permission page
        adapter = if (skipPermissionPage) {
            OnboardingPagerAdapter(skipPermissionPage = true)
        } else {
            OnboardingPagerAdapter()
        }
        binding.viewPager.adapter = adapter
        
        // Set up page indicator dots
        setupPageIndicator(adapter.itemCount)
        
        // Set up button listeners
        binding.buttonSkip.setOnClickListener {
            completeOnboarding()
        }
        
        binding.buttonNext.setOnClickListener {
            if (binding.viewPager.currentItem < adapter.itemCount - 1) {
                binding.viewPager.currentItem += 1
            } else {
                completeOnboarding()
            }
        }
        
        // Prevent swiping past permission page until permission is granted
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateButtonText(position, adapter.itemCount)
                updatePageIndicator(position)
                // Speak the content of the new page immediately
                speakCurrentPageContent()
            }
        })
        binding.viewPager.isUserInputEnabled = true // default
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                val currentPage = binding.viewPager.currentItem
                if (!skipPermissionPage && currentPage == 1 && !isNotificationServiceEnabled()) {
                    // Disable swiping on permission page if permission not granted
                    binding.viewPager.isUserInputEnabled = false
                } else {
                    binding.viewPager.isUserInputEnabled = true
                }
            }
        })
        
        // Set initial button text
        updateButtonText(0, adapter.itemCount)
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
            }
            binding.pageIndicator.addView(dot)
        }
        updatePageIndicator(0)
    }
    
    private fun updatePageIndicator(currentPage: Int) {
        for (i in 0 until binding.pageIndicator.childCount) {
            val dot = binding.pageIndicator.getChildAt(i) as android.widget.ImageView
            dot.isSelected = (i == currentPage)
        }
    }
    
    private fun updateButtonText(currentPage: Int, totalPages: Int) {
        if (currentPage == totalPages - 1) {
            binding.buttonNext.text = "Get Started"
        } else {
            binding.buttonNext.text = "Next"
        }
        
        // Disable Next button on permission page if permissions not granted
        if (!skipPermissionPage && currentPage == 1) { // Permission page
            val hasPermission = isNotificationServiceEnabled()
            binding.buttonNext.isEnabled = hasPermission
            if (!hasPermission) {
                binding.buttonNext.text = "Grant Permission First"
            }
        } else {
            binding.buttonNext.isEnabled = true
        }
    }
    
    private fun isNotificationServiceEnabled(): Boolean {
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
    
    private fun openNotificationSettings() {
        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }
    
    fun refreshPermissionStatus() {
        // Update the current page to reflect permission status
        val currentPage = binding.viewPager.currentItem
        updateButtonText(currentPage, binding.viewPager.adapter?.itemCount ?: 4)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            1001 -> { // Bluetooth permission
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    InAppLogger.log("OnboardingActivity", "Bluetooth permission granted")
                    // Refresh the current page to reload Bluetooth devices
                    adapter.notifyDataSetChanged()
                } else {
                    InAppLogger.log("OnboardingActivity", "Bluetooth permission denied")
                    android.widget.Toast.makeText(
                        this,
                        "Bluetooth permission is required to configure Bluetooth rules",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            1002 -> { // WiFi permission
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    InAppLogger.log("OnboardingActivity", "WiFi permission granted")
                    // Refresh the current page to reload WiFi networks
                    adapter.notifyDataSetChanged()
                } else {
                    InAppLogger.log("OnboardingActivity", "WiFi permission denied")
                    android.widget.Toast.makeText(
                        this,
                        "WiFi permission is required to configure WiFi rules",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
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