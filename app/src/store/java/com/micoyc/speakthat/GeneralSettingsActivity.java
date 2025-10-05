package com.micoyc.speakthat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.micoyc.speakthat.databinding.ActivityGeneralSettingsBinding;
import org.json.JSONException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GeneralSettingsActivity extends AppCompatActivity {
    private ActivityGeneralSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    
    // Activity result launchers for file operations
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<Intent> fileSaverLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize SharedPreferences FIRST
        sharedPreferences = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE);

        // Apply saved theme after SharedPreferences is initialized
        applySavedTheme();

        binding = ActivityGeneralSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set the activity title
        getSupportActionBar().setTitle(getString(R.string.title_general_settings));

        // Initialize activity result launchers
        initializeActivityResultLaunchers();

        setupThemeSettings();
        setupPerformanceSettings();
        setupAccessibilityPermission();
        setupDataManagement();
        setupThemeIcon();
    }

    private void applySavedTheme() {
        boolean isDarkMode = sharedPreferences.getBoolean("dark_mode", false);
        
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
                    performExport();
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

    private void setupThemeSettings() {
        // Dark Mode Toggle
        MaterialSwitch darkModeSwitch = binding.switchDarkMode;
        boolean isDarkMode = sharedPreferences.getBoolean(getString(R.string.prefs_dark_mode), false);
        darkModeSwitch.setChecked(isDarkMode);

        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(getString(R.string.prefs_dark_mode), isChecked).apply();
            
            // Apply theme immediately
            if (isChecked) {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            }
            
            // Update theme icon
            setupThemeIcon();
            
            // Show restart dialog
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.theme_changed_title))
                .setMessage(getString(R.string.theme_changed_message))
                .setPositiveButton(getString(R.string.restart_now), (dialog, which) -> {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(getString(R.string.later), null)
                .show();
        });
    }

    private void setupThemeIcon() {
        // Set the appropriate icon for Theme section based on current theme
        boolean isDarkMode = (getResources().getConfiguration().uiMode & 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES;
        
        int iconRes = isDarkMode ? R.drawable.ic_light_mode_24 : R.drawable.ic_dark_mode_24;
        binding.iconTheme.setImageResource(iconRes);
    }

    private void setupPerformanceSettings() {
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

        // Auto-Start Toggle
        MaterialSwitch autoStartSwitch = binding.switchAutoStart;
        boolean autoStartEnabled = sharedPreferences.getBoolean(getString(R.string.prefs_auto_start_enabled), true);
        autoStartSwitch.setChecked(autoStartEnabled);

        autoStartSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(getString(R.string.prefs_auto_start_enabled), isChecked).apply();
        });

        // Battery Optimization Toggle
        MaterialSwitch batteryOptimizationSwitch = binding.switchBatteryOptimization;
        boolean batteryOptimizationEnabled = sharedPreferences.getBoolean(getString(R.string.prefs_battery_optimization_enabled), false);
        batteryOptimizationSwitch.setChecked(batteryOptimizationEnabled);

        batteryOptimizationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(getString(R.string.prefs_battery_optimization_enabled), isChecked).apply();
        });

        // Aggressive Processing Toggle
        MaterialSwitch aggressiveProcessingSwitch = binding.switchAggressiveProcessing;
        boolean aggressiveProcessingEnabled = sharedPreferences.getBoolean(getString(R.string.prefs_aggressive_processing_enabled), false);
        aggressiveProcessingSwitch.setChecked(aggressiveProcessingEnabled);

        aggressiveProcessingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(getString(R.string.prefs_aggressive_processing_enabled), isChecked).apply();
        });

        // Service Restart Policy
        String restartPolicy = sharedPreferences.getString(getString(R.string.prefs_restart_policy), getString(R.string.restart_policy_never));
        switch (restartPolicy) {
            case "never":
                binding.radioRestartNever.setChecked(true);
                break;
            case "crash":
                binding.radioRestartOnCrash.setChecked(true);
                break;
            case "periodic":
                binding.radioRestartPeriodic.setChecked(true);
                break;
        }

        binding.radioGroupRestartPolicy.setOnCheckedChangeListener((group, checkedId) -> {
            String policy;
            if (checkedId == R.id.radioRestartNever) {
                policy = getString(R.string.restart_policy_never);
            } else if (checkedId == R.id.radioRestartOnCrash) {
                policy = getString(R.string.restart_policy_crash);
            } else if (checkedId == R.id.radioRestartPeriodic) {
                policy = getString(R.string.restart_policy_periodic);
            } else {
                policy = getString(R.string.restart_policy_never);
            }
            sharedPreferences.edit().putString(getString(R.string.prefs_restart_policy), policy).apply();
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

    private boolean checkStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
               == PackageManager.PERMISSION_GRANTED;
    }

    private void performExport() {
        try {
            // Generate export data
            String exportData = FilterConfigManager.exportFullConfiguration(this);
            
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
            String exportData = FilterConfigManager.exportFullConfiguration(this);
            
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
            FilterConfigManager.ImportResult result = FilterConfigManager.importFullConfiguration(this, content.toString());
            
            if (result.success) {
                Toast.makeText(this, String.format(getString(R.string.configuration_imported_successfully), result.message), Toast.LENGTH_LONG).show();
                // Refresh the activity to show updated settings
                recreate();
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



    private void performExportWithPermissionCheck() {
        // For Android 11+ (API 30+), we don't need WRITE_EXTERNAL_STORAGE for app-specific files
        // The ACTION_CREATE_DOCUMENT intent will handle the file creation
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ - no permission needed for document creation
            performExport();
        } else {
            // Android 10 and below - check for storage permission
            if (checkStoragePermission()) {
                performExport();
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
    }
} 