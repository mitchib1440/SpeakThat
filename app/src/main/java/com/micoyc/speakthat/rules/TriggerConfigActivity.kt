package com.micoyc.speakthat.rules

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.micoyc.speakthat.AccessibilityUtils
import com.micoyc.speakthat.AppListManager
import com.micoyc.speakthat.AppPickerActivity
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.R
import com.micoyc.speakthat.databinding.ActivityTriggerConfigBinding
import com.micoyc.speakthat.utils.WifiCapabilityChecker
import java.text.SimpleDateFormat
import java.util.*

class TriggerConfigActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityTriggerConfigBinding
    private var triggerType: TriggerType? = null
    private var originalTrigger: Trigger? = null
    private var isEditing = false

    private val selectedForegroundApps = mutableListOf<String>()
    private lateinit var foregroundAppPickerLauncher: ActivityResultLauncher<Intent>
    private val selectedNotificationFromApps = mutableListOf<String>()
    private lateinit var notificationFromPickerLauncher: ActivityResultLauncher<Intent>
    

    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTriggerConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply saved theme
        applySavedTheme()
        
        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Configure Trigger"
        
        // Get intent data
        triggerType = intent.getSerializableExtraCompat(EXTRA_TRIGGER_TYPE)
        isEditing = intent.getBooleanExtra(EXTRA_IS_EDITING, false)
        
        if (isEditing) {
            val triggerData = intent.getStringExtra(EXTRA_TRIGGER_DATA)
            originalTrigger = Trigger.fromJson(triggerData ?: "")
        }
        
        setupForegroundAppPickerLauncher()
        setupNotificationFromPickerLauncher()
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
        when (triggerType) {
            TriggerType.BATTERY_PERCENTAGE -> setupBatteryPercentageUI()
            TriggerType.CHARGING_STATUS -> setupChargingStatusUI()
            TriggerType.DEVICE_UNLOCKED -> setupDeviceUnlockedUI()
            TriggerType.NOTIFICATION_CONTAINS -> setupNotificationContainsUI()
            TriggerType.NOTIFICATION_FROM -> setupNotificationFromUI()
            TriggerType.FOREGROUND_APP -> setupForegroundAppUI()
            TriggerType.SCREEN_ORIENTATION -> setupScreenOrientationUI()
            TriggerType.SCREEN_STATE -> setupScreenStateUI()
            TriggerType.TIME_SCHEDULE -> setupTimeScheduleUI()
            TriggerType.BLUETOOTH_DEVICE -> setupBluetoothUI()
            TriggerType.WIRED_HEADPHONES -> setupWiredHeadphonesUI()
            TriggerType.WIFI_NETWORK -> setupWifiUI()
            else -> {
                InAppLogger.logError("TriggerConfigActivity", "Unknown trigger type: $triggerType")
                finish()
            }
        }
        
        // Set up save button
        binding.btnSave.setOnClickListener {
            saveTrigger()
        }
        
        // Set up cancel button
        binding.btnCancel.setOnClickListener {
            finish()
        }
    }
    
    private fun setupScreenStateUI() {
        binding.cardScreenState.visibility = View.VISIBLE
        
        // Set up screen state options
        val screenStateOptions = arrayOf("Screen is ON", "Screen is OFF")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, screenStateOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerScreenState.adapter = adapter
    }

    private fun setupBatteryPercentageUI() {
        binding.cardBatteryPercentage.visibility = View.VISIBLE

        val modeOptions = arrayOf(
            getString(R.string.trigger_battery_mode_above),
            getString(R.string.trigger_battery_mode_below)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modeOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerBatteryMode.adapter = adapter

        binding.sliderBatteryPercentage.addOnChangeListener { _, value, _ ->
            updateBatteryPercentageDisplay(value.toInt())
        }

        updateBatteryPercentageDisplay(binding.sliderBatteryPercentage.value.toInt())
    }

    private fun setupChargingStatusUI() {
        binding.cardChargingStatus.visibility = View.VISIBLE

        val statusOptions = arrayOf(
            getString(R.string.trigger_battery_status_charging),
            getString(R.string.trigger_battery_status_discharging)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, statusOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerChargingStatus.adapter = adapter
    }

    private fun setupNotificationContainsUI() {
        binding.cardNotificationContains.visibility = View.VISIBLE
    }

    private fun setupNotificationFromUI() {
        binding.cardNotificationFrom.visibility = View.VISIBLE
        binding.textNotificationFromDescription.text = getString(R.string.trigger_notification_from_description)
        binding.btnManageNotificationFromApps.setOnClickListener {
            openNotificationFromAppPicker()
        }
        updateNotificationFromSummary()
    }

    private fun setupForegroundAppUI() {
        binding.cardForegroundApp.visibility = View.VISIBLE
        binding.textForegroundAppDescription.text = getString(R.string.trigger_foreground_app_description)
        binding.btnManageForegroundApps.setOnClickListener {
            if (!AccessibilityUtils.isAccessibilityServiceEnabled(this)) {
                Toast.makeText(
                    this,
                    getString(R.string.trigger_foreground_app_accessibility_required),
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            openForegroundAppPicker()
        }
        updateForegroundAppSummary()
    }

    private fun setupDeviceUnlockedUI() {
        binding.cardDeviceUnlocked.visibility = View.VISIBLE
        val modeOptions = arrayOf(
            getString(R.string.trigger_device_unlocked_mode_unlocked),
            getString(R.string.trigger_device_unlocked_mode_locked)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modeOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDeviceUnlockedMode.adapter = adapter
    }

    private fun setupScreenOrientationUI() {
        binding.cardScreenOrientation.visibility = View.VISIBLE
        val modeOptions = arrayOf(
            getString(R.string.trigger_screen_orientation_portrait),
            getString(R.string.trigger_screen_orientation_landscape)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modeOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerScreenOrientation.adapter = adapter
    }
    
    private fun setupTimeScheduleUI() {
        binding.cardTimeSchedule.visibility = View.VISIBLE
        
        // Set up time pickers
        binding.btnStartTime.setOnClickListener {
            showTimePickerDialog(true)
        }
        
        binding.btnEndTime.setOnClickListener {
            showTimePickerDialog(false)
        }
        
        // Set up day selection
        setupDaySelection()
    }
    
    private fun setupBluetoothUI() {
        binding.cardBluetooth.visibility = View.VISIBLE
        
        // Set up Bluetooth options
        binding.switchAnyDevice.setOnCheckedChangeListener { _, isChecked ->
            binding.editDeviceAddresses.isEnabled = !isChecked
            if (isChecked) {
                binding.editDeviceAddresses.setText("")
            }
        }
        
        binding.btnSelectDevices.setOnClickListener {
            showBluetoothDeviceSelection()
        }
    }
    
    private fun setupWiredHeadphonesUI() {
        binding.cardWiredHeadphones.visibility = View.VISIBLE
        
        // Set up connection state options
        val connectionStateOptions = arrayOf(
            getString(R.string.trigger_wired_headphones_disconnected),
            getString(R.string.trigger_wired_headphones_connected)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, connectionStateOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerWiredHeadphonesConnectionState.adapter = adapter
        // Default to "Disconnected" (index 0)
        binding.spinnerWiredHeadphonesConnectionState.setSelection(0)
    }
    
    private fun setupWifiUI() {
        binding.cardWifi.visibility = View.VISIBLE
        
        // Set up WiFi options
        binding.switchAnyNetwork.setOnCheckedChangeListener { _, isChecked ->
            binding.editNetworkSSIDs.isEnabled = !isChecked
            if (isChecked) {
                binding.editNetworkSSIDs.setText("")
            }
        }
        
        binding.btnSelectNetworks.setOnClickListener {
            showWifiNetworkSelection()
        }
    }

    private fun setupForegroundAppPickerLauncher() {
        foregroundAppPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val selected = result.data?.getStringArrayListExtra(
                    AppPickerActivity.EXTRA_SELECTED_PACKAGES
                ) ?: arrayListOf()
                selectedForegroundApps.clear()
                selectedForegroundApps.addAll(selected)
                updateForegroundAppSummary()
            }
        }
    }

    private fun setupNotificationFromPickerLauncher() {
        notificationFromPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val selected = result.data?.getStringArrayListExtra(
                    AppPickerActivity.EXTRA_SELECTED_PACKAGES
                ) ?: arrayListOf()
                selectedNotificationFromApps.clear()
                selectedNotificationFromApps.addAll(selected)
                updateNotificationFromSummary()
            }
        }
    }

    private fun openNotificationFromAppPicker() {
        val selectedPackages = ArrayList(selectedNotificationFromApps)
        val intent = AppPickerActivity.createIntent(
            this,
            getString(R.string.trigger_notification_from_title),
            selectedPackages,
            arrayListOf(),
            false
        )
        notificationFromPickerLauncher.launch(intent)
    }

    private fun openForegroundAppPicker() {
        val selectedPackages = ArrayList(selectedForegroundApps)
        val intent = AppPickerActivity.createIntent(
            this,
            getString(R.string.trigger_foreground_app_title),
            selectedPackages,
            arrayListOf(),
            false
        )
        foregroundAppPickerLauncher.launch(intent)
    }

    private fun updateNotificationFromSummary() {
        val countText = getString(
            R.string.trigger_notification_from_selected_count,
            selectedNotificationFromApps.size
        )
        binding.textNotificationFromSummary.text = countText
    }

    private fun updateForegroundAppSummary() {
        val countText = getString(
            R.string.trigger_foreground_app_selected_count,
            selectedForegroundApps.size
        )
        binding.textForegroundAppsSummary.text = countText
    }
    
    private fun setupDaySelection() {
        // Set up day selection with checkboxes
        val days = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val dayValues = arrayOf(
            java.util.Calendar.MONDAY,
            java.util.Calendar.TUESDAY,
            java.util.Calendar.WEDNESDAY,
            java.util.Calendar.THURSDAY,
            java.util.Calendar.FRIDAY,
            java.util.Calendar.SATURDAY,
            java.util.Calendar.SUNDAY
        )
        
        // Create checkboxes for each day
        days.forEachIndexed { index, dayName ->
            val checkbox = android.widget.CheckBox(this).apply {
                text = dayName
                id = android.view.View.generateViewId()
                setOnCheckedChangeListener { _, isChecked ->
                    updateSelectedDays(dayValues[index], isChecked)
                }
            }
            binding.layoutDays.addView(checkbox)
        }
    }
    
    private val selectedDays = mutableSetOf<Int>()
    
    private fun updateSelectedDays(day: Int, isSelected: Boolean) {
        if (isSelected) {
            selectedDays.add(day)
        } else {
            selectedDays.remove(day)
        }
        
        updateSelectedDaysDisplay()
    }
    
    private fun updateSelectedDaysDisplay() {
        // Update the display
        val dayNames = selectedDays.map { dayValue ->
            when (dayValue) {
                java.util.Calendar.MONDAY -> "Mon"
                java.util.Calendar.TUESDAY -> "Tue"
                java.util.Calendar.WEDNESDAY -> "Wed"
                java.util.Calendar.THURSDAY -> "Thu"
                java.util.Calendar.FRIDAY -> "Fri"
                java.util.Calendar.SATURDAY -> "Sat"
                java.util.Calendar.SUNDAY -> "Sun"
                else -> ""
            }
        }
        
        binding.textSelectedDays.text = if (dayNames.isNotEmpty()) {
            dayNames.joinToString(", ")
        } else {
            "No days selected"
        }
    }
    
    // Properties to store time values
    private var startTimeMillis: Long? = null
    private var endTimeMillis: Long? = null
    
    private fun showTimePickerDialog(isStartTime: Boolean) {
        val calendar = Calendar.getInstance()
        
        // Use saved time if available, otherwise use current time
        val savedTimeMillis = if (isStartTime) startTimeMillis else endTimeMillis
        val initialHour: Int
        val initialMinute: Int
        
        if (savedTimeMillis != null && savedTimeMillis > 0) {
            // Use saved time
            initialHour = (savedTimeMillis / (60 * 60 * 1000)).toInt()
            initialMinute = ((savedTimeMillis % (60 * 60 * 1000)) / (60 * 1000)).toInt()
            InAppLogger.logDebug("TriggerConfigActivity", "Time picker: Using saved time for ${if (isStartTime) "start" else "end"}: ${String.format("%02d:%02d", initialHour, initialMinute)} (${savedTimeMillis}ms)")
        } else {
            // Use current time as default
            initialHour = calendar.get(Calendar.HOUR_OF_DAY)
            initialMinute = calendar.get(Calendar.MINUTE)
            InAppLogger.logDebug("TriggerConfigActivity", "Time picker: Using current time for ${if (isStartTime) "start" else "end"}: ${String.format("%02d:%02d", initialHour, initialMinute)}")
        }
        
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val timeInMillis = (hourOfDay * 60 * 60 * 1000L) + (minute * 60 * 1000L)
                val timeString = String.format("%02d:%02d", hourOfDay, minute)
                
                if (isStartTime) {
                    binding.textStartTime.text = timeString
                    startTimeMillis = timeInMillis
                    InAppLogger.logDebug("TriggerConfigActivity", "Start time set to: $timeString (${timeInMillis}ms)")
                } else {
                    binding.textEndTime.text = timeString
                    endTimeMillis = timeInMillis
                    InAppLogger.logDebug("TriggerConfigActivity", "End time set to: $timeString (${timeInMillis}ms)")
                }
            },
            initialHour,
            initialMinute,
            true
        ).show()
    }
    
    private fun showBluetoothDeviceSelection() {
        // Check and request permissions first
        if (!checkBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }
        
        try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter
            
            if (bluetoothAdapter == null) {
                AlertDialog.Builder(this)
                    .setTitle("Bluetooth Not Available")
                    .setMessage("Bluetooth is not available on this device.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }
            
            if (!bluetoothAdapter.isEnabled) {
                AlertDialog.Builder(this)
                    .setTitle("Bluetooth Disabled")
                    .setMessage("Please enable Bluetooth to select devices.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }
            
            val bondedDevices = bluetoothAdapter.bondedDevices.toList()
            
            if (bondedDevices.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("No Devices Found")
                    .setMessage("No paired Bluetooth devices found. Please pair some devices first.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }
            
            // Create device list with names and addresses
            val deviceItems = bondedDevices.map { device ->
                "${device.name ?: "Unknown Device"} (${device.address})"
            }.toTypedArray()
            
            val selectedIndices = mutableSetOf<Int>()
            
            AlertDialog.Builder(this)
                .setTitle("Select Bluetooth Devices")
                .setMultiChoiceItems(deviceItems, null) { _, which, isChecked ->
                    if (isChecked) {
                        selectedIndices.add(which)
                    } else {
                        selectedIndices.remove(which)
                    }
                }
                .setPositiveButton("OK") { _, _ ->
                    val selectedDevices = selectedIndices.map { index ->
                        bondedDevices[index]
                    }
                    
                    val deviceInfo = selectedDevices.map { device ->
                        "${device.name ?: "Unknown"} (${device.address})"
                    }
                    
                    binding.editDeviceAddresses.setText(deviceInfo.joinToString("\n"))
                    
                    // Also store just the addresses for the trigger data
                    val addresses = selectedDevices.map { it.address }.toSet()
                    binding.editDeviceAddresses.tag = addresses
                }
                .setNegativeButton("Cancel", null)
                .show()
                
        } catch (e: Throwable) {
            InAppLogger.logError("TriggerConfigActivity", "Error showing Bluetooth devices: ${e.message}")
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("Failed to load Bluetooth devices: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
    }
    
    private fun showWifiNetworkSelection() {
        // Check and request permissions first
        if (!checkWifiPermissions()) {
            requestWifiPermissions()
            return
        }
        
        try {
            InAppLogger.log("TriggerConfig", "Starting WiFi network selection")
            val wifiManager = applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            
            if (!wifiManager.isWifiEnabled) {
                InAppLogger.log("TriggerConfig", "WiFi is disabled - showing dialog")
                AlertDialog.Builder(this)
                    .setTitle("WiFi Disabled")
                    .setMessage("Please enable WiFi to select networks.")
                    .setPositiveButton("OK") { _, _ ->
                        InAppLogger.logUserAction("WiFi disabled dialog dismissed")
                    }
                    .show()
                return
            }
            
            // Try to get available networks using different approaches
            val availableNetworks = mutableListOf<String>()
            
            // Method 1: Try to get configured networks (works on older Android versions)
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                InAppLogger.log("TriggerConfig", "Using configured networks method for WiFi detection")
                @Suppress("DEPRECATION")
                val configuredNetworks = wifiManager.configuredNetworks
                if (configuredNetworks != null) {
                    availableNetworks.addAll(configuredNetworks.map { network ->
                        @Suppress("DEPRECATION")
                        network.SSID.removeSurrounding("\"")
                    })
                    InAppLogger.log("TriggerConfig", "Found ${availableNetworks.size} configured WiFi networks")
                }
            }
            
            // Method 2: Try to get scan results (requires location permission on newer versions)
            if (availableNetworks.isEmpty()) {
                InAppLogger.log("TriggerConfig", "Using scan results method for WiFi detection")
                try {
                    val scanResults = wifiManager.scanResults
                    if (scanResults.isNotEmpty()) {
                        availableNetworks.addAll(scanResults.map { result ->
                            @Suppress("DEPRECATION")
                            val ssid = result.SSID
                            ssid.removeSurrounding("\"")
                        }.distinct())
                        InAppLogger.log("TriggerConfig", "Found ${availableNetworks.size} WiFi networks from scan results")
                    } else {
                        InAppLogger.log("TriggerConfig", "No WiFi networks found in scan results")
                    }
                } catch (e: SecurityException) {
                    InAppLogger.logDebug("TriggerConfigActivity", "Cannot access scan results: ${e.message}")
                }
            }
            
            // Method 3: If still no networks, show manual entry dialog
            if (availableNetworks.isEmpty()) {
                InAppLogger.log("TriggerConfig", "No WiFi networks detected - showing manual entry dialog")
                AlertDialog.Builder(this)
                    .setTitle("WiFi Networks")
                    .setMessage("Unable to automatically detect WiFi networks. Please manually enter WiFi network names (SSIDs) one per line.\n\nNote: You can also try enabling location services and granting location permission to automatically detect nearby networks.")
                    .setPositiveButton("OK") { _, _ ->
                        InAppLogger.logUserAction("WiFi manual entry dialog - OK clicked")
                        binding.editNetworkSSIDs.requestFocus()
                    }
                    .show()
                return
            }
            
            // Remove duplicates and empty entries
            val uniqueNetworks = availableNetworks.filter { it.isNotEmpty() }.distinct().sorted()
            
            if (uniqueNetworks.isEmpty()) {
                InAppLogger.log("TriggerConfig", "No valid WiFi networks found after filtering")
                AlertDialog.Builder(this)
                    .setTitle("No Networks Found")
                    .setMessage("No WiFi networks found. Please connect to some networks first or manually enter network names.")
                    .setPositiveButton("OK") { _, _ ->
                        InAppLogger.logUserAction("No WiFi networks found dialog dismissed")
                    }
                    .show()
                return
            }
            
            // Create network list with SSIDs
            val networkItems = uniqueNetworks.toTypedArray()
            
            val selectedIndices = mutableSetOf<Int>()
            
            AlertDialog.Builder(this)
                .setTitle("Select WiFi Networks")
                .setMultiChoiceItems(networkItems, null) { _, which, isChecked ->
                    if (isChecked) {
                        selectedIndices.add(which)
                    } else {
                        selectedIndices.remove(which)
                    }
                }
                .setPositiveButton("OK") { _, _ ->
                    val selectedNetworks = selectedIndices.map { index ->
                        networkItems[index]
                    }
                    
                    binding.editNetworkSSIDs.setText(selectedNetworks.joinToString("\n"))
                    
                    // Also store the SSIDs for the trigger data
                    val ssids = selectedNetworks.toSet()
                    binding.editNetworkSSIDs.tag = ssids
                }
                .setNegativeButton("Cancel", null)
                .show()
                
        } catch (e: Throwable) {
            InAppLogger.logError("TriggerConfigActivity", "Error showing WiFi networks: ${e.message}")
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("Failed to load WiFi networks. Please manually enter network names.")
                .setPositiveButton("OK") { _, _ ->
                    binding.editNetworkSSIDs.requestFocus()
                }
                .show()
        }
    }
    
    private fun loadCurrentValues() {
        originalTrigger?.let { trigger ->
            // Load inversion state for supported trigger types
            binding.switchInvertTimeSchedule.isChecked = trigger.inverted
            binding.switchInvertBluetooth.isChecked = trigger.inverted
            binding.switchInvertWifi.isChecked = trigger.inverted
            
            when (trigger.type) {
                TriggerType.BATTERY_PERCENTAGE -> {
                    val mode = trigger.data["mode"] as? String ?: "above"
                    val percentageRaw = trigger.data["percentage"]
                    val percentage = when (percentageRaw) {
                        is Int -> percentageRaw
                        is Long -> percentageRaw.toInt()
                        is Double -> percentageRaw.toInt()
                        is Float -> percentageRaw.toInt()
                        is Number -> percentageRaw.toInt()
                        is String -> percentageRaw.toIntOrNull()
                        else -> null
                    } ?: 50

                    val modeIndex = if (mode == "below") 1 else 0
                    binding.spinnerBatteryMode.setSelection(modeIndex)
                    binding.sliderBatteryPercentage.value = percentage.toFloat()
                    updateBatteryPercentageDisplay(percentage)
                }
                TriggerType.CHARGING_STATUS -> {
                    val status = trigger.data["status"] as? String ?: "charging"
                    val statusIndex = if (status == "discharging") 1 else 0
                    binding.spinnerChargingStatus.setSelection(statusIndex)
                }
                TriggerType.DEVICE_UNLOCKED -> {
                    val mode = trigger.data["mode"] as? String ?: "unlocked"
                    val modeIndex = if (mode == "locked") 1 else 0
                    binding.spinnerDeviceUnlockedMode.setSelection(modeIndex)
                }
                TriggerType.NOTIFICATION_CONTAINS -> {
                    val phrase = trigger.data["phrase"] as? String ?: ""
                    val caseSensitive = trigger.data["case_sensitive"] as? Boolean ?: false
                    binding.editNotificationPhrase.setText(phrase)
                    binding.checkNotificationCaseSensitive.isChecked = caseSensitive
                    binding.switchInvertNotificationContains.isChecked = trigger.inverted
                }
                TriggerType.NOTIFICATION_FROM -> {
                    val packagesData = trigger.data["app_packages"]
                    val packages = when (packagesData) {
                        is Set<*> -> packagesData.filterIsInstance<String>()
                        is List<*> -> packagesData.filterIsInstance<String>()
                        else -> emptyList()
                    }
                    selectedNotificationFromApps.clear()
                    selectedNotificationFromApps.addAll(packages)
                    binding.switchInvertNotificationFrom.isChecked = trigger.inverted
                    updateNotificationFromSummary()
                }
                TriggerType.FOREGROUND_APP -> {
                    val packagesData = trigger.data["app_packages"]
                    val packages = when (packagesData) {
                        is Set<*> -> packagesData.filterIsInstance<String>()
                        is List<*> -> packagesData.filterIsInstance<String>()
                        else -> emptyList()
                    }
                    selectedForegroundApps.clear()
                    selectedForegroundApps.addAll(packages)
                    binding.switchInvertForegroundApp.isChecked = trigger.inverted
                    updateForegroundAppSummary()
                }
                TriggerType.SCREEN_ORIENTATION -> {
                    val mode = trigger.data["mode"] as? String ?: "portrait"
                    val modeIndex = if (mode == "landscape") 1 else 0
                    binding.spinnerScreenOrientation.setSelection(modeIndex)
                }
                TriggerType.SCREEN_STATE -> {
                    val screenState = trigger.data["screen_state"] as? String ?: "on"
                    val position = if (screenState == "on") 0 else 1
                    binding.spinnerScreenState.setSelection(position)
                }
                
                TriggerType.TIME_SCHEDULE -> {
                    InAppLogger.logDebug("TriggerConfigActivity", "Loading time schedule trigger - full trigger data: ${trigger.data}")
                    
                    // Handle different number types that might be stored
                    val startTimeRaw = trigger.data["start_time"]
                    val endTimeRaw = trigger.data["end_time"]
                    val daysOfWeekRaw = trigger.data["days_of_week"]
                    
                    val startTime = when (startTimeRaw) {
                        is Long -> startTimeRaw
                        is Double -> startTimeRaw.toLong()
                        is Int -> startTimeRaw.toLong()
                        else -> 0L
                    }
                    
                    val endTime = when (endTimeRaw) {
                        is Long -> endTimeRaw
                        is Double -> endTimeRaw.toLong()
                        is Int -> endTimeRaw.toLong()
                        else -> 0L
                    }
                    
                    val daysOfWeek = when (daysOfWeekRaw) {
                        is Set<*> -> daysOfWeekRaw.mapNotNull { 
                            when (it) {
                                is Int -> it
                                is Double -> it.toInt()
                                is Long -> it.toInt()
                                else -> null
                            }
                        }.toSet()
                        is List<*> -> daysOfWeekRaw.mapNotNull { 
                            when (it) {
                                is Int -> it
                                is Double -> it.toInt()
                                is Long -> it.toInt()
                                else -> null
                            }
                        }.toSet()
                        else -> emptySet<Int>()
                    }
                    
                    InAppLogger.logDebug("TriggerConfigActivity", "Loading time schedule trigger - startTime: ${startTime}ms, endTime: ${endTime}ms, days: $daysOfWeek")
                    
                    // Set time displays - Fix time conversion logic
                    val startHour = (startTime / (60 * 60 * 1000)).toInt()
                    val startMinute = ((startTime % (60 * 60 * 1000)) / (60 * 1000)).toInt()
                    binding.textStartTime.text = String.format("%02d:%02d", startHour, startMinute)
                    startTimeMillis = startTime
                    
                    val endHour = (endTime / (60 * 60 * 1000)).toInt()
                    val endMinute = ((endTime % (60 * 60 * 1000)) / (60 * 1000)).toInt()
                    binding.textEndTime.text = String.format("%02d:%02d", endHour, endMinute)
                    endTimeMillis = endTime
                    
                    InAppLogger.logDebug("TriggerConfigActivity", "Time display set - start: ${String.format("%02d:%02d", startHour, startMinute)}, end: ${String.format("%02d:%02d", endHour, endMinute)}")
                    
                    // Convert 0-based days from stored rule to Calendar constants (1-based) for UI
                    val convertedDays = daysOfWeek.map { day ->
                        when (day) {
                            0 -> java.util.Calendar.SUNDAY    // Sunday (0) -> Calendar.SUNDAY
                            1 -> java.util.Calendar.MONDAY    // Monday (1) -> Calendar.MONDAY
                            2 -> java.util.Calendar.TUESDAY   // Tuesday (2) -> Calendar.TUESDAY
                            3 -> java.util.Calendar.WEDNESDAY // Wednesday (3) -> Calendar.WEDNESDAY
                            4 -> java.util.Calendar.THURSDAY  // Thursday (4) -> Calendar.THURSDAY
                            5 -> java.util.Calendar.FRIDAY    // Friday (5) -> Calendar.FRIDAY
                            6 -> java.util.Calendar.SATURDAY  // Saturday (6) -> Calendar.SATURDAY
                            else -> day
                        }
                    }
                    
                    // Set selected days
                    selectedDays.clear()
                    selectedDays.addAll(convertedDays)
                    
                    InAppLogger.logDebug("TriggerConfigActivity", "Selected days loaded: $selectedDays")
                    
                    // Update checkboxes
                    val dayValues = arrayOf(
                        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
                    )
                    
                    for (i in 0 until binding.layoutDays.childCount) {
                        val checkbox = binding.layoutDays.getChildAt(i) as? android.widget.CheckBox
                        checkbox?.isChecked = selectedDays.contains(dayValues[i])
                    }
                    
                    // Update the display without clearing selection
                    updateSelectedDaysDisplay()
                }
                
                TriggerType.WIRED_HEADPHONES -> {
                    val connectionState = trigger.data["connection_state"] as? String ?: "disconnected"
                    val stateIndex = if (connectionState == "connected") 1 else 0
                    binding.spinnerWiredHeadphonesConnectionState.setSelection(stateIndex)
                }
                TriggerType.BLUETOOTH_DEVICE -> {
                    val deviceAddressesData = trigger.data["device_addresses"]
                    val deviceAddresses = when (deviceAddressesData) {
                        is Set<*> -> deviceAddressesData.mapNotNull { it as? String }.toSet()
                        is List<*> -> deviceAddressesData.mapNotNull { it as? String }.toSet()
                        else -> emptySet<String>()
                    }
                    
                    // Temporarily remove listener to prevent interference during loading
                    binding.switchAnyDevice.setOnCheckedChangeListener(null)
                    
                    if (deviceAddresses.isEmpty()) {
                        binding.switchAnyDevice.isChecked = true
                        binding.editDeviceAddresses.isEnabled = false
                    } else {
                        binding.switchAnyDevice.isChecked = false
                        binding.editDeviceAddresses.isEnabled = true
                        
                        // Try to get device names for better display
                        val deviceInfo = mutableListOf<String>()
                        try {
                            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
                            val bluetoothAdapter = bluetoothManager?.adapter
                            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                                val bondedDevices = bluetoothAdapter.bondedDevices
                                for (address in deviceAddresses) {
                                    val device = bondedDevices.find { it.address == address }
                                    if (device != null) {
                                        deviceInfo.add("${device.name ?: "Unknown"} ($address)")
                                    } else {
                                        deviceInfo.add("Unknown Device ($address)")
                                    }
                                }
                            } else {
                                // Fallback to just addresses if Bluetooth is not available
                                deviceInfo.addAll(deviceAddresses.map { "Unknown Device ($it)" })
                            }
                        } catch (e: Throwable) {
                            // Fallback to just addresses if there's an error
                            deviceInfo.addAll(deviceAddresses.map { "Unknown Device ($it)" })
                        }
                        
                        binding.editDeviceAddresses.setText(deviceInfo.joinToString("\n"))
                        // Store the addresses in tag for later retrieval
                        binding.editDeviceAddresses.tag = deviceAddresses
                    }
                    
                    // Restore the listener
                    binding.switchAnyDevice.setOnCheckedChangeListener { _, isChecked ->
                        binding.editDeviceAddresses.isEnabled = !isChecked
                        if (isChecked) {
                            binding.editDeviceAddresses.setText("")
                        }
                    }
                }
                
                TriggerType.WIFI_NETWORK -> {
                    // Handle both Set<String> and List<String> since JSON serialization converts Sets to Lists
                    val networkSSIDsData = trigger.data["network_ssids"]
                    val networkSSIDs = when (networkSSIDsData) {
                        is Set<*> -> networkSSIDsData.filterIsInstance<String>().toSet()
                        is List<*> -> networkSSIDsData.filterIsInstance<String>().toSet()
                        else -> emptySet<String>()
                    }
                    
                    // Temporarily remove listener to prevent interference during loading
                    binding.switchAnyNetwork.setOnCheckedChangeListener(null)
                    
                    if (networkSSIDs.isEmpty()) {
                        binding.switchAnyNetwork.isChecked = true
                        binding.editNetworkSSIDs.isEnabled = false
                    } else {
                        binding.switchAnyNetwork.isChecked = false
                        binding.editNetworkSSIDs.isEnabled = true
                        binding.editNetworkSSIDs.setText(networkSSIDs.joinToString("\n"))
                        // Store the SSIDs in tag for later retrieval
                        binding.editNetworkSSIDs.tag = networkSSIDs
                    }
                    
                    // Restore the listener
                    binding.switchAnyNetwork.setOnCheckedChangeListener { _, isChecked ->
                        binding.editNetworkSSIDs.isEnabled = !isChecked
                        if (isChecked) {
                            binding.editNetworkSSIDs.setText("")
                        }
                    }
                }
            }
        }
    }
    
    private fun saveTrigger() {
        val trigger = when (triggerType) {
            TriggerType.BATTERY_PERCENTAGE -> createBatteryPercentageTrigger()
            TriggerType.CHARGING_STATUS -> createChargingStatusTrigger()
            TriggerType.DEVICE_UNLOCKED -> createDeviceUnlockedTrigger()
            TriggerType.NOTIFICATION_CONTAINS -> createNotificationContainsTrigger()
            TriggerType.NOTIFICATION_FROM -> createNotificationFromTrigger()
            TriggerType.FOREGROUND_APP -> createForegroundAppTrigger()
            TriggerType.SCREEN_ORIENTATION -> createScreenOrientationTrigger()
            TriggerType.SCREEN_STATE -> createScreenStateTrigger()
            TriggerType.TIME_SCHEDULE -> createTimeScheduleTrigger()
            TriggerType.BLUETOOTH_DEVICE -> createBluetoothTrigger()
            TriggerType.WIRED_HEADPHONES -> createWiredHeadphonesTrigger()
            TriggerType.WIFI_NETWORK -> {
                val wifiTrigger = createWifiTrigger()
                
                // Check if this is a WiFi trigger with specific networks
                val networkSSIDs = (wifiTrigger.data["network_ssids"] as? Collection<*>)
                    ?.mapNotNull { it as? String }
                    ?.toSet()
                if (networkSSIDs?.isNotEmpty() == true) {
                    val canResolve = WifiCapabilityChecker.canResolveWifiSSID(this)
                    if (!canResolve) {
                        // Warn only when SSID resolution isn’t possible on this device/context
                        showWifiCompatibilityWarningBeforeSave(wifiTrigger)
                        return
                    } else {
                        InAppLogger.logDebug("TriggerConfigActivity", "WiFi SSID resolution available; skipping compatibility warning.")
                    }
                }
                
                wifiTrigger
            }
            else -> return
        }
        
        // Create a new intent for the result to avoid modifying the original intent
        val resultIntent = android.content.Intent().apply {
            putExtra(RESULT_TRIGGER, trigger.toJson())
            putExtra(EXTRA_IS_EDITING, isEditing)
        }
        setResult(RESULT_OK, resultIntent)
        
        InAppLogger.logUserAction("Trigger configured: ${trigger.getLogMessage()}", "TriggerConfigActivity")
        finish()
    }
    
    private fun showWifiCompatibilityWarningBeforeSave(trigger: Trigger) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.wifi_compatibility_warning_title))
            .setMessage(getString(R.string.wifi_compatibility_warning_message))
            .setPositiveButton(getString(R.string.wifi_compatibility_warning_create_anyway)) { _, _ ->
                // Save the trigger with the original data (user wants to proceed)
                saveTriggerInternal(trigger)
            }
            .setNegativeButton(getString(R.string.wifi_compatibility_warning_nevermind), null)
            .setNeutralButton(getString(R.string.wifi_compatibility_warning_open_github)) { _, _ ->
                // Open GitHub link
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/mitchib1440/SpeakThat"))
                startActivity(intent)
            }
            .create()
        
        dialog.show()
    }
    
    private fun saveTriggerInternal(trigger: Trigger) {
        // Create a new intent for the result to avoid modifying the original intent
        val resultIntent = android.content.Intent().apply {
            putExtra(RESULT_TRIGGER, trigger.toJson())
            putExtra(EXTRA_IS_EDITING, isEditing)
        }
        setResult(RESULT_OK, resultIntent)
        
        InAppLogger.logUserAction("Trigger configured: ${trigger.getLogMessage()}", "TriggerConfigActivity")
        finish()
    }
    
    private fun createScreenStateTrigger(): Trigger {
        val screenState = if (binding.spinnerScreenState.selectedItemPosition == 0) "on" else "off"
        val description = if (screenState == "on") "Screen is on" else "Screen is off"
        val inverted = false // Screen state inversion removed; spinner handles on/off selection
        
        return if (isEditing && originalTrigger != null) {
            // Preserve the original trigger ID when editing
            originalTrigger!!.copy(
                data = mapOf("screen_state" to screenState),
                description = description,
                inverted = inverted
            )
        } else {
            // Create new trigger
            Trigger(
                type = TriggerType.SCREEN_STATE,
                data = mapOf("screen_state" to screenState),
                description = description,
                inverted = inverted
            )
        }
    }

    private fun createBatteryPercentageTrigger(): Trigger {
        val mode = if (binding.spinnerBatteryMode.selectedItemPosition == 1) "below" else "above"
        val percentage = binding.sliderBatteryPercentage.value.toInt()
        val description = if (mode == "below") {
            getString(R.string.rule_trigger_battery_below, percentage)
        } else {
            getString(R.string.rule_trigger_battery_above, percentage)
        }

        return if (isEditing && originalTrigger != null) {
            originalTrigger!!.copy(
                data = mapOf(
                    "mode" to mode,
                    "percentage" to percentage
                ),
                description = description,
                inverted = false
            )
        } else {
            Trigger(
                type = TriggerType.BATTERY_PERCENTAGE,
                data = mapOf(
                    "mode" to mode,
                    "percentage" to percentage
                ),
                description = description,
                inverted = false
            )
        }
    }

    private fun createNotificationContainsTrigger(): Trigger {
        val phrase = binding.editNotificationPhrase.text?.toString().orEmpty().trim()
        val caseSensitive = binding.checkNotificationCaseSensitive.isChecked
        val inverted = binding.switchInvertNotificationContains.isChecked
        val description = if (inverted) {
            getString(R.string.rule_trigger_notification_not_contains, phrase)
        } else {
            getString(R.string.rule_trigger_notification_contains, phrase)
        }

        return if (isEditing && originalTrigger != null) {
            originalTrigger!!.copy(
                data = mapOf(
                    "phrase" to phrase,
                    "case_sensitive" to caseSensitive
                ),
                description = description,
                inverted = inverted
            )
        } else {
            Trigger(
                type = TriggerType.NOTIFICATION_CONTAINS,
                data = mapOf(
                    "phrase" to phrase,
                    "case_sensitive" to caseSensitive
                ),
                description = description,
                inverted = inverted
            )
        }
    }

    private fun createNotificationFromTrigger(): Trigger {
        val selectedPackages = selectedNotificationFromApps.toSet()
        val inverted = binding.switchInvertNotificationFrom.isChecked
        val description = if (selectedPackages.size == 1) {
            val packageName = selectedPackages.first()
            val appName = AppListManager.findAppByPackage(this, packageName)?.displayName ?: packageName
            getString(R.string.rule_trigger_notification_from_single, appName)
        } else {
            getString(R.string.rule_trigger_notification_from_multiple, selectedPackages.size)
        }.let { base ->
            if (inverted) getString(R.string.rule_trigger_notification_from_not, base) else base
        }

        return if (isEditing && originalTrigger != null) {
            originalTrigger!!.copy(
                data = mapOf("app_packages" to selectedPackages),
                description = description,
                inverted = inverted
            )
        } else {
            Trigger(
                type = TriggerType.NOTIFICATION_FROM,
                data = mapOf("app_packages" to selectedPackages),
                description = description,
                inverted = inverted
            )
        }
    }

    private fun createForegroundAppTrigger(): Trigger {
        val selectedPackages = selectedForegroundApps.toSet()
        val inverted = binding.switchInvertForegroundApp.isChecked
        val description = if (selectedPackages.size == 1) {
            val packageName = selectedPackages.first()
            val appName = AppListManager.findAppByPackage(this, packageName)?.displayName ?: packageName
            getString(R.string.rule_trigger_foreground_app_single, appName)
        } else {
            getString(R.string.rule_trigger_foreground_app_multiple, selectedPackages.size)
        }.let { base ->
            if (inverted) getString(R.string.rule_trigger_foreground_app_not, base) else base
        }

        return if (isEditing && originalTrigger != null) {
            originalTrigger!!.copy(
                data = mapOf("app_packages" to selectedPackages),
                description = description,
                inverted = inverted
            )
        } else {
            Trigger(
                type = TriggerType.FOREGROUND_APP,
                data = mapOf("app_packages" to selectedPackages),
                description = description,
                inverted = inverted
            )
        }
    }

    private fun createDeviceUnlockedTrigger(): Trigger {
        val mode = if (binding.spinnerDeviceUnlockedMode.selectedItemPosition == 1) "locked" else "unlocked"
        val description = if (mode == "locked") {
            getString(R.string.rule_trigger_device_locked)
        } else {
            getString(R.string.rule_trigger_device_unlocked)
        }

        return if (isEditing && originalTrigger != null) {
            originalTrigger!!.copy(
                data = mapOf("mode" to mode),
                description = description,
                inverted = false
            )
        } else {
            Trigger(
                type = TriggerType.DEVICE_UNLOCKED,
                data = mapOf("mode" to mode),
                description = description,
                inverted = false
            )
        }
    }

    private fun createScreenOrientationTrigger(): Trigger {
        val mode = if (binding.spinnerScreenOrientation.selectedItemPosition == 1) "landscape" else "portrait"
        val description = if (mode == "landscape") {
            getString(R.string.rule_trigger_orientation_landscape)
        } else {
            getString(R.string.rule_trigger_orientation_portrait)
        }

        return if (isEditing && originalTrigger != null) {
            originalTrigger!!.copy(
                data = mapOf("mode" to mode),
                description = description,
                inverted = false
            )
        } else {
            Trigger(
                type = TriggerType.SCREEN_ORIENTATION,
                data = mapOf("mode" to mode),
                description = description,
                inverted = false
            )
        }
    }

    private fun createChargingStatusTrigger(): Trigger {
        val status = if (binding.spinnerChargingStatus.selectedItemPosition == 1) "discharging" else "charging"
        val description = if (status == "discharging") {
            getString(R.string.rule_trigger_battery_discharging)
        } else {
            getString(R.string.rule_trigger_battery_charging)
        }

        return if (isEditing && originalTrigger != null) {
            originalTrigger!!.copy(
                data = mapOf("status" to status),
                description = description,
                inverted = false
            )
        } else {
            Trigger(
                type = TriggerType.CHARGING_STATUS,
                data = mapOf("status" to status),
                description = description,
                inverted = false
            )
        }
    }
    
    private fun createTimeScheduleTrigger(): Trigger {
        val startTime = startTimeMillis ?: 0L
        val endTime = endTimeMillis ?: 0L
        
        // Convert Calendar constants (1-based) to 0-based days for RuleEvaluator
        val daysOfWeek = selectedDays.map { day ->
            when (day) {
                java.util.Calendar.MONDAY -> 1    // Monday stays 1
                java.util.Calendar.TUESDAY -> 2   // Tuesday stays 2
                java.util.Calendar.WEDNESDAY -> 3 // Wednesday stays 3
                java.util.Calendar.THURSDAY -> 4  // Thursday stays 4
                java.util.Calendar.FRIDAY -> 5    // Friday stays 5
                java.util.Calendar.SATURDAY -> 6  // Saturday stays 6
                java.util.Calendar.SUNDAY -> 0    // Sunday becomes 0
                else -> day
            }
        }.toSet()
        
        val startHour = (startTime / (60 * 60 * 1000)).toInt()
        val startMinute = ((startTime % (60 * 60 * 1000)) / (60 * 1000)).toInt()
        val endHour = (endTime / (60 * 60 * 1000)).toInt()
        val endMinute = ((endTime % (60 * 60 * 1000)) / (60 * 1000)).toInt()
        
        val description = "Time: ${String.format("%02d:%02d", startHour, startMinute)} - ${String.format("%02d:%02d", endHour, endMinute)}"
        val inverted = binding.switchInvertTimeSchedule.isChecked
        
        return if (isEditing && originalTrigger != null) {
            // Preserve the original trigger ID when editing
            originalTrigger!!.copy(
                data = mapOf(
                    "start_time" to startTime,
                    "end_time" to endTime,
                    "days_of_week" to daysOfWeek
                ),
                description = description,
                inverted = inverted
            )
        } else {
            // Create new trigger
            Trigger(
                type = TriggerType.TIME_SCHEDULE,
                data = mapOf(
                    "start_time" to startTime,
                    "end_time" to endTime,
                    "days_of_week" to daysOfWeek
                ),
                description = description,
                inverted = inverted
            )
        }
    }

    private fun updateBatteryPercentageDisplay(percentage: Int) {
        binding.textBatteryPercentageValue.text = getString(
            R.string.trigger_battery_percentage_value,
            percentage
        )
    }
    
    private fun createBluetoothTrigger(): Trigger {
        val deviceAddresses = if (binding.switchAnyDevice.isChecked) {
            emptySet<String>()
        } else {
            // Use stored addresses from tag if available, otherwise parse from text
            val storedAddresses = (binding.editDeviceAddresses.tag as? Set<*>)
                ?.filterIsInstance<String>()
                ?.toSet()
            if (storedAddresses != null) {
                storedAddresses
            } else {
                binding.editDeviceAddresses.text.toString()
                    .split("\n")
                    .filter { it.isNotBlank() }
                    .map { line ->
                        // Extract address from "Name (Address)" format
                        val match = Regex("\\(([0-9A-Fa-f:]+)\\)").find(line)
                        match?.groupValues?.get(1) ?: line
                    }
                    .toSet()
            }
        }
        
        val description = if (deviceAddresses.isEmpty()) {
            "Any Bluetooth device connected"
        } else {
            "Specific Bluetooth devices: ${deviceAddresses.joinToString(", ")}"
        }
        val inverted = binding.switchInvertBluetooth.isChecked
        
        return if (isEditing && originalTrigger != null) {
            // Preserve the original trigger ID when editing
            originalTrigger!!.copy(
                data = mapOf("device_addresses" to deviceAddresses),
                description = description,
                inverted = inverted
            )
        } else {
            // Create new trigger
            Trigger(
                type = TriggerType.BLUETOOTH_DEVICE,
                data = mapOf("device_addresses" to deviceAddresses),
                description = description,
                inverted = inverted
            )
        }
    }
    
    private fun createWiredHeadphonesTrigger(): Trigger {
        val connectionState = if (binding.spinnerWiredHeadphonesConnectionState.selectedItemPosition == 1) {
            "connected"
        } else {
            "disconnected"
        }
        
        val description = if (connectionState == "connected") {
            getString(R.string.rule_trigger_wired_headphones_connected)
        } else {
            getString(R.string.rule_trigger_wired_headphones_disconnected)
        }
        
        return if (isEditing && originalTrigger != null) {
            originalTrigger!!.copy(
                type = TriggerType.WIRED_HEADPHONES,
                data = mapOf("connection_state" to connectionState),
                description = description,
                inverted = false
            )
        } else {
            Trigger(
                type = TriggerType.WIRED_HEADPHONES,
                data = mapOf("connection_state" to connectionState),
                description = description,
                inverted = false
            )
        }
    }
    
    private fun createWifiTrigger(): Trigger {
        val networkSSIDs = if (binding.switchAnyNetwork.isChecked) {
            emptySet<String>()
        } else {
            // Use stored SSIDs from tag if available, otherwise parse from text
            val storedSSIDs = (binding.editNetworkSSIDs.tag as? Set<*>)
                ?.filterIsInstance<String>()
                ?.toSet()
            if (storedSSIDs != null) {
                storedSSIDs
            } else {
                binding.editNetworkSSIDs.text.toString()
                    .split("\n")
                    .filter { it.isNotBlank() }
                    .toSet()
            }
        }
        
        val description = if (networkSSIDs.isEmpty()) {
            "Connected to any WiFi network"
        } else {
            "Connected to: ${networkSSIDs.joinToString(", ")}"
        }
        val inverted = binding.switchInvertWifi.isChecked
        
        return if (isEditing && originalTrigger != null) {
            // Preserve the original trigger ID when editing
            originalTrigger!!.copy(
                data = mapOf("network_ssids" to networkSSIDs),
                description = description,
                inverted = inverted
            )
        } else {
            // Create new trigger
            Trigger(
                type = TriggerType.WIFI_NETWORK,
                data = mapOf("network_ssids" to networkSSIDs),
                description = description,
                inverted = inverted
            )
        }
    }
    
    // Permission handling methods
    private fun checkBluetoothPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(android.Manifest.permission.BLUETOOTH) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADMIN) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun checkWifiPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.NEARBY_WIFI_DEVICES) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestBluetoothPermissions() {
        InAppLogger.log("TriggerConfig", "Requesting Bluetooth permissions")
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        
        requestPermissions(permissions, REQUEST_BLUETOOTH_PERMISSIONS)
    }
    
    private fun requestWifiPermissions() {
        InAppLogger.log("TriggerConfig", "Requesting WiFi permissions")
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
        
        when (requestCode) {
            REQUEST_BLUETOOTH_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                    InAppLogger.logDebug("TriggerConfigActivity", "Bluetooth permissions granted")
                    showBluetoothDeviceSelection()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Permission Required")
                        .setMessage("Bluetooth permissions are required to select devices.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            REQUEST_WIFI_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                    InAppLogger.logDebug("TriggerConfigActivity", "WiFi permissions granted")
                    showWifiNetworkSelection()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Permission Required")
                        .setMessage("WiFi permissions are required to select networks.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }
    
    companion object {
        const val EXTRA_TRIGGER_TYPE = "trigger_type"
        const val EXTRA_TRIGGER_DATA = "trigger_data"
        const val EXTRA_IS_EDITING = "is_editing"
        const val RESULT_TRIGGER = "result_trigger"
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 2001
        private const val REQUEST_WIFI_PERMISSIONS = 2002
    }
    
    override fun onSupportNavigateUp(): Boolean {
        @Suppress("DEPRECATION")
        onBackPressed()
        return true
    }
} 