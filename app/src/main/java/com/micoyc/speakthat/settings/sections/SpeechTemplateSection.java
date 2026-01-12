package com.micoyc.speakthat.settings.sections;

import android.text.Html;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.micoyc.speakthat.InAppLogger;
import com.micoyc.speakthat.R;
import com.micoyc.speakthat.SpeechTemplateConstants;
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import com.micoyc.speakthat.settings.BehaviorSettingsSection;
import com.micoyc.speakthat.settings.BehaviorSettingsStore;
import com.micoyc.speakthat.settings.managers.SpeechTemplateManager;

public class SpeechTemplateSection implements BehaviorSettingsSection {
    private final AppCompatActivity activity;
    private final ActivityBehaviorSettingsBinding binding;
    private final BehaviorSettingsStore store;
    private final SpeechTemplateManager templateManager;
    private final String[] templatePresets;
    private final String[] templateKeys;

    public SpeechTemplateSection(
        AppCompatActivity activity,
        ActivityBehaviorSettingsBinding binding,
        BehaviorSettingsStore store
    ) {
        this.activity = activity;
        this.binding = binding;
        this.store = store;
        this.templateManager = new SpeechTemplateManager(activity);
        this.templatePresets = templateManager.getTemplatePresets();
        this.templateKeys = templateManager.getTemplateKeys();
    }

    @Override
    public void bind() {
        setupSpeechTemplateUI();
    }

    @Override
    public void load() {
        loadSpeechTemplateSettings();
    }

    @Override
    public void release() {
    }

