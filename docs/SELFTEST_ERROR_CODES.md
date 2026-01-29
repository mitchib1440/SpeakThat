# SpeakThat SelfTest Error Codes Reference

## Overview

The SelfTest diagnostic tool helps identify why SpeakThat may not be reading notifications. Each test result includes a 4-digit error code that indicates the specific issue detected.

This document catalogs all error codes, their meanings, and troubleshooting steps.

---

## Success Code

### 0000 - Test Passed ✅

**Meaning:** SpeakThat is working correctly. Notifications are being received and read aloud.

**User Experience:** The test notification was posted, detected by the service, passed all filters, was spoken by TTS, and the user confirmed they heard it.

**Next Steps:** None required. The app is functioning as expected.

---

## Error Codes (Grouped by Category)

## 4xx - Notification Delivery Issues

### 0400 - Notification Not Posted ❌

**Meaning:** The test notification could not be created or posted to the system notification area.

**Common Causes:**
- POST_NOTIFICATIONS permission not granted (Android 13+)
- System notification service unavailable
- Memory constraints preventing notification creation
- System blocking app from posting notifications

**Troubleshooting Steps:**
1. Check that POST_NOTIFICATIONS permission is granted
2. Ensure the app has not been restricted in system settings
3. Check if "Notification Access" is still enabled for SpeakThat
4. Restart the device if the issue persists
5. Check for battery optimization restrictions

**Technical Details:** This error occurs when `NotificationManager.notify()` fails or returns an error during Step 8 of the SelfTest.

---

### 0404 - Service Not Receiving Notifications ❌

**Meaning:** The test notification appeared on screen, but the NotificationListenerService did not receive it.

**Common Causes:**
- NotificationListener permission revoked or disabled
- Android system not forwarding notifications to the service
- Service crashed or was killed by the system
- Notification listener binding disconnected

**Troubleshooting Steps:**
1. Go to Settings → Apps → Special App Access → Notification Access
2. Toggle SpeakThat OFF, wait 3 seconds, then toggle back ON
3. Restart the SpeakThat app
4. If issue persists, restart the device
5. Check if battery optimization is killing the service
6. Ensure "Don't optimize" is selected for SpeakThat in battery settings
7. If SelfTest shows “Permission enabled – reconnecting notification listener…”, wait for the automatic rebind sequence to finish (two attempts spaced ~1.5s apart). The status will update if Android accepts the request.

**Technical Details:** The notification was successfully posted (Step 8), but the `onNotificationPosted()` callback was never triggered. This indicates a system-level issue with NotificationListenerService binding.

**Developer Notes:** 
- The listener health watchdog now writes to `ServiceRebind` / `ServiceHealth` in `InAppLogger`. Look for lines such as `Listener health monitor requested rebind`.
- Check for `onListenerDisconnected()` calls in logs
- Monitor service lifecycle events and compare `last_connect`, `last_disconnect`, and `last_rebind_attempt` timestamps in Development Settings → Background Process Monitor.
- Verify that `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` is still active

---

## 41x - Settings & Configuration Issues

### 0410 - Master Switch Disabled ❌

**Meaning:** The SpeakThat master switch is turned off, preventing all notifications from being read.

**User Experience:** The notification was received but immediately blocked because the master switch is disabled.

**Troubleshooting Steps:**
1. Go to the SpeakThat main screen
2. Enable the master switch at the top of the screen
3. Run the SelfTest again

**Technical Details:** The notification passed the NotificationListener stage but was blocked in the first check of `onNotificationPosted()` due to `master_switch_enabled` preference being false.

**When This Occurs:**
- User manually disabled the master switch
- A rule action disabled the master switch
- An app crash reset preferences (rare)

---

### 0411 - Audio Mode Blocking ❌

**Meaning:** The device is in Silent or Vibrate mode, and "Honor Audio Mode" is enabled in Voice Settings.

