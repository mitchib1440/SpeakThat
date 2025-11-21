package com.micoyc.speakthat

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.micoyc.speakthat.databinding.ActivityAboutBinding
import java.util.Locale

class AboutActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAboutBinding
    private var textToSpeech: TextToSpeech? = null
    private var isSpeaking = false
    
    // Voice settings listener
    private var voiceSettingsPrefs: android.content.SharedPreferences? = null
    private val voiceSettingsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "speech_rate", "pitch", "voice_name", "audio_usage", "content_type" -> {
                applyVoiceSettings()
            }
            "language", "tts_language" -> {
                // For language changes, we need to reinitialize TTS to ensure it uses the new language
                InAppLogger.log("AboutActivity", "Language settings changed - reinitializing TTS")
                reinitializeTtsWithCurrentSettings()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply saved theme FIRST before anything else
        val mainPrefs = getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
        applySavedTheme(mainPrefs)
        
        // Initialize voice settings listener
        voiceSettingsPrefs = getSharedPreferences("VoiceSettings", android.content.Context.MODE_PRIVATE)
        voiceSettingsPrefs?.registerOnSharedPreferenceChangeListener(voiceSettingsListener)
        
        // Initialize view binding
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.about)
        
        // Set up click listeners
        setupClickListeners()
        
        // Load app information
        loadAppInfo()
        
        // Load statistics
        loadStatistics()
    }

    private fun applySavedTheme(prefs: android.content.SharedPreferences) {
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
    
    private fun setupClickListeners() {
        binding.buttonTTS.setOnClickListener {
            if (isSpeaking) {
                stopTTS()
            } else {
                startTTS()
            }
        }
        
        // Add update check button
        binding.buttonCheckUpdates.setOnClickListener {
            UpdateFeature.startUpdateActivity(this, forceCheck = true)
        }
    }
    
    private fun loadAppInfo() {
        // App version and build info with build variant
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val buildVariant = InAppLogger.getBuildVariantInfo()
        binding.textVersion.text = getString(R.string.version_format_with_variant, packageInfo.versionName, buildVariant)
        
        // App description
        binding.textDescription.text = getString(R.string.app_description)
        
        // Developer info
        binding.textDeveloper.text = getString(R.string.developer_info)
        
        // Features list
        binding.textFeatures.text = getString(R.string.app_features)
        
        // License info
        binding.textLicense.text = getString(R.string.license_info)
    }
    
    private fun loadStatistics() {
        try {
            val statsManager = StatisticsManager.getInstance(this)
            val stats = statsManager.getStats()
            
            val received = (stats["notifications_received"] as? Number)?.toInt() ?: 0
            val read = (stats["notifications_read"] as? Number)?.toInt() ?: 0
            val readoutsInterrupted = (stats["readouts_interrupted"] as? Number)?.toInt() ?: 0
            val percentage = (stats["percentage_read"] as? Number)?.toDouble() ?: 0.0
            val filterReasons = (stats["filter_reasons"] as? Map<*, *>)?.entries
                ?.mapNotNull { entry ->
                    val key = entry.key as? String ?: return@mapNotNull null
                    val value = (entry.value as? Number)?.toInt() ?: return@mapNotNull null
                    key to value
                }?.toMap() ?: emptyMap()
            val appsRead = (stats["apps_read"] as? Collection<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
            
            val statsText = buildString {
                // Basic stats
                append(getString(R.string.statistics_notifications_received))
                append(": ")
                append(received)
                append("\n")
                
                append(getString(R.string.statistics_notifications_read_label))
                append(": ")
                append(read)
                append("\n")
                
                append(getString(R.string.statistics_readouts_interrupted))
                append(": ")
                append(readoutsInterrupted)
                append("\n")
                
                append(getString(R.string.statistics_percentage_read))
                append(": ")
                append(String.format(Locale.getDefault(), getString(R.string.statistics_percentage_format), percentage))
                append("\n\n")
                
                // Filter reasons
                if (filterReasons.isNotEmpty()) {
                    append(getString(R.string.statistics_filter_reasons))
                    append(":\n")
                    filterReasons.entries.sortedByDescending { it.value }.forEach { (reason, count) ->
                        val reasonString = getFilterReasonString(reason)
                        append(String.format(Locale.getDefault(), getString(R.string.statistics_filter_count_format), reasonString, count))
                        append("\n")
                    }
                    append("\n")
                } else {
                    append(getString(R.string.statistics_no_filter_reasons))
                    append("\n\n")
                }
                
                // Apps read
                if (appsRead.isNotEmpty()) {
                    append(getString(R.string.statistics_apps_read))
                    append(": ")
                    append(appsRead.sorted().joinToString(", "))
                } else {
                    append(getString(R.string.statistics_no_apps_read))
                }
            }
            
            binding.textStatistics.text = statsText
        } catch (e: Exception) {
            android.util.Log.e("AboutActivity", "Error loading statistics", e)
            binding.textStatistics.text = getString(R.string.statistics_title)
        }
    }
    
    private fun getFilterReasonString(reason: String): String {
        return when (reason) {
            StatisticsManager.FILTER_MASTER_SWITCH -> getString(R.string.statistics_filter_reason_master_switch)
            StatisticsManager.FILTER_DND -> getString(R.string.statistics_filter_reason_dnd)
            StatisticsManager.FILTER_AUDIO_MODE -> getString(R.string.statistics_filter_reason_audio_mode)
            StatisticsManager.FILTER_PHONE_CALLS -> getString(R.string.statistics_filter_reason_phone_calls)
            StatisticsManager.FILTER_WORD_FILTERS -> getString(R.string.statistics_filter_reason_word_filters)
            StatisticsManager.FILTER_CONDITIONAL_RULES -> getString(R.string.statistics_filter_reason_conditional_rules)
            StatisticsManager.FILTER_MEDIA_BEHAVIOR -> getString(R.string.statistics_filter_reason_media_behavior)
            StatisticsManager.FILTER_SKIP_MODE -> getString(R.string.statistics_filter_reason_skip_mode)
            StatisticsManager.FILTER_APP_LIST -> getString(R.string.statistics_filter_reason_app_list)
            StatisticsManager.FILTER_DEDUPLICATION -> getString(R.string.statistics_filter_reason_deduplication)
            StatisticsManager.FILTER_DISMISSAL_MEMORY -> getString(R.string.statistics_filter_reason_dismissal_memory)
            StatisticsManager.FILTER_GROUP_SUMMARY -> getString(R.string.statistics_filter_reason_group_summary)
            StatisticsManager.FILTER_SELF_PACKAGE -> getString(R.string.statistics_filter_reason_self_package)
            else -> reason
        }
    }
    
    private fun startTTS() {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(this) { status ->
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
                    
                    // Set up utterance progress listener
                    textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            isSpeaking = true
                            updateButtonState()
                        }
                        
                        override fun onDone(utteranceId: String?) {
                            isSpeaking = false
                            updateButtonState()
                        }
                        
                        override fun onError(utteranceId: String?) {
                            isSpeaking = false
                            updateButtonState()
                            Toast.makeText(this@AboutActivity, getString(R.string.tts_error_occurred), Toast.LENGTH_SHORT).show()
                        }
                    })
                    
                    speakAboutApp()
                } else {
                    Toast.makeText(this, getString(R.string.tts_initialization_failed), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            speakAboutApp()
        }
    }
    
    private fun speakAboutApp() {
        val aboutText = buildAboutText()
        
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
        InAppLogger.log("AboutActivity", "Audio attributes applied - Usage: $ttsUsage, Content: $contentType")
        
        // Create volume bundle with proper volume parameters
        val volumeParams = VoiceSettingsActivity.createVolumeBundle(ttsVolume, ttsUsage, speakerphoneEnabled)
        
        textToSpeech?.speak(aboutText, TextToSpeech.QUEUE_FLUSH, volumeParams, getString(R.string.tts_utterance_about_app))
    }
    
    private fun stopTTS() {
        textToSpeech?.stop()
        isSpeaking = false
        updateButtonState()
    }
    
    private fun updateButtonState() {
        runOnUiThread {
            if (isSpeaking) {
                binding.buttonTTS.text = getString(R.string.stop_reading)
                binding.buttonTTS.setIconResource(R.drawable.ic_pause_24)
            } else {
                binding.buttonTTS.text = getString(R.string.read_about_app)
                binding.buttonTTS.setIconResource(R.drawable.ic_play_arrow_24)
            }
        }
    }
    
    private fun applyVoiceSettings() {
        textToSpeech?.let { tts ->
            voiceSettingsPrefs?.let { prefs ->
                VoiceSettingsActivity.applyVoiceSettings(tts, prefs)
            }
        }
    }
    
    private fun reinitializeTtsWithCurrentSettings() {
        // Stop any current speech
        textToSpeech?.stop()
        isSpeaking = false
        updateButtonState()
        
        // Shutdown existing TTS instance
        textToSpeech?.shutdown()
        
        // Create new TTS instance with current settings
        textToSpeech = TextToSpeech(this) { status ->
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
                
                // Set up utterance progress listener
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    @Suppress("DEPRECATION")
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                        updateButtonState()
                    }
                    
                    @Suppress("DEPRECATION")
                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        updateButtonState()
                    }
                    
                    @Suppress("DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        updateButtonState()
                        Toast.makeText(this@AboutActivity, getString(R.string.tts_error_occurred), Toast.LENGTH_SHORT).show()
                    }
                })
                
                InAppLogger.log("AboutActivity", "TTS reinitialized successfully with new language settings")
            } else {
                InAppLogger.log("AboutActivity", "TTS reinitialization failed with status: $status")
                Toast.makeText(this@AboutActivity, getString(R.string.tts_initialization_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun buildAboutText(): String {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val buildVariant = InAppLogger.getBuildVariantInfo()
        val version = getString(R.string.version_format_with_variant, packageInfo.versionName, buildVariant)
        
        // Get the user's TTS language setting
        // Use hardcoded preference key to avoid localization issues
        val ttsLanguageCode = voiceSettingsPrefs?.getString("tts_language", getString(R.string.system_default)) ?: getString(R.string.system_default)
        
        val introText = TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, R.string.tts_about_intro)
        val descriptionText = TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, R.string.tts_about_description)
        val featuresIntroText = TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, R.string.tts_about_features_intro)
        val featuresText = TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, R.string.tts_about_features)
        val developerText = TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, R.string.tts_about_developer)
        val licenseText = TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, R.string.tts_about_license)
        val thankYouText = TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, R.string.tts_about_thank_you)
        
        return """
            $introText $version.
            
            $descriptionText
            
            $featuresIntroText $featuresText
            
            $developerText
            
            $licenseText
            
            $thankYouText
        """.trimIndent()
    }
    
    override fun onResume() {
        super.onResume()
        // Reload statistics when activity resumes to show updated data
        loadStatistics()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Unregister voice settings listener
        voiceSettingsPrefs?.unregisterOnSharedPreferenceChangeListener(voiceSettingsListener)
        
        textToSpeech?.let { tts ->
            tts.stop()
            tts.shutdown()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
} 