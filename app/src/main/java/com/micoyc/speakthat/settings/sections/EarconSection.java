/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat.settings.sections;

import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.micoyc.speakthat.InAppLogger;
import com.micoyc.speakthat.R;
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import com.micoyc.speakthat.settings.BehaviorSettingsSection;
import com.micoyc.speakthat.settings.BehaviorSettingsStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class EarconSection implements BehaviorSettingsSection {
    private static final int DURATION_MS = 200;

    private final AppCompatActivity activity;
    private final ActivityBehaviorSettingsBinding binding;
    private final BehaviorSettingsStore store;

    private ActivityResultLauncher<String> pickSoundLauncher;
    private MediaPlayer previewPlayer;

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
        pickSoundLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::onSoundPicked
        );

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
            activity,
            R.array.behavior_earcon_modes,
            android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerEarcon.setAdapter(adapter);

        binding.spinnerEarcon.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String mode = positionToMode(position);
                updateCustomPanelVisibility(mode);
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

        binding.btnEarconSelectSound.setOnClickListener(v ->
            pickSoundLauncher.launch("audio/*"));

        binding.btnPreviewEarcon.setOnClickListener(v -> playPreview());
    }

    @Override
    public void load() {
        String mode = store.prefs().getString(
            BehaviorSettingsStore.KEY_EARCON_MODE,
            BehaviorSettingsStore.DEFAULT_EARCON_MODE
        );
        int pos = modeToPosition(mode);
        binding.spinnerEarcon.setSelection(pos, false);
        updateCustomPanelVisibility(mode);
    }

    @Override
    public void release() {
        releasePreviewPlayer();
    }

    private static int modeToPosition(String mode) {
        if (BehaviorSettingsStore.EARCON_SOFT_CLICK.equals(mode)) {
            return 1;
        }
        if (BehaviorSettingsStore.EARCON_CUSTOM.equals(mode)) {
            return 2;
        }
        return 0;
    }

    private static String positionToMode(int position) {
        switch (position) {
            case 1:
                return BehaviorSettingsStore.EARCON_SOFT_CLICK;
            case 2:
                return BehaviorSettingsStore.EARCON_CUSTOM;
            default:
                return BehaviorSettingsStore.EARCON_NONE;
        }
    }

    private void updateCustomPanelVisibility(String mode) {
        View panel = binding.layoutEarconCustom;
        boolean show = BehaviorSettingsStore.EARCON_CUSTOM.equals(mode);
        if (show) {
            if (panel.getVisibility() != View.VISIBLE) {
                panel.setVisibility(View.VISIBLE);
                panel.setAlpha(0f);
                panel.animate()
                    .alpha(1f)
                    .setDuration(DURATION_MS)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
            }
        } else {
            if (panel.getVisibility() == View.VISIBLE) {
                panel.animate()
                    .alpha(0f)
                    .setDuration(DURATION_MS)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> {
                        panel.setVisibility(View.GONE);
                        panel.setAlpha(0f);
                    })
                    .start();
            }
        }
    }

    private void onSoundPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        File dest = new File(activity.getFilesDir(), BehaviorSettingsStore.CUSTOM_EARCON_FILE_NAME);
        try (InputStream in = activity.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                Toast.makeText(activity, R.string.behavior_earcon_operation_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            Toast.makeText(activity, R.string.behavior_earcon_saved_toast, Toast.LENGTH_SHORT).show();
            InAppLogger.log("BehaviorSettings", "Custom earcon saved to files dir");
        } catch (Exception e) {
            InAppLogger.logError("BehaviorSettings", "Failed to save custom earcon: " + e.getMessage());
            Toast.makeText(activity, R.string.behavior_earcon_operation_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void playPreview() {
        releasePreviewPlayer();
        String mode = positionToMode(binding.spinnerEarcon.getSelectedItemPosition());
        try {
            previewPlayer = new MediaPlayer();
            if (BehaviorSettingsStore.EARCON_SOFT_CLICK.equals(mode)) {
                AssetFileDescriptor afd = activity.getResources().openRawResourceFd(R.raw.soft_click);
                previewPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            } else if (BehaviorSettingsStore.EARCON_CUSTOM.equals(mode)) {
                File f = new File(activity.getFilesDir(), BehaviorSettingsStore.CUSTOM_EARCON_FILE_NAME);
                if (!f.exists()) {
                    Toast.makeText(activity, R.string.behavior_earcon_operation_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                previewPlayer.setDataSource(f.getAbsolutePath());
            } else {
                Toast.makeText(activity, R.string.behavior_earcon_preview_none, Toast.LENGTH_SHORT).show();
                return;
            }
            previewPlayer.setOnCompletionListener(mp -> releasePreviewPlayer());
            previewPlayer.prepare();
            previewPlayer.start();
        } catch (Exception e) {
            releasePreviewPlayer();
            InAppLogger.logError("BehaviorSettings", "Earcon preview failed: " + e.getMessage());
            Toast.makeText(activity, R.string.behavior_earcon_operation_failed, Toast.LENGTH_SHORT).show();
        }
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
