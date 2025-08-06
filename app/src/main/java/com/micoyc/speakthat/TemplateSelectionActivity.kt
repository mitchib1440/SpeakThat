package com.micoyc.speakthat

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.databinding.ActivityTemplateSelectionBinding
import com.micoyc.speakthat.databinding.ItemTemplateBinding
import com.micoyc.speakthat.rules.RuleManager
import com.micoyc.speakthat.rules.RuleTemplates
import com.micoyc.speakthat.rules.RuleTemplate
import android.view.LayoutInflater
import android.view.ViewGroup

class TemplateSelectionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityTemplateSelectionBinding
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private lateinit var ruleManager: RuleManager
    private lateinit var adapter: TemplateAdapter
    
    companion object {
        private const val PREFS_NAME = "SpeakThatPrefs"
        private const val KEY_DARK_MODE = "dark_mode"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme FIRST before anything else
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        applySavedTheme()
        
        super.onCreate(savedInstanceState)
        binding = ActivityTemplateSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Choose a Template or Create Custom Rule"
        
        // Initialize rule manager
        ruleManager = RuleManager(this)
        
        setupRecyclerView()
        setupButtons()
    }
    
    private fun applySavedTheme() {
        val isDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
    
    private fun setupRecyclerView() {
        adapter = TemplateAdapter(
            templates = RuleTemplates.getAllTemplates(this),
            onTemplateSelected = { template -> handleTemplateSelection(template) }
        )
        
        binding.recyclerTemplates.apply {
            layoutManager = LinearLayoutManager(this@TemplateSelectionActivity)
            adapter = this@TemplateSelectionActivity.adapter
        }
        
        InAppLogger.logDebug("TemplateSelectionActivity", "RecyclerView initialized with ${RuleTemplates.getAllTemplates(this).size} templates")
    }
    
    private fun setupButtons() {
        binding.btnCreateCustomRule.setOnClickListener {
            // Go to the regular rule builder
            startActivity(Intent(this, RuleBuilderActivity::class.java))
            InAppLogger.logUserAction("Create custom rule button clicked")
        }
    }
    
    private fun handleTemplateSelection(template: RuleTemplate) {
        InAppLogger.logDebug("TemplateSelectionActivity", "Template selected: ${template.name}")
        
        if (template.requiresDeviceSelection) {
            // Handle device selection based on type
            when (template.deviceType) {
                "bluetooth" -> handleBluetoothDeviceSelection(template)
                "wifi" -> handleWifiNetworkSelection(template)
                "time_schedule" -> handleTimeScheduleSelection(template)
                else -> createRuleFromTemplate(template)
            }
        } else {
            // No device selection needed, create rule directly
            createRuleFromTemplate(template)
        }
    }
    
    private fun handleBluetoothDeviceSelection(template: RuleTemplate) {
        InAppLogger.logDebug("TemplateSelectionActivity", "Starting Bluetooth device selection")
        
        // Check Bluetooth permissions and availability
        if (!checkBluetoothPermissions()) {
            InAppLogger.logDebug("TemplateSelectionActivity", "Bluetooth permissions not granted, requesting...")
            requestBluetoothPermissions()
            return
        }
        
        InAppLogger.logDebug("TemplateSelectionActivity", "Bluetooth permissions OK")
        
        try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            InAppLogger.logDebug("TemplateSelectionActivity", "BluetoothManager obtained")
            
            val bluetoothAdapter = bluetoothManager.adapter
            InAppLogger.logDebug("TemplateSelectionActivity", "BluetoothAdapter: ${bluetoothAdapter != null}")
            
            if (bluetoothAdapter == null) {
                InAppLogger.logError("TemplateSelectionActivity", "Bluetooth adapter is null")
                showErrorDialog("Bluetooth is not available on this device.")
                return
            }
            
            InAppLogger.logDebug("TemplateSelectionActivity", "Bluetooth enabled: ${bluetoothAdapter.isEnabled}")
            
            if (!bluetoothAdapter.isEnabled) {
                InAppLogger.logDebug("TemplateSelectionActivity", "Bluetooth is disabled")
                showErrorDialog("Please enable Bluetooth to select devices.")
                return
            }
            
            val bondedDevices = bluetoothAdapter.bondedDevices.toList()
            InAppLogger.logDebug("TemplateSelectionActivity", "Found ${bondedDevices.size} bonded devices")
            
            if (bondedDevices.isEmpty()) {
                InAppLogger.logDebug("TemplateSelectionActivity", "No bonded devices found")
                showErrorDialog("No paired Bluetooth devices found. Please pair some devices first.")
                return
            }
            
            // Log device names for debugging
            bondedDevices.forEachIndexed { index, device ->
                InAppLogger.logDebug("TemplateSelectionActivity", "Device $index: ${device.name} (${device.address})")
            }
            
            // Create device selection dialog
            val deviceNames = bondedDevices.map { device ->
                device.name ?: "Unknown Device (${device.address})"
            }.toTypedArray()
            
            InAppLogger.logDebug("TemplateSelectionActivity", "Showing device selection dialog with ${deviceNames.size} devices")
            InAppLogger.logDebug("TemplateSelectionActivity", "Device names array: ${deviceNames.joinToString(", ")}")
            
            // Check if deviceNames array is empty or has empty strings
            if (deviceNames.isEmpty()) {
                InAppLogger.logError("TemplateSelectionActivity", "Device names array is empty!")
                showErrorDialog("No Bluetooth devices found. Please pair some devices first.")
                return
            }
            
            // Check for empty device names
            val emptyNames = deviceNames.count { it.isEmpty() || it == "Unknown Device ()" }
            InAppLogger.logDebug("TemplateSelectionActivity", "Empty device names: $emptyNames")
            
            // Create a custom dialog with ListView instead of setItems
            val dialogView = LayoutInflater.from(this).inflate(android.R.layout.select_dialog_item, null)
            val listView = android.widget.ListView(this).apply {
                adapter = android.widget.ArrayAdapter(this@TemplateSelectionActivity, android.R.layout.simple_list_item_1, deviceNames)
                setOnItemClickListener { _, _, position, _ ->
                    InAppLogger.logDebug("TemplateSelectionActivity", "User selected device at index $position")
                    val selectedDevice = bondedDevices[position]
                    InAppLogger.logDebug("TemplateSelectionActivity", "Selected device: ${selectedDevice.name} (${selectedDevice.address})")
                    val deviceName = selectedDevice.name ?: "Unknown Device"
                    val deviceDisplayText = "$deviceName (${selectedDevice.address})"
                    
                    createRuleFromTemplate(template, mapOf(
                        "device_addresses" to setOf(selectedDevice.address),
                        "device_display_text" to deviceDisplayText
                    ))
                }
            }
            
            AlertDialog.Builder(this)
                .setTitle("Select Bluetooth Device")
                .setMessage("Choose the Bluetooth device for this rule:")
                .setView(listView)
                .setNegativeButton("Cancel") { _, _ ->
                    InAppLogger.logDebug("TemplateSelectionActivity", "User cancelled Bluetooth device selection")
                }
                .setOnDismissListener {
                    InAppLogger.logDebug("TemplateSelectionActivity", "Bluetooth device selection dialog dismissed")
                }
                .show()
                
        } catch (e: Exception) {
            InAppLogger.logError("TemplateSelectionActivity", "Error handling Bluetooth device selection: ${e.message}")
            InAppLogger.logError("TemplateSelectionActivity", "Exception stack trace: ${e.stackTraceToString()}")
            showErrorDialog("Error accessing Bluetooth devices. Please try again.")
        }
    }
    
    private fun handleWifiNetworkSelection(template: RuleTemplate) {
        try {
            val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
            
            if (!wifiManager.isWifiEnabled) {
                showErrorDialog("Please enable WiFi to select networks.")
                return
            }
            
            // Get configured networks (saved WiFi networks)
            val configuredNetworks = wifiManager.configuredNetworks
            
            if (configuredNetworks == null || configuredNetworks.isEmpty()) {
                // Fallback to manual input if no networks detected
                showManualWifiInputDialog(template)
                return
            }
            
            // Create network selection dialog
            val networkNames = configuredNetworks.map { network ->
                network.SSID?.removeSurrounding("\"") ?: "Unknown Network"
            }.toTypedArray()
            
            AlertDialog.Builder(this)
                .setTitle("Select WiFi Network")
                .setMessage("Choose the WiFi network for this rule:")
                .setItems(networkNames) { _, which ->
                    val selectedNetwork = configuredNetworks[which]
                    val networkSsid = selectedNetwork.SSID?.removeSurrounding("\"") ?: "Unknown Network"
                    createRuleFromTemplate(template, mapOf(
                        "ssid" to networkSsid,
                        "networkId" to selectedNetwork.networkId
                    ))
                }
                .setNegativeButton("Manual Input") { _, _ ->
                    showManualWifiInputDialog(template)
                }
                .setNeutralButton("Cancel", null)
                .show()
                
        } catch (e: Exception) {
            InAppLogger.logError("TemplateSelectionActivity", "Error handling WiFi network selection: ${e.message}")
            showManualWifiInputDialog(template)
        }
    }
    
    private fun showManualWifiInputDialog(template: RuleTemplate) {
        val input = android.widget.EditText(this).apply {
            hint = "Enter WiFi network name (SSID)"
            setSingleLine()
        }
        
        AlertDialog.Builder(this)
            .setTitle("Enter WiFi Network")
            .setMessage("Enter the name of your WiFi network:")
            .setView(input)
            .setPositiveButton("Create Rule") { _, _ ->
                val ssid = input.text.toString().trim()
                if (ssid.isNotEmpty()) {
                    createRuleFromTemplate(template, mapOf(
                        "ssid" to ssid,
                        "networkId" to -1 // Unknown network ID
                    ))
                } else {
                    showErrorDialog("Please enter a WiFi network name.")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun handleTimeScheduleSelection(template: RuleTemplate) {
        // Create a custom dialog for time and day selection
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_time_schedule, null)
        
        // Set up time pickers
        val timePickerStart = dialogView.findViewById<android.widget.TimePicker>(R.id.timePickerStart)
        val timePickerEnd = dialogView.findViewById<android.widget.TimePicker>(R.id.timePickerEnd)
        
        // Set default times (10 PM to 8 AM)
        timePickerStart.hour = 22
        timePickerStart.minute = 0
        timePickerEnd.hour = 8
        timePickerEnd.minute = 0
        
        // Set up day checkboxes
        val checkBoxMonday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxMonday)
        val checkBoxTuesday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxTuesday)
        val checkBoxWednesday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxWednesday)
        val checkBoxThursday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxThursday)
        val checkBoxFriday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxFriday)
        val checkBoxSaturday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxSaturday)
        val checkBoxSunday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxSunday)
        val checkBoxEveryDay = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxEveryDay)
        
        // Set up "Every Day" checkbox to control all others
        checkBoxEveryDay.setOnCheckedChangeListener { _, isChecked ->
            checkBoxMonday.isChecked = isChecked
            checkBoxTuesday.isChecked = isChecked
            checkBoxWednesday.isChecked = isChecked
            checkBoxThursday.isChecked = isChecked
            checkBoxFriday.isChecked = isChecked
            checkBoxSaturday.isChecked = isChecked
            checkBoxSunday.isChecked = isChecked
        }
        
        // Create dialog with proper sizing for time pickers
        val dialog = AlertDialog.Builder(this)
            .setTitle("Set Quiet Hours")
            .setMessage("Choose the time range and days when you don't want notifications read:")
            .setView(dialogView)
            .setPositiveButton("Create Rule") { _, _ ->
                // Collect selected days
                val selectedDays = mutableSetOf<Int>()
                if (checkBoxMonday.isChecked) selectedDays.add(1)
                if (checkBoxTuesday.isChecked) selectedDays.add(2)
                if (checkBoxWednesday.isChecked) selectedDays.add(3)
                if (checkBoxThursday.isChecked) selectedDays.add(4)
                if (checkBoxFriday.isChecked) selectedDays.add(5)
                if (checkBoxSaturday.isChecked) selectedDays.add(6)
                if (checkBoxSunday.isChecked) selectedDays.add(7)
                
                // Create custom data for the rule
                val customData = mapOf(
                    "startHour" to timePickerStart.hour,
                    "startMinute" to timePickerStart.minute,
                    "endHour" to timePickerEnd.hour,
                    "endMinute" to timePickerEnd.minute,
                    "selectedDays" to selectedDays
                )
                
                createRuleFromTemplate(template, customData)
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        // Show dialog and then adjust its width to use more screen space
        dialog.show()
        
        // Ensure dialog uses adequate width for time pickers
        val window = dialog.window
        if (window != null) {
            val displayMetrics = resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.95).toInt() // Use 95% of screen width
            val height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            window.setLayout(width, height)
        }
    }
    
    private fun createRuleFromTemplate(template: RuleTemplate, customData: Map<String, Any> = emptyMap()) {
        try {
            val rule = RuleTemplates.createRuleFromTemplate(template, customData)
            
            // Add the rule to the rule manager
            val success = ruleManager.addRule(rule)
            
            if (success) {
                InAppLogger.logUserAction("Rule created from template: ${template.name}")
                
                // Show success message and finish
                AlertDialog.Builder(this)
                    .setTitle("Rule Created!")
                    .setMessage("Your rule '${template.name}' has been created successfully.")
                    .setPositiveButton("OK") { _, _ ->
                        finish()
                    }
                    .show()
            } else {
                showErrorDialog("Failed to create rule. Please try again.")
            }
            
        } catch (e: Exception) {
            InAppLogger.logError("TemplateSelectionActivity", "Error creating rule from template: ${e.message}")
            showErrorDialog("Error creating rule. Please try again.")
        }
    }
    
    private fun checkBluetoothPermissions(): Boolean {
        // Check if we have the necessary Bluetooth permissions
        return checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestBluetoothPermissions() {
        requestPermissions(
            arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT),
            1001
        )
    }
    
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

/**
 * Adapter for displaying rule templates in a RecyclerView
 */
class TemplateAdapter(
    private val templates: List<RuleTemplate>,
    private val onTemplateSelected: (RuleTemplate) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<TemplateAdapter.TemplateViewHolder>() {
    
    class TemplateViewHolder(val binding: ItemTemplateBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplateViewHolder {
        val binding = ItemTemplateBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TemplateViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: TemplateViewHolder, position: Int) {
        val template = templates[position]
        
        holder.binding.apply {
            textIcon.text = template.icon
            textTitle.text = template.name
            textDescription.text = template.description
            
            // Set click listener
            root.setOnClickListener {
                onTemplateSelected(template)
            }
        }
    }
    
    override fun getItemCount(): Int = templates.size
} 