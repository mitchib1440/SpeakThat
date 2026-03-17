/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

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