**User Experience:** The notification was received and passed filtering, but TTS was blocked due to respecting the device's audio mode.

**Troubleshooting Steps:**
1. **Option A - Change Audio Mode:**
   - Switch device to Normal/Ring mode
   - Run the test again

2. **Option B - Disable Honor Audio Mode:**
   - Go to Voice Settings in SpeakThat
   - Scroll to "Honor Audio Mode"
   - Disable this setting
   - Note: Notifications will now be read even in Silent mode

**Technical Details:** 
- Blocked in `checkAudioMode()` or similar audio environment checks
- Controlled by `honor_audio_mode` preference in VoiceSettings
- Common issue when testing at night or in meetings

**Related Settings:**
- Honor Audio Mode (VoiceSettings)
- Ringer Mode detection
- Audio stream routing

---

### 0412 - Do Not Disturb Active ❌

**Meaning:** Do Not Disturb mode is active, and "Honor Do Not Disturb" is enabled.

**User Experience:** The notification was received but not read because the device is in DND mode.

**Troubleshooting Steps:**
1. **Option A - Disable DND:**
   - Turn off Do Not Disturb mode
   - Run the test again

2. **Option B - Disable Honor DND:**
   - Go to Voice Settings in SpeakThat
   - Find "Honor Do Not Disturb"
   - Disable this setting
   - Note: Notifications will now interrupt DND mode

3. **Option C - Configure DND Exceptions:**
   - In Android settings, allow SpeakThat to bypass DND
   - Set notification priority appropriately

**Technical Details:**
- Detected via `NotificationManager.getCurrentInterruptionFilter()`
- Blocked when filter is not `INTERRUPTION_FILTER_ALL`
- Controlled by `honor_dnd` preference

**Related Features:**
- DND scheduling
- Priority app configuration
- Notification channels (Android 8+)

---

### 0413 - Blocked by Rules ❌

**Meaning:** The notification was blocked by the rules system.

**User Experience:** An active rule evaluated the notification and determined it should not be read.

**Common Causes:**
- Rule with "Block notification" action triggered
- Rule condition matched (time, location, app, etc.)
- Rule exception blocked the notification
- Conflicting rules with blocking actions

**Troubleshooting Steps:**
1. Go to Rules in SpeakThat settings
2. Review active rules (indicated by toggle being ON)
3. Check each rule's conditions and actions
4. Temporarily disable rules one by one to identify the culprit
5. Review rule logs in Development Settings (if verbose logging enabled)
6. Check for "Block All" or global blocking rules

**Technical Details:**
- Blocked in `RuleEvaluator.evaluate()`
- Check logs for "Rules blocked" or "Rule evaluation: blocked"
- Rules are evaluated in priority order

**Common Scenarios:**
- "Quiet hours" rule blocking all notifications at night
- "Meeting mode" rule active
- App-specific blocking rule
- Location-based blocking (e.g., at work)

**Developer Notes:**
- Review rule evaluation order
- Check for exception handling in rule system
- Verify condition matching logic

---

### 0414 - Blocked by Filtering ❌

**Meaning:** The notification was blocked by content filtering, app filtering, deduplication, or dismissal memory.

**User Experience:** The notification reached the service but was filtered out before TTS.

**Common Causes:**
1. **Deduplication:** Identical or similar notification was recently read
2. **Dismissal Memory:** Similar notification was recently dismissed
3. **App Filtering:** SpeakThat is in the blocked apps list
4. **Word Filtering:** Notification content contains blocked words
5. **Content Filtering:** Notification text was empty after filtering
6. **Private App Flag:** App is marked as private and filtered

**Troubleshooting Steps:**

**For Deduplication:**
1. Go to Settings → Behavior Settings
2. Check "Notification Deduplication" setting
3. Temporarily disable to test
4. Note: SelfTest should bypass deduplication automatically

**For Dismissal Memory:**
1. Go to Settings → Behavior Settings
2. Check "Dismissal Memory" setting
3. Disable if causing issues
4. Clear dismissal memory if available

