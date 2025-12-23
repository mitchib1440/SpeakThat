package com.micoyc.speakthat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
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

public class FilterSettingsActivity extends AppCompatActivity {
    private ActivityFilterSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    private androidx.activity.result.ActivityResultLauncher<Intent> appListPickerLauncher;
    private androidx.activity.result.ActivityResultLauncher<Intent> filteredMediaPickerLauncher;
    // File picker for import
    private ActivityResultLauncher<Intent> importFileLauncher;
    
    // SharedPreferences keys
    private static final String PREFS_NAME = "SpeakThatPrefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_APP_LIST_MODE = "app_list_mode";
    private static final String KEY_APP_LIST = "app_list";
    private static final String KEY_APP_PRIVATE_FLAGS = "app_private_flags";
    private static final String KEY_WORD_BLACKLIST = "word_blacklist";
    private static final String KEY_WORD_BLACKLIST_PRIVATE = "word_blacklist_private";
    private static final String KEY_WORD_REPLACEMENTS = "word_replacements";
    private static final String KEY_URL_HANDLING_MODE = "url_handling_mode";
    private static final String KEY_URL_REPLACEMENT_TEXT = "url_replacement_text";
    private static final String DEFAULT_URL_HANDLING_MODE = "domain_only";
    private static final String DEFAULT_URL_REPLACEMENT_TEXT = "";
    private static final String KEY_DEFAULTS_INITIALIZED = "defaults_initialized";
    
    // Media notification filtering keys
    private static final String KEY_MEDIA_FILTERING_ENABLED = "media_filtering_enabled";
    private static final String KEY_MEDIA_FILTER_EXCEPTED_APPS = "media_filter_excepted_apps";
    private static final String KEY_MEDIA_FILTER_IMPORTANT_KEYWORDS = "media_filter_important_keywords";
    private static final String KEY_MEDIA_FILTERED_APPS = "media_filtered_apps";
    private static final String KEY_MEDIA_FILTERED_APPS_PRIVATE = "media_filtered_apps_private";
    
    // Persistent/Silent notification filtering key
    private static final String KEY_PERSISTENT_FILTERING_ENABLED = "persistent_filtering_enabled";
    
    // Individual persistent filtering category keys
    private static final String KEY_FILTER_PERSISTENT = "filter_persistent";
    private static final String KEY_FILTER_SILENT = "filter_silent";
    private static final String KEY_FILTER_FOREGROUND_SERVICES = "filter_foreground_services";
    private static final String KEY_FILTER_LOW_PRIORITY = "filter_low_priority";
    private static final String KEY_FILTER_SYSTEM_NOTIFICATIONS = "filter_system_notifications";

    // Adapters
    private AppListAdapter appListAdapter;
    private WordListAdapter wordBlacklistAdapter;
    private WordSwapAdapter wordSwapAdapter;
    private AppListAdapter mediaExceptedAppsAdapter;
    private WordListAdapter mediaImportantKeywordsAdapter;
    private AppListAdapter filteredMediaAppsAdapter;

    // Data lists
    private List<AppFilterItem> appList = new ArrayList<>();
    private List<WordFilterItem> wordBlacklistItems = new ArrayList<>();
    private List<WordReplacementItem> wordReplacementItems = new ArrayList<>();
    
    // URL handling variables
    private String urlHandlingMode = DEFAULT_URL_HANDLING_MODE;
    private String urlReplacementText = DEFAULT_URL_REPLACEMENT_TEXT;
    
    private List<AppFilterItem> mediaExceptedAppsList = new ArrayList<>();
    private List<WordFilterItem> mediaImportantKeywordsList = new ArrayList<>();
    private List<AppFilterItem> filteredMediaAppsList = new ArrayList<>();

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

        appListPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> selected = result.getData().getStringArrayListExtra(AppPickerActivity.EXTRA_SELECTED_PACKAGES);
                    ArrayList<String> privatePackages = result.getData().getStringArrayListExtra(AppPickerActivity.EXTRA_PRIVATE_PACKAGES);
                    if (selected != null) {
                        Set<String> privateSet = privatePackages != null ? new HashSet<>(privatePackages) : new HashSet<>();
                        appList.clear();
                        for (String pkg : selected) {
                            appList.add(new AppFilterItem(pkg, privateSet.contains(pkg)));
                        }
                        appListAdapter.notifyDataSetChanged();
                        saveAppList();
                        updateCountDisplays();
                    }
                }
            }
        );

        filteredMediaPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> selected = result.getData().getStringArrayListExtra(AppPickerActivity.EXTRA_SELECTED_PACKAGES);
                    if (selected != null) {
                        Set<String> privateExisting = new HashSet<>();
                        for (AppFilterItem item : filteredMediaAppsList) {
                            if (item.isPrivate) {
                                privateExisting.add(item.packageName);
                            }
                        }
                        filteredMediaAppsList.clear();
                        for (String pkg : selected) {
                            filteredMediaAppsList.add(new AppFilterItem(pkg, privateExisting.contains(pkg)));
                        }
                        filteredMediaAppsAdapter.notifyDataSetChanged();
                        saveFilteredMediaApps();
                        updateCountDisplays();
                    }
                }
            }
        );

        // Set up toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_filter_settings);
        }

        initializeUI();
        loadSettings();
        initializeFilePicker();

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
        boolean isDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false); // Default to light mode
        
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
            
            // R.string.button_save setting
            saveAppListMode(mode);
        });

        // Set up RecyclerViews
        setupAppListRecycler();
        setupWordBlacklistRecycler();
        setupWordReplacementRecycler();
        setupMediaFilteringRecyclers();
        setupFilteredMediaAppsRecycler();

        // Set up button listeners
        binding.btnManageAppList.setOnClickListener(v -> openAppListPicker());
        binding.txtAppFilterHelp.setOnClickListener(v -> showAppFilterHelp());
        binding.btnAddBlacklistWord.setOnClickListener(v -> addBlacklistWord());
        binding.btnAddReplacement.setOnClickListener(v -> addWordReplacement());
        
        // Set up media filtering button listeners
        binding.btnAddMediaExceptedApp.setOnClickListener(v -> addMediaExceptedApp());
        binding.btnAddMediaImportantKeyword.setOnClickListener(v -> addMediaImportantKeyword());
        binding.btnManageFilteredMediaApps.setOnClickListener(v -> openFilteredMediaAppsPicker());
        binding.txtMediaFilterHelp.setOnClickListener(v -> showMediaFilterHelp());
        
        // Set up media filtering switch
        binding.switchMediaFiltering.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.mediaFilteringSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            saveMediaFilteringEnabled(isChecked);
        });
        
        // Set up persistent/silent notification filtering switch
        binding.switchPersistentFiltering.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.persistentFilteringCategories.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            savePersistentFilteringEnabled(isChecked);
        });
        
        // Set up individual persistent filtering category switches
        binding.switchFilterPersistent.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveFilterPersistent(isChecked);
        });
        
        binding.switchFilterSilent.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveFilterSilent(isChecked);
        });
        
        binding.switchFilterForegroundServices.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveFilterForegroundServices(isChecked);
        });
        
        binding.switchFilterLowPriority.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveFilterLowPriority(isChecked);
        });
        
        binding.switchFilterSystemNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveFilterSystemNotifications(isChecked);
        });
        
        // Set up collapsible advanced options
        binding.advancedOptionsHeader.setOnClickListener(v -> toggleAdvancedOptions());
        
        // Set up URL handling radio buttons
        binding.urlHandlingModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String mode = DEFAULT_URL_HANDLING_MODE;
            if (checkedId == R.id.radioUrlReadFull) {
                mode = "read_full";
            } else if (checkedId == R.id.radioUrlDomainOnly) {
                mode = "domain_only";
            } else if (checkedId == R.id.radioUrlDontRead) {
                mode = "dont_read";
            }
            
            // Show/hide custom replacement text section
            binding.urlReplacementTextSection.setVisibility(
                "dont_read".equals(mode) ? View.VISIBLE : View.GONE
            );
            
            saveUrlHandlingMode(mode);
        });
        
        // Set up URL replacement text field
        binding.editUrlReplacementText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(android.text.Editable s) {
                saveUrlReplacementText(s.toString());
            }
        });
        
        // Set up collapsible sections
        binding.appListHeader.setOnClickListener(v -> toggleAppList());
        binding.blacklistHeader.setOnClickListener(v -> toggleBlacklist());
        binding.replacementHeader.setOnClickListener(v -> toggleReplacement());
        
        // Set up media excepted app input field
        setupMediaExceptedAppSelector();
    }

    private void setupAppListRecycler() {
        appListAdapter = new AppListAdapter(appList, this::removeApp, this::toggleAppPrivate, this::editApp, true);
        binding.recyclerAppList.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerAppList.setAdapter(appListAdapter);
    }

    private void setupWordBlacklistRecycler() {
        wordBlacklistAdapter = new WordListAdapter(wordBlacklistItems, this::removeBlacklistWord, this::onWordTypeChange, this::editBlacklistWord);
        binding.recyclerBlacklistWords.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerBlacklistWords.setAdapter(wordBlacklistAdapter);
    }

    private void setupWordReplacementRecycler() {
        wordSwapAdapter = new WordSwapAdapter(wordReplacementItems, this::removeWordReplacement, this::editWordReplacement);
        binding.recyclerWordReplacements.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerWordReplacements.setAdapter(wordSwapAdapter);
    }
    
    private void setupMediaFilteringRecyclers() {
        // Set up media excepted apps RecyclerView
        mediaExceptedAppsAdapter = new AppListAdapter(mediaExceptedAppsList, this::removeMediaExceptedApp, this::toggleMediaExceptedAppPrivate, this::editMediaExceptedApp, false);
        binding.recyclerMediaExceptedApps.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerMediaExceptedApps.setAdapter(mediaExceptedAppsAdapter);
        
        // Set up media important keywords RecyclerView
        mediaImportantKeywordsAdapter = new WordListAdapter(mediaImportantKeywordsList, this::removeMediaImportantKeyword, this::onMediaKeywordTypeChange, this::editMediaImportantKeyword);
        binding.recyclerMediaImportantKeywords.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerMediaImportantKeywords.setAdapter(mediaImportantKeywordsAdapter);
    }
    
    private void setupFilteredMediaAppsRecycler() {
        // Set up filtered media apps RecyclerView
        filteredMediaAppsAdapter = new AppListAdapter(filteredMediaAppsList, this::removeFilteredMediaAppFromList, this::toggleFilteredMediaAppPrivate, this::editFilteredMediaApp, false);
        binding.recyclerFilteredMediaApps.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerFilteredMediaApps.setAdapter(filteredMediaAppsAdapter);
    }

    private void openAppListPicker() {
        ArrayList<String> selectedPackages = new ArrayList<>();
        ArrayList<String> privatePackages = new ArrayList<>();
        for (AppFilterItem item : appList) {
            selectedPackages.add(item.packageName);
            if (item.isPrivate) {
                privatePackages.add(item.packageName);
            }
        }
        Intent intent = AppPickerActivity.createIntent(
            this,
            getString(R.string.filter_manage_apps),
            selectedPackages,
            privatePackages,
            true
        );
        appListPickerLauncher.launch(intent);
    }

    private void openFilteredMediaAppsPicker() {
        ArrayList<String> selectedPackages = new ArrayList<>();
        ArrayList<String> privatePackages = new ArrayList<>();
        for (AppFilterItem item : filteredMediaAppsList) {
            selectedPackages.add(item.packageName);
            if (item.isPrivate) {
                privatePackages.add(item.packageName);
            }
        }
        Intent intent = AppPickerActivity.createIntent(
            this,
            getString(R.string.filter_media_apps_title),
            selectedPackages,
            privatePackages,
            false
        );
        filteredMediaPickerLauncher.launch(intent);
    }
    
    private void setupMediaExceptedAppSelector() {
        // Use lazy loading adapter for media excepted app selector
        LazyAppSearchAdapter mediaExceptedAppAdapter = new LazyAppSearchAdapter(this);
        binding.editMediaExceptedApp.setAdapter(mediaExceptedAppAdapter);
        binding.editMediaExceptedApp.setThreshold(1); // Show suggestions after 1 character
        
        // Handle app selection
        binding.editMediaExceptedApp.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo selectedApp = mediaExceptedAppAdapter.getItem(position);
            if (selectedApp != null) {
                binding.editMediaExceptedApp.setText(selectedApp.packageName);
                binding.editMediaExceptedApp.setSelection(selectedApp.packageName.length());
            }
        });
        
        InAppLogger.log("AppSelector", "Lazy media excepted app selector initialized - apps will load on search");
    }

    private void loadSettings() {
        // Remove default initialization for word blacklist, media keywords, app blacklist, and media exception apps
        // These should be empty by default
        // initializeDefaultWordBlacklist();
        // initializeDefaultMediaKeywords();
        // initializeDefaultMediaExceptionApps();
        // initializeDefaultAppBlacklist();
        
        // Initialize default filtered media apps if not already set
        initializeDefaultFilteredMediaApps();
        
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
        wordBlacklistAdapter.notifyDataSetChanged();
        updateCountDisplays();

        // Load word swaps
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
        wordSwapAdapter.notifyDataSetChanged();
        updateCountDisplays();
        
        // Load URL handling settings
        urlHandlingMode = sharedPreferences.getString(KEY_URL_HANDLING_MODE, DEFAULT_URL_HANDLING_MODE);
        urlReplacementText = sharedPreferences.getString(KEY_URL_REPLACEMENT_TEXT, DEFAULT_URL_REPLACEMENT_TEXT);
        
        // Set radio button selection
        switch (urlHandlingMode) {
            case "read_full":
                binding.radioUrlReadFull.setChecked(true);
                binding.urlReplacementTextSection.setVisibility(View.GONE);
                break;
            case "domain_only":
                binding.radioUrlDomainOnly.setChecked(true);
                binding.urlReplacementTextSection.setVisibility(View.GONE);
                break;
            case "dont_read":
                binding.radioUrlDontRead.setChecked(true);
                binding.urlReplacementTextSection.setVisibility(View.VISIBLE);
                break;
            default:
                binding.radioUrlDomainOnly.setChecked(true);
                binding.urlReplacementTextSection.setVisibility(View.GONE);
                break;
        }
        
        // Set custom replacement text
        binding.editUrlReplacementText.setText(urlReplacementText);
        
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
        
        // Load filtered media apps (separate from app list filtering)
        Set<String> filteredMediaApps = sharedPreferences.getStringSet(KEY_MEDIA_FILTERED_APPS, new HashSet<>());
        Set<String> filteredMediaAppsPrivate = sharedPreferences.getStringSet(KEY_MEDIA_FILTERED_APPS_PRIVATE, new HashSet<>());
        
        filteredMediaAppsList.clear();
        for (String app : filteredMediaApps) {
            filteredMediaAppsList.add(new AppFilterItem(app, filteredMediaAppsPrivate.contains(app)));
        }
        filteredMediaAppsAdapter.notifyDataSetChanged();
        updateCountDisplays();
        
        // Load persistent/silent notification filtering setting
        boolean isPersistentFilteringEnabled = sharedPreferences.getBoolean(KEY_PERSISTENT_FILTERING_ENABLED, true); // Default to enabled
        binding.switchPersistentFiltering.setChecked(isPersistentFilteringEnabled);
        binding.persistentFilteringCategories.setVisibility(isPersistentFilteringEnabled ? View.VISIBLE : View.GONE);
        
        // Load individual persistent filtering category settings
        boolean filterPersistent = sharedPreferences.getBoolean(KEY_FILTER_PERSISTENT, true); // Default to enabled
        boolean filterSilent = sharedPreferences.getBoolean(KEY_FILTER_SILENT, true); // Default to enabled
        boolean filterForegroundServices = sharedPreferences.getBoolean(KEY_FILTER_FOREGROUND_SERVICES, true); // Default to enabled
        boolean filterLowPriority = sharedPreferences.getBoolean(KEY_FILTER_LOW_PRIORITY, false); // Default to disabled
        boolean filterSystemNotifications = sharedPreferences.getBoolean(KEY_FILTER_SYSTEM_NOTIFICATIONS, false); // Default to disabled
        
        binding.switchFilterPersistent.setChecked(filterPersistent);
        binding.switchFilterSilent.setChecked(filterSilent);
        binding.switchFilterForegroundServices.setChecked(filterForegroundServices);
        binding.switchFilterLowPriority.setChecked(filterLowPriority);
        binding.switchFilterSystemNotifications.setChecked(filterSystemNotifications);
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
        // Defer notification to avoid crash if called during RecyclerView layout pass
        binding.recyclerAppList.post(() -> {
            appListAdapter.notifyItemChanged(position);
            updateCountDisplays();
        });
        saveAppList();
    }

    private void addBlacklistWord() {
        String word = binding.editBlacklistWord.getText().toString().trim();
        if (word.isEmpty()) {
            Toast.makeText(this, "Please enter a word", Toast.LENGTH_SHORT).show();
            return;
        }

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

        wordBlacklistItems.add(new WordFilterItem(word, false));
        wordBlacklistAdapter.notifyDataSetChanged();
        updateCountDisplays();
        binding.editBlacklistWord.setText("");
        saveWordBlacklist();
    }

    private void removeBlacklistWord(int position) {
        wordBlacklistItems.remove(position);
        wordBlacklistAdapter.notifyDataSetChanged();
        updateCountDisplays();
        saveWordBlacklist();
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
        wordSwapAdapter.notifyDataSetChanged();
        updateCountDisplays();
        binding.editReplaceFrom.setText("");
        binding.editReplaceTo.setText("");
        
        saveWordReplacements();
    }

    private void removeWordReplacement(int position) {
        wordReplacementItems.remove(position);
        wordSwapAdapter.notifyDataSetChanged();
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
        String input = binding.editMediaExceptedApp.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Please enter an app name or package name", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Try to match input to JSON apps as fallback for display names
        String packageNameToAdd = input;
        AppListData matched = null;
        for (AppListData app : AppListManager.INSTANCE.loadAppList(this)) {
            if (app.displayName.equalsIgnoreCase(input) || app.packageName.equalsIgnoreCase(input)) {
                matched = app;
                break;
            }
        }
        if (matched != null) {
            packageNameToAdd = matched.packageName;
        }
        
        // Check if already in list
        for (AppFilterItem item : mediaExceptedAppsList) {
            if (item.packageName.equals(packageNameToAdd)) {
                Toast.makeText(this, "App already in exception list", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        // Add to list
        mediaExceptedAppsList.add(new AppFilterItem(packageNameToAdd, false));
        mediaExceptedAppsAdapter.notifyDataSetChanged();
        saveMediaExceptedApps();
        
        // Clear input
        binding.editMediaExceptedApp.setText("");
        
        InAppLogger.log("AppSelector", "Added app to media exception list: " + packageNameToAdd);
        Toast.makeText(this, "Added " + packageNameToAdd + " to media exception list", Toast.LENGTH_SHORT).show();
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
        // Defer notification to avoid crash if called during RecyclerView layout pass
        binding.recyclerMediaExceptedApps.post(() -> {
            mediaExceptedAppsAdapter.notifyItemChanged(position);
        });
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

    private void removeFilteredMediaAppFromList(int position) {
        if (position >= 0 && position < filteredMediaAppsList.size()) {
            AppFilterItem item = filteredMediaAppsList.get(position);
            
            // Remove from filtered media apps list
            filteredMediaAppsList.remove(position);
            
            // R.string.button_save updated list
            saveFilteredMediaApps();
            filteredMediaAppsAdapter.notifyDataSetChanged();
            
            Toast.makeText(this, "Removed " + item.packageName + " from filter", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleFilteredMediaAppPrivate(int position) {
        if (position >= 0 && position < filteredMediaAppsList.size()) {
            AppFilterItem item = filteredMediaAppsList.get(position);
            item.isPrivate = !item.isPrivate;
            
            // Update in SharedPreferences
            saveFilteredMediaApps();
            // Defer notification to avoid crash if called during RecyclerView layout pass
            binding.recyclerFilteredMediaApps.post(() -> {
                filteredMediaAppsAdapter.notifyItemChanged(position);
            });
        }
    }

    private void editFilteredMediaApp(int position) {
        if (position >= 0 && position < filteredMediaAppsList.size()) {
            showEditFilteredMediaAppDialog(filteredMediaAppsList.get(position), position);
        }
    }

    private void showEditFilteredMediaAppDialog(AppFilterItem item, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_edit_filtered_media_app);
        
        // Create input field
        final EditText input = new EditText(this);
        input.setText(item.packageName);
        input.setHint("Package name (e.g., com.android.chrome)");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSelection(input.getText().length()); // Move cursor to end
        
        // Add padding
        input.setPadding(50, 30, 50, 30);
        builder.setView(input);
        
        builder.setPositiveButton(R.string.button_save, (dialog, which) -> {
            String newPackageName = input.getText().toString().trim();
            if (!newPackageName.isEmpty()) {
                // Check for duplicates (excluding current item)
                for (int i = 0; i < filteredMediaAppsList.size(); i++) {
                    if (i != position && filteredMediaAppsList.get(i).packageName.equals(newPackageName)) {
                        Toast.makeText(this, "App already in filtered list", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                
                // Update the item
                item.packageName = newPackageName;
                filteredMediaAppsAdapter.notifyItemChanged(position);
                
                // Update in SharedPreferences
                saveFilteredMediaApps();
                
                Toast.makeText(this, "Filtered media app updated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Package name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton(R.string.button_cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }
    
    private void showEditMediaExceptedAppDialog(AppFilterItem item, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_edit_media_exception_app);
        
        // Create input field
        final EditText input = new EditText(this);
        input.setText(item.packageName);
        input.setHint("Package name (e.g., com.android.chrome)");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSelection(input.getText().length()); // Move cursor to end
        
        // Add padding
        input.setPadding(50, 30, 50, 30);
        builder.setView(input);
        
        builder.setPositiveButton(R.string.button_save, (dialog, which) -> {
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
        
        builder.setNegativeButton(R.string.button_cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }
    
    private void showEditMediaImportantKeywordDialog(WordFilterItem item, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_edit_media_important_keyword);
        
        // Create input field
        final EditText input = new EditText(this);
        input.setText(item.word);
        input.setHint("Important keyword (e.g., reply, comment)");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSelection(input.getText().length()); // Move cursor to end
        
        // Add padding
        input.setPadding(50, 30, 50, 30);
        builder.setView(input);
        
        builder.setPositiveButton(R.string.button_save, (dialog, which) -> {
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
        
        builder.setNegativeButton(R.string.button_cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showEditAppDialog(AppFilterItem item, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_edit_app_filter);
        
        // Create input field
        final EditText input = new EditText(this);
        input.setText(item.packageName);
        input.setHint("Package name (e.g., com.android.chrome)");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSelection(input.getText().length()); // Move cursor to end
        
        // Add padding
        input.setPadding(50, 30, 50, 30);
        builder.setView(input);
        
        builder.setPositiveButton(R.string.button_save, (dialog, which) -> {
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
        
        builder.setNegativeButton(R.string.button_cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showEditWordDialog(WordFilterItem item, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_edit_word_filter);
        
        // Create input field
        final EditText input = new EditText(this);
        input.setText(item.word);
        input.setHint("Word or phrase to filter");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSelection(input.getText().length()); // Move cursor to end
        
        // Add padding
        input.setPadding(50, 30, 50, 30);
        builder.setView(input);
        
        builder.setPositiveButton(R.string.button_save, (dialog, which) -> {
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
        
        builder.setNegativeButton(R.string.button_cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showEditWordReplacementDialog(WordReplacementItem item, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_edit_word_swap);
        
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
        spacer.setTextColor(getResources().getColor(R.color.purple_200, null));
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
        
        builder.setPositiveButton(R.string.button_save, (dialog, which) -> {
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
            wordSwapAdapter.notifyItemChanged(position);
            saveWordReplacements();
            Toast.makeText(this, "Word swap updated", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton(R.string.button_cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void saveAppListMode(String mode) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_APP_LIST_MODE, mode);
        editor.apply();
    }
    
    private void saveUrlHandlingMode(String mode) {
        urlHandlingMode = mode;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_URL_HANDLING_MODE, mode);
        editor.apply();
    }
    
    private void saveUrlReplacementText(String text) {
        urlReplacementText = text;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_URL_REPLACEMENT_TEXT, text);
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
    
    private void saveFilteredMediaApps() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        
        Set<String> apps = new HashSet<>();
        Set<String> privateApps = new HashSet<>();
        
        for (AppFilterItem item : filteredMediaAppsList) {
            apps.add(item.packageName);
            if (item.isPrivate) {
                privateApps.add(item.packageName);
            }
        }
        
        editor.putStringSet(KEY_MEDIA_FILTERED_APPS, apps);
        editor.putStringSet(KEY_MEDIA_FILTERED_APPS_PRIVATE, privateApps);
        editor.apply();
    }
    
    private void initializeDefaultFilteredMediaApps() {
        Set<String> existingApps = sharedPreferences.getStringSet(KEY_MEDIA_FILTERED_APPS, new HashSet<>());
        
        // Only initialize if the list is empty
        if (existingApps.isEmpty()) {
            Set<String> defaultMediaApps = new HashSet<>();
            defaultMediaApps.add("com.google.android.youtube");
            
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putStringSet(KEY_MEDIA_FILTERED_APPS, defaultMediaApps);
            editor.putStringSet(KEY_MEDIA_FILTERED_APPS_PRIVATE, new HashSet<>());
            editor.apply();
            
            InAppLogger.log("AppSelector", "Initialized default filtered media apps: " + defaultMediaApps.size() + " apps");
        }
    }
    
    private void saveMediaFilteringEnabled(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_MEDIA_FILTERING_ENABLED, enabled);
        editor.apply();
    }
    
    private void savePersistentFilteringEnabled(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_PERSISTENT_FILTERING_ENABLED, enabled);
        editor.apply();
    }
    
    private void saveFilterPersistent(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_FILTER_PERSISTENT, enabled);
        editor.apply();
    }
    
    private void saveFilterSilent(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_FILTER_SILENT, enabled);
        editor.apply();
    }
    
    private void saveFilterForegroundServices(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_FILTER_FOREGROUND_SERVICES, enabled);
        editor.apply();
    }
    
    private void saveFilterLowPriority(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_FILTER_LOW_PRIORITY, enabled);
        editor.apply();
    }
    
    private void saveFilterSystemNotifications(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_FILTER_SYSTEM_NOTIFICATIONS, enabled);
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
        binding.txtAppListSummary.setText("(" + appList.size() + " apps)");
        
        // Update blacklist count
        binding.txtBlacklistCount.setText("(" + wordBlacklistItems.size() + " words)");
        
        // Update replacement count
        binding.txtReplacementCount.setText("(" + wordReplacementItems.size() + " swaps)");

        binding.txtFilteredMediaAppsCount.setText("(" + filteredMediaAppsList.size() + " apps)");
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
        
        // Programmatically set icon tint for theme adaptation
        MenuItem exportItem = menu.findItem(R.id.action_export_filters);
        MenuItem importItem = menu.findItem(R.id.action_import_filters);
        
        // Get the appropriate color based on theme
        int iconColor = getResources().getColor(android.R.color.white, getTheme());
        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_NO) {
            // Light mode - use black
            iconColor = getResources().getColor(android.R.color.black, getTheme());
        }
        
        if (exportItem != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                exportItem.setIconTintList(android.content.res.ColorStateList.valueOf(iconColor));
            }
        }
        
        if (importItem != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                importItem.setIconTintList(android.content.res.ColorStateList.valueOf(iconColor));
            }
        }
        
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
            
            // Use FileExportHelper to create the file with fallback support
            File exportFile = FileExportHelper.createExportFile(this, "exports", filename, configData);
            
            if (exportFile != null) {
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
                    .setTitle(R.string.dialog_title_export_successful)
                    .setMessage("Filter configuration exported to:\n" + exportFile.getAbsolutePath() + 
                               "\n\nExported settings:\n" + summary)
                    .setPositiveButton(R.string.button_ok, null)
                    .show();
                
                InAppLogger.log("FilterConfig", "Filter configuration exported to " + filename);
            } else {
                // Fallback to text-based sharing if file creation failed
                Intent textShareIntent = new Intent(Intent.ACTION_SEND);
                textShareIntent.setType("text/plain");
                textShareIntent.putExtra(Intent.EXTRA_SUBJECT, "SpeakThat! Filter Configuration");
                textShareIntent.putExtra(Intent.EXTRA_TEXT, configData);
                
                startActivity(Intent.createChooser(textShareIntent, "Export Filter Configuration as Text"));
                
                Toast.makeText(this, "Configuration exported as text (file creation failed)", Toast.LENGTH_LONG).show();
                InAppLogger.log("FilterConfig", "Filter configuration exported as text fallback");
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            InAppLogger.logError("FilterConfig", "Export failed: " + e.getMessage());
        }
    }
    
    private void importFilterConfigDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title_import_filter_configuration)
            .setMessage("This will replace your current filter settings with the imported configuration.\n\nCurrent settings:\n" + 
                       FilterConfigManager.getFilterSummary(this) + "\n\nDo you want to continue?")
            .setPositiveButton(R.string.button_select_file, (dialog, which) -> openFilePicker())
            .setNegativeButton(R.string.button_cancel, null)
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
                    .setTitle(R.string.dialog_title_import_successful)
                    .setMessage(result.message + "\n\nNew settings:\n" + FilterConfigManager.getFilterSummary(this))
                    .setPositiveButton(R.string.button_ok, null)
                    .show();
                
                Toast.makeText(this, "Filter configuration imported successfully!", Toast.LENGTH_SHORT).show();
            } else {
                // Show error dialog
                new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_title_import_failed)
                    .setMessage(result.message)
                    .setPositiveButton(R.string.button_ok, null)
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

    private void showAppFilterHelp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_app_filter_help)
               .setMessage("How App Filtering Works:\n\n" +
                          "🔍 Search Function:\n" +
                          "• Search through your installed apps by name or package\n" +
                          "• You can type any Android package name manually\n" +
                          "• Click a suggestion to instantly add the package name\n" +
                          "• Filtering works even if an app doesn't appear in search\n\n" +
                          "📋 Package Names:\n" +
                          "• Find package names in: Settings → Apps → [App Name] → Advanced\n" +
                          "• Examples: com.whatsapp, com.discord, com.google.android.apps.maps\n" +
                          "• You can also search by app name (e.g., 'WhatsApp', 'Discord')\n\n" +
                          "✅ Filtering Effectiveness:\n" +
                          "• Works perfectly regardless of search results\n" +
                          "• Matches the actual package name from notifications\n" +
                          "• Test by adding an app, then checking if its notifications are filtered")
               .setPositiveButton(R.string.button_got_it, (dialog, which) -> dialog.dismiss())
               .show();
        
        InAppLogger.log("FilterSettings", "App filter help dialog shown");
    }
    
    private void showMediaFilterHelp() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_title_smart_media_filter)
               .setMessage("How Smart Media Detection Works:\n\n" +
                          "🔍 Reliable Detection Methods:\n" +
                          "• Media session flags (official Android media controls)\n" +
                          "• Progress bars/seekbars (actual playback controls)\n" +
                          "• System notification categories (media_session, playback)\n" +
                          "• No unreliable text pattern matching\n\n" +
                          
                          "📱 Exception Apps:\n" +
                          "• Apps that should never have notifications filtered\n" +
                          "• Useful for apps that send both media and important notifications\n" +
                          "• Add apps that might have false positives\n\n" +
                          
                          "🔑 Important Keywords:\n" +
                          "• Words that indicate social interaction (like 'reply', 'comment')\n" +
                          "• Notifications containing these words won't be filtered\n" +
                          "• Helps preserve important notifications from media apps\n\n" +
                          
                          "✅ Benefits:\n" +
                          "• Prevents false positives (like Gmail being detected as media)\n" +
                          "• Only blocks actual media control notifications\n" +
                          "• Works with persistent/silent notification filtering\n" +
                          "• Much more reliable than text-based detection")
               .setPositiveButton(R.string.button_got_it, (dialog, which) -> dialog.dismiss())
               .show();
        
        InAppLogger.log("FilterSettings", "Smart media filter help dialog shown");
    }
} 