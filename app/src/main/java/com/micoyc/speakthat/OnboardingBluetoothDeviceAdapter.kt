/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Simple adapter for Bluetooth devices in onboarding configuration dialogs
 */
class OnboardingBluetoothDeviceAdapter(
    private val onDeviceSelected: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<OnboardingBluetoothDeviceAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<BluetoothDevice>()
    private val selectedDevices = mutableSetOf<String>()

    fun updateDevices(newDevices: List<BluetoothDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

    fun getSelectedDevices(): Set<String> = selectedDevices.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkedTextView: android.widget.CheckedTextView = itemView.findViewById(android.R.id.text1)

        fun bind(device: BluetoothDevice) {
            val deviceName = device.name ?: "Unknown Device"
            val deviceAddress = device.address
            
            checkedTextView.text = deviceName
            checkedTextView.isChecked = selectedDevices.contains(deviceAddress)
            
            itemView.setOnClickListener {
                if (selectedDevices.contains(deviceAddress)) {
                    selectedDevices.remove(deviceAddress)
                    checkedTextView.isChecked = false
                } else {
                    selectedDevices.add(deviceAddress)
                    checkedTextView.isChecked = true
                }
                onDeviceSelected(device)
            }
        }
    }
} 