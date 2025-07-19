package com.micoyc.speakthat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.micoyc.speakthat.databinding.ItemOnboardingPageBinding

class OnboardingPagerAdapter(private val skipPermissionPage: Boolean = false) : RecyclerView.Adapter<OnboardingPagerAdapter.OnboardingViewHolder>() {
    
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
    }
    
    data class OnboardingPage(
        val title: String,
        val description: String,
        val icon: String,
        val showPermissionButton: Boolean = false,
        val showAppSelector: Boolean = false,
        val showWordSelector: Boolean = false
    )
} 