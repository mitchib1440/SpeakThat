package com.micoyc.speakthat.settings.sections;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.micoyc.speakthat.InAppLogger;
import com.micoyc.speakthat.R;
import com.micoyc.speakthat.WaveCalibrationActivity;
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import com.micoyc.speakthat.settings.BehaviorSettingsSection;
import com.micoyc.speakthat.settings.BehaviorSettingsStore;
import com.micoyc.speakthat.settings.managers.ShakeSensorManager;
import com.micoyc.speakthat.settings.managers.WaveSensorManager;
import android.text.Html;

public class GestureSection implements BehaviorSettingsSection {
    private static final int REQUEST_WAVE_CALIBRATION = 1001;

    private final AppCompatActivity activity;
    private final ActivityBehaviorSettingsBinding binding;
    private final BehaviorSettingsStore store;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final ShakeSensorManager shakeSensorManager;
    private final WaveSensorManager waveSensorManager;

    private float calibratedMaxDistance = -1f;
    private float thresholdPercent = 60f;
    private boolean isProcessingCalibrationResult = false;
    private boolean isProgrammaticallySettingSwitch = false;

    public GestureSection(
        AppCompatActivity activity,
        ActivityBehaviorSettingsBinding binding,
        BehaviorSettingsStore store
    ) {
        this.activity = activity;
        this.binding = binding;
        this.store = store;
        shakeSensorManager = new ShakeSensorManager(activity, (current, max) -> {
            int progress = Math.round(Math.min(current, 25f));
            binding.progressShakeMeter.setProgress(progress);
            binding.textCurrentShake.setText(String.format("Current shake: %.1f", current));

            float threshold = binding.sliderShakeIntensity.getValue();
            if (current >= threshold) {
                binding.textCurrentShake.setTextColor(activity.getColor(android.R.color.holo_green_dark));
            } else {
                binding.textCurrentShake.setTextColor(activity.getColor(android.R.color.secondary_text_dark));
            }
        });

        waveSensorManager = new WaveSensorManager(activity, (distance, minDistance) -> {
            float maxRange = calibratedMaxDistance > 0 ? calibratedMaxDistance : 5.0f;
            float progressValue;
            if (distance == 0) {
                progressValue = maxRange;
            } else {
                progressValue = Math.max(0, maxRange - distance);
            }

            int progress = Math.round(progressValue);
            binding.progressWaveMeter.setProgress(progress);

            if (distance == 0) {
                binding.textCurrentWave.setText("Object detected (0 cm)");
            } else {
                binding.textCurrentWave.setText(String.format("Distance: %.1f cm", distance));
            }

            float threshold = calibratedMaxDistance * (thresholdPercent / 100f);
            boolean isTriggered = (distance == 0) || (distance <= threshold);

            if (isTriggered) {
                binding.textCurrentWave.setTextColor(activity.getColor(android.R.color.white));
            } else {
                binding.textCurrentWave.setTextColor(activity.getColor(android.R.color.secondary_text_dark));
            }
        });
    }

