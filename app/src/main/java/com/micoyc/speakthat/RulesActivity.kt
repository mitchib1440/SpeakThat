package com.micoyc.speakthat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.annotation.StringRes
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.Toast
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.databinding.ActivityRulesBinding
import com.micoyc.speakthat.automation.AutomationMode
import com.micoyc.speakthat.automation.AutomationModeManager
import com.micoyc.speakthat.rules.RuleManager
import com.micoyc.speakthat.rules.Rule
import com.micoyc.speakthat.rules.RulesAdapter
import com.micoyc.speakthat.rules.RuleTemplates

class RulesActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRulesBinding
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private lateinit var ruleManager: RuleManager
    private lateinit var automationModeManager: AutomationModeManager
    private lateinit var rulesAdapter: RulesAdapter
    private var hasShownExperimentalWarning = false
    private var experimentalWarningDialog: AlertDialog? = null
    private var suppressModeCallback = false
    private var currentMode: AutomationMode = AutomationMode.OFF
    
    companion object {
        private const val PREFS_NAME = "SpeakThatPrefs"
        private const val KEY_DARK_MODE = "dark_mode"
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
        automationModeManager = AutomationModeManager(this)
        
        setupModeSelector()
        setupAutomationStringsCard()
        setupButtons()
        setupRecyclerView()
        updateUI(automationModeManager.getMode())
    }
    
    private fun applySavedTheme() {
        val isDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false)
        
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
    
    private fun setupModeSelector() {
        val initialMode = automationModeManager.getMode()
        updateModeSelection(initialMode)
        
        binding.radioAutomationModes.setOnCheckedChangeListener { _, checkedId ->
            if (suppressModeCallback) return@setOnCheckedChangeListener
            
            val selectedMode = when (checkedId) {
                R.id.radioModeConditional -> AutomationMode.CONDITIONAL_RULES
                R.id.radioModeExternal -> AutomationMode.EXTERNAL_AUTOMATION
                else -> AutomationMode.OFF
            }
            
            automationModeManager.setMode(selectedMode)
            updateUI(selectedMode)
        }
        
        binding.buttonAutomationInfo.setOnClickListener {
            showAutomationInfoDialog()
        }
    }
    
    private fun setupAutomationStringsCard() {
        val rows = listOf(
            Triple(binding.cardAutomationStringActionEnable, binding.textAutomationValueActionEnable, R.string.rules_quick_strings_action_enable_label),
            Triple(binding.cardAutomationStringActionDisable, binding.textAutomationValueActionDisable, R.string.rules_quick_strings_action_disable_label),
            Triple(binding.cardAutomationStringReceiver, binding.textAutomationValueReceiver, R.string.rules_quick_strings_receiver_label),
            Triple(binding.cardAutomationStringPackage, binding.textAutomationValuePackage, R.string.rules_quick_strings_package_label)
        )

        rows.forEach { (container, valueView, labelRes) ->
            container.setOnClickListener {
                copyAutomationValue(valueView.text.toString(), labelRes)
            }
        }
    }
    
    private fun copyAutomationValue(value: String, @StringRes labelRes: Int) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SpeakThat automation", value))
        Toast.makeText(
            this,
            getString(R.string.rules_quick_strings_copied, getString(labelRes)),
            Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun setupButtons() {
        binding.fabAddRule.setOnClickListener {
            openTemplateSelection("Add rule FAB clicked - opened template selection")
        }
    }
    
    private fun openTemplateSelection(logMessage: String) {
        // Go directly to template selection
        startActivity(Intent(this, TemplateSelectionActivity::class.java))
        InAppLogger.logUserAction(logMessage)
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
        val mode = automationModeManager.getMode()
        InAppLogger.logDebug("RulesActivity", "onResume() called - refreshing UI (mode=$mode)")
        updateUI(mode)
        
        if (!hasShownExperimentalWarning &&
            mode == AutomationMode.CONDITIONAL_RULES &&
            ruleManager.isRulesEnabled()
        ) {
            hasShownExperimentalWarning = true
            showExperimentalFeatureWarning()
        }
    }
    
    private fun updateUI(modeOverride: AutomationMode? = null) {
        val mode = modeOverride ?: automationModeManager.getMode()
        currentMode = mode
        updateModeSelection(mode)
        binding.cardAutomationStrings.visibility = if (mode == AutomationMode.EXTERNAL_AUTOMATION) View.VISIBLE else View.GONE
        binding.fabAddRule.visibility = if (mode == AutomationMode.CONDITIONAL_RULES) View.VISIBLE else View.GONE
        
        when (mode) {
            AutomationMode.CONDITIONAL_RULES -> renderRulesContent()
            AutomationMode.OFF -> renderAutomationDisabledState(
                R.string.rules_mode_off_empty_title,
                R.string.rules_mode_off_empty_description
            )
            AutomationMode.EXTERNAL_AUTOMATION -> renderAutomationDisabledState(
                R.string.rules_mode_external_empty_title,
                R.string.rules_mode_external_empty_description
            )
        }
    }
    
    private fun updateModeSelection(mode: AutomationMode) {
        suppressModeCallback = true
        binding.radioModeOff.isChecked = mode == AutomationMode.OFF
        binding.radioModeConditional.isChecked = mode == AutomationMode.CONDITIONAL_RULES
        binding.radioModeExternal.isChecked = mode == AutomationMode.EXTERNAL_AUTOMATION
        suppressModeCallback = false
    }
    
    private fun renderRulesContent() {
        val rules = ruleManager.getAllRules()
        InAppLogger.logDebug("RulesActivity", "Rendering ${rules.size} rules for Conditional mode")
        if (rules.isNotEmpty()) {
            binding.rulesContainer.visibility = View.VISIBLE
            binding.emptyStateContainer.visibility = View.GONE
            rulesAdapter.updateRules(rules)
        } else {
            binding.rulesContainer.visibility = View.GONE
            binding.emptyStateContainer.visibility = View.VISIBLE
            binding.textEmptyStateTitle.setText(R.string.rules_no_rules_created)
            binding.textEmptyStateDescription.setText(R.string.rules_create_first_rule_description)
            InAppLogger.logDebug("RulesActivity", "No rules found, showing empty state")
        }
    }
    
    private fun renderAutomationDisabledState(
        @StringRes titleRes: Int,
        @StringRes descriptionRes: Int
    ) {
        binding.rulesContainer.visibility = View.GONE
        binding.emptyStateContainer.visibility = View.VISIBLE
        binding.textEmptyStateTitle.setText(titleRes)
        binding.textEmptyStateDescription.setText(descriptionRes)
        InAppLogger.logDebug("RulesActivity", "Automation disabled state applied (title=$titleRes)")
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
    
    private fun showAutomationInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.rules_mode_info_title)
            .setMessage(R.string.rules_mode_info_description)
            .setPositiveButton(R.string.ok, null)
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