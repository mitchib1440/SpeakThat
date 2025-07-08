package com.micoyc.speakthat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.widget.AutoCompleteTextView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.micoyc.speakthat.databinding.ActivityFilterSettingsBinding;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.micoyc.speakthat.AppListData;
import com.micoyc.speakthat.AppListManager;
import com.micoyc.speakthat.AppSearchAdapter;

public class FilterSettingsActivity extends AppCompatActivity {
    private ActivityFilterSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    
    // File picker for import
    private ActivityResultLauncher<Intent> importFileLauncher;
    
    // App selector
    private AutoCompleteTextView editAppName;
    private AppAutoCompleteAdapter appSelectorAdapter;
    private List<AppInfo> installedApps;
    
    // SharedPreferences keys
    private static final String PREFS_NAME = "SpeakThatPrefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_APP_LIST_MODE = "app_list_mode";
    private static final String KEY_APP_LIST = "app_list";
    private static final String KEY_APP_PRIVATE_FLAGS = "app_private_flags";
    private static final String KEY_WORD_BLACKLIST = "word_blacklist";
    private static final String KEY_WORD_BLACKLIST_PRIVATE = "word_blacklist_private";
    private static final String KEY_WORD_REPLACEMENTS = "word_replacements";
    private static final String KEY_DEFAULTS_INITIALIZED = "defaults_initialized";
    
    // Media notification filtering keys
    private static final String KEY_MEDIA_FILTERING_ENABLED = "media_filtering_enabled";
    private static final String KEY_MEDIA_FILTER_EXCEPTED_APPS = "media_filter_excepted_apps";
    private static final String KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS = "media_filter_important_keywords";

    // Adapters
    private AppListAdapter appListAdapter;
    private WordListAdapter wordBlacklistAdapter;
    private WordReplacementAdapter wordReplacementAdapter;
    private AppListAdapter mediaExceptedAppsAdapter;
    private WordListAdapter mediaImportantKeywordsAdapter;

    // Data lists
    private List<AppFilterItem> appList = new ArrayList<>();
    private List<WordFilterItem> wordBlacklistItems = new ArrayList<>();
    private List<WordReplacementItem> wordReplacementItems = new ArrayList<>();
    private List<AppFilterItem> mediaExceptedAppsList = new ArrayList<>();
    private List<WordFilterItem> mediaImportantKeywordsList = new ArrayList<>();

    private LocalBroadcastManager localBroadcastManager;
    private android.content.BroadcastReceiver repairBlacklistReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme FIRST before anything else
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applySavedTheme();
        
        super.onCreate(savedInstanceState);
        binding = ActivityFilterSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        initializeUI();
        loadSettings();
        initializeFilePicker();
        
        initializeViews();
        initializeAppSelector();
        
