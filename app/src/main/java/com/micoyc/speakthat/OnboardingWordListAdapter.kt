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
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OnboardingWordListAdapter(private val onRemoveClick: (String) -> Unit) : 
    RecyclerView.Adapter<OnboardingWordListAdapter.ViewHolder>() {
    
    private var words: List<String> = emptyList()
    
    fun updateWords(newWords: List<String>) {
        words = newWords
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_word, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val word = words[position]
        holder.bind(word, position)
    }
    
    override fun getItemCount(): Int = words.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val wordText: TextView = itemView.findViewById(R.id.wordText)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.buttonDelete)
        
        fun bind(word: String, _position: Int) {
            wordText.text = word
            
            // Set up delete button - pass the word instead of position
            deleteButton.setOnClickListener {
                onRemoveClick(word)
            }
        }
    }
} 