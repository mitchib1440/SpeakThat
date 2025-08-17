package com.micoyc.speakthat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.micoyc.speakthat.databinding.ItemOnboardingRuleTemplateBinding
import com.micoyc.speakthat.rules.RuleTemplate
import com.micoyc.speakthat.rules.RuleTemplates

/**
 * Adapter for displaying rule templates in the onboarding flow
 * Allows users to quickly add common rule templates during setup
 */
class OnboardingRuleTemplateAdapter(
    private val onTemplateSelected: (RuleTemplate) -> Unit,
    private val onTemplateConfigured: (RuleTemplate, Map<String, Any>) -> Unit,
    private val onTemplateNeedsConfiguration: (RuleTemplate) -> Unit
) : RecyclerView.Adapter<OnboardingRuleTemplateAdapter.TemplateViewHolder>() {

    private val templates = mutableListOf<RuleTemplate>()

    init {
        // Load templates - we'll populate this when the adapter is created
        loadTemplates()
    }

    private fun loadTemplates() {
        // We'll populate this when we have access to context
        // This will be called from the ViewHolder when binding
    }

    fun loadTemplates(context: android.content.Context) {
        templates.clear()
        templates.addAll(RuleTemplates.getAllTemplates(context))
        notifyDataSetChanged()
        InAppLogger.log("OnboardingRuleTemplateAdapter", "Loaded ${templates.size} rule templates")
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplateViewHolder {
        val binding = ItemOnboardingRuleTemplateBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TemplateViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TemplateViewHolder, position: Int) {
        holder.bind(templates[position])
    }

    override fun getItemCount(): Int = templates.size

    inner class TemplateViewHolder(
        private val binding: ItemOnboardingRuleTemplateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(template: RuleTemplate) {
            binding.textTitle.text = template.name
            binding.textDescription.text = template.description
            binding.imageIcon.setImageResource(template.iconDrawable)
            binding.imageIcon.setColorFilter(android.graphics.Color.WHITE)

            // Set up click listeners
            binding.root.setOnClickListener {
                handleTemplateSelection(template)
            }

            binding.buttonAdd.setOnClickListener {
                handleTemplateSelection(template)
            }

            InAppLogger.log("OnboardingRuleTemplateAdapter", "Bound template: ${template.name}")
        }

        private fun handleTemplateSelection(template: RuleTemplate) {
            if (template.requiresDeviceSelection) {
                // Call the configuration callback for templates that need user input
                onTemplateNeedsConfiguration(template)
            } else {
                // For templates that don't need configuration, call the original callback
                onTemplateSelected(template)
            }
        }
    }
} 