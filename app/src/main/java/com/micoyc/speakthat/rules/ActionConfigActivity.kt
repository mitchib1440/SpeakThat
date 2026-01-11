package com.micoyc.speakthat.rules

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.databinding.ActivityActionConfigBinding

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

        applySavedTheme()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Configure Action"

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
        if (actionType == ActionType.DISABLE_SPEAKTHAT) {
            setupDisableSpeakThatUI()
        } else {
            InAppLogger.logError("ActionConfigActivity", "Unknown action type: $actionType")
            finish()
        }

        binding.btnSave.setOnClickListener { saveAction() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun setupDisableSpeakThatUI() {
        binding.cardDisableSpeakThat.visibility = View.VISIBLE
        binding.textDisableSpeakThatInfo.text = getString(com.micoyc.speakthat.R.string.action_disable_description)
    }

    private fun loadCurrentValues() {
        // No additional fields to load for Skip this notification
    }

    private fun saveAction() {
        val action = createDisableSpeakThatAction()

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
            originalAction!!.copy(
                description = "Skip this notification"
            )
        } else {
            Action(
                type = ActionType.DISABLE_SPEAKTHAT,
                description = "Skip this notification"
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        @Suppress("DEPRECATION")
        onBackPressed()
        return true
    }
}

