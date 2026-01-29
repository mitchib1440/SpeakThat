# SpeakThat External Automation Intents

This document explains how to control SpeakThat via broadcast intents from automation tools (MacroDroid, Tasker, Automate, etc.). These intents mirror the manual master switch and only work when the user deliberately enables the `External Automation` mode inside the app.

---

## 1. Prerequisites

- SpeakThat vNext (automation mode radio group present in `Settings → Conditional Rules`).
- User has selected the `External Automation` radio option. When this mode is active the built-in Conditional Rules engine is paused.
- The app must remain installed under the package name `com.micoyc.speakthat`. Rename/split APKs are not supported.

If External Automation is turned off, every broadcast is ignored; no errors are thrown so make sure the user confirms their selection before testing scripts.

---

## 2. Broadcast Specification

| Field | Value |
| --- | --- |
| **Package** | `com.micoyc.speakthat` |
| **Component** | `com.micoyc.speakthat.automation.AutomationBroadcastReceiver` |
| **Exported** | Yes (but dynamically disabled unless External Automation mode is active) |
| **Permission** | None required (intents are explicit) |
| **Supported Actions** | `com.micoyc.speakthat.intent.ACTION_ENABLE_SPEAKTHAT`<br>`com.micoyc.speakthat.intent.ACTION_DISABLE_SPEAKTHAT` |
| **Categories** | _Not required_ |
| **Data / MIME type** | _None_ |
| **Extras** | Optional: `source` (String) — describe your automation profile so it appears in logs, e.g. `"MacroDroid:BTCar"` |
| **Recommended Flags** | `FLAG_INCLUDE_STOPPED_PACKAGES` so the intent still lands after a reboot,<br>`FLAG_RECEIVER_FOREGROUND` if your automation app allows it and you need faster delivery. |

The receiver simply toggles the master switch. It does **not** try to schedule state changes or restore Conditional Rules; automation workflows should explicitly send the match action whenever the environment changes (car connected/disconnected, work hours, etc.).

---

## 3. Example Configurations

### 3.1 MacroDroid
1. Create a new Macro.
2. Choose any trigger (e.g. “Bluetooth Device Connected – Car Stereo”).
3. Add an `Action → System Settings → Send Intent`.
4. Fill the fields:
   - `Action`: `com.micoyc.speakthat.intent.ACTION_ENABLE_SPEAKTHAT`
   - `Package`: `com.micoyc.speakthat`
   - `Class`: `com.micoyc.speakthat.automation.AutomationBroadcastReceiver`
   - `Categories`, `Data`, `Mime Type`: leave blank.
   - `Extras`: `source:String:MacroDroid-CarIn`
   - Enable the `Include stopped packages` flag.
5. Clone the macro, swap the trigger to “Bluetooth Device Disconnected – Car Stereo”, and change the action to `com.micoyc.speakthat.intent.ACTION_DISABLE_SPEAKTHAT` with a matching `source` note (e.g. `MacroDroid-CarOut`).

### 3.2 Tasker
Create a Task → `System → Send Intent` and use the same field values. Pair it with Profiles such as `State → Net → BT Connected` (enable) and `State → Net → BT Connected (invert)` (disable). Tasker automatically keeps the last package/class you entered for faster editing.

### 3.3 Broadcast Testing via ADB
```bash
adb shell am broadcast \
  -p com.micoyc.speakthat \
  -n com.micoyc.speakthat/.automation.AutomationBroadcastReceiver \
  -a com.micoyc.speakthat.intent.ACTION_ENABLE_SPEAKTHAT \
  --es source "adb quick test" \
  --receiver-include-stopped-packages
```
Replace `ACTION_ENABLE_SPEAKTHAT` with `ACTION_DISABLE_SPEAKTHAT` to mute.

---

## 4. Operational Notes

- **Mutual Exclusivity**: External Automation mode disables Conditional Rules entirely. To go back, open `Settings → Conditional Rules`, choose `Conditional Rules`, and automation intents will stop being processed.
- **Master Switch Visibility**: Broadcast changes immediately update the master switch, persistent notification, Quick Settings tile, and any UI currently on screen.
- **Logging**: Each broadcast writes to `InAppLogger` with the `source` string (if provided). Keep your labels short and recognizable; they appear in stress-test logs.
- **Crash Safety**: The receiver simply calls `MasterSwitchController` and returns; there is no long-running work, so you can freely chain these intents.
- **Reboots**: Automation apps should resend the desired state after boot (e.g., Tasker profile `Event → System → Device Boot`). SpeakThat does not persist “automation overrides”; it only persists the master switch itself.

---

## 5. Troubleshooting

| Symptom | Checks |
| --- | --- |
| Intent has no effect | Ensure External Automation mode is enabled.<br>Confirm package/class spelling; both must point to `com.micoyc.speakthat`.<br>Verify your automation tool is allowed to run in the background (battery optimizations). |
| Master switch flips back immediately | Another automation or the Quick Settings tile toggled it. Check logs (`Settings → Development → View Logcat (filtered)`). |
| Conditional Rules stopped working | This is expected while External Automation is selected; switch back to `Conditional Rules` mode to re-enable the internal engine. |
| Need to script multiple devices | Use distinct `source` extras (e.g., `"Tasker-Home"`, `"Tasker-Car"`) so logs show which profile last toggled SpeakThat. |

---

With these settings your automation app can act as the single source of truth for enabling or disabling SpeakThat. Always document the triggers and keep the enable/disable pair symmetrical to avoid leaving the master switch in the wrong state.

