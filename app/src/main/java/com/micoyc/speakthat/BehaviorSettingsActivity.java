package com.micoyc.speakthat;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BehaviorSettingsActivity extends AppCompatActivity implements SensorEventListener, CustomAppNameAdapter.OnCustomAppNameActionListener {
    private ActivityBehaviorSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    
    // SharedPreferences keys
    private static final String PREFS_NAME = "SpeakThatPrefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_NOTIFICATION_BEHAVIOR = "notification_behavior"; // "interrupt", "queue", "smart"
    private static final String KEY_PRIORITY_APPS = "priority_apps"; // Set<String> of package names
    private static final String KEY_SHAKE_TO_STOP_ENABLED = "shake_to_stop_enabled";
    private static final String KEY_SHAKE_THRESHOLD = "shake_threshold";
    private static final String KEY_MEDIA_BEHAVIOR = "media_behavior";
    private static final String KEY_DUCKING_VOLUME = "ducking_volume";
    private static final String KEY_DELAY_BEFORE_READOUT = "delay_before_readout";
    private static final String KEY_CUSTOM_APP_NAMES = "custom_app_names"; // JSON string of custom app names

    // Media behavior options
    private static final String MEDIA_BEHAVIOR_IGNORE = "ignore";
    private static final String MEDIA_BEHAVIOR_PAUSE = "pause";
    private static final String MEDIA_BEHAVIOR_DUCK = "duck";
    private static final String MEDIA_BEHAVIOR_SILENCE = "silence";

    // Default values
    private static final String DEFAULT_NOTIFICATION_BEHAVIOR = "interrupt";
    private static final String DEFAULT_MEDIA_BEHAVIOR = MEDIA_BEHAVIOR_IGNORE;
    private static final int DEFAULT_DUCKING_VOLUME = 30; // 30% volume when ducking
    private static final int DEFAULT_DELAY_BEFORE_READOUT = 0; // 0 seconds (no delay)

    // Adapter and data
    private PriorityAppAdapter priorityAppAdapter;
    private List<String> priorityAppsList = new ArrayList<>();
    private CustomAppNameAdapter customAppNameAdapter;
    private List<CustomAppNameAdapter.CustomAppNameEntry> customAppNamesList = new ArrayList<>();
    
    // Shake detection
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isTestingShake = false;
    private float currentShakeValue = 0f;
    private float maxShakeValue = 0f;
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme FIRST before anything else
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applySavedTheme();
        
        super.onCreate(savedInstanceState);
        binding = ActivityBehaviorSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Initialize sensor manager
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        initializeUI();
        loadSettings();
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
        // Set up behavior mode radio buttons
        binding.behaviorModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String mode = "interrupt"; // default
            if (checkedId == R.id.radioQueue) {
                mode = "queue";
            } else if (checkedId == R.id.radioSkip) {
                mode = "skip";
            } else if (checkedId == R.id.radioSmart) {
                mode = "smart";
            }
            
            // Show/hide priority apps section
            binding.priorityAppsSection.setVisibility(
                "smart".equals(mode) ? View.VISIBLE : View.GONE
            );
            
            // Save setting
            saveBehaviorMode(mode);
        });

        // Set up RecyclerView for priority apps
        setupPriorityAppsRecycler();

        // Set up button listener
        binding.btnAddPriorityApp.setOnClickListener(v -> addPriorityApp());

        // Set up RecyclerView for custom app names
        setupCustomAppNamesRecycler();

        // Set up custom app name button listener
        binding.btnAddCustomAppName.setOnClickListener(v -> addCustomAppName());

        // Set up info button listeners
        binding.btnNotificationBehaviorInfo.setOnClickListener(v -> showNotificationBehaviorDialog());
        binding.btnMediaBehaviorInfo.setOnClickListener(v -> showMediaBehaviorDialog());
        binding.btnShakeToStopInfo.setOnClickListener(v -> showShakeToStopDialog());
        binding.btnDelayInfo.setOnClickListener(v -> showDelayDialog());
        binding.btnAppNamesInfo.setOnClickListener(v -> showCustomAppNamesDialog());

        // Set up shake to stop toggle
        binding.switchShakeToStop.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.shakeSettingsSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            saveShakeToStopEnabled(isChecked);
            
            // Stop any ongoing test when disabling
            if (!isChecked && isTestingShake) {
                stopShakeTest();
            }
        });

        // Set up shake intensity slider
        binding.sliderShakeIntensity.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(Slider slider, float value, boolean fromUser) {
                if (fromUser) {
                    updateThresholdMarker(value);
                    updateThresholdText(value);
                    saveShakeThreshold(value);
                }
            }
        });

        // Set up shake test button
        binding.btnShakeTest.setOnClickListener(v -> {
            if (isTestingShake) {
                stopShakeTest();
            } else {
                startShakeTest();
            }
        });

        // Set up media behavior radio buttons
        binding.mediaBehaviorGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String mediaBehavior = MEDIA_BEHAVIOR_IGNORE; // default
            if (checkedId == R.id.radioMediaPause) {
                mediaBehavior = MEDIA_BEHAVIOR_PAUSE;
            } else if (checkedId == R.id.radioMediaDuck) {
                mediaBehavior = MEDIA_BEHAVIOR_DUCK;
            } else if (checkedId == R.id.radioMediaSilence) {
                mediaBehavior = MEDIA_BEHAVIOR_SILENCE;
            }
            
            // Show/hide ducking volume controls
            binding.duckingVolumeContainer.setVisibility(
                MEDIA_BEHAVIOR_DUCK.equals(mediaBehavior) ? View.VISIBLE : View.GONE
            );
            
            // Save setting
            saveMediaBehavior(mediaBehavior);
        });

        // Set up ducking volume slider
        binding.duckingVolumeSeekBar.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(Slider slider, float value, boolean fromUser) {
                if (fromUser) {
                    int volumePercent = (int) value;
                    updateDuckingVolumeDisplay(volumePercent);
                    saveDuckingVolume(volumePercent);
                }
            }
        });

        // Set up delay before readout radio buttons
        binding.delayGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int delaySeconds = 0; // default
            if (checkedId == R.id.radioDelay1s) {
                delaySeconds = 1;
            } else if (checkedId == R.id.radioDelay2s) {
                delaySeconds = 2;
            } else if (checkedId == R.id.radioDelay3s) {
                delaySeconds = 3;
            }
            
            // Save setting
            saveDelayBeforeReadout(delaySeconds);
        });
    }

    private void setupPriorityAppsRecycler() {
        priorityAppAdapter = new PriorityAppAdapter(priorityAppsList, this::removePriorityApp);
        binding.recyclerPriorityApps.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPriorityApps.setAdapter(priorityAppAdapter);
    }

    private void setupCustomAppNamesRecycler() {
        customAppNameAdapter = new CustomAppNameAdapter(this);
        binding.recyclerCustomAppNames.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerCustomAppNames.setAdapter(customAppNameAdapter);
    }

    private void loadSettings() {
        // Load behavior mode
        String behaviorMode = sharedPreferences.getString(KEY_NOTIFICATION_BEHAVIOR, "interrupt");
        switch (behaviorMode) {
            case "queue":
                binding.radioQueue.setChecked(true);
                binding.priorityAppsSection.setVisibility(View.GONE);
                break;
            case "skip":
                binding.radioSkip.setChecked(true);
                binding.priorityAppsSection.setVisibility(View.GONE);
                break;
            case "smart":
                binding.radioSmart.setChecked(true);
                binding.priorityAppsSection.setVisibility(View.VISIBLE);
                break;
            default: // "interrupt"
                binding.radioInterrupt.setChecked(true);
                binding.priorityAppsSection.setVisibility(View.GONE);
                break;
        }

        // Load priority apps
        Set<String> priorityApps = sharedPreferences.getStringSet(KEY_PRIORITY_APPS, new HashSet<>());
        priorityAppsList.clear();
        priorityAppsList.addAll(priorityApps);
        priorityAppAdapter.notifyDataSetChanged();

        // Load shake to stop settings
        boolean shakeEnabled = sharedPreferences.getBoolean(KEY_SHAKE_TO_STOP_ENABLED, false);
        binding.switchShakeToStop.setChecked(shakeEnabled);
        binding.shakeSettingsSection.setVisibility(shakeEnabled ? View.VISIBLE : View.GONE);

        float shakeThreshold = sharedPreferences.getFloat(KEY_SHAKE_THRESHOLD, 12.0f);
        binding.sliderShakeIntensity.setValue(shakeThreshold);
        updateThresholdMarker(shakeThreshold);
        updateThresholdText(shakeThreshold);

        // Load media behavior settings
        String savedMediaBehavior = sharedPreferences.getString(KEY_MEDIA_BEHAVIOR, DEFAULT_MEDIA_BEHAVIOR);
        switch (savedMediaBehavior) {
            case MEDIA_BEHAVIOR_PAUSE:
                binding.radioMediaPause.setChecked(true);
                binding.duckingVolumeContainer.setVisibility(View.GONE);
                break;
            case MEDIA_BEHAVIOR_DUCK:
                binding.radioMediaDuck.setChecked(true);
                binding.duckingVolumeContainer.setVisibility(View.VISIBLE);
                break;
            case MEDIA_BEHAVIOR_SILENCE:
                binding.radioMediaSilence.setChecked(true);
                binding.duckingVolumeContainer.setVisibility(View.GONE);
                break;
            default: // MEDIA_BEHAVIOR_IGNORE
                binding.radioMediaIgnore.setChecked(true);
                binding.duckingVolumeContainer.setVisibility(View.GONE);
                break;
        }

        // Load ducking volume
        int savedDuckingVolume = sharedPreferences.getInt(KEY_DUCKING_VOLUME, DEFAULT_DUCKING_VOLUME);
        binding.duckingVolumeSeekBar.setValue(savedDuckingVolume);
        updateDuckingVolumeDisplay(savedDuckingVolume);

        // Load delay before readout settings
        int savedDelay = sharedPreferences.getInt(KEY_DELAY_BEFORE_READOUT, DEFAULT_DELAY_BEFORE_READOUT);
        switch (savedDelay) {
            case 1:
                binding.radioDelay1s.setChecked(true);
                break;
            case 2:
                binding.radioDelay2s.setChecked(true);
                break;
            case 3:
                binding.radioDelay3s.setChecked(true);
                break;
            default: // 0 seconds
                binding.radioDelayNone.setChecked(true);
                break;
        }

        // Load custom app names
        loadCustomAppNames();
    }

    private void addPriorityApp() {
        String appName = binding.editPriorityApp.getText().toString().trim();
        if (appName.isEmpty()) {
            Toast.makeText(this, "Please enter an app name", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check for duplicates
        if (priorityAppsList.contains(appName)) {
            Toast.makeText(this, "App already in priority list", Toast.LENGTH_SHORT).show();
            return;
        }

        priorityAppsList.add(appName);
        priorityAppAdapter.notifyDataSetChanged();
        binding.editPriorityApp.setText("");
        
        savePriorityApps();
    }

    private void removePriorityApp(int position) {
        priorityAppsList.remove(position);
        priorityAppAdapter.notifyDataSetChanged();
        savePriorityApps();
    }

    private void addCustomAppName() {
        String packageName = binding.editAppPackage.getText().toString().trim();
        String customName = binding.editCustomAppName.getText().toString().trim();
        
        if (packageName.isEmpty() || customName.isEmpty()) {
            Toast.makeText(this, "Please enter both package name and custom name", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check for duplicates
        for (CustomAppNameAdapter.CustomAppNameEntry entry : customAppNamesList) {
            if (entry.getPackageName().equals(packageName)) {
                Toast.makeText(this, "Package name already has a custom name", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        CustomAppNameAdapter.CustomAppNameEntry entry = new CustomAppNameAdapter.CustomAppNameEntry(packageName, customName);
        customAppNamesList.add(entry);
        customAppNameAdapter.addCustomAppName(entry);
        
        // Clear input fields
        binding.editAppPackage.setText("");
        binding.editCustomAppName.setText("");
        
        saveCustomAppNames();
    }

    private void removeCustomAppName(int position) {
        customAppNamesList.remove(position);
        customAppNameAdapter.removeCustomAppName(position);
        saveCustomAppNames();
    }

    @Override
    public void onDelete(int position) {
        removeCustomAppName(position);
    }

    private void loadCustomAppNames() {
        String customAppNamesJson = sharedPreferences.getString(KEY_CUSTOM_APP_NAMES, "[]");
        customAppNamesList.clear();
        
        try {
            JSONArray jsonArray = new JSONArray(customAppNamesJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String packageName = jsonObject.getString("packageName");
                String customName = jsonObject.getString("customName");
                customAppNamesList.add(new CustomAppNameAdapter.CustomAppNameEntry(packageName, customName));
            }
        } catch (JSONException e) {
            Log.e("BehaviorSettings", "Error loading custom app names", e);
        }
        
        // If no custom app names exist, add some defaults
        if (customAppNamesList.isEmpty()) {
            addDefaultCustomAppNames();
        }
        
        customAppNameAdapter.updateCustomAppNames(customAppNamesList);
    }

    private void addDefaultCustomAppNames() {
        // Add some common custom app names
        CustomAppNameAdapter.CustomAppNameEntry[] defaultEntries = {
            new CustomAppNameAdapter.CustomAppNameEntry("com.twitter.android", "Twitter"),
            new CustomAppNameAdapter.CustomAppNameEntry("com.facebook.katana", "Facebook"),
            new CustomAppNameAdapter.CustomAppNameEntry("com.whatsapp", "WhatsApp"),
            new CustomAppNameAdapter.CustomAppNameEntry("com.instagram.android", "Instagram"),
            new CustomAppNameAdapter.CustomAppNameEntry("com.telegram.messenger", "Telegram"),
            new CustomAppNameAdapter.CustomAppNameEntry("org.telegram.messenger", "Telegram"),
            new CustomAppNameAdapter.CustomAppNameEntry("com.snapchat.android", "Snapchat"),
            new CustomAppNameAdapter.CustomAppNameEntry("com.discord", "Discord"),
            new CustomAppNameAdapter.CustomAppNameEntry("com.slack", "Slack"),
            new CustomAppNameAdapter.CustomAppNameEntry("com.microsoft.teams", "Teams")
        };
        
        for (CustomAppNameAdapter.CustomAppNameEntry entry : defaultEntries) {
            customAppNamesList.add(entry);
        }
        
        saveCustomAppNames();
    }

    private void saveCustomAppNames() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (CustomAppNameAdapter.CustomAppNameEntry entry : customAppNamesList) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("packageName", entry.getPackageName());
                jsonObject.put("customName", entry.getCustomName());
                jsonArray.put(jsonObject);
            }
            
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(KEY_CUSTOM_APP_NAMES, jsonArray.toString());
            editor.apply();
            
            InAppLogger.log("BehaviorSettings", "Custom app names saved: " + customAppNamesList.size() + " entries");
        } catch (JSONException e) {
            Log.e("BehaviorSettings", "Error saving custom app names", e);
        }
    }

    private void saveBehaviorMode(String mode) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_NOTIFICATION_BEHAVIOR, mode);
        editor.apply();
    }

    private void savePriorityApps() {
        Set<String> appsSet = new HashSet<>(priorityAppsList);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putStringSet(KEY_PRIORITY_APPS, appsSet);
        editor.apply();
    }

    private void saveShakeToStopEnabled(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_SHAKE_TO_STOP_ENABLED, enabled);
        editor.apply();
    }

    private void saveShakeThreshold(float threshold) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(KEY_SHAKE_THRESHOLD, threshold);
        editor.apply();
    }

    private void saveMediaBehavior(String mediaBehavior) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_MEDIA_BEHAVIOR, mediaBehavior);
        editor.apply();
        InAppLogger.log("BehaviorSettings", "Media behavior changed to: " + mediaBehavior);
    }

    private void saveDuckingVolume(int volume) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_DUCKING_VOLUME, volume);
        editor.apply();
        InAppLogger.log("BehaviorSettings", "Ducking volume changed to: " + volume + "%");
    }

    private void saveDelayBeforeReadout(int delaySeconds) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_DELAY_BEFORE_READOUT, delaySeconds);
        editor.apply();
        InAppLogger.log("BehaviorSettings", "Delay before readout changed to: " + delaySeconds + " seconds");
    }

    private void updateThresholdMarker(float threshold) {
        // Calculate marker position based on threshold value (5-25 range)
        float percentage = (threshold - 5f) / 20f; // Convert to 0-1 range
        
        // Check if layout is already available
        int progressBarWidth = binding.progressShakeMeter.getWidth();
        if (progressBarWidth > 0) {
            // Layout is ready, update immediately
            updateMarkerPosition(percentage, progressBarWidth);
        } else {
            // Layout not ready yet, use ViewTreeObserver
            binding.progressShakeMeter.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    binding.progressShakeMeter.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    
                    int width = binding.progressShakeMeter.getWidth();
                    if (width > 0) {
                        updateMarkerPosition(percentage, width);
                    }
                }
            });
        }
    }

    private void updateMarkerPosition(float percentage, int progressBarWidth) {
        int markerPosition = (int) (progressBarWidth * percentage);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) binding.thresholdMarker.getLayoutParams();
        params.leftMargin = Math.max(0, markerPosition - 1); // Center the 3dp wide marker, ensure >= 0
        binding.thresholdMarker.setLayoutParams(params);
    }

    private void updateThresholdText(float threshold) {
        binding.textThreshold.setText(String.format("Threshold: %.1f", threshold));
    }

    private void startShakeTest() {
        if (accelerometer != null) {
            isTestingShake = true;
            maxShakeValue = 0f;
            binding.btnShakeTest.setText("Stop Test");
            binding.progressShakeMeter.setProgress(0);
            binding.textCurrentShake.setText("Current shake: 0.0");
            
            // Register sensor listener with high frequency for testing
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        } else {
            Toast.makeText(this, "Accelerometer not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopShakeTest() {
        if (isTestingShake) {
            isTestingShake = false;
            binding.btnShakeTest.setText("Start Test");
            sensorManager.unregisterListener(this);
            
            // Show max value achieved
            if (maxShakeValue > 0) {
                Toast.makeText(this, String.format("Peak shake: %.1f", maxShakeValue), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && isTestingShake) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            
            // Calculate total acceleration (subtract gravity)
            currentShakeValue = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;
            
            // Track maximum value during test
            if (currentShakeValue > maxShakeValue) {
                maxShakeValue = currentShakeValue;
            }
            
            // Update UI on main thread
            uiHandler.post(() -> {
                // Update progress bar (clamped to max value of 25)
                int progress = Math.round(Math.min(currentShakeValue, 25f));
                binding.progressShakeMeter.setProgress(progress);
                
                // Update current shake text
                binding.textCurrentShake.setText(String.format("Current shake: %.1f", currentShakeValue));
                
                // Check if threshold exceeded
                float threshold = binding.sliderShakeIntensity.getValue();
                if (currentShakeValue >= threshold) {
                    binding.textCurrentShake.setTextColor(getColor(android.R.color.holo_green_dark));
                } else {
                    binding.textCurrentShake.setTextColor(getColor(android.R.color.secondary_text_dark));
                }
            });
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed for this implementation
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up sensor listener
        if (isTestingShake) {
            sensorManager.unregisterListener(this);
        }
        
        binding = null;
    }

    // Interface for callback
    public interface OnPriorityAppActionListener {
        void onAction(int position);
    }

    private int getMediaBehaviorIndex(String mediaBehavior) {
        switch (mediaBehavior) {
            case MEDIA_BEHAVIOR_IGNORE: return 0;
            case MEDIA_BEHAVIOR_PAUSE: return 1;
            case MEDIA_BEHAVIOR_DUCK: return 2;
            case MEDIA_BEHAVIOR_SILENCE: return 3;
            default: return 0;
        }
    }

    private String getMediaBehaviorFromIndex(int index) {
        switch (index) {
            case 0: return MEDIA_BEHAVIOR_IGNORE;
            case 1: return MEDIA_BEHAVIOR_PAUSE;
            case 2: return MEDIA_BEHAVIOR_DUCK;
            case 3: return MEDIA_BEHAVIOR_SILENCE;
            default: return MEDIA_BEHAVIOR_IGNORE;
        }
    }

    private void updateDuckingVolumeDisplay(int volume) {
        binding.duckingVolumeValue.setText(volume + "%");
    }

    private void showNotificationBehaviorDialog() {
        // Track dialog usage for analytics
        trackDialogUsage("notification_behavior_info");
        
        String htmlText = "Choose how SpeakThat handles multiple notifications:<br><br>" +
                "<b>🔄 Interrupt</b> - Stops current notification and reads new one immediately. Best for urgent notifications.<br><br>" +
                "<b>📋 Queue</b> - Finishes current notification, then reads new ones in order. Nothing gets missed.<br><br>" +
                "<b>⏭️ Skip</b> - Ignores new notifications while reading. Simple but you might miss important ones.<br><br>" +
                "<b>🧠 Smart (Recommended)</b> - Priority apps interrupt, others queue. Perfect balance of urgency and completeness.<br><br>" +
                "Smart mode lets you choose which apps are urgent enough to interrupt (like calls, messages) while other apps wait their turn.";
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Notification Behavior Options")
                .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton(R.string.use_recommended, (dialog, which) -> {
                    // Track recommendation usage
                    trackDialogUsage("notification_behavior_recommended");
                    
                    // Set to Smart mode
                    binding.radioSmart.setChecked(true);
                    binding.priorityAppsSection.setVisibility(View.VISIBLE);
                    saveBehaviorMode("smart");
                    
                    // Add some common priority apps if none exist
                    if (priorityAppsList.isEmpty()) {
                        addDefaultPriorityApps();
                    }
                })
                .setNegativeButton(R.string.got_it, null)
                .show();
    }

    private void showMediaBehaviorDialog() {
        // Track dialog usage for analytics
        trackDialogUsage("media_behavior_info");
        
        String htmlText = "Choose how SpeakThat handles notifications while music/videos play:<br><br>" +
                "<b>🎵 Ignore</b> - Speaks over your media. Simple but can be disruptive.<br><br>" +
                "<b>⏸️ Pause</b> - Pauses media completely while speaking. Good for podcasts, but interrupts music flow.<br><br>" +
                "<b>🔉 Lower Audio (Recommended)</b> - Temporarily reduces media volume so you can hear both. Most natural experience.<br><br>" +
                "<b>🔇 Silence</b> - Doesn't speak while media plays. Quiet but you might miss important notifications.<br><br>" +
                "Lower Audio works like a car radio - it ducks the music down when speaking, then brings it back up. Perfect for music lovers who still want notifications.";
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Media Behavior Options")
                .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton(R.string.use_recommended, (dialog, which) -> {
                    // Track recommendation usage
                    trackDialogUsage("media_behavior_recommended");
                    
                    // Set to Lower Audio (Duck)
                    binding.radioMediaDuck.setChecked(true);
                    binding.duckingVolumeContainer.setVisibility(View.VISIBLE);
                    saveMediaBehavior(MEDIA_BEHAVIOR_DUCK);
                })
                .setNegativeButton(R.string.got_it, null)
                .show();
    }

    private void showShakeToStopDialog() {
        // Track dialog usage for analytics
        trackDialogUsage("shake_to_stop_info");
        
        String htmlText = "Shake to Stop lets you instantly silence SpeakThat by shaking your device:<br><br>" +
                "<b>✨ Why it's helpful:</b><br>" +
                "• Stop embarrassing notifications in quiet places<br>" +
                "• Quick control when your hands are busy<br>" +
                "• Works even when screen is off<br>" +
                "• No fumbling for buttons<br><br>" +
                "<b>🎯 How it works:</b><br>" +
                "• Shake your device firmly (like a dice shake)<br>" +
                "• Current notification stops immediately<br>" +
                "• Queued notifications are cleared<br>" +
                "• New notifications work normally<br><br>" +
                "<b>⚙️ Customization:</b><br>" +
                "• Adjust sensitivity for your preference<br>" +
                "• Test your shake strength with the meter<br>" +
                "• Works great for both gentle and vigorous shakers<br><br>" +
                "This feature is especially loved by people who get notifications during meetings, movies, or while driving.";
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Shake to Stop Feature")
                .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton(R.string.use_recommended, (dialog, which) -> {
                    // Track recommendation usage
                    trackDialogUsage("shake_to_stop_recommended");
                    
                    // Enable shake to stop
                    binding.switchShakeToStop.setChecked(true);
                    binding.shakeSettingsSection.setVisibility(View.VISIBLE);
                    saveShakeToStopEnabled(true);
                })
                .setNegativeButton(R.string.got_it, null)
                .show();
    }

    private void showDelayDialog() {
        // Track dialog usage for analytics
        trackDialogUsage("delay_info");
        
        String htmlText = "Delay Before Readout gives you a brief pause before SpeakThat starts speaking:<br><br>" +
                "<b>🎯 Perfect for avoiding notification sound overlap:</b><br>" +
                "• Your phone plays its notification sound first<br>" +
                "• Then SpeakThat waits the specified delay<br>" +
                "• Finally, SpeakThat speaks the notification<br>" +
                "• No more audio collision or jarring interruptions<br><br>" +
                "<b>⏰ Grace period for shake-to-cancel:</b><br>" +
                "• Gives you time to shake your phone to cancel<br>" +
                "• Perfect for notifications in quiet places<br>" +
                "• Especially useful during meetings or movies<br>" +
                "• Cancel before the readout even starts<br><br>" +
                "<b>🔧 Recommended settings:</b><br>" +
                "• <b>None (0s)</b> - Immediate readout<br>" +
                "• <b>1 second</b> - Quick pause, minimal delay<br>" +
                "• <b>2 seconds</b> - Recommended for most users<br>" +
                "• <b>3 seconds</b> - Extra time for reaction<br><br>" +
                "This feature was inspired by Touchless Notifications and helps create a more polished, less jarring notification experience.";
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Delay Before Readout")
                .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton(R.string.use_recommended, (dialog, which) -> {
                    // Track recommendation usage
                    trackDialogUsage("delay_recommended");
                    
                    // Set to 2 seconds (recommended)
                    binding.radioDelay2s.setChecked(true);
                    saveDelayBeforeReadout(2);
                })
                .setNegativeButton(R.string.got_it, null)
                .show();
    }

    private void showCustomAppNamesDialog() {
        // Track dialog usage for analytics
        trackDialogUsage("custom_app_names_info");
        
        String htmlText = "Custom App Names let you change how app names are spoken in notifications:<br><br>" +
                "<b>🎯 Why customize app names?</b><br>" +
                "Some apps have confusing or unclear names when spoken aloud. This feature lets you create custom names that are easier to understand:<br><br>" +
                "<b>📱 Examples:</b><br>" +
                "• <b>X app</b> → <b>Twitter</b><br>" +
                "• <b>Meta</b> → <b>Facebook</b><br>" +
                "• <b>WA</b> → <b>WhatsApp</b><br>" +
                "• <b>IG</b> → <b>Instagram</b><br><br>" +
                "<b>🔧 How to use:</b><br>" +
                "1. Find the app's package name (e.g., com.twitter.android)<br>" +
                "2. Enter a custom name that's easier to say<br>" +
                "3. SpeakThat will use your custom name instead<br><br>" +
                "<b>💡 Finding package names:</b><br>" +
                "• Check the app's Play Store URL<br>" +
                "• Use a package name finder app<br>" +
                "• Common format: com.company.appname<br><br>" +
                "<b>Note:</b> This only affects how the app name is spoken, not the actual app name on your device.";
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Custom App Names")
                .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton("Got it!", null)
                .show();
    }

    private void addDefaultPriorityApps() {
        // Add some common priority apps
        String[] defaultPriorityApps = {
            "Phone", "Messages", "WhatsApp", "Telegram", "Signal"
        };
        
        for (String appName : defaultPriorityApps) {
            if (!priorityAppsList.contains(appName)) {
                priorityAppsList.add(appName);
            }
        }
        
        priorityAppAdapter.notifyDataSetChanged();
        savePriorityApps();
        
        Toast.makeText(this, "Added common priority apps. You can remove or add more as needed.", Toast.LENGTH_LONG).show();
    }

    private void trackDialogUsage(String dialogType) {
        // Local analytics - track which help dialogs are most used
        String key = "dialog_usage_" + dialogType;
        int currentCount = sharedPreferences.getInt(key, 0);
        sharedPreferences.edit().putInt(key, currentCount + 1).apply();
        
        // Also track total dialog usage
        int totalUsage = sharedPreferences.getInt("total_dialog_usage", 0);
        sharedPreferences.edit().putInt("total_dialog_usage", totalUsage + 1).apply();
        
        // Track last used timestamp for analytics
        sharedPreferences.edit().putLong("last_dialog_usage", System.currentTimeMillis()).apply();
    }

} 