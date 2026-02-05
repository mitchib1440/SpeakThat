package com.micoyc.speakthat.settings.sections;

import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.micoyc.speakthat.InAppLogger;
import com.micoyc.speakthat.R;
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import com.micoyc.speakthat.settings.BehaviorSettingsSection;
import com.micoyc.speakthat.settings.BehaviorSettingsStore;
import android.text.Html;

public class MediaBehaviorSection implements BehaviorSettingsSection {
    private static final String MEDIA_BEHAVIOR_IGNORE = "ignore";
    private static final String MEDIA_BEHAVIOR_PAUSE = "pause";
    private static final String MEDIA_BEHAVIOR_DUCK = "duck";
    private static final String MEDIA_BEHAVIOR_SILENCE = "silence";

    private static final String DEFAULT_MEDIA_BEHAVIOR = MEDIA_BEHAVIOR_PAUSE;

    private final AppCompatActivity activity;
    private final ActivityBehaviorSettingsBinding binding;
    private final BehaviorSettingsStore store;

    public MediaBehaviorSection(
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
        binding.mediaBehaviorGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String mediaBehavior = MEDIA_BEHAVIOR_IGNORE;
            if (checkedId == R.id.radioMediaPause) {
                mediaBehavior = MEDIA_BEHAVIOR_PAUSE;
            } else if (checkedId == R.id.radioMediaDuck) {
                mediaBehavior = MEDIA_BEHAVIOR_DUCK;
            } else if (checkedId == R.id.radioMediaSilence) {
                mediaBehavior = MEDIA_BEHAVIOR_SILENCE;
            }

            boolean showDucking = MEDIA_BEHAVIOR_DUCK.equals(mediaBehavior);
            binding.duckingVolumeContainer.setVisibility(showDucking ? View.VISIBLE : View.GONE);
            binding.duckingWarningText.setVisibility(showDucking ? View.VISIBLE : View.GONE);
            if (showDucking) {
                binding.duckingWarningText.setText(
                    activity.getString(R.string.behavior_ducking_enhanced_warning) + "\n\n" +
                    activity.getString(R.string.behavior_ducking_device_tip) + "\n\n" +
                    activity.getString(R.string.behavior_ducking_fallback_tip)
                );
            }

            saveMediaBehavior(mediaBehavior);
        });

        binding.btnMediaBehaviorInfo.setOnClickListener(v -> showMediaBehaviorDialog());

        binding.duckingFallbackGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String fallbackStrategy = "manual";
            if (checkedId == R.id.radioFallbackPause) {
                fallbackStrategy = "pause";
            }
            saveDuckingFallbackStrategy(fallbackStrategy);
        });

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
    }

    @Override
    public void load() {
        String savedMediaBehavior = store.prefs().getString(
            BehaviorSettingsStore.KEY_MEDIA_BEHAVIOR,
            DEFAULT_MEDIA_BEHAVIOR
        );

        switch (savedMediaBehavior) {
            case MEDIA_BEHAVIOR_PAUSE:
                binding.radioMediaPause.setChecked(true);
                binding.duckingVolumeContainer.setVisibility(View.GONE);
                break;
            case MEDIA_BEHAVIOR_DUCK:
                binding.radioMediaDuck.setChecked(true);
                binding.duckingVolumeContainer.setVisibility(View.VISIBLE);
                binding.duckingWarningText.setVisibility(View.VISIBLE);
                binding.duckingWarningText.setText(
                    activity.getString(R.string.behavior_ducking_enhanced_warning) + "\n\n" +
                    activity.getString(R.string.behavior_ducking_device_tip) + "\n\n" +
                    activity.getString(R.string.behavior_ducking_fallback_tip)
                );
                break;
            case MEDIA_BEHAVIOR_SILENCE:
                binding.radioMediaSilence.setChecked(true);
                binding.duckingVolumeContainer.setVisibility(View.GONE);
                break;
            default:
                binding.radioMediaIgnore.setChecked(true);
                binding.duckingVolumeContainer.setVisibility(View.GONE);
                break;
        }

        binding.radioMediaDuck.setEnabled(true);

        int savedDuckingVolume = store.prefs().getInt(
            BehaviorSettingsStore.KEY_DUCKING_VOLUME,
            BehaviorSettingsStore.DEFAULT_DUCKING_VOLUME
        );
        binding.duckingVolumeSeekBar.setValue(savedDuckingVolume);
        updateDuckingVolumeDisplay(savedDuckingVolume);

        String savedFallbackStrategy = loadDuckingFallbackStrategy();
        if ("pause".equals(savedFallbackStrategy)) {
            binding.radioFallbackPause.setChecked(true);
        } else {
            binding.radioFallbackManual.setChecked(true);
        }
    }

    @Override
    public void release() {
    }

    private void saveMediaBehavior(String mediaBehavior) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putString(BehaviorSettingsStore.KEY_MEDIA_BEHAVIOR, mediaBehavior).apply();
        InAppLogger.log("BehaviorSettings", "Media behavior changed to: " + mediaBehavior);
    }

    private void saveDuckingVolume(int volume) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putInt(BehaviorSettingsStore.KEY_DUCKING_VOLUME, volume).apply();
        InAppLogger.log("BehaviorSettings", "Ducking volume changed to: " + volume + "%");
    }

    private void updateDuckingVolumeDisplay(int volume) {
        binding.duckingVolumeValue.setText(volume + "%");
    }

    private void saveDuckingFallbackStrategy(String strategy) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putString(BehaviorSettingsStore.KEY_DUCKING_FALLBACK_STRATEGY, strategy).apply();
        InAppLogger.log("LowerAudio", "Ducking fallback strategy saved: " + strategy);
    }

    private String loadDuckingFallbackStrategy() {
        return store.prefs().getString(BehaviorSettingsStore.KEY_DUCKING_FALLBACK_STRATEGY, "manual");
    }

    private void showMediaBehaviorDialog() {
        store.trackDialogUsage("media_behavior_info");
        String htmlText = "Choose how SpeakThat handles notifications while music/videos play:<br><br>" +
            "<b>Ignore</b> - Speaks over your media. Simple but can be disruptive.<br><br>" +
            "<b>Pause</b> - Pauses media completely while speaking. Good for podcasts, but interrupts music flow. <i>Now with improved compatibility and fallback strategies.</i><br><br>" +
            "<b>Lower Audio</b> - Temporarily reduces media volume so you can hear both.<br><br>" +
            "<b>Silence</b> - Doesn't speak while media plays. Quiet but you might miss important notifications.<br><br>" +
            "Lower Audio is HIGHLY dependent on your device. Some devices do not support it all the time for third party apps!<br>" +
            "Pause is recommended for better reliability!";

        new MaterialAlertDialogBuilder(activity)
            .setTitle("Media Behavior Options")
            .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.use_recommended, (dialog, which) -> {
                store.trackDialogUsage("media_behavior_recommended");
                binding.radioMediaDuck.setChecked(true);
                binding.duckingVolumeContainer.setVisibility(View.VISIBLE);
                saveMediaBehavior(MEDIA_BEHAVIOR_DUCK);
            })
            .setNegativeButton(R.string.got_it, null)
            .show();
    }
}
