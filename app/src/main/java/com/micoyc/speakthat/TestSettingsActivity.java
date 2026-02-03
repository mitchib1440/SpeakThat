package com.micoyc.speakthat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.button.MaterialButton;
import com.micoyc.speakthat.databinding.ActivityTestSettingsBinding;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

/**
 * Enhanced test activity to verify all settings are working correctly
 * Now includes real-time updates, interactive testing, and comprehensive validation
 */
public class TestSettingsActivity extends AppCompatActivity {
    
    private ActivityTestSettingsBinding binding;
    private SharedPreferences mainPrefs;
    private SharedPreferences voicePrefs;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    
    private static final String PREFS_NAME = "SpeakThatPrefs";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply saved theme
        mainPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isDarkMode = mainPrefs.getBoolean("dark_mode", true); // Default to dark mode
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
        
        // Initialize TTS for testing
        initializeTTS();
        
        // Set up preference change listener for real-time updates
        setupPreferenceListener();
        
        // Set up refresh button
        binding.btnRefresh.setOnClickListener(v -> runAllTests());
        
        // Set up export button
        binding.btnExport.setOnClickListener(v -> exportTestResults());
        
        // Set up interactive test buttons
        binding.btnTestTTS.setOnClickListener(v -> testTTSFunctionality());
        
