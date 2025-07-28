package com.micoyc.speakthat.rules

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageButton
import com.micoyc.speakthat.R
import com.micoyc.speakthat.InAppLogger

class TriggerAdapter(
    private var triggers: MutableList<Trigger>,
    private val onEditTrigger: (Trigger) -> Unit,
    private val onRemoveTrigger: (Trigger) -> Unit,
    private val onTriggersChanged: (() -> Unit)? = null
) : RecyclerView.Adapter<TriggerAdapter.TriggerViewHolder>() {

    class TriggerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textDescription: TextView = view.findViewById(R.id.textTriggerDetails)
        val textType: TextView = view.findViewById(R.id.textTriggerType)
        val btnEdit: ImageButton = view.findViewById(R.id.buttonEditTrigger)
        val btnRemove: ImageButton = view.findViewById(R.id.buttonRemoveTrigger)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TriggerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trigger, parent, false)
        return TriggerViewHolder(view)
    }

    override fun onBindViewHolder(holder: TriggerViewHolder, position: Int) {
        val trigger = triggers[position]
        
        holder.textDescription.text = trigger.description.ifEmpty { 
            "Trigger: ${trigger.type.displayName}" 
        }
        holder.textType.text = trigger.type.displayName
        
        holder.btnEdit.setOnClickListener {
            onEditTrigger(trigger)
        }
        
        holder.btnRemove.setOnClickListener {
            onRemoveTrigger(trigger)
        }
    }

    override fun getItemCount() = triggers.size

    fun updateTriggers(newTriggers: List<Trigger>) {
        triggers.clear()
        triggers.addAll(newTriggers)
        notifyDataSetChanged()
        onTriggersChanged?.invoke()
        InAppLogger.logDebug("TriggerAdapter", "Updated triggers: ${triggers.size} items")
    }

    fun addTrigger(trigger: Trigger) {
        triggers.add(trigger)
        notifyItemInserted(triggers.size - 1)
        onTriggersChanged?.invoke()
        InAppLogger.logDebug("TriggerAdapter", "Added trigger: ${trigger.getLogMessage()}")
    }

    fun removeTrigger(trigger: Trigger) {
        val index = triggers.indexOf(trigger)
        if (index != -1) {
            triggers.removeAt(index)
            notifyItemRemoved(index)
            onTriggersChanged?.invoke()
            InAppLogger.logDebug("TriggerAdapter", "Removed trigger: ${trigger.getLogMessage()}")
        }
    }

    fun getTriggers(): List<Trigger> = triggers.toList()
} 