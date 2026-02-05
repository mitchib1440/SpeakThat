package com.micoyc.speakthat

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.recyclerview.widget.RecyclerView
import com.micoyc.speakthat.LanguagePresetManager
import com.micoyc.speakthat.automation.AutomationMode
import com.micoyc.speakthat.automation.AutomationModeManager
import com.micoyc.speakthat.databinding.ItemOnboardingPageBinding
import com.micoyc.speakthat.rules.RuleTemplate
import com.micoyc.speakthat.rules.TriggerConfigActivity
import com.micoyc.speakthat.rules.TriggerType
import com.micoyc.speakthat.utils.WifiCapabilityChecker

class OnboardingPagerAdapter(
    private val skipPermissionPage: Boolean = false
) : RecyclerView.Adapter<OnboardingPagerAdapter.OnboardingViewHolder>() {
    
    var appPickerLauncher: ActivityResultLauncher<Intent>? = null
    
    private val pages = if (skipPermissionPage) listOf(
        OnboardingPage(
            titleResId = R.string.onboarding_language_theme_title,
            descriptionResId = R.string.onboarding_language_theme_description,
            icon = "🌍",
            showLanguageSelector = true,
            showThemeSelector = false
        ),
        OnboardingPage(
            titleResId = R.string.onboarding_welcome_title,
            descriptionResId = R.string.onboarding_welcome_description,
            icon = "🔊"
        ),
        OnboardingPage(
            titleResId = R.string.onboarding_privacy_title,
            descriptionResId = R.string.onboarding_privacy_description,
            icon = "🔒"
        ),
        OnboardingPage(
            titleResId = R.string.onboarding_filters_title,
            descriptionResId = R.string.onboarding_filters_description,
            icon = "⚙️"
        ),
        OnboardingPage(
            titleResId = R.string.onboarding_apps_title,
            descriptionResId = R.string.onboarding_apps_description,
            icon = "🚫",
            showAppSelector = true
        ),
        OnboardingPage(
            titleResId = R.string.onboarding_words_title,
            descriptionResId = R.string.onboarding_words_description,
            icon = "🔇",
            showWordSelector = true
        ),
        OnboardingPage(
            titleResId = R.string.onboarding_rules_title,
            descriptionResId = R.string.onboarding_rules_description,
            icon = "🧠",
            showRuleTemplates = true
        ),
        OnboardingPage(
            titleResId = R.string.onboarding_complete_title,
            descriptionResId = R.string.onboarding_complete_description,
            icon = "✅"
        )
    ) else listOf(
        OnboardingPage(
            titleResId = R.string.onboarding_language_theme_title,
            descriptionResId = R.string.onboarding_language_theme_description,
            icon = "🌍",
            showLanguageSelector = true,
            showThemeSelector = false
        ),
        OnboardingPage(
            titleResId = R.string.onboarding_welcome_title,
            descriptionResId = R.string.onboarding_welcome_description,
            icon = "🔊"
        ),
        OnboardingPage(
            titleResId = R.string.onboarding_permission_title,
            descriptionResId = R.string.onboarding_permission_description,
            icon = "🔔",
            showPermissionButton = true
        ),
        OnboardingPage(
            titleResId = R.string.onboarding_privacy_title,
            descriptionResId = R.string.onboarding_privacy_description,
            icon = "🔒"
        ),
        OnboardingPage(
            titleResId = R.string.onboarding_filters_title,
            descriptionResId = R.string.onboarding_filters_description,
            icon = "⚙️"
        ),
        OnboardingPage(
            titleResId = R.string.onboarding_apps_title,
            descriptionResId = R.string.onboarding_apps_description,
            icon = "🚫",
            showAppSelector = true
        ),
        OnboardingPage(
            titleResId = R.string.onboarding_words_title,
            descriptionResId = R.string.onboarding_words_description,
            icon = "🔇",
            showWordSelector = true
        ),
        OnboardingPage(
            titleResId = R.string.onboarding_rules_title,
            descriptionResId = R.string.onboarding_rules_description,
            icon = "🧠",
            showRuleTemplates = true
        ),
        OnboardingPage(
            titleResId = R.string.onboarding_complete_title,
            descriptionResId = R.string.onboarding_complete_description,
            icon = "✅"
        )
    )
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val binding = ItemOnboardingPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return OnboardingViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        holder.bind(pages[position])
    }
    
    override fun getItemCount(): Int = pages.size
    
    fun refreshUIText() {
        // Force a full rebind of all items to update text with new language
        notifyDataSetChanged()
        InAppLogger.log("OnboardingPagerAdapter", "UI text refreshed for language change")
    }
    
    inner class OnboardingViewHolder(private val binding: ItemOnboardingPageBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(page: OnboardingPage) {
            android.util.Log.d("OnboardingPagerAdapter", "Binding page with titleResId: ${page.titleResId}")
            binding.textTitle.text = binding.root.context.getString(page.titleResId)
            binding.textDescription.text = binding.root.context.getString(page.descriptionResId)
            binding.textIcon.text = page.icon
            
            // Show/hide permission button based on page
            if (page.showPermissionButton) {
                binding.buttonPermission.visibility = android.view.View.VISIBLE
                binding.buttonPermission.text = binding.root.context.getString(R.string.item_onboarding_notification_settings)
                binding.buttonPermission.setOnClickListener {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        binding.root.context.startActivity(intent)
                        InAppLogger.log("OnboardingPagerAdapter", "Opening notification settings")
                    } catch (e: Exception) {
                        InAppLogger.logError("OnboardingPagerAdapter", "Failed to open notification settings: ${e.message}")
                        android.widget.Toast.makeText(
                            binding.root.context,
                            "Failed to open notification settings",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                binding.buttonPermission.visibility = android.view.View.GONE
            }
            
            // Show/hide app selector section
            if (page.showAppSelector) {
                binding.appSelectorSection.visibility = android.view.View.VISIBLE
                setupAppSelector()
            } else {
                binding.appSelectorSection.visibility = android.view.View.GONE
            }
            
            // Show/hide word selector section
            if (page.showWordSelector) {
                binding.wordSelectorSection.visibility = android.view.View.VISIBLE
                setupWordSelector()
            } else {
                binding.wordSelectorSection.visibility = android.view.View.GONE
            }
            
            // Show/hide rule templates section
            if (page.showRuleTemplates) {
                binding.ruleTemplatesSection.visibility = android.view.View.VISIBLE
                setupRuleTemplates()
            } else {
                binding.ruleTemplatesSection.visibility = android.view.View.GONE
            }
            
            // Show/hide language selector section
            if (page.showLanguageSelector) {
                binding.languageSelectorSection.visibility = android.view.View.VISIBLE
                setupLanguageSelector()
            } else {
                binding.languageSelectorSection.visibility = android.view.View.GONE
            }
            
            // Show/hide theme selector section
            if (page.showThemeSelector) {
                binding.themeSelectorSection.visibility = android.view.View.VISIBLE
                setupThemeSelector()
            } else {
                binding.themeSelectorSection.visibility = android.view.View.GONE
            }
        }
        
        private fun setupAppSelector() {
            binding.buttonManageApps.setOnClickListener {
                launchAppPicker()
            }
            
            // Set up RecyclerView for selected apps
            val selectedAppsAdapter = OnboardingAppListAdapter { packageName ->
                removeAppFromBlacklist(packageName)
            }
            val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(binding.root.context)
            binding.recyclerSelectedApps.layoutManager = layoutManager
            binding.recyclerSelectedApps.adapter = selectedAppsAdapter
            
            // Load existing apps
            loadSelectedApps(selectedAppsAdapter)
            updateAppCount(null)
        }
        
        private fun launchAppPicker() {
            val ctx = binding.root.context
            val prefs = ctx.getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
            val selected = prefs.getStringSet("app_list", LinkedHashSet())?.toCollection(ArrayList()) ?: arrayListOf()
            val priv = prefs.getStringSet("app_private_flags", LinkedHashSet())?.toCollection(ArrayList()) ?: arrayListOf()
            val intent = AppPickerActivity.createIntent(
                ctx,
                ctx.getString(R.string.onboarding_apps_title),
                selected,
                priv,
                true
            )
            appPickerLauncher?.launch(intent)
        }
        
        private fun setupWordSelector() {
            // Set up add button
            binding.buttonAddWord.setOnClickListener {
                val input = binding.editWord.text?.toString()?.trim() ?: ""
                if (input.isNotEmpty()) {
                    addWordToBlacklist(input)
                    binding.editWord.text?.clear()
                }
            }
            
            // Set up RecyclerView for selected words
            val selectedWordsAdapter = OnboardingWordListAdapter { word ->
                removeWordFromBlacklist(word)
            }
            val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(binding.root.context)
            binding.recyclerSelectedWords.layoutManager = layoutManager
            binding.recyclerSelectedWords.adapter = selectedWordsAdapter
            
            // Load existing words
            loadSelectedWords(selectedWordsAdapter)
        }
        
        private fun setupLanguageSelector() {
            // Get all available language presets (excluding custom)
            val allPresets = LanguagePresetManager.getAllPresets()
            val nonCustomPresets = allPresets.filter { !it.isCustom }
            
            // Create adapter with preset display names
            val presetNames = nonCustomPresets.map { it.displayName }
            
            val presetAdapter = android.widget.ArrayAdapter(
                binding.root.context,
                android.R.layout.simple_spinner_item, 
                presetNames
            )
            presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.languagePresetSpinner.adapter = presetAdapter
            
            // Get the currently selected language from settings
            val voiceSettingsPrefs = binding.root.context.getSharedPreferences("VoiceSettings", android.content.Context.MODE_PRIVATE)
            val currentLanguage = voiceSettingsPrefs.getString("language", "en_US")
            
            // Find the index of the currently selected language
            val currentPresetIndex = nonCustomPresets.indexOfFirst { it.uiLocale == currentLanguage }
            if (currentPresetIndex >= 0) {
                // Set the spinner to the currently selected language
                binding.languagePresetSpinner.setSelection(currentPresetIndex)
                InAppLogger.log("OnboardingLanguageSelector", "Restored language selection to: ${nonCustomPresets[currentPresetIndex].displayName}")
            }
            
            // Add listener to handle preset selection
            binding.languagePresetSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    if (position >= 0 && position < nonCustomPresets.size) {
                        val selectedPreset = nonCustomPresets[position]
                        InAppLogger.log("OnboardingLanguageSelector", "Language preset selected: ${selectedPreset.displayName}")
                        
                        // Apply the preset settings using the onboarding-specific method
                        LanguagePresetManager.applyPresetForOnboarding(binding.root.context, selectedPreset)
                        
                        InAppLogger.log("OnboardingLanguageSelector", "Preset applied successfully: ${selectedPreset.displayName}")
                    }
                }
                
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                    // Do nothing
                }
            }
        }
        
        private fun setupThemeSelector() {
            // Get current theme setting
            val sharedPreferences = binding.root.context.getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
            val isDarkMode = sharedPreferences.getBoolean("dark_mode", true) // Default to dark mode
            
            // Set initial state
            binding.themeSwitch.isChecked = isDarkMode
            
            // Add listener to handle theme changes
            binding.themeSwitch.setOnCheckedChangeListener { _, isChecked ->
                sharedPreferences.edit().putBoolean("dark_mode", isChecked).apply()
                
                // Apply theme immediately
                val desiredMode = if (isChecked) {
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
                
                InAppLogger.log("OnboardingThemeSelector", "Theme changed to: ${if (isChecked) "dark" else "light"}")
            }
        }
        
        private fun addAppToBlacklist(input: String) {
            val sharedPreferences = binding.root.context.getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
            val currentApps = sharedPreferences.getStringSet("app_list", LinkedHashSet())?.toMutableSet() ?: mutableSetOf()
            
            // Set app list mode to blacklist if not already set
            if (sharedPreferences.getString("app_list_mode", "none") == "none") {
                sharedPreferences.edit().putString("app_list_mode", "blacklist").apply()
            }
            
            // Try to resolve input to package name
            var packageNameToAdd = input
            try {
                val packageManager = binding.root.context.packageManager
                
                // First, try to get package info directly (in case input is already a package name)
                try {
                    val appInfo = packageManager.getApplicationInfo(input, 0)
                    packageNameToAdd = appInfo.packageName
                    InAppLogger.log("OnboardingAppSelector", "Direct package match: $input -> $packageNameToAdd")
                } catch (e: Exception) {
                    // Input is not a package name, try to find by app name
                    InAppLogger.log("OnboardingAppSelector", "Input '$input' is not a package name, searching by app name...")
                    
                    // Query for all launchable apps
                    val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
                    mainIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    
                    val resolveInfoList = packageManager.queryIntentActivities(mainIntent, 0)
                    
                    for (resolveInfo in resolveInfoList) {
                        try {
                            val packageName = resolveInfo.activityInfo.packageName
                            val appName = resolveInfo.loadLabel(packageManager).toString()
                            
                            // Skip our own app
                            if (packageName == binding.root.context.packageName) {
                                continue
                            }
                            
                            // Check if this is actually a system app using ApplicationInfo flags
                            try {
                                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                                // Skip actual system apps (but allow user-installed apps even if they have system-like package names)
                                if ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 && 
                                    (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
                                    // This is a true system app (not a user-installed update)
                                    continue
                                }
                            } catch (e: Exception) {
                                // If we can't get app info, skip it
                                continue
                            }
                            
                            // Check if app name matches input
                            if (appName.equals(input, ignoreCase = true)) {
                                packageNameToAdd = packageName
                                InAppLogger.log("OnboardingAppSelector", "Found app match: '$input' -> $packageNameToAdd")
                                break
                            }
                        } catch (e: Exception) {
                            // Skip apps that can't be loaded
                            continue
                        }
                    }
                }
                
                // If we still haven't found a match, log it
                if (packageNameToAdd == input) {
                    InAppLogger.log("OnboardingAppSelector", "Could not resolve '$input' to a package name, using as-is")
                }
                
            } catch (e: Exception) {
                InAppLogger.logError("OnboardingAppSelector", "Error resolving app: ${e.message}")
                // Fallback to using input as-is
            }
            
            // Check for duplicates
            if (currentApps.contains(packageNameToAdd)) {
                InAppLogger.log("OnboardingAppSelector", "App '$packageNameToAdd' is already in blacklist")
                return
            }
            
            currentApps.add(packageNameToAdd)
            sharedPreferences.edit().putStringSet("app_list", currentApps).apply()
            
            InAppLogger.log("OnboardingAppSelector", "Added app '$packageNameToAdd' to blacklist")
            
            // Refresh the RecyclerView
            val adapter = binding.recyclerSelectedApps.adapter as? OnboardingAppListAdapter
            adapter?.updateApps(currentApps.toList())
            updateAppCount(currentApps.size)
        }
        
        private fun removeAppFromBlacklist(packageName: String) {
            val sharedPreferences = binding.root.context.getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
            val currentApps = sharedPreferences.getStringSet("app_list", LinkedHashSet())?.toMutableSet() ?: mutableSetOf()
            val currentPrivate = sharedPreferences.getStringSet("app_private_flags", LinkedHashSet())?.toMutableSet() ?: mutableSetOf()
            
            if (currentApps.contains(packageName)) {
                currentApps.remove(packageName)
                currentPrivate.remove(packageName)
                sharedPreferences.edit()
                    .putStringSet("app_list", currentApps)
                    .putStringSet("app_private_flags", currentPrivate)
                    .apply()
                
                InAppLogger.log("OnboardingAppSelector", "Removed app '$packageName' from blacklist")
                
                // Refresh the RecyclerView
                val adapter = binding.recyclerSelectedApps.adapter as? OnboardingAppListAdapter
                adapter?.updateApps(currentApps.toList())
                updateAppCount(currentApps.size)
            }
        }
        
        private fun loadSelectedApps(adapter: OnboardingAppListAdapter) {
            val sharedPreferences = binding.root.context.getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
            val currentApps = sharedPreferences.getStringSet("app_list", LinkedHashSet()) ?: LinkedHashSet()
            adapter.updateApps(currentApps.toList())
            updateAppCount(currentApps.size)
        }

        private fun updateAppCount(count: Int?) {
            val resolvedCount = count ?: run {
                val prefs = binding.root.context.getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
                prefs.getStringSet("app_list", LinkedHashSet())?.size ?: 0
            }
            binding.textAppCount.text = "(${resolvedCount} apps)"
        }
        
        private fun addWordToBlacklist(word: String) {
            val sharedPreferences = binding.root.context.getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
            val currentWords = sharedPreferences.getStringSet("word_blacklist", LinkedHashSet())?.toMutableSet() ?: mutableSetOf()
            
            // Check for duplicates
            if (currentWords.contains(word)) {
                return
            }
            
            currentWords.add(word)
            sharedPreferences.edit().putStringSet("word_blacklist", currentWords).apply()
            
            // Refresh the RecyclerView
            val adapter = binding.recyclerSelectedWords.adapter as? OnboardingWordListAdapter
            adapter?.updateWords(currentWords.toList())
        }
        
        private fun removeWordFromBlacklist(word: String) {
            val sharedPreferences = binding.root.context.getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
            val currentWords = sharedPreferences.getStringSet("word_blacklist", LinkedHashSet())?.toMutableSet() ?: mutableSetOf()
            
            if (currentWords.contains(word)) {
                currentWords.remove(word)
                sharedPreferences.edit().putStringSet("word_blacklist", currentWords).apply()
                
                InAppLogger.log("OnboardingWordSelector", "Removed word '$word' from blacklist")
                
                // Refresh the RecyclerView
                val adapter = binding.recyclerSelectedWords.adapter as? OnboardingWordListAdapter
                adapter?.updateWords(currentWords.toList())
            }
        }
        
        private fun loadSelectedWords(adapter: OnboardingWordListAdapter) {
            val sharedPreferences = binding.root.context.getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
            val currentWords = sharedPreferences.getStringSet("word_blacklist", LinkedHashSet()) ?: LinkedHashSet()
            adapter.updateWords(currentWords.toList())
        }
        
        private fun setupRuleTemplates() {
            // Initialize rule templates adapter with both callbacks
            val ruleTemplatesAdapter = OnboardingRuleTemplateAdapter(
                onTemplateSelected = { template ->
                    addRuleFromTemplate(template)
                },
                onTemplateConfigured = { _, _ ->
                    // This callback is not used in the simplified approach
                    // The configuration is handled by the activity
                },
                onTemplateNeedsConfiguration = { template ->
                    launchRuleConfiguration(template)
                }
            )

            // Set up RecyclerView for rule templates
            val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(binding.root.context)
            binding.recyclerRuleTemplates.layoutManager = layoutManager
            binding.recyclerRuleTemplates.adapter = ruleTemplatesAdapter

            // Load templates
            ruleTemplatesAdapter.loadTemplates(binding.root.context)

            InAppLogger.log("OnboardingRuleTemplates", "Rule templates section set up")
        }
        
        private fun addRuleFromTemplate(template: com.micoyc.speakthat.rules.RuleTemplate) {
            try {
                // Create a rule from the template
                val rule = com.micoyc.speakthat.rules.RuleTemplates.createRuleFromTemplate(template)
                
                // Get the rule manager and add the rule
                val ruleManager = com.micoyc.speakthat.rules.RuleManager(binding.root.context)
                ruleManager.addRule(rule)
                
                // Ensure Conditional Rules mode is active
                val automationModeManager = AutomationModeManager(binding.root.context)
                automationModeManager.setMode(AutomationMode.CONDITIONAL_RULES)
                InAppLogger.log("OnboardingRuleTemplates", "Enabled Conditional Rules feature")
                
                InAppLogger.log("OnboardingRuleTemplates", "Added rule from template: ${template.name}")
                
                // Show a toast to confirm the rule was added
                android.widget.Toast.makeText(
                    binding.root.context,
                    "Added rule: ${template.name}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                InAppLogger.logError("OnboardingRuleTemplates", "Error adding rule from template: ${e.message}")
                
                // Show error toast
                android.widget.Toast.makeText(
                    binding.root.context,
                    "Error adding rule. Please try again.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        private fun launchRuleConfiguration(template: RuleTemplate) {
            // Determine which trigger type to configure based on the template
            val triggerType = template.triggers.firstOrNull()?.type
            if (triggerType == null) {
                InAppLogger.logError("OnboardingRuleTemplates", "No trigger found in template: ${template.name}")
                return
            }

            InAppLogger.log("OnboardingRuleTemplates", "Launching inline configuration for template: ${template.name}, trigger type: $triggerType")

            // Show appropriate configuration dialog based on trigger type
            when (triggerType) {
                TriggerType.BLUETOOTH_DEVICE -> showBluetoothConfigurationDialog(template)
                TriggerType.TIME_SCHEDULE -> showTimeScheduleConfigurationDialog(template)
                TriggerType.WIFI_NETWORK -> showWifiConfigurationDialog(template)
                else -> {
                    InAppLogger.logError("OnboardingRuleTemplates", "Unsupported trigger type: $triggerType")
                    android.widget.Toast.makeText(
                        binding.root.context,
                        "Configuration not supported for this template type",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        private fun showBluetoothConfigurationDialog(template: RuleTemplate) {
            // Check permission first before showing dialog
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (binding.root.context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    // Request permission first
                    if (binding.root.context is android.app.Activity) {
                        (binding.root.context as android.app.Activity).requestPermissions(
                            arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT),
                            1001
                        )
                    }
                    android.widget.Toast.makeText(
                        binding.root.context,
                        "Bluetooth permission needed. Please grant permission and try again.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }

            val dialogView = android.view.LayoutInflater.from(binding.root.context)
                .inflate(R.layout.dialog_bluetooth_configuration, null)
            
            val dialog = androidx.appcompat.app.AlertDialog.Builder(binding.root.context)
                .setTitle("Configure ${template.name}")
                .setView(dialogView)
                .setPositiveButton("Add Rule") { _, _ ->
                    // Get selected devices and create rule
                    val selectedDevices = getSelectedBluetoothDevices(dialogView)
                    if (selectedDevices.isNotEmpty()) {
                        val customData = mapOf("device_addresses" to selectedDevices)
                        addRuleFromTemplateWithData(template, customData)
                    } else {
                        // If no devices selected, create rule with placeholder data
                        val customData = mapOf("device_addresses" to setOf("placeholder_device"))
                        addRuleFromTemplateWithData(template, customData)
                        android.widget.Toast.makeText(
                            binding.root.context,
                            "Rule added with placeholder. You can configure devices later in settings.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .create()

            // Set up Bluetooth device list
            setupBluetoothDeviceList(dialogView)
            
            dialog.show()
        }

        private fun showTimeScheduleConfigurationDialog(template: RuleTemplate) {
            // Determine which layout to use based on screen size
            val displayMetrics = binding.root.context.resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val screenWidth = displayMetrics.widthPixels
            
            // Use compact layout for very small screens
            val layoutResId = when {
                screenHeight < 600 || screenWidth < 400 -> R.layout.dialog_time_schedule_compact
                else -> R.layout.dialog_time_schedule_configuration
            }
            
            val dialogView = android.view.LayoutInflater.from(binding.root.context)
                .inflate(layoutResId, null)
            
            val dialog = androidx.appcompat.app.AlertDialog.Builder(binding.root.context)
                .setTitle("Configure ${template.name}")
                .setView(dialogView)
                .setPositiveButton("Add Rule") { _, _ ->
                    // Get selected time and days
                    val timeData = getSelectedTimeSchedule(dialogView)
                    if (timeData.isNotEmpty()) {
                        addRuleFromTemplateWithData(template, timeData)
                    } else {
                        android.widget.Toast.makeText(
                            binding.root.context,
                            "Please select time and days",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .create()

            // Set up time pickers and day selection
            setupTimeScheduleControls(dialogView)
            
            // Show dialog and then adjust its size based on screen dimensions
            dialog.show()
            
            // Ensure dialog uses adaptive sizing for different screen sizes
            val window = dialog.window
            if (window != null) {
                val dialogDisplayMetrics = binding.root.context.resources.displayMetrics
                val dialogScreenHeight = dialogDisplayMetrics.heightPixels
                val dialogScreenWidth = dialogDisplayMetrics.widthPixels
                
                // Calculate adaptive width and height based on screen size
                val width = when {
                    dialogScreenWidth < 600 -> (dialogScreenWidth * 0.98).toInt() // Very small screens: use almost full width
                    dialogScreenWidth < 800 -> (dialogScreenWidth * 0.95).toInt() // Small screens: use 95% width
                    else -> (dialogScreenWidth * 0.90).toInt() // Larger screens: use 90% width
                }
                
                // Calculate adaptive height based on screen height - more aggressive for small screens
                val maxDialogHeight = when {
                    dialogScreenHeight < 600 -> (dialogScreenHeight * 0.70).toInt() // Very small screens: limit to 70% of screen height
                    dialogScreenHeight < 800 -> (dialogScreenHeight * 0.75).toInt() // Small screens: limit to 75% of screen height
                    dialogScreenHeight < 1200 -> (dialogScreenHeight * 0.80).toInt() // Medium screens: limit to 80% of screen height
                    else -> android.view.ViewGroup.LayoutParams.WRAP_CONTENT // Larger screens: use wrap content
                }
                
                window.setLayout(width, maxDialogHeight)
                
                // Log the adaptive sizing for debugging
                InAppLogger.logDebug("OnboardingPagerAdapter", "Dialog sizing: screen=${dialogScreenWidth}x${dialogScreenHeight}, dialog=${width}x${maxDialogHeight}")
            }
        }

        private fun showWifiConfigurationDialog(template: RuleTemplate) {
            val dialogView = android.view.LayoutInflater.from(binding.root.context)
                .inflate(R.layout.dialog_wifi_configuration, null)
            
            val dialog = androidx.appcompat.app.AlertDialog.Builder(binding.root.context)
                .setTitle("Configure ${template.name}")
                .setView(dialogView)
                .setPositiveButton("Add Rule") { _, _ ->
                    // Get entered SSID
                    val ssid = getEnteredWifiSsid(dialogView)
                    if (ssid.isNotEmpty()) {
                        val customData = mapOf("network_ssids" to setOf(ssid))
                        addRuleFromTemplateWithData(template, customData)
                    } else {
                        android.widget.Toast.makeText(
                            binding.root.context,
                            "Please enter a WiFi network name",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .create()
            
            dialog.show()
        }

        private fun setupBluetoothDeviceList(dialogView: android.view.View) {
            val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerBluetoothDevices)
            val adapter = OnboardingBluetoothDeviceAdapter { _ ->
                // Handle device selection
            }
            
            recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(binding.root.context)
            recyclerView.adapter = adapter
            
            // Load available Bluetooth devices
            loadAvailableBluetoothDevices(adapter)
        }

        private fun setupTimeScheduleControls(dialogView: android.view.View) {
            val timePickerStart = dialogView.findViewById<android.widget.TimePicker>(R.id.timePickerStart)
            val timePickerEnd = dialogView.findViewById<android.widget.TimePicker>(R.id.timePickerEnd)
            val checkBoxMonday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxMonday)
            val checkBoxTuesday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxTuesday)
            val checkBoxWednesday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxWednesday)
            val checkBoxThursday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxThursday)
            val checkBoxFriday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxFriday)

            // Set default times (9 AM to 5 PM)
            timePickerStart.hour = 9
            timePickerStart.minute = 0
            timePickerEnd.hour = 17
            timePickerEnd.minute = 0

            // Set default days (Monday to Friday)
            checkBoxMonday.isChecked = true
            checkBoxTuesday.isChecked = true
            checkBoxWednesday.isChecked = true
            checkBoxThursday.isChecked = true
            checkBoxFriday.isChecked = true
        }

        private fun getSelectedBluetoothDevices(dialogView: android.view.View): Set<String> {
            // Get selected device addresses from the adapter
            val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerBluetoothDevices)
            val adapter = recyclerView.adapter as? OnboardingBluetoothDeviceAdapter
            return adapter?.getSelectedDevices() ?: emptySet()
        }

        private fun getSelectedTimeSchedule(dialogView: android.view.View): Map<String, Any> {
            val timePickerStart = dialogView.findViewById<android.widget.TimePicker>(R.id.timePickerStart)
            val timePickerEnd = dialogView.findViewById<android.widget.TimePicker>(R.id.timePickerEnd)
            val checkBoxMonday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxMonday)
            val checkBoxTuesday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxTuesday)
            val checkBoxWednesday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxWednesday)
            val checkBoxThursday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxThursday)
            val checkBoxFriday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxFriday)
            val checkBoxSaturday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxSaturday)
            val checkBoxSunday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxSunday)

            val selectedDays = mutableSetOf<Int>()
            if (checkBoxMonday.isChecked) selectedDays.add(1)
            if (checkBoxTuesday.isChecked) selectedDays.add(2)
            if (checkBoxWednesday.isChecked) selectedDays.add(3)
            if (checkBoxThursday.isChecked) selectedDays.add(4)
            if (checkBoxFriday.isChecked) selectedDays.add(5)
            if (checkBoxSaturday.isChecked) selectedDays.add(6)
            if (checkBoxSunday.isChecked) selectedDays.add(7)

            return mapOf(
                "startHour" to timePickerStart.hour,
                "startMinute" to timePickerStart.minute,
                "endHour" to timePickerEnd.hour,
                "endMinute" to timePickerEnd.minute,
                "selectedDays" to selectedDays
            )
        }

        private fun getEnteredWifiSsid(dialogView: android.view.View): String {
            // Get entered SSID from the EditText
            val editText = dialogView.findViewById<android.widget.EditText>(R.id.editWifiSsid)
            return editText.text?.toString()?.trim() ?: ""
        }

        private fun loadAvailableBluetoothDevices(adapter: OnboardingBluetoothDeviceAdapter) {
            // Load available Bluetooth devices (permission already checked)
            val bluetoothManager = binding.root.context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                try {
                    val pairedDevices = bluetoothAdapter.bondedDevices
                    adapter.updateDevices(pairedDevices.toList())
                    InAppLogger.log("OnboardingBluetooth", "Loaded ${pairedDevices.size} Bluetooth devices")
                } catch (e: SecurityException) {
                    InAppLogger.logError("OnboardingBluetooth", "Security exception accessing Bluetooth devices: ${e.message}")
                    android.widget.Toast.makeText(
                        binding.root.context,
                        "Permission denied to access Bluetooth devices",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                android.widget.Toast.makeText(
                    binding.root.context,
                    "Bluetooth is not available or enabled",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        private fun addRuleFromTemplateWithData(template: RuleTemplate, customData: Map<String, Any>) {
            try {
                // Check if this is a WiFi rule with specific networks and we can't resolve SSIDs
                val hasWifiTrigger = template.triggers.any { it.type == TriggerType.WIFI_NETWORK }
                val hasSpecificNetworks = (customData["network_ssids"] as? Collection<*>)?.any { (it as? String)?.isNotBlank() == true } == true
                
                if (hasWifiTrigger && hasSpecificNetworks) {
                    val canResolve = WifiCapabilityChecker.canResolveWifiSSID(binding.root.context)
                    if (!canResolve) {
                        // Warn only when SSID resolution isn’t possible on this device/context
                        showWifiCompatibilityWarningBeforeCreationOnboarding(template, customData)
                        return
                    } else {
                        InAppLogger.logDebug("OnboardingRuleTemplates", "WiFi SSID resolution available; skipping compatibility warning.")
                    }
                }
                
                // Create a rule from the template with custom data
                val rule = com.micoyc.speakthat.rules.RuleTemplates.createRuleFromTemplate(template, customData)
                
                // Get the rule manager and add the rule
                val ruleManager = com.micoyc.speakthat.rules.RuleManager(binding.root.context)
                ruleManager.addRule(rule)
                
                // Enable Conditional Rules if it's not already enabled
                if (!ruleManager.isRulesEnabled()) {
                    val automationModeManager = AutomationModeManager(binding.root.context)
                    automationModeManager.setMode(AutomationMode.CONDITIONAL_RULES)
                    InAppLogger.log("OnboardingRuleTemplates", "Enabled Conditional Rules feature")
                }
                
                InAppLogger.log("OnboardingRuleTemplates", "Added configured rule from template: ${template.name}")
                
                // Show a toast to confirm the rule was added
                android.widget.Toast.makeText(
                    binding.root.context,
                    "Added rule: ${template.name}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                InAppLogger.logError("OnboardingRuleTemplates", "Error adding configured rule from template: ${e.message}")
                
                // Show error toast
                android.widget.Toast.makeText(
                    binding.root.context,
                    "Error adding rule. Please try again.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        private fun showWifiCompatibilityWarningBeforeCreationOnboarding(template: RuleTemplate, customData: Map<String, Any>) {
            val dialog = androidx.appcompat.app.AlertDialog.Builder(binding.root.context)
                .setTitle(binding.root.context.getString(R.string.wifi_compatibility_warning_title))
                .setMessage(binding.root.context.getString(R.string.wifi_compatibility_warning_message))
                .setPositiveButton(binding.root.context.getString(R.string.wifi_compatibility_warning_add_anyway)) { _, _ ->
                    // Create the rule with the original data (user wants to proceed)
                    addRuleFromTemplateWithDataInternal(template, customData)
                }
                .setNegativeButton(binding.root.context.getString(R.string.wifi_compatibility_warning_nevermind), null)
                .setNeutralButton(binding.root.context.getString(R.string.wifi_compatibility_warning_open_github)) { _, _ ->
                    // Open GitHub link
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/mitchib1440/SpeakThat"))
                    binding.root.context.startActivity(intent)
                }
                .create()
            
            dialog.show()
        }
        
        private fun addRuleFromTemplateWithDataInternal(template: RuleTemplate, customData: Map<String, Any>) {
            try {
                // Create a rule from the template with custom data
                val rule = com.micoyc.speakthat.rules.RuleTemplates.createRuleFromTemplate(template, customData)
                
                // Get the rule manager and add the rule
                val ruleManager = com.micoyc.speakthat.rules.RuleManager(binding.root.context)
                ruleManager.addRule(rule)
                
                // Enable Conditional Rules if it's not already enabled
                if (!ruleManager.isRulesEnabled()) {
                    val automationModeManager = AutomationModeManager(binding.root.context)
                    automationModeManager.setMode(AutomationMode.CONDITIONAL_RULES)
                    InAppLogger.log("OnboardingRuleTemplates", "Enabled Conditional Rules feature")
                }
                
                InAppLogger.log("OnboardingRuleTemplates", "Added configured rule from template: ${template.name}")
                
                // Show a toast to confirm the rule was added
                android.widget.Toast.makeText(
                    binding.root.context,
                    "Added rule: ${template.name}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                
            } catch (e: Exception) {
                InAppLogger.logError("OnboardingRuleTemplates", "Error adding configured rule from template: ${e.message}")
                
                // Show error toast
                android.widget.Toast.makeText(
                    binding.root.context,
                    "Error adding rule. Please try again.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    data class OnboardingPage(
        val titleResId: Int,
        val descriptionResId: Int,
        val icon: String,
        val showPermissionButton: Boolean = false,
        val showAppSelector: Boolean = false,
        val showWordSelector: Boolean = false,
        val showRuleTemplates: Boolean = false,
        val showLanguageSelector: Boolean = false,
        val showThemeSelector: Boolean = false
    )
}