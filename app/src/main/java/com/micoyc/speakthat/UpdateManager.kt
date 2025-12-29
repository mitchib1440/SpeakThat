package com.micoyc.speakthat

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.micoyc.speakthat.InAppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Privacy-focused auto-update manager for GitHub distribution
 * 
 * This class handles:
 * - Checking for new versions on GitHub
 * - Downloading APK files securely
 * - Verifying APK signatures to prevent malware
 * - Managing update preferences
 * - Detecting installation source to prevent conflicts with Google Play
 * 
 * Security features:
 * - HTTPS-only connections
 * - APK signature verification
 * - User consent required for all operations
 * - No background polling (battery-friendly)
 * - Robust Google Play detection to prevent update conflicts
 */
class UpdateManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateManager"
        
        // GitHub API endpoints - replace with your actual repository
        // Format: https://api.github.com/repos/USERNAME/REPOSITORY
        private const val GITHUB_API_BASE = "https://api.github.com/repos/mitchib1440/SpeakThat"
        private const val RELEASES_ENDPOINT = "$GITHUB_API_BASE/releases/latest"
        
        // Update check preferences
        private const val PREFS_NAME = "UpdatePrefs"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_LAST_CHECKED_VERSION = "last_checked_version"
        private const val KEY_UPDATE_CHECK_INTERVAL = "update_check_interval_hours"
        private const val KEY_INSTALLATION_SOURCE = "installation_source"
        private const val KEY_GOOGLE_PLAY_DETECTED = "google_play_detected"
        private const val KEY_CACHED_VERSION_NAME = "cached_update_version"
        private const val KEY_CACHED_FILE_SIZE = "cached_update_size"
        private const val KEY_CACHED_RELEASE_NOTES = "cached_update_notes"
        private const val KEY_CACHED_RELEASE_DATE = "cached_update_date"
        private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"
        
        // Google Play Store identifiers
        private const val GOOGLE_PLAY_STORE_PACKAGE = "com.android.vending"
        private const val GOOGLE_PLAY_STORE_PACKAGE_ALT = "com.google.android.packageinstaller"
        
        // Default settings
        private const val DEFAULT_UPDATE_CHECK_INTERVAL = 24L // 24 hours
        private const val NETWORK_TIMEOUT_SECONDS = 30L
        
        // Build-time flags for distribution channel detection
        // These should be set in build.gradle for different build variants
        private const val BUILD_FLAVOR_GITHUB = "github"
        private const val BUILD_FLAVOR_GOOGLE_PLAY = "googleplay"
        
        // Use WeakReference to prevent memory leaks
        // This allows the UpdateManager to be garbage collected when not in use
        private var instanceRef: java.lang.ref.WeakReference<UpdateManager>? = null
        
        fun getInstance(context: Context): UpdateManager {
            // Try to get existing instance from weak reference
            var instance = instanceRef?.get()
            
            if (instance == null) {
                // Create new instance if none exists or was garbage collected
                synchronized(this) {
                    instance = instanceRef?.get()
                    if (instance == null) {
                        instance = UpdateManager(context.applicationContext)
                        instanceRef = java.lang.ref.WeakReference(instance)
                        Log.d(TAG, "Created new UpdateManager instance")
                    }
                }
            }
            
            return instance!!
        }
    }
    
    // SharedPreferences for storing update preferences
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // HTTP client with timeouts for network requests
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    /**
     * Check if app was installed from Google Play Store
     * Uses only the installer package name check, which is the most reliable method
     * @return true if app was installed from Google Play Store
     */
    fun isInstalledFromGooglePlay(): Boolean {
        // Check if we've already determined the installation source
        val cachedResult = prefs.getBoolean(KEY_GOOGLE_PLAY_DETECTED, false)
        if (cachedResult) {
            Log.d(TAG, "Using cached Google Play detection result: true")
            InAppLogger.logSystemEvent("Using cached Google Play detection: true", "UpdateManager")
            return true
        }
        
        try {
            Log.d(TAG, "Performing simple Google Play installation detection...")
            InAppLogger.logSystemEvent("Performing simple Google Play detection", "UpdateManager")
            
            // SIMPLE DETECTION: Only check installer package name
            // This is the most reliable method according to Android best practices
            val installerPackage = getInstallerPackageName()
            Log.d(TAG, "Installer package: $installerPackage")
            InAppLogger.logSystemEvent("Installer package detected: $installerPackage", "UpdateManager")
            
            // Only consider it Google Play if installer package is exactly "com.android.vending"
            val isGooglePlay = installerPackage == GOOGLE_PLAY_STORE_PACKAGE
            
            Log.i(TAG, "Detection result: ${if (isGooglePlay) "Google Play" else "Non-Google Play"}")
            InAppLogger.logSystemEvent("Detection result: ${if (isGooglePlay) "Google Play" else "Non-Google Play"}", "UpdateManager")
            
            cacheGooglePlayDetection(isGooglePlay)
            return isGooglePlay
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in Google Play detection", e)
            InAppLogger.logSystemEvent("Error in Google Play detection: ${e.message}", "UpdateManager")
            // On error, assume NOT from Google Play to prevent blocking legitimate updates
            cacheGooglePlayDetection(false)
            return false
        }
    }
    
    /**
     * Check if app was installed from a repository (F-Droid, IzzyOnDroid, etc.)
     * @return true if app was installed from a repository
     */
    fun isInstalledFromRepository(): Boolean {
        try {
            val installerPackage = getInstallerPackageName()
            Log.d(TAG, "Checking repository installation, installer: $installerPackage")
            
            // Common repository installer packages
            val repositoryPackages = listOf(
                "org.fdroid.fdroid",           // F-Droid
                "com.fdroid.fdroid",           // F-Droid (alternative)
                "de.izzysoft.fdroid",          // IzzyOnDroid
                "com.aurora.store",             // Aurora Store
                "com.aurora.services",          // Aurora Services
                "com.android.vending.billing.InAppBillingService.COIN" // Some alternative stores
            )
            
            val isFromRepository = installerPackage != null && 
                                 installerPackage != GOOGLE_PLAY_STORE_PACKAGE &&
                                 repositoryPackages.any { installerPackage.contains(it) }
            
            Log.i(TAG, "Repository detection result: ${if (isFromRepository) "Repository" else "Not Repository"}")
            InAppLogger.logSystemEvent("Repository detection: ${if (isFromRepository) "Repository" else "Not Repository"}", "UpdateManager")
            
            return isFromRepository
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in repository detection", e)
            InAppLogger.logSystemEvent("Error in repository detection: ${e.message}", "UpdateManager")
            return false
        }
    }
    
    /**
     * Check if auto-updates should be enabled for this installation
     * Repository users should get updates from their repository instead
     * @return true if auto-updates should be enabled
     */
    fun shouldEnableAutoUpdates(): Boolean {
        // Google Play users can use auto-updates
        if (isInstalledFromGooglePlay()) {
            return true
        }
        
        // Repository users should get updates from their repository
        if (isInstalledFromRepository()) {
            Log.i(TAG, "Auto-updates disabled for repository installation")
            InAppLogger.logSystemEvent("Auto-updates disabled for repository installation", "UpdateManager")
            return false
        }
        
        // For other installations (direct APK, etc.), allow auto-updates
        return true
    }
    
    /**
     * Get the installer package name using the most reliable method available
     */
    private fun getInstallerPackageName(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+)
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                // Android 10 and below
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting installer package name", e)
            null
        }
    }
    
    /**
     * Check if Google Play Store is installed on the device
     */
    private fun isGooglePlayStoreInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(GOOGLE_PLAY_STORE_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking if Google Play Store is installed", e)
            false
        }
    }
    

    
    /**
     * Check if app was installed via system methods (ADB, etc.)
     */
    private fun isSystemInstalled(): Boolean {
        return try {
            val installerPackage = getInstallerPackageName()
            installerPackage == null || installerPackage.isEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Error checking system installation", e)
            false
        }
    }
    
    /**
     * Cache the Google Play detection result to avoid repeated checks
     */
    private fun cacheGooglePlayDetection(isGooglePlay: Boolean) {
        prefs.edit()
            .putBoolean(KEY_GOOGLE_PLAY_DETECTED, isGooglePlay)
            .putString(KEY_INSTALLATION_SOURCE, if (isGooglePlay) "google_play" else "other")
            .apply()
        
        Log.d(TAG, "Cached Google Play detection result: $isGooglePlay")
    }
    
    /**
     * Get the cached installation source for debugging
     */
    fun getInstallationSource(): String {
        return prefs.getString(KEY_INSTALLATION_SOURCE, "unknown") ?: "unknown"
    }
    
    /**
     * Reset Google Play detection cache (for testing purposes)
     * This allows re-detection of installation source
     */
    fun resetGooglePlayDetectionCache() {
        prefs.edit()
            .remove(KEY_GOOGLE_PLAY_DETECTED)
            .remove(KEY_INSTALLATION_SOURCE)
            .apply()
        
        Log.d(TAG, "Google Play detection cache reset")
        InAppLogger.logSystemEvent("Google Play detection cache reset", "UpdateManager")
    }
    
    /**
     * Force a fresh Google Play detection (ignores cache)
     * Use this when you suspect the cached result is incorrect
     * @return true if detected as Google Play, false otherwise
     */
    fun forceFreshGooglePlayDetection(): Boolean {
        Log.i(TAG, "Forcing fresh Google Play detection...")
        InAppLogger.logSystemEvent("Forcing fresh Google Play detection", "UpdateManager")
        
        // Clear the cache first
        resetGooglePlayDetectionCache()
        
        // Perform fresh detection
        return isInstalledFromGooglePlay()
    }
    
    /**
     * Manually override Google Play detection result
     * Use this only in edge cases where automatic detection is incorrect
     * @param isGooglePlay The manual detection result
     */
    fun manuallyOverrideGooglePlayDetection(isGooglePlay: Boolean) {
        Log.w(TAG, "Manual override of Google Play detection: $isGooglePlay")
        InAppLogger.logSystemEvent("Manual override of Google Play detection: $isGooglePlay", "UpdateManager")
        
        cacheGooglePlayDetection(isGooglePlay)
    }
    
    /**
     * Get the current detection confidence level
     * @return Confidence level as a percentage (0-100)
     */
    fun getDetectionConfidence(): Int {
        try {
            val installerPackage = getInstallerPackageName()
            
            // With simple installer package detection, confidence is high if we have a clear result
            return when {
                installerPackage == GOOGLE_PLAY_STORE_PACKAGE -> 100  // Definitely Google Play
                installerPackage == null || installerPackage.isEmpty() -> 90  // Likely sideloaded
                else -> 95  // Non-Google Play installer
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating detection confidence", e)
            return 0
        }
    }
    
    /**
     * Get detailed installation source information for debugging
     * @return Map containing all detection details
     */
    fun getInstallationSourceDetails(): Map<String, Any> {
        val details = mutableMapOf<String, Any>()
        
        try {
            details["cached_result"] = prefs.getBoolean(KEY_GOOGLE_PLAY_DETECTED, false)
            details["cached_source"] = getInstallationSource()
            details["installer_package"] = getInstallerPackageName() ?: "null"
            details["google_play_installed"] = isGooglePlayStoreInstalled()
            details["system_installed"] = isSystemInstalled()
            
            // Get current app version for context
            details["current_version"] = getCurrentVersionName()
            
            // Add simple detection info
            details["detection_method"] = "installer_package_only"
            details["google_play_package"] = GOOGLE_PLAY_STORE_PACKAGE
            
            // Add debug info about the detection process
            details["debug_info"] = "Use resetGooglePlayDetectionCache() to clear cached result and re-detect"
            details["force_fresh_detection"] = "Use forceFreshGooglePlayDetection() to ignore cache and re-detect"
            
        } catch (e: Exception) {
            details["error"] = e.message ?: "Unknown error"
        }
        
        return details
    }
    

    
    /**
     * Debug method to log all installation source details
     * Call this to understand why the detection is working as it is
     */
    fun logInstallationSourceDetails() {
        val details = getInstallationSourceDetails()
        Log.i(TAG, "=== INSTALLATION SOURCE DEBUG INFO ===")
        InAppLogger.logSystemEvent("=== INSTALLATION SOURCE DEBUG INFO ===", "UpdateManager")
        details.forEach { (key, value) ->
            Log.i(TAG, "$key: $value")
            InAppLogger.logSystemEvent("$key: $value", "UpdateManager")
        }
        Log.i(TAG, "=======================================")
        InAppLogger.logSystemEvent("=======================================", "UpdateManager")
    }
    
    /**
     * Check if an update is available by comparing current version with GitHub release
     * @return UpdateInfo if update is available, null otherwise
     */
    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for updates...")
            
            // CRITICAL: Check if app was installed from Google Play Store
            if (isInstalledFromGooglePlay()) {
                Log.i(TAG, "App installed from Google Play Store - GitHub updates disabled for security and policy compliance")
                InAppLogger.logSystemEvent("Update check blocked - Google Play installation detected", "UpdateManager")
                return@withContext null
            }
            
            // Get current app version from package manager
            val currentVersion = getCurrentVersionName()
            Log.d(TAG, "Current version: $currentVersion")
            
            // Fetch latest release info from GitHub API
            val releaseInfo = fetchLatestReleaseInfo()
            if (releaseInfo == null) {
                Log.w(TAG, "Failed to fetch release info from GitHub")
                return@withContext null
            }
            
            Log.d(TAG, "Latest version on GitHub: ${releaseInfo.versionName}")
            
            // Compare versions to see if update is needed
            if (isNewerVersion(releaseInfo.versionName, currentVersion)) {
                Log.i(TAG, "Update available: ${releaseInfo.versionName}")
                
                // Save last checked version to avoid repeated notifications
                prefs.edit()
                    .putString(KEY_LAST_CHECKED_VERSION, releaseInfo.versionName)
                    .putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis())
                    .apply()

                // Cache update details for UI/notification surfaces
                cacheUpdateInfo(releaseInfo)
                
                return@withContext releaseInfo
            } else {
                Log.d(TAG, "No update available - app is up to date")
                clearCachedUpdateInfo()
                return@withContext null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            return@withContext null
        }
    }
    
    /**
     * Download the APK file from GitHub releases
     * @param updateInfo Update information containing download URL
     * @param progressCallback Progress callback (0-100)
     * @return Downloaded file or null if failed
     */
    suspend fun downloadApk(
        updateInfo: UpdateInfo,
        progressCallback: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting APK download: ${updateInfo.downloadUrl}")
            
            // Create HTTP request to download APK
            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed with HTTP code: ${response.code}")
                return@withContext null
            }
            
            val body = response.body ?: return@withContext null
            val contentLength = body.contentLength()
            
            // Create temporary file in app's cache directory
            val tempFile = File(context.cacheDir, "update_${updateInfo.versionName}.apk")
            
            // Download file with progress tracking
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(8192) // 8KB buffer
                var bytesRead: Int
                var totalBytesRead = 0L
                
                body.byteStream().use { input ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Calculate and report progress percentage
                        if (contentLength > 0) {
                            val progress = ((totalBytesRead * 100) / contentLength).toInt()
                            progressCallback(progress)
                        }
                    }
                }
            }
            
            Log.d(TAG, "APK download completed: ${tempFile.absolutePath}")
            return@withContext tempFile
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
            return@withContext null
        }
    }
    
    /**
     * Verify APK signature to ensure it's from the same developer
     * This prevents downloading malware or fake updates
     * @param apkFile Downloaded APK file
     * @return true if signature is valid (matches current app)
     */
    suspend fun verifyApkSignature(apkFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Verifying APK signature for security...")
            
            // Get current app's signature
            val currentSignature = getCurrentAppSignature()
            
            // Get downloaded APK's signature
            val apkSignature = getApkSignature(apkFile)
            
            // Compare signatures - they must match for security
            val isValid = currentSignature == apkSignature
            Log.d(TAG, "APK signature verification result: $isValid")
            
            return@withContext isValid
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying APK signature", e)
            return@withContext false
        }
    }
    
    /**
     * Check if enough time has passed since last update check
     * This prevents too frequent API calls based on user frequency settings
     * @return true if update check is allowed
     */
    fun shouldCheckForUpdates(): Boolean {
        val lastCheck = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        
        // Get frequency setting from general preferences
        val generalPrefs = context.getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
        var frequency = generalPrefs.getString("update_check_frequency", "weekly")
        if (frequency == "never") {
            frequency = "weekly"
            generalPrefs.edit().putString("update_check_frequency", "weekly").apply()
        }
        
        // Convert frequency to hours
        val intervalHours = when (frequency) {
            "daily" -> 24L
            "weekly" -> 24L * 7L
            "monthly" -> 24L * 30L
            else -> 24L // Default to daily
        }
        
        val intervalMs = intervalHours * 60 * 60 * 1000 // Convert hours to milliseconds
        val timeSinceLastCheck = System.currentTimeMillis() - lastCheck
        
        Log.d(TAG, "Update check: last=$lastCheck, frequency=$frequency, interval=${intervalHours}h, timeSince=${timeSinceLastCheck}ms")
        
        return timeSinceLastCheck >= intervalMs
    }
    
    /**
     * Check if we've already notified about this version
     * Prevents spam notifications for the same update
     * @param versionName Version to check
     * @return true if already notified
     */
    fun hasNotifiedAboutVersion(versionName: String): Boolean {
        val lastNotifiedVersion = prefs.getString(KEY_LAST_NOTIFIED_VERSION, "")
        return lastNotifiedVersion == versionName
    }
    

    
    // Private helper methods below
    
    /**
     * Get current app version from package manager
     * Strips any suffixes for clean version comparison
     */
    fun getCurrentVersionName(): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val fullVersionName = packageInfo.versionName ?: "1.0.0"
        
        // Strip any suffixes (like "-github", "-store") for clean version comparison
        // This ensures backwards compatibility with existing users
        return stripVersionSuffix(fullVersionName)
    }
    
    /**
     * Strip version suffixes for clean comparison
     * Handles suffixes like "-github", "-store", etc.
     */
    private fun stripVersionSuffix(versionName: String): String {
        // Remove common suffixes that might exist in older versions
        return versionName.replace(Regex("-[a-zA-Z]+$"), "")
    }
    
    /**
     * Fetch latest release info from GitHub API
     * This calls the GitHub REST API to get release information
     */
    private suspend fun fetchLatestReleaseInfo(): UpdateInfo? {
        val request = Request.Builder()
            .url(RELEASES_ENDPOINT)
            .addHeader("Accept", "application/vnd.github.v3+json")
            .build()
        
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(TAG, "GitHub API request failed with code: ${response.code}")
            return null
        }
        
        val jsonString = response.body?.string() ?: return null
        val json = JSONObject(jsonString)
        
        // Extract version from GitHub tag (remove "v" prefix if present)
        val tagName = json.getString("tag_name")
        val versionName = tagName.removePrefix("v") // Remove "v" prefix if present
        
        // Ensure clean version name for comparison
        val cleanVersionName = stripVersionSuffix(versionName)
        
        // Find APK asset in the release
        // IMPORTANT: Filter out "NoUpdate" APKs to ensure GitHub users only get the auto-update version
        val assets = json.getJSONArray("assets")
        var downloadUrl = ""
        var fileSize = 0L
        
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.getString("name")
            
            // Skip APKs with "NoUpdate" in the filename (these are for stores)
            if (name.contains("NoUpdate", ignoreCase = true)) {
                Log.d(TAG, "Skipping NoUpdate APK: $name")
                continue
            }
            
            if (name.endsWith(".apk")) {
                downloadUrl = asset.getString("browser_download_url")
                fileSize = asset.getLong("size")
                Log.d(TAG, "Found suitable APK for GitHub users: $name")
                break
            }
        }
        
        if (downloadUrl.isEmpty()) {
            Log.w(TAG, "No APK asset found in GitHub release")
            return null
        }
        
        return UpdateInfo(
            versionName = cleanVersionName, // Use clean version name for comparison
            versionCode = extractVersionCode(cleanVersionName),
            downloadUrl = downloadUrl,
            fileSize = fileSize,
            releaseNotes = json.optString("body", ""),
            releaseDate = json.optString("published_at", "")
        )
    }
    
    /**
     * Compare version strings to determine if new version is newer
     * Supports semantic versioning (1.2.3 format)
     */
    private fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
        val newParts = newVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLength = maxOf(newParts.size, currentParts.size)
        
        for (i in 0 until maxLength) {
            val newPart = newParts.getOrNull(i) ?: 0
            val currentPart = currentParts.getOrNull(i) ?: 0
            
            if (newPart > currentPart) return true
            if (newPart < currentPart) return false
        }
        
        return false // Versions are equal
    }

    fun isNewerThanInstalled(versionName: String): Boolean {
        return isNewerVersion(versionName, getCurrentVersionName())
    }

    fun cacheUpdateInfo(updateInfo: UpdateInfo) {
        prefs.edit()
            .putString(KEY_CACHED_VERSION_NAME, updateInfo.versionName)
            .putLong(KEY_CACHED_FILE_SIZE, updateInfo.fileSize)
            .putString(KEY_CACHED_RELEASE_NOTES, updateInfo.releaseNotes ?: "")
            .putString(KEY_CACHED_RELEASE_DATE, updateInfo.releaseDate ?: "")
            .apply()
    }

    fun clearCachedUpdateInfo() {
        prefs.edit()
            .remove(KEY_CACHED_VERSION_NAME)
            .remove(KEY_CACHED_FILE_SIZE)
            .remove(KEY_CACHED_RELEASE_NOTES)
            .remove(KEY_CACHED_RELEASE_DATE)
            .apply()
    }

    fun getCachedUpdateInfo(): UpdateInfo? {
        val version = prefs.getString(KEY_CACHED_VERSION_NAME, null) ?: return null
        val size = prefs.getLong(KEY_CACHED_FILE_SIZE, -1L)
        val notes = prefs.getString(KEY_CACHED_RELEASE_NOTES, "") ?: ""
        val date = prefs.getString(KEY_CACHED_RELEASE_DATE, "") ?: ""
        return UpdateInfo(
            versionName = version,
            versionCode = extractVersionCode(version),
            downloadUrl = "", // Download URL not cached; fetched fresh in UpdateActivity
            fileSize = if (size >= 0) size else 0L,
            releaseNotes = notes,
            releaseDate = date
        )
    }

    fun markVersionNotified(versionName: String) {
        prefs.edit().putString(KEY_LAST_NOTIFIED_VERSION, versionName).apply()
    }

    fun lastNotifiedVersion(): String? = prefs.getString(KEY_LAST_NOTIFIED_VERSION, null)
    
    /**
     * Extract version code from version name
     * Simple conversion: 1.2.3 -> 10203
     */
    private fun extractVersionCode(versionName: String): Int {
        val parts = versionName.split(".")
        return parts.getOrNull(0)?.toIntOrNull()?.times(10000)?.plus(
            parts.getOrNull(1)?.toIntOrNull()?.times(100) ?: 0
        )?.plus(parts.getOrNull(2)?.toIntOrNull() ?: 0) ?: 0
    }
    
    /**
     * Get current app's signature hash for verification
     */
    private fun getCurrentAppSignature(): String {
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNATURES
        )
        @Suppress("DEPRECATION")
        val signatures = packageInfo.signatures
        if (signatures != null && signatures.isNotEmpty()) {
            val signature = signatures[0]
            val md = MessageDigest.getInstance("SHA-256")
            md.update(signature.toByteArray())
            return md.digest().joinToString("") { "%02x".format(it) }
        }
        return ""
    }
    
    /**
     * Get APK file's signature hash for verification
     */
    private fun getApkSignature(apkFile: File): String {
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.GET_SIGNATURES
        )
        @Suppress("DEPRECATION")
        val signatures = packageInfo?.signatures
        if (signatures != null && signatures.isNotEmpty()) {
            val signature = signatures[0]
            val md = MessageDigest.getInstance("SHA-256")
            md.update(signature.toByteArray())
            return md.digest().joinToString("") { "%02x".format(it) }
        }
        return ""
    }
    

    
    /**
     * Data class containing update information
     */
    data class UpdateInfo(
        val versionName: String,        // Version name (e.g., "1.2.3")
        val versionCode: Int,           // Version code (e.g., 10203)
        val downloadUrl: String,        // Direct download URL for APK
        val fileSize: Long,             // File size in bytes
        val releaseNotes: String,       // Release notes from GitHub
        val releaseDate: String         // Release date from GitHub
    )
} 