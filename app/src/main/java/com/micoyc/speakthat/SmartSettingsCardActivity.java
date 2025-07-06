package com.micoyc.speakthat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Card-based Smart Settings interface with visual rule builder
 * Supports both card view and list view modes
 */
public class SmartSettingsCardActivity extends AppCompatActivity {
    private static final String TAG = "SmartSettingsCard";
    
    private ConditionalFilterManager conditionalFilterManager;
    private RuleBuilderManager ruleBuilderManager;
    private LinearLayout rulesContainer;
    private LinearLayout emptyStateContainer;
    private MaterialButton btnViewToggle;
    private boolean isCardView = true;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_settings_card_view);
        
        // Initialize managers
        conditionalFilterManager = new ConditionalFilterManager(this);
        ruleBuilderManager = new RuleBuilderManager(this);
        
        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        // Initialize views
        initializeViews();
        
        // Load and display rules
        refreshRulesList();
        
        Log.d(TAG, "SmartSettingsCardActivity created with visual rule builder");
        InAppLogger.log("SmartSettings", "Card-based Smart Settings opened");
    }
    
    private void initializeViews() {
        rulesContainer = findViewById(R.id.rulesContainer);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        btnViewToggle = findViewById(R.id.btnViewToggle);
        
        // Setup button listeners
        MaterialButton btnAddRule = findViewById(R.id.btnAddRule);
        MaterialButton btnTemplates = findViewById(R.id.btnTemplates);
        MaterialButton btnCreateExampleRules = findViewById(R.id.btnCreateExampleRules);
        
        btnAddRule.setOnClickListener(v -> showVisualRuleBuilder(null));
        btnTemplates.setOnClickListener(v -> showTemplateSelector());
        btnCreateExampleRules.setOnClickListener(v -> createExampleRules());
        btnViewToggle.setOnClickListener(v -> toggleView());
    }
    
    private void toggleView() {
        isCardView = !isCardView;
        btnViewToggle.setText(isCardView ? "List View" : "Card View");
        refreshRulesList();
        
        Toast.makeText(this, "Switched to " + (isCardView ? "Card" : "List") + " View", Toast.LENGTH_SHORT).show();
    }
    
    private void refreshRulesList() {
        rulesContainer.removeAllViews();
        
        List<ConditionalFilterManager.ConditionalRule> rules = conditionalFilterManager.getAllRules();
        
        if (rules.isEmpty()) {
            emptyStateContainer.setVisibility(View.VISIBLE);
            rulesContainer.setVisibility(View.GONE);
        } else {
            emptyStateContainer.setVisibility(View.GONE);
            rulesContainer.setVisibility(View.VISIBLE);
            
            for (ConditionalFilterManager.ConditionalRule rule : rules) {
                if (isCardView) {
                    addRuleCardView(rule);
                } else {
                    addRuleListView(rule);
                }
            }
        }
    }
    
    private void addRuleCardView(ConditionalFilterManager.ConditionalRule rule) {
        View cardView = LayoutInflater.from(this).inflate(R.layout.item_smart_rule_card, rulesContainer, false);
        
        // Setup card content
        TextView tvRuleName = cardView.findViewById(R.id.tvRuleName);
        SwitchCompat switchRuleEnabled = cardView.findViewById(R.id.switchRuleEnabled);
        LinearLayout triggersContainer = cardView.findViewById(R.id.triggersContainer);
        LinearLayout actionsContainer = cardView.findViewById(R.id.actionsContainer);
        MaterialButton btnLogicGate = cardView.findViewById(R.id.btnLogicGate);
        
        // Set rule data
        tvRuleName.setText(rule.name);
        switchRuleEnabled.setChecked(rule.enabled);
        
        // Setup logic gate display (for now, showing AND logic)
        btnLogicGate.setText("And (all conditions must be true)");
        
        // Populate triggers
        triggersContainer.removeAllViews();
        for (ConditionalFilterManager.Condition condition : rule.conditions) {
            TextView triggerView = new TextView(this);
            triggerView.setText("• " + formatCondition(condition));
            triggerView.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            triggerView.setTextSize(14f);
            triggersContainer.addView(triggerView);
        }
        
        // Populate actions
        actionsContainer.removeAllViews();
        for (ConditionalFilterManager.Action action : rule.actions) {
            TextView actionView = new TextView(this);
            actionView.setText("• " + formatAction(action));
            actionView.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
            actionView.setTextSize(14f);
            actionsContainer.addView(actionView);
        }
        
        // Setup action buttons
        MaterialButton btnTestRule = cardView.findViewById(R.id.btnTestRule);
        MaterialButton btnEditRule = cardView.findViewById(R.id.btnEditRule);
        MaterialButton btnDeleteRule = cardView.findViewById(R.id.btnDeleteRule);
        
        btnTestRule.setOnClickListener(v -> testRule(rule));
        btnEditRule.setOnClickListener(v -> showVisualRuleBuilder(rule));
        btnDeleteRule.setOnClickListener(v -> deleteRule(rule));
        
        // Setup toggle functionality
        switchRuleEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            rule.enabled = isChecked;
            conditionalFilterManager.updateRule(rule);
            String status = isChecked ? "enabled" : "disabled";
            Toast.makeText(this, "Rule " + status, Toast.LENGTH_SHORT).show();
            InAppLogger.log("SmartSettings", "Rule " + rule.name + " " + status);
        });
        
        rulesContainer.addView(cardView);
    }
    
    private void addRuleListView(ConditionalFilterManager.ConditionalRule rule) {
        // Use the existing list item layout for list view
        View listView = LayoutInflater.from(this).inflate(R.layout.item_smart_rule, rulesContainer, false);
        
        TextView tvRuleName = listView.findViewById(R.id.textRuleName);
        TextView tvRuleDescription = listView.findViewById(R.id.textDescription);
        TextView tvConditionCount = listView.findViewById(R.id.textConditionCount);
        TextView tvActionCount = listView.findViewById(R.id.textActionCount);
        TextView tvPriority = listView.findViewById(R.id.textPriority);
        SwitchCompat switchEnabled = listView.findViewById(R.id.switchEnabled);
        
        tvRuleName.setText(rule.name);
        tvRuleDescription.setText(rule.description);
        tvConditionCount.setText(rule.conditions.size() + " conditions");
        tvActionCount.setText(rule.actions.size() + " actions");
        tvPriority.setText("Priority " + rule.priority);
        switchEnabled.setChecked(rule.enabled);
        
        // Setup click listeners
        listView.setOnClickListener(v -> showVisualRuleBuilder(rule));
        switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            rule.enabled = isChecked;
            conditionalFilterManager.updateRule(rule);
            String status = isChecked ? "enabled" : "disabled";
            Toast.makeText(this, "Rule " + status, Toast.LENGTH_SHORT).show();
        });
        
        rulesContainer.addView(listView);
    }
    
    private String formatCondition(ConditionalFilterManager.Condition condition) {
        switch (condition.type) {
            case TIME_OF_DAY:
                return "Time is " + condition.value;
            case DAY_OF_WEEK:
                return "Day is " + condition.value;
            case APP_PACKAGE:
                return "App is " + condition.value;
            case NOTIFICATION_CONTENT:
                return "Content " + condition.operator.displayName + " \"" + condition.value + "\"";
            case DEVICE_STATE:
                return "Device " + condition.value.replace("_", " ");
            default:
                return condition.type.displayName + ": " + condition.value;
        }
    }
    
    private String formatAction(ConditionalFilterManager.Action action) {
        switch (action.type) {
            case BLOCK_NOTIFICATION:
                return "Block notification";
            case MAKE_PRIVATE:
                return "Make private";
            case SET_DELAY:
                return "Delay by " + action.value + " seconds";
            case MODIFY_TEXT:
                return "Replace with \"" + action.value + "\"";
            case DISABLE_MASTER_SWITCH:
                return "Disable SpeakThat";
            case ENABLE_MASTER_SWITCH:
                return "Enable SpeakThat";
            case LOG_EVENT:
                return "Log: " + action.value;
            default:
                return action.type.displayName + ": " + action.value;
        }
    }
    
    private void showVisualRuleBuilder(ConditionalFilterManager.ConditionalRule existingRule) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_visual_rule_builder, null);
        
        // Store dialog view reference for use in nested methods
        final View finalDialogView = dialogView;
        
        // Initialize dialog components
        TextInputEditText etRuleName = dialogView.findViewById(R.id.etRuleName);
        TextInputEditText etRuleDescription = dialogView.findViewById(R.id.etRuleDescription);
        SeekBar seekBarPriority = dialogView.findViewById(R.id.seekBarPriority);
        TextView tvPriorityValue = dialogView.findViewById(R.id.tvPriorityValue);
        LinearLayout conditionsContainer = dialogView.findViewById(R.id.conditionsContainer);
        LinearLayout actionsContainer = dialogView.findViewById(R.id.actionsContainer);
        MaterialButton btnAddCondition = dialogView.findViewById(R.id.btnAddCondition);
        MaterialButton btnAddAction = dialogView.findViewById(R.id.btnAddAction);
        MaterialButton btnCloseDialog = dialogView.findViewById(R.id.btnCloseDialog);
        
        // Add error handling for rule name
        com.google.android.material.textfield.TextInputLayout tilRuleName = dialogView.findViewById(R.id.tilRuleName);
        
        // Set up logic gate spinner
        Spinner spinnerLogicGate = dialogView.findViewById(R.id.spinnerLogicGate);
        ArrayAdapter<String> logicAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, 
            new String[]{"AND", "OR"});
        logicAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLogicGate.setAdapter(logicAdapter);
        spinnerLogicGate.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateRulePreview(finalDialogView);
            }
            
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        
        // Set up priority slider
        seekBarPriority.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvPriorityValue.setText(String.valueOf(progress));
                updateRulePreview(finalDialogView);
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // Set up add buttons
        btnAddCondition.setOnClickListener(v -> {
            addConditionToBuilder(conditionsContainer, null, finalDialogView);
            updateRulePreview(finalDialogView);
        });
        
        btnAddAction.setOnClickListener(v -> {
            addActionToBuilder(actionsContainer, null, finalDialogView);
            updateRulePreview(finalDialogView);
        });
        
        // Pre-populate if editing existing rule
        if (existingRule != null) {
            etRuleName.setText(existingRule.name);
            etRuleDescription.setText(existingRule.description);
            seekBarPriority.setProgress(existingRule.priority);
            
            // Add existing conditions
            for (ConditionalFilterManager.Condition condition : existingRule.conditions) {
                addConditionToBuilder(conditionsContainer, condition, finalDialogView);
            }
            
            // Add existing actions
            for (ConditionalFilterManager.Action action : existingRule.actions) {
                addActionToBuilder(actionsContainer, action, finalDialogView);
            }
        } else {
            // Default values for new rule
            seekBarPriority.setProgress(5);
            addConditionToBuilder(conditionsContainer, null, finalDialogView);
            addActionToBuilder(actionsContainer, null, finalDialogView);
        }
        
        // Initial preview update
        updateRulePreview(finalDialogView);
        
        // Create dialog without built-in buttons
        builder.setView(dialogView);
        
        AlertDialog dialog = builder.create();
        
        // Set up custom button handlers
        btnCloseDialog.setOnClickListener(v -> dialog.dismiss());
        
        // Set up dialog buttons (bottom buttons)
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Save", (DialogInterface.OnClickListener) null);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", (DialogInterface.OnClickListener) null);
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Test Rule", (DialogInterface.OnClickListener) null);
        
        dialog.show();
        
        // Override button click handlers to prevent auto-dismiss on validation errors
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            // Clear any previous errors
            tilRuleName.setError(null);
            
            // Validate rule name
            String ruleName = etRuleName.getText().toString().trim();
            if (ruleName.isEmpty()) {
                tilRuleName.setError("Please add a name to your rule");
                etRuleName.requestFocus();
                return; // Don't close dialog
            }
            
            // Validate conditions and actions
            if (conditionsContainer.getChildCount() == 0) {
                Toast.makeText(this, "At least one trigger is required", Toast.LENGTH_SHORT).show();
                return; // Don't close dialog
            }
            
            if (actionsContainer.getChildCount() == 0) {
                Toast.makeText(this, "At least one action is required", Toast.LENGTH_SHORT).show();
                return; // Don't close dialog
            }
            
            // Try to save the rule
            if (saveRuleFromBuilder(finalDialogView, existingRule)) {
                refreshRulesList();
                dialog.dismiss(); // Only dismiss if save was successful
            }
            // If save failed, dialog stays open with error message from saveRuleFromBuilder
        });
        
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> dialog.dismiss());
        
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            // TODO: Implement test functionality
            Toast.makeText(this, "Test functionality coming soon", Toast.LENGTH_SHORT).show();
        });
        
        // Make dialog larger
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.95),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.85)
            );
        }
    }
    
    private void addConditionToBuilder(LinearLayout container, ConditionalFilterManager.Condition existingCondition, View dialogView) {
        View conditionView = LayoutInflater.from(this).inflate(R.layout.item_condition_builder, container, false);
        
        Spinner typeSpinner = conditionView.findViewById(R.id.spinnerConditionType);
        LinearLayout parametersContainer = conditionView.findViewById(R.id.parametersContainer);
        MaterialButton btnRemove = conditionView.findViewById(R.id.btnRemoveCondition);
        
        // Setup condition type spinner
        List<ConditionalFilterManager.ConditionType> conditionTypes = ruleBuilderManager.getAvailableConditionTypes();
        ArrayAdapter<ConditionalFilterManager.ConditionType> adapter = new ArrayAdapter<ConditionalFilterManager.ConditionType>(this, android.R.layout.simple_spinner_item, conditionTypes) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setText(getItem(position).displayName);
                return view;
            }
            
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setText(getItem(position).displayName);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(adapter);
        
        // Setup type change listener
        typeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                ConditionalFilterManager.ConditionType selectedType = conditionTypes.get(position);
                updateConditionParameters(parametersContainer, selectedType, existingCondition);
                updateRulePreview(dialogView);
            }
            
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        
        // Set existing condition
        if (existingCondition != null) {
            for (int i = 0; i < conditionTypes.size(); i++) {
                if (conditionTypes.get(i) == existingCondition.type) {
                    typeSpinner.setSelection(i);
                    break;
                }
            }
        }
        
        // Setup remove button
        btnRemove.setOnClickListener(v -> {
            container.removeView(conditionView);
            updateRulePreview(dialogView);
        });
        
        container.addView(conditionView);
    }
    
    private void addActionToBuilder(LinearLayout container, ConditionalFilterManager.Action existingAction, View dialogView) {
        View actionView = LayoutInflater.from(this).inflate(R.layout.item_action_builder, container, false);
        
        Spinner typeSpinner = actionView.findViewById(R.id.spinnerActionType);
        LinearLayout parametersContainer = actionView.findViewById(R.id.parametersContainer);
        MaterialButton btnRemove = actionView.findViewById(R.id.btnRemoveAction);
        
        // Setup action type spinner
        List<ConditionalFilterManager.ActionType> actionTypes = ruleBuilderManager.getAvailableActionTypes();
        ArrayAdapter<ConditionalFilterManager.ActionType> adapter = new ArrayAdapter<ConditionalFilterManager.ActionType>(this, android.R.layout.simple_spinner_item, actionTypes) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setText(getItem(position).displayName);
                return view;
            }
            
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setText(getItem(position).displayName);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(adapter);
        
        // Setup type change listener
        typeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                ConditionalFilterManager.ActionType selectedType = actionTypes.get(position);
                updateActionParameters(parametersContainer, selectedType, existingAction);
                updateRulePreview(dialogView);
            }
            
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        
        // Set existing action
        if (existingAction != null) {
            for (int i = 0; i < actionTypes.size(); i++) {
                if (actionTypes.get(i) == existingAction.type) {
                    typeSpinner.setSelection(i);
                    break;
                }
            }
        }
        
        // Setup remove button
        btnRemove.setOnClickListener(v -> {
            container.removeView(actionView);
            updateRulePreview(dialogView);
        });
        
        container.addView(actionView);
    }
    
    private void updateConditionParameters(LinearLayout container, ConditionalFilterManager.ConditionType type, ConditionalFilterManager.Condition existingCondition) {
        container.removeAllViews();
        View parameterView = ruleBuilderManager.buildConditionUI(container, type, existingCondition);
        container.addView(parameterView);
    }
    
    private void updateActionParameters(LinearLayout container, ConditionalFilterManager.ActionType type, ConditionalFilterManager.Action existingAction) {
        container.removeAllViews();
        View parameterView = ruleBuilderManager.buildActionUI(container, type, existingAction);
        container.addView(parameterView);
    }
    
    private void updateRulePreview(View dialogView) {
        TextView tvRulePreview = dialogView.findViewById(R.id.tvRulePreview);
        
        // Build preview text based on current rule configuration
        StringBuilder preview = new StringBuilder("This rule will trigger when ");
        
        LinearLayout conditionsContainer = dialogView.findViewById(R.id.conditionsContainer);
        int conditionCount = conditionsContainer.getChildCount();
        
        if (conditionCount == 0) {
            preview.append("no conditions are set");
        } else {
            Spinner logicSpinner = dialogView.findViewById(R.id.spinnerLogicGate);
            String logic = logicSpinner.getSelectedItem().toString();
            
            if (conditionCount == 1) {
                preview.append("the condition is met");
            } else {
                preview.append(logic.equals("AND") ? "all conditions are met" : "any condition is met");
            }
        }
        
        LinearLayout actionsContainer = dialogView.findViewById(R.id.actionsContainer);
        int actionCount = actionsContainer.getChildCount();
        
        if (actionCount > 0) {
            preview.append(" and will perform ").append(actionCount).append(" action");
            if (actionCount > 1) preview.append("s");
        }
        
        preview.append(".");
        
        tvRulePreview.setText(preview.toString());
    }
    
    private boolean saveRuleFromBuilder(View dialogView, ConditionalFilterManager.ConditionalRule existingRule) {
        // Extract rule data from dialog
        TextInputEditText etRuleName = dialogView.findViewById(R.id.etRuleName);
        TextInputEditText etRuleDescription = dialogView.findViewById(R.id.etRuleDescription);
        SeekBar seekBarPriority = dialogView.findViewById(R.id.seekBarPriority);
        LinearLayout conditionsContainer = dialogView.findViewById(R.id.conditionsContainer);
        LinearLayout actionsContainer = dialogView.findViewById(R.id.actionsContainer);
        TextInputLayout tilRuleName = dialogView.findViewById(R.id.tilRuleName);
        
        String name = etRuleName.getText().toString().trim();
        String description = etRuleDescription.getText().toString().trim();
        int priority = seekBarPriority.getProgress();
        
        // Validate rule name
        if (name.isEmpty()) {
            tilRuleName.setError("Please add a name to your rule");
            etRuleName.requestFocus();
            return false; // Don't save, keep dialog open
        }
        tilRuleName.setError(null); // Clear any previous errors
        
        // Extract conditions
        List<ConditionalFilterManager.Condition> conditions = new ArrayList<>();
        for (int i = 0; i < conditionsContainer.getChildCount(); i++) {
            View conditionView = conditionsContainer.getChildAt(i);
            Spinner typeSpinner = conditionView.findViewById(R.id.spinnerConditionType);
            LinearLayout parametersContainer = conditionView.findViewById(R.id.parametersContainer);
            
            if (parametersContainer.getChildCount() > 0) {
                ConditionalFilterManager.ConditionType type = (ConditionalFilterManager.ConditionType) typeSpinner.getSelectedItem();
                ConditionalFilterManager.Condition condition = ruleBuilderManager.extractCondition(parametersContainer.getChildAt(0), type);
                if (condition != null) {
                    conditions.add(condition);
                }
            }
        }
        
        // Extract actions
        List<ConditionalFilterManager.Action> actions = new ArrayList<>();
        for (int i = 0; i < actionsContainer.getChildCount(); i++) {
            View actionView = actionsContainer.getChildAt(i);
            Spinner typeSpinner = actionView.findViewById(R.id.spinnerActionType);
            LinearLayout parametersContainer = actionView.findViewById(R.id.parametersContainer);
            
            if (parametersContainer.getChildCount() > 0) {
                ConditionalFilterManager.ActionType type = (ConditionalFilterManager.ActionType) typeSpinner.getSelectedItem();
                ConditionalFilterManager.Action action = ruleBuilderManager.extractAction(parametersContainer.getChildAt(0), type);
                if (action != null) {
                    actions.add(action);
                }
            }
        }
        
        // Create or update rule
        ConditionalFilterManager.ConditionalRule rule;
        if (existingRule != null) {
            rule = existingRule;
            rule.name = name;
            rule.description = description;
            rule.priority = priority;
            rule.conditions = conditions;
            rule.actions = actions;
            rule.lastModified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            conditionalFilterManager.updateRule(rule);
            Toast.makeText(this, "Rule updated successfully", Toast.LENGTH_SHORT).show();
            InAppLogger.log("SmartSettings", "Updated rule: " + name);
        } else {
            rule = new ConditionalFilterManager.ConditionalRule();
            rule.name = name;
            rule.description = description;
            rule.priority = priority;
            rule.conditions = conditions;
            rule.actions = actions;
            rule.enabled = true;
            conditionalFilterManager.addRule(rule);
            Toast.makeText(this, "Rule created successfully", Toast.LENGTH_SHORT).show();
            InAppLogger.log("SmartSettings", "Created rule: " + name);
        }
        
        return true;
    }
    
    private void testRule(ConditionalFilterManager.ConditionalRule rule) {
        // TODO: Implement rule testing functionality
        Toast.makeText(this, "Testing rule: " + rule.name, Toast.LENGTH_SHORT).show();
        InAppLogger.log("SmartSettings", "Testing rule: " + rule.name);
    }
    
    private void deleteRule(ConditionalFilterManager.ConditionalRule rule) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Rule")
            .setMessage("Are you sure you want to delete the rule '" + rule.name + "'?")
            .setPositiveButton("Delete", (dialog, which) -> {
                conditionalFilterManager.removeRule(rule.id);
                refreshRulesList();
                Toast.makeText(this, "Rule deleted", Toast.LENGTH_SHORT).show();
                InAppLogger.log("SmartSettings", "Deleted rule: " + rule.name);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void showTemplateSelector() {
        // Use existing template functionality from SmartSettingsActivity
        String[] templates = {"🌙 Night Mode", "📱 Screen On", "💼 Work Hours", "🎯 Focus Mode", "😴 Quiet Time"};
        
        new AlertDialog.Builder(this)
            .setTitle("Choose Template")
            .setItems(templates, (dialog, which) -> {
                createTemplateRule(which);
                refreshRulesList();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void createTemplateRule(int templateIndex) {
        // Use the same template creation logic from SmartSettingsActivity
        ConditionalFilterManager.ConditionalRule rule = new ConditionalFilterManager.ConditionalRule();
        
        switch (templateIndex) {
            case 0: // Night Mode
                rule.name = "🌙 Night Mode";
                rule.description = "Automatically disables SpeakThat when charging at night";
                rule.priority = 8;
                rule.conditions = new ArrayList<>();
                rule.conditions.add(new ConditionalFilterManager.Condition(
                    ConditionalFilterManager.ConditionType.TIME_OF_DAY,
                    "",
                    ConditionalFilterManager.ComparisonOperator.IN_RANGE,
                    "22:00-06:00"
                ));
                rule.actions = new ArrayList<>();
                rule.actions.add(new ConditionalFilterManager.Action(
                    ConditionalFilterManager.ActionType.DISABLE_MASTER_SWITCH,
                    "",
                    "true"
                ));
                break;
                
            case 1: // Screen On
                rule.name = "📱 Screen On";
                rule.description = "Prevents notifications when actively using phone";
                rule.priority = 9;
                rule.conditions = new ArrayList<>();
                rule.conditions.add(new ConditionalFilterManager.Condition(
                    ConditionalFilterManager.ConditionType.DEVICE_STATE,
                    "screen_state",
                    ConditionalFilterManager.ComparisonOperator.EQUALS,
                    "on"
                ));
                rule.actions = new ArrayList<>();
                rule.actions.add(new ConditionalFilterManager.Action(
                    ConditionalFilterManager.ActionType.BLOCK_NOTIFICATION,
                    "",
                    "true"
                ));
                break;
                
            case 2: // Work Hours
                rule.name = "💼 Work Hours";
                rule.description = "Makes social media private during work hours";
                rule.priority = 6;
                rule.conditions = new ArrayList<>();
                rule.conditions.add(new ConditionalFilterManager.Condition(
                    ConditionalFilterManager.ConditionType.TIME_OF_DAY,
                    "",
                    ConditionalFilterManager.ComparisonOperator.IN_RANGE,
                    "09:00-17:00"
                ));
                rule.conditions.add(new ConditionalFilterManager.Condition(
                    ConditionalFilterManager.ConditionType.DAY_OF_WEEK,
                    "",
                    ConditionalFilterManager.ComparisonOperator.EQUALS,
                    "weekday"
                ));
                rule.conditions.add(new ConditionalFilterManager.Condition(
                    ConditionalFilterManager.ConditionType.APP_PACKAGE,
                    "",
                    ConditionalFilterManager.ComparisonOperator.CONTAINS,
                    "social"
                ));
                rule.actions = new ArrayList<>();
                rule.actions.add(new ConditionalFilterManager.Action(
                    ConditionalFilterManager.ActionType.MAKE_PRIVATE,
                    "",
                    "true"
                ));
                break;
                
            case 3: // Focus Mode
                rule.name = "🎯 Focus Mode";
                rule.description = "Adds delay to distracting app notifications";
                rule.priority = 7;
                rule.conditions = new ArrayList<>();
                rule.conditions.add(new ConditionalFilterManager.Condition(
                    ConditionalFilterManager.ConditionType.APP_PACKAGE,
                    "",
                    ConditionalFilterManager.ComparisonOperator.CONTAINS,
                    "entertainment"
                ));
                rule.actions = new ArrayList<>();
                rule.actions.add(new ConditionalFilterManager.Action(
                    ConditionalFilterManager.ActionType.SET_DELAY,
                    "",
                    "300"
                ));
                break;
                
            case 4: // Quiet Time
                rule.name = "😴 Quiet Time";
                rule.description = "Completely blocks notifications during set hours";
                rule.priority = 10;
                rule.conditions = new ArrayList<>();
                rule.conditions.add(new ConditionalFilterManager.Condition(
                    ConditionalFilterManager.ConditionType.TIME_OF_DAY,
                    "",
                    ConditionalFilterManager.ComparisonOperator.IN_RANGE,
                    "23:00-07:00"
                ));
                rule.actions = new ArrayList<>();
                rule.actions.add(new ConditionalFilterManager.Action(
                    ConditionalFilterManager.ActionType.BLOCK_NOTIFICATION,
                    "",
                    "true"
                ));
                break;
        }
        
        rule.enabled = true;
        conditionalFilterManager.addRule(rule);
        
        Toast.makeText(this, "Created template rule: " + rule.name, Toast.LENGTH_SHORT).show();
        InAppLogger.log("SmartSettings", "Created template rule: " + rule.name);
    }
    
    private void createExampleRules() {
        conditionalFilterManager.createExampleRules();
        refreshRulesList();
        Toast.makeText(this, "Example rules created", Toast.LENGTH_SHORT).show();
        InAppLogger.log("SmartSettings", "Created example rules");
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
} 