package com.micoyc.speakthat.settings;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.micoyc.speakthat.InAppLogger;
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import com.micoyc.speakthat.settings.sections.ContentCapSection;
import com.micoyc.speakthat.settings.sections.CooldownSection;
import com.micoyc.speakthat.settings.sections.CustomAppNamesSection;
import com.micoyc.speakthat.settings.sections.DelaySection;
import com.micoyc.speakthat.settings.sections.GestureSection;
import com.micoyc.speakthat.settings.sections.MediaBehaviorSection;
import com.micoyc.speakthat.settings.sections.NotificationBehaviorSection;
import com.micoyc.speakthat.settings.sections.RespectModesSection;
import com.micoyc.speakthat.settings.sections.SpeechTemplateSection;
import java.util.ArrayList;
import java.util.List;

public class BehaviorSettingsActivity extends AppCompatActivity {
    private ActivityBehaviorSettingsBinding binding;
    private BehaviorSettingsStore store;
    private final List<BehaviorSettingsSection> sections = new ArrayList<>();

    private GestureSection gestureSection;
    private CooldownSection cooldownSection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        store = new BehaviorSettingsStore(this);
        applySavedTheme();

        super.onCreate(savedInstanceState);
        binding = ActivityBehaviorSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Show loading initially
        setLoading(true);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(com.micoyc.speakthat.R.string.title_behavior_settings));
        }

        setupSections();
        for (BehaviorSettingsSection section : sections) {
            section.bind();
        }
        for (BehaviorSettingsSection section : sections) {
            section.load();
        }
        
        // Mark initialization complete - sections can now save preferences without causing loops
        store.setInitializationComplete();

        if (cooldownSection != null) {
            cooldownSection.testAppListFunctionality();
        }

        // Hide loading after sections are loaded
        setLoading(false);
    }

    private void setLoading(boolean loading) {
        android.view.View loadingContainer = findViewById(com.micoyc.speakthat.R.id.loadingContainer);
        android.view.View scrollView = findViewById(com.micoyc.speakthat.R.id.behaviorSettingsScrollView);
        android.widget.TextView loadingText = findViewById(com.micoyc.speakthat.R.id.loadingText);
        
        if (loadingContainer != null) {
            loadingContainer.setVisibility(loading ? android.view.View.VISIBLE : android.view.View.GONE);
        }
        if (scrollView != null) {
            scrollView.setVisibility(loading ? android.view.View.INVISIBLE : android.view.View.VISIBLE);
        }
        
        // Set random loading text
        if (loading && loadingText != null) {
            int[] loadingLines = {
                com.micoyc.speakthat.R.string.loading_line_1, com.micoyc.speakthat.R.string.loading_line_2, com.micoyc.speakthat.R.string.loading_line_3,
                com.micoyc.speakthat.R.string.loading_line_4, com.micoyc.speakthat.R.string.loading_line_5, com.micoyc.speakthat.R.string.loading_line_6,
                com.micoyc.speakthat.R.string.loading_line_7, com.micoyc.speakthat.R.string.loading_line_8, com.micoyc.speakthat.R.string.loading_line_9,
                com.micoyc.speakthat.R.string.loading_line_10, com.micoyc.speakthat.R.string.loading_line_11, com.micoyc.speakthat.R.string.loading_line_12,
                com.micoyc.speakthat.R.string.loading_line_13, com.micoyc.speakthat.R.string.loading_line_14, com.micoyc.speakthat.R.string.loading_line_15,
                com.micoyc.speakthat.R.string.loading_line_16, com.micoyc.speakthat.R.string.loading_line_17, com.micoyc.speakthat.R.string.loading_line_18,
                com.micoyc.speakthat.R.string.loading_line_19, com.micoyc.speakthat.R.string.loading_line_20, com.micoyc.speakthat.R.string.loading_line_21,
                com.micoyc.speakthat.R.string.loading_line_22, com.micoyc.speakthat.R.string.loading_line_23, com.micoyc.speakthat.R.string.loading_line_24,
                com.micoyc.speakthat.R.string.loading_line_25, com.micoyc.speakthat.R.string.loading_line_26, com.micoyc.speakthat.R.string.loading_line_27,
                com.micoyc.speakthat.R.string.loading_line_28, com.micoyc.speakthat.R.string.loading_line_29, com.micoyc.speakthat.R.string.loading_line_30,
                com.micoyc.speakthat.R.string.loading_line_31, com.micoyc.speakthat.R.string.loading_line_32, com.micoyc.speakthat.R.string.loading_line_33,
                com.micoyc.speakthat.R.string.loading_line_34, com.micoyc.speakthat.R.string.loading_line_35, com.micoyc.speakthat.R.string.loading_line_36,
                com.micoyc.speakthat.R.string.loading_line_37, com.micoyc.speakthat.R.string.loading_line_38, com.micoyc.speakthat.R.string.loading_line_39,
                com.micoyc.speakthat.R.string.loading_line_40, com.micoyc.speakthat.R.string.loading_line_41, com.micoyc.speakthat.R.string.loading_line_42,
                com.micoyc.speakthat.R.string.loading_line_43, com.micoyc.speakthat.R.string.loading_line_44, com.micoyc.speakthat.R.string.loading_line_45,
                com.micoyc.speakthat.R.string.loading_line_46, com.micoyc.speakthat.R.string.loading_line_47, com.micoyc.speakthat.R.string.loading_line_48,
                com.micoyc.speakthat.R.string.loading_line_49, com.micoyc.speakthat.R.string.loading_line_50
            };
            int randomLine = loadingLines[new java.util.Random().nextInt(loadingLines.length)];
            loadingText.setText(randomLine);
        }
    }

    private void setupSections() {
        NotificationBehaviorSection notificationBehaviorSection =
            new NotificationBehaviorSection(this, binding, store);
        gestureSection = new GestureSection(this, binding, store);
        MediaBehaviorSection mediaBehaviorSection =
            new MediaBehaviorSection(this, binding, store);
        DelaySection delaySection = new DelaySection(this, binding, store);
        ContentCapSection contentCapSection = new ContentCapSection(this, binding, store);
        CustomAppNamesSection customAppNamesSection =
            new CustomAppNamesSection(this, binding, store);
        cooldownSection = new CooldownSection(this, binding, store);
        RespectModesSection respectModesSection =
            new RespectModesSection(this, binding, store);
        SpeechTemplateSection speechTemplateSection =
            new SpeechTemplateSection(this, binding, store);

        sections.add(notificationBehaviorSection);
        sections.add(gestureSection);
        sections.add(mediaBehaviorSection);
        sections.add(delaySection);
        sections.add(contentCapSection);
        sections.add(customAppNamesSection);
        sections.add(cooldownSection);
        sections.add(respectModesSection);
        sections.add(speechTemplateSection);
    }

    private void applySavedTheme() {
        boolean isDarkMode = store.prefs().getBoolean(BehaviorSettingsStore.KEY_DARK_MODE, true); // Default to dark mode
        int desiredMode = isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        int currentMode = AppCompatDelegate.getDefaultNightMode();
        
        // Only set the night mode if it's different from the current mode
        // This prevents unnecessary configuration changes that cause activity recreation loops
        if (currentMode != desiredMode) {
            AppCompatDelegate.setDefaultNightMode(desiredMode);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Simply close this activity instead of navigating to parent
        // This prevents the activity recreation loop caused by parent activity chain
        finish();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        InAppLogger.logAppLifecycle("Behaviour Settings resumed", "BehaviorSettingsActivity");
    }

    @Override
    protected void onDestroy() {
        for (BehaviorSettingsSection section : sections) {
            section.release();
        }
        super.onDestroy();
        binding = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (gestureSection != null) {
            gestureSection.onActivityResult(requestCode, resultCode, data);
        }
    }

    public interface OnPriorityAppActionListener {
        void onAction(int position);
    }

    public static boolean isDoNotDisturbEnabled(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
                return true;
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    android.app.NotificationManager notificationManager =
                        (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (notificationManager != null) {
                        int currentInterruptionFilter = notificationManager.getCurrentInterruptionFilter();
                        return currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
                    }
                } catch (SecurityException e) {
                    Log.d("BehaviorSettings", "No permission to check DND status, using ringer mode fallback");
                }
            }
        }
        return false;
    }

    public static boolean shouldHonourDoNotDisturb(Context context) {
        android.content.SharedPreferences prefs =
            context.getSharedPreferences(BehaviorSettingsStore.PREFS_NAME, Context.MODE_PRIVATE);
        boolean honourDND = prefs.getBoolean(
            BehaviorSettingsStore.KEY_HONOUR_DO_NOT_DISTURB,
            BehaviorSettingsStore.DEFAULT_HONOUR_DO_NOT_DISTURB
        );

        if (honourDND) {
            return isDoNotDisturbEnabled(context);
        }
        return false;
    }

    public static String getAudioModeBlockReason(Context context) {
        android.content.SharedPreferences prefs =
            context.getSharedPreferences(BehaviorSettingsStore.PREFS_NAME, Context.MODE_PRIVATE);
        boolean honourSilent = prefs.getBoolean(
            BehaviorSettingsStore.KEY_HONOUR_SILENT_MODE,
            BehaviorSettingsStore.DEFAULT_HONOUR_SILENT_MODE
        );
        boolean honourVibrate = prefs.getBoolean(
            BehaviorSettingsStore.KEY_HONOUR_VIBRATE_MODE,
            BehaviorSettingsStore.DEFAULT_HONOUR_VIBRATE_MODE
        );

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return null;
        }

        int ringerMode = audioManager.getRingerMode();
        if (ringerMode == AudioManager.RINGER_MODE_SILENT && honourSilent) {
            return "Silent";
        }
        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE && honourVibrate) {
            return "Vibrate";
        }
        return null;
    }

    public static boolean shouldHonourAudioMode(Context context) {
        return getAudioModeBlockReason(context) != null;
    }

    public static boolean isPhoneCallActive(Context context) {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int audioMode = audioManager.getMode();
                if (audioMode == AudioManager.MODE_IN_CALL) {
                    return true;
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.telephony.TelephonyManager telephonyManager =
                    (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (telephonyManager != null) {
                    int callState = telephonyManager.getCallState();
                    return callState == android.telephony.TelephonyManager.CALL_STATE_OFFHOOK;
                }
            }

            return false;
        } catch (SecurityException e) {
            Log.d("BehaviorSettings", "No permission to check call state, using audio mode fallback");
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int audioMode = audioManager.getMode();
                return audioMode == AudioManager.MODE_IN_CALL;
            }
            return false;
        } catch (Exception e) {
            Log.e("BehaviorSettings", "Error checking phone call state", e);
            return false;
        }
    }

    public static boolean shouldHonourPhoneCalls(Context context) {
        android.content.SharedPreferences prefs =
            context.getSharedPreferences(BehaviorSettingsStore.PREFS_NAME, Context.MODE_PRIVATE);
        boolean honourPhoneCalls = prefs.getBoolean(
            BehaviorSettingsStore.KEY_HONOUR_PHONE_CALLS,
            BehaviorSettingsStore.DEFAULT_HONOUR_PHONE_CALLS
        );

        if (honourPhoneCalls) {
            return isPhoneCallActive(context);
        }
        return false;
    }
}
