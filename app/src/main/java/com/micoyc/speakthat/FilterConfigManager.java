package com.micoyc.speakthat;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import com.micoyc.speakthat.StatsSnapshot;
import com.micoyc.speakthat.rules.RuleConfigManager;

public class FilterConfigManager {
    
    private static final String PREFS_NAME = "SpeakThatPrefs";
    private static final String CONFIG_VERSION = "1.0";
    
    // Filter preference keys
    private static final String KEY_APP_LIST_MODE = "app_list_mode";
    private static final String KEY_APP_LIST = "app_list";
    private static final String KEY_APP_PRIVATE_FLAGS = "app_private_flags";
    private static final String KEY_WORD_LIST_MODE = "word_list_mode";
    private static final String KEY_WORD_BLACKLIST = "word_blacklist";
    private static final String KEY_WORD_BLACKLIST_PRIVATE = "word_blacklist_private";
    private static final String KEY_WORD_REPLACEMENTS = "word_replacements";
    private static final String KEY_URL_HANDLING_MODE = "url_handling_mode";
    private static final String KEY_URL_REPLACEMENT_TEXT = "url_replacement_text";
    private static final String KEY_TIDY_SPEECH_REMOVE_EMOJIS = "tidy_speech_remove_emojis";
    
    public static class FilterConfig {
        public String appListMode;
        public Set<String> appList;
        public Set<String> appPrivateFlags;
        public String wordListMode;
        public Set<String> wordBlacklist;
        public Set<String> wordBlacklistPrivate;
        public String wordReplacements; // Stored as delimited string
        public String urlHandlingMode;
        public String urlReplacementText;
        public boolean tidySpeechRemoveEmojis;
        // Media filtering settings
        public boolean mediaFilteringEnabled;
        public Set<String> mediaFilterExceptedApps;
        public Set<String> mediaFilterExceptedAppsPrivate;
        public Set<String> mediaFilterImportantKeywords;
        public Set<String> mediaFilterImportantKeywordsPrivate;
        public Set<String> mediaFilteredApps;
        public Set<String> mediaFilteredAppsPrivate;
        // Persistent/silent filtering settings
        public boolean persistentFilteringEnabled;
        public boolean filterPersistent;
        public boolean filterSilent;
        public boolean filterForegroundServices;
        public boolean filterLowPriority;
        public boolean filterSystemNotifications;
        public String exportDate;
        public String appVersion;
        public String configVersion;
        
        public FilterConfig() {
            this.appList = new HashSet<>();
            this.appPrivateFlags = new HashSet<>();
            this.wordListMode = "blacklist"; // Default to blacklist for backward compatibility
            this.wordBlacklist = new HashSet<>();
            this.wordBlacklistPrivate = new HashSet<>();
            this.wordReplacements = "";
            this.urlHandlingMode = "domain_only";
            this.urlReplacementText = "";
            this.tidySpeechRemoveEmojis = false;
            // Media filtering defaults
            this.mediaFilteringEnabled = false;
            this.mediaFilterExceptedApps = new HashSet<>();
            this.mediaFilterExceptedAppsPrivate = new HashSet<>();
            this.mediaFilterImportantKeywords = new HashSet<>();
            this.mediaFilterImportantKeywordsPrivate = new HashSet<>();
            this.mediaFilteredApps = new HashSet<>();
            this.mediaFilteredAppsPrivate = new HashSet<>();
            // Persistent/silent filtering defaults
            this.persistentFilteringEnabled = false;
            this.filterPersistent = true;
            this.filterSilent = true;
            this.filterForegroundServices = false;
            this.filterLowPriority = false;
            this.filterSystemNotifications = false;
            this.exportDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            this.appVersion = "1.0"; // Static version for export compatibility
            this.configVersion = CONFIG_VERSION;
        }
    }
    
    public static class FullConfig {
        public String exportDate;
        public String appVersion;
        public String configVersion;
        public FilterConfig filters;
        public VoiceConfig voice;
        public BehaviorConfig behavior;
        public GeneralConfig general;
        public StatsConfig statistics;
        
        public FullConfig() {
            this.exportDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            this.appVersion = "1.0";
            this.configVersion = CONFIG_VERSION;
            this.filters = new FilterConfig();
            this.voice = new VoiceConfig();
            this.behavior = new BehaviorConfig();
            this.general = new GeneralConfig();
            this.statistics = new StatsConfig();
        }
    }
    
    public static class VoiceConfig {
        public float speechRate;
        public float pitch;
        public float ttsVolume;
        public String voiceName;
        public String language;
        public String ttsLanguage;        // NEW: TTS-specific language setting
        public String languagePreset;    // NEW: Selected language preset ID
        public boolean isCustomPreset;   // NEW: Whether settings represent a custom preset
        public boolean advancedEnabled;  // NEW: Whether advanced options are enabled
        public int audioUsage;
        public int contentType;
        public String ttsEngine;         // TTS engine package name
        public boolean speakerphoneEnabled; // Force speakerphone output
        
        public VoiceConfig() {
            this.speechRate = 1.0f;
            this.pitch = 1.0f;
            this.ttsVolume = 1.0f;
            this.voiceName = "";
            this.language = "en_US";
            this.ttsLanguage = "system";     // Default TTS language
            this.languagePreset = "en_US";   // Default preset
            this.isCustomPreset = false;     // Not custom by default
            this.advancedEnabled = false;    // Advanced options off by default
            this.audioUsage = 0;
            this.contentType = 0;
            this.ttsEngine = "";             // Empty means use system default
            this.speakerphoneEnabled = false; // Default to false
        }
    }
    
    public static class BehaviorConfig {
        public String notificationBehavior;
        public Set<String> priorityApps;
        public boolean shakeToStopEnabled;
        public float shakeThreshold;
        public int shakeTimeoutSeconds;
        public String mediaBehavior;
        public int duckingVolume;
        public String duckingFallbackStrategy;
        public int delayBeforeReadout;
        public boolean honourDoNotDisturb;
        public boolean honourPhoneCalls; // Add honour phone calls setting
        public boolean honourSilentMode; // Honour Silent ringer mode
        public boolean honourVibrateMode; // Honour Vibrate ringer mode
        @Deprecated
        public boolean honourAudioMode; // Legacy combined audio mode flag
        public boolean persistentNotification; // Add persistent notification setting
        public boolean notificationWhileReading; // Add notification while reading setting
        public boolean waveToStopEnabled;
        public int waveTimeoutSeconds;
        public long waveHoldDurationMs; // Wave hold duration in milliseconds
        public boolean pocketModeEnabled;
        public String customAppNames;
        public String cooldownApps;
        public String speechTemplate;
        public String contentCapMode;
        public int contentCapWordCount;
        public int contentCapSentenceCount;
        public int contentCapTimeLimit;
        public boolean notificationDeduplication; // Deduplication setting
        public boolean dismissalMemoryEnabled; // Dismissal memory setting
        public int dismissalMemoryTimeout; // Dismissal memory timeout in minutes
        
