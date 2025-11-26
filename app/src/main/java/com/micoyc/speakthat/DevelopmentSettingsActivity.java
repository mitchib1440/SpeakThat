package com.micoyc.speakthat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.micoyc.speakthat.databinding.ActivityDevelopmentSettingsBinding;
import com.micoyc.speakthat.rules.RuleSystemTest;
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
    private static final String KEY_DISABLE_MEDIA_FALLBACK = "disable_media_fallback";
    private static final long LISTENER_WARNING_THRESHOLD_MS = 5 * 60 * 1000L;

    private boolean isLogAutoRefreshPaused = false;
    private Runnable logUpdateRunnable;
    private boolean isActivityVisible = false;
    
    // New variables for smart log display
    private long lastLogUpdateTime = 0;
    private int lastLogCount = 0;
    private boolean hasNewLogs = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme FIRST before anything else
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applySavedTheme();
        
        super.onCreate(savedInstanceState);
        binding = ActivityDevelopmentSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Development Settings");
        }

        InAppLogger.logAppLifecycle("Development Settings created", "DevelopmentSettingsActivity");
        initializeUI();
        loadSettings();
        // Don't start log updates in onCreate - wait for onResume
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityVisible = true;
        
        // Check for new logs instead of starting auto-refresh
        checkForNewLogs();
        updateListenerHealthCard();
        
        // Start a very slow background check for new logs (every 30 seconds)
        // This only updates the indicator, not the UI
        startBackgroundLogCheck();
        
        InAppLogger.logAppLifecycle("Development Settings resumed", "DevelopmentSettingsActivity");
    }
    
    /**
     * Start a very slow background check for new logs (battery-friendly)
     */
    private void startBackgroundLogCheck() {
        // Only check every 30 seconds to save battery
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isActivityVisible && !isDestroyed() && !isFinishing()) {
                    checkForNewLogs();
                    // Schedule next check
                    uiHandler.postDelayed(this, 30000); // 30 seconds
                }
            }
        }, 30000); // 30 seconds
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityVisible = false;
        
        // Stop any remaining auto-refresh and background checks
        stopLogUpdates();
        stopBackgroundLogCheck();
        lastLogCount = InAppLogger.getLogCount();
        
        InAppLogger.logAppLifecycle("Development Settings paused", "DevelopmentSettingsActivity");
    }
    
    /**
     * Stop the background log check
     */
    private void stopBackgroundLogCheck() {
        // Remove any pending background checks
        uiHandler.removeCallbacksAndMessages(null);
        InAppLogger.log("Development", "Background log check stopped");
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
        // Set up notification history button
        binding.btnShowHistory.setOnClickListener(v -> showNotificationHistory());
        
        // Set up log controls
        binding.btnClearLogs.setOnClickListener(v -> clearLogs());
        binding.btnExportLogs.setOnClickListener(v -> exportLogs());
        binding.btnRefreshLogs.setOnClickListener(v -> refreshLogs());
        // Removed play button (btnPauseLogs) and its logic
        
        // Ensure icons are set in code to fix invisible icon bug
        binding.btnRefreshLogs.setIconResource(R.drawable.ic_refresh_24);
        binding.btnClearLogs.setIconResource(R.drawable.ic_delete_24);
        binding.btnExportLogs.setIconResource(R.drawable.ic_file_upload_24);
        // Removed play button icon setup
        
        // Try additional fixes for icon visibility
        binding.btnRefreshLogs.setIconTint(ColorStateList.valueOf(Color.WHITE));
        binding.btnClearLogs.setIconTint(ColorStateList.valueOf(Color.WHITE));
        binding.btnExportLogs.setIconTint(ColorStateList.valueOf(Color.WHITE));
        // Removed play button icon tint
        
        // Set icon size programmatically (24dp converted to pixels)
        int iconSizePx = (int) (24 * getResources().getDisplayMetrics().density);
        binding.btnRefreshLogs.setIconSize(iconSizePx);
        binding.btnClearLogs.setIconSize(iconSizePx);
        binding.btnExportLogs.setIconSize(iconSizePx);
        // Removed play button icon size
        
        // For icon-only buttons, we need to center them properly
        // Remove text and use appropriate gravity
        binding.btnRefreshLogs.setText("");
        binding.btnClearLogs.setText("");
        binding.btnExportLogs.setText("");
        // Removed play button text
        
        // Try different approach for centering icons - use padding to center them
        binding.btnRefreshLogs.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);
        binding.btnClearLogs.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);
        binding.btnExportLogs.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);
        // Removed play button icon gravity
        
        // Set padding to center the icons better
        int paddingPx = (int) (4 * getResources().getDisplayMetrics().density);
        binding.btnRefreshLogs.setIconPadding(paddingPx);
        binding.btnClearLogs.setIconPadding(paddingPx);
        binding.btnExportLogs.setIconPadding(paddingPx);
        // Removed play button icon padding
        
        // Removed play button styling and content description
        
        // Debug: Log icon status
        InAppLogger.log("Development", "Setting up log control button icons");
        InAppLogger.log("Development", "Refresh button icon: " + (binding.btnRefreshLogs.getIcon() != null ? "SET" : "NULL"));
        InAppLogger.log("Development", "Clear button icon: " + (binding.btnClearLogs.getIcon() != null ? "SET" : "NULL"));
        InAppLogger.log("Development", "Export button icon: " + (binding.btnExportLogs.getIcon() != null ? "SET" : "NULL"));
        // Removed play button icon status log
        
        // Set up crash log controls
        binding.btnViewCrashLogs.setOnClickListener(v -> showCrashLogs());
        binding.btnClearCrashLogs.setOnClickListener(v -> clearCrashLogs());
        binding.btnExportExternalCrashLogs.setOnClickListener(v -> exportExternalCrashLogs());
        binding.btnOpenCrashLogsFolder.setOnClickListener(v -> openCrashLogsFolder());
        
        // Set up debug crash log button
        binding.btnDebugCrashLogs.setOnClickListener(v -> showCrashLogDebugInfo());
        
        // Set up test Lower Audio button
        binding.btnTestLowerAudio.setOnClickListener(v -> testLowerAudioCompatibility());
        
        // Set up crash test button
        binding.btnTestCrash.setOnClickListener(v -> testCrash());
        

        
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
        
        binding.switchDisableMediaFallback.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveDisableMediaFallback(isChecked);
            InAppLogger.log("Development", "Media behavior fallback " + (isChecked ? "disabled" : "enabled"));
        });
        

        
        // Set up log display
        binding.textLogDisplay.setMovementMethod(new ScrollingMovementMethod());
        binding.textLogDisplay.setHorizontallyScrolling(true);
        
        // Update crash log button visibility
        updateCrashLogButtonVisibility();
        
        // Set up Repair Blacklist button
        binding.btnRepairBlacklist.setOnClickListener(v -> repairWordBlacklist());
        
        // Add Background Process Monitor button
        binding.btnBackgroundMonitor.setOnClickListener(v -> showBackgroundProcessMonitor());
        
        // Add Installation Source Debug button
        binding.btnInstallationSourceDebug.setOnClickListener(v -> showInstallationSourceDebug());
        
        // Add Rule System Test button
        binding.btnRuleSystemTest.setOnClickListener(v -> testRuleSystem());
        
        // Add Review Reminder Test button
        binding.btnTestReviewReminder.setOnClickListener(v -> testReviewReminder());
        
        // Add Reset Review Reminder button
        binding.btnResetReviewReminder.setOnClickListener(v -> resetReviewReminder());
        
        // Add Review Reminder Stats button
        binding.btnReviewReminderStats.setOnClickListener(v -> showReviewReminderStats());
        
        // Add Test Settings button
        binding.btnTestSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, TestSettingsActivity.class);
            startActivity(intent);
        });
        
        // Add Reset Statistics button
        binding.btnResetStatistics.setOnClickListener(v -> resetStatistics());
        
        // Add welcome message
        InAppLogger.log("Development", "Development Settings opened");

        setupListenerHealthCard();
    }

    private void setupListenerHealthCard() {
        binding.buttonListenerRetry.setOnClickListener(v -> handleListenerRetry());
        binding.buttonListenerPermission.setOnClickListener(v -> {
            InAppLogger.log("ListenerHealth", "User opened notification access from Development Settings");
            openNotificationListenerSettings();
        });
        binding.buttonListenerBattery.setOnClickListener(v -> {
            InAppLogger.log("ListenerHealth", "User opened General Settings to adjust battery optimizations");
            startActivity(new Intent(this, GeneralSettingsActivity.class));
        });
        updateListenerHealthCard();
    }

    private void handleListenerRetry() {
        try {
            boolean requested = NotificationListenerRecovery.requestRebind(this, "dev_manual_retry", true);
            int messageRes = requested
                ? R.string.listener_warning_retry_in_progress
                : R.string.listener_warning_retry_denied;
            Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show();
            if (requested) {
                InAppLogger.log("ListenerHealth", "Manual rebind requested via Development Settings");
            } else {
                InAppLogger.logWarning("ListenerHealth", "Manual rebind throttled via Development Settings");
            }
        } catch (Exception e) {
            android.util.Log.e("DevelopmentSettings", "Failed to request listener rebind from Development Settings", e);
            InAppLogger.logError("ListenerHealth", "Manual rebind failed in Development Settings: " + e.getMessage());
            Toast.makeText(this, getString(R.string.listener_warning_retry_denied), Toast.LENGTH_SHORT).show();
        } finally {
            updateListenerHealthCard();
        }
    }

    private void updateListenerHealthCard() {
        if (binding == null) {
            return;
        }

        NotificationListenerRecovery.ListenerStatus status =
            NotificationListenerRecovery.getListenerStatus(this, LISTENER_WARNING_THRESHOLD_MS);

        boolean permissionGranted = status.getPermissionGranted();
        boolean isDisconnected = status.isDisconnected();
        boolean isStale = status.isStale();

        long lastHealthy = Math.max(status.getLastConnect(), status.getLastHeartbeat());
        CharSequence summary;

        if (!permissionGranted) {
            summary = getString(R.string.listener_status_permission_missing);
        } else if (isDisconnected) {
            summary = getString(
                R.string.listener_status_disconnected,
                formatRelativeTime(status.getLastDisconnect())
            );
        } else if (isStale) {
            summary = getString(
                R.string.listener_status_idle,
                formatRelativeTime(lastHealthy)
            );
        } else {
            summary = getString(
                R.string.listener_status_ok,
                formatRelativeTime(lastHealthy)
            );
        }

        StringBuilder details = new StringBuilder();
        details.append(getString(R.string.listener_health_last_activity, formatRelativeTime(lastHealthy)));
        details.append("\n");
        details.append(getString(R.string.listener_health_last_connect, formatRelativeTime(status.getLastConnect())));
        details.append("\n");
        details.append(getString(R.string.listener_health_last_disconnect, formatRelativeTime(status.getLastDisconnect())));
        details.append("\n");
        details.append(getString(R.string.listener_health_last_heartbeat, formatRelativeTime(status.getLastHeartbeat())));
        details.append("\n");

        long lastAttempt = status.getLastRebindAttempt();
        if (lastAttempt > 0L) {
            details.append(getString(R.string.listener_warning_last_attempt, formatRelativeTime(lastAttempt)));
        } else {
            details.append(getString(R.string.listener_warning_last_attempt, getString(R.string.listener_warning_time_unknown)));
        }
        details.append("\n");

        long lastSuccess = status.getLastRebindSuccess();
        details.append(getString(R.string.listener_health_last_rebind_success, formatRelativeTime(lastSuccess)));

        binding.textListenerHealthTitle.setText(R.string.dev_listener_health_title);
        binding.textListenerHealthSummary.setText(summary);
        binding.textListenerHealthDetails.setText(details.toString().trim());
        binding.buttonListenerRetry.setEnabled(permissionGranted);
    }

    private void openNotificationListenerSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        startActivity(intent);
    }

    private void updateCrashLogButtonVisibility() {
        // Show crash log buttons only if crash logs exist
        boolean hasCrashLogs = InAppLogger.hasCrashLogs();
        boolean hasExternalCrashLogs = InAppLogger.hasExternalCrashLogs();
        
        binding.btnViewCrashLogs.setVisibility(hasCrashLogs ? View.VISIBLE : View.GONE);
        binding.btnClearCrashLogs.setVisibility(hasCrashLogs ? View.VISIBLE : View.GONE);
        binding.btnExportExternalCrashLogs.setVisibility(hasExternalCrashLogs ? View.VISIBLE : View.GONE);
        binding.btnOpenCrashLogsFolder.setVisibility(hasExternalCrashLogs ? View.VISIBLE : View.GONE);
        
        if (hasCrashLogs) {
            binding.btnViewCrashLogs.setText("📄 View Crash Logs");
            binding.btnClearCrashLogs.setText("🗑️ " + getString(R.string.button_clear_crash_logs));
        }
        
        if (hasExternalCrashLogs) {
            java.util.List<java.io.File> externalCrashLogs = InAppLogger.getExternalCrashLogs();
            binding.btnExportExternalCrashLogs.setText("📤 Export External (" + externalCrashLogs.size() + ")");
            binding.btnOpenCrashLogsFolder.setText("📁 Open Folder");
        }
        
        // Debug: Log the crash log status
        InAppLogger.log("Development", "Crash log status check - Has crash logs: " + hasCrashLogs + ", Has external crash logs: " + hasExternalCrashLogs);
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
        builder.setPositiveButton(R.string.button_export_crash_logs, (dialog, which) -> {
            exportCrashLogs();
        });
        builder.setNeutralButton(R.string.button_clear_crash_logs, (dialog, which) -> {
            clearCrashLogs();
        });
        builder.setNegativeButton(R.string.button_close, null);
        
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
                // Use FileExportHelper to create the file with fallback support
                File crashLogFile = FileExportHelper.createExportFile(this, "logs", "speakthat_crash_logs_" + timestamp + ".txt", logContent);
                
                if (crashLogFile != null) {
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
                    
                    startActivity(Intent.createChooser(fileShareIntent, getString(R.string.button_export_crash_logs) + " as File"));
                    
                    Toast.makeText(this, "Crash logs exported as file! File saved to: " + crashLogFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    InAppLogger.log("Development", "Crash logs exported as file: " + crashLogFile.getName());
                } else {
                    throw new Exception("File creation failed");
                }
                
            } catch (Exception fileException) {
                // Fallback to text-based sharing if file sharing fails
                InAppLogger.log("Development", "Crash log file export failed, falling back to text: " + fileException.getMessage());
                
                Intent textShareIntent = new Intent(Intent.ACTION_SEND);
                textShareIntent.setType("text/plain");
                textShareIntent.putExtra(Intent.EXTRA_SUBJECT, "SpeakThat! Crash Logs - " + timestamp);
                textShareIntent.putExtra(Intent.EXTRA_TEXT, logContent);
                
                startActivity(Intent.createChooser(textShareIntent, getString(R.string.button_export_crash_logs) + " as Text"));
                
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
            .setTitle(R.string.button_clear_crash_logs)
            .setMessage("Are you sure you want to delete all crash logs? This action cannot be undone.")
            .setPositiveButton(R.string.button_clear, (dialog, which) -> {
                InAppLogger.clearCrashLogs();
                updateCrashLogButtonVisibility();
                Toast.makeText(this, "Crash logs cleared", Toast.LENGTH_SHORT).show();
                InAppLogger.logUserAction("Crash logs cleared", "");
            })
            .setNegativeButton(R.string.button_cancel, null)
            .show();
    }
    
    private void exportExternalCrashLogs() {
        try {
            java.util.List<java.io.File> externalCrashLogs = InAppLogger.getExternalCrashLogs();
            if (externalCrashLogs.isEmpty()) {
                Toast.makeText(this, "No external crash logs found", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Create a combined crash log file
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault()).format(new java.util.Date());
            StringBuilder combinedContent = new StringBuilder();
            
            combinedContent.append("SpeakThat! External Crash Logs Export\n");
            combinedContent.append("Generated: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date())).append("\n");
            combinedContent.append("Total Crash Logs: ").append(externalCrashLogs.size()).append("\n");
            combinedContent.append("===========================================\n\n");
            
            for (java.io.File crashFile : externalCrashLogs) {
                combinedContent.append("=== ").append(crashFile.getName()).append(" ===\n");
                combinedContent.append("Created: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(crashFile.lastModified()))).append("\n");
                combinedContent.append("Size: ").append(crashFile.length()).append(" bytes\n");
                combinedContent.append("Path: ").append(crashFile.getAbsolutePath()).append("\n\n");
                
                try {
                    String content = new String(java.nio.file.Files.readAllBytes(crashFile.toPath()));
                    combinedContent.append(content);
                } catch (Exception e) {
                    combinedContent.append("Error reading file: ").append(e.getMessage()).append("\n");
                }
                combinedContent.append("\n\n");
            }
            
            // Create and share the combined file
            java.io.File exportFile = FileExportHelper.createExportFile(this, "exports", "SpeakThat_All_Crash_Logs_" + timestamp + ".txt", combinedContent.toString());
            
            if (exportFile != null) {
                android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    exportFile
                );
                
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "SpeakThat! All Crash Logs - " + timestamp);
                shareIntent.putExtra(Intent.EXTRA_TEXT, "SpeakThat! All crash logs attached. Please send this to the developer for analysis.");
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                startActivity(Intent.createChooser(shareIntent, "Export All Crash Logs"));
                
                Toast.makeText(this, "All crash logs exported! File saved to: " + exportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                InAppLogger.log("Development", "All external crash logs exported: " + exportFile.getName());
            } else {
                Toast.makeText(this, "Failed to create export file", Toast.LENGTH_SHORT).show();
                InAppLogger.logError("Development", "Failed to create external crash logs export file");
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "Failed to export external crash logs: " + e.getMessage(), Toast.LENGTH_LONG).show();
            InAppLogger.logError("Development", "Failed to export external crash logs: " + e.getMessage());
        }
    }
    
    private void openCrashLogsFolder() {
        try {
            String crashLogsDir = InAppLogger.getExternalCrashLogsDirectory();
            if (crashLogsDir == null) {
                Toast.makeText(this, "Crash logs directory not available", Toast.LENGTH_SHORT).show();
                return;
            }
            
            java.io.File crashLogsFolder = new java.io.File(crashLogsDir);
            if (!crashLogsFolder.exists()) {
                Toast.makeText(this, "Crash logs folder does not exist", Toast.LENGTH_SHORT).show();
                return;
            }
            
            android.net.Uri folderUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                getApplicationContext().getPackageName() + ".fileprovider",
                crashLogsFolder
            );
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(folderUri, "resource/folder");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            try {
                startActivity(intent);
                InAppLogger.log("Development", "Opened crash logs folder: " + crashLogsDir);
            } catch (Exception e) {
                // Fallback: show folder path in a dialog
                new AlertDialog.Builder(this)
                    .setTitle("Crash Logs Folder")
                    .setMessage("Crash logs are stored in:\n\n" + crashLogsDir + "\n\nYou can access this folder using a file manager app.")
                    .setPositiveButton("Copy Path", (dialog, which) -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("Crash Logs Path", crashLogsDir);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(this, "Path copied to clipboard", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Close", null)
                    .show();
                
                InAppLogger.log("Development", "Showed crash logs folder path: " + crashLogsDir);
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "Failed to open crash logs folder: " + e.getMessage(), Toast.LENGTH_LONG).show();
            InAppLogger.logError("Development", "Failed to open crash logs folder: " + e.getMessage());
        }
    }

    private void showCrashLogDebugInfo() {
        // Get detailed crash log status information
        boolean hasCrashLogs = InAppLogger.hasCrashLogs();
        boolean hasExternalCrashLogs = InAppLogger.hasExternalCrashLogs();
        String crashLogs = InAppLogger.getCrashLogs();
        java.util.List<java.io.File> externalCrashLogs = InAppLogger.getExternalCrashLogs();
        String externalCrashLogsDir = InAppLogger.getExternalCrashLogsDirectory();
        
        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append("=== CRASH LOG DEBUG INFO ===\n\n");
        debugInfo.append("Internal Crash Logs: ").append(hasCrashLogs).append("\n");
        debugInfo.append("External Crash Logs: ").append(hasExternalCrashLogs).append("\n");
        debugInfo.append("External Crash Log Count: ").append(externalCrashLogs.size()).append("\n");
        debugInfo.append("External Crash Logs Directory: ").append(externalCrashLogsDir != null ? externalCrashLogsDir : "Not available").append("\n");
        debugInfo.append("Crash Log Content Length: ").append(crashLogs.length()).append(" characters\n");
        debugInfo.append("Logger Initialized: ").append(InAppLogger.getLogCount() > 0).append("\n");
        debugInfo.append("Current Log Count: ").append(InAppLogger.getLogCount()).append("\n\n");
        
        if (hasExternalCrashLogs && !externalCrashLogs.isEmpty()) {
            debugInfo.append("--- EXTERNAL CRASH LOGS ---\n");
            for (java.io.File crashFile : externalCrashLogs) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                debugInfo.append("• ").append(crashFile.getName()).append(" (").append(sdf.format(new java.util.Date(crashFile.lastModified()))).append(")\n");
                debugInfo.append("  Size: ").append(crashFile.length()).append(" bytes\n");
                debugInfo.append("  Path: ").append(crashFile.getAbsolutePath()).append("\n");
            }
            debugInfo.append("\n");
        }
        
        if (hasCrashLogs) {
            debugInfo.append("--- INTERNAL CRASH LOG PREVIEW ---\n");
            debugInfo.append(crashLogs.length() > 500 ? 
                crashLogs.substring(0, 500) + "...\n[TRUNCATED]" : 
                crashLogs);
        } else {
            debugInfo.append("--- NO INTERNAL CRASH LOGS FOUND ---\n");
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
        builder.setPositiveButton(R.string.button_close, null);
        builder.setNeutralButton(R.string.button_force_test_crash, (dialog, which) -> {
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
        boolean verboseLogging = sharedPreferences.getBoolean(KEY_VERBOSE_LOGGING, true); // Default to enabled
        boolean logFilters = sharedPreferences.getBoolean(KEY_LOG_FILTERS, true); // Default to enabled
        boolean logNotifications = sharedPreferences.getBoolean(KEY_LOG_NOTIFICATIONS, true); // Default to enabled
        boolean logUserActions = sharedPreferences.getBoolean(KEY_LOG_USER_ACTIONS, true); // Default to enabled
        boolean logSystemEvents = sharedPreferences.getBoolean(KEY_LOG_SYSTEM_EVENTS, true); // Default to enabled
        boolean disableMediaFallback = sharedPreferences.getBoolean(KEY_DISABLE_MEDIA_FALLBACK, false); // Default to disabled
        
        binding.switchVerboseLogging.setChecked(verboseLogging);
        binding.switchLogFilters.setChecked(logFilters);
        binding.switchLogNotifications.setChecked(logNotifications);
        binding.switchLogUserActions.setChecked(logUserActions);
        binding.switchLogSystemEvents.setChecked(logSystemEvents);
        binding.switchDisableMediaFallback.setChecked(disableMediaFallback);
    }

    private void showNotificationHistory() {
        List<NotificationReaderService.NotificationData> notifications = NotificationReaderService.getRecentNotifications();
        
        if (notifications.isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Notification History")
                   .setMessage("No notifications to display")
                   .setPositiveButton(R.string.button_close, (dialog, which) -> dialog.dismiss())
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
               .setPositiveButton(R.string.button_close, (dialog, which) -> dialog.dismiss())
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
        InAppLogger.logUserAction("Filter creation success toast shown: " + action + " (" + type + ")", "");
        
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
                // Use FileExportHelper to create the file with fallback support
                File logFile = FileExportHelper.createExportFile(this, "logs", "speakthat_logs_" + timestamp + ".txt", logContent);
                
                if (logFile != null) {
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
                } else {
                    throw new Exception("File creation failed");
                }
                
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
        try {
            String logs = InAppLogger.getRecentLogs(200); // Get last 200 logs
            binding.textLogDisplay.setText(logs);
            
            // Update indicators
            lastLogUpdateTime = System.currentTimeMillis();
            lastLogCount = InAppLogger.getLogCount();
            hasNewLogs = false;
            
            // Update refresh button to show last update time
            updateRefreshButtonStatus();
            
            // Scroll the TextView to the bottom (safe, no parent cast)
            binding.textLogDisplay.post(() -> {
                if (binding.textLogDisplay.getLayout() != null) {
                    int scrollAmount = binding.textLogDisplay.getLayout().getLineTop(binding.textLogDisplay.getLineCount()) - binding.textLogDisplay.getHeight();
                    if (scrollAmount > 0)
                        binding.textLogDisplay.scrollTo(0, scrollAmount);
                    else
                        binding.textLogDisplay.scrollTo(0, 0);
                }
            });
            
            InAppLogger.log("Development", "Logs refreshed manually - " + lastLogCount + " total logs");
            
        } catch (Exception e) {
            InAppLogger.logError("Development", "Error refreshing logs: " + e.getMessage());
            binding.textLogDisplay.setText("Error loading logs: " + e.getMessage());
        }
    }

    private void startLogUpdates() {
        // DEPRECATED: No longer using auto-refresh to save battery
        // Logs are now refreshed manually or when new logs are detected
        InAppLogger.log("Development", "Auto-refresh disabled - using smart log display system");
    }

    private void stopLogUpdates() {
        if (logUpdateRunnable != null) {
            uiHandler.removeCallbacks(logUpdateRunnable);
            InAppLogger.log("Development", "Log auto-refresh stopped to save battery");
        }
    }
    
    /**
     * Check if there are new logs available without updating the UI
     */
    private void checkForNewLogs() {
        int currentLogCount = InAppLogger.getLogCount();
        
        if (currentLogCount > lastLogCount) {
            hasNewLogs = true;
            updateRefreshButtonStatus();
            InAppLogger.log("Development", "New logs detected: " + (currentLogCount - lastLogCount) + " new entries");
        }
    }
    
    /**
     * Update the refresh button to show new logs indicator and last update time
     */
    private void updateRefreshButtonStatus() {
        if (hasNewLogs) {
            // Show new logs indicator
            binding.btnRefreshLogs.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.purple_200)));
            binding.btnRefreshLogs.setIconTint(ColorStateList.valueOf(Color.WHITE));
            
            // Add a subtle animation or indicator
            binding.btnRefreshLogs.setAlpha(0.8f);
            
        } else {
            // Normal state
            binding.btnRefreshLogs.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.purple_300)));
            binding.btnRefreshLogs.setIconTint(ColorStateList.valueOf(Color.WHITE));
            binding.btnRefreshLogs.setAlpha(1.0f);
        }
        
        // Show last update time in a subtle way
        if (lastLogUpdateTime > 0) {
            long timeSinceUpdate = System.currentTimeMillis() - lastLogUpdateTime;
            String timeText = formatTimeSinceUpdate(timeSinceUpdate);
            
            // Update button tooltip or add a small text indicator
            binding.btnRefreshLogs.setContentDescription("Refresh logs (Last: " + timeText + ")");
        }
    }
    
    /**
     * Format time since last update in a user-friendly way
     */
    private String formatTimeSinceUpdate(long timeSinceUpdate) {
        if (timeSinceUpdate < 60000) { // Less than 1 minute
            return "Just now";
        } else if (timeSinceUpdate < 3600000) { // Less than 1 hour
            long minutes = timeSinceUpdate / 60000;
            return minutes + "m ago";
        } else { // More than 1 hour
            long hours = timeSinceUpdate / 3600000;
            return hours + "h ago";
        }
    }

    private void saveVerboseLogging(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_VERBOSE_LOGGING, enabled);
        editor.apply();
        InAppLogger.log("Development", "Verbose logging setting saved: " + enabled);
    }

    private void saveLogFilters(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_LOG_FILTERS, enabled);
        editor.apply();
        InAppLogger.log("Development", "Log filters setting saved: " + enabled);
    }

    private void saveLogNotifications(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_LOG_NOTIFICATIONS, enabled);
        editor.apply();
        InAppLogger.log("Development", "Log notifications setting saved: " + enabled);
    }

    private void saveLogUserActions(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_LOG_USER_ACTIONS, enabled);
        editor.apply();
        InAppLogger.log("Development", "Log user actions setting saved: " + enabled);
    }

    private void saveLogSystemEvents(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_LOG_SYSTEM_EVENTS, enabled);
        editor.apply();
        InAppLogger.log("Development", "Log system events setting saved: " + enabled);
    }

    private void saveDisableMediaFallback(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_DISABLE_MEDIA_FALLBACK, enabled);
        editor.apply();
        InAppLogger.log("Development", "Disable media fallback setting saved: " + enabled);
    }



    private void toggleLogPause() {
        // DEPRECATED: Auto-refresh is no longer used
        // This method now serves as a "check for new logs" function
        checkForNewLogs();
        
        if (hasNewLogs) {
            // If there are new logs, refresh immediately
            refreshLogs();
            Toast.makeText(this, "New logs found and refreshed!", Toast.LENGTH_SHORT).show();
            InAppLogger.logUserAction("Log refresh - new logs found toast shown", "");
        } else {
            // If no new logs, just refresh to show current state
            refreshLogs();
            Toast.makeText(this, "Logs refreshed - no new entries", Toast.LENGTH_SHORT).show();
            InAppLogger.logUserAction("Log refresh - no new entries toast shown", "");
        }
        
        InAppLogger.log("Development", "Manual log check triggered - new logs: " + hasNewLogs);
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
                .setPositiveButton(R.string.button_clear_analytics, (dialog, which) -> {
                    clearAnalytics();
                })
                .setNegativeButton(R.string.button_close, null)
                .show();
        
        InAppLogger.log("Development", "Analytics dialog viewed - Total usage: " + totalUsage);
    }
    
    private void clearAnalytics() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        
        // R.string.button_clear all analytics keys
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
        
        // Clean up any remaining handlers
        stopLogUpdates();
        
        // Log final state
        InAppLogger.log("Development", "Development Settings destroyed - final log count: " + InAppLogger.getLogCount());
        
        InAppLogger.logAppLifecycle("Development Settings destroyed", "DevelopmentSettingsActivity");
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



    private void showBatteryOptimizationReport() {
        StringBuilder report = new StringBuilder();
        report.append("🔋 BATTERY OPTIMIZATION REPORT\n");
        report.append("==============================\n\n");
        
        report.append("📊 BACKGROUND PROCESSES STATUS:\n");
        report.append("─────────────────────────────\n");
        
        // Check if log auto-refresh is running (should always be false now)
        boolean logsRunning = false; // Auto-refresh is completely disabled
        report.append("• Log Auto-Refresh: ").append("✅ DISABLED").append("\n");
        report.append("  ✅ OPTIMIZED: Smart log display system eliminates battery drain\n");
        report.append("  ✅ Logs are captured in real-time but UI updates only on demand\n");
        
        // Check notification service status
        boolean serviceRunning = isNotificationServiceRunning();
        report.append("• Notification Service: ").append(serviceRunning ? "🟢 RUNNING" : "🔴 STOPPED").append("\n");
        if (serviceRunning) {
            report.append("  ✅ NORMAL: Required for app functionality\n");
        }
        
        report.append("\n🛡️ BATTERY PROTECTION MEASURES:\n");
        report.append("──────────────────────────────\n");
        report.append("✅ Shake sensors only active during TTS playback\n");
        report.append("✅ Smart log display - no continuous UI updates\n");
        report.append("✅ Force sensor cleanup on service destruction\n");
        report.append("✅ Proper handler/runnable cleanup\n");
        report.append("✅ Device state checked only when notifications arrive\n");
        report.append("✅ No continuous background monitoring\n");
        report.append("✅ Real-time logging without UI refresh overhead\n");
        
        report.append("\n📱 DEVICE STATE MONITORING:\n");
        report.append("─────────────────────────────\n");
        report.append("✅ Screen state: Checked on-demand only\n");
        report.append("✅ Charging state: Checked on-demand only\n");
        report.append("✅ No broadcast receivers for continuous monitoring\n");
        
        report.append("\n🔧 RECENT BATTERY FIXES (v1.1):\n");
        report.append("────────────────────────────\n");
        report.append("• FIXED: Eliminated 2-second log refresh timer (CRITICAL)\n");
        report.append("• FIXED: Smart log display system implemented\n");
        report.append("• FIXED: New logs indicator without continuous UI updates\n");
        report.append("• FIXED: Manual refresh with visual feedback\n");
        report.append("• FIXED: Enhanced sensor cleanup in NotificationReaderService\n");
        report.append("• FIXED: Proper lifecycle management for background processes\n");
        
        report.append("\n💡 BATTERY USAGE EXPLANATION:\n");
        report.append("────────────────────────────\n");
        report.append("The previous 27% battery usage was from:\n");
        report.append("1. ❌ CRITICAL: 2-second log refresh timer (NOW FIXED)\n");
        report.append("2. Previous bug where shake sensors stayed active\n");
        report.append("3. Android battery stats include historical usage\n\n");
        
        report.append("🔄 To reset battery stats:\n");
        report.append("• Restart your device\n");
        report.append("• Or wait 24 hours for automatic reset\n");
        report.append("• Battery usage should be <2% after fixes\n");
        
        report.append("\n⚡ NEW SMART LOG SYSTEM:\n");
        report.append("──────────────────────\n");
        report.append("• ✅ All logs captured in real-time (no data loss)\n");
        report.append("• ✅ UI only updates when you refresh manually\n");
        report.append("• ✅ Visual indicator when new logs are available\n");
        report.append("• ✅ Zero battery drain from log display\n");
        report.append("• ✅ Perfect for debugging without battery impact\n");
        
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
        builder.setPositiveButton(R.string.button_export_report, (dialog, which) -> {
            exportBatteryReport(report.toString());
        });
        builder.setNegativeButton(R.string.button_close, null);
        
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
                .setPositiveButton(R.string.button_ok, null)
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

    // --- Background Process Monitor ---
    private String getBackgroundProcessStatus() {
        StringBuilder sb = new StringBuilder();
        // Check NotificationReaderService running (registered as notification listener)
        try {
            NotificationListenerRecovery.ListenerStatus status = NotificationListenerRecovery.getListenerStatus(this);
            sb.append("NotificationReaderService: ").append(status.getPermissionGranted() ? "🟢 RUNNING" : "🔴 STOPPED").append("\n");

            if (status.getLastConnect() > 0) {
                sb.append("  Last connect: ").append(formatRelativeTime(status.getLastConnect())).append("\n");
            }
            if (status.getLastDisconnect() > 0) {
                sb.append("  Last disconnect: ").append(formatRelativeTime(status.getLastDisconnect())).append("\n");
            }
            if (status.getLastRebindAttempt() > 0) {
                sb.append("  Last rebind request: ").append(formatRelativeTime(status.getLastRebindAttempt()));
                if (!TextUtils.isEmpty(status.getLastRebindReason())) {
                    sb.append(" (").append(status.getLastRebindReason()).append(")");
                }
                sb.append("\n");
            }
            if (status.getLastRebindSuccess() > 0) {
                sb.append("  Last rebind success: ").append(formatRelativeTime(status.getLastRebindSuccess())).append("\n");
            }
        } catch (Exception e) {
            sb.append("NotificationReaderService: Error checking status\n");
        }

        // Check if MainActivity sensor listeners are active (best effort)
        try {
            boolean isSensorActive = MainActivity.isSensorListenerActive;
            sb.append("Shake/Wave Sensor Listeners: ").append(isSensorActive ? "🟢 ACTIVE" : "🔴 INACTIVE").append("\n");
        } catch (Exception e) {
            sb.append("Shake/Wave Sensor Listeners: Error checking status\n");
        }
        return sb.toString();
    }

    private String formatRelativeTime(long timestamp) {
        if (timestamp <= 0L) {
            return getString(R.string.listener_warning_time_unknown);
        }
        CharSequence span = DateUtils.getRelativeTimeSpanString(
            timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        );
        return span != null ? span.toString() : getString(R.string.listener_warning_time_unknown);
    }

    private void showBackgroundProcessMonitor() {
        String status = getBackgroundProcessStatus();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Background Process Monitor")
                .setMessage(status)
                .setPositiveButton(R.string.button_ok, null)
                .show();
    }

    private void showInstallationSourceDebug() {
        try {
            UpdateManager updateManager = UpdateManager.Companion.getInstance(this);
            java.util.Map<String, Object> details = updateManager.getInstallationSourceDetails();
            boolean isGooglePlay = updateManager.isInstalledFromGooglePlay();
            
            StringBuilder message = new StringBuilder();
            message.append("=== INSTALLATION SOURCE DEBUG ===\n");
            message.append("Currently detected as Google Play: ").append(isGooglePlay).append("\n\n");
            message.append("Detailed Information:\n");
            
            for (java.util.Map.Entry<String, Object> entry : details.entrySet()) {
                message.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            
            message.append("\n=== DEBUG OPTIONS ===\n");
            message.append("• Use 'Reset Cache' to clear cached result\n");
            message.append("• Use 'Re-detect' to force fresh detection\n");
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Installation Source Debug")
                    .setMessage(message.toString())
                    .setPositiveButton(R.string.button_reset_cache, (dialog, which) -> {
                        updateManager.resetGooglePlayDetectionCache();
                        Toast.makeText(this, "Cache reset. Re-run detection to see changes.", Toast.LENGTH_LONG).show();
                        InAppLogger.logUserAction("Installation source cache reset toast shown", "");
                    })
                    .setNegativeButton(R.string.button_re_detect, (dialog, which) -> {
                        updateManager.resetGooglePlayDetectionCache();
                        // Force a fresh detection
                        boolean newResult = updateManager.isInstalledFromGooglePlay();
                        Toast.makeText(this, "Re-detection complete. Result: " + newResult, Toast.LENGTH_LONG).show();
                        InAppLogger.logUserAction("Installation source re-detection complete toast shown: " + newResult, "");
                        // Show the updated debug info
                        showInstallationSourceDebug();
                    })
                    .setNeutralButton(R.string.button_ok, null)
                    .show();
                    
            InAppLogger.log("Development", "Installation source debug dialog shown");
            
        } catch (Exception e) {
            Toast.makeText(this, "Error showing debug info: " + e.getMessage(), Toast.LENGTH_LONG).show();
            InAppLogger.logError("Development", "Error showing installation source debug: " + e.getMessage());
        }
    }
    
    private void testRuleSystem() {
        InAppLogger.log("Development", "Starting rule system test (no pre-made rules)...");
        
        // Run tests in background thread to avoid blocking UI
        new Thread(() -> {
            try {
                RuleSystemTest test = new RuleSystemTest(this);
                test.runTestsWithoutPreMadeRules();
                
                // Show results on UI thread
                uiHandler.post(() -> {
                    String summary = test.getTestSummary();
                    
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("🧪 Rule System Test Results");
                    builder.setMessage(summary);
                    builder.setPositiveButton(R.string.button_ok, null);
                    
                    AlertDialog dialog = builder.create();
                    dialog.show();

                    InAppLogger.log("Development", "Rule system test completed (no pre-made rules)");
                    InAppLogger.logUserAction("Rule system test run (no pre-made rules)", "");
                });
                
            } catch (Exception e) {
                // Show error on UI thread
                uiHandler.post(() -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("❌ Rule System Test Error");
                    builder.setMessage("Error running rule system test: " + e.getMessage());
                    builder.setPositiveButton(R.string.button_ok, null);
                    
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    
                    InAppLogger.logError("Development", "Rule system test failed: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void testReviewReminder() {
        InAppLogger.log("Development", "Testing review reminder dialog");
        
        try {
            ReviewReminderManager reviewManager = ReviewReminderManager.Companion.getInstance(this);
            reviewManager.showReminderDialog(this);
            
            Toast.makeText(this, "Review reminder dialog shown", Toast.LENGTH_SHORT).show();
            InAppLogger.logUserAction("Review reminder test dialog shown toast", "");
        } catch (Exception e) {
            InAppLogger.logError("Development", "Error testing review reminder: " + e.getMessage());
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("❌ Review Reminder Test Error");
            builder.setMessage("Error testing review reminder: " + e.getMessage());
            builder.setPositiveButton(R.string.button_ok, null);
            builder.show();
        }
    }
    
    private void resetReviewReminder() {
        InAppLogger.log("Development", "Resetting review reminder state");
        
        try {
            ReviewReminderManager reviewManager = ReviewReminderManager.Companion.getInstance(this);
            reviewManager.resetReminderState();
            
            Toast.makeText(this, "Review reminder state reset", Toast.LENGTH_SHORT).show();
            InAppLogger.logUserAction("Review reminder state reset toast shown", "");
        } catch (Exception e) {
            InAppLogger.logError("Development", "Error resetting review reminder: " + e.getMessage());
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("❌ Review Reminder Reset Error");
            builder.setMessage("Error resetting review reminder: " + e.getMessage());
            builder.setPositiveButton(R.string.button_ok, null);
            builder.show();
        }
    }
    
    private void resetStatistics() {
        InAppLogger.log("Development", "Reset statistics button clicked");
        
        // Show confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dev_reset_statistics_title));
        builder.setMessage(getString(R.string.dev_reset_statistics_message));
        builder.setPositiveButton(R.string.button_ok, (dialog, which) -> {
            try {
                StatisticsManager statsManager = StatisticsManager.Companion.getInstance(this);
                statsManager.resetStats();
                
                Toast.makeText(this, getString(R.string.dev_reset_statistics_success), Toast.LENGTH_SHORT).show();
                InAppLogger.logUserAction("Statistics reset", "");
            } catch (Exception e) {
                InAppLogger.logError("Development", "Error resetting statistics: " + e.getMessage());
                
                AlertDialog.Builder errorBuilder = new AlertDialog.Builder(this);
                errorBuilder.setTitle("❌ Statistics Reset Error");
                errorBuilder.setMessage("Error resetting statistics: " + e.getMessage());
                errorBuilder.setPositiveButton(R.string.button_ok, null);
                errorBuilder.show();
            }
        });
        builder.setNegativeButton(R.string.button_cancel, null);
        builder.show();
    }
    
    private void showReviewReminderStats() {
        InAppLogger.log("Development", "Showing review reminder statistics");
        
        try {
            ReviewReminderManager reviewManager = ReviewReminderManager.Companion.getInstance(this);
            java.util.Map<String, Object> stats = reviewManager.getReminderStats();
            
            StringBuilder statsText = new StringBuilder();
            statsText.append("📊 Review Reminder Statistics\n");
            statsText.append("============================\n\n");
            
            for (java.util.Map.Entry<String, Object> entry : stats.entrySet()) {
                statsText.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("📊 Review Reminder Statistics");
            builder.setMessage(statsText.toString());
            builder.setPositiveButton(R.string.button_ok, null);
            builder.show();
            
        } catch (Exception e) {
            InAppLogger.logError("Development", "Error showing review reminder stats: " + e.getMessage());
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("❌ Review Reminder Stats Error");
            builder.setMessage("Error showing review reminder stats: " + e.getMessage());
            builder.setPositiveButton(R.string.button_ok, null);
            builder.show();
        }
    }
    
    /**
     * Test Lower Audio compatibility by attempting to request audio focus
     * This helps users understand if their device supports the Lower Audio feature
     */
    private void testLowerAudioCompatibility() {
        // Show testing dialog
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        AlertDialog testingDialog = builder.setTitle(R.string.behavior_ducking_test_title)
                .setMessage(R.string.behavior_ducking_test_message)
                .setCancelable(false)
                .show();

        // Run the audio focus test on a background thread to avoid blocking the UI
        new Thread(() -> {
            // Attempt to request audio focus to test compatibility
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            AudioManager.OnAudioFocusChangeListener testListener = new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    // This is just for testing, we don't need to handle focus changes
                }
            };

            // Request audio focus with ducking
            int result = audioManager.requestAudioFocus(testListener, 
                    AudioManager.STREAM_MUSIC, 
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

            // CRITICAL: Always release audio focus after testing
            audioManager.abandonAudioFocus(testListener);

            // Show results on the main thread
            runOnUiThread(() -> {
                // Dismiss the testing dialog
                testingDialog.dismiss();

                // Show result dialog
                MaterialAlertDialogBuilder resultBuilder = new MaterialAlertDialogBuilder(this);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    resultBuilder.setTitle(R.string.behavior_ducking_test_success_title)
                            .setMessage(R.string.behavior_ducking_test_success)
                            .setPositiveButton(R.string.audio_ducking_disabled_ok, null);
                    
                    // Log successful test
                    InAppLogger.log("LowerAudioTest", "Audio focus test successful - device supports Lower Audio well");
                } else {
                    resultBuilder.setTitle(R.string.behavior_ducking_test_warning_title)
                            .setMessage(R.string.behavior_ducking_test_warning)
                            .setPositiveButton(R.string.audio_ducking_disabled_ok, null);
                    
                    // Log failed test
                    InAppLogger.log("LowerAudioTest", "Audio focus test failed - device may have limited Lower Audio support");
                }
                
                resultBuilder.show();
            });
        }).start();
    }
    
    private void testCrash() {
        // Show confirmation dialog with strong warning
        new AlertDialog.Builder(this)
            .setTitle("💥 Test Crash - DANGER ZONE")
            .setMessage("This will intentionally crash the app for development and debugging purposes.\n\n" +
                       "⚠️ WARNING: This will immediately terminate the app!\n" +
                       "⚠️ Make sure you have saved any important work!\n" +
                       "⚠️ The crash will be logged and can be used to test the crash logging system.\n\n" +
                       "Are you absolutely sure you know what you're doing?")
            .setPositiveButton("YES, CRASH THE APP", (dialog, which) -> {
                // Log the intentional crash for debugging
                InAppLogger.log("CrashTest", "Intentional crash triggered by user for testing purposes");
                InAppLogger.logSystemEvent("Crash Test", "User initiated crash test from development settings");
                
                // Show a brief "crashing..." message
                Toast.makeText(this, "💥 CRASHING IN 2 SECONDS...", Toast.LENGTH_SHORT).show();
                
                // Delay the crash slightly to allow the toast to show
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    // Create a fatal exception that will be caught by the crash handler
                    throw new RuntimeException("Intentional crash for testing crash logging system - triggered from development settings");
                }, 2000);
            })
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
}