package com.micoyc.speakthat;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages TTS language selection and ensures only languages with actual translations are available.
 * This prevents the "fraud" issue of showing languages that don't have proper translations.
 */
public class TtsLanguageManager {
    
    private static final String TAG = "TtsLanguageManager";
    
    /**
     * Represents a supported TTS language with its display name and locale
     */
    public static class TtsLanguage {
        public final String displayName;
        public final String localeCode;
        public final Locale locale;
        
        public TtsLanguage(String displayName, String localeCode, Locale locale) {
            this.displayName = displayName;
            this.localeCode = localeCode;
            this.locale = locale;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    /**
     * Get all supported TTS languages that have actual translations
     * This list should only include languages that have complete string translations
     */
    public static List<TtsLanguage> getSupportedTtsLanguages(Context context) {
        List<TtsLanguage> languages = new ArrayList<>();
        
        // Add default option first
        languages.add(new TtsLanguage("Default (Use System Language)", "system", null));
        
        // Add languages that have actual translations
        // English (using GB spelling as default since you're British)
        languages.add(new TtsLanguage("English (United Kingdom)", "en_GB", locale("en-GB")));
        
        // German (has complete translations)
        languages.add(new TtsLanguage("Deutsch (Deutschland)", "de_DE", locale("de-DE")));
        
        // Italian (has complete translations)
        languages.add(new TtsLanguage("Italiano (Italia)", "it_IT", locale("it-IT")));
        
        // Swedish (has complete translations)
        languages.add(new TtsLanguage("Svenska (Sverige)", "sv_SE", locale("sv-SE")));
        
        // Portuguese (has complete translations)
        languages.add(new TtsLanguage("Português (Brasil)", "pt_BR", locale("pt-BR")));
        
        // Spanish (has complete translations)
        languages.add(new TtsLanguage("Español (España)", "es_ES", locale("es-ES")));
        
        // French (has complete translations)
        languages.add(new TtsLanguage("Français (France)", "fr_FR", locale("fr-FR")));
        
        // Arabic (has complete translations - RTL language)
        languages.add(new TtsLanguage("العربية (المملكة العربية السعودية)", "ar_SA", locale("ar-SA")));
        
        // Japanese (has complete translations - SOV word order)
        languages.add(new TtsLanguage("日本語 (日本)", "ja_JP", locale("ja-JP")));
        
        // Korean (has complete translations - SOV word order)
        languages.add(new TtsLanguage("한국어 (대한민국)", "ko_KR", locale("ko-KR")));
        
        // Chinese Simplified (has complete translations)
        languages.add(new TtsLanguage("中文 (简体)", "zh_CN", locale("zh-CN")));
        
        // Chinese Traditional (has complete translations)
        languages.add(new TtsLanguage("中文 (繁體)", "zh_TW", locale("zh-TW")));
        
        // Russian (has complete translations)
        languages.add(new TtsLanguage("Русский (Россия)", "ru_RU", locale("ru-RU")));
        
        // Thai (has complete translations)
        languages.add(new TtsLanguage("ไทย (ประเทศไทย)", "th_TH", locale("th-TH")));
        
        // Vietnamese (has complete translations)
        languages.add(new TtsLanguage("Tiếng Việt (Việt Nam)", "vi_VN", locale("vi-VN")));
        
        // Hindi (has complete translations - SOV word order, Devanagari script)
        languages.add(new TtsLanguage("हिन्दी (भारत)", "hi_IN", locale("hi-IN")));
        
        // Indonesian (has complete translations)
        languages.add(new TtsLanguage("Bahasa Indonesia (Indonesia)", "id_ID", locale("id-ID")));
        
        // Turkish (has complete translations - agglutinative language)
        languages.add(new TtsLanguage("Türkçe (Türkiye)", "tr_TR", locale("tr-TR")));
        
        // Polish (has complete translations - complex grammar)
        languages.add(new TtsLanguage("Polski (Polska)", "pl_PL", locale("pl-PL")));
        
        // Punjabi (has complete translations - Gurmukhi script)
        languages.add(new TtsLanguage("ਪੰਜਾਬੀ (ਭਾਰਤ)", "pa_IN", locale("pa-IN")));
        
        // Dutch (has complete translations)
        languages.add(new TtsLanguage("Nederlands (Nederland)", "nl_NL", locale("nl-NL")));
        
        // Note: Add more languages here ONLY when they have complete translations
        // This prevents the "fraud" issue of showing languages without proper translations
        
        InAppLogger.log(TAG, "Supported TTS languages: " + languages.size() + " languages available");
        return languages;
    }
    
    /**
     * Get the display name for a language code
     */
    public static String getLanguageDisplayName(String languageCode) {
        if ("system".equals(languageCode)) {
            return "Default (Use System Language)";
        }
        
        for (TtsLanguage lang : getSupportedTtsLanguages(null)) {
            if (lang.localeCode.equals(languageCode)) {
                return lang.displayName;
            }
        }
        
        return "Unknown Language";
    }
    
    /**
     * Get the locale for a language code
     */
    public static Locale getLocaleForLanguage(String languageCode) {
        if ("system".equals(languageCode)) {
            return Locale.getDefault();
        }
        
        for (TtsLanguage lang : getSupportedTtsLanguages(null)) {
            if (lang.localeCode.equals(languageCode)) {
                return lang.locale;
            }
        }
        
        return Locale.getDefault();
    }
    
    /**
     * Check if a language has complete translations by testing key TTS strings
     */
    public static boolean hasCompleteTranslations(Context context, String languageCode) {
        if ("system".equals(languageCode)) {
            return true; // System language is always available
        }
        
        try {
            // Create a configuration for the target language
            Configuration config = new Configuration(context.getResources().getConfiguration());
            Locale targetLocale = getLocaleForLanguage(languageCode);
            if (targetLocale != null) {
                config.setLocale(targetLocale);
            }
            
            // Create a new Resources instance with the target language
            Context targetContext = context.createConfigurationContext(config);
            Resources targetResources = targetContext.getResources();
            
            // Test key TTS strings to ensure they exist and are translated
            String[] testStrings = {
                "tts_template_notified_you",
                "tts_about_intro", 
                "tts_voice_test",
                "tts_onboarding_welcome",
                "tts_onboarding_language_theme"
            };
            
            for (String stringName : testStrings) {
                int resourceId = targetResources.getIdentifier(stringName, "string", context.getPackageName());
                if (resourceId == 0) {
                    InAppLogger.log(TAG, "Missing translation for " + stringName + " in " + languageCode);
                    return false;
                }
                
                String translatedString = targetResources.getString(resourceId);
                if (translatedString == null || translatedString.trim().isEmpty()) {
                    InAppLogger.log(TAG, "Empty translation for " + stringName + " in " + languageCode);
                    return false;
                }
            }
            
            InAppLogger.log(TAG, "Complete translations verified for " + languageCode);
            return true;
            
        } catch (Exception e) {
            InAppLogger.log(TAG, "Error checking translations for " + languageCode + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get a localized TTS string based on the user's TTS language setting
     */
    public static String getLocalizedTtsString(Context context, String ttsLanguageCode, int stringResourceId) {
        if ("system".equals(ttsLanguageCode)) {
            // Use system language
            return context.getString(stringResourceId);
        }
        
        try {
            // Create a configuration for the target language
            Configuration config = new Configuration(context.getResources().getConfiguration());
            Locale targetLocale = getLocaleForLanguage(ttsLanguageCode);
            if (targetLocale != null) {
                config.setLocale(targetLocale);
            }
            
            // Create a new Resources instance with the target language
            Context targetContext = context.createConfigurationContext(config);
            Resources targetResources = targetContext.getResources();
            
            return targetResources.getString(stringResourceId);
            
        } catch (Exception e) {
            InAppLogger.log(TAG, "Error getting localized string for " + ttsLanguageCode + ": " + e.getMessage());
            // Fallback to system language
            return context.getString(stringResourceId);
        }
    }
    
    /**
     * Get a localized TTS string by name (for dynamic string access)
     */
    public static String getLocalizedTtsStringByName(Context context, String ttsLanguageCode, String stringName) {
        if ("system".equals(ttsLanguageCode)) {
            // Use system language
            int resourceId = context.getResources().getIdentifier(stringName, "string", context.getPackageName());
            return resourceId != 0 ? context.getString(resourceId) : stringName;
        }
        
        try {
            // Create a configuration for the target language
            Configuration config = new Configuration(context.getResources().getConfiguration());
            Locale targetLocale = getLocaleForLanguage(ttsLanguageCode);
            if (targetLocale != null) {
                config.setLocale(targetLocale);
            }
            
            // Create a new Resources instance with the target language
            Context targetContext = context.createConfigurationContext(config);
            Resources targetResources = targetContext.getResources();
            
            int resourceId = targetResources.getIdentifier(stringName, "string", context.getPackageName());
            if (resourceId != 0) {
                return targetResources.getString(resourceId);
            } else {
                InAppLogger.log(TAG, "String not found: " + stringName + " in " + ttsLanguageCode);
                // Fallback to system language
                int fallbackResourceId = context.getResources().getIdentifier(stringName, "string", context.getPackageName());
                return fallbackResourceId != 0 ? context.getString(fallbackResourceId) : stringName;
            }
            
        } catch (Exception e) {
            InAppLogger.log(TAG, "Error getting localized string by name for " + ttsLanguageCode + ": " + e.getMessage());
            // Fallback to system language
            int fallbackResourceId = context.getResources().getIdentifier(stringName, "string", context.getPackageName());
            return fallbackResourceId != 0 ? context.getString(fallbackResourceId) : stringName;
        }
    }
    
    /**
     * Attempt to find which string resource key matches a stored localized value by checking
     * the base resources plus every supported TTS language variant.
     */
    public static String findMatchingStringKey(Context context, String storedValue, String[] candidateKeys) {
        if (context == null || storedValue == null || candidateKeys == null || candidateKeys.length == 0) {
            return null;
        }
        
        // First check system/default resources
        for (String key : candidateKeys) {
            String baseString = getLocalizedTtsStringByName(context, "system", key);
            if (storedValue.equals(baseString)) {
                return key;
            }
        }
        
        // Check each supported TTS language
        List<TtsLanguage> languages = getSupportedTtsLanguages(context);
        for (TtsLanguage language : languages) {
            if (language == null || language.localeCode == null || "system".equals(language.localeCode)) {
                continue;
            }
            
            for (String key : candidateKeys) {
                String localizedString = getLocalizedTtsStringByName(context, language.localeCode, key);
                if (storedValue.equals(localizedString)) {
                    return key;
                }
            }
        }
        
        return null;
    }
    
    private static Locale locale(String languageTag) {
        if (languageTag == null || languageTag.isEmpty()) {
            return Locale.getDefault();
        }
        String normalizedTag = languageTag.replace('_', '-');
        Locale locale = Locale.forLanguageTag(normalizedTag);
        if (locale == null || locale.getLanguage() == null || locale.getLanguage().isEmpty() || "und".equals(locale.getLanguage())) {
            String[] parts = languageTag.split("[-_]");
            if (parts.length >= 2) {
                return new Locale.Builder().setLanguage(parts[0]).setRegion(parts[1]).build();
            } else if (parts.length == 1 && !parts[0].isEmpty()) {
                return new Locale.Builder().setLanguage(parts[0]).build();
            }
            return Locale.getDefault();
        }
        return locale;
    }
} 