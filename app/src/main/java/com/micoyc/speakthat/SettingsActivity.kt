package com.micoyc.speakthat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.materialswitch.MaterialSwitch
import com.micoyc.speakthat.databinding.ActivitySettingsBinding
import com.micoyc.speakthat.donations.DonationManager
import com.micoyc.speakthat.donations.DonationManagerProvider
import com.micoyc.speakthat.settings.BehaviorSettingsActivity

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var searchAdapter: SearchResultsAdapter
    private val settingsCategories = mutableListOf<SettingsCategory>()
    private val allSettings = mutableListOf<SettingsItem>()
    private val donationManager: DonationManager by lazy { DonationManagerProvider.get(this) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply saved theme FIRST before anything else
        val mainPrefs = getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
        applySavedTheme(mainPrefs)
        
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Configure system UI for proper insets handling
        configureSystemUI()
        
        setupToolbar()
        setupSearchFunctionality()
        setupSettingsCategories()
        setupAllSettings()
        setupClickListeners()
        setupThemeIcon()
    }

    private fun applySavedTheme(prefs: android.content.SharedPreferences) {
        val isDarkMode = prefs.getBoolean("dark_mode", true) // Default to dark mode
        
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
    
    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.settings)
    }
    
    private fun setupSearchFunctionality() {
        // Setup RecyclerView for search results
        searchAdapter = SearchResultsAdapter(emptyList()) { settingsItem ->
            // Hide search results and execute the settings item action
            binding.searchResultsRecyclerView.visibility = View.GONE
            binding.settingsScrollView.visibility = View.VISIBLE
            binding.searchEditText.setText("")
            
            // Handle special cases for support items
            when (settingsItem.id) {
                "feature_request", "bug_report", "general_support" -> {
                    showSupportDialog(settingsItem.id)
                }
                else -> {
                    settingsItem.navigationAction()
                }
            }
        }
        
        binding.searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = searchAdapter
        }
        
        // Setup search text watcher
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearch(s?.toString() ?: "")
            }
        })
    }
    
    private fun setupSettingsCategories() {
        // Create settings categories with their metadata (for backward compatibility)
        settingsCategories.clear()
        settingsCategories.addAll(listOf(
            SettingsCategory(
                id = "general",
                title = "General Settings",
                description = "App preferences, theme settings",
                cardView = binding.cardGeneralSettings,
                onClickAction = { startActivity(Intent(this, GeneralSettingsActivity::class.java)) }
            ),
            SettingsCategory(
                id = "behavior",
                title = "Behavior Settings",
                description = "When and how notifications are read",
                cardView = binding.cardBehaviorSettings,
                onClickAction = { startActivity(Intent(this, BehaviorSettingsActivity::class.java)) }
            ),
            SettingsCategory(
                id = "voice",
                title = "Voice Settings",
                description = "Text-to-speech voice and speed",
                cardView = binding.cardVoiceSettings,
                onClickAction = { startActivity(Intent(this, VoiceSettingsActivity::class.java)) }
            ),
            SettingsCategory(
                id = "filter",
                title = "Filter Settings",
                description = "Choose which apps to read",
                cardView = binding.cardFilterSettings,
                onClickAction = { startActivity(Intent(this, FilterSettingsActivity::class.java)) }
            ),
            SettingsCategory(
                id = "conditional",
                title = "Conditional Rules",
                description = "Advanced rules for when notifications are read",
                cardView = binding.cardConditionalRules,
                onClickAction = { startActivity(Intent(this, RulesActivity::class.java)) }
            ),
            SettingsCategory(
                id = "development",
                title = "Development Settings",
                description = "Debug tools and logging system",
                cardView = binding.cardDevelopmentSettings,
                onClickAction = { startActivity(Intent(this, DevelopmentSettingsActivity::class.java)) }
            ),
            SettingsCategory(
                id = "onboarding",
                title = "Re-run Onboarding",
                description = "See the app introduction again",
                cardView = binding.cardReRunOnboarding,
                onClickAction = { 
                    InAppLogger.logUserAction("Re-run onboarding selected")
                    startActivity(Intent(this, OnboardingActivity::class.java))
                }
            ),
            SettingsCategory(
                id = "support",
                title = "Support & Feedback",
                description = "Get help, report bugs, request features",
                cardView = binding.cardSupportFeedback,
                onClickAction = { showSupportDialog() }
            )
        ))
    }
    
    private fun setupAllSettings() {
        // Load all individual settings from the database
        allSettings.clear()
        allSettings.addAll(SettingsDatabase.getAllSettings(this))
    }
    
    private fun performSearch(query: String) {
        if (query.isBlank()) {
            // Show all settings when search is empty
            binding.searchResultsRecyclerView.visibility = View.GONE
            binding.settingsScrollView.visibility = View.VISIBLE
            return
        }
        
        val filteredResults = allSettings.filter { settingsItem ->
            val searchText = query.lowercase()
            
            // Search in title, description, category title, and keywords
            settingsItem.title.lowercase().contains(searchText) ||
            settingsItem.description.lowercase().contains(searchText) ||
            settingsItem.categoryTitle.lowercase().contains(searchText) ||
            settingsItem.searchKeywords.any { keyword -> 
                keyword.lowercase().contains(searchText) 
            }
        }
        
        if (filteredResults.isNotEmpty()) {
            searchAdapter.updateResults(filteredResults)
            binding.searchResultsRecyclerView.visibility = View.VISIBLE
            binding.settingsScrollView.visibility = View.GONE
        } else {
            binding.searchResultsRecyclerView.visibility = View.GONE
            binding.settingsScrollView.visibility = View.VISIBLE
        }
    }
    
    private fun setupClickListeners() {
        // The click listeners are now handled through the settingsCategories list
        // but we keep the original setup for backward compatibility
        binding.cardGeneralSettings.setOnClickListener {
            startActivity(Intent(this, GeneralSettingsActivity::class.java))
        }
        
        binding.cardBehaviorSettings.setOnClickListener {
            startActivity(Intent(this, BehaviorSettingsActivity::class.java))
        }
        
        binding.cardVoiceSettings.setOnClickListener {
            startActivity(Intent(this, VoiceSettingsActivity::class.java))
        }
        
        binding.cardFilterSettings.setOnClickListener {
            startActivity(Intent(this, FilterSettingsActivity::class.java))
        }
        
        binding.cardConditionalRules.setOnClickListener {
            startActivity(Intent(this, RulesActivity::class.java))
        }
        
        binding.cardDevelopmentSettings.setOnClickListener {
            startActivity(Intent(this, DevelopmentSettingsActivity::class.java))
        }
        
        binding.cardSupportFeedback.setOnClickListener {
            showSupportDialog()
        }
        
        binding.cardDonate.setOnClickListener {
            donationManager.showDonate(this) { showDonateDialog() }
        }
        
        binding.cardReRunOnboarding.setOnClickListener {
            InAppLogger.logUserAction("Re-run onboarding selected")
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            onBackPressedDispatcher.onBackPressed()
        } else {
            @Suppress("DEPRECATION")
            onBackPressed()
        }
        return true
    }
    
    private fun configureSystemUI() {
        // Set up proper window insets handling for different Android versions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // For Android 11+ (API 30+), use the new window insets API
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(true)
        } else {
            // For older versions (Android 10 and below), ensure proper system UI flags
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }
    
    private fun showSupportDialog(type: String = "") {
        InAppLogger.logUserAction("Support dialog opened")
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_support_feedback, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Get UI elements
        val cardFeatureRequest = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardFeatureRequest)
        val cardBugReport = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBugReport)
        val cardGeneralSupport = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardGeneralSupport)
        val switchIncludeLogs = dialogView.findViewById<MaterialSwitch>(R.id.switchIncludeLogs)
        val textLogInfo = dialogView.findViewById<android.widget.TextView>(R.id.textLogInfo)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        
        // Update log info
        val logCount = InAppLogger.getLogCount()
        textLogInfo.text = "Current log entries: $logCount"
        
        // Set up click listeners
        cardFeatureRequest.setOnClickListener {
            InAppLogger.logUserAction("Feature request selected")
            dialog.dismiss()
            sendSupportEmail("Feature Request", switchIncludeLogs.isChecked)
        }
        
        cardBugReport.setOnClickListener {
            InAppLogger.logUserAction("Bug report selected")
            dialog.dismiss()
            sendSupportEmail("Bug Report", switchIncludeLogs.isChecked)
        }
        
        cardGeneralSupport.setOnClickListener {
            InAppLogger.logUserAction("General support selected")
            dialog.dismiss()
            sendSupportEmail("Support", switchIncludeLogs.isChecked)
        }
        
        btnCancel.setOnClickListener {
            InAppLogger.logUserAction("Support dialog cancelled")
            dialog.dismiss()
        }
        
        // If a specific type was requested, automatically trigger it
        when (type) {
            "feature_request" -> {
                dialog.dismiss()
                sendSupportEmail("Feature Request", false)
            }
            "bug_report" -> {
                dialog.dismiss()
                sendSupportEmail("Bug Report", true)
            }
            "general_support" -> {
                dialog.dismiss()
                sendSupportEmail("Support", true)
            }
            else -> {
                dialog.show()
            }
        }
    }
    
    private fun sendSupportEmail(type: String, includeLogs: Boolean) {
        try {
            val subject = "SpeakThat! $type"
            val recipient = "micoycbusiness@gmail.com"
            
            val bodyBuilder = StringBuilder()
            
            when (type) {
                "Feature Request" -> {
                    bodyBuilder.append("I would like to request the following feature:\n\n")
                    bodyBuilder.append("[Please explain your idea]\n\n\n\n")
                }
                "Bug Report" -> {
                    bodyBuilder.append("I encountered the following issue:\n\n")
                    bodyBuilder.append("[Please explain the issue]\n\n\n\n")
                }
                "Support" -> {
                    bodyBuilder.append("I need help with:\n\n")
                    bodyBuilder.append("[Please explain what you need help with]\n\n\n\n")
                }
            }
            
            if (includeLogs) {
                bodyBuilder.append("=== DEBUG INFORMATION ===\n")
                bodyBuilder.append(InAppLogger.getSystemInfo(this))
                bodyBuilder.append("\n\n=== DEBUG LOGS ===\n")
                bodyBuilder.append(InAppLogger.getLogsForSupport())
                bodyBuilder.append("\n=== END DEBUG INFO ===\n\n")
            }
            
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, bodyBuilder.toString())
            }
            
            try {
                startActivity(emailIntent)
                InAppLogger.logUserAction("Support email opened", "Type: $type, Logs: $includeLogs")
            } catch (e: Exception) {
                // Fallback: try with chooser
                try {
                    startActivity(Intent.createChooser(emailIntent, "Send email"))
                    InAppLogger.logUserAction("Support email opened via chooser", "Type: $type, Logs: $includeLogs")
                } catch (e2: Exception) {
                    Toast.makeText(this, "No email app found. Please install an email app to send support requests.", Toast.LENGTH_LONG).show()
                    InAppLogger.logError("Support", "No email app available: ${e2.message}")
                }
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening email: ${e.message}", Toast.LENGTH_LONG).show()
            InAppLogger.logCrash(e, "Support email")
        }
    }
    
    private fun showDonateDialog() {
        InAppLogger.logUserAction("Donate dialog opened")
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_donate, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Get UI elements
        val cardKoFi = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardKoFi)
        val cardPatreon = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardPatreon)
        val cardGitHub = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardGitHub)
        val cardBitcoin = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBitcoin)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        
        // Set up click listeners
        cardKoFi.setOnClickListener {
            InAppLogger.logUserAction("Ko-fi donation selected")
            dialog.dismiss()
            openUrl("https://ko-fi.com/mitchib1440")
        }
        
        cardPatreon.setOnClickListener {
            InAppLogger.logUserAction("Patreon donation selected")
            dialog.dismiss()
            openUrl("https://www.patreon.com/c/mitchib1440")
        }
        
        cardGitHub.setOnClickListener {
            InAppLogger.logUserAction("GitHub Sponsors donation selected")
            dialog.dismiss()
            openUrl("https://github.com/sponsors/mitchib1440")
        }
        
        cardBitcoin.setOnClickListener {
            InAppLogger.logUserAction("Bitcoin donation selected")
            dialog.dismiss()
            copyBitcoinAddress()
        }
        
        btnCancel.setOnClickListener {
            InAppLogger.logUserAction("Donate dialog cancelled")
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            InAppLogger.logUserAction("External link opened", "URL: $url")
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open link: ${e.message}", Toast.LENGTH_SHORT).show()
            InAppLogger.logError("Donate", "Failed to open URL: ${e.message}")
        }
    }
    
    private fun copyBitcoinAddress() {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Bitcoin Address", "38ACvcbhsyaF5K4kRdxCFwPcKZNafUv8sq")
            clipboard.setPrimaryClip(clip)
            
            Toast.makeText(this, "Bitcoin address copied to clipboard", Toast.LENGTH_LONG).show()
            InAppLogger.logUserAction("Bitcoin address copied to clipboard")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to copy address: ${e.message}", Toast.LENGTH_SHORT).show()
            InAppLogger.logError("Donate", "Failed to copy Bitcoin address: ${e.message}")
        }
    }
    
    private fun setupThemeIcon() {
        // Set the appropriate icon for General Settings based on current theme
        val isDarkMode = resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        val iconRes = if (isDarkMode) {
            R.drawable.ic_light_mode_24
        } else {
            R.drawable.ic_dark_mode_24
        }
        
        binding.iconGeneralSettings.setImageResource(iconRes)
    }
    

} 
