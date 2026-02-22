package com.micoyc.speakthat.settings.sections;

import androidx.appcompat.app.AppCompatActivity;
import com.micoyc.speakthat.InAppLogger;
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import com.micoyc.speakthat.settings.BehaviorSettingsSection;
import com.micoyc.speakthat.settings.BehaviorSettingsStore;

public class RespectModesSection implements BehaviorSettingsSection {
    private final AppCompatActivity activity;
    private final ActivityBehaviorSettingsBinding binding;
    private final BehaviorSettingsStore store;

    public RespectModesSection(
        AppCompatActivity activity,
        ActivityBehaviorSettingsBinding binding,
        BehaviorSettingsStore store
    ) {
        this.activity = activity;
        this.binding = binding;
        this.store = store;
    }

    @Override
    public void bind() {
        binding.switchHonourDoNotDisturb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveHonourDoNotDisturb(isChecked);
        });

        binding.switchHonourSilentMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveHonourSilentMode(isChecked);
        });

        binding.switchHonourVibrateMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveHonourVibrateMode(isChecked);
        });

        binding.switchHonourPhoneCalls.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveHonourPhoneCalls(isChecked);
        });

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
