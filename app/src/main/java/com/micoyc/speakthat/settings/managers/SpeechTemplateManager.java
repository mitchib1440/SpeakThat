package com.micoyc.speakthat.settings.managers;

import android.content.Context;
import com.micoyc.speakthat.SpeechTemplateConstants;
import com.micoyc.speakthat.TtsLanguageManager;

public class SpeechTemplateManager {
    private static final String[] TEMPLATE_PRESETS = {
        "Default", "Minimal", "Formal", "Casual", "Time Aware", "Content Only", "App Only", "Varied", "Custom"
    };

    private static final String[] TEMPLATE_KEYS = {
        "tts_format_default",
        "tts_format_minimal",
        "tts_format_formal",
        "tts_format_casual",
        "tts_format_time_aware",
        "tts_format_content_only",
        "tts_format_app_only",
        "VARIED",
        "CUSTOM"
    };

    private static final String[] VARIED_FORMATS = {
        "{app} notified you: {content}",
        "{app} reported: {content}",
        "Notification from {app}, saying {content}",
        "Notification from {app}: {content}",
        "{app} alerts you: {content}",
        "Update from {app}: {content}",
        "{app} says: {content}",
        "{app} notification: {content}",
        "New notification: {app}: {content}",
        "New from {app}: {content}",
        "{app} said: {content}",
        "{app} updated you: {content}",
        "New notification from {app}: saying: {content}",
        "New update from {app}: {content}",
        "{app}: {content}"
    };

    private final Context context;

    public SpeechTemplateManager(Context context) {
        this.context = context;
    }

    public String[] getTemplatePresets() {
        return TEMPLATE_PRESETS;
    }

    public String[] getTemplateKeys() {
        return TEMPLATE_KEYS;
    }

    public String[] getVariedFormats() {
        return VARIED_FORMATS;
    }

    public String getLocalizedTemplateValue(String templateKey) {
        if ("VARIED".equals(templateKey) || "CUSTOM".equals(templateKey)) {
            return templateKey;
        }

        android.content.SharedPreferences voiceSettingsPrefs =
            context.getSharedPreferences("VoiceSettings", Context.MODE_PRIVATE);
        String ttsLanguageCode = voiceSettingsPrefs.getString("tts_language", "system");

        return TtsLanguageManager.getLocalizedTtsStringByName(context, ttsLanguageCode, templateKey);
    }

    public String resolveTemplateKey(String savedTemplate) {
        if (savedTemplate == null || savedTemplate.trim().isEmpty()) {
            return SpeechTemplateConstants.TEMPLATE_KEY_CUSTOM;
        }
        if (SpeechTemplateConstants.TEMPLATE_KEY_VARIED.equals(savedTemplate)) {
            return SpeechTemplateConstants.TEMPLATE_KEY_VARIED;
        }
        String match = TtsLanguageManager.findMatchingStringKey(
            context,
            savedTemplate,
            SpeechTemplateConstants.RESOURCE_TEMPLATE_KEYS
        );
        return match != null ? match : SpeechTemplateConstants.TEMPLATE_KEY_CUSTOM;
    }

    public boolean isResourceTemplateKey(String templateKey) {
        if (templateKey == null) {
            return false;
        }
        for (String key : SpeechTemplateConstants.RESOURCE_TEMPLATE_KEYS) {
            if (templateKey.equals(key)) {
                return true;
            }
        }
        return false;
    }

    public int getTemplateIndex(String templateKey) {
        if (templateKey == null) {
            return -1;
        }
        for (int i = 0; i < TEMPLATE_KEYS.length; i++) {
            if (templateKey.equals(TEMPLATE_KEYS[i])) {
                return i;
            }
        }
        return -1;
    }