        public BehaviorConfig() {
            this.notificationBehavior = "interrupt";
            this.priorityApps = new HashSet<>();
            this.shakeToStopEnabled = true;
            this.shakeThreshold = 12.0f;
            this.shakeTimeoutSeconds = 30;
            this.mediaBehavior = "ignore";
            this.duckingVolume = 30;
            this.duckingFallbackStrategy = "manual";
            this.delayBeforeReadout = 0;
            this.honourDoNotDisturb = true;
            this.honourPhoneCalls = true; // Default to true for safety
            this.honourSilentMode = true; // Default to true for safety
            this.honourVibrateMode = true; // Default to true for safety
            this.honourAudioMode = true; // Legacy: keep for backwards compatibility
            this.persistentNotification = false; // Default to false
            this.notificationWhileReading = false; // Default to false
            this.waveToStopEnabled = false;
            this.waveTimeoutSeconds = 30;
            this.waveHoldDurationMs = 150; // Default wave hold duration
            this.pocketModeEnabled = false;
            this.customAppNames = "[]";
            this.cooldownApps = "[]";
            this.speechTemplate = "{app} notified you: {content}";
            this.contentCapMode = "disabled";
            this.contentCapWordCount = 6;
            this.contentCapSentenceCount = 1;
            this.contentCapTimeLimit = 10;
            this.notificationDeduplication = false; // Default to false
            this.dismissalMemoryEnabled = true; // Default to true
            this.dismissalMemoryTimeout = 15; // Default 15 minutes
        }
    }
    
    public static class GeneralConfig {
        public boolean darkMode;
        public boolean autoStartOnBoot;
        public boolean batteryOptimizationDisabled;
        @Deprecated
        public boolean aggressiveBackgroundProcessing; // Deprecated: No longer used in UI, kept for backwards compatibility
        public String serviceRestartPolicy;
        
        public GeneralConfig() {
            this.darkMode = true;
            this.autoStartOnBoot = false;
            this.batteryOptimizationDisabled = false;
            this.aggressiveBackgroundProcessing = false;
            this.serviceRestartPolicy = "periodic";
        }
    }

    public static class StatsConfig {
        public int notificationsReceived;
        public int notificationsRead;
        public int readoutsInterrupted;
        public int listenerRebinds;
        public int listenerRebindsSkipped;
        public int listenerRebindsRecovered;
        public int logoTaps;
        public HashMap<String, Integer> filterReasons;
        public Set<String> appsRead;

        public StatsConfig() {
            this.notificationsReceived = 0;
            this.notificationsRead = 0;
            this.readoutsInterrupted = 0;
            this.listenerRebinds = 0;
            this.listenerRebindsSkipped = 0;
            this.listenerRebindsRecovered = 0;
            this.logoTaps = 0;
            this.filterReasons = new HashMap<>();
            this.appsRead = new HashSet<>();
        }
    }
    
    public static class ImportResult {
        public boolean success;
        public String message;
        public int filtersImported;
        
        public ImportResult(boolean success, String message, int filtersImported) {
            this.success = success;
            this.message = message;
            this.filtersImported = filtersImported;
        }
    }
    
    /**
     * Export all current filter settings to JSON format
     */
    public static String exportFilters(Context context) throws JSONException {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        FilterConfig config = new FilterConfig();
        
        // Load current settings
        config.appListMode = prefs.getString(KEY_APP_LIST_MODE, "none");
        config.appList = new HashSet<>(prefs.getStringSet(KEY_APP_LIST, new HashSet<>()));
        config.appPrivateFlags = new HashSet<>(prefs.getStringSet(KEY_APP_PRIVATE_FLAGS, new HashSet<>()));
        config.wordListMode = prefs.getString(KEY_WORD_LIST_MODE, "blacklist");
        config.wordBlacklist = new HashSet<>(prefs.getStringSet(KEY_WORD_BLACKLIST, new HashSet<>()));
        config.wordBlacklistPrivate = new HashSet<>(prefs.getStringSet(KEY_WORD_BLACKLIST_PRIVATE, new HashSet<>()));
        config.wordReplacements = prefs.getString(KEY_WORD_REPLACEMENTS, "");
        config.urlHandlingMode = prefs.getString(KEY_URL_HANDLING_MODE, "domain_only");
        config.urlReplacementText = prefs.getString(KEY_URL_REPLACEMENT_TEXT, "");
        config.tidySpeechRemoveEmojis = prefs.getBoolean(KEY_TIDY_SPEECH_REMOVE_EMOJIS, false);
        
        // Create JSON structure
        JSONObject json = new JSONObject();
        
        // Metadata
        JSONObject metadata = new JSONObject();
        metadata.put("exportDate", config.exportDate);
        metadata.put("appVersion", config.appVersion);
        metadata.put("configVersion", config.configVersion);
        metadata.put("exportType", "SpeakThat_FilterConfig");
        json.put("metadata", metadata);
        
        // Filter settings
        JSONObject filters = new JSONObject();
        filters.put("appListMode", config.appListMode);
        filters.put("appList", new JSONArray(config.appList));
        filters.put("appPrivateFlags", new JSONArray(config.appPrivateFlags));
        filters.put("wordListMode", config.wordListMode);
        filters.put("wordBlacklist", new JSONArray(config.wordBlacklist));
        filters.put("wordBlacklistPrivate", new JSONArray(config.wordBlacklistPrivate));
        filters.put("wordReplacements", config.wordReplacements);
        filters.put("urlHandlingMode", config.urlHandlingMode);
        filters.put("urlReplacementText", config.urlReplacementText);
        filters.put("tidySpeechRemoveEmojis", config.tidySpeechRemoveEmojis);
        json.put("filters", filters);
        
        // Future extension point - we can add more sections here
        // json.put("behaviorSettings", ...);
        // json.put("voiceSettings", ...);
        
        return json.toString(2); // Pretty print with 2-space indentation
    }
    
    /**
     * Export full configuration including all settings
     */
    public static String exportFullConfiguration(Context context) throws JSONException {
        return exportFullConfiguration(context, false);
    }

    /**
     * Export full configuration including all settings, optionally including rules.
     */
    public static String exportFullConfiguration(Context context, boolean includeRules) throws JSONException {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences voicePrefs = context.getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE);
        
        FullConfig config = new FullConfig();
        
        // Load filter settings
        config.filters.appListMode = prefs.getString(KEY_APP_LIST_MODE, "none");
        config.filters.appList = new HashSet<>(prefs.getStringSet(KEY_APP_LIST, new HashSet<>()));
        config.filters.appPrivateFlags = new HashSet<>(prefs.getStringSet(KEY_APP_PRIVATE_FLAGS, new HashSet<>()));
        config.filters.wordListMode = prefs.getString(KEY_WORD_LIST_MODE, "blacklist");
        config.filters.wordBlacklist = new HashSet<>(prefs.getStringSet(KEY_WORD_BLACKLIST, new HashSet<>()));
        config.filters.wordBlacklistPrivate = new HashSet<>(prefs.getStringSet(KEY_WORD_BLACKLIST_PRIVATE, new HashSet<>()));
        config.filters.wordReplacements = prefs.getString(KEY_WORD_REPLACEMENTS, "");
        config.filters.urlHandlingMode = prefs.getString(KEY_URL_HANDLING_MODE, "domain_only");
        config.filters.urlReplacementText = prefs.getString(KEY_URL_REPLACEMENT_TEXT, "");
        config.filters.tidySpeechRemoveEmojis = prefs.getBoolean(KEY_TIDY_SPEECH_REMOVE_EMOJIS, false);
        
        // Load media filtering settings
        config.filters.mediaFilteringEnabled = prefs.getBoolean("media_filtering_enabled", false);
        config.filters.mediaFilterExceptedApps = new HashSet<>(prefs.getStringSet("media_filter_excepted_apps", new HashSet<>()));
        config.filters.mediaFilterExceptedAppsPrivate = new HashSet<>(prefs.getStringSet("media_filter_excepted_apps_private", new HashSet<>()));
        config.filters.mediaFilterImportantKeywords = new HashSet<>(prefs.getStringSet("media_filter_important_keywords", new HashSet<>()));
        config.filters.mediaFilterImportantKeywordsPrivate = new HashSet<>(prefs.getStringSet("media_filter_important_keywords_private", new HashSet<>()));
        config.filters.mediaFilteredApps = new HashSet<>(prefs.getStringSet("media_filtered_apps", new HashSet<>()));
        config.filters.mediaFilteredAppsPrivate = new HashSet<>(prefs.getStringSet("media_filtered_apps_private", new HashSet<>()));
        
