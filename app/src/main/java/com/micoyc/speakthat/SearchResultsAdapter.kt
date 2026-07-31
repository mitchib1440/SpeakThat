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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchResultsAdapter(
    private var groups: List<SettingsSearchEngine.SearchCategoryGroup>,
    private val onItemClick: (SettingsItem) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.searchCategoryIcon)
        val title: TextView = itemView.findViewById(R.id.searchCategoryTitle)
        val itemsContainer: LinearLayout = itemView.findViewById(R.id.searchCategoryItems)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_category_card, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val group = groups[position]
        val context = holder.itemView.context

        holder.icon.setImageResource(group.categoryIconRes)
        holder.title.setText(group.categoryTitleRes)
        holder.itemsContainer.removeAllViews()

        val inflater = LayoutInflater.from(context)
        group.items.forEach { item ->
            val row = inflater.inflate(R.layout.item_search_result, holder.itemsContainer, false)
            row.findViewById<TextView>(R.id.searchResultTitle).setText(item.titleRes)
            row.findViewById<TextView>(R.id.searchResultDescription).setText(item.descriptionRes)
            row.setOnClickListener { onItemClick(item) }
            holder.itemsContainer.addView(row)
        }
    }

    override fun getItemCount(): Int = groups.size

    fun updateResults(newGroups: List<SettingsSearchEngine.SearchCategoryGroup>) {
        groups = newGroups
        notifyDataSetChanged()
    }
}
