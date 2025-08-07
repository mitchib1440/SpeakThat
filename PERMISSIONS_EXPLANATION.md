# SpeakThat Permissions Explanation

This document explains why SpeakThat requires each permission, making it easier for open source stores like F-Droid to review and understand the app's permission requirements.

## Core Functionality Permissions

### `BIND_NOTIFICATION_LISTENER_SERVICE`
- **Purpose**: Core app functionality
- **Usage**: Reading notification content aloud to the user
- **Why needed**: This is the primary feature of the app - without this permission, the app cannot function
- **User control**: User must explicitly enable this in Android's notification access settings

### `POST_NOTIFICATIONS` (Android 13+)
- **Purpose**: Service status notifications
- **Usage**: Shows a persistent notification when the service is active
- **Why needed**: Required by Android 13+ to show any notifications
- **User control**: User can disable notifications in app settings

### `MODIFY_AUDIO_SETTINGS`
- **Purpose**: Text-to-speech functionality
- **Usage**: Allows the app to speak notification content aloud
- **Why needed**: Required for TTS to work properly
- **User control**: User controls TTS settings in the app

## Internet and Network Permissions

### `INTERNET`
- **Purpose**: Network voice downloads
- **Usage**: Downloading TTS voices from Google's servers when users select network voices
- **Why needed**: Android TTS engine requires internet access to download network voices
- **User control**: User chooses which voices to use (local vs network)
- **Privacy note**: Only used for legitimate TTS voice downloads, not tracking or analytics

### `ACCESS_WIFI_STATE` and `ACCESS_NETWORK_STATE`
- **Purpose**: WiFi-based conditional filtering
- **Usage**: Smart rules that filter notifications based on WiFi network
- **Why needed**: Allows users to create rules like "only read notifications when connected to home WiFi"
- **User control**: User creates and controls these rules

### `NEARBY_WIFI_DEVICES` (Android 13+)
- **Purpose**: WiFi device scanning for conditional rules
- **Usage**: Enhanced WiFi-based filtering capabilities
- **Why needed**: Required by Android 13+ for WiFi scanning features
- **User control**: User controls which rules use WiFi conditions

## Bluetooth Permissions

### `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`
- **Purpose**: Bluetooth-based conditional filtering
- **Usage**: Smart rules that filter notifications based on Bluetooth device connections
- **Why needed**: Allows users to create rules like "only read notifications when headphones are connected"
- **User control**: User creates and controls these rules
- **Privacy note**: Only scans for paired devices, no tracking of unknown devices

## Location Permissions

### `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION`
- **Purpose**: Location-based conditional filtering
- **Usage**: Smart rules that filter notifications based on user's location
- **Why needed**: Allows users to create rules like "don't read notifications when at work"
- **User control**: User creates and controls these rules
- **Privacy note**: Location data stays on device, never transmitted

## System Integration Permissions

### `BIND_QUICK_SETTINGS_TILE`
- **Purpose**: Quick Settings tile
- **Usage**: Allows users to quickly enable/disable the service from Quick Settings
- **Why needed**: Provides convenient access to service controls
- **User control**: User can remove the tile from Quick Settings

### `RECEIVE_BOOT_COMPLETED`
- **Purpose**: Auto-start on boot
- **Usage**: Automatically starts the service after device reboot
- **Why needed**: Ensures service continues working after device restarts
- **User control**: User can disable auto-start in app settings

### `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- **Purpose**: Battery optimization exemption
- **Usage**: Prevents the service from being killed by battery optimization
- **Why needed**: Ensures reliable notification reading service
- **User control**: User must explicitly grant this permission

## Storage Permissions

### `WRITE_EXTERNAL_STORAGE` (Android < API 29)
- **Purpose**: File export/import
- **Usage**: Export settings and notification history to files
- **Why needed**: Allows users to backup their settings and data
- **User control**: User initiates export operations
- **Note**: Not needed on Android 10+ due to scoped storage

## Phone State Permission

### `READ_PHONE_STATE`
- **Purpose**: Phone call detection
- **Usage**: Pauses TTS when phone calls are active
- **Why needed**: Prevents TTS from interrupting phone calls
- **User control**: User can disable this feature in settings

## Queries

### `android.intent.action.MAIN`
- **Purpose**: App filtering
- **Usage**: Allows users to search and select apps by name for filtering
- **Why needed**: Enables the app selection feature in filters
- **User control**: User controls which apps are filtered

## Privacy and Transparency

### Data Handling
- All notification content is processed locally on the device
- No notification data is ever transmitted to external servers
- Location data (if used) stays on device
- Bluetooth scanning only detects paired devices
- Internet access is only used for legitimate TTS voice downloads

### User Control
- Users have full control over which notifications are read
- All permissions can be revoked by the user
- Conditional rules are created and managed by the user
- Service can be completely disabled at any time

### Store Variant Differences
The Store variant (for F-Droid, Play Store, etc.) has additional privacy measures:
- No auto-updater (updates handled by stores)
- No online app icons (uses local fallback icons only)
- No network libraries (excludes OkHttp and Coil)
- Internet permission only used for TTS voice downloads

## Compliance

This app complies with:
- F-Droid's anti-features policy
- Google Play Store's privacy requirements
- General Data Protection Regulation (GDPR) principles
- Android's privacy best practices

All permissions are used for legitimate app functionality and user-requested features.
