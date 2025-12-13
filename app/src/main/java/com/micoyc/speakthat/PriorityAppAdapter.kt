package com.micoyc.speakthat

import android.content.pm.PackageManager
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

    private val appNameCache = mutableMapOf<String, String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_priority_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val packageName = items[position]

        holder.textAppName.text = resolveAppName(holder.itemView.context, packageName)

        // Set up remove button listener with null safety
        holder.buttonRemove.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                removeListener?.onAction(pos)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textAppName: TextView = itemView.findViewById(R.id.textAppName)
        val buttonRemove: ImageButton = itemView.findViewById(R.id.buttonRemove)
    }

    private fun resolveAppName(context: android.content.Context, packageName: String): String {
        appNameCache[packageName]?.let { return it }

        var displayName = packageName
        val pm = context.packageManager
        try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo)?.toString()
            if (!label.isNullOrBlank()) {
                displayName = label
            }
        } catch (_: PackageManager.NameNotFoundException) {
            val appData = AppListManager.findAppByPackage(context, packageName)
            if (appData != null && !appData.displayName.isNullOrBlank()) {
                displayName = appData.displayName
            }
        }

        appNameCache[packageName] = displayName
        return displayName
    }
}