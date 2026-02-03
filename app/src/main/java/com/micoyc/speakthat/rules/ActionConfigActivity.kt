package com.micoyc.speakthat.rules

import android.graphics.Typeface
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.R
import com.micoyc.speakthat.SpeechTemplateConstants
import com.micoyc.speakthat.databinding.ActivityActionConfigBinding
import com.micoyc.speakthat.settings.managers.SpeechTemplateManager

class ActionConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActionConfigBinding
    private var actionType: ActionType? = null
    private var originalAction: Action? = null
    private var isEditing = false
    private lateinit var templateManager: SpeechTemplateManager
    private lateinit var templatePresets: Array<String>
    private lateinit var templateKeys: Array<String>

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

        actionType = intent.getSerializableExtraCompat(EXTRA_ACTION_TYPE)
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
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", true) // Default to dark mode
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private inline fun <reified T : java.io.Serializable> Intent.getSerializableExtraCompat(key: String): T? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getSerializableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getSerializableExtra(key) as? T
        }
    }

    private fun setupUI() {
        if (actionType == ActionType.SKIP_NOTIFICATION || actionType == ActionType.DISABLE_SPEAKTHAT) {
            setupSkipNotificationUI()
        } else if (actionType == ActionType.APPLY_CUSTOM_SPEECH_FORMAT) {
            setupCustomSpeechFormatUI()
        } else if (actionType == ActionType.FORCE_PRIVATE) {
            setupForcePrivateUI()
        } else if (actionType == ActionType.OVERRIDE_PRIVATE) {
            setupOverridePrivateUI()
        } else {
            InAppLogger.logError("ActionConfigActivity", "Unknown action type: $actionType")
            finish()
        }

        binding.btnSave.setOnClickListener { saveAction() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun setupSkipNotificationUI() {
        binding.cardDisableSpeakThat.visibility = View.VISIBLE
        binding.textDisableSpeakThatInfo.text = getString(com.micoyc.speakthat.R.string.action_disable_description)
    }

    private fun setupCustomSpeechFormatUI() {
        binding.cardCustomSpeechFormat.visibility = View.VISIBLE
        templateManager = SpeechTemplateManager(this)
        templatePresets = templateManager.templatePresets
        templateKeys = templateManager.templateKeys

        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            templatePresets
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSpeechTemplateAction.adapter = adapter

        binding.spinnerSpeechTemplateAction.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedKey = templateKeys[position]
                if (selectedKey == SpeechTemplateConstants.TEMPLATE_KEY_VARIED) {
                    binding.editCustomSpeechTemplateAction.setText(SpeechTemplateConstants.TEMPLATE_KEY_VARIED)
                    binding.editCustomSpeechTemplateAction.isEnabled = false
                } else {
                    binding.editCustomSpeechTemplateAction.isEnabled = true
                    if (selectedKey == SpeechTemplateConstants.TEMPLATE_KEY_CUSTOM) {
                        if (binding.editCustomSpeechTemplateAction.text.isNullOrBlank()) {
                            binding.editCustomSpeechTemplateAction.setText("")
                        }
                    } else {
                        val localizedTemplate = templateManager.getLocalizedTemplateValue(selectedKey)
                        binding.editCustomSpeechTemplateAction.setText(localizedTemplate)
                    }
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
            }
        }

        setupPlaceholderList()
    }

    private fun setupForcePrivateUI() {
        binding.cardForcePrivate.visibility = View.VISIBLE
        binding.textForcePrivateInfo.text = getString(R.string.action_force_private_description)
    }

    private fun setupOverridePrivateUI() {
        binding.cardOverridePrivate.visibility = View.VISIBLE
        binding.textOverridePrivateInfo.text = getString(R.string.action_override_private_description)
    }

    private fun setupPlaceholderList() {
        val placeholders = listOf(
            PlaceholderItem(R.string.speech_placeholder_app_label, R.string.speech_placeholder_app_desc),
            PlaceholderItem(R.string.speech_placeholder_package_label, R.string.speech_placeholder_package_desc),
            PlaceholderItem(R.string.speech_placeholder_content_label, R.string.speech_placeholder_content_desc),
            PlaceholderItem(R.string.speech_placeholder_title_label, R.string.speech_placeholder_title_desc),
            PlaceholderItem(R.string.speech_placeholder_text_label, R.string.speech_placeholder_text_desc),
            PlaceholderItem(R.string.speech_placeholder_bigtext_label, R.string.speech_placeholder_bigtext_desc),
            PlaceholderItem(R.string.speech_placeholder_summary_label, R.string.speech_placeholder_summary_desc),
            PlaceholderItem(R.string.speech_placeholder_info_label, R.string.speech_placeholder_info_desc),
            PlaceholderItem(R.string.speech_placeholder_ticker_label, R.string.speech_placeholder_ticker_desc),
            PlaceholderItem(R.string.speech_placeholder_time_label, R.string.speech_placeholder_time_desc),
            PlaceholderItem(R.string.speech_placeholder_date_label, R.string.speech_placeholder_date_desc),
            PlaceholderItem(R.string.speech_placeholder_timestamp_label, R.string.speech_placeholder_timestamp_desc),
            PlaceholderItem(R.string.speech_placeholder_priority_label, R.string.speech_placeholder_priority_desc),
            PlaceholderItem(R.string.speech_placeholder_category_label, R.string.speech_placeholder_category_desc),
            PlaceholderItem(R.string.speech_placeholder_channel_label, R.string.speech_placeholder_channel_desc)
        )

        binding.placeholderListContainer.removeAllViews()
        placeholders.forEach { item ->
            addPlaceholderItem(getString(item.labelRes), getString(item.descRes))
        }
    }

    private fun addPlaceholderItem(token: String, description: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            setBackgroundResource(com.micoyc.speakthat.R.drawable.gradient_card_subtle_right)
        }

        val tokenView = TextView(this).apply {
            text = token
            setTextColor(ContextCompat.getColor(this@ActionConfigActivity, R.color.purple_card_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
        }

        val descView = TextView(this).apply {
            text = description
            setTextColor(ContextCompat.getColor(this@ActionConfigActivity, R.color.purple_card_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }

        container.addView(tokenView)
        container.addView(descView)

        container.setOnClickListener { appendPlaceholderToken(token) }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dpToPx(8)
        }

        binding.placeholderListContainer.addView(container, params)
    }

    private fun appendPlaceholderToken(token: String) {
        val edit = binding.editCustomSpeechTemplateAction
        if (!edit.isEnabled) {
            return
        }

        val current = edit.text?.toString().orEmpty()
        val separator = if (current.isEmpty() || current.endsWith(" ")) "" else " "
        val newText = current + separator + token
        edit.setText(newText)
        edit.setSelection(newText.length)
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class PlaceholderItem(
        val labelRes: Int,
        val descRes: Int
    )

    private fun loadCurrentValues() {
        if (actionType == ActionType.APPLY_CUSTOM_SPEECH_FORMAT) {
            val template = originalAction?.data?.get("template") as? String ?: ""
            val templateKey = originalAction?.data?.get("template_key") as? String
            val resolvedKey = templateKey ?: templateManager.resolveTemplateKey(template)
            val templateIndex = templateManager.getTemplateIndex(resolvedKey)
            val selection = if (templateIndex >= 0) templateIndex else templateManager.templateKeys.size - 1
            binding.spinnerSpeechTemplateAction.setSelection(selection)
            binding.editCustomSpeechTemplateAction.setText(template)
            binding.editCustomSpeechTemplateAction.isEnabled = resolvedKey != SpeechTemplateConstants.TEMPLATE_KEY_VARIED
        }
    }

    private fun saveAction() {
        val action = when (actionType) {
            ActionType.APPLY_CUSTOM_SPEECH_FORMAT -> createCustomSpeechFormatAction()
            ActionType.FORCE_PRIVATE -> createForcePrivateAction()
            ActionType.OVERRIDE_PRIVATE -> createOverridePrivateAction()
            else -> createSkipNotificationAction()
        }

        val resultIntent = android.content.Intent().apply {
            putExtra(RESULT_ACTION, action.toJson())
            putExtra(EXTRA_IS_EDITING, isEditing)
        }
        setResult(RESULT_OK, resultIntent)

        InAppLogger.logUserAction("Action configured: ${action.getLogMessage()}", "ActionConfigActivity")
        finish()
    }

    private fun createSkipNotificationAction(): Action {
        return if (isEditing && originalAction != null) {
            originalAction!!.copy(
                type = ActionType.SKIP_NOTIFICATION,
                description = getString(R.string.action_skip_notification_title)
            )
        } else {
            Action(
                type = ActionType.SKIP_NOTIFICATION,
                description = getString(R.string.action_skip_notification_title)
            )
        }
    }

    private fun createCustomSpeechFormatAction(): Action {
        val template = binding.editCustomSpeechTemplateAction.text?.toString().orEmpty().trim()
        val selectedKey = templateKeys.getOrNull(binding.spinnerSpeechTemplateAction.selectedItemPosition)
        val templateKey = if (selectedKey == SpeechTemplateConstants.TEMPLATE_KEY_VARIED) {
            SpeechTemplateConstants.TEMPLATE_KEY_VARIED
        } else {
            templateManager.resolveTemplateKey(template)
        }
        val description = getString(R.string.action_custom_speech_format_title)

        return if (isEditing && originalAction != null) {
            originalAction!!.copy(
                type = ActionType.APPLY_CUSTOM_SPEECH_FORMAT,
                data = mapOf(
                    "template" to if (selectedKey == SpeechTemplateConstants.TEMPLATE_KEY_VARIED) SpeechTemplateConstants.TEMPLATE_KEY_VARIED else template,
                    "template_key" to templateKey
                ),
                description = description
            )
        } else {
            Action(
                type = ActionType.APPLY_CUSTOM_SPEECH_FORMAT,
                data = mapOf(
                    "template" to if (selectedKey == SpeechTemplateConstants.TEMPLATE_KEY_VARIED) SpeechTemplateConstants.TEMPLATE_KEY_VARIED else template,
                    "template_key" to templateKey
                ),
                description = description
            )
        }
    }

    private fun createForcePrivateAction(): Action {
        val description = getString(R.string.action_force_private_title)

        return if (isEditing && originalAction != null) {
            originalAction!!.copy(
                type = ActionType.FORCE_PRIVATE,
                description = description,
                data = emptyMap()
            )
        } else {
            Action(
                type = ActionType.FORCE_PRIVATE,
                description = description,
                data = emptyMap()
            )
        }
    }

    private fun createOverridePrivateAction(): Action {
        val description = getString(R.string.action_override_private_title)

        return if (isEditing && originalAction != null) {
            originalAction!!.copy(
                type = ActionType.OVERRIDE_PRIVATE,
                description = description,
                data = emptyMap()
            )
        } else {
            Action(
                type = ActionType.OVERRIDE_PRIVATE,
                description = description,
                data = emptyMap()
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        @Suppress("DEPRECATION")
        onBackPressed()
        return true
    }
}