        // Run initial tests
        runAllTests();
    }
    
    private void initializeTTS() {
        // Get selected TTS engine from preferences
        SharedPreferences voiceSettingsPrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE);
        String selectedEngine = voiceSettingsPrefs.getString("tts_engine_package", "");
        
        if (selectedEngine.isEmpty()) {
            // Use system default engine
            tts = new TextToSpeech(this, status -> {
                isTtsReady = (status == TextToSpeech.SUCCESS);
                if (isTtsReady) {
                    // Apply current voice settings
                    applyVoiceSettings();
                }
            });
        } else {
            // Use selected custom engine
            tts = new TextToSpeech(this, status -> {
                isTtsReady = (status == TextToSpeech.SUCCESS);
                if (isTtsReady) {
                    // Apply current voice settings
                    applyVoiceSettings();
                }
            }, selectedEngine);
        }
    }
    
    private void applyVoiceSettings() {
        if (!isTtsReady) return;
        
        float speechRate = voicePrefs.getFloat("speech_rate", 1.0f);
        float pitch = voicePrefs.getFloat("pitch", 1.0f);
        String language = voicePrefs.getString("language", "en_US");
        
        tts.setSpeechRate(speechRate);
        tts.setPitch(pitch);
        
        // Set language
        String[] langParts = language.split("_");
        if (langParts.length == 2) {
            Locale targetLocale = Locale.forLanguageTag((langParts[0] + "-" + langParts[1]).replace('_', '-'));
            if (targetLocale == null || targetLocale.getLanguage().isEmpty() || "und".equals(targetLocale.getLanguage())) {
                targetLocale = new Locale.Builder().setLanguage(langParts[0]).setRegion(langParts[1]).build();
            }
            tts.setLanguage(targetLocale);
        }
    }
    
    private void setupPreferenceListener() {
        preferenceListener = (sharedPreferences, key) -> {
            // Re-run tests when preferences change
            runAllTests();
            
            // Update TTS settings if voice preferences changed
            if (key.startsWith("voice_") || key.equals("speech_rate") || key.equals("pitch") || key.equals("language")) {
                applyVoiceSettings();
            }
        };
        
        mainPrefs.registerOnSharedPreferenceChangeListener(preferenceListener);
        voicePrefs.registerOnSharedPreferenceChangeListener(preferenceListener);
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
        
        // Test Performance Impact
        results.append("\n⚡ PERFORMANCE IMPACT\n");
        results.append("--------------------\n");
        testPerformanceImpact(results);
        
        // Test Setting Validation
        results.append("\n✅ SETTING VALIDATION\n");
        results.append("--------------------\n");
        validateSettings(results);
        
        // Test Instructions
        results.append("\n📋 INTERACTIVE TESTING\n");
        results.append("---------------------\n");
        results.append("• TTS Test: Tap 'Test TTS' to hear current voice settings\n");
        results.append("• Refresh: Tap 'Refresh' to re-run all tests\n");
        results.append("• Export: Tap 'Export' to share test results\n\n");
        
        results.append("✅ All tests completed!\n");
        
        binding.testResults.setText(results.toString());
    }
    
    private void testGeneralSettings(StringBuilder results) {
        try {
            boolean darkMode = mainPrefs.getBoolean("dark_mode", false);
            boolean autoStart = mainPrefs.getBoolean("auto_start_on_boot", false);
            boolean batteryOpt = mainPrefs.getBoolean("battery_optimization_disabled", false);
            String restartPolicy = mainPrefs.getString("service_restart_policy", "periodic");
            boolean dataExportEnabled = mainPrefs.getBoolean("data_export_enabled", false);
            boolean dataImportEnabled = mainPrefs.getBoolean("data_import_enabled", false);
            String backupFrequency = mainPrefs.getString("backup_frequency", "weekly");
            
            results.append("Dark Mode: ").append(darkMode ? "✅ Enabled" : "❌ Disabled").append("\n");
            results.append("Auto-start on Boot: ").append(autoStart ? "✅ Enabled" : "❌ Disabled").append("\n");
            results.append("Battery Optimization: ").append(batteryOpt ? "✅ Disabled" : "❌ Enabled").append("\n");
            results.append("Restart Policy: ").append(restartPolicy).append("\n");
            results.append("Data Export: ").append(dataExportEnabled ? "✅ Enabled" : "❌ Disabled").append("\n");
            results.append("Data Import: ").append(dataImportEnabled ? "✅ Enabled" : "❌ Disabled").append("\n");
            results.append("Backup Frequency: ").append(backupFrequency).append("\n");
        } catch (Exception e) {
            results.append("❌ Error reading general settings: ").append(e.getMessage()).append("\n");
        }
    }
    
    private void testVoiceSettings(StringBuilder results) {
        try {
            float speechRate = voicePrefs.getFloat("speech_rate", 1.0f);
            float pitch = voicePrefs.getFloat("pitch", 1.0f);
            String voiceName = voicePrefs.getString("voice_name", "");
            String language = voicePrefs.getString("language", "en_US");
            int audioUsage = voicePrefs.getInt("audio_usage", 0);
            int contentType = voicePrefs.getInt("content_type", 0);
            boolean ttsEnabled = voicePrefs.getBoolean("tts_enabled", true);
            int volume = voicePrefs.getInt("volume", 100);
            boolean duckingEnabled = voicePrefs.getBoolean("ducking_enabled", true);
            
            results.append("TTS Status: ").append(isTtsReady ? "✅ Ready" : "❌ Not Ready").append("\n");
            results.append("TTS Enabled: ").append(ttsEnabled ? "✅ Yes" : "❌ No").append("\n");
            results.append("Speech Rate: ").append(speechRate).append("x\n");
            results.append("Pitch: ").append(pitch).append("x\n");
            results.append("Volume: ").append(volume).append("%\n");
            results.append("Voice: ").append(voiceName.isEmpty() ? "Default" : voiceName).append("\n");
            results.append("Language: ").append(language).append("\n");
            results.append("Audio Usage: ").append(getAudioUsageName(audioUsage)).append("\n");
            results.append("Content Type: ").append(getContentTypeName(contentType)).append("\n");
            results.append("Ducking: ").append(duckingEnabled ? "✅ Enabled" : "❌ Disabled").append("\n");
        } catch (Exception e) {
            results.append("❌ Error reading voice settings: ").append(e.getMessage()).append("\n");
        }
    }
    
    private void testBehaviorSettings(StringBuilder results) {
        try {
            String notificationBehavior = mainPrefs.getString("notification_behavior", "interrupt");
            Set<String> priorityApps = mainPrefs.getStringSet("priority_apps", null);
            boolean shakeToStop = mainPrefs.getBoolean("shake_to_stop_enabled", true);
            float shakeThreshold = mainPrefs.getFloat("shake_threshold", 12.0f);
            String mediaBehavior = mainPrefs.getString("media_behavior", "ignore");
            int duckingVolume = mainPrefs.getInt("ducking_volume", 30);
            int delayBeforeReadout = mainPrefs.getInt("delay_before_readout", 0);
            boolean readAppName = mainPrefs.getBoolean("read_app_name", true);
            boolean readNotificationTitle = mainPrefs.getBoolean("read_notification_title", true);
            boolean readNotificationText = mainPrefs.getBoolean("read_notification_text", true);
            int maxReadoutLength = mainPrefs.getInt("max_readout_length", 200);
            boolean waveToStop = mainPrefs.getBoolean("wave_to_stop_enabled", false);
            float waveThresholdCm = mainPrefs.getFloat("wave_threshold", 3.0f);
            int waveTimeoutSeconds = mainPrefs.getInt("wave_timeout_seconds", 30);
            SharedPreferences behaviorPrefs = getSharedPreferences("BehaviorSettings", MODE_PRIVATE);
            float waveThresholdPercent = behaviorPrefs.getFloat("wave_threshold_percent", 60f);
            boolean honourDnd = mainPrefs.getBoolean("honour_do_not_disturb", true);
            boolean honourPhoneCalls = mainPrefs.getBoolean("honour_phone_calls", true);
            boolean honourSilentMode = mainPrefs.getBoolean("honour_silent_mode", true);
            boolean honourVibrateMode = mainPrefs.getBoolean("honour_vibrate_mode", true);
            String contentCapMode = mainPrefs.getString("content_cap_mode", "disabled");
            int contentCapWordCount = mainPrefs.getInt("content_cap_word_count", 6);
            int contentCapSentenceCount = mainPrefs.getInt("content_cap_sentence_count", 1);
            int contentCapTimeLimit = mainPrefs.getInt("content_cap_time_limit", 10);
            
            results.append("Notification Behavior: ").append(notificationBehavior).append("\n");
            results.append("Priority Apps: ").append(priorityApps != null ? priorityApps.size() : 0).append(" apps\n");
            results.append("Shake to Stop: ").append(shakeToStop ? "✅ Enabled" : "❌ Disabled").append("\n");
            results.append("Shake Threshold: ").append(shakeThreshold).append("\n");
            results.append("Media Behavior: ").append(mediaBehavior).append("\n");
            results.append("Ducking Volume: ").append(duckingVolume).append("%\n");
            results.append("Delay Before Readout: ").append(delayBeforeReadout).append("s\n");
            results.append("Read App Name: ").append(readAppName ? "✅ Yes" : "❌ No").append("\n");
            results.append("Read Title: ").append(readNotificationTitle ? "✅ Yes" : "❌ No").append("\n");
            results.append("Read Text: ").append(readNotificationText ? "✅ Yes" : "❌ No").append("\n");
            results.append("Max Readout Length: ").append(maxReadoutLength).append(" chars\n");
            results.append("Wave to Stop: ").append(waveToStop ? "✅ Enabled" : "❌ Disabled").append("\n");
            results.append("Wave Threshold: ").append(String.format(Locale.getDefault(), "%.1f cm (%.0f%%)", waveThresholdCm, waveThresholdPercent)).append("\n");
            results.append("Wave Timeout: ").append(waveTimeoutSeconds == 0 ? "Disabled" : waveTimeoutSeconds + "s").append("\n");
            results.append("Honour Do Not Disturb: ").append(honourDnd ? "✅ Yes" : "❌ No").append("\n");
            results.append("Honour Phone Calls: ").append(honourPhoneCalls ? "✅ Yes" : "❌ No").append("\n");
            results.append("Honour Silent Mode: ").append(honourSilentMode ? "✅ Yes" : "❌ No").append("\n");
            results.append("Honour Vibrate Mode: ").append(honourVibrateMode ? "✅ Yes" : "❌ No").append("\n");
            results.append("Content Cap Mode: ").append(contentCapMode).append("\n");
            results.append("Content Cap Words: ").append(contentCapWordCount).append("\n");
            results.append("Content Cap Sentences: ").append(contentCapSentenceCount).append("\n");
            results.append("Content Cap Time: ").append(contentCapTimeLimit).append("s\n");
        } catch (Exception e) {
            results.append("❌ Error reading behavior settings: ").append(e.getMessage()).append("\n");
        }
    }
    
    private void testFilterSettings(StringBuilder results) {
        try {
            String appListMode = mainPrefs.getString("app_list_mode", "none");
            Set<String> appList = mainPrefs.getStringSet("app_list", null);
            Set<String> privateApps = mainPrefs.getStringSet("app_private_flags", null);
            Set<String> wordBlacklist = mainPrefs.getStringSet("word_blacklist", null);
            Set<String> privateWords = mainPrefs.getStringSet("word_blacklist_private", null);
            String wordReplacements = mainPrefs.getString("word_replacements", "");
            boolean caseSensitive = mainPrefs.getBoolean("case_sensitive_filtering", false);
            boolean regexEnabled = mainPrefs.getBoolean("regex_filtering", false);
            int minWordLength = mainPrefs.getInt("min_word_length", 3);
            String urlHandlingMode = mainPrefs.getString("url_handling_mode", "domain_only");
            String urlReplacementText = mainPrefs.getString("url_replacement_text", "");
            
            results.append("App List Mode: ").append(appListMode).append("\n");
            results.append("Filtered Apps: ").append(appList != null ? appList.size() : 0).append(" apps\n");
            results.append("Private Apps: ").append(privateApps != null ? privateApps.size() : 0).append(" apps\n");
            results.append("Blocked Words: ").append(wordBlacklist != null ? wordBlacklist.size() : 0).append(" words\n");
            results.append("Private Words: ").append(privateWords != null ? privateWords.size() : 0).append(" words\n");
            results.append("Word Swaps: ").append(wordReplacements.split("\\|").length - (wordReplacements.isEmpty() ? 0 : 1)).append(" swaps\n");
            results.append("Case Sensitive: ").append(caseSensitive ? "✅ Yes" : "❌ No").append("\n");
            results.append("Regex Enabled: ").append(regexEnabled ? "✅ Yes" : "❌ No").append("\n");
            results.append("Min Word Length: ").append(minWordLength).append(" chars\n");
            results.append("URL Handling: ").append(urlHandlingMode).append("\n");
            if ("dont_read".equals(urlHandlingMode)) {
                results.append("URL Replacement Text: ").append(urlReplacementText.isEmpty() ? "(empty)" : urlReplacementText).append("\n");
            }
        } catch (Exception e) {
            results.append("❌ Error reading filter settings: ").append(e.getMessage()).append("\n");
        }
    }
    
    private void testSystemStatus(StringBuilder results) {
        try {
            boolean masterSwitch = mainPrefs.getBoolean("master_switch_enabled", true);
            boolean hasSeenOnboarding = mainPrefs.getBoolean("has_seen_onboarding", false);
            boolean isNotificationServiceEnabled = isNotificationServiceEnabled();
            boolean isAccessibilityServiceEnabled = isAccessibilityServiceEnabled();
            long lastServiceStart = mainPrefs.getLong("last_service_start", 0);
            int serviceRestartCount = mainPrefs.getInt("service_restart_count", 0);
            String appVersion = getAppVersion();
            
            results.append("Master Switch: ").append(masterSwitch ? "✅ Enabled" : "❌ Disabled").append("\n");
            results.append("Onboarding Completed: ").append(hasSeenOnboarding ? "✅ Yes" : "❌ No").append("\n");
            results.append("Notification Permission: ").append(isNotificationServiceEnabled ? "✅ Granted" : "❌ Not Granted").append("\n");
            results.append("Accessibility Service: ").append(isAccessibilityServiceEnabled ? "✅ Enabled" : "❌ Disabled").append("\n");
            results.append("Last Service Start: ").append(lastServiceStart > 0 ? new Date(lastServiceStart).toString() : "Never").append("\n");
            results.append("Service Restart Count: ").append(serviceRestartCount).append("\n");
            results.append("App Version: ").append(appVersion).append("\n");
        } catch (Exception e) {
            results.append("❌ Error reading system status: ").append(e.getMessage()).append("\n");
        }
    }
    
    private void testPerformanceImpact(StringBuilder results) {
        try {
            boolean batteryOpt = mainPrefs.getBoolean("battery_optimization_disabled", false);
            String restartPolicy = mainPrefs.getString("service_restart_policy", "periodic");
            int delayBeforeReadout = mainPrefs.getInt("delay_before_readout", 0);
            int maxReadoutLength = mainPrefs.getInt("max_readout_length", 200);
            Set<String> appList = mainPrefs.getStringSet("app_list", null);
            Set<String> wordBlacklist = mainPrefs.getStringSet("word_blacklist", null);
            
            results.append("Battery Impact: ");
            if (!batteryOpt) {
                results.append("⚠️ MEDIUM (Battery optimization enabled)\n");
            } else {
                results.append("✅ LOW (Battery optimization disabled)\n");
            }
            
            results.append("Memory Impact: ");
            int appCount = appList != null ? appList.size() : 0;
            int wordCount = wordBlacklist != null ? wordBlacklist.size() : 0;
            if (appCount > 50 || wordCount > 100) {
                results.append("⚠️ HIGH (Many filters)\n");
            } else if (appCount > 20 || wordCount > 50) {
                results.append("⚠️ MEDIUM (Moderate filters)\n");
            } else {
                results.append("✅ LOW (Few filters)\n");
            }
            
            results.append("Processing Delay: ").append(delayBeforeReadout).append("s\n");
            results.append("Text Length Limit: ").append(maxReadoutLength).append(" chars\n");
            results.append("Restart Frequency: ").append(restartPolicy).append("\n");
        } catch (Exception e) {
            results.append("❌ Error analyzing performance: ").append(e.getMessage()).append("\n");
        }
    }
    
    private void validateSettings(StringBuilder results) {
        try {
            // Check for conflicts
            boolean batteryOpt = mainPrefs.getBoolean("battery_optimization_disabled", false);
            String notificationBehavior = mainPrefs.getString("notification_behavior", "interrupt");
            String mediaBehavior = mainPrefs.getString("media_behavior", "ignore");
            
            // Validation checks
            
            if (notificationBehavior.equals("interrupt") && mediaBehavior.equals("pause")) {
                results.append("⚠️ WARNING: Interrupting notifications with media pausing may cause conflicts\n");
            }
            
            // Check for missing required settings
            boolean hasSeenOnboarding = mainPrefs.getBoolean("has_seen_onboarding", false);
            if (!hasSeenOnboarding) {
                results.append("⚠️ WARNING: Onboarding not completed\n");
            }
            
            boolean isNotificationServiceEnabled = isNotificationServiceEnabled();
            if (!isNotificationServiceEnabled) {
                results.append("❌ ERROR: Notification permission not granted\n");
            }
            
            // Check for reasonable values
            float speechRate = voicePrefs.getFloat("speech_rate", 1.0f);
            if (speechRate < 0.1f || speechRate > 3.0f) {
                results.append("⚠️ WARNING: Speech rate may be too extreme: ").append(speechRate).append("\n");
            }
            
            float shakeThreshold = mainPrefs.getFloat("shake_threshold", 12.0f);
            if (shakeThreshold < 5.0f || shakeThreshold > 50.0f) {
                results.append("⚠️ WARNING: Shake threshold may be too extreme: ").append(shakeThreshold).append("\n");
            }
            
            results.append("✅ Settings validation completed\n");
        } catch (Exception e) {
            results.append("❌ Error validating settings: ").append(e.getMessage()).append("\n");
        }
    }
    
    private void testTTSFunctionality() {
        if (!isTtsReady) {
            Toast.makeText(this, "TTS not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String testText = "This is a test of the current voice settings. Speech rate, pitch, and language are being tested.";
        tts.speak(testText, TextToSpeech.QUEUE_FLUSH, null, "test_utterance");
        Toast.makeText(this, "TTS test started", Toast.LENGTH_SHORT).show();
    }
    
    private void exportTestResults() {
        String results = binding.testResults.getText().toString();
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "SpeakThat Settings Test Results");
        shareIntent.putExtra(Intent.EXTRA_TEXT, results);
        
        startActivity(Intent.createChooser(shareIntent, "Share Test Results"));
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
    
    /**
     * Check if the accessibility service is enabled
     * 
     * @return true if the accessibility service is enabled, false otherwise
     */
    private boolean isAccessibilityServiceEnabled() {
        String packageName = getPackageName();
        String serviceName = packageName + "/com.micoyc.speakthat.SpeakThatAccessibilityService";
        
        String enabledServices = android.provider.Settings.Secure.getString(getContentResolver(), 
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        
        if (enabledServices != null && !enabledServices.isEmpty()) {
            String[] services = enabledServices.split(":");
            for (String service : services) {
                if (service.equals(serviceName)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private String getAppVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "Unknown";
        }
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
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up
        if (preferenceListener != null) {
            mainPrefs.unregisterOnSharedPreferenceChangeListener(preferenceListener);
            voicePrefs.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        }
        
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
} 