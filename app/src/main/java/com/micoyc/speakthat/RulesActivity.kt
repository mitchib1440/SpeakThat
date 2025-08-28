package com.micoyc.speakthat

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.databinding.ActivityRulesBinding
import com.micoyc.speakthat.rules.RuleManager
import com.micoyc.speakthat.rules.Rule
import com.micoyc.speakthat.rules.RulesAdapter
import com.micoyc.speakthat.rules.RuleTemplates

class RulesActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRulesBinding
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private lateinit var ruleManager: RuleManager
    private lateinit var rulesAdapter: RulesAdapter
    private var hasShownExperimentalWarning = false
    private var experimentalWarningDialog: AlertDialog? = null
    
    companion object {
        private const val PREFS_NAME = "SpeakThatPrefs"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_RULES_ENABLED = "rules_enabled"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme FIRST before anything else
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        applySavedTheme()
        
        super.onCreate(savedInstanceState)
        binding = ActivityRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Rules"
        
        // Initialize rule manager
        ruleManager = RuleManager(this)
        
        setupMasterToggle()
        setupButtons()
        setupRecyclerView()
        loadSettings()
    }
    
    private fun applySavedTheme() {
        val isDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false)
        
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
    
    private fun setupMasterToggle() {
        binding.switchMasterToggle.setOnCheckedChangeListener { _, isChecked ->
            // Update both local preferences and rule manager
            sharedPreferences.edit().putBoolean(KEY_RULES_ENABLED, isChecked).apply()
            ruleManager.setRulesEnabled(isChecked)
            
            updateUI()
            InAppLogger.logUserAction("Rules master toggle: ${if (isChecked) "enabled" else "disabled"}")
        }
    }
    
    private fun setupButtons() {
        binding.btnAddRule.setOnClickListener {
            // Go directly to template selection
            startActivity(Intent(this, TemplateSelectionActivity::class.java))
            InAppLogger.logUserAction("Add rule button clicked - opened template selection")
        }
        
        binding.btnAddFirstRule.setOnClickListener {
            // Go directly to template selection
            startActivity(Intent(this, TemplateSelectionActivity::class.java))
            InAppLogger.logUserAction("Add first rule button clicked - opened template selection")
        }
    }
    

    
    private fun setupRecyclerView() {
        rulesAdapter = RulesAdapter(
            mutableListOf(),
            onEditRule = { rule -> editRule(rule) },
            onDeleteRule = { rule -> deleteRule(rule) },
            onToggleRule = { rule, enabled -> toggleRule(rule, enabled) }
        )
        
        binding.recyclerRules.apply {
            layoutManager = LinearLayoutManager(this@RulesActivity)
            adapter = rulesAdapter
        }
        
        InAppLogger.logDebug("RulesActivity", "RecyclerView initialized")
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh the rules list when returning from RuleBuilderActivity
        InAppLogger.logDebug("RulesActivity", "onResume() called - refreshing UI")
        updateUI()
        
        // Show experimental warning only once when rules are enabled
        if (!hasShownExperimentalWarning && ruleManager.isRulesEnabled()) {
            hasShownExperimentalWarning = true
            showExperimentalFeatureWarning()
        }
    }
    
    private fun loadSettings() {
        val isMasterEnabled = ruleManager.isRulesEnabled()
        binding.switchMasterToggle.isChecked = isMasterEnabled
        updateUI()
    }
    
    private fun updateUI() {
        val isMasterEnabled = ruleManager.isRulesEnabled()
        InAppLogger.logDebug("RulesActivity", "updateUI() - Master toggle enabled: $isMasterEnabled")
        
        if (isMasterEnabled) {
            val rules = ruleManager.getAllRules()
            val hasRules = rules.isNotEmpty()
            
            InAppLogger.logDebug("RulesActivity", "updateUI() - Loaded ${rules.size} rules from RuleManager")
            if (rules.isNotEmpty()) {
                InAppLogger.logDebug("RulesActivity", "updateUI() - Rule names: ${rules.map { it.name }}")
            }
            
            if (hasRules) {
                binding.rulesContainer.visibility = View.VISIBLE
                binding.emptyStateContainer.visibility = View.GONE
                rulesAdapter.updateRules(rules)
                InAppLogger.logDebug("RulesActivity", "Found ${rules.size} rules to display")
            } else {
                binding.rulesContainer.visibility = View.GONE
                binding.emptyStateContainer.visibility = View.VISIBLE
                InAppLogger.logDebug("RulesActivity", "No rules found, showing empty state")
            }
        } else {
            binding.rulesContainer.visibility = View.GONE
            binding.emptyStateContainer.visibility = View.VISIBLE
            InAppLogger.logDebug("RulesActivity", "Rules system disabled, showing empty state")
        }
    }
    
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showExperimentalFeatureWarning() {
        // Dismiss any existing dialog first
        experimentalWarningDialog?.dismiss()
        
        experimentalWarningDialog = AlertDialog.Builder(this)
            .setTitle("Experimental Feature")
            .setMessage("Rules are experimental. If you experience issues, you can disable them in settings. Your existing filters and privacy settings will always be respected.")
            .setPositiveButton("I Understand") { _, _ ->
                InAppLogger.logUserAction("Experimental feature warning acknowledged")
                experimentalWarningDialog = null
            }
            .setOnDismissListener {
                experimentalWarningDialog = null
            }
            .show()
    }
    
    // Rule management methods
    private fun editRule(rule: Rule) {
        // Launch RuleBuilderActivity with existing rule data for editing
        val intent = Intent(this, RuleBuilderActivity::class.java).apply {
            putExtra("EDIT_RULE_ID", rule.id)
            putExtra("EDIT_RULE_DATA", rule.toJson())
        }
        startActivity(intent)
        InAppLogger.logUserAction("Edit rule: ${rule.name}")
    }
    
    private fun deleteRule(rule: Rule) {
        AlertDialog.Builder(this)
            .setTitle("Delete Rule")
            .setMessage("Are you sure you want to delete the rule '${rule.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                val success = ruleManager.deleteRule(rule.id)
                if (success) {
                    rulesAdapter.removeRule(rule)
                    InAppLogger.logUserAction("Rule deleted: ${rule.name}")
                } else {
                    InAppLogger.logError("RulesActivity", "Failed to delete rule: ${rule.name}")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun toggleRule(rule: Rule, enabled: Boolean) {
        val updatedRule = rule.copy(enabled = enabled)
        val success = ruleManager.updateRule(updatedRule)
        if (success) {
            rulesAdapter.updateRule(updatedRule)
            InAppLogger.logUserAction("Rule ${if (enabled) "enabled" else "disabled"}: ${rule.name}")
        } else {
            InAppLogger.logError("RulesActivity", "Failed to toggle rule: ${rule.name}")
            // Revert the switch if update failed
            rulesAdapter.updateRule(rule)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up dialog to prevent window leaks
        experimentalWarningDialog?.dismiss()
        experimentalWarningDialog = null
    }
    
    override fun onSupportNavigateUp(): Boolean {
        @Suppress("DEPRECATION")
        onBackPressed()
        return true
    }
} 