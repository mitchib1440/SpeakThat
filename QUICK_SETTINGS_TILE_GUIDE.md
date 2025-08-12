# SpeakThat Quick Settings Tile Guide

## Overview

SpeakThat now includes a Quick Settings tile that allows you to quickly toggle the app's master switch without opening the app. This provides instant access to enable or disable notification reading from anywhere on your device.

## How to Add the Quick Settings Tile

### Step 1: Open Quick Settings
1. Swipe down from the top of your screen to open the Quick Settings panel
2. Swipe down again to expand the full Quick Settings view

### Step 2: Edit Quick Settings
1. Tap the pencil/edit icon (usually in the bottom-left corner)
2. This opens the Quick Settings editor

### Step 3: Add SpeakThat Tile
1. Scroll through the available tiles to find "SpeakThat"
2. Drag the SpeakThat tile to your Quick Settings panel
3. Tap "Done" to save your changes

## How to Use the Quick Settings Tile

### Toggle SpeakThat
- **Tap the tile once** to toggle SpeakThat on/off
- The tile will show "Enabled" when active, "Disabled" when inactive
- A brief toast message confirms the action

### Visual Indicators
- **Active State**: Tile appears highlighted/colored, shows "Enabled"
- **Inactive State**: Tile appears dimmed, shows "Disabled"
- **Icon**: Uses the SpeakThat logo for easy identification

## Perfect Sync with Main App

The Quick Settings tile maintains perfect synchronization with the main app:

- **Changes from tile** are immediately reflected in the main app
- **Changes from main app** are immediately reflected in the tile
- **No conflicts** - both controls always show the same state
- **Real-time updates** when the app is open

## Troubleshooting

### Tile Not Available
If you don't see the SpeakThat tile in the Quick Settings editor:

1. **Check Android Version**: Quick Settings tiles require Android 7.0 or higher
2. **Restart Device**: Try restarting your device and check again
3. **Reinstall App**: If the issue persists, try reinstalling SpeakThat

### Tile Not Responding
If the tile doesn't respond when tapped:

1. **Check Permissions**: Ensure SpeakThat has notification access
2. **Restart Quick Settings**: Close and reopen the Quick Settings panel
3. **Restart Device**: Try restarting your device

### Sync Issues
If the tile and main app show different states:

1. **Refresh Tile**: Open the main app to sync the state
2. **Restart App**: Close and reopen SpeakThat
3. **Check Logs**: Use the app's logging feature to diagnose issues

## Technical Details

### Battery Impact
- **Minimal**: The tile only processes when you interact with it
- **No Background Monitoring**: No continuous background activity
- **Efficient Updates**: Only updates when the Quick Settings panel is open

### Compatibility
- **Android 7.0+**: Full support with all features
- **Older Versions**: Graceful fallback - tile simply won't appear
- **All Devices**: Works on phones, tablets, and other Android devices

### Permissions
The Quick Settings tile requires:
- `BIND_QUICK_SETTINGS_TILE` - System permission for Quick Settings tiles
- `BIND_NOTIFICATION_LISTENER_SERVICE` - For notification access (already required)

## Support

If you encounter any issues with the Quick Settings tile:

1. Check the app's built-in logging for error details
2. Ensure you're using the latest version of SpeakThat
3. Report issues through the app's feedback system

---

**Note**: The Quick Settings tile is designed to be simple, reliable, and battery-efficient. It provides the same functionality as the main app's master switch but with the convenience of quick access from anywhere on your device. 