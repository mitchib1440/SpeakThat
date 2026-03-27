/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat.settings.sections;

import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.micoyc.speakthat.InAppLogger;
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import com.micoyc.speakthat.settings.BehaviorSettingsActivity;
import com.micoyc.speakthat.settings.BehaviorSettingsSection;
import com.micoyc.speakthat.settings.BehaviorSettingsStore;

public class RespectModesSection implements BehaviorSettingsSection {
    public static final int REQUEST_PHONE_STATE_PERMISSION = 2001;

    private final BehaviorSettingsActivity activity;
    private final ActivityBehaviorSettingsBinding binding;
    private final BehaviorSettingsStore store;

    public RespectModesSection(
        BehaviorSettingsActivity activity,
        ActivityBehaviorSettingsBinding binding,
        BehaviorSettingsStore store
    ) {
        this.activity = activity;
        this.binding = binding;
        this.store = store;
    }

    @Override
    public void bind() {
        attachHonourDoNotDisturbListener();

        binding.switchHonourSilentMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveHonourSilentMode(isChecked);
        });

        binding.switchHonourVibrateMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveHonourVibrateMode(isChecked);
        });

        binding.switchHonourPhoneCalls.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (store.isInitializing()) {
                return;
            }
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.READ_PHONE_STATE)
                        != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(
                        new String[]{android.Manifest.permission.READ_PHONE_STATE},
                        REQUEST_PHONE_STATE_PERMISSION
                    );
                    return;
                }
            }
            saveHonourPhoneCalls(isChecked);
        });

    }

    private void attachHonourDoNotDisturbListener() {
        binding.switchHonourDoNotDisturb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (store.isInitializing()) {
                return;
            }
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NotificationManager nm =
                    (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null && !nm.isNotificationPolicyAccessGranted()) {
                    activity.setAwaitingNotificationPolicyAccess(true);
                    binding.switchHonourDoNotDisturb.setOnCheckedChangeListener(null);
                    binding.switchHonourDoNotDisturb.setChecked(false);
                    attachHonourDoNotDisturbListener();
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                    } catch (ActivityNotFoundException e) {
                        Log.w("BehaviorSettings", "Notification policy settings not found", e);
                        activity.setAwaitingNotificationPolicyAccess(false);
                        InAppLogger.log("BehaviorSettings", "Could not open notification policy settings");
                    }
                    return;
                }
            }
            saveHonourDoNotDisturb(isChecked);
        });
    }

    /**
     * Called from {@link BehaviorSettingsActivity#onResume()} after returning from system settings.
     */
    public void onHostResume() {
        if (!activity.takeAwaitingNotificationPolicyAccess()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NotificationManager nm =
                (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                saveHonourDoNotDisturb(true);
                binding.switchHonourDoNotDisturb.setOnCheckedChangeListener(null);
                binding.switchHonourDoNotDisturb.setChecked(true);
                attachHonourDoNotDisturbListener();
                InAppLogger.log("BehaviorSettings", "Notification policy access granted; Honour DND enabled");
            } else {
                InAppLogger.log("BehaviorSettings", "Notification policy access still not granted");
            }
        }
    }

    @Override
    public void load() {
        migrateAudioModePreferenceIfNeeded();

        boolean honourDoNotDisturb = store.prefs().getBoolean(
            BehaviorSettingsStore.KEY_HONOUR_DO_NOT_DISTURB,
            BehaviorSettingsStore.DEFAULT_HONOUR_DO_NOT_DISTURB
        );
        binding.switchHonourDoNotDisturb.setChecked(honourDoNotDisturb);

        boolean honourSilentMode = store.prefs().getBoolean(
            BehaviorSettingsStore.KEY_HONOUR_SILENT_MODE,
            BehaviorSettingsStore.DEFAULT_HONOUR_SILENT_MODE
        );
        boolean honourVibrateMode = store.prefs().getBoolean(
            BehaviorSettingsStore.KEY_HONOUR_VIBRATE_MODE,
            BehaviorSettingsStore.DEFAULT_HONOUR_VIBRATE_MODE
        );
        binding.switchHonourSilentMode.setChecked(honourSilentMode);
        binding.switchHonourVibrateMode.setChecked(honourVibrateMode);

        boolean honourPhoneCalls = store.prefs().getBoolean(
            BehaviorSettingsStore.KEY_HONOUR_PHONE_CALLS,
            BehaviorSettingsStore.DEFAULT_HONOUR_PHONE_CALLS
        );
        binding.switchHonourPhoneCalls.setChecked(honourPhoneCalls);

    }

    @Override
    public void release() {
    }

    private void saveHonourDoNotDisturb(boolean honour) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_HONOUR_DO_NOT_DISTURB, honour).apply();
        InAppLogger.log("BehaviorSettings", "Honour Do Not Disturb changed to: " + honour);
    }

    private void saveHonourSilentMode(boolean honour) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_HONOUR_SILENT_MODE, honour).apply();
        InAppLogger.log("BehaviorSettings", "Honour Silent Mode changed to: " + honour);
    }

    private void saveHonourVibrateMode(boolean honour) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_HONOUR_VIBRATE_MODE, honour).apply();
        InAppLogger.log("BehaviorSettings", "Honour Vibrate Mode changed to: " + honour);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQUEST_PHONE_STATE_PERMISSION) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveHonourPhoneCalls(true);
            InAppLogger.log("BehaviorSettings", "READ_PHONE_STATE permission granted");
        } else {
            binding.switchHonourPhoneCalls.setChecked(false);
            InAppLogger.log("BehaviorSettings", "READ_PHONE_STATE permission denied, reverting switch");
        }
    }

    private void saveHonourPhoneCalls(boolean honour) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_HONOUR_PHONE_CALLS, honour).apply();
        InAppLogger.log("BehaviorSettings", "Honour phone calls changed to: " + honour);
    }

    private void migrateAudioModePreferenceIfNeeded() {
        boolean hasSilent = store.prefs().contains(BehaviorSettingsStore.KEY_HONOUR_SILENT_MODE);
        boolean hasVibrate = store.prefs().contains(BehaviorSettingsStore.KEY_HONOUR_VIBRATE_MODE);
        if (hasSilent && hasVibrate) {
            return;
        }

        boolean legacyHonourAudioMode = store.prefs().getBoolean("honour_audio_mode", true);
        store.prefs().edit()
            .putBoolean(BehaviorSettingsStore.KEY_HONOUR_SILENT_MODE, legacyHonourAudioMode)
            .putBoolean(BehaviorSettingsStore.KEY_HONOUR_VIBRATE_MODE, legacyHonourAudioMode)
            .apply();
        InAppLogger.log(
            "BehaviorSettings",
            "Migrated legacy honour_audio_mode (" + legacyHonourAudioMode + ") to split Silent/Vibrate flags"
        );
    }

}