    @Override
    public void bind() {
        binding.btnShakeToStopInfo.setOnClickListener(v -> showShakeToStopDialog());
        binding.btnWaveToStopInfo.setOnClickListener(v -> showWaveToStopDialog());
        binding.btnPressToStopInfo.setOnClickListener(v -> showPressToStopDialog());

        binding.switchShakeToStop.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.shakeSettingsSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            saveShakeToStopEnabled(isChecked);

            if (!isChecked && shakeSensorManager.isTesting()) {
                stopShakeTest();
            }
        });

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

        binding.btnShakeTest.setOnClickListener(v -> {
            if (shakeSensorManager.isTesting()) {
                stopShakeTest();
            } else {
                startShakeTest();
            }
        });

        binding.switchWaveToStop.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.d("WaveCalibration", "Switch toggle triggered - isChecked: " + isChecked +
                ", isProcessingCalibrationResult: " + isProcessingCalibrationResult);

            if (isProcessingCalibrationResult) {
                Log.d("WaveCalibration", "Skipping switch processing - currently handling calibration result");
                return;
            }

            if (isChecked) {
                if (!hasValidCalibrationData()) {
                    Log.d("WaveCalibration", "No valid calibration data - launching calibration");
                    launchWaveCalibration();
                    buttonView.setChecked(false);
                    return;
                }
            }

            Log.d("WaveCalibration", "Processing normal switch toggle - isChecked: " + isChecked);
            binding.waveSettingsSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            saveWaveToStopEnabled(isChecked);

            if (isChecked) {
                uiHandler.postDelayed(this::forceUpdateWaveMarker, 200);
            }

            if (!isChecked && waveSensorManager.isTesting()) {
                stopWaveTest();
            }
        });

        binding.sliderWaveSensitivity.setValueFrom(30f);
        binding.sliderWaveSensitivity.setValueTo(90f);
        binding.sliderWaveSensitivity.setStepSize(1f);
        binding.sliderWaveSensitivity.setLabelFormatter(value -> String.format("%.0f%%", value));
        binding.textWaveThreshold.setText("Proximity Threshold: 60% (3.00 cm of 5.00 cm max) - Not calibrated");

        binding.sliderWaveSensitivity.addOnChangeListener((slider, value, fromUser) -> {
            Log.d("WaveTest", "Slider changed - value: " + value + ", fromUser: " + fromUser);
            thresholdPercent = value;

            float maxDistance = calibratedMaxDistance > 0 ? calibratedMaxDistance : 5.0f;
            float threshold = maxDistance * (thresholdPercent / 100f);

            Log.d("WaveTest", "Calculated threshold: " + threshold + " cm from " + maxDistance + " cm max");

            saveWaveThresholdPercent(thresholdPercent);
            updateWaveThresholdMarker(threshold);
            updateWaveThresholdText(threshold);
        });

        binding.btnWaveTest.setOnClickListener(v -> {
            if (waveSensorManager.isTesting()) {
                stopWaveTest();
            } else {
                startWaveTest();
            }
        });

        binding.btnRecalibrateWave.setOnClickListener(v -> launchWaveCalibration());

        binding.switchPressToStop.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                boolean hasPermission = isAccessibilityServiceEnabled();
                if (!hasPermission) {
                    showAccessibilityPermissionRequiredDialog();
                    buttonView.setChecked(false);
                    return;
                }
            }
            savePressToStopEnabled(isChecked);
        });

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

        binding.switchShakeTimeoutDisabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isProgrammaticallySettingSwitch) {
                return;
            }

            if (isChecked) {
                showTimeoutDisableWarning("shake");
                buttonView.setChecked(false);
            } else {
                int currentSliderValue = (int) binding.sliderShakeTimeout.getValue();
                saveShakeTimeoutSeconds(currentSliderValue);
                updateShakeTimeoutDisplay(currentSliderValue);
            }
        });

        binding.btnShakeTimeoutInfo.setOnClickListener(v -> showTimeoutInfoDialog("shake"));

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

        binding.switchWaveTimeoutDisabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isProgrammaticallySettingSwitch) {
                return;
            }

            if (isChecked) {
                showTimeoutDisableWarning("wave");
                buttonView.setChecked(false);
            } else {
                int currentSliderValue = (int) binding.sliderWaveTimeout.getValue();
                saveWaveTimeoutSeconds(currentSliderValue);
                updateWaveTimeoutDisplay(currentSliderValue);
            }
        });

        binding.btnWaveTimeoutInfo.setOnClickListener(v -> showTimeoutInfoDialog("wave"));

        binding.switchPocketMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            savePocketModeEnabled(isChecked);
        });
    }

    @Override
    public void load() {
        boolean shakeEnabled = store.prefs().getBoolean(BehaviorSettingsStore.KEY_SHAKE_TO_STOP_ENABLED, true);
        binding.switchShakeToStop.setChecked(shakeEnabled);
        binding.shakeSettingsSection.setVisibility(shakeEnabled ? View.VISIBLE : View.GONE);

        float shakeThreshold = store.prefs().getFloat(BehaviorSettingsStore.KEY_SHAKE_THRESHOLD, 12.0f);
        binding.sliderShakeIntensity.setValue(shakeThreshold);
        updateThresholdMarker(shakeThreshold);
        updateThresholdText(shakeThreshold);

        int shakeTimeoutSeconds = store.prefs().getInt(BehaviorSettingsStore.KEY_SHAKE_TIMEOUT_SECONDS, 30);
        if (shakeTimeoutSeconds == 0) {
            binding.sliderShakeTimeout.setValue(30);
            isProgrammaticallySettingSwitch = true;
            binding.switchShakeTimeoutDisabled.setChecked(true);
            isProgrammaticallySettingSwitch = false;
        } else {
            binding.sliderShakeTimeout.setValue(shakeTimeoutSeconds);
            isProgrammaticallySettingSwitch = true;
            binding.switchShakeTimeoutDisabled.setChecked(false);
            isProgrammaticallySettingSwitch = false;
        }
        updateShakeTimeoutDisplay(shakeTimeoutSeconds);

        boolean waveEnabled = store.prefs().getBoolean(BehaviorSettingsStore.KEY_WAVE_TO_STOP_ENABLED, false);
        Log.d("WaveCalibration", "Loading wave settings - waveEnabled: " + waveEnabled);
        binding.switchWaveToStop.setChecked(waveEnabled);
        binding.waveSettingsSection.setVisibility(waveEnabled ? View.VISIBLE : View.GONE);

        float percent = store.behaviorPrefs().getFloat(BehaviorSettingsStore.KEY_WAVE_THRESHOLD_PERCENT, 60f);
        if (percent < 30f || percent > 90f) {
            float clamped = (percent < 30f || percent > 90f) ? 60f : Math.max(30f, Math.min(90f, percent));
            Log.d("WaveTest", "Clamping invalid threshold percent: " + percent + " -> " + clamped);
            percent = clamped;
            saveWaveThresholdPercent(percent);
        }
        thresholdPercent = percent;
        calibratedMaxDistance = store.behaviorPrefs().getFloat(BehaviorSettingsStore.KEY_SENSOR_MAX_RANGE, -1f);
        float threshold = (calibratedMaxDistance > 0) ?
            (calibratedMaxDistance * (thresholdPercent / 100f)) : 3.0f;

        binding.sliderWaveSensitivity.setEnabled(true);
        binding.sliderWaveSensitivity.setValue(thresholdPercent);

        updateWaveProgressBarMax();
        updateWaveThresholdMarker(threshold);
        updateWaveThresholdText(threshold);

        int waveTimeoutSeconds = store.prefs().getInt(BehaviorSettingsStore.KEY_WAVE_TIMEOUT_SECONDS, 30);
        if (waveTimeoutSeconds == 0) {
            binding.sliderWaveTimeout.setValue(30);
            isProgrammaticallySettingSwitch = true;
            binding.switchWaveTimeoutDisabled.setChecked(true);
            isProgrammaticallySettingSwitch = false;
        } else {
            binding.sliderWaveTimeout.setValue(waveTimeoutSeconds);
            isProgrammaticallySettingSwitch = true;
            binding.switchWaveTimeoutDisabled.setChecked(false);
            isProgrammaticallySettingSwitch = false;
        }
        updateWaveTimeoutDisplay(waveTimeoutSeconds);

        boolean pressEnabled = store.prefs().getBoolean(BehaviorSettingsStore.KEY_PRESS_TO_STOP_ENABLED, false);
        boolean hasAccessibilityPermission = isAccessibilityServiceEnabled();

        if (!hasAccessibilityPermission && pressEnabled) {
            store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_PRESS_TO_STOP_ENABLED, false).apply();
            pressEnabled = false;
        }

        binding.switchPressToStop.setChecked(pressEnabled);
        binding.switchPressToStop.setEnabled(hasAccessibilityPermission);

        boolean pocketModeEnabled = store.prefs().getBoolean(BehaviorSettingsStore.KEY_POCKET_MODE_ENABLED, false);
        binding.switchPocketMode.setChecked(pocketModeEnabled);
    }

    @Override
    public void release() {
        shakeSensorManager.stop();
        waveSensorManager.stop();
        uiHandler.removeCallbacksAndMessages(null);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("WaveCalibration", "onActivityResult called - requestCode: " + requestCode +
            ", resultCode: " + resultCode);

        if (requestCode == REQUEST_WAVE_CALIBRATION) {
            Log.d("WaveCalibration", "Processing wave calibration result - resultCode: " + resultCode);

            if (resultCode == android.app.Activity.RESULT_OK) {
                Log.d("WaveCalibration", "Calibration successful - enabling wave-to-stop");

                isProcessingCalibrationResult = true;

                binding.switchWaveToStop.setChecked(true);
                binding.waveSettingsSection.setVisibility(View.VISIBLE);
                saveWaveToStopEnabled(true);

                loadWaveThresholdFromCalibration();

                uiHandler.postDelayed(() -> {
                    isProcessingCalibrationResult = false;
                    Log.d("WaveCalibration", "Calibration result processing completed");
                }, 500);

                Toast.makeText(activity, "Wave detection calibrated successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Log.d("WaveCalibration", "Calibration cancelled or failed - resultCode: " + resultCode);
                Toast.makeText(activity, "Wave detection setup cancelled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startShakeTest() {
        if (shakeSensorManager.start()) {
            binding.btnShakeTest.setText("Stop Test");
            binding.progressShakeMeter.setProgress(0);
            binding.textCurrentShake.setText("Current shake: 0.0");
        } else {
            Toast.makeText(activity, "Accelerometer not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopShakeTest() {
        if (shakeSensorManager.isTesting()) {
            shakeSensorManager.stop();
            binding.btnShakeTest.setText("Start Test");
            float maxShakeValue = shakeSensorManager.getMaxShakeValue();
            if (maxShakeValue > 0) {
                Toast.makeText(
                    activity,
                    String.format("Peak shake: %.1f", maxShakeValue),
                    Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    private void startWaveTest() {
        waveSensorManager.resetMinDistance(calibratedMaxDistance > 0 ? calibratedMaxDistance : 5.0f);
        if (waveSensorManager.start()) {
            binding.btnWaveTest.setText("Stop Test");
            binding.progressWaveMeter.setProgress(0);
            binding.textCurrentWave.setText("No object detected");

            Log.d("WaveTest", "Starting wave test with proximity sensor: " + waveSensorManager.getSensorName());
            Log.d("WaveTest", "Sensor max range: " + waveSensorManager.getSensorMaximumRange() + " cm");
        } else {
            Toast.makeText(activity, "Proximity sensor not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopWaveTest() {
        if (waveSensorManager.isTesting()) {
            waveSensorManager.stop();
            binding.btnWaveTest.setText("Start Test");
            float minWaveValue = waveSensorManager.getMinWaveValue();
            if (minWaveValue == 0) {
                Toast.makeText(activity, "Test complete: Object detected at 0 cm", Toast.LENGTH_SHORT).show();
            } else if (minWaveValue < 5.0f) {
                Toast.makeText(
                    activity,
                    String.format("Test complete: Closest proximity: %.1f cm", minWaveValue),
                    Toast.LENGTH_SHORT
                ).show();
            } else {
                Toast.makeText(activity, "Test complete: No objects detected within range", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateThresholdMarker(float threshold) {
        float percentage = (threshold - 5f) / 20f;

        int progressBarWidth = binding.progressShakeMeter.getWidth();
        if (progressBarWidth > 0) {
            updateMarkerPosition(percentage, progressBarWidth);
        } else {
            binding.progressShakeMeter.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        binding.progressShakeMeter.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        int width = binding.progressShakeMeter.getWidth();
                        if (width > 0) {
                            updateMarkerPosition(percentage, width);
                        }
                    }
                }
            );
        }
    }

    private void updateMarkerPosition(float percentage, int progressBarWidth) {
        int markerPosition = (int) (progressBarWidth * percentage);
        ViewGroup.MarginLayoutParams params =
            (ViewGroup.MarginLayoutParams) binding.thresholdMarker.getLayoutParams();
        params.leftMargin = Math.max(0, markerPosition - 1);
        binding.thresholdMarker.setLayoutParams(params);
    }

    private void updateThresholdText(float threshold) {
        binding.textThreshold.setText(String.format("Threshold: %.1f", threshold));
    }

    private void updateWaveThresholdMarker(float threshold) {
        Log.d("WaveTest", "updateWaveThresholdMarker called with threshold: " + threshold + " cm");

        int progressBarWidth = binding.progressWaveMeter.getWidth();
        Log.d("WaveTest", "Progress bar width: " + progressBarWidth);

        if (progressBarWidth > 0) {
            float maxRange = calibratedMaxDistance > 0 ? calibratedMaxDistance : 5.0f;
            float percentage = Math.max(0, Math.min(1.0f, threshold / maxRange));
            Log.d("WaveTest", "Updating marker - threshold: " + threshold + " cm, maxRange: " +
                maxRange + " cm, percentage: " + percentage + ", width: " + progressBarWidth);
            updateWaveMarkerPosition(percentage, progressBarWidth);
        } else {
            Log.d("WaveTest", "Layout not ready, retrying in 100ms");
            uiHandler.postDelayed(() -> updateWaveThresholdMarker(threshold), 100);
        }
    }

    private void forceUpdateWaveMarker() {
        if (binding.waveSettingsSection.getVisibility() == View.VISIBLE) {
            float currentThresholdPercent = binding.sliderWaveSensitivity.getValue();
            float maxDistance = calibratedMaxDistance > 0 ? calibratedMaxDistance : 5.0f;
            float threshold = maxDistance * (currentThresholdPercent / 100f);
            Log.d("WaveTest", "Force updating marker with threshold percent: " + currentThresholdPercent +
                "%, calculated threshold: " + threshold + " cm");
            updateWaveThresholdMarker(threshold);
        }
    }

    private void updateWaveMarkerPosition(float percentage, int progressBarWidth) {
        int marginStart = Math.round(percentage * progressBarWidth);

        FrameLayout.LayoutParams params =
            (FrameLayout.LayoutParams) binding.waveThresholdMarker.getLayoutParams();
        if (params == null) {
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
        float maxDistance = calibratedMaxDistance > 0 ? calibratedMaxDistance : 5.0f;
        String statusText = calibratedMaxDistance > 0
            ? String.format(
                "Proximity Threshold: %.0f%% (%.2f cm of %.2f cm max)",
                thresholdPercent,
                threshold,
                maxDistance
            )
            : String.format(
                "Proximity Threshold: %.0f%% (%.2f cm of %.2f cm max) - Not calibrated",
                thresholdPercent,
                threshold,
                maxDistance
            );
        binding.textWaveThreshold.setText(statusText);
    }

    private void updateWaveProgressBarMax() {
        int maxValue = Math.round(calibratedMaxDistance > 0 ? calibratedMaxDistance : 5.0f);
        binding.progressWaveMeter.setMax(maxValue);
        Log.d("WaveTest", "Updated progress bar max value to: " + maxValue);
    }

    private void saveShakeToStopEnabled(boolean enabled) {
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_SHAKE_TO_STOP_ENABLED, enabled).apply();
    }

    private void saveShakeThreshold(float threshold) {
        store.prefs().edit().putFloat(BehaviorSettingsStore.KEY_SHAKE_THRESHOLD, threshold).apply();
    }

    private void saveShakeTimeoutSeconds(int timeoutSeconds) {
        int validatedTimeout = timeoutSeconds;
        if (timeoutSeconds < 0 || (timeoutSeconds > 0 && timeoutSeconds < 5) || timeoutSeconds > 300) {
            validatedTimeout = 30;
            Log.w("BehaviorSettings", "Invalid shake timeout value attempted (" + timeoutSeconds + "), resetting to 30 seconds");
            InAppLogger.logWarning("BehaviorSettings", "Invalid shake timeout value attempted, resetting to 30 seconds");
            binding.sliderShakeTimeout.setValue(validatedTimeout);
            updateShakeTimeoutDisplay(validatedTimeout);
            isProgrammaticallySettingSwitch = true;
            binding.switchShakeTimeoutDisabled.setChecked(false);
            isProgrammaticallySettingSwitch = false;
        }

        store.prefs().edit().putInt(BehaviorSettingsStore.KEY_SHAKE_TIMEOUT_SECONDS, validatedTimeout).apply();
        InAppLogger.log("BehaviorSettings", "Shake timeout changed to: " + validatedTimeout + " seconds");
    }

    private void saveWaveToStopEnabled(boolean enabled) {
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_WAVE_TO_STOP_ENABLED, enabled).apply();
    }

    private void savePressToStopEnabled(boolean enabled) {
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_PRESS_TO_STOP_ENABLED, enabled).apply();
    }

    private void saveWaveTimeoutSeconds(int timeoutSeconds) {
        int validatedTimeout = timeoutSeconds;
        if (timeoutSeconds < 0 || (timeoutSeconds > 0 && timeoutSeconds < 5) || timeoutSeconds > 300) {
            validatedTimeout = 30;
            Log.w("BehaviorSettings", "Invalid wave timeout value attempted (" + timeoutSeconds + "), resetting to 30 seconds");
            InAppLogger.logWarning("BehaviorSettings", "Invalid wave timeout value attempted, resetting to 30 seconds");
            binding.sliderWaveTimeout.setValue(validatedTimeout);
            updateWaveTimeoutDisplay(validatedTimeout);
            isProgrammaticallySettingSwitch = true;
            binding.switchWaveTimeoutDisabled.setChecked(false);
            isProgrammaticallySettingSwitch = false;
        }

        store.prefs().edit().putInt(BehaviorSettingsStore.KEY_WAVE_TIMEOUT_SECONDS, validatedTimeout).apply();
        InAppLogger.log("BehaviorSettings", "Wave timeout changed to: " + validatedTimeout + " seconds");
    }

    private void savePocketModeEnabled(boolean enabled) {
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_POCKET_MODE_ENABLED, enabled).apply();
        InAppLogger.log("BehaviorSettings", "Pocket mode changed to: " + enabled);
    }

    private void saveWaveThreshold(float threshold) {
        float validatedThreshold = validateWaveThreshold(threshold);

        if (validatedThreshold != threshold) {
            Toast.makeText(
                activity,
                String.format(
                    "Threshold adjusted from %.1fcm to %.1fcm to prevent false triggers",
                    threshold,
                    validatedThreshold
                ),
                Toast.LENGTH_LONG
            ).show();

            binding.sliderWaveSensitivity.setValue(validatedThreshold);
            updateWaveThresholdMarker(validatedThreshold);
            updateWaveThresholdText(validatedThreshold);
        }

        store.prefs().edit().putFloat(BehaviorSettingsStore.KEY_WAVE_THRESHOLD, validatedThreshold).apply();
        Log.d("WaveTest", "Wave threshold saved: " + validatedThreshold + " cm (original: " + threshold + " cm)");
    }

    private float validateWaveThreshold(float threshold) {
        float maxRange = 5.0f;
        float sensorMax = waveSensorManager.getSensorMaximumRange();
        if (sensorMax > 0) {
            maxRange = sensorMax;
        }

        float minSafeThreshold = maxRange * 0.7f;

        if (threshold > minSafeThreshold) {
            return Math.min(minSafeThreshold, 3.0f);
        }

        return threshold;
    }

    private boolean hasValidCalibrationData() {
        float threshold = store.behaviorPrefs().getFloat(BehaviorSettingsStore.KEY_WAVE_THRESHOLD_V1, -1f);
        long timestamp = store.behaviorPrefs().getLong(BehaviorSettingsStore.KEY_CALIBRATION_TIMESTAMP, 0L);

        boolean hasValidData = threshold > 0f && timestamp > 0L;
        Log.d("WaveCalibration", "hasValidCalibrationData check - threshold: " + threshold +
            ", timestamp: " + timestamp + ", result: " + hasValidData);

        return hasValidData;
    }

    private void launchWaveCalibration() {
        try {
            Log.d("WaveCalibration", "launchWaveCalibration called - launching wave calibration activity");
            Intent intent = new Intent(activity, WaveCalibrationActivity.class);
            activity.startActivityForResult(intent, REQUEST_WAVE_CALIBRATION);
            Log.d("WaveCalibration", "Wave calibration activity launched successfully");
        } catch (Exception e) {
            Log.e("WaveCalibration", "Failed to launch wave calibration activity", e);
            Toast.makeText(
                activity,
                "Failed to launch calibration: " + e.getMessage(),
                Toast.LENGTH_LONG
            ).show();
        }
    }

    private void loadWaveThresholdFromCalibration() {
        Log.d("WaveCalibration", "loadWaveThresholdFromCalibration called");

        calibratedMaxDistance = store.behaviorPrefs().getFloat(BehaviorSettingsStore.KEY_SENSOR_MAX_RANGE, -1f);
        thresholdPercent = store.behaviorPrefs().getFloat(BehaviorSettingsStore.KEY_WAVE_THRESHOLD_PERCENT, 60f);
        float threshold = (calibratedMaxDistance > 0) ?
            (calibratedMaxDistance * (thresholdPercent / 100f)) : 3.0f;

        Log.d("WaveCalibration", "Loaded calibration data - maxDistance: " + calibratedMaxDistance +
            ", thresholdPercent: " + thresholdPercent + ", calculated threshold: " + threshold);

        store.behaviorPrefs().edit().putFloat(BehaviorSettingsStore.KEY_WAVE_THRESHOLD_V1, threshold).apply();
        Log.d("WaveCalibration", "Saved calculated threshold: " + threshold + " to wave_threshold_v1");

        binding.sliderWaveSensitivity.setValue(thresholdPercent);
        updateWaveProgressBarMax();
        updateWaveThresholdMarker(threshold);
        updateWaveThresholdText(threshold);
    }

    private void saveWaveThresholdPercent(float percent) {
        float clamped = (percent < 30f || percent > 90f) ? 60f : Math.max(30f, Math.min(90f, percent));
        if (clamped != percent) {
            Toast.makeText(
                activity,
                "Threshold percent adjusted to valid range (30-90%)",
                Toast.LENGTH_SHORT
            ).show();
        }
        store.behaviorPrefs().edit().putFloat(BehaviorSettingsStore.KEY_WAVE_THRESHOLD_PERCENT, clamped).apply();
        float threshold = (calibratedMaxDistance > 0) ?
            (calibratedMaxDistance * (clamped / 100f)) : 3.0f;
        store.behaviorPrefs().edit().putFloat(BehaviorSettingsStore.KEY_WAVE_THRESHOLD_V1, threshold).apply();
    }

    private void updateShakeTimeoutDisplay(int timeoutSeconds) {
        if (timeoutSeconds == 0) {
            binding.textShakeTimeout.setText("Timeout: Disabled");
        } else if (timeoutSeconds == 1) {
            binding.textShakeTimeout.setText("Timeout: 1 second");
        } else {
            binding.textShakeTimeout.setText("Timeout: " + timeoutSeconds + " seconds");
        }
    }

    private void updateWaveTimeoutDisplay(int timeoutSeconds) {
        if (timeoutSeconds == 0) {
            binding.textWaveTimeout.setText("Timeout: Disabled");
        } else if (timeoutSeconds == 1) {
            binding.textWaveTimeout.setText("Timeout: 1 second");
        } else {
            binding.textWaveTimeout.setText("Timeout: " + timeoutSeconds + " seconds");
        }
    }

    private void showTimeoutDisableWarning(String type) {
        String title = type.equals("shake")
            ? "Disable Shake Timeout?"
            : "Disable Wave Timeout?";
        String message = "⚠ **WARNING** ⚠\n\n" +
            "Disabling the timeout could be really bad for your battery if TTS fails to terminate!\n\n" +
            "I **strongly** recommend you set the timer to 5 minutes instead.\n\n" +
            "Are you sure you want to disable the timeout?";

        new MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(Html.fromHtml(
                message,
                Html.FROM_HTML_MODE_COMPACT
            ))
            .setPositiveButton(R.string.button_nevermind_keep_timeout, (dialog, which) -> {
            })
            .setNegativeButton(R.string.button_disable_timeout, (dialog, which) -> {
                if (type.equals("shake")) {
                    isProgrammaticallySettingSwitch = true;
                    binding.switchShakeTimeoutDisabled.setChecked(true);
                    isProgrammaticallySettingSwitch = false;
                    saveShakeTimeoutSeconds(0);
                    updateShakeTimeoutDisplay(0);
                } else {
                    isProgrammaticallySettingSwitch = true;
                    binding.switchWaveTimeoutDisabled.setChecked(true);
                    isProgrammaticallySettingSwitch = false;
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

        new MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(Html.fromHtml(
                message,
                Html.FROM_HTML_MODE_COMPACT
            ))
            .setPositiveButton(R.string.button_got_it, null)
            .show();
    }

    private void showShakeToStopDialog() {
        store.trackDialogUsage("shake_to_stop_info");
        String htmlText = "Shake to Stop lets you instantly silence SpeakThat by shaking your device:<br><br>" +
            "<b>Why it's helpful:</b><br>" +
            "• Stop embarrassing notifications in quiet places<br>" +
            "• Quick control when your hands are busy<br>" +
            "• Works even when screen is off<br>" +
            "• No fumbling for buttons<br><br>" +
            "<b>How it works:</b><br>" +
            "• Shake your device firmly (like a dice shake)<br>" +
            "• Current notification stops immediately<br>" +
            "• Queued notifications are cleared<br>" +
            "• New notifications work normally<br><br>" +
            "<b>Customization:</b><br>" +
            "• Adjust sensitivity for your preference<br>" +
            "• Test your shake strength with the meter<br>" +
            "• Works great for both gentle and vigorous shakers<br><br>" +
            "This feature is especially loved by people who get notifications during meetings, movies, or while driving.";

        new MaterialAlertDialogBuilder(activity)
            .setTitle("Shake to Stop Feature")
            .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.use_recommended, (dialog, which) -> {
                store.trackDialogUsage("shake_to_stop_recommended");
                binding.switchShakeToStop.setChecked(true);
                binding.shakeSettingsSection.setVisibility(View.VISIBLE);
                saveShakeToStopEnabled(true);
            })
            .setNegativeButton(R.string.got_it, null)
            .show();
    }

    private void showWaveToStopDialog() {
        store.trackDialogUsage("wave_to_stop_info");
        String htmlText = activity.getString(R.string.wave_to_stop_dialog_message);

        new MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.wave_to_stop_dialog_title))
            .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.use_recommended, (dialog, which) -> {
                store.trackDialogUsage("wave_to_stop_recommended");
                binding.switchWaveToStop.setChecked(true);
                binding.waveSettingsSection.setVisibility(View.VISIBLE);
                saveWaveToStopEnabled(true);
            })
            .setNegativeButton(R.string.got_it, null)
            .show();
    }

    private void showPressToStopDialog() {
        store.trackDialogUsage("press_to_stop_info");
        String htmlText = activity.getString(R.string.press_to_stop_dialog_message);

        new MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.press_to_stop_dialog_title))
            .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.got_it, null)
            .show();
    }

    private boolean isAccessibilityServiceEnabled() {
        String packageName = activity.getPackageName();
        String serviceName = packageName + "/com.micoyc.speakthat.SpeakThatAccessibilityService";

        String enabledServices = android.provider.Settings.Secure.getString(
            activity.getContentResolver(),
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

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

    private void showAccessibilityPermissionRequiredDialog() {
        new MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.accessibility_permission_required_title))
            .setMessage(activity.getString(R.string.accessibility_permission_required_message))
            .setPositiveButton(activity.getString(R.string.open_accessibility_settings), (dialog, which) -> {
                Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                activity.startActivity(intent);
            })
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show();
    }
}
