package com.micoyc.speakthat;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Accessibility Service for SpeakThat
 * 
 * This service enables advanced control features for the notification reader:
 * - Volume button control: Stop/pause notification readouts using hardware volume buttons
 * - Enhanced gesture recognition: Alternative control methods beyond current shake/wave detection
 * - Global media control: Better integration with music apps and media playback
 * - System-wide shortcuts: Quick actions from anywhere in the system
 * 
 * This service is completely optional - core app functionality works without it.
 * All processing stays on-device with no data collection.
 */
public class SpeakThatAccessibilityService extends AccessibilityService {
    
    private static final String TAG = "SpeakThatAccessibility";
    
    // Track volume button states for simultaneous press detection
    private boolean volumeUpPressed = false;
    private boolean volumeDownPressed = false;
    private long lastVolumeUpTime = 0;
    private long lastVolumeDownTime = 0;
    private static final long SIMULTANEOUS_PRESS_THRESHOLD = 500; // 500ms window
    
    @Override
    public void onServiceConnected() {
        Log.d(TAG, "Accessibility service connected");
        
        // Read current Press to Stop setting for debugging
        // Note: The service will automatically disable key event processing if the feature is turned off
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE);
            boolean pressToStopEnabled = prefs.getBoolean("press_to_stop_enabled", false);
            Log.d(TAG, "Press to Stop setting: " + pressToStopEnabled);
            if (!pressToStopEnabled) {
                Log.d(TAG, "Press to Stop is disabled - key events will be passed through to system");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading settings: " + e.getMessage());
        }
        
        // Configure the accessibility service
        // FLAG_REQUEST_FILTER_KEY_EVENTS is crucial for intercepting hardware key events
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED |
                         AccessibilityEvent.TYPE_VIEW_FOCUSED |
                         AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS; // Essential for key event interception
        info.notificationTimeout = 100;

        setServiceInfo(info);
        Log.d(TAG, "Accessibility service configured with key event filtering");
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }

        // Track foreground app for rule conditions
        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null) {
                ForegroundAppTracker.INSTANCE.updateForegroundPackage(pkg.toString());
            }
        }
    }
    
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Log.d(TAG, "Key event received: " + event.getKeyCode() + ", action: " + event.getAction());

        // Early exit if Press to Stop is disabled to save resources
        android.content.SharedPreferences prefs = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE);
        boolean pressToStopEnabled = prefs.getBoolean("press_to_stop_enabled", false);
        if (!pressToStopEnabled) {
            return super.onKeyEvent(event); // Let system handle the event normally
        }

        // Handle volume button events for simultaneous press detection
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                    Log.d(TAG, "Volume UP button pressed");
                    volumeUpPressed = true;
                    lastVolumeUpTime = System.currentTimeMillis();
                    checkForSimultaneousPress();
                    return false; // Let normal volume control work
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    Log.d(TAG, "Volume DOWN button pressed");
                    volumeDownPressed = true;
                    lastVolumeDownTime = System.currentTimeMillis();
                    checkForSimultaneousPress();
                    return false; // Let normal volume control work
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                    Log.d(TAG, "Volume UP button released");
                    volumeUpPressed = false;
                    break;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    Log.d(TAG, "Volume DOWN button released");
                    volumeDownPressed = false;
                    break;
            }
        }
        return super.onKeyEvent(event);
    }
    
    /**
     * Check if both volume buttons are pressed simultaneously
     * 
     * This method determines if both volume buttons are currently pressed and
     * if they were pressed within the time threshold to be considered "simultaneous".
     * This prevents accidental triggers from slightly staggered button presses.
     */
    private void checkForSimultaneousPress() {
        // Check if both buttons are currently pressed and within the time threshold
        boolean bothPressed = volumeUpPressed && volumeDownPressed;
        boolean withinThreshold = Math.abs(lastVolumeUpTime - lastVolumeDownTime) <= SIMULTANEOUS_PRESS_THRESHOLD;

        Log.d(TAG, "Simultaneous press check - Both: " + bothPressed + ", Within threshold: " + withinThreshold);

        if (bothPressed && withinThreshold) {
            Log.d(TAG, "SIMULTANEOUS PRESS DETECTED!");
            handleSimultaneousVolumePress();
        }
    }
    
    /**
     * Handle simultaneous volume button press to stop notification reading
     * 
     * This method is called when both volume buttons are pressed simultaneously.
     * It checks if the "Press to Stop" feature is enabled in settings, and if so,
     * sends a broadcast to the NotificationReaderService to stop the current TTS.
     * 
     * The broadcast is sent with explicit package targeting to ensure it reaches
     * the correct app instance, even in multi-user or multi-process scenarios.
     */
    private void handleSimultaneousVolumePress() {
        Log.d(TAG, "Handling simultaneous volume press");
        
        // Check if Press to Stop is enabled in settings
        android.content.SharedPreferences prefs = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE);
        boolean pressToStopEnabled = prefs.getBoolean("press_to_stop_enabled", false);
        
        Log.d(TAG, "Press to Stop enabled: " + pressToStopEnabled);
        
        if (pressToStopEnabled) {
            Log.d(TAG, "Sending STOP_READING broadcast");
            
            // Send broadcast to NotificationReaderService to stop TTS
            // Explicit package targeting ensures the broadcast reaches our app
            Intent intent = new Intent("com.micoyc.speakthat.STOP_READING");
            intent.setPackage("com.micoyc.speakthat");
            sendBroadcast(intent);
            
            Log.d(TAG, "STOP_READING broadcast sent successfully");
            
            // Reset button states to prevent multiple triggers
            volumeUpPressed = false;
            volumeDownPressed = false;
        } else {
            Log.d(TAG, "Press to Stop disabled - ignoring simultaneous volume button press");
        }
    }
    
    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted");
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Accessibility service destroyed");
        super.onDestroy();
    }
}
