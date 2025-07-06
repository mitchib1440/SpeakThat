package com.micoyc.speakthat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.micoyc.speakthat.ConditionalFilterManager.ConditionalRule;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying conditional rules in the Smart Settings RecyclerView
 */
public class SmartRulesAdapter extends RecyclerView.Adapter<SmartRulesAdapter.RuleViewHolder> {
    
    private List<ConditionalRule> rulesList;
    private OnRuleClickListener onRuleClickListener;
    private OnRuleToggleListener onRuleToggleListener;
    private OnRuleDeleteListener onRuleDeleteListener;
    
    public interface OnRuleClickListener {
        void onRuleClick(ConditionalRule rule);
    }
    
    public interface OnRuleToggleListener {
        void onRuleToggle(ConditionalRule rule, boolean enabled);
    }
    
    public interface OnRuleDeleteListener {
        void onRuleDelete(ConditionalRule rule);
    }
    
    public SmartRulesAdapter(List<ConditionalRule> rulesList, 
                           OnRuleClickListener onRuleClickListener,
                           OnRuleToggleListener onRuleToggleListener,
                           OnRuleDeleteListener onRuleDeleteListener) {
        this.rulesList = rulesList;
        this.onRuleClickListener = onRuleClickListener;
        this.onRuleToggleListener = onRuleToggleListener;
        this.onRuleDeleteListener = onRuleDeleteListener;
    }
    
    @NonNull
    @Override
    public RuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_smart_rule, parent, false);
        return new RuleViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull RuleViewHolder holder, int position) {
        ConditionalRule rule = rulesList.get(position);
        holder.bind(rule, onRuleClickListener, onRuleToggleListener, onRuleDeleteListener);
    }
    
    @Override
    public int getItemCount() {
        return rulesList.size();
    }
    
    public static class RuleViewHolder extends RecyclerView.ViewHolder {
        private TextView textRuleName;
        private TextView textDescription;
        private TextView textPriority;
        private TextView textConditionCount;
        private TextView textActionCount;
        private TextView textCreatedDate;
        private SwitchCompat switchEnabled;
        private MaterialButton btnDelete;
        
        public RuleViewHolder(@NonNull View itemView) {
            super(itemView);
            textRuleName = itemView.findViewById(R.id.textRuleName);
            textDescription = itemView.findViewById(R.id.textDescription);
            textPriority = itemView.findViewById(R.id.textPriority);
            textConditionCount = itemView.findViewById(R.id.textConditionCount);
            textActionCount = itemView.findViewById(R.id.textActionCount);
            textCreatedDate = itemView.findViewById(R.id.textCreatedDate);
            switchEnabled = itemView.findViewById(R.id.switchEnabled);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
        
        public void bind(ConditionalRule rule, 
                        OnRuleClickListener onRuleClickListener,
                        OnRuleToggleListener onRuleToggleListener,
                        OnRuleDeleteListener onRuleDeleteListener) {
            
            // Set rule data
            textRuleName.setText(rule.name);
            textDescription.setText(rule.description);
            textPriority.setText("Priority " + rule.priority);
            
            // Set counts
            int conditionCount = rule.conditions != null ? rule.conditions.size() : 0;
            int actionCount = rule.actions != null ? rule.actions.size() : 0;
            
            textConditionCount.setText(conditionCount + " condition" + (conditionCount == 1 ? "" : "s"));
            textActionCount.setText(actionCount + " action" + (actionCount == 1 ? "" : "s"));
            
            // Set created date
            if (rule.createdDate != null && !rule.createdDate.isEmpty()) {
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                    textCreatedDate.setText("Created: " + outputFormat.format(inputFormat.parse(rule.createdDate)));
                } catch (Exception e) {
                    textCreatedDate.setText("Created: " + rule.createdDate);
                }
            } else {
                textCreatedDate.setText("Created: Unknown");
            }
            
            // Set enabled state
            switchEnabled.setChecked(rule.enabled);
            
            // Set visual state based on enabled/disabled
            float alpha = rule.enabled ? 1.0f : 0.6f;
            textRuleName.setAlpha(alpha);
            textDescription.setAlpha(alpha);
            textPriority.setAlpha(alpha);
            textConditionCount.setAlpha(alpha);
            textActionCount.setAlpha(alpha);
            textCreatedDate.setAlpha(alpha);
            
            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (onRuleClickListener != null) {
                    onRuleClickListener.onRuleClick(rule);
                }
            });
            
            switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (onRuleToggleListener != null) {
                    onRuleToggleListener.onRuleToggle(rule, isChecked);
                }
                
                // Update visual state
                float newAlpha = isChecked ? 1.0f : 0.6f;
                textRuleName.setAlpha(newAlpha);
                textDescription.setAlpha(newAlpha);
                textPriority.setAlpha(newAlpha);
                textConditionCount.setAlpha(newAlpha);
                textActionCount.setAlpha(newAlpha);
                textCreatedDate.setAlpha(newAlpha);
            });
            
            btnDelete.setOnClickListener(v -> {
                if (onRuleDeleteListener != null) {
                    onRuleDeleteListener.onRuleDelete(rule);
                }
            });
        }
    }
} 