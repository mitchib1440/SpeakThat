/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.micoyc.speakthat.databinding.ActivityGeneralSettingsBinding;
import com.micoyc.speakthat.permissions.PermissionCatalog;
import com.micoyc.speakthat.permissions.PermissionSyncManager;
import com.micoyc.speakthat.permissions.PermissionSyncSession;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.micoyc.speakthat.rules.Rule;
import com.micoyc.speakthat.rules.RuleConfigManager;
import com.micoyc.speakthat.rules.RuleConfigManager.RulePermissionType;

public class GeneralSettingsActivity extends AppCompatActivity {
    private ActivityGeneralSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    private boolean isUpdatingBatteryOptimizationSwitch = false;
    
    // Activity result launchers for file operations
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<Intent> fileSaverLauncher;
    private List<Rule> pendingRulesImport = null;
    private boolean includeRulesInExport = false;

    private PermissionSyncSession permissionSyncSession = null;

    private static final int REQUEST_RULES_IMPORT_PERMISSIONS = 3001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize SharedPreferences FIRST
        sharedPreferences = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE);

        // Apply saved theme after SharedPreferences is initialized
        applySavedTheme();

        binding = ActivityGeneralSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set title and enable back navigation in app bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.title_general_settings));
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize activity result launchers
        initializeActivityResultLaunchers();

        setupPerformanceSettings();
        setupToastNotifications();
        setupAccessibilityPermission();
        setupDataManagement();
    }

    private void applySavedTheme() {
        boolean isDarkMode = sharedPreferences.getBoolean("dark_mode", true); // Default to dark mode
        
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void initializeActivityResultLaunchers() {
        // Permission launcher
        requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    promptExportIncludeRules();
                } else {
                    Toast.makeText(this, getString(R.string.storage_permission_required), Toast.LENGTH_LONG).show();
                }
            }
        );
        
        // File picker launcher for import
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        importFromUri(uri);
                    }
                }
            }
        );
        
        // File saver launcher for export
        fileSaverLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        exportToUri(uri);
                    }
                }
            }
        );
    }


    private void setupPerformanceSettings() {
        ServiceRestartPolicy.migrateIfNeeded(sharedPreferences);

        // Persistent Notification Toggle
        MaterialSwitch persistentNotificationSwitch = binding.switchPersistentNotification;
        boolean persistentNotificationEnabled = sharedPreferences.getBoolean(getString(R.string.prefs_persistent_notification), false);
        persistentNotificationSwitch.setChecked(persistentNotificationEnabled);

        persistentNotificationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // User is enabling persistent notification - check permission first
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        // Request permission
                        requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
                        // Don't save the setting yet - wait for permission result
                        buttonView.setChecked(false);
                        return;
                    }
                }
            }
            sharedPreferences.edit().putBoolean(getString(R.string.prefs_persistent_notification), isChecked).apply();
        });

        // Notification While Reading Toggle
        MaterialSwitch notificationWhileReadingSwitch = binding.switchNotificationWhileReading;
        boolean notificationWhileReadingEnabled = sharedPreferences.getBoolean(getString(R.string.prefs_notification_while_reading), false);
        notificationWhileReadingSwitch.setChecked(notificationWhileReadingEnabled);

        notificationWhileReadingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // User is enabling reading notification - check permission first
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        // Request permission
                        requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1002);
                        // Don't save the setting yet - wait for permission result
                        buttonView.setChecked(false);
                        return;
                    }
                }
            }
            sharedPreferences.edit().putBoolean(getString(R.string.prefs_notification_while_reading), isChecked).apply();
        });

        // Main Screen History Toggle
        MaterialSwitch mainScreenHistorySwitch = binding.switchMainScreenHistory;
        boolean mainScreenHistoryEnabled = sharedPreferences.getBoolean("show_history_on_main", true);
        mainScreenHistorySwitch.setChecked(mainScreenHistoryEnabled);

        // Show System Blocks in History Toggle
        MaterialSwitch showSystemBlocksSwitch = binding.switchShowSystemBlocks;
        boolean showSystemBlocksEnabled = sharedPreferences.getBoolean("show_system_blocks_history", false);
        showSystemBlocksSwitch.setChecked(showSystemBlocksEnabled);

        // Apply dependent UI state on load
        showSystemBlocksSwitch.setEnabled(mainScreenHistoryEnabled);
        showSystemBlocksSwitch.setAlpha(mainScreenHistoryEnabled ? 1.0f : 0.5f);

        mainScreenHistorySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean("show_history_on_main", isChecked).apply();
            showSystemBlocksSwitch.setEnabled(isChecked);
            showSystemBlocksSwitch.setAlpha(isChecked ? 1.0f : 0.5f);
        });

        showSystemBlocksSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean("show_system_blocks_history", isChecked).apply();
        });

        // Auto-Start Toggle
        MaterialSwitch autoStartSwitch = binding.switchAutoStart;
        boolean autoStartEnabled = sharedPreferences.getBoolean(getString(R.string.prefs_auto_start_enabled), true);
        autoStartSwitch.setChecked(autoStartEnabled);

        autoStartSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(getString(R.string.prefs_auto_start_enabled), isChecked).apply();
        });

        // Battery Optimization Toggle
        MaterialSwitch batteryOptimizationSwitch = binding.switchBatteryOptimization;
        syncBatteryOptimizationState();

        batteryOptimizationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isUpdatingBatteryOptimizationSwitch) {
                return;
            }
            if (isChecked) {
                requestBatteryOptimizationExemption();
            } else {
                sharedPreferences.edit().putBoolean(getString(R.string.prefs_battery_optimization_enabled), false).apply();
            }
        });


        // Service Restart Policy (stored values are always English: never | crash | periodic)
        String restartPolicy = ServiceRestartPolicy.readPolicy(sharedPreferences);
        switch (restartPolicy) {
            case ServiceRestartPolicy.VALUE_NEVER:
                binding.radioRestartNever.setChecked(true);
                break;
            case ServiceRestartPolicy.VALUE_CRASH:
                binding.radioRestartOnCrash.setChecked(true);
                break;
            case ServiceRestartPolicy.VALUE_PERIODIC:
                binding.radioRestartPeriodic.setChecked(true);
                break;
            default:
                binding.radioRestartOnCrash.setChecked(true);
                break;
        }
        ServiceRestartPolicyScheduler.syncPeriodicWork(this, restartPolicy);

        binding.radioGroupRestartPolicy.setOnCheckedChangeListener((group, checkedId) -> {
            String policy;
            if (checkedId == R.id.radioRestartNever) {
                policy = ServiceRestartPolicy.VALUE_NEVER;
            } else if (checkedId == R.id.radioRestartOnCrash) {
                policy = ServiceRestartPolicy.VALUE_CRASH;
            } else if (checkedId == R.id.radioRestartPeriodic) {
                policy = ServiceRestartPolicy.VALUE_PERIODIC;
            } else {
                policy = ServiceRestartPolicy.DEFAULT_POLICY;
            }
            sharedPreferences.edit().putString(ServiceRestartPolicy.PREFS_KEY, policy).apply();
            ServiceRestartPolicyScheduler.syncPeriodicWork(GeneralSettingsActivity.this, policy);
        });
    }

    private void setupToastNotifications() {
        // Main App Toggle Toast
        MaterialSwitch toastMainAppSwitch = binding.switchToastMainApp;
        boolean toastMainAppEnabled = sharedPreferences.getBoolean(com.micoyc.speakthat.MasterSwitchController.KEY_TOAST_MAIN_APP, true);
        toastMainAppSwitch.setChecked(toastMainAppEnabled);

        toastMainAppSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(com.micoyc.speakthat.MasterSwitchController.KEY_TOAST_MAIN_APP, isChecked).apply();
        });

        // Quick Settings Tile Toast
        MaterialSwitch toastQuickSettingsSwitch = binding.switchToastQuickSettings;
        boolean toastQuickSettingsEnabled = sharedPreferences.getBoolean(com.micoyc.speakthat.MasterSwitchController.KEY_TOAST_QUICK_SETTINGS, true);
        toastQuickSettingsSwitch.setChecked(toastQuickSettingsEnabled);

        toastQuickSettingsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(com.micoyc.speakthat.MasterSwitchController.KEY_TOAST_QUICK_SETTINGS, isChecked).apply();
        });

        // Automation Intents Toast
        MaterialSwitch toastAutomationSwitch = binding.switchToastAutomation;
        boolean toastAutomationEnabled = sharedPreferences.getBoolean(com.micoyc.speakthat.MasterSwitchController.KEY_TOAST_AUTOMATION, true);
        toastAutomationSwitch.setChecked(toastAutomationEnabled);

        toastAutomationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(com.micoyc.speakthat.MasterSwitchController.KEY_TOAST_AUTOMATION, isChecked).apply();
        });
    }

    private void setupDataManagement() {
        // Export Configuration
        binding.exportConfigButton.setOnClickListener(v -> {
            performExportWithPermissionCheck();
        });

        // Import Configuration
        binding.importConfigButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(getString(R.string.export_mime_type));
            filePickerLauncher.launch(intent);
        });

        // Clear All Data
        binding.clearDataButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.clear_all_data_title))
                .setMessage(getString(R.string.clear_all_data_message))
                .setPositiveButton(getString(R.string.clear_all_data_button), (dialog, which) -> {
                    clearAllData();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
        });
    }

    /**
     * Set up the accessibility permission button
     * 
     * This method configures the accessibility permission button in the General Settings.
     * When clicked, it checks if the accessibility service is enabled and either shows
     * a success message or guides the user to enable the permission.
     */
    private void setupAccessibilityPermission() {
        // Accessibility Permission Button
        // Using findViewById instead of binding due to binding generation issue
        android.view.View accessibilityButton = findViewById(R.id.buttonAccessibilityPermission);

        if (accessibilityButton != null) {
            accessibilityButton.setOnClickListener(v -> {
                // Check if accessibility service is already enabled
                boolean isEnabled = isAccessibilityServiceEnabled();

                if (isEnabled) {
                    // Already enabled - show success message
                    Toast.makeText(this, getString(R.string.accessibility_permission_granted), Toast.LENGTH_SHORT).show();
                } else {
                    // Not enabled - show explanation and guide user to settings
                    showAccessibilityPermissionDialog();
                }
            });
        }
    }
    
    /**
     * Check if the accessibility service is enabled
     * 
     * This method checks if the SpeakThatAccessibilityService is enabled in the
     * Android accessibility settings. It's similar to how notification listener
     * permission is checked.
     * 
     * @return true if the accessibility service is enabled, false otherwise
     */
    private boolean isAccessibilityServiceEnabled() {
        String packageName = getPackageName();
        String serviceName = packageName + "/com.micoyc.speakthat.SpeakThatAccessibilityService";

        String enabledServices = android.provider.Settings.Secure.getString(getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        if (enabledServices != null && !enabledServices.isEmpty()) {
            String[] services = enabledServices.split(":");
            for (String service : services) {
                if (service.equals(serviceName)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Show dialog explaining accessibility permission and guide user to enable it
     */
    private void showAccessibilityPermissionDialog() {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.accessibility_permission_explanation_title))
            .setMessage(getString(R.string.accessibility_permission_explanation_message))
            .setPositiveButton(getString(R.string.accessibility_permission_open_settings), (dialog, which) -> {
                // Try to open the specific accessibility service settings first
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                    Toast.makeText(this, "Please find 'SpeakThat Accessibility' in the list and enable it", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    // Fallback to general accessibility settings
                    Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                    Toast.makeText(this, getString(R.string.accessibility_permission_request), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton(getString(R.string.accessibility_permission_cancel), null)
            .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncBatteryOptimizationState();
        if (permissionSyncSession != null) {
            permissionSyncSession.onResume();
            if (permissionSyncSession.isFinished()) {
                permissionSyncSession = null;
            }
        }
    }

    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
               == PackageManager.PERMISSION_GRANTED;
    }

    private void performExport() {
        try {
            // Generate export data
            String exportData = FilterConfigManager.exportFullConfiguration(this, includeRulesInExport);
            
            // Create filename with timestamp
            String timestamp = new SimpleDateFormat(getString(R.string.date_format_export), Locale.getDefault()).format(new Date());
            String filename = String.format(getString(R.string.export_filename_format), timestamp);
            
            // Launch file saver
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(getString(R.string.export_mime_type));
            intent.putExtra(Intent.EXTRA_TITLE, filename);
            fileSaverLauncher.launch(intent);
            
        } catch (JSONException e) {
            Toast.makeText(this, String.format(getString(R.string.export_failed), e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void exportToUri(Uri uri) {
        try {
            // Generate export data
            String exportData = FilterConfigManager.exportFullConfiguration(this, includeRulesInExport);
            
            // Write to file using ContentResolver
            try (java.io.OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                            if (outputStream == null) {
                throw new IOException(getString(R.string.error_unable_to_open_output_stream));
            }
            outputStream.write(exportData.getBytes(getString(R.string.export_charset)));
            }
            
            Toast.makeText(this, getString(R.string.configuration_exported_successfully), Toast.LENGTH_LONG).show();
            
        } catch (Exception e) {
            Toast.makeText(this, String.format(getString(R.string.export_failed), e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void importFromUri(Uri uri) {
        try {
            // Read file content
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(getFileFromUri(uri)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            
            // Import configuration
            String importedJson = content.toString();
            FilterConfigManager.ImportResult result = FilterConfigManager.importFullConfiguration(this, importedJson);
            
            if (result.success) {
                Toast.makeText(this, String.format(getString(R.string.configuration_imported_successfully), result.message), Toast.LENGTH_LONG).show();
                permissionSyncSession = PermissionSyncManager.startSync(
                    this,
                    true,
                    null,
                    () -> {
                        permissionSyncSession = null;
                        handleOptionalRulesImport(importedJson);
                    }
                );
            } else {
                Toast.makeText(this, String.format(getString(R.string.import_failed), result.message), Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Toast.makeText(this, String.format(getString(R.string.import_failed), e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private File getFileFromUri(Uri uri) throws IOException {
        // Use ContentResolver to get input stream from URI
        try (java.io.InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException(getString(R.string.error_unable_to_open_input_stream));
            }
            
            // Create a temporary file
            File tempFile = File.createTempFile(getString(R.string.import_temp_filename), ".json", getCacheDir());
            
            // Copy content to temp file
            try (java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            
            return tempFile;
        }
    }

    private void clearAllData() {
        try {
            // Clear main preferences
            SharedPreferences.Editor mainEditor = getSharedPreferences(getString(R.string.prefs_speakthat), MODE_PRIVATE).edit();
            mainEditor.clear();
            mainEditor.apply();
            
            // Clear voice settings
            SharedPreferences.Editor voiceEditor = getSharedPreferences(getString(R.string.prefs_voice_settings), MODE_PRIVATE).edit();
            voiceEditor.clear();
            voiceEditor.apply();
            
            // Clear notification history
            SharedPreferences.Editor historyEditor = getSharedPreferences(getString(R.string.prefs_notification_history), MODE_PRIVATE).edit();
            historyEditor.clear();
            historyEditor.apply();
            
            Toast.makeText(this, getString(R.string.all_data_cleared_successfully), Toast.LENGTH_LONG).show();
            
            // Refresh the activity to show default settings
            recreate();
            
        } catch (Exception e) {
            Toast.makeText(this, String.format(getString(R.string.failed_to_clear_data), e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void syncBatteryOptimizationState() {
        boolean exempt = isBatteryOptimizationExempt();
        updateBatteryOptimizationSwitch(exempt);
        sharedPreferences.edit().putBoolean(getString(R.string.prefs_battery_optimization_enabled), exempt).apply();
    }

    private void updateBatteryOptimizationSwitch(boolean isChecked) {
        isUpdatingBatteryOptimizationSwitch = true;
        binding.switchBatteryOptimization.setChecked(isChecked);
        isUpdatingBatteryOptimizationSwitch = false;
    }

    private boolean isBatteryOptimizationExempt() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        return powerManager != null && powerManager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            sharedPreferences.edit().putBoolean(getString(R.string.prefs_battery_optimization_enabled), true).apply();
            updateBatteryOptimizationSwitch(true);
            return;
        }

        if (isBatteryOptimizationExempt()) {
            sharedPreferences.edit().putBoolean(getString(R.string.prefs_battery_optimization_enabled), true).apply();
            updateBatteryOptimizationSwitch(true);
            return;
        }

        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (Exception e) {
            openBatteryOptimizationSettingsFallback();
        }
    }

    private void openBatteryOptimizationSettingsFallback() {
        try {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(intent);
        } catch (Exception ignored) {
            // If this also fails, leave the switch as-is; the user can retry.
        }
    }

    private void performExportWithPermissionCheck() {
        // For Android 11+ (API 30+), we don't need WRITE_EXTERNAL_STORAGE for app-specific files
        // The ACTION_CREATE_DOCUMENT intent will handle the file creation
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ - no permission needed for document creation
            promptExportIncludeRules();
        } else {
            // Android 10 and below - check for storage permission
            if (checkStoragePermission()) {
                promptExportIncludeRules();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == 1001 || requestCode == 1002) { // Notification permission requests
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Permission granted - enable the setting
                if (requestCode == 1001) {
                    // Persistent notification
                    binding.switchPersistentNotification.setChecked(true);
                    sharedPreferences.edit().putBoolean(getString(R.string.prefs_persistent_notification), true).apply();
                    Toast.makeText(this, getString(R.string.persistent_notification_enabled), Toast.LENGTH_SHORT).show();
                } else if (requestCode == 1002) {
                    // Reading notification
                    binding.switchNotificationWhileReading.setChecked(true);
                    sharedPreferences.edit().putBoolean(getString(R.string.prefs_notification_while_reading), true).apply();
                    Toast.makeText(this, getString(R.string.reading_notification_enabled), Toast.LENGTH_SHORT).show();
                }
            } else {
                // Permission denied
                Toast.makeText(this, getString(R.string.notification_permission_denied), Toast.LENGTH_LONG).show();
            }
        }

        if (permissionSyncSession != null) {
            permissionSyncSession.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (permissionSyncSession.isFinished()) {
                permissionSyncSession = null;
            }
        }
    }

    private void promptExportIncludeRules() {
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.rules_export_choice_title))
            .setMessage(getString(R.string.rules_export_choice_message))
            .setPositiveButton(getString(R.string.rules_export_choice_include), (dialog, which) -> {
                includeRulesInExport = true;
                performExport();
            })
            .setNegativeButton(getString(R.string.rules_export_choice_exclude), (dialog, which) -> {
                includeRulesInExport = false;
                performExport();
            })
            .setNeutralButton(getString(R.string.button_cancel), null)
            .show();
    }

    private void handleOptionalRulesImport(String jsonData) {
        List<Rule> rules = RuleConfigManager.extractRulesFromFullConfig(jsonData);
        if (rules.isEmpty()) {
            recreate();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.rules_import_master_title))
            .setMessage(getString(R.string.rules_import_master_message, rules.size()))
            .setPositiveButton(getString(R.string.rules_import_master_confirm), (dialog, which) -> handleRulesImportWithPermissions(rules))
            .setNegativeButton(getString(R.string.rules_import_master_skip), (dialog, which) -> {
                recreate();
            })
            .show();
    }

    private void handleRulesImportWithPermissions(List<Rule> rules) {
        EnumSet<RulePermissionType> required = RuleConfigManager.getRequiredPermissionTypes(rules);
        permissionSyncSession = PermissionSyncManager.startSync(
            this,
            false,
            rules,
            () -> {
                permissionSyncSession = null;
                boolean allowBluetooth =
                    !required.contains(RulePermissionType.BLUETOOTH) || PermissionCatalog.hasBluetoothPermissions(this);
                boolean allowWifi =
                    !required.contains(RulePermissionType.WIFI) || PermissionCatalog.hasAllWifiPermissions(this);
                List<Rule> filtered = RuleConfigManager.filterRulesByPermissions(rules, allowBluetooth, allowWifi);
                int skippedCount = rules.size() - filtered.size();
                applyImportedRules(filtered, skippedCount);
            }
        );
    }

    private void handleRulesImportPermissionsResult() {
        if (pendingRulesImport == null) {
            return;
        }
        List<Rule> rules = pendingRulesImport;
        pendingRulesImport = null;

        EnumSet<RulePermissionType> required = RuleConfigManager.getRequiredPermissionTypes(rules);
        boolean allowBluetooth = !required.contains(RulePermissionType.BLUETOOTH) || hasBluetoothPermissions();
        boolean allowWifi = !required.contains(RulePermissionType.WIFI) || hasWifiPermissions();
        List<Rule> filtered = RuleConfigManager.filterRulesByPermissions(rules, allowBluetooth, allowWifi);
        int skippedCount = rules.size() - filtered.size();
        applyImportedRules(filtered, skippedCount);
    }

    private void applyImportedRules(List<Rule> rules, int skippedCount) {
        RuleConfigManager.RuleImportResult result = RuleConfigManager.importRules(this, rules, skippedCount);
        if (result.getSuccess()) {
            String message;
            if (skippedCount > 0) {
                message = getString(R.string.rules_import_master_success_with_skips, rules.size(), skippedCount);
            } else {
                message = getString(R.string.rules_import_master_success, rules.size());
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, getString(R.string.rules_import_failed, result.getMessage()), Toast.LENGTH_LONG).show();
        }
        recreate();
    }

    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
    }

    private List<String> getBluetoothPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN);
        } else {
            permissions.add(android.Manifest.permission.BLUETOOTH);
            permissions.add(android.Manifest.permission.BLUETOOTH_ADMIN);
        }
        return permissions;
    }

    private boolean hasWifiPermissions() {
        return com.micoyc.speakthat.utils.BackgroundLocationHelper.INSTANCE.hasAllWifiPermissions(this);
    }

    private List<String> getWifiPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        return permissions;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
} 