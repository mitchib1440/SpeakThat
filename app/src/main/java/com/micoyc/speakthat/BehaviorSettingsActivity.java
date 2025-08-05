package com.micoyc.speakthat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
import android.widget.AutoCompleteTextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.slider.Slider;
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.micoyc.speakthat.LazyAppSearchAdapter;
import com.micoyc.speakthat.AppInfo;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.text.Editable;
import android.text.TextWatcher;

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
    private static final String KEY_SHAKE_TIMEOUT_SECONDS = "shake_timeout_seconds";
    private static final String KEY_WAVE_TO_STOP_ENABLED = "wave_to_stop_enabled";
    private static final String KEY_WAVE_THRESHOLD = "wave_threshold";
    private static final String KEY_WAVE_TIMEOUT_SECONDS = "wave_timeout_seconds";
    private static final String KEY_POCKET_MODE_ENABLED = "pocket_mode_enabled";
    private static final String KEY_MEDIA_BEHAVIOR = "media_behavior";
    private static final String KEY_DUCKING_VOLUME = "ducking_volume";
    private static final String KEY_DELAY_BEFORE_READOUT = "delay_before_readout";
    private static final String KEY_CUSTOM_APP_NAMES = "custom_app_names"; // JSON string of custom app names
    private static final String KEY_COOLDOWN_APPS = "cooldown_apps"; // JSON string of cooldown app settings
    private static final String KEY_HONOUR_DO_NOT_DISTURB = "honour_do_not_disturb"; // boolean
    private static final String KEY_HONOUR_PHONE_CALLS = "honour_phone_calls"; // boolean
    private static final String KEY_SPEECH_TEMPLATE = "speech_template"; // Custom speech template

    // Media behavior options
    private static final String MEDIA_BEHAVIOR_IGNORE = "ignore";
    private static final String MEDIA_BEHAVIOR_PAUSE = "pause";
    private static final String MEDIA_BEHAVIOR_DUCK = "duck";
    private static final String MEDIA_BEHAVIOR_SILENCE = "silence";

    // Default values
    private static final String DEFAULT_NOTIFICATION_BEHAVIOR = "smart";
    private static final String DEFAULT_MEDIA_BEHAVIOR = MEDIA_BEHAVIOR_DUCK;
    private static final int DEFAULT_DUCKING_VOLUME = 30; // 30% volume when ducking
    private static final int DEFAULT_DELAY_BEFORE_READOUT = 2; // 2 seconds
    private static final boolean DEFAULT_HONOUR_DO_NOT_DISTURB = true; // Default to honouring DND
    private static final boolean DEFAULT_HONOUR_PHONE_CALLS = true; // Default to honouring phone calls

    // Speech template constants
    private static final String DEFAULT_SPEECH_TEMPLATE = "{app} notified you: {content}";
    private static final String[] TEMPLATE_PRESETS = {
        "Default", "Minimal", "Formal", "Casual", "Time Aware", "Content Only", "App Only", "Varied", "Custom"
    };
    // Template keys that correspond to localized string resources
    private static final String[] TEMPLATE_KEYS = {
        "tts_format_default",
        "tts_format_minimal", 
        "tts_format_formal",
        "tts_format_casual",
        "tts_format_time_aware",
        "tts_format_content_only",
        "tts_format_app_only",
        "VARIED", // Special marker for varied mode
        "CUSTOM" // Custom will be handled separately
    };

    // Varied format options for random selection
    private static final String[] VARIED_FORMATS = {
        "{app} notified you: {content}",
        "{app} reported: {content}",
        "Notification from {app}, saying {content}",
        "Notification from {app}: {content}",
        "{app} alerts you: {content}",
        "Update from {app}: {content}",
        "{app} says: {content}",
        "{app} notification: {content}",
        "New notification: {app}: {content}",
        "New from {app}: {content}",
        "{app} said: {content}",
        "{app} updated you: {content}",
        "New notification from {app}: saying: {content}",
        "New update from {app}: {content}",
        "{app}: {content}"
    };

    // Adapter and data
    private PriorityAppAdapter priorityAppAdapter;
    private List<String> priorityAppsList = new ArrayList<>();
    private CustomAppNameAdapter customAppNameAdapter;
    private List<CustomAppNameAdapter.CustomAppNameEntry> customAppNamesList = new ArrayList<>();
    private CooldownAppAdapter cooldownAppAdapter;
    private List<CooldownAppAdapter.CooldownAppItem> cooldownAppsList = new ArrayList<>();
    
    // App selector for cooldown apps
    private LazyAppSearchAdapter cooldownAppSelectorAdapter;
    
    // App selector for priority apps
    private LazyAppSearchAdapter priorityAppSelectorAdapter;
    
    // Shake detection
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isTestingShake = false;
    private float currentShakeValue = 0f;
    private float maxShakeValue = 0f;
    
    // Wave detection
    private Sensor proximitySensor;
    private boolean isTestingWave = false;
    private float currentWaveValue = 5.0f; // Default max distance
    private float minWaveValue = 5.0f; // Track minimum (closest) value during test
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    private float calibratedMaxDistance = -1f;
    private float thresholdPercent = 60f; // Default to 60%
    private boolean isProcessingCalibrationResult = false; // Flag to prevent race condition
    private boolean isProgrammaticallySettingSwitch = false; // Flag to prevent dialog loops

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme FIRST before anything else
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applySavedTheme();
        
        super.onCreate(savedInstanceState);
        binding = ActivityBehaviorSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.title_behavior_settings));
        }

        // Initialize sensor manager
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        initializeUI();
        loadSettings();
        
        // Test app list functionality for debugging
        testAppListFunctionality();
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

        // Set up priority app selector
        setupPriorityAppSelector();

        // Set up button listener
        binding.btnAddPriorityApp.setOnClickListener(v -> addPriorityApp());

        // Set up RecyclerView for custom app names
        setupCustomAppNamesRecycler();

        // Set up RecyclerView for cooldown apps
        setupCooldownAppsRecycler();

        // Set up cooldown app selector
        setupCooldownAppSelector();

        // Set up custom app name button listener
        binding.btnAddCustomAppName.setOnClickListener(v -> addCustomAppName());

        // Set up cooldown app button listener
        binding.btnAddCooldownApp.setOnClickListener(v -> addCooldownApp());

        // Set up info button listeners
        binding.btnNotificationBehaviorInfo.setOnClickListener(v -> showNotificationBehaviorDialog());
        binding.btnMediaBehaviorInfo.setOnClickListener(v -> showMediaBehaviorDialog());
        binding.btnDoNotDisturbInfo.setOnClickListener(v -> showDoNotDisturbDialog());
        binding.btnShakeToStopInfo.setOnClickListener(v -> showShakeToStopDialog());
        binding.btnWaveToStopInfo.setOnClickListener(v -> showWaveToStopDialog());
        binding.btnDelayInfo.setOnClickListener(v -> showDelayDialog());
        binding.btnAppNamesInfo.setOnClickListener(v -> showCustomAppNamesDialog());
        binding.btnCooldownInfo.setOnClickListener(v -> showCooldownDialog());

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

        // Set up wave to stop toggle
        binding.switchWaveToStop.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.d("WaveCalibration", "Switch toggle triggered - isChecked: " + isChecked + ", isProcessingCalibrationResult: " + isProcessingCalibrationResult);
            
            // Skip processing if we're currently handling a calibration result
            if (isProcessingCalibrationResult) {
                Log.d("WaveCalibration", "Skipping switch processing - currently handling calibration result");
                return;
            }
            
            if (isChecked) {
                // Check if calibration data exists
                if (!hasValidCalibrationData()) {
                    Log.d("WaveCalibration", "No valid calibration data - launching calibration");
                    // Launch calibration activity
                    launchWaveCalibration();
                    // Don't save the toggle state yet - wait for calibration result
                    buttonView.setChecked(false);
                    return;
                }
            }

            Log.d("WaveCalibration", "Processing normal switch toggle - isChecked: " + isChecked);
            binding.waveSettingsSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            saveWaveToStopEnabled(isChecked);
            
            // Force update marker when section becomes visible
            if (isChecked) {
                uiHandler.postDelayed(() -> forceUpdateWaveMarker(), 200);
            }
            
            // Stop any ongoing test when disabling
            if (!isChecked && isTestingWave) {
                stopWaveTest();
            }
        });

        // Set up wave sensitivity slider
        binding.sliderWaveSensitivity.setValueFrom(30f);
        binding.sliderWaveSensitivity.setValueTo(90f);
        binding.sliderWaveSensitivity.setStepSize(1f);
        binding.sliderWaveSensitivity.setLabelFormatter(value -> String.format("%.0f%%", value));
        binding.textWaveThreshold.setText("Proximity Threshold: 60% (3.00 cm of 5.00 cm max) - Not calibrated");

        binding.sliderWaveSensitivity.addOnChangeListener((slider, value, fromUser) -> {
            Log.d("WaveTest", "Slider changed - value: " + value + ", fromUser: " + fromUser);
            thresholdPercent = value;
            
            // Use calibrated distance if available, otherwise use default fallback
            float maxDistance = calibratedMaxDistance > 0 ? calibratedMaxDistance : 5.0f;
            float threshold = maxDistance * (thresholdPercent / 100f);
            
            Log.d("WaveTest", "Calculated threshold: " + threshold + " cm from " + maxDistance + " cm max");
            
            saveWaveThresholdPercent(thresholdPercent);
            updateWaveThresholdMarker(threshold);
            updateWaveThresholdText(threshold);
        });

        // Set up wave test button
        binding.btnWaveTest.setOnClickListener(v -> {
            if (isTestingWave) {
                stopWaveTest();
            } else {
                startWaveTest();
            }
        });
        
        // Set up wave recalibration button
        binding.btnRecalibrateWave.setOnClickListener(v -> {
            launchWaveCalibration();
        });

        // Set up shake timeout slider
        binding.sliderShakeTimeout.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(Slider slider, float value, boolean fromUser) {
                if (fromUser) {
                    int timeoutSeconds = (int) value;
                    updateShakeTimeoutDisplay(timeoutSeconds);
                    saveShakeTimeoutSeconds(timeoutSeconds);
                }
            }
        });

        // Set up shake timeout disable switch
        binding.switchShakeTimeoutDisabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isProgrammaticallySettingSwitch) {
                return; // Skip if we're programmatically setting the switch
            }
            
            if (isChecked) {
                showTimeoutDisableWarning("shake");
                buttonView.setChecked(false); // Reset until user confirms
            } else {
                // User unchecked the disable switch - re-enable timeout with current slider value
                int currentSliderValue = (int) binding.sliderShakeTimeout.getValue();
                saveShakeTimeoutSeconds(currentSliderValue);
                updateShakeTimeoutDisplay(currentSliderValue);
            }
        });

        // Set up shake timeout info button
        binding.btnShakeTimeoutInfo.setOnClickListener(v -> showTimeoutInfoDialog("shake"));

        // Set up wave timeout slider
        binding.sliderWaveTimeout.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(Slider slider, float value, boolean fromUser) {
                if (fromUser) {
                    int timeoutSeconds = (int) value;
                    updateWaveTimeoutDisplay(timeoutSeconds);
                    saveWaveTimeoutSeconds(timeoutSeconds);
                }
            }
        });

        // Set up wave timeout disable switch
        binding.switchWaveTimeoutDisabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isProgrammaticallySettingSwitch) {
                return; // Skip if we're programmatically setting the switch
            }
            
            if (isChecked) {
                showTimeoutDisableWarning("wave");
                buttonView.setChecked(false); // Reset until user confirms
            } else {
                // User unchecked the disable switch - re-enable timeout with current slider value
                int currentSliderValue = (int) binding.sliderWaveTimeout.getValue();
                saveWaveTimeoutSeconds(currentSliderValue);
                updateWaveTimeoutDisplay(currentSliderValue);
            }
        });

        // Set up wave timeout info button
        binding.btnWaveTimeoutInfo.setOnClickListener(v -> showTimeoutInfoDialog("wave"));

        // Set up pocket mode switch
        binding.switchPocketMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            savePocketModeEnabled(isChecked);
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
            
            // Show/hide ducking volume controls and warning
            boolean showDucking = MEDIA_BEHAVIOR_DUCK.equals(mediaBehavior);
            binding.duckingVolumeContainer.setVisibility(showDucking ? View.VISIBLE : View.GONE);
            binding.duckingWarningText.setVisibility(showDucking ? View.VISIBLE : View.GONE);
            if (showDucking) {
                binding.duckingWarningText.setText("⚠️ Audio ducking works differently across devices and Android versions. Some devices may not duck certain apps or audio streams properly.\n\n" +
                        "💡 If ducking isn't working well: Try different audio streams in Voice Settings ('Voice Call' or 'Notification' often work best). " +
                        "Avoid 'Media' stream as it may duck your TTS along with your music.\n\n" +
                        "🔧 The app automatically uses advanced system ducking when available, with smooth manual ducking as backup for maximum compatibility.");
            }
            // Show/hide pause warning
            boolean showPauseWarning = MEDIA_BEHAVIOR_PAUSE.equals(mediaBehavior);
            binding.pauseWarningText.setVisibility(showPauseWarning ? View.VISIBLE : View.GONE);
            
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

        // Set up Do Not Disturb toggle
        binding.switchHonourDoNotDisturb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveHonourDoNotDisturb(isChecked);
        });
        
        // Set up Audio Mode toggle
        binding.switchHonourAudioMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveHonourAudioMode(isChecked);
        });
        
        // Set up Phone Calls toggle
        binding.switchHonourPhoneCalls.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveHonourPhoneCalls(isChecked);
        });
        
        // Set up Audio Mode info button
        binding.btnAudioModeInfo.setOnClickListener(v -> showAudioModeDialog());
        
        // Set up Phone Calls info button
        binding.btnPhoneCallsInfo.setOnClickListener(v -> showPhoneCallsDialog());
        
        // Set up speech template functionality
        setupSpeechTemplateUI();
    }
    
    private void setupSpeechTemplateUI() {
        // Set up template preset spinner
        ArrayAdapter<String> templateAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, TEMPLATE_PRESETS
        );
        templateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerSpeechTemplate.setAdapter(templateAdapter);
        
        // Handle template preset selection
        binding.spinnerSpeechTemplate.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                String selectedTemplateKey = TEMPLATE_KEYS[position];
                
                if (selectedTemplateKey.equals("VARIED")) {
                    // Hide custom input section for varied mode
                    binding.editCustomSpeechTemplate.setVisibility(View.GONE);
                    // Also hide the "Custom Format:" label by finding its parent container
                    View customFormatContainer = (View) binding.editCustomSpeechTemplate.getParent().getParent();
                    customFormatContainer.setVisibility(View.GONE);
                    binding.textSpeechPreview.setText("Varied mode: Random format selected for each notification");
                    binding.textSpeechPreview.setTextColor(getResources().getColor(R.color.text_tertiary));
                    saveSpeechTemplate("VARIED");
                } else if (selectedTemplateKey.equals("CUSTOM")) {
                    // Custom mode - show input field
                    binding.editCustomSpeechTemplate.setVisibility(View.VISIBLE);
                    View customFormatContainer = (View) binding.editCustomSpeechTemplate.getParent().getParent();
                    customFormatContainer.setVisibility(View.VISIBLE);
                    updateSpeechPreview();
                } else {
                    // Regular preset - get localized template and update input field and preview
                    String localizedTemplate = getLocalizedTemplateValue(selectedTemplateKey);
                    binding.editCustomSpeechTemplate.setVisibility(View.VISIBLE);
                    View customFormatContainer = (View) binding.editCustomSpeechTemplate.getParent().getParent();
                    customFormatContainer.setVisibility(View.VISIBLE);
                    binding.editCustomSpeechTemplate.setText(localizedTemplate);
                    updateSpeechPreview();
                    saveSpeechTemplate(localizedTemplate);
                }
            }
            
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Do nothing
            }
        });
        
        // Handle custom template text changes
        binding.editCustomSpeechTemplate.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String newTemplate = s.toString();
                
                // Don't auto-select if we're in varied mode
                if (binding.spinnerSpeechTemplate.getSelectedItemPosition() == TEMPLATE_PRESETS.length - 2) { // Varied is second to last
                    return;
                }
                
                // Check if the new template matches any preset
                boolean matchesPreset = false;
                for (int i = 0; i < TEMPLATE_KEYS.length - 1; i++) { // Skip the last "Custom" option
                    String localizedTemplate = getLocalizedTemplateValue(TEMPLATE_KEYS[i]);
                    if (localizedTemplate.equals(newTemplate)) {
                        matchesPreset = true;
                        binding.spinnerSpeechTemplate.setSelection(i);
                        break;
                    }
                }
                if (!matchesPreset) {
                    binding.spinnerSpeechTemplate.setSelection(TEMPLATE_KEYS.length - 1); // "Custom" is the last option
                }
                
                updateSpeechPreview();
                saveSpeechTemplate(newTemplate);
            }
        });
        
        // Set up test template button
        binding.btnTestSpeechTemplate.setOnClickListener(v -> showTemplateTestDialog());
        
        // Set up speech template info button
        binding.btnSpeechTemplateInfo.setOnClickListener(v -> showSpeechTemplateDialog());
    }
    
    private void showTemplateTestDialog() {
        String template = binding.editCustomSpeechTemplate.getText().toString();
        boolean isVariedMode = template.equals("VARIED") || binding.spinnerSpeechTemplate.getSelectedItemPosition() == TEMPLATE_PRESETS.length - 2;
        
        // Create different test scenarios with realistic content
        String[] testTitles = {
            "Mitchi",
            "SpeakThat! Bug Report",
            "@mitchib1440",
            "Weather Alert",
            "Battery low (15% remaining)",
            "'Thank you for using SpeakThat!'"
        };
        
        String[] testTexts = {
            "I heard you're using SpeakThat! Did it just speak that?",
            "Thanks for submitting a bug report! We'll get this one squashed in the next release! - Mitchi",
            "Just released a new app update! Check it out",
            "Heavy rain expected in 2 hours",
            "connect charger",
            "@mitchib1440 replied"
        };
        
        String[] testContents = {
            "Mitchi: I heard you're using SpeakThat! Did it just speak that?",
            "SpeakThat! Bug Report: Thanks for submitting a bug report! We'll get this one squashed in the next release! - Mitchi",
            "@mitchib1440: Just released a new app update! Check it out",
            "Weather Alert: Heavy rain expected in 2 hours",
            "Battery low (15% remaining) - connect charger",
            "@mitchib1440 replied: 'Thank you for using SpeakThat!'"
        };
        
        String[] testApps = {
            "Messages",
            "Gmail", 
            "Twitter",
            "Weather",
            "System",
            "YouTube"
        };
        
        StringBuilder testResults = new StringBuilder();
        
        if (isVariedMode) {
            testResults.append("Your format: <b>Varied (Random selection)</b><br><br>");
            testResults.append("<b>How it would sound (random format for each):</b><br><br>");
        } else {
            testResults.append("Your format: <b>").append(template).append("</b><br><br>");
            testResults.append("<b>How it would sound:</b><br><br>");
        }
        
        for (int i = 0; i < testContents.length; i++) {
            String templateToUse = template;
            if (isVariedMode) {
                // Use a random varied format for each test scenario
                templateToUse = VARIED_FORMATS[i % VARIED_FORMATS.length];
            }
            
            String result = templateToUse
                .replace("{app}", testApps[i])
                .replace("{package}", "com.test." + testApps[i].toLowerCase())
                .replace("{content}", testContents[i])
                .replace("{title}", testTitles[i])
                .replace("{text}", testTexts[i])
                .replace("{bigtext}", testContents[i])
                .replace("{summary}", "1 new notification")
                .replace("{info}", "Tap to view")
                .replace("{time}", "14:40")
                .replace("{date}", "Dec 15")
                .replace("{timestamp}", "14:40 Dec 15")
                .replace("{priority}", "High")
                .replace("{category}", "Message")
                .replace("{channel}", "Notifications");
            
            if (isVariedMode) {
                testResults.append("• <b>").append(testApps[i]).append(":</b> \"").append(result).append("\" <i>(using: ").append(templateToUse).append(")</i><br><br>");
            } else {
                testResults.append("• <b>").append(testApps[i]).append(":</b> \"").append(result).append("\"<br><br>");
            }
        }
        
        if (isVariedMode) {
            testResults.append("<b>💡 Varied Mode Tips:</b><br>");
            testResults.append("• Each notification gets a random format from 15 options<br>");
            testResults.append("• Adds variety and personality to your notifications<br>");
            testResults.append("• No configuration needed - just enjoy the variety!<br>");
            testResults.append("• Test with real notifications to see the full effect");
        } else {
            testResults.append("<b>💡 Tips:</b><br>");
            testResults.append("• Test with real notifications to see actual results<br>");
            testResults.append("• Adjust spacing and punctuation for better pronunciation<br>");
            testResults.append("• Consider how it sounds when spoken quickly");
        }
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("🧪 Format Test Results")
                .setMessage(Html.fromHtml(testResults.toString(), Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton("Got it!", null)
                .show();
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

    private void setupCooldownAppsRecycler() {
        cooldownAppAdapter = new CooldownAppAdapter(this, cooldownAppsList, new CooldownAppAdapter.OnCooldownAppActionListener() {
            @Override
            public void onCooldownTimeChanged(int position, int cooldownSeconds) {
                if (position >= 0 && position < cooldownAppsList.size()) {
                    cooldownAppsList.get(position).cooldownSeconds = cooldownSeconds;
                    saveCooldownApps();
                }
            }

            @Override
            public void onDeleteCooldownApp(int position) {
                removeCooldownApp(position);
            }
        });
        binding.recyclerCooldownApps.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerCooldownApps.setAdapter(cooldownAppAdapter);
    }

    private void setupCooldownAppSelector() {
        // Use lazy loading adapter for cooldown app selector
        cooldownAppSelectorAdapter = new LazyAppSearchAdapter(this);
        binding.editCooldownApp.setAdapter(cooldownAppSelectorAdapter);
        binding.editCooldownApp.setThreshold(1); // Show suggestions after 1 character
        
        // Handle app selection
        binding.editCooldownApp.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo selectedApp = cooldownAppSelectorAdapter.getItem(position);
            if (selectedApp != null) {
                // Add the selected app to the cooldown list
                addCooldownAppFromSelection(selectedApp);
                // Clear the input field after selection
                binding.editCooldownApp.setText("");
            }
        });
        
        InAppLogger.log("AppSelector", "Lazy cooldown app selector initialized - apps will load on search");
    }

    private void setupPriorityAppSelector() {
        // Use lazy loading adapter for priority app selector
        priorityAppSelectorAdapter = new LazyAppSearchAdapter(this);
        binding.editPriorityApp.setAdapter(priorityAppSelectorAdapter);
        binding.editPriorityApp.setThreshold(1); // Show suggestions after 1 character
        
        // Handle app selection
        binding.editPriorityApp.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo selectedApp = priorityAppSelectorAdapter.getItem(position);
            if (selectedApp != null) {
                // Add the selected app to the priority list
                addPriorityAppFromSelection(selectedApp);
                // Clear the input field after selection
                binding.editPriorityApp.setText("");
            }
        });
        
        InAppLogger.log("AppSelector", "Lazy priority app selector initialized - apps will load on search");
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
        boolean shakeEnabled = sharedPreferences.getBoolean(KEY_SHAKE_TO_STOP_ENABLED, true);
        binding.switchShakeToStop.setChecked(shakeEnabled);
        binding.shakeSettingsSection.setVisibility(shakeEnabled ? View.VISIBLE : View.GONE);

        float shakeThreshold = sharedPreferences.getFloat(KEY_SHAKE_THRESHOLD, 12.0f);
        binding.sliderShakeIntensity.setValue(shakeThreshold);
        updateThresholdMarker(shakeThreshold);
        updateThresholdText(shakeThreshold);

        // Load shake timeout settings
        int shakeTimeoutSeconds = sharedPreferences.getInt(KEY_SHAKE_TIMEOUT_SECONDS, 30);
        if (shakeTimeoutSeconds == 0) {
            // Timeout is disabled - set slider to 30 but keep disabled state
            binding.sliderShakeTimeout.setValue(30);
            isProgrammaticallySettingSwitch = true;
            binding.switchShakeTimeoutDisabled.setChecked(true);
            isProgrammaticallySettingSwitch = false;
        } else {
            // Timeout is enabled - set slider to actual value
            binding.sliderShakeTimeout.setValue(shakeTimeoutSeconds);
            isProgrammaticallySettingSwitch = true;
            binding.switchShakeTimeoutDisabled.setChecked(false);
            isProgrammaticallySettingSwitch = false;
        }
        updateShakeTimeoutDisplay(shakeTimeoutSeconds);

        // Load wave to stop settings
        boolean waveEnabled = sharedPreferences.getBoolean(KEY_WAVE_TO_STOP_ENABLED, false);
        Log.d("WaveCalibration", "Loading wave settings - waveEnabled: " + waveEnabled);
        binding.switchWaveToStop.setChecked(waveEnabled);
        binding.waveSettingsSection.setVisibility(waveEnabled ? View.VISIBLE : View.GONE);

        // Clamp threshold percent to valid range (30-90), default to 60 if out of range
        float percent = getSharedPreferences("BehaviorSettings", MODE_PRIVATE).getFloat("wave_threshold_percent", 60f);
        if (percent < 30f || percent > 90f) {
            float clamped = (percent < 30f || percent > 90f) ? 60f : Math.max(30f, Math.min(90f, percent));
            Log.d("WaveTest", "Clamping invalid threshold percent: " + percent + " -> " + clamped);
            percent = clamped;
            saveWaveThresholdPercent(percent);
        }
        thresholdPercent = percent;
        calibratedMaxDistance = getSharedPreferences("BehaviorSettings", MODE_PRIVATE).getFloat("sensor_max_range_v1", -1f);
        float threshold = (calibratedMaxDistance > 0) ? (calibratedMaxDistance * (thresholdPercent / 100f)) : 3.0f;

        // Enable slider (will use fallback values if not calibrated)
        binding.sliderWaveSensitivity.setEnabled(true);
        binding.sliderWaveSensitivity.setValue(thresholdPercent);
        
        // Update progress bar max value to match calibrated distance
        updateWaveProgressBarMax();
        
        updateWaveThresholdMarker(threshold);
        updateWaveThresholdText(threshold);

        // Load wave timeout settings
        int waveTimeoutSeconds = sharedPreferences.getInt(KEY_WAVE_TIMEOUT_SECONDS, 30);
        if (waveTimeoutSeconds == 0) {
            // Timeout is disabled - set slider to 30 but keep disabled state
            binding.sliderWaveTimeout.setValue(30);
            isProgrammaticallySettingSwitch = true;
            binding.switchWaveTimeoutDisabled.setChecked(true);
            isProgrammaticallySettingSwitch = false;
        } else {
            // Timeout is enabled - set slider to actual value
            binding.sliderWaveTimeout.setValue(waveTimeoutSeconds);
            isProgrammaticallySettingSwitch = true;
            binding.switchWaveTimeoutDisabled.setChecked(false);
            isProgrammaticallySettingSwitch = false;
        }
        updateWaveTimeoutDisplay(waveTimeoutSeconds);

        // Load pocket mode setting
        boolean pocketModeEnabled = sharedPreferences.getBoolean(KEY_POCKET_MODE_ENABLED, false);
        binding.switchPocketMode.setChecked(pocketModeEnabled);

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

        // Load cooldown apps
        loadCooldownApps();

        // Load Do Not Disturb setting
        boolean honourDoNotDisturb = sharedPreferences.getBoolean(KEY_HONOUR_DO_NOT_DISTURB, DEFAULT_HONOUR_DO_NOT_DISTURB);
        binding.switchHonourDoNotDisturb.setChecked(honourDoNotDisturb);
        
        // Load Audio Mode setting
        boolean honourAudioMode = sharedPreferences.getBoolean("honour_audio_mode", true); // Default to true for safety
        binding.switchHonourAudioMode.setChecked(honourAudioMode);
        
        // Load Phone Calls setting
        boolean honourPhoneCalls = sharedPreferences.getBoolean(KEY_HONOUR_PHONE_CALLS, DEFAULT_HONOUR_PHONE_CALLS);
        binding.switchHonourPhoneCalls.setChecked(honourPhoneCalls);
        
        // Load speech template settings
        loadSpeechTemplateSettings();
    }
    
    private void loadSpeechTemplateSettings() {
        String savedTemplate = sharedPreferences.getString(KEY_SPEECH_TEMPLATE, DEFAULT_SPEECH_TEMPLATE);
        
        if (savedTemplate.equals("VARIED")) {
            // Set spinner to Varied and hide custom input section
            binding.spinnerSpeechTemplate.setSelection(TEMPLATE_PRESETS.length - 2); // Varied is second to last
            binding.editCustomSpeechTemplate.setVisibility(View.GONE);
            View customFormatContainer = (View) binding.editCustomSpeechTemplate.getParent().getParent();
            customFormatContainer.setVisibility(View.GONE);
            binding.textSpeechPreview.setText("Varied mode: Random format selected for each notification");
            binding.textSpeechPreview.setTextColor(getResources().getColor(R.color.text_tertiary));
        } else {
            // Find the preset that matches the saved template
            int presetIndex = -1;
            for (int i = 0; i < TEMPLATE_KEYS.length; i++) {
                String localizedTemplate = getLocalizedTemplateValue(TEMPLATE_KEYS[i]);
                if (localizedTemplate.equals(savedTemplate)) {
                    presetIndex = i;
                    break;
                }
            }
            
            if (presetIndex >= 0) {
                // It's a preset
                binding.spinnerSpeechTemplate.setSelection(presetIndex);
                binding.editCustomSpeechTemplate.setText(savedTemplate);
            } else {
                // It's a custom template
                binding.spinnerSpeechTemplate.setSelection(TEMPLATE_KEYS.length - 1); // Custom is last
                binding.editCustomSpeechTemplate.setText(savedTemplate);
            }
            
            binding.editCustomSpeechTemplate.setVisibility(View.VISIBLE);
            View customFormatContainer = (View) binding.editCustomSpeechTemplate.getParent().getParent();
            customFormatContainer.setVisibility(View.VISIBLE);
            updateSpeechPreview();
        }
    }
    
    /**
     * Get localized template value based on user's TTS language setting
     */
    private String getLocalizedTemplateValue(String templateKey) {
        if ("VARIED".equals(templateKey) || "CUSTOM".equals(templateKey)) {
            return templateKey;
        }
        
        // Get the user's TTS language setting
        android.content.SharedPreferences voiceSettingsPrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE);
        String ttsLanguageCode = voiceSettingsPrefs.getString("tts_language", "system");
        
        return TtsLanguageManager.getLocalizedTtsStringByName(this, ttsLanguageCode, templateKey);
    }
    
    private void saveSpeechTemplate(String template) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_SPEECH_TEMPLATE, template);
        editor.apply();
        InAppLogger.log("BehaviorSettings", "Speech template saved: " + template);
    }
    
    private void updateSpeechPreview() {
        String template = binding.editCustomSpeechTemplate.getText().toString();
        String preview = generateSpeechPreview(template);
        binding.textSpeechPreview.setText(Html.fromHtml("Preview: " + preview, Html.FROM_HTML_MODE_LEGACY));
    }
    
    private String generateSpeechPreview(String template) {
        // Replace placeholders with sample values and make them bold
        String preview = template
            .replace("{app}", "**Messages**")
            .replace("{package}", "**com.google.android.apps.messaging**")
            .replace("{content}", "**Mitchi: I heard you're using SpeakThat! Did it just speak that?**")
            .replace("{title}", "**Mitchi**")
            .replace("{text}", "**I heard you're using SpeakThat! Did it just speak that?**")
            .replace("{bigtext}", "**Mitchi: I heard you're using SpeakThat! Did it just speak that?**")
            .replace("{summary}", "**1 new message**")
            .replace("{info}", "**Tap to view**")
            .replace("{time}", "**14:30**")
            .replace("{date}", "**Dec 15**")
            .replace("{timestamp}", "**14:30 Dec 15**")
            .replace("{priority}", "**High**")
            .replace("{category}", "**Message**")
            .replace("{channel}", "**Messages**");
        
        // Convert markdown-style bold to HTML bold
        StringBuilder result = new StringBuilder();
        boolean inBold = false;
        for (int i = 0; i < preview.length(); i++) {
            if (i < preview.length() - 1 && preview.charAt(i) == '*' && preview.charAt(i + 1) == '*') {
                if (inBold) {
                    result.append("</b>");
                } else {
                    result.append("<b>");
                }
                inBold = !inBold;
                i++; // Skip the second asterisk
            } else {
                result.append(preview.charAt(i));
            }
        }
        return result.toString();
    }

    private void addPriorityApp() {
        String input = binding.editPriorityApp.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Please enter an app name or package name", Toast.LENGTH_SHORT).show();
            return;
        }

        // Try to match input to JSON apps as fallback for display names
        String packageNameToAdd = input;
        AppListData matched = findAppByNameOrPackage(input);
        if (matched != null) {
            packageNameToAdd = matched.packageName;
        }

        // Check for duplicates
        if (priorityAppsList.contains(packageNameToAdd)) {
            Toast.makeText(this, "App already in priority list", Toast.LENGTH_SHORT).show();
            return;
        }

        priorityAppsList.add(packageNameToAdd);
        priorityAppAdapter.notifyDataSetChanged();
        binding.editPriorityApp.setText("");
        
        savePriorityApps();
        
        // Show success message
        String displayName = matched != null ? matched.displayName : input;
        Toast.makeText(this, "Added " + displayName + " to priority list", Toast.LENGTH_SHORT).show();
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

    private void addCooldownApp() {
        String appName = binding.editCooldownApp.getText().toString().trim();
        Log.d("BehaviorSettings", "addCooldownApp called with: '" + appName + "'");
        
        if (appName.isEmpty()) {
            Log.d("BehaviorSettings", "App name is empty");
            Toast.makeText(this, "Please enter an app name", Toast.LENGTH_SHORT).show();
            return;
        }

        // First try to find the app in the device's installed apps
        PackageManager pm = getPackageManager();
        try {
            // Try to get app info directly from package manager
            ApplicationInfo appInfo = pm.getApplicationInfo(appName, 0);
            String displayName = pm.getApplicationLabel(appInfo).toString();
            
            // Create AppInfo object and add it
            AppInfo selectedApp = new AppInfo(displayName, appName, pm.getApplicationIcon(appInfo));
            addCooldownAppFromSelection(selectedApp);
            return;
            
        } catch (PackageManager.NameNotFoundException e) {
            // App not found in device, try searching in JSON app list
            Log.d("BehaviorSettings", "App not found in device, trying JSON app list");
        }

        // Find the app in the JSON app list
        AppListData appData = findAppByNameOrPackage(appName);
        if (appData == null) {
            Log.d("BehaviorSettings", "App not found for: '" + appName + "'");
            Toast.makeText(this, "App not found. Please check the app name or package.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("BehaviorSettings", "Found app: " + appData.displayName + " (" + appData.packageName + ")");

        // Check for duplicates
        for (CooldownAppAdapter.CooldownAppItem item : cooldownAppsList) {
            if (item.packageName.equals(appData.packageName)) {
                Log.d("BehaviorSettings", "App already in cooldown list: " + appData.packageName);
                Toast.makeText(this, "App already in cooldown list", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Add to cooldown list with default 1-second cooldown
        CooldownAppAdapter.CooldownAppItem item = new CooldownAppAdapter.CooldownAppItem(
            appData.packageName, 
            appData.displayName, 
            appData.iconSlug, 
            1 // Default 1-second cooldown
        );
        cooldownAppsList.add(item);
        cooldownAppAdapter.notifyDataSetChanged();
        binding.editCooldownApp.setText("");
        
        Log.d("BehaviorSettings", "Added app to cooldown list: " + appData.displayName);
        saveCooldownApps();
    }

    private void removeCooldownApp(int position) {
        if (position >= 0 && position < cooldownAppsList.size()) {
            cooldownAppsList.remove(position);
            cooldownAppAdapter.notifyDataSetChanged();
            saveCooldownApps();
        }
    }

    private void loadCooldownApps() {
        String cooldownAppsJson = sharedPreferences.getString(KEY_COOLDOWN_APPS, "[]");
        cooldownAppsList.clear();
        
        try {
            JSONArray jsonArray = new JSONArray(cooldownAppsJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String packageName = jsonObject.getString("packageName");
                String displayName = jsonObject.getString("displayName");
                String iconSlug = jsonObject.optString("iconSlug", "");
                boolean hasDeviceIcon = jsonObject.optBoolean("hasDeviceIcon", false);
                int cooldownSeconds = jsonObject.optInt("cooldownSeconds", 1);
                cooldownAppsList.add(new CooldownAppAdapter.CooldownAppItem(packageName, displayName, iconSlug, cooldownSeconds, hasDeviceIcon));
            }
        } catch (JSONException e) {
            Log.e("BehaviorSettings", "Error loading cooldown apps", e);
        }
    }

    private void saveCooldownApps() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (CooldownAppAdapter.CooldownAppItem item : cooldownAppsList) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("packageName", item.packageName);
                jsonObject.put("displayName", item.displayName);
                jsonObject.put("iconSlug", item.iconSlug != null ? item.iconSlug : "");
                jsonObject.put("hasDeviceIcon", item.icon != null); // Flag to indicate if it has a device icon
                jsonObject.put("cooldownSeconds", item.cooldownSeconds);
                jsonArray.put(jsonObject);
            }
            
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(KEY_COOLDOWN_APPS, jsonArray.toString());
            editor.apply();
            
            InAppLogger.log("BehaviorSettings", "Cooldown apps saved: " + cooldownAppsList.size() + " entries");
        } catch (JSONException e) {
            Log.e("BehaviorSettings", "Error saving cooldown apps", e);
        }
    }

    private AppListData findAppByNameOrPackage(String query) {
        // Load app list from JSON
        List<AppListData> appList = AppListManager.INSTANCE.loadAppList(this);
        if (appList == null) {
            Log.e("BehaviorSettings", "App list is null");
            return null;
        }
        
        String lowerQuery = query.toLowerCase().trim();
        Log.d("BehaviorSettings", "Searching for app: '" + query + "' (lowercase: '" + lowerQuery + "')");
        Log.d("BehaviorSettings", "App list size: " + appList.size());
        
        // First, try exact matches
        for (AppListData app : appList) {
            if (app.displayName.toLowerCase().equals(lowerQuery) ||
                app.packageName.toLowerCase().equals(lowerQuery)) {
                Log.d("BehaviorSettings", "Found exact match: " + app.displayName + " (" + app.packageName + ")");
                return app;
            }
        }
        
        // Then, try contains matches
        for (AppListData app : appList) {
            if (app.displayName.toLowerCase().contains(lowerQuery) ||
                app.packageName.toLowerCase().contains(lowerQuery) ||
                (app.aliases != null && app.aliases.stream().anyMatch(alias -> alias.toLowerCase().contains(lowerQuery)))) {
                Log.d("BehaviorSettings", "Found partial match: " + app.displayName + " (" + app.packageName + ")");
                return app;
            }
        }
        
        // Finally, try word boundary matches (more flexible)
        String[] queryWords = lowerQuery.split("\\s+");
        for (AppListData app : appList) {
            String displayNameLower = app.displayName.toLowerCase();
            String packageNameLower = app.packageName.toLowerCase();
            
            boolean matches = false;
            for (String word : queryWords) {
                if (displayNameLower.contains(word) || packageNameLower.contains(word) ||
                    (app.aliases != null && app.aliases.stream().anyMatch(alias -> alias.toLowerCase().contains(word)))) {
                    matches = true;
                    break;
                }
            }
            
            if (matches) {
                Log.d("BehaviorSettings", "Found word match: " + app.displayName + " (" + app.packageName + ")");
                return app;
            }
        }
        
        Log.d("BehaviorSettings", "No app found for query: '" + query + "'");
        return null;
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

    private void saveShakeTimeoutSeconds(int timeoutSeconds) {
        // Safety validation: ensure timeout is within valid range (0 or 5-300)
        int validatedTimeout = timeoutSeconds;
        if (timeoutSeconds < 0 || (timeoutSeconds > 0 && timeoutSeconds < 5) || timeoutSeconds > 300) {
            validatedTimeout = 30; // Reset to safe default
            Log.w("BehaviorSettings", "Invalid shake timeout value attempted ($timeoutSeconds), resetting to 30 seconds");
            InAppLogger.logWarning("BehaviorSettings", "Invalid shake timeout value attempted, resetting to 30 seconds");
            // Update the UI to reflect the corrected value
            if (binding != null) {
                binding.sliderShakeTimeout.setValue(validatedTimeout);
                updateShakeTimeoutDisplay(validatedTimeout);
                // Uncheck the disable switch if we're resetting to a valid value
                isProgrammaticallySettingSwitch = true;
                binding.switchShakeTimeoutDisabled.setChecked(false);
                isProgrammaticallySettingSwitch = false;
            }
        }
        
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_SHAKE_TIMEOUT_SECONDS, validatedTimeout);
        editor.apply();
        InAppLogger.log("BehaviorSettings", "Shake timeout changed to: " + validatedTimeout + " seconds");
    }

    private void saveWaveToStopEnabled(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_WAVE_TO_STOP_ENABLED, enabled);
        editor.apply();
    }

    private void saveWaveTimeoutSeconds(int timeoutSeconds) {
        // Safety validation: ensure timeout is within valid range (0 or 5-300)
        int validatedTimeout = timeoutSeconds;
        if (timeoutSeconds < 0 || (timeoutSeconds > 0 && timeoutSeconds < 5) || timeoutSeconds > 300) {
            validatedTimeout = 30; // Reset to safe default
            Log.w("BehaviorSettings", "Invalid wave timeout value attempted ($timeoutSeconds), resetting to 30 seconds");
            InAppLogger.logWarning("BehaviorSettings", "Invalid wave timeout value attempted, resetting to 30 seconds");
            // Update the UI to reflect the corrected value
            if (binding != null) {
                binding.sliderWaveTimeout.setValue(validatedTimeout);
                updateWaveTimeoutDisplay(validatedTimeout);
                // Uncheck the disable switch if we're resetting to a valid value
                isProgrammaticallySettingSwitch = true;
                binding.switchWaveTimeoutDisabled.setChecked(false);
                isProgrammaticallySettingSwitch = false;
            }
        }
        
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_WAVE_TIMEOUT_SECONDS, validatedTimeout);
        editor.apply();
        InAppLogger.log("BehaviorSettings", "Wave timeout changed to: " + validatedTimeout + " seconds");
    }

    private void savePocketModeEnabled(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_POCKET_MODE_ENABLED, enabled);
        editor.apply();
        InAppLogger.log("BehaviorSettings", "Pocket mode changed to: " + enabled);
    }

    private void saveWaveThreshold(float threshold) {
        // Smart validation: Prevent users from setting thresholds that would cause problems
        float validatedThreshold = validateWaveThreshold(threshold);
        
        if (validatedThreshold != threshold) {
            // Show warning to user about the adjustment
            String message = String.format("Threshold adjusted from %.1fcm to %.1fcm to prevent false triggers", 
                                         threshold, validatedThreshold);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            
            // Update the slider to reflect the corrected value
            binding.sliderWaveSensitivity.setValue(validatedThreshold);
            updateWaveThresholdMarker(validatedThreshold);
            updateWaveThresholdText(validatedThreshold);
        }
        
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(KEY_WAVE_THRESHOLD, validatedThreshold);
        editor.apply();
        
        Log.d("WaveTest", "Wave threshold saved: " + validatedThreshold + " cm (original: " + threshold + " cm)");
    }
    
    /**
     * Validates wave threshold to prevent false triggers
     * @param threshold The user-selected threshold
     * @return A validated threshold that won't cause problems
     */
    private float validateWaveThreshold(float threshold) {
        // Get the proximity sensor's maximum range
        float maxRange = 5.0f; // Default fallback
        if (proximitySensor != null) {
            maxRange = proximitySensor.getMaximumRange();
        }
        
        // If threshold is too close to max range, it will cause false triggers
        // Require at least 30% difference from max range
        float minSafeThreshold = maxRange * 0.7f;
        
        if (threshold > minSafeThreshold) {
            // Threshold is too high, adjust it down
            return Math.min(minSafeThreshold, 3.0f); // Cap at 3cm for safety
        }
        
        return threshold;
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

    private void saveHonourDoNotDisturb(boolean honour) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_HONOUR_DO_NOT_DISTURB, honour);
        editor.apply();
        InAppLogger.log("BehaviorSettings", "Honour Do Not Disturb changed to: " + honour);
    }

    private void saveHonourAudioMode(boolean honour) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("honour_audio_mode", honour);
        editor.apply();
        InAppLogger.log("BehaviorSettings", "Honour Audio Mode changed to: " + honour);
    }

    private void saveHonourPhoneCalls(boolean honour) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_HONOUR_PHONE_CALLS, honour);
        editor.apply();
        InAppLogger.log("BehaviorSettings", "Honour phone calls changed to: " + honour);
    }

    private void updateThresholdMarker(float threshold) {
        // Check if binding is null (activity might be destroyed)
        if (binding == null) {
            return;
        }
        
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
                    // Check if binding is still valid when callback executes
                    if (binding == null) {
                        return;
                    }
                    
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
        // Check if binding is null (activity might be destroyed)
        if (binding == null) {
            return;
        }
        
        int markerPosition = (int) (progressBarWidth * percentage);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) binding.thresholdMarker.getLayoutParams();
        params.leftMargin = Math.max(0, markerPosition - 1); // Center the 3dp wide marker, ensure >= 0
        binding.thresholdMarker.setLayoutParams(params);
    }

    private void updateThresholdText(float threshold) {
        // Check if binding is null (activity might be destroyed)
        if (binding == null) {
            return;
        }
        
        binding.textThreshold.setText(String.format("Threshold: %.1f", threshold));
    }

    private void updateWaveThresholdMarker(float threshold) {
        Log.d("WaveTest", "updateWaveThresholdMarker called with threshold: " + threshold + " cm");
        
        // Check if binding is null (activity might be destroyed)
        if (binding == null) {
            Log.d("WaveTest", "Binding is null, skipping marker update");
            return;
        }
        
        // Simplified marker update - similar to shake threshold marker
        int progressBarWidth = binding.progressWaveMeter.getWidth();
        Log.d("WaveTest", "Progress bar width: " + progressBarWidth);
        
        if (progressBarWidth > 0) {
            // For proximity: closer = higher percentage
            // Use the actual calibrated max distance instead of hardcoded 10.0f
            // Threshold is the distance where we trigger (closer than this = trigger)
            float maxRange = calibratedMaxDistance > 0 ? calibratedMaxDistance : 5.0f; // fallback to 5cm
            // Flip the calculation: threshold/maxRange gives us the correct direction
            float percentage = Math.max(0, Math.min(1.0f, threshold / maxRange));
            Log.d("WaveTest", "Updating marker - threshold: " + threshold + " cm, maxRange: " + maxRange + " cm, percentage: " + percentage + ", width: " + progressBarWidth);
            updateWaveMarkerPosition(percentage, progressBarWidth);
        } else {
            // Layout not ready yet, try again after a short delay
            Log.d("WaveTest", "Layout not ready, retrying in 100ms");
            uiHandler.postDelayed(() -> updateWaveThresholdMarker(threshold), 100);
        }
    }

    // Force update the marker when the wave settings section becomes visible
    private void forceUpdateWaveMarker() {
        // Check if binding is null (activity might be destroyed)
        if (binding == null) {
            Log.d("WaveTest", "Binding is null, skipping force marker update");
            return;
        }
        
        if (binding.waveSettingsSection.getVisibility() == View.VISIBLE) {
            float currentThresholdPercent = binding.sliderWaveSensitivity.getValue();
            float maxDistance = calibratedMaxDistance > 0 ? calibratedMaxDistance : 5.0f;
            float threshold = maxDistance * (currentThresholdPercent / 100f);
            Log.d("WaveTest", "Force updating marker with threshold percent: " + currentThresholdPercent + "%, calculated threshold: " + threshold + " cm");
            updateWaveThresholdMarker(threshold);
        }
    }

    private void updateWaveMarkerPosition(float percentage, int progressBarWidth) {
        // Check if binding is null (activity might be destroyed)
        if (binding == null) {
            Log.d("WaveTest", "Binding is null, skipping marker position update");
            return;
        }
        
        int marginStart = Math.round(percentage * progressBarWidth);
        
        // Since the marker is in a FrameLayout, we need to use FrameLayout.LayoutParams
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) binding.waveThresholdMarker.getLayoutParams();
        if (params == null) {
            // Create new params if they don't exist
            params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        
        params.leftMargin = marginStart;
        binding.waveThresholdMarker.setLayoutParams(params);
        Log.d("WaveTest", "Marker position updated - marginStart: " + marginStart + "px, percentage: " + percentage);
    }

    private void updateWaveThresholdText(float threshold) {
        // Check if binding is null (activity might be destroyed)
        if (binding == null) {
            Log.d("WaveTest", "Binding is null, skipping threshold text update");
            return;
        }
        
        float maxDistance = calibratedMaxDistance > 0 ? calibratedMaxDistance : 5.0f;
        String statusText = calibratedMaxDistance > 0 ? 
            String.format("Proximity Threshold: %.0f%% (%.2f cm of %.2f cm max)", thresholdPercent, threshold, maxDistance) :
            String.format("Proximity Threshold: %.0f%% (%.2f cm of %.2f cm max) - Not calibrated", thresholdPercent, threshold, maxDistance);
        binding.textWaveThreshold.setText(statusText);
    }
    
    private void updateWaveProgressBarMax() {
        // Check if binding is null (activity might be destroyed)
        if (binding == null) {
            Log.d("WaveTest", "Binding is null, skipping progress bar max update");
            return;
        }
        
        // Update the progress bar's max value to match the calibrated max distance
        // Round to nearest integer for cleaner display
        int maxValue = Math.round(calibratedMaxDistance > 0 ? calibratedMaxDistance : 5.0f);
        binding.progressWaveMeter.setMax(maxValue);
        Log.d("WaveTest", "Updated progress bar max value to: " + maxValue);
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

    private void startWaveTest() {
        if (proximitySensor != null) {
            isTestingWave = true;
            minWaveValue = calibratedMaxDistance > 0 ? calibratedMaxDistance : 5.0f;
            binding.btnWaveTest.setText("Stop Test");
            binding.progressWaveMeter.setProgress(0);
            binding.textCurrentWave.setText("No object detected");
            
            // Log sensor info for debugging
            Log.d("WaveTest", "Starting wave test with proximity sensor: " + proximitySensor.getName());
            Log.d("WaveTest", "Sensor max range: " + proximitySensor.getMaximumRange() + " cm");
            
            // Register sensor listener with high frequency for testing
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            Toast.makeText(this, "Proximity sensor not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopWaveTest() {
        if (isTestingWave) {
            isTestingWave = false;
            binding.btnWaveTest.setText("Start Test");
            sensorManager.unregisterListener(this);
            
            // Show test results
            if (minWaveValue == 0) {
                Toast.makeText(this, "Test complete: Object detected at 0 cm", Toast.LENGTH_SHORT).show();
            } else if (minWaveValue < 5.0f) {
                Toast.makeText(this, String.format("Test complete: Closest proximity: %.1f cm", minWaveValue), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Test complete: No objects detected within range", Toast.LENGTH_SHORT).show();
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
                // Check if binding is null (activity might be destroyed)
                if (binding == null) {
                    return;
                }
                
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
        } else if (event.sensor.getType() == Sensor.TYPE_PROXIMITY && isTestingWave) {
            // Proximity sensor returns distance in cm
            currentWaveValue = event.values[0];
            
            // Log the raw sensor value for debugging
            Log.d("WaveTest", "Proximity sensor value: " + currentWaveValue + " cm");
            
            // Handle different proximity sensor behaviors:
            // Some sensors return 0 when close, max when far
            // Others return actual distance values
            float displayValue = currentWaveValue;
            
            // Track minimum value during test (closest distance)
            if (currentWaveValue < minWaveValue) {
                minWaveValue = currentWaveValue;
            }
            
            // Update UI on main thread
            uiHandler.post(() -> {
                // Check if binding is null (activity might be destroyed)
                if (binding == null) {
                    return;
                }
                
                // Update progress bar (inverted: closer = higher progress)
                // Use calibrated max distance instead of hardcoded 10.0f
                float maxRange = calibratedMaxDistance > 0 ? calibratedMaxDistance : 5.0f; // fallback to 5cm
                float progressValue;
                if (currentWaveValue == 0) {
                    // Sensor returns 0 when close (most common)
                    progressValue = maxRange; // Full progress for calibrated range
                } else {
                    // Sensor returns distance, invert it for calibrated range
                    progressValue = Math.max(0, maxRange - currentWaveValue);
                }
                
                int progress = Math.round(progressValue);
                binding.progressWaveMeter.setProgress(progress);
                
                // Update current wave text with more info
                String statusText = currentWaveValue == 0 ? "Object detected (0 cm)" : 
                                   String.format("Distance: %.1f cm", currentWaveValue);
                binding.textCurrentWave.setText(statusText);
                
                // Check if threshold exceeded (closer than threshold)
                float threshold = calibratedMaxDistance * (thresholdPercent / 100f);
                boolean isTriggered = (currentWaveValue == 0) || (currentWaveValue <= threshold);
                
                if (isTriggered) {
                    binding.textCurrentWave.setTextColor(getColor(android.R.color.holo_green_dark));
                } else {
                    binding.textCurrentWave.setTextColor(getColor(android.R.color.secondary_text_dark));
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
        if (cooldownAppSelectorAdapter != null) {
            cooldownAppSelectorAdapter.shutdown();
        }
        
        super.onDestroy();
        
        // Clean up sensor listeners
        if (isTestingShake || isTestingWave) {
            sensorManager.unregisterListener(this);
        }
        
        // Remove any pending delayed operations
        uiHandler.removeCallbacksAndMessages(null);
        
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
        // Check if binding is null (activity might be destroyed)
        if (binding == null) {
            return;
        }
        
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

    private void showWaveToStopDialog() {
        // Track dialog usage for analytics
        trackDialogUsage("wave_to_stop_info");
        
        String htmlText = getString(R.string.wave_to_stop_dialog_message);
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(getString(R.string.wave_to_stop_dialog_title))
                .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton(R.string.use_recommended, (dialog, which) -> {
                    // Track recommendation usage
                    trackDialogUsage("wave_to_stop_recommended");
                    
                    // Enable wave to stop
                    binding.switchWaveToStop.setChecked(true);
                    binding.waveSettingsSection.setVisibility(View.VISIBLE);
                    saveWaveToStopEnabled(true);
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

    private void showCooldownDialog() {
        // Track dialog usage for analytics
        trackDialogUsage("cooldown_info");
        
        String htmlText = "Notification Cooldown prevents apps from having multiple notifications read within a specified time period:<br><br>" +
                "<b>🎯 Why use cooldown?</b><br>" +
                "Some apps send rapid-fire notifications that can be overwhelming. This feature helps manage notification spam by enforcing a \"quiet period\" between notifications from the same app:<br><br>" +
                "<b>📱 Perfect for:</b><br>" +
                "• <b>Chat apps</b> - WhatsApp, Telegram, Discord<br>" +
                "• <b>Social media</b> - Twitter, Instagram, Facebook<br>" +
                "• <b>Games</b> - Apps with frequent updates<br>" +
                "• <b>Any app</b> that sends rapid notifications<br><br>" +
                "<b>⚙️ How it works:</b><br>" +
                "1. Add an app to the cooldown list<br>" +
                "2. Set a cooldown time (e.g., 5 seconds)<br>" +
                "3. If the same app sends another notification within that time, it gets skipped<br>" +
                "4. After the cooldown period, new notifications are read normally<br><br>" +
                "<b>💡 Recommended settings:</b><br>" +
                "• <b>1-3 seconds</b> - For apps that send 2-3 notifications quickly<br>" +
                "• <b>5-10 seconds</b> - For chat apps with message bursts<br>" +
                "• <b>15-30 seconds</b> - For very spammy apps<br>" +
                "• <b>1-5 minutes</b> - For apps that send many notifications over time<br><br>" +
                "<b>Note:</b> Only notifications from the same app are affected. Different apps can still send notifications normally.";
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Notification Cooldown")
                .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton("Got it!", null)
                .show();
    }
    
    private void showSpeechTemplateDialog() {
        // Track dialog usage for analytics
        trackDialogUsage("speech_template_info");
        
        String htmlText = "Customize exactly how your notifications are spoken aloud using placeholders and formats.<br><br>" +
                
                "<b>🎯 What are Speech Formats?</b><br>" +
                "Speech formats let you control the exact format and wording of how notifications are read out. Instead of always hearing \"WhatsApp notified you: New message\", you can make it say whatever you prefer.<br><br>" +
                
                "<b>💡 Why customize?</b><br>" +
                "• <b>Personal preference</b> - Some like formal, others casual<br>" +
                "• <b>Clarity</b> - Make app names easier to understand<br>" +
                "• <b>Brevity</b> - Shorter formats for quick scanning<br>" +
                "• <b>Context</b> - Add time, priority, or other details<br>" +
                "• <b>Accessibility</b> - Format that works best for your needs<br><br>" +
                
                "<b>📝 Complete Placeholder Reference:</b><br><br>" +
                
                "<b>🔤 App Information:</b><br>" +
                "• <b>{app}</b> - App display name (automatically uses custom names and respects privacy settings)<br>" +
                "• <b>{package}</b> - Package name (e.g., \"com.google.android.apps.messaging\")<br><br>" +
                
                "<b>📄 Notification Content:</b><br>" +
                "• <b>{content}</b> - Full notification (title + text combined)<br>" +
                "• <b>{title}</b> - Notification title only (e.g., \"Mitchi\" for Messages)<br>" +
                "• <b>{text}</b> - Notification text only (e.g., \"I heard you're using SpeakThat!\" for Messages)<br>" +
                "• <b>{bigtext}</b> - Big text content (expanded notification)<br>" +
                "• <b>{summary}</b> - Summary text (e.g., \"1 new message\")<br>" +
                "• <b>{info}</b> - Info text (additional details)<br><br>" +
                
                "<b>⏰ Time & Date:</b><br>" +
                "• <b>{time}</b> - Current time in HH:mm format (e.g., \"14:30\")<br>" +
                "• <b>{date}</b> - Current date in MMM dd format (e.g., \"Dec 15\")<br>" +
                "• <b>{timestamp}</b> - Full timestamp (e.g., \"14:30 Dec 15\")<br><br>" +
                
                "<b>📊 Notification Metadata:</b><br>" +
                "• <b>{priority}</b> - Priority level (Min, Low, Default, High, Max)<br>" +
                "• <b>{category}</b> - Notification category (Message, Call, etc.)<br>" +
                "• <b>{channel}</b> - Notification channel ID<br><br>" +
                
                "<b>💡 What's the difference?</b><br>" +
                "• <b>{content} vs {title} + {text}</b> - {content} is everything, {title} and {text} are separate parts<br>" +
                "• <b>{info}</b> - Usually contains \"Tap to view\" or similar action text<br>" +
                "• <b>{app}</b> - Automatically uses custom names if set, and respects privacy settings<br><br>" +
                
                "<b>⚠️ Important Notes:</b><br>" +
                "• <b>Avoid {title} {bigtext}</b> - This can cause duplication since bigtext often includes the title<br>" +
                "• <b>Use {content}</b> for the full notification, or {title} + {text} for separate parts<br>" +
                "• <b>Test your format</b> with the Test button to see exactly how it will sound<br><br>" +
                
                "<b>💡 Format Examples:</b><br><br>" +
                
                "<b>Quick & Simple:</b><br>" +
                "• <b>Minimal:</b> \"{app}: {content}\" → \"Messages: Mitchi: I heard you're using SpeakThat!\"<br>" +
                "• <b>App Only:</b> \"{app}\" → \"Messages\"<br>" +
                "• <b>Content Only:</b> \"{content}\" → \"Mitchi: I heard you're using SpeakThat!\"<br><br>" +
                
                "<b>Informative:</b><br>" +
                "• <b>Default:</b> \"{app} notified you: {content}\" → \"Messages notified you: Mitchi: I heard you're using SpeakThat!\"<br>" +
                "• <b>Formal:</b> \"Notification from {app}: {content}\" → \"Notification from Gmail: New email from John Smith: Meeting tomorrow at 3 PM\"<br>" +
                "• <b>Casual:</b> \"{app} says: {content}\" → \"Weather says: Weather Alert: Heavy rain expected in 2 hours\"<br><br>" +
                
                "<b>Time-Aware:</b><br>" +
                "• <b>Time Stamp:</b> \"{app} at {time}: {content}\" → \"Twitter at 14:30: @mitchib1440: Just released a new app update!\"<br>" +
                "• <b>Full Context:</b> \"{app} ({time}): {content}\" → \"Messages (14:30): Mitchi: I heard you're using SpeakThat!\"<br><br>" +
                
                "<b>Advanced Examples:</b><br>" +
                "• <b>Priority Aware:</b> \"{app} ({priority}): {content}\" → \"Gmail (High): New email from John Smith: Meeting tomorrow at 3 PM\"<br>" +
                "• <b>Category Aware:</b> \"{category} from {app}: {content}\" → \"Message from Messages: Mitchi: I heard you're using SpeakThat!\"<br>" +
                "• <b>Sender Focused:</b> \"{title} via {app}: {text}\" → \"Mitchi via Messages: I heard you're using SpeakThat!\"<br>" +
                "• <b>Detailed:</b> \"{app} - {title}: {bigtext}\" → \"Gmail - New email from John Smith: Meeting tomorrow at 3 PM - Please bring the quarterly report\"<br><br>" +
                
                "<b>⚙️ How to Use:</b><br>" +
                "1. <b>Choose a preset</b> - Start with a format that's close to what you want<br>" +
                "2. <b>Customize</b> - Edit the format to add or remove elements<br>" +
                "3. <b>Preview</b> - See exactly how it will sound with the preview<br>" +
                "4. <b>Test</b> - Try it with real notifications<br>" +
                "5. <b>Refine</b> - Adjust based on what sounds best to you<br><br>" +
                
                "<b>🔧 Tips & Tricks:</b><br>" +
                "• <b>Mix and match</b> - Combine placeholders in any order<br>" +
                "• <b>Keep it concise</b> - Shorter formats are easier to understand quickly<br>" +
                "• <b>Use spacing</b> - Add spaces around placeholders for better pronunciation<br>" +
                "• <b>Test thoroughly</b> - Different apps may have different content formats<br>" +
                "• <b>Consider context</b> - Time-aware formats are great for busy periods<br><br>" +
                
                "<b>🎯 Recommended Starting Points:</b><br>" +
                "• <b>New users:</b> Start with \"Default\" or \"Minimal\"<br>" +
                "• <b>Power users:</b> Try \"Time Aware\" or custom formats<br>" +
                "• <b>Accessibility focus:</b> Use \"Formal\" or add priority information<br>" +
                "• <b>Quick scanning:</b> Use \"App Only\" or \"Content Only\"<br><br>" +
                
                "<b>💡 Real App Examples:</b><br><br>" +
                
                "<b>📱 Messages (Android):</b><br>" +
                "• <b>Title:</b> \"Mitchi\"<br>" +
                "• <b>Text:</b> \"I heard you're using SpeakThat! Did it just speak that?\"<br>" +
                "• <b>BigText:</b> \"I heard you're using SpeakThat! Did it just speak that?\"<br>" +
                "• <b>Content:</b> \"Mitchi: I heard you're using SpeakThat! Did it just speak that?\"<br><br>" +
                
                "<b>📧 Gmail:</b><br>" +
                "• <b>Title:</b> \"New email from John Smith\"<br>" +
                "• <b>Text:</b> \"Meeting tomorrow at 3 PM\"<br>" +
                "• <b>BigText:</b> \"Meeting tomorrow at 3 PM - Please bring the quarterly report and budget spreadsheet. We'll discuss Q4 projections.\"<br>" +
                "• <b>Content:</b> \"New email from John Smith: Meeting tomorrow at 3 PM\"<br><br>" +
                
                "<b>🌤️ Weather:</b><br>" +
                "• <b>Title:</b> \"Weather Alert\"<br>" +
                "• <b>Text:</b> \"Heavy rain expected in 2 hours\"<br>" +
                "• <b>BigText:</b> \"Heavy rain expected in 2 hours - Bring an umbrella and expect delays. Rainfall amounts of 1-2 inches possible.\"<br>" +
                "• <b>Content:</b> \"Weather Alert: Heavy rain expected in 2 hours\"<br><br>" +
                
                "<b>🐦 Twitter:</b><br>" +
                "• <b>Title:</b> \"@mitchib1440\"<br>" +
                "• <b>Text:</b> \"Just released a new app update! Check it out\"<br>" +
                "• <b>BigText:</b> \"Just released a new app update! Check it out - The new version includes dark mode and improved performance. #AndroidDev #AppUpdate\"<br>" +
                "• <b>Content:</b> \"@mitchib1440: Just released a new app update! Check it out\"<br><br>" +
                
                "<b>💡 Remember that different apps present their notifications in different ways.</b><br><br>";
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("🗣️ Speech Formats - Complete Guide")
                .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton("Use Recommended", (dialog, which) -> {
                    // Track recommendation usage
                    trackDialogUsage("speech_template_recommended");
                    
                    // Set to a good starting template (Default - matches current behavior)
                    binding.editCustomSpeechTemplate.setText("{app} notified you: {content}");
                    binding.spinnerSpeechTemplate.setSelection(0); // Select "Default"
                    updateSpeechPreview();
                    saveSpeechTemplate("{app} notified you: {content}");
                    
                    Toast.makeText(this, "Set to recommended format: Default", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Got it!", null)
                .show();
    }

    private void showDoNotDisturbDialog() {
        // Track dialog usage for analytics
        trackDialogUsage("do_not_disturb_info");
        
        String htmlText = "Honour Do Not Disturb mode respects your device's Do Not Disturb settings:<br><br>" +
                "<b>🎯 What it does:</b><br>" +
                "When your device is in Do Not Disturb mode, SpeakThat will not read any notifications aloud. This ensures complete silence when you need it most.<br><br>" +
                "<b>📱 When it's useful:</b><br>" +
                "• <b>Meetings and presentations</b> - No embarrassing interruptions<br>" +
                "• <b>Sleep time</b> - Respects your bedtime quiet hours<br>" +
                "• <b>Focus time</b> - When you need to concentrate without distractions<br>" +
                "• <b>Quiet environments</b> - Libraries, theaters, or public transport<br><br>" +
                "<b>⚙️ How it works:</b><br>" +
                "• Automatically detects when Do Not Disturb is enabled<br>" +
                "• Works with both manual and scheduled DND<br>" +
                "• Respects all DND modes (Alarms only, Priority only, etc.)<br>" +
                "• Notifications resume normally when DND is disabled<br><br>" +
                "<b>💡 Tip:</b> This feature works seamlessly with your device's existing Do Not Disturb settings. No additional configuration needed!";
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Honour Do Not Disturb")
                .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton(R.string.use_recommended, (dialog, which) -> {
                    // Track recommendation usage
                    trackDialogUsage("do_not_disturb_recommended");
                    
                    // Enable honour Do Not Disturb
                    binding.switchHonourDoNotDisturb.setChecked(true);
                    saveHonourDoNotDisturb(true);
                })
                .setNegativeButton(R.string.got_it, null)
                .show();
    }

    private void showAudioModeDialog() {
        // Track dialog usage for analytics
        trackDialogUsage("audio_mode_info");
        
        String htmlText = "Honour Audio Mode ensures notifications are only read when your device is in Sound mode:<br><br>" +
                "<b>🎯 What it does:</b><br>" +
                "When your device is in Silent or Vibrate mode, SpeakThat will not read any notifications aloud. This prevents unwanted audio when your phone is muted.<br><br>" +
                "<b>📱 When it's useful:</b><br>" +
                "• <b>Silent mode</b> - No audio interruptions when phone is completely muted<br>" +
                "• <b>Vibrate mode</b> - Respects your choice to only feel notifications<br>" +
                "• <b>Meetings</b> - No embarrassing audio when phone is on vibrate<br>" +
                "• <b>Quiet environments</b> - Ensures notifications only play when you can hear them<br><br>" +
                "<b>⚙️ How it works:</b><br>" +
                "• Automatically detects your device's audio mode<br>" +
                "• Only allows TTS when in Sound mode (RINGER_MODE_NORMAL)<br>" +
                "• Blocks TTS in Silent mode (RINGER_MODE_SILENT)<br>" +
                "• Blocks TTS in Vibrate mode (RINGER_MODE_VIBRATE)<br>" +
                "• Notifications resume normally when switched back to Sound mode<br><br>" +
                "<b>💡 Tip:</b> This feature works with your device's physical volume buttons and system settings. Perfect for users who frequently switch between audio modes!";
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Honour Audio Mode")
                .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton(R.string.use_recommended, (dialog, which) -> {
                    // Track recommendation usage
                    trackDialogUsage("audio_mode_recommended");
                    
                    // Enable honour audio mode
                    binding.switchHonourAudioMode.setChecked(true);
                    saveHonourAudioMode(true);
                })
                .setNegativeButton(R.string.got_it, null)
                .show();
    }

    private void showPhoneCallsDialog() {
        // Track dialog usage for analytics
        trackDialogUsage("phone_calls_info");
        
        String htmlText = "Honour Phone Calls prevents notification readouts when you're on a phone call:<br><br>" +
                "<b>🎯 What it does:</b><br>" +
                "When you're on a phone call, SpeakThat will not read any notifications aloud. This prevents interruptions during important conversations.<br><br>" +
                "<b>📱 When it's useful:</b><br>" +
                "• <b>Important calls</b> - No interruptions during business or personal calls<br>" +
                "• <b>Conference calls</b> - Maintains professional audio environment<br>" +
                "• <b>Voice calls</b> - Prevents notification audio from being heard by call participants<br>" +
                "• <b>Video calls</b> - Keeps your audio clean during video conversations<br><br>" +
                "<b>⚙️ How it works:</b><br>" +
                "• Automatically detects when you're on a phone call<br>" +
                "• Uses both AudioManager and TelephonyManager for reliable detection<br>" +
                "• Works with all types of calls (cellular, VoIP, video calls)<br>" +
                "• Notifications resume normally when call ends<br>" +
                "• Gracefully handles permission restrictions<br><br>" +
                "<b>💡 Tip:</b> This feature respects your conversation privacy and ensures you never miss important notifications due to call interruptions!";
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Honour Phone Calls")
                .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton(R.string.use_recommended, (dialog, which) -> {
                    // Track recommendation usage
                    trackDialogUsage("phone_calls_recommended");
                    
                    // Enable honour phone calls
                    binding.switchHonourPhoneCalls.setChecked(true);
                    saveHonourPhoneCalls(true);
                })
                .setNegativeButton(R.string.got_it, null)
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
    
    // Wave calibration methods
    private static final int REQUEST_WAVE_CALIBRATION = 1001;
    
    private boolean hasValidCalibrationData() {
        SharedPreferences prefs = getSharedPreferences("BehaviorSettings", MODE_PRIVATE);
        float threshold = prefs.getFloat("wave_threshold_v1", -1f);
        long timestamp = prefs.getLong("calibration_timestamp_v1", 0L);
        
        boolean hasValidData = threshold > 0f && timestamp > 0L;
        Log.d("WaveCalibration", "hasValidCalibrationData check - threshold: " + threshold + ", timestamp: " + timestamp + ", result: " + hasValidData);
        
        // Check if we have valid calibration data
        return hasValidData;
    }
    
    private void launchWaveCalibration() {
        try {
            Log.d("WaveCalibration", "launchWaveCalibration called - launching wave calibration activity");
            Intent intent = new Intent(this, WaveCalibrationActivity.class);
            startActivityForResult(intent, REQUEST_WAVE_CALIBRATION);
            Log.d("WaveCalibration", "Wave calibration activity launched successfully");
        } catch (Exception e) {
            Log.e("WaveCalibration", "Failed to launch wave calibration activity", e);
            Toast.makeText(this, "Failed to launch calibration: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        Log.d("WaveCalibration", "onActivityResult called - requestCode: " + requestCode + ", resultCode: " + resultCode);
        
        if (requestCode == REQUEST_WAVE_CALIBRATION) {
            Log.d("WaveCalibration", "Processing wave calibration result - resultCode: " + resultCode);
            
            if (resultCode == RESULT_OK) {
                Log.d("WaveCalibration", "Calibration successful - enabling wave-to-stop");
                
                // Set flag to prevent race condition
                isProcessingCalibrationResult = true;
                
                // Calibration successful - enable wave-to-stop
                binding.switchWaveToStop.setChecked(true);
                binding.waveSettingsSection.setVisibility(View.VISIBLE);
                saveWaveToStopEnabled(true);
                
                // Load and display the calibrated threshold
                loadWaveThresholdFromCalibration();
                
                // Clear the flag after a short delay to allow UI updates to complete
                uiHandler.postDelayed(() -> {
                    isProcessingCalibrationResult = false;
                    Log.d("WaveCalibration", "Calibration result processing completed");
                }, 500);
                
                Toast.makeText(this, "Wave detection calibrated successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Log.d("WaveCalibration", "Calibration cancelled or failed - resultCode: " + resultCode);
                
                // Calibration cancelled or failed
                Toast.makeText(this, "Wave detection setup cancelled", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void loadWaveThresholdFromCalibration() {
        Log.d("WaveCalibration", "loadWaveThresholdFromCalibration called");
        
        SharedPreferences prefs = getSharedPreferences("BehaviorSettings", MODE_PRIVATE);
        calibratedMaxDistance = prefs.getFloat("sensor_max_range_v1", -1f);
        thresholdPercent = prefs.getFloat("wave_threshold_percent", 60f);
        float threshold = (calibratedMaxDistance > 0) ? (calibratedMaxDistance * (thresholdPercent / 100f)) : 3.0f;

        Log.d("WaveCalibration", "Loaded calibration data - maxDistance: " + calibratedMaxDistance + ", thresholdPercent: " + thresholdPercent + ", calculated threshold: " + threshold);

        // Save the calculated threshold to the key that hasValidCalibrationData() checks
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("wave_threshold_v1", threshold);
        editor.apply();
        
        Log.d("WaveCalibration", "Saved calculated threshold: " + threshold + " to wave_threshold_v1");

        // Update the slider and UI
        binding.sliderWaveSensitivity.setValue(thresholdPercent);
        updateWaveProgressBarMax();
        updateWaveThresholdMarker(threshold);
        updateWaveThresholdText(threshold);
    }

    private void saveWaveThresholdPercent(float percent) {
        // Clamp percent to valid range (30-90), default to 60 if out of range
        float clamped = (percent < 30f || percent > 90f) ? 60f : Math.max(30f, Math.min(90f, percent));
        if (clamped != percent) {
            Toast.makeText(this, "Threshold percent adjusted to valid range (30-90%)", Toast.LENGTH_SHORT).show();
        }
        SharedPreferences.Editor editor = getSharedPreferences("BehaviorSettings", MODE_PRIVATE).edit();
        editor.putFloat("wave_threshold_percent", clamped);
        float threshold = (calibratedMaxDistance > 0) ? (calibratedMaxDistance * (clamped / 100f)) : 3.0f;
        editor.putFloat("wave_threshold_v1", threshold);
        editor.apply();
    }

    /**
     * Test method to debug app list functionality
     * Call this from onCreate or add a test button
     */
    private void testAppListFunctionality() {
        Log.d("BehaviorSettings", "=== Testing App List Functionality ===");
        
        // Test 1: Load app list
        List<AppListData> appList = AppListManager.INSTANCE.loadAppList(this);
        if (appList == null) {
            Log.e("BehaviorSettings", "TEST FAILED: App list is null");
            return;
        }
        
        Log.d("BehaviorSettings", "TEST PASSED: App list loaded with " + appList.size() + " apps");
        
        // Test 2: Search for common apps
        String[] testQueries = {"whatsapp", "facebook", "youtube", "gmail", "chrome"};
        
        for (String query : testQueries) {
            AppListData found = findAppByNameOrPackage(query);
            if (found != null) {
                Log.d("BehaviorSettings", "TEST PASSED: Found '" + query + "' -> " + found.displayName);
            } else {
                Log.e("BehaviorSettings", "TEST FAILED: Could not find '" + query + "'");
            }
        }
        
        // Test 3: Show first few apps for verification
        Log.d("BehaviorSettings", "First 5 apps in list:");
        for (int i = 0; i < Math.min(5, appList.size()); i++) {
            AppListData app = appList.get(i);
            Log.d("BehaviorSettings", "  " + (i+1) + ". " + app.displayName + " (" + app.packageName + ")");
        }
        
        Log.d("BehaviorSettings", "=== App List Test Complete ===");
    }

    private void addCooldownAppFromSelection(AppInfo selectedApp) {
        Log.d("BehaviorSettings", "Adding cooldown app from selection: " + selectedApp.appName + " (" + selectedApp.packageName + ")");
        
        // Check for duplicates
        for (CooldownAppAdapter.CooldownAppItem item : cooldownAppsList) {
            if (item.packageName.equals(selectedApp.packageName)) {
                Toast.makeText(this, "App already in cooldown list", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Add to cooldown list with default 1-second cooldown
        CooldownAppAdapter.CooldownAppItem item = new CooldownAppAdapter.CooldownAppItem(
            selectedApp.packageName, 
            selectedApp.appName, 
            selectedApp.icon, // Use the actual app icon
            1 // Default 1-second cooldown
        );
        cooldownAppsList.add(item);
        cooldownAppAdapter.notifyDataSetChanged();
        
        Log.d("BehaviorSettings", "Added app to cooldown list: " + selectedApp.appName);
        saveCooldownApps();
        
        Toast.makeText(this, "Added " + selectedApp.appName + " to cooldown list", Toast.LENGTH_SHORT).show();
    }

    private void addPriorityAppFromSelection(AppInfo selectedApp) {
        Log.d("BehaviorSettings", "Adding priority app from selection: " + selectedApp.appName + " (" + selectedApp.packageName + ")");
        
        // Check for duplicates
        if (priorityAppsList.contains(selectedApp.packageName)) {
            Toast.makeText(this, "App already in priority list", Toast.LENGTH_SHORT).show();
            return;
        }

        // Add to priority list
        priorityAppsList.add(selectedApp.packageName);
        priorityAppAdapter.notifyDataSetChanged();
        
        Log.d("BehaviorSettings", "Added app to priority list: " + selectedApp.appName);
        savePriorityApps();
        
        Toast.makeText(this, "Added " + selectedApp.appName + " to priority list", Toast.LENGTH_SHORT).show();
    }

    /**
     * Utility method to check if the device is currently in Do Not Disturb mode
     * This can be used by other parts of the app to respect the DND setting
     * @param context The application context
     * @return true if Do Not Disturb is enabled, false otherwise
     */
    public static boolean isDoNotDisturbEnabled(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            // Check for silent mode (traditional DND)
            if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
                return true;
            }
            
            // For Android 6.0+ (API 23+), also check for Do Not Disturb mode
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    android.app.NotificationManager notificationManager = 
                        (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (notificationManager != null) {
                        int currentInterruptionFilter = notificationManager.getCurrentInterruptionFilter();
                        // Check if DND is enabled (any mode except ALL)
                        return currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
                    }
                } catch (SecurityException e) {
                    // If we don't have permission to check DND status, fall back to ringer mode
                    Log.d("BehaviorSettings", "No permission to check DND status, using ringer mode fallback");
                }
            }
        }
        return false;
    }

    /**
     * Check if SpeakThat should honour Do Not Disturb mode
     * @param context The application context
     * @return true if DND should be honoured, false otherwise
     */
    public static boolean shouldHonourDoNotDisturb(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean honourDND = prefs.getBoolean(KEY_HONOUR_DO_NOT_DISTURB, DEFAULT_HONOUR_DO_NOT_DISTURB);
        
        if (honourDND) {
            return isDoNotDisturbEnabled(context);
        }
        return false;
    }

    /**
     * Check if the device is currently in Sound mode (not Silent or Vibrate)
     * This can be used to prevent TTS when the phone is muted
     * @param context The application context
     * @return true if device is in Sound mode, false if Silent or Vibrate
     */
    public static boolean isDeviceInSoundMode(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int ringerMode = audioManager.getRingerMode();
            // Only allow TTS when in RINGER_MODE_NORMAL (Sound mode)
            // RINGER_MODE_SILENT = 0, RINGER_MODE_VIBRATE = 1, RINGER_MODE_NORMAL = 2
            return ringerMode == AudioManager.RINGER_MODE_NORMAL;
        }
        return false;
    }

    /**
     * Check if SpeakThat should honour audio mode (only speak when in Sound mode)
     * @param context The application context
     * @return true if audio mode should be honoured, false otherwise
     */
    public static boolean shouldHonourAudioMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean honourAudioMode = prefs.getBoolean("honour_audio_mode", true); // Default to true for safety
        
        if (honourAudioMode) {
            return !isDeviceInSoundMode(context); // Return true if we should block (not in sound mode)
        }
        return false;
    }

    /**
     * Check if the device is currently in a phone call
     * @param context The application context
     * @return true if a phone call is active, false otherwise
     */
    public static boolean isPhoneCallActive(Context context) {
        try {
            // Check audio mode - if in call mode, a call is likely active
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int audioMode = audioManager.getMode();
                // AudioManager.MODE_IN_CALL indicates an active phone call
                if (audioMode == AudioManager.MODE_IN_CALL) {
                    return true;
                }
            }
            
            // Additional check using TelephonyManager for more reliable detection
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                // For Android 9+ (API 28+), we can use TelephonyManager
                android.telephony.TelephonyManager telephonyManager = 
                    (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (telephonyManager != null) {
                    int callState = telephonyManager.getCallState();
                    // TelephonyManager.CALL_STATE_OFFHOOK indicates an active call
                    return callState == android.telephony.TelephonyManager.CALL_STATE_OFFHOOK;
                }
            }
            
            return false;
        } catch (SecurityException e) {
            // If we don't have permission to check call state, fall back to audio mode only
            Log.d("BehaviorSettings", "No permission to check call state, using audio mode fallback");
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int audioMode = audioManager.getMode();
                return audioMode == AudioManager.MODE_IN_CALL;
            }
            return false;
        } catch (Exception e) {
            Log.e("BehaviorSettings", "Error checking phone call state", e);
            return false;
        }
    }

    /**
     * Check if SpeakThat should honour phone calls (prevent readouts during calls)
     * @param context The application context
     * @return true if phone calls should be honoured, false otherwise
     */
    public static boolean shouldHonourPhoneCalls(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean honourPhoneCalls = prefs.getBoolean(KEY_HONOUR_PHONE_CALLS, DEFAULT_HONOUR_PHONE_CALLS);
        
        if (honourPhoneCalls) {
            return isPhoneCallActive(context);
        }
        return false;
    }

    // Timeout display methods
    private void updateShakeTimeoutDisplay(int timeoutSeconds) {
        if (binding != null) {
            String displayText = timeoutSeconds == 0 ? "Timeout: Disabled" : 
                               timeoutSeconds == 1 ? "Timeout: 1 second" :
                               "Timeout: " + timeoutSeconds + " seconds";
            binding.textShakeTimeout.setText(displayText);
        }
    }

    private void updateWaveTimeoutDisplay(int timeoutSeconds) {
        if (binding != null) {
            String displayText = timeoutSeconds == 0 ? "Timeout: Disabled" : 
                               timeoutSeconds == 1 ? "Timeout: 1 second" :
                               "Timeout: " + timeoutSeconds + " seconds";
            binding.textWaveTimeout.setText(displayText);
        }
    }

    // Warning dialog methods
    private void showTimeoutDisableWarning(String type) {
        String title = type.equals("shake") ? "Disable Shake Timeout?" : "Disable Wave Timeout?";
        String message = "⚠️ **WARNING** ⚠️\n\n" +
                        "Disabling the timeout could be really bad for your battery if TTS fails to terminate!\n\n" +
                        "I **strongly** recommend you set the timer to 5 minutes instead.\n\n" +
                        "Are you sure you want to disable the timeout?";

        new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(Html.fromHtml(message, Html.FROM_HTML_MODE_COMPACT))
            .setPositiveButton("Nevermind, keep the timeout enabled (Recommended)", (dialog, which) -> {
                // User chose to keep timeout enabled - do nothing
            })
            .setNegativeButton("Disable the timeout", (dialog, which) -> {
                // User confirmed - disable timeout
                if (type.equals("shake")) {
                    isProgrammaticallySettingSwitch = true;
                    binding.switchShakeTimeoutDisabled.setChecked(true);
                    isProgrammaticallySettingSwitch = false;
                    // Don't set slider to 0 - keep it at current value but save 0
                    saveShakeTimeoutSeconds(0);
                    updateShakeTimeoutDisplay(0);
                } else {
                    isProgrammaticallySettingSwitch = true;
                    binding.switchWaveTimeoutDisabled.setChecked(true);
                    isProgrammaticallySettingSwitch = false;
                    // Don't set slider to 0 - keep it at current value but save 0
                    saveWaveTimeoutSeconds(0);
                    updateWaveTimeoutDisplay(0);
                }
            })
            .show();
    }

    private void showTimeoutInfoDialog(String type) {
        String title = type.equals("shake") ? "Shake Timeout Info" : "Wave Timeout Info";
        String message = "**Timeout Settings**\n\n" +
                        "The timeout automatically stops listening for gestures after a set time to save battery.\n\n" +
                        "• **5-30 seconds**: Good for battery life\n" +
                        "• **30-120 seconds**: Balanced approach\n" +
                        "• **120+ seconds**: Uses more battery\n" +
                        "• **Disabled**: No battery protection (not recommended)\n\n" +
                        "**Recommendation**: Start with 30 seconds and adjust based on your needs.";

        new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(Html.fromHtml(message, Html.FROM_HTML_MODE_COMPACT))
            .setPositiveButton("Got it", null)
            .show();
    }
} 