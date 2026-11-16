/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.micoyc.speakthat.databinding.ActivitySettingsBinding
import com.micoyc.speakthat.settings.BehaviorSettingsActivity
import com.micoyc.speakthat.summary.SummarySettingsActivity

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var searchAdapter: SearchResultsAdapter
    private val settingsCategories = mutableListOf<SettingsCategory>()
    private val allSettings = mutableListOf<SettingsItem>()
    private val searchHandler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null
    
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
    }

    private fun applySavedTheme(prefs: android.content.SharedPreferences) {
        val isDarkMode = prefs.getBoolean("dark_mode", true) // Default to dark mode
        val desiredMode = if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        } else {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        }
        val currentMode = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode()
        
        // Only set the night mode if it's different from the current mode
        // This prevents unnecessary configuration changes that cause activity recreation loops
        if (currentMode != desiredMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(desiredMode)
        }
    }
    
    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.settings)
    }
    
    private fun setupSearchFunctionality() {
        searchAdapter = SearchResultsAdapter(emptyList()) { settingsItem ->
            binding.searchResultsRecyclerView.visibility = View.GONE
            binding.settingsScrollView.visibility = View.VISIBLE
            binding.searchEditText.setText("")
            binding.searchProgressBar.visibility = View.GONE
            settingsItem.navigationAction(this, settingsItem.id)
        }
        
        binding.searchResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = searchAdapter
        }
        
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                scheduleSearch(s?.toString() ?: "")
            }
        })
    }

    private fun scheduleSearch(query: String) {
        pendingSearch?.let { searchHandler.removeCallbacks(it) }
        if (query.isBlank()) {
            binding.searchProgressBar.visibility = View.GONE
            binding.searchResultsRecyclerView.visibility = View.GONE
            binding.settingsScrollView.visibility = View.VISIBLE
            return
        }

        binding.searchProgressBar.visibility = View.VISIBLE
        val runnable = Runnable {
            performSearch(query)
            binding.searchProgressBar.visibility = View.GONE
        }
        pendingSearch = runnable
        searchHandler.postDelayed(runnable, 300)
    }

    override fun onDestroy() {
        pendingSearch?.let { searchHandler.removeCallbacks(it) }
        super.onDestroy()
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
                id = "compatibility",
                title = "Compatibility Settings",
                description = "Fixes for common device-related issues",
                cardView = binding.cardCompatibilitySettings,
                onClickAction = { startActivity(Intent(this, CompatibilitySettingsActivity::class.java)) }
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
                onClickAction = { startActivity(Intent(this, SupportActivity::class.java)) }
            )
        ))
    }
    
    private fun setupAllSettings() {
        allSettings.clear()
        allSettings.addAll(SettingsDatabase.getAllSettings(this))
    }
    
    private fun performSearch(query: String) {
        if (query.isBlank()) {
            binding.searchResultsRecyclerView.visibility = View.GONE
            binding.settingsScrollView.visibility = View.VISIBLE
            return
        }

        val groups = SettingsSearchEngine.search(this, query, allSettings)

        if (groups.isNotEmpty()) {
            searchAdapter.updateResults(groups)
            binding.searchResultsRecyclerView.visibility = View.VISIBLE
            binding.settingsScrollView.visibility = View.GONE
        } else {
            binding.searchResultsRecyclerView.visibility = View.GONE
            binding.settingsScrollView.visibility = View.VISIBLE
        }
    }
    
    private fun setupClickListeners() {
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
        
        binding.cardCompatibilitySettings.setOnClickListener {
            startActivity(Intent(this, CompatibilitySettingsActivity::class.java))
        }

        binding.cardSummarySettings.setOnClickListener {
            startActivity(Intent(this, SummarySettingsActivity::class.java))
        }

        binding.cardClockSettings.setOnClickListener {
            startActivity(Intent(this, ClockSettingsActivity::class.java))
        }
        
        binding.cardDevelopmentSettings.setOnClickListener {
            startActivity(Intent(this, DevelopmentSettingsActivity::class.java))
        }
        
        binding.cardSupportFeedback.setOnClickListener {
            startActivity(Intent(this, SupportActivity::class.java))
        }
        
        // Donations removed in this personal build
        binding.cardDonate.visibility = View.GONE
        
        binding.cardReRunOnboarding.setOnClickListener {
            InAppLogger.logUserAction("Re-run onboarding selected")
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        // Simply close this activity instead of navigating to parent
        // This prevents the activity recreation loop caused by parent activity chain
        finish()
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
}
