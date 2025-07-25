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
import com.google.android.material.switchmaterial.SwitchMaterial
import com.micoyc.speakthat.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var searchAdapter: SearchResultsAdapter
    private val settingsCategories = mutableListOf<SettingsCategory>()
    private val allSettings = mutableListOf<SettingsItem>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Configure system UI for proper insets handling
        configureSystemUI()
        
        setupToolbar()
        setupSearchFunctionality()
        setupSettingsCategories()
        setupAllSettings()
        setupClickListeners()
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
                icon = "⚙️",
                cardView = binding.cardGeneralSettings,
                onClickAction = { startActivity(Intent(this, GeneralSettingsActivity::class.java)) }
            ),
            SettingsCategory(
                id = "behavior",
                title = "Behavior Settings",
                description = "When and how notifications are read",
                icon = "🔔",
                cardView = binding.cardBehaviorSettings,
                onClickAction = { startActivity(Intent(this, BehaviorSettingsActivity::class.java)) }
            ),
            SettingsCategory(
                id = "voice",
                title = "Voice Settings",
                description = "Text-to-speech voice and speed",
                icon = "🎙️",
                cardView = binding.cardVoiceSettings,
                onClickAction = { startActivity(Intent(this, VoiceSettingsActivity::class.java)) }
            ),
            SettingsCategory(
                id = "filter",
                title = "Filter Settings",
                description = "Choose which apps to read",
                icon = "🔍",
                cardView = binding.cardFilterSettings,
                onClickAction = { startActivity(Intent(this, FilterSettingsActivity::class.java)) }
            ),
            SettingsCategory(
                id = "development",
                title = "Development Settings",
                description = "Debug tools and logging system",
                icon = "🛠️",
                cardView = binding.cardDevelopmentSettings,
                onClickAction = { startActivity(Intent(this, DevelopmentSettingsActivity::class.java)) }
            ),
            SettingsCategory(
                id = "onboarding",
                title = "Re-run Onboarding",
                description = "See the app introduction again",
                icon = "🔄",
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
                icon = "💬",
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
        
        binding.cardDevelopmentSettings.setOnClickListener {
            startActivity(Intent(this, DevelopmentSettingsActivity::class.java))
        }
        
        binding.cardSupportFeedback.setOnClickListener {
            showSupportDialog()
        }
        
        binding.cardReRunOnboarding.setOnClickListener {
            InAppLogger.logUserAction("Re-run onboarding selected")
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    private fun configureSystemUI() {
        // Set up proper window insets handling for different Android versions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // For Android 11+ (API 30+), use the new window insets API
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
        val switchIncludeLogs = dialogView.findViewById<SwitchMaterial>(R.id.switchIncludeLogs)
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
                sendSupportEmail("Bug Report", false)
            }
            "general_support" -> {
                dialog.dismiss()
                sendSupportEmail("Support", false)
            }
            else -> {
                dialog.show()
            }
        }
    }
    
    private fun sendSupportEmail(type: String, includeLogs: Boolean) {
        try {
            val subject = "SpeakThat! $type"
            val recipient = "oceanstream72@gmail.com"
            
            val bodyBuilder = StringBuilder()
            bodyBuilder.append("Hello SpeakThat! Team,\n\n")
            
            when (type) {
                "Feature Request" -> {
                    bodyBuilder.append("I would like to request the following feature:\n\n")
                    bodyBuilder.append("[Please describe your feature request here]\n\n")
                    bodyBuilder.append("Why would this feature be useful?\n")
                    bodyBuilder.append("[Please explain the benefit or use case]\n\n")
                }
                "Bug Report" -> {
                    bodyBuilder.append("I encountered the following issue:\n\n")
                    bodyBuilder.append("What happened?\n")
                    bodyBuilder.append("[Please describe the problem]\n\n")
                    bodyBuilder.append("What were you trying to do?\n")
                    bodyBuilder.append("[Please describe the steps you took]\n\n")
                    bodyBuilder.append("What did you expect to happen?\n")
                    bodyBuilder.append("[Please describe the expected behavior]\n\n")
                }
                "Support" -> {
                    bodyBuilder.append("I need help with:\n\n")
                    bodyBuilder.append("[Please describe your question or issue]\n\n")
                }
            }
            
            if (includeLogs) {
                bodyBuilder.append("=== DEBUG INFORMATION ===\n")
                bodyBuilder.append(InAppLogger.getSystemInfo())
                bodyBuilder.append("\n\n=== DEBUG LOGS ===\n")
                bodyBuilder.append(InAppLogger.getLogsForSupport())
                bodyBuilder.append("\n=== END DEBUG INFO ===\n\n")
            }
            
            bodyBuilder.append("Thank you for your time!\n")
            bodyBuilder.append("- SpeakThat! User")
            
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
    

} 