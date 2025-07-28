package com.micoyc.speakthat.rules

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageButton
import com.micoyc.speakthat.R
import com.micoyc.speakthat.InAppLogger

class ActionAdapter(
    private var actions: MutableList<Action>,
    private val onEditAction: (Action) -> Unit,
    private val onRemoveAction: (Action) -> Unit
) : RecyclerView.Adapter<ActionAdapter.ActionViewHolder>() {

    class ActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textDescription: TextView = view.findViewById(R.id.textActionDetails)
        val textType: TextView = view.findViewById(R.id.textActionType)
        val btnEdit: ImageButton = view.findViewById(R.id.buttonEditAction)
        val btnRemove: ImageButton = view.findViewById(R.id.buttonRemoveAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_action, parent, false)
        return ActionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        val action = actions[position]
        
        holder.textDescription.text = action.description.ifEmpty { 
            "Action: ${action.type.displayName}" 
        }
        holder.textType.text = action.type.displayName
        
        holder.btnEdit.setOnClickListener {
            onEditAction(action)
        }
        
        holder.btnRemove.setOnClickListener {
            onRemoveAction(action)
        }
    }

    override fun getItemCount() = actions.size

    fun updateActions(newActions: List<Action>) {
        actions.clear()
        actions.addAll(newActions)
        notifyDataSetChanged()
        InAppLogger.logDebug("ActionAdapter", "Updated actions: ${actions.size} items")
    }

    fun addAction(action: Action) {
        actions.add(action)
        notifyItemInserted(actions.size - 1)
        InAppLogger.logDebug("ActionAdapter", "Added action: ${action.getLogMessage()}")
    }

    fun removeAction(action: Action) {
        val index = actions.indexOf(action)
        if (index != -1) {
            actions.removeAt(index)
            notifyItemRemoved(index)
            InAppLogger.logDebug("ActionAdapter", "Removed action: ${action.getLogMessage()}")
        }
    }

    fun getActions(): List<Action> = actions.toList()
} 