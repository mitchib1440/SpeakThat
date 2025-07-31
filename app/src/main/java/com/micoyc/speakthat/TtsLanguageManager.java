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
        languages.add(new TtsLanguage("English (United Kingdom)", "en_GB", new Locale("en", "GB")));
        
        // German (has complete translations)
        languages.add(new TtsLanguage("Deutsch (Deutschland)", "de_DE", new Locale("de", "DE")));
        
        // Italian (has complete translations)
        languages.add(new TtsLanguage("Italiano (Italia)", "it_IT", new Locale("it", "IT")));
        
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
                "tts_onboarding_welcome"
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
} 