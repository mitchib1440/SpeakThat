package com.micoyc.speakthat;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages language presets that combine UI language, TTS language, and default voice settings
 * into easy-to-use presets for better user experience.
 * 
 * This system provides:
 * - Simple preset selection for most users
 * - Backwards compatibility with existing settings
 * - Advanced customization still available
 * - Safe defaults and robust error handling
 */
public class LanguagePresetManager {
    
    private static final String TAG = "LanguagePresetManager";
    
    // SharedPreferences keys for the new preset system
    private static final String KEY_LANGUAGE_PRESET = "language_preset";
    private static final String KEY_IS_CUSTOM_PRESET = "is_custom_preset";
    
    // Default preset ID - safe fallback for any issues
    private static final String DEFAULT_PRESET_ID = "en_US";
    
    /**
     * Represents a complete language preset that sets multiple related settings at once
     */
    public static class LanguagePreset {
        public final String id;              // Unique identifier (e.g., "en_US")
        public final String displayName;     // User-friendly name (e.g., "English (United States)")
        public final String uiLocale;        // Android locale for UI (e.g., "en_US")
        public final String ttsLanguage;     // TTS language code (e.g., "en_US")
        public final String defaultVoice;    // Default voice name (null = let system choose)
        public final boolean isCustom;      // True if this represents custom/advanced settings
        
        public LanguagePreset(String id, String displayName, String uiLocale, 
                            String ttsLanguage, String defaultVoice, boolean isCustom) {
            this.id = id;
            this.displayName = displayName;
            this.uiLocale = uiLocale;
            this.ttsLanguage = ttsLanguage;
            this.defaultVoice = defaultVoice;
            this.isCustom = isCustom;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    /**
     * Get all available language presets.
     * Starting with English and Japanese as requested by user.
     * More languages can be easily added here when ready.
     */
    public static List<LanguagePreset> getAllPresets() {
        List<LanguagePreset> presets = new ArrayList<>();
        
        // English variants - well-supported and tested
        presets.add(new LanguagePreset(
            "en_US", 
            "English (United States)", 
            "en_US", 
            "en_US", 
            null,  // Let system choose best English voice
            false
        ));
        
        presets.add(new LanguagePreset(
            "en_GB", 
            "English (United Kingdom)", 
            "en_GB", 
            "en_GB", 
            null,  // Let system choose best UK English voice
            false
        ));
        
        presets.add(new LanguagePreset(
            "en_CA", 
            "English (Canada)", 
            "en_CA", 
            "en_CA", 
            null,  // Let system choose best Canadian English voice
            false
        ));
        
        presets.add(new LanguagePreset(
            "en_AU", 
            "English (Australia)", 
            "en_AU", 
            "en_AU", 
            null,  // Let system choose best Australian English voice
            false
        ));
        
        // Japanese - complete UI translations available, good test case
        presets.add(new LanguagePreset(
            "ja_JP", 
            "日本語 (日本)", 
            "ja_JP", 
            "ja_JP", 
            null,  // Let system choose best Japanese voice
            false
        ));
        
        // Custom preset - represents user's advanced customizations
        presets.add(new LanguagePreset(
            "custom", 
            "Custom (Advanced Settings)", 
            "", 
            "", 
            "", 
            true
        ));
        
        InAppLogger.log(TAG, "Available language presets: " + presets.size() + " presets loaded");
        return presets;
    }
    
    /**
     * Get the current language preset from SharedPreferences.
     * Returns default preset if none is set or if there's any issue.
     */
    public static LanguagePreset getCurrentPreset(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE);
        String presetId = prefs.getString(KEY_LANGUAGE_PRESET, DEFAULT_PRESET_ID);
        boolean isCustom = prefs.getBoolean(KEY_IS_CUSTOM_PRESET, false);
        
        // If marked as custom, return the custom preset
        if (isCustom) {
            return getCustomPreset();
        }
        
        // Find the matching preset
        for (LanguagePreset preset : getAllPresets()) {
            if (preset.id.equals(presetId)) {
                InAppLogger.log(TAG, "Current preset loaded: " + preset.displayName);
                return preset;
            }
        }
        
        // Fallback to default if preset not found
        InAppLogger.log(TAG, "Preset not found (" + presetId + "), using default: " + DEFAULT_PRESET_ID);
        return getDefaultPreset();
    }
    
