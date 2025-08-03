# Update System: How It Works with Build Variants

## Overview

The update system has been modified to work seamlessly with the new build variants while maintaining backwards compatibility with existing users.

## Key Changes Made

### 1. **Version Name Handling**

**Problem:** The original build configuration added `-github` and `-store` suffixes to version names, which would break version comparison for existing users.

**Solution:** 
- Removed `versionNameSuffix` from both flavors to keep version names clean
- Added suffix stripping logic in `UpdateManager` for backwards compatibility
- All versions are now compared as clean semantic versions (e.g., "1.3.2")

### 2. **APK Filtering for GitHub Users**

**Problem:** Both APK variants will be uploaded to the same GitHub release, but GitHub users should only download the auto-update version.

**Solution:**
- Modified `fetchLatestReleaseInfo()` to filter out APKs containing "NoUpdate" in the filename
- GitHub users will only see and download APKs without "NoUpdate" in the name
- Store users don't use the auto-updater, so they're unaffected

### 3. **Backwards Compatibility**

**Problem:** Existing users have no idea about build variants or suffixes.

**Solution:**
- Added `stripVersionSuffix()` method to handle any existing suffixes
- Version comparison works with clean semantic versions
- Existing users continue to work without any issues

## How It Works

### **For GitHub Users (Direct Downloads):**

1. **App checks for updates** via `UpdateManager.checkForUpdates()`
2. **Fetches GitHub release info** from the latest release
3. **Filters APK assets** - skips any APK with "NoUpdate" in the filename
4. **Downloads appropriate APK** - only the auto-update version
5. **Installs update** using `REQUEST_INSTALL_PACKAGES` permission

### **For Store Users (F-Droid, Play Store, etc.):**

1. **No auto-update functionality** - `UpdateFeature.isEnabled()` returns `false`
2. **Stores handle updates** - they download the "NoUpdate" APK automatically
3. **No `REQUEST_INSTALL_PACKAGES` permission** - store variant doesn't have it

## APK Naming Strategy

### **GitHub Release Structure:**
```
Release: "SpeakThat! v1.3.2"
Assets:
├── SpeakThat-v1.3.2.apk          (GitHub users - with auto-updater)
└── SpeakThat-NoUpdate-v1.3.2.apk (Store users - no auto-updater)
```

### **How Each User Type Gets Updates:**

**GitHub Users:**
- Download: `SpeakThat-v1.3.2.apk`
- Auto-update: ✅ Enabled
- Permissions: `REQUEST_INSTALL_PACKAGES` ✅

**Store Users:**
- Download: `SpeakThat-NoUpdate-v1.3.2.apk`
- Auto-update: ❌ Disabled (stores handle it)
- Permissions: `REQUEST_INSTALL_PACKAGES` ❌

## Code Changes Made

### **1. UpdateManager.kt - APK Filtering**
```kotlin
// Skip APKs with "NoUpdate" in the filename (these are for stores)
if (name.contains("NoUpdate", ignoreCase = true)) {
    Log.d(TAG, "Skipping NoUpdate APK: $name")
    continue
}
```

### **2. UpdateManager.kt - Version Suffix Handling**
```kotlin
private fun stripVersionSuffix(versionName: String): String {
    // Remove common suffixes that might exist in older versions
    return versionName.replace(Regex("-[a-zA-Z]+$"), "")
}
```

### **3. build.gradle.kts - Clean Version Names**
```kotlin
create("github") {
    // versionNameSuffix = "-github"  // Removed for clean versions
}

create("store") {
    // versionNameSuffix = "-store"   // Removed for clean versions
}
```

## Version Comparison Logic

### **Current Version (from device):**
- Gets version from `PackageManager`
- Strips any suffixes (e.g., "1.3.2-github" → "1.3.2")
- Uses clean version for comparison

### **GitHub Release Version:**
- Extracts from release tag (e.g., "v1.3.2" → "1.3.2")
- Strips any suffixes for consistency
- Compares clean semantic versions

### **Example:**
```
Device version: "1.3.1-github" → "1.3.1"
GitHub version: "v1.3.2" → "1.3.2"
Result: Update available ✅
```

## Testing the System

### **Build Commands:**
```bash
# Build GitHub variant (with auto-updater)
./gradlew assembleGithubRelease

# Build Store variant (without auto-updater)
./gradlew assembleStoreRelease
```

### **Expected Behavior:**
- **GitHub variant:** Can check for updates, download APKs, install updates
- **Store variant:** No update functionality, no auto-update permissions

## Benefits

1. **✅ Backwards Compatibility:** Existing users continue to work
2. **✅ Clean Version Names:** No confusing suffixes in version numbers
3. **✅ Proper APK Filtering:** GitHub users only get auto-update APKs
4. **✅ Store Compliance:** Store variant has no auto-update permissions
5. **✅ Single Release:** Both APKs in one GitHub release
6. **✅ Automatic Updates:** GitHub users get seamless updates

## Summary

The update system now properly handles:
- **Different APK variants** in the same GitHub release
- **Backwards compatibility** with existing users
- **Clean version comparison** without suffixes
- **Proper filtering** to ensure users get the right APK type
- **Store compliance** for distribution channels

This ensures that GitHub users get automatic updates while store users rely on their respective app stores for updates, all from a single codebase with appropriate build variants. 