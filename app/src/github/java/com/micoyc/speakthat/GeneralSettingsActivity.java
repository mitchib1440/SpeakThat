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
        setupAutoUpdateSettings();
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
                    Toast.makeText(this, "Storage permission required for export", Toast.LENGTH_LONG).show();
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
        boolean isDarkMode = sharedPreferences.getBoolean("dark_mode", false);
        darkModeSwitch.setChecked(isDarkMode);

        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean("dark_mode", isChecked).apply();
            
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
                .setTitle("Theme Changed")
                .setMessage("The theme has been applied. For the best experience, you may want to restart the app.")
                .setPositiveButton("Restart Now", (dialog, which) -> {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Later", null)
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
        boolean persistentNotificationEnabled = sharedPreferences.getBoolean("persistent_notification", false);
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
            sharedPreferences.edit().putBoolean("persistent_notification", isChecked).apply();
        });

        // Notification While Reading Toggle
        MaterialSwitch notificationWhileReadingSwitch = binding.switchNotificationWhileReading;
        boolean notificationWhileReadingEnabled = sharedPreferences.getBoolean("notification_while_reading", false);
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
            sharedPreferences.edit().putBoolean("notification_while_reading", isChecked).apply();
        });

        // Auto-Start Toggle
        MaterialSwitch autoStartSwitch = binding.switchAutoStart;
        boolean autoStartEnabled = sharedPreferences.getBoolean("auto_start_enabled", true);
        autoStartSwitch.setChecked(autoStartEnabled);

        autoStartSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean("auto_start_enabled", isChecked).apply();
        });

        // Battery Optimization Toggle
        MaterialSwitch batteryOptimizationSwitch = binding.switchBatteryOptimization;
        boolean batteryOptimizationEnabled = sharedPreferences.getBoolean("battery_optimization_enabled", false);
        batteryOptimizationSwitch.setChecked(batteryOptimizationEnabled);

        batteryOptimizationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean("battery_optimization_enabled", isChecked).apply();
        });

        // Aggressive Processing Toggle
        MaterialSwitch aggressiveProcessingSwitch = binding.switchAggressiveProcessing;
        boolean aggressiveProcessingEnabled = sharedPreferences.getBoolean("aggressive_processing_enabled", false);
        aggressiveProcessingSwitch.setChecked(aggressiveProcessingEnabled);

        aggressiveProcessingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean("aggressive_processing_enabled", isChecked).apply();
        });

        // Service Restart Policy
        String restartPolicy = sharedPreferences.getString("restart_policy", "never");
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
                policy = "never";
            } else if (checkedId == R.id.radioRestartOnCrash) {
                policy = "crash";
            } else if (checkedId == R.id.radioRestartPeriodic) {
                policy = "periodic";
            } else {
                policy = "never";
            }
            sharedPreferences.edit().putString("restart_policy", policy).apply();
        });
    }

    private void setupAutoUpdateSettings() {
        // Auto-Update Toggle - Default to ON (true)
        MaterialSwitch autoUpdateSwitch = binding.switchAutoUpdate;
        boolean autoUpdateEnabled = sharedPreferences.getBoolean("auto_update_enabled", true);
        autoUpdateSwitch.setChecked(autoUpdateEnabled);

        autoUpdateSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean("auto_update_enabled", isChecked).apply();
        });

        // Update Check Frequency - Default to Weekly
        String updateFrequency = sharedPreferences.getString("update_check_frequency", "weekly");
        switch (updateFrequency) {
            case "daily":
                binding.radioUpdateDaily.setChecked(true);
                break;
            case "weekly":
                binding.radioUpdateWeekly.setChecked(true);
                break;
            case "monthly":
                binding.radioUpdateMonthly.setChecked(true);
                break;
            case "never":
                binding.radioUpdateNever.setChecked(true);
                break;
            default:
                // Default to weekly if no value is set
                binding.radioUpdateWeekly.setChecked(true);
                sharedPreferences.edit().putString("update_check_frequency", "weekly").apply();
                break;
        }

        binding.radioGroupUpdateFrequency.setOnCheckedChangeListener((group, checkedId) -> {
            String frequency;
            if (checkedId == R.id.radioUpdateDaily) {
                frequency = "daily";
            } else if (checkedId == R.id.radioUpdateWeekly) {
                frequency = "weekly";
            } else if (checkedId == R.id.radioUpdateMonthly) {
                frequency = "monthly";
            } else if (checkedId == R.id.radioUpdateNever) {
                frequency = "never";
            } else {
                frequency = "weekly"; // Default fallback
            }
            sharedPreferences.edit().putString("update_check_frequency", frequency).apply();
        });
    }

    private void setupAccessibilityPermission() {
        // Accessibility Permission Button
        // Using findViewById instead of binding due to binding generation issue
        android.view.View accessibilityButton = findViewById(R.id.buttonAccessibilityPermission);
        if (accessibilityButton != null) {
            accessibilityButton.setOnClickListener(v -> {
                // Check if accessibility service is already enabled
                if (isAccessibilityServiceEnabled()) {
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
     * Similar to how notification listener permission is checked
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

    private void setupDataManagement() {
        // Export Configuration
        binding.exportConfigButton.setOnClickListener(v -> {
            performExportWithPermissionCheck();
        });

        // Import Configuration
        binding.importConfigButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            filePickerLauncher.launch(intent);
        });

        // Clear All Data
        binding.clearDataButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Clear All Data")
                .setMessage("This will reset all settings to defaults and clear notification history.\n\nThis action cannot be undone.\n\nDo you want to continue?")
                .setPositiveButton("Clear All Data", (dialog, which) -> {
                    clearAllData();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
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
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());
            String filename = "SpeakThat_Config_" + timestamp + ".json";
            
            // Launch file saver
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, filename);
            fileSaverLauncher.launch(intent);
            
        } catch (JSONException e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void exportToUri(Uri uri) {
        try {
            // Generate export data
            String exportData = FilterConfigManager.exportFullConfiguration(this);
            
            // Write to file using ContentResolver
            try (java.io.OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                if (outputStream == null) {
                    throw new IOException("Unable to open output stream for URI");
                }
                outputStream.write(exportData.getBytes("UTF-8"));
            }
            
            Toast.makeText(this, "Configuration exported successfully", Toast.LENGTH_LONG).show();
            
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, "Configuration imported successfully: " + result.message, Toast.LENGTH_LONG).show();
                // Refresh the activity to show updated settings
                recreate();
            } else {
                Toast.makeText(this, "Import failed: " + result.message, Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private File getFileFromUri(Uri uri) throws IOException {
        // Use ContentResolver to get input stream from URI
        try (java.io.InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("Unable to open input stream from URI");
            }
            
            // Create a temporary file
            File tempFile = File.createTempFile("speakthat_import", ".json", getCacheDir());
            
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
            SharedPreferences.Editor mainEditor = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE).edit();
            mainEditor.clear();
            mainEditor.apply();
            
            // Clear voice settings
            SharedPreferences.Editor voiceEditor = getSharedPreferences("VoiceSettings", MODE_PRIVATE).edit();
            voiceEditor.clear();
            voiceEditor.apply();
            
            // Clear notification history
            SharedPreferences.Editor historyEditor = getSharedPreferences("NotificationHistory", MODE_PRIVATE).edit();
            historyEditor.clear();
            historyEditor.apply();
            
            Toast.makeText(this, "All data cleared successfully", Toast.LENGTH_LONG).show();
            
            // Refresh the activity to show default settings
            recreate();
            
        } catch (Exception e) {
            Toast.makeText(this, "Failed to clear data: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
                    sharedPreferences.edit().putBoolean("persistent_notification", true).apply();
                    Toast.makeText(this, "Persistent notification enabled", Toast.LENGTH_SHORT).show();
                } else if (requestCode == 1002) {
                    // Reading notification
                    binding.switchNotificationWhileReading.setChecked(true);
                    sharedPreferences.edit().putBoolean("notification_while_reading", true).apply();
                    Toast.makeText(this, "Reading notification enabled", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Permission denied
                Toast.makeText(this, "Notification permission denied - feature will not work", Toast.LENGTH_LONG).show();
            }
        }
    }
} 