        // Load persistent/silent filtering settings
        config.filters.persistentFilteringEnabled = prefs.getBoolean("persistent_filtering_enabled", false);
        config.filters.filterPersistent = prefs.getBoolean("filter_persistent", true);
        config.filters.filterSilent = prefs.getBoolean("filter_silent", true);
        config.filters.filterForegroundServices = prefs.getBoolean("filter_foreground_services", false);
        config.filters.filterLowPriority = prefs.getBoolean("filter_low_priority", false);
        config.filters.filterSystemNotifications = prefs.getBoolean("filter_system_notifications", false);
        
        // Load voice settings
        config.voice.speechRate = voicePrefs.getFloat("speech_rate", 1.0f);
        config.voice.pitch = voicePrefs.getFloat("pitch", 1.0f);
        config.voice.ttsVolume = voicePrefs.getFloat("tts_volume", 1.0f);
        config.voice.voiceName = voicePrefs.getString("voice_name", "");
        config.voice.language = voicePrefs.getString("language", "en_US");
        config.voice.ttsLanguage = voicePrefs.getString("tts_language", "system");        // NEW
        config.voice.languagePreset = voicePrefs.getString("language_preset", "en_US");  // NEW
        config.voice.isCustomPreset = voicePrefs.getBoolean("is_custom_preset", false);  // NEW
        config.voice.advancedEnabled = voicePrefs.getBoolean("show_advanced_voice", false); // NEW
        config.voice.audioUsage = voicePrefs.getInt("audio_usage", 0);
        config.voice.contentType = voicePrefs.getInt("content_type", 0);
        config.voice.ttsEngine = voicePrefs.getString("tts_engine_package", "");
        config.voice.speakerphoneEnabled = voicePrefs.getBoolean("speakerphone_enabled", false);
        
        // Load behavior settings
        config.behavior.notificationBehavior = prefs.getString("notification_behavior", "interrupt");
        config.behavior.priorityApps = new HashSet<>(prefs.getStringSet("priority_apps", new HashSet<>()));
        config.behavior.shakeToStopEnabled = prefs.getBoolean("shake_to_stop_enabled", true);
        config.behavior.shakeThreshold = prefs.getFloat("shake_threshold", 12.0f);
        config.behavior.shakeTimeoutSeconds = prefs.getInt("shake_timeout_seconds", 30);
        config.behavior.mediaBehavior = prefs.getString("media_behavior", "ignore");
        config.behavior.duckingVolume = prefs.getInt("ducking_volume", 30);
        config.behavior.duckingFallbackStrategy = prefs.getString("ducking_fallback_strategy", "manual");
        config.behavior.delayBeforeReadout = prefs.getInt("delay_before_readout", 0);
        config.behavior.honourDoNotDisturb = prefs.getBoolean("honour_do_not_disturb", true);
        config.behavior.honourPhoneCalls = prefs.getBoolean("honour_phone_calls", true); // Add honour phone calls
        // Split audio mode: prefer new keys, fall back to legacy combined flag
        boolean legacyHonourAudioMode = prefs.getBoolean("honour_audio_mode", true);
        config.behavior.honourSilentMode = prefs.getBoolean("honour_silent_mode", legacyHonourAudioMode);
        config.behavior.honourVibrateMode = prefs.getBoolean("honour_vibrate_mode", legacyHonourAudioMode);
        config.behavior.honourAudioMode = legacyHonourAudioMode;
        config.behavior.persistentNotification = prefs.getBoolean("persistent_notification", false); // Add persistent notification
        config.behavior.notificationWhileReading = prefs.getBoolean("notification_while_reading", false); // Add notification while reading
        config.behavior.waveToStopEnabled = prefs.getBoolean("wave_to_stop_enabled", false);
        config.behavior.waveTimeoutSeconds = prefs.getInt("wave_timeout_seconds", 30);
        config.behavior.waveHoldDurationMs = prefs.getInt("wave_hold_duration_ms", 150); // Read as int, auto-converts to long
        config.behavior.pocketModeEnabled = prefs.getBoolean("pocket_mode_enabled", false);
        config.behavior.customAppNames = prefs.getString("custom_app_names", "[]");
        config.behavior.cooldownApps = prefs.getString("cooldown_apps", "[]");
        config.behavior.speechTemplate = prefs.getString("speech_template", "{app} notified you: {content}");
        config.behavior.contentCapMode = prefs.getString("content_cap_mode", "disabled");
        config.behavior.contentCapWordCount = prefs.getInt("content_cap_word_count", 6);
        config.behavior.contentCapSentenceCount = prefs.getInt("content_cap_sentence_count", 1);
        config.behavior.contentCapTimeLimit = prefs.getInt("content_cap_time_limit", 10);
        config.behavior.notificationDeduplication = prefs.getBoolean("notification_deduplication", false);
        config.behavior.dismissalMemoryEnabled = prefs.getBoolean("dismissal_memory_enabled", true);
        config.behavior.dismissalMemoryTimeout = prefs.getInt("dismissal_memory_timeout", 15);
        
        // Load general settings
        config.general.darkMode = prefs.getBoolean("dark_mode", true);
        config.general.autoStartOnBoot = prefs.getBoolean("auto_start_on_boot", false);
        config.general.batteryOptimizationDisabled = prefs.getBoolean("battery_optimization_disabled", false);
        config.general.aggressiveBackgroundProcessing = prefs.getBoolean("aggressive_background_processing", false);
        config.general.serviceRestartPolicy = prefs.getString("service_restart_policy", "periodic");

        // Load statistics from main prefs
        StatsSnapshot statsSnapshot = StatisticsManager.Companion.exportSnapshot(context);
        config.statistics.notificationsReceived = statsSnapshot.getNotificationsReceived();
        config.statistics.notificationsRead = statsSnapshot.getNotificationsRead();
        config.statistics.readoutsInterrupted = statsSnapshot.getReadoutsInterrupted();
        config.statistics.listenerRebinds = statsSnapshot.getListenerRebinds();
        config.statistics.listenerRebindsSkipped = statsSnapshot.getListenerRebindsSkipped();
        config.statistics.listenerRebindsRecovered = statsSnapshot.getListenerRebindsRecovered();
        config.statistics.logoTaps = statsSnapshot.getLogoTaps();
        config.statistics.filterReasons = new HashMap<>(statsSnapshot.getFilterReasons());
        config.statistics.appsRead = new HashSet<>(statsSnapshot.getAppsRead());
        
        // Create JSON structure
        JSONObject json = new JSONObject();
        
        // Metadata
        JSONObject metadata = new JSONObject();
        metadata.put("exportDate", config.exportDate);
        metadata.put("appVersion", config.appVersion);
        metadata.put("configVersion", config.configVersion);
        metadata.put("exportType", "SpeakThat_FullConfig");
        json.put("metadata", metadata);
        
