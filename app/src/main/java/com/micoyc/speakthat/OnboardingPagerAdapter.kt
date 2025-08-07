package com.micoyc.speakthat

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.recyclerview.widget.RecyclerView
import com.micoyc.speakthat.databinding.ItemOnboardingPageBinding
import com.micoyc.speakthat.rules.RuleTemplate
import com.micoyc.speakthat.rules.TriggerConfigActivity
import com.micoyc.speakthat.rules.TriggerType

class OnboardingPagerAdapter(
    private val skipPermissionPage: Boolean = false
) : RecyclerView.Adapter<OnboardingPagerAdapter.OnboardingViewHolder>() {
    
    private val pages = if (skipPermissionPage) listOf(
        OnboardingPage(
            title = "Welcome to SpeakThat!",
            description = "Stay connected without staring at your screen. Your phone will read notifications aloud so you can focus on what matters.",
            icon = "🔊"
        ),
        OnboardingPage(
            title = "Your Privacy Matters",
            description = "🔒 You control exactly what gets read aloud\n\n📱 Shake your phone to stop any announcement instantly\n\n⚙️ Filter apps, words, and notification types\n\n🔐 Everything stays on your device",
            icon = "🔒"
        ),
        OnboardingPage(
            title = "Let's Set Up Basic Filters",
            description = "SpeakThat features a powerful filtering system that allows YOU to choose what gets read and what doesn't.\n\nLet's set up some basic privacy filters to get you started.",
            icon = "⚙️"
        ),
        OnboardingPage(
            title = "Block Sensitive Apps",
            description = "Type the name of an app you want me to NEVER read notifications from.\n\nCommon examples: banking apps, medical apps, dating apps",
            icon = "🚫",
            showAppSelector = true
        ),
        OnboardingPage(
            title = "Block Sensitive Words",
            description = "Type words or phrases that you want me to NEVER read notifications containing!\n\nCommon examples: password, PIN, credit card, medical terms",
            icon = "🔇",
            showWordSelector = true
        ),
        OnboardingPage(
            title = "Set Up Smart Rules",
            description = "Set up smart rules that automatically control when notifications are read based on your situation.\n\nFor example, only read notifications when headphones are connected, or skip notifications when the screen is on.",
            icon = "🧠",
            showRuleTemplates = true
        ),
        OnboardingPage(
            title = "You're All Set!",
            description = "SpeakThat is ready to help you stay connected while keeping your eyes free!\n\n💡 You can add more filters anytime in the app settings.",
            icon = "✅"
        )
    ) else listOf(
        OnboardingPage(
            title = "Welcome to SpeakThat!",
            description = "Stay connected without staring at your screen. Your phone will read notifications aloud so you can focus on what matters.",
            icon = "🔊"
        ),
        OnboardingPage(
            title = "Notification Access Required",
            description = "SpeakThat needs permission to read your notifications aloud.\n\n🔒 Everything stays on your device\n📱 No data is sent to us or anyone else\n⚙️ You control what gets read\n💬 We only read what you allow\n💡 Tip: In the list, tap 'SpeakThat' to enable",
            icon = "🔔",
            showPermissionButton = true
        ),
        OnboardingPage(
            title = "Your Privacy Matters",
            description = "🔒 You control exactly what gets read aloud\n\n📱 Shake your phone to stop any announcement instantly\n\n⚙️ Filter apps, words, and notification types\n\n🔐 Everything stays on your device",
            icon = "🔒"
        ),
        OnboardingPage(
            title = "Let's Set Up Basic Filters",
            description = "SpeakThat features a powerful filtering system that allows YOU to choose what gets read and what doesn't.\n\nLet's set up some basic privacy filters to get you started.",
            icon = "⚙️"
        ),
        OnboardingPage(
            title = "Block Sensitive Apps",
            description = "Type the name of an app you want me to NEVER read notifications from.\n\nCommon examples: banking apps, medical apps, dating apps",
            icon = "🚫",
            showAppSelector = true
        ),
        OnboardingPage(
            title = "Block Sensitive Words",
            description = "Type words or phrases that you want me to NEVER read notifications containing!\n\nCommon examples: password, PIN, credit card, medical terms",
            icon = "🔇",
            showWordSelector = true
        ),
        OnboardingPage(
            title = "Set Up Smart Rules",
            description = "Set up smart rules that automatically control when notifications are read based on your situation.\n\nFor example, only read notifications when headphones are connected, or skip notifications when the screen is on.",
            icon = "🧠",
            showRuleTemplates = true
        ),
        OnboardingPage(
            title = "You're All Set!",
            description = "SpeakThat is ready to help you stay connected while keeping your eyes free!\n\n💡 You can add more filters anytime in the app settings.",
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
    
    class OnboardingViewHolder(private val binding: ItemOnboardingPageBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(page: OnboardingPage) {
            android.util.Log.d("OnboardingPagerAdapter", "Binding page: ${page.title}")
            binding.textTitle.text = page.title
            binding.textDescription.text = page.description
            binding.textIcon.text = page.icon
            
            // Show/hide permission button based on page
            if (page.showPermissionButton) {
                binding.buttonPermission.visibility = android.view.View.VISIBLE
                binding.buttonPermission.text = "Open Notification Settings"
                binding.buttonPermission.setOnClickListener {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    binding.root.context.startActivity(intent)
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
        }
        
        private fun setupAppSelector() {
            // Initialize app search adapter
            val appSearchAdapter = LazyAppSearchAdapter(binding.root.context)
            binding.editAppName.setAdapter(appSearchAdapter)
            binding.editAppName.threshold = 1 // Show suggestions after 1 character
            
            // Handle app selection from dropdown
            binding.editAppName.setOnItemClickListener { _, _, position, _ ->
                val selectedApp = appSearchAdapter.getItem(position)
                if (selectedApp != null) {
                    binding.editAppName.setText(selectedApp.packageName)
                    binding.editAppName.setSelection(selectedApp.packageName.length)
                    InAppLogger.log("OnboardingAppSelector", "Selected app: ${selectedApp.appName} (${selectedApp.packageName})")
                }
            }
            
            // Set up add button
            binding.buttonAddApp.setOnClickListener {
                val input = binding.editAppName.text?.toString()?.trim() ?: ""
                if (input.isNotEmpty()) {
                    addAppToBlacklist(input)
                    binding.editAppName.text?.clear()
                }
            }
            
            // Set up RecyclerView for selected apps
            val selectedAppsAdapter = OnboardingAppListAdapter { packageName ->
                removeAppFromBlacklist(packageName)
            }
            val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(binding.root.context)
            binding.recyclerSelectedApps.layoutManager = layoutManager
            binding.recyclerSelectedApps.adapter = selectedAppsAdapter
            binding.recyclerSelectedApps.setHasFixedSize(true)
            
            // Load existing apps
            loadSelectedApps(selectedAppsAdapter)
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
            binding.recyclerSelectedWords.setHasFixedSize(true)
            
            // Load existing words
            loadSelectedWords(selectedWordsAdapter)
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
                            
                            // Skip system apps and our own app
                            if (packageName == binding.root.context.packageName || 
                                packageName.startsWith("com.android.") ||
                                packageName.startsWith("android.") ||
                                packageName.startsWith("com.samsung.") ||
                                packageName.startsWith("com.sec.")) {
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
        }
        
        private fun removeAppFromBlacklist(packageName: String) {
            val sharedPreferences = binding.root.context.getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
            val currentApps = sharedPreferences.getStringSet("app_list", LinkedHashSet())?.toMutableSet() ?: mutableSetOf()
            
            if (currentApps.contains(packageName)) {
                currentApps.remove(packageName)
                sharedPreferences.edit().putStringSet("app_list", currentApps).apply()
                
                InAppLogger.log("OnboardingAppSelector", "Removed app '$packageName' from blacklist")
                
                // Refresh the RecyclerView
                val adapter = binding.recyclerSelectedApps.adapter as? OnboardingAppListAdapter
                adapter?.updateApps(currentApps.toList())
            }
        }
        
        private fun loadSelectedApps(adapter: OnboardingAppListAdapter) {
            val sharedPreferences = binding.root.context.getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
            val currentApps = sharedPreferences.getStringSet("app_list", LinkedHashSet()) ?: LinkedHashSet()
            adapter.updateApps(currentApps.toList())
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
                     onTemplateConfigured = { template, customData ->
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
                 binding.recyclerRuleTemplates.setHasFixedSize(true)

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
                    
                    // Enable Conditional Rules if it's not already enabled
                    val sharedPreferences = binding.root.context.getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
                    if (!sharedPreferences.getBoolean("conditional_rules_enabled", false)) {
                        sharedPreferences.edit().putBoolean("conditional_rules_enabled", true).apply()
                        InAppLogger.log("OnboardingRuleTemplates", "Enabled Conditional Rules feature")
                    }
                    
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
                val dialogView = android.view.LayoutInflater.from(binding.root.context)
                    .inflate(R.layout.dialog_time_schedule_configuration, null)
                
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
                
                dialog.show()
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
                val adapter = OnboardingBluetoothDeviceAdapter { device ->
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
                val checkBoxSaturday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxSaturday)
                val checkBoxSunday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxSunday)

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
                val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
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
                    // Create a rule from the template with custom data
                    val rule = com.micoyc.speakthat.rules.RuleTemplates.createRuleFromTemplate(template, customData)
                    
                    // Get the rule manager and add the rule
                    val ruleManager = com.micoyc.speakthat.rules.RuleManager(binding.root.context)
                    ruleManager.addRule(rule)
                    
                    // Enable Conditional Rules if it's not already enabled
                    val sharedPreferences = binding.root.context.getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
                    if (!sharedPreferences.getBoolean("conditional_rules_enabled", false)) {
                        sharedPreferences.edit().putBoolean("conditional_rules_enabled", true).apply()
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
        val title: String,
        val description: String,
        val icon: String,
        val showPermissionButton: Boolean = false,
        val showAppSelector: Boolean = false,
        val showWordSelector: Boolean = false,
        val showRuleTemplates: Boolean = false
    )
} 