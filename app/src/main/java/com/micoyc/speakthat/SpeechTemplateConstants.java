package com.micoyc.speakthat;

/**
 * Centralized constants for speech template handling so Activity and Service stay in sync.
 */
public final class SpeechTemplateConstants {

    private SpeechTemplateConstants() {
        // Utility class
    }

    public static final String KEY_SPEECH_TEMPLATE = "speech_template";
    public static final String KEY_SPEECH_TEMPLATE_KEY = "speech_template_key";

    public static final String TEMPLATE_KEY_VARIED = "VARIED";
    public static final String TEMPLATE_KEY_CUSTOM = "CUSTOM";
    public static final String DEFAULT_TEMPLATE_KEY = "tts_format_default";

    /**
     * Template keys that map directly to localized string resources (excludes VARIED/CUSTOM).
     */
    public static final String[] RESOURCE_TEMPLATE_KEYS = {
        "tts_format_default",
        "tts_format_minimal",
        "tts_format_formal",
        "tts_format_casual",
        "tts_format_time_aware",
        "tts_format_content_only",
        "tts_format_app_only"
    };
}

