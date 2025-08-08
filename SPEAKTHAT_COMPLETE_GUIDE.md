# SpeakThat! Complete User Guide

## What is SpeakThat!?

**SpeakThat!** is an open-source Android notification reader that announces your notifications aloud using text-to-speech. It's designed to help you stay connected without constantly checking your phone - perfect for hands-free operation, accessibility, and staying informed while multitasking.

### Core Purpose
- **Reads notifications aloud** from any app with customizable filters
- **Privacy-first approach** - everything stays on your device
- **Hands-free operation** - no need to look at your screen
- **Accessibility support** - helps visually impaired users stay informed

---

## 🚀 Getting Started

### First-Time Setup

1. **Install the App**
   - Download the APK from GitHub or your preferred app store
   - Install the app on your Android device
   - Launch SpeakThat! for the first time

2. **Onboarding Process**
   The app will guide you through a comprehensive setup process:

   **Welcome Screen**
   - Introduces the app's purpose and benefits
   - Explains how it helps you stay connected hands-free

   **Permission Setup**
   - **Notification Access**: Required for the app to read your notifications
   - Tap "Open Notification Settings" and enable SpeakThat! in the list
   - This permission is essential for the app to function

   **Privacy Overview**
   - Learn about privacy controls and safety features
   - Understand that everything stays on your device
   - Discover the "Shake to Stop" feature for instant silence

   **Basic Filter Setup**
   - **App Filtering**: Block sensitive apps (banking, medical, dating apps)
   - **Word Filtering**: Block notifications containing sensitive words (passwords, PINs, etc.)
   - **Smart Rules**: Set up conditional rules for advanced control

3. **Master Switch**
   - The main screen shows a large toggle to enable/disable notification reading
   - When enabled, SpeakThat! will start reading notifications immediately
   - When disabled, all notification reading stops

---

## 🔧 Core Features

### 1. Notification Reading
- **Real-time processing**: Notifications are read as they arrive
- **Smart formatting**: "App Name notified you: notification content"
- **Multiple behavior modes**: Choose how multiple notifications are handled

### 2. Privacy & Security
- **100% local processing**: No data leaves your device
- **Granular control**: Block, allow, or privatize any app
- **Word filtering**: Block notifications containing sensitive terms
- **Private mode**: Replace sensitive content with generic messages

### 3. Voice Customization
- **Speech rate**: Adjust how fast notifications are read
- **Pitch control**: Change the voice tone
- **Voice selection**: Choose from available system voices
- **Language support**: Multiple language options
- **Preview function**: Test your voice settings before applying

---

## ⚙️ Advanced Features

### 1. Smart Rules System
The most powerful feature of SpeakThat! - create conditional rules that automatically control when notifications are read based on your situation.

**Rule Components:**
- **Triggers**: Conditions that activate the rule (time, Bluetooth, WiFi, etc.)
- **Actions**: What happens when the rule activates (block notifications, change settings, etc.)
- **Exceptions**: Override conditions that prevent the rule from executing

**Available Triggers:**
- **Time Schedule**: Specific times or time ranges
- **Bluetooth Device**: When specific devices are connected/disconnected
- **WiFi Network**: When connected to specific networks
- **Screen State**: When screen is on/off
- **App-Specific**: When specific apps send notifications

**Available Actions:**
- **Skip this notification**: Don't read the current notification
- **Enable/Disable app filters**: Temporarily change app filtering
- **Change voice settings**: Modify speech parameters

**Logic Gates:**
- **AND**: All triggers must be met (more restrictive)
- **OR**: Any trigger can activate the rule (more permissive)

### 2. Notification Behavior Modes
Choose how multiple notifications are handled:

- **🔄 Interrupt**: Stops current notification and reads new one immediately
- **📋 Queue**: Finishes current notification, then reads new ones in order
- **⏭️ Skip**: Ignores new notifications while reading
- **🧠 Smart (Recommended)**: Priority apps interrupt, others queue

### 3. Media-Aware Behavior
Control how notifications interact with your music/videos:

- **🎵 Ignore**: Speaks over your media
- **⏸️ Pause**: Pauses media completely while speaking
- **🔉 Lower Audio (Recommended)**: Temporarily reduces media volume
- **🔇 Silence**: Doesn't speak while media plays

### 4. Gesture Controls
- **Shake to Stop**: Instantly silence SpeakThat! by shaking your device
- **Wave to Stop**: Use proximity sensor to stop notifications with hand gestures
- **Customizable sensitivity**: Adjust gesture detection to your preference

---

## 🛡️ Privacy Controls

### App Filtering
- **Block**: Never read notifications from specific apps
- **Private**: Replace notification content with generic messages
- **Allow**: Normal notification reading
- **Cooldown**: Prevent spam by setting time delays between notifications

### Word Filtering
- **Block words**: Never read notifications containing specific terms
- **Private words**: Replace sensitive terms with generic alternatives
- **Word replacements**: Customize how specific words are spoken

### Persistent/Silent Notification Filtering
- **Persistent notifications**: Ongoing notifications (music controls, navigation)
- **Silent notifications**: Notifications with no sound/vibration
- **Foreground services**: Background service notifications
- **Low priority**: Minimum priority notifications
- **System notifications**: Android system notifications

---

## 📱 User Interface

### Main Screen
- **Master Switch**: Large toggle to enable/disable the service
- **Service Status**: Shows if notification reading is active
- **Quick Actions**: Access to history, settings, and support
- **Version Info**: Current app version and build variant

### Settings Menu
Organized into logical categories:

**General Settings**
- Master switch controls
- Notification behavior
- Media behavior
- Gesture controls
- Battery optimization

**Voice Settings**
- Speech rate and pitch
- Voice selection
- Language options
- Preview and test functions