        // Filter settings
        JSONObject filters = new JSONObject();
        filters.put("appListMode", config.filters.appListMode);
        filters.put("appList", new JSONArray(config.filters.appList));
        filters.put("appPrivateFlags", new JSONArray(config.filters.appPrivateFlags));
        filters.put("wordListMode", config.filters.wordListMode);
        filters.put("wordBlacklist", new JSONArray(config.filters.wordBlacklist));
        filters.put("wordBlacklistPrivate", new JSONArray(config.filters.wordBlacklistPrivate));
        filters.put("wordReplacements", config.filters.wordReplacements);
        filters.put("urlHandlingMode", config.filters.urlHandlingMode);
        filters.put("urlReplacementText", config.filters.urlReplacementText);
        filters.put("tidySpeechRemoveEmojis", config.filters.tidySpeechRemoveEmojis);
        // Media filtering settings
        filters.put("mediaFilteringEnabled", config.filters.mediaFilteringEnabled);
        filters.put("mediaFilterExceptedApps", new JSONArray(config.filters.mediaFilterExceptedApps));
        filters.put("mediaFilterExceptedAppsPrivate", new JSONArray(config.filters.mediaFilterExceptedAppsPrivate));
        filters.put("mediaFilterImportantKeywords", new JSONArray(config.filters.mediaFilterImportantKeywords));
        filters.put("mediaFilterImportantKeywordsPrivate", new JSONArray(config.filters.mediaFilterImportantKeywordsPrivate));
        filters.put("mediaFilteredApps", new JSONArray(config.filters.mediaFilteredApps));
        filters.put("mediaFilteredAppsPrivate", new JSONArray(config.filters.mediaFilteredAppsPrivate));
        // Persistent/silent filtering settings
        filters.put("persistentFilteringEnabled", config.filters.persistentFilteringEnabled);
        filters.put("filterPersistent", config.filters.filterPersistent);
        filters.put("filterSilent", config.filters.filterSilent);
        filters.put("filterForegroundServices", config.filters.filterForegroundServices);
        filters.put("filterLowPriority", config.filters.filterLowPriority);
        filters.put("filterSystemNotifications", config.filters.filterSystemNotifications);
        json.put("filters", filters);
        
        // Voice settings
        JSONObject voice = new JSONObject();
        voice.put("speechRate", config.voice.speechRate);
        voice.put("pitch", config.voice.pitch);
        voice.put("ttsVolume", config.voice.ttsVolume);
        voice.put("voiceName", config.voice.voiceName);
        voice.put("language", config.voice.language);
        voice.put("ttsLanguage", config.voice.ttsLanguage);           // NEW
        voice.put("languagePreset", config.voice.languagePreset);   // NEW
        voice.put("isCustomPreset", config.voice.isCustomPreset);   // NEW
        voice.put("advancedEnabled", config.voice.advancedEnabled); // NEW
        voice.put("audioUsage", config.voice.audioUsage);
        voice.put("contentType", config.voice.contentType);
        voice.put("ttsEngine", config.voice.ttsEngine);
        voice.put("speakerphoneEnabled", config.voice.speakerphoneEnabled);
        json.put("voice", voice);
        
        // Behavior settings
        JSONObject behavior = new JSONObject();
        behavior.put("notificationBehavior", config.behavior.notificationBehavior);
        behavior.put("priorityApps", new JSONArray(config.behavior.priorityApps));
        behavior.put("shakeToStopEnabled", config.behavior.shakeToStopEnabled);
        behavior.put("shakeThreshold", config.behavior.shakeThreshold);
        behavior.put("shakeTimeoutSeconds", config.behavior.shakeTimeoutSeconds);
        behavior.put("mediaBehavior", config.behavior.mediaBehavior);
        behavior.put("duckingVolume", config.behavior.duckingVolume);
        behavior.put("duckingFallbackStrategy", config.behavior.duckingFallbackStrategy);
        behavior.put("delayBeforeReadout", config.behavior.delayBeforeReadout);
        behavior.put("honourDoNotDisturb", config.behavior.honourDoNotDisturb);
        behavior.put("honourPhoneCalls", config.behavior.honourPhoneCalls); // Add honour phone calls
        behavior.put("honourSilentMode", config.behavior.honourSilentMode); // Split audio mode
        behavior.put("honourVibrateMode", config.behavior.honourVibrateMode); // Split audio mode
        behavior.put("honourAudioMode", config.behavior.honourAudioMode); // Legacy combined flag
        behavior.put("persistentNotification", config.behavior.persistentNotification); // Add persistent notification
        behavior.put("notificationWhileReading", config.behavior.notificationWhileReading); // Add notification while reading
        behavior.put("waveToStopEnabled", config.behavior.waveToStopEnabled);
        behavior.put("waveTimeoutSeconds", config.behavior.waveTimeoutSeconds);
        behavior.put("waveHoldDurationMs", (int) config.behavior.waveHoldDurationMs); // Cast long to int for JSON
        behavior.put("pocketModeEnabled", config.behavior.pocketModeEnabled);
        behavior.put("customAppNames", config.behavior.customAppNames);
        behavior.put("cooldownApps", config.behavior.cooldownApps);
        behavior.put("speechTemplate", config.behavior.speechTemplate);
        behavior.put("contentCapMode", config.behavior.contentCapMode);
        behavior.put("contentCapWordCount", config.behavior.contentCapWordCount);
        behavior.put("contentCapSentenceCount", config.behavior.contentCapSentenceCount);
        behavior.put("contentCapTimeLimit", config.behavior.contentCapTimeLimit);
        behavior.put("notificationDeduplication", config.behavior.notificationDeduplication);
        behavior.put("dismissalMemoryEnabled", config.behavior.dismissalMemoryEnabled);
        behavior.put("dismissalMemoryTimeout", config.behavior.dismissalMemoryTimeout);
        json.put("behavior", behavior);
        
        // General settings
        JSONObject general = new JSONObject();
        general.put("darkMode", config.general.darkMode);
        general.put("autoStartOnBoot", config.general.autoStartOnBoot);
        general.put("batteryOptimizationDisabled", config.general.batteryOptimizationDisabled);
        general.put("aggressiveBackgroundProcessing", config.general.aggressiveBackgroundProcessing);
        general.put("serviceRestartPolicy", config.general.serviceRestartPolicy);
        json.put("general", general);

        // Statistics
        JSONObject statistics = new JSONObject();
        statistics.put("notificationsReceived", config.statistics.notificationsReceived);
        statistics.put("notificationsRead", config.statistics.notificationsRead);
        statistics.put("readoutsInterrupted", config.statistics.readoutsInterrupted);
        statistics.put("listenerRebinds", config.statistics.listenerRebinds);
        statistics.put("listenerRebindsSkipped", config.statistics.listenerRebindsSkipped);
        statistics.put("listenerRebindsRecovered", config.statistics.listenerRebindsRecovered);
        statistics.put("logoTaps", config.statistics.logoTaps);
        statistics.put("filterReasons", new JSONObject(config.statistics.filterReasons));
        statistics.put("appsRead", new JSONArray(config.statistics.appsRead));
        json.put("statistics", statistics);

        if (includeRules) {
            try {
                String rulesExport = RuleConfigManager.exportRules(context);
                JSONObject rulesJson = new JSONObject(rulesExport);
                JSONArray rulesArray = rulesJson.optJSONArray("rules");
                if (rulesArray != null) {
                    json.put("rules", rulesArray);
                }
            } catch (Exception e) {
                InAppLogger.logError("FilterConfig", "Failed to append rules to export: " + e.getMessage());
            }
        }
        