**For App Filtering:**
1. Go to Settings → App Filtering
2. Check if "App List Mode" is set to "Block Selected"
3. Review the blocked apps list
4. Ensure SpeakThat is not in the blocked list

**For Word Filtering:**
1. Go to Settings → Content Filtering
2. Review "Word Blacklist"
3. Check if test notification content matches any blocked words
4. Temporarily clear filters to test

**Technical Details:**
- Multiple filtering systems can trigger this code
- Check specific log messages for exact cause
- Filtering order: App → Content → Deduplication → Dismissal Memory

**Log Patterns to Look For:**
- "Duplicate notification"
- "Dismissal memory"
- "App is in block list"
- "Content filter blocked"
- "Word blacklist match"
- "Notification text empty after filtering"

---

### 0415 - TTS Not Initialized ❌

**Meaning:** Text-to-Speech engine is not initialized or failed to initialize.

**User Experience:** The notification passed all filters and was ready to be spoken, but TTS was unavailable.

**Common Causes:**
- TTS engine crashed or not responding
- TTS data not installed for selected language
- TTS engine disabled in system settings
- Insufficient resources to initialize TTS
- Selected TTS engine uninstalled

**Troubleshooting Steps:**
1. **Check TTS Engine:**
   - Go to Android Settings → System → Languages & Input → Text-to-Speech
   - Verify a TTS engine is selected and active
   - Test TTS with the "Listen to an example" button

2. **Check Language Data:**
   - Ensure voice data is downloaded for your selected language
   - Install additional language packs if needed

3. **Restart TTS:**
   - Force stop SpeakThat
   - Clear app cache (optional)
   - Restart the app
   - TTS will reinitialize on startup

4. **Try Different Engine:**
   - Go to Voice Settings in SpeakThat
   - Select a different TTS engine
   - Test if the new engine works

5. **Check Resources:**
   - Ensure device has sufficient free memory
   - Close other apps that may be using TTS
   - Restart device if needed

**Technical Details:**
- TTS initialization happens in `onCreate()` of NotificationReaderService
- Failed initialization logs: "TTS not initialized" or "TTS initialization failed"
- Status code from TTS: `TextToSpeech.ERROR` or `TextToSpeech.STOPPED`

**Related Settings:**
- TTS Engine selection (VoiceSettings)
- Language selection
- Speech rate and pitch (may affect some engines)

**Developer Notes:**
- Monitor `TextToSpeech.OnInitListener` callback
- Check for `ERROR` status code
- Verify TTS engine package is installed and enabled
- Consider implementing fallback TTS engine

---

### 0420 - Unknown Blocking Reason ❌

**Meaning:** The notification was received but not read, and the specific cause could not be determined.

**User Experience:** Something prevented the notification from being spoken, but it doesn't match any known blocking pattern.

**Common Causes:**
- New blocking condition not yet categorized
- Edge case in notification processing
- Timing issue causing premature exit
- Exception or error not properly logged
- Race condition in filtering logic

**Troubleshooting Steps:**
1. **Export Debug Logs Immediately:**
   - Tap "Export Logs" button on the error screen
   - Logs contain crucial information for diagnosis

2. **Share Report:**
   - Tap "Share Report" to send via email
   - Include any additional context about device state

3. **Try to Reproduce:**
   - Run the SelfTest multiple times
   - Note if error is consistent or intermittent

4. **Check Common Settings:**
   - Master switch: ON
   - Volume: Not muted
   - Audio mode: Normal
   - DND: OFF
   - Rules: Disabled temporarily

5. **Systematic Disable:**
   - Disable all custom settings one by one
   - Run test after each change
   - Identify which setting causes the issue

**Technical Details:**
- This is a catch-all error code for unclassified issues
- Always accompanied by "Unknown reason: Check debug logs for details"
- Critical to review full logs to identify root cause

