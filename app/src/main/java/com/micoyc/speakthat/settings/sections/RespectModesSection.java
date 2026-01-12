package com.micoyc.speakthat.settings.sections;

import android.text.Html;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.micoyc.speakthat.InAppLogger;
import com.micoyc.speakthat.R;
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

        binding.switchNotificationDeduplication.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveNotificationDeduplication(isChecked);
        });

        binding.btnDeduplicationInfo.setOnClickListener(v -> showDeduplicationDialog());

        binding.switchDismissalMemory.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveDismissalMemoryEnabled(isChecked);
            binding.dismissalMemorySettingsSection.setVisibility(isChecked ? android.view.View.VISIBLE : android.view.View.GONE);
        });

        binding.dismissalMemoryTimeoutGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int timeoutMinutes = BehaviorSettingsStore.DEFAULT_DISMISSAL_MEMORY_TIMEOUT;
            if (checkedId == R.id.radioDismissalMemory5min) {
                timeoutMinutes = 5;
            } else if (checkedId == R.id.radioDismissalMemory15min) {
                timeoutMinutes = 15;
            } else if (checkedId == R.id.radioDismissalMemory30min) {
                timeoutMinutes = 30;
            } else if (checkedId == R.id.radioDismissalMemory1hour) {
                timeoutMinutes = 60;
            }

            saveDismissalMemoryTimeout(timeoutMinutes);
        });

        binding.btnDismissalMemoryInfo.setOnClickListener(v -> showDismissalMemoryDialog());
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

        boolean notificationDeduplication = store.prefs().getBoolean(
            BehaviorSettingsStore.KEY_NOTIFICATION_DEDUPLICATION,
            BehaviorSettingsStore.DEFAULT_NOTIFICATION_DEDUPLICATION
        );
        binding.switchNotificationDeduplication.setChecked(notificationDeduplication);

        boolean dismissalMemoryEnabled = store.prefs().getBoolean(
            BehaviorSettingsStore.KEY_DISMISSAL_MEMORY_ENABLED,
            BehaviorSettingsStore.DEFAULT_DISMISSAL_MEMORY_ENABLED
        );
        binding.switchDismissalMemory.setChecked(dismissalMemoryEnabled);
        binding.dismissalMemorySettingsSection.setVisibility(
            dismissalMemoryEnabled ? android.view.View.VISIBLE : android.view.View.GONE
        );

        int dismissalMemoryTimeout = store.prefs().getInt(
            BehaviorSettingsStore.KEY_DISMISSAL_MEMORY_TIMEOUT,
            BehaviorSettingsStore.DEFAULT_DISMISSAL_MEMORY_TIMEOUT
        );
        switch (dismissalMemoryTimeout) {
            case 5:
                binding.radioDismissalMemory5min.setChecked(true);
                break;
            case 15:
                binding.radioDismissalMemory15min.setChecked(true);
                break;
            case 30:
                binding.radioDismissalMemory30min.setChecked(true);
                break;
            case 60:
                binding.radioDismissalMemory1hour.setChecked(true);
                break;
            default:
                binding.radioDismissalMemory15min.setChecked(true);
                break;
        }
    }

    @Override
    public void release() {
    }

    private void saveHonourDoNotDisturb(boolean honour) {
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_HONOUR_DO_NOT_DISTURB, honour).apply();
        InAppLogger.log("BehaviorSettings", "Honour Do Not Disturb changed to: " + honour);
    }

    private void saveHonourSilentMode(boolean honour) {
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_HONOUR_SILENT_MODE, honour).apply();
        InAppLogger.log("BehaviorSettings", "Honour Silent Mode changed to: " + honour);
    }

    private void saveHonourVibrateMode(boolean honour) {
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_HONOUR_VIBRATE_MODE, honour).apply();
        InAppLogger.log("BehaviorSettings", "Honour Vibrate Mode changed to: " + honour);
    }

    private void saveHonourPhoneCalls(boolean honour) {
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_HONOUR_PHONE_CALLS, honour).apply();
        InAppLogger.log("BehaviorSettings", "Honour phone calls changed to: " + honour);
    }

    private void saveNotificationDeduplication(boolean enabled) {
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_NOTIFICATION_DEDUPLICATION, enabled).apply();
        InAppLogger.log("BehaviorSettings", "Notification deduplication changed to: " + enabled);
    }

    private void saveDismissalMemoryEnabled(boolean enabled) {
        store.prefs().edit().putBoolean(BehaviorSettingsStore.KEY_DISMISSAL_MEMORY_ENABLED, enabled).apply();
        InAppLogger.log("BehaviorSettings", "Dismissal memory enabled changed to: " + enabled);
    }

    private void saveDismissalMemoryTimeout(int timeoutMinutes) {
        store.prefs().edit().putInt(BehaviorSettingsStore.KEY_DISMISSAL_MEMORY_TIMEOUT, timeoutMinutes).apply();
        InAppLogger.log("BehaviorSettings", "Dismissal memory timeout changed to: " + timeoutMinutes + " minutes");
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

    private void showDeduplicationDialog() {
        store.trackDialogUsage("deduplication_info");
        String htmlText = "Notification Deduplication prevents the same notification from being read multiple times:<br><br>" +
            "<b>What it does:</b><br>" +
            "When the same notification is posted multiple times in quick succession, SpeakThat will only read it once. This prevents annoying duplicate readouts.<br><br>" +
            "<b>When it's useful:</b><br>" +
            "• <b>Duplicate notifications</b> - Some apps post the same notification multiple times<br>" +
            "• <b>System updates</b> - Android may post notifications multiple times during updates<br>" +
            "• <b>App restarts</b> - Apps may re-post notifications when restarting<br>" +
            "• <b>Network issues</b> - Connectivity problems can cause duplicate notifications<br><br>" +
            "<b>How it works:</b><br>" +
            "• Uses a 30-second window to detect duplicates<br>" +
            "• Compares notification package, ID, and content hash<br>" +
            "• Automatically cleans up old entries to save memory<br>" +
            "• Logs when duplicates are detected for debugging<br>" +
            "• Works with all notification types and apps<br><br>" +
            "<b>Tip:</b> Enable this if you experience duplicate notifications. Most users won't need this, but it's a quick fix for devices with notification issues!";

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_title_notification_deduplication)
            .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.got_it, null)
            .show();
    }

    private void showDismissalMemoryDialog() {
        store.trackDialogUsage("dismissal_memory_info");
        String htmlText = activity.getString(R.string.dialog_dismissal_memory_explanation);

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_title_dismissal_memory)
            .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.got_it, null)
            .show();
    }
}
