package com.micoyc.speakthat

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.micoyc.speakthat.automation.AutomationMode
import com.micoyc.speakthat.automation.AutomationModeManager
import com.micoyc.speakthat.databinding.ActivitySelftestBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * SelfTest Activity
 * Comprehensive diagnostic system that tests the notification pipeline
 * and identifies specific failure points with error codes
 */
class SelfTestActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySelftestBinding
    private lateinit var selfTestHelper: SelfTestHelper
    private var sharedPreferences: SharedPreferences? = null
    private val handler = Handler(Looper.getMainLooper())
    private var listenerRecoveryAttempts = 0
    
    private var currentStep = 0
    private var errorCode = 0
    private var errorMessage = ""
    private var waitingForUserFeedback = false
    
    companion object {
        private const val TAG = "SelfTestActivity"
        private const val REQUEST_POST_NOTIFICATIONS = 1001
        const val PREFS_NAME = "SpeakThatPrefs"
        const val KEY_MASTER_SWITCH_ENABLED = "master_switch_enabled"
        private const val MAX_LISTENER_RECOVERY_ATTEMPTS = 2
        private const val LISTENER_RECOVERY_DELAY_MS = 1500L
        private const val LISTENER_STALE_THRESHOLD_MS = 5 * 60 * 1000L
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply saved theme FIRST before anything else
        val mainPrefs = getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
        applySavedTheme(mainPrefs)
        
        binding = ActivitySelftestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize preferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Initialize helper
        selfTestHelper = SelfTestHelper(this)
        
        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.selftest_title)
        
        // Initialize UI
        initializeSteps()
        setupActionButtons()
        
        // Start the test automatically
        startSelfTest()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun applySavedTheme(prefs: SharedPreferences) {
        val isDarkMode = prefs.getBoolean("dark_mode", true) // Default to dark mode
        val desiredMode = if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        } else {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        }
        val currentMode = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode()
        
        // Only set the night mode if it's different from the current mode
        // This prevents unnecessary configuration changes that cause activity recreation loops
        if (currentMode != desiredMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(desiredMode)
        }
    }
    
    private fun initializeSteps() {
        // Initialize all step views
        setStepStatus(binding.step1.root, "⏳", getString(R.string.selftest_step1_title), "")
        setStepStatus(binding.step2.root, "⏳", getString(R.string.selftest_step2_title), "")
        setStepStatus(binding.step3.root, "⏳", getString(R.string.selftest_step3_title), "")
        setStepStatus(binding.step4.root, "⏳", getString(R.string.selftest_step4_title), "")
        setStepStatus(binding.step5.root, "⏳", getString(R.string.selftest_step5_title), "")
        setStepStatus(binding.step6.root, "⏳", getString(R.string.selftest_step6_title), "")
        setStepStatus(binding.step7.root, "⏳", getString(R.string.selftest_step7_title), "")
        setStepStatus(binding.step8.root, "⏳", getString(R.string.selftest_step8_title), "")
        setStepStatus(binding.step9.root, "⏳", getString(R.string.selftest_step9_title), "")
    }
    
    private fun setStepStatus(stepView: View, icon: String, title: String, detail: String) {
        val iconView = stepView.findViewById<TextView>(R.id.textStepIcon)
        val titleView = stepView.findViewById<TextView>(R.id.textStepTitle)
        val detailView = stepView.findViewById<TextView>(R.id.textStepDetail)
        
        iconView?.text = icon
        titleView?.text = title
        detailView?.text = detail
        detailView?.visibility = if (detail.isEmpty()) View.GONE else View.VISIBLE
    }
    
    private fun setupActionButtons() {
        binding.buttonYes.setOnClickListener {
            handleUserFeedback(true)
        }
        
        binding.buttonNo.setOnClickListener {
            handleUserFeedback(false)
        }
        
        binding.buttonExportLogs.setOnClickListener {
            exportLogs()
        }
        
        binding.buttonShareReport.setOnClickListener {
            shareReport()
        }
        
        binding.buttonTryAgain.setOnClickListener {
            restartTest()
        }
        
        binding.buttonClose.setOnClickListener {
            finish()
        }
    }
    
    private fun startSelfTest() {
        // Reset UI
        binding.layoutSteps.visibility = View.VISIBLE
        binding.layoutResults.visibility = View.GONE
        binding.layoutLoading.visibility = View.GONE
        listenerRecoveryAttempts = 0
        currentStep = 0
        errorCode = 0
        errorMessage = ""
        
        // Clear logs and mark test start
        InAppLogger.clear()
        InAppLogger.log("SelfTest", "=== SELFTEST STARTED ===")
        InAppLogger.log("SelfTest", "Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        
        // Start pre-flight checks
        runPreflightChecks()
    }
    
    private fun runPreflightChecks() {
        handler.post {
            checkNotificationListenerPermission()
        }
    }
    
    // Step 1: Check NotificationListenerService permission
    private fun checkNotificationListenerPermission() {
        currentStep = 1
        InAppLogger.log("SelfTest", "Step 1: Checking NotificationListener permission")
        
        val isEnabled = isNotificationServiceEnabled()
        if (isEnabled) {
            if (!isListenerConnectionHealthy()) {
                setStepStatus(binding.step1.root, "⏳", getString(R.string.selftest_step1_title), getString(R.string.selftest_step1_rebinding))
                val scheduled = scheduleListenerRecovery("selftest_step1") {
                    checkNotificationListenerPermission()
                }
                if (scheduled) {
                    return
                }
                InAppLogger.log("SelfTest", "Step 1: Listener recovery not scheduled (limit reached)")
            }

            setStepStatus(binding.step1.root, "✓", getString(R.string.selftest_step1_title), getString(R.string.selftest_step1_pass))
            InAppLogger.log("SelfTest", "Step 1: PASS - NotificationListener enabled")
            handler.postDelayed({ checkPostNotificationsPermission() }, 300)
        } else {
            setStepStatus(binding.step1.root, "✗", getString(R.string.selftest_step1_title), getString(R.string.selftest_step1_fail))
            InAppLogger.log("SelfTest", "Step 1: FAIL - NotificationListener not enabled")
            showError(404, getString(R.string.selftest_error_0404_title), getString(R.string.selftest_error_0404_description))
        }
    }

    private fun isListenerConnectionHealthy(): Boolean {
        val status = NotificationListenerRecovery.getListenerStatus(this, LISTENER_STALE_THRESHOLD_MS)

        if (!status.permissionGranted) {
            InAppLogger.log("SelfTest", "Listener permission missing - cannot be healthy")
            return false
        }

        if (status.isDisconnected) {
            InAppLogger.log("SelfTest", "Listener disconnect newer than connect - needs recovery")
            return false
        }

        if (status.isStale) {
            val reference = maxOf(status.lastConnect, status.lastHeartbeat)
            val age = if (reference > 0L) System.currentTimeMillis() - reference else -1L
            InAppLogger.log("SelfTest", "Listener connection stale (${age}ms) - needs recovery")
            return false
        }

        if (status.lastConnect == 0L) {
            InAppLogger.log("SelfTest", "Listener connection timestamp missing - treating as unhealthy")
            return false
        }

        return true
    }

    private fun scheduleListenerRecovery(reason: String, onRecoveryComplete: () -> Unit): Boolean {
        if (listenerRecoveryAttempts >= MAX_LISTENER_RECOVERY_ATTEMPTS) {
            return false
        }

        val requested = NotificationListenerRecovery.requestRebind(this, reason, true)
        return if (requested) {
            listenerRecoveryAttempts++
            InAppLogger.log("SelfTest", "Listener recovery attempt #$listenerRecoveryAttempts (reason=$reason)")
            handler.postDelayed(onRecoveryComplete, LISTENER_RECOVERY_DELAY_MS)
            true
        } else {
            InAppLogger.log("SelfTest", "Listener recovery request denied by system (reason=$reason)")
            false
        }
    }
    
    // Step 2: Check POST_NOTIFICATIONS permission (Android 13+)
    private fun checkPostNotificationsPermission() {
        currentStep = 2
        InAppLogger.log("SelfTest", "Step 2: Checking POST_NOTIFICATIONS permission")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                == PackageManager.PERMISSION_GRANTED) {
                setStepStatus(binding.step2.root, "✓", getString(R.string.selftest_step2_title), getString(R.string.selftest_step2_pass))
                InAppLogger.log("SelfTest", "Step 2: PASS - POST_NOTIFICATIONS granted")
                handler.postDelayed({ checkMasterSwitch() }, 300)
            } else {
                setStepStatus(binding.step2.root, "⏳", getString(R.string.selftest_step2_title), getString(R.string.selftest_step2_requesting))
                InAppLogger.log("SelfTest", "Step 2: Requesting POST_NOTIFICATIONS permission")
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATIONS)
            }
        } else {
            // Not needed on older Android versions
            setStepStatus(binding.step2.root, "✓", getString(R.string.selftest_step2_title), getString(R.string.selftest_step2_not_needed))
            InAppLogger.log("SelfTest", "Step 2: SKIP - POST_NOTIFICATIONS not required on Android < 13")
            handler.postDelayed({ checkMasterSwitch() }, 300)
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setStepStatus(binding.step2.root, "✓", getString(R.string.selftest_step2_title), getString(R.string.selftest_step2_pass))
                InAppLogger.log("SelfTest", "Step 2: PASS - POST_NOTIFICATIONS granted")
                handler.postDelayed({ checkMasterSwitch() }, 300)
            } else {
                setStepStatus(binding.step2.root, "✗", getString(R.string.selftest_step2_title), getString(R.string.selftest_step2_fail))
                InAppLogger.log("SelfTest", "Step 2: FAIL - POST_NOTIFICATIONS denied")
                showError(400, getString(R.string.selftest_error_0400_title), getString(R.string.selftest_error_0400_description))
            }
        }
    }
    
    // Step 3: Check Master Switch
    private fun checkMasterSwitch() {
        currentStep = 3
        InAppLogger.log("SelfTest", "Step 3: Checking master switch")
        
        val isEnabled = sharedPreferences?.getBoolean(KEY_MASTER_SWITCH_ENABLED, true) ?: true
        if (isEnabled) {
            setStepStatus(binding.step3.root, "✓", getString(R.string.selftest_step3_title), getString(R.string.selftest_step3_pass))
            InAppLogger.log("SelfTest", "Step 3: PASS - Master switch enabled")
            handler.postDelayed({ checkTtsInitialization() }, 300)
        } else {
            setStepStatus(binding.step3.root, "✗", getString(R.string.selftest_step3_title), getString(R.string.selftest_step3_fail))
            InAppLogger.log("SelfTest", "Step 3: FAIL - Master switch disabled")
            showError(410, getString(R.string.selftest_error_0410_title), getString(R.string.selftest_error_0410_description))
        }
    }
    
    // Step 4: Check TTS Initialization
    private fun checkTtsInitialization() {
        currentStep = 4
        InAppLogger.log("SelfTest", "Step 4: Checking TTS initialization")
        
        // Simple check - try to create a TTS instance
        var testTts: TextToSpeech? = null
        testTts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                setStepStatus(binding.step4.root, "✓", getString(R.string.selftest_step4_title), getString(R.string.selftest_step4_pass))
                InAppLogger.log("SelfTest", "Step 4: PASS - TTS initialized")
                testTts?.shutdown()
                handler.postDelayed({ checkVolume() }, 300)
            } else {
                setStepStatus(binding.step4.root, "✗", getString(R.string.selftest_step4_title), getString(R.string.selftest_step4_fail))
                InAppLogger.log("SelfTest", "Step 4: FAIL - TTS initialization failed")
                testTts?.shutdown()
                showError(415, getString(R.string.selftest_error_0415_title), getString(R.string.selftest_error_0415_description))
            }
        }
    }
    
    // Step 5: Check Volume
    private fun checkVolume() {
        currentStep = 5
        InAppLogger.log("SelfTest", "Step 5: Checking volume levels")
        
        // Get the configured audio stream type from Voice Settings
        val voicePrefs = getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE)
        val audioUsageIndex = voicePrefs.getInt("audio_usage", 0) // Default to Media (index 0)
        val streamType = getStreamTypeFromAudioUsage(audioUsageIndex)
        val streamName = getStreamTypeName(streamType)
        
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(streamType)
        val maxVolume = audioManager.getStreamMaxVolume(streamType)
        
        InAppLogger.log("SelfTest", "Step 5: Checking $streamName stream volume (audio_usage index: $audioUsageIndex)")
        
        if (currentVolume > 0) {
            val percentage = (currentVolume.toFloat() / maxVolume * 100).toInt()
            setStepStatus(binding.step5.root, "✓", getString(R.string.selftest_step5_title), 
                getString(R.string.selftest_step5_pass, percentage))
            InAppLogger.log("SelfTest", "Step 5: PASS - $streamName volume at $percentage% ($currentVolume/$maxVolume)")
            handler.postDelayed({ checkAudioMode() }, 300)
        } else {
            setStepStatus(binding.step5.root, "✗", getString(R.string.selftest_step5_title), getString(R.string.selftest_step5_fail))
            InAppLogger.log("SelfTest", "Step 5: FAIL - $streamName volume is 0")
            showError(441, getString(R.string.selftest_error_0441_title), getString(R.string.selftest_error_0441_description, streamName))
        }
    }
    
    /**
     * Convert audio usage index to Android stream type
     * Matches the mapping used in VoiceSettingsActivity
     */
    private fun getStreamTypeFromAudioUsage(audioUsageIndex: Int): Int {
        return when (audioUsageIndex) {
            0 -> AudioManager.STREAM_MUSIC      // Media
            1 -> AudioManager.STREAM_NOTIFICATION // Notification
            2 -> AudioManager.STREAM_ALARM      // Alarm
            3 -> AudioManager.STREAM_VOICE_CALL // Voice Call
            4 -> AudioManager.STREAM_SYSTEM     // Assistance (using SYSTEM as closest match)
            else -> AudioManager.STREAM_NOTIFICATION // Default fallback
        }
    }
    
    /**
     * Get a human-readable name for the stream type
     */
    private fun getStreamTypeName(streamType: Int): String {
        return when (streamType) {
            AudioManager.STREAM_MUSIC -> "Media"
            AudioManager.STREAM_NOTIFICATION -> "Notification"
            AudioManager.STREAM_ALARM -> "Alarm"
            AudioManager.STREAM_VOICE_CALL -> "Voice Call"
            AudioManager.STREAM_SYSTEM -> "System"
            else -> "Unknown"
        }
    }
    
    // Step 6: Check Audio Mode
    private fun checkAudioMode() {
        currentStep = 6
        InAppLogger.log("SelfTest", "Step 6: Checking audio mode")
        
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringerMode = audioManager.ringerMode
        val modeName = when (ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            AudioManager.RINGER_MODE_NORMAL -> "Normal"
            else -> "Unknown"
        }
        
        // Check split Honour Audio Mode settings (fallback to legacy combined flag)
        val behaviorPrefs = getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
        val hasSilent = behaviorPrefs.contains("honour_silent_mode")
        val hasVibrate = behaviorPrefs.contains("honour_vibrate_mode")
        val legacyHonor = behaviorPrefs.getBoolean("honour_audio_mode", true)
        val honorSilent = behaviorPrefs.getBoolean("honour_silent_mode", legacyHonor)
        val honorVibrate = behaviorPrefs.getBoolean("honour_vibrate_mode", legacyHonor)

        val blockedBySilent = ringerMode == AudioManager.RINGER_MODE_SILENT && honorSilent
        val blockedByVibrate = ringerMode == AudioManager.RINGER_MODE_VIBRATE && honorVibrate

        if (!(blockedBySilent || blockedByVibrate)) {
            setStepStatus(binding.step6.root, "✓", getString(R.string.selftest_step6_title), 
                getString(R.string.selftest_step6_pass, modeName))
            InAppLogger.log("SelfTest", "Step 6: PASS - Audio mode: $modeName (silent=$honorSilent, vibrate=$honorVibrate, legacy=$legacyHonor, hasSplit=${hasSilent && hasVibrate})")
            handler.postDelayed({ checkRules() }, 300)
        } else {
            setStepStatus(binding.step6.root, "✗", getString(R.string.selftest_step6_title), 
                getString(R.string.selftest_step6_fail, modeName))
            InAppLogger.log("SelfTest", "Step 6: FAIL - Audio mode blocked: $modeName")
            showError(411, getString(R.string.selftest_error_0411_title), getString(R.string.selftest_error_0411_description))
        }
    }
    
    // Step 7: Check Rules System
    private fun checkRules() {
        currentStep = 7
        InAppLogger.log("SelfTest", "Step 7: Checking rules system")
        
        when (AutomationModeManager(this).getMode()) {
            AutomationMode.OFF -> {
                setStepStatus(
                    binding.step7.root,
                    "✓",
                    getString(R.string.selftest_step7_title),
                    getString(R.string.selftest_step7_pass_disabled)
                )
                InAppLogger.log("SelfTest", "Step 7: PASS - Automation disabled")
            }
            AutomationMode.CONDITIONAL_RULES -> {
                setStepStatus(
                    binding.step7.root,
                    "⚠",
                    getString(R.string.selftest_step7_title),
                    getString(R.string.selftest_step7_warning)
                )
                InAppLogger.log("SelfTest", "Step 7: WARNING - Conditional Rules enabled, may block notifications")
            }
            AutomationMode.EXTERNAL_AUTOMATION -> {
                setStepStatus(
                    binding.step7.root,
                    "⚠",
                    getString(R.string.selftest_step7_title),
                    getString(R.string.selftest_step7_external)
                )
                InAppLogger.log("SelfTest", "Step 7: WARNING - External automation controls SpeakThat")
            }
        }
        
        handler.postDelayed({ postTestNotification() }, 300)
    }
    
    // Step 8: Post Test Notification
    private fun postTestNotification() {
        currentStep = 8
        InAppLogger.log("SelfTest", "Step 8: Posting test notification")
        
        setStepStatus(binding.step8.root, "⏳", getString(R.string.selftest_step8_title), getString(R.string.selftest_step8_posting))
        
        val posted = selfTestHelper.postTestNotification()
        if (posted) {
            setStepStatus(binding.step8.root, "✓", getString(R.string.selftest_step8_title), getString(R.string.selftest_step8_posted))
            InAppLogger.log("SelfTest", "Step 8: Test notification posted")
            handler.postDelayed({ monitorLogs() }, 500)
        } else {
            setStepStatus(binding.step8.root, "✗", getString(R.string.selftest_step8_title), getString(R.string.selftest_step8_fail))
            InAppLogger.log("SelfTest", "Step 8: FAIL - Could not post notification")
            showError(400, getString(R.string.selftest_error_0400_title), getString(R.string.selftest_error_0400_description))
        }
    }
    
    // Step 9: Monitor Logs
    private fun monitorLogs() {
        currentStep = 9
        InAppLogger.log("SelfTest", "Step 9: Monitoring logs for test notification")
        
        setStepStatus(binding.step9.root, "⏳", getString(R.string.selftest_step9_title), getString(R.string.selftest_step9_monitoring))
        
        // Show loading indicator
        binding.layoutLoading.visibility = View.VISIBLE
        binding.textLoadingMessage.text = getString(R.string.selftest_monitoring_message)
        
        // Monitor logs for up to 10 seconds
        selfTestHelper.monitorLogsForTestNotification(10000) { result ->
            handler.post {
                binding.layoutLoading.visibility = View.GONE
                analyzeResults(result)
            }
        }
    }
    
    private fun analyzeResults(result: SelfTestHelper.TestResult) {
        InAppLogger.log("SelfTest", "Analyzing test results: ${result.status}")
        
        when (result.status) {
            SelfTestHelper.TestStatus.NOT_RECEIVED -> {
                val recoveryScheduled = scheduleListenerRecovery("selftest_step9") {
                    selfTestHelper.cancelTestNotification()
                    postTestNotification()
                }

                if (recoveryScheduled) {
                    setStepStatus(binding.step9.root, "⏳", getString(R.string.selftest_step9_title), getString(R.string.selftest_step9_rebinding))
                    InAppLogger.log("SelfTest", "Result: Notification not received - auto recovery scheduled")
                } else {
                    // Don't show checkmark/cross yet - waiting for user feedback
                    setStepStatus(binding.step9.root, "❓", getString(R.string.selftest_step9_title), getString(R.string.selftest_step9_not_received))
                    InAppLogger.log("SelfTest", "Result: Notification not received by service")
                    askUserIfNotificationAppeared()
                }
            }
            SelfTestHelper.TestStatus.RECEIVED_NOT_READ -> {
                // This is a definitive failure - show immediately
                setStepStatus(binding.step9.root, "✗", getString(R.string.selftest_step9_title), getString(R.string.selftest_step9_received_not_read))
                InAppLogger.log("SelfTest", "Result: Notification received but not read - ${result.blockingReason}")
                showErrorForBlockingReason(result.blockingReason)
            }
            SelfTestHelper.TestStatus.READ_SUCCESSFULLY -> {
                // Don't show checkmark/cross yet - waiting for user feedback
                setStepStatus(binding.step9.root, "❓", getString(R.string.selftest_step9_title), getString(R.string.selftest_step9_success))
                InAppLogger.log("SelfTest", "Result: Notification read successfully")
                askUserIfHeardNotification()
            }
        }
    }
    
    private fun askUserIfNotificationAppeared() {
        showUserFeedbackQuestion(
            getString(R.string.selftest_question_notification_appeared),
            yesCallback = {
                // User saw notification but service didn't receive it - this is a failure
                setStepStatus(binding.step9.root, "✗", getString(R.string.selftest_step9_title), getString(R.string.selftest_step9_not_received))
                showError(404, getString(R.string.selftest_error_0404_title), getString(R.string.selftest_error_0404_description))
            },
            noCallback = {
                // Notification was never posted - this is also a failure
                setStepStatus(binding.step9.root, "✗", getString(R.string.selftest_step9_title), getString(R.string.selftest_step9_not_received))
                showError(400, getString(R.string.selftest_error_0400_title), getString(R.string.selftest_error_0400_description))
            }
        )
    }
    
    private fun askUserIfHeardNotification() {
        showUserFeedbackQuestion(
            getString(R.string.selftest_question_heard_notification),
            yesCallback = {
                // Success! Now we can show the checkmark
                setStepStatus(binding.step9.root, "✓", getString(R.string.selftest_step9_title), getString(R.string.selftest_step9_success))
                showSuccess()
            },
            noCallback = {
                // TTS worked but user didn't hear it - this is a failure
                setStepStatus(binding.step9.root, "✗", getString(R.string.selftest_step9_title), getString(R.string.selftest_step9_success))
                showError(443, getString(R.string.selftest_error_0443_title), getString(R.string.selftest_error_0443_description))
            }
        )
    }
    
    private fun showUserFeedbackQuestion(question: String, yesCallback: () -> Unit, noCallback: () -> Unit) {
        waitingForUserFeedback = true
        
        binding.layoutSteps.visibility = View.GONE
        binding.layoutResults.visibility = View.VISIBLE
        
        // Hide the result status elements - we don't know the outcome yet!
        binding.textResultIcon.visibility = View.GONE
        binding.textErrorCode.visibility = View.GONE
        binding.textResultTitle.visibility = View.GONE
        binding.textResultDescription.visibility = View.GONE
        
        // Only show the user feedback question
        binding.layoutUserFeedback.visibility = View.VISIBLE
        binding.textUserQuestion.text = question
        binding.layoutActionButtons.visibility = View.GONE
        
        binding.buttonYes.setOnClickListener {
            waitingForUserFeedback = false
            // Re-show the result elements before calling the callback
            binding.textResultIcon.visibility = View.VISIBLE
            binding.textErrorCode.visibility = View.VISIBLE
            binding.textResultTitle.visibility = View.VISIBLE
            binding.textResultDescription.visibility = View.VISIBLE
            yesCallback()
        }
        
        binding.buttonNo.setOnClickListener {
            waitingForUserFeedback = false
            // Re-show the result elements before calling the callback
            binding.textResultIcon.visibility = View.VISIBLE
            binding.textErrorCode.visibility = View.VISIBLE
            binding.textResultTitle.visibility = View.VISIBLE
            binding.textResultDescription.visibility = View.VISIBLE
            noCallback()
        }
    }
    
    private fun handleUserFeedback(answer: Boolean) {
        // This is handled by the button click listeners set in showUserFeedbackQuestion
    }
    
    private fun showErrorForBlockingReason(reason: String) {
        // Determine error code based on the blocking reason
        val determinedErrorCode = when {
            reason.contains("Test interrupted", ignoreCase = true) -> 999 // Special code for user interruption
            reason.contains("TTS_STARTED_NOT_COMPLETED", ignoreCase = true) -> 442 // TTS started but didn't complete
            reason.contains("TTS_NEVER_STARTED", ignoreCase = true) -> 444 // TTS never started
            reason.contains("deduplication", ignoreCase = true) -> 414
            reason.contains("dismissal memory", ignoreCase = true) -> 414
            reason.contains("master switch", ignoreCase = true) -> 410
            reason.contains("audio mode", ignoreCase = true) -> 411
            reason.contains("do not disturb", ignoreCase = true) -> 412
            reason.contains("rules", ignoreCase = true) -> 413
            reason.contains("content filter", ignoreCase = true) -> 414
            reason.contains("tts not initialized", ignoreCase = true) -> 415
            reason.contains("tts error", ignoreCase = true) -> 415
            reason.contains("volume is 0", ignoreCase = true) || reason.contains("volume: 0", ignoreCase = true) -> 441
            reason.contains("volume", ignoreCase = true) -> 440
            else -> 420
        }
        
        // For user interruption, show a special message
        if (determinedErrorCode == 999) {
            val title = "Test Interrupted"
            // Show the detailed reason directly from SelfTestHelper
            showError(0, title, reason)
            return
        }
        
        // For TTS-specific errors, extract the clean reason
        val cleanReason = when {
            reason.contains("TTS_STARTED_NOT_COMPLETED", ignoreCase = true) -> 
                reason.substringAfter("TTS_STARTED_NOT_COMPLETED: ").trim()
            reason.contains("TTS_NEVER_STARTED", ignoreCase = true) -> 
                reason.substringAfter("TTS_NEVER_STARTED: ").trim()
            else -> reason
        }
        
        // For other errors, show both the generic error description AND the specific reason
        val errorTitle = getString(getErrorTitleResourceId(determinedErrorCode))
        val errorDescription = getString(getErrorDescriptionResourceId(determinedErrorCode))
        
        // Combine the generic description with the specific reason
        val detailedDescription = "$errorDescription\n\n📋 Specific Issue:\n$cleanReason"
        showError(determinedErrorCode, errorTitle, detailedDescription)
    }
    
    private fun showSuccess() {
        InAppLogger.log("SelfTest", "=== SELFTEST COMPLETED SUCCESSFULLY ===")
        
        binding.layoutSteps.visibility = View.GONE
        binding.layoutResults.visibility = View.VISIBLE
        binding.layoutUserFeedback.visibility = View.GONE
        binding.layoutActionButtons.visibility = View.VISIBLE
        
        binding.textResultIcon.text = "✅"
        binding.textErrorCode.text = getString(R.string.selftest_error_code, "0000")
        binding.textErrorCode.visibility = View.VISIBLE
        binding.textResultTitle.text = getString(R.string.selftest_success_title)
        binding.textResultDescription.text = getString(R.string.selftest_success_description)
    }
    
    private fun showError(code: Int, title: String, description: String) {
        errorCode = code
        errorMessage = title
        
        InAppLogger.log("SelfTest", "=== SELFTEST FAILED: Error $code - $title ===")
        
        binding.layoutSteps.visibility = View.GONE
        binding.layoutResults.visibility = View.VISIBLE
        binding.layoutUserFeedback.visibility = View.GONE
        binding.layoutActionButtons.visibility = View.VISIBLE
        
        binding.textResultIcon.text = "❌"
        binding.textErrorCode.text = getString(R.string.selftest_error_code, code.toString().padStart(4, '0'))
        binding.textErrorCode.visibility = View.VISIBLE
        binding.textResultTitle.text = title
        binding.textResultDescription.text = description
    }
    
    private fun getErrorTitleResourceId(code: Int): Int {
        return when (code) {
            400 -> R.string.selftest_error_0400_title
            404 -> R.string.selftest_error_0404_title
            410 -> R.string.selftest_error_0410_title
            411 -> R.string.selftest_error_0411_title
            412 -> R.string.selftest_error_0412_title
            413 -> R.string.selftest_error_0413_title
            414 -> R.string.selftest_error_0414_title
            415 -> R.string.selftest_error_0415_title
            420 -> R.string.selftest_error_0420_title
            440 -> R.string.selftest_error_0440_title
            441 -> R.string.selftest_error_0441_title
            442 -> R.string.selftest_error_0442_title
            443 -> R.string.selftest_error_0443_title
            444 -> R.string.selftest_error_0444_title
            else -> R.string.selftest_error_unknown_title
        }
    }
    
    private fun getErrorDescriptionResourceId(code: Int): Int {
        return when (code) {
            400 -> R.string.selftest_error_0400_description
            404 -> R.string.selftest_error_0404_description
            410 -> R.string.selftest_error_0410_description
            411 -> R.string.selftest_error_0411_description
            412 -> R.string.selftest_error_0412_description
            413 -> R.string.selftest_error_0413_description
            414 -> R.string.selftest_error_0414_description
            415 -> R.string.selftest_error_0415_description
            420 -> R.string.selftest_error_0420_description
            440 -> R.string.selftest_error_0440_description
            441 -> R.string.selftest_error_0441_description
            442 -> R.string.selftest_error_0442_description
            443 -> R.string.selftest_error_0443_description
            444 -> R.string.selftest_error_0444_description
            else -> R.string.selftest_error_unknown_description
        }
    }
    
    private fun exportLogs() {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val errorCodeStr = errorCode.toString().padStart(4, '0')
        val filename = "speakthat_selftest_${errorCodeStr}_$timestamp.txt"
        
        InAppLogger.log("SelfTest", "Exporting logs to: $filename")
        
        // Use FileExportHelper to export logs
        val logsContent = InAppLogger.getAllLogs()
        val exportFile = FileExportHelper.createExportFile(this, "selftest", filename, logsContent)
        
        if (exportFile != null) {
            Toast.makeText(this, getString(R.string.selftest_logs_exported, filename), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, getString(R.string.selftest_logs_export_failed), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun shareReport() {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val errorCodeStr = errorCode.toString().padStart(4, '0')
        
        // Build comprehensive report including system diagnostics
        val report = buildString {
            append("SpeakThat SelfTest Report\n")
            append("=".repeat(50) + "\n\n")
            
            // Test Result Summary
            append("TEST RESULT\n")
            append("-".repeat(50) + "\n")
            append("Test Time: $timestamp\n")
            append("Error Code: $errorCodeStr\n")
            append("Status: ${if (errorCode == 0) "✅ SUCCESS" else "❌ FAILED"}\n")
            append("Error: $errorMessage\n\n")
            
            // Device Info
            append("DEVICE INFORMATION\n")
            append("-".repeat(50) + "\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\n")
            
            // System Diagnostics (from Test Settings)
            append(getSystemDiagnostics())
            
            // Debug Logs (last 50 lines to keep size reasonable)
            append("\nDEBUG LOGS (Recent)\n")
            append("-".repeat(50) + "\n")
            val allLogs = InAppLogger.getAllLogs()
            val logLines = allLogs.split("\n")
            val recentLogs = if (logLines.size > 50) {
                logLines.takeLast(50).joinToString("\n")
            } else {
                allLogs
            }
            append(recentLogs)
            append("\n\n")
            append("=".repeat(50) + "\n")
            append("End of Report\n")
        }
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SpeakThat SelfTest Report - Error $errorCodeStr")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        
        startActivity(Intent.createChooser(shareIntent, getString(R.string.selftest_share_report)))
    }
    
    private fun getSystemDiagnostics(): String {
        return buildString {
            append("SYSTEM DIAGNOSTICS\n")
            append("-".repeat(50) + "\n")
            
            try {
                val mainPrefs = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE)
                val voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
                
                // General Settings
                append("\n📱 General Settings:\n")
                append("  Dark Mode: ${mainPrefs.getBoolean("dark_mode", false)}\n")
                append("  Master Switch: ${mainPrefs.getBoolean("master_switch_enabled", true)}\n")
                append("  Auto-start: ${mainPrefs.getBoolean("auto_start_on_boot", false)}\n")
                
                // Voice Settings
                append("\n🎤 Voice Settings:\n")
                val speechRate = voicePrefs.getFloat("speech_rate", 1.0f)
                val pitch = voicePrefs.getFloat("pitch", 1.0f)
                val language = voicePrefs.getString("language", "en_US") ?: "en_US"
                append("  Speech Rate: ${speechRate}x\n")
                append("  Pitch: ${pitch}x\n")
                append("  Language: $language\n")
                append("  TTS Volume: ${voicePrefs.getFloat("tts_volume", 1.0f)}\n")
                append("  Audio Usage: ${voicePrefs.getInt("audio_usage", 4)}\n")
                append("  Content Type: ${voicePrefs.getInt("content_type", 0)}\n")
                append("  Ducking Enabled: ${voicePrefs.getBoolean("ducking_enabled", true)}\n")
                
                // Behavior Settings
                append("\n⚙️ Behavior Settings:\n")
                append("  Shake to Stop: ${mainPrefs.getBoolean("shake_to_stop_enabled", false)}\n")
                append("  Wave to Stop: ${mainPrefs.getBoolean("wave_to_stop_enabled", false)}\n")
                append("  Press to Stop: ${mainPrefs.getBoolean("press_volume_to_stop", false)}\n")
                append("  Shake Threshold: ${mainPrefs.getFloat("shake_threshold", 15.0f)}\n")
                append("  Read App Name: ${mainPrefs.getBoolean("read_app_name", true)}\n")
                append("  Honor Audio Mode: ${voicePrefs.getBoolean("honor_audio_mode", true)}\n")
                
                // Filter Settings
                append("\n🔍 Filter Settings:\n")
                append("  Deduplication: ${mainPrefs.getBoolean("notification_deduplication", true)}\n")
                append("  Dismissal Memory: ${mainPrefs.getBoolean("dismissal_memory_enabled", false)}\n")
                append("  App List Mode: ${mainPrefs.getString("app_list_mode", "none")}\n")
                val wordBlacklist = mainPrefs.getStringSet("word_blacklist", null)
                append("  Blocked Words: ${wordBlacklist?.size ?: 0} words\n")
                
                // System Status
                append("\n🔧 System Status:\n")
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val streamType = AudioManager.STREAM_NOTIFICATION
                val currentVolume = audioManager.getStreamVolume(streamType)
                val maxVolume = audioManager.getStreamMaxVolume(streamType)
                val percentage = if (maxVolume > 0) (currentVolume.toFloat() / maxVolume * 100).toInt() else 0
                append("  Notification Volume: $currentVolume/$maxVolume ($percentage%)\n")
                append("  Ringer Mode: ${getRingerModeName(audioManager.ringerMode)}\n")
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                append("  DND Active: ${notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL}\n")
                append("  NotificationListener: ${if (isNotificationServiceEnabled()) "✅ Enabled" else "❌ Disabled"}\n")
                
            } catch (e: Exception) {
                append("\n❌ Error collecting diagnostics: ${e.message}\n")
            }
            
            append("\n")
        }
    }
    
    private fun getRingerModeName(mode: Int): String {
        return when (mode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            AudioManager.RINGER_MODE_NORMAL -> "Normal"
            else -> "Unknown"
        }
    }
    
    private fun restartTest() {
        // Reset all UI and start again
        initializeSteps()
        startSelfTest()
    }
    
    private fun isNotificationServiceEnabled(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":").toTypedArray()
            for (name in names) {
                val componentName = ComponentName.unflattenFromString(name)
                if (componentName != null && TextUtils.equals(packageName, componentName.packageName)) {
                    return true
                }
            }
        }
        return false
    }
}