    /**
     * Save the current language preset to SharedPreferences
     */
    public static void saveCurrentPreset(Context context, LanguagePreset preset) {
        SharedPreferences prefs = context.getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putString(KEY_LANGUAGE_PRESET, preset.id);
        editor.putBoolean(KEY_IS_CUSTOM_PRESET, preset.isCustom);
        editor.apply();
        
        InAppLogger.log(TAG, "Preset saved: " + preset.displayName + " (custom: " + preset.isCustom + ")");
    }
    
    /**
     * Apply a language preset by setting all related voice settings
     */
    public static void applyPreset(Context context, LanguagePreset preset) {
        if (preset.isCustom) {
            InAppLogger.log(TAG, "Custom preset selected - not overriding existing settings");
            return;
        }
        
        SharedPreferences prefs = context.getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Apply the preset settings
        editor.putString("language", preset.uiLocale);
        editor.putString("tts_language", preset.ttsLanguage);
        
        // Clear specific voice to let system choose appropriate one for the language
        if (preset.defaultVoice == null) {
            editor.remove("voice_name");
            InAppLogger.log(TAG, "Preset applied - cleared specific voice, letting system choose");
        } else {
            editor.putString("voice_name", preset.defaultVoice);
            InAppLogger.log(TAG, "Preset applied - set specific voice: " + preset.defaultVoice);
        }
        
        editor.apply();
        
        // Save the preset itself
        saveCurrentPreset(context, preset);
        
        // Apply the UI language change immediately (only if different from current)
        applyUILanguageChange(context, preset.uiLocale);
        
        InAppLogger.log(TAG, "Language preset applied: " + preset.displayName + 
                       " (UI: " + preset.uiLocale + ", TTS: " + preset.ttsLanguage + ")");
    }
    
    /**
     * Apply UI language change immediately to the current activity
     */
    private static void applyUILanguageChange(Context context, String uiLocale) {
        try {
            // Parse the locale string (e.g., "ja_JP" -> Locale("ja", "JP"))
            String[] localeParts = uiLocale.split("_");
            Locale targetLocale;
            if (localeParts.length >= 2) {
                targetLocale = new Locale(localeParts[0], localeParts[1]);
            } else if (localeParts.length == 1) {
                targetLocale = new Locale(localeParts[0]);
            } else {
                targetLocale = Locale.getDefault();
            }
            
            // Check if the target locale is already the current locale to avoid infinite loops
            Configuration currentConfig = context.getResources().getConfiguration();
            Locale currentLocale = currentConfig.getLocales().get(0);
            
            if (currentLocale != null && 
                targetLocale.getLanguage().equals(currentLocale.getLanguage()) &&
                targetLocale.getCountry().equals(currentLocale.getCountry())) {
                InAppLogger.log(TAG, "UI language already set to: " + targetLocale.toString() + " - skipping change");
                return;
            }
            
            InAppLogger.log(TAG, "Applying UI language change from " + 
                           (currentLocale != null ? currentLocale.toString() : "null") + 
                           " to: " + targetLocale.toString());
            
            // Update the app's locale configuration
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(targetLocale);
            
            // Apply the new configuration
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
            
            // Also update the default locale for this session
            Locale.setDefault(targetLocale);
            
            InAppLogger.log(TAG, "UI language changed successfully to: " + targetLocale.getDisplayLanguage());
            
            // For proper UI language update, we should refresh the current activity
            // This ensures all UI elements are updated to the new language
            if (context instanceof android.app.Activity) {
                android.app.Activity activity = (android.app.Activity) context;
                InAppLogger.log(TAG, "Refreshing activity to apply new UI language");
                activity.recreate();
            }
            
        } catch (Exception e) {
            InAppLogger.log(TAG, "Error applying UI language change: " + e.getMessage());
        }
    }
    
