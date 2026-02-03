package com.micoyc.speakthat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.Toast
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.FileExportHelper
import com.micoyc.speakthat.databinding.ActivityRulesBinding
import com.micoyc.speakthat.automation.AutomationMode
import com.micoyc.speakthat.automation.AutomationModeManager
import com.micoyc.speakthat.rules.RuleManager
import com.micoyc.speakthat.rules.Rule
import com.micoyc.speakthat.rules.RulesAdapter
import com.micoyc.speakthat.rules.RuleTemplates
import com.micoyc.speakthat.rules.RuleConfigManager
import com.micoyc.speakthat.rules.RuleConfigManager.RulePermissionType

class RulesActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRulesBinding
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private lateinit var ruleManager: RuleManager
    private lateinit var automationModeManager: AutomationModeManager
    private lateinit var rulesAdapter: RulesAdapter
    private var suppressModeCallback = false
    private var currentMode: AutomationMode = AutomationMode.OFF
    private lateinit var importFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private var pendingRulesImport: List<Rule>? = null
    
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
        supportActionBar?.title = getString(R.string.rules_title)
        
        // Initialize rule manager
        ruleManager = RuleManager(this)
        automationModeManager = AutomationModeManager(this)
        
        // Show loading initially
        setLoading(true)

        setupModeSelector()
        setupAutomationStringsCard()
        setupButtons()
        setupRecyclerView()
        initializeImportExportLaunchers()
        updateUI(automationModeManager.getMode())

        // Hide loading after initialization
        setLoading(false)
    }

    private fun setLoading(loading: Boolean) {
        binding.loadingContainer.visibility = if (loading) View.VISIBLE else View.GONE
        binding.rulesScrollView.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        
        // Set random loading text
        if (loading) {
            val loadingLines = listOf(
                R.string.loading_line_1, R.string.loading_line_2, R.string.loading_line_3,
                R.string.loading_line_4, R.string.loading_line_5, R.string.loading_line_6,
                R.string.loading_line_7, R.string.loading_line_8, R.string.loading_line_9,
                R.string.loading_line_10, R.string.loading_line_11, R.string.loading_line_12,
                R.string.loading_line_13, R.string.loading_line_14, R.string.loading_line_15,
                R.string.loading_line_16, R.string.loading_line_17, R.string.loading_line_18,
                R.string.loading_line_19, R.string.loading_line_20, R.string.loading_line_21,
                R.string.loading_line_22, R.string.loading_line_23, R.string.loading_line_24,
                R.string.loading_line_25, R.string.loading_line_26, R.string.loading_line_27,
                R.string.loading_line_28, R.string.loading_line_29, R.string.loading_line_30,
                R.string.loading_line_31, R.string.loading_line_32, R.string.loading_line_33,
                R.string.loading_line_34, R.string.loading_line_35, R.string.loading_line_36,
                R.string.loading_line_37, R.string.loading_line_38, R.string.loading_line_39,
                R.string.loading_line_40, R.string.loading_line_41, R.string.loading_line_42,
                R.string.loading_line_43, R.string.loading_line_44, R.string.loading_line_45,
                R.string.loading_line_46, R.string.loading_line_47, R.string.loading_line_48,
                R.string.loading_line_49, R.string.loading_line_50
            )
            val randomLine = loadingLines.random()
            binding.loadingText.setText(randomLine)
        }
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
    }
    
    private fun updateUI(modeOverride: AutomationMode? = null) {
        val mode = modeOverride ?: automationModeManager.getMode()
        currentMode = mode
        updateModeSelection(mode)
        binding.cardAutomationStrings.visibility = if (mode == AutomationMode.EXTERNAL_AUTOMATION) View.VISIBLE else View.GONE
        binding.fabAddRule.visibility = if (mode == AutomationMode.CONDITIONAL_RULES) View.VISIBLE else View.GONE
        invalidateOptionsMenu()
        
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
            .setTitle(R.string.rules_error_title)
            .setMessage(message)
            .setPositiveButton(R.string.button_ok, null)
            .show()
    }
    
    private fun showAutomationInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.rules_mode_info_title)
            .setMessage(R.string.rules_mode_info_description)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun initializeImportExportLaunchers() {
        importFileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val uri = result.data?.data
                if (uri != null) {
                    importRulesFromUri(uri)
                }
            }
        }

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            val pendingRules = pendingRulesImport ?: return@registerForActivityResult
            val requiredTypes = RuleConfigManager.getRequiredPermissionTypes(pendingRules)
            val allowBluetooth = !requiredTypes.contains(RulePermissionType.BLUETOOTH) || hasBluetoothPermissions()
            val allowWifi = !requiredTypes.contains(RulePermissionType.WIFI) || hasWifiPermissions()
            val filteredRules = RuleConfigManager.filterRulesByPermissions(pendingRules, allowBluetooth, allowWifi)
            val skippedCount = pendingRules.size - filteredRules.size
            pendingRulesImport = null
            applyImportedRules(filteredRules, skippedCount)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_rules, menu)
        tintMenuIcons(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val showRulesActions = currentMode == AutomationMode.CONDITIONAL_RULES
        menu.findItem(R.id.action_export_rules)?.isVisible = showRulesActions
        menu.findItem(R.id.action_import_rules)?.isVisible = showRulesActions
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_rules -> {
                exportRulesConfig()
                true
            }
            R.id.action_import_rules -> {
                importRulesConfigDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun tintMenuIcons(menu: Menu) {
        val iconColor = if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_NO) {
            getColor(android.R.color.black)
        } else {
            getColor(android.R.color.white)
        }
        val tint = ColorStateList.valueOf(iconColor)
        listOf(
            menu.findItem(R.id.action_export_rules),
            menu.findItem(R.id.action_import_rules)
        ).forEach { menuItem ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                menuItem?.iconTintList = tint
            }
        }
    }

    private fun exportRulesConfig() {
        try {
            val rulesData = RuleConfigManager.exportRules(this)
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val filename = getString(R.string.rules_export_filename_format, timestamp)
            val exportFile = FileExportHelper.createExportFile(this, "exports", filename, rulesData)

            if (exportFile != null) {
                val fileUri = FileProvider.getUriForFile(
                    this,
                    "com.micoyc.speakthat.fileprovider",
                    exportFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = getString(R.string.rules_export_mime_type)
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.rules_export_share_subject))
                    putExtra(
                        Intent.EXTRA_TEXT,
                        getString(
                            R.string.rules_export_share_text,
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                        )
                    )
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(Intent.createChooser(shareIntent, getString(R.string.rules_export_chooser_title)))

                val summary = buildRulesSummary(ruleManager.getAllRules())
                AlertDialog.Builder(this)
                    .setTitle(R.string.rules_export_success_title)
                    .setMessage(getString(R.string.rules_export_success_message, exportFile.absolutePath, summary))
                    .setPositiveButton(R.string.button_ok, null)
                    .show()

                InAppLogger.log("RuleConfig", "Rules exported to $filename")
            } else {
                val textShareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = getString(R.string.rules_export_text_mime_type)
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.rules_export_share_subject))
                    putExtra(Intent.EXTRA_TEXT, rulesData)
                }
                startActivity(Intent.createChooser(textShareIntent, getString(R.string.rules_export_text_chooser_title)))
                Toast.makeText(this, getString(R.string.rules_export_text_fallback), Toast.LENGTH_LONG).show()
                InAppLogger.log("RuleConfig", "Rules exported as text fallback")
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.rules_export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            InAppLogger.logError("RuleConfig", "Rules export failed: ${e.message}")
        }
    }

    private fun importRulesConfigDialog() {
        val summary = buildRulesSummary(ruleManager.getAllRules())
        AlertDialog.Builder(this)
            .setTitle(R.string.rules_import_dialog_title)
            .setMessage(getString(R.string.rules_import_dialog_message, summary))
            .setPositiveButton(R.string.button_select_file) { _, _ -> openRulesFilePicker() }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun openRulesFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(getString(R.string.rules_export_mime_type), getString(R.string.rules_export_text_mime_type), "*/*"))
        }
        importFileLauncher.launch(Intent.createChooser(intent, getString(R.string.rules_import_file_picker_title)))
    }

    private fun importRulesFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val content = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        content.append(line)
                    }
                    importRulesFromJson(content.toString())
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.rules_import_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            InAppLogger.logError("RuleConfig", "Rules import file read failed: ${e.message}")
        }
    }

    private fun importRulesFromJson(jsonData: String) {
        try {
            val rules = RuleConfigManager.extractRulesFromRulesConfig(jsonData)
            if (rules.isEmpty()) {
                showErrorDialog(getString(R.string.rules_import_empty))
                return
            }
            handleRulesImportWithPermissions(rules)
        } catch (e: Exception) {
            showErrorDialog(getString(R.string.rules_import_invalid, e.message ?: ""))
        }
    }

    private fun handleRulesImportWithPermissions(rules: List<Rule>) {
        val requiredTypes = RuleConfigManager.getRequiredPermissionTypes(rules)
        val missingPermissions = mutableListOf<String>()

        if (requiredTypes.contains(RulePermissionType.BLUETOOTH) && !hasBluetoothPermissions()) {
            missingPermissions.addAll(getBluetoothPermissions())
        }
        if (requiredTypes.contains(RulePermissionType.WIFI) && !hasWifiPermissions()) {
            missingPermissions.addAll(getWifiPermissions())
        }

        if (missingPermissions.isNotEmpty()) {
            pendingRulesImport = rules
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            applyImportedRules(rules, 0)
        }
    }

    private fun applyImportedRules(rules: List<Rule>, skippedCount: Int) {
        val result = RuleConfigManager.importRules(this, rules, skippedCount)
        if (result.success) {
            val summary = buildRulesSummary(rules)
            val message = if (skippedCount > 0) {
                getString(R.string.rules_import_success_with_skips, rules.size, skippedCount, summary)
            } else {
                getString(R.string.rules_import_success, rules.size, summary)
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.rules_import_success_title)
                .setMessage(message)
                .setPositiveButton(R.string.button_ok, null)
                .show()
            updateUI(currentMode)
        } else {
            showErrorDialog(getString(R.string.rules_import_failed, result.message))
        }
    }

    private fun buildRulesSummary(rules: List<Rule>): String {
        val enabledCount = rules.count { it.enabled }
        val disabledCount = rules.size - enabledCount
        return getString(R.string.rules_summary_format, rules.size, enabledCount, disabledCount)
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getBluetoothPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            listOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    private fun hasWifiPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getWifiPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                android.Manifest.permission.NEARBY_WIFI_DEVICES,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
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
    }
    
    override fun onSupportNavigateUp(): Boolean {
        @Suppress("DEPRECATION")
        onBackPressed()
        return true
    }
} 