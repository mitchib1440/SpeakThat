package com.micoyc.speakthat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.micoyc.speakthat.databinding.FragmentOnboardingKillNoiseBinding
import com.micoyc.speakthat.databinding.FragmentOnboardingLanguageBinding
import com.micoyc.speakthat.databinding.FragmentOnboardingPermissionBinding
import com.micoyc.speakthat.databinding.FragmentOnboardingPlaceholderBinding
import com.micoyc.speakthat.databinding.FragmentOnboardingPrivacyBinding
import com.micoyc.speakthat.databinding.FragmentOnboardingSystemCheckBinding
import com.micoyc.speakthat.databinding.FragmentOnboardingWelcomeBinding

/**
 * New Onboarding Pager Adapter for 10-page onboarding flow
 * Pages 1-4 are implemented, pages 5-10 are placeholders
 */
class OnboardingPagerAdapterNew : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    // Callback for permission status changes
    var onPermissionStatusChanged: (() -> Unit)? = null
    
    // Callback for navigation control (enable/disable next/back buttons)
    var onNavigationControlChanged: ((nextEnabled: Boolean, backEnabled: Boolean) -> Unit)? = null
    
    // Callback for swipe control (enable/disable swiping)
    var onSwipeControlChanged: ((swipeEnabled: Boolean) -> Unit)? = null
    
    // Callback for test completion
    var onSystemCheckCompleted: ((success: Boolean) -> Unit)? = null
    
    // Callback to stop TTS
    var onStopTts: (() -> Unit)? = null
    
    // Callback to skip to a specific page
    var onSkipToPage: ((pageIndex: Int) -> Unit)? = null
    
    companion object {
        private const val VIEW_TYPE_LANGUAGE = 0
        private const val VIEW_TYPE_WELCOME = 1
        private const val VIEW_TYPE_PRIVACY = 2
        private const val VIEW_TYPE_PERMISSION = 3
        private const val VIEW_TYPE_SYSTEM_CHECK = 4
        private const val VIEW_TYPE_KILL_NOISE = 5
        private const val VIEW_TYPE_PLACEHOLDER = 6
        
        private const val PAGE_COUNT = 10
        
        // Request code for POST_NOTIFICATIONS permission
        const val REQUEST_POST_NOTIFICATIONS = 1001
    }
    
    override fun getItemCount(): Int = PAGE_COUNT
    
    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> VIEW_TYPE_LANGUAGE
            1 -> VIEW_TYPE_WELCOME
            2 -> VIEW_TYPE_PRIVACY
            3 -> VIEW_TYPE_PERMISSION
            4 -> VIEW_TYPE_SYSTEM_CHECK
            5 -> VIEW_TYPE_KILL_NOISE
            else -> VIEW_TYPE_PLACEHOLDER
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        
        return when (viewType) {
            VIEW_TYPE_LANGUAGE -> {
                val binding = FragmentOnboardingLanguageBinding.inflate(inflater, parent, false)
                LanguageViewHolder(binding)
            }
            VIEW_TYPE_WELCOME -> {
                val binding = FragmentOnboardingWelcomeBinding.inflate(inflater, parent, false)
                WelcomeViewHolder(binding)
            }
            VIEW_TYPE_PRIVACY -> {
                val binding = FragmentOnboardingPrivacyBinding.inflate(inflater, parent, false)
                PrivacyViewHolder(binding)
            }
            VIEW_TYPE_PERMISSION -> {
                val binding = FragmentOnboardingPermissionBinding.inflate(inflater, parent, false)
                PermissionViewHolder(binding)
            }
            VIEW_TYPE_SYSTEM_CHECK -> {
                val binding = FragmentOnboardingSystemCheckBinding.inflate(inflater, parent, false)
                SystemCheckViewHolder(binding)
            }
            VIEW_TYPE_KILL_NOISE -> {
                val binding = FragmentOnboardingKillNoiseBinding.inflate(inflater, parent, false)
                KillNoiseViewHolder(binding)
            }
            else -> {
                val binding = FragmentOnboardingPlaceholderBinding.inflate(inflater, parent, false)
                PlaceholderViewHolder(binding)
            }
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is LanguageViewHolder -> holder.bind()
            is WelcomeViewHolder -> holder.bind()
            is PrivacyViewHolder -> holder.bind()
            is PermissionViewHolder -> holder.bind()
            is SystemCheckViewHolder -> holder.bind()
            is KillNoiseViewHolder -> holder.bind()
            is PlaceholderViewHolder -> holder.bind(position)
        }
    }
    
    /**
     * ViewHolder for Language Selection Page (Page 1)
     */
    inner class LanguageViewHolder(
        private val binding: FragmentOnboardingLanguageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind() {
            setupLanguageSelector()
            setupTranslateButton()
        }
        
        private fun setupLanguageSelector() {
            // Get all available language presets (excluding custom)
            val allPresets = LanguagePresetManager.getAllPresets()
            val nonCustomPresets = allPresets.filter { !it.isCustom }
            
            // Create adapter with preset display names
            val presetNames = nonCustomPresets.map { it.displayName }
            
            val presetAdapter = ArrayAdapter(
                binding.root.context,
                android.R.layout.simple_spinner_item,
                presetNames
            )
            presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.languagePresetSpinner.adapter = presetAdapter
            
            // Get the currently selected language from settings
            val voiceSettingsPrefs = binding.root.context.getSharedPreferences("VoiceSettings", android.content.Context.MODE_PRIVATE)
            val currentLanguage = voiceSettingsPrefs.getString("language", "en_US")
            
            // Find the index of the currently selected language
            val currentPresetIndex = nonCustomPresets.indexOfFirst { it.uiLocale == currentLanguage }
            if (currentPresetIndex >= 0) {
                // Set the spinner to the currently selected language
                binding.languagePresetSpinner.setSelection(currentPresetIndex)
                InAppLogger.log("OnboardingLanguagePage", "Restored language selection to: ${nonCustomPresets[currentPresetIndex].displayName}")
            }
            
            // Add listener to handle preset selection
            binding.languagePresetSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    if (position >= 0 && position < nonCustomPresets.size) {
                        val selectedPreset = nonCustomPresets[position]
                        InAppLogger.log("OnboardingLanguagePage", "Language preset selected: ${selectedPreset.displayName}")
                        
                        // Apply the preset settings using the onboarding-specific method
                        LanguagePresetManager.applyPresetForOnboarding(binding.root.context, selectedPreset)
                        
                        InAppLogger.log("OnboardingLanguagePage", "Preset applied successfully: ${selectedPreset.displayName}")
                    }
                }
                
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                    // Do nothing
                }
            }
        }
        
        private fun setupTranslateButton() {
            binding.buttonTranslate.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.speakthat.app/translate"))
                binding.root.context.startActivity(intent)
                InAppLogger.log("OnboardingLanguagePage", "Opening Weblate translation page")
            }
        }
    }
    
    /**
     * ViewHolder for Welcome Page (Page 2)
     */
    inner class WelcomeViewHolder(
        private val binding: FragmentOnboardingWelcomeBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind() {
            // No interactive elements on this page
            InAppLogger.log("OnboardingWelcomePage", "Welcome page loaded")
        }
    }
    
    /**
     * ViewHolder for Privacy Page (Page 3)
     */
    inner class PrivacyViewHolder(
        private val binding: FragmentOnboardingPrivacyBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind() {
            setupPrivacyPolicyButton()
        }
        
        private fun setupPrivacyPolicyButton() {
            binding.buttonPrivacyPolicy.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.speakthat.app/privacy"))
                binding.root.context.startActivity(intent)
                InAppLogger.log("OnboardingPrivacyPage", "Opening privacy policy page")
            }
        }
    }
    
    /**
     * ViewHolder for Permission Page (Page 4)
     */
    inner class PermissionViewHolder(
        private val binding: FragmentOnboardingPermissionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind() {
            setupOpenSettingsButton()
            updatePermissionStatus()
        }
        
        private fun setupOpenSettingsButton() {
            binding.buttonOpenSettings.setOnClickListener {
                openNotificationSettings()
            }
        }
        
        private fun openNotificationSettings() {
            try {
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                
                // Try to highlight/pre-select SpeakThat in the list
                // This uses an undocumented feature that works on some Android versions
                intent.putExtra(":settings:fragment_args_key", binding.root.context.packageName)
                intent.putExtra(":settings:show_fragment_args", android.os.Bundle().apply {
                    putString("package", binding.root.context.packageName)
                })
                
                binding.root.context.startActivity(intent)
                InAppLogger.log("OnboardingPermissionPage", "Opening notification listener settings")
            } catch (e: Exception) {
                // Fallback to regular settings page if the specific intent fails
                try {
                    val fallbackIntent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    binding.root.context.startActivity(fallbackIntent)
                    InAppLogger.log("OnboardingPermissionPage", "Opened notification settings (fallback)")
                } catch (e2: Exception) {
                    InAppLogger.logError("OnboardingPermissionPage", "Failed to open notification settings: ${e2.message}")
                    android.widget.Toast.makeText(
                        binding.root.context,
                        "Failed to open notification settings",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        
        private fun updatePermissionStatus() {
            val hasPermission = isNotificationServiceEnabled()
            
            if (hasPermission) {
                binding.textPermissionStatus.visibility = android.view.View.VISIBLE
                binding.textPermissionStatus.text = binding.root.context.getString(R.string.onboarding_permission_granted)
                binding.textPermissionStatus.setTextColor(binding.root.context.getColor(R.color.green_200))
            } else {
                binding.textPermissionStatus.visibility = android.view.View.VISIBLE
                binding.textPermissionStatus.text = binding.root.context.getString(R.string.onboarding_permission_not_granted)
                binding.textPermissionStatus.setTextColor(binding.root.context.getColor(R.color.white_200))
            }
            
            // Notify the activity that permission status may have changed
            // The activity's page change callback will update swipe control
            onPermissionStatusChanged?.invoke()
        }
        
        private fun isNotificationServiceEnabled(): Boolean {
            val packageName = binding.root.context.packageName
            val flat = android.provider.Settings.Secure.getString(
                binding.root.context.contentResolver, 
                "enabled_notification_listeners"
            )
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
        
        /**
         * Called when the user returns to this page (e.g., from Settings)
         */
        fun refreshPermissionStatus() {
            updatePermissionStatus()
        }
    }
    
    /**
     * ViewHolder for System Check Page (Page 5)
     * Manages 4 UI states: Intro, Testing, Feedback, Failure
     */
    inner class SystemCheckViewHolder(
        private val binding: FragmentOnboardingSystemCheckBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private val handler = Handler(Looper.getMainLooper())
        private lateinit var selfTestHelper: SelfTestHelper
        private var testInProgress = false
        private var testCompleted = false
        private var testSuccess = false
        
        // Test progress tracking (10 steps total)
        private var currentTestStep = 0
        private val totalTestSteps = 10
        
        fun bind() {
            selfTestHelper = SelfTestHelper(binding.root.context)
            
            // Initialize to intro state
            showIntroState()
            
            setupButtons()
        }
        
        private fun setupButtons() {
            // State 1: Intro - "Run System Check" button
            binding.buttonRunTest.setOnClickListener {
                requestPermissionAndStartTest()
            }
            
            // State 3: Feedback buttons
            binding.buttonFeedbackYes.setOnClickListener {
                onUserConfirmedSuccess()
            }
            
            binding.buttonFeedbackNo.setOnClickListener {
                onUserReportedFailure()
            }
        }
        
        private fun showIntroState() {
            binding.layoutStateIntro.visibility = View.VISIBLE
            binding.layoutStateTesting.visibility = View.GONE
            binding.layoutStateFeedback.visibility = View.GONE
            binding.layoutStateFailure.visibility = View.GONE
            
            testInProgress = false
            testCompleted = false
            
            // Re-enable navigation and swiping
            onNavigationControlChanged?.invoke(true, true)
            onSwipeControlChanged?.invoke(true)
            
            InAppLogger.log("OnboardingSystemCheck", "Showing intro state")
        }
        
        private fun showTestingState() {
            binding.layoutStateIntro.visibility = View.GONE
            binding.layoutStateTesting.visibility = View.VISIBLE
            binding.layoutStateFeedback.visibility = View.GONE
            binding.layoutStateFailure.visibility = View.GONE
            
            // Reset progress bar
            binding.progressBar.progress = 0
            currentTestStep = 0
            
            testInProgress = true
            testCompleted = false
            
            // Disable navigation and swiping during test
            onNavigationControlChanged?.invoke(false, false)
            onSwipeControlChanged?.invoke(false)
            
            InAppLogger.log("OnboardingSystemCheck", "Showing testing state")
        }
        
        private fun showFeedbackState() {
            binding.layoutStateIntro.visibility = View.GONE
            binding.layoutStateTesting.visibility = View.GONE
            binding.layoutStateFeedback.visibility = View.VISIBLE
            binding.layoutStateFailure.visibility = View.GONE
            
            testInProgress = false
            testCompleted = false
            
            // Keep navigation and swiping disabled until user responds
            onNavigationControlChanged?.invoke(false, false)
            onSwipeControlChanged?.invoke(false)
            
            InAppLogger.log("OnboardingSystemCheck", "Showing feedback state")
        }
        
        private fun showFailureState(reason: String) {
            binding.layoutStateIntro.visibility = View.GONE
            binding.layoutStateTesting.visibility = View.GONE
            binding.layoutStateFeedback.visibility = View.GONE
            binding.layoutStateFailure.visibility = View.VISIBLE
            
            // Format the failure description with the reason
            val description = binding.root.context.getString(
                R.string.onboarding_system_check_failure_card_description,
                reason
            )
            binding.textFailureReason.text = description
            
            testInProgress = false
            testCompleted = true
            testSuccess = false
            
            // Re-enable navigation and swiping
            onNavigationControlChanged?.invoke(true, true)
            onSwipeControlChanged?.invoke(true)
            
            // Save failure to SharedPreferences
            saveTestResult(false)
            
            InAppLogger.log("OnboardingSystemCheck", "Showing failure state: $reason")
        }
        
        private fun requestPermissionAndStartTest() {
            val context = binding.root.context
            
            // Stop any ongoing TTS first
            onStopTts?.invoke()
            InAppLogger.log("OnboardingSystemCheck", "Stopped TTS before starting test")
            
            // Check if we need POST_NOTIFICATIONS permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                    
                    // Request permission through the activity
                    if (context is OnboardingActivity) {
                        InAppLogger.log("OnboardingSystemCheck", "Requesting POST_NOTIFICATIONS permission")
                        ActivityCompat.requestPermissions(
                            context,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            REQUEST_POST_NOTIFICATIONS
                        )
                        // Test will be started in onPermissionResult callback
                    } else {
                        InAppLogger.logError("OnboardingSystemCheck", "Cannot request permission: context is not OnboardingActivity")
                        showFailureState(binding.root.context.getString(R.string.onboarding_system_check_failure_permission_denied))
                    }
                    return
                }
            }
            
            // Permission already granted or not needed, start test
            startSystemCheck()
        }
        
        fun onPermissionResult(granted: Boolean) {
            if (granted) {
                InAppLogger.log("OnboardingSystemCheck", "POST_NOTIFICATIONS permission granted, starting test")
                startSystemCheck()
            } else {
                InAppLogger.log("OnboardingSystemCheck", "POST_NOTIFICATIONS permission denied")
                showFailureState(binding.root.context.getString(R.string.onboarding_system_check_failure_permission_denied))
            }
        }
        
        private fun startSystemCheck() {
            InAppLogger.log("OnboardingSystemCheck", "=== SYSTEM CHECK STARTED ===")
            showTestingState()
            
            // Start the multi-step test process
            runTestSequence()
        }
        
        private fun runTestSequence() {
            val context = binding.root.context
            
            // Step 1: Check NotificationListener permission
            advanceProgress("Checking notification access")
            val activity = context as? OnboardingActivity
            if (activity == null || !activity.isNotificationServiceEnabled()) {
                showFailureState("SpeakThat doesn't have notification access. Please grant notification access on the previous page.")
                return
            }
            
            handler.postDelayed({
                if (!testInProgress) return@postDelayed // Test was cancelled
                
                // Step 2: Check master switch
                advanceProgress("Checking master switch")
                val prefs = context.getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
                val masterEnabled = prefs.getBoolean("master_switch_enabled", true)
                if (!masterEnabled) {
                    showFailureState("SpeakThat's master switch is turned off. Please enable it in settings.")
                    return@postDelayed
                }
                
                handler.postDelayed({
                    if (!testInProgress) return@postDelayed
                    
                    // Step 3: Check volume
                    advanceProgress("Checking volume")
                    val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                    val voicePrefs = context.getSharedPreferences("VoiceSettings", android.content.Context.MODE_PRIVATE)
                    val audioUsageIndex = voicePrefs.getInt("audio_usage", 0)
                    val streamType = getStreamTypeFromAudioUsage(audioUsageIndex)
                    val currentVolume = audioManager.getStreamVolume(streamType)
                    
                    if (currentVolume == 0) {
                        val streamName = getStreamTypeName(streamType)
                        showFailureState("Your $streamName volume is turned down. Please turn up your volume and try again.")
                        return@postDelayed
                    }
                    
                    handler.postDelayed({
                        if (!testInProgress) return@postDelayed
                        
                        // Step 4: Check audio mode
                        advanceProgress("Checking audio mode")
                        val ringerMode = audioManager.ringerMode
                        val honorSilent = prefs.getBoolean("honour_silent_mode", prefs.getBoolean("honour_audio_mode", true))
                        val honorVibrate = prefs.getBoolean("honour_vibrate_mode", prefs.getBoolean("honour_audio_mode", true))
                        
                        if ((ringerMode == android.media.AudioManager.RINGER_MODE_SILENT && honorSilent) ||
                            (ringerMode == android.media.AudioManager.RINGER_MODE_VIBRATE && honorVibrate)) {
                            showFailureState("Your phone is in Silent or Vibrate mode. Please switch to normal mode or disable 'Honor Audio Mode' in settings.")
                            return@postDelayed
                        }
                        
                        handler.postDelayed({
                            if (!testInProgress) return@postDelayed
                            
                            // Step 5-6: Check filters and rules (simplified check)
                            advanceProgress("Checking filters")
                            handler.postDelayed({
                                if (!testInProgress) return@postDelayed
                                
                                advanceProgress("Checking rules")
                                
                                handler.postDelayed({
                                    if (!testInProgress) return@postDelayed
                                    
                                    // Step 7: Post test notification
                                    advanceProgress("Posting test notification")
                                    val posted = selfTestHelper.postTestNotification()
                                    
                                    if (!posted) {
                                        showFailureState("Failed to post test notification. Please check notification settings.")
                                        return@postDelayed
                                    }
                                    
                                    handler.postDelayed({
                                        if (!testInProgress) return@postDelayed
                                        
                                        // Step 8-10: Monitor for notification and TTS
                                        advanceProgress("Waiting for notification")
                                        
                                        // Use SelfTestHelper to monitor logs
                                        selfTestHelper.monitorLogsForTestNotification(10000) { result ->
                                            handler.post {
                                                if (!testInProgress) return@post // Test was cancelled
                                                
                                                when (result.status) {
                                                    SelfTestHelper.TestStatus.NOT_RECEIVED -> {
                                                        showFailureState("The test notification was not received by SpeakThat. This may be a system issue.")
                                                    }
                                                    SelfTestHelper.TestStatus.RECEIVED_NOT_READ -> {
                                                        val plainReason = translateErrorToPlainEnglish(result.blockingReason)
                                                        showFailureState(plainReason)
                                                    }
                                                    SelfTestHelper.TestStatus.READ_SUCCESSFULLY -> {
                                                        // Progress to 100%
                                                        advanceProgress("Test complete")
                                                        binding.progressBar.progress = 100
                                                        
                                                        // Small delay before showing feedback
                                                        handler.postDelayed({
                                                            if (!testInProgress) return@postDelayed
                                                            showFeedbackState()
                                                        }, 500)
                                                    }
                                                }
                                            }
                                        }
                                    }, 300)
                                }, 300)
                            }, 300)
                        }, 300)
                    }, 300)
                }, 300)
            }, 300)
        }
        
        private fun advanceProgress(stepName: String) {
            currentTestStep++
            val progress = (currentTestStep * 100) / totalTestSteps
            binding.progressBar.progress = progress
            InAppLogger.log("OnboardingSystemCheck", "Test progress: $stepName ($currentTestStep/$totalTestSteps, $progress%)")
        }
        
        private fun getStreamTypeFromAudioUsage(audioUsageIndex: Int): Int {
            return when (audioUsageIndex) {
                0 -> android.media.AudioManager.STREAM_MUSIC
                1 -> android.media.AudioManager.STREAM_NOTIFICATION
                2 -> android.media.AudioManager.STREAM_ALARM
                3 -> android.media.AudioManager.STREAM_VOICE_CALL
                4 -> android.media.AudioManager.STREAM_SYSTEM
                else -> android.media.AudioManager.STREAM_NOTIFICATION
            }
        }
        
        private fun getStreamTypeName(streamType: Int): String {
            return when (streamType) {
                android.media.AudioManager.STREAM_MUSIC -> "Media"
                android.media.AudioManager.STREAM_NOTIFICATION -> "Notification"
                android.media.AudioManager.STREAM_ALARM -> "Alarm"
                android.media.AudioManager.STREAM_VOICE_CALL -> "Voice Call"
                android.media.AudioManager.STREAM_SYSTEM -> "System"
                else -> "Unknown"
            }
        }
        
        private fun translateErrorToPlainEnglish(technicalReason: String): String {
            return when {
                technicalReason.contains("Test interrupted", ignoreCase = true) -> 
                    "You stopped the test before it could complete."
                technicalReason.contains("Duplicate notification", ignoreCase = true) || 
                technicalReason.contains("deduplication", ignoreCase = true) -> 
                    "The test notification was blocked by duplicate detection. Try disabling deduplication in settings."
                technicalReason.contains("Dismissal memory", ignoreCase = true) -> 
                    "The test notification was blocked by dismissal memory. Try disabling dismissal memory in settings."
                technicalReason.contains("Master switch", ignoreCase = true) -> 
                    "SpeakThat's master switch is turned off. Please enable it in settings."
                technicalReason.contains("Audio mode", ignoreCase = true) || 
                technicalReason.contains("Silent mode", ignoreCase = true) || 
                technicalReason.contains("Vibrate mode", ignoreCase = true) -> 
                    "Your phone is in Silent or Vibrate mode. Please switch to normal mode."
                technicalReason.contains("Do Not Disturb", ignoreCase = true) || 
                technicalReason.contains("DND", ignoreCase = true) -> 
                    "Do Not Disturb mode is blocking notifications. Please disable it."
                technicalReason.contains("Rules blocked", ignoreCase = true) || 
                technicalReason.contains("Rule evaluation", ignoreCase = true) -> 
                    "A conditional rule is blocking the notification. Check your rules in settings."
                technicalReason.contains("content filter", ignoreCase = true) -> 
                    "The notification was blocked by a content filter. Check your filter settings."
                technicalReason.contains("TTS not initialized", ignoreCase = true) -> 
                    "Text-to-Speech failed to start. Please check your TTS settings."
                technicalReason.contains("TTS error", ignoreCase = true) -> 
                    "Text-to-Speech encountered an error. Please check your TTS engine."
                technicalReason.contains("TTS_STARTED_NOT_COMPLETED", ignoreCase = true) -> 
                    "Text-to-Speech started but didn't complete. This may be a TTS engine issue."
                technicalReason.contains("TTS_NEVER_STARTED", ignoreCase = true) -> 
                    "Text-to-Speech never started. Please check your TTS settings."
                technicalReason.contains("volume is 0", ignoreCase = true) || 
                technicalReason.contains("volume: 0", ignoreCase = true) -> 
                    "Your volume is turned down. Please turn up your volume."
                else -> 
                    technicalReason // Fallback to technical reason if no translation available
            }
        }
        
        private fun onUserConfirmedSuccess() {
            InAppLogger.log("OnboardingSystemCheck", "User confirmed test success")
            testCompleted = true
            testSuccess = true
            
            // Save success to SharedPreferences
            saveTestResult(true)
            
            // Re-enable navigation, swiping, and notify completion
            onNavigationControlChanged?.invoke(true, true)
            onSwipeControlChanged?.invoke(true)
            onSystemCheckCompleted?.invoke(true)
            
            // The activity will handle moving to the next page
        }
        
        private fun onUserReportedFailure() {
            InAppLogger.log("OnboardingSystemCheck", "User reported test failure")
            showFailureState("You indicated that you didn't hear the test notification. This may be due to volume, audio mode, or TTS settings.")
        }
        
        private fun saveTestResult(success: Boolean) {
            val prefs = binding.root.context.getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("system_check_passed", success).apply()
            InAppLogger.log("OnboardingSystemCheck", "Test result saved: success=$success")
        }
        
        /**
         * Cancel the test if it's in progress (e.g., user navigated away or pressed skip)
         */
        fun cancelTest() {
            if (testInProgress) {
                InAppLogger.log("OnboardingSystemCheck", "Test cancelled by user navigation")
                testInProgress = false
                selfTestHelper.cancelTestNotification()
                
                // Re-enable navigation and swiping
                onNavigationControlChanged?.invoke(true, true)
                onSwipeControlChanged?.invoke(true)
            }
        }
    }
    
    /**
     * ViewHolder for Kill the Noise Page (Page 6)
     */
    inner class KillNoiseViewHolder(
        private val binding: FragmentOnboardingKillNoiseBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind() {
            setupSkipButton()
        }
        
        private fun setupSkipButton() {
            binding.buttonSkipToEnd.setOnClickListener {
                // Jump to page 10 (index 9)
                onSkipToPage?.invoke(9)
                InAppLogger.log("OnboardingKillNoisePage", "Skipping to final page")
            }
        }
    }
    
    /**
     * ViewHolder for Placeholder Pages (Pages 7-10)
     */
    inner class PlaceholderViewHolder(
        private val binding: FragmentOnboardingPlaceholderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(position: Int) {
            InAppLogger.log("OnboardingPlaceholderPage", "Placeholder page ${position + 1} loaded")
        }
    }
    
    /**
     * Refresh UI text for language changes
     */
    fun refreshUIText() {
        notifyDataSetChanged()
        InAppLogger.log("OnboardingPagerAdapterNew", "UI text refreshed for language change")
    }
    
    /**
     * Get the SystemCheckViewHolder if currently visible
     * Used for handling permission results and cancellation
     */
    private var currentSystemCheckViewHolder: SystemCheckViewHolder? = null
    
    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder is SystemCheckViewHolder) {
            currentSystemCheckViewHolder = holder
        }
    }
    
    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder is SystemCheckViewHolder) {
            holder.cancelTest()
            currentSystemCheckViewHolder = null
        }
    }
    
    fun onPostNotificationsPermissionResult(granted: Boolean) {
        currentSystemCheckViewHolder?.onPermissionResult(granted)
    }
}
