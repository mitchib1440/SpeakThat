package com.micoyc.speakthat;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.ArrayAdapter;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.micoyc.speakthat.databinding.ActivityCompatibilitySettingsBinding;

public class CompatibilitySettingsActivity extends AppCompatActivity {
    private ActivityCompatibilitySettingsBinding binding;
    private SharedPreferences mainPrefs;
    private SharedPreferences voicePrefs;
    private boolean isLoadingSettings = false;

    private static final String MAIN_PREFS_NAME = "SpeakThatPrefs";
    private static final String VOICE_PREFS_NAME = "VoiceSettings";

    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_AUDIO_USAGE = "audio_usage";
    private static final String KEY_CONTENT_TYPE = "content_type";
    private static final String KEY_SPEAKERPHONE_ENABLED = "speakerphone_enabled";
    private static final String KEY_DISABLE_MEDIA_FALLBACK = "disable_media_fallback";
    private static final String KEY_ENABLE_LEGACY_DUCKING = "enable_legacy_ducking";
    private static final String KEY_DUCKING_FALLBACK_STRATEGY = "ducking_fallback_strategy";
    private static final String KEY_NOTIFICATION_DEDUPLICATION = "notification_deduplication";
    private static final String KEY_DISMISSAL_MEMORY_ENABLED = "dismissal_memory_enabled";
    private static final String KEY_DISMISSAL_MEMORY_TIMEOUT = "dismissal_memory_timeout";

    private static final int DEFAULT_AUDIO_USAGE = 0;
    private static final int DEFAULT_CONTENT_TYPE = 0;
    private static final int VOICE_CALL_INDEX = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mainPrefs = getSharedPreferences(MAIN_PREFS_NAME, MODE_PRIVATE);
        voicePrefs = getSharedPreferences(VOICE_PREFS_NAME, MODE_PRIVATE);
        applySavedTheme();