**What to Look For in Logs:**
- Exception stack traces
- Unexpected null values
- Timing of events (rapid state changes)
- Service lifecycle events
- Memory warnings

**Developer Priority:** HIGH - This indicates a gap in error detection/classification

---

### 0440 - Audio Output Issue ❌

**Meaning:** TTS completed successfully (speech was generated), but the user didn't hear it.

**User Experience:** The logs show TTS finished speaking, but no audio was perceived by the user.

**Common Causes:**
1. **Volume Issues:**
   - Notification volume muted
   - Media volume muted (if using Media stream)
   - Volume extremely low
   - Volume keys pressed during test

2. **Audio Routing:**
   - Bluetooth headphones connected but not worn
   - Audio output to disconnected device
   - USB audio device selected
   - HDMI audio routing active

3. **Audio Stream Selection:**
   - Wrong audio stream chosen in Voice Settings
   - System suppressing selected stream
   - Audio ducking reducing volume to zero

4. **Device-Specific:**
   - Hardware audio issues
   - Multiple audio outputs configured
   - Sound enhancement settings interfering
   - Gaming/Performance mode affecting audio

5. **Timing Issues:**
   - TTS spoken too quickly (very high speech rate)
   - Audio started but immediately stopped
   - User wasn't paying attention during brief speech

**Troubleshooting Steps:**

**Step 1 - Check Volume:**
1. Press volume UP button several times
2. Ensure notification volume slider is at 75%+
3. Check that device is not in silent mode
4. Test volume with a different app (YouTube, Music)

**Step 2 - Check Audio Routing:**
1. Disconnect all Bluetooth devices
2. Remove wired headphones/USB audio
3. Ensure audio is playing through phone speaker
4. Test with a phone call to verify speaker works

**Step 3 - Check Audio Stream:**
1. Go to Voice Settings in SpeakThat
2. Note current "Audio Stream" setting
3. Try different streams:
   - Notification (default)
   - Media (for Bluetooth compatibility)
   - Alarm (bypasses some restrictions)
   - Music (alternative to Media)

**Step 4 - Test TTS Directly:**
1. Go to Voice Settings
2. Use the TTS test button
3. Adjust speech rate if too fast
4. Verify you can hear test speech

**Step 5 - Check Audio Settings:**
1. Android Settings → Sound & Vibration
2. Disable any sound enhancement features
3. Check "Separate app sound" settings
4. Ensure SpeakThat isn't routed to a different output

**Step 6 - Device-Specific:**
1. Check for Gaming/Performance modes
2. Disable Dolby Atmos or similar enhancements
3. Check manufacturer audio settings (Samsung, Xiaomi, etc.)
4. Try safe mode to rule out third-party interference

**Technical Details:**
- TTS `onDone()` callback was triggered successfully
- Audio was generated and sent to Android AudioManager
- Issue is in audio routing or output stage, not TTS generation
- Logs show: "SelfTest notification speaking" AND "TTS completed"

**Audio Stream Types:**
- STREAM_NOTIFICATION (4) - Default, respects notification volume
- STREAM_MEDIA (3) - For music/media, routes to Bluetooth
- STREAM_ALARM (4) - Bypasses DND, loud
- STREAM_MUSIC (3) - Legacy media stream

**Related Settings:**
- Audio Stream (VoiceSettings)
- TTS Volume (VoiceSettings)
- Audio Ducking (VoiceSettings)
- Speech Rate (can make speech too brief)

**Common Devices With This Issue:**
- Samsung phones with "Separate App Sound"
- Xiaomi/MIUI with audio profiles
- OnePlus with gaming mode
- Phones with active Bluetooth connections

---

## Special Internal Codes

### 0999 - Test Interrupted (Not an Error) ⚠️

**Meaning:** The test was interrupted by the user using a stop gesture (Shake to Stop, Wave to Stop, Press to Stop).

**User Experience:** TTS started speaking the test notification, but the user intentionally stopped it.