        return json.toString(2); // Pretty print with 2-space indentation
    }
    
    /**
     * Import filter settings from JSON format
     */
    public static ImportResult importFilters(Context context, String jsonData) {
        try {
            JSONObject json = new JSONObject(jsonData);
            
            // Validate this is a SpeakThat filter config
            if (!json.has("metadata") || !json.has("filters")) {
                return new ImportResult(false, "Invalid file format: Missing required sections", 0);
            }
            
            JSONObject metadata = json.getJSONObject("metadata");
            if (!metadata.optString("exportType", "").equals("SpeakThat_FilterConfig")) {
                return new ImportResult(false, "Invalid file format: Not a SpeakThat filter configuration", 0);
            }
            
            // Check version compatibility (for future use)
            String importVersion = metadata.optString("configVersion", "1.0");
            if (!isVersionCompatible(importVersion)) {
                return new ImportResult(false, "Incompatible configuration version: " + importVersion, 0);
            }
            
            // Parse filter settings
            JSONObject filters = json.getJSONObject("filters");
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            
            int filtersImported = 0;
            
            // Import app list mode
            if (filters.has("appListMode")) {
                editor.putString(KEY_APP_LIST_MODE, filters.getString("appListMode"));
                filtersImported++;
            }
            
            // Import app list
            if (filters.has("appList")) {
                Set<String> appList = jsonArrayToStringSet(filters.getJSONArray("appList"));
                editor.putStringSet(KEY_APP_LIST, appList);
                filtersImported += appList.size();
            }
            
            // Import app private flags
            if (filters.has("appPrivateFlags")) {
                Set<String> appPrivateFlags = jsonArrayToStringSet(filters.getJSONArray("appPrivateFlags"));
                editor.putStringSet(KEY_APP_PRIVATE_FLAGS, appPrivateFlags);
                filtersImported += appPrivateFlags.size();
            }
            
            // Import word list mode (default to blacklist for backward compatibility)
            if (filters.has("wordListMode")) {
                editor.putString(KEY_WORD_LIST_MODE, filters.getString("wordListMode"));
                filtersImported++;
            } else {
                // If importing old config without wordListMode, default to blacklist
                editor.putString(KEY_WORD_LIST_MODE, "blacklist");
            }
            
            // Import word blacklist
            if (filters.has("wordBlacklist")) {
                Set<String> wordBlacklist = jsonArrayToStringSet(filters.getJSONArray("wordBlacklist"));
                editor.putStringSet(KEY_WORD_BLACKLIST, wordBlacklist);
                filtersImported += wordBlacklist.size();
            }
            
            // Import word blacklist private
            if (filters.has("wordBlacklistPrivate")) {
                Set<String> wordBlacklistPrivate = jsonArrayToStringSet(filters.getJSONArray("wordBlacklistPrivate"));
                editor.putStringSet(KEY_WORD_BLACKLIST_PRIVATE, wordBlacklistPrivate);
                filtersImported += wordBlacklistPrivate.size();
            }
            
            // Import word swaps
            if (filters.has("wordReplacements")) {
                String wordReplacements = filters.getString("wordReplacements");
                editor.putString(KEY_WORD_REPLACEMENTS, wordReplacements);
                if (!wordReplacements.isEmpty()) {
                    filtersImported += wordReplacements.split("\\|").length;
                }
            }
            
            // Import URL handling settings
            if (filters.has("urlHandlingMode")) {
                editor.putString(KEY_URL_HANDLING_MODE, filters.getString("urlHandlingMode"));
                filtersImported++;
            }
            
            if (filters.has("urlReplacementText")) {
                editor.putString(KEY_URL_REPLACEMENT_TEXT, filters.getString("urlReplacementText"));
                filtersImported++;
            }

            if (filters.has("tidySpeechRemoveEmojis")) {
                editor.putBoolean(KEY_TIDY_SPEECH_REMOVE_EMOJIS, filters.getBoolean("tidySpeechRemoveEmojis"));
                filtersImported++;
            }
            
            // Apply all changes
            editor.apply();
            
            // Log the import
            InAppLogger.log("FilterConfig", "Imported " + filtersImported + " filter settings from configuration");
            
            return new ImportResult(true, "Successfully imported " + filtersImported + " filter settings", filtersImported);
            
        } catch (JSONException e) {
            InAppLogger.logError("FilterConfig", "Import failed: " + e.getMessage());
            return new ImportResult(false, "Invalid JSON format: " + e.getMessage(), 0);
        } catch (Exception e) {
            InAppLogger.logError("FilterConfig", "Import error: " + e.getMessage());
            return new ImportResult(false, "Import failed: " + e.getMessage(), 0);
        }
    }
    
    /**
     * Import full configuration including all settings
     */
    public static ImportResult importFullConfiguration(Context context, String jsonData) {
        try {
            JSONObject json = new JSONObject(jsonData);
            
            // Validate this is a SpeakThat full config
            if (!json.has("metadata")) {
                return new ImportResult(false, "Invalid file format: Missing metadata section", 0);
            }
            
            JSONObject metadata = json.getJSONObject("metadata");
            String exportType = metadata.optString("exportType", "");
            if (!exportType.equals("SpeakThat_FullConfig") && !exportType.equals("SpeakThat_FilterConfig")) {
                return new ImportResult(false, "Invalid file format: Not a SpeakThat configuration", 0);
            }
            
            // Check version compatibility
            String importVersion = metadata.optString("configVersion", "1.0");
            if (!isVersionCompatible(importVersion)) {
                return new ImportResult(false, "Incompatible configuration version: " + importVersion, 0);
            }
            
            SharedPreferences.Editor mainEditor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            SharedPreferences.Editor voiceEditor = context.getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE).edit();
            
            int totalImported = 0;
            
            // Import filter settings (if present)
            if (json.has("filters")) {
                JSONObject filters = json.getJSONObject("filters");
                
                if (filters.has("appListMode")) {
                    mainEditor.putString(KEY_APP_LIST_MODE, filters.getString("appListMode"));
                    totalImported++;
                }
                
                if (filters.has("appList")) {
                    Set<String> appList = jsonArrayToStringSet(filters.getJSONArray("appList"));
                    mainEditor.putStringSet(KEY_APP_LIST, appList);
                    totalImported += appList.size();
                }
                
                if (filters.has("appPrivateFlags")) {
                    Set<String> appPrivateFlags = jsonArrayToStringSet(filters.getJSONArray("appPrivateFlags"));
                    mainEditor.putStringSet(KEY_APP_PRIVATE_FLAGS, appPrivateFlags);
                    totalImported += appPrivateFlags.size();
                }
                
                // Import word list mode (default to blacklist for backward compatibility)
                if (filters.has("wordListMode")) {
                    mainEditor.putString(KEY_WORD_LIST_MODE, filters.getString("wordListMode"));
                    totalImported++;
                } else {
                    // If importing old config without wordListMode, default to blacklist
                    mainEditor.putString(KEY_WORD_LIST_MODE, "blacklist");
                }
                
                if (filters.has("wordBlacklist")) {
                    Set<String> wordBlacklist = jsonArrayToStringSet(filters.getJSONArray("wordBlacklist"));
                    mainEditor.putStringSet(KEY_WORD_BLACKLIST, wordBlacklist);
                    totalImported += wordBlacklist.size();
                }
                
                if (filters.has("wordBlacklistPrivate")) {
                    Set<String> wordBlacklistPrivate = jsonArrayToStringSet(filters.getJSONArray("wordBlacklistPrivate"));
                    mainEditor.putStringSet(KEY_WORD_BLACKLIST_PRIVATE, wordBlacklistPrivate);
                    totalImported += wordBlacklistPrivate.size();
                }
                
                if (filters.has("wordReplacements")) {
                    String wordReplacements = filters.getString("wordReplacements");
                    mainEditor.putString(KEY_WORD_REPLACEMENTS, wordReplacements);
                    if (!wordReplacements.isEmpty()) {
                        totalImported += wordReplacements.split("\\|").length;
                    }
                }
                
                // Import URL handling settings
                if (filters.has("urlHandlingMode")) {
                    mainEditor.putString(KEY_URL_HANDLING_MODE, filters.getString("urlHandlingMode"));
                    totalImported++;
                }
                
                if (filters.has("urlReplacementText")) {
                    mainEditor.putString(KEY_URL_REPLACEMENT_TEXT, filters.getString("urlReplacementText"));
                    totalImported++;
                }

                if (filters.has("tidySpeechRemoveEmojis")) {
                    mainEditor.putBoolean(KEY_TIDY_SPEECH_REMOVE_EMOJIS, filters.getBoolean("tidySpeechRemoveEmojis"));
                    totalImported++;
                }
                
                // Import media filtering settings
                if (filters.has("mediaFilteringEnabled")) {
                    mainEditor.putBoolean("media_filtering_enabled", filters.getBoolean("mediaFilteringEnabled"));
                    totalImported++;
                }
                
                if (filters.has("mediaFilterExceptedApps")) {
                    Set<String> excepted = jsonArrayToStringSet(filters.getJSONArray("mediaFilterExceptedApps"));
                    mainEditor.putStringSet("media_filter_excepted_apps", excepted);
                    totalImported += excepted.size();
                }
                
                if (filters.has("mediaFilterExceptedAppsPrivate")) {
                    Set<String> exceptedPrivate = jsonArrayToStringSet(filters.getJSONArray("mediaFilterExceptedAppsPrivate"));
                    mainEditor.putStringSet("media_filter_excepted_apps_private", exceptedPrivate);
                    totalImported += exceptedPrivate.size();
                }
                
                if (filters.has("mediaFilterImportantKeywords")) {
                    Set<String> keywords = jsonArrayToStringSet(filters.getJSONArray("mediaFilterImportantKeywords"));
                    mainEditor.putStringSet("media_filter_important_keywords", keywords);
                    totalImported += keywords.size();
                }
                
                if (filters.has("mediaFilterImportantKeywordsPrivate")) {
                    Set<String> keywordsPrivate = jsonArrayToStringSet(filters.getJSONArray("mediaFilterImportantKeywordsPrivate"));
                    mainEditor.putStringSet("media_filter_important_keywords_private", keywordsPrivate);
                    totalImported += keywordsPrivate.size();
                }
                
                if (filters.has("mediaFilteredApps")) {
                    Set<String> filtered = jsonArrayToStringSet(filters.getJSONArray("mediaFilteredApps"));
                    mainEditor.putStringSet("media_filtered_apps", filtered);
                    totalImported += filtered.size();
                }
                
                if (filters.has("mediaFilteredAppsPrivate")) {
                    Set<String> filteredPrivate = jsonArrayToStringSet(filters.getJSONArray("mediaFilteredAppsPrivate"));
                    mainEditor.putStringSet("media_filtered_apps_private", filteredPrivate);
                    totalImported += filteredPrivate.size();
                }
                
                // Import persistent/silent filtering settings
                if (filters.has("persistentFilteringEnabled")) {
                    mainEditor.putBoolean("persistent_filtering_enabled", filters.getBoolean("persistentFilteringEnabled"));
                    totalImported++;
                }
                
                if (filters.has("filterPersistent")) {
                    mainEditor.putBoolean("filter_persistent", filters.getBoolean("filterPersistent"));
                    totalImported++;
                }
                
                if (filters.has("filterSilent")) {
                    mainEditor.putBoolean("filter_silent", filters.getBoolean("filterSilent"));
                    totalImported++;
                }
                
                if (filters.has("filterForegroundServices")) {
                    mainEditor.putBoolean("filter_foreground_services", filters.getBoolean("filterForegroundServices"));
                    totalImported++;
                }
                
                if (filters.has("filterLowPriority")) {
                    mainEditor.putBoolean("filter_low_priority", filters.getBoolean("filterLowPriority"));
                    totalImported++;
                }
                
                if (filters.has("filterSystemNotifications")) {
                    mainEditor.putBoolean("filter_system_notifications", filters.getBoolean("filterSystemNotifications"));
                    totalImported++;
                }
            }
            
            // Import voice settings (if present)
            if (json.has("voice")) {
                JSONObject voice = json.getJSONObject("voice");
                
                if (voice.has("speechRate")) {
                    voiceEditor.putFloat("speech_rate", (float) voice.getDouble("speechRate"));
                    totalImported++;
                }
                
                if (voice.has("pitch")) {
                    voiceEditor.putFloat("pitch", (float) voice.getDouble("pitch"));
                    totalImported++;
                }
                
                if (voice.has("ttsVolume")) {
                    voiceEditor.putFloat("tts_volume", (float) voice.getDouble("ttsVolume"));
                    totalImported++;
                }
                
                if (voice.has("voiceName")) {
                    voiceEditor.putString("voice_name", voice.getString("voiceName"));
                    totalImported++;
                }
                
                if (voice.has("language")) {
                    voiceEditor.putString("language", voice.getString("language"));
                    totalImported++;
                }
                
                if (voice.has("audioUsage")) {
                    voiceEditor.putInt("audio_usage", voice.getInt("audioUsage"));
                    totalImported++;
                }
                
                if (voice.has("contentType")) {
                    voiceEditor.putInt("content_type", voice.getInt("contentType"));
                    totalImported++;
                }
                
                // NEW PRESET FIELDS (with backwards compatibility)
                if (voice.has("ttsLanguage")) {
                    voiceEditor.putString("tts_language", voice.getString("ttsLanguage"));
                    totalImported++;
                }
                
                if (voice.has("languagePreset")) {
                    voiceEditor.putString("language_preset", voice.getString("languagePreset"));
                    totalImported++;
                } else {
                    // Backwards compatibility: If no preset is specified, try to detect from language setting
                    if (voice.has("language")) {
                        String language = voice.getString("language");
                        String ttsLanguage = voice.optString("ttsLanguage", "system");
                        String voiceName = voice.optString("voiceName", "");
                        
                        // Use LanguagePresetManager to find best matching preset
                        // This will be applied after preferences are saved
                        voiceEditor.putString("_legacy_import_language", language);
                        voiceEditor.putString("_legacy_import_tts_language", ttsLanguage);
                        voiceEditor.putString("_legacy_import_voice_name", voiceName);
                    }
                }
                
                if (voice.has("isCustomPreset")) {
                    voiceEditor.putBoolean("is_custom_preset", voice.getBoolean("isCustomPreset"));
                    totalImported++;
                }
                
                if (voice.has("advancedEnabled")) {
                    voiceEditor.putBoolean("show_advanced_voice", voice.getBoolean("advancedEnabled"));
                    totalImported++;
                }
                
                // Import TTS engine with graceful fallback
                if (voice.has("ttsEngine")) {
                    String ttsEngine = voice.getString("ttsEngine");
                    if (!ttsEngine.isEmpty()) {
                        // Check if the TTS engine is available on this device
                        boolean engineAvailable = isTtsEngineAvailable(context, ttsEngine);
                        if (engineAvailable) {
                            voiceEditor.putString("tts_engine_package", ttsEngine);
                            InAppLogger.log("FilterConfig", "Imported TTS engine: " + ttsEngine);
                        } else {
                            // Fall back to system default
                            voiceEditor.putString("tts_engine_package", "");
                            InAppLogger.log("FilterConfig", "TTS engine not available (" + ttsEngine + "), using system default");
                        }
                    } else {
                        voiceEditor.putString("tts_engine_package", "");
                    }
                    totalImported++;
                }
                
                if (voice.has("speakerphoneEnabled")) {
                    voiceEditor.putBoolean("speakerphone_enabled", voice.getBoolean("speakerphoneEnabled"));
                    totalImported++;
                }
            }
            
            // Import behavior settings (if present)
            if (json.has("behavior")) {
                JSONObject behavior = json.getJSONObject("behavior");
                
                if (behavior.has("notificationBehavior")) {
                    mainEditor.putString("notification_behavior", behavior.getString("notificationBehavior"));
                    totalImported++;
                }
                
                if (behavior.has("priorityApps")) {
                    Set<String> priorityApps = jsonArrayToStringSet(behavior.getJSONArray("priorityApps"));
                    mainEditor.putStringSet("priority_apps", priorityApps);
                    totalImported += priorityApps.size();
                }
                
                if (behavior.has("shakeToStopEnabled")) {
                    mainEditor.putBoolean("shake_to_stop_enabled", behavior.getBoolean("shakeToStopEnabled"));
                    totalImported++;
                }
                
                if (behavior.has("shakeThreshold")) {
                    mainEditor.putFloat("shake_threshold", (float) behavior.getDouble("shakeThreshold"));
                    totalImported++;
                }
                
                if (behavior.has("shakeTimeoutSeconds")) {
                    int timeout = behavior.getInt("shakeTimeoutSeconds");
                    // Safety validation: ensure timeout is within valid range (0 or 5-300)
                    if (timeout < 0 || (timeout > 0 && timeout < 5) || timeout > 300) {
                        timeout = 30; // Reset to safe default
                        InAppLogger.log("FilterConfig", "Invalid shake timeout value imported, reset to 30 seconds");
                    }
                    mainEditor.putInt("shake_timeout_seconds", timeout);
                    totalImported++;
                }
                
                if (behavior.has("mediaBehavior")) {
                    mainEditor.putString("media_behavior", behavior.getString("mediaBehavior"));
                    totalImported++;
                }
                
                if (behavior.has("duckingVolume")) {
                    mainEditor.putInt("ducking_volume", behavior.getInt("duckingVolume"));
                    totalImported++;
                }
                
                if (behavior.has("duckingFallbackStrategy")) {
                    mainEditor.putString("ducking_fallback_strategy", behavior.getString("duckingFallbackStrategy"));
                    totalImported++;
                }
                
                if (behavior.has("delayBeforeReadout")) {
                    mainEditor.putInt("delay_before_readout", behavior.getInt("delayBeforeReadout"));
                    totalImported++;
                }
                
                if (behavior.has("honourDoNotDisturb")) {
                    mainEditor.putBoolean("honour_do_not_disturb", behavior.getBoolean("honourDoNotDisturb"));
                    totalImported++;
                }
                
                if (behavior.has("honourPhoneCalls")) {
                    mainEditor.putBoolean("honour_phone_calls", behavior.getBoolean("honourPhoneCalls"));
                    totalImported++;
                }
                
                // Prefer split audio-mode flags; fall back to legacy combined flag
                if (behavior.has("honourSilentMode")) {
                    mainEditor.putBoolean("honour_silent_mode", behavior.getBoolean("honourSilentMode"));
                    totalImported++;
                }
                if (behavior.has("honourVibrateMode")) {
                    mainEditor.putBoolean("honour_vibrate_mode", behavior.getBoolean("honourVibrateMode"));
                    totalImported++;
                }
                if (behavior.has("honourAudioMode")) {
                    boolean legacyHonour = behavior.getBoolean("honourAudioMode");
                    mainEditor.putBoolean("honour_audio_mode", legacyHonour);
                    if (!behavior.has("honourSilentMode")) {
                        mainEditor.putBoolean("honour_silent_mode", legacyHonour);
                    }
                    if (!behavior.has("honourVibrateMode")) {
                        mainEditor.putBoolean("honour_vibrate_mode", legacyHonour);
                    }
                    totalImported++;
                }
                
                if (behavior.has("persistentNotification")) {
                    mainEditor.putBoolean("persistent_notification", behavior.getBoolean("persistentNotification"));
                    totalImported++;
                }
                
                if (behavior.has("notificationWhileReading")) {
                    mainEditor.putBoolean("notification_while_reading", behavior.getBoolean("notificationWhileReading"));
                    totalImported++;
                }
                
                if (behavior.has("waveToStopEnabled")) {
                    mainEditor.putBoolean("wave_to_stop_enabled", behavior.getBoolean("waveToStopEnabled"));
                    totalImported++;
                }
                
                if (behavior.has("waveTimeoutSeconds")) {
                    int timeout = behavior.getInt("waveTimeoutSeconds");
                    // Safety validation: ensure timeout is within valid range (0 or 5-300)
                    if (timeout < 0 || (timeout > 0 && timeout < 5) || timeout > 300) {
                        timeout = 30; // Reset to safe default
                        InAppLogger.log("FilterConfig", "Invalid wave timeout value imported, reset to 30 seconds");
                    }
                    mainEditor.putInt("wave_timeout_seconds", timeout);
                    totalImported++;
                }
                
                if (behavior.has("waveHoldDurationMs")) {
                    int holdDuration = behavior.getInt("waveHoldDurationMs");
                    // Safety validation
                    if (holdDuration < 50 || holdDuration > 1000) {
                        holdDuration = 150; // Reset to safe default
                        InAppLogger.log("FilterConfig", "Invalid wave hold duration imported, reset to 150ms");
                    }
                    mainEditor.putInt("wave_hold_duration_ms", holdDuration);
                    totalImported++;
                }
                
                if (behavior.has("pocketModeEnabled")) {
                    mainEditor.putBoolean("pocket_mode_enabled", behavior.getBoolean("pocketModeEnabled"));
                    totalImported++;
                }
                
                if (behavior.has("customAppNames")) {
                    mainEditor.putString("custom_app_names", behavior.getString("customAppNames"));
                    totalImported++;
                }
                
                if (behavior.has("cooldownApps")) {
                    mainEditor.putString("cooldown_apps", behavior.getString("cooldownApps"));
                    totalImported++;
                }
                
                if (behavior.has("speechTemplate")) {
                    mainEditor.putString("speech_template", behavior.getString("speechTemplate"));
                    totalImported++;
                }
                
                if (behavior.has("contentCapMode")) {
                    mainEditor.putString("content_cap_mode", behavior.getString("contentCapMode"));
                    totalImported++;
                }
                
                if (behavior.has("contentCapWordCount")) {
                    mainEditor.putInt("content_cap_word_count", behavior.getInt("contentCapWordCount"));
                    totalImported++;
                }
                
                if (behavior.has("contentCapSentenceCount")) {
                    mainEditor.putInt("content_cap_sentence_count", behavior.getInt("contentCapSentenceCount"));
                    totalImported++;
                }
                
                if (behavior.has("contentCapTimeLimit")) {
                    mainEditor.putInt("content_cap_time_limit", behavior.getInt("contentCapTimeLimit"));
                    totalImported++;
                }
                
                if (behavior.has("notificationDeduplication")) {
                    mainEditor.putBoolean("notification_deduplication", behavior.getBoolean("notificationDeduplication"));
                    totalImported++;
                }
                
                if (behavior.has("dismissalMemoryEnabled")) {
                    mainEditor.putBoolean("dismissal_memory_enabled", behavior.getBoolean("dismissalMemoryEnabled"));
                    totalImported++;
                }
                
                if (behavior.has("dismissalMemoryTimeout")) {
                    int timeout = behavior.getInt("dismissalMemoryTimeout");
                    // Safety validation
                    if (timeout < 1 || timeout > 120) {
                        timeout = 15; // Reset to safe default
                        InAppLogger.log("FilterConfig", "Invalid dismissal memory timeout imported, reset to 15 minutes");
                    }
                    mainEditor.putInt("dismissal_memory_timeout", timeout);
                    totalImported++;
                }
            }
            
            // Import general settings (if present)
            if (json.has("general")) {
                JSONObject general = json.getJSONObject("general");
                
                if (general.has("darkMode")) {
                    mainEditor.putBoolean("dark_mode", general.getBoolean("darkMode"));
                    totalImported++;
                }
                
                if (general.has("autoStartOnBoot")) {
                    mainEditor.putBoolean("auto_start_on_boot", general.getBoolean("autoStartOnBoot"));
                    totalImported++;
                }
                
                if (general.has("batteryOptimizationDisabled")) {
                    mainEditor.putBoolean("battery_optimization_disabled", general.getBoolean("batteryOptimizationDisabled"));
                    totalImported++;
                }
                
                if (general.has("aggressiveBackgroundProcessing")) {
                    mainEditor.putBoolean("aggressive_background_processing", general.getBoolean("aggressiveBackgroundProcessing"));
                    totalImported++;
                }
                
                if (general.has("serviceRestartPolicy")) {
                    mainEditor.putString("service_restart_policy", general.getString("serviceRestartPolicy"));
                    totalImported++;
                }
            }

            // Import statistics (if present) - overwrites current stats
            if (json.has("statistics")) {
                JSONObject statistics = json.getJSONObject("statistics");
                StatsSnapshot snapshot = StatisticsManager.Companion.snapshotFromJson(statistics);
                StatisticsManager.Companion.importSnapshot(context, snapshot);

                int statsImported = 0;
                if (statistics.has("notificationsReceived")) statsImported++;
                if (statistics.has("notificationsRead")) statsImported++;
                if (statistics.has("readoutsInterrupted")) statsImported++;
                if (statistics.has("listenerRebinds")) statsImported++;
                if (statistics.has("listenerRebindsSkipped")) statsImported++;
                if (statistics.has("listenerRebindsRecovered")) statsImported++;
                if (statistics.has("logoTaps")) statsImported++;

                JSONObject filterReasonsJson = statistics.optJSONObject("filterReasons");
                if (filterReasonsJson != null) {
                    statsImported += filterReasonsJson.length();
                } else if (statistics.has("filterReasons")) {
                    statsImported++;
                }

                JSONArray appsReadJson = statistics.optJSONArray("appsRead");
                if (appsReadJson != null) {
                    statsImported += appsReadJson.length();
                } else if (statistics.has("appsRead")) {
                    statsImported++;
                }

                totalImported += statsImported;
            }
            
            // Apply all changes
            mainEditor.apply();
            voiceEditor.apply();
            
            // Handle legacy import preset migration
            processLegacyImportPresets(context);
            
            // Log the import
            InAppLogger.log("FilterConfig", "Imported " + totalImported + " settings from full configuration");
            
            return new ImportResult(true, "Successfully imported " + totalImported + " settings from full configuration", totalImported);
            
        } catch (JSONException e) {
            InAppLogger.logError("FilterConfig", "Full import failed: " + e.getMessage());
            return new ImportResult(false, "Invalid JSON format: " + e.getMessage(), 0);
        } catch (Exception e) {
            InAppLogger.logError("FilterConfig", "Full import error: " + e.getMessage());
            return new ImportResult(false, "Import failed: " + e.getMessage(), 0);
        }
    }
    
    /**
     * Get a summary of current filter settings for display
     */
    public static String getFilterSummary(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        StringBuilder summary = new StringBuilder();
        
        // App filtering
        String appMode = prefs.getString(KEY_APP_LIST_MODE, "none");
        Set<String> appList = prefs.getStringSet(KEY_APP_LIST, new HashSet<>());
        Set<String> privateApps = prefs.getStringSet(KEY_APP_PRIVATE_FLAGS, new HashSet<>());
        
        summary.append("📱 App Filtering: ").append(appMode);
        if (!appList.isEmpty()) {
            summary.append(" (").append(appList.size()).append(" apps)");
        }
        if (!privateApps.isEmpty()) {
            summary.append(", ").append(privateApps.size()).append(" private apps");
        }
        summary.append("\n");
        
        // Word filtering
        String wordMode = prefs.getString(KEY_WORD_LIST_MODE, "blacklist");
        Set<String> blockedWords = prefs.getStringSet(KEY_WORD_BLACKLIST, new HashSet<>());
        Set<String> privateWords = prefs.getStringSet(KEY_WORD_BLACKLIST_PRIVATE, new HashSet<>());
        String replacements = prefs.getString(KEY_WORD_REPLACEMENTS, "");
        
        summary.append("🚫 Word Filtering: ").append(wordMode);
        if (!blockedWords.isEmpty() || !privateWords.isEmpty()) {
            summary.append(" (").append(blockedWords.size() + privateWords.size()).append(" words)");
        }
        if (!privateWords.isEmpty()) {
            summary.append(", ").append(privateWords.size()).append(" private");
        }
        if (!replacements.isEmpty()) {
            int replacementCount = replacements.split("\\|").length;
            summary.append(", ").append(replacementCount).append(" replacements");
        }
        
        return summary.toString();
    }
    
    private static Set<String> jsonArrayToStringSet(JSONArray jsonArray) throws JSONException {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            set.add(jsonArray.getString(i));
        }
        return set;
    }
    
    private static boolean isVersionCompatible(String version) {
        // For now, all 1.x versions are compatible
        // In the future, we can add more sophisticated version checking
        return version.startsWith("1.");
    }
    
    /**
     * Process legacy import settings and migrate them to the new preset system
     */
    private static void processLegacyImportPresets(Context context) {
        SharedPreferences voicePrefs = context.getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE);
        
        // Check if we have legacy import markers
        if (voicePrefs.contains("_legacy_import_language")) {
            String language = voicePrefs.getString("_legacy_import_language", "en_US");
            String ttsLanguage = voicePrefs.getString("_legacy_import_tts_language", "system");
            String voiceName = voicePrefs.getString("_legacy_import_voice_name", "");
            
            InAppLogger.log("FilterConfig", "Processing legacy import - Language: " + language + 
                           ", TTS: " + ttsLanguage + ", Voice: " + voiceName);
            
            // Use LanguagePresetManager to find the best matching preset
            LanguagePresetManager.LanguagePreset bestMatch = 
                LanguagePresetManager.findBestMatch(language, ttsLanguage, voiceName);
            
            // Save the detected preset
            SharedPreferences.Editor editor = voicePrefs.edit();
            editor.putString("language_preset", bestMatch.id);
            editor.putBoolean("is_custom_preset", bestMatch.isCustom);
            
            // Clean up the legacy markers
            editor.remove("_legacy_import_language");
            editor.remove("_legacy_import_tts_language");
            editor.remove("_legacy_import_voice_name");
            
            editor.apply();
            
            InAppLogger.log("FilterConfig", "Legacy import migrated to preset: " + bestMatch.displayName + 
                           " (custom: " + bestMatch.isCustom + ")");
        }
    }
    
    /**
     * Check if a TTS engine is available on this device
     * @param context Application context
     * @param enginePackage Package name of the TTS engine
     * @return true if the engine is installed and available, false otherwise
     */
    private static boolean isTtsEngineAvailable(Context context, String enginePackage) {
        if (enginePackage == null || enginePackage.isEmpty()) {
            return false;
        }
        
        try {
            // Check if the package is installed
            context.getPackageManager().getPackageInfo(enginePackage, 0);
            
            // Additionally check if it's a valid TTS engine by checking available engines
            android.speech.tts.TextToSpeech.EngineInfo[] engines = 
                new android.speech.tts.TextToSpeech(context, null).getEngines().toArray(
                    new android.speech.tts.TextToSpeech.EngineInfo[0]);
            
            for (android.speech.tts.TextToSpeech.EngineInfo engine : engines) {
                if (engine.name.equals(enginePackage)) {
                    return true;
                }
            }
            
            // Package exists but isn't a TTS engine
            return false;
        } catch (Exception e) {
            // Package not found or other error
            InAppLogger.log("FilterConfig", "TTS engine check failed for " + enginePackage + ": " + e.getMessage());
            return false;
        }
    }
} 