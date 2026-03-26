# PermissionSyncManager - Manual QA Checklist

## Test devices / OS versions
- Android 10 (API 29)
- Android 11 (API 30)
- Android 12 (API 31/32)
- Android 13+ (API 33+)

## Pre-test setup (per device)
1. Start from a clean app state (fresh install or `Clear All Data`).
2. Make sure the notification listener service is enabled (if you rely on readout/persistent features).
3. Ensure the runtime permissions you want to test are *missing*:
   - `READ_PHONE_STATE` (Honour Phone Calls)
   - `POST_NOTIFICATIONS` (Persistent indicators / notifications while reading)
   - WiFi permissions (WiFi rules)
   - Bluetooth permissions (Bluetooth rules)
4. Ensure system settings permissions are *missing*:
   - `Draw over other apps` (SYSTEM_ALERT_WINDOW)
   - `Exact alarms` (SCHEDULE_EXACT_ALARM)

## 1) Full import (General Settings JSON) - core permission sync
### Case A: Honour phone calls + missing `READ_PHONE_STATE`
1. Import a JSON that sets `honour_phone_calls = true`.
2. Verify the app shows a single “Permissions Required” dialog (title: `Permissions Required`).
3. Tap `Enable`.
4. Verify the runtime prompt for `READ_PHONE_STATE` appears.
5. After the import completes (no manual toggle), verify “Honour Phone Calls” actually works (calls are honoured).

### Case B: Deny `READ_PHONE_STATE`
1. Repeat Case A but deny the runtime permission.
2. Verify the “Honour Phone Calls” toggle ends up disabled/off after the sync finishes.

### Case C: Persistent notifications + missing `POST_NOTIFICATIONS` (Android 13+)
1. Import a JSON with:
   - `persistent_notification = true` (and/or `notification_while_reading = true`)
   - Master switch enabled
2. Verify the same summary dialog includes “Notifications (POST_NOTIFICATIONS)”.
3. Tap `Enable` and grant `POST_NOTIFICATIONS`.
4. Verify persistent indicator behavior works immediately after import.

### Case D: Summary overlay + missing overlay + exact alarms
1. Import a JSON with summary overlay enabled (global enabled + scheduler enabled) and clock precision enabled.
2. Verify the sync dialog opens:
   - Overlay settings screen first (Draw over other apps)
   - Exact alarms settings screen next (if still required)
3. Verify overlay rendering and scheduler/clock precision behave immediately.

### Case E: Deny overlay / exact alarms
1. Deny overlay permission in settings.
2. Verify summary overlay/scheduler toggles end up disabled/off.
3. Deny exact alarms permission (precision + scheduler paths).
4. Verify clock precision mode becomes disabled/off and summary scheduler is disabled.

## 2) Full import - GitHub updater permission (REQUEST_INSTALL_PACKAGES)
1. Use a GitHub build variant (ENABLE_AUTO_UPDATER = true).
2. Import a JSON with `auto_update_enabled = true`.
3. Verify the dialog includes “Self-updater (install packages)” when the permission is missing.
4. Deny `REQUEST_INSTALL_PACKAGES`.
5. Verify `auto_update_enabled` is reverted to `false` after sync.

## 3) Conditional Rules (RulesActivity) - runtime sync (WiFi/Bluetooth)
### Case A: Import CR rules requiring WiFi with WiFi permissions missing
1. Ensure WiFi permissions are missing.
2. Import a Conditional Rules JSON containing WiFi triggers.
3. Verify the dialog includes “Wi-Fi rules (network + location)”.
4. Tap `Enable`, grant runtime permissions.
5. Verify imported WiFi rules are present and active.

### Case B: Deny WiFi permissions
1. Repeat Case A but deny.
2. Verify WiFi rules are skipped (filtered out) and only non-WiFi-licensed rules apply.

### Case C: Import CR rules requiring Bluetooth with Bluetooth permissions missing
1. Ensure Bluetooth permissions are missing.
2. Import Conditional Rules JSON containing Bluetooth triggers.
3. Verify the dialog includes “Bluetooth rules”.
4. Deny and verify Bluetooth rules are skipped.

## 4) Sanity checks after any test
1. Re-open the relevant screen and confirm toggles reflect the reconciled state.
2. Start the main service/master switch and verify there are no crashes or dead states.

