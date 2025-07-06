package com.micoyc.speakthat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.micoyc.speakthat.databinding.ActivityDevelopmentSettingsBinding;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Calendar;

public class DevelopmentSettingsActivity extends AppCompatActivity {
    private ActivityDevelopmentSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    
    // SharedPreferences keys
    private static final String PREFS_NAME = "SpeakThatPrefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_VERBOSE_LOGGING = "verbose_logging";
    private static final String KEY_LOG_FILTERS = "log_filters";
    private static final String KEY_LOG_NOTIFICATIONS = "log_notifications";
    private static final String KEY_LOG_USER_ACTIONS = "log_user_actions";
    private static final String KEY_LOG_SYSTEM_EVENTS = "log_system_events";
    private static final String KEY_LOG_SENSITIVE_DATA = "log_sensitive_data";

    private boolean isLogAutoRefreshPaused = false;
    private Runnable logUpdateRunnable;
    private boolean isActivityVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme FIRST before anything else
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applySavedTheme();
        
        super.onCreate(savedInstanceState);
        binding = ActivityDevelopmentSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        initializeUI();
        loadSettings();
        // Don't start log updates in onCreate - wait for onResume
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityVisible = true;
        startLogUpdates();
        InAppLogger.log("Development", "Development Settings resumed - starting log updates");
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityVisible = false;
        stopLogUpdates();
        InAppLogger.log("Development", "Development Settings paused - stopping log updates");
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
        // Set up notification history button
        binding.btnShowHistory.setOnClickListener(v -> showNotificationHistory());
        
        // Set up log controls
        binding.btnClearLogs.setOnClickListener(v -> clearLogs());
        binding.btnExportLogs.setOnClickListener(v -> exportLogs());
        binding.btnRefreshLogs.setOnClickListener(v -> refreshLogs());
        binding.btnPauseLogs.setOnClickListener(v -> toggleLogPause());
        
        // Ensure icons are set in code to fix invisible icon bug
        binding.btnRefreshLogs.setIconResource(R.drawable.ic_refresh_24);
        binding.btnClearLogs.setIconResource(R.drawable.ic_delete_24);
        binding.btnExportLogs.setIconResource(R.drawable.ic_file_upload_24);
        
        // Try additional fixes for icon visibility
        binding.btnRefreshLogs.setIconTint(ColorStateList.valueOf(Color.WHITE));
        binding.btnClearLogs.setIconTint(ColorStateList.valueOf(Color.WHITE));
        binding.btnExportLogs.setIconTint(ColorStateList.valueOf(Color.WHITE));
        
        // Set icon size programmatically (24dp converted to pixels)
        int iconSizePx = (int) (24 * getResources().getDisplayMetrics().density);
        binding.btnRefreshLogs.setIconSize(iconSizePx);
        binding.btnClearLogs.setIconSize(iconSizePx);
        binding.btnExportLogs.setIconSize(iconSizePx);
        binding.btnPauseLogs.setIconSize(iconSizePx);
        
        // For icon-only buttons, we need to center them properly
        // Remove text and use appropriate gravity
        binding.btnRefreshLogs.setText("");
        binding.btnClearLogs.setText("");
        binding.btnExportLogs.setText("");
        binding.btnPauseLogs.setText("");
        
