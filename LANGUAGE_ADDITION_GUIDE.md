# SpeakThat Language Addition Guide

This guide provides comprehensive instructions for adding new languages to the SpeakThat app. The app uses a template-based localization system that supports multiple languages for Text-to-Speech (TTS) content.

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Step-by-Step Process](#step-by-step-process)
4. [Required String Resources](#required-string-resources)
5. [File Structure](#file-structure)
6. [Testing Your Language](#testing-your-language)
7. [Troubleshooting](#troubleshooting)
8. [Language Code Reference](#language-code-reference)
9. [Example: Adding French](#example-adding-french)

## Overview

SpeakThat uses a centralized localization system with:
- **Template strings** with placeholders (`{app}`, `{content}`, `{time}`)
- **TtsLanguageManager** for language management
- **Dynamic TTS language selection** in Voice Settings
- **Automatic fallback** to English for missing translations

**Important Note**: The app has been cleaned up to use ONLY template-based strings. There are no longer any phrase-by-phrase translations that need to be duplicated. This makes translation work much simpler and more straightforward.

## Prerequisites

Before adding a new language, ensure you:
- Have Android Studio installed
- Understand the target language's grammar and sentence structure
- Can provide accurate translations for technical terms
- Are familiar with Android resource file structure

## Step-by-Step Process

### Step 1: Create the Language Resource File

1. **Navigate to the resources directory:**
   ```
   app/src/main/res/
   ```

2. **Create a new `values-xx` directory** (replace `xx` with the appropriate language code):
   ```
   app/src/main/res/values-fr/     (for French)
   app/src/main/res/values-es/     (for Spanish)
   app/src/main/res/values-ja/     (for Japanese)
   ```

3. **Create `strings.xml` inside the new directory:**
   ```
   app/src/main/res/values-fr/strings.xml
   ```

### Step 2: Add the Language to TtsLanguageManager

1. **Open the file:**
   ```
   app/src/main/java/com/micoyc/speakthat/TtsLanguageManager.java
   ```

2. **Locate the `getSupportedTtsLanguages()` method** (around line 50-80)

3. **Add your language to the list:**
   ```java
   public static List<TtsLanguage> getSupportedTtsLanguages(Context context) {
       List<TtsLanguage> languages = new ArrayList<>();
       
       // Default option (always first)
       languages.add(new TtsLanguage("Default (Use System Language)", null, null));
       
       // Existing languages
       languages.add(new TtsLanguage("English (United Kingdom)", "en_GB", new Locale("en", "GB")));
       languages.add(new TtsLanguage("Deutsch (Deutschland)", "de_DE", new Locale("de", "DE")));
       languages.add(new TtsLanguage("Italiano (Italia)", "it_IT", new Locale("it", "IT")));
       
       // Add your new language here
       languages.add(new TtsLanguage("Français (France)", "fr_FR", new Locale("fr", "FR")));
       
       return languages;
   }
   ```

### Step 3: Translate All Required Strings

Copy the entire content from `app/src/main/res/values/strings.xml` and translate all the TTS-related strings. See the [Required String Resources](#required-string-resources) section below.

### Step 4: Build and Test

1. **Build the project:**
   ```bash
   ./gradlew assembleDebug
   ```

2. **Install and test the app**
3. **Go to Voice Settings → TTS Language**
4. **Verify your language appears in the spinner**
5. **Test TTS functionality in all areas**

## Required String Resources

You must translate ALL of the following string categories. Missing any will cause the language to not appear in the TTS Language spinner.

### 1. Notification Template Strings (15 strings)
```xml
<!-- TTS Template Strings (for improved localization) -->
<!-- These are complete sentence templates with placeholders for proper localization -->
<string name="tts_template_notified_you">{app} notified you: {content}</string>
<string name="tts_template_reported">{app} reported: {content}</string>
<string name="tts_template_saying">Notification from {app}, saying {content}</string>
<string name="tts_template_alerts_you">{app} alerts you: {content}</string>
<string name="tts_template_update_from">Update from {app}: {content}</string>
<string name="tts_template_notification">{app} notification: {content}</string>
<string name="tts_template_new_notification">New notification: {app}: {content}</string>
<string name="tts_template_new_from">New from {app}: {content}</string>
<string name="tts_template_said">{app} said: {content}</string>
<string name="tts_template_updated_you">{app} updated you: {content}</string>
<string name="tts_template_new_notification_from">New notification from {app}: saying: {content}</string>
<string name="tts_template_new_update_from">New update from {app}: {content}</string>
<string name="tts_template_notification_from">Notification from {app}: {content}</string>
<string name="tts_template_says">{app} says: {content}</string>
<string name="tts_template_private_notification">You received a private notification from {app}</string>
```

### 2. About Page TTS Strings (7 strings)
```xml
<!-- About Page TTS -->
<string name="tts_about_intro">Welcome to SpeakThat!</string>
<string name="tts_about_description">SpeakThat is an Android Notification Reader that announces your notifications aloud using text-to-speech. Perfect for hands-free operation, accessibility, and staying informed while multitasking.</string>
<string name="tts_about_features_intro">Key features include:</string>
<string name="tts_about_features">Real-time notification reading, customizable voice settings, smart notification filtering, app-specific controls, media-aware behavior, notification history tracking, Shake to Stop and Wave to Stop, custom speech formatting, dark mode and light mode support, and full offline functionality.</string>
<string name="tts_about_developer">Developed with love by Mitchi, a solo developer from the UK with over 12 years of experience testing and using notification readers every day. SpeakThat was inspired by Touchless Notifications, originally developed by DYNA Logix, and aims to carry forth the legacy of customisable notification readers. This project was accelerated by Artificial Intelligence assisted programming.</string>
<string name="tts_about_license">This project is open source and available under the GPL-3.0 License. You can view the full license text and source code on GitHub. Contributions and feedback are welcome!</string>
<string name="tts_about_thank_you">Thank you for using SpeakThat!</string>
```

### 3. Voice Settings Test String (1 string)
```xml
<!-- Voice Settings Test -->
<string name="tts_voice_test">This is how your notifications will sound with these voice settings.</string>
```

### 4. Onboarding TTS Strings (7 strings)
```xml
<!-- Onboarding TTS -->
<string name="tts_onboarding_welcome">Welcome to SpeakThat! Stay connected without staring at your screen. Your phone will read notifications aloud so you can focus on what matters.</string>
<string name="tts_onboarding_permission">SpeakThat needs permission to read your notifications aloud. Everything stays on your device and you control what gets read.</string>
<string name="tts_onboarding_privacy">Your privacy matters. You control exactly what gets read aloud. Shake your phone to stop any announcement instantly. Everything stays on your device.</string>
<string name="tts_onboarding_filters">Let\'s set up some basic privacy filters to get you started. SpeakThat features a powerful filtering system that allows you to choose what gets read and what doesn\'t.</string>
<string name="tts_onboarding_apps">Type the name of an app you want me to never read notifications from. Common examples include banking apps, medical apps, and dating apps.</string>
<string name="tts_onboarding_words">Type words or phrases that you want me to never read notifications containing. Common examples include password, PIN, credit card, and medical terms.</string>
<string name="tts_onboarding_complete">SpeakThat is ready to help you stay connected while keeping your eyes free! You can add more filters anytime in the app settings.</string>
```

### 5. Behavior Settings Strings (18 strings)
```xml
<!-- Behavior Settings Speech Format Templates -->
<string name="tts_behavior_default">Default format</string>
<string name="tts_behavior_minimal">Minimal format</string>
<string name="tts_behavior_formal">Formal format</string>
<string name="tts_behavior_casual">Casual format</string>
<string name="tts_behavior_time_aware">Time aware format</string>
<string name="tts_behavior_content_only">Content only format</string>
<string name="tts_behavior_app_only">App only format</string>
<string name="tts_behavior_varied">Varied format</string>
<string name="tts_behavior_custom">Custom format</string>

<!-- Speech Format Preset Templates -->
<string name="tts_format_default">{app} notified you: {content}</string>
<string name="tts_format_minimal">{app}: {content}</string>
<string name="tts_format_formal">Notification from {app}: {content}</string>
<string name="tts_format_casual">{app} says: {content}</string>
<string name="tts_format_time_aware">{app} at {time}: {content}</string>
<string name="tts_format_content_only">{content}</string>
<string name="tts_format_app_only">{app}</string>
<string name="tts_format_varied">VARIED</string>
<string name="tts_format_custom">CUSTOM</string>
```

## File Structure

Your new language should follow this structure:
```
app/src/main/res/
├── values/                    # English (base language)
│   └── strings.xml
├── values-de/                 # German
│   └── strings.xml
├── values-it/                 # Italian
│   └── strings.xml
└── values-fr/                 # Your new language
    └── strings.xml
```

## Testing Your Language

### 1. Build Verification
```bash
./gradlew assembleDebug
```
- Should complete without errors
- Check for any missing string resource warnings

### 2. App Testing
1. **Install the app**
2. **Go to Settings → Voice Settings**
3. **Check TTS Language spinner** - your language should appear
4. **Select your language**
5. **Test each component:**
   - **Notifications** - Send a test notification
   - **About page** - Tap "Read About Page Aloud"
   - **Voice Settings** - Tap "Test Voice Settings"
   - **Onboarding** - Re-run onboarding from settings
   - **Behavior Settings** - Test different speech formats

### 3. Verification Checklist
- [ ] Language appears in TTS Language spinner
- [ ] Language can be selected and saved
- [ ] Notifications speak in the selected language
- [ ] About page speaks in the selected language
- [ ] Voice Settings test speaks in the selected language
- [ ] Onboarding speaks in the selected language
- [ ] Behavior Settings formats work in the selected language

## Troubleshooting

### Language Doesn't Appear in Spinner
**Cause:** Missing required string resources
**Solution:** Ensure ALL required strings are translated and present

### TTS Still Speaks English
**Cause:** String resource not found or fallback to English
**Solution:** Check string names match exactly (case-sensitive)

### Build Errors
**Cause:** XML syntax errors or invalid characters
**Solution:** Validate XML syntax and escape special characters

### App Crashes
**Cause:** Null pointer or missing resource
**Solution:** Check that all placeholders (`{app}`, `{content}`) are preserved

## Language Code Reference

Common language codes for Android:
- **French:** `fr` → `values-fr/`
- **Spanish:** `es` → `values-es/`
- **Portuguese:** `pt` → `values-pt/`
- **Russian:** `ru` → `values-ru/`
- **Japanese:** `ja` → `values-ja/`
- **Korean:** `ko` → `values-ko/`
- **Chinese (Simplified):** `zh` → `values-zh/`
- **Arabic:** `ar` → `values-ar/`
- **Dutch:** `nl` → `values-nl/`
- **Swedish:** `sv` → `values-sv/`

## Example: Adding French

### Step 1: Create Directory and File
```bash
mkdir app/src/main/res/values-fr
touch app/src/main/res/values-fr/strings.xml
```

### Step 2: Update TtsLanguageManager.java
```java
// Add to getSupportedTtsLanguages()
languages.add(new TtsLanguage("Français (France)", "fr_FR", new Locale("fr", "FR")));
```

### Step 3: Create French strings.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- TTS Template Strings -->
    <string name="tts_template_notified_you">{app} vous a notifié : {content}</string>
    <string name="tts_template_reported">{app} a signalé : {content}</string>
    <string name="tts_template_saying">Notification de {app}, disant {content}</string>
    <!-- ... continue with all required strings -->
    
    <!-- About Page TTS -->
    <string name="tts_about_intro">Bienvenue dans SpeakThat !</string>
    <string name="tts_about_description">SpeakThat est un lecteur de notifications Android qui annonce vos notifications à haute voix en utilisant la synthèse vocale. Parfait pour une utilisation mains libres, l\'accessibilité et rester informé tout en effectuant plusieurs tâches.</string>
    <!-- ... continue with all required strings -->
    
    <!-- Continue with all other categories... -->
</resources>
```

### Step 4: Test
1. Build the project
2. Install and test
3. Verify French appears in TTS Language spinner
4. Test all TTS functionality

## Important Notes

1. **Preserve Placeholders:** Never translate `{app}`, `{content}`, `{time}` - these are dynamic values
2. **Escape Special Characters:** Use `\'` for apostrophes, `\"` for quotes
3. **Maintain Context:** Ensure translations fit the notification context
4. **Test Thoroughly:** Test all components before considering complete
5. **Grammar Considerations:** Adapt sentence structure for your language (S-V-O vs S-O-V)

## Support

If you encounter issues:
1. Check this guide first
2. Verify all required strings are present
3. Test with a simple language first
4. Check the app logs for error messages
5. Ensure XML syntax is correct

---

**Happy localizing! 🌍**

*This guide covers all aspects of adding new languages to SpeakThat. Follow it carefully to ensure your language works correctly across all app components.* 