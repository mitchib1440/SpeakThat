package com.micoyc.speakthat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * SelfTest Helper
 * Handles test notification posting and log analysis for the SelfTest system
 */
class SelfTestHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "SelfTestHelper"
        private const val TEST_NOTIFICATION_ID = 9999
        private const val TEST_CHANNEL_ID = "selftest_channel"
        private const val TEST_CHANNEL_NAME = "SelfTest Notifications"
        const val EXTRA_IS_SELFTEST = "is_selftest_notification"
        
        // Log markers to look for
        private const val LOG_MARKER_RECEIVED = "SelfTest notification received"
        private const val LOG_MARKER_FILTERING = "SelfTest notification passed filtering"
        private const val LOG_MARKER_SPEAKING = "SelfTest notification speaking"
        private const val LOG_MARKER_TTS_COMPLETED = "TTS completed"
    }
    
    enum class TestStatus {
        NOT_RECEIVED,
        RECEIVED_NOT_READ,
        READ_SUCCESSFULLY
    }
    
    data class TestResult(
        val status: TestStatus,
        val blockingReason: String = ""
    )
    
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringCallback: ((TestResult) -> Unit)? = null
    
    /**
     * Post a test notification that SpeakThat should read
     */
    fun postTestNotification(): Boolean {
        return try {
            // Create notification channel (required for Android O+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    TEST_CHANNEL_ID,
                    TEST_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Channel for SelfTest notifications"
                }
                
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
            
            // Create the notification with special marker
            val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_add_24)
                .setContentTitle("SpeakThat Test")
                .setContentText("This is a test notification. If you can hear this, the test passed.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            
            // Add custom extra to mark this as a test notification
            notification.extras.putBoolean(EXTRA_IS_SELFTEST, true)
            
            // Post the notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(TEST_NOTIFICATION_ID, notification)
            
            InAppLogger.log("SelfTest", "Test notification posted with ID: $TEST_NOTIFICATION_ID")
            Log.d(TAG, "Test notification posted successfully")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post test notification", e)
            InAppLogger.logError("SelfTest", "Failed to post test notification: ${e.message}")
            false
        }
    }
    
    /**
     * Monitor logs for test notification events
     * Uses two-stage timeout:
     * 1. Initial timeout (10s) to wait for notification to start being read
     * 2. Extended timeout (60s) once speaking starts, to accommodate slow speech rates
     * Checks every 500ms
     */
    fun monitorLogsForTestNotification(timeoutMs: Long, callback: (TestResult) -> Unit) {
        monitoringCallback = callback
        val startTime = System.currentTimeMillis()
        var checkCount = 0
        var speakingDetectedTime: Long? = null
        val extendedTimeoutMs = 60000L // 60 seconds for slow speech rates
        
        InAppLogger.log("SelfTest", "Started monitoring logs with two-stage timeout: ${timeoutMs}ms initial, ${extendedTimeoutMs}ms extended")
        
        val checkRunnable = object : Runnable {
            override fun run() {
                checkCount++
                val elapsed = System.currentTimeMillis() - startTime
                
                // Get all logs and check for markers
                val logsText = InAppLogger.getAllLogs()
                
                val receivedNotification = logsText.contains(LOG_MARKER_RECEIVED)
                val passedFiltering = logsText.contains(LOG_MARKER_FILTERING)
                val speaking = logsText.contains(LOG_MARKER_SPEAKING)
                val ttsCompleted = logsText.contains(LOG_MARKER_TTS_COMPLETED)
                
                // Track when speaking starts for extended timeout
                if (speaking && speakingDetectedTime == null) {
                    speakingDetectedTime = System.currentTimeMillis()
                    InAppLogger.log("SelfTest", "Speaking detected at ${elapsed}ms - extending timeout to ${extendedTimeoutMs}ms")
                    Log.d(TAG, "Speaking detected - switching to extended timeout")
                }
                
                val timeSinceSpeaking = speakingDetectedTime?.let { System.currentTimeMillis() - it }
                
                InAppLogger.log("SelfTest", "Log check #$checkCount (elapsed: ${elapsed}ms, speaking: ${timeSinceSpeaking ?: "N/A"}ms)")
                Log.d(TAG, "Log analysis: received=$receivedNotification, filtered=$passedFiltering, speaking=$speaking, completed=$ttsCompleted")
                
                when {
                    ttsCompleted && speaking -> {
                        // Success! Notification was read
                        InAppLogger.log("SelfTest", "Test notification read successfully (total time: ${elapsed}ms)")
                        monitoringCallback?.invoke(TestResult(TestStatus.READ_SUCCESSFULLY))
                        monitoringCallback = null
                        return
                    }
                    speaking && !ttsCompleted && (logsText.contains("TTS stopped by shake", ignoreCase = true) 
                        || logsText.contains("TTS stopped by wave", ignoreCase = true)
                        || logsText.contains("TTS stopped by press", ignoreCase = true)) -> {
                        // User explicitly cancelled TTS before completion - treat as special case, not a failure
                        InAppLogger.log("SelfTest", "Test interrupted by user action (TTS was stopped before completion)")
                        val reason = findBlockingReason(logsText)
                        monitoringCallback?.invoke(TestResult(TestStatus.RECEIVED_NOT_READ, reason))
                        monitoringCallback = null
                        return
                    }
                    receivedNotification && !passedFiltering -> {
                        // Received but blocked by filtering
                        InAppLogger.log("SelfTest", "Test notification received but blocked by filtering")
                        val reason = findBlockingReason(logsText)
                        monitoringCallback?.invoke(TestResult(TestStatus.RECEIVED_NOT_READ, reason))
                        monitoringCallback = null
                        return
                    }
                    speaking && timeSinceSpeaking != null && timeSinceSpeaking >= extendedTimeoutMs -> {
                        // Speaking started but didn't complete within extended timeout
                        InAppLogger.log("SelfTest", "Test notification started speaking but didn't complete after ${extendedTimeoutMs}ms")
                        val reason = "TTS_STARTED_NOT_COMPLETED: TTS started but didn't complete within timeout (possible TTS hang or audio routing issue)"
                        monitoringCallback?.invoke(TestResult(TestStatus.RECEIVED_NOT_READ, reason))
                        monitoringCallback = null
                        return
                    }
                    receivedNotification && passedFiltering && !speaking -> {
                        // Received and filtered but not speaking - keep waiting with initial timeout
                        if (elapsed >= timeoutMs) {
                            InAppLogger.log("SelfTest", "Test notification received and filtered but not speaking after ${timeoutMs}ms")
                            val reason = findBlockingReason(logsText)
                            // If no specific reason found, this means TTS never started
                            val finalReason = if (reason.contains("Unknown reason", ignoreCase = true)) {
                                "TTS_NEVER_STARTED: Notification was received and passed filtering, but TTS never started speaking"
                            } else {
                                reason
                            }
                            monitoringCallback?.invoke(TestResult(TestStatus.RECEIVED_NOT_READ, finalReason))
                            monitoringCallback = null
                            return
                        }
                    }
                    elapsed >= timeoutMs && speakingDetectedTime == null -> {
                        // Initial timeout reached and speaking hasn't started - notification not received or blocked
                        InAppLogger.log("SelfTest", "Timeout reached (${timeoutMs}ms) - test notification not received or not speaking")
                        if (receivedNotification) {
                            val reason = findBlockingReason(logsText)
                            // If notification was received and passed filtering but TTS never started, provide specific reason
                            val finalReason = if (passedFiltering && reason.contains("Unknown reason", ignoreCase = true)) {
                                "TTS_NEVER_STARTED: Notification was received and passed filtering, but TTS never started speaking"
                            } else {
                                reason
                            }
                            monitoringCallback?.invoke(TestResult(TestStatus.RECEIVED_NOT_READ, finalReason))
                        } else {
                            monitoringCallback?.invoke(TestResult(TestStatus.NOT_RECEIVED))
                        }
                        monitoringCallback = null
                        return
                    }
                }
                
                // Continue monitoring
                handler.postDelayed(this, 500)
            }
        }
        
        // Start monitoring
        handler.post(checkRunnable)
    }
    
    /**
     * Analyze logs to find why notification was blocked
     * Returns a detailed, user-friendly reason
     */
    private fun findBlockingReason(logsText: String): String {
        return when {
            // Check for TTS cancellation by user (specific methods only)
            logsText.contains("TTS stopped by shake", ignoreCase = true) -> 
                "Test interrupted: You stopped TTS using Shake to Stop"
            logsText.contains("TTS stopped by wave", ignoreCase = true) -> 
                "Test interrupted: You stopped TTS using Wave to Stop"
            logsText.contains("TTS stopped by press", ignoreCase = true) -> 
                "Test interrupted: You stopped TTS using Press to Stop"
                
            // Check for deduplication
            logsText.contains("Duplicate notification", ignoreCase = true) && logsText.contains("skipping", ignoreCase = true) -> 
                "Blocked by deduplication: The test notification was considered a duplicate of a recent notification"
            logsText.contains("Content-based duplicate", ignoreCase = true) -> 
                "Blocked by content deduplication: Similar notification was recently read"
                
            // Check for dismissal memory
            logsText.contains("Dismissal memory", ignoreCase = true) && logsText.contains("recently dismissed", ignoreCase = true) -> 
                "Blocked by dismissal memory: A similar notification was recently dismissed"
                
            // Check for master switch
            logsText.contains("Master switch disabled", ignoreCase = true) || logsText.contains("Master switch is OFF", ignoreCase = true) -> 
                "Master switch is disabled in settings"
                
            // Check for audio mode issues
            logsText.contains("Audio mode", ignoreCase = true) && logsText.contains("ignoring", ignoreCase = true) -> 
                "Blocked by audio mode: Phone is in Silent or Vibrate mode and 'Honor Audio Mode' is enabled"
            logsText.contains("Silent mode", ignoreCase = true) || logsText.contains("Vibrate mode", ignoreCase = true) -> 
                "Blocked by audio mode: Phone audio mode is set to Silent or Vibrate"
                
            // Check for DND
            logsText.contains("Do Not Disturb", ignoreCase = true) || logsText.contains("DND", ignoreCase = true) -> 
                "Blocked by Do Not Disturb mode"
                
            // Check for rules system
            logsText.contains("Rules blocked", ignoreCase = true) || logsText.contains("Rule evaluation", ignoreCase = true) && logsText.contains("blocked", ignoreCase = true) -> 
                "Blocked by rules system: An active rule prevented this notification from being read"
                
            // Check for content filtering
            logsText.contains("content filter", ignoreCase = true) && logsText.contains("blocked", ignoreCase = true) -> 
                "Blocked by content filtering: The notification text matched a filter pattern"
            logsText.contains("filter", ignoreCase = true) && logsText.contains("empty", ignoreCase = true) -> 
                "Blocked by filtering: The notification text was empty or filtered out completely"
                
            // Check for TTS issues
            logsText.contains("TTS not initialized", ignoreCase = true) -> 
                "TTS engine not initialized: Text-to-Speech failed to start"
            logsText.contains("TTS error", ignoreCase = true) -> 
                "TTS engine error: Text-to-Speech encountered an error"
                
            // Check for volume issues
            logsText.contains("volume is 0", ignoreCase = true) || logsText.contains("volume: 0", ignoreCase = true) -> 
                "Volume is muted: Notification volume is set to 0"
                
            // Default
            else -> 
                "Unknown reason: Check debug logs for details. The notification may have been blocked by a filter, rule, or system setting."
        }
    }
    
    /**
     * Cancel the test notification
     */
    fun cancelTestNotification() {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(TEST_NOTIFICATION_ID)
            Log.d(TAG, "Test notification cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel test notification", e)
        }
    }
}

