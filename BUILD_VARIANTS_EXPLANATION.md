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
- **INTERNET permission**: Required for network voice downloads (legitimate TTS feature)
- **No network libraries**: Excludes OkHttp and Coil to reduce APK size and privacy footprint
- **Target users**: Privacy-conscious users who prefer app store distribution

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
- **Store manifest**: Inherits INTERNET permission for network voice downloads

## APK Output Files

- **GitHub**: `SpeakThat-v1.3.3.apk` (with auto-updater)
- **Store**: `SpeakThat-NoUpdater-v1.3.3.apk` (privacy-focused)