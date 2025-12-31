package com.micoyc.speakthat.rules

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
        
        exceptionType = intent.getSerializableExtra(EXTRA_EXCEPTION_TYPE) as? ExceptionType
        isEditing = intent.getBooleanExtra(EXTRA_IS_EDITING, false)
        
        if (isEditing) {
            val exceptionData = intent.getStringExtra(EXTRA_EXCEPTION_DATA)
            originalException = Exception.fromJson(exceptionData ?: "")
        }
        
        setupUI()
        loadCurrentValues()
    }

    private fun applySavedTheme() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean(KEY_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun setupUI() {
        when (exceptionType) {
            ExceptionType.SCREEN_STATE -> setupScreenStateUI()
            ExceptionType.TIME_SCHEDULE -> setupTimeScheduleUI()
            ExceptionType.BLUETOOTH_DEVICE -> setupBluetoothUI()
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

    private fun setupWifiUI() {
        binding.cardWifi.visibility = View.VISIBLE
        
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
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            
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
                            result.SSID.removeSurrounding("\"")
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
            // Load inversion state for supported exception types
            binding.switchInvertTimeSchedule.isChecked = exception.inverted
            binding.switchInvertBluetooth.isChecked = exception.inverted
            binding.switchInvertWifi.isChecked = exception.inverted
            
            when (exception.type) {
                ExceptionType.SCREEN_STATE -> {
                    val screenState = exception.data["screen_state"] as? String ?: "on"
                    val position = if (screenState == "on") 0 else 1
                    binding.spinnerScreenState.setSelection(position)
                }
                
                ExceptionType.TIME_SCHEDULE -> {
                    val startTime = exception.data["start_time"] as? Long ?: 0L
                    val endTime = exception.data["end_time"] as? Long ?: 0L
                    val daysOfWeek = exception.data["days_of_week"] as? Set<Int> ?: emptySet()
                    
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
                
                ExceptionType.BLUETOOTH_DEVICE -> {
                    val deviceAddressesData = exception.data["device_addresses"]
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
                            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
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
            ExceptionType.SCREEN_STATE -> createScreenStateException()
            ExceptionType.TIME_SCHEDULE -> createTimeScheduleException()
            ExceptionType.BLUETOOTH_DEVICE -> createBluetoothException()
            ExceptionType.WIFI_NETWORK -> {
                val wifiException = createWifiException()
                
                // Check if this is a WiFi exception with specific networks
                val networkSSIDs = wifiException.data["network_ssids"] as? Set<String>
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

    private fun createBluetoothException(): Exception {
        val isAnyDevice = binding.switchAnyDevice.isChecked
        
        val deviceAddresses = if (isAnyDevice) {
            emptySet<String>()
        } else {
            // Try to get from tag first, then parse from text
            val addressesFromTag = binding.editDeviceAddresses.tag as? Set<String>
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
        
        val description = if (isAnyDevice) {
            "Any Bluetooth device"
        } else if (deviceAddresses.isEmpty()) {
            "No devices specified"
        } else {
            "Specific devices (${deviceAddresses.size})"
        }
        val inverted = binding.switchInvertBluetooth.isChecked
        
        return if (isEditing && originalException != null) {
            // Preserve the original exception ID when editing
            originalException!!.copy(
                data = mapOf("device_addresses" to deviceAddresses),
                description = description,
                inverted = inverted
            )
        } else {
            // Create new exception
            Exception(
                type = ExceptionType.BLUETOOTH_DEVICE,
                data = mapOf("device_addresses" to deviceAddresses),
                description = description,
                inverted = inverted
            )
        }
    }

    private fun createWifiException(): Exception {
        val isAnyNetwork = binding.switchAnyNetwork.isChecked
        
        val networkSSIDs = if (isAnyNetwork) {
            emptySet<String>()
        } else {
            // Try to get from tag first, then parse from text
            val ssidsFromTag = binding.editNetworkSSIDs.tag as? Set<String>
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
        
        val description = if (isAnyNetwork) {
            "Any WiFi network"
        } else if (networkSSIDs.isEmpty()) {
            "No networks specified"
        } else {
            "Specific networks (${networkSSIDs.size})"
        }
        val inverted = binding.switchInvertWifi.isChecked
        
        return if (isEditing && originalException != null) {
            // Preserve the original exception ID when editing
            originalException!!.copy(
                data = mapOf("network_ssids" to networkSSIDs),
                description = description,
                inverted = inverted
            )
        } else {
            // Create new exception
            Exception(
                type = ExceptionType.WIFI_NETWORK,
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