package com.micoyc.speakthat.utils

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.micoyc.speakthat.InAppLogger

/**
 * Shared Bluetooth detection helpers used by rule evaluation and conditions.
 *
 * Detection is intentionally broad and resilient:
 * - Profile checks for actively connected classic + LE devices.
 * - Output routing checks for real playback paths.
 */
object BluetoothConnectionHelper {

    private const val TAG = "BluetoothConnectionHelper"

    fun getActivelyConnectedDevices(context: Context, logTag: String = TAG): Set<BluetoothDevice> {
        val allConnectedDevices = mutableSetOf<BluetoothDevice>()

        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            if (bluetoothManager == null) {
                InAppLogger.logDebug(logTag, "BluetoothManager unavailable while detecting connected devices")
                return emptySet()
            }

            var profilesSupported = false
            for (profile in getProfilesForDetection()) {
                try {
                    val devices = bluetoothManager.getConnectedDevices(profile)
                    allConnectedDevices.addAll(devices)
                    profilesSupported = true
                    InAppLogger.logDebug(logTag, "Profile $profile connected devices: ${devices.map { it.address }}")
                } catch (e: Throwable) {
                    InAppLogger.logDebug(logTag, "Profile $profile not supported: ${e.message}")
                }
            }

            if (!profilesSupported) {
                InAppLogger.logDebug(logTag, "No Bluetooth profiles available for connection queries; routing fallback may still detect active output")
            }
        } catch (e: Throwable) {
            InAppLogger.logError(logTag, "Error getting connected Bluetooth devices: ${e.message}")
        }

        return allConnectedDevices
    }

    fun getMatchingBondedDevices(
        context: Context,
        requiredAddresses: Set<String>,
        logTag: String = TAG
    ): List<BluetoothDevice> {
        if (requiredAddresses.isEmpty()) {
            return emptyList()
        }

        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val bondedDevices = bluetoothManager?.adapter?.bondedDevices ?: emptySet()
            bondedDevices.filter { requiredAddresses.contains(it.address) }.also { matches ->
                InAppLogger.logDebug(logTag, "Matching bonded devices: ${matches.map { "${it.name} (${it.address})" }}")
            }
        } catch (e: Throwable) {
            InAppLogger.logError(logTag, "Error getting bonded Bluetooth devices: ${e.message}")
            emptyList()
        }
    }

    fun hasBluetoothOutputRoute(audioManager: AudioManager, logTag: String = TAG): Boolean {
        return try {
            val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val bluetoothOutputs = outputDevices.filter { isBluetoothOutputType(it.type) }
            if (bluetoothOutputs.isNotEmpty()) {
                InAppLogger.logDebug(
                    logTag,
                    "Bluetooth output route detected: ${bluetoothOutputs.map { "type=${it.type}, name=${it.productName}" }}"
                )
            }
            bluetoothOutputs.isNotEmpty()
        } catch (e: Throwable) {
            InAppLogger.logError(logTag, "Error checking Bluetooth output route: ${e.message}")
            false
        }
    }

    fun isBluetoothOutputType(deviceType: Int): Boolean {
        return when (deviceType) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID -> true
            else -> false
        }
    }

    private fun getProfilesForDetection(): List<Int> {
        val profiles = mutableListOf(
            BluetoothProfile.A2DP,
            BluetoothProfile.HEADSET
        )
        // LE audio profile constant is available on API 31+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            profiles.add(BluetoothProfile.LE_AUDIO)
        }
        return profiles
    }
}
