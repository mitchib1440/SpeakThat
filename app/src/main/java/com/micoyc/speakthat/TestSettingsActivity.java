package com.micoyc.speakthat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.micoyc.speakthat.databinding.ActivityTestSettingsBinding;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

/**
 * Test activity to verify all settings are working correctly
 */
public class TestSettingsActivity extends AppCompatActivity {
    
    private ActivityTestSettingsBinding binding;
    private SharedPreferences mainPrefs;
    private SharedPreferences voicePrefs;
    
    private static final String PREFS_NAME = "SpeakThatPrefs";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply saved theme
        mainPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isDarkMode = mainPrefs.getBoolean("dark_mode", false); // Default to light mode
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
        
        // Initialize voice preferences
        voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE);
        
        // Initialize view binding
        binding = ActivityTestSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Set up toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_test_settings);
        }
        
        // Run all tests
        runAllTests();
    }
    
    private void applySavedTheme() {
        boolean isDarkMode = mainPrefs.getBoolean("dark_mode", true);
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }
    
    private void runAllTests() {
        StringBuilder results = new StringBuilder();
        results.append("🔍 SPEAKTHAT SETTINGS TEST\n");
        results.append("========================\n\n");
        results.append("Test run: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n\n");
        
        // Test General Settings
        results.append("📱 GENERAL SETTINGS\n");
        results.append("-------------------\n");
        testGeneralSettings(results);
        
        // Test Voice Settings
        results.append("\n🎤 VOICE SETTINGS\n");
        results.append("----------------\n");
        testVoiceSettings(results);
        
        // Test Behavior Settings
        results.append("\n⚙️ BEHAVIOR SETTINGS\n");
        results.append("-------------------\n");
        testBehaviorSettings(results);
        
        // Test Filter Settings
        results.append("\n🔍 FILTER SETTINGS\n");
        results.append("-----------------\n");
        testFilterSettings(results);
        
        // Test System Status
        results.append("\n🔧 SYSTEM STATUS\n");
        results.append("---------------\n");
        testSystemStatus(results);
        
        // Test Instructions
        results.append("\n📋 TESTING INSTRUCTIONS\n");
        results.append("----------------------\n");
        results.append("1. Auto-start: Restart device, check if service starts\n");
        results.append("2. Battery optimization: Check system settings\n");
        results.append("3. Export/Import: Use General Settings → Data Management\n");
        results.append("4. TTS Onboarding: Clear app data and restart\n");
        results.append("5. Service restart: Force-stop service and observe\n\n");
        
        results.append("✅ All tests completed!\n");
        
        binding.testResults.setText(results.toString());
    }
    
    private void testGeneralSettings(StringBuilder results) {
        boolean darkMode = mainPrefs.getBoolean("dark_mode", false); // Default to light mode
        boolean autoStart = mainPrefs.getBoolean("auto_start_on_boot", false);
        boolean batteryOpt = mainPrefs.getBoolean("battery_optimization_disabled", false);
        boolean aggressiveProc = mainPrefs.getBoolean("aggressive_background_processing", false);
        String restartPolicy = mainPrefs.getString("service_restart_policy", "periodic");
        
        results.append("Dark Mode: ").append(darkMode ? "✅ Enabled" : "❌ Disabled").append("\n");
        results.append("Auto-start on Boot: ").append(autoStart ? "✅ Enabled" : "❌ Disabled").append("\n");
        results.append("Battery Optimization: ").append(batteryOpt ? "✅ Disabled" : "❌ Enabled").append("\n");
        results.append("Aggressive Processing: ").append(aggressiveProc ? "✅ Enabled" : "❌ Disabled").append("\n");
        results.append("Restart Policy: ").append(restartPolicy).append("\n");
    }
    
    private void testVoiceSettings(StringBuilder results) {
        float speechRate = voicePrefs.getFloat("speech_rate", 1.0f);
        float pitch = voicePrefs.getFloat("pitch", 1.0f);
        String voiceName = voicePrefs.getString("voice_name", "");
        String language = voicePrefs.getString("language", "en_US");
        int audioUsage = voicePrefs.getInt("audio_usage", 0);
        int contentType = voicePrefs.getInt("content_type", 0);
        
        results.append("Speech Rate: ").append(speechRate).append("x\n");
        results.append("Pitch: ").append(pitch).append("x\n");
        results.append("Voice: ").append(voiceName.isEmpty() ? "Default" : voiceName).append("\n");
        results.append("Language: ").append(language).append("\n");
        results.append("Audio Usage: ").append(getAudioUsageName(audioUsage)).append("\n");
        results.append("Content Type: ").append(getContentTypeName(contentType)).append("\n");
    }
    
    private void testBehaviorSettings(StringBuilder results) {
        String notificationBehavior = mainPrefs.getString("notification_behavior", "interrupt");
        Set<String> priorityApps = mainPrefs.getStringSet("priority_apps", null);
        boolean shakeToStop = mainPrefs.getBoolean("shake_to_stop_enabled", true);
        float shakeThreshold = mainPrefs.getFloat("shake_threshold", 12.0f);
        String mediaBehavior = mainPrefs.getString("media_behavior", "ignore");
        int duckingVolume = mainPrefs.getInt("ducking_volume", 30);
        int delayBeforeReadout = mainPrefs.getInt("delay_before_readout", 0);
        
        results.append("Notification Behavior: ").append(notificationBehavior).append("\n");
        results.append("Priority Apps: ").append(priorityApps != null ? priorityApps.size() : 0).append(" apps\n");
        results.append("Shake to Stop: ").append(shakeToStop ? "✅ Enabled" : "❌ Disabled").append("\n");
        results.append("Shake Threshold: ").append(shakeThreshold).append("\n");
        results.append("Media Behavior: ").append(mediaBehavior).append("\n");
        results.append("Ducking Volume: ").append(duckingVolume).append("%\n");
        results.append("Delay Before Readout: ").append(delayBeforeReadout).append("s\n");
    }
    
    private void testFilterSettings(StringBuilder results) {
        String appListMode = mainPrefs.getString("app_list_mode", "none");
        Set<String> appList = mainPrefs.getStringSet("app_list", null);
        Set<String> privateApps = mainPrefs.getStringSet("app_private_flags", null);
        Set<String> wordBlacklist = mainPrefs.getStringSet("word_blacklist", null);
        Set<String> privateWords = mainPrefs.getStringSet("word_blacklist_private", null);
        String wordReplacements = mainPrefs.getString("word_replacements", "");
        
        results.append("App List Mode: ").append(appListMode).append("\n");
        results.append("Filtered Apps: ").append(appList != null ? appList.size() : 0).append(" apps\n");
        results.append("Private Apps: ").append(privateApps != null ? privateApps.size() : 0).append(" apps\n");
        results.append("Blocked Words: ").append(wordBlacklist != null ? wordBlacklist.size() : 0).append(" words\n");
        results.append("Private Words: ").append(privateWords != null ? privateWords.size() : 0).append(" words\n");
        results.append("Word Swaps: ").append(wordReplacements.split("\\|").length - (wordReplacements.isEmpty() ? 0 : 1)).append(" swaps\n");
    }
    
    private void testSystemStatus(StringBuilder results) {
        boolean masterSwitch = mainPrefs.getBoolean("master_switch_enabled", true);
        boolean hasSeenOnboarding = mainPrefs.getBoolean("has_seen_onboarding", false);
        boolean isNotificationServiceEnabled = isNotificationServiceEnabled();
        
        results.append("Master Switch: ").append(masterSwitch ? "✅ Enabled" : "❌ Disabled").append("\n");
        results.append("Onboarding Completed: ").append(hasSeenOnboarding ? "✅ Yes" : "❌ No").append("\n");
        results.append("Notification Permission: ").append(isNotificationServiceEnabled ? "✅ Granted" : "❌ Not Granted").append("\n");
    }
    
    private boolean isNotificationServiceEnabled() {
        String packageName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat != null && !flat.isEmpty()) {
            String[] names = flat.split(":");
            for (String name : names) {
                android.content.ComponentName componentName = android.content.ComponentName.unflattenFromString(name);
                if (componentName != null && packageName.equals(componentName.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private String getAudioUsageName(int usage) {
        switch (usage) {
            case 0: return "Media";
            case 1: return "Notification";
            case 2: return "Alarm";
            case 3: return "Voice Call";
            case 4: return "Navigation";
            default: return "Unknown";
        }
    }
    
    private String getContentTypeName(int contentType) {
        switch (contentType) {
            case 0: return "Speech";
            case 1: return "Music";
            case 2: return "Notification Sound";
            case 3: return "Sonification";
            default: return "Unknown";
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
} 