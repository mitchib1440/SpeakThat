/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat.rules

import android.graphics.Typeface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.LanguagePresetManager
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
    private lateinit var voiceSettingsPrefs: SharedPreferences
    private var overrideLanguageCodes: List<String> = emptyList()
    private var overrideLanguageLabels: List<String> = emptyList()
    private var overrideVoiceOptions: List<OverrideVoiceOption> = emptyList()
    private var voiceOverrideTts: TextToSpeech? = null
    private var pendingOverrideVoiceSelection: String? = null
    private var initialAction: Action? = null

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
        voiceSettingsPrefs = getSharedPreferences("VoiceSettings", MODE_PRIVATE)

        if (isEditing) {
            val actionData = intent.getStringExtra(EXTRA_ACTION_DATA)
            originalAction = Action.fromJson(actionData ?: "")
        }

        setupUI()
        loadCurrentValues()
        initialAction = getCurrentAction()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    showUnsavedChangesDialog()
                } else {
                    finish()
                }
            }
        })
    }

    private fun applySavedTheme() {
        val sharedPreferences = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", true) // Default to dark mode
        val desiredMode = if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        
        // Only set the night mode if it's different from the current mode
        // This prevents unnecessary configuration changes that cause activity recreation loops
        if (currentMode != desiredMode) {
            AppCompatDelegate.setDefaultNightMode(desiredMode)
        }
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
        } else if (actionType == ActionType.OVERRIDE_VOICE) {
            setupOverrideTtsVoiceUI()
        } else if (actionType == ActionType.FORCE_PRIVATE) {
            setupForcePrivateUI()
        } else if (actionType == ActionType.OVERRIDE_PRIVATE) {
            setupOverridePrivateUI()
        } else if (actionType == ActionType.OVERRIDE_EMOJI_REMOVAL) {
            setupOverrideEmojiRemovalUI()
        } else if (actionType == ActionType.OVERRIDE_SEPARATE_DIGITS) {
            setupOverrideSeparateDigitsUI()
        } else if (actionType == ActionType.OVERRIDE_CONTENT_CAP) {
            setupOverrideContentCapUI()
        } else if (actionType == ActionType.OVERRIDE_AUDIO_STREAM) {
            setupOverrideAudioStreamUI()
        } else if (actionType == ActionType.APPLY_WORD_SWAPS) {
            setupApplyWordSwapsUI()
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

    private fun setupOverrideTtsVoiceUI() {
        binding.cardOverrideTtsVoice.visibility = View.VISIBLE
        setupOverrideLanguageSpinner()

        binding.switchOverrideVoiceAdvanced.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutOverrideVoiceAdvancedSection.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) {
                ensureVoiceOverrideTts()
            }
        }
    }

    private fun setupOverrideLanguageSpinner() {
        val presets = LanguagePresetManager.getAllPresets()
            .filter { !it.isCustom }
        val languageByCode = linkedMapOf<String, String>()
        presets.forEach { preset ->
            if (!languageByCode.containsKey(preset.ttsLanguage)) {
                languageByCode[preset.ttsLanguage] = preset.displayName
            }
        }
        overrideLanguageCodes = languageByCode.keys.toList()
        overrideLanguageLabels = languageByCode.values.toList()

        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            overrideLanguageLabels
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerOverrideVoiceLanguage.adapter = adapter
        binding.spinnerOverrideVoiceLanguage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshOverrideVoiceOptions()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun ensureVoiceOverrideTts() {
        if (voiceOverrideTts != null) {
            refreshOverrideVoiceOptions()
            return
        }

        val enginePackage = voiceSettingsPrefs.getString("tts_engine_package", "").orEmpty()
        voiceOverrideTts = if (enginePackage.isBlank()) {
            TextToSpeech(this) { status -> onVoiceOverrideTtsInit(status) }
        } else {
            TextToSpeech(this, { status -> onVoiceOverrideTtsInit(status) }, enginePackage)
        }
    }

    private fun onVoiceOverrideTtsInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            InAppLogger.logError("ActionConfigActivity", "Voice override TTS init failed: $status")
            return
        }
        refreshOverrideVoiceOptions()
    }

    private fun refreshOverrideVoiceOptions() {
        val tts = voiceOverrideTts
        val voiceOptions = tts?.voices
            ?.map { buildOverrideVoiceOption(it) }
            ?.sortedBy { it.label.lowercase() }
            .orEmpty()

        val entries = mutableListOf(
            OverrideVoiceOption(
                label = getString(R.string.action_override_tts_voice_default_voice_option),
                voiceName = null
            )
        )
        entries.addAll(voiceOptions)
        overrideVoiceOptions = entries

        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            overrideVoiceOptions.map { it.label }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerOverrideVoiceName.adapter = adapter

        val pendingName = pendingOverrideVoiceSelection
        if (!pendingName.isNullOrBlank()) {
            val index = overrideVoiceOptions.indexOfFirst { it.voiceName == pendingName }
            if (index >= 0) {
                binding.spinnerOverrideVoiceName.setSelection(index)
                pendingOverrideVoiceSelection = null
            }
        }
    }

    private fun buildOverrideVoiceOption(voice: Voice): OverrideVoiceOption {
        val locale = voice.locale
        val language = locale?.displayLanguage?.takeIf { it.isNotBlank() } ?: "Unknown language"
        val region = locale?.country?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val quality = extractVoiceQuality(voice.name)
        val networkType = if (voice.isNetworkConnectionRequired) "Network" else "Local"
        val label = "$language$region - $quality ($networkType)"
        return OverrideVoiceOption(label = label, voiceName = voice.name)
    }

    private fun extractVoiceQuality(voiceName: String): String {
        val normalized = voiceName.lowercase()
        return when {
            "local" in normalized -> "High Quality"
            "network" in normalized -> "Network"
            "enhanced" in normalized -> "Enhanced"
            "compact" in normalized -> "Compact"
            else -> "Standard"
        }
    }

    private fun setupForcePrivateUI() {
        binding.cardForcePrivate.visibility = View.VISIBLE
        binding.textForcePrivateInfo.text = getString(R.string.action_force_private_description)
    }

    private fun setupOverridePrivateUI() {
        binding.cardOverridePrivate.visibility = View.VISIBLE
        binding.textOverridePrivateInfo.text = getString(R.string.action_override_private_description)
    }

    private fun setupOverrideEmojiRemovalUI() {
        binding.cardOverrideEmojiRemoval.visibility = View.VISIBLE
        binding.textOverrideEmojiRemovalInfo.text = getString(R.string.action_override_emoji_removal_description)
    }

    private fun setupOverrideSeparateDigitsUI() {
        binding.cardOverrideSeparateDigits.visibility = View.VISIBLE
        binding.textOverrideSeparateDigitsInfo.text = getString(R.string.action_override_separate_digits_description)
    }

    private fun setupOverrideContentCapUI() {
        binding.cardOverrideContentCap.visibility = View.VISIBLE
        
        binding.contentCapModeGroup.setOnCheckedChangeListener { _, checkedId ->
            binding.contentCapWordSection.visibility = View.GONE
            binding.contentCapSentenceSection.visibility = View.GONE
            binding.contentCapTimeSection.visibility = View.GONE

            when (checkedId) {
                R.id.radioContentCapWords -> binding.contentCapWordSection.visibility = View.VISIBLE
                R.id.radioContentCapSentences -> binding.contentCapSentenceSection.visibility = View.VISIBLE
                R.id.radioContentCapTime -> binding.contentCapTimeSection.visibility = View.VISIBLE
            }
        }

        binding.sliderContentCapWordCount.addOnChangeListener { _, value, _ ->
            binding.tvContentCapWordCountValue.text = getString(R.string.content_cap_word_count_value, value.toInt())
        }

        binding.sliderContentCapSentenceCount.addOnChangeListener { _, value, _ ->
            binding.tvContentCapSentenceCountValue.text = getString(R.string.content_cap_sentence_count_value, value.toInt())
        }

        binding.sliderContentCapTimeLimit.addOnChangeListener { _, value, _ ->
            binding.tvContentCapTimeLimitValue.text = getString(R.string.content_cap_time_limit_value, value.toInt())
        }

        // Default to disabled
        binding.radioContentCapDisabled.isChecked = true
    }

    private fun setupPlaceholderList() {
        val placeholders = listOf(
            PlaceholderItem(R.string.speech_placeholder_app_label, R.string.speech_placeholder_app_desc),
            PlaceholderItem(R.string.speech_placeholder_package_label, R.string.speech_placeholder_package_desc),
            PlaceholderItem(R.string.speech_placeholder_content_label, R.string.speech_placeholder_content_desc),
            PlaceholderItem(R.string.speech_placeholder_title_label, R.string.speech_placeholder_title_desc),
            PlaceholderItem(R.string.speech_placeholder_text_label, R.string.speech_placeholder_text_desc),
            PlaceholderItem(R.string.speech_placeholder_bigtext_label, R.string.speech_placeholder_bigtext_desc),
            PlaceholderItem(R.string.speech_placeholder_subtext_label, R.string.speech_placeholder_subtext_desc),
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

    private fun setupOverrideAudioStreamUI() {
        binding.cardOverrideAudioStream.visibility = View.VISIBLE
        
        val audioUsageOptions = arrayOf(
            getString(com.micoyc.speakthat.R.string.voice_audio_usage_media_recommended),
            getString(com.micoyc.speakthat.R.string.voice_audio_usage_notification),
            getString(com.micoyc.speakthat.R.string.voice_audio_usage_alarm),
            getString(com.micoyc.speakthat.R.string.voice_audio_usage_voice_call),
            getString(com.micoyc.speakthat.R.string.voice_audio_usage_assistance)
        )
        
        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            audioUsageOptions
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAudioStream.adapter = adapter
    }

    private val wordSwapsList = mutableListOf<Pair<String, String>>()
    private lateinit var wordSwapAdapter: WordSwapAdapter

    private fun setupApplyWordSwapsUI() {
        binding.cardApplyWordSwaps.visibility = View.VISIBLE
        
        wordSwapAdapter = WordSwapAdapter(wordSwapsList, 
            { position -> 
                wordSwapsList.removeAt(position)
                wordSwapAdapter.notifyItemRemoved(position)
                wordSwapAdapter.notifyItemRangeChanged(position, wordSwapsList.size)
            },
            { position -> 
                showEditWordSwapDialog(position)
            }
        )
        
        binding.recyclerViewWordSwaps.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@ActionConfigActivity)
            adapter = wordSwapAdapter
        }
        
        binding.btnAddWordSwap.setOnClickListener {
            showEditWordSwapDialog(-1)
        }
    }

    private fun showEditWordSwapDialog(position: Int) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(com.micoyc.speakthat.R.layout.bottom_sheet_edit_word_swap, null)
        bottomSheetDialog.setContentView(view)

        val editFrom = view.findViewById<com.google.android.material.textfield.TextInputEditText>(com.micoyc.speakthat.R.id.editFrom)
        val editTo = view.findViewById<com.google.android.material.textfield.TextInputEditText>(com.micoyc.speakthat.R.id.editTo)
        val btnSave = view.findViewById<com.google.android.material.button.MaterialButton>(com.micoyc.speakthat.R.id.btnSave)
        val btnCancel = view.findViewById<com.google.android.material.button.MaterialButton>(com.micoyc.speakthat.R.id.btnCancel)

        if (position >= 0) {
            editFrom.setText(wordSwapsList[position].first)
            editTo.setText(wordSwapsList[position].second)
        }

        btnSave.setOnClickListener {
            val from = editFrom.text.toString().trim()
            val to = editTo.text.toString().trim()

            if (from.isEmpty()) {
                android.widget.Toast.makeText(this, "Please enter a word or phrase to replace", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (position >= 0) {
                wordSwapsList[position] = Pair(from, to)
                wordSwapAdapter.notifyItemChanged(position)
            } else {
                wordSwapsList.add(Pair(from, to))
                wordSwapAdapter.notifyItemInserted(wordSwapsList.size - 1)
            }
            bottomSheetDialog.dismiss()
        }

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private inner class WordSwapAdapter(
        private val items: List<Pair<String, String>>,
        private val onRemove: (Int) -> Unit,
        private val onEdit: (Int) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<WordSwapAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val textFrom: android.widget.TextView = view.findViewById(com.micoyc.speakthat.R.id.textFrom)
            val textTo: android.widget.TextView = view.findViewById(com.micoyc.speakthat.R.id.textTo)
            val btnRemove: android.widget.ImageButton = view.findViewById(com.micoyc.speakthat.R.id.buttonRemove)
            
            init {
                view.setOnClickListener { onEdit(adapterPosition) }
                btnRemove.setOnClickListener { onRemove(adapterPosition) }
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(com.micoyc.speakthat.R.layout.item_word_swap, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textFrom.text = item.first
            holder.textTo.text = item.second
        }

        override fun getItemCount() = items.size
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class PlaceholderItem(
        val labelRes: Int,
        val descRes: Int
    )

    private data class OverrideVoiceOption(
        val label: String,
        val voiceName: String?
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
        } else if (actionType == ActionType.OVERRIDE_VOICE) {
            val savedLanguage = (originalAction?.data?.get("language") as? String)
                ?: voiceSettingsPrefs.getString("language", "en_US")
                ?: "en_US"
            val languageIndex = overrideLanguageCodes.indexOf(savedLanguage).takeIf { it >= 0 } ?: 0
            binding.spinnerOverrideVoiceLanguage.setSelection(languageIndex)

            val savedVoiceName = (originalAction?.data?.get("voice_name") as? String).orEmpty()
            if (savedVoiceName.isNotBlank()) {
                binding.switchOverrideVoiceAdvanced.isChecked = true
                binding.layoutOverrideVoiceAdvancedSection.visibility = View.VISIBLE
                pendingOverrideVoiceSelection = savedVoiceName
                ensureVoiceOverrideTts()
            }
        } else if (actionType == ActionType.OVERRIDE_CONTENT_CAP) {
            val mode = originalAction?.data?.get("mode") as? String ?: "disabled"
            val wordCount = (originalAction?.data?.get("word_count") as? Number)?.toInt() ?: 6
            val sentenceCount = (originalAction?.data?.get("sentence_count") as? Number)?.toInt() ?: 1
            val timeLimit = (originalAction?.data?.get("time_limit") as? Number)?.toInt() ?: 10

            when (mode) {
                "words" -> binding.radioContentCapWords.isChecked = true
                "sentences" -> binding.radioContentCapSentences.isChecked = true
                "time" -> binding.radioContentCapTime.isChecked = true
                else -> binding.radioContentCapDisabled.isChecked = true
            }

            binding.sliderContentCapWordCount.value = wordCount.toFloat()
            binding.sliderContentCapSentenceCount.value = sentenceCount.toFloat()
            binding.sliderContentCapTimeLimit.value = timeLimit.toFloat()
        } else if (actionType == ActionType.OVERRIDE_AUDIO_STREAM) {
            val audioUsageIndex = (originalAction?.data?.get("audio_usage_index") as? Number)?.toInt() ?: 4
            if (audioUsageIndex in 0..4) {
                binding.spinnerAudioStream.setSelection(audioUsageIndex)
            }
        } else if (actionType == ActionType.APPLY_WORD_SWAPS) {
            val swapsJson = originalAction?.data?.get("swaps_json") as? String ?: "[]"
            val replaceGlobal = originalAction?.data?.get("replace_global") as? Boolean ?: false
            
            try {
                val jsonArray = org.json.JSONArray(swapsJson)
                wordSwapsList.clear()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val from = obj.optString("from", "")
                    val to = obj.optString("to", "")
                    if (from.isNotEmpty()) {
                        wordSwapsList.add(Pair(from, to))
                    }
                }
                wordSwapAdapter.notifyDataSetChanged()
            } catch (e: kotlin.Exception) {
                InAppLogger.logError("ActionConfigActivity", "Failed to parse word swaps JSON: ${e.message}")
            }
            
            binding.switchReplaceGlobalSwaps.isChecked = replaceGlobal
        }
    }

    private fun getCurrentAction(): Action {
        return when (actionType) {
            ActionType.APPLY_CUSTOM_SPEECH_FORMAT -> createCustomSpeechFormatAction()
            ActionType.OVERRIDE_VOICE -> createOverrideTtsVoiceAction()
            ActionType.FORCE_PRIVATE -> createForcePrivateAction()
            ActionType.OVERRIDE_PRIVATE -> createOverridePrivateAction()
            ActionType.OVERRIDE_EMOJI_REMOVAL -> createOverrideEmojiRemovalAction()
            ActionType.OVERRIDE_SEPARATE_DIGITS -> createOverrideSeparateDigitsAction()
            ActionType.OVERRIDE_CONTENT_CAP -> createOverrideContentCapAction()
            ActionType.OVERRIDE_AUDIO_STREAM -> createOverrideAudioStreamAction()
            ActionType.APPLY_WORD_SWAPS -> createApplyWordSwapsAction()
            else -> createSkipNotificationAction()
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        return getCurrentAction() != initialAction
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.unsaved_changes_title)
            .setMessage(R.string.unsaved_changes_message)
            .setPositiveButton(R.string.button_save) { _, _ -> saveAction() }
            .setNegativeButton(R.string.discard) { _, _ -> finish() }
            .setNeutralButton(R.string.button_cancel, null)
            .show()
    }

    private fun saveAction() {
        val action = getCurrentAction()

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

    private fun createOverrideTtsVoiceAction(): Action {
        val selectedLanguage = overrideLanguageCodes
            .getOrNull(binding.spinnerOverrideVoiceLanguage.selectedItemPosition)
            ?: voiceSettingsPrefs.getString("language", "en_US")
            ?: "en_US"
        val useSpecificVoice = binding.switchOverrideVoiceAdvanced.isChecked
        val selectedVoiceName = if (useSpecificVoice) {
            overrideVoiceOptions
                .getOrNull(binding.spinnerOverrideVoiceName.selectedItemPosition)
                ?.voiceName
        } else {
            null
        }
        val actionData = mutableMapOf<String, Any>(
            "language" to selectedLanguage
        )
        if (!selectedVoiceName.isNullOrBlank()) {
            actionData["voice_name"] = selectedVoiceName
        }
        val description = getString(R.string.action_override_tts_voice_title)

        return if (isEditing && originalAction != null) {
            originalAction!!.copy(
                type = ActionType.OVERRIDE_VOICE,
                data = actionData,
                description = description
            )
        } else {
            Action(
                type = ActionType.OVERRIDE_VOICE,
                data = actionData,
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

    private fun createOverrideEmojiRemovalAction(): Action {
        val description = getString(R.string.action_override_emoji_removal_title)

        return if (isEditing && originalAction != null) {
            originalAction!!.copy(
                type = ActionType.OVERRIDE_EMOJI_REMOVAL,
                description = description,
                data = emptyMap()
            )
        } else {
            Action(
                type = ActionType.OVERRIDE_EMOJI_REMOVAL,
                description = description,
                data = emptyMap()
            )
        }
    }

    private fun createOverrideSeparateDigitsAction(): Action {
        val description = getString(R.string.action_override_separate_digits_title)

        return if (isEditing && originalAction != null) {
            originalAction!!.copy(
                type = ActionType.OVERRIDE_SEPARATE_DIGITS,
                description = description,
                data = emptyMap()
            )
        } else {
            Action(
                type = ActionType.OVERRIDE_SEPARATE_DIGITS,
                description = description,
                data = emptyMap()
            )
        }
    }

    private fun createOverrideContentCapAction(): Action {
        val mode = when {
            binding.radioContentCapWords.isChecked -> "words"
            binding.radioContentCapSentences.isChecked -> "sentences"
            binding.radioContentCapTime.isChecked -> "time"
            else -> "disabled"
        }
        
        val wordCount = binding.sliderContentCapWordCount.value.toInt()
        val sentenceCount = binding.sliderContentCapSentenceCount.value.toInt()
        val timeLimit = binding.sliderContentCapTimeLimit.value.toInt()

        val data = mapOf(
            "mode" to mode,
            "word_count" to wordCount,
            "sentence_count" to sentenceCount,
            "time_limit" to timeLimit
        )

        val description = getString(R.string.action_override_content_cap_title)

        return if (isEditing && originalAction != null) {
            originalAction!!.copy(
                type = ActionType.OVERRIDE_CONTENT_CAP,
                description = description,
                data = data
            )
        } else {
            Action(
                type = ActionType.OVERRIDE_CONTENT_CAP,
                description = description,
                data = data
            )
        }
    }

    private fun createOverrideAudioStreamAction(): Action {
        val audioUsageIndex = binding.spinnerAudioStream.selectedItemPosition
        
        val data = mapOf(
            "audio_usage_index" to audioUsageIndex
        )

        val streamName = binding.spinnerAudioStream.selectedItem?.toString() ?: ""
        val description = "${getString(com.micoyc.speakthat.R.string.action_override_audio_stream_title)}: $streamName"

        return if (isEditing && originalAction != null) {
            originalAction!!.copy(
                type = ActionType.OVERRIDE_AUDIO_STREAM,
                description = description,
                data = data
            )
        } else {
            Action(
                type = ActionType.OVERRIDE_AUDIO_STREAM,
                description = description,
                data = data
            )
        }
    }

    private fun createApplyWordSwapsAction(): Action {
        val replaceGlobal = binding.switchReplaceGlobalSwaps.isChecked
        
        val jsonArray = org.json.JSONArray()
        for (swap in wordSwapsList) {
            val obj = org.json.JSONObject()
            obj.put("from", swap.first)
            obj.put("to", swap.second)
            jsonArray.put(obj)
        }
        val swapsJson = jsonArray.toString()
        
        val data = mapOf(
            "swaps_json" to swapsJson,
            "replace_global" to replaceGlobal
        )

        val description = getString(com.micoyc.speakthat.R.string.rule_action_apply_word_swaps)

        return if (isEditing && originalAction != null) {
            originalAction!!.copy(
                type = ActionType.APPLY_WORD_SWAPS,
                description = description,
                data = data
            )
        } else {
            Action(
                type = ActionType.APPLY_WORD_SWAPS,
                description = description,
                data = data
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        voiceOverrideTts?.shutdown()
        voiceOverrideTts = null
        super.onDestroy()
    }
}

