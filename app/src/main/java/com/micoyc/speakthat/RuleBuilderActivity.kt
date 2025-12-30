package com.micoyc.speakthat

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.micoyc.speakthat.databinding.ActivityRuleBuilderBinding
import com.micoyc.speakthat.automation.AutomationMode
import com.micoyc.speakthat.automation.AutomationModeManager
import com.micoyc.speakthat.rules.RuleManager
import com.micoyc.speakthat.rules.Rule
import com.micoyc.speakthat.rules.Trigger
import com.micoyc.speakthat.rules.Action
import com.micoyc.speakthat.rules.Exception
import com.micoyc.speakthat.rules.TriggerType
import com.micoyc.speakthat.rules.ActionType
import com.micoyc.speakthat.rules.ExceptionType
import com.micoyc.speakthat.rules.LogicGate
import com.micoyc.speakthat.rules.TriggerAdapter
import com.micoyc.speakthat.rules.ActionAdapter
import com.micoyc.speakthat.rules.ExceptionAdapter
import com.micoyc.speakthat.rules.TriggerConfigActivity
import com.micoyc.speakthat.rules.ActionConfigActivity
import com.micoyc.speakthat.rules.ExceptionConfigActivity

class RuleBuilderActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRuleBuilderBinding
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private lateinit var ruleManager: RuleManager
    
    // Current rule being built
    private var currentRule: Rule? = null
    private var isEditing = false
    
    // Adapters for RecyclerViews
    private lateinit var triggerAdapter: TriggerAdapter
    private lateinit var actionAdapter: ActionAdapter
    private lateinit var exceptionAdapter: ExceptionAdapter
    private var pendingWifiAction: PendingWifiAction? = null
    private var pendingExistingTrigger: Trigger? = null
    private var pendingExistingException: Exception? = null
    
    companion object {
        private const val PREFS_NAME = "SpeakThatPrefs"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val REQUEST_WIFI_PERMISSIONS = 3001
    }
    
    // Activity Result launchers
    private val triggerConfigLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { intent ->
                val triggerJson = intent.getStringExtra(TriggerConfigActivity.RESULT_TRIGGER)
                if (triggerJson != null) {
                    val trigger = Trigger.fromJson(triggerJson)
                    if (trigger != null) {
                        // Check if we're editing an existing trigger
                        val isEditing = intent.getBooleanExtra(TriggerConfigActivity.EXTRA_IS_EDITING, false)
                        if (isEditing) {
                            // Find and update the existing trigger
                            val existingTriggers = triggerAdapter.getTriggers().toMutableList()
                            val index = existingTriggers.indexOfFirst { it.id == trigger.id }
                            if (index != -1) {
                                existingTriggers[index] = trigger
                                triggerAdapter.updateTriggers(existingTriggers)
                            }
                        } else {
                            // Add new trigger
                            triggerAdapter.addTrigger(trigger)
                        }
                        InAppLogger.logUserAction("Trigger configured: ${trigger.getLogMessage()}", "RuleBuilderActivity")
                    }
                }
            }
        }
    }
    
    private val actionConfigLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { intent ->
                val actionJson = intent.getStringExtra(ActionConfigActivity.RESULT_ACTION)
                if (actionJson != null) {
                    val action = Action.fromJson(actionJson)
                    if (action != null) {
                        // Check if we're editing an existing action
                        val isEditing = intent.getBooleanExtra(ActionConfigActivity.EXTRA_IS_EDITING, false)
                        if (isEditing) {
                            // Find and update the existing action
                            val existingActions = actionAdapter.getActions().toMutableList()
                            val index = existingActions.indexOfFirst { it.id == action.id }
                            if (index != -1) {
                                existingActions[index] = action
                                actionAdapter.updateActions(existingActions)
                            }
                        } else {
                            // Add new action
                            actionAdapter.addAction(action)
                        }
                        InAppLogger.logUserAction("Action configured: ${action.getLogMessage()}", "RuleBuilderActivity")
                    }
                }
            }
        }
    }
    
    private val exceptionConfigLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { intent ->
                val exceptionJson = intent.getStringExtra(ExceptionConfigActivity.RESULT_EXCEPTION)
                if (exceptionJson != null) {
                    val exception = Exception.fromJson(exceptionJson)
                    if (exception != null) {
                        // Check if we're editing an existing exception
                        val isEditing = intent.getBooleanExtra(ExceptionConfigActivity.EXTRA_IS_EDITING, false)
                        if (isEditing) {
                            // Find and update the existing exception
                            val existingExceptions = exceptionAdapter.getExceptions().toMutableList()
                            val index = existingExceptions.indexOfFirst { it.id == exception.id }
                            if (index != -1) {
                                existingExceptions[index] = exception
                                exceptionAdapter.updateExceptions(existingExceptions)
                            }
                        } else {
                            // Add new exception
                            exceptionAdapter.addException(exception)
                        }
                        InAppLogger.logUserAction("Exception configured: ${exception.getLogMessage()}", "RuleBuilderActivity")
                    }
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme FIRST before anything else
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        applySavedTheme()
        
        super.onCreate(savedInstanceState)
        binding = ActivityRuleBuilderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize rule manager
        ruleManager = RuleManager(this)
        
        // Check if we're editing an existing rule
        val editRuleId = intent.getStringExtra("EDIT_RULE_ID")
        val editRuleData = intent.getStringExtra("EDIT_RULE_DATA")
        
        if (editRuleId != null && editRuleData != null) {
            // We're editing an existing rule
            isEditing = true
            currentRule = Rule.fromJson(editRuleData)
            supportActionBar?.title = getString(R.string.dialog_title_edit_rule)
        } else {
            // We're creating a new rule
            isEditing = false
            currentRule = null
            supportActionBar?.title = getString(R.string.dialog_title_create_rule)
        }
        
        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        setupRecyclerViews()
        setupUI()
        
        // Load existing rule data if editing
        if (isEditing && currentRule != null) {
            loadExistingRule()
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
    
    private fun setupUI() {
        setupAddButtons()
        setupLogicGates()
        setupSaveButton()
    }
    
    private fun setupRecyclerViews() {
        // Set up trigger RecyclerView
        triggerAdapter = TriggerAdapter(
            mutableListOf(),
            onEditTrigger = { trigger -> editTrigger(trigger) },
            onRemoveTrigger = { trigger -> removeTrigger(trigger) },
            onTriggersChanged = { updateLogicGateVisibility() }
        )
        binding.recyclerTriggers.apply {
            layoutManager = LinearLayoutManager(this@RuleBuilderActivity)
            adapter = triggerAdapter
        }
        
        // Set up action RecyclerView
        actionAdapter = ActionAdapter(
            mutableListOf(),
            onEditAction = { action -> editAction(action) },
            onRemoveAction = { action -> removeAction(action) }
        )
        binding.recyclerActions.apply {
            layoutManager = LinearLayoutManager(this@RuleBuilderActivity)
            adapter = actionAdapter
        }
        
        // Set up exception RecyclerView
        exceptionAdapter = ExceptionAdapter(
            mutableListOf(),
            onEditException = { exception -> editException(exception) },
            onRemoveException = { exception -> removeException(exception) },
            onExceptionsChanged = { updateLogicGateVisibility() }
        )
        binding.recyclerExceptions.apply {
            layoutManager = LinearLayoutManager(this@RuleBuilderActivity)
            adapter = exceptionAdapter
        }
        
        InAppLogger.logDebug("RuleBuilderActivity", "RecyclerViews initialized")
    }
    
    private fun setupAddButtons() {
        binding.btnAddTrigger.setOnClickListener {
            showTriggerMenu()
        }
        
        binding.btnAddAction.setOnClickListener {
            showActionMenu()
        }
        
        binding.btnAddException.setOnClickListener {
            showExceptionMenu()
        }
    }
    
    private fun setupLogicGates() {
        // Set up trigger logic gate toggle group
        binding.toggleTriggerLogic.check(binding.btnTriggerAnd.id) // Default to AND
        
        // Set up exception logic gate toggle group
        binding.toggleExceptionLogic.check(binding.btnExceptionAnd.id) // Default to AND
        
        // Update initial visibility
        updateLogicGateVisibility()
    }
    
    private fun updateLogicGateVisibility() {
        // Show trigger logic gate if there are multiple triggers
        val triggerCount = triggerAdapter.getTriggers().size
        binding.layoutTriggerLogic.visibility = if (triggerCount > 1) View.VISIBLE else View.GONE
        
        // Show exception logic gate if there are multiple exceptions
        val exceptionCount = exceptionAdapter.getExceptions().size
        binding.layoutExceptionLogic.visibility = if (exceptionCount > 1) View.VISIBLE else View.GONE
        
        InAppLogger.logDebug("RuleBuilderActivity", "Logic gate visibility updated - Triggers: $triggerCount, Exceptions: $exceptionCount")
    }
    
    private fun setupSaveButton() {
        binding.btnSaveRule.setOnClickListener {
            saveRule()
        }
    }
    
    private fun saveRule() {
        InAppLogger.logDebug("RuleBuilderActivity", "=== SAVE RULE STARTED ===")
        InAppLogger.logDebug("RuleBuilderActivity", "isEditing: $isEditing, currentRule: ${currentRule?.name}")
        
        val ruleName = binding.editRuleName.text.toString().trim()
        InAppLogger.logDebug("RuleBuilderActivity", "Rule name: '$ruleName'")
        
        if (ruleName.isEmpty()) {
            InAppLogger.logDebug("RuleBuilderActivity", "Rule name is empty, showing error dialog")
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_invalid_rule_name))
                .setMessage(getString(R.string.dialog_message_invalid_rule_name))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
            return
        }
        
        // Get logic gates from UI
        val triggerLogic = when (binding.toggleTriggerLogic.checkedButtonId) {
            binding.btnTriggerAnd.id -> LogicGate.AND
            binding.btnTriggerOr.id -> LogicGate.OR
            binding.btnTriggerXor.id -> LogicGate.XOR
            else -> LogicGate.AND
        }
        
        val exceptionLogic = when (binding.toggleExceptionLogic.checkedButtonId) {
            binding.btnExceptionAnd.id -> LogicGate.AND
            binding.btnExceptionOr.id -> LogicGate.OR
            binding.btnExceptionXor.id -> LogicGate.XOR
            else -> LogicGate.AND
        }
        
        // Create rule with actual data from adapters
        InAppLogger.logDebug("RuleBuilderActivity", "Creating rule object...")
        InAppLogger.logDebug("RuleBuilderActivity", "Triggers: ${triggerAdapter.getTriggers().size}")
        InAppLogger.logDebug("RuleBuilderActivity", "Actions: ${actionAdapter.getActions().size}")
        InAppLogger.logDebug("RuleBuilderActivity", "Exceptions: ${exceptionAdapter.getExceptions().size}")
        
        val ruleToSave = if (isEditing && currentRule != null) {
            // Update existing rule
            InAppLogger.logDebug("RuleBuilderActivity", "Creating updated rule from existing rule")
            val updatedRule = currentRule!!.copy(
                name = ruleName,
                triggers = triggerAdapter.getTriggers(),
                actions = actionAdapter.getActions(),
                exceptions = exceptionAdapter.getExceptions(),
                triggerLogic = triggerLogic,
                exceptionLogic = exceptionLogic,
                modifiedAt = System.currentTimeMillis()
            )
            InAppLogger.logDebug("RuleBuilderActivity", "Editing existing rule - Original ID: ${currentRule!!.id}, Updated ID: ${updatedRule.id}")
            updatedRule
        } else {
            // Create new rule
            InAppLogger.logDebug("RuleBuilderActivity", "Creating new rule")
            Rule(
                name = ruleName,
                enabled = true,
                triggers = triggerAdapter.getTriggers(),
                actions = actionAdapter.getActions(),
                exceptions = exceptionAdapter.getExceptions(),
                triggerLogic = triggerLogic,
                exceptionLogic = exceptionLogic
            )
        }
        
        InAppLogger.logDebug("RuleBuilderActivity", "Saving rule: ${ruleToSave.getLogMessage()}")
        
        // Validate the rule
        InAppLogger.logDebug("RuleBuilderActivity", "Validating rule...")
        val validation = ruleManager.validateRule(ruleToSave)
        InAppLogger.logDebug("RuleBuilderActivity", "Validation result: ${validation.isValid}, Errors: ${validation.errors}")
        
        if (!validation.isValid) {
            InAppLogger.logDebug("RuleBuilderActivity", "Rule validation failed, showing error dialog")
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_invalid_rule))
                .setMessage(getString(R.string.dialog_message_invalid_rule, validation.errors.joinToString("\n")))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
            return
        }
        
        // Save or update the rule
        InAppLogger.logDebug("RuleBuilderActivity", "About to ${if (isEditing) "update" else "add"} rule...")
        val success = if (isEditing) {
            InAppLogger.logDebug("RuleBuilderActivity", "Calling ruleManager.updateRule()")
            ruleManager.updateRule(ruleToSave)
        } else {
            InAppLogger.logDebug("RuleBuilderActivity", "Calling ruleManager.addRule()")
            val addSuccess = ruleManager.addRule(ruleToSave)
            
            // Enable Conditional Rules if it's not already enabled and rule was added successfully
            if (addSuccess && !ruleManager.isRulesEnabled()) {
                AutomationModeManager(this).setMode(AutomationMode.CONDITIONAL_RULES)
                InAppLogger.logUserAction("Enabled Conditional Rules feature")
            }
            
            addSuccess
        }
        
        InAppLogger.logDebug("RuleBuilderActivity", "Save/update result: $success")
        
        if (success) {
            InAppLogger.logUserAction("Rule ${if (isEditing) "updated" else "saved"}: $ruleName")
            InAppLogger.logDebug("RuleBuilderActivity", "Rule saved successfully, finishing activity")
            finish()
        } else {
            InAppLogger.logDebug("RuleBuilderActivity", "Rule save failed, showing error dialog")
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_error))
                .setMessage(getString(R.string.dialog_message_save_rule_failed, if (isEditing) getString(R.string.update_rule) else getString(R.string.save_rule)))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        }
        
        InAppLogger.logDebug("RuleBuilderActivity", "=== SAVE RULE COMPLETED ===")
    }
    
    private fun showTriggerMenu() {
        val triggerOptions = arrayOf(
            "Bluetooth Device Connected",
            "Screen State (On/Off)",
            "Time Schedule",
            "WiFi Network Connected"
        )
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_add_trigger))
            .setItems(triggerOptions) { _, which ->
                when (which) {
                    0 -> addBluetoothTrigger()
                    1 -> addScreenStateTrigger()
                    2 -> addTimeScheduleTrigger()
                    3 -> addWifiNetworkTrigger()
                }
            }
            .show()
    }
    
    private fun showActionMenu() {
        val actionOptions = arrayOf(
            "Disable SpeakThat",
            "Enable Specific App Filter",
            "Disable Specific App Filter",
            "Change Voice Settings"
        )
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_add_action))
            .setItems(actionOptions) { _, which ->
                when (which) {
                    0 -> addDisableSpeakThatAction()
                    1 -> addEnableAppFilterAction()
                    2 -> addDisableAppFilterAction()
                    3 -> addChangeVoiceSettingsAction()
                }
            }
            .show()
    }
    
    private fun showExceptionMenu() {
        val exceptionOptions = arrayOf(
            "Bluetooth Device Connected",
            "Screen State (On/Off)",
            "Time Schedule",
            "WiFi Network Connected"
        )
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_add_exception))
            .setItems(exceptionOptions) { _, which ->
                when (which) {
                    0 -> addBluetoothException()
                    1 -> addScreenStateException()
                    2 -> addTimeScheduleException()
                    3 -> addWifiNetworkException()
                }
            }
            .show()
    }
    
    // Trigger methods
    private fun addBluetoothTrigger() {
        launchTriggerConfig(TriggerType.BLUETOOTH_DEVICE, null)
    }
    
    private fun addScreenStateTrigger() {
        launchTriggerConfig(TriggerType.SCREEN_STATE, null)
    }
    
    private fun addTimeScheduleTrigger() {
        launchTriggerConfig(TriggerType.TIME_SCHEDULE, null)
    }
    
    private fun addWifiNetworkTrigger() {
        launchTriggerConfig(TriggerType.WIFI_NETWORK, null)
    }
    
    private fun launchTriggerConfig(triggerType: TriggerType, existingTrigger: Trigger?) {
        if (triggerType == TriggerType.WIFI_NETWORK && !hasWifiPermissions()) {
            pendingWifiAction = if (existingTrigger == null) PendingWifiAction.ADD_TRIGGER else PendingWifiAction.EDIT_TRIGGER
            pendingExistingTrigger = existingTrigger
            requestWifiPermissions()
            return
        }

        val intent = android.content.Intent(this, TriggerConfigActivity::class.java).apply {
            putExtra(TriggerConfigActivity.EXTRA_TRIGGER_TYPE, triggerType)
            putExtra(TriggerConfigActivity.EXTRA_IS_EDITING, existingTrigger != null)
            if (existingTrigger != null) {
                putExtra(TriggerConfigActivity.EXTRA_TRIGGER_DATA, existingTrigger.toJson())
            }
        }
        
        triggerConfigLauncher.launch(intent)
    }
    
    // Action methods
    private fun addDisableSpeakThatAction() {
        launchActionConfig(ActionType.DISABLE_SPEAKTHAT, null)
    }
    
    private fun addEnableAppFilterAction() {
        launchActionConfig(ActionType.ENABLE_APP_FILTER, null)
    }
    
    private fun addDisableAppFilterAction() {
        launchActionConfig(ActionType.DISABLE_APP_FILTER, null)
    }
    
    private fun addChangeVoiceSettingsAction() {
        launchActionConfig(ActionType.CHANGE_VOICE_SETTINGS, null)
    }
    
    private fun launchActionConfig(actionType: ActionType, existingAction: Action?) {
        val intent = android.content.Intent(this, ActionConfigActivity::class.java).apply {
            putExtra(ActionConfigActivity.EXTRA_ACTION_TYPE, actionType)
            putExtra(ActionConfigActivity.EXTRA_IS_EDITING, existingAction != null)
            if (existingAction != null) {
                putExtra(ActionConfigActivity.EXTRA_ACTION_DATA, existingAction.toJson())
            }
        }
        
        actionConfigLauncher.launch(intent)
    }

    private fun launchExceptionConfig(exceptionType: ExceptionType, existingException: Exception?) {
        if (exceptionType == ExceptionType.WIFI_NETWORK && !hasWifiPermissions()) {
            pendingWifiAction = if (existingException == null) PendingWifiAction.ADD_EXCEPTION else PendingWifiAction.EDIT_EXCEPTION
            pendingExistingException = existingException
            requestWifiPermissions()
            return
        }

        val intent = android.content.Intent(this, ExceptionConfigActivity::class.java).apply {
            putExtra(ExceptionConfigActivity.EXTRA_EXCEPTION_TYPE, exceptionType)
            putExtra(ExceptionConfigActivity.EXTRA_IS_EDITING, existingException != null)
            if (existingException != null) {
                putExtra(ExceptionConfigActivity.EXTRA_EXCEPTION_DATA, existingException.toJson())
            }
        }
        
        exceptionConfigLauncher.launch(intent)
    }
    
    // Exception methods
    private fun addBluetoothException() {
        // Create a Bluetooth exception (any device connected)
        val exception = Exception(
            type = ExceptionType.BLUETOOTH_DEVICE,
            data = mapOf("device_addresses" to emptySet<String>()),
            description = "Any Bluetooth device connected"
        )
        
        exceptionAdapter.addException(exception)
        InAppLogger.logUserAction("Added Bluetooth exception", "RuleBuilderActivity")
    }
    
    private fun addScreenStateException() {
        launchExceptionConfig(ExceptionType.SCREEN_STATE, null)
    }
    
    private fun addTimeScheduleException() {
        launchExceptionConfig(ExceptionType.TIME_SCHEDULE, null)
    }
    
    private fun addWifiNetworkException() {
        launchExceptionConfig(ExceptionType.WIFI_NETWORK, null)
    }
    
    // Edit and remove methods for triggers, actions, and exceptions
    private fun editTrigger(trigger: Trigger) {
        launchTriggerConfig(trigger.type, trigger)
    }
    
    private fun removeTrigger(trigger: Trigger) {
        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.dialog_title_remove_trigger))
                .setMessage(getString(R.string.dialog_message_remove_trigger))
                .setPositiveButton(getString(R.string.button_remove)) { _, _ ->
                triggerAdapter.removeTrigger(trigger)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun editAction(action: Action) {
        launchActionConfig(action.type, action)
    }
    
    private fun removeAction(action: Action) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_remove_action))
            .setMessage(getString(R.string.dialog_message_remove_action))
            .setPositiveButton(getString(R.string.button_remove)) { _, _ ->
                actionAdapter.removeAction(action)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun editException(exception: Exception) {
        launchExceptionConfig(exception.type, exception)
    }
    
    private fun removeException(exception: Exception) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_remove_exception))
            .setMessage(getString(R.string.dialog_message_remove_exception))
            .setPositiveButton(getString(R.string.button_remove)) { _, _ ->
                exceptionAdapter.removeException(exception)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showNotImplementedDialog(feature: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_title_coming_soon))
            .setMessage(getString(R.string.dialog_message_coming_soon, feature))
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }
    

    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun hasWifiPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.NEARBY_WIFI_DEVICES) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestWifiPermissions() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.NEARBY_WIFI_DEVICES,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        requestPermissions(permissions, REQUEST_WIFI_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_WIFI_PERMISSIONS) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                when (pendingWifiAction) {
                    PendingWifiAction.ADD_TRIGGER -> launchTriggerConfig(TriggerType.WIFI_NETWORK, null)
                    PendingWifiAction.EDIT_TRIGGER -> launchTriggerConfig(TriggerType.WIFI_NETWORK, pendingExistingTrigger)
                    PendingWifiAction.ADD_EXCEPTION -> launchExceptionConfig(ExceptionType.WIFI_NETWORK, null)
                    PendingWifiAction.EDIT_EXCEPTION -> launchExceptionConfig(ExceptionType.WIFI_NETWORK, pendingExistingException)
                    null -> { /* no-op */ }
                }
            } else {
                InAppLogger.logFilter("WiFi permissions denied; cannot configure WiFi rules.")
            }
            pendingWifiAction = null
            pendingExistingTrigger = null
            pendingExistingException = null
        }
    }

    private enum class PendingWifiAction {
        ADD_TRIGGER,
        EDIT_TRIGGER,
        ADD_EXCEPTION,
        EDIT_EXCEPTION
    }

    private fun loadExistingRule() {
        currentRule?.let { rule ->
            // Set rule name
            binding.editRuleName.setText(rule.name)
            
            // Load triggers
            triggerAdapter.updateTriggers(rule.triggers.toMutableList())
            
            // Load actions
            actionAdapter.updateActions(rule.actions.toMutableList())
            
            // Load exceptions
            exceptionAdapter.updateExceptions(rule.exceptions.toMutableList())
            
            // Set logic gates
            when (rule.triggerLogic) {
                LogicGate.AND -> binding.toggleTriggerLogic.check(binding.btnTriggerAnd.id)
                LogicGate.OR -> binding.toggleTriggerLogic.check(binding.btnTriggerOr.id)
                LogicGate.XOR -> binding.toggleTriggerLogic.check(binding.btnTriggerXor.id)
            }
            
            when (rule.exceptionLogic) {
                LogicGate.AND -> binding.toggleExceptionLogic.check(binding.btnExceptionAnd.id)
                LogicGate.OR -> binding.toggleExceptionLogic.check(binding.btnExceptionOr.id)
                LogicGate.XOR -> binding.toggleExceptionLogic.check(binding.btnExceptionXor.id)
            }
            
            // Update logic gate visibility
            updateLogicGateVisibility()
            
            InAppLogger.logDebug("RuleBuilderActivity", "Loaded existing rule: ${rule.name}")
        }
    }
} 