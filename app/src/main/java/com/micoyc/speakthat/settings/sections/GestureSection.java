/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat.settings.sections;

import android.content.Intent;
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
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import com.micoyc.speakthat.settings.BehaviorSettingsSection;
import com.micoyc.speakthat.settings.BehaviorSettingsStore;
import com.micoyc.speakthat.settings.managers.ShakeSensorManager;
import android.text.Html;

public class GestureSection implements BehaviorSettingsSection {

    private final AppCompatActivity activity;
    private final ActivityBehaviorSettingsBinding binding;
    private final BehaviorSettingsStore store;

    private final ShakeSensorManager shakeSensorManager;

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
            binding.waveSettingsSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            saveWaveToStopEnabled(isChecked);
        });

        binding.sliderWaveHold.setValueFrom(0f);
        binding.sliderWaveHold.setValueTo(500f);
        binding.sliderWaveHold.setStepSize(10f);
        binding.sliderWaveHold.setLabelFormatter(value ->
            activity.getString(R.string.behavior_wave_hold_label_ms, Math.round(value))
        );
        binding.sliderWaveHold.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int holdMs = Math.round(value);
                updateWaveHoldDisplay(holdMs);
                saveWaveHoldDurationMs(holdMs);
            }
        });

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
        binding.switchWaveToStop.setChecked(waveEnabled);
        binding.waveSettingsSection.setVisibility(waveEnabled ? View.VISIBLE : View.GONE);

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

        int waveHoldMs = store.prefs().getInt(
            BehaviorSettingsStore.KEY_WAVE_HOLD_DURATION_MS,
            BehaviorSettingsStore.DEFAULT_WAVE_HOLD_DURATION_MS
        );
        if (waveHoldMs < 0 || waveHoldMs > 500) {
            waveHoldMs = BehaviorSettingsStore.DEFAULT_WAVE_HOLD_DURATION_MS;
            saveWaveHoldDurationMs(waveHoldMs);
        }
        binding.sliderWaveHold.setValue(waveHoldMs);
        updateWaveHoldDisplay(waveHoldMs);

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

    private void saveShakeToStopEnabled(boolean enabled) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_SHAKE_TO_STOP_ENABLED, enabled).apply();
    }

    private void saveShakeThreshold(float threshold) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putFloat(BehaviorSettingsStore.KEY_SHAKE_THRESHOLD, threshold).apply();
    }

    private void saveShakeTimeoutSeconds(int timeoutSeconds) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
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
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_WAVE_TO_STOP_ENABLED, enabled).apply();
    }

    private void savePressToStopEnabled(boolean enabled) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_PRESS_TO_STOP_ENABLED, enabled).apply();
    }

    private void saveWaveTimeoutSeconds(int timeoutSeconds) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
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

    private void saveWaveHoldDurationMs(int holdMs) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        int validatedHold = validateWaveHoldDurationMs(holdMs);
        if (validatedHold != holdMs) {
            binding.sliderWaveHold.setValue(validatedHold);
            updateWaveHoldDisplay(validatedHold);
        }
        store.prefs().edit().putInt(BehaviorSettingsStore.KEY_WAVE_HOLD_DURATION_MS, validatedHold).apply();
        InAppLogger.log("BehaviorSettings", "Wave hold duration changed to: " + validatedHold + " ms");
    }

    private void savePocketModeEnabled(boolean enabled) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_POCKET_MODE_ENABLED, enabled).apply();
        InAppLogger.log("BehaviorSettings", "Pocket mode changed to: " + enabled);
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

    private void updateWaveHoldDisplay(int holdMs) {
        binding.textWaveHold.setText(activity.getString(R.string.behavior_wave_hold_current, holdMs));
    }

    private int validateWaveHoldDurationMs(int holdMs) {
        return Math.max(0, Math.min(500, holdMs));
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
