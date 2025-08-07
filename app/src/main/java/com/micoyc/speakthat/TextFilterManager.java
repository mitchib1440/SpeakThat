package com.micoyc.speakthat;

import android.content.Context;
import android.content.SharedPreferences;
import android.speech.tts.Voice;
import android.util.Log;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Manages filtering of multilingual text based on voice capabilities and user preferences.
 * This prevents garbled speech when text contains characters the selected voice cannot pronounce.
 */
public class TextFilterManager {
    
    private static final String TAG = "TextFilterManager";
    
    // Filter modes
    public static final int FILTER_MODE_SKIP = 0;
    public static final int FILTER_MODE_REPLACE = 1;
    public static final int FILTER_MODE_ATTEMPT = 2;
    
    // Character patterns for different scripts
    private static final Pattern LATIN_PATTERN = Pattern.compile("[a-zA-Z0-9\\s\\p{Punct}]+");
    private static final Pattern HIRAGANA_PATTERN = Pattern.compile("[\\p{Hiragana}]+");
    private static final Pattern KATAKANA_PATTERN = Pattern.compile("[\\p{Katakana}]+");
    private static final Pattern HAN_PATTERN = Pattern.compile("[\\p{Han}]+");
    private static final Pattern HANGUL_PATTERN = Pattern.compile("[\\p{Hangul}]+");
    private static final Pattern ARABIC_PATTERN = Pattern.compile("[\\p{Arabic}]+");
    private static final Pattern CYRILLIC_PATTERN = Pattern.compile("[\\p{Cyrillic}]+");
    private static final Pattern THAI_PATTERN = Pattern.compile("[\\p{Thai}]+");
    private static final Pattern DEVANAGARI_PATTERN = Pattern.compile("[\\p{Devanagari}]+");
    
    /**
     * Filter text based on the selected voice and user preferences
     */
    public static String filterTextForVoice(String text, Voice voice, Context context) {
        if (text == null || text.isEmpty() || voice == null) {
            return text;
        }
        
        // Get user's filter preference
        SharedPreferences prefs = context.getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE);
        int filterMode = prefs.getInt("multilingual_filter", FILTER_MODE_SKIP);
        
        // Get voice language
        String voiceLanguage = getVoiceLanguage(voice);
        
        Log.d(TAG, "Text filtering - Voice: " + voice.getName() + ", Language: " + voiceLanguage + ", Filter mode: " + getFilterModeName(filterMode));
        
        // Check if text contains unsupported characters
        if (isTextCompatibleWithVoice(text, voiceLanguage)) {
            Log.d(TAG, "Text is compatible with voice - no filtering needed");
            return text; // No filtering needed
        }
        
        Log.d(TAG, "Text contains unsupported characters for voice language: " + voiceLanguage);
        
