package com.micoyc.speakthat

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for Bluetooth devices in BluetoothConditionActivity
 */
class BluetoothDeviceAdapter(
    private val devices: MutableList<BluetoothDevice>,
    private val onRemoveClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
    }

    override fun getItemCount(): Int = devices.size

    fun addDevice(device: BluetoothDevice) {
        if (!devices.contains(device)) {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }

    fun removeDevice(device: BluetoothDevice) {
        val position = devices.indexOf(device)
        if (position != -1) {
            devices.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun getDevices(): List<BluetoothDevice> = devices.toList()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceName: TextView = itemView.findViewById(R.id.textDeviceName)
        private val deviceAddress: TextView = itemView.findViewById(R.id.textDeviceAddress)
        private val buttonRemove: ImageButton = itemView.findViewById(R.id.buttonRemove)

        fun bind(device: BluetoothDevice) {
            deviceName.text = device.name ?: "Unknown Device"
            deviceAddress.text = device.address
            
            buttonRemove.setOnClickListener {
                onRemoveClick(device)
            }
        }
    }
} 