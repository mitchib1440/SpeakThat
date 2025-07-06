package com.micoyc.speakthat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PriorityAppAdapter(
    private val items: List<String>,
    private val removeListener: BehaviorSettingsActivity.OnPriorityAppActionListener?
) : RecyclerView.Adapter<PriorityAppAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_priority_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appName = items[position]
        
        holder.textAppName.text = appName
        
        // Set up remove button listener with null safety
        holder.buttonRemove.setOnClickListener {
            removeListener?.onAction(holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textAppName: TextView = itemView.findViewById(R.id.textAppName)
        val buttonRemove: ImageButton = itemView.findViewById(R.id.buttonRemove)
    }
} 