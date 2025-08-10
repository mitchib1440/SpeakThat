package com.micoyc.speakthat.rules

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

import com.micoyc.speakthat.R
import com.micoyc.speakthat.InAppLogger

class RulesAdapter(
    private var rules: MutableList<Rule>,
    private val onEditRule: (Rule) -> Unit,
    private val onDeleteRule: (Rule) -> Unit,
    private val onToggleRule: (Rule, Boolean) -> Unit
) : RecyclerView.Adapter<RulesAdapter.RuleViewHolder>() {

    class RuleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textRuleName: TextView = view.findViewById(R.id.textRuleName)
        val textRuleSummary: TextView = view.findViewById(R.id.textRuleSummary)
        val textRuleDescription: TextView = view.findViewById(R.id.textRuleDescription)
        val switchEnabled: com.google.android.material.materialswitch.MaterialSwitch = view.findViewById(R.id.switchRuleEnabled)
        val buttonDelete: MaterialButton = view.findViewById(R.id.buttonDeleteRule)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rule, parent, false)
        return RuleViewHolder(view)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        val rule = rules[position]
        
        holder.textRuleName.text = rule.name
        holder.textRuleSummary.text = rule.getSummary()
        
        // Set natural language description
        holder.textRuleDescription.text = rule.getNaturalLanguageDescription(holder.itemView.context)
        
        // Temporarily remove listener to prevent recursive calls
        holder.switchEnabled.setOnCheckedChangeListener(null)
        
        // Set enabled state
        holder.switchEnabled.isChecked = rule.enabled
        
        // Set up click listeners
        holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            onToggleRule(rule, isChecked)
        }
        
        // Set up delete button click listener
        holder.buttonDelete.setOnClickListener {
            onDeleteRule(rule)
        }
        
        // Set up card click for editing (since no edit button)
        holder.itemView.setOnClickListener {
            onEditRule(rule)
        }
    }

    override fun getItemCount() = rules.size

    fun updateRules(newRules: List<Rule>) {
        rules.clear()
        rules.addAll(newRules)
        notifyDataSetChanged()
        InAppLogger.logDebug("RulesAdapter", "Updated rules: ${rules.size} items")
    }

    fun addRule(rule: Rule) {
        rules.add(rule)
        notifyItemInserted(rules.size - 1)
        InAppLogger.logDebug("RulesAdapter", "Added rule: ${rule.getLogMessage()}")
    }

    fun removeRule(rule: Rule) {
        val index = rules.indexOf(rule)
        if (index != -1) {
            rules.removeAt(index)
            notifyItemRemoved(index)
            InAppLogger.logDebug("RulesAdapter", "Removed rule: ${rule.getLogMessage()}")
        }
    }

    fun updateRule(rule: Rule) {
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index != -1) {
            rules[index] = rule
            notifyItemChanged(index)
            InAppLogger.logDebug("RulesAdapter", "Updated rule: ${rule.getLogMessage()}")
        }
    }

    fun getRules(): List<Rule> = rules.toList()
} 