**Common Causes:**
- Shake to Stop gesture detected during test
- Wave to Stop gesture detected during test
- Volume button press (Press to Stop enabled)
- Manual TTS cancellation

**Troubleshooting Steps:**
This is not an error! It means:
1. The system is working correctly
2. Notifications ARE being received and processed
3. TTS started successfully
4. The gesture detection is working

**To Complete Test:**
1. Run the SelfTest again
2. Don't use stop gestures during the test
3. Let the test notification play completely
4. Then answer "Yes" when asked if you heard it

**Technical Details:**
- Not displayed as an actual error code
- Shows as "Test Interrupted" instead of "Error Code: 0999"
- Logs contain: "TTS stopped by shake/wave/press" or "User interrupted speech"
- This is a successful partial test (proves notification detection works)

**Why This Matters:**
- Confirms notification receiving is working
- Confirms gesture detection is working
- Confirms TTS initialization is working
- Only issue is test completion interrupted by user

---

## Error Code Ranges & Categories

| Range | Category | Description |
|-------|----------|-------------|
| 0000 | Success | Test passed, everything working |
| 40x | Notification Issues | Problems with notification delivery |
| 41x | Configuration | Settings preventing notification reading |
| 42x | Unknown/Misc | Unclassified or rare issues |
| 44x | Audio Output | TTS works but audio not heard |
| 999 | User Action | Test interrupted by user (not an error) |

---

## Diagnostic Flow

Understanding the test progression helps interpret error codes:

```
Step 1: Check NotificationListener Permission
        ↓ (PASS: Permission granted)
Step 2: Check POST_NOTIFICATIONS Permission
        ↓ (PASS: Permission granted or requested)
Step 3: Check Master Switch
        ↓ (PASS: Enabled)
Step 4: Initialize TTS
        ↓ (PASS: TTS ready) → If fail: Error 0415
Step 5: Check Volume
        ↓ (WARNING: Low volume detected)
Step 6: Check Audio Mode & DND
        ↓ (PASS: Normal mode)
Step 7: Check Rules System
        ↓ (PASS: No blocking rules)
Step 8: Post Test Notification
        ↓ (PASS: Notification posted) → If fail: Error 0400
Step 9: Monitor Logs for Notification
        ↓ (WAIT: 10 seconds or until speaking detected)
        
        Branch A: Notification NOT received
                → Ask user if they saw notification
                   Yes: Error 0404 (Service not receiving)
                   No: Error 0400 (Not posted)
        
        Branch B: Notification received but NOT read
                → Analyze blocking reason
                   Master switch: Error 0410
                   Audio mode: Error 0411
                   DND: Error 0412
                   Rules: Error 0413
                   Filtering: Error 0414
                   TTS issue: Error 0415
                   Unknown: Error 0420
        
        Branch C: Notification spoken successfully
                → Ask user if they heard it
                   Yes: SUCCESS (0000)
                   No: Error 0440 (Audio output issue)
```

---

## Common Scenarios & Solutions

### Scenario 1: Fresh Install, Error 0404
**Symptoms:** Just installed app, notification permission granted, but error 0404.

**Solution:**
1. The NotificationListener sometimes needs a system refresh
2. Toggle the permission OFF and ON
3. Restart the device
4. This is a known Android limitation, not an app bug

---

### Scenario 2: Worked Yesterday, Now Error 0410
**Symptoms:** App was working, now master switch disabled error.

**Possible Causes:**
- User accidentally toggled switch
- Rule action disabled the switch
- App data corruption

**Solution:**
1. Enable master switch on main screen
2. Check rules for "Disable Master Switch" actions
3. If recurring, disable problematic rules

---

### Scenario 3: Intermittent 0420 Errors
**Symptoms:** Sometimes passes, sometimes fails with unknown error.

**Possible Causes:**
- Battery optimization killing service
- Memory pressure causing issues
- Background restrictions
- Race condition in code