    public String generateSpeechPreview(String template) {
        String preview = template
            .replace("{app}", "**Messages**")
            .replace("{package}", "**com.google.android.apps.messaging**")
            .replace("{content}", "**Mitchi: I heard you're using SpeakThat! Did it just speak that?**")
            .replace("{title}", "**Mitchi**")
            .replace("{text}", "**I heard you're using SpeakThat! Did it just speak that?**")
            .replace("{bigtext}", "**Mitchi: I heard you're using SpeakThat! Did it just speak that?**")
            .replace("{summary}", "**1 new message**")
            .replace("{info}", "**Tap to view**")
            .replace("{ticker}", "**Legacy ticker text**")
            .replace("{time}", "**14:30**")
            .replace("{date}", "**Dec 15**")
            .replace("{timestamp}", "**14:30 Dec 15**")
            .replace("{priority}", "**High**")
            .replace("{category}", "**Message**")
            .replace("{channel}", "**Messages**");

        StringBuilder result = new StringBuilder();
        boolean inBold = false;
        for (int i = 0; i < preview.length(); i++) {
            if (i < preview.length() - 1 && preview.charAt(i) == '*' && preview.charAt(i + 1) == '*') {
                if (inBold) {
                    result.append("</b>");
                } else {
                    result.append("<b>");
                }
                inBold = !inBold;
                i++;
            } else {
                result.append(preview.charAt(i));
            }
        }
        return result.toString();
    }

    public String buildTemplateTestResults(String template, boolean isVariedMode) {
        String[] testTitles = {
            "Mitchi",
            "SpeakThat! Bug Report",
            "@mitchib1440",
            "Weather Alert",
            "Battery low (15% remaining)",
            "'Thank you for using SpeakThat!'"
        };

        String[] testTexts = {
            "I heard you're using SpeakThat! Did it just speak that?",
            "Thanks for submitting a bug report! We'll get this one squashed in the next release! - Mitchi",
            "Just released a new app update! Check it out",
            "Heavy rain expected in 2 hours",
            "connect charger",
            "@mitchib1440 replied"
        };

        String[] testContents = {
            "Mitchi: I heard you're using SpeakThat! Did it just speak that?",
            "SpeakThat! Bug Report: Thanks for submitting a bug report! We'll get this one squashed in the next release! - Mitchi",
            "@mitchib1440: Just released a new app update! Check it out",
            "Weather Alert: Heavy rain expected in 2 hours",
            "Battery low (15% remaining) - connect charger",
            "@mitchib1440 replied: 'Thank you for using SpeakThat!'"
        };

        String[] testApps = {
            "Messages",
            "Gmail",
            "Twitter",
            "Weather",
            "System",
            "YouTube"
        };

        StringBuilder testResults = new StringBuilder();
        if (isVariedMode) {
            testResults.append("Your format: <b>Varied (Random selection)</b><br><br>");
            testResults.append("<b>How it would sound (random format for each):</b><br><br>");
        } else {
            testResults.append("Your format: <b>").append(template).append("</b><br><br>");
            testResults.append("<b>How it would sound:</b><br><br>");
        }

        for (int i = 0; i < testContents.length; i++) {
            String templateToUse = template;
            if (isVariedMode) {
                templateToUse = VARIED_FORMATS[i % VARIED_FORMATS.length];
            }

            String result = templateToUse
                .replace("{app}", testApps[i])
                .replace("{package}", "com.test." + testApps[i].toLowerCase())
                .replace("{content}", testContents[i])
                .replace("{title}", testTitles[i])
                .replace("{text}", testTexts[i])
                .replace("{bigtext}", testContents[i])
                .replace("{summary}", "1 new notification")
                .replace("{info}", "Tap to view")
                .replace("{time}", "14:40")
                .replace("{date}", "Dec 15")
                .replace("{timestamp}", "14:40 Dec 15")
                .replace("{priority}", "High")
                .replace("{category}", "Message")
                .replace("{channel}", "Notifications");

            if (isVariedMode) {
                testResults.append("• <b>").append(testApps[i]).append(":</b> \"")
                    .append(result).append("\" <i>(using: ").append(templateToUse)
                    .append(")</i><br><br>");
            } else {
                testResults.append("• <b>").append(testApps[i]).append(":</b> \"")
                    .append(result).append("\"<br><br>");
            }
        }

        if (isVariedMode) {
            testResults.append("<b>💡 Varied Mode Tips:</b><br>");
            testResults.append("• Each notification gets a random format from 15 options<br>");
            testResults.append("• Adds variety and personality to your notifications<br>");
            testResults.append("• No configuration needed - just enjoy the variety!<br>");
            testResults.append("• Test with real notifications to see the full effect");
        } else {
            testResults.append("<b>💡 Tips:</b><br>");
            testResults.append("• Test with real notifications to see actual results<br>");
            testResults.append("• Adjust spacing and punctuation for better pronunciation<br>");
            testResults.append("• Consider how it sounds when spoken quickly");
        }

        return testResults.toString();
    }
}