    private void setupSpeechTemplateUI() {
        ArrayAdapter<String> templateAdapter = new ArrayAdapter<>(
            activity, android.R.layout.simple_spinner_item, templatePresets
        );
        templateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerSpeechTemplate.setAdapter(templateAdapter);

        binding.spinnerSpeechTemplate.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                String selectedTemplateKey = templateKeys[position];

                if (selectedTemplateKey.equals("VARIED")) {
                    binding.editCustomSpeechTemplate.setVisibility(android.view.View.GONE);
                    android.view.View customFormatContainer = (android.view.View) binding.editCustomSpeechTemplate.getParent().getParent();
                    customFormatContainer.setVisibility(android.view.View.GONE);
                    binding.textSpeechPreview.setText("Varied mode: Random format selected for each notification");
                    binding.textSpeechPreview.setTextColor(activity.getResources().getColor(R.color.text_tertiary));
                    saveSpeechTemplate("VARIED");
                    saveSpeechTemplateKey(SpeechTemplateConstants.TEMPLATE_KEY_VARIED);
                } else if (selectedTemplateKey.equals("CUSTOM")) {
                    binding.editCustomSpeechTemplate.setVisibility(android.view.View.VISIBLE);
                    android.view.View customFormatContainer = (android.view.View) binding.editCustomSpeechTemplate.getParent().getParent();
                    customFormatContainer.setVisibility(android.view.View.VISIBLE);
                    updateSpeechPreview();
                    saveSpeechTemplateKey(SpeechTemplateConstants.TEMPLATE_KEY_CUSTOM);
                } else {
                    String localizedTemplate = templateManager.getLocalizedTemplateValue(selectedTemplateKey);
                    binding.editCustomSpeechTemplate.setVisibility(android.view.View.VISIBLE);
                    android.view.View customFormatContainer = (android.view.View) binding.editCustomSpeechTemplate.getParent().getParent();
                    customFormatContainer.setVisibility(android.view.View.VISIBLE);
                    binding.editCustomSpeechTemplate.setText(localizedTemplate);
                    updateSpeechPreview();
                    saveSpeechTemplate(localizedTemplate);
                    saveSpeechTemplateKey(selectedTemplateKey);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        binding.editCustomSpeechTemplate.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String newTemplate = s.toString();

                if (binding.spinnerSpeechTemplate.getSelectedItemPosition() == templatePresets.length - 2) {
                    return;
                }

                boolean matchesPreset = false;
                String matchedKey = null;
                for (int i = 0; i < templateKeys.length - 1; i++) {
                    String localizedTemplate = templateManager.getLocalizedTemplateValue(templateKeys[i]);
                    if (localizedTemplate.equals(newTemplate)) {
                        matchesPreset = true;
                        matchedKey = templateKeys[i];
                        binding.spinnerSpeechTemplate.setSelection(i);
                        break;
                    }
                }
                if (!matchesPreset) {
                    binding.spinnerSpeechTemplate.setSelection(templateKeys.length - 1);
                }

                updateSpeechPreview();
                saveSpeechTemplate(newTemplate);
                if (matchesPreset && matchedKey != null) {
                    saveSpeechTemplateKey(matchedKey);
                } else {
                    saveSpeechTemplateKey(SpeechTemplateConstants.TEMPLATE_KEY_CUSTOM);
                }
            }
        });

        binding.btnTestSpeechTemplate.setOnClickListener(v -> showTemplateTestDialog());
        binding.btnSpeechTemplateInfo.setOnClickListener(v -> showSpeechTemplateDialog());
    }

    private void showTemplateTestDialog() {
        String template = binding.editCustomSpeechTemplate.getText().toString();
        boolean isVariedMode = template.equals("VARIED") ||
            binding.spinnerSpeechTemplate.getSelectedItemPosition() == templatePresets.length - 2;
        String testResults = templateManager.buildTemplateTestResults(template, isVariedMode);

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_title_format_test_results)
            .setMessage(Html.fromHtml(testResults, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.button_got_it, null)
            .show();
    }

    private void loadSpeechTemplateSettings() {
        String savedTemplate = store.prefs().getString(
            BehaviorSettingsStore.KEY_SPEECH_TEMPLATE,
            BehaviorSettingsStore.DEFAULT_SPEECH_TEMPLATE
        );
        String templateKey = store.prefs().getString(BehaviorSettingsStore.KEY_SPEECH_TEMPLATE_KEY, null);

        if (templateKey == null) {
            templateKey = templateManager.resolveTemplateKey(savedTemplate);
            saveSpeechTemplateKey(templateKey);
        }

        if (SpeechTemplateConstants.TEMPLATE_KEY_VARIED.equals(templateKey)) {
            binding.spinnerSpeechTemplate.setSelection(templatePresets.length - 2);
            binding.editCustomSpeechTemplate.setVisibility(android.view.View.GONE);
            android.view.View customFormatContainer = (android.view.View) binding.editCustomSpeechTemplate.getParent().getParent();
            customFormatContainer.setVisibility(android.view.View.GONE);
            binding.textSpeechPreview.setText("Varied mode: Random format selected for each notification");
            binding.textSpeechPreview.setTextColor(activity.getResources().getColor(R.color.text_tertiary));
            return;
        }

        binding.editCustomSpeechTemplate.setVisibility(android.view.View.VISIBLE);
        android.view.View customFormatContainer = (android.view.View) binding.editCustomSpeechTemplate.getParent().getParent();
        customFormatContainer.setVisibility(android.view.View.VISIBLE);

        if (templateManager.isResourceTemplateKey(templateKey)) {
            int presetIndex = templateManager.getTemplateIndex(templateKey);
            if (presetIndex >= 0) {
                binding.spinnerSpeechTemplate.setSelection(presetIndex);
            }
            binding.editCustomSpeechTemplate.setText(templateManager.getLocalizedTemplateValue(templateKey));
        } else {
            binding.spinnerSpeechTemplate.setSelection(templateKeys.length - 1);
            binding.editCustomSpeechTemplate.setText(savedTemplate);
        }

        updateSpeechPreview();
    }

    private void saveSpeechTemplateKey(String templateKey) {
        if (templateKey == null || templateKey.isEmpty()) {
            templateKey = SpeechTemplateConstants.TEMPLATE_KEY_CUSTOM;
        }
        store.prefs().edit().putString(BehaviorSettingsStore.KEY_SPEECH_TEMPLATE_KEY, templateKey).apply();
        InAppLogger.log("BehaviorSettings", "Speech template key saved: " + templateKey);
    }

    private void saveSpeechTemplate(String template) {
        store.prefs().edit().putString(BehaviorSettingsStore.KEY_SPEECH_TEMPLATE, template).apply();
        InAppLogger.log("BehaviorSettings", "Speech template saved: " + template);
    }

    private void updateSpeechPreview() {
        String template = binding.editCustomSpeechTemplate.getText().toString();
        String preview = templateManager.generateSpeechPreview(template);
        binding.textSpeechPreview.setText(
            Html.fromHtml("Preview: " + preview, Html.FROM_HTML_MODE_LEGACY)
        );
    }

    private void showSpeechTemplateDialog() {
        store.trackDialogUsage("speech_template_info");
        String htmlText = "Customize exactly how your notifications are spoken aloud using placeholders and formats.<br><br>" +
            "<b>What are Speech Formats?</b><br>" +
            "Speech formats let you control the exact format and wording of how notifications are read out. Instead of always hearing \"WhatsApp notified you: New message\", you can make it say whatever you prefer.<br><br>" +
            "<b>Why customize?</b><br>" +
            "• <b>Personal preference</b> - Some like formal, others casual<br>" +
            "• <b>Clarity</b> - Make app names easier to understand<br>" +
            "• <b>Brevity</b> - Shorter formats for quick scanning<br>" +
            "• <b>Context</b> - Add time, priority, or other details<br>" +
            "• <b>Accessibility</b> - Format that works best for your needs<br><br>" +
            "<b>Complete Placeholder Reference:</b><br><br>" +
            "<b>App Information:</b><br>" +
            "• <b>{app}</b> - App display name (automatically uses custom names and respects privacy settings)<br>" +
            "• <b>{package}</b> - Package name (e.g., \"com.google.android.apps.messaging\")<br><br>" +
            "<b>Notification Content:</b><br>" +
            "• <b>{content}</b> - Full notification (title + text combined)<br>" +
            "• <b>{title}</b> - Notification title only (e.g., \"Mitchi\" for Messages)<br>" +
            "• <b>{text}</b> - Notification text only (e.g., \"I heard you're using SpeakThat!\" for Messages)<br>" +
            "• <b>{bigtext}</b> - Big text content (expanded notification)<br>" +
            "• <b>{summary}</b> - Summary text (e.g., \"1 new message\")<br>" +
            "• <b>{info}</b> - Info text (additional details)<br>" +
            activity.getString(R.string.behavior_speech_placeholder_ticker_html) + "<br><br>" +
            "<b>Time & Date:</b><br>" +
            "• <b>{time}</b> - Current time in HH:mm format (e.g., \"14:30\")<br>" +
            "• <b>{date}</b> - Current date in MMM dd format (e.g., \"Dec 15\")<br>" +
            "• <b>{timestamp}</b> - Full timestamp (e.g., \"14:30 Dec 15\")<br><br>" +
            "<b>Notification Metadata:</b><br>" +
            "• <b>{priority}</b> - Priority level (Min, Low, Default, High, Max)<br>" +
            "• <b>{category}</b> - Notification category (Message, Call, etc.)<br>" +
            "• <b>{channel}</b> - Notification channel ID<br><br>" +
            "<b>What's the difference?</b><br>" +
            "• <b>{content} vs {title} + {text}</b> - {content} is everything, {title} and {text} are separate parts<br>" +
            "• <b>{info}</b> - Usually contains \"Tap to view\" or similar action text<br>" +
            "• <b>{app}</b> - Automatically uses custom names if set, and respects privacy settings<br><br>" +
            "<b>⚠ Important Notes:</b><br>" +
            "• <b>Avoid {title} {bigtext}</b> - This can cause duplication since bigtext often includes the title<br>" +
            "• <b>Use {content}</b> for the full notification, or {title} + {text} for separate parts<br>" +
            "• <b>Test your format</b> with the Test button to see exactly how it will sound<br>" +
            activity.getString(R.string.behavior_speech_placeholder_ticker_note) + "<br><br>" +
            "<b>Format Examples:</b><br><br>" +
            "<b>Quick & Simple:</b><br>" +
            "• <b>Minimal:</b> \"{app}: {content}\" → \"Messages: Mitchi: I heard you're using SpeakThat!\"<br>" +
            "• <b>App Only:</b> \"{app}\" → \"Messages\"<br>" +
            "• <b>Content Only:</b> \"{content}\" → \"Mitchi: I heard you're using SpeakThat!\"<br><br>" +
            "<b>Informative:</b><br>" +
            "• <b>Default:</b> \"{app} notified you: {content}\" → \"Messages notified you: Mitchi: I heard you're using SpeakThat!\"<br>" +
            "• <b>Formal:</b> \"Notification from {app}: {content}\" → \"Notification from Gmail: New email from John Smith: Meeting tomorrow at 3 PM\"<br>" +
            "• <b>Casual:</b> \"{app} says: {content}\" → \"Weather says: Weather Alert: Heavy rain expected in 2 hours\"<br><br>" +
            "<b>Time-Aware:</b><br>" +
            "• <b>Time Stamp:</b> \"{app} at {time}: {content}\" → \"Twitter at 14:30: @mitchib1440: Just released a new app update!\"<br>" +
            "• <b>Full Context:</b> \"{app} ({time}): {content}\" → \"Messages (14:30): Mitchi: I heard you're using SpeakThat!\"<br><br>" +
            "<b>Legacy / Compatibility:</b><br>" +
            activity.getString(R.string.behavior_speech_example_ticker_html) + "<br><br>" +
            "<b>Advanced Examples:</b><br>" +
            "• <b>Priority Aware:</b> \"{app} ({priority}): {content}\" → \"Gmail (High): New email from John Smith: Meeting tomorrow at 3 PM\"<br>" +
            "• <b>Category Aware:</b> \"{category} from {app}: {content}\" → \"Message from Messages: Mitchi: I heard you're using SpeakThat!\"<br>" +
            "• <b>Sender Focused:</b> \"{title} via {app}: {text}\" → \"Mitchi via Messages: I heard you're using SpeakThat!\"<br>" +
            "• <b>Detailed:</b> \"{app} - {title}: {bigtext}\" → \"Gmail - New email from John Smith: Meeting tomorrow at 3 PM - Please bring the quarterly report\"<br><br>" +
            "<b>How to Use:</b><br>" +
            "1. <b>Choose a preset</b> - Start with a format that's close to what you want<br>" +
            "2. <b>Customize</b> - Edit the format to add or remove elements<br>" +
            "3. <b>Preview</b> - See exactly how it will sound with the preview<br>" +
            "4. <b>Test</b> - Try it with real notifications<br>" +
            "5. <b>Refine</b> - Adjust based on what sounds best to you<br><br>" +
            "<b>Tips & Tricks:</b><br>" +
            "• <b>Mix and match</b> - Combine placeholders in any order<br>" +
            "• <b>Keep it concise</b> - Shorter formats are easier to understand quickly<br>" +
            "• <b>Use spacing</b> - Add spaces around placeholders for better pronunciation<br>" +
            "• <b>Test thoroughly</b> - Different apps may have different content formats<br>" +
            "• <b>Consider context</b> - Time-aware formats are great for busy periods<br><br>" +
            "<b>Recommended Starting Points:</b><br>" +
            "• <b>New users:</b> Start with \"Default\" or \"Minimal\"<br>" +
            "• <b>Power users:</b> Try \"Time Aware\" or custom formats<br>" +
            "• <b>Accessibility focus:</b> Use \"Formal\" or add priority information<br>" +
            "• <b>Quick scanning:</b> Use \"App Only\" or \"Content Only\"<br><br>" +
            "<b>Real App Examples:</b><br><br>" +
            "<b>Messages:</b><br>" +
            "• <b>Title:</b> \"Mitchi\"<br>" +
            "• <b>Text:</b> \"I heard you're using SpeakThat! Did it just speak that?\"<br>" +
            "• <b>BigText:</b> \"I heard you're using SpeakThat! Did it just speak that?\"<br>" +
            "• <b>Content:</b> \"Mitchi: I heard you're using SpeakThat! Did it just speak that?\"<br><br>" +
            "<b>Gmail:</b><br>" +
            "• <b>Title:</b> \"New email from John Smith\"<br>" +
            "• <b>Text:</b> \"Meeting tomorrow at 3 PM\"<br>" +
            "• <b>BigText:</b> \"Meeting tomorrow at 3 PM - Please bring the quarterly report and budget spreadsheet. We'll discuss Q4 projections.\"<br>" +
            "• <b>Content:</b> \"New email from John Smith: Meeting tomorrow at 3 PM\"<br><br>" +
            "<b>Weather:</b><br>" +
            "• <b>Title:</b> \"Weather Alert\"<br>" +
            "• <b>Text:</b> \"Heavy rain expected in 2 hours\"<br>" +
            "• <b>BigText:</b> \"Heavy rain expected in 2 hours - Bring an umbrella and expect delays. Rainfall amounts of 1-2 inches possible.\"<br>" +
            "• <b>Content:</b> \"Weather Alert: Heavy rain expected in 2 hours\"<br><br>" +
            "<b>Twitter:</b><br>" +
            "• <b>Title:</b> \"@mitchib1440\"<br>" +
            "• <b>Text:</b> \"Just released a new app update! Check it out\"<br>" +
            "• <b>BigText:</b> \"Just released a new app update! Check it out - The new version includes dark mode and improved performance. #AndroidDev #AppUpdate\"<br>" +
            "• <b>Content:</b> \"@mitchib1440: Just released a new app update! Check it out\"<br><br>" +
            "<b>Remember that different apps present their notifications in different ways.</b><br><br>";

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_title_speech_formats_guide)
            .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.button_use_recommended, (dialog, which) -> {
                store.trackDialogUsage("speech_template_recommended");
                binding.editCustomSpeechTemplate.setText("{app} notified you: {content}");
                binding.spinnerSpeechTemplate.setSelection(0);
                updateSpeechPreview();
                saveSpeechTemplate("{app} notified you: {content}");
                Toast.makeText(activity, "Set to recommended format: Default", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(R.string.button_got_it, null)
            .show();
    }
}
