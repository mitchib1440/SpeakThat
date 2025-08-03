Full disclosure, this text and many other guidance texts in this project were written by an AI as I was learning the ropes of programming on Android

# Build Variants: How the System Knows What Should Be Different

## Overview

Android's build system uses **source sets** to organize different files for different build variants. This allows you to have a single codebase that produces different APKs with different features, permissions, and resources.

## How It Works

### 1. Source Set Structure

```
app/src/
├── main/           # Shared code for ALL variants
├── github/         # GitHub-specific files (auto-updater)
└── store/          # Store-specific files (no auto-updater)
```

### 2. Manifest Merging

Android automatically merges manifests from different source sets in this priority order:

1. **`main/AndroidManifest.xml`** - Base manifest (shared permissions, activities)
2. **`github/AndroidManifest.xml`** - GitHub-specific overrides/additions
3. **`store/AndroidManifest.xml`** - Store-specific overrides/additions

### 3. How Permissions Are Handled

**GitHub Variant:**
- `main/` manifest: Contains shared permissions (INTERNET, BLUETOOTH, etc.)
- `github/` manifest: **ADDED** `REQUEST_INSTALL_PACKAGES` and `INSTALL_PACKAGES`
- **Result:** GitHub APK has auto-update permissions

**Store Variant:**
- `main/` manifest: Contains shared permissions (INTERNET, BLUETOOTH, etc.)
- `store/` manifest: **NO** auto-update permissions
- **Result:** Store APK does NOT have auto-update permissions

### 4. Code Organization

#### Shared Code (in `main/`)
```kotlin
// This code exists in BOTH variants
class MainActivity : AppCompatActivity() {
    fun someSharedFunction() {
        // Bug fixes here apply to both variants automatically
    }
}
```

#### Flavor-Specific Code
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

#### Conditional Code (using BuildConfig)
```kotlin
// This code exists in BOTH variants but behaves differently
object UpdateFeature {
    fun startUpdateActivity(context: Context) {
        if (BuildConfig.ENABLE_AUTO_UPDATER) {
            // This code only runs in GitHub variant
            UpdateActivity.start(context)
        } else {
            // This code only runs in Store variant
            showStoreMessage(context)
        }
    }
}
```

### 5. Resource Overrides

**GitHub Variant:**
- `main/res/values/strings.xml`: Base strings
- `github/res/values/strings.xml`: `update_channel = "GitHub"`

**Store Variant:**
- `main/res/values/strings.xml`: Base strings  
- `store/res/values/strings.xml`: `update_channel = "App Store"`

### 6. Build Process

When you build:

**GitHub Variant (`assembleGithubRelease`):**
1. Compiles `main/` + `github/` source sets
2. Merges `main/AndroidManifest.xml` + `github/AndroidManifest.xml`
3. Uses `github/res/` resources (overriding `main/res/` where needed)
4. Sets `BuildConfig.ENABLE_AUTO_UPDATER = true`

**Store Variant (`assembleStoreRelease`):**
1. Compiles `main/` + `store/` source sets  
2. Merges `main/AndroidManifest.xml` + `store/AndroidManifest.xml`
3. Uses `store/res/` resources (overriding `main/res/` where needed)
4. Sets `BuildConfig.ENABLE_AUTO_UPDATER = false`

### 7. How Bug Fixes Work

**Bug fixes are automatic because:**

1. **Shared Code:** Most of your code is in `main/` and exists in both variants
2. **Single Compilation:** When you fix a bug in `main/`, it's compiled into both APKs
3. **No Duplication:** You don't need separate bug fixes for each variant

**Example:**
```kotlin
// In main/java/.../MainActivity.kt
fun processNotification(notification: Notification) {
    // BUG: Missing null check
    val title = notification.title.toString() // This could crash
    
    // FIX: Add null check
    val title = notification.title?.toString() ?: "Unknown"
    
    // This fix automatically applies to both GitHub and Store variants
}
```

### 8. How Conditional Features Work

**Using BuildConfig Fields:**
```kotlin
// Defined in build.gradle.kts
buildConfigField("boolean", "ENABLE_AUTO_UPDATER", "true")  // GitHub
buildConfigField("boolean", "ENABLE_AUTO_UPDATER", "false") // Store

// Used in code
if (BuildConfig.ENABLE_AUTO_UPDATER) {
    // Only compiled into GitHub variant
    checkForUpdates()
}
```

**Using Source Sets:**
```kotlin
// app/src/github/java/.../UpdateManager.kt
// This class only exists in GitHub variant
class UpdateManager {
    fun downloadUpdate() { /* ... */ }
}

// app/src/main/java/.../MainActivity.kt  
// This code exists in both variants
class MainActivity {
    fun checkUpdates() {
        // This line only compiles in GitHub variant
        UpdateManager().downloadUpdate() // Compile error in Store variant
    }
}
```

### 9. Testing Different Variants

**In Android Studio:**
1. Open "Build Variants" panel
2. Select "githubDebug" or "storeDebug"
3. Run the app - you'll see different behavior

**Command Line:**
```bash
# Build GitHub variant
./gradlew assembleGithubRelease

# Build Store variant  
./gradlew assembleStoreRelease
```

### 10. Key Benefits

1. **Single Codebase:** All bug fixes apply to both variants automatically
2. **Conditional Features:** Easy to enable/disable features per variant
3. **Store Compliance:** Store variant has no auto-update permissions
4. **GitHub Features:** GitHub variant has full auto-update functionality
5. **Resource Flexibility:** Different strings, icons, etc. per variant

## Summary

The Android build system "knows" what should be different through:

1. **Source set organization** - Different files in different directories
2. **Manifest merging** - Automatic combination of manifest files
3. **BuildConfig fields** - Compile-time flags for conditional logic
4. **Resource overriding** - Flavor-specific resources replace main resources

This gives you the best of both worlds: shared code for bug fixes and maintenance, with variant-specific features for different distribution channels. 