    /**
     * Detect if current settings match a preset or if they're custom
     */
    public static LanguagePreset detectCurrentPreset(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE);
        
        String currentLanguage = prefs.getString("language", "en_US");
        String currentTtsLanguage = prefs.getString("tts_language", "system");
        String currentVoice = prefs.getString("voice_name", "");
        boolean advancedEnabled = prefs.getBoolean("show_advanced_voice", false);
        
        InAppLogger.log(TAG, "Detecting preset - Language: " + currentLanguage + 
                       ", TTS: " + currentTtsLanguage + ", Voice: " + currentVoice + 
                       ", Advanced: " + advancedEnabled);
        
        // If advanced options are enabled and user has customized settings, mark as custom
        if (advancedEnabled && (!currentVoice.isEmpty() || !"system".equals(currentTtsLanguage))) {
            InAppLogger.log(TAG, "Advanced settings detected - marking as custom preset");
            return getCustomPreset();
        }
        
        // Check if current settings match any preset
        for (LanguagePreset preset : getAllPresets()) {
            if (preset.isCustom) continue; // Skip custom preset in matching
            
            boolean languageMatches = preset.uiLocale.equals(currentLanguage);
            boolean ttsMatches = preset.ttsLanguage.equals(currentTtsLanguage) || 
                               ("system".equals(currentTtsLanguage) && preset.ttsLanguage.equals(currentLanguage));
            boolean voiceMatches = (preset.defaultVoice == null && currentVoice.isEmpty()) ||
                                 (preset.defaultVoice != null && preset.defaultVoice.equals(currentVoice));
            
            if (languageMatches && ttsMatches && voiceMatches) {
                InAppLogger.log(TAG, "Settings match preset: " + preset.displayName);
                return preset;
            }
        }
        
        // No exact match found - mark as custom
        InAppLogger.log(TAG, "No matching preset found - using custom preset");
        return getCustomPreset();
    }
    
    /**
     * Detect current preset from settings, ignoring the advanced flag.
     * This is used when disabling advanced mode to find the best matching preset.
     */
    public static LanguagePreset detectCurrentPresetIgnoringAdvanced(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE);
        
        String currentLanguage = prefs.getString("language", "en_US");
        String currentTtsLanguage = prefs.getString("tts_language", "system");
        String currentVoice = prefs.getString("voice_name", "");
        
        InAppLogger.log(TAG, "Detecting preset (ignoring advanced flag) - Language: " + currentLanguage + 
                       ", TTS: " + currentTtsLanguage + ", Voice: " + currentVoice);
        
        // Check if current settings match any preset, ignoring advanced mode completely
        for (LanguagePreset preset : getAllPresets()) {
            if (preset.isCustom) continue; // Skip custom preset in matching
            
            boolean languageMatches = preset.uiLocale.equals(currentLanguage);
            boolean ttsMatches = preset.ttsLanguage.equals(currentTtsLanguage) || 
                               ("system".equals(currentTtsLanguage) && preset.ttsLanguage.equals(currentLanguage));
            boolean voiceMatches = (preset.defaultVoice == null && currentVoice.isEmpty()) ||
                                 (preset.defaultVoice != null && preset.defaultVoice.equals(currentVoice));
            
            if (languageMatches && ttsMatches && voiceMatches) {
                InAppLogger.log(TAG, "Settings match preset (ignoring advanced): " + preset.displayName);
                return preset;
            }
        }
        
