package com.micoyc.speakthat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchResultsAdapter(
    private var searchResults: List<SettingsItem>,
    private val onItemClick: (SettingsItem) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.SearchResultViewHolder>() {

    class SearchResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val categoryIcon: TextView = itemView.findViewById(R.id.searchResultIcon)
        val title: TextView = itemView.findViewById(R.id.searchResultTitle)
        val description: TextView = itemView.findViewById(R.id.searchResultDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return SearchResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        val result = searchResults[position]
        
        holder.categoryIcon.text = result.categoryIcon
        holder.title.text = result.title
        holder.description.text = "${result.description} (${result.categoryTitle})"
        
        holder.itemView.setOnClickListener {
            onItemClick(result)
        }
    }

    override fun getItemCount(): Int = searchResults.size

    fun updateResults(newResults: List<SettingsItem>) {
        searchResults = newResults
        notifyDataSetChanged()
    }
} 