        // Apply filtering based on user preference
        switch (filterMode) {
            case FILTER_MODE_SKIP:
                return filterTextSkip(text, voiceLanguage);
            case FILTER_MODE_REPLACE:
                return filterTextReplace(text, voiceLanguage);
            case FILTER_MODE_ATTEMPT:
                Log.d(TAG, "Attempt mode: letting TTS try to pronounce unsupported text (voice engine may still skip some text)");
                return text; // Don't filter, let TTS attempt
            default:
                return filterTextSkip(text, voiceLanguage);
        }
    }
    
    /**
     * Check if text is compatible with the given voice language
     */
    private static boolean isTextCompatibleWithVoice(String text, String voiceLanguage) {
        // Get supported character pattern for the voice language
        Pattern supportedPattern = getSupportedPatternForLanguage(voiceLanguage);
        
        // Check if all characters in the text are supported
        return supportedPattern.matcher(text).matches();
    }
    
    /**
     * Filter text by removing unsupported characters
     */
    private static String filterTextSkip(String text, String voiceLanguage) {
        Pattern supportedPattern = getSupportedPatternForLanguage(voiceLanguage);
        
        // Split text into words and keep only supported words
        String[] words = text.split("\\s+");
        StringBuilder filteredText = new StringBuilder();
        
        for (String word : words) {
            if (supportedPattern.matcher(word).matches()) {
                if (filteredText.length() > 0) {
                    filteredText.append(" ");
                }
                filteredText.append(word);
            }
        }
        
        String result = filteredText.toString().trim();
        Log.d(TAG, "Filtered text (skip mode): '" + text + "' -> '" + result + "'");
        return result;
    }
    
    /**
     * Filter text by replacing unsupported characters with placeholder
     * Groups consecutive unsupported text into single [unreadable] markers
     */
    private static String filterTextReplace(String text, String voiceLanguage) {
        Pattern supportedPattern = getSupportedPatternForLanguage(voiceLanguage);
        
        // Split text into words
        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();
        boolean inUnreadableSection = false;
        
        for (String word : words) {
            if (supportedPattern.matcher(word).matches()) {
                // Supported word - add it and reset unreadable flag
                if (inUnreadableSection) {
                    result.append(" [unreadable]");
                    inUnreadableSection = false;
                }
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(word);
            } else {
                // Unsupported word - mark as unreadable section
                inUnreadableSection = true;
            }
        }
        
        // Add final [unreadable] if we ended in an unreadable section
        if (inUnreadableSection) {
            result.append(" [unreadable]");
        }
        
        String finalResult = result.toString().trim();
        Log.d(TAG, "Filtered text (replace mode): '" + text + "' -> '" + finalResult + "'");
        return finalResult;
    }
    
    /**
     * Get the language code from a voice
     */
    private static String getVoiceLanguage(Voice voice) {
        if (voice.getLocale() != null) {
            return voice.getLocale().getLanguage();
        }
        
        // Fallback: try to extract from voice name
        String voiceName = voice.getName().toLowerCase();
        if (voiceName.contains("en-")) return "en";
        if (voiceName.contains("ja-")) return "ja";
        if (voiceName.contains("ko-")) return "ko";
        if (voiceName.contains("zh-")) return "zh";
        if (voiceName.contains("ar-")) return "ar";
        if (voiceName.contains("ru-")) return "ru";
        if (voiceName.contains("th-")) return "th";
        if (voiceName.contains("hi-")) return "hi";
        
        return "en"; // Default to English
    }
    
    /**
     * Get the supported character pattern for a given language
     */
    private static Pattern getSupportedPatternForLanguage(String language) {
        switch (language.toLowerCase()) {
            case "ja": // Japanese
                return Pattern.compile("[\\p{Hiragana}\\p{Katakana}\\p{Han}a-zA-Z0-9\\s\\p{Punct}]+");
            case "ko": // Korean
                return Pattern.compile("[\\p{Hangul}a-zA-Z0-9\\s\\p{Punct}]+");
            case "zh": // Chinese
                return Pattern.compile("[\\p{Han}a-zA-Z0-9\\s\\p{Punct}]+");
            case "ar": // Arabic
                return Pattern.compile("[\\p{Arabic}a-zA-Z0-9\\s\\p{Punct}]+");
            case "ru": // Russian
                return Pattern.compile("[\\p{Cyrillic}a-zA-Z0-9\\s\\p{Punct}]+");
            case "th": // Thai
                return Pattern.compile("[\\p{Thai}a-zA-Z0-9\\s\\p{Punct}]+");
            case "hi": // Hindi
                return Pattern.compile("[\\p{Devanagari}a-zA-Z0-9\\s\\p{Punct}]+");
            default: // Latin-based languages (English, Spanish, French, etc.)
                return LATIN_PATTERN;
        }
    }
    
    /**
     * Get filter mode name for logging
     */
    public static String getFilterModeName(int filterMode) {
        switch (filterMode) {
            case FILTER_MODE_SKIP:
                return "Skip";
            case FILTER_MODE_REPLACE:
                return "Replace";
            case FILTER_MODE_ATTEMPT:
                return "Attempt";
            default:
                return "Unknown";
        }
    }
}
