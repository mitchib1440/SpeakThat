package com.micoyc.speakthat.rules

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
import com.micoyc.speakthat.databinding.ActivityExceptionConfigBinding
import com.micoyc.speakthat.utils.WifiCapabilityChecker
import com.google.gson.Gson
import java.util.*

class ExceptionConfigActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExceptionConfigBinding
    private var exceptionType: ExceptionType? = null
    private var originalException: Exception? = null
    private var isEditing = false
    
    // Time schedule variables
    private var startTimeMillis: Long? = null
    private var endTimeMillis: Long? = null
    private val selectedDays = mutableSetOf<Int>()

    private val selectedForegroundApps = mutableListOf<String>()
    private lateinit var foregroundAppPickerLauncher: ActivityResultLauncher<Intent>
    private val selectedNotificationFromApps = mutableListOf<String>()
    private lateinit var notificationFromPickerLauncher: ActivityResultLauncher<Intent>

    companion object {
        const val EXTRA_EXCEPTION_TYPE = "exception_type"
        const val EXTRA_EXCEPTION_DATA = "exception_data"
        const val EXTRA_IS_EDITING = "is_editing"
        const val RESULT_EXCEPTION = "result_exception"
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 2001
        private const val REQUEST_WIFI_PERMISSIONS = 2002
        private const val PREFS_NAME = "SpeakThatPrefs"
        private const val KEY_DARK_MODE = "dark_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExceptionConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        applySavedTheme()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Configure Exception"
        
        exceptionType = intent.getSerializableExtraCompat(EXTRA_EXCEPTION_TYPE)
        isEditing = intent.getBooleanExtra(EXTRA_IS_EDITING, false)
        
        if (isEditing) {
            val exceptionData = intent.getStringExtra(EXTRA_EXCEPTION_DATA)
            originalException = Exception.fromJson(exceptionData ?: "")
        }

        setupForegroundAppPickerLauncher()
        setupNotificationFromPickerLauncher()
        setupUI()
        loadCurrentValues()
    }

    private fun applySavedTheme() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean(KEY_DARK_MODE, true) // Default to dark mode
        val desiredMode = if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        
        // Only set the night mode if it's different from the current mode
        // This prevents unnecessary configuration changes that cause activity recreation loops
        if (currentMode != desiredMode) {
            AppCompatDelegate.setDefaultNightMode(desiredMode)
        }
    }

    private fun setupUI() {
        when (exceptionType) {
            ExceptionType.BATTERY_PERCENTAGE -> setupBatteryPercentageUI()
            ExceptionType.CHARGING_STATUS -> setupChargingStatusUI()
            ExceptionType.DEVICE_UNLOCKED -> setupDeviceUnlockedUI()
            ExceptionType.NOTIFICATION_CONTAINS -> setupNotificationContainsUI()
            ExceptionType.NOTIFICATION_FROM -> setupNotificationFromUI()
            ExceptionType.FOREGROUND_APP -> setupForegroundAppUI()
            ExceptionType.SCREEN_ORIENTATION -> setupScreenOrientationUI()
            ExceptionType.SCREEN_STATE -> setupScreenStateUI()
            ExceptionType.TIME_SCHEDULE -> setupTimeScheduleUI()
            ExceptionType.BLUETOOTH_DEVICE -> setupBluetoothUI()
            ExceptionType.WIRED_HEADPHONES -> setupWiredHeadphonesUI()
            ExceptionType.WIFI_NETWORK -> setupWifiUI()
            else -> {
                InAppLogger.logError("ExceptionConfigActivity", "Unknown exception type: $exceptionType")
                finish()
            }
        }

        // Set up save button
        binding.buttonSave.setOnClickListener {
            saveException()
        }

        // Set up cancel button
        binding.buttonCancel.setOnClickListener {
            finish()
        }
    }

    private fun setupScreenStateUI() {
        binding.cardScreenState.visibility = View.VISIBLE
        
        // Set up screen state spinner
        val screenStates = arrayOf("Screen is on", "Screen is off")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, screenStates)
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
        setupDaySelection()
        
        // Set up time pickers
        binding.buttonStartTime.setOnClickListener {
            showTimePickerDialog(true)
        }
        
        binding.buttonEndTime.setOnClickListener {
            showTimePickerDialog(false)
        }
    }

    private fun setupBluetoothUI() {
        binding.cardBluetooth.visibility = View.VISIBLE
        
        // Set up connection state spinner
        val connectionStateOptions = arrayOf(
            getString(R.string.exception_bluetooth_connected),
            getString(R.string.exception_bluetooth_disconnected)
        )
        val stateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, connectionStateOptions)
        stateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerBluetoothConnectionState.adapter = stateAdapter
        // Default to "Connected" (index 0)
        binding.spinnerBluetoothConnectionState.setSelection(0)
        
        // Set up any device switch
        binding.switchAnyDevice.setOnCheckedChangeListener { _, isChecked ->
            binding.editDeviceAddresses.isEnabled = !isChecked
            if (isChecked) {
                binding.editDeviceAddresses.setText("")
            }
        }
        
        // Set up select devices button
        binding.buttonSelectDevices.setOnClickListener {
            showBluetoothDeviceSelection()
        }
    }
    
    private fun setupWiredHeadphonesUI() {
        binding.cardWiredHeadphones.visibility = View.VISIBLE
        
        // Set up connection state options
        val connectionStateOptions = arrayOf(
            getString(R.string.exception_wired_headphones_disconnected),
            getString(R.string.exception_wired_headphones_connected)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, connectionStateOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerWiredHeadphonesConnectionState.adapter = adapter
        // Default to "Disconnected" (index 0)
        binding.spinnerWiredHeadphonesConnectionState.setSelection(0)
    }
    
    private fun setupWifiUI() {
        binding.cardWifi.visibility = View.VISIBLE
        
        // Set up connection state spinner
        val connectionStateOptions = arrayOf(
            getString(R.string.exception_wifi_connected),
            getString(R.string.exception_wifi_disconnected)
        )
        val stateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, connectionStateOptions)
        stateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerWifiConnectionState.adapter = stateAdapter
        // Default to "Connected" (index 0)
        binding.spinnerWifiConnectionState.setSelection(0)
        
        // Set up any network switch
        binding.switchAnyNetwork.setOnCheckedChangeListener { _, isChecked ->
            binding.editNetworkSSIDs.isEnabled = !isChecked
            if (isChecked) {
                binding.editNetworkSSIDs.setText("")
            }
        }
        
        // Set up select networks button
        binding.buttonSelectNetworks.setOnClickListener {
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
        val days = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val dayValues = arrayOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )

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

    private fun updateSelectedDays(day: Int, isSelected: Boolean) {
        if (isSelected) {
            selectedDays.add(day)
        } else {
            selectedDays.remove(day)
        }
        
        val dayNames = selectedDays.map { dayValue ->
            when (dayValue) {
                Calendar.MONDAY -> "Mon"
                Calendar.TUESDAY -> "Tue"
                Calendar.WEDNESDAY -> "Wed"
                Calendar.THURSDAY -> "Thu"
                Calendar.FRIDAY -> "Fri"
                Calendar.SATURDAY -> "Sat"
                Calendar.SUNDAY -> "Sun"
                else -> ""
            }
        }
        
        binding.textSelectedDays.text = if (dayNames.isNotEmpty()) {
            dayNames.joinToString(", ")
        } else {
            "No days selected"
        }
    }

    private fun showTimePickerDialog(isStartTime: Boolean) {
        val currentTime = if (isStartTime) startTimeMillis else endTimeMillis
        val hour = if (currentTime != null) (currentTime / (60 * 60 * 1000)).toInt() else 0
        val minute = if (currentTime != null) ((currentTime % (60 * 60 * 1000)) / (60 * 1000)).toInt() else 0

        android.app.TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val timeMillis = (selectedHour * 60 * 60 * 1000 + selectedMinute * 60 * 1000).toLong()
                if (isStartTime) {
                    startTimeMillis = timeMillis
                    binding.textStartTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
                } else {
                    endTimeMillis = timeMillis
                    binding.textEndTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
                }
            },
            hour,
            minute,
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
                    
                    // Store addresses in tag for later retrieval
                    val addresses = selectedDevices.map { it.address }.toSet()
                    binding.editDeviceAddresses.tag = addresses
                }
                .setNegativeButton("Cancel", null)
                .show()
                
        } catch (e: Throwable) {
            InAppLogger.logError("ExceptionConfigActivity", "Error showing Bluetooth devices: ${e.message}")
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
            InAppLogger.log("ExceptionConfig", "Starting WiFi network selection")
            val wifiManager = applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            
            if (!wifiManager.isWifiEnabled) {
                InAppLogger.log("ExceptionConfig", "WiFi is disabled - showing dialog")
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
                @Suppress("DEPRECATION")
                val configuredNetworks = wifiManager.configuredNetworks
                if (configuredNetworks != null) {
                    availableNetworks.addAll(configuredNetworks.map { network ->
                        @Suppress("DEPRECATION")
                        network.SSID.removeSurrounding("\"")
                    })
                }
            }
            
            // Method 2: Try to get scan results (requires location permission on newer versions)
            if (availableNetworks.isEmpty()) {
                try {
                    val scanResults = wifiManager.scanResults
                    if (scanResults.isNotEmpty()) {
                        availableNetworks.addAll(scanResults.map { result ->
                            @Suppress("DEPRECATION")
                            val ssid = result.SSID
                            ssid.removeSurrounding("\"")
                        }.distinct())
                    }
                } catch (e: SecurityException) {
                    InAppLogger.logDebug("ExceptionConfigActivity", "Cannot access scan results: ${e.message}")
                }
            }
            
            // Method 3: If still no networks, show manual entry dialog
            if (availableNetworks.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("WiFi Networks")
                    .setMessage("Unable to automatically detect WiFi networks. Please manually enter WiFi network names (SSIDs) one per line.\n\nNote: You can also try enabling location services and granting location permission to automatically detect nearby networks.")
                    .setPositiveButton("OK") { _, _ ->
                        binding.editNetworkSSIDs.requestFocus()
                    }
                    .show()
                return
            }
            
            // Remove duplicates and empty entries
            val uniqueNetworks = availableNetworks.filter { it.isNotEmpty() }.distinct().sorted()
            
            if (uniqueNetworks.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("No Networks Found")
                    .setMessage("No WiFi networks found. Please connect to some networks first or manually enter network names.")
                    .setPositiveButton("OK", null)
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
                    
                    // Store SSIDs in tag for later retrieval
                    val ssids = selectedNetworks.toSet()
                    binding.editNetworkSSIDs.tag = ssids
                }
                .setNegativeButton("Cancel", null)
                .show()
                
        } catch (e: Throwable) {
            InAppLogger.logError("ExceptionConfigActivity", "Error showing WiFi networks: ${e.message}")
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
        originalException?.let { exception ->
            // Load inversion state for supported exception types (only TIME_SCHEDULE still uses inversion)
            binding.switchInvertTimeSchedule.isChecked = exception.inverted
            
            when (exception.type) {
                ExceptionType.BATTERY_PERCENTAGE -> {
                    val mode = exception.data["mode"] as? String ?: "above"
                    val percentageRaw = exception.data["percentage"]
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
                ExceptionType.CHARGING_STATUS -> {
                    val status = exception.data["status"] as? String ?: "charging"
                    val statusIndex = if (status == "discharging") 1 else 0
                    binding.spinnerChargingStatus.setSelection(statusIndex)
                }
                ExceptionType.DEVICE_UNLOCKED -> {
                    val mode = exception.data["mode"] as? String ?: "unlocked"
                    val modeIndex = if (mode == "locked") 1 else 0
                    binding.spinnerDeviceUnlockedMode.setSelection(modeIndex)
                }
                ExceptionType.NOTIFICATION_CONTAINS -> {
                    val phrase = exception.data["phrase"] as? String ?: ""
                    val caseSensitive = exception.data["case_sensitive"] as? Boolean ?: false
                    binding.editNotificationPhrase.setText(phrase)
                    binding.checkNotificationCaseSensitive.isChecked = caseSensitive
                    binding.switchInvertNotificationContains.isChecked = exception.inverted
                }
                ExceptionType.NOTIFICATION_FROM -> {
                    val packagesData = exception.data["app_packages"]
                    val packages = when (packagesData) {
                        is Set<*> -> packagesData.filterIsInstance<String>()
                        is List<*> -> packagesData.filterIsInstance<String>()
                        else -> emptyList()
                    }
                    selectedNotificationFromApps.clear()
                    selectedNotificationFromApps.addAll(packages)
                    binding.switchInvertNotificationFrom.isChecked = exception.inverted
                    updateNotificationFromSummary()
                }
                ExceptionType.FOREGROUND_APP -> {
                    val packagesData = exception.data["app_packages"]
                    val packages = when (packagesData) {
                        is Set<*> -> packagesData.filterIsInstance<String>()
                        is List<*> -> packagesData.filterIsInstance<String>()
                        else -> emptyList()
                    }
                    selectedForegroundApps.clear()
                    selectedForegroundApps.addAll(packages)
                    binding.switchInvertForegroundApp.isChecked = exception.inverted
                    updateForegroundAppSummary()
                }
                ExceptionType.SCREEN_ORIENTATION -> {
                    val mode = exception.data["mode"] as? String ?: "portrait"
                    val modeIndex = if (mode == "landscape") 1 else 0
                    binding.spinnerScreenOrientation.setSelection(modeIndex)
                }
                ExceptionType.SCREEN_STATE -> {
                    val screenState = exception.data["screen_state"] as? String ?: "on"
                    val position = if (screenState == "on") 0 else 1
                    binding.spinnerScreenState.setSelection(position)
                }
                
                ExceptionType.TIME_SCHEDULE -> {
                    val startTime = exception.data["start_time"] as? Long ?: 0L
                    val endTime = exception.data["end_time"] as? Long ?: 0L
                    val daysOfWeek = (exception.data["days_of_week"] as? Collection<*>)
                        ?.mapNotNull { it as? Int }
                        ?.toSet()
                        ?: emptySet()
                    
                    // Set time displays
                    val startHour = (startTime / (60 * 60 * 1000)).toInt()
                    val startMinute = ((startTime % (60 * 60 * 1000)) / (60 * 1000)).toInt()
                    binding.textStartTime.text = String.format("%02d:%02d", startHour, startMinute)
                    startTimeMillis = startTime
                    
                    val endHour = (endTime / (60 * 60 * 1000)).toInt()
                    val endMinute = ((endTime % (60 * 60 * 1000)) / (60 * 1000)).toInt()
                    binding.textEndTime.text = String.format("%02d:%02d", endHour, endMinute)
                    endTimeMillis = endTime
                    
                    // Convert 0-based days from stored exception to Calendar constants (1-based) for UI
                    val convertedDays = daysOfWeek.map { day ->
                        when (day) {
                            0 -> Calendar.SUNDAY    // Sunday (0) -> Calendar.SUNDAY
                            1 -> Calendar.MONDAY    // Monday (1) -> Calendar.MONDAY
                            2 -> Calendar.TUESDAY   // Tuesday (2) -> Calendar.TUESDAY
                            3 -> Calendar.WEDNESDAY // Wednesday (3) -> Calendar.WEDNESDAY
                            4 -> Calendar.THURSDAY  // Thursday (4) -> Calendar.THURSDAY
                            5 -> Calendar.FRIDAY    // Friday (5) -> Calendar.FRIDAY
                            6 -> Calendar.SATURDAY  // Saturday (6) -> Calendar.SATURDAY
                            else -> day
                        }
                    }
                    
                    // Set selected days
                    selectedDays.clear()
                    selectedDays.addAll(convertedDays)
                    
                    // Update checkboxes
                    val dayValues = arrayOf(
                        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
                    )
                    
                    for (i in 0 until binding.layoutDays.childCount) {
                        val checkbox = binding.layoutDays.getChildAt(i) as? android.widget.CheckBox
                        checkbox?.isChecked = selectedDays.contains(dayValues[i])
                    }
                    
                    updateSelectedDays(0, false) // Update display
                }
                
                ExceptionType.WIRED_HEADPHONES -> {
                    val connectionState = exception.data["connection_state"] as? String ?: "disconnected"
                    val stateIndex = if (connectionState == "connected") 1 else 0
                    binding.spinnerWiredHeadphonesConnectionState.setSelection(stateIndex)
                }
                ExceptionType.BLUETOOTH_DEVICE -> {
                    val deviceAddressesData = exception.data["device_addresses"]
                    val deviceAddresses = when (deviceAddressesData) {
                        is Set<*> -> deviceAddressesData.mapNotNull { it as? String }.toSet()
                        is List<*> -> deviceAddressesData.mapNotNull { it as? String }.toSet()
                        else -> emptySet<String>()
                    }
                    
                    // Handle backwards compatibility: check if connection_state exists in data
                    // If not, use the inverted field to determine the state
                    val connectionState = exception.data["connection_state"] as? String
                        ?: if (exception.inverted) "disconnected" else "connected"
                    
                    // Set connection state spinner (0=Connected, 1=Disconnected)
                    val stateIndex = if (connectionState == "disconnected") 1 else 0
                    binding.spinnerBluetoothConnectionState.setSelection(stateIndex)
                    
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
                
                ExceptionType.WIFI_NETWORK -> {
                    // Handle both Set<String> and List<String> since JSON serialization converts Sets to Lists
                    val networkSSIDsData = exception.data["network_ssids"]
                    val networkSSIDs = when (networkSSIDsData) {
                        is Set<*> -> networkSSIDsData.filterIsInstance<String>().toSet()
                        is List<*> -> networkSSIDsData.filterIsInstance<String>().toSet()
                        else -> emptySet<String>()
                    }
                    
                    // Handle backwards compatibility: check if connection_state exists in data
                    // If not, use the inverted field to determine the state
                    val connectionState = exception.data["connection_state"] as? String
                        ?: if (exception.inverted) "disconnected" else "connected"
                    
                    // Set connection state spinner (0=Connected, 1=Disconnected)
                    val stateIndex = if (connectionState == "disconnected") 1 else 0
                    binding.spinnerWifiConnectionState.setSelection(stateIndex)
                    
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

    private fun saveException() {
        val exception = when (exceptionType) {
            ExceptionType.BATTERY_PERCENTAGE -> createBatteryPercentageException()
            ExceptionType.CHARGING_STATUS -> createChargingStatusException()
            ExceptionType.DEVICE_UNLOCKED -> createDeviceUnlockedException()
            ExceptionType.NOTIFICATION_CONTAINS -> createNotificationContainsException()
            ExceptionType.NOTIFICATION_FROM -> createNotificationFromException()
            ExceptionType.FOREGROUND_APP -> createForegroundAppException()
            ExceptionType.SCREEN_ORIENTATION -> createScreenOrientationException()
            ExceptionType.SCREEN_STATE -> createScreenStateException()
            ExceptionType.TIME_SCHEDULE -> createTimeScheduleException()
            ExceptionType.BLUETOOTH_DEVICE -> createBluetoothException()
            ExceptionType.WIRED_HEADPHONES -> createWiredHeadphonesException()
            ExceptionType.WIFI_NETWORK -> {
                val wifiException = createWifiException()
                
                // Check if this is a WiFi exception with specific networks
                val networkSSIDs = (wifiException.data["network_ssids"] as? Collection<*>)
                    ?.mapNotNull { it as? String }
                    ?.toSet()
                if (networkSSIDs?.isNotEmpty() == true) {
                    val canResolve = WifiCapabilityChecker.canResolveWifiSSID(this)
                    if (!canResolve) {
                        // Warn only when SSID resolution isn’t possible on this device/context
                        showWifiCompatibilityWarningBeforeSave(wifiException)
                        return
                    } else {
                        InAppLogger.logDebug("ExceptionConfigActivity", "WiFi SSID resolution available; skipping compatibility warning.")
                    }
                }
                
                wifiException
            }
            else -> return
        }
        
        // Create a new intent for the result to avoid modifying the original intent
        val resultIntent = android.content.Intent().apply {
            putExtra(RESULT_EXCEPTION, exception.toJson())
            putExtra(EXTRA_IS_EDITING, isEditing)
        }
        setResult(RESULT_OK, resultIntent)
        
        InAppLogger.logUserAction("Exception configured: ${exception.getLogMessage()}", "ExceptionConfigActivity")
        finish()
    }
    
    private fun showWifiCompatibilityWarningBeforeSave(exception: Exception) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.wifi_compatibility_warning_title))
            .setMessage(getString(R.string.wifi_compatibility_warning_message))
            .setPositiveButton(getString(R.string.wifi_compatibility_warning_create_anyway)) { _, _ ->
                // Save the exception with the original data (user wants to proceed)
                saveExceptionInternal(exception)
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
    
    private fun saveExceptionInternal(exception: Exception) {
        // Create a new intent for the result to avoid modifying the original intent
        val resultIntent = android.content.Intent().apply {
            putExtra(RESULT_EXCEPTION, exception.toJson())
            putExtra(EXTRA_IS_EDITING, isEditing)
        }
        setResult(RESULT_OK, resultIntent)
        
        InAppLogger.logUserAction("Exception configured: ${exception.getLogMessage()}", "ExceptionConfigActivity")
        finish()
    }

    private fun createScreenStateException(): Exception {
        val screenState = if (binding.spinnerScreenState.selectedItemPosition == 0) "on" else "off"
        val description = if (screenState == "on") "Screen is on" else "Screen is off"
        val inverted = false // Screen state inversion removed; spinner handles on/off selection
        
        return if (isEditing && originalException != null) {
            // Preserve the original exception ID when editing
            originalException!!.copy(
                data = mapOf("screen_state" to screenState),
                description = description,
                inverted = inverted
            )
        } else {
            // Create new exception
            Exception(
                type = ExceptionType.SCREEN_STATE,
                data = mapOf("screen_state" to screenState),
                description = description,
                inverted = inverted
            )
        }
    }

    private fun createBatteryPercentageException(): Exception {
        val mode = if (binding.spinnerBatteryMode.selectedItemPosition == 1) "below" else "above"
        val percentage = binding.sliderBatteryPercentage.value.toInt()
        val description = if (mode == "below") {
            getString(R.string.rule_exception_battery_below, percentage)
        } else {
            getString(R.string.rule_exception_battery_above, percentage)
        }

        return if (isEditing && originalException != null) {
            originalException!!.copy(
                data = mapOf(
                    "mode" to mode,
                    "percentage" to percentage
                ),
                description = description,
                inverted = false
            )
        } else {
            Exception(
                type = ExceptionType.BATTERY_PERCENTAGE,
                data = mapOf(
                    "mode" to mode,
                    "percentage" to percentage
                ),
                description = description,
                inverted = false
            )
        }
    }

    private fun createNotificationContainsException(): Exception {
        val phrase = binding.editNotificationPhrase.text?.toString().orEmpty().trim()
        val caseSensitive = binding.checkNotificationCaseSensitive.isChecked
        val inverted = binding.switchInvertNotificationContains.isChecked
        val description = if (inverted) {
            getString(R.string.rule_exception_notification_not_contains, phrase)
        } else {
            getString(R.string.rule_exception_notification_contains, phrase)
        }

        return if (isEditing && originalException != null) {
            originalException!!.copy(
                data = mapOf(
                    "phrase" to phrase,
                    "case_sensitive" to caseSensitive
                ),
                description = description,
                inverted = inverted
            )
        } else {
            Exception(
                type = ExceptionType.NOTIFICATION_CONTAINS,
                data = mapOf(
                    "phrase" to phrase,
                    "case_sensitive" to caseSensitive
                ),
                description = description,
                inverted = inverted
            )
        }
    }

    private fun createNotificationFromException(): Exception {
        val selectedPackages = selectedNotificationFromApps.toSet()
        val inverted = binding.switchInvertNotificationFrom.isChecked
        val description = if (selectedPackages.size == 1) {
            val packageName = selectedPackages.first()
            val appName = AppListManager.findAppByPackage(this, packageName)?.displayName ?: packageName
            getString(R.string.rule_exception_notification_from_single, appName)
        } else {
            getString(R.string.rule_exception_notification_from_multiple, selectedPackages.size)
        }.let { base ->
            if (inverted) getString(R.string.rule_exception_notification_from_not, base) else base
        }

        return if (isEditing && originalException != null) {
            originalException!!.copy(
                data = mapOf("app_packages" to selectedPackages),
                description = description,
                inverted = inverted
            )
        } else {
            Exception(
                type = ExceptionType.NOTIFICATION_FROM,
                data = mapOf("app_packages" to selectedPackages),
                description = description,
                inverted = inverted
            )
        }
    }

    private fun createForegroundAppException(): Exception {
        val selectedPackages = selectedForegroundApps.toSet()
        val inverted = binding.switchInvertForegroundApp.isChecked
        val description = if (selectedPackages.size == 1) {
            val packageName = selectedPackages.first()
            val appName = AppListManager.findAppByPackage(this, packageName)?.displayName ?: packageName
            getString(R.string.rule_exception_foreground_app_single, appName)
        } else {
            getString(R.string.rule_exception_foreground_app_multiple, selectedPackages.size)
        }.let { base ->
            if (inverted) getString(R.string.rule_exception_foreground_app_not, base) else base
        }

        return if (isEditing && originalException != null) {
            originalException!!.copy(
                data = mapOf("app_packages" to selectedPackages),
                description = description,
                inverted = inverted
            )
        } else {
            Exception(
                type = ExceptionType.FOREGROUND_APP,
                data = mapOf("app_packages" to selectedPackages),
                description = description,
                inverted = inverted
            )
        }
    }

    private fun createDeviceUnlockedException(): Exception {
        val mode = if (binding.spinnerDeviceUnlockedMode.selectedItemPosition == 1) "locked" else "unlocked"
        val description = if (mode == "locked") {
            getString(R.string.rule_exception_device_locked)
        } else {
            getString(R.string.rule_exception_device_unlocked)
        }

        return if (isEditing && originalException != null) {
            originalException!!.copy(
                data = mapOf("mode" to mode),
                description = description,
                inverted = false
            )
        } else {
            Exception(
                type = ExceptionType.DEVICE_UNLOCKED,
                data = mapOf("mode" to mode),
                description = description,
                inverted = false
            )
        }
    }

    private fun createScreenOrientationException(): Exception {
        val mode = if (binding.spinnerScreenOrientation.selectedItemPosition == 1) "landscape" else "portrait"
        val description = if (mode == "landscape") {
            getString(R.string.rule_exception_orientation_landscape)
        } else {
            getString(R.string.rule_exception_orientation_portrait)
        }

        return if (isEditing && originalException != null) {
            originalException!!.copy(
                data = mapOf("mode" to mode),
                description = description,
                inverted = false
            )
        } else {
            Exception(
                type = ExceptionType.SCREEN_ORIENTATION,
                data = mapOf("mode" to mode),
                description = description,
                inverted = false
            )
        }
    }

    private fun createChargingStatusException(): Exception {
        val status = if (binding.spinnerChargingStatus.selectedItemPosition == 1) "discharging" else "charging"
        val description = if (status == "discharging") {
            getString(R.string.rule_exception_battery_discharging)
        } else {
            getString(R.string.rule_exception_battery_charging)
        }

        return if (isEditing && originalException != null) {
            originalException!!.copy(
                data = mapOf("status" to status),
                description = description,
                inverted = false
            )
        } else {
            Exception(
                type = ExceptionType.CHARGING_STATUS,
                data = mapOf("status" to status),
                description = description,
                inverted = false
            )
        }
    }

    private fun createTimeScheduleException(): Exception {
        val startTime = startTimeMillis ?: 0L
        val endTime = endTimeMillis ?: 0L
        
        // Convert Calendar constants (1-based) to 0-based days for RuleEvaluator
        val daysOfWeek = selectedDays.map { day ->
            when (day) {
                Calendar.MONDAY -> 1    // Monday stays 1
                Calendar.TUESDAY -> 2   // Tuesday stays 2
                Calendar.WEDNESDAY -> 3 // Wednesday stays 3
                Calendar.THURSDAY -> 4  // Thursday stays 4
                Calendar.FRIDAY -> 5    // Friday stays 5
                Calendar.SATURDAY -> 6  // Saturday stays 6
                Calendar.SUNDAY -> 0    // Sunday becomes 0
                else -> day
            }
        }.toSet()
        
        val description = if (daysOfWeek.isEmpty()) {
            "Always"
        } else {
            val dayNames = daysOfWeek.map { day ->
                when (day) {
                    Calendar.MONDAY -> "Mon"
                    Calendar.TUESDAY -> "Tue"
                    Calendar.WEDNESDAY -> "Wed"
                    Calendar.THURSDAY -> "Thu"
                    Calendar.FRIDAY -> "Fri"
                    Calendar.SATURDAY -> "Sat"
                    Calendar.SUNDAY -> "Sun"
                    else -> ""
                }
            }.joinToString(", ")
            
            "Between ${String.format("%02d:%02d", (startTime / (60 * 60 * 1000)).toInt(), ((startTime % (60 * 60 * 1000)) / (60 * 1000)).toInt())} and ${String.format("%02d:%02d", (endTime / (60 * 60 * 1000)).toInt(), ((endTime % (60 * 60 * 1000)) / (60 * 1000)).toInt())} on $dayNames"
        }
        val inverted = binding.switchInvertTimeSchedule.isChecked
        
        return if (isEditing && originalException != null) {
            // Preserve the original exception ID when editing
            originalException!!.copy(
                data = mapOf(
                    "start_time" to startTime,
                    "end_time" to endTime,
                    "days_of_week" to daysOfWeek
                ),
                description = description,
                inverted = inverted
            )
        } else {
            // Create new exception
            Exception(
                type = ExceptionType.TIME_SCHEDULE,
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

    private inline fun <reified T : java.io.Serializable> Intent.getSerializableExtraCompat(key: String): T? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getSerializableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getSerializableExtra(key) as? T
        }
    }

    private fun createBluetoothException(): Exception {
        val isAnyDevice = binding.switchAnyDevice.isChecked
        
        val deviceAddresses = if (isAnyDevice) {
            emptySet<String>()
        } else {
            // Try to get from tag first, then parse from text
            val addressesFromTag = (binding.editDeviceAddresses.tag as? Set<*>)
                ?.filterIsInstance<String>()
                ?.toSet()
            if (addressesFromTag != null) {
                addressesFromTag
            } else {
                val text = binding.editDeviceAddresses.text.toString().trim()
                if (text.isNotEmpty()) {
                    text.split("\n").map { line ->
                        // Extract address from "Name (Address)" format
                        val addressMatch = Regex("\\(([A-Fa-f0-9:]{17})\\)").find(line)
                        addressMatch?.groupValues?.get(1) ?: line.trim()
                    }.toSet()
                } else {
                    emptySet()
                }
            }
        }
        
        // Get connection state from spinner (0=Connected, 1=Disconnected)
        val connectionState = if (binding.spinnerBluetoothConnectionState.selectedItemPosition == 1) {
            "disconnected"
        } else {
            "connected"
        }
        
        val description = if (isAnyDevice) {
            if (connectionState == "connected") {
                "Any Bluetooth device connected"
            } else {
                "Any Bluetooth device disconnected"
            }
        } else if (deviceAddresses.isEmpty()) {
            "No devices specified"
        } else {
            if (connectionState == "connected") {
                "Specific devices connected (${deviceAddresses.size})"
            } else {
                "Specific devices disconnected (${deviceAddresses.size})"
            }
        }
        
        return if (isEditing && originalException != null) {
            // Preserve the original exception ID when editing
            originalException!!.copy(
                data = mapOf(
                    "device_addresses" to deviceAddresses,
                    "connection_state" to connectionState
                ),
                description = description,
                inverted = false // Clear the inverted flag as we're now using connection_state
            )
        } else {
            // Create new exception
            Exception(
                type = ExceptionType.BLUETOOTH_DEVICE,
                data = mapOf(
                    "device_addresses" to deviceAddresses,
                    "connection_state" to connectionState
                ),
                description = description,
                inverted = false // No longer using inverted
            )
        }
    }
    
    private fun createWiredHeadphonesException(): Exception {
        val connectionState = if (binding.spinnerWiredHeadphonesConnectionState.selectedItemPosition == 1) {
            "connected"
        } else {
            "disconnected"
        }
        
        val description = if (connectionState == "connected") {
            getString(R.string.rule_exception_wired_headphones_connected)
        } else {
            getString(R.string.rule_exception_wired_headphones_disconnected)
        }
        
        return if (isEditing && originalException != null) {
            originalException!!.copy(
                type = ExceptionType.WIRED_HEADPHONES,
                data = mapOf("connection_state" to connectionState),
                description = description,
                inverted = false
            )
        } else {
            Exception(
                type = ExceptionType.WIRED_HEADPHONES,
                data = mapOf("connection_state" to connectionState),
                description = description,
                inverted = false
            )
        }
    }
    
    private fun createWifiException(): Exception {
        val isAnyNetwork = binding.switchAnyNetwork.isChecked
        
        val networkSSIDs = if (isAnyNetwork) {
            emptySet<String>()
        } else {
            // Try to get from tag first, then parse from text
            val ssidsFromTag = (binding.editNetworkSSIDs.tag as? Set<*>)
                ?.filterIsInstance<String>()
                ?.toSet()
            if (ssidsFromTag != null) {
                ssidsFromTag
            } else {
                val text = binding.editNetworkSSIDs.text.toString().trim()
                if (text.isNotEmpty()) {
                    text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                } else {
                    emptySet()
                }
            }
        }
        
        // Get connection state from spinner (0=Connected, 1=Disconnected)
        val connectionState = if (binding.spinnerWifiConnectionState.selectedItemPosition == 1) {
            "disconnected"
        } else {
            "connected"
        }
        
        val description = if (isAnyNetwork) {
            if (connectionState == "connected") {
                "Any WiFi network connected"
            } else {
                "Any WiFi network disconnected"
            }
        } else if (networkSSIDs.isEmpty()) {
            "No networks specified"
        } else {
            if (connectionState == "connected") {
                "Specific networks connected (${networkSSIDs.size})"
            } else {
                "Specific networks disconnected (${networkSSIDs.size})"
            }
        }
        
        return if (isEditing && originalException != null) {
            // Preserve the original exception ID when editing
            originalException!!.copy(
                data = mapOf(
                    "network_ssids" to networkSSIDs,
                    "connection_state" to connectionState
                ),
                description = description,
                inverted = false // Clear the inverted flag as we're now using connection_state
            )
        } else {
            // Create new exception
            Exception(
                type = ExceptionType.WIFI_NETWORK,
                data = mapOf(
                    "network_ssids" to networkSSIDs,
                    "connection_state" to connectionState
                ),
                description = description,
                inverted = false // No longer using inverted
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
                    InAppLogger.logDebug("ExceptionConfigActivity", "Bluetooth permissions granted")
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
                    InAppLogger.logDebug("ExceptionConfigActivity", "WiFi permissions granted")
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

    override fun onSupportNavigateUp(): Boolean {
        @Suppress("DEPRECATION")
        onBackPressed()
        return true
    }
} 