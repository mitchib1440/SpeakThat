package com.micoyc.speakthat.settings.sections;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.micoyc.speakthat.InAppLogger;
import com.micoyc.speakthat.R;
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import com.micoyc.speakthat.settings.BehaviorSettingsSection;
import com.micoyc.speakthat.settings.BehaviorSettingsStore;
import android.text.Html;

public class DelaySection implements BehaviorSettingsSection {
    private final AppCompatActivity activity;
    private final ActivityBehaviorSettingsBinding binding;
    private final BehaviorSettingsStore store;

    public DelaySection(
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
        binding.delayGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int delaySeconds = 0;
            if (checkedId == R.id.radioDelay1s) {
                delaySeconds = 1;
            } else if (checkedId == R.id.radioDelay2s) {
                delaySeconds = 2;
            } else if (checkedId == R.id.radioDelay3s) {
                delaySeconds = 3;
            }

            saveDelayBeforeReadout(delaySeconds);
        });

        binding.btnDelayInfo.setOnClickListener(v -> showDelayDialog());
    }

    @Override
    public void load() {
        int savedDelay = store.prefs().getInt(
            BehaviorSettingsStore.KEY_DELAY_BEFORE_READOUT,
            BehaviorSettingsStore.DEFAULT_DELAY_BEFORE_READOUT
        );
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
            default:
                binding.radioDelayNone.setChecked(true);
                break;
        }
    }

    @Override
    public void release() {
    }

    private void saveDelayBeforeReadout(int delaySeconds) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putInt(BehaviorSettingsStore.KEY_DELAY_BEFORE_READOUT, delaySeconds).apply();
        InAppLogger.log("BehaviorSettings", "Delay before readout changed to: " + delaySeconds + " seconds");
    }

    private void showDelayDialog() {
        store.trackDialogUsage("delay_info");
        String htmlText = "Delay Before Readout gives you a brief pause before SpeakThat starts speaking:<br><br>" +
            "<b>Perfect for avoiding notification sound overlap:</b><br>" +
            "• Your phone plays its notification sound first<br>" +
            "• Then SpeakThat waits the specified delay<br>" +
            "• Finally, SpeakThat speaks the notification<br>" +
            "• No more audio collision or jarring interruptions<br><br>" +
            "<b>Grace period for shake-to-cancel:</b><br>" +
            "• Gives you time to shake your phone to cancel<br>" +
            "• Perfect for notifications in quiet places<br>" +
            "• Especially useful during meetings or movies<br>" +
            "• Cancel before the readout even starts<br><br>" +
            "<b>Recommended settings:</b><br>" +
            "• <b>None (0s)</b> - Immediate readout<br>" +
            "• <b>1 second</b> - Quick pause, minimal delay<br>" +
            "• <b>2 seconds</b> - Recommended for most users<br>" +
            "• <b>3 seconds</b> - Extra time for reaction<br><br>" +
            "This feature was inspired by Touchless Notifications and helps create a more polished, less jarring notification experience.";

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_title_delay_before_readout)
            .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.use_recommended, (dialog, which) -> {
                store.trackDialogUsage("delay_recommended");
                binding.radioDelay2s.setChecked(true);
                saveDelayBeforeReadout(2);
            })
            .setNegativeButton(R.string.got_it, null)
            .show();
    }
}
