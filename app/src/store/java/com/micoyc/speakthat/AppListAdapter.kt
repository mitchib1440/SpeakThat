package com.micoyc.speakthat

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.ImageView
import android.widget.TextView

/**
 * Store-specific version of AppSearchAdapter that never makes network requests
 * This ensures privacy-conscious users don't have any network activity for app icons
 */
class AppSearchAdapter(
    context: Context,
    private val appList: List<AppListData>
) : ArrayAdapter<AppListData>(context, R.layout.item_app_selector, appList) {
    
    private val inflater = LayoutInflater.from(context)
    private var filteredApps = ArrayList(appList.take(20)) // Start with first 20 apps
    
    override fun getCount(): Int = filteredApps.size
    
    override fun getItem(position: Int): AppListData? = 
        if (position < filteredApps.size) filteredApps[position] else null
    
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_app_selector, parent, false)
        
        val app = getItem(position)
        if (app != null) {
            val iconView = view.findViewById<ImageView>(R.id.appIcon)
            // Store variant: Always use local fallback icons for privacy
            // No network requests are made, even if iconSlug is provided
            iconView.setImageResource(R.drawable.ic_app_unknown)
            
            // Set app name
            val nameView = view.findViewById<TextView>(R.id.appName)
            nameView.text = app.displayName
            
            // Set package name
            val packageView = view.findViewById<TextView>(R.id.packageName)
            packageView.text = app.packageName
        }
        
        return view
    }
    
    override fun getFilter(): Filter = AppFilter()
    
    private inner class AppFilter : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            val suggestions = ArrayList<AppListData>()
            
            val filterText = constraint?.toString()?.lowercase()?.trim() ?: ""
            
            if (filterText.isEmpty()) {
                // Show first 20 apps when no filter
                suggestions.addAll(appList.take(20))
            } else {
                // Filter apps based on query
                for (app in appList) {
                    if (app.matchesQuery(filterText)) {
                        suggestions.add(app)
                    }
                }
            }
            
            results.values = suggestions
            results.count = suggestions.size
            
            // Debug logging
            android.util.Log.d("AppSearchAdapter", "Filtering with '$filterText' - found ${suggestions.size} results")
            
            return results
        }
        
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            filteredApps.clear()
            if (results?.values != null) {
                @Suppress("UNCHECKED_CAST")
                filteredApps.addAll(results.values as List<AppListData>)
            }
            
            // Debug logging
            android.util.Log.d("AppSearchAdapter", "Publishing ${filteredApps.size} results")
            
            // Just notify the adapter of changes
            notifyDataSetChanged()
        }
    }
} 