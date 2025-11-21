package com.micoyc.speakthat.rules

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.databinding.ActivityActionConfigBinding
import com.google.gson.Gson

class ActionConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActionConfigBinding
    private var actionType: ActionType? = null
    private var originalAction: Action? = null
    private var isEditing = false

    companion object {
        const val EXTRA_ACTION_TYPE = "action_type"
        const val EXTRA_ACTION_DATA = "action_data"
        const val EXTRA_IS_EDITING = "is_editing"
        const val RESULT_ACTION = "result_action"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActionConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply saved theme
        applySavedTheme()

        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Configure Action"

        // Get intent data
        actionType = intent.getSerializableExtra(EXTRA_ACTION_TYPE) as? ActionType
        isEditing = intent.getBooleanExtra(EXTRA_IS_EDITING, false)

        if (isEditing) {
            val actionData = intent.getStringExtra(EXTRA_ACTION_DATA)
            originalAction = Action.fromJson(actionData ?: "")
        }

        setupUI()
        loadCurrentValues()
    }

    private fun applySavedTheme() {
        val sharedPreferences = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun setupUI() {
        when (actionType) {
            ActionType.DISABLE_SPEAKTHAT -> setupDisableSpeakThatUI()
            ActionType.ENABLE_APP_FILTER -> setupEnableAppFilterUI()
            ActionType.DISABLE_APP_FILTER -> setupDisableAppFilterUI()
            ActionType.CHANGE_VOICE_SETTINGS -> setupChangeVoiceSettingsUI()
            else -> {
                InAppLogger.logError("ActionConfigActivity", "Unknown action type: $actionType")
                finish()
            }
        }

        // Set up save button
        binding.btnSave.setOnClickListener {
            saveAction()
        }

        // Set up cancel button
        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun setupDisableSpeakThatUI() {
        binding.cardDisableSpeakThat.visibility = View.VISIBLE
        
        // This action doesn't need additional configuration
        binding.textDisableSpeakThatInfo.text = "This action will disable SpeakThat completely when the rule is triggered."
    }

    private fun setupEnableAppFilterUI() {
        binding.cardEnableAppFilter.visibility = View.VISIBLE
        
        // Set up app selection
        binding.btnSelectApp.setOnClickListener {
            showAppSelection()
        }
    }

    private fun setupDisableAppFilterUI() {
        binding.cardDisableAppFilter.visibility = View.VISIBLE
        
        // Set up app selection
        binding.btnSelectAppDisable.setOnClickListener {
            showAppSelection()
        }
    }

    private fun setupChangeVoiceSettingsUI() {
        binding.cardChangeVoiceSettings.visibility = View.VISIBLE
        
        // Set up voice settings options
        setupVoiceSettingsOptions()
    }

    private fun setupVoiceSettingsOptions() {
        // Speech rate options
        val speechRateOptions = arrayOf("Very Slow", "Slow", "Normal", "Fast", "Very Fast")
        val speechRateValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f)
        
        val speechRateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, speechRateOptions)
        speechRateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSpeechRate.adapter = speechRateAdapter
        
        // Pitch options
        val pitchOptions = arrayOf("Very Low", "Low", "Normal", "High", "Very High")
        val pitchValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f)
        
        val pitchAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, pitchOptions)
        pitchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPitch.adapter = pitchAdapter
        
        // Store values for later use
        binding.spinnerSpeechRate.tag = speechRateValues
        binding.spinnerPitch.tag = pitchValues
    }

    private fun showAppSelection() {
        // For now, show a simple dialog to enter app package name
        // In a full implementation, this would show a list of installed apps
        AlertDialog.Builder(this)
            .setTitle("Select App")
            .setMessage("Enter the app package name (e.g., com.whatsapp):")
            .setView(android.widget.EditText(this).apply {
                hint = "com.whatsapp"
            })
            .setPositiveButton("OK") { dialog, _ ->
                val editText = (dialog as AlertDialog).findViewById<android.widget.EditText>(android.R.id.text1)
                val packageName = editText?.text?.toString()?.trim()
                if (!packageName.isNullOrEmpty()) {
                    binding.editAppPackage.setText(packageName)
                    binding.editAppPackageDisable.setText(packageName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadCurrentValues() {
        originalAction?.let { action ->
            when (action.type) {
                ActionType.ENABLE_APP_FILTER -> {
                    val appPackage = action.data["app_package"] as? String
                    if (!appPackage.isNullOrEmpty()) {
                        binding.editAppPackage.setText(appPackage)
                    }
                }
                
                ActionType.DISABLE_APP_FILTER -> {
                    val appPackage = action.data["app_package"] as? String
                    if (!appPackage.isNullOrEmpty()) {
                        binding.editAppPackageDisable.setText(appPackage)
                    }
                }
                
                ActionType.CHANGE_VOICE_SETTINGS -> {
                    val voiceSettingsData = action.data["voice_settings"]
                    if (voiceSettingsData is Map<*, *>) {
                        val speechRate = (voiceSettingsData["speech_rate"] as? Number)?.toFloat() ?: 1.0f
                        val pitch = (voiceSettingsData["pitch"] as? Number)?.toFloat() ?: 1.0f
                        
                        val speechRateValues = binding.spinnerSpeechRate.tag as? FloatArray
                        val speechRateIndex = speechRateValues?.indexOfFirst { it == speechRate }?.takeIf { it >= 0 } ?: 2
                        binding.spinnerSpeechRate.setSelection(speechRateIndex)
                        
                        val pitchValues = binding.spinnerPitch.tag as? FloatArray
                        val pitchIndex = pitchValues?.indexOfFirst { it == pitch }?.takeIf { it >= 0 } ?: 2
                        binding.spinnerPitch.setSelection(pitchIndex)
                    }
                }
                
                else -> {
                    // No additional configuration needed
                }
            }
        }
    }

    private fun saveAction() {
        val action = when (actionType) {
            ActionType.DISABLE_SPEAKTHAT -> createDisableSpeakThatAction()
            ActionType.ENABLE_APP_FILTER -> createEnableAppFilterAction()
            ActionType.DISABLE_APP_FILTER -> createDisableAppFilterAction()
            ActionType.CHANGE_VOICE_SETTINGS -> createChangeVoiceSettingsAction()
            else -> return
        }

        // Create a new intent for the result to avoid modifying the original intent
        val resultIntent = android.content.Intent().apply {
            putExtra(RESULT_ACTION, action.toJson())
            putExtra(EXTRA_IS_EDITING, isEditing)
        }
        setResult(RESULT_OK, resultIntent)

        InAppLogger.logUserAction("Action configured: ${action.getLogMessage()}", "ActionConfigActivity")
        finish()
    }

    private fun createDisableSpeakThatAction(): Action {
        return if (isEditing && originalAction != null) {
            // Preserve the original action ID when editing
            originalAction!!.copy(
                description = "Disable SpeakThat"
            )
        } else {
            // Create new action
            Action(
                type = ActionType.DISABLE_SPEAKTHAT,
                description = "Disable SpeakThat"
            )
        }
    }

    private fun createEnableAppFilterAction(): Action {
        val appPackage = binding.editAppPackage.text.toString().trim()
        
        if (appPackage.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Invalid App Package")
                .setMessage("Please enter an app package name.")
                .setPositiveButton("OK", null)
                .show()
            return Action(type = ActionType.ENABLE_APP_FILTER, description = "Enable App Filter")
        }
        
        return if (isEditing && originalAction != null) {
            // Preserve the original action ID when editing
            originalAction!!.copy(
                data = mapOf("app_package" to appPackage),
                description = "Enable filter for $appPackage"
            )
        } else {
            // Create new action
            Action(
                type = ActionType.ENABLE_APP_FILTER,
                data = mapOf("app_package" to appPackage),
                description = "Enable filter for $appPackage"
            )
        }
    }

    private fun createDisableAppFilterAction(): Action {
        val appPackage = binding.editAppPackageDisable.text.toString().trim()
        
        if (appPackage.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Invalid App Package")
                .setMessage("Please enter an app package name.")
                .setPositiveButton("OK", null)
                .show()
            return Action(type = ActionType.DISABLE_APP_FILTER, description = "Disable App Filter")
        }
        
        return if (isEditing && originalAction != null) {
            // Preserve the original action ID when editing
            originalAction!!.copy(
                data = mapOf("app_package" to appPackage),
                description = "Disable filter for $appPackage"
            )
        } else {
            // Create new action
            Action(
                type = ActionType.DISABLE_APP_FILTER,
                data = mapOf("app_package" to appPackage),
                description = "Disable filter for $appPackage"
            )
        }
    }

    private fun createChangeVoiceSettingsAction(): Action {
        val speechRateValues = binding.spinnerSpeechRate.tag as? FloatArray ?: floatArrayOf(1.0f)
        val pitchValues = binding.spinnerPitch.tag as? FloatArray ?: floatArrayOf(1.0f)
        
        val speechRate = speechRateValues.getOrElse(binding.spinnerSpeechRate.selectedItemPosition) { 1.0f }
        val pitch = pitchValues.getOrElse(binding.spinnerPitch.selectedItemPosition) { 1.0f }
        
        val voiceSettings = mapOf(
            "speech_rate" to speechRate,
            "pitch" to pitch
        )
        
        return if (isEditing && originalAction != null) {
            // Preserve the original action ID when editing
            originalAction!!.copy(
                data = mapOf("voice_settings" to voiceSettings),
                description = "Change voice settings (Rate: $speechRate, Pitch: $pitch)"
            )
        } else {
            // Create new action
            Action(
                type = ActionType.CHANGE_VOICE_SETTINGS,
                data = mapOf("voice_settings" to voiceSettings),
                description = "Change voice settings (Rate: $speechRate, Pitch: $pitch)"
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        @Suppress("DEPRECATION")
        onBackPressed()
        return true
    }
} 