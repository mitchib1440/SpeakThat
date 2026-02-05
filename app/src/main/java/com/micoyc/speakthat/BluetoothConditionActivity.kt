package com.micoyc.speakthat

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.databinding.ActivityBluetoothConditionBinding

class BluetoothConditionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityBluetoothConditionBinding
    private lateinit var sharedPreferences: android.content.SharedPreferences
    private lateinit var adapter: BluetoothDeviceAdapter
    private val devices = mutableListOf<BluetoothDevice>()
    
    companion object {
        private const val PREFS_NAME = "SpeakThatPrefs"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_BLUETOOTH_ALLOWED_DEVICES = "bluetooth_allowed_devices"
    }
    
    // Activity Result API for Bluetooth enable request
    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            showDeviceSelectionDialog()
        } else {
            showErrorDialog("Bluetooth must be enabled to select devices")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme FIRST before anything else
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        applySavedTheme()
        
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothConditionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Bluetooth Devices"
        
        setupRecyclerView()
        setupAddButton()
        loadSavedDevices()
    }
    
    private fun applySavedTheme() {
        val isDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, true) // Default to dark mode
        val desiredMode = if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        
        // Only set the night mode if it's different from the current mode
        // This prevents unnecessary configuration changes that cause activity recreation loops
        if (currentMode != desiredMode) {
            AppCompatDelegate.setDefaultNightMode(desiredMode)
        }
    }
    
    private fun setupRecyclerView() {
        adapter = BluetoothDeviceAdapter(devices) { device ->
            removeDevice(device)
        }
        
        binding.recyclerBluetoothDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerBluetoothDevices.adapter = adapter
    }
    
    private fun setupAddButton() {
        binding.btnAddBluetoothDevice.setOnClickListener {
            showDeviceSelectionDialog()
        }
    }
    
    private fun showDeviceSelectionDialog() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        
        if (bluetoothAdapter == null) {
            showErrorDialog("Bluetooth not available on this device")
            return
        }
        
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableBtIntent)
            return
        }
        
        val bondedDevices = bluetoothAdapter.bondedDevices.toList()
        
        if (bondedDevices.isEmpty()) {
            showErrorDialog("No paired Bluetooth devices found. Please pair a device first.")
            return
        }
        
        val deviceNames = bondedDevices.map { device ->
            device.name ?: "Unknown Device (${device.address})"
        }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Select Bluetooth Device")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = bondedDevices[which]
                addDevice(selectedDevice)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun addDevice(device: BluetoothDevice) {
        if (!devices.contains(device)) {
            adapter.addDevice(device)
            saveDevices()
            InAppLogger.logUserAction("Added Bluetooth device: ${device.name} (${device.address})")
        }
    }
    
    private fun removeDevice(device: BluetoothDevice) {
        AlertDialog.Builder(this)
            .setTitle("Remove Device")
            .setMessage("Remove ${device.name ?: "Unknown Device"} from the list?")
            .setPositiveButton("Remove") { _, _ ->
                adapter.removeDevice(device)
                saveDevices()
                InAppLogger.logUserAction("Removed Bluetooth device: ${device.name} (${device.address})")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun loadSavedDevices() {
        val savedAddresses = sharedPreferences.getStringSet(KEY_BLUETOOTH_ALLOWED_DEVICES, emptySet()) ?: emptySet()
        
        if (savedAddresses.isNotEmpty()) {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            
            if (bluetoothAdapter != null) {
                val bondedDevices = bluetoothAdapter.bondedDevices
                
                for (address in savedAddresses) {
                    val device = bondedDevices.find { it.address == address }
                    if (device != null) {
                        adapter.addDevice(device)
                    }
                }
            }
        }
    }
    
    private fun saveDevices() {
        val addresses = adapter.getDevices().map { it.address }.toSet()
        sharedPreferences.edit().putStringSet(KEY_BLUETOOTH_ALLOWED_DEVICES, addresses).apply()
    }
    
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    

    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
} 