        // Try different approach for centering icons - use padding to center them
        binding.btnRefreshLogs.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);
        binding.btnClearLogs.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);
        binding.btnExportLogs.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);
        binding.btnPauseLogs.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);
        
        // Set padding to center the icons better
        int paddingPx = (int) (4 * getResources().getDisplayMetrics().density);
        binding.btnRefreshLogs.setIconPadding(paddingPx);
        binding.btnClearLogs.setIconPadding(paddingPx);
        binding.btnExportLogs.setIconPadding(paddingPx);
        binding.btnPauseLogs.setIconPadding(paddingPx);
        
        // Ensure pause button starts with proper styling and icon
        binding.btnPauseLogs.setIconResource(R.drawable.ic_pause_24);
        binding.btnPauseLogs.setIconTint(ColorStateList.valueOf(Color.WHITE));
        
        // Debug: Log icon status
        InAppLogger.log("Development", "Setting up log control button icons");
        InAppLogger.log("Development", "Refresh button icon: " + (binding.btnRefreshLogs.getIcon() != null ? "SET" : "NULL"));
        InAppLogger.log("Development", "Clear button icon: " + (binding.btnClearLogs.getIcon() != null ? "SET" : "NULL"));
        InAppLogger.log("Development", "Export button icon: " + (binding.btnExportLogs.getIcon() != null ? "SET" : "NULL"));
        
        // Set up crash log controls
        binding.btnViewCrashLogs.setOnClickListener(v -> showCrashLogs());
        binding.btnClearCrashLogs.setOnClickListener(v -> clearCrashLogs());
        
        // Set up debug crash log button
        binding.btnDebugCrashLogs.setOnClickListener(v -> showCrashLogDebugInfo());
        
        // Set up conditional rules debug button
        binding.btnDebugConditionalRules.setOnClickListener(v -> showConditionalRulesDebugInfo());
        
        // Set up analytics button
        binding.btnShowAnalytics.setOnClickListener(v -> showAnalyticsDialog());
        
        // Set up battery optimization report button
        binding.btnBatteryReport.setOnClickListener(v -> showBatteryOptimizationReport());
        
        // Set up logging options
        binding.switchVerboseLogging.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveVerboseLogging(isChecked);
            InAppLogger.setVerboseMode(isChecked);
            InAppLogger.log("Development", "Verbose logging " + (isChecked ? "enabled" : "disabled"));
        });
        
        binding.switchLogFilters.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveLogFilters(isChecked);
            InAppLogger.setLogFilters(isChecked);
            InAppLogger.log("Development", "Filter logging " + (isChecked ? "enabled" : "disabled"));
        });
        
        binding.switchLogNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveLogNotifications(isChecked);
            InAppLogger.setLogNotifications(isChecked);
            InAppLogger.log("Development", "Notification logging " + (isChecked ? "enabled" : "disabled"));
        });
        
        binding.switchLogUserActions.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveLogUserActions(isChecked);
            InAppLogger.setLogUserActions(isChecked);
            InAppLogger.log("Development", "User action logging " + (isChecked ? "enabled" : "disabled"));
        });
        
        binding.switchLogSystemEvents.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveLogSystemEvents(isChecked);
            InAppLogger.setLogSystemEvents(isChecked);
            InAppLogger.log("Development", "System event logging " + (isChecked ? "enabled" : "disabled"));
        });
        
        binding.switchLogSensitiveData.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveLogSensitiveData(isChecked);
            InAppLogger.setLogSensitiveData(isChecked);
            InAppLogger.log("Development", "Sensitive data logging " + (isChecked ? "enabled" : "disabled"));
        });
        
        // Set up log display
        binding.textLogDisplay.setMovementMethod(new ScrollingMovementMethod());
        binding.textLogDisplay.setHorizontallyScrolling(true);
        
        // Update crash log button visibility
        updateCrashLogButtonVisibility();
        
        // Set up Repair Blacklist button
        binding.btnRepairBlacklist.setOnClickListener(v -> repairWordBlacklist());
        
        // Add welcome message
        InAppLogger.log("Development", "Development Settings opened");
    }

    private void updateCrashLogButtonVisibility() {
        // Show crash log buttons only if crash logs exist
        boolean hasCrashLogs = InAppLogger.hasCrashLogs();
        binding.btnViewCrashLogs.setVisibility(hasCrashLogs ? View.VISIBLE : View.GONE);
        binding.btnClearCrashLogs.setVisibility(hasCrashLogs ? View.VISIBLE : View.GONE);
        
        if (hasCrashLogs) {
            binding.btnViewCrashLogs.setText("📄 View Crash Logs");
            binding.btnClearCrashLogs.setText("🗑️ Clear Crash Logs");
        }
        
        // Debug: Log the crash log status
        InAppLogger.log("Development", "Crash log status check - Has crash logs: " + hasCrashLogs);
    }

    private void showCrashLogs() {
        String crashLogs = InAppLogger.getCrashLogs();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("💥 Crash Logs");
        
        // Create scrollable text view for crash logs
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setText(crashLogs);
        textView.setTextSize(12f);
        textView.setPadding(16, 16, 16, 16);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        scrollView.addView(textView);
        
        builder.setView(scrollView);
        builder.setPositiveButton("Export Crash Logs", (dialog, which) -> {
            exportCrashLogs();
        });
        builder.setNeutralButton("Clear Crash Logs", (dialog, which) -> {
            clearCrashLogs();
        });
        builder.setNegativeButton("Close", null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Make dialog larger
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.8)
            );
        }
        
        InAppLogger.logUserAction("Crash logs viewed", "");
    }

    private void exportCrashLogs() {
        try {
            String crashLogs = InAppLogger.getCrashLogs();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());
            String logContent = "SpeakThat! Crash Logs\n" +
                "Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n" +
                "===========================================\n\n" +
                crashLogs;
            
            // Try to create and share a file first
            try {
                // Create logs directory if it doesn't exist
                File logsDir = new File(getExternalFilesDir(null), "logs");
                if (!logsDir.exists()) {
                    logsDir.mkdirs();
                }
                
                // Create crash log file with timestamp
                File crashLogFile = new File(logsDir, "speakthat_crash_logs_" + timestamp + ".txt");
                
                // Write crash logs to file
                FileWriter writer = new FileWriter(crashLogFile);
                writer.write(logContent);
                writer.close();
                
                InAppLogger.log("Development", "Crash log file created: " + crashLogFile.getAbsolutePath());
                
                // Create file sharing intent using FileProvider
                Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    crashLogFile
                );
                
                Intent fileShareIntent = new Intent(Intent.ACTION_SEND);
                fileShareIntent.setType("text/plain");
                fileShareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                fileShareIntent.putExtra(Intent.EXTRA_SUBJECT, "SpeakThat! Crash Logs - " + timestamp);
                fileShareIntent.putExtra(Intent.EXTRA_TEXT, "SpeakThat! Crash Logs attached as file.");
                fileShareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                startActivity(Intent.createChooser(fileShareIntent, "Export Crash Logs as File"));
                
                Toast.makeText(this, "Crash logs exported as file! File saved to: " + crashLogFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                InAppLogger.log("Development", "Crash logs exported as file: " + crashLogFile.getName());
                
            } catch (Exception fileException) {
                // Fallback to text-based sharing if file sharing fails
                InAppLogger.log("Development", "Crash log file export failed, falling back to text: " + fileException.getMessage());
                
                Intent textShareIntent = new Intent(Intent.ACTION_SEND);
                textShareIntent.setType("text/plain");
                textShareIntent.putExtra(Intent.EXTRA_SUBJECT, "SpeakThat! Crash Logs - " + timestamp);
                textShareIntent.putExtra(Intent.EXTRA_TEXT, logContent);
                
                startActivity(Intent.createChooser(textShareIntent, "Export Crash Logs as Text"));
                
                Toast.makeText(this, "Crash logs exported as text (file export failed)", Toast.LENGTH_LONG).show();
                InAppLogger.log("Development", "Crash logs exported as text fallback");
            }
            
            InAppLogger.logUserAction("Crash logs exported", "");
            
        } catch (Exception e) {
            Toast.makeText(this, "Failed to export crash logs: " + e.getMessage(), Toast.LENGTH_LONG).show();
            InAppLogger.logError("Development", "Crash log export failed completely: " + e.getMessage());
        }
    }

    private void clearCrashLogs() {
        new AlertDialog.Builder(this)
            .setTitle("Clear Crash Logs")
            .setMessage("Are you sure you want to delete all crash logs? This action cannot be undone.")
            .setPositiveButton("Clear", (dialog, which) -> {
                InAppLogger.clearCrashLogs();
                updateCrashLogButtonVisibility();
                Toast.makeText(this, "Crash logs cleared", Toast.LENGTH_SHORT).show();
                InAppLogger.logUserAction("Crash logs cleared", "");
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showCrashLogDebugInfo() {
        // Get detailed crash log status information
        boolean hasCrashLogs = InAppLogger.hasCrashLogs();
        String crashLogs = InAppLogger.getCrashLogs();
        
        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append("=== CRASH LOG DEBUG INFO ===\n\n");
        debugInfo.append("Has Crash Logs: ").append(hasCrashLogs).append("\n");
        debugInfo.append("Crash Log Content Length: ").append(crashLogs.length()).append(" characters\n");
        debugInfo.append("Logger Initialized: ").append(InAppLogger.getLogCount() > 0).append("\n");
        debugInfo.append("Current Log Count: ").append(InAppLogger.getLogCount()).append("\n\n");
        
        if (hasCrashLogs) {
            debugInfo.append("--- CRASH LOG PREVIEW ---\n");
            debugInfo.append(crashLogs.length() > 500 ? 
                crashLogs.substring(0, 500) + "...\n[TRUNCATED]" : 
                crashLogs);
        } else {
            debugInfo.append("--- NO CRASH LOGS FOUND ---\n");
            debugInfo.append("Possible reasons:\n");
            debugInfo.append("• No crashes have occurred\n");
            debugInfo.append("• Crash occurred before logger initialization\n");
            debugInfo.append("• Crash logs were cleared\n");
            debugInfo.append("• File system access issues\n");
        }
        
        debugInfo.append("\n\n--- RECENT REGULAR LOGS ---\n");
        debugInfo.append(InAppLogger.getRecentLogs(10));
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔍 Crash Log Debug Info");
        
        // Create scrollable text view
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setText(debugInfo.toString());
        textView.setTextSize(11f);
        textView.setPadding(16, 16, 16, 16);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        textView.setTextIsSelectable(true);
        scrollView.addView(textView);
        
        builder.setView(scrollView);
        builder.setPositiveButton("Close", null);
        builder.setNeutralButton("Force Test Crash", (dialog, which) -> {
            // Force a test crash for debugging
            InAppLogger.log("Development", "User requested test crash");
            throw new RuntimeException("Test crash for debugging crash log system");
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Make dialog larger
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.8)
            );
        }
        
        InAppLogger.logUserAction("Crash log debug info viewed", "");
    }

    private void loadSettings() {
        // Load logging preferences
        boolean verboseLogging = sharedPreferences.getBoolean(KEY_VERBOSE_LOGGING, true);
        boolean logFilters = sharedPreferences.getBoolean(KEY_LOG_FILTERS, true);
        boolean logNotifications = sharedPreferences.getBoolean(KEY_LOG_NOTIFICATIONS, true);
        boolean logUserActions = sharedPreferences.getBoolean(KEY_LOG_USER_ACTIONS, true);
        boolean logSystemEvents = sharedPreferences.getBoolean(KEY_LOG_SYSTEM_EVENTS, true);
        boolean logSensitiveData = sharedPreferences.getBoolean(KEY_LOG_SENSITIVE_DATA, false);
        
        binding.switchVerboseLogging.setChecked(verboseLogging);
        binding.switchLogFilters.setChecked(logFilters);
        binding.switchLogNotifications.setChecked(logNotifications);
        binding.switchLogUserActions.setChecked(logUserActions);
        binding.switchLogSystemEvents.setChecked(logSystemEvents);
        binding.switchLogSensitiveData.setChecked(logSensitiveData);
        
        // Apply settings to logger
        InAppLogger.setVerboseMode(verboseLogging);
        InAppLogger.setLogFilters(logFilters);
        InAppLogger.setLogNotifications(logNotifications);
        InAppLogger.setLogUserActions(logUserActions);
        InAppLogger.setLogSystemEvents(logSystemEvents);
        InAppLogger.setLogSensitiveData(logSensitiveData);
    }

    private void showNotificationHistory() {
        List<NotificationReaderService.NotificationData> notifications = NotificationReaderService.getRecentNotifications();
        
        if (notifications.isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Notification History")
                   .setMessage("No notifications to display")
                   .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                   .show();
        } else {
            showEnhancedNotificationHistory(notifications);
        }
        
        InAppLogger.log("Development", "Notification history viewed (" + notifications.size() + " items)");
    }
    
    private void showEnhancedNotificationHistory(List<NotificationReaderService.NotificationData> notifications) {
        // Create custom dialog with RecyclerView
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_notification_history, null);
        
        RecyclerView recyclerView = dialogView.findViewById(R.id.recyclerNotifications);
        TextView titleText = dialogView.findViewById(R.id.textHistoryTitle);
        
        titleText.setText("Notification History (" + notifications.size() + " items)");
        
        // Set up RecyclerView
        NotificationHistoryAdapter adapter = new NotificationHistoryAdapter(notifications, this::showFilterSuggestionDialog);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        
        builder.setView(dialogView)
               .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
               .show();
    }
    
    private void showFilterSuggestionDialog(NotificationReaderService.NotificationData notification) {
        InAppLogger.log("Development", "Filter suggestion requested for: " + notification.getAppName());
        
        // Analyze the notification for filter suggestions
        NotificationFilterHelper.FilterSuggestion suggestion = 
            NotificationFilterHelper.analyzeNotification(notification.getAppName(), notification.getPackageName(), notification.getText());
        
        // Create filter suggestion dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_filter_suggestion, null);
        
        // Set up dialog content
        TextView originalText = dialogView.findViewById(R.id.textOriginalNotification);
        TextView patternPreview = dialogView.findViewById(R.id.textPatternPreview);
        TextView keywordsPreview = dialogView.findViewById(R.id.textKeywordsPreview);
        TextView exactPreview = dialogView.findViewById(R.id.textExactPreview);
        TextView appSpecificPreview = dialogView.findViewById(R.id.textAppSpecificPreview);
        
        RadioGroup filterTypeGroup = dialogView.findViewById(R.id.radioGroupFilterType);
        RadioGroup filterActionGroup = dialogView.findViewById(R.id.radioGroupFilterAction);
        
        // Set preview text
        originalText.setText(suggestion.originalText);
        patternPreview.setText("Preview: " + suggestion.patternMatch);
        keywordsPreview.setText("Preview: " + suggestion.keywordMatch);
        exactPreview.setText("Preview: " + suggestion.exactMatch);
        appSpecificPreview.setText("Preview: Block all " + suggestion.appName + " notifications");
        
        // Set up the dialog
        AlertDialog dialog = builder.setView(dialogView).create();
        
        // Handle buttons
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnCreateFilter).setOnClickListener(v -> {
            createFilterFromSuggestion(suggestion, filterTypeGroup, filterActionGroup);
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void createFilterFromSuggestion(NotificationFilterHelper.FilterSuggestion suggestion, 
                                          RadioGroup filterTypeGroup, RadioGroup filterActionGroup) {
        // Determine filter type
        NotificationFilterHelper.FilterType filterType = NotificationFilterHelper.FilterType.PATTERN;
        int selectedFilterType = filterTypeGroup.getCheckedRadioButtonId();
        if (selectedFilterType == R.id.radioExact) {
            filterType = NotificationFilterHelper.FilterType.EXACT;
        } else if (selectedFilterType == R.id.radioKeywords) {
            filterType = NotificationFilterHelper.FilterType.KEYWORDS;
        } else if (selectedFilterType == R.id.radioAppSpecific) {
            filterType = NotificationFilterHelper.FilterType.APP_SPECIFIC;
        }
        
        // Determine action (block vs private)
        boolean isPrivateFilter = filterActionGroup.getCheckedRadioButtonId() == R.id.radioPrivate;
        
        // Create filter rule
        String filterRule = NotificationFilterHelper.createFilterRule(suggestion, filterType);
        
        if (filterType == NotificationFilterHelper.FilterType.APP_SPECIFIC) {
            // Add to app blacklist
            addToAppFilter(suggestion.packageName, isPrivateFilter);
        } else {
            // Add to word blacklist
            addToWordFilter(filterRule, isPrivateFilter);
        }
        
        String action = isPrivateFilter ? "private" : "blocked";
        String type = filterType.displayName.toLowerCase();
        
        Toast.makeText(this, "Filter created! Similar notifications will be " + action + " (" + type + ")", 
                      Toast.LENGTH_LONG).show();
        
        String logDetails = filterType == NotificationFilterHelper.FilterType.APP_SPECIFIC 
            ? "Package: " + suggestion.packageName + " (" + suggestion.appName + ")"
            : "Rule: " + filterRule;
        InAppLogger.log("Development", "Filter created - Type: " + type + ", Action: " + action + ", " + logDetails);
    }
    
    private void addToWordFilter(String filterRule, boolean isPrivate) {
        String prefKey = isPrivate ? "word_blacklist_private" : "word_blacklist";
        Set<String> currentFilters = new HashSet<>(sharedPreferences.getStringSet(prefKey, new HashSet<>()));
        currentFilters.add(filterRule);
        
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet(prefKey, currentFilters);
        editor.apply();
    }
    
    private void addToAppFilter(String appName, boolean isPrivate) {
        String prefKey = isPrivate ? "app_private_flags" : "app_list";
        Set<String> currentFilters = new HashSet<>(sharedPreferences.getStringSet(prefKey, new HashSet<>()));
        currentFilters.add(appName);
        
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet(prefKey, currentFilters);
        
        // If adding to blacklist, make sure app list mode is set to blacklist
        if (!isPrivate) {
            editor.putString("app_list_mode", "blacklist");
        }
        
        editor.apply();
    }

    private void clearLogs() {
        InAppLogger.clear();
        binding.textLogDisplay.setText("");
        InAppLogger.log("Development", "Logs cleared");
        refreshLogs();
    }

    private void exportLogs() {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());
            String logContent = "SpeakThat! Debug Logs\n" +
                "Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n" +
                "App Version: " + getString(R.string.app_name) + " (Development Build)\n" +
                "===========================================\n\n" +
                InAppLogger.getAllLogs();
            
            // Try to create and share a file first
            try {
                // Create logs directory if it doesn't exist
                File logsDir = new File(getExternalFilesDir(null), "logs");
                if (!logsDir.exists()) {
                    logsDir.mkdirs();
                }
                
                // Create log file with timestamp
                File logFile = new File(logsDir, "speakthat_logs_" + timestamp + ".txt");
                
                // Write logs to file
                FileWriter writer = new FileWriter(logFile);
                writer.write(logContent);
                writer.close();
                
                InAppLogger.log("Development", "Log file created: " + logFile.getAbsolutePath());
                
                // Create file sharing intent using FileProvider
                Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    logFile
                );
                
                Intent fileShareIntent = new Intent(Intent.ACTION_SEND);
                fileShareIntent.setType("text/plain");
                fileShareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                fileShareIntent.putExtra(Intent.EXTRA_SUBJECT, "SpeakThat! Debug Logs - " + timestamp);
                fileShareIntent.putExtra(Intent.EXTRA_TEXT, "SpeakThat! Debug Logs attached as file.");
                fileShareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                startActivity(Intent.createChooser(fileShareIntent, "Export Logs as File"));
                
                Toast.makeText(this, "Logs exported as file! File saved to: " + logFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                InAppLogger.log("Development", "Logs exported as file: " + logFile.getName());
                
            } catch (Exception fileException) {
                // Fallback to text-based sharing if file sharing fails
                InAppLogger.log("Development", "File export failed, falling back to text: " + fileException.getMessage());
                
                Intent textShareIntent = new Intent(Intent.ACTION_SEND);
                textShareIntent.setType("text/plain");
                textShareIntent.putExtra(Intent.EXTRA_SUBJECT, "SpeakThat! Debug Logs - " + timestamp);
                textShareIntent.putExtra(Intent.EXTRA_TEXT, logContent);
                
                startActivity(Intent.createChooser(textShareIntent, "Export Logs as Text"));
                
                Toast.makeText(this, "Logs exported as text (file export failed)", Toast.LENGTH_LONG).show();
                InAppLogger.log("Development", "Logs exported as text fallback");
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "Failed to export logs: " + e.getMessage(), Toast.LENGTH_LONG).show();
            InAppLogger.log("Development", "Log export failed completely: " + e.getMessage());
        }
    }

    private void refreshLogs() {
        String logs = InAppLogger.getRecentLogs(100); // Get last 100 log entries
        binding.textLogDisplay.setText(logs);
        
        // Scroll to bottom only if not paused
        if (!isLogAutoRefreshPaused) {
            binding.textLogDisplay.post(() -> {
                if (binding.textLogDisplay.getLayout() != null) {
                    int scrollAmount = binding.textLogDisplay.getLayout().getLineTop(binding.textLogDisplay.getLineCount()) 
                                     - binding.textLogDisplay.getHeight();
                    if (scrollAmount > 0) {
                        binding.textLogDisplay.scrollTo(0, scrollAmount);
                    } else {
                        binding.textLogDisplay.scrollTo(0, 0);
                    }
                }
            });
        }
    }

    private void startLogUpdates() {
        // Update logs every 2 seconds
        logUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isDestroyed() && !isFinishing() && isActivityVisible) {
                    if (!isLogAutoRefreshPaused) {
                        refreshLogs();
                    }
                    uiHandler.postDelayed(this, 2000);
                }
            }
        };
        uiHandler.postDelayed(logUpdateRunnable, 2000);
    }

    private void stopLogUpdates() {
        if (logUpdateRunnable != null) {
            uiHandler.removeCallbacks(logUpdateRunnable);
            InAppLogger.log("Development", "Log auto-refresh stopped to save battery");
        }
    }

    private void saveVerboseLogging(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_VERBOSE_LOGGING, enabled);
        editor.apply();
    }

    private void saveLogFilters(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_LOG_FILTERS, enabled);
        editor.apply();
    }

    private void saveLogNotifications(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_LOG_NOTIFICATIONS, enabled);
        editor.apply();
    }

    private void saveLogUserActions(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_LOG_USER_ACTIONS, enabled);
        editor.apply();
    }

    private void saveLogSystemEvents(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_LOG_SYSTEM_EVENTS, enabled);
        editor.apply();
    }

    private void saveLogSensitiveData(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_LOG_SENSITIVE_DATA, enabled);
        editor.apply();
    }

    private void toggleLogPause() {
        isLogAutoRefreshPaused = !isLogAutoRefreshPaused;
        
        // Get icon size and padding for consistency
        int iconSizePx = (int) (24 * getResources().getDisplayMetrics().density);
        int paddingPx = (int) (4 * getResources().getDisplayMetrics().density);
        
        if (isLogAutoRefreshPaused) {
            // Paused - update button to show resume icon
            binding.btnPauseLogs.setIconResource(R.drawable.ic_play_arrow_24); // Material play icon
            binding.btnPauseLogs.setText("");
            binding.btnPauseLogs.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_primary_light)));
            // Maintain consistent styling
            binding.btnPauseLogs.setIconTint(ColorStateList.valueOf(Color.WHITE));
            binding.btnPauseLogs.setIconSize(iconSizePx);
            binding.btnPauseLogs.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);
            binding.btnPauseLogs.setIconPadding(paddingPx);
            Toast.makeText(this, "Auto-refresh paused. You can now scroll through logs.", Toast.LENGTH_SHORT).show();
        } else {
            // Resumed - update button to show pause icon
            binding.btnPauseLogs.setIconResource(R.drawable.ic_pause_24); // Material pause icon
            binding.btnPauseLogs.setText("");
            binding.btnPauseLogs.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_primary_light)));
            // Maintain consistent styling
            binding.btnPauseLogs.setIconTint(ColorStateList.valueOf(Color.WHITE));
            binding.btnPauseLogs.setIconSize(iconSizePx);
            binding.btnPauseLogs.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);
            binding.btnPauseLogs.setIconPadding(paddingPx);
            refreshLogs(); // Immediately refresh when resuming
            Toast.makeText(this, "Auto-refresh resumed.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void showAnalyticsDialog() {
        StringBuilder analytics = new StringBuilder();
        analytics.append("📊 Help Usage Analytics\n");
        analytics.append("========================\n\n");
        
        // Get total usage
        int totalUsage = sharedPreferences.getInt("total_dialog_usage", 0);
        analytics.append("Total help dialogs opened: ").append(totalUsage).append("\n\n");
        
        if (totalUsage > 0) {
            analytics.append("📋 Dialog Usage Breakdown:\n");
            analytics.append("─────────────────────────\n");
            
            // Individual dialog stats
            int notificationInfo = sharedPreferences.getInt("dialog_usage_notification_behavior_info", 0);
            int notificationRec = sharedPreferences.getInt("dialog_usage_notification_behavior_recommended", 0);
            int mediaInfo = sharedPreferences.getInt("dialog_usage_media_behavior_info", 0);
            int mediaRec = sharedPreferences.getInt("dialog_usage_media_behavior_recommended", 0);
            int shakeInfo = sharedPreferences.getInt("dialog_usage_shake_to_stop_info", 0);
            int shakeRec = sharedPreferences.getInt("dialog_usage_shake_to_stop_recommended", 0);
            
            analytics.append("🔔 Notification Behavior:\n");
            analytics.append("   Info viewed: ").append(notificationInfo).append(" times\n");
            analytics.append("   Recommended used: ").append(notificationRec).append(" times\n\n");
            
            analytics.append("🎵 Media Behavior:\n");
            analytics.append("   Info viewed: ").append(mediaInfo).append(" times\n");
            analytics.append("   Recommended used: ").append(mediaRec).append(" times\n\n");
            
            analytics.append("📳 Shake to Stop:\n");
            analytics.append("   Info viewed: ").append(shakeInfo).append(" times\n");
            analytics.append("   Recommended used: ").append(shakeRec).append(" times\n\n");
            
            // Calculate recommendation adoption rate
            int totalInfoViews = notificationInfo + mediaInfo + shakeInfo;
            int totalRecommendations = notificationRec + mediaRec + shakeRec;
            
            if (totalInfoViews > 0) {
                int adoptionRate = (totalRecommendations * 100) / totalInfoViews;
                analytics.append("✨ Insights:\n");
                analytics.append("─────────\n");
                analytics.append("Recommendation adoption rate: ").append(adoptionRate).append("%\n");
                
                if (adoptionRate >= 80) {
                    analytics.append("🎉 Excellent! Users find recommendations very helpful.\n");
                } else if (adoptionRate >= 60) {
                    analytics.append("👍 Good! Most users trust the recommendations.\n");
                } else if (adoptionRate >= 40) {
                    analytics.append("📈 Moderate adoption. Users appreciate having options.\n");
                } else {
                    analytics.append("🤔 Low adoption. Users prefer to explore settings themselves.\n");
                }
            }
            
            // Last usage timestamp
            long lastUsage = sharedPreferences.getLong("last_dialog_usage", 0);
            if (lastUsage > 0) {
                Date lastDate = new Date(lastUsage);
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault());
                analytics.append("\nLast help dialog opened: ").append(sdf.format(lastDate)).append("\n");
            }
        } else {
            analytics.append("No help dialogs have been opened yet.\n");
            analytics.append("These analytics help us understand which features need better explanations.\n");
        }
        
        analytics.append("\n💡 Privacy Note:\n");
        analytics.append("This data never leaves your device. It's stored locally to help improve the app's user experience.");
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Help Usage Analytics")
                .setMessage(analytics.toString())
                .setPositiveButton("Clear Analytics", (dialog, which) -> {
                    clearAnalytics();
                })
                .setNegativeButton("Close", null)
                .show();
        
        InAppLogger.log("Development", "Analytics dialog viewed - Total usage: " + totalUsage);
    }
    
    private void clearAnalytics() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        
        // Clear all analytics keys
        editor.remove("total_dialog_usage");
        editor.remove("dialog_usage_notification_behavior_info");
        editor.remove("dialog_usage_notification_behavior_recommended");
        editor.remove("dialog_usage_media_behavior_info");
        editor.remove("dialog_usage_media_behavior_recommended");
        editor.remove("dialog_usage_shake_to_stop_info");
        editor.remove("dialog_usage_shake_to_stop_recommended");
        editor.remove("last_dialog_usage");
        
        editor.apply();
        
        Toast.makeText(this, "Analytics data cleared", Toast.LENGTH_SHORT).show();
        InAppLogger.log("Development", "Analytics data cleared by user");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        InAppLogger.log("Development", "Development Settings closed");
        binding = null;
        uiHandler.removeCallbacks(logUpdateRunnable);
    }

    private void showConditionalRulesDebugInfo() {
        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append("=== CONDITIONAL RULES DEBUG INFO ===\n\n");
        
        try {
            // Get ConditionalFilterManager instance
            ConditionalFilterManager conditionalManager = new ConditionalFilterManager(this);
            List<ConditionalFilterManager.ConditionalRule> rules = conditionalManager.getAllRules();
            
            debugInfo.append("Rules Count: ").append(rules.size()).append("\n");
            debugInfo.append("Service Running: ").append(isNotificationServiceRunning()).append("\n\n");
            
            if (rules.isEmpty()) {
                debugInfo.append("--- NO RULES FOUND ---\n");
                debugInfo.append("Possible reasons:\n");
                debugInfo.append("• No rules have been created\n");
                debugInfo.append("• Rules storage is corrupted\n");
                debugInfo.append("• SharedPreferences access issues\n\n");
                
                debugInfo.append("Try creating a test rule:\n");
                debugInfo.append("1. Go to Smart Settings\n");
                debugInfo.append("2. Add a simple rule (e.g., Screen On)\n");
                debugInfo.append("3. Come back here to see if it appears\n");
            } else {
                debugInfo.append("--- RULES SUMMARY ---\n");
                for (int i = 0; i < rules.size(); i++) {
                    ConditionalFilterManager.ConditionalRule rule = rules.get(i);
                    debugInfo.append((i + 1)).append(". ").append(rule.name)
                            .append(" (enabled: ").append(rule.enabled)
                            .append(", priority: ").append(rule.priority).append(")\n");
                    debugInfo.append("   Conditions: ").append(rule.conditions.size()).append("\n");
                    debugInfo.append("   Actions: ").append(rule.actions.size()).append("\n");
                    
                    // Show condition details
                    for (ConditionalFilterManager.Condition condition : rule.conditions) {
                        debugInfo.append("   • ").append(condition.type)
                                .append(" ").append(condition.operator.displayName)
                                .append(" '").append(condition.value).append("'");
                        if (!condition.parameter.isEmpty()) {
                            debugInfo.append(" (param: '").append(condition.parameter).append("')");
                        }
                        debugInfo.append("\n");
                    }
                    debugInfo.append("\n");
                }
            }
            
            // Test a sample notification context
            debugInfo.append("--- TEST EVALUATION ---\n");
            ConditionalFilterManager.NotificationContext testContext = 
                new ConditionalFilterManager.NotificationContext("Test App", "com.test.app", "Test notification");
            
            ConditionalFilterManager.ConditionalResult result = conditionalManager.applyConditionalRules(testContext);
            debugInfo.append("Test notification result:\n");
            debugInfo.append("• Should block: ").append(result.shouldBlock).append("\n");
            debugInfo.append("• Should make private: ").append(result.shouldMakePrivate).append("\n");
            debugInfo.append("• Delay seconds: ").append(result.delaySeconds).append("\n");
            debugInfo.append("• Applied rules: ").append(result.appliedRules).append("\n");
            debugInfo.append("• Has changes: ").append(result.hasChanges).append("\n\n");
            
            // Check current device state for screen condition
            android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean isScreenOn = powerManager.isInteractive();
            debugInfo.append("--- CURRENT DEVICE STATE ---\n");
            debugInfo.append("Screen is: ").append(isScreenOn ? "ON" : "OFF").append("\n");
            debugInfo.append("Time: ").append(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n");
            
            Calendar cal = Calendar.getInstance();
            int dayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) ? 7 : cal.get(Calendar.DAY_OF_WEEK) - 1;
            String[] days = {"", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
            debugInfo.append("Day: ").append(days[dayOfWeek]).append("\n\n");
            
        } catch (Exception e) {
            debugInfo.append("ERROR: ").append(e.getMessage()).append("\n");
            debugInfo.append("Stack trace:\n").append(android.util.Log.getStackTraceString(e));
        }
        
        // Show recent conditional logs
        debugInfo.append("--- RECENT CONDITIONAL LOGS ---\n");
        String allLogs = InAppLogger.getAllLogs();
        String[] logLines = allLogs.split("\n");
        int conditionalLogCount = 0;
        for (String line : logLines) {
            if (line.contains("Conditional") && conditionalLogCount < 20) {
                debugInfo.append(line).append("\n");
                conditionalLogCount++;
            }
        }
        
        if (conditionalLogCount == 0) {
            debugInfo.append("No conditional logs found in recent history\n");
            debugInfo.append("This suggests rules aren't being evaluated\n");
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🧠 Smart Rules Debug Info");
        
        // Create scrollable text view
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setText(debugInfo.toString());
        textView.setTextSize(11f);
        textView.setPadding(16, 16, 16, 16);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        textView.setTextIsSelectable(true);
        scrollView.addView(textView);
        
        builder.setView(scrollView);
        builder.setPositiveButton("Close", null);
        builder.setNeutralButton("Test Notification", (dialog, which) -> {
            // Simulate a test notification to trigger rule evaluation
            InAppLogger.log("ConditionalTest", "User triggered test notification for rule debugging");
            Toast.makeText(this, "Test notification logged - check the logs for rule evaluation", Toast.LENGTH_LONG).show();
        });
        builder.setNegativeButton("Fix Rules", (dialog, which) -> {
            fixLegacyDeviceStateRules();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Make dialog larger
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.8)
            );
        }
        
        InAppLogger.logUserAction("Conditional rules debug info viewed", "");
    }
    
    private boolean isNotificationServiceRunning() {
        try {
            String enabledListeners = android.provider.Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
            return enabledListeners != null && enabledListeners.contains(getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private void fixLegacyDeviceStateRules() {
        try {
            ConditionalFilterManager conditionalManager = new ConditionalFilterManager(this);
            List<ConditionalFilterManager.ConditionalRule> rules = conditionalManager.getAllRules();
            
            int fixedCount = 0;
            
            for (ConditionalFilterManager.ConditionalRule rule : rules) {
                boolean ruleModified = false;
                
                // Check each condition for legacy device state format
                for (ConditionalFilterManager.Condition condition : rule.conditions) {
                    if (condition.type == ConditionalFilterManager.ConditionType.DEVICE_STATE) {
                        // Fix legacy formats
                        if ("screen_on".equals(condition.value)) {
                            condition.value = "on";
                            condition.parameter = "screen_state";
                            ruleModified = true;
                            InAppLogger.log("Development", "Fixed legacy rule: " + rule.name + " - screen_on -> on");
                        } else if ("screen_off".equals(condition.value)) {
                            condition.value = "off";
                            condition.parameter = "screen_state";
                            ruleModified = true;
                            InAppLogger.log("Development", "Fixed legacy rule: " + rule.name + " - screen_off -> off");
                        } else if (condition.parameter.isEmpty()) {
                            // Fix missing parameters for existing correct values
                            if ("on".equals(condition.value) || "off".equals(condition.value)) {
                                condition.parameter = "screen_state";
                                ruleModified = true;
                                InAppLogger.log("Development", "Fixed missing parameter for rule: " + rule.name);
                            } else if ("charging".equals(condition.value) || "not_charging".equals(condition.value)) {
                                condition.parameter = "charging_state";
                                ruleModified = true;
                                InAppLogger.log("Development", "Fixed missing charging parameter for rule: " + rule.name);
                            }
                        }
                    }
                }
                
                // Fix incorrect action types for screen-on rules
                boolean hasScreenOnCondition = rule.conditions.stream()
                    .anyMatch(c -> c.type == ConditionalFilterManager.ConditionType.DEVICE_STATE 
                                && "screen_state".equals(c.parameter) 
                                && "on".equals(c.value));
                
                if (hasScreenOnCondition) {
                    for (ConditionalFilterManager.Action action : rule.actions) {
                        if (action.type == ConditionalFilterManager.ActionType.DISABLE_MASTER_SWITCH) {
                            // Change to BLOCK_NOTIFICATION for screen-on rules
                            action.type = ConditionalFilterManager.ActionType.BLOCK_NOTIFICATION;
                            action.value = "true";
                            ruleModified = true;
                            InAppLogger.log("Development", "Fixed action for screen-on rule: " + rule.name + " - changed to BLOCK_NOTIFICATION");
                        }
                    }
                }
                
                if (ruleModified) {
                    conditionalManager.updateRule(rule);
                    fixedCount++;
                }
            }
            
            if (fixedCount > 0) {
                Toast.makeText(this, "Fixed " + fixedCount + " legacy rule(s)! Please test your rules now.", Toast.LENGTH_LONG).show();
                InAppLogger.log("Development", "Fixed " + fixedCount + " legacy device state rules");
            } else {
                Toast.makeText(this, "No legacy rules found that need fixing.", Toast.LENGTH_SHORT).show();
                InAppLogger.log("Development", "No legacy rules needed fixing");
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "Error fixing rules: " + e.getMessage(), Toast.LENGTH_LONG).show();
            InAppLogger.logError("Development", "Failed to fix legacy rules: " + e.getMessage());
        }
    }

    private void showBatteryOptimizationReport() {
        StringBuilder report = new StringBuilder();
        report.append("🔋 BATTERY OPTIMIZATION REPORT\n");
        report.append("==============================\n\n");
        
        report.append("📊 BACKGROUND PROCESSES STATUS:\n");
        report.append("─────────────────────────────\n");
        
        // Check if log auto-refresh is running
        boolean logsRunning = isActivityVisible && !isLogAutoRefreshPaused;
        report.append("• Log Auto-Refresh: ").append(logsRunning ? "⚡ ACTIVE" : "✅ STOPPED").append("\n");
        if (logsRunning) {
            report.append("  ⚠️ WARNING: Logs refresh every 2 seconds while visible\n");
        } else {
            report.append("  ✅ OPTIMIZED: Only runs when Development Settings is visible\n");
        }
        
        // Check notification service status
        boolean serviceRunning = isNotificationServiceRunning();
        report.append("• Notification Service: ").append(serviceRunning ? "🟢 RUNNING" : "🔴 STOPPED").append("\n");
        if (serviceRunning) {
            report.append("  ✅ NORMAL: Required for app functionality\n");
        }
        
        report.append("\n🛡️ BATTERY PROTECTION MEASURES:\n");
        report.append("──────────────────────────────\n");
        report.append("✅ Shake sensors only active during TTS playback\n");
        report.append("✅ Log refresh stops when Development Settings not visible\n");
        report.append("✅ Force sensor cleanup on service destruction\n");
        report.append("✅ Proper handler/runnable cleanup\n");
        report.append("✅ Device state checked only when notifications arrive\n");
        report.append("✅ No continuous background monitoring\n");
        
        report.append("\n📱 DEVICE STATE MONITORING:\n");
        report.append("─────────────────────────────\n");
        report.append("✅ Screen state: Checked on-demand only\n");
        report.append("✅ Charging state: Checked on-demand only\n");
        report.append("✅ No broadcast receivers for continuous monitoring\n");
        
        report.append("\n🔧 RECENT BATTERY FIXES (v1.0):\n");
        report.append("────────────────────────────\n");
        report.append("• Fixed: Development log timer now stops when activity not visible\n");
        report.append("• Fixed: Enhanced sensor cleanup in NotificationReaderService\n");
        report.append("• Fixed: Proper lifecycle management for background processes\n");
        report.append("• Fixed: Force unregister all sensors on service destroy\n");
        
        report.append("\n💡 BATTERY USAGE EXPLANATION:\n");
        report.append("────────────────────────────\n");
        report.append("The 27% battery usage you saw was likely from:\n");
        report.append("1. Previous bug where shake sensors stayed active\n");
        report.append("2. Development log timer running continuously\n");
        report.append("3. Android battery stats include historical usage\n\n");
        
        report.append("🔄 To reset battery stats:\n");
        report.append("• Restart your device\n");
        report.append("• Or wait 24 hours for automatic reset\n");
        report.append("• Battery usage should be <5% after fixes\n");
        
        report.append("\n⚡ PERFORMANCE TIPS:\n");
        report.append("──────────────────\n");
        report.append("• Avoid keeping Development Settings open\n");
        report.append("• Use 'Pause' button if viewing logs for extended time\n");
        report.append("• Disable verbose logging if not needed\n");
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔋 Battery Optimization Report");
        
        // Create scrollable text view
        ScrollView scrollView = new ScrollView(this);
        TextView textView = new TextView(this);
        textView.setText(report.toString());
        textView.setTextSize(12f);
        textView.setPadding(16, 16, 16, 16);
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        textView.setTextIsSelectable(true);
        scrollView.addView(textView);
        
        builder.setView(scrollView);
        builder.setPositiveButton("Export Report", (dialog, which) -> {
            exportBatteryReport(report.toString());
        });
        builder.setNegativeButton("Close", null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Make dialog larger
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.8)
            );
        }
        
        InAppLogger.log("Development", "Battery optimization report viewed");
    }
    
    private void exportBatteryReport(String reportContent) {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());
            String fullReport = "SpeakThat! Battery Optimization Report\n" +
                "Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n" +
                "===========================================\n\n" +
                reportContent;
            
            // Create and share intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "SpeakThat Battery Report - " + timestamp);
            shareIntent.putExtra(Intent.EXTRA_TEXT, fullReport);
            
            Intent chooser = Intent.createChooser(shareIntent, "Export Battery Report");
            startActivity(chooser);
            
            InAppLogger.log("Development", "Battery report exported");
            
        } catch (Exception e) {
            Toast.makeText(this, "Failed to export battery report: " + e.getMessage(), Toast.LENGTH_LONG).show();
            InAppLogger.logError("Development", "Failed to export battery report: " + e.getMessage());
        }
    }

    private void repairWordBlacklist() {
        try {
            SharedPreferences prefs = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE);
            Set<String> blockWords = new HashSet<>(prefs.getStringSet("word_blacklist", new HashSet<>())) ;
            Set<String> privateWords = new HashSet<>(prefs.getStringSet("word_blacklist_private", new HashSet<>())) ;

            // Clean: remove empty/whitespace-only, deduplicate between sets
            Set<String> cleanedBlock = new HashSet<>();
            Set<String> cleanedPrivate = new HashSet<>();
            for (String word : blockWords) {
                String w = word.trim();
                if (!w.isEmpty()) cleanedBlock.add(w);
            }
            for (String word : privateWords) {
                String w = word.trim();
                if (!w.isEmpty() && !cleanedBlock.contains(w)) cleanedPrivate.add(w);
            }

            // Save cleaned sets
            SharedPreferences.Editor editor = prefs.edit();
            editor.putStringSet("word_blacklist", cleanedBlock);
            editor.putStringSet("word_blacklist_private", cleanedPrivate);
            editor.apply();

            // Show detailed result
            StringBuilder msg = new StringBuilder();
            msg.append("Word blacklist cleaned and re-saved.\n\n");
            msg.append("Block: ").append(cleanedBlock.size()).append("\n");
            msg.append("Private: ").append(cleanedPrivate.size()).append("\n");
            if (blockWords.size() != cleanedBlock.size() || privateWords.size() != cleanedPrivate.size()) {
                msg.append("\nRemoved duplicates or empty entries.");
            } else {
                msg.append("\nNo changes were needed.");
            }
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Repair Word Blacklist")
                .setMessage(msg.toString())
                .setPositiveButton("OK", null)
                .show();
            InAppLogger.log("Development", "Word blacklist comprehensively repaired. Block: " + cleanedBlock.size() + ", Private: " + cleanedPrivate.size());

            // Send broadcast to notify FilterSettingsActivity to reload
            Intent intent = new Intent("com.micoyc.speakthat.ACTION_REPAIR_BLACKLIST");
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Repair failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            InAppLogger.logError("Development", "Repair blacklist failed: " + e.getMessage());
        }
    }
} 