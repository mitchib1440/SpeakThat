package com.micoyc.speakthat;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class FilterConfigManager {
    
    private static final String PREFS_NAME = "SpeakThatPrefs";
    private static final String CONFIG_VERSION = "1.0";
    
    // Filter preference keys
    private static final String KEY_APP_LIST_MODE = "app_list_mode";
    private static final String KEY_APP_LIST = "app_list";
    private static final String KEY_APP_PRIVATE_FLAGS = "app_private_flags";
    private static final String KEY_WORD_BLACKLIST = "word_blacklist";
    private static final String KEY_WORD_BLACKLIST_PRIVATE = "word_blacklist_private";
    private static final String KEY_WORD_REPLACEMENTS = "word_replacements";
    
    public static class FilterConfig {
        public String appListMode;
        public Set<String> appList;
        public Set<String> appPrivateFlags;
        public Set<String> wordBlacklist;
        public Set<String> wordBlacklistPrivate;
        public String wordReplacements; // Stored as delimited string
        public String exportDate;
        public String appVersion;
        public String configVersion;
        
        public FilterConfig() {
            this.appList = new HashSet<>();
            this.appPrivateFlags = new HashSet<>();
            this.wordBlacklist = new HashSet<>();
            this.wordBlacklistPrivate = new HashSet<>();
            this.wordReplacements = "";
            this.exportDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            this.appVersion = "1.0"; // Static version for export compatibility
            this.configVersion = CONFIG_VERSION;
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
        config.wordBlacklist = new HashSet<>(prefs.getStringSet(KEY_WORD_BLACKLIST, new HashSet<>()));
        config.wordBlacklistPrivate = new HashSet<>(prefs.getStringSet(KEY_WORD_BLACKLIST_PRIVATE, new HashSet<>()));
        config.wordReplacements = prefs.getString(KEY_WORD_REPLACEMENTS, "");
        
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
        filters.put("wordBlacklist", new JSONArray(config.wordBlacklist));
        filters.put("wordBlacklistPrivate", new JSONArray(config.wordBlacklistPrivate));
        filters.put("wordReplacements", config.wordReplacements);
        json.put("filters", filters);
        
        // Future extension point - we can add more sections here
        // json.put("behaviorSettings", ...);
        // json.put("voiceSettings", ...);
        
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
            
            // Import word replacements
            if (filters.has("wordReplacements")) {
                String wordReplacements = filters.getString("wordReplacements");
                editor.putString(KEY_WORD_REPLACEMENTS, wordReplacements);
                if (!wordReplacements.isEmpty()) {
                    filtersImported += wordReplacements.split("\\|").length;
                }
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
        Set<String> blockedWords = prefs.getStringSet(KEY_WORD_BLACKLIST, new HashSet<>());
        Set<String> privateWords = prefs.getStringSet(KEY_WORD_BLACKLIST_PRIVATE, new HashSet<>());
        String replacements = prefs.getString(KEY_WORD_REPLACEMENTS, "");
        
        summary.append("🚫 Word Filtering: ").append(blockedWords.size()).append(" blocked");
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
} 