        // No exact match found - return custom preset
        InAppLogger.log(TAG, "No matching preset found (ignoring advanced) - using custom");
        return getCustomPreset();
    }
    
    /**
     * Get the default language preset (English US)
     */
    public static LanguagePreset getDefaultPreset() {
        for (LanguagePreset preset : getAllPresets()) {
            if (preset.id.equals(DEFAULT_PRESET_ID)) {
                return preset;
            }
        }
        
        // This should never happen, but provide ultimate fallback
        InAppLogger.log(TAG, "ERROR: Default preset not found! Creating emergency fallback");
        return new LanguagePreset(
            DEFAULT_PRESET_ID, 
            "English (United States)", 
            "en_US", 
            "en_US", 
            null, 
            false
        );
    }
    
    /**
     * Get the custom preset that represents advanced user settings
     */
    public static LanguagePreset getCustomPreset() {
        for (LanguagePreset preset : getAllPresets()) {
            if (preset.isCustom) {
                return preset;
            }
        }
        
        // This should never happen, but provide fallback
        InAppLogger.log(TAG, "ERROR: Custom preset not found! Creating emergency fallback");
        return new LanguagePreset(
            "custom", 
            "Custom (Advanced Settings)", 
            "", 
            "", 
            "", 
            true
        );
    }
    
    /**
     * Find the best matching preset for given settings (used for backwards compatibility)
     */
    public static LanguagePreset findBestMatch(String language, String ttsLanguage, String voiceName) {
        InAppLogger.log(TAG, "Finding best preset match for - Language: " + language + 
                       ", TTS: " + ttsLanguage + ", Voice: " + voiceName);
        
        // If user has specific voice set, consider it custom
        if (voiceName != null && !voiceName.isEmpty()) {
            InAppLogger.log(TAG, "Specific voice detected - using custom preset");
            return getCustomPreset();
        }
        
        // Try to find exact match
        for (LanguagePreset preset : getAllPresets()) {
            if (preset.isCustom) continue;
            
            boolean languageMatches = preset.uiLocale.equals(language);
            boolean ttsMatches = preset.ttsLanguage.equals(ttsLanguage) || 
                               ("system".equals(ttsLanguage) && preset.ttsLanguage.equals(language));
            
            if (languageMatches && ttsMatches) {
                InAppLogger.log(TAG, "Exact match found: " + preset.displayName);
                return preset;
            }
        }
        
        // Try to find language-only match
        for (LanguagePreset preset : getAllPresets()) {
            if (preset.isCustom) continue;
            
            if (preset.uiLocale.equals(language)) {
                InAppLogger.log(TAG, "Language-only match found: " + preset.displayName);
                return preset;
            }
        }
        
        // No match found - use default
        InAppLogger.log(TAG, "No match found - using default preset");
        return getDefaultPreset();
    }
    
    /**
     * Check if the user should be migrated to the new preset system
     * (i.e., they have old settings but no preset saved)
     */
    public static boolean needsPresetMigration(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE);
        
        // If they already have a preset saved, no migration needed
        if (prefs.contains(KEY_LANGUAGE_PRESET)) {
            return false;
        }
        
        // If they have any voice settings, they need migration
        return prefs.contains("language") || prefs.contains("tts_language") || prefs.contains("voice_name");
    }
    
    /**
     * Migrate existing settings to the new preset system
     */
    public static void migrateToPresetSystem(Context context) {
        if (!needsPresetMigration(context)) {
            InAppLogger.log(TAG, "No preset migration needed");
            return;
        }
        
        InAppLogger.log(TAG, "Migrating existing settings to preset system");
        
        SharedPreferences prefs = context.getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE);
        String existingLanguage = prefs.getString("language", "en_US");
        String existingTtsLanguage = prefs.getString("tts_language", "system");
        String existingVoice = prefs.getString("voice_name", "");
        
        // Find the best matching preset
        LanguagePreset bestMatch = findBestMatch(existingLanguage, existingTtsLanguage, existingVoice);
        
        // Save the preset
        saveCurrentPreset(context, bestMatch);
        
        InAppLogger.log(TAG, "Migration complete - selected preset: " + bestMatch.displayName);
    }
}
