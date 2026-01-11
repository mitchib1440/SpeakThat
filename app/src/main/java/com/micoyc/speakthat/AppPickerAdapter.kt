package com.micoyc.speakthat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat

class AppPickerAdapter(
    private val items: MutableList<AppPickerActivity.SelectableApp>,
    private val allowPrivate: Boolean,
    private val onItemChanged: () -> Unit
) : RecyclerView.Adapter<AppPickerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_picker, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.appName.text = item.label
        holder.packageName.text = item.packageName

        val iconDrawable = item.icon ?: ContextCompat.getDrawable(holder.itemView.context, R.drawable.ic_app_unknown)
        holder.appIcon.setImageDrawable(iconDrawable)

        holder.selectCheck.setOnCheckedChangeListener(null)
        holder.privateCheck.setOnCheckedChangeListener(null)

        holder.selectCheck.isChecked = item.selected

        if (allowPrivate && item.selected) {
            holder.privateCheck.visibility = View.VISIBLE
            holder.privateCheck.isChecked = item.isPrivate
        } else {
            holder.privateCheck.visibility = View.GONE
            holder.privateCheck.isChecked = false
        }

        holder.selectCheck.setOnCheckedChangeListener { _, isChecked ->
            item.selected = isChecked
            if (!isChecked) {
                item.isPrivate = false
            }
            notifyItemChanged(holder.bindingAdapterPosition)
            onItemChanged()
        }

        holder.privateCheck.setOnCheckedChangeListener { _, isChecked ->
            if (allowPrivate && item.selected) {
                item.isPrivate = isChecked
            } else {
                item.isPrivate = false
            }
            onItemChanged()
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<AppPickerActivity.SelectableApp>) {
        val snapshot = if (newItems === items) {
            // Avoid clearing and reusing the same list reference; take a copy instead
            newItems.toList()
        } else {
            newItems
        }
        items.clear()
        items.addAll(snapshot)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIcon)
        val appName: TextView = view.findViewById(R.id.appName)
        val packageName: TextView = view.findViewById(R.id.packageName)
        val privateCheck: CheckBox = view.findViewById(R.id.privateCheck)
        val selectCheck: CheckBox = view.findViewById(R.id.selectCheck)
    }
}

