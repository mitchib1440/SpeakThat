package com.micoyc.speakthat.settings.sections;

import android.util.Log;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.micoyc.speakthat.InAppLogger;
import com.micoyc.speakthat.R;
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import com.micoyc.speakthat.settings.BehaviorSettingsSection;
import com.micoyc.speakthat.settings.BehaviorSettingsStore;
import android.text.Html;

public class ContentCapSection implements BehaviorSettingsSection {
    private final AppCompatActivity activity;
    private final ActivityBehaviorSettingsBinding binding;
    private final BehaviorSettingsStore store;

    public ContentCapSection(
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
        binding.btnContentCapInfo.setOnClickListener(v -> showContentCapDialog());

        binding.contentCapModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String mode = "disabled";
            if (checkedId == R.id.radioContentCapWords) {
                mode = "words";
            } else if (checkedId == R.id.radioContentCapSentences) {
                mode = "sentences";
            } else if (checkedId == R.id.radioContentCapTime) {
                mode = "time";
            }

            binding.contentCapWordSection.setVisibility("words".equals(mode) ? View.VISIBLE : View.GONE);
            binding.contentCapSentenceSection.setVisibility("sentences".equals(mode) ? View.VISIBLE : View.GONE);
            binding.contentCapTimeSection.setVisibility("time".equals(mode) ? View.VISIBLE : View.GONE);

            saveContentCapMode(mode);
            Log.d("BehaviorSettings", "Content Cap mode changed to: " + mode);
            InAppLogger.log("BehaviorSettings", "Content Cap mode changed to: " + mode);
        });

        binding.sliderContentCapWordCount.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int wordCount = (int) value;
                binding.tvContentCapWordCountValue.setText(
                    activity.getString(R.string.content_cap_word_count_value, wordCount)
                );
                saveContentCapWordCount(wordCount);
                Log.d("BehaviorSettings", "Content Cap word count changed to: " + wordCount);
            }
        });

        binding.sliderContentCapSentenceCount.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int sentenceCount = (int) value;
                binding.tvContentCapSentenceCountValue.setText(
                    activity.getString(R.string.content_cap_sentence_count_value, sentenceCount)
                );
                saveContentCapSentenceCount(sentenceCount);
                Log.d("BehaviorSettings", "Content Cap sentence count changed to: " + sentenceCount);
            }
        });

        binding.sliderContentCapTimeLimit.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int timeLimit = (int) value;
                binding.tvContentCapTimeLimitValue.setText(
                    activity.getString(R.string.content_cap_time_limit_value, timeLimit)
                );
                saveContentCapTimeLimit(timeLimit);
                Log.d("BehaviorSettings", "Content Cap time limit changed to: " + timeLimit);
            }
        });
    }

    @Override
    public void load() {
        String contentCapMode = store.prefs().getString(
            BehaviorSettingsStore.KEY_CONTENT_CAP_MODE,
            BehaviorSettingsStore.DEFAULT_CONTENT_CAP_MODE
        );
        switch (contentCapMode) {
            case "words":
                binding.radioContentCapWords.setChecked(true);
                binding.contentCapWordSection.setVisibility(View.VISIBLE);
                binding.contentCapSentenceSection.setVisibility(View.GONE);
                binding.contentCapTimeSection.setVisibility(View.GONE);
                break;
            case "sentences":
                binding.radioContentCapSentences.setChecked(true);
                binding.contentCapWordSection.setVisibility(View.GONE);
                binding.contentCapSentenceSection.setVisibility(View.VISIBLE);
                binding.contentCapTimeSection.setVisibility(View.GONE);
                break;
            case "time":
                binding.radioContentCapTime.setChecked(true);
                binding.contentCapWordSection.setVisibility(View.GONE);
                binding.contentCapSentenceSection.setVisibility(View.GONE);
                binding.contentCapTimeSection.setVisibility(View.VISIBLE);
                break;
            default:
                binding.radioContentCapDisabled.setChecked(true);
                binding.contentCapWordSection.setVisibility(View.GONE);
                binding.contentCapSentenceSection.setVisibility(View.GONE);
                binding.contentCapTimeSection.setVisibility(View.GONE);
                break;
        }

        int wordCount = store.prefs().getInt(
            BehaviorSettingsStore.KEY_CONTENT_CAP_WORD_COUNT,
            BehaviorSettingsStore.DEFAULT_CONTENT_CAP_WORD_COUNT
        );
        binding.sliderContentCapWordCount.setValue(wordCount);
        binding.tvContentCapWordCountValue.setText(
            activity.getString(R.string.content_cap_word_count_value, wordCount)
        );

        int sentenceCount = store.prefs().getInt(
            BehaviorSettingsStore.KEY_CONTENT_CAP_SENTENCE_COUNT,
            BehaviorSettingsStore.DEFAULT_CONTENT_CAP_SENTENCE_COUNT
        );
        binding.sliderContentCapSentenceCount.setValue(sentenceCount);
        binding.tvContentCapSentenceCountValue.setText(
            activity.getString(R.string.content_cap_sentence_count_value, sentenceCount)
        );

        int timeLimit = store.prefs().getInt(
            BehaviorSettingsStore.KEY_CONTENT_CAP_TIME_LIMIT,
            BehaviorSettingsStore.DEFAULT_CONTENT_CAP_TIME_LIMIT
        );
        binding.sliderContentCapTimeLimit.setValue(timeLimit);
        binding.tvContentCapTimeLimitValue.setText(
            activity.getString(R.string.content_cap_time_limit_value, timeLimit)
        );

        Log.d("BehaviorSettings", "Loaded Content Cap settings: mode=" + contentCapMode +
            ", wordCount=" + wordCount + ", sentenceCount=" + sentenceCount + ", timeLimit=" + timeLimit);
        InAppLogger.log("BehaviorSettings", "Loaded Content Cap settings: mode=" + contentCapMode +
            ", wordCount=" + wordCount + ", sentenceCount=" + sentenceCount + ", timeLimit=" + timeLimit);
    }

    @Override
    public void release() {
    }

    private void saveContentCapMode(String mode) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putString(BehaviorSettingsStore.KEY_CONTENT_CAP_MODE, mode).apply();
        InAppLogger.log("BehaviorSettings", "Content Cap mode changed to: " + mode);
    }

    private void saveContentCapWordCount(int wordCount) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putInt(BehaviorSettingsStore.KEY_CONTENT_CAP_WORD_COUNT, wordCount).apply();
        InAppLogger.log("BehaviorSettings", "Content Cap word count changed to: " + wordCount);
    }

    private void saveContentCapSentenceCount(int sentenceCount) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putInt(BehaviorSettingsStore.KEY_CONTENT_CAP_SENTENCE_COUNT, sentenceCount).apply();
        InAppLogger.log("BehaviorSettings", "Content Cap sentence count changed to: " + sentenceCount);
    }

    private void saveContentCapTimeLimit(int timeLimit) {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        store.prefs().edit().putInt(BehaviorSettingsStore.KEY_CONTENT_CAP_TIME_LIMIT, timeLimit).apply();
        InAppLogger.log("BehaviorSettings", "Content Cap time limit changed to: " + timeLimit + " seconds");
    }

    private void showContentCapDialog() {
        store.trackDialogUsage("content_cap_info");
        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_title_content_cap)
            .setMessage(Html.fromHtml(activity.getString(R.string.content_cap_help_message), Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.button_got_it, null)
            .show();
    }
}
