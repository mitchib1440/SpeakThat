# How the Android Build System Knows What Should Be Different

## Your Question Answered

You asked: **"How does it know what should be different? For instance, we don't want the store version including the auto-updater or the REQUEST_INSTALL_PACKAGES permission, but those need to be included with the github variant."**

## The Answer: Source Sets + Manifest Merging

The Android build system uses **source sets** and **manifest merging** to automatically handle differences between build variants.

### 1. Source Set Organization

```
app/src/
├── main/           # Shared for ALL variants
├── github/         # GitHub-specific files
└── store/          # Store-specific files
```

### 2. How Permissions Are Handled

**GitHub Variant:**
- `main/AndroidManifest.xml`: Contains shared permissions (INTERNET, BLUETOOTH, etc.)
- `github/AndroidManifest.xml`: **ADDED** `REQUEST_INSTALL_PACKAGES` and `INSTALL_PACKAGES`
- **Result:** GitHub APK has auto-update permissions

**Store Variant:**
- `main/AndroidManifest.xml`: Contains shared permissions (INTERNET, BLUETOOTH, etc.)
- `store/AndroidManifest.xml`: **NO** auto-update permissions
- **Result:** Store APK does NOT have auto-update permissions

### 3. Manifest Merging Process

Android automatically merges manifests in this order:

1. **Base manifest** (`main/AndroidManifest.xml`)
2. **Flavor manifest** (`github/AndroidManifest.xml` or `store/AndroidManifest.xml`)

**For GitHub variant:**
```xml
<!-- main/AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.BLUETOOTH" />
<!-- ... other shared permissions ... -->

<!-- github/AndroidManifest.xml -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.INSTALL_PACKAGES" />

<!-- FINAL RESULT: GitHub APK has ALL permissions -->
```

**For Store variant:**
```xml
<!-- main/AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.BLUETOOTH" />
<!-- ... other shared permissions ... -->

<!-- store/AndroidManifest.xml -->
<!-- EMPTY - no auto-update permissions -->

<!-- FINAL RESULT: Store APK has shared permissions only -->
```

### 4. How Code Differences Work

**Shared Code (in `main/`):**
```kotlin
// This exists in BOTH variants
class MainActivity : AppCompatActivity() {
    fun processNotification() {
        // Bug fixes here apply to both automatically
    }
}
```

**Flavor-Specific Code:**
```kotlin
// app/src/github/java/.../GitHubSpecificActivity.kt
// This class ONLY exists in GitHub variant
class GitHubSpecificActivity : Activity() {
    // Auto-update functionality
}

// app/src/store/java/.../StoreSpecificActivity.kt
// This class ONLY exists in Store variant  
class StoreSpecificActivity : Activity() {
    // Store-specific functionality
}
```

**Conditional Code (using BuildConfig):**
```kotlin
// This code exists in BOTH variants but behaves differently
if (BuildConfig.ENABLE_AUTO_UPDATER) {
    // Only runs in GitHub variant
    checkForUpdates()
} else {
    // Only runs in Store variant
    showStoreMessage()
}
```

### 5. Build Process

When you run `./gradlew assembleGithubDebug`:

1. **Compiles:** `main/` + `github/` source sets
2. **Merges:** `main/AndroidManifest.xml` + `github/AndroidManifest.xml`
3. **Sets:** `BuildConfig.ENABLE_AUTO_UPDATER = true`
4. **Result:** APK with auto-update permissions

When you run `./gradlew assembleStoreDebug`:

1. **Compiles:** `main/` + `store/` source sets
2. **Merges:** `main/AndroidManifest.xml` + `store/AndroidManifest.xml`
3. **Sets:** `BuildConfig.ENABLE_AUTO_UPDATER = false`
4. **Result:** APK without auto-update permissions

### 6. Why Bug Fixes Are Automatic

**Bug fixes apply to both variants because:**

1. **Single Codebase:** Most code is in `main/` and exists in both variants
2. **Shared Compilation:** When you fix a bug in `main/`, it's compiled into both APKs
3. **No Duplication:** You don't need separate fixes for each variant

**Example:**
```kotlin
// In main/java/.../MainActivity.kt
fun processNotification(notification: Notification) {
    // BUG: Missing null check
    val title = notification.title.toString() // Could crash
    
    // FIX: Add null check
    val title = notification.title?.toString() ?: "Unknown"
    
    // This fix automatically applies to both GitHub and Store variants
}
```

### 7. Testing Different Variants

**In Android Studio:**
1. Open "Build Variants" panel
2. Select "githubDebug" or "storeDebug"
3. Run the app - you'll see different behavior

**Command Line:**
```bash
# Build GitHub variant (with auto-updater)
./gradlew assembleGithubRelease

# Build Store variant (without auto-updater)
./gradlew assembleStoreRelease
```

## Summary

The Android build system "knows" what should be different through:

1. **Source set organization** - Different files in different directories
2. **Manifest merging** - Automatic combination of manifest files
3. **BuildConfig fields** - Compile-time flags for conditional logic
4. **Resource overriding** - Flavor-specific resources replace main resources

This gives you:
- ✅ **Single codebase** for easy maintenance
- ✅ **Automatic bug fixes** across all variants
- ✅ **Store compliance** (no auto-update permissions)
- ✅ **GitHub features** (full auto-update functionality)
- ✅ **Conditional features** based on build variant

The system is designed to handle exactly your use case: one codebase, multiple distribution channels, with appropriate features for each. 