/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat.settings.sections;

import androidx.appcompat.app.AppCompatActivity;
import com.micoyc.speakthat.InAppLogger;
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import com.micoyc.speakthat.settings.BehaviorSettingsSection;
import com.micoyc.speakthat.settings.BehaviorSettingsStore;

public class AndroidAutoSection implements BehaviorSettingsSection {

    private final AppCompatActivity activity;
    private final ActivityBehaviorSettingsBinding binding;
    private final BehaviorSettingsStore store;

    public AndroidAutoSection(
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
        binding.switchDisableSpeakThatAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (store.isInitializing()) return;
            store.prefs().edit().putBoolean("android_auto_disable_speakthat", isChecked).apply();
            InAppLogger.log("BehaviorSettings", "Android Auto Disable SpeakThat changed to: " + isChecked);
        });

        binding.switchDisableScoAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (store.isInitializing()) return;
            store.prefs().edit().putBoolean("android_auto_disable_sco", isChecked).apply();
            InAppLogger.log("BehaviorSettings", "Android Auto Disable SCO changed to: " + isChecked);
        });
    }

    @Override
    public void load() {
        boolean disableSpeakThat = store.prefs().getBoolean("android_auto_disable_speakthat", true);
        binding.switchDisableSpeakThatAuto.setChecked(disableSpeakThat);

        boolean disableSco = store.prefs().getBoolean("android_auto_disable_sco", true);
        binding.switchDisableScoAuto.setChecked(disableSco);
    }

    @Override
    public void release() {
        // Nothing to release
    }
}
