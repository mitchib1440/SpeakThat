/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat.settings.sections;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import com.micoyc.speakthat.InAppLogger;
import com.micoyc.speakthat.R;
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import com.micoyc.speakthat.settings.BehaviorSettingsSection;
import com.micoyc.speakthat.settings.BehaviorSettingsStore;
import java.util.Arrays;

// Earcon used to support custom sounds, but it required using the media player which didn't play nice with other SpeakThat features.

public class EarconSection implements BehaviorSettingsSection {
    private static final String VOICE_PREFS_NAME = "VoiceSettings";
    private static final String KEY_TTS_ENGINE = "tts_engine_package";
    private static final String GOOGLE_TTS_PACKAGE = "com.google.android.tts";

    private final AppCompatActivity activity;
    private final ActivityBehaviorSettingsBinding binding;
    private final BehaviorSettingsStore store;

    private MediaPlayer previewPlayer;
    private String[] earconValues;

    public EarconSection(
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
        earconValues = activity.getResources().getStringArray(R.array.behavior_earcon_mode_values);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
            activity,
            R.array.behavior_earcon_modes,
            android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerEarcon.setAdapter(adapter);

        binding.spinnerEarcon.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                String mode = positionToMode(position);
                applyEarconDelayDisclaimer(mode);
                if (store.isInitializing()) {
                    return;
                }
                store.prefs().edit().putString(BehaviorSettingsStore.KEY_EARCON_MODE, mode).apply();
                InAppLogger.log("BehaviorSettings", "Earcon mode changed to: " + mode);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        binding.btnPreviewEarcon.setOnClickListener(v -> playPreview());
        applyEarconDelayDisclaimer(positionToMode(binding.spinnerEarcon.getSelectedItemPosition()));
    }

    @Override
    public void load() {
        String mode = store.prefs().getString(
            BehaviorSettingsStore.KEY_EARCON_MODE,
            BehaviorSettingsStore.DEFAULT_EARCON_MODE
        );
        int pos = modeToPosition(mode);
        binding.spinnerEarcon.setSelection(pos, false);
        applyEarconDelayDisclaimer(positionToMode(pos));
    }

    @Override
    public void release() {
        releasePreviewPlayer();
    }

    /**
     * Refreshes the earcon disclaimer when the host activity resumes (e.g. after changing TTS engine in Voice settings).
     */
    public void onHostResume() {
        applyEarconDelayDisclaimer(positionToMode(binding.spinnerEarcon.getSelectedItemPosition()));
    }

    private int modeToPosition(String mode) {
        if (earconValues == null) {
            earconValues = activity.getResources().getStringArray(R.array.behavior_earcon_mode_values);
        }
        int idx = Arrays.asList(earconValues).indexOf(mode);
        return idx >= 0 ? idx : 0;
    }

    private String positionToMode(int position) {
        if (earconValues == null || position < 0 || position >= earconValues.length) {
            return BehaviorSettingsStore.EARCON_NONE;
        }
        return earconValues[position];
    }

    private String resolveEffectiveTtsEnginePackage() {
        android.content.SharedPreferences voicePrefs =
            activity.getSharedPreferences(VOICE_PREFS_NAME, Context.MODE_PRIVATE);
        String saved = voicePrefs.getString(KEY_TTS_ENGINE, "");
        if (saved != null && !saved.isEmpty()) {
            return saved;
        }
        String def = Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.TTS_DEFAULT_SYNTH);
        return def != null ? def : "";
    }

    private static boolean isGoogleSpeechServices(String packageName) {
        return GOOGLE_TTS_PACKAGE.equals(packageName);
    }

    private void applyEarconDelayDisclaimer(String mode) {
        if (BehaviorSettingsStore.EARCON_NONE.equals(mode)) {
            binding.layoutEarconDelayDisclaimer.setVisibility(View.GONE);
            return;
        }
        binding.layoutEarconDelayDisclaimer.setVisibility(View.VISIBLE);

        boolean google = isGoogleSpeechServices(resolveEffectiveTtsEnginePackage());
        int textRes = google ? R.string.behavior_earcon_delay_disclaimer : R.string.behavior_earcon_engine_warning;
        int textColor = ContextCompat.getColor(
            activity,
            google ? R.color.purple_card_text_primary : R.color.text_warning
        );
        CharSequence text = activity.getText(textRes);
        binding.textEarconDelayDisclaimer.setText(textRes);
        binding.textEarconDelayDisclaimer.setTextColor(textColor);
        binding.textEarconDelayDisclaimer.setContentDescription(text);
        ImageViewCompat.setImageTintList(
            binding.imageEarconDelayDisclaimerIcon,
            ColorStateList.valueOf(textColor)
        );
        binding.imageEarconDelayDisclaimerIcon.setContentDescription(text);
    }

    private void playPreview() {
        releasePreviewPlayer();
        String mode = positionToMode(binding.spinnerEarcon.getSelectedItemPosition());
        InAppLogger.log("BehaviorSettings", "Earcon preview requested. mode=" + mode);
        int rawId = rawResourceForMode(mode);
        if (rawId == 0) {
            Toast.makeText(activity, R.string.behavior_earcon_preview_none, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            previewPlayer = new MediaPlayer();
            AssetFileDescriptor afd = activity.getResources().openRawResourceFd(rawId);
            previewPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            previewPlayer.setOnCompletionListener(mp -> releasePreviewPlayer());
            previewPlayer.prepare();
            previewPlayer.start();
        } catch (Exception e) {
            releasePreviewPlayer();
            InAppLogger.logError("BehaviorSettings", "Earcon preview failed: " + e.getMessage());
            Toast.makeText(activity, R.string.behavior_earcon_operation_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private static int rawResourceForMode(String mode) {
        if (BehaviorSettingsStore.EARCON_DIGITAL_BEEP.equals(mode)) {
            return R.raw.digital_beep;
        }
        if (BehaviorSettingsStore.EARCON_ANDROID_TAP.equals(mode)) {
            return R.raw.android_tap;
        }
        if (BehaviorSettingsStore.EARCON_SOFT_CLICK.equals(mode)) {
            return R.raw.soft_click;
        }
        if (BehaviorSettingsStore.EARCON_HARD_CLICK.equals(mode)) {
            return R.raw.hard_click;
        }
        if (BehaviorSettingsStore.EARCON_REVERB_TAP.equals(mode)) {
            return R.raw.reverb_tap;
        }
        if (BehaviorSettingsStore.EARCON_SQUEAK.equals(mode)) {
            return R.raw.squeak;
        }
        if (BehaviorSettingsStore.EARCON_SOFT_PLOP.equals(mode)) {
            return R.raw.soft_plop;
        }
        if (BehaviorSettingsStore.EARCON_HARD_PLOP.equals(mode)) {
            return R.raw.hard_plop;
        }
        if (BehaviorSettingsStore.EARCON_SOFT_POP.equals(mode)) {
            return R.raw.soft_pop;
        }
        if (BehaviorSettingsStore.EARCON_HARD_POP.equals(mode)) {
            return R.raw.hard_pop;
        }
        return 0;
    }

    private void releasePreviewPlayer() {
        if (previewPlayer != null) {
            try {
                previewPlayer.release();
            } catch (Exception ignored) {
            }
            previewPlayer = null;
        }
    }
}
