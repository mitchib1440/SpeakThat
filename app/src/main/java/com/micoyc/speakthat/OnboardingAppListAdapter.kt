package com.micoyc.speakthat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OnboardingAppListAdapter(private val onRemoveClick: (String) -> Unit) : 
    RecyclerView.Adapter<OnboardingAppListAdapter.ViewHolder>() {
    
    private var apps: List<String> = emptyList()
    
    fun updateApps(newApps: List<String>) {
        apps = newApps
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_app, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.bind(app, position)
    }
    
    override fun getItemCount(): Int = apps.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appName: TextView = itemView.findViewById(R.id.appName)
        private val packageName: TextView = itemView.findViewById(R.id.packageName)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.buttonDelete)
        
        fun bind(app: String, _position: Int) {
            // Get app display name and icon
            val appDisplayName = try {
                val packageManager = itemView.context.packageManager
                val appInfo = packageManager.getApplicationInfo(app, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                app
            }
            
            // Set app name (just the display name)
            appName.text = appDisplayName
            
            // Set package name (just the package name)
            packageName.text = app
            
            // Set app icon
            android.util.Log.d("OnboardingAppListAdapter", "Attempting to load icon for package: $app")
            try {
                val packageManager = itemView.context.packageManager
                val appInfo = packageManager.getApplicationInfo(app, 0)
                android.util.Log.d("OnboardingAppListAdapter", "Got appInfo for $app: ${appInfo.packageName}")
                val icon = packageManager.getApplicationIcon(appInfo)
                appIcon.setImageDrawable(icon)
                appIcon.visibility = android.view.View.VISIBLE
                android.util.Log.d("OnboardingAppListAdapter", "Successfully loaded icon for $app using getApplicationIcon")
            } catch (e: Exception) {
                android.util.Log.w("OnboardingAppListAdapter", "First method failed for $app: ${e.message}")
                // Try alternative method for getting app icon
                try {
                    val packageManager = itemView.context.packageManager
                    val appInfo = packageManager.getApplicationInfo(app, 0)
                    val icon = appInfo.loadIcon(packageManager)
                    appIcon.setImageDrawable(icon)
                    appIcon.visibility = android.view.View.VISIBLE
                    android.util.Log.d("OnboardingAppListAdapter", "Successfully loaded icon for $app using loadIcon")
                } catch (e2: Exception) {
                    android.util.Log.w("OnboardingAppListAdapter", "Second method failed for $app: ${e2.message}")
                    // Fallback to default icon if we can't get the app icon
                    appIcon.setImageResource(R.drawable.ic_app_default)
                    appIcon.visibility = android.view.View.VISIBLE
                    android.util.Log.w("OnboardingAppListAdapter", "Using default icon for $app")
                }
            }
            
            // Set up delete button - pass the package name instead of position
            deleteButton.setOnClickListener {
                onRemoveClick(app)
            }
        }
    }
} 