        // Test the new JSON app list functionality
        AppListTest.INSTANCE.testAppListLoading(this);

        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        repairBlacklistReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                if ("com.micoyc.speakthat.ACTION_REPAIR_BLACKLIST".equals(intent.getAction())) {
                    loadSettings();
                    Toast.makeText(FilterSettingsActivity.this, "Word blacklist reloaded after repair", Toast.LENGTH_SHORT).show();
                }
            }
        };
        localBroadcastManager.registerReceiver(repairBlacklistReceiver, new android.content.IntentFilter("com.micoyc.speakthat.ACTION_REPAIR_BLACKLIST"));
    }

    private void applySavedTheme() {
        boolean isDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, true);
        
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void initializeUI() {
        // Set up app list mode radio buttons
        binding.appListModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String mode = "none";
            if (checkedId == R.id.radioWhitelist) {
                mode = "whitelist";
            } else if (checkedId == R.id.radioBlacklist) {
                mode = "blacklist";
            }
            
            // Show/hide app list section
            binding.appListSection.setVisibility(
                "none".equals(mode) ? View.GONE : View.VISIBLE
            );
            
            // Save setting
            saveAppListMode(mode);
        });

        // Set up blacklist type spinner
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
            this, R.array.blacklist_types, android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerBlacklistType.setAdapter(spinnerAdapter);

        // Set up RecyclerViews
        setupAppListRecycler();
        setupWordBlacklistRecycler();
        setupWordReplacementRecycler();
        setupMediaFilteringRecyclers();

        // Set up button listeners
        binding.btnAddApp.setOnClickListener(v -> addApp());
        binding.txtAppFilterHelp.setOnClickListener(v -> showAppFilterHelp());
        binding.btnAddBlacklistWord.setOnClickListener(v -> addBlacklistWord());
        binding.btnAddReplacement.setOnClickListener(v -> addWordReplacement());
        
        // Set up media filtering button listeners
        binding.btnAddMediaExceptedApp.setOnClickListener(v -> addMediaExceptedApp());
        binding.btnAddMediaImportantKeyword.setOnClickListener(v -> addMediaImportantKeyword());
        binding.txtMediaFilterHelp.setOnClickListener(v -> showMediaFilterHelp());
        
        // Set up media filtering switch
        binding.switchMediaFiltering.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.mediaFilteringSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            saveMediaFilteringEnabled(isChecked);
        });
        
        // Set up collapsible advanced options
        binding.advancedOptionsHeader.setOnClickListener(v -> toggleAdvancedOptions());
        
        // Set up collapsible sections
        binding.appListHeader.setOnClickListener(v -> toggleAppList());
        binding.blacklistHeader.setOnClickListener(v -> toggleBlacklist());
        binding.replacementHeader.setOnClickListener(v -> toggleReplacement());
        
        // Set up media excepted app input field
        setupMediaExceptedAppSelector();
    }

    private void setupAppListRecycler() {
        appListAdapter = new AppListAdapter(appList, this::removeApp, this::toggleAppPrivate, this::editApp);
        binding.recyclerAppList.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerAppList.setAdapter(appListAdapter);
    }

    private void setupWordBlacklistRecycler() {
        wordBlacklistAdapter = new WordListAdapter(wordBlacklistItems, this::removeBlacklistWord, this::onWordTypeChange, this::editBlacklistWord);
        binding.recyclerBlacklistWords.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerBlacklistWords.setAdapter(wordBlacklistAdapter);
        
        // Force the RecyclerView to measure itself properly
        binding.recyclerBlacklistWords.post(() -> {
            binding.recyclerBlacklistWords.requestLayout();
        });
    }

    private void setupWordReplacementRecycler() {
        wordReplacementAdapter = new WordReplacementAdapter(wordReplacementItems, this::removeWordReplacement, this::editWordReplacement);
        binding.recyclerWordReplacements.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerWordReplacements.setAdapter(wordReplacementAdapter);
    }
    
    private void setupMediaFilteringRecyclers() {
        // Set up media excepted apps RecyclerView
        mediaExceptedAppsAdapter = new AppListAdapter(mediaExceptedAppsList, this::removeMediaExceptedApp, this::toggleMediaExceptedAppPrivate, this::editMediaExceptedApp);
        binding.recyclerMediaExceptedApps.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerMediaExceptedApps.setAdapter(mediaExceptedAppsAdapter);
        
        // Set up media important keywords RecyclerView
        mediaImportantKeywordsAdapter = new WordListAdapter(mediaImportantKeywordsList, this::removeMediaImportantKeyword, this::onMediaKeywordTypeChange, this::editMediaImportantKeyword);
        binding.recyclerMediaImportantKeywords.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerMediaImportantKeywords.setAdapter(mediaImportantKeywordsAdapter);
    }
    
    private void setupMediaExceptedAppSelector() {
        // Load apps from JSON file
        List<AppListData> jsonApps = AppListManager.INSTANCE.loadAppList(this);
        
        if (jsonApps != null && !jsonApps.isEmpty()) {
            // Create adapter with JSON data
            AppSearchAdapter jsonAdapter = new AppSearchAdapter(this, jsonApps);
            binding.editMediaExceptedApp.setAdapter(jsonAdapter);
            binding.editMediaExceptedApp.setThreshold(1); // Show suggestions after 1 character
            
            // Handle app selection
            binding.editMediaExceptedApp.setOnItemClickListener((parent, view, position, id) -> {
                AppListData selectedApp = jsonAdapter.getItem(position);
                if (selectedApp != null) {
                    binding.editMediaExceptedApp.setText(selectedApp.packageName);
                    binding.editMediaExceptedApp.setSelection(selectedApp.packageName.length());
                }
            });
        } else {
            // Fallback to old system if JSON loading fails
            installedApps = getCommonApps();
            
            if (installedApps != null && !installedApps.isEmpty()) {
                appSelectorAdapter = new AppAutoCompleteAdapter(this, installedApps);
                binding.editMediaExceptedApp.setAdapter(appSelectorAdapter);
                binding.editMediaExceptedApp.setThreshold(1);
                
                binding.editMediaExceptedApp.setOnItemClickListener((parent, view, position, id) -> {
                    AppInfo selectedApp = appSelectorAdapter.getItem(position);
                    if (selectedApp != null) {
                        binding.editMediaExceptedApp.setText(selectedApp.packageName);
                        binding.editMediaExceptedApp.setSelection(selectedApp.packageName.length());
                    }
                });
            }
        }
    }

    private void loadSettings() {
        // Initialize defaults if this is the first time
        initializeDefaultWordBlacklist();
        initializeDefaultMediaKeywords();
        initializeDefaultMediaExceptionApps();
        
        // Load app list mode
        String appListMode = sharedPreferences.getString(KEY_APP_LIST_MODE, "none");
        switch (appListMode) {
            case "whitelist":
                binding.radioWhitelist.setChecked(true);
                binding.appListSection.setVisibility(View.VISIBLE);
                break;
            case "blacklist":
                binding.radioBlacklist.setChecked(true);
                binding.appListSection.setVisibility(View.VISIBLE);
                break;
            default:
                binding.radioNone.setChecked(true);
                binding.appListSection.setVisibility(View.GONE);
                break;
        }

        // Load app list
        Set<String> apps = sharedPreferences.getStringSet(KEY_APP_LIST, new HashSet<>());
        Set<String> privateApps = sharedPreferences.getStringSet(KEY_APP_PRIVATE_FLAGS, new HashSet<>());
        
        appList.clear();
        for (String app : apps) {
            appList.add(new AppFilterItem(app, privateApps.contains(app)));
        }
        appListAdapter.notifyDataSetChanged();
        updateCountDisplays();

        // Load word blacklist
        Set<String> blacklistWords = sharedPreferences.getStringSet(KEY_WORD_BLACKLIST, new HashSet<>());
        Set<String> privateWords = sharedPreferences.getStringSet(KEY_WORD_BLACKLIST_PRIVATE, new HashSet<>());
        
        wordBlacklistItems.clear();
        for (String word : blacklistWords) {
            wordBlacklistItems.add(new WordFilterItem(word, false)); // false = block entirely
        }
        for (String word : privateWords) {
            wordBlacklistItems.add(new WordFilterItem(word, true)); // true = make private
        }
        
        Log.d("FilterSettings", "Loaded " + wordBlacklistItems.size() + " blacklist items");
        wordBlacklistAdapter.notifyDataSetChanged();
        
        // Force layout refresh to ensure all items are visible
        binding.recyclerBlacklistWords.post(() -> {
            Log.d("FilterSettings", "Forcing layout refresh for " + wordBlacklistItems.size() + " items");
            wordBlacklistAdapter.notifyDataSetChanged();
            updateCountDisplays();
            binding.recyclerBlacklistWords.requestLayout();
            binding.recyclerBlacklistWords.invalidate();
            
            // Additional fix for ScrollView + RecyclerView measurement issues
            binding.recyclerBlacklistWords.getLayoutManager().requestLayout();
            binding.recyclerBlacklistWords.getParent().requestLayout();
        });

        // Load word replacements
        String replacementData = sharedPreferences.getString(KEY_WORD_REPLACEMENTS, "");
        wordReplacementItems.clear();
        if (!replacementData.isEmpty()) {
            String[] pairs = replacementData.split("\\|");
            for (String pair : pairs) {
                String[] parts = pair.split(":", 2);
                if (parts.length == 2) {
                    wordReplacementItems.add(new WordReplacementItem(parts[0], parts[1]));
                }
            }
        }
        wordReplacementAdapter.notifyDataSetChanged();
        updateCountDisplays();
        
        // Load media notification filtering settings
        boolean isMediaFilteringEnabled = sharedPreferences.getBoolean(KEY_MEDIA_FILTERING_ENABLED, true); // Default to enabled
        binding.switchMediaFiltering.setChecked(isMediaFilteringEnabled);
        binding.mediaFilteringSection.setVisibility(isMediaFilteringEnabled ? View.VISIBLE : View.GONE);
        
        // Load media excepted apps
        Set<String> mediaExceptedApps = sharedPreferences.getStringSet(KEY_MEDIA_FILTER_EXCEPTED_APPS, new HashSet<>());
        Set<String> mediaExceptedAppsPrivate = sharedPreferences.getStringSet(KEY_MEDIA_FILTER_EXCEPTED_APPS + "_private", new HashSet<>());
        
        mediaExceptedAppsList.clear();
        for (String app : mediaExceptedApps) {
            mediaExceptedAppsList.add(new AppFilterItem(app, mediaExceptedAppsPrivate.contains(app)));
        }
        mediaExceptedAppsAdapter.notifyDataSetChanged();
        
        // Load media important keywords
        Set<String> mediaImportantKeywords = sharedPreferences.getStringSet(KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS, new HashSet<>());
        Set<String> mediaImportantKeywordsPrivate = sharedPreferences.getStringSet(KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS + "_private", new HashSet<>());
        
        mediaImportantKeywordsList.clear();
        for (String keyword : mediaImportantKeywords) {
            mediaImportantKeywordsList.add(new WordFilterItem(keyword, mediaImportantKeywordsPrivate.contains(keyword)));
        }
        mediaImportantKeywordsAdapter.notifyDataSetChanged();
    }

    /**
     * Initialize default word blacklist entries for common notification patterns
     * that most users would want filtered out by default.
     */
    private void initializeDefaultWordBlacklist() {
        // Check if defaults have already been initialized
        boolean defaultsInitialized = sharedPreferences.getBoolean(KEY_DEFAULTS_INITIALIZED, false);
        if (defaultsInitialized) {
            return;
        }

        // Get existing blacklist words to avoid duplicates
        Set<String> existingBlockWords = sharedPreferences.getStringSet(KEY_WORD_BLACKLIST, new HashSet<>());
        Set<String> existingPrivateWords = sharedPreferences.getStringSet(KEY_WORD_BLACKLIST_PRIVATE, new HashSet<>());
        
        // Create new sets to avoid modifying the existing ones
        Set<String> newBlockWords = new HashSet<>(existingBlockWords);
        Set<String> newPrivateWords = new HashSet<>(existingPrivateWords);

        // System/Background notifications (block entirely)
        String[] systemWords = {
            "syncing new emails", "post sent", "updating location", 
            "sync complete", "sync finished", "now playing",
            "location updated", "location sharing", "location services"
        };

        // Media/Entertainment notifications (block entirely)
        String[] mediaWords = {
            "now playing", "media controls", "playback controls"
        };

        // Location/Safety notifications (block entirely)
        String[] locationWords = {
            "location updated", "location sharing", "location services"
        };

        // Battery/Power notifications (block entirely)
        String[] batteryWords = {
            // Removed generic terms that could appear in conversation
        };

        // Network/Connectivity notifications (block entirely)
        String[] networkWords = {
            // Removed generic terms that could appear in conversation
        };

        // Add all system words to block list
        for (String word : systemWords) {
            if (!newBlockWords.contains(word) && !newPrivateWords.contains(word)) {
                newBlockWords.add(word);
            }
        }

        // Add all media words to block list
        for (String word : mediaWords) {
            if (!newBlockWords.contains(word) && !newPrivateWords.contains(word)) {
                newBlockWords.add(word);
            }
        }

        // Add all location words to block list
        for (String word : locationWords) {
            if (!newBlockWords.contains(word) && !newPrivateWords.contains(word)) {
                newBlockWords.add(word);
            }
        }

        // Add all battery words to block list
        for (String word : batteryWords) {
            if (!newBlockWords.contains(word) && !newPrivateWords.contains(word)) {
                newBlockWords.add(word);
            }
        }

        // Add all network words to block list
        for (String word : networkWords) {
            if (!newBlockWords.contains(word) && !newPrivateWords.contains(word)) {
                newBlockWords.add(word);
            }
        }

        // Save the updated blacklist
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet(KEY_WORD_BLACKLIST, newBlockWords);
        editor.putStringSet(KEY_WORD_BLACKLIST_PRIVATE, newPrivateWords);
        editor.putBoolean(KEY_DEFAULTS_INITIALIZED, true);
        editor.apply();

        Log.d("FilterSettings", "Initialized default word blacklist with " + 
              (newBlockWords.size() - existingBlockWords.size()) + " new entries");
    }

    /**
     * Initialize default media filtering important keywords that indicate
     * important notifications that should not be filtered even during media playback.
     */
    private void initializeDefaultMediaKeywords() {
        // Check if defaults have already been initialized
        boolean defaultsInitialized = sharedPreferences.getBoolean(KEY_DEFAULTS_INITIALIZED, false);
        if (defaultsInitialized) {
            return;
        }

        // Get existing media important keywords
        Set<String> existingKeywords = sharedPreferences.getStringSet(KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS, new HashSet<>());
        Set<String> existingPrivateKeywords = sharedPreferences.getStringSet(KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS + "_private", new HashSet<>());
        
        // Create new sets to avoid modifying the existing ones
        Set<String> newKeywords = new HashSet<>(existingKeywords);
        Set<String> newPrivateKeywords = new HashSet<>(existingPrivateKeywords);

        // Keywords that indicate important notifications (not media controls)
        String[] importantKeywords = {
            "reply", "replied", "comment", "commented", "mention", "mentioned", 
            "tag", "tagged", "share", "shared", "message", "messaged", 
            "call", "called", "missed call", "urgent", "emergency", 
            "alert", "warning", "error", "failed", "success", 
            "completed", "finished", "new", "unread", "unopened", 
            "pending", "scheduled"
        };

        // Add all important keywords
        for (String keyword : importantKeywords) {
            if (!newKeywords.contains(keyword) && !newPrivateKeywords.contains(keyword)) {
                newKeywords.add(keyword);
            }
        }

        // Save the updated keywords
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet(KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS, newKeywords);
        editor.putStringSet(KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS + "_private", newPrivateKeywords);
        editor.apply();

        Log.d("FilterSettings", "Initialized default media filtering keywords with " + 
              (newKeywords.size() - existingKeywords.size()) + " new entries");
    }

    /**
     * Initialize default media exception apps that should never have
     * notifications filtered even during media playback.
     */
    private void initializeDefaultMediaExceptionApps() {
        // Check if defaults have already been initialized
        boolean defaultsInitialized = sharedPreferences.getBoolean(KEY_DEFAULTS_INITIALIZED, false);
        if (defaultsInitialized) {
            return;
        }

        // Get existing media exception apps
        Set<String> existingApps = sharedPreferences.getStringSet(KEY_MEDIA_FILTER_EXCEPTED_APPS, new HashSet<>());
        Set<String> existingPrivateApps = sharedPreferences.getStringSet(KEY_MEDIA_FILTER_EXCEPTED_APPS + "_private", new HashSet<>());
        
        // Create new sets to avoid modifying the existing ones
        Set<String> newApps = new HashSet<>(existingApps);
        Set<String> newPrivateApps = new HashSet<>(existingPrivateApps);

        // Apps that commonly have important notifications that shouldn't be filtered
        String[] exceptionApps = {
            "com.whatsapp",           // WhatsApp - important messages
            "com.facebook.orca",      // Facebook Messenger
            "com.instagram.android",  // Instagram - DMs and comments
            "com.twitter.android",    // Twitter - mentions and DMs
            "com.google.android.gm",  // Gmail - important emails
            "com.microsoft.office.outlook", // Outlook
            "com.skype.raider",       // Skype
            "com.discord",            // Discord
            "com.telegram.messenger", // Telegram
            "com.snapchat.android",   // Snapchat
            "com.google.android.apps.messaging", // Android Messages
            "com.android.phone",      // Phone app
            "com.android.incallui",   // In-call UI
            "com.android.server.telecom", // Telecom service
            "com.android.contacts",   // Contacts
            "com.android.calendar",   // Calendar
            "com.google.android.calendar", // Google Calendar
            "com.android.alarmclock", // Clock/Alarm
            "com.google.android.apps.clock", // Google Clock
            "com.android.systemui"    // System UI (calls, etc.)
        };

        // Add all exception apps
        for (String app : exceptionApps) {
            if (!newApps.contains(app) && !newPrivateApps.contains(app)) {
                newApps.add(app);
            }
        }

        // Save the updated exception apps
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet(KEY_MEDIA_FILTER_EXCEPTED_APPS, newApps);
        editor.putStringSet(KEY_MEDIA_FILTER_EXCEPTED_APPS + "_private", newPrivateApps);
        editor.apply();

        Log.d("FilterSettings", "Initialized default media exception apps with " + 
              (newApps.size() - existingApps.size()) + " new entries");
    }

    private void addApp() {
        String input = editAppName.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Please enter an app name or package name", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check for duplicates
        for (AppFilterItem item : appList) {
            if (item.packageName.equals(input)) {
                Toast.makeText(this, "App already in list", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Try to match input to a known app in the JSON (by displayName or packageName)
        AppListData matched = null;
        for (AppListData app : AppListManager.INSTANCE.loadAppList(this)) {
            if (app.displayName.equalsIgnoreCase(input) || app.packageName.equalsIgnoreCase(input)) {
                matched = app;
                break;
            }
        }
        String packageNameToAdd = (matched != null) ? matched.packageName : input;

        // Check for duplicates again (in case user entered displayName)
        for (AppFilterItem item : appList) {
            if (item.packageName.equals(packageNameToAdd)) {
                Toast.makeText(this, "App already in list", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        appList.add(new AppFilterItem(packageNameToAdd, false));
        appListAdapter.notifyDataSetChanged();
        updateCountDisplays();
        editAppName.setText("");
        saveAppList();
    }

    private void removeApp(int position) {
        appList.remove(position);
        appListAdapter.notifyDataSetChanged();
        updateCountDisplays();
        saveAppList();
    }

    private void toggleAppPrivate(int position) {
        AppFilterItem item = appList.get(position);
        item.isPrivate = !item.isPrivate;
        appListAdapter.notifyDataSetChanged();
        updateCountDisplays();
        saveAppList();
    }

    private void addBlacklistWord() {
        String word = binding.editBlacklistWord.getText().toString().trim();
        if (word.isEmpty()) {
            Toast.makeText(this, "Please enter a word", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedType = binding.spinnerBlacklistType.getSelectedItemPosition();
        boolean isPrivate = selectedType == 1; // 0 = Block, 1 = Private

        // Check for duplicates in UI list
        for (WordFilterItem item : wordBlacklistItems) {
            if (item.word.equals(word)) {
                Toast.makeText(this, "Word already in blacklist", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        // Check for duplicates in SharedPreferences sets (defensive)
        Set<String> blockWords = sharedPreferences.getStringSet(KEY_WORD_BLACKLIST, new HashSet<>());
        Set<String> privateWords = sharedPreferences.getStringSet(KEY_WORD_BLACKLIST_PRIVATE, new HashSet<>());
        if (blockWords.contains(word) || privateWords.contains(word)) {
            Toast.makeText(this, "Word already in blacklist (storage)", Toast.LENGTH_SHORT).show();
            // Optionally, reload the list to repair UI
            loadSettings();
            return;
        }

        wordBlacklistItems.add(new WordFilterItem(word, isPrivate));
        wordBlacklistAdapter.notifyDataSetChanged();
        updateCountDisplays();
        binding.editBlacklistWord.setText("");
        saveWordBlacklist();
        
        // Force layout refresh to ensure new item is visible
        binding.recyclerBlacklistWords.post(() -> {
            wordBlacklistAdapter.notifyDataSetChanged();
            binding.recyclerBlacklistWords.requestLayout();
            binding.recyclerBlacklistWords.getLayoutManager().requestLayout();
            binding.recyclerBlacklistWords.getParent().requestLayout();
        });
        
        // Always reload from storage to ensure UI and storage are in sync
        loadSettings();
    }

    private void removeBlacklistWord(int position) {
        wordBlacklistItems.remove(position);
        wordBlacklistAdapter.notifyDataSetChanged();
        updateCountDisplays();
        saveWordBlacklist();
        
        // Force layout refresh to ensure proper rendering
        binding.recyclerBlacklistWords.post(() -> {
            wordBlacklistAdapter.notifyDataSetChanged();
            binding.recyclerBlacklistWords.requestLayout();
            binding.recyclerBlacklistWords.getLayoutManager().requestLayout();
            binding.recyclerBlacklistWords.getParent().requestLayout();
        });
    }

    private void onWordTypeChange(int position, boolean isPrivate) {
        if (position >= 0 && position < wordBlacklistItems.size()) {
            wordBlacklistItems.get(position).isPrivate = isPrivate;
            saveWordBlacklist();
        }
    }

    private void addWordReplacement() {
        String from = binding.editReplaceFrom.getText().toString().trim();
        String to = binding.editReplaceTo.getText().toString().trim();
        
        if (from.isEmpty()) {
            Toast.makeText(this, "Please enter a word or phrase to replace", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check for duplicates and loops
        for (WordReplacementItem item : wordReplacementItems) {
            if (item.from.equals(from)) {
                Toast.makeText(this, "Replacement already exists", Toast.LENGTH_SHORT).show();
                return;
            }
            if (item.to.equals(from) && item.from.equals(to)) {
                Toast.makeText(this, "This would create a replacement loop", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        wordReplacementItems.add(new WordReplacementItem(from, to));
        wordReplacementAdapter.notifyDataSetChanged();
        updateCountDisplays();
        binding.editReplaceFrom.setText("");
        binding.editReplaceTo.setText("");
        
        saveWordReplacements();
    }

    private void removeWordReplacement(int position) {
        wordReplacementItems.remove(position);
        wordReplacementAdapter.notifyDataSetChanged();
        updateCountDisplays();
        saveWordReplacements();
    }

    private void editApp(int position) {
        if (position >= 0 && position < appList.size()) {
            AppFilterItem item = appList.get(position);
            showEditAppDialog(item, position);
        }
    }

    private void editBlacklistWord(int position) {
        if (position >= 0 && position < wordBlacklistItems.size()) {
            WordFilterItem item = wordBlacklistItems.get(position);
            showEditWordDialog(item, position);
        }
    }

    private void editWordReplacement(int position) {
        if (position >= 0 && position < wordReplacementItems.size()) {
            WordReplacementItem item = wordReplacementItems.get(position);
            showEditWordReplacementDialog(item, position);
        }
    }
    
    // Media filtering methods
    private void addMediaExceptedApp() {
        String appName = binding.editMediaExceptedApp.getText().toString().trim();
        if (appName.isEmpty()) {
            Toast.makeText(this, "Please select an app", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Find the app info
        AppInfo selectedApp = null;
        for (AppInfo app : installedApps) {
            if (app.appName.equals(appName)) {
                selectedApp = app;
                break;
            }
        }
        
        if (selectedApp == null) {
            Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check if already in list
        for (AppFilterItem item : mediaExceptedAppsList) {
            if (item.packageName.equals(selectedApp.packageName)) {
                Toast.makeText(this, "App already in exception list", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        // Add to list
        mediaExceptedAppsList.add(new AppFilterItem(selectedApp.packageName, false));
        mediaExceptedAppsAdapter.notifyDataSetChanged();
        saveMediaExceptedApps();
        
        // Clear input
        binding.editMediaExceptedApp.setText("");
        
        Toast.makeText(this, "Added " + selectedApp.appName + " to media exception list", Toast.LENGTH_SHORT).show();
    }
    
    private void removeMediaExceptedApp(int position) {
        AppFilterItem item = mediaExceptedAppsList.get(position);
        mediaExceptedAppsList.remove(position);
        mediaExceptedAppsAdapter.notifyDataSetChanged();
        saveMediaExceptedApps();
        
        Toast.makeText(this, "Removed " + item.packageName + " from media exception list", Toast.LENGTH_SHORT).show();
    }
    
    private void toggleMediaExceptedAppPrivate(int position) {
        AppFilterItem item = mediaExceptedAppsList.get(position);
        item.isPrivate = !item.isPrivate;
        mediaExceptedAppsAdapter.notifyDataSetChanged();
        saveMediaExceptedApps();
    }
    
    private void editMediaExceptedApp(int position) {
        AppFilterItem item = mediaExceptedAppsList.get(position);
        showEditMediaExceptedAppDialog(item, position);
    }
    
    private void addMediaImportantKeyword() {
        String keyword = binding.editMediaImportantKeyword.getText().toString().trim();
        if (keyword.isEmpty()) {
            Toast.makeText(this, "Please enter a keyword", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Check if already in list
        for (WordFilterItem item : mediaImportantKeywordsList) {
            if (item.word.equals(keyword)) {
                Toast.makeText(this, "Keyword already in list", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        // Add to list
        mediaImportantKeywordsList.add(new WordFilterItem(keyword, false));
        mediaImportantKeywordsAdapter.notifyDataSetChanged();
        saveMediaImportantKeywords();
        
        // Clear input
        binding.editMediaImportantKeyword.setText("");
        
        Toast.makeText(this, "Added keyword: " + keyword, Toast.LENGTH_SHORT).show();
    }
    
    private void removeMediaImportantKeyword(int position) {
        WordFilterItem item = mediaImportantKeywordsList.get(position);
        mediaImportantKeywordsList.remove(position);
        mediaImportantKeywordsAdapter.notifyDataSetChanged();
        saveMediaImportantKeywords();
        
        Toast.makeText(this, "Removed keyword: " + item.word, Toast.LENGTH_SHORT).show();
    }
    
    private void onMediaKeywordTypeChange(int position, boolean isPrivate) {
        WordFilterItem item = mediaImportantKeywordsList.get(position);
        item.isPrivate = isPrivate;
        mediaImportantKeywordsAdapter.notifyDataSetChanged();
        saveMediaImportantKeywords();
    }
    
    private void editMediaImportantKeyword(int position) {
        WordFilterItem item = mediaImportantKeywordsList.get(position);
        showEditMediaImportantKeywordDialog(item, position);
    }
    
    private void showEditMediaExceptedAppDialog(AppFilterItem item, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Media Exception App");
        
        // Create input field
        final EditText input = new EditText(this);
        input.setText(item.packageName);
        input.setHint("Package name (e.g., com.android.chrome)");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSelection(input.getText().length()); // Move cursor to end
        
        // Add padding
        input.setPadding(50, 30, 50, 30);
        builder.setView(input);
        
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newPackageName = input.getText().toString().trim();
            if (!newPackageName.isEmpty()) {
                // Check for duplicates (excluding current item)
                for (int i = 0; i < mediaExceptedAppsList.size(); i++) {
                    if (i != position && mediaExceptedAppsList.get(i).packageName.equals(newPackageName)) {
                        Toast.makeText(this, "App already in exception list", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                
                // Update the item
                item.packageName = newPackageName;
                mediaExceptedAppsAdapter.notifyItemChanged(position);
                saveMediaExceptedApps();
                Toast.makeText(this, "Media exception app updated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Package name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
    
    private void showEditMediaImportantKeywordDialog(WordFilterItem item, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Media Important Keyword");
        
        // Create input field
        final EditText input = new EditText(this);
        input.setText(item.word);
        input.setHint("Important keyword (e.g., reply, comment)");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSelection(input.getText().length()); // Move cursor to end
        
        // Add padding
        input.setPadding(50, 30, 50, 30);
        builder.setView(input);
        
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newKeyword = input.getText().toString().trim();
            if (!newKeyword.isEmpty()) {
                // Check for duplicates (excluding current item)
                for (int i = 0; i < mediaImportantKeywordsList.size(); i++) {
                    if (i != position && mediaImportantKeywordsList.get(i).word.equals(newKeyword)) {
                        Toast.makeText(this, "Keyword already in list", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                
                // Update the item
                item.word = newKeyword;
                mediaImportantKeywordsAdapter.notifyItemChanged(position);
                saveMediaImportantKeywords();
                Toast.makeText(this, "Media important keyword updated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Keyword cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showEditAppDialog(AppFilterItem item, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit App Filter");
        
        // Create input field
        final EditText input = new EditText(this);
        input.setText(item.packageName);
        input.setHint("Package name (e.g., com.android.chrome)");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSelection(input.getText().length()); // Move cursor to end
        
        // Add padding
        input.setPadding(50, 30, 50, 30);
        builder.setView(input);
        
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newPackageName = input.getText().toString().trim();
            if (!newPackageName.isEmpty()) {
                // Check for duplicates (excluding current item)
                for (int i = 0; i < appList.size(); i++) {
                    if (i != position && appList.get(i).packageName.equals(newPackageName)) {
                        Toast.makeText(this, "App already in list", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                
                // Update the item
                item.packageName = newPackageName;
                appListAdapter.notifyItemChanged(position);
                saveAppList();
                Toast.makeText(this, "App filter updated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Package name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showEditWordDialog(WordFilterItem item, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Word Filter");
        
        // Create input field
        final EditText input = new EditText(this);
        input.setText(item.word);
        input.setHint("Word or phrase to filter");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSelection(input.getText().length()); // Move cursor to end
        
        // Add padding
        input.setPadding(50, 30, 50, 30);
        builder.setView(input);
        
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newWord = input.getText().toString().trim();
            if (!newWord.isEmpty()) {
                // Check for duplicates (excluding current item)
                for (int i = 0; i < wordBlacklistItems.size(); i++) {
                    if (i != position && wordBlacklistItems.get(i).word.equals(newWord)) {
                        Toast.makeText(this, "Word already in blacklist", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                
                // Update the item
                item.word = newWord;
                wordBlacklistAdapter.notifyItemChanged(position);
                saveWordBlacklist();
                Toast.makeText(this, "Word filter updated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Word cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showEditWordReplacementDialog(WordReplacementItem item, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Word Replacement");
        
        // Create layout for two input fields
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);
        
        // From field
        final EditText inputFrom = new EditText(this);
        inputFrom.setText(item.from);
        inputFrom.setHint("Replace this word/phrase");
        inputFrom.setInputType(InputType.TYPE_CLASS_TEXT);
        inputFrom.setSelection(inputFrom.getText().length());
        layout.addView(inputFrom);
        
        // Add some spacing
        TextView spacer = new TextView(this);
        spacer.setText("↓");
        spacer.setGravity(Gravity.CENTER);
        spacer.setPadding(0, 20, 0, 20);
        spacer.setTextColor(getResources().getColor(R.color.brand_primary, null));
        spacer.setTextSize(18);
        layout.addView(spacer);
        
        // To field
        final EditText inputTo = new EditText(this);
        inputTo.setText(item.to);
        inputTo.setHint("With this word/phrase");
        inputTo.setInputType(InputType.TYPE_CLASS_TEXT);
        inputTo.setSelection(inputTo.getText().length());
        layout.addView(inputTo);
        
        builder.setView(layout);
        
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newFrom = inputFrom.getText().toString().trim();
            String newTo = inputTo.getText().toString().trim();
            
            if (newFrom.isEmpty()) {
                Toast.makeText(this, "Please enter a word or phrase to replace", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Check for duplicates (excluding current item)
            for (int i = 0; i < wordReplacementItems.size(); i++) {
                if (i != position && wordReplacementItems.get(i).from.equals(newFrom)) {
                    Toast.makeText(this, "Replacement already exists", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            // Check for replacement loops
            for (int i = 0; i < wordReplacementItems.size(); i++) {
                if (i != position) {
                    WordReplacementItem otherItem = wordReplacementItems.get(i);
                    if (otherItem.to.equals(newFrom) && otherItem.from.equals(newTo)) {
                        Toast.makeText(this, "This would create a replacement loop", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }
            
            // Update the item
            item.from = newFrom;
            item.to = newTo;
            wordReplacementAdapter.notifyItemChanged(position);
            saveWordReplacements();
            Toast.makeText(this, "Word replacement updated", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void saveAppListMode(String mode) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_APP_LIST_MODE, mode);
        editor.apply();
    }

    private void saveAppList() {
        Set<String> apps = new HashSet<>();
        Set<String> privateApps = new HashSet<>();
        
        for (AppFilterItem item : appList) {
            apps.add(item.packageName);
            if (item.isPrivate) {
                privateApps.add(item.packageName);
            }
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet(KEY_APP_LIST, apps);
        editor.putStringSet(KEY_APP_PRIVATE_FLAGS, privateApps);
        editor.apply();
    }

    private void saveWordBlacklist() {
        Set<String> blockWords = new HashSet<>();
        Set<String> privateWords = new HashSet<>();
        
        for (WordFilterItem item : wordBlacklistItems) {
            if (item.isPrivate) {
                privateWords.add(item.word);
            } else {
                blockWords.add(item.word);
            }
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet(KEY_WORD_BLACKLIST, blockWords);
        editor.putStringSet(KEY_WORD_BLACKLIST_PRIVATE, privateWords);
        editor.apply();
    }

    private void saveWordReplacements() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wordReplacementItems.size(); i++) {
            WordReplacementItem item = wordReplacementItems.get(i);
            if (i > 0) sb.append("|");
            sb.append(item.from).append(":").append(item.to);
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_WORD_REPLACEMENTS, sb.toString());
        editor.apply();
    }
    
    private void saveMediaExceptedApps() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        
        Set<String> packageNames = new HashSet<>();
        Set<String> privateFlags = new HashSet<>();
        
        for (AppFilterItem item : mediaExceptedAppsList) {
            packageNames.add(item.packageName);
            if (item.isPrivate) {
                privateFlags.add(item.packageName);
            }
        }
        
        editor.putStringSet(KEY_MEDIA_FILTER_EXCEPTED_APPS, packageNames);
        editor.putStringSet(KEY_MEDIA_FILTER_EXCEPTED_APPS + "_private", privateFlags);
        editor.apply();
    }
    
    private void saveMediaImportantKeywords() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        
        Set<String> keywords = new HashSet<>();
        Set<String> privateKeywords = new HashSet<>();
        
        for (WordFilterItem item : mediaImportantKeywordsList) {
            keywords.add(item.word);
            if (item.isPrivate) {
                privateKeywords.add(item.word);
            }
        }
        
        editor.putStringSet(KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS, keywords);
        editor.putStringSet(KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS + "_private", privateKeywords);
        editor.apply();
    }
    
    private void saveMediaFilteringEnabled(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_MEDIA_FILTERING_ENABLED, enabled);
        editor.apply();
    }
    
    private void toggleAdvancedOptions() {
        boolean isVisible = binding.advancedOptionsContent.getVisibility() == View.VISIBLE;
        
        if (isVisible) {
            // Collapse
            binding.advancedOptionsContent.setVisibility(View.GONE);
            binding.iconAdvancedOptions.setImageResource(android.R.drawable.arrow_down_float);
        } else {
            // Expand
            binding.advancedOptionsContent.setVisibility(View.VISIBLE);
            binding.iconAdvancedOptions.setImageResource(android.R.drawable.arrow_up_float);
        }
    }

    private void toggleAppList() {
        boolean isVisible = binding.appListContent.getVisibility() == View.VISIBLE;
        
        if (isVisible) {
            // Collapse
            binding.appListContent.setVisibility(View.GONE);
            binding.iconAppList.setImageResource(android.R.drawable.arrow_down_float);
        } else {
            // Expand
            binding.appListContent.setVisibility(View.VISIBLE);
            binding.iconAppList.setImageResource(android.R.drawable.arrow_up_float);
        }
    }

    private void toggleBlacklist() {
        boolean isVisible = binding.blacklistContent.getVisibility() == View.VISIBLE;
        
        if (isVisible) {
            // Collapse
            binding.blacklistContent.setVisibility(View.GONE);
            binding.iconBlacklist.setImageResource(android.R.drawable.arrow_down_float);
        } else {
            // Expand
            binding.blacklistContent.setVisibility(View.VISIBLE);
            binding.iconBlacklist.setImageResource(android.R.drawable.arrow_up_float);
        }
    }

    private void toggleReplacement() {
        boolean isVisible = binding.replacementContent.getVisibility() == View.VISIBLE;
        
        if (isVisible) {
            // Collapse
            binding.replacementContent.setVisibility(View.GONE);
            binding.iconReplacement.setImageResource(android.R.drawable.arrow_down_float);
        } else {
            // Expand
            binding.replacementContent.setVisibility(View.VISIBLE);
            binding.iconReplacement.setImageResource(android.R.drawable.arrow_up_float);
        }
    }

    private void updateCountDisplays() {
        // Update app list count
        binding.txtAppListCount.setText("(" + appList.size() + " apps)");
        
        // Update blacklist count
        binding.txtBlacklistCount.setText("(" + wordBlacklistItems.size() + " words)");
        
        // Update replacement count
        binding.txtReplacementCount.setText("(" + wordReplacementItems.size() + " replacements)");
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (localBroadcastManager != null && repairBlacklistReceiver != null) {
            localBroadcastManager.unregisterReceiver(repairBlacklistReceiver);
        }
        binding = null;
    }

    // Data classes
    public static class AppFilterItem {
        public String packageName;
        public boolean isPrivate;

        public AppFilterItem(String packageName, boolean isPrivate) {
            this.packageName = packageName;
            this.isPrivate = isPrivate;
        }
    }

    public static class WordFilterItem {
        public String word;
        public boolean isPrivate;

        public WordFilterItem(String word, boolean isPrivate) {
            this.word = word;
            this.isPrivate = isPrivate;
        }
    }

    public static class WordReplacementItem {
        public String from;
        public String to;

        public WordReplacementItem(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }

    // Interfaces for callbacks
    public interface OnAppActionListener {
        void onAction(int position);
    }

    public interface OnWordActionListener {
        void onAction(int position);
    }

    private void initializeFilePicker() {
        importFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        importFilterConfig(uri);
                    }
                }
            }
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_filter_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_export_filters) {
            exportFilterConfig();
            return true;
        } else if (id == R.id.action_import_filters) {
            importFilterConfigDialog();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void exportFilterConfig() {
        try {
            // Generate export data
            String configData = FilterConfigManager.exportFilters(this);
            
            // Create filename with timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());
            String filename = "SpeakThat_Filters_" + timestamp + ".json";
            
            // Save to external files directory
            File exportDir = new File(getExternalFilesDir(null), "exports");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            
            File exportFile = new File(exportDir, filename);
            FileWriter writer = new FileWriter(exportFile);
            writer.write(configData);
            writer.close();
            
            // Create content URI using FileProvider for security
            Uri fileUri = FileProvider.getUriForFile(this, 
                "com.micoyc.speakthat.fileprovider", 
                exportFile);
            
            // Create share intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "SpeakThat! Filter Configuration");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Filter configuration backup created on " + 
                                new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(shareIntent, "Export Filter Configuration"));
            
            // Show success message with summary
            String summary = FilterConfigManager.getFilterSummary(this);
            new AlertDialog.Builder(this)
                .setTitle("Export Successful")
                .setMessage("Filter configuration exported to:\n" + exportFile.getAbsolutePath() + 
                           "\n\nExported settings:\n" + summary)
                .setPositiveButton("OK", null)
                .show();
            
            InAppLogger.log("FilterConfig", "Filter configuration exported to " + filename);
            
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            InAppLogger.logError("FilterConfig", "Export failed: " + e.getMessage());
        }
    }
    
    private void importFilterConfigDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Import Filter Configuration")
            .setMessage("This will replace your current filter settings with the imported configuration.\n\nCurrent settings:\n" + 
                       FilterConfigManager.getFilterSummary(this) + "\n\nDo you want to continue?")
            .setPositiveButton("Select File", (dialog, which) -> openFilePicker())
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        // Add JSON MIME type
        String[] mimeTypes = {"application/json", "text/plain", "*/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        importFileLauncher.launch(Intent.createChooser(intent, "Select Filter Configuration File"));
    }
    
    private void importFilterConfig(Uri uri) {
        try {
            // Read file content
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder content = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            reader.close();
            inputStream.close();
            
            // Import the configuration
            FilterConfigManager.ImportResult result = FilterConfigManager.importFilters(this, content.toString());
            
            if (result.success) {
                // Refresh the UI to show imported settings
                loadSettings();
                
                // Show success dialog
                new AlertDialog.Builder(this)
                    .setTitle("Import Successful")
                    .setMessage(result.message + "\n\nNew settings:\n" + FilterConfigManager.getFilterSummary(this))
                    .setPositiveButton("OK", null)
                    .show();
                
                Toast.makeText(this, "Filter configuration imported successfully!", Toast.LENGTH_SHORT).show();
            } else {
                // Show error dialog
                new AlertDialog.Builder(this)
                    .setTitle("Import Failed")
                    .setMessage(result.message)
                    .setPositiveButton("OK", null)
                    .show();
            }
            
        } catch (IOException e) {
            Toast.makeText(this, "Failed to read file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            InAppLogger.logError("FilterConfig", "Import file read failed: " + e.getMessage());
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            InAppLogger.logError("FilterConfig", "Import failed: " + e.getMessage());
        }
    }

    private void initializeViews() {
        editAppName = findViewById(R.id.editAppName);
    }

    private void initializeAppSelector() {
        // Load apps from JSON file
        List<AppListData> jsonApps = AppListManager.INSTANCE.loadAppList(this);
        
        if (jsonApps != null && !jsonApps.isEmpty()) {
            // Create adapter with JSON data
            AppSearchAdapter jsonAdapter = new AppSearchAdapter(this, jsonApps);
            editAppName.setAdapter(jsonAdapter);
            editAppName.setThreshold(1); // Show suggestions after 1 character
            
            // Debug: Test the adapter
            InAppLogger.log("AppSelector", "Adapter created with " + jsonApps.size() + " apps");
            InAppLogger.log("AppSelector", "First few apps: " + 
                (jsonApps.size() > 0 ? jsonApps.get(0).displayName : "none") + ", " +
                (jsonApps.size() > 1 ? jsonApps.get(1).displayName : "none") + ", " +
                (jsonApps.size() > 2 ? jsonApps.get(2).displayName : "none"));
            
            // Handle app selection
            editAppName.setOnItemClickListener((parent, view, position, id) -> {
                AppListData selectedApp = jsonAdapter.getItem(position);
                if (selectedApp != null) {
                    editAppName.setText(selectedApp.packageName);
                    editAppName.setSelection(selectedApp.packageName.length());
                    InAppLogger.log("AppSelector", "Selected app: " + selectedApp.displayName + " (" + selectedApp.packageName + ")");
                }
            });
            
            // Test dropdown functionality
            editAppName.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    InAppLogger.log("AppSelector", "EditText focused - should show dropdown");
                    // Force show dropdown with first few items
                    editAppName.showDropDown();
                }
            });
            
            // Test: Add a button to manually trigger dropdown
            binding.btnAddApp.setOnClickListener(v -> {
                InAppLogger.log("AppSelector", "Add button clicked - testing dropdown");
                editAppName.showDropDown();
            });
            
            InAppLogger.log("AppSelector", "App selector initialized with " + jsonApps.size() + " apps from JSON");
        } else {
            // Fallback to old system if JSON loading fails
            installedApps = getCommonApps();
            
            if (installedApps != null && !installedApps.isEmpty()) {
                appSelectorAdapter = new AppAutoCompleteAdapter(this, installedApps);
                editAppName.setAdapter(appSelectorAdapter);
                editAppName.setThreshold(1);
                
                editAppName.setOnItemClickListener((parent, view, position, id) -> {
                    AppInfo selectedApp = appSelectorAdapter.getItem(position);
                    if (selectedApp != null) {
                        editAppName.setText(selectedApp.packageName);
                        editAppName.setSelection(selectedApp.packageName.length());
                        InAppLogger.log("AppSelector", "Selected app: " + selectedApp.appName + " (" + selectedApp.packageName + ")");
                    }
                });
                
                InAppLogger.log("AppSelector", "Fallback: App selector initialized with " + installedApps.size() + " common apps");
            } else {
                InAppLogger.logError("AppSelector", "No apps loaded for selector");
            }
        }
    }
    
    private List<AppInfo> getCommonApps() {
        List<AppInfo> apps = new ArrayList<>();
        PackageManager pm = getPackageManager();
        
                 // Common notification-heavy apps that users typically want to filter
         // Focused on apps that send frequent updates, location tracking, or repetitive notifications
         String[][] commonApps = {
             // Navigation & Location Apps
             {"Google Maps", "com.google.android.apps.maps"},
             {"Waze", "com.waze"},
             {"Life360", "com.life360.android.safetymapd"},
             {"Find My Device", "com.google.android.apps.adm"},
             
             // Ride Sharing & Delivery Apps
             {"Uber", "com.ubercab"},
             {"Lyft", "me.lyft.android"},
             {"DoorDash", "com.dd.doordash"},
             {"Uber Eats", "com.ubercab.eats"},
             {"Grubhub", "com.grubhub.android"},
             {"Postmates", "com.postmates.android"},
             
             // Messaging Apps (can be very chatty)
             {"WhatsApp", "com.whatsapp"},
             {"Discord", "com.discord"},
             {"Telegram", "org.telegram.messenger"},
             {"Signal", "org.thoughtcrime.securesms"},
             {"Slack", "com.Slack"},
             {"Microsoft Teams", "com.microsoft.teams"},
             
             // Social Media (frequent notifications)
             {"Facebook", "com.facebook.katana"},
             {"Instagram", "com.instagram.android"},
             {"Twitter", "com.twitter.android"},
             {"TikTok", "com.zhiliaoapp.musically"},
             {"Snapchat", "com.snapchat.android"},
             
             // Shopping & Payment (order updates)
             {"Amazon", "com.amazon.mShop.android.shopping"},
             {"Google Pay", "com.google.android.apps.nfc.payment"},
             {"PayPal", "com.paypal.android.p2pmobile"},
             {"Venmo", "com.venmo"},
             
             // Google Services (system notifications)
             {"Gmail", "com.google.android.gm"},
             {"YouTube", "com.google.android.youtube"},
             {"Google Play Store", "com.android.vending"},
             
             // Work/Meeting Apps
             {"Zoom", "us.zoom.videomeetings"},
             {"Skype", "com.skype.raider"},
             
             // Entertainment (progress updates)
             {"Spotify", "com.spotify.music"},
             {"Netflix", "com.netflix.mediaclient"}
         };
        
        try {
            for (String[] appData : commonApps) {
                String appName = appData[0];
                String packageName = appData[1];
                
                                 // Try to get the real app icon and name if installed
                 try {
                     ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                     android.graphics.drawable.Drawable icon = pm.getApplicationIcon(appInfo);
                     String realName = pm.getApplicationLabel(appInfo).toString();
                     apps.add(new AppInfo(realName, packageName, icon));
                     InAppLogger.log("AppSelector", "Found installed app: " + realName + " (" + packageName + ")");
                 } catch (Exception e) {
                     // Can't detect if app is installed (Android 11+ restrictions), but filtering still works
                     android.graphics.drawable.Drawable icon = pm.getDefaultActivityIcon();
                     apps.add(new AppInfo(appName, packageName, icon));
                 }
            }
            
            // Sort alphabetically
            apps.sort((a, b) -> a.appName.compareToIgnoreCase(b.appName));
            
            InAppLogger.log("AppSelector", "Loaded " + apps.size() + " common apps for selector");
            
        } catch (Exception e) {
            InAppLogger.logError("AppSelector", "Error loading common apps: " + e.getMessage());
        }
        
        return apps;
    }

    private void showAppFilterHelp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📱 App Filter Help")
               .setMessage("How App Filtering Works:\n\n" +
                          "🔍 Search Function:\n" +
                          "• Search through thousands of apps by name, package, or category\n" +
                          "• You can type any Android package name manually\n" +
                          "• Click a suggestion to instantly add the package name\n" +
                          "• Filtering works even if an app doesn't appear in search\n\n" +
                          
                          "📋 Package Names:\n" +
                          "• Find package names in: Settings → Apps → [App Name] → Advanced\n" +
                          "• Examples: com.whatsapp, com.discord, com.google.android.apps.maps\n" +
                          "• You can also search by app name (e.g., 'WhatsApp', 'Discord')\n\n" +
                          
                          "⚠️ Android 11+ Limitation:\n" +
                          "• Apps cannot see what's installed on your device\n" +
                          "• Search shows popular apps, but all package names work\n" +
                          "• This is an Android privacy feature, not a bug\n\n" +
                          
                          "✅ Filtering Effectiveness:\n" +
                          "• Works perfectly regardless of search results\n" +
                          "• Matches the actual package name from notifications\n" +
                          "• Test by adding an app, then checking if its notifications are filtered")
               .setPositiveButton("Got it!", (dialog, which) -> dialog.dismiss())
               .show();
        
        InAppLogger.log("FilterSettings", "App filter help dialog shown");
    }
    
    private void showMediaFilterHelp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎵 Media Notification Filter Help")
               .setMessage("How Media Notification Filtering Works:\n\n" +
                          "🔍 Automatic Detection:\n" +
                          "• Detects music players, video apps, and media controls\n" +
                          "• Identifies playback bars and media session notifications\n" +
                          "• Uses content patterns, app categories, and media flags\n\n" +
                          
                          "📱 Exception Apps:\n" +
                          "• Apps that should never have notifications filtered\n" +
                          "• Useful for apps like YouTube where you want replies but not playback controls\n" +
                          "• Add apps that send both media and important notifications\n\n" +
                          
                          "🔑 Important Keywords:\n" +
                          "• Words that indicate important notifications (like 'reply', 'comment')\n" +
                          "• Notifications containing these words won't be filtered\n" +
                          "• Helps preserve social media updates while filtering media controls\n\n" +
                          
                          "✅ Benefits:\n" +
                          "• Prevents annoying media control notifications\n" +
                          "• Keeps important social media updates\n" +
                          "• Works automatically without manual configuration\n" +
                          "• Customizable exceptions for specific needs")
               .setPositiveButton("Got it!", (dialog, which) -> dialog.dismiss())
               .show();
        
        InAppLogger.log("FilterSettings", "Media filter help dialog shown");
    }
} 