        super.onCreate(savedInstanceState);
        binding = ActivityCompatibilitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings_compatibility_title);
        }

        InAppLogger.logAppLifecycle("Compatibility Settings created", "CompatibilitySettingsActivity");

        isLoadingSettings = true;
        setupAudioRouting();
        setupDuckingCompatibility();
        setupDuplicationWorkarounds();
        loadSettings();
        isLoadingSettings = false;
    }

    private void applySavedTheme() {
        boolean isDarkMode = mainPrefs.getBoolean(KEY_DARK_MODE, true);
        int desiredMode = isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        int currentMode = AppCompatDelegate.getDefaultNightMode();
        if (currentMode != desiredMode) {
            AppCompatDelegate.setDefaultNightMode(desiredMode);
        }
    }

    // ========== Card 1: Audio Routing ==========

    private void setupAudioRouting() {
        String[] audioUsageOptions = {
            getString(R.string.voice_audio_usage_media_recommended),
            getString(R.string.voice_audio_usage_notification),
            getString(R.string.voice_audio_usage_alarm),
            getString(R.string.voice_audio_usage_voice_call),
            getString(R.string.voice_audio_usage_assistance)
        };
        ArrayAdapter<String> audioUsageAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, audioUsageOptions);
        audioUsageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.audioUsageSpinner.setAdapter(audioUsageAdapter);

        binding.audioUsageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                saveToVoicePrefs(KEY_AUDIO_USAGE, position);
                updateSpeakerphoneVisibility(position);
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        String[] contentTypeOptions = {
            "Speech (Default)", "Music", "Notification Sound", "Sonification"
        };
        ArrayAdapter<String> contentTypeAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, contentTypeOptions);
        contentTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.contentTypeSpinner.setAdapter(contentTypeAdapter);

        binding.contentTypeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                saveToVoicePrefs(KEY_CONTENT_TYPE, position);
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        binding.speakerphoneSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isLoadingSettings) {
                voicePrefs.edit().putBoolean(KEY_SPEAKERPHONE_ENABLED, isChecked).apply();
                InAppLogger.log("CompatibilitySettings", "Speakerphone " + (isChecked ? "enabled" : "disabled"));
            }
        });

        binding.btnAudioHelp.setOnClickListener(v -> showAudioHelpDialog());
    }

    private void updateSpeakerphoneVisibility(int audioUsageIndex) {
        binding.speakerphoneOptionLayout.setVisibility(
            audioUsageIndex == VOICE_CALL_INDEX ? View.VISIBLE : View.GONE);
    }

    private void saveToVoicePrefs(String key, int value) {
        if (isLoadingSettings) return;
        voicePrefs.edit().putInt(key, value).apply();
        InAppLogger.log("CompatibilitySettings", key + " saved: " + value);
    }

    // ========== Card 2: Ducking Compatibility ==========

    private void setupDuckingCompatibility() {
        binding.switchDisableMediaFallback.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isLoadingSettings) {
                mainPrefs.edit().putBoolean(KEY_DISABLE_MEDIA_FALLBACK, isChecked).apply();
                InAppLogger.log("CompatibilitySettings", "Media fallback " + (isChecked ? "disabled" : "enabled"));
            }
        });

        binding.switchEnableLegacyDucking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isLoadingSettings) {
                mainPrefs.edit().putBoolean(KEY_ENABLE_LEGACY_DUCKING, isChecked).apply();
                InAppLogger.log("CompatibilitySettings", "Legacy ducking " + (isChecked ? "enabled" : "disabled"));
            }
        });

        binding.duckingFallbackGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (isLoadingSettings) return;
            String strategy = "manual";
            if (checkedId == R.id.radioFallbackPause) {
                strategy = "pause";
            }
            mainPrefs.edit().putString(KEY_DUCKING_FALLBACK_STRATEGY, strategy).apply();
            InAppLogger.log("CompatibilitySettings", "Ducking fallback strategy saved: " + strategy);
        });
    }

    // ========== Card 3: Duplication Workarounds ==========

    private void setupDuplicationWorkarounds() {
        binding.switchNotificationDeduplication.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isLoadingSettings) {
                mainPrefs.edit().putBoolean(KEY_NOTIFICATION_DEDUPLICATION, isChecked).apply();
                InAppLogger.log("CompatibilitySettings", "Deduplication " + (isChecked ? "enabled" : "disabled"));
            }
        });

        binding.btnDeduplicationInfo.setOnClickListener(v -> showDeduplicationDialog());

        binding.switchDismissalMemory.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isLoadingSettings) {
                mainPrefs.edit().putBoolean(KEY_DISMISSAL_MEMORY_ENABLED, isChecked).apply();
                InAppLogger.log("CompatibilitySettings", "Dismissal memory " + (isChecked ? "enabled" : "disabled"));
            }
            binding.dismissalMemorySettingsSection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        binding.btnDismissalMemoryInfo.setOnClickListener(v -> showDismissalMemoryDialog());

        binding.dismissalMemoryTimeoutGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (isLoadingSettings) return;
            int timeout = 15;
            if (checkedId == R.id.radioDismissalMemory5min) timeout = 5;
            else if (checkedId == R.id.radioDismissalMemory15min) timeout = 15;
            else if (checkedId == R.id.radioDismissalMemory30min) timeout = 30;
            else if (checkedId == R.id.radioDismissalMemory1hour) timeout = 60;
            mainPrefs.edit().putInt(KEY_DISMISSAL_MEMORY_TIMEOUT, timeout).apply();
            InAppLogger.log("CompatibilitySettings", "Dismissal memory timeout: " + timeout + " minutes");
        });
    }

    // ========== Load Settings ==========

    private void loadSettings() {
        // Audio Routing
        int audioUsage = voicePrefs.getInt(KEY_AUDIO_USAGE, DEFAULT_AUDIO_USAGE);
        if (binding.audioUsageSpinner.getAdapter() != null && audioUsage < binding.audioUsageSpinner.getAdapter().getCount()) {
            binding.audioUsageSpinner.setSelection(audioUsage);
        } else {
            binding.audioUsageSpinner.setSelection(DEFAULT_AUDIO_USAGE);
        }

        int contentType = voicePrefs.getInt(KEY_CONTENT_TYPE, DEFAULT_CONTENT_TYPE);
        if (binding.contentTypeSpinner.getAdapter() != null && contentType < binding.contentTypeSpinner.getAdapter().getCount()) {
            binding.contentTypeSpinner.setSelection(contentType);
        } else {
            binding.contentTypeSpinner.setSelection(DEFAULT_CONTENT_TYPE);
        }

        binding.speakerphoneSwitch.setChecked(voicePrefs.getBoolean(KEY_SPEAKERPHONE_ENABLED, false));
        updateSpeakerphoneVisibility(audioUsage);

        // Ducking Compatibility
        binding.switchDisableMediaFallback.setChecked(mainPrefs.getBoolean(KEY_DISABLE_MEDIA_FALLBACK, false));
        binding.switchEnableLegacyDucking.setChecked(mainPrefs.getBoolean(KEY_ENABLE_LEGACY_DUCKING, false));

        String fallbackStrategy = mainPrefs.getString(KEY_DUCKING_FALLBACK_STRATEGY, "manual");
        if ("pause".equals(fallbackStrategy)) {
            binding.radioFallbackPause.setChecked(true);
        } else {
            binding.radioFallbackManual.setChecked(true);
        }

        // Duplication Workarounds
        boolean deduplicationEnabled = mainPrefs.getBoolean(KEY_NOTIFICATION_DEDUPLICATION, false);
        binding.switchNotificationDeduplication.setChecked(deduplicationEnabled);

        boolean dismissalMemoryEnabled = mainPrefs.getBoolean(KEY_DISMISSAL_MEMORY_ENABLED, true);
        binding.switchDismissalMemory.setChecked(dismissalMemoryEnabled);
        binding.dismissalMemorySettingsSection.setVisibility(dismissalMemoryEnabled ? View.VISIBLE : View.GONE);

        int dismissalTimeout = mainPrefs.getInt(KEY_DISMISSAL_MEMORY_TIMEOUT, 15);
        switch (dismissalTimeout) {
            case 5: binding.radioDismissalMemory5min.setChecked(true); break;
            case 30: binding.radioDismissalMemory30min.setChecked(true); break;
            case 60: binding.radioDismissalMemory1hour.setChecked(true); break;
            default: binding.radioDismissalMemory15min.setChecked(true); break;
        }
    }

    // ========== Dialogs ==========

    private void showAudioHelpDialog() {
        String helpText = "\uD83D\uDD0A Audio Stream Type\n" +
                "Controls which volume slider affects notification speech:\n\n" +
                "• Media (Recommended): Uses the media volume slider so you can quickly adjust notification speech alongside music and videos\n" +
                "• Notification: Uses notification/ringer volume (mutes when your ringer is silenced)\n" +
                "• Alarm: Uses alarm volume (great for critical alerts)\n" +
                "• Voice Call: Uses call volume (routes to the earpiece unless speakerphone is enabled)\n" +
                "• Assistance: Uses navigation volume so speech stays audible even when ringer is muted\n\n" +
                "\uD83C\uDFB5 Content Type\n" +
                "Tells the system how to optimize audio processing:\n\n" +
                "• Speech: Optimized for voice clarity (recommended)\n" +
                "• Music: Optimized for music playback\n" +
                "• Notification Sound: For short notification sounds\n" +
                "• Sonification: For UI sounds and alerts\n\n" +
                "⚠\uFE0F Device Compatibility\n" +
                "Audio behavior varies significantly across devices and Android versions. " +
                "Some devices may not support ducking for certain streams or apps.\n\n" +
                "\uD83D\uDCA1 Troubleshooting Audio Issues\n" +
                "If ducking isn't working well:\n" +
                "• Try 'Voice Call' or 'Notification' streams (most compatible with strict apps)\n" +
                "• Switch back to 'Media' after troubleshooting if you want one slider for everything\n" +
                "• Different apps respond differently to audio ducking\n" +
                "• The app automatically chooses the best ducking method for your device\n\n" +
                "\uD83C\uDFAF Recommended Settings\n" +
                "For best results: Media + Speech\n" +
                "Alternative: Voice Call + Speech when you need isolation from media apps";

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_audio_settings_help)
                .setMessage(helpText)
                .setPositiveButton(R.string.button_got_it, null)
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();

        InAppLogger.log("CompatibilitySettings", "Audio help dialog shown");
    }

    private void showDeduplicationDialog() {
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

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_notification_deduplication)
            .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.got_it, null)
            .show();
    }

    private void showDismissalMemoryDialog() {
        String htmlText = getString(R.string.dialog_dismissal_memory_explanation);

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_dismissal_memory)
            .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.got_it, null)
            .show();
    }

    // ========== Navigation ==========

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        InAppLogger.logAppLifecycle("Compatibility Settings destroyed", "CompatibilitySettingsActivity");
    }
}
