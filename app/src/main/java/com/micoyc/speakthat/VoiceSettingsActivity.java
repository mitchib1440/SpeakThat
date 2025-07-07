package com.micoyc.speakthat;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class VoiceSettingsActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    // SharedPreferences keys
    private static final String PREFS_NAME = "VoiceSettings";
    private static final String KEY_SPEECH_RATE = "speech_rate";
    private static final String KEY_PITCH = "pitch";
    private static final String KEY_VOICE_NAME = "voice_name";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_AUDIO_USAGE = "audio_usage";
    private static final String KEY_CONTENT_TYPE = "content_type";

    // Default values
    private static final float DEFAULT_SPEECH_RATE = 1.0f;
    private static final float DEFAULT_PITCH = 1.0f;
    private static final String DEFAULT_LANGUAGE = "en_US";
    private static final int DEFAULT_AUDIO_USAGE = 0; // USAGE_MEDIA
    private static final int DEFAULT_CONTENT_TYPE = 0; // CONTENT_TYPE_SPEECH

    // UI Components
    private SeekBar speechRateSeekBar;
    private SeekBar pitchSeekBar;
    private TextView speechRateValue;
    private TextView pitchValue;
    private Spinner voiceSpinner;
    private Spinner languageSpinner;
    private Spinner audioUsageSpinner;
    private Spinner contentTypeSpinner;
    private Button previewButton;
    private Button saveButton;
    private Button resetButton;

    // TTS and data
    private TextToSpeech textToSpeech;
    private boolean isTtsReady = false;
    private List<Voice> availableVoices = new ArrayList<>();
    private List<Locale> availableLanguages = new ArrayList<>();
    private SharedPreferences sharedPreferences;

    // Current settings
    private float currentSpeechRate = DEFAULT_SPEECH_RATE;
    private float currentPitch = DEFAULT_PITCH;
    private Voice currentVoice;
    private Locale currentLanguage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_settings);

        initializeViews();
        initializeSharedPreferences();
        initializeTextToSpeech();
        setupToolbar();
        setupSeekBars();
        setupButtons();
        loadSavedSettings();
    }

    private void initializeViews() {
        speechRateSeekBar = findViewById(R.id.speechRateSeekBar);
        speechRateValue = findViewById(R.id.speechRateValue);
        pitchSeekBar = findViewById(R.id.pitchSeekBar);
        pitchValue = findViewById(R.id.pitchValue);
        voiceSpinner = findViewById(R.id.voiceSpinner);
        languageSpinner = findViewById(R.id.languageSpinner);
        audioUsageSpinner = findViewById(R.id.audioUsageSpinner);
        contentTypeSpinner = findViewById(R.id.contentTypeSpinner);
        previewButton = findViewById(R.id.previewButton);
        saveButton = findViewById(R.id.saveButton);
        resetButton = findViewById(R.id.resetButton);
        
        // Set up audio help button
        Button btnAudioHelp = findViewById(R.id.btnAudioHelp);
        btnAudioHelp.setOnClickListener(v -> showAudioHelpDialog());
    }

    private void initializeSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, this);
    }

    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle("Voice Settings");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true;
            setupVoicesAndLanguages();
            loadSavedSettings(); // Load saved settings after TTS is ready
            updateUIWithCurrentSettings();
            InAppLogger.log("VoiceSettings", "TextToSpeech initialized successfully");
        } else {
            InAppLogger.log("VoiceSettings", "TextToSpeech initialization failed");
            Toast.makeText(this, "Text-to-Speech initialization failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupSeekBars() {
        // Speech Rate SeekBar (0.1x to 3.0x)
        speechRateSeekBar.setMax(290); // (3.0 - 0.1) * 100
        speechRateSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentSpeechRate = 0.1f + (progress / 100.0f);
                speechRateValue.setText(String.format("%.1fx", currentSpeechRate));
                if (fromUser && isTtsReady) {
                    textToSpeech.setSpeechRate(currentSpeechRate);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Pitch SeekBar (0.1x to 2.0x)
        pitchSeekBar.setMax(190); // (2.0 - 0.1) * 100
        pitchSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentPitch = 0.1f + (progress / 100.0f);
                pitchValue.setText(String.format("%.1fx", currentPitch));
                if (fromUser && isTtsReady) {
                    textToSpeech.setPitch(currentPitch);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupButtons() {
        previewButton.setOnClickListener(v -> previewVoiceSettings());
        saveButton.setOnClickListener(v -> saveSettings());
        resetButton.setOnClickListener(v -> resetToDefaults());
    }

    private void setupVoicesAndLanguages() {
        if (!isTtsReady) return;

        // Get available voices
        Set<Voice> voices = textToSpeech.getVoices();
        if (voices != null) {
            availableVoices.clear();
            availableVoices.addAll(voices);
        }

        // Get available languages
        availableLanguages.clear();
        availableLanguages.add(Locale.US);
        availableLanguages.add(Locale.UK);
        availableLanguages.add(Locale.CANADA);
        availableLanguages.add(Locale.FRANCE);
        availableLanguages.add(Locale.GERMANY);
        availableLanguages.add(Locale.ITALY);
        availableLanguages.add(new Locale("es", "ES")); // Spanish

        setupVoiceSpinner();
        setupLanguageSpinner();
        setupAudioChannelSpinners();
    }

    private void setupVoiceSpinner() {
        List<String> voiceNames = new ArrayList<>();
        
        // Add default option at the top
        voiceNames.add("Default (Use Language Setting)");
        
        for (Voice voice : availableVoices) {
            String displayName = voice.getName();
            
            // Try to make voice names more user-friendly
            if (displayName.contains("en-gb")) {
                displayName = "English (UK) - " + extractVoiceQuality(displayName);
            } else if (displayName.contains("en-us")) {
                displayName = "English (US) - " + extractVoiceQuality(displayName);
            } else if (displayName.contains("en-au")) {
                displayName = "English (Australia) - " + extractVoiceQuality(displayName);
            } else if (displayName.contains("en-ca")) {
                displayName = "English (Canada) - " + extractVoiceQuality(displayName);
            } else if (displayName.contains("en-in")) {
                displayName = "English (India) - " + extractVoiceQuality(displayName);
            } else if (displayName.contains("fr-")) {
                displayName = "French - " + extractVoiceQuality(displayName);
            } else if (displayName.contains("es-")) {
                displayName = "Spanish - " + extractVoiceQuality(displayName);
            } else if (displayName.contains("de-")) {
                displayName = "German - " + extractVoiceQuality(displayName);
            } else if (displayName.contains("it-")) {
                displayName = "Italian - " + extractVoiceQuality(displayName);
            } else {
                // Fallback: just clean up the original name
                displayName = voice.getName().replace("-", " ").replace("_", " ");
                if (displayName.length() > 35) {
                    displayName = displayName.substring(0, 32) + "...";
                }
            }
            
            voiceNames.add(displayName);
        }

        ArrayAdapter<String> voiceAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, voiceNames);
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceSpinner.setAdapter(voiceAdapter);
    }
    
    private String extractVoiceQuality(String voiceName) {
        if (voiceName.contains("local")) {
            return "High Quality";
        } else if (voiceName.contains("network")) {
            return "Network";
        } else if (voiceName.contains("enhanced")) {
            return "Enhanced";
        } else if (voiceName.contains("compact")) {
            return "Compact";
        } else {
            return "Standard";
        }
    }

    private void setupLanguageSpinner() {
        List<String> languageNames = new ArrayList<>();
        for (Locale locale : availableLanguages) {
            languageNames.add(locale.getDisplayName());
        }

        ArrayAdapter<String> languageAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, languageNames);
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(languageAdapter);
    }

    private void setupAudioChannelSpinners() {
        // Audio Usage Spinner
        String[] audioUsageOptions = {
            "Media (Default)", 
            "Notification", 
            "Alarm", 
            "Voice Call", 
            "Assistance"
        };
        ArrayAdapter<String> audioUsageAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, audioUsageOptions);
        audioUsageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        audioUsageSpinner.setAdapter(audioUsageAdapter);

        // Content Type Spinner
        String[] contentTypeOptions = {
            "Speech (Default)", 
            "Music", 
            "Notification Sound", 
            "Sonification"
        };
        ArrayAdapter<String> contentTypeAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, contentTypeOptions);
        contentTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        contentTypeSpinner.setAdapter(contentTypeAdapter);
    }

    private void loadSavedSettings() {
        // Load speech rate
        currentSpeechRate = sharedPreferences.getFloat(KEY_SPEECH_RATE, DEFAULT_SPEECH_RATE);
        int speechRateProgress = (int) ((currentSpeechRate - 0.1f) * 100);
        speechRateSeekBar.setProgress(speechRateProgress);

        // Load pitch
        currentPitch = sharedPreferences.getFloat(KEY_PITCH, DEFAULT_PITCH);
        int pitchProgress = (int) ((currentPitch - 0.1f) * 100);
        pitchSeekBar.setProgress(pitchProgress);

        // Load voice
        String savedVoiceName = sharedPreferences.getString(KEY_VOICE_NAME, "");
        if (!savedVoiceName.isEmpty()) {
            // Look for the saved voice in the available voices (starting from index 1 since 0 is "Default")
            boolean voiceFound = false;
            for (int i = 0; i < availableVoices.size(); i++) {
                if (availableVoices.get(i).getName().equals(savedVoiceName)) {
                    voiceSpinner.setSelection(i + 1); // +1 because of "Default" option at index 0
                    currentVoice = availableVoices.get(i);
                    voiceFound = true;
                    InAppLogger.log("VoiceSettings", "Loaded saved voice: " + savedVoiceName + " at position " + (i + 1));
                    break;
                }
            }
            if (!voiceFound) {
                voiceSpinner.setSelection(0); // Default to "Default" option
                currentVoice = null;
                InAppLogger.log("VoiceSettings", "Saved voice not found: " + savedVoiceName + ", using default");
            }
        } else {
            voiceSpinner.setSelection(0); // Default to "Default" option
            currentVoice = null;
            InAppLogger.log("VoiceSettings", "No saved voice, using default");
        }

        // Load language
        String savedLanguage = sharedPreferences.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
        for (int i = 0; i < availableLanguages.size(); i++) {
            if (availableLanguages.get(i).toString().equals(savedLanguage)) {
                languageSpinner.setSelection(i);
                currentLanguage = availableLanguages.get(i);
                break;
            }
        }

        // Load audio settings
        int audioUsage = sharedPreferences.getInt(KEY_AUDIO_USAGE, DEFAULT_AUDIO_USAGE);
        audioUsageSpinner.setSelection(audioUsage);
        
        int contentType = sharedPreferences.getInt(KEY_CONTENT_TYPE, DEFAULT_CONTENT_TYPE);
        contentTypeSpinner.setSelection(contentType);
    }

    private void updateUIWithCurrentSettings() {
        if (!isTtsReady) return;

        // Apply current settings to TTS
        textToSpeech.setSpeechRate(currentSpeechRate);
        textToSpeech.setPitch(currentPitch);
        
        if (currentLanguage != null) {
            textToSpeech.setLanguage(currentLanguage);
        }
        
        if (currentVoice != null) {
            textToSpeech.setVoice(currentVoice);
        }
    }

    private void previewVoiceSettings() {
        if (!isTtsReady) {
            Toast.makeText(this, "Text-to-Speech not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        // Apply current UI settings
        applyCurrentUISettings();

        // Preview text
        String previewText = "This is how your notifications will sound with these voice settings.";
        textToSpeech.speak(previewText, TextToSpeech.QUEUE_FLUSH, null, "preview");
        
        InAppLogger.log("VoiceSettings", "Voice preview played - Rate: " + currentSpeechRate + ", Pitch: " + currentPitch);
    }

    private void applyCurrentUISettings() {
        // Apply speech rate and pitch
        textToSpeech.setSpeechRate(currentSpeechRate);
        textToSpeech.setPitch(currentPitch);

        // Apply selected voice
        int voicePosition = voiceSpinner.getSelectedItemPosition();
        if (voicePosition > 0 && (voicePosition - 1) < availableVoices.size()) {
            // Subtract 1 because position 0 is "Default" option
            currentVoice = availableVoices.get(voicePosition - 1);
            textToSpeech.setVoice(currentVoice);
        } else {
            currentVoice = null; // Default option selected
        }

        // Apply selected language
        int languagePosition = languageSpinner.getSelectedItemPosition();
        if (languagePosition >= 0 && languagePosition < availableLanguages.size()) {
            currentLanguage = availableLanguages.get(languagePosition);
            textToSpeech.setLanguage(currentLanguage);
        }

        // Apply audio attributes
        applyAudioAttributes();
    }

    private void applyAudioAttributes() {
        int audioUsageIndex = audioUsageSpinner.getSelectedItemPosition();
        int contentTypeIndex = contentTypeSpinner.getSelectedItemPosition();
        
        // Map spinner indices to AudioAttributes constants
        int audioUsage = getAudioUsageFromIndex(audioUsageIndex);
        int contentType = getContentTypeFromIndex(contentTypeIndex);
        
        android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
            .setUsage(audioUsage)
            .setContentType(contentType)
            .build();
            
        textToSpeech.setAudioAttributes(audioAttributes);
    }

    private int getAudioUsageFromIndex(int index) {
        switch (index) {
            case 0: return android.media.AudioAttributes.USAGE_MEDIA;
            case 1: return android.media.AudioAttributes.USAGE_NOTIFICATION;
            case 2: return android.media.AudioAttributes.USAGE_ALARM;
            case 3: return android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
            case 4: return android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
            default: return android.media.AudioAttributes.USAGE_MEDIA;
        }
    }

    private int getContentTypeFromIndex(int index) {
        switch (index) {
            case 0: return android.media.AudioAttributes.CONTENT_TYPE_SPEECH;
            case 1: return android.media.AudioAttributes.CONTENT_TYPE_MUSIC;
            case 2: return android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION;
            case 3: return android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION;
            default: return android.media.AudioAttributes.CONTENT_TYPE_SPEECH;
        }
    }

    private void saveSettings() {
        // Update current values from UI before saving
        currentSpeechRate = 0.1f + (speechRateSeekBar.getProgress() / 100.0f);
        currentPitch = 0.1f + (pitchSeekBar.getProgress() / 100.0f);
        
        // Get currently selected voice and language
        int voicePosition = voiceSpinner.getSelectedItemPosition();
        if (voicePosition > 0 && (voicePosition - 1) < availableVoices.size()) {
            // Subtract 1 because position 0 is "Default" option
            currentVoice = availableVoices.get(voicePosition - 1);
        } else {
            currentVoice = null; // Default option selected
        }
        
        int languagePosition = languageSpinner.getSelectedItemPosition();
        if (languagePosition >= 0 && languagePosition < availableLanguages.size()) {
            currentLanguage = availableLanguages.get(languagePosition);
        }
        
        applyCurrentUISettings();

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(KEY_SPEECH_RATE, currentSpeechRate);
        editor.putFloat(KEY_PITCH, currentPitch);
        
        // Save voice setting (if a specific voice is selected)
        if (currentVoice != null && voicePosition > 0) { // Position 0 is typically "Default" or no selection
            editor.putString(KEY_VOICE_NAME, currentVoice.getName());
            InAppLogger.log("VoiceSettings", "Saving specific voice: " + currentVoice.getName());
        } else {
            // Clear voice setting if default/none is selected
            editor.remove(KEY_VOICE_NAME);
            InAppLogger.log("VoiceSettings", "Cleared specific voice setting (using default)");
        }
        
        // Save language setting (always save the selected language)
        if (currentLanguage != null) {
            editor.putString(KEY_LANGUAGE, currentLanguage.toString());
            InAppLogger.log("VoiceSettings", "Saving language: " + currentLanguage.toString());
        } else {
            InAppLogger.log("VoiceSettings", "No language selected to save");
        }

        // Save audio settings
        editor.putInt(KEY_AUDIO_USAGE, audioUsageSpinner.getSelectedItemPosition());
        editor.putInt(KEY_CONTENT_TYPE, contentTypeSpinner.getSelectedItemPosition());
        
        editor.apply();

        Toast.makeText(this, "Voice settings saved", Toast.LENGTH_SHORT).show();
        InAppLogger.log("VoiceSettings", "Settings saved - Rate: " + currentSpeechRate + ", Pitch: " + currentPitch + 
                       ", Voice: " + (currentVoice != null && voicePosition > 0 ? currentVoice.getName() : "default") + 
                       ", Language: " + (currentLanguage != null ? currentLanguage.toString() : "none"));
    }

    private void resetToDefaults() {
        currentSpeechRate = DEFAULT_SPEECH_RATE;
        currentPitch = DEFAULT_PITCH;
        currentLanguage = Locale.getDefault();

        // Reset UI
        speechRateSeekBar.setProgress((int) ((DEFAULT_SPEECH_RATE - 0.1f) * 100));
        pitchSeekBar.setProgress((int) ((DEFAULT_PITCH - 0.1f) * 100));
        
        // Reset language spinner to default
        for (int i = 0; i < availableLanguages.size(); i++) {
            if (availableLanguages.get(i).equals(Locale.getDefault()) || 
                availableLanguages.get(i).equals(Locale.US)) {
                languageSpinner.setSelection(i);
                break;
            }
        }

        // Reset voice spinner to first item
        if (voiceSpinner.getAdapter().getCount() > 0) {
            voiceSpinner.setSelection(0);
        }

        // Reset audio settings
        audioUsageSpinner.setSelection(DEFAULT_AUDIO_USAGE);
        contentTypeSpinner.setSelection(DEFAULT_CONTENT_TYPE);

        updateUIWithCurrentSettings();
        Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show();
        InAppLogger.log("VoiceSettings", "Settings reset to defaults");
    }

    // Public method to get current settings for use by NotificationReaderService
    public static void applyVoiceSettings(TextToSpeech tts, SharedPreferences prefs) {
        if (tts == null || prefs == null) return;

        float speechRate = prefs.getFloat(KEY_SPEECH_RATE, DEFAULT_SPEECH_RATE);
        float pitch = prefs.getFloat(KEY_PITCH, DEFAULT_PITCH);
        String voiceName = prefs.getString(KEY_VOICE_NAME, "");
        String language = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);

        InAppLogger.log("VoiceSettings", "Applying voice settings - Rate: " + speechRate + ", Pitch: " + pitch + ", Voice: " + voiceName + ", Language: " + language);

        tts.setSpeechRate(speechRate);
        tts.setPitch(pitch);

        // Apply language setting if we have one
        boolean languageApplied = false;
        if (!language.isEmpty() && !language.equals("")) {
            Locale targetLocale = null;
            String[] langParts = language.split("_");
            if (langParts.length >= 2) {
                targetLocale = new Locale(langParts[0], langParts[1]);
            } else if (langParts.length == 1) {
                targetLocale = new Locale(langParts[0]);
            } else {
                targetLocale = Locale.getDefault();
            }
            
            int langResult = tts.setLanguage(targetLocale);
            languageApplied = (langResult == TextToSpeech.LANG_AVAILABLE || langResult == TextToSpeech.LANG_COUNTRY_AVAILABLE || langResult == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE);
            InAppLogger.log("VoiceSettings", "Language set to: " + targetLocale.toString() + " (result: " + langResult + ", success: " + languageApplied + ")");
        }

        // Apply specific voice if we have one (this may override the language setting, which is intended)
        boolean voiceApplied = false;
        if (!voiceName.isEmpty()) {
            Set<Voice> voices = tts.getVoices();
            if (voices != null) {
                for (Voice voice : voices) {
                    if (voice.getName().equals(voiceName)) {
                        int voiceResult = tts.setVoice(voice);
                        voiceApplied = (voiceResult == TextToSpeech.SUCCESS);
                        InAppLogger.log("VoiceSettings", "Specific voice set to: " + voice.getName() + " (result: " + voiceResult + ", success: " + voiceApplied + ")");
                        if (voiceApplied) {
                            InAppLogger.log("VoiceSettings", "Note: Specific voice overrides language setting");
                        }
                        break;
                    }
                }
                if (!voiceApplied) {
                    InAppLogger.log("VoiceSettings", "Specific voice not found: " + voiceName + " (available voices: " + voices.size() + ")");
                }
            } else {
                InAppLogger.log("VoiceSettings", "No voices available from TTS engine");
            }
        }
        
        // Log what was actually applied
        if (voiceApplied) {
            InAppLogger.log("VoiceSettings", "Final result: Using specific voice (" + voiceName + ")");
        } else if (languageApplied) {
            InAppLogger.log("VoiceSettings", "Final result: Using language setting (" + language + ")");
        } else {
            InAppLogger.log("VoiceSettings", "Final result: Using TTS defaults (no custom voice or language applied)");
        }

        // Apply audio attributes
        int audioUsageIndex = prefs.getInt(KEY_AUDIO_USAGE, DEFAULT_AUDIO_USAGE);
        int contentTypeIndex = prefs.getInt(KEY_CONTENT_TYPE, DEFAULT_CONTENT_TYPE);
        
        int audioUsage = getAudioUsageFromIndexStatic(audioUsageIndex);
        int contentType = getContentTypeFromIndexStatic(contentTypeIndex);
        
        android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
            .setUsage(audioUsage)
            .setContentType(contentType)
            .build();
            
        tts.setAudioAttributes(audioAttributes);
        InAppLogger.log("VoiceSettings", "Audio attributes applied - Usage: " + audioUsage + ", Content: " + contentType);
    }

    private static int getAudioUsageFromIndexStatic(int index) {
        switch (index) {
            case 0: return android.media.AudioAttributes.USAGE_MEDIA;
            case 1: return android.media.AudioAttributes.USAGE_NOTIFICATION;
            case 2: return android.media.AudioAttributes.USAGE_ALARM;
            case 3: return android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
            case 4: return android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
            default: return android.media.AudioAttributes.USAGE_MEDIA;
        }
    }

    private static int getContentTypeFromIndexStatic(int index) {
        switch (index) {
            case 0: return android.media.AudioAttributes.CONTENT_TYPE_SPEECH;
            case 1: return android.media.AudioAttributes.CONTENT_TYPE_MUSIC;
            case 2: return android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION;
            case 3: return android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION;
            default: return android.media.AudioAttributes.CONTENT_TYPE_SPEECH;
        }
    }

    private void showAudioHelpDialog() {
        String helpText = "🔊 Audio Stream Type\n" +
                "Controls which volume slider affects notification speech:\n\n" +
                "• Media: Uses media volume (recommended)\n" +
                "• Notification: Uses notification volume\n" +
                "• Alarm: Uses alarm volume\n" +
                "• Voice Call: Uses call volume\n" +
                "• Assistance: Uses navigation volume\n\n" +
                
                "🎵 Content Type\n" +
                "Tells the system how to optimize audio processing:\n\n" +
                "• Speech: Optimized for voice clarity (recommended)\n" +
                "• Music: Optimized for music playback\n" +
                "• Notification Sound: For short notification sounds\n" +
                "• Sonification: For UI sounds and alerts\n\n" +
                
                "💡 Recommendation\n" +
                "For the best notification reading experience, use:\n" +
                "Media + Speech\n\n" +
                
                "This ensures notifications use your media volume (which you're used to controlling) " +
                "and are optimized for clear speech rather than music.";

        new android.app.AlertDialog.Builder(this)
                .setTitle("Audio Settings Help")
                .setMessage(helpText)
                .setPositiveButton("Got it", null)
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
                
        InAppLogger.log("VoiceSettings", "Audio help dialog shown");
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
} 