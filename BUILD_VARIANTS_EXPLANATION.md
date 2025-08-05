Full disclosure, this text and many other guidance texts in this project were written by an AI as I was learning the ropes of programming on Android

# Build Variants: GitHub vs Store Distribution

## Overview

SpeakThat uses Android build variants to create two different APK versions optimized for different distribution channels:

- **GitHub variant**: Full-featured with auto-updates and online icons
- **Store variant**: Privacy-focused with no internet permissions

## Key Differences

### **GitHub Variant** (`github` flavor)
- **Auto-updater enabled**: Can download and install updates from GitHub
- **Online app icons**: Loads app icons from CDN for better visual experience
- **INTERNET permission**: Required for updates and icon loading
- **Network libraries**: Includes OkHttp and Coil for network operations
- **Target users**: Direct download users who want latest features

### **Store Variant** (`store` flavor) 
- **No auto-updater**: Updates handled by app stores (Play Store, F-Droid, etc.)
- **Local icons only**: Uses only local fallback icons for privacy
- **No INTERNET permission**: Privacy-conscious users can trust the app won't make network requests
- **No network libraries**: Excludes OkHttp and Coil to reduce APK size and privacy footprint
- **Target users**: Privacy-conscious users who prefer app store distribution

## Privacy Benefits of Store Variant

The Store variant is specifically designed for privacy-conscious users:

1. **No network permissions**: Users can verify the app cannot make any internet requests
2. **No online icon loading**: App icons are loaded only from local resources
3. **Smaller APK size**: Excludes network libraries (OkHttp, Coil)
4. **Transparent behavior**: Users know exactly what the app can and cannot do

## Technical Implementation

### Build Configuration
```kotlin
// build.gradle.kts
productFlavors {
    create("github") {
        buildConfigField("boolean", "ENABLE_AUTO_UPDATER", "true")
        buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"github\"")
    }
    
    create("store") {
        buildConfigField("boolean", "ENABLE_AUTO_UPDATER", "false") 
        buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"store\"")
    }
}

dependencies {
    // Network libraries only for GitHub variant
    githubImplementation("io.coil-kt:coil:2.4.0")
    githubImplementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

### Conditional Code
- **GitHub variant**: Uses `AppListAdapter.kt` with Coil for online icon loading
- **Store variant**: Uses `app/src/store/java/.../AppListAdapter.kt` with local icons only

### Permission Management
- **Main manifest**: Contains all permissions including INTERNET
- **Store manifest**: Removes INTERNET permission using `tools:node="remove"`

## APK Output Files

- **GitHub**: `SpeakThat-v1.3.3.apk` (with auto-updater)
- **Store**: `SpeakThat-NoUpdater-v1.3.3.apk` (privacy-focused)

## User Experience

### GitHub Users
- Get automatic updates when new versions are released
- See colorful app icons loaded from CDN
- Slightly larger APK due to network libraries

### Store Users  
- Updates handled by their app store
- See consistent local fallback icons
- Smaller APK with no network dependencies
- Complete privacy - no internet access

## Why This Matters

Privacy-conscious users often avoid apps with INTERNET permissions unless absolutely necessary. By providing a Store variant without internet access, we:

1. **Respect user privacy preferences**
2. **Provide transparency about app behavior**
3. **Reduce attack surface** (no network code = no network vulnerabilities)
4. **Maintain functionality** (app works perfectly without online icons)

This approach demonstrates that privacy and functionality can coexist when designed thoughtfully. 