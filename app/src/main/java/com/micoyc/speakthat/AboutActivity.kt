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
            "speech_rate", "pitch", "voice_name", "language", "audio_usage", "content_type" -> {
                applyVoiceSettings()
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
        textToSpeech?.speak(aboutText, TextToSpeech.QUEUE_FLUSH, null, getString(R.string.tts_utterance_about_app))
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
    
    private fun buildAboutText(): String {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val buildVariant = InAppLogger.getBuildVariantInfo()
        val version = getString(R.string.version_format_with_variant, packageInfo.versionName, buildVariant)
        
        // Get the user's TTS language setting
        val ttsLanguageCode = voiceSettingsPrefs?.getString(getString(R.string.prefs_tts_language), getString(R.string.system_default)) ?: getString(R.string.system_default)
        
        return """
            ${TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, R.string.tts_about_intro)} $version.
            
            ${TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, R.string.tts_about_description)}
            
            ${TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, R.string.tts_about_features_intro)} ${TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, R.string.tts_about_features)}
            
            ${TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, R.string.tts_about_developer)}
            
            ${TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, R.string.tts_about_license)}
            
            ${TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, R.string.tts_about_thank_you)}
        """.trimIndent()
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