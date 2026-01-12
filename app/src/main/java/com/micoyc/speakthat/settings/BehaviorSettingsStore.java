package com.micoyc.speakthat.settings;

import android.content.Context;
import android.content.SharedPreferences;

import com.micoyc.speakthat.SpeechTemplateConstants;

public class BehaviorSettingsStore {
    public static final String PREFS_NAME = "SpeakThatPrefs";

    // SharedPreferences keys
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_NOTIFICATION_BEHAVIOR = "notification_behavior";
    public static final String KEY_PRIORITY_APPS = "priority_apps";
    public static final String KEY_SHAKE_TO_STOP_ENABLED = "shake_to_stop_enabled";
    public static final String KEY_SHAKE_THRESHOLD = "shake_threshold";
    public static final String KEY_SHAKE_TIMEOUT_SECONDS = "shake_timeout_seconds";
    public static final String KEY_WAVE_TO_STOP_ENABLED = "wave_to_stop_enabled";
    public static final String KEY_WAVE_THRESHOLD = "wave_threshold";
    public static final String KEY_WAVE_TIMEOUT_SECONDS = "wave_timeout_seconds";
    public static final String KEY_PRESS_TO_STOP_ENABLED = "press_to_stop_enabled";
    public static final String KEY_POCKET_MODE_ENABLED = "pocket_mode_enabled";
    public static final String KEY_MEDIA_BEHAVIOR = "media_behavior";
    public static final String KEY_DUCKING_VOLUME = "ducking_volume";
    public static final String KEY_DELAY_BEFORE_READOUT = "delay_before_readout";
    public static final String KEY_CUSTOM_APP_NAMES = "custom_app_names";
    public static final String KEY_COOLDOWN_APPS = "cooldown_apps";
    public static final String KEY_HONOUR_DO_NOT_DISTURB = "honour_do_not_disturb";
    public static final String KEY_HONOUR_PHONE_CALLS = "honour_phone_calls";
    public static final String KEY_HONOUR_SILENT_MODE = "honour_silent_mode";
    public static final String KEY_HONOUR_VIBRATE_MODE = "honour_vibrate_mode";
    public static final String KEY_NOTIFICATION_DEDUPLICATION = "notification_deduplication";
    public static final String KEY_DISMISSAL_MEMORY_ENABLED = "dismissal_memory_enabled";
    public static final String KEY_DISMISSAL_MEMORY_TIMEOUT = "dismissal_memory_timeout";

    // Content Cap settings
    public static final String KEY_CONTENT_CAP_MODE = "content_cap_mode";
    public static final String KEY_CONTENT_CAP_WORD_COUNT = "content_cap_word_count";
    public static final String KEY_CONTENT_CAP_SENTENCE_COUNT = "content_cap_sentence_count";
    public static final String KEY_CONTENT_CAP_TIME_LIMIT = "content_cap_time_limit";

    public static final String KEY_SPEECH_TEMPLATE = "speech_template";
    public static final String KEY_SPEECH_TEMPLATE_KEY = SpeechTemplateConstants.KEY_SPEECH_TEMPLATE_KEY;

    // Calibration prefs keys
    public static final String PREFS_BEHAVIOR_SETTINGS = "BehaviorSettings";
    public static final String KEY_WAVE_THRESHOLD_PERCENT = "wave_threshold_percent";
    public static final String KEY_SENSOR_MAX_RANGE = "sensor_max_range_v1";
    public static final String KEY_WAVE_THRESHOLD_V1 = "wave_threshold_v1";
    public static final String KEY_CALIBRATION_TIMESTAMP = "calibration_timestamp_v1";

    public static final String KEY_DUCKING_FALLBACK_STRATEGY = "ducking_fallback_strategy";

    // Default values
    public static final String DEFAULT_NOTIFICATION_BEHAVIOR = "smart";
    public static final int DEFAULT_DUCKING_VOLUME = 30;
    public static final int DEFAULT_DELAY_BEFORE_READOUT = 2;
    public static final boolean DEFAULT_HONOUR_DO_NOT_DISTURB = true;
    public static final boolean DEFAULT_HONOUR_PHONE_CALLS = true;
    public static final boolean DEFAULT_HONOUR_SILENT_MODE = true;
    public static final boolean DEFAULT_HONOUR_VIBRATE_MODE = true;
    public static final boolean DEFAULT_NOTIFICATION_DEDUPLICATION = false;
    public static final boolean DEFAULT_DISMISSAL_MEMORY_ENABLED = true;
    public static final int DEFAULT_DISMISSAL_MEMORY_TIMEOUT = 15;

    public static final String DEFAULT_CONTENT_CAP_MODE = "disabled";
    public static final int DEFAULT_CONTENT_CAP_WORD_COUNT = 6;
    public static final int DEFAULT_CONTENT_CAP_SENTENCE_COUNT = 1;
    public static final int DEFAULT_CONTENT_CAP_TIME_LIMIT = 10;

    public static final String DEFAULT_SPEECH_TEMPLATE = "{app} notified you: {content}";

    private final SharedPreferences prefs;
    private final SharedPreferences behaviorPrefs;

    public BehaviorSettingsStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        behaviorPrefs = context.getSharedPreferences(PREFS_BEHAVIOR_SETTINGS, Context.MODE_PRIVATE);
    }

    public SharedPreferences prefs() {
        return prefs;
    }

    public SharedPreferences behaviorPrefs() {
        return behaviorPrefs;
    }

    public void trackDialogUsage(String dialogType) {
        String key = "dialog_usage_" + dialogType;
        int currentCount = prefs.getInt(key, 0);
        prefs.edit().putInt(key, currentCount + 1).apply();

        int totalUsage = prefs.getInt("total_dialog_usage", 0);
        prefs.edit().putInt("total_dialog_usage", totalUsage + 1).apply();

        prefs.edit().putLong("last_dialog_usage", System.currentTimeMillis()).apply();
    }
}
