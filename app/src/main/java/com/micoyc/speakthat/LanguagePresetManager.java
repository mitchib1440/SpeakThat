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
     * Includes all languages with Weblate translations (at various completion levels).
     */
    public static List<LanguagePreset> getAllPresets() {
        List<LanguagePreset> presets = new ArrayList<>();
        
        // English variants
        presets.add(new LanguagePreset(
            "en_US", 
            "English (United States)", 
            "en_US", 
            "en_US", 
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "en_GB", 
            "English (United Kingdom)", 
            "en_GB", 
            "en_GB", 
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "en_CA", 
            "English (Canada)", 
            "en_CA", 
            "en_CA", 
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "en_AU", 
            "English (Australia)", 
            "en_AU", 
            "en_AU", 
            null,
            false
        ));
        
        // All other supported languages, alphabetical
        presets.add(new LanguagePreset(
            "ar",
            "العربية",
            "ar",
            "ar",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "zh_CN",
            "中文 (简体)",
            "zh_CN",
            "zh_CN",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "zh_TW",
            "中文 (繁體)",
            "zh_TW",
            "zh_TW",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "nl_NL", 
            "Nederlands (Nederland)", 
            "nl_NL", 
            "nl_NL", 
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "et_EE",
            "Eesti (Eesti)",
            "et_EE",
            "et_EE",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "fr_FR",
            "Français (France)",
            "fr_FR",
            "fr_FR",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "de_DE", 
            "Deutsch (Deutschland)", 
            "de_DE", 
            "de_DE", 
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "hi_IN",
            "हिन्दी (भारत)",
            "hi_IN",
            "hi_IN",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "id_ID",
            "Bahasa Indonesia",
            "id_ID",
            "id_ID",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "it_IT",
            "Italiano (Italia)",
            "it_IT",
            "it_IT",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "ja_JP", 
            "日本語 (日本)", 
            "ja_JP", 
            "ja_JP", 
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "ko_KR",
            "한국어 (대한민국)",
            "ko_KR",
            "ko_KR",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "pl_PL",
            "Polski (Polska)",
            "pl_PL",
            "pl_PL",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "pt_PT",
            "Português (Portugal)",
            "pt_PT",
            "pt_PT",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "pa_IN",
            "ਪੰਜਾਬੀ (ਭਾਰਤ)",
            "pa_IN",
            "pa_IN",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "ru_RU",
            "Русский (Россия)",
            "ru_RU",
            "ru_RU",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "es_ES", 
            "Español (España)", 
            "es_ES", 
            "es_ES", 
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "sv_SE",
            "Svenska (Sverige)",
            "sv_SE",
            "sv_SE",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "th_TH",
            "ไทย (ประเทศไทย)",
            "th_TH",
            "th_TH",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "tr_TR", 
            "Türkçe (Türkiye)", 
            "tr_TR", 
            "tr_TR", 
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "ur_PK",
            "اردو (پاکستان)",
            "ur_PK",
            "ur_PK",
            null,
            false
        ));
        
        presets.add(new LanguagePreset(
            "vi_VN",
            "Tiếng Việt (Việt Nam)",
            "vi_VN",
            "vi_VN",
            null,
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
     * Apply a language preset for onboarding without showing restart dialog
     * This method is specifically designed for use during onboarding to avoid interrupting the flow
     */
    public static void applyPresetForOnboarding(Context context, LanguagePreset preset) {
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
            InAppLogger.log(TAG, "Onboarding preset applied - cleared specific voice, letting system choose");
        } else {
            editor.putString("voice_name", preset.defaultVoice);
            InAppLogger.log(TAG, "Onboarding preset applied - set specific voice: " + preset.defaultVoice);
        }
        
        editor.apply();
        
        // Save the preset itself
        saveCurrentPreset(context, preset);
        
        // Apply the UI language change immediately without showing dialog
        applyUILanguageChangeForOnboarding(context, preset.uiLocale);
        
        InAppLogger.log(TAG, "Onboarding language preset applied: " + preset.displayName + 
                       " (UI: " + preset.uiLocale + ", TTS: " + preset.ttsLanguage + ")");
    }
    
    /**
     * Apply UI language change immediately to the current activity
     */
    private static void applyUILanguageChange(Context context, String uiLocale) {
        try {
            Locale targetLocale = parseLocaleString(uiLocale);
            
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
            
            // For proper UI language update, we should show the language change dialog
            // This gives users options for how to apply the language change
            if (context instanceof android.app.Activity) {
                android.app.Activity activity = (android.app.Activity) context;
                InAppLogger.log(TAG, "Language change detected - showing dialog in current activity");
                
                // Show the language change dialog directly in the current activity
                showLanguageChangeDialog(activity, targetLocale);
            }
            
        } catch (Exception e) {
            InAppLogger.log(TAG, "Error applying UI language change: " + e.getMessage());
        }
    }
    
    /**
     * Apply UI language change immediately for onboarding without showing restart dialog
     */
    private static void applyUILanguageChangeForOnboarding(Context context, String uiLocale) {
        try {
            Locale targetLocale = parseLocaleString(uiLocale);
            
            // Check if the target locale is already the current locale to avoid infinite loops
            Configuration currentConfig = context.getResources().getConfiguration();
            Locale currentLocale = currentConfig.getLocales().get(0);
            
            if (currentLocale != null && 
                targetLocale.getLanguage().equals(currentLocale.getLanguage()) &&
                targetLocale.getCountry().equals(currentLocale.getCountry())) {
                InAppLogger.log(TAG, "Onboarding UI language already set to: " + targetLocale.toString() + " - skipping change");
                return;
            }
            
            InAppLogger.log(TAG, "Applying onboarding UI language change from " + 
                           (currentLocale != null ? currentLocale.toString() : "null") + 
                           " to: " + targetLocale.toString());
            
            // Update the app's locale configuration
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(targetLocale);
            
            // Apply the new configuration
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
            
            // Also update the default locale for this session
            Locale.setDefault(targetLocale);
            
            InAppLogger.log(TAG, "Onboarding UI language changed successfully to: " + targetLocale.getDisplayLanguage());
            
            // For onboarding, we need to recreate the current activity to apply the language change immediately
            // This ensures the UI updates without interrupting the onboarding flow
            if (context instanceof android.app.Activity) {
                android.app.Activity activity = (android.app.Activity) context;
                InAppLogger.log(TAG, "Recreating onboarding activity to apply language change: " + targetLocale.getDisplayLanguage());
                
                // Recreate the activity to apply the new language immediately
                activity.recreate();
            }
            
        } catch (Exception e) {
            InAppLogger.log(TAG, "Error applying onboarding UI language change: " + e.getMessage());
        }
    }

    private static Locale parseLocaleString(String localeString) {
        if (localeString == null || localeString.isEmpty()) {
            return Locale.getDefault();
        }

        String normalizedTag = localeString.replace('_', '-');
        Locale locale = Locale.forLanguageTag(normalizedTag);
        if (locale == null || locale.getLanguage() == null || locale.getLanguage().isEmpty() || "und".equals(locale.getLanguage())) {
            String[] parts = localeString.split("_");
            if (parts.length >= 2) {
                return new Locale.Builder().setLanguage(parts[0]).setRegion(parts[1]).build();
            } else if (parts.length == 1 && !parts[0].isEmpty()) {
                return new Locale.Builder().setLanguage(parts[0]).build();
            }
            return Locale.getDefault();
        }
        return locale;
    }
    
    /**
     * Show language change dialog with options for user
     */
    private static void showLanguageChangeDialog(android.app.Activity activity, Locale newLocale) {
        try {
            String languageName = newLocale.getDisplayLanguage(newLocale);
            String currentLanguageName = Locale.getDefault().getDisplayLanguage(Locale.getDefault());
            
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
            builder.setTitle(activity.getString(R.string.dialog_title_language_changed))
                   .setMessage(activity.getString(R.string.dialog_message_language_changed, currentLanguageName, languageName))
                   .setPositiveButton(activity.getString(R.string.button_restart_app), (dialog, which) -> {
                       // Restart the entire app to ensure all components use new language
                       restartApp(activity);
                   })
                   .setNegativeButton(activity.getString(R.string.button_apply_now), (dialog, which) -> {
                       // Just recreate current activity
                       activity.recreate();
                   })
                   .setNeutralButton(activity.getString(R.string.button_later), (dialog, which) -> {
                       // Do nothing - user can manually restart later
                       android.widget.Toast.makeText(activity, 
                           activity.getString(R.string.toast_language_manual_restart), 
                           android.widget.Toast.LENGTH_SHORT).show();
                   })
                   .setCancelable(false)
                   .show();
            
            InAppLogger.log(TAG, "Language change dialog shown: " + currentLanguageName + " → " + languageName);
        } catch (Exception e) {
            InAppLogger.log(TAG, "Error showing language change dialog: " + e.getMessage());
        }
    }
    
    /**
     * Restart the entire app to ensure all components use the new language
     */
    private static void restartApp(android.app.Activity activity) {
        try {
            // Create intent to restart the app
            android.content.Intent intent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Show a brief message
            android.widget.Toast.makeText(activity, 
                activity.getString(R.string.toast_language_restarting), 
                android.widget.Toast.LENGTH_SHORT).show();
            
            // Start the new instance and finish current one
            activity.startActivity(intent);
            activity.finish();
            
            InAppLogger.log(TAG, "App restart initiated for language change");
        } catch (Exception e) {
            InAppLogger.log(TAG, "Failed to restart app: " + e.getMessage());
            // Fallback to recreate if restart fails
            activity.recreate();
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
