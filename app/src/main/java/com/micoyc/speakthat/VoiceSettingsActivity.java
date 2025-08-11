package com.micoyc.speakthat;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import android.app.AlertDialog;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.view.View;
import android.widget.LinearLayout;
import java.util.HashSet;

public class VoiceSettingsActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    // SharedPreferences keys
    private static final String PREFS_NAME = "VoiceSettings";
    private static final String KEY_SPEECH_RATE = "speech_rate";
    private static final String KEY_PITCH = "pitch";
    private static final String KEY_TTS_VOLUME = "tts_volume";
    private static final String KEY_VOICE_NAME = "voice_name";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_AUDIO_USAGE = "audio_usage";
    private static final String KEY_CONTENT_TYPE = "content_type";
    private static final String KEY_SHOW_ADVANCED = "show_advanced_voice";
    private static final String KEY_TTS_LANGUAGE = "tts_language";


    // Default values
    private static final float DEFAULT_SPEECH_RATE = 1.0f;
    private static final float DEFAULT_PITCH = 1.0f;
    private static final float DEFAULT_TTS_VOLUME = 1.0f;
    private static final String DEFAULT_LANGUAGE = "en_US";
    private static final int DEFAULT_AUDIO_USAGE = 1; // USAGE_NOTIFICATION (recommended for Duck Audio)
    private static final int DEFAULT_CONTENT_TYPE = 0; // CONTENT_TYPE_SPEECH

    // UI Components
    private SeekBar speechRateSeekBar;
    private SeekBar pitchSeekBar;
    private SeekBar ttsVolumeSeekBar;
    private TextView speechRateValue;
    private TextView pitchValue;
    private TextView ttsVolumeValue;
    private Spinner voiceSpinner;
    private Spinner languageSpinner;
    private Spinner audioUsageSpinner;
    private Spinner contentTypeSpinner;
    private Spinner ttsLanguageSpinner;

    private Button previewButton;
    private Button resetButton;
    private ImageView btnVoiceInfo;
    private MaterialSwitch switchAdvancedVoice;
    private LinearLayout layoutAdvancedVoiceSection;

    // TTS and data
    private TextToSpeech textToSpeech;
    private boolean isTtsReady = false;
    private List<Voice> availableVoices = new ArrayList<>();
    private List<Locale> availableLanguages = new ArrayList<>();
    private SharedPreferences sharedPreferences;

    // Current settings
    private float currentSpeechRate = DEFAULT_SPEECH_RATE;
    private float currentPitch = DEFAULT_PITCH;
    private float currentTtsVolume = DEFAULT_TTS_VOLUME;
    private Voice currentVoice;
    private Locale currentLanguage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply saved theme FIRST before anything else
        SharedPreferences mainPrefs = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE);
        applySavedTheme(mainPrefs);
        
        setContentView(R.layout.activity_voice_settings);

        initializeViews();
        initializeSharedPreferences();
        initializeTextToSpeech();
        setupToolbar();
        setupSeekBars();
        setupButtons();
        // Restore advanced switch state
        boolean showAdvanced = sharedPreferences.getBoolean(KEY_SHOW_ADVANCED, false);
        switchAdvancedVoice.setChecked(showAdvanced);
        layoutAdvancedVoiceSection.setVisibility(showAdvanced ? View.VISIBLE : View.GONE);
        setupAdvancedSwitch();
        loadSavedSettings();
    }

    private void applySavedTheme(SharedPreferences prefs) {
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void initializeViews() {
        speechRateSeekBar = findViewById(R.id.speechRateSeekBar);
        speechRateValue = findViewById(R.id.speechRateValue);
        pitchSeekBar = findViewById(R.id.pitchSeekBar);
        pitchValue = findViewById(R.id.pitchValue);
        ttsVolumeSeekBar = findViewById(R.id.ttsVolumeSeekBar);
        ttsVolumeValue = findViewById(R.id.ttsVolumeValue);
        voiceSpinner = findViewById(R.id.voiceSpinner);
        languageSpinner = findViewById(R.id.languageSpinner);
        audioUsageSpinner = findViewById(R.id.audioUsageSpinner);
        contentTypeSpinner = findViewById(R.id.contentTypeSpinner);
        ttsLanguageSpinner = findViewById(R.id.ttsLanguageSpinner);
        previewButton = findViewById(R.id.previewButton);
        resetButton = findViewById(R.id.resetButton);
        btnVoiceInfo = findViewById(R.id.btnVoiceInfo);
        switchAdvancedVoice = findViewById(R.id.switchAdvancedVoice);
        layoutAdvancedVoiceSection = findViewById(R.id.layoutAdvancedVoiceSection);
        
        // Set up audio help button
        ImageView btnAudioHelp = findViewById(R.id.btnAudioHelp);
        btnAudioHelp.setOnClickListener(v -> showAudioHelpDialog());
    }

    private void initializeSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, this);
    }

    private void setupToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.title_voice_settings));
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
                    // Save immediately when user changes the setting
                    saveSpeechRate(currentSpeechRate);
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
                    // Save immediately when user changes the setting
                    savePitch(currentPitch);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // TTS Volume SeekBar (0.0 to 1.0)
        ttsVolumeSeekBar.setMax(100); // 0.0 to 1.0 in 0.01 increments
        ttsVolumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentTtsVolume = progress / 100.0f;
                ttsVolumeValue.setText(String.format("%.0f%%", currentTtsVolume * 100));
                if (fromUser && isTtsReady) {
                    // Apply volume through audio attributes
                    applyAudioAttributes();
                    // Save immediately when user changes the setting
                    saveTtsVolume(currentTtsVolume);
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
        // Remove save button functionality - settings save automatically now
        resetButton.setOnClickListener(v -> resetToDefaults());
        btnVoiceInfo.setOnClickListener(v -> showVoiceInfoDialog());
        
        // Translation assistance functionality
        LinearLayout cardTranslationHelp = findViewById(R.id.cardTranslationHelp);
        cardTranslationHelp.setOnClickListener(v -> sendTranslationEmail());
        
        // Multi-language warning functionality
        LinearLayout multiLanguageWarning = findViewById(R.id.multiLanguageWarning);
        multiLanguageWarning.setOnClickListener(v -> showMultiLanguageWarningDialog());
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
        availableLanguages.add(new Locale("pt", "PT")); // Portuguese (Portugal)
        availableLanguages.add(new Locale("pt", "BR")); // Portuguese (Brazil)

        setupVoiceSpinner();
        setupLanguageSpinner();
        setupTtsLanguageSpinner();
        setupAudioChannelSpinners();
    }

    private void setupVoiceSpinner() {
        List<String> voiceNames = new ArrayList<>();
        // Add default option at the top
        voiceNames.add("Default (Use Language Setting)");
        
        // Enhanced logging for voice debugging
        InAppLogger.log("VoiceSettings", "Setting up voice spinner - Total available voices: " + availableVoices.size());
        
        for (Voice voice : availableVoices) {
            StringBuilder displayName = new StringBuilder();
            // Language/locale
            Locale locale = voice.getLocale();
            if (locale != null) {
                displayName.append(locale.getDisplayLanguage());
                if (!locale.getCountry().isEmpty()) {
                    displayName.append(" (").append(locale.getCountry()).append(")");
                }
                displayName.append(" - ");
            }
            // Quality
            displayName.append(extractVoiceQuality(voice.getName()));
            // Gender (if available in name)
            String name = voice.getName().toLowerCase();
            if (name.contains("male")) {
                displayName.append(" - Male");
            } else if (name.contains("female")) {
                displayName.append(" - Female");
            }
            // Network/local
            if (voice.isNetworkConnectionRequired()) {
                displayName.append(" (Network)");
            } else {
                displayName.append(" (Local)");
            }
            // Fallback: show raw name if not enough info
            if (displayName.length() < 8) {
                displayName.append(voice.getName());
            }
            voiceNames.add(displayName.toString());
        }
        
        // Log summary of available languages
        Set<String> availableLanguages = new HashSet<>();
        for (Voice voice : availableVoices) {
            if (voice.getLocale() != null) {
                availableLanguages.add(voice.getLocale().getLanguage());
            }
        }
        InAppLogger.log("VoiceSettings", "Available language codes: " + String.join(", ", availableLanguages));
        
        ArrayAdapter<String> voiceAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, voiceNames);
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceSpinner.setAdapter(voiceAdapter);
        
        // Add listener to save voice selection automatically
        voiceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && (position - 1) < availableVoices.size()) {
                    currentVoice = availableVoices.get(position - 1);
                    saveVoice(currentVoice.getName());
                } else {
                    currentVoice = null;
                    saveVoice(""); // Clear voice setting
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                currentVoice = null;
                saveVoice(""); // Clear voice setting
            }
        });
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
    
    private static String extractLanguageFromVoiceName(String voiceName) {
        // Common language patterns in voice names
        if (voiceName.toLowerCase().contains("pt-") || voiceName.toLowerCase().contains("portuguese")) {
            return "pt";
        } else if (voiceName.toLowerCase().contains("es-") || voiceName.toLowerCase().contains("spanish")) {
            return "es";
        } else if (voiceName.toLowerCase().contains("fr-") || voiceName.toLowerCase().contains("french")) {
            return "fr";
        } else if (voiceName.toLowerCase().contains("de-") || voiceName.toLowerCase().contains("german")) {
            return "de";
        } else if (voiceName.toLowerCase().contains("it-") || voiceName.toLowerCase().contains("italian")) {
            return "it";
        } else if (voiceName.toLowerCase().contains("en-") || voiceName.toLowerCase().contains("english")) {
            return "en";
        } else if (voiceName.toLowerCase().contains("ja-") || voiceName.toLowerCase().contains("japanese")) {
            return "ja";
        } else if (voiceName.toLowerCase().contains("ko-") || voiceName.toLowerCase().contains("korean")) {
            return "ko";
        } else if (voiceName.toLowerCase().contains("zh-") || voiceName.toLowerCase().contains("chinese")) {
            return "zh";
        } else if (voiceName.toLowerCase().contains("ru-") || voiceName.toLowerCase().contains("russian")) {
            return "ru";
        } else if (voiceName.toLowerCase().contains("ar-") || voiceName.toLowerCase().contains("arabic")) {
            return "ar";
        } else if (voiceName.toLowerCase().contains("hi-") || voiceName.toLowerCase().contains("hindi")) {
            return "hi";
        } else if (voiceName.toLowerCase().contains("th-") || voiceName.toLowerCase().contains("thai")) {
            return "th";
        } else if (voiceName.toLowerCase().contains("tr-") || voiceName.toLowerCase().contains("turkish")) {
            return "tr";
        } else if (voiceName.toLowerCase().contains("pl-") || voiceName.toLowerCase().contains("polish")) {
            return "pl";
        } else if (voiceName.toLowerCase().contains("nl-") || voiceName.toLowerCase().contains("dutch")) {
            return "nl";
        } else if (voiceName.toLowerCase().contains("sv-") || voiceName.toLowerCase().contains("swedish")) {
            return "sv";
        } else if (voiceName.toLowerCase().contains("da-") || voiceName.toLowerCase().contains("danish")) {
            return "da";
        } else if (voiceName.toLowerCase().contains("no-") || voiceName.toLowerCase().contains("norwegian")) {
            return "no";
        } else if (voiceName.toLowerCase().contains("fi-") || voiceName.toLowerCase().contains("finnish")) {
            return "fi";
        } else if (voiceName.toLowerCase().contains("cs-") || voiceName.toLowerCase().contains("czech")) {
            return "cs";
        } else if (voiceName.toLowerCase().contains("sk-") || voiceName.toLowerCase().contains("slovak")) {
            return "sk";
        } else if (voiceName.toLowerCase().contains("hu-") || voiceName.toLowerCase().contains("hungarian")) {
            return "hu";
        } else if (voiceName.toLowerCase().contains("ro-") || voiceName.toLowerCase().contains("romanian")) {
            return "ro";
        } else if (voiceName.toLowerCase().contains("bg-") || voiceName.toLowerCase().contains("bulgarian")) {
            return "bg";
        } else if (voiceName.toLowerCase().contains("hr-") || voiceName.toLowerCase().contains("croatian")) {
            return "hr";
        } else if (voiceName.toLowerCase().contains("sl-") || voiceName.toLowerCase().contains("slovenian")) {
            return "sl";
        } else if (voiceName.toLowerCase().contains("et-") || voiceName.toLowerCase().contains("estonian")) {
            return "et";
        } else if (voiceName.toLowerCase().contains("lv-") || voiceName.toLowerCase().contains("latvian")) {
            return "lv";
        } else if (voiceName.toLowerCase().contains("lt-") || voiceName.toLowerCase().contains("lithuanian")) {
            return "lt";
        } else if (voiceName.toLowerCase().contains("el-") || voiceName.toLowerCase().contains("greek")) {
            return "el";
        } else if (voiceName.toLowerCase().contains("he-") || voiceName.toLowerCase().contains("hebrew")) {
            return "he";
        } else if (voiceName.toLowerCase().contains("id-") || voiceName.toLowerCase().contains("indonesian")) {
            return "id";
        } else if (voiceName.toLowerCase().contains("ms-") || voiceName.toLowerCase().contains("malay")) {
            return "ms";
        } else if (voiceName.toLowerCase().contains("vi-") || voiceName.toLowerCase().contains("vietnamese")) {
            return "vi";
        } else if (voiceName.toLowerCase().contains("bn-") || voiceName.toLowerCase().contains("bengali")) {
            return "bn";
        } else if (voiceName.toLowerCase().contains("ta-") || voiceName.toLowerCase().contains("tamil")) {
            return "ta";
        } else if (voiceName.toLowerCase().contains("te-") || voiceName.toLowerCase().contains("telugu")) {
            return "te";
        } else if (voiceName.toLowerCase().contains("kn-") || voiceName.toLowerCase().contains("kannada")) {
            return "kn";
        } else if (voiceName.toLowerCase().contains("ml-") || voiceName.toLowerCase().contains("malayalam")) {
            return "ml";
        } else if (voiceName.toLowerCase().contains("gu-") || voiceName.toLowerCase().contains("gujarati")) {
            return "gu";
        } else if (voiceName.toLowerCase().contains("pa-") || voiceName.toLowerCase().contains("punjabi")) {
            return "pa";
        } else if (voiceName.toLowerCase().contains("mr-") || voiceName.toLowerCase().contains("marathi")) {
            return "mr";
        } else if (voiceName.toLowerCase().contains("ur-") || voiceName.toLowerCase().contains("urdu")) {
            return "ur";
        } else if (voiceName.toLowerCase().contains("fa-") || voiceName.toLowerCase().contains("persian")) {
            return "fa";
        } else if (voiceName.toLowerCase().contains("uk-") || voiceName.toLowerCase().contains("ukrainian")) {
            return "uk";
        } else if (voiceName.toLowerCase().contains("be-") || voiceName.toLowerCase().contains("belarusian")) {
            return "be";
        } else if (voiceName.toLowerCase().contains("kk-") || voiceName.toLowerCase().contains("kazakh")) {
            return "kk";
        } else if (voiceName.toLowerCase().contains("uz-") || voiceName.toLowerCase().contains("uzbek")) {
            return "uz";
        } else if (voiceName.toLowerCase().contains("ky-") || voiceName.toLowerCase().contains("kyrgyz")) {
            return "ky";
        } else if (voiceName.toLowerCase().contains("tg-") || voiceName.toLowerCase().contains("tajik")) {
            return "tg";
        } else if (voiceName.toLowerCase().contains("tk-") || voiceName.toLowerCase().contains("turkmen")) {
            return "tk";
        } else if (voiceName.toLowerCase().contains("az-") || voiceName.toLowerCase().contains("azerbaijani")) {
            return "az";
        } else if (voiceName.toLowerCase().contains("ka-") || voiceName.toLowerCase().contains("georgian")) {
            return "ka";
        } else if (voiceName.toLowerCase().contains("am-") || voiceName.toLowerCase().contains("amharic")) {
            return "am";
        } else if (voiceName.toLowerCase().contains("sw-") || voiceName.toLowerCase().contains("swahili")) {
            return "sw";
        } else if (voiceName.toLowerCase().contains("zu-") || voiceName.toLowerCase().contains("zulu")) {
            return "zu";
        } else if (voiceName.toLowerCase().contains("af-") || voiceName.toLowerCase().contains("afrikaans")) {
            return "af";
        } else if (voiceName.toLowerCase().contains("is-") || voiceName.toLowerCase().contains("icelandic")) {
            return "is";
        } else if (voiceName.toLowerCase().contains("mt-") || voiceName.toLowerCase().contains("maltese")) {
            return "mt";
        } else if (voiceName.toLowerCase().contains("cy-") || voiceName.toLowerCase().contains("welsh")) {
            return "cy";
        } else if (voiceName.toLowerCase().contains("ga-") || voiceName.toLowerCase().contains("irish")) {
            return "ga";
        } else if (voiceName.toLowerCase().contains("eu-") || voiceName.toLowerCase().contains("basque")) {
            return "eu";
        } else if (voiceName.toLowerCase().contains("ca-") || voiceName.toLowerCase().contains("catalan")) {
            return "ca";
        } else if (voiceName.toLowerCase().contains("gl-") || voiceName.toLowerCase().contains("galician")) {
            return "gl";
        } else if (voiceName.toLowerCase().contains("sq-") || voiceName.toLowerCase().contains("albanian")) {
            return "sq";
        } else if (voiceName.toLowerCase().contains("mk-") || voiceName.toLowerCase().contains("macedonian")) {
            return "mk";
        } else if (voiceName.toLowerCase().contains("sr-") || voiceName.toLowerCase().contains("serbian")) {
            return "sr";
        } else if (voiceName.toLowerCase().contains("bs-") || voiceName.toLowerCase().contains("bosnian")) {
            return "bs";
        } else if (voiceName.toLowerCase().contains("me-") || voiceName.toLowerCase().contains("montenegrin")) {
            return "me";
        } else if (voiceName.toLowerCase().contains("mn-") || voiceName.toLowerCase().contains("mongolian")) {
            return "mn";
        } else if (voiceName.toLowerCase().contains("ne-") || voiceName.toLowerCase().contains("nepali")) {
            return "ne";
        } else if (voiceName.toLowerCase().contains("si-") || voiceName.toLowerCase().contains("sinhala")) {
            return "si";
        } else if (voiceName.toLowerCase().contains("my-") || voiceName.toLowerCase().contains("burmese")) {
            return "my";
        } else if (voiceName.toLowerCase().contains("km-") || voiceName.toLowerCase().contains("khmer")) {
            return "km";
        } else if (voiceName.toLowerCase().contains("lo-") || voiceName.toLowerCase().contains("lao")) {
            return "lo";
        } else if (voiceName.toLowerCase().contains("jw-") || voiceName.toLowerCase().contains("javanese")) {
            return "jw";
        } else if (voiceName.toLowerCase().contains("su-") || voiceName.toLowerCase().contains("sundanese")) {
            return "su";
        } else if (voiceName.toLowerCase().contains("ceb-") || voiceName.toLowerCase().contains("cebuano")) {
            return "ceb";
        } else if (voiceName.toLowerCase().contains("fil-") || voiceName.toLowerCase().contains("filipino")) {
            return "fil";
        } else if (voiceName.toLowerCase().contains("haw-") || voiceName.toLowerCase().contains("hawaiian")) {
            return "haw";
        } else if (voiceName.toLowerCase().contains("mi-") || voiceName.toLowerCase().contains("maori")) {
            return "mi";
        } else if (voiceName.toLowerCase().contains("sm-") || voiceName.toLowerCase().contains("samoan")) {
            return "sm";
        } else if (voiceName.toLowerCase().contains("to-") || voiceName.toLowerCase().contains("tongan")) {
            return "to";
        } else if (voiceName.toLowerCase().contains("fj-") || voiceName.toLowerCase().contains("fijian")) {
            return "fj";
        } else if (voiceName.toLowerCase().contains("yue-") || voiceName.toLowerCase().contains("cantonese")) {
            return "yue";
        } else if (voiceName.toLowerCase().contains("cmn-") || voiceName.toLowerCase().contains("mandarin")) {
            return "cmn";
        } else if (voiceName.toLowerCase().contains("nan-") || voiceName.toLowerCase().contains("minnan")) {
            return "nan";
        } else if (voiceName.toLowerCase().contains("hak-") || voiceName.toLowerCase().contains("hakka")) {
            return "hak";
        } else if (voiceName.toLowerCase().contains("bo-") || voiceName.toLowerCase().contains("tibetan")) {
            return "bo";
        } else if (voiceName.toLowerCase().contains("dz-") || voiceName.toLowerCase().contains("dzongkha")) {
            return "dz";
        } else if (voiceName.toLowerCase().contains("as-") || voiceName.toLowerCase().contains("assamese")) {
            return "as";
        } else if (voiceName.toLowerCase().contains("or-") || voiceName.toLowerCase().contains("odia")) {
            return "or";
        } else if (voiceName.toLowerCase().contains("sa-") || voiceName.toLowerCase().contains("sanskrit")) {
            return "sa";
        } else if (voiceName.toLowerCase().contains("sd-") || voiceName.toLowerCase().contains("sindhi")) {
            return "sd";
        } else if (voiceName.toLowerCase().contains("ks-") || voiceName.toLowerCase().contains("kashmiri")) {
            return "ks";
        } else if (voiceName.toLowerCase().contains("doi-") || voiceName.toLowerCase().contains("dogri")) {
            return "doi";
        } else if (voiceName.toLowerCase().contains("brx-") || voiceName.toLowerCase().contains("bodo")) {
            return "brx";
        } else if (voiceName.toLowerCase().contains("mni-") || voiceName.toLowerCase().contains("manipuri")) {
            return "mni";
        } else if (voiceName.toLowerCase().contains("sat-") || voiceName.toLowerCase().contains("santali")) {
            return "sat";
        } else if (voiceName.toLowerCase().contains("kok-") || voiceName.toLowerCase().contains("konkani")) {
            return "kok";
        } else if (voiceName.toLowerCase().contains("mai-") || voiceName.toLowerCase().contains("maithili")) {
            return "mai";
        }
        
        // If no pattern matches, return null
        return null;
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
        
        // Add listener to save language selection automatically
        languageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < availableLanguages.size()) {
                    currentLanguage = availableLanguages.get(position);
                    saveLanguage(currentLanguage.toString());
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Keep current language
            }
        });
    }

    private void setupTtsLanguageSpinner() {
        // Use the TtsLanguageManager to get only languages with actual translations
        List<TtsLanguageManager.TtsLanguage> supportedLanguages = TtsLanguageManager.getSupportedTtsLanguages(this);
        
        List<String> ttsLanguageNames = new ArrayList<>();
        for (TtsLanguageManager.TtsLanguage language : supportedLanguages) {
            ttsLanguageNames.add(language.displayName);
        }

        ArrayAdapter<String> ttsLanguageAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, ttsLanguageNames);
        ttsLanguageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ttsLanguageSpinner.setAdapter(ttsLanguageAdapter);
        
        InAppLogger.log("VoiceSettings", "TTS Language spinner setup with " + supportedLanguages.size() + " supported languages");
        
        // Add listener to save TTS language selection automatically
        ttsLanguageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < supportedLanguages.size()) {
                    String selectedDisplayName = ttsLanguageNames.get(position);
                    String languageCode = "system"; // Default
                    
                    for (TtsLanguageManager.TtsLanguage language : supportedLanguages) {
                        if (language.displayName.equals(selectedDisplayName)) {
                            languageCode = language.localeCode;
                            break;
                        }
                    }
                    
                    saveTtsLanguage(languageCode);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                saveTtsLanguage("system"); // Default to system
            }
        });
    }



    private void setupAudioChannelSpinners() {
        // Audio Usage Spinner
        String[] audioUsageOptions = {
            "Media",
            "Notification (Recommended)",
            "Alarm",
            "Voice Call",
            "Assistance"
        };
        ArrayAdapter<String> audioUsageAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, audioUsageOptions);
        audioUsageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        audioUsageSpinner.setAdapter(audioUsageAdapter);
        
        // Add listener to save audio usage selection automatically
        audioUsageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                saveAudioUsage(position);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                saveAudioUsage(DEFAULT_AUDIO_USAGE);
            }
        });

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
        
        // Add listener to save content type selection automatically
        contentTypeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                saveContentType(position);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                saveContentType(DEFAULT_CONTENT_TYPE);
            }
        });
    }

    private void setupAdvancedSwitch() {
        switchAdvancedVoice.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Show warning dialog
                new AlertDialog.Builder(this)
                                    .setTitle(getString(R.string.dialog_title_advanced_voice_options))
                .setMessage(getString(R.string.dialog_message_advanced_voice_options))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(getString(R.string.button_continue), (dialog, which) -> {
                        layoutAdvancedVoiceSection.setVisibility(View.VISIBLE);
                        saveAdvancedVoiceEnabled(true);
                    })
                    .setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                        switchAdvancedVoice.setChecked(false);
                        layoutAdvancedVoiceSection.setVisibility(View.GONE);
                        saveAdvancedVoiceEnabled(false);
                    })
                    .setCancelable(false)
                    .show();
            } else {
                layoutAdvancedVoiceSection.setVisibility(View.GONE);
                saveAdvancedVoiceEnabled(false);
            }
        });
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

        // Load TTS volume
        currentTtsVolume = sharedPreferences.getFloat(KEY_TTS_VOLUME, DEFAULT_TTS_VOLUME);
        int volumeProgress = (int) (currentTtsVolume * 100);
        ttsVolumeSeekBar.setProgress(volumeProgress);
        ttsVolumeValue.setText(String.format("%.0f%%", currentTtsVolume * 100));

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

        // Load TTS language (only if adapter is set up)
        String savedTtsLanguage = sharedPreferences.getString(KEY_TTS_LANGUAGE, "system");
        if (ttsLanguageSpinner.getAdapter() != null) {
            // Convert saved language code to display name for selection
            String displayName = TtsLanguageManager.getLanguageDisplayName(savedTtsLanguage);
            for (int i = 0; i < ttsLanguageSpinner.getAdapter().getCount(); i++) {
                if (ttsLanguageSpinner.getAdapter().getItem(i).equals(displayName)) {
                    ttsLanguageSpinner.setSelection(i);
                    break;
                }
            }
        }

        // Load audio settings with bounds checking
        int audioUsage = sharedPreferences.getInt(KEY_AUDIO_USAGE, DEFAULT_AUDIO_USAGE);
        if (audioUsageSpinner.getAdapter() != null && audioUsage < audioUsageSpinner.getAdapter().getCount()) {
            audioUsageSpinner.setSelection(audioUsage);
        } else {
            audioUsageSpinner.setSelection(DEFAULT_AUDIO_USAGE);
            InAppLogger.log("VoiceSettings", "Audio usage index out of bounds, using default: " + DEFAULT_AUDIO_USAGE);
        }
        
        int contentType = sharedPreferences.getInt(KEY_CONTENT_TYPE, DEFAULT_CONTENT_TYPE);
        if (contentTypeSpinner.getAdapter() != null && contentType < contentTypeSpinner.getAdapter().getCount()) {
            contentTypeSpinner.setSelection(contentType);
        } else {
            contentTypeSpinner.setSelection(DEFAULT_CONTENT_TYPE);
            InAppLogger.log("VoiceSettings", "Content type index out of bounds, using default: " + DEFAULT_CONTENT_TYPE);
        }



        // Load advanced voice switch state
        boolean showAdvanced = sharedPreferences.getBoolean(KEY_SHOW_ADVANCED, false);
        switchAdvancedVoice.setChecked(showAdvanced);
        layoutAdvancedVoiceSection.setVisibility(showAdvanced ? View.VISIBLE : View.GONE);
    }

    /**
     * Updates the TTS instance with current UI settings.
     * CRITICAL: This method respects the voice override logic - if a specific voice is selected,
     * it completely overrides any language setting. This ensures the "Specific Voice" option
     * always takes precedence over "Language & Region" as intended by the user.
     * 
     * This method is called during TTS initialization and when resetting to defaults.
     */
    private void updateUIWithCurrentSettings() {
        if (!isTtsReady) return;

        // Apply speech rate and pitch (these don't conflict with voice/language settings)
        textToSpeech.setSpeechRate(currentSpeechRate);
        textToSpeech.setPitch(currentPitch);
        
        // CRITICAL: Apply voice settings with proper override logic
        // The order matters - specific voice should override language setting
        if (currentVoice != null) {
            // Specific voice selected - apply it and skip language setting entirely
            // This ensures the user's voice choice is always respected
            int voiceResult = textToSpeech.setVoice(currentVoice);
            InAppLogger.log("VoiceSettings", "UI Update: Specific voice applied: " + currentVoice.getName() + " (result: " + voiceResult + ")");
        } else if (currentLanguage != null) {
            // No specific voice selected - apply language setting as fallback
            // This only happens when user hasn't chosen a specific voice
            int langResult = textToSpeech.setLanguage(currentLanguage);
            InAppLogger.log("VoiceSettings", "UI Update: Language applied: " + currentLanguage.toString() + " (result: " + langResult + ")");
        }
    }

    private void previewVoiceSettings() {
        if (!isTtsReady) {
            Toast.makeText(this, "Text-to-Speech not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        // Apply current UI settings
        applyCurrentUISettings();

        // Get the current TTS language setting
        String ttsLanguageCode = "system"; // Default
        if (ttsLanguageSpinner.getAdapter() != null) {
            int ttsLanguagePosition = ttsLanguageSpinner.getSelectedItemPosition();
            if (ttsLanguagePosition >= 0 && ttsLanguagePosition < ttsLanguageSpinner.getAdapter().getCount()) {
                String selectedDisplayName = ttsLanguageSpinner.getAdapter().getItem(ttsLanguagePosition).toString();
                
                // Convert display name to language code
                List<TtsLanguageManager.TtsLanguage> supportedLanguages = TtsLanguageManager.getSupportedTtsLanguages(this);
                for (TtsLanguageManager.TtsLanguage language : supportedLanguages) {
                    if (language.displayName.equals(selectedDisplayName)) {
                        ttsLanguageCode = language.localeCode;
                        break;
                    }
                }
            }
        }

        // Get localized preview text
        String previewText = TtsLanguageManager.getLocalizedTtsString(this, ttsLanguageCode, R.string.tts_voice_test);
        textToSpeech.speak(previewText, TextToSpeech.QUEUE_FLUSH, null, "preview");
        
        InAppLogger.log("VoiceSettings", "Voice preview played - Rate: " + currentSpeechRate + ", Pitch: " + currentPitch + ", Language: " + ttsLanguageCode);
    }
    


    /**
     * Applies the current UI settings to the TTS instance for preview.
     * CRITICAL: This method implements the voice override logic for the preview button.
     * If a specific voice is successfully selected, it completely overrides the language setting.
     * This ensures the preview matches exactly what will happen during actual notifications.
     */
    private void applyCurrentUISettings() {
        // Apply speech rate and pitch (these don't conflict with voice/language settings)
        textToSpeech.setSpeechRate(currentSpeechRate);
        textToSpeech.setPitch(currentPitch);

        // CRITICAL: Apply selected voice with override logic
        // Check if user selected a specific voice (not the "Default" option)
        int voicePosition = voiceSpinner.getSelectedItemPosition();
        boolean specificVoiceSelected = false;
        if (voicePosition > 0 && (voicePosition - 1) < availableVoices.size()) {
            // Subtract 1 because position 0 is "Default (Use Language Setting)" option
            currentVoice = availableVoices.get(voicePosition - 1);
            int voiceResult = textToSpeech.setVoice(currentVoice);
            specificVoiceSelected = (voiceResult == TextToSpeech.SUCCESS);
            InAppLogger.log("VoiceSettings", "Preview: Specific voice set to: " + currentVoice.getName() + " (result: " + voiceResult + ", success: " + specificVoiceSelected + ")");
        } else {
            currentVoice = null; // Default option selected - will use language setting
        }

        // CRITICAL: Apply language setting ONLY if no specific voice was successfully selected
        // This implements the override logic - specific voice takes complete precedence
        if (!specificVoiceSelected) {
            // No specific voice or voice selection failed - fall back to language setting
            int languagePosition = languageSpinner.getSelectedItemPosition();
            if (languagePosition >= 0 && languagePosition < availableLanguages.size()) {
                currentLanguage = availableLanguages.get(languagePosition);
                int langResult = textToSpeech.setLanguage(currentLanguage);
                InAppLogger.log("VoiceSettings", "Preview: Language set to: " + currentLanguage.toString() + " (result: " + langResult + ")");
            }
        } else {
            // Specific voice was successfully applied - skip language setting entirely
            // This ensures the voice choice is not overridden by language setting
            InAppLogger.log("VoiceSettings", "Preview: Skipping language setting - specific voice overrides it");
        }

        // Apply TTS language (this is separate from the TTS engine language)
        // The TTS language setting controls the language of app phrases, not the TTS engine
        if (ttsLanguageSpinner.getAdapter() != null) {
            int ttsLanguagePosition = ttsLanguageSpinner.getSelectedItemPosition();
            if (ttsLanguagePosition >= 0 && ttsLanguagePosition < ttsLanguageSpinner.getAdapter().getCount()) {
                String selectedTtsLanguage = ttsLanguageSpinner.getAdapter().getItem(ttsLanguagePosition).toString();
                // Note: This setting will be used by NotificationReaderService to determine which localized strings to use
                // It doesn't change the TTS engine language here
            }
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
            default: return android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
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

    // Note: saveSettings() method removed - settings now save automatically when changed

    private void resetToDefaults() {
        currentSpeechRate = DEFAULT_SPEECH_RATE;
        currentPitch = DEFAULT_PITCH;
        currentTtsVolume = DEFAULT_TTS_VOLUME;
        currentLanguage = new Locale("en", "US");

        // Reset UI
        speechRateSeekBar.setProgress((int) ((DEFAULT_SPEECH_RATE - 0.1f) * 100));
        pitchSeekBar.setProgress((int) ((DEFAULT_PITCH - 0.1f) * 100));
        ttsVolumeSeekBar.setProgress((int) (DEFAULT_TTS_VOLUME * 100));
        ttsVolumeValue.setText(String.format("%.0f%%", DEFAULT_TTS_VOLUME * 100));

        // Reset language spinner to English (United States)
        for (int i = 0; i < availableLanguages.size(); i++) {
            if (availableLanguages.get(i).toString().equals(DEFAULT_LANGUAGE)) {
                languageSpinner.setSelection(i);
                break;
            }
        }

        // Reset voice spinner to first item (Default)
        if (voiceSpinner.getAdapter().getCount() > 0) {
            voiceSpinner.setSelection(0);
        }

        // Reset TTS language spinner to "Default (Use System Language)"
        if (ttsLanguageSpinner.getAdapter() != null && ttsLanguageSpinner.getAdapter().getCount() > 0) {
            ttsLanguageSpinner.setSelection(0); // First item is always "Default (Use System Language)"
        }



        // Hide advanced options by default
        switchAdvancedVoice.setChecked(false);
        layoutAdvancedVoiceSection.setVisibility(View.GONE);
        saveAdvancedVoiceEnabled(false);

        // Reset audio settings with bounds checking
        if (audioUsageSpinner.getAdapter() != null && DEFAULT_AUDIO_USAGE < audioUsageSpinner.getAdapter().getCount()) {
            audioUsageSpinner.setSelection(DEFAULT_AUDIO_USAGE);
        } else {
            audioUsageSpinner.setSelection(0); // Fallback to first option
            InAppLogger.log("VoiceSettings", "Reset: Audio usage default out of bounds, using index 0");
        }
        
        if (contentTypeSpinner.getAdapter() != null && DEFAULT_CONTENT_TYPE < contentTypeSpinner.getAdapter().getCount()) {
            contentTypeSpinner.setSelection(DEFAULT_CONTENT_TYPE);
        } else {
            contentTypeSpinner.setSelection(0); // Fallback to first option
            InAppLogger.log("VoiceSettings", "Reset: Content type default out of bounds, using index 0");
        }

        updateUIWithCurrentSettings();
        Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show();
        InAppLogger.log("VoiceSettings", "Settings reset to defaults");
    }

    /**
     * CRITICAL: Public method to apply voice settings to any TTS instance.
     * This is the main method used by NotificationReaderService and other components.
     * 
     * IMPLEMENTATION DETAILS:
     * 1. If a specific voice is selected, it completely overrides any language setting
     * 2. If no specific voice, falls back to language setting
     * 3. If no language setting, uses system default
     * 4. If system default fails, falls back to US English
     * 
     * This ensures consistent behavior across all app components.
     */
    public static void applyVoiceSettings(TextToSpeech tts, SharedPreferences prefs) {
        if (tts == null || prefs == null) return;

        // Load all voice settings from preferences
        float speechRate = prefs.getFloat(KEY_SPEECH_RATE, DEFAULT_SPEECH_RATE);
        float pitch = prefs.getFloat(KEY_PITCH, DEFAULT_PITCH);
        float ttsVolume = prefs.getFloat(KEY_TTS_VOLUME, DEFAULT_TTS_VOLUME);
        String voiceName = prefs.getString(KEY_VOICE_NAME, "");
        String language = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);

        InAppLogger.log("VoiceSettings", "Applying voice settings - Rate: " + speechRate + ", Pitch: " + pitch + ", Volume: " + (ttsVolume * 100) + "%, Voice: " + voiceName + ", Language: " + language);

        // Apply speech rate and pitch (these don't conflict with voice/language settings)
        tts.setSpeechRate(speechRate);
        tts.setPitch(pitch);

        // CRITICAL: Apply language setting with proper override logic
        // The order is important - check for specific voice first, then fall back to language
        boolean languageApplied = false;
        if (!voiceName.isEmpty()) {
            // CRITICAL: Skip language setting if we have a specific voice
            // The specific voice will completely override any language setting
            // This ensures the user's voice choice is always respected
            InAppLogger.log("VoiceSettings", "Skipping language setting - specific voice will override it");
        } else if (!language.isEmpty() && !language.equals("")) {
            // No specific voice selected - apply the language setting
            // Parse language string (e.g., "en_US" -> Locale("en", "US"))
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
        } else {
            // No specific voice and no language setting - use system default as fallback
            int langResult = tts.setLanguage(Locale.getDefault());
            languageApplied = (langResult == TextToSpeech.LANG_AVAILABLE || langResult == TextToSpeech.LANG_COUNTRY_AVAILABLE || langResult == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE);
            InAppLogger.log("VoiceSettings", "Using system default language: " + Locale.getDefault().toString() + " (result: " + langResult + ", success: " + languageApplied + ")");
            
            // CRITICAL: If system default fails, fallback to US English
            // This ensures the app always has a working language setting
            if (!languageApplied) {
                InAppLogger.log("VoiceSettings", "System default language failed, falling back to US English");
                tts.setLanguage(Locale.US);
            }
        }

        // CRITICAL: Apply specific voice if we have one
        // This completely overrides any language setting that was applied above
        // This is the core of the voice override feature
        boolean voiceApplied = false;
        if (!voiceName.isEmpty()) {
            Set<Voice> voices = tts.getVoices();
            if (voices != null) {
                // Enhanced logging for voice debugging
                InAppLogger.log("VoiceSettings", "Attempting to set voice: " + voiceName);
                InAppLogger.log("VoiceSettings", "Total available voices: " + voices.size());
                
                // CRITICAL: Try to find and set the exact voice
                // This will override any language setting that was applied earlier
                for (Voice voice : voices) {
                    if (voice.getName().equals(voiceName)) {
                        int voiceResult = tts.setVoice(voice);
                        voiceApplied = (voiceResult == TextToSpeech.SUCCESS);
                        InAppLogger.log("VoiceSettings", "Specific voice set to: " + voice.getName() + " (result: " + voiceResult + ", success: " + voiceApplied + ")");
                        if (voiceApplied) {
                            // CRITICAL: This is the key message - specific voice overrides language
                            InAppLogger.log("VoiceSettings", "Note: Specific voice overrides language setting");
                        } else {
                            InAppLogger.log("VoiceSettings", "WARNING: Voice selection failed despite finding the voice");
                        }
                        break;
                    }
                }
                
                // CRITICAL: If exact voice not found, try language-based fallback
                // This provides a robust fallback system for when specific voices are unavailable
                if (!voiceApplied) {
                    InAppLogger.log("VoiceSettings", "Specific voice not found: " + voiceName + " (available voices: " + voices.size() + ")");
                    
                    // Extract language from voice name for intelligent fallback
                    // This allows us to find any voice with the same language
                    String fallbackLanguage = extractLanguageFromVoiceName(voiceName);
                    if (fallbackLanguage != null) {
                        InAppLogger.log("VoiceSettings", "Attempting language fallback for: " + fallbackLanguage);
                        
                        // CRITICAL: Try to find any voice with the same language
                        // This ensures users still get a voice in their preferred language
                        for (Voice voice : voices) {
                            if (voice.getLocale() != null && voice.getLocale().getLanguage().equals(fallbackLanguage)) {
                                int fallbackResult = tts.setVoice(voice);
                                if (fallbackResult == TextToSpeech.SUCCESS) {
                                    InAppLogger.log("VoiceSettings", "Language fallback voice applied: " + voice.getName() + " (Language: " + fallbackLanguage + ")");
                                    voiceApplied = true;
                                    break;
                                }
                            }
                        }
                        if (!voiceApplied) {
                            InAppLogger.log("VoiceSettings", "No fallback voice found for language: " + fallbackLanguage);
                        }
                    }
                }
            } else {
                InAppLogger.log("VoiceSettings", "No voices available from TTS engine");
            }
        }
        
        // CRITICAL: Log the final result of voice settings application
        // This helps with debugging and confirms the override logic is working
        if (voiceApplied) {
            // CRITICAL: This confirms the voice override is working
            InAppLogger.log("VoiceSettings", "Final result: Using specific voice (" + voiceName + ") - language setting ignored");
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
        InAppLogger.log("VoiceSettings", "Audio attributes applied - Usage: " + audioUsage + ", Content: " + contentType + ", Volume: " + (ttsVolume * 100) + "%");
    }

    private static int getAudioUsageFromIndexStatic(int index) {
        switch (index) {
            case 0: return android.media.AudioAttributes.USAGE_MEDIA;
            case 1: return android.media.AudioAttributes.USAGE_NOTIFICATION;
            case 2: return android.media.AudioAttributes.USAGE_ALARM;
            case 3: return android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
            case 4: return android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
            default: return android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
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
                "• Media: Uses media volume (may conflict with ducking)\n" +
                "• Notification (Recommended): Uses notification volume\n" +
                "• Alarm: Uses alarm volume\n" +
                "• Voice Call: Uses call volume (often works well)\n" +
                "• Assistance: Uses navigation volume\n\n" +
                
                "🎵 Content Type\n" +
                "Tells the system how to optimize audio processing:\n\n" +
                "• Speech: Optimized for voice clarity (recommended)\n" +
                "• Music: Optimized for music playback\n" +
                "• Notification Sound: For short notification sounds\n" +
                "• Sonification: For UI sounds and alerts\n\n" +
                
                "⚠️ Device Compatibility\n" +
                "Audio behavior varies significantly across devices and Android versions. " +
                "Some devices may not support ducking for certain streams or apps.\n\n" +
                
                "💡 Troubleshooting Audio Issues\n" +
                "If ducking isn't working well:\n" +
                "• Try 'Voice Call' or 'Notification' streams (most compatible)\n" +
                "• Avoid 'Media' stream as it may duck your TTS with your music\n" +
                "• Different apps respond differently to audio ducking\n" +
                "• The app automatically chooses the best ducking method for your device\n\n" +
                
                "🎯 Recommended Settings\n" +
                "For best results: Notification + Speech\n" +
                "Alternative: Voice Call + Speech";

        new android.app.AlertDialog.Builder(this)
                .setTitle("Audio Settings Help")
                .setMessage(helpText)
                .setPositiveButton("Got it", null)
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
                
        InAppLogger.log("VoiceSettings", "Audio help dialog shown");
    }

    /**
     * Shows comprehensive information about advanced voice options.
     * This dialog educates users about different voice types, their implications,
     * and important warnings before they rely on specific voices.
     * 
     * The dialog explains:
     * - Different voice types (Local, Network, Enhanced, Compact)
     * - Storage and performance implications
     * - Important warnings about network requirements
     * - Recommendations for different use cases
     * - How the voice override system works
     */
    private void showVoiceInfoDialog() {
        String infoText = "🎯 Advanced Voice Types Explained\n\n" +
                
                "🔹 Local Voices (High Quality)\n" +
                "• Stored on your device\n" +
                "• Work offline\n" +
                "• Fast and reliable\n" +
                "• Limited selection\n" +
                "• No data usage\n\n" +
                
                "🌐 Network Voices\n" +
                "• Downloaded on-demand\n" +
                "• Require internet for first use\n" +
                "• Cached locally after download\n" +
                "• Much larger selection\n" +
                "• ~10-50MB per voice\n\n" +
                
                "⚡ Enhanced Voices\n" +
                "• Higher quality than standard\n" +
                "• May use more processing power\n" +
                "• Better pronunciation\n" +
                "• Larger file size\n\n" +
                
                "📱 Compact Voices\n" +
                "• Smaller file size\n" +
                "• Faster loading\n" +
                "• Good for older devices\n" +
                "• Slightly lower quality\n\n" +
                
                "⚠️ Important Warnings\n" +
                "• Network voices require internet for first download\n" +
                "• Large voices may take time to download\n" +
                "• Some voices may not work on all devices\n" +
                "• Voice quality varies by device and Android version\n\n" +
                
                "💡 Recommendations\n" +
                "• Start with Language setting (simpler)\n" +
                "• Try Local voices first (reliable)\n" +
                "• Network voices for more options\n" +
                "• Test voices before relying on them\n" +
                "• Reset settings if you have issues\n\n" +
                
                "🔄 How It Works\n" +
                "• Specific Voice completely overrides Language setting\n" +
                "• Voice settings apply to all notifications\n" +
                "• Changes take effect immediately\n" +
                "• Settings are saved automatically";

        new android.app.AlertDialog.Builder(this)
                .setTitle("Advanced Voice Information")
                .setMessage(infoText)
                .setPositiveButton("Got it", null)
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
                
        InAppLogger.log("VoiceSettings", "Voice info dialog shown");
    }

    /**
     * Shows a dialog explaining the multi-language limitation of the Specific Voice feature.
     * 
     * The dialog explains:
     * - Current limitation of one voice at a time
     * - Potential issues with mixed-language content
     * - Recommendations for multi-language users
     * - Future plans for multi-language support
     */
    private void showMultiLanguageWarningDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.specific_voice_warning_dialog_title))
                .setMessage(getString(R.string.specific_voice_warning_dialog_message))
                .setPositiveButton(getString(R.string.specific_voice_warning_dialog_got_it), null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
                
        InAppLogger.log("VoiceSettings", "Multi-language warning dialog shown");
    }

    private void saveSpeechRate(float speechRate) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(KEY_SPEECH_RATE, speechRate);
        editor.apply();
        InAppLogger.log("VoiceSettings", "Speech rate saved: " + speechRate);
    }

    private void savePitch(float pitch) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(KEY_PITCH, pitch);
        editor.apply();
        InAppLogger.log("VoiceSettings", "Pitch saved: " + pitch);
    }

    private void saveVoice(String voiceName) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (!voiceName.isEmpty()) {
            editor.putString(KEY_VOICE_NAME, voiceName);
            InAppLogger.log("VoiceSettings", "Specific voice saved: " + voiceName);
        } else {
            editor.remove(KEY_VOICE_NAME);
            InAppLogger.log("VoiceSettings", "Voice setting cleared (using default)");
        }
        editor.apply();
    }

    private void saveLanguage(String language) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_LANGUAGE, language);
        editor.apply();
        InAppLogger.log("VoiceSettings", "Language saved: " + language);
    }

    private void saveTtsLanguage(String languageCode) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (!"system".equals(languageCode)) {
            editor.putString(KEY_TTS_LANGUAGE, languageCode);
            InAppLogger.log("VoiceSettings", "TTS language saved: " + languageCode);
        } else {
            editor.remove(KEY_TTS_LANGUAGE);
            InAppLogger.log("VoiceSettings", "TTS language cleared (using system default)");
        }
        editor.apply();
    }

    private void saveAudioUsage(int audioUsageIndex) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_AUDIO_USAGE, audioUsageIndex);
        editor.apply();
        InAppLogger.log("VoiceSettings", "Audio usage saved: " + audioUsageIndex);
    }

    private void saveContentType(int contentTypeIndex) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(KEY_CONTENT_TYPE, contentTypeIndex);
        editor.apply();
        InAppLogger.log("VoiceSettings", "Content type saved: " + contentTypeIndex);
    }

    private void saveAdvancedVoiceEnabled(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_SHOW_ADVANCED, enabled);
        editor.apply();
        InAppLogger.log("VoiceSettings", "Advanced voice options " + (enabled ? "enabled" : "disabled"));
    }

    private void saveTtsVolume(float volume) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat(KEY_TTS_VOLUME, volume);
        editor.apply();
        InAppLogger.log("VoiceSettings", "TTS volume saved: " + (volume * 100) + "%");
    }


    
    private void sendTranslationEmail() {
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SENDTO);
            intent.setData(android.net.Uri.parse("mailto:"));
            intent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"micoycbusiness@gmail.com"});
            intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "SpeakThat! Translations");
            
            StringBuilder body = new StringBuilder();
            body.append("I would like to help translate SpeakThat! to my language.\n\n");
            body.append("Languages I speak:\n\n");
            body.append("[Please replace this text with the languages you speak]\n\n");
            
            intent.putExtra(android.content.Intent.EXTRA_TEXT, body.toString());
            
            startActivity(intent);
            InAppLogger.logUserAction("Translation email opened from Voice Settings", "");
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open email app", Toast.LENGTH_SHORT).show();
            InAppLogger.logError("VoiceSettings", "Failed to open translation email: " + e.getMessage());
        }
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