**Filter Settings**
- App filtering (block/private/allow)
- Word filtering
- Cooldown settings
- Custom app names

**Behavior Settings**
- Notification behavior modes
- Media interaction
- Gesture sensitivity
- Do Not Disturb integration

**Rules System**
- Create and manage smart rules
- Rule templates for common scenarios
- Rule testing and debugging

**Support & Feedback**
- In-app logging
- Support contact
- About information
- Re-run onboarding

---

## 🔍 Troubleshooting

### Common Issues

**Notifications not being read:**
1. Check if the master switch is enabled
2. Verify notification access permission is granted
3. Check if the app is blocked in your device's battery optimization
4. Ensure the app isn't in Do Not Disturb mode

**Voice not working:**
1. Test voice settings in the Voice Settings menu
2. Check if your device's text-to-speech is working
3. Try a different voice or language
4. Restart the app

**Rules not working:**
1. Verify the rule is enabled
2. Check trigger conditions are met
3. Review exception settings
4. Use the in-app logger to debug rule evaluation

**Battery drain:**
1. Disable persistent notification if not needed
2. Adjust gesture sensitivity to reduce sensor usage
3. Use cooldown settings to reduce processing
4. Check battery optimization settings

### In-App Logging
SpeakThat! includes comprehensive logging to help diagnose issues:
- Access logs through Settings → Support & Feedback → View Logs
- Logs show notification processing, rule evaluation, and error messages
- Useful for debugging and getting support

---

## 🎯 Use Cases & Examples

### Driving
- **Setup**: Enable SpeakThat! and set up gesture controls
- **Usage**: Notifications are read aloud without taking eyes off the road
- **Safety**: Shake phone to instantly stop embarrassing notifications

### Accessibility
- **Setup**: Configure voice settings for optimal clarity
- **Usage**: Stay informed without visual screen reading
- **Customization**: Adjust speech rate and voice to personal preference

### Work/Meetings
- **Setup**: Create time-based rules to only read during breaks
- **Usage**: Stay connected without disrupting meetings
- **Privacy**: Block work-related apps during personal time

### Media Consumption
- **Setup**: Configure media-aware behavior (lower audio recommended)
- **Usage**: Hear notifications without interrupting music/podcasts
- **Control**: Use gesture controls to stop notifications during important content

### Privacy-Sensitive Situations
- **Setup**: Block sensitive apps and words
- **Usage**: Receive notifications without compromising privacy
- **Emergency**: Shake or wave to stop any notification instantly

---

## 🔧 Advanced Configuration

### Custom Speech Templates
- Modify how notifications are formatted
- Use placeholders: {app} for app name, {content} for notification text
- Create varied formats for more natural speech

### Battery Optimization
- Request battery optimization exemption for reliable operation
- Adjust sensor timeouts to balance functionality and battery life
- Use cooldown settings to reduce processing frequency

### Export/Import Settings
- Backup your configuration for device transfers
- Share settings between devices
- Restore from previous configurations

---

## 📞 Support & Community

### Getting Help
- **In-app Support**: Settings → Support & Feedback
- **GitHub Issues**: Report bugs and request features
- **Email Support**: Direct contact with the developer

### Contributing
- **Open Source**: View and contribute to the codebase
- **Translations**: Help translate the app to your language
- **Testing**: Provide feedback on new features

### Privacy Commitment
- **No data collection**: Everything stays on your device
- **Open source**: Transparent code for security verification
- **User control**: You decide exactly what gets read

---

## 🎉 Tips & Best Practices

### Getting the Most Out of SpeakThat!

1. **Start Simple**: Begin with basic app and word filtering, then explore advanced features
2. **Test Gestures**: Find the right sensitivity for shake/wave controls
3. **Use Smart Rules**: Create rules for common scenarios (work hours, driving, etc.)
4. **Regular Maintenance**: Review and update filters as your app usage changes
5. **Voice Optimization**: Spend time finding the perfect voice settings for your needs

### Recommended Settings for New Users
- **Notification Behavior**: Smart mode (priority apps interrupt, others queue)
- **Media Behavior**: Lower Audio (most natural experience)
- **Gesture Controls**: Enable both shake and wave to stop
- **Basic Filters**: Block banking, medical, and dating apps
- **Word Filters**: Block password, PIN, credit card terms

### Power User Tips
- **Rule Templates**: Use built-in templates as starting points for custom rules
- **Conditional Logic**: Combine multiple triggers with AND/OR logic for precise control
- **Cooldown Settings**: Prevent notification spam from chat apps
- **Custom App Names**: Make app names more natural when spoken
- **Export Settings**: Backup your configuration regularly

---

## 📋 Quick Reference

### Essential Permissions
- **Notification Access**: Required for core functionality
- **Bluetooth**: For Bluetooth-based rules
- **Location**: For WiFi network detection
- **Phone State**: For call detection features

### Key Settings Locations
- **Master Switch**: Main screen
- **Voice Settings**: Settings → Voice Settings
- **App Filters**: Settings → Filter Settings → App Filtering
- **Smart Rules**: Settings → Rules
- **Gesture Controls**: Settings → Behavior Settings

### Quick Actions
- **Stop Reading**: Shake device or wave hand
- **Open Settings**: Tap settings icon on main screen
- **View History**: Tap history button on main screen
- **Test Voice**: Voice Settings → Test Voice button

---

**SpeakThat!** is designed to be powerful yet simple to use. Start with the basic features and gradually explore the advanced options as you become comfortable with the app. The privacy-first approach means you're always in control of what gets read, and the comprehensive filtering system ensures you only hear what matters to you.

Remember: Your privacy and control are the top priorities. Everything stays on your device, and you decide exactly what gets read aloud.