**Solution:**
1. Disable battery optimization for SpeakThat
2. Ensure background restrictions are off
3. Export logs when error occurs
4. Report pattern to developer

---

### Scenario 4: Always Error 0414, But No Filters Set
**Symptoms:** Filtering error but user has no filters configured.

**Possible Causes:**
- Deduplication blocking repeated tests
- Previous test notification in dismissal memory
- App-level filtering active

**Solution:**
1. Wait 5 minutes between tests (deduplication timeout)
2. Disable deduplication temporarily
3. Clear app data if issue persists
4. Note: SelfTest should bypass deduplication (may be a bug)

---

### Scenario 5: Error 0440, Volume is Maximum
**Symptoms:** Audio output error but volume is clearly high.

**Likely Cause:** Bluetooth or audio routing issue

**Solution:**
1. Check Bluetooth connections
2. Try different audio stream (Media instead of Notification)
3. Test with Bluetooth OFF
4. Check for split audio routing in system settings

---

## When to Report an Issue

### Report Immediately If:
- Error 0420 occurs consistently
- Error 0404 persists after permission toggle + restart
- Error 0414 when no filters are configured
- Any error with suspicious log entries (crashes, exceptions)
- New error patterns not documented here

### Information to Include:
1. Error code number
2. Full exported debug logs
3. System information (from Share Report)
4. Steps to reproduce
5. Frequency (always, sometimes, once)
6. Recent changes (app update, system update, settings changed)
7. Device manufacturer and model
8. Android version

---

## Developer Notes

### Adding New Error Codes

When adding a new error code:

1. Choose appropriate range:
   - 40x: Notification delivery
   - 41x: Configuration/settings
   - 42x: Unknown/miscellaneous
   - 43x: (Reserved for future use)
   - 44x: Audio/TTS output

2. Add string resources in `strings.xml`:
   ```xml
   <string name="selftest_error_0XXX_title">Error Title</string>
   <string name="selftest_error_0XXX_description">Detailed description...</string>
   ```

3. Update `getErrorTitleResourceId()` and `getErrorDescriptionResourceId()` in `SelfTestActivity.kt`

4. Update `showErrorForBlockingReason()` if applicable for automatic detection

5. Add to this documentation with:
   - Meaning
   - Common causes
   - Troubleshooting steps
   - Technical details
   - Related settings

6. Update diagnostic flow diagram if new step added

---

## Version History

- **v1.0** (2025-11-12): Initial error code catalogue
  - Codes: 0000, 0400, 0404, 0410, 0411, 0412, 0413, 0414, 0415, 0420, 0440, 0999
  - Comprehensive troubleshooting guide
  - Common scenarios documented

---

## Quick Reference Table

| Code | Title | Quick Fix |
|------|-------|-----------|
| 0000 | Success | None needed |
| 0400 | Not Posted | Check POST_NOTIFICATIONS permission |
| 0404 | Not Received | Toggle NotificationListener permission |
| 0410 | Master Switch Off | Enable master switch |
| 0411 | Audio Mode | Switch to Normal mode or disable Honor Audio Mode |
| 0412 | DND Active | Disable DND or disable Honor DND |
| 0413 | Blocked by Rules | Review and disable blocking rules |
| 0414 | Filtered | Check filters, deduplication, dismissal memory |
| 0415 | TTS Failed | Check TTS engine, restart app |
| 0420 | Unknown | Export logs, report issue |
| 0440 | Audio Issue | Check volume, Bluetooth, audio stream |
| 0999 | Interrupted | Don't use stop gestures during test |

---

## Additional Resources

- **Development Settings:** Enable verbose logging for detailed diagnostics
- **Test Settings:** System information and settings validation
- **Share Report:** Automatically includes system diagnostics with logs
- **In-App Logs:** Real-time logging of all events and decisions

---

*This document should be updated whenever new error codes are added or troubleshooting procedures change.*

