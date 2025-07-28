# Translation Template for SpeakThat

🎉 Thank you for helping with translations!

## How the system works

I've created a localization system that allows users to choose the language for app phrases (like "notified you:") separately from the TTS engine language.

## Files to modify

### 1. Italian: `app/src/main/res/values-it/strings.xml`

This file contains the Italian translations. Here's the current content:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- TTS App Phrases (Italian) -->
    <string name="tts_notified_you">ti ha notificato:</string>
    <string name="tts_private_notification_received">Hai ricevuto una notifica privata da</string>
    <string name="tts_an_app">Un\'app</string>
    
    <!-- Template Preset Phrases (Italian) -->
    <string name="tts_notification_from">Notifica da</string>
    <string name="tts_says">dice:</string>
    
    <!-- Varied Format Phrases (Italian) -->
    <string name="tts_reported">ha riportato:</string>
    <string name="tts_saying">dicendo</string>
    <string name="tts_alerts_you">ti avvisa:</string>
    <string name="tts_update_from">Aggiornamento da</string>
    <string name="tts_notification">notifica:</string>
    <string name="tts_new_notification">Nuova notifica:</string>
    <string name="tts_new_from">Nuovo da</string>
    <string name="tts_said">ha detto:</string>
    <string name="tts_updated_you">ti ha aggiornato:</string>
    <string name="tts_new_notification_from">Nuova notifica da</string>
    <string name="tts_saying_colon">dicendo:</string>
    <string name="tts_new_update_from">Nuovo aggiornamento da</string>
</resources>
```

### 2. German: `app/src/main/res/values-de/strings.xml`

This file contains the German translations. Here's the current content:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- TTS App Phrases (German) -->
    <string name="tts_notified_you">hat dich benachrichtigt:</string>
    <string name="tts_private_notification_received">Du hast eine private Benachrichtigung von</string>
    <string name="tts_an_app">Eine App</string>
    
    <!-- Template Preset Phrases (German) -->
    <string name="tts_notification_from">Benachrichtigung von</string>
    <string name="tts_says">sagt:</string>
    
    <!-- Varied Format Phrases (German) -->
    <string name="tts_reported">hat gemeldet:</string>
    <string name="tts_saying">sagend</string>
    <string name="tts_alerts_you">warnt dich:</string>
    <string name="tts_update_from">Update von</string>
    <string name="tts_notification">Benachrichtigung:</string>
    <string name="tts_new_notification">Neue Benachrichtigung:</string>
    <string name="tts_new_from">Neu von</string>
    <string name="tts_said">hat gesagt:</string>
    <string name="tts_updated_you">hat dich aktualisiert:</string>
    <string name="tts_new_notification_from">Neue Benachrichtigung von</string>
    <string name="tts_saying_colon">sagend:</string>
    <string name="tts_new_update_from">Neues Update von</string>
</resources>
```

## What each string means

### Core Phrases:
- **`tts_notified_you`**: Used in speech templates like "{app} notified you: {content}"
- **`tts_private_notification_received`**: Used when a notification is made private (replaces entire content)
- **`tts_an_app`**: Used when an app is in private mode (replaces app name)

### Template Preset Phrases:
- **`tts_notification_from`**: Used in "Formal" template: "Notification from {app}: {content}"
- **`tts_says`**: Used in "Casual" template: "{app} says: {content}"

### Varied Format Phrases:
These are used in "Varied" mode for random selection:
- **`tts_reported`**: "{app} reported: {content}"
- **`tts_saying`**: "Notification from {app}, saying {content}"
- **`tts_alerts_you`**: "{app} alerts you: {content}"
- **`tts_update_from`**: "Update from {app}: {content}"
- **`tts_notification`**: "{app} notification: {content}"
- **`tts_new_notification`**: "New notification: {app}: {content}"
- **`tts_new_from`**: "New from {app}: {content}"
- **`tts_said`**: "{app} said: {content}"
- **`tts_updated_you`**: "{app} updated you: {content}"
- **`tts_new_notification_from`**: "New notification from {app}: saying: {content}"
- **`tts_saying_colon`**: "New notification from {app}: saying: {content}"
- **`tts_new_update_from`**: "New update from {app}: {content}"

## How to improve translations

Feel free to suggest better translations! For example:

### Italian suggestions:
- `tts_notified_you`: Maybe "ti ha inviato una notifica:" or "ha notificato:"?
- `tts_private_notification_received`: Maybe "Hai ricevuto una notifica privata da parte di"?
- `tts_saying`: Maybe "che dice" instead of "dicendo"?

### German suggestions:
- `tts_notified_you`: Maybe "hat dir eine Benachrichtigung gesendet:"?
- `tts_private_notification_received`: Maybe "Du hast eine private Nachricht von"?
- `tts_saying`: Maybe "mit der Nachricht" instead of "sagend"?

## Testing

To test your translations:
1. Set the TTS Language to "Italian (Italy)" or "German (Germany)" in Voice Settings
2. Go to Behavior Settings → Speech Template
3. Try different templates:
   - **Default**: "{app} notified you: {content}"
   - **Formal**: "Notification from {app}: {content}"
   - **Casual**: "{app} says: {content}"
   - **Varied**: Random format each time
4. Send yourself a test notification
5. The app phrases should now be in Italian/German!

## Adding more languages

To add another language (e.g., Spanish):
1. Create `app/src/main/res/values-es/strings.xml`
2. Add the translations
3. Update the `getLocalizedTtsString()` method in `NotificationReaderService.kt` to include the new language

## Questions?

Feel free to ask if anything isn't clear! The goal is to make the app feel natural in your native language. 🚀 
