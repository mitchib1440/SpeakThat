package com.micoyc.speakthat

import android.net.wifi.WifiConfiguration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Simple adapter for WiFi networks in onboarding configuration dialogs
 */
class OnboardingWifiNetworkAdapter(
    private val onNetworkSelected: (WifiConfiguration) -> Unit
) : RecyclerView.Adapter<OnboardingWifiNetworkAdapter.NetworkViewHolder>() {

    private val networks = mutableListOf<WifiConfiguration>()
    private val selectedNetworks = mutableSetOf<String>()

    fun updateNetworks(newNetworks: List<WifiConfiguration>) {
        networks.clear()
        networks.addAll(newNetworks)
        notifyDataSetChanged()
    }

    fun getSelectedNetworks(): Set<String> = selectedNetworks.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NetworkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false)
        return NetworkViewHolder(view)
    }

    override fun onBindViewHolder(holder: NetworkViewHolder, position: Int) {
        holder.bind(networks[position])
    }

    override fun getItemCount(): Int = networks.size

    inner class NetworkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(android.R.id.text1)
        private val textView: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(network: WifiConfiguration) {
            val networkName = network.SSID?.removeSurrounding("\"") ?: "Unknown Network"
            
            textView.text = networkName
            checkBox.isChecked = selectedNetworks.contains(networkName)
            
            itemView.setOnClickListener {
                if (selectedNetworks.contains(networkName)) {
                    selectedNetworks.remove(networkName)
                    checkBox.isChecked = false
                } else {
                    selectedNetworks.add(networkName)
                    checkBox.isChecked = true
                }
                onNetworkSelected(network)
            }
        }
    }
} 