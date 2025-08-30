# SpeakThat Bluetooth Logic Documentation

This document provides a comprehensive overview of all Bluetooth-related functionality in the SpeakThat app, explaining how each component works and how they connect together to provide Bluetooth-based notification filtering.

## Table of Contents

1. [Overview](#overview)
2. [Core Components](#core-components)
3. [Data Structures](#data-structures)
4. [Permission Handling](#permission-handling)
5. [Bluetooth Detection Logic](#bluetooth-detection-logic)
6. [User Interface Components](#user-interface-components)
7. [Rule System Integration](#rule-system-integration)
8. [Condition System Integration](#condition-system-integration)
9. [Template System Integration](#template-system-integration)
10. [Error Handling and Fallbacks](#error-handling-and-fallbacks)
11. [Flow Diagrams](#flow-diagrams)

## Overview

The Bluetooth functionality in SpeakThat allows users to create rules and conditions that filter notifications based on Bluetooth device connections. This includes:

- **Triggers**: Rules that activate when specific Bluetooth devices connect/disconnect
- **Conditions**: Filters that only allow notifications when certain Bluetooth devices are connected
- **Exceptions**: Overrides that prevent rules from activating when specific Bluetooth devices are connected

The system supports both specific device selection and "any Bluetooth device" detection, with multiple fallback methods for reliable device detection.

## Core Components

### 1. BluetoothCondition.kt
**Purpose**: Implements the condition-based filtering system for Bluetooth devices.

**Key Features**:
- `BluetoothCondition` data class: Stores configuration (enabled state, allowed devices, connection requirement)
- `BluetoothConditionChecker` class: Implements the actual checking logic

**How it works**:
```kotlin
data class BluetoothCondition(
    override val enabled: Boolean = false,
    val allowedDevices: Set<String> = emptySet(), // Device addresses
    val requireConnected: Boolean = true
) : BaseCondition(enabled, "bluetooth")
```

The checker evaluates whether notifications should be allowed based on:
1. If the condition is enabled
2. If Bluetooth connection is required
3. If specific devices are selected, whether any are connected
4. If no devices are selected, allows all notifications

### 2. RuleEvaluator.kt - Bluetooth Trigger Logic
**Purpose**: Evaluates Bluetooth triggers in the rule system.

**Key Methods**:
- `evaluateBluetoothTrigger()`: Main evaluation method
- `evaluateAnyBluetoothDevice()`: Detects any Bluetooth device connection
- `evaluateSpecificBluetoothDevices()`: Detects specific device connections
- `getActivelyConnectedDevices()`: Gets currently connected devices
- `checkAnyBondedDeviceConnected()`: Fallback detection via audio routing
- `checkSpecificBondedDeviceConnected()`: Specific device fallback detection

**Detection Methods**:
1. **Primary Method**: Uses `BluetoothManager.getConnectedDevices()` with A2DP and HEADSET profiles
2. **Fallback Method**: Checks audio routing via `AudioManager` (A2DP/SCO status)
3. **Bonded Device Check**: Verifies devices are in the paired devices list

### 3. BluetoothConditionActivity.kt
**Purpose**: Standalone activity for managing Bluetooth device conditions.

**Features**:
- Device selection from paired devices
- Add/remove devices from the allowed list
- Persistent storage of device selections
- Bluetooth enablement prompting

**Key Methods**:
- `showDeviceSelectionDialog()`: Shows dialog with paired devices
- `addDevice()` / `removeDevice()`: Manages device list
- `loadSavedDevices()` / `saveDevices()`: Persistence handling

### 4. BluetoothDeviceAdapter.kt
**Purpose**: RecyclerView adapter for displaying Bluetooth devices in lists.

**Features**:
- Displays device name and MAC address
- Remove button for each device
- Dynamic list management

## Data Structures

### TriggerType.BLUETOOTH_DEVICE
```kotlin
enum class TriggerType(val displayName: String, val description: String) {
    BLUETOOTH_DEVICE("Bluetooth Device Connected", "When a specific Bluetooth device is connected"),
    // ... other types
}
```

### Trigger Data Structure
```kotlin
data class Trigger(
    val id: String = generateId(),
    val type: TriggerType,
    val enabled: Boolean = true,
    val inverted: Boolean = false,
    val data: Map<String, Any> = emptyMap(),
    val description: String = ""
)
```

**Bluetooth Trigger Data**:
- `device_addresses`: Set<String> - MAC addresses of required devices
- Empty set means "any Bluetooth device"

### ExceptionType.BLUETOOTH_DEVICE
Similar structure to triggers but for exceptions (overrides).

## Permission Handling

### Required Permissions
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
```

### Permission Checking Logic
```kotlin
private fun checkBluetoothPermissions(): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        // Android 12+ requires BLUETOOTH_CONNECT and BLUETOOTH_SCAN
        checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
        checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
        // Older versions use BLUETOOTH and BLUETOOTH_ADMIN
        checkSelfPermission(android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
        checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
    }
}
```

### Permission Request Flow
1. Check if permissions are granted
2. If not, request appropriate permissions based on Android version
3. Handle permission results in `onRequestPermissionsResult()`
4. Retry the original operation if permissions are granted

## Bluetooth Detection Logic

### Primary Detection Method
Uses `BluetoothManager.getConnectedDevices()` with multiple profiles:

```kotlin
private fun getActivelyConnectedDevices(): Set<BluetoothDevice> {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    
    val profiles = listOf(
        BluetoothProfile.A2DP,      // Audio devices (music, podcasts)
        BluetoothProfile.HEADSET    // Headset devices (calls)
    )
    
    val allConnectedDevices = mutableSetOf<BluetoothDevice>()
    
    for (profile in profiles) {
        try {
            val devices = bluetoothManager.getConnectedDevices(profile)
            allConnectedDevices.addAll(devices)
        } catch (e: Throwable) {
            // Profile not supported, continue with others
        }
    }
    
    return allConnectedDevices
}
```

### Fallback Detection Method
Uses audio routing information when primary method fails:

```kotlin
private fun checkAnyBondedDeviceConnected(): Boolean {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    val isBluetoothA2dpOn = audioManager.isBluetoothA2dpOn
    val isBluetoothScoOn = audioManager.isBluetoothScoOn
    val audioMode = audioManager.mode
    
    return isBluetoothA2dpOn || isBluetoothScoOn || 
           audioMode == AudioManager.MODE_IN_COMMUNICATION ||
           audioMode == AudioManager.MODE_IN_CALL
}
```

### Specific Device Detection
For specific devices, combines bonded device list with audio routing:

```kotlin
private fun checkSpecificBondedDeviceConnected(requiredDevices: Set<String>): Boolean {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    val bondedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
    
    // Check if any required device is in the bonded list
    val matchingBondedDevices = bondedDevices.filter { device ->
        requiredDevices.contains(device.address)
    }
    
    if (matchingBondedDevices.isEmpty()) {
        return false
    }
    
    // Then check if audio is being routed to Bluetooth
    return checkAnyBondedDeviceConnected()
}
```

## User Interface Components

### 1. BluetoothConditionActivity
**Layout**: `activity_bluetooth_condition.xml`
- Header section with description
- RecyclerView for selected devices
- Add device button
- Information section with instructions

**Features**:
- Material Design cards
- Dark/light mode support
- Responsive layout with ScrollView

### 2. BluetoothDeviceAdapter
**Layout**: `item_bluetooth_device.xml`
- Device name (bold, primary text color)
- MAC address (secondary text color)
- Remove button (red tint)

**Functionality**:
- Add/remove devices dynamically
- Click handling for removal
- Device list management

### 3. TriggerConfigActivity Bluetooth UI
**Features**:
- Device selection dialog
- "Any device" toggle
- Invert trigger option
- Device address input field

## Rule System Integration

### Trigger Creation
```kotlin
private fun createBluetoothTrigger(): Trigger {
    val deviceAddresses = if (binding.switchAnyDevice.isChecked) {
        emptySet<String>()
    } else {
        // Parse device addresses from UI
        binding.editDeviceAddresses.text.toString()
            .split("\n")
            .filter { it.isNotBlank() }
            .map { line ->
                val match = Regex("\\(([0-9A-Fa-f:]+)\\)").find(line)
                match?.groupValues?.get(1) ?: line
            }
            .toSet()
    }
    
    return Trigger(
        type = TriggerType.BLUETOOTH_DEVICE,
        data = mapOf("device_addresses" to deviceAddresses),
        description = if (deviceAddresses.isEmpty()) {
            "Any Bluetooth device connected"
        } else {
            "Specific Bluetooth devices: ${deviceAddresses.joinToString(", ")}"
        },
        inverted = binding.switchInvertBluetooth.isChecked
    )
}
```

### Trigger Evaluation
```kotlin
private fun evaluateBluetoothTrigger(trigger: Trigger): EvaluationResult {
    // Check Bluetooth availability and permissions
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
        return EvaluationResult(success = false, message = "Bluetooth not available/disabled")
    }
    
    // Get required device addresses
    val deviceAddressesData = trigger.data["device_addresses"]
    val requiredDevices = when (deviceAddressesData) {
        is Set<*> -> deviceAddressesData.filterIsInstance<String>().toSet()
        is List<*> -> deviceAddressesData.filterIsInstance<String>().toSet()
        else -> emptySet<String>()
    }
    
    // Evaluate based on whether specific devices are required
    return if (requiredDevices.isEmpty()) {
        evaluateAnyBluetoothDevice()
    } else {
        evaluateSpecificBluetoothDevices(requiredDevices)
    }
}
```

## Condition System Integration

**IMPORTANT**: The condition system now uses the same robust detection logic as the trigger system, ensuring consistency across the app.

### Condition Evaluation
```kotlin
override fun shouldAllowNotification(context: Context): Boolean {
    if (!condition.enabled) {
        return true // No restriction if disabled
    }
    
    if (!condition.requireConnected) {
        return true // No Bluetooth requirement
    }
    
    if (condition.allowedDevices.isEmpty()) {
        return true // Allow if no devices specified
    }
    
    // Check if any allowed devices are connected using robust detection
    return checkIfAllowedDevicesConnected(condition.allowedDevices)
}

/**
 * Check if any of the allowed devices are connected using multiple detection methods
 */
private fun checkIfAllowedDevicesConnected(allowedDevices: Set<String>): Boolean {
    // Method 1: Try to get actively connected devices via BluetoothManager
    val connectedDevices = getActivelyConnectedDevices()
    val connectedAddresses = connectedDevices.map { it.address }.toSet()
    
    // Check if any allowed device is actively connected
    val specificDeviceConnected = allowedDevices.any { it in connectedAddresses }
    if (specificDeviceConnected) {
        return true
    }
    
    // Method 2: Check if any allowed device is bonded and has active audio routing
    return checkSpecificBondedDeviceConnected(allowedDevices)
}
}
```

## Template System Integration

### Bluetooth Headphones Template
```kotlin
// From RuleTemplates.kt
RuleTemplate(
    name = "Bluetooth Headphones",
    description = "Select your Bluetooth headphones, and notifications will ONLY be read when they are connected to your phone",
    trigger = Trigger(
        type = TriggerType.BLUETOOTH_DEVICE,
        data = mapOf("device_addresses" to emptySet<String>()),
        description = "When Bluetooth headphones are connected"
    ),
    action = Action(
        type = ActionType.DISABLE_SPEAKTHAT,
        description = "Skip this notification"
    )
)
```

### Template Selection Flow
1. User selects "Bluetooth Headphones" template
2. System checks Bluetooth permissions
3. If permissions granted, shows device selection dialog
4. User selects specific headphones
5. Template is customized with selected devices
6. Rule is created and saved

## Error Handling and Fallbacks

### Graceful Degradation
1. **Bluetooth Not Available**: Returns false/error with appropriate message
2. **Bluetooth Disabled**: Prompts user to enable Bluetooth
3. **No Permissions**: Requests permissions with explanation
4. **No Paired Devices**: Shows helpful message to pair devices first
5. **Profile Not Supported**: Falls back to audio routing detection

### Error Messages
- "Bluetooth not available on this device"
- "Please enable Bluetooth to select devices"
- "No paired Bluetooth devices found. Please pair a device first."
- "Bluetooth permissions are required to select devices."

### Fallback Detection Methods
1. **Primary**: `BluetoothManager.getConnectedDevices()` with profiles
2. **Secondary**: Audio routing detection via `AudioManager`
3. **Tertiary**: Bonded device list verification

## Flow Diagrams

### Bluetooth Trigger Evaluation Flow
```
Start
  ↓
Check Bluetooth Available?
  ↓ No → Return False
  ↓ Yes
Check Bluetooth Enabled?
  ↓ No → Return False
  ↓ Yes
Check Permissions?
  ↓ No → Request Permissions → Return False
  ↓ Yes
Get Required Devices
  ↓
Any Specific Devices Required?
  ↓ No → Check Any Device Connected
  ↓ Yes → Check Specific Devices Connected
  ↓
Return Evaluation Result
```

### Device Selection Flow
```
User Clicks "Add Device"
  ↓
Check Bluetooth Permissions
  ↓ No → Request Permissions
  ↓ Yes
Check Bluetooth Enabled
  ↓ No → Prompt to Enable
  ↓ Yes
Get Bonded Devices
  ↓
Show Device Selection Dialog
  ↓
User Selects Device
  ↓
Add to Device List
  ↓
Save to SharedPreferences
```

### Condition Evaluation Flow
```
Notification Received
  ↓
Check if Bluetooth Condition Enabled
  ↓ No → Allow Notification
  ↓ Yes
Check if Connection Required
  ↓ No → Allow Notification
  ↓ Yes
Check if Devices Specified
  ↓ No → Allow Notification
  ↓ Yes
Check if Any Specified Device Connected
  ↓ Yes → Allow Notification
  ↓ No → Block Notification
```

## Key Implementation Details

### Device Address Handling
- MAC addresses are stored as strings in the format "XX:XX:XX:XX:XX:XX"
- Addresses are extracted from "Name (Address)" format in UI
- Case-insensitive comparison for robustness

### State Management
- Device selections are stored in SharedPreferences
- Rule configurations are stored in JSON format
- Conditions are evaluated in real-time during notification processing

### Performance Considerations
- Bluetooth detection is cached and reused during rule evaluation
- Audio routing checks are lightweight fallbacks
- Device lists are only refreshed when needed

### Battery Optimization
- Bluetooth detection uses system APIs efficiently
- No continuous polling or background monitoring
- Detection only occurs when rules are evaluated

This comprehensive Bluetooth system provides reliable device detection with multiple fallback methods, ensuring that notification filtering works consistently across different devices and Android versions.
