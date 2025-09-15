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
    
    @Override
    public void onServiceConnected() {
        Log.d(TAG, "Accessibility service connected");
        
        // Configure the service
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED | 
                         AccessibilityEvent.TYPE_VIEW_FOCUSED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.notificationTimeout = 100;
        
        setServiceInfo(info);
        Log.d(TAG, "Accessibility service configured with key event filtering");
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Handle accessibility events if needed
        // Currently not used, but available for future features
    }
    
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Log.d(TAG, "Key event received: " + event.getKeyCode() + ", action: " + event.getAction());
        
        // Handle key events (like volume buttons)
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                    Log.d(TAG, "Volume UP button pressed");
                    handleVolumeButtonPress();
                    return true; // Consume the event
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    Log.d(TAG, "Volume DOWN button pressed");
                    handleVolumeButtonPress();
                    return true; // Consume the event
            }
        }
        return super.onKeyEvent(event);
    }
    
    /**
     * Handle volume button press to stop/pause notification reading
     */
    private void handleVolumeButtonPress() {
        Log.d(TAG, "Volume button pressed - checking if Press to Stop is enabled");
        
        // Check if Press to Stop is enabled in settings
        android.content.SharedPreferences prefs = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE);
        boolean pressToStopEnabled = prefs.getBoolean("press_to_stop_enabled", false);
        
        if (pressToStopEnabled) {
            Log.d(TAG, "Press to Stop is enabled - stopping notification reading");
            
            // Send broadcast to NotificationReaderService to stop TTS
            Intent intent = new Intent("com.micoyc.speakthat.STOP_READING");
            sendBroadcast(intent);
        } else {
            Log.d(TAG, "Press to Stop is disabled - ignoring volume button press");
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
