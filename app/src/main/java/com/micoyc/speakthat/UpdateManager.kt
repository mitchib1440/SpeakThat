package com.micoyc.speakthat

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
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
 * 
 * Security features:
 * - HTTPS-only connections
 * - APK signature verification
 * - User consent required for all operations
 * - No background polling (battery-friendly)
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
        
        // Default settings
        private const val DEFAULT_UPDATE_CHECK_INTERVAL = 24L // 24 hours
        private const val NETWORK_TIMEOUT_SECONDS = 30L
        
        @Volatile
        private var INSTANCE: UpdateManager? = null
        
        fun getInstance(context: Context): UpdateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UpdateManager(context.applicationContext).also { INSTANCE = it }
            }
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
     * Check if an update is available by comparing current version with GitHub release
     * @return UpdateInfo if update is available, null otherwise
     */
    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for updates...")
            
            // Get current app version from package manager
            val currentVersion = getCurrentVersion()
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
                
                return@withContext releaseInfo
            } else {
                Log.d(TAG, "No update available - app is up to date")
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
        val frequency = generalPrefs.getString("update_check_frequency", "weekly")
        
        // Convert frequency to hours
        val intervalHours = when (frequency) {
            "daily" -> 24L
            "weekly" -> 24L * 7L
            "monthly" -> 24L * 30L
            "never" -> Long.MAX_VALUE // Never check
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
        val lastNotifiedVersion = prefs.getString(KEY_LAST_CHECKED_VERSION, "")
        return lastNotifiedVersion == versionName
    }
    

    
    // Private helper methods below
    
    /**
     * Get current app version from package manager
     */
    private fun getCurrentVersion(): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return packageInfo.versionName ?: "1.0.0"
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
        
        // Find APK asset in the release
        val assets = json.getJSONArray("assets")
        var downloadUrl = ""
        var fileSize = 0L
        
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.getString("name")
            if (name.endsWith(".apk")) {
                downloadUrl = asset.getString("browser_download_url")
                fileSize = asset.getLong("size")
                break
            }
        }
        
        if (downloadUrl.isEmpty()) {
            Log.w(TAG, "No APK asset found in GitHub release")
            return null
        }
        
        return UpdateInfo(
            versionName = versionName,
            versionCode = extractVersionCode(versionName),
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
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNATURES
        )
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
        val packageInfo = context.packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.GET_SIGNATURES
        )
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