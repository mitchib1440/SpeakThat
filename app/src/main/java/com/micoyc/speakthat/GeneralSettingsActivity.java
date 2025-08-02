package com.micoyc.speakthat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.micoyc.speakthat.databinding.ActivityGeneralSettingsBinding;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GeneralSettingsActivity extends AppCompatActivity {
    private ActivityGeneralSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    private ActivityResultLauncher<Intent> importFileLauncher;
    private boolean isInitializing = true; // Flag to prevent restart during initialization
    
    // SharedPreferences keys
    private static final String PREFS_NAME = "SpeakThatPrefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_AUTO_START = "auto_start_on_boot";
    private static final String KEY_BATTERY_OPTIMIZATION = "battery_optimization_disabled";
    private static final String KEY_AGGRESSIVE_PROCESSING = "aggressive_background_processing";
    private static final String KEY_SERVICE_RESTART_POLICY = "service_restart_policy"; // "never", "crash", "periodic"
    private static final String KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled";
    private static final String KEY_UPDATE_CHECK_FREQUENCY = "update_check_frequency"; // "daily", "weekly", "monthly", "never"
    
    // Default values
    private static final boolean DEFAULT_AUTO_START = true;
    private static final boolean DEFAULT_BATTERY_OPTIMIZATION = true;
    private static final boolean DEFAULT_AGGRESSIVE_PROCESSING = false;
    private static final String DEFAULT_SERVICE_RESTART_POLICY = "periodic";
    private static final boolean DEFAULT_AUTO_UPDATE_ENABLED = true;
    private static final String DEFAULT_UPDATE_CHECK_FREQUENCY = "weekly";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Apply saved theme before setting content view
        applySavedTheme();
        
        binding = ActivityGeneralSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Configure system UI for proper insets handling
        configureSystemUI();

        // Set up action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.title_general_settings));
        }

        // Initialize file picker for import
        initializeFilePicker();

        // Initialize all settings
        initializeDarkModeSwitch();
        initializeAppBehaviorSettings();
        initializeUpdateSettings();
        initializeDataManagementSettings();
        
        // Mark initialization as complete
        isInitializing = false;
    }

    private void applySavedTheme() {
        boolean isDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false); // Default to light mode
        
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void configureSystemUI() {
        // Simplified system UI configuration to avoid emulator issues
        // The app will work fine without explicit system UI flags
    }

    private void initializeDarkModeSwitch() {
        SwitchMaterial darkModeSwitch = binding.switchDarkMode;
        
        // Set initial state based on saved preference (ensure it matches what was applied in applySavedTheme)
        boolean isDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false); // Default to light mode
        darkModeSwitch.setChecked(isDarkMode);
        
        // Set up switch listener
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Don't restart during initialization
            if (isInitializing) {
                return;
            }
            
            // Only proceed if the value actually changed
            boolean currentDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false);
            if (isChecked == currentDarkMode) {
                return; // No change, don't restart
            }
            
            // Save the preference
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(KEY_DARK_MODE, isChecked);
            editor.apply();
            
            // Apply the theme change without restarting the app
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
            
            // Show a message to the user instead of restarting
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_theme_changed))
                .setMessage(getString(R.string.dialog_message_theme_changed))
                .setPositiveButton(getString(R.string.button_restart_now), (dialog, which) -> {
                    // Only restart if user explicitly chooses to
                    restartApp();
                })
                .setNegativeButton(getString(R.string.button_later), null)
                .show();
            
            InAppLogger.log("GeneralSettings", "Dark mode changed to: " + (isChecked ? "dark" : "light"));
        });
    }

    private void initializeAppBehaviorSettings() {
        // Auto-start on boot
        SwitchMaterial autoStartSwitch = binding.switchAutoStart;
        boolean autoStartEnabled = sharedPreferences.getBoolean(KEY_AUTO_START, DEFAULT_AUTO_START);
        autoStartSwitch.setChecked(autoStartEnabled);
        
        autoStartSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(KEY_AUTO_START, isChecked);
            editor.apply();
            
            InAppLogger.log("GeneralSettings", "Auto-start on boot: " + (isChecked ? "enabled" : "disabled"));
        });

        // Battery optimization exemption
        SwitchMaterial batteryOptimizationSwitch = binding.switchBatteryOptimization;
        boolean batteryOptimizationDisabled = sharedPreferences.getBoolean(KEY_BATTERY_OPTIMIZATION, DEFAULT_BATTERY_OPTIMIZATION);
        batteryOptimizationSwitch.setChecked(batteryOptimizationDisabled);
        
        batteryOptimizationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(KEY_BATTERY_OPTIMIZATION, isChecked);
            editor.apply();
            
            if (isChecked) {
                // Request to ignore battery optimization
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            }
            
            InAppLogger.log("GeneralSettings", "Battery optimization disabled: " + isChecked);
        });

        // Aggressive background processing
        SwitchMaterial aggressiveProcessingSwitch = binding.switchAggressiveProcessing;
        boolean aggressiveProcessingEnabled = sharedPreferences.getBoolean(KEY_AGGRESSIVE_PROCESSING, DEFAULT_AGGRESSIVE_PROCESSING);
        aggressiveProcessingSwitch.setChecked(aggressiveProcessingEnabled);
        
        aggressiveProcessingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(KEY_AGGRESSIVE_PROCESSING, isChecked);
            editor.apply();
            
            InAppLogger.log("GeneralSettings", "Aggressive background processing: " + (isChecked ? "enabled" : "disabled"));
        });

        // Service restart policy
        String restartPolicy = sharedPreferences.getString(KEY_SERVICE_RESTART_POLICY, DEFAULT_SERVICE_RESTART_POLICY);
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
            String newPolicy;
            if (checkedId == R.id.radioRestartNever) {
                newPolicy = "never";
            } else if (checkedId == R.id.radioRestartOnCrash) {
                newPolicy = "crash";
            } else if (checkedId == R.id.radioRestartPeriodic) {
                newPolicy = "periodic";
            } else {
                newPolicy = DEFAULT_SERVICE_RESTART_POLICY;
            }
            
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(KEY_SERVICE_RESTART_POLICY, newPolicy);
            editor.apply();
            
            InAppLogger.log("GeneralSettings", "Service restart policy changed to: " + newPolicy);
        });
    }
    
    private void initializeUpdateSettings() {
        // Auto-update switch
        SwitchMaterial autoUpdateSwitch = binding.switchAutoUpdate;
        boolean autoUpdateEnabled = sharedPreferences.getBoolean(KEY_AUTO_UPDATE_ENABLED, DEFAULT_AUTO_UPDATE_ENABLED);
        autoUpdateSwitch.setChecked(autoUpdateEnabled);
        
        autoUpdateSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(KEY_AUTO_UPDATE_ENABLED, isChecked);
            editor.apply();
            
            InAppLogger.log("GeneralSettings", "Auto-update: " + (isChecked ? "enabled" : "disabled"));
        });
        
        // Update check frequency radio group
        String updateFrequency = sharedPreferences.getString(KEY_UPDATE_CHECK_FREQUENCY, DEFAULT_UPDATE_CHECK_FREQUENCY);
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
        }
        
        binding.radioGroupUpdateFrequency.setOnCheckedChangeListener((group, checkedId) -> {
            String newFrequency;
            if (checkedId == R.id.radioUpdateDaily) {
                newFrequency = "daily";
            } else if (checkedId == R.id.radioUpdateWeekly) {
                newFrequency = "weekly";
            } else if (checkedId == R.id.radioUpdateMonthly) {
                newFrequency = "monthly";
            } else if (checkedId == R.id.radioUpdateNever) {
                newFrequency = "never";
            } else {
                newFrequency = DEFAULT_UPDATE_CHECK_FREQUENCY;
            }
            
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(KEY_UPDATE_CHECK_FREQUENCY, newFrequency);
            editor.apply();
            
            InAppLogger.log("GeneralSettings", "Update check frequency changed to: " + newFrequency);
        });
    }

    private void initializeDataManagementSettings() {
        // Export configuration
        binding.exportConfigButton.setOnClickListener(v -> exportFullConfiguration());
        
        // Import configuration
        binding.importConfigButton.setOnClickListener(v -> importFullConfiguration());
        
        // Clear all data
        binding.clearDataButton.setOnClickListener(v -> showClearDataDialog());
        
        // Test settings
        binding.testSettingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, TestSettingsActivity.class);
            startActivity(intent);
        });
    }

    private void initializeFilePicker() {
        importFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        importFullConfiguration(uri);
                    }
                }
            }
        );
    }

    private void exportFullConfiguration() {
        try {
            // Generate export data using extended FilterConfigManager
            String configData = FilterConfigManager.exportFullConfiguration(this);
            
            // Create filename with timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());
            String filename = "SpeakThat_FullConfig_" + timestamp + ".json";
            
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
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "SpeakThat! Full Configuration");
                shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.export_full_config_text,
                                    new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date())));
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                startActivity(Intent.createChooser(shareIntent, getString(R.string.export_full_config_chooser)));
                
                // Show success message
                new AlertDialog.Builder(this)
                                    .setTitle(getString(R.string.dialog_title_export_successful))
                .setMessage(getString(R.string.dialog_message_export_successful, exportFile.getAbsolutePath()))
                .setPositiveButton(getString(R.string.ok), null)
                    .show();
                
                InAppLogger.log("GeneralSettings", "Full configuration exported to " + filename);
            } else {
                // Fallback to text-based sharing if file creation failed
                Intent textShareIntent = new Intent(Intent.ACTION_SEND);
                textShareIntent.setType("text/plain");
                textShareIntent.putExtra(Intent.EXTRA_SUBJECT, "SpeakThat! Full Configuration");
                textShareIntent.putExtra(Intent.EXTRA_TEXT, configData);
                
                startActivity(Intent.createChooser(textShareIntent, getString(R.string.export_full_config_text_chooser)));
                
                new AlertDialog.Builder(this)
                                    .setTitle(getString(R.string.dialog_title_export_successful))
                .setMessage(getString(R.string.dialog_message_export_successful_text))
                .setPositiveButton(getString(R.string.ok), null)
                    .show();
                
                InAppLogger.log("GeneralSettings", "Full configuration exported as text fallback");
            }
            
        } catch (Exception e) {
            InAppLogger.logError("GeneralSettings", "Export failed: " + e.getMessage());
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_export_failed))
                .setMessage(getString(R.string.dialog_message_export_failed, e.getMessage()))
                .setPositiveButton(getString(R.string.ok), null)
                .show();
        }
    }

    private void importFullConfiguration() {
        new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.dialog_title_import_full_config))
                .setMessage(getString(R.string.dialog_message_import_full_config))
                .setPositiveButton(getString(R.string.button_select_file), (dialog, which) -> openFilePicker())
                .setNegativeButton(getString(R.string.cancel), null)
            .show();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        // Add JSON MIME type
        String[] mimeTypes = {"application/json", "text/plain", "*/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        importFileLauncher.launch(Intent.createChooser(intent, getString(R.string.select_configuration_file)));
    }

    private void importFullConfiguration(Uri uri) {
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
            FilterConfigManager.ImportResult result = FilterConfigManager.importFullConfiguration(this, content.toString());
            
            if (result.success) {
                // Show success dialog
                new AlertDialog.Builder(this)
                                    .setTitle(getString(R.string.dialog_title_import_successful))
                .setMessage(getString(R.string.dialog_message_import_successful, result.message))
                .setPositiveButton(getString(R.string.ok), null)
                    .show();
                
                InAppLogger.log("GeneralSettings", "Full configuration imported successfully");
            } else {
                // Show error dialog
                new AlertDialog.Builder(this)
                                    .setTitle(getString(R.string.dialog_title_import_failed))
                .setMessage(getString(R.string.dialog_message_import_failed, result.message))
                .setPositiveButton(getString(R.string.ok), null)
                    .show();
            }
            
        } catch (IOException e) {
            InAppLogger.logError("GeneralSettings", "Import file read failed: " + e.getMessage());
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_import_failed))
                .setMessage(getString(R.string.dialog_message_import_failed_read, e.getMessage()))
                .setPositiveButton(getString(R.string.ok), null)
                .show();
        }
    }

    private void showClearDataDialog() {
        new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.dialog_title_clear_all_data))
                .setMessage(getString(R.string.dialog_message_clear_all_data))
                .setPositiveButton(getString(R.string.button_clear_all_data), (dialog, which) -> clearAllData())
                .setNegativeButton(getString(R.string.cancel), null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }

    private void clearAllData() {
        try {
            // Clear all SharedPreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.clear();
            editor.apply();
            
            // Clear voice settings
            SharedPreferences voicePrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE);
            voicePrefs.edit().clear().apply();
            
            // Clear notification history
            // Note: This would need to be implemented in NotificationReaderService
            
            // Reset to default theme
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            
            InAppLogger.log("GeneralSettings", "All data cleared successfully");
            
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_data_cleared))
                .setMessage(getString(R.string.dialog_message_data_cleared))
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> restartApp())
                .show();
                
        } catch (Exception e) {
            InAppLogger.logError("GeneralSettings", "Clear data failed: " + e.getMessage());
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_clear_failed))
                .setMessage(getString(R.string.dialog_message_clear_failed, e.getMessage()))
                .setPositiveButton(getString(R.string.ok), null)
                .show();
        }
    }

    private void restartApp() {
        // Create intent to restart the main activity
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        
        // Finish all activities in the current task
        finishAffinity();
        
        // Apply transition animation
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
} 