package com.micoyc.speakthat

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.micoyc.speakthat.databinding.ActivityUpdateBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.view.WindowCompat

/**
 * Privacy-focused update activity
 * 
 * This activity provides:
 * - Clean, simple UI for update process
 * - Manual update checks only (no background polling)
 * - Clear progress indication
 * - APK signature verification
 * - User consent at every step
 * - Release notes viewing
 * 
 * Security features:
 * - Always asks user permission before downloading
 * - Verifies APK signature before installation
 * - Clear error messages for security issues
 */
class UpdateActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityUpdateBinding
    private lateinit var updateManager: UpdateManager
    private var updateInfo: UpdateManager.UpdateInfo? = null
    private var downloadedApk: File? = null
    
    companion object {
        private const val TAG = "UpdateActivity"
        private const val EXTRA_FORCE_CHECK = "force_check"
        
        /**
         * Start the update activity
         * @param context Context to start from
         * @param forceCheck If true, bypass time restrictions and check immediately
         */
        fun start(context: android.content.Context, forceCheck: Boolean = false) {
            val intent = Intent(context, UpdateActivity::class.java).apply {
                putExtra(EXTRA_FORCE_CHECK, forceCheck)
            }
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply saved theme FIRST before anything else
        val mainPrefs = getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
        applySavedTheme(mainPrefs)
        
        // Initialize logging
        InAppLogger.logSystemEvent("UpdateActivity started", "UpdateActivity")
        
        // Initialize update manager
        updateManager = UpdateManager.getInstance(this)
        
        // Initialize view binding
        binding = ActivityUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Configure system UI for proper insets handling
        // configureSystemUI() // This line is removed as per the new_code, as the edge-to-edge display handles insets.
        
        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.check_for_updates)
        
        // Set up UI
        setupUI()
        
        // Check for updates
        val forceCheck = intent.getBooleanExtra(EXTRA_FORCE_CHECK, false)
        if (forceCheck || updateManager.shouldCheckForUpdates()) {
            checkForUpdates()
        } else {
            showNoUpdateAvailable()
        }
    }

    private fun applySavedTheme(prefs: android.content.SharedPreferences) {
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
    
    /**
     * Set up UI elements and click listeners
     */
    private fun setupUI() {
        // Set up click listeners for all buttons
        binding.buttonCheckAgain.setOnClickListener {
            checkForUpdates()
        }
        
        binding.buttonDownload.setOnClickListener {
            updateInfo?.let { info ->
                downloadUpdate(info)
            }
        }
        
        binding.buttonInstall.setOnClickListener {
            downloadedApk?.let { apk ->
                installUpdate(apk)
            }
        }
        
        binding.buttonViewReleaseNotes.setOnClickListener {
            updateInfo?.let { info ->
                showReleaseNotes(info.releaseNotes)
            }
        }
        


    }
    
    /**
     * Configure system UI for proper insets handling
     */
    private fun configureSystemUI() {
        // Use fitsSystemWindows for proper padding - this should handle the status bar automatically
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(true)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }
    
    /**
     * Check for available updates
     */
    private fun checkForUpdates() {
        showCheckingState()
        
        lifecycleScope.launch {
            try {
                // CRITICAL: Check if app was installed from Google Play Store
                var isGooglePlay = updateManager.isInstalledFromGooglePlay()
                
                // AUTOMATIC FORCE FRESH DETECTION: If initially detected as Google Play, try force fresh detection
                if (isGooglePlay) {
                    Log.i(TAG, "Initial detection: Google Play Store - attempting force fresh detection")
                    InAppLogger.logSystemEvent("Initial Google Play detection - attempting force fresh detection", "UpdateActivity")
                    
                    // Show a brief message to user
                    withContext(Dispatchers.Main) {
                        binding.textStatus.text = "Verifying installation source..."
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    
                    // Force fresh detection
                    isGooglePlay = updateManager.forceFreshGooglePlayDetection()
                    
                    Log.i(TAG, "Force fresh detection result: isGooglePlay = $isGooglePlay")
                    InAppLogger.logSystemEvent("Force fresh detection result: isGooglePlay = $isGooglePlay", "UpdateActivity")
                    
                    // If still detected as Google Play after force fresh detection
                    if (isGooglePlay) {
                        Log.i(TAG, "Confirmed Google Play Store installation - GitHub updates disabled")
                        InAppLogger.logSystemEvent("Update check blocked - confirmed Google Play installation", "UpdateActivity")
                        
                        withContext(Dispatchers.Main) {
                            showGooglePlayMessage()
                        }
                        return@launch
                    } else {
                        Log.i(TAG, "Force fresh detection corrected false positive - proceeding with update check")
                        InAppLogger.logSystemEvent("Force fresh detection corrected false positive", "UpdateActivity")
                        
                        // Show brief success message
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@UpdateActivity, "Installation source verified - proceeding with update check", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                
                val update = updateManager.checkForUpdates()
                
                withContext(Dispatchers.Main) {
                    if (update != null) {
                        updateInfo = update
                        showUpdateAvailable(update)
                        // Show toast for update found
                        Toast.makeText(this@UpdateActivity, "Update available: ${update.versionName}", Toast.LENGTH_SHORT).show()
                    } else {
                        showNoUpdateAvailable()
                        // Show toast for no update
                        Toast.makeText(this@UpdateActivity, "You're using the latest version!", Toast.LENGTH_SHORT).show()
                    }
                }
                
            } catch (e: Exception) {
                InAppLogger.logError("UpdateActivity", "Update check failed: ${e.message}")
                
                withContext(Dispatchers.Main) {
                    showErrorState(getString(R.string.update_check_failed))
                    // Show error toast
                    val errorMessage = when {
                        e.message?.contains("network", ignoreCase = true) == true -> "Unable to check for updates! - Network error"
                        e.message?.contains("timeout", ignoreCase = true) == true -> "Unable to check for updates! - Connection timeout"
                        else -> "Unable to check for updates! - ${e.message ?: "Unknown error"}"
                    }
                    Toast.makeText(this@UpdateActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    /**
     * Start download process with user confirmation
     */
    private fun downloadUpdate(updateInfo: UpdateManager.UpdateInfo) {
        // Ask for user consent before downloading
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.download_update))
            .setMessage(getString(R.string.download_confirmation, updateInfo.versionName))
            .setPositiveButton(getString(R.string.download)) { _, _ ->
                performDownload(updateInfo)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    /**
     * Perform the actual download with progress tracking
     */
    private fun performDownload(updateInfo: UpdateManager.UpdateInfo) {
        showDownloadingState()
        
        lifecycleScope.launch {
            try {
                val apkFile = updateManager.downloadApk(updateInfo) { progress ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        updateDownloadProgress(progress)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (apkFile != null) {
                        downloadedApk = apkFile
                        showDownloadComplete()
                    } else {
                        showErrorState(getString(R.string.download_failed))
                    }
                }
                
            } catch (e: Exception) {
                InAppLogger.logError("UpdateActivity", "Download failed: ${e.message}")
                
                withContext(Dispatchers.Main) {
                    showErrorState(getString(R.string.download_failed))
                }
            }
        }
    }
    
    /**
     * Install the downloaded APK with signature verification
     */
    private fun installUpdate(apkFile: File) {
        // Verify APK signature first for security
        lifecycleScope.launch {
            try {
                val isValid = updateManager.verifyApkSignature(apkFile)
                
                withContext(Dispatchers.Main) {
                    if (isValid) {
                        startInstallation(apkFile)
                    } else {
                        showErrorState(getString(R.string.invalid_apk_signature))
                    }
                }
                
            } catch (e: Exception) {
                InAppLogger.logError("UpdateActivity", "Signature verification failed: ${e.message}")
                
                withContext(Dispatchers.Main) {
                    showErrorState(getString(R.string.signature_verification_failed))
                }
            }
        }
    }
    
    /**
     * Start the installation process using Android's package installer
     */
    private fun startInstallation(apkFile: File) {
        try {
            // Verify file exists and is readable
            if (!apkFile.exists()) {
                throw Exception("APK file does not exist: ${apkFile.absolutePath}")
            }
            
            if (!apkFile.canRead()) {
                throw Exception("APK file is not readable: ${apkFile.absolutePath}")
            }
            
            Log.d(TAG, "Installing APK: ${apkFile.absolutePath} (size: ${apkFile.length()} bytes)")
            
            // Create content URI for the APK file
            val uri = FileProvider.getUriForFile(
                this,
                "com.micoyc.speakthat.fileprovider",
                apkFile
            )
            
            Log.d(TAG, "FileProvider URI: $uri")
            
            // Create intent to install the APK
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            
            // Verify that we can resolve the intent
            if (intent.resolveActivity(packageManager) == null) {
                throw Exception("No app found to handle APK installation")
            }
            
            startActivity(intent)
            
            // Log successful installation start
            InAppLogger.logSystemEvent("Update installation started", "UpdateActivity")
            
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed: ${e.message}", e)
            InAppLogger.logError("UpdateActivity", "Installation failed: ${e.message}")
            showErrorState(getString(R.string.installation_failed))
        }
    }
    
    /**
     * Show release notes in a dialog
     */
    private fun showReleaseNotes(notes: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.release_notes))
            .setMessage(notes.ifEmpty { getString(R.string.no_release_notes) })
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }
    

    
    // UI State Management Methods
    
    /**
     * Show checking for updates state
     */
    private fun showCheckingState() {
        binding.progressBar.visibility = View.VISIBLE
        binding.textStatus.text = getString(R.string.checking_for_updates)
        binding.buttonCheckAgain.visibility = View.GONE
        binding.buttonDownload.visibility = View.GONE
        binding.buttonInstall.visibility = View.GONE
        binding.buttonViewReleaseNotes.visibility = View.GONE
    }
    
    /**
     * Show update available state
     */
    private fun showUpdateAvailable(update: UpdateManager.UpdateInfo) {
        binding.progressBar.visibility = View.GONE
        binding.textStatus.text = getString(R.string.update_available, update.versionName)
        binding.textUpdateInfo.text = getString(
            R.string.update_info,
            update.versionName,
            formatFileSize(update.fileSize),
            update.releaseDate
        )
        
        binding.buttonCheckAgain.visibility = View.GONE
        binding.buttonDownload.visibility = View.VISIBLE
        binding.buttonViewReleaseNotes.visibility = View.VISIBLE
        binding.buttonInstall.visibility = View.GONE
        
        // Log update availability
        InAppLogger.logSystemEvent("Update available: ${update.versionName}", "UpdateActivity")
    }
    
    /**
     * Show no update available state
     */
    private fun showNoUpdateAvailable() {
        binding.progressBar.visibility = View.GONE
        binding.textStatus.text = getString(R.string.no_update_available)
        binding.textUpdateInfo.text = getString(R.string.app_up_to_date)
        
        binding.buttonCheckAgain.visibility = View.VISIBLE
        binding.buttonDownload.visibility = View.GONE
        binding.buttonInstall.visibility = View.GONE
        binding.buttonViewReleaseNotes.visibility = View.GONE
    }
    
    /**
     * Show downloading state
     */
    private fun showDownloadingState() {
        binding.progressBar.visibility = View.VISIBLE
        binding.textStatus.text = getString(R.string.downloading_update)
        binding.buttonDownload.visibility = View.GONE
        binding.buttonInstall.visibility = View.GONE
    }
    
    /**
     * Update download progress
     */
    private fun updateDownloadProgress(progress: Int) {
        binding.progressBar.progress = progress
        binding.textStatus.text = getString(R.string.downloading_update_progress, progress)
    }
    
    /**
     * Show download complete state
     */
    private fun showDownloadComplete() {
        binding.progressBar.visibility = View.GONE
        binding.textStatus.text = getString(R.string.download_complete)
        binding.buttonInstall.visibility = View.VISIBLE
        binding.buttonDownload.visibility = View.GONE
        
        // Log successful download
        InAppLogger.logSystemEvent("Update downloaded successfully", "UpdateActivity")
    }
    
    /**
     * Show error state
     */
    private fun showErrorState(errorMessage: String) {
        binding.progressBar.visibility = View.GONE
        binding.textStatus.text = errorMessage
        binding.buttonCheckAgain.visibility = View.VISIBLE
        binding.buttonDownload.visibility = View.GONE
        binding.buttonInstall.visibility = View.GONE
        binding.buttonViewReleaseNotes.visibility = View.GONE
        
        // Log error
        InAppLogger.logError("UpdateActivity", errorMessage)
    }
    
    /**
     * Format file size for display
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
    
    /**
     * Handle back button press
     */
    override fun onSupportNavigateUp(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            onBackPressedDispatcher.onBackPressed()
        } else {
            @Suppress("DEPRECATION")
            onBackPressed()
        }
        return true
    }
    
    /**
     * Show Google Play message to users who installed from Google Play Store
     * This explains why GitHub updates are disabled and directs them to Google Play
     */
    private fun showGooglePlayMessage() {
        binding.progressBar.visibility = View.GONE
        binding.textStatus.text = "Updates via Google Play"
        binding.textUpdateInfo.text = "You installed SpeakThat from Google Play Store. " +
            "For security and policy compliance, automatic updates are handled through Google Play.\n\n" +
            "To update the app, please visit Google Play Store and check for updates there.\n\n" +
            "This ensures you receive verified, secure updates that comply with Google Play policies."
        
        binding.buttonCheckAgain.visibility = View.VISIBLE
        binding.buttonDownload.visibility = View.GONE
        binding.buttonInstall.visibility = View.GONE
        binding.buttonViewReleaseNotes.visibility = View.GONE
        
        // Add a button to open Google Play Store
        binding.buttonCheckAgain.text = "Open Google Play"
        binding.buttonCheckAgain.setOnClickListener {
            try {
                // Open Google Play Store to the app's page
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("market://details?id=${packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to web browser if Play Store app is not available
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=${packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }
        
        Log.i(TAG, "Showed Google Play message in UpdateActivity")
        InAppLogger.logSystemEvent("Google Play message shown in UpdateActivity", "UpdateActivity")
    }

} 