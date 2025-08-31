# Ducking Volume Improvements - Android "Tricks" Implementation

## Overview

The "Lower Volume" (ducking) mode in SpeakThat has been enhanced with several Android-specific "tricks" to prevent TTS volume reduction when ducking is active. These improvements address the fundamental limitation where manual volume ducking affects both media and TTS when they share the same audio stream.

## Problem Analysis

### Root Cause
When using manual volume ducking (`duckMediaVolume()`), the TTS volume is also reduced because:
1. Both media and TTS are often routed through the same `STREAM_MUSIC` on many devices
2. `audioManager.setStreamVolume(STREAM_MUSIC, ...)` affects all audio in that stream
3. Device-specific audio routing policies may override TTS audio attributes

### Device Variability
- Some devices route TTS through `STREAM_MUSIC` regardless of `AudioAttributes.USAGE_*`
- Different Android versions and manufacturer customizations handle audio routing differently
- Audio focus policies vary significantly across devices and ROMs

## Implemented Android "Tricks"

### Trick 1: Stream-Specific Ducking
**Location**: `tryStreamSpecificDucking()`

**Approach**: Instead of ducking the entire `STREAM_MUSIC`, target specific scenarios:

- **Isolated Streams**: If TTS uses `USAGE_NOTIFICATION` or `USAGE_ALARM`, safely duck only `STREAM_MUSIC`
- **Less Aggressive Ducking**: For TTS using `USAGE_MEDIA`, use a higher ducking volume (+20%) to minimize TTS impact
- **Stream Analysis**: Check current volumes across different streams to make informed decisions

**Benefits**:
- Preserves TTS volume when using isolated audio streams
- Reduces TTS impact when using shared streams
- More targeted approach than blanket volume reduction

### Trick 2: Enhanced VolumeShaper Integration
**Location**: Enhanced `duckMediaVolume()` with VolumeShaper

**Approach**: Use Android's `VolumeShaper` API for smooth transitions and better integration:

- **Smooth Transitions**: Gradual volume changes instead of abrupt drops
- **System Integration**: Better integration with Android's audio policy
- **TTS Compensation**: Apply volume compensation when VolumeShaper is used

**Benefits**:
- More professional audio experience
- Better system integration on Android 8.0+
- Reduced audio artifacts

### Trick 3: TTS Volume Compensation
**Location**: `applyTtsVolumeCompensation()` and `restoreTtsVolumeCompensation()`

**Approach**: Actively compensate for TTS volume reduction during ducking:

- **Dynamic Compensation**: Calculate compensation factor based on ducking level:
  - Heavy ducking (≤20%): 1.5x compensation
  - Medium ducking (≤40%): 1.3x compensation  
  - Light ducking (>40%): 1.2x compensation
- **Usage-Based Targeting**: Only apply to TTS using `USAGE_MEDIA` or `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`
- **Automatic Restoration**: Restore original TTS volume when ducking ends

**Benefits**:
- Maintains TTS audibility during ducking
- Automatic compensation and restoration
- Usage-specific targeting to avoid over-compensation

### Trick 4: Alternative Audio Focus Strategy
**Location**: `tryAlternativeDuckingFocus()` and `handleAlternativeDuckingFocusChange()`

**Approach**: Try different audio focus strategies that might work better on specific devices:

- **Usage Switching**: Temporarily use `USAGE_ALARM` for TTS during ducking (some devices handle this better)
- **Alternative Focus Request**: Use `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` with different audio attributes
- **Device-Specific Optimization**: Leverage device-specific audio policies

**Benefits**:
- Better compatibility with device-specific audio policies
- Alternative approach when standard ducking fails
- Device-specific optimizations

### Trick 5: Less Aggressive Fallback Strategy
**Location**: Enhanced `tryEnhancedDucking()` and fallback logic

**Approach**: Make the fallback to manual volume control less aggressive to avoid fighting with system ducking:

- **Retry Mechanism**: Retry audio focus request once after a 100ms delay
- **Delayed Fallback**: Wait 200ms before checking if system ducking is working
- **Conditional Fallback**: Only fall back to manual ducking if music volume is still >30%
- **Alternative Usage Types**: Try `USAGE_ASSISTANCE_ACCESSIBILITY` and `USAGE_MEDIA` before falling back

**Benefits**:
- Prevents unnecessary fighting with system audio policies
- Gives system ducking time to take effect
- Reduces race conditions between app and system audio handling
- Better compatibility with device-specific audio policies

## Implementation Details

### Volume Compensation Logic
```kotlin
val compensationFactor = when {
    duckingVolume <= 20 -> 1.5f  // Heavy ducking - more compensation
    duckingVolume <= 40 -> 1.3f  // Medium ducking - moderate compensation
    else -> 1.2f                 // Light ducking - slight compensation
}
```

### Stream-Specific Decision Making
```kotlin
// If TTS is using isolated stream, safe to duck MUSIC stream
if (ttsUsage == USAGE_NOTIFICATION || ttsUsage == USAGE_ALARM) {
    // Safe to duck MUSIC stream only
} else if (ttsUsage == USAGE_MEDIA) {
    // Use less aggressive ducking to minimize TTS impact
    val lessAggressiveDuckVolume = (maxVolume * (duckingVolume + 20) / 100)
}
```

### Less Aggressive Fallback Logic
```kotlin
// Retry audio focus request after delay
Thread.sleep(100)
val retryResult = audioManager.requestAudioFocus(focusRequest)

// Wait for system ducking to take effect
Thread.sleep(200)

// Only fall back if music is still loud
val volumePercentage = (currentVolume * 100) / maxVolume
if (volumePercentage <= 30) {
    // Assume system ducking is working
    return true
}

// Try alternative usage types
val alternativeUsages = listOf(
    USAGE_ASSISTANCE_ACCESSIBILITY, // 11
    USAGE_MEDIA, // 1
    originalUsage // Fallback
)
```

### Automatic Cleanup
- TTS volume compensation is automatically restored when:
  - Media volume is restored
  - Media behavior cleanup occurs
  - TTS completes or is interrupted

## Testing Recommendations

### Test Scenarios
1. **Different TTS Usage Settings**: Test with each audio usage type (Media, Notification, Alarm, etc.)
2. **Various Ducking Levels**: Test with different ducking volume settings (10%, 30%, 50%)
3. **Device Switching**: Test app switching during TTS with ducking active
4. **Background/Foreground**: Test app going to background during TTS with ducking

### Expected Behavior
- **TTS Volume Preservation**: TTS should remain audible during ducking
- **Smooth Transitions**: Volume changes should be gradual, not abrupt
- **Automatic Restoration**: TTS volume should return to normal when ducking ends
- **Device Compatibility**: Should work across different Android devices and versions

## Limitations and Considerations

### Known Limitations
- **Device-Specific Behavior**: Some devices may still have issues due to manufacturer customizations
- **Audio Focus Policies**: Device-specific audio focus policies may override our strategies
- **Android Version Differences**: Behavior may vary between Android versions

### Performance Impact
- **Minimal Overhead**: Volume compensation calculations are lightweight
- **Memory Usage**: Additional variables for tracking compensation state
- **Battery Impact**: Negligible - only active during TTS with ducking

### User Experience
- **Transparency**: Most improvements are transparent to users
- **Fallback Behavior**: If tricks fail, falls back to original behavior
- **Logging**: Comprehensive logging for debugging and user support

## Future Enhancements

### Potential Improvements
1. **Machine Learning**: Learn device-specific behavior patterns
2. **User Feedback**: Collect user feedback on ducking effectiveness
3. **Advanced Audio Analysis**: Real-time audio stream analysis
4. **Custom Audio Policies**: Device-specific audio policy overrides

### Android API Evolution
- **New Audio APIs**: Monitor for new Android audio APIs that might help
- **Audio Focus Improvements**: Future Android versions may improve audio focus handling
- **VolumeShaper Enhancements**: Enhanced VolumeShaper capabilities in newer Android versions

## Critical Fix: Foreground Service Requirement (Android 12+)

### Root Cause Identified
After deep research, the primary issue was identified: **On Android 12+ (API 31+), audio focus requests from background services are denied unless the service is promoted to a foreground service with a visible notification.**

Additionally, on Android 14+ (API 34+), **each foreground service type requires a specific runtime permission** to be declared in the manifest.

This explains why your logs showed:
```
Audio focus denied for: Assistant usage with transient focus
```

And later:
```
SecurityException: Starting FGS with type mediaPlayback requires permissions: [android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK]
```

### Implementation
**Location**: `promoteToForegroundService()` and `stopForegroundService()`

**Approach**: 
- **Automatic Promotion**: Service is automatically promoted to foreground when TTS starts
- **Foreground Notification**: Shows "SpeakThat - Reading notifications aloud" notification
- **Automatic Cleanup**: Service returns to background when TTS completes
- **Android 12+ Compatibility**: Uses `android:foregroundServiceType="mediaPlayback"` in manifest
- **Android 14+ Permission**: Requires `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission

**Benefits**:
- Enables audio focus requests to be granted on Android 12+
- Allows system ducking to work properly
- Maintains user experience with minimal notification intrusion
- Automatic lifecycle management

### Manifest Changes
```xml
<!-- Permission for foreground service media playback (Android 14+) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<service
    android:name=".NotificationReaderService"
    android:foregroundServiceType="mediaPlayback">
```

## Conclusion

These Android "tricks" provide a comprehensive approach to preventing TTS volume reduction during ducking mode. The implementation uses multiple strategies that work together to provide the best possible experience across different devices and Android versions.

**The key breakthrough was implementing foreground service promotion during TTS playback**, which enables the Android audio focus API to work properly on Android 12+. This should resolve the core issue where audio focus requests were being denied, forcing the app to fall back to manual volume control.

The key insight is that no single approach works universally, so we implement multiple fallback strategies that can adapt to different device behaviors and audio policies. This ensures that users get the best possible ducking experience while maintaining TTS audibility.
