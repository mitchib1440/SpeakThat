package com.micoyc.speakthat.rules

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageButton
import com.micoyc.speakthat.R
import com.micoyc.speakthat.InAppLogger

class ExceptionAdapter(
    private var exceptions: MutableList<Exception>,
    private val onEditException: (Exception) -> Unit,
    private val onRemoveException: (Exception) -> Unit,
    private val onExceptionsChanged: (() -> Unit)? = null
) : RecyclerView.Adapter<ExceptionAdapter.ExceptionViewHolder>() {

    class ExceptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textDescription: TextView = view.findViewById(R.id.textExceptionDetails)
        val textType: TextView = view.findViewById(R.id.textExceptionType)
        val btnEdit: ImageButton = view.findViewById(R.id.buttonEditException)
        val btnRemove: ImageButton = view.findViewById(R.id.buttonRemoveException)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExceptionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exception, parent, false)
        return ExceptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExceptionViewHolder, position: Int) {
        val exception = exceptions[position]
        
        holder.textDescription.text = exception.description.ifEmpty { 
            "Exception: ${exception.type.displayName}" 
        }
        holder.textType.text = exception.type.displayName
        
        holder.btnEdit.setOnClickListener {
            onEditException(exception)
        }
        
        holder.btnRemove.setOnClickListener {
            onRemoveException(exception)
        }
    }

    override fun getItemCount() = exceptions.size

    fun updateExceptions(newExceptions: List<Exception>) {
        exceptions.clear()
        exceptions.addAll(newExceptions)
        notifyDataSetChanged()
        onExceptionsChanged?.invoke()
        InAppLogger.logDebug("ExceptionAdapter", "Updated exceptions: ${exceptions.size} items")
    }

    fun addException(exception: Exception) {
        exceptions.add(exception)
        notifyItemInserted(exceptions.size - 1)
        onExceptionsChanged?.invoke()
        InAppLogger.logDebug("ExceptionAdapter", "Added exception: ${exception.getLogMessage()}")
    }

    fun removeException(exception: Exception) {
        val index = exceptions.indexOf(exception)
        if (index != -1) {
            exceptions.removeAt(index)
            notifyItemRemoved(index)
            onExceptionsChanged?.invoke()
            InAppLogger.logDebug("ExceptionAdapter", "Removed exception: ${exception.getLogMessage()}")
        }
    }

    fun getExceptions(): List<Exception> = exceptions.toList()
} 