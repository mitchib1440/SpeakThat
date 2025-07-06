package com.micoyc.speakthat;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Expandable architecture for visual rule builder system
 * Supports plugin-style registration of condition and action builders
 */
public class RuleBuilderManager {
    private static final String TAG = "RuleBuilderManager";
    
    private Context context;
    private Map<ConditionalFilterManager.ConditionType, ConditionBuilder> conditionBuilders;
    private Map<ConditionalFilterManager.ActionType, ActionBuilder> actionBuilders;
    
    public RuleBuilderManager(Context context) {
        this.context = context;
        this.conditionBuilders = new HashMap<>();
        this.actionBuilders = new HashMap<>();
        
        // Register built-in builders
        registerBuiltInBuilders();
    }
    
    /**
     * Register all built-in condition and action builders
     */
    private void registerBuiltInBuilders() {
        // Register condition builders
        registerConditionBuilder(ConditionalFilterManager.ConditionType.TIME_OF_DAY, new TimeConditionBuilder());
        registerConditionBuilder(ConditionalFilterManager.ConditionType.DAY_OF_WEEK, new DayConditionBuilder());
        registerConditionBuilder(ConditionalFilterManager.ConditionType.APP_PACKAGE, new AppConditionBuilder());
        registerConditionBuilder(ConditionalFilterManager.ConditionType.NOTIFICATION_CONTENT, new ContentConditionBuilder());
        registerConditionBuilder(ConditionalFilterManager.ConditionType.DEVICE_STATE, new DeviceStateConditionBuilder());
        // Note: LOCATION builder will be added when location features are implemented
        
        // Register action builders
        registerActionBuilder(ConditionalFilterManager.ActionType.BLOCK_NOTIFICATION, new BlockActionBuilder());
        registerActionBuilder(ConditionalFilterManager.ActionType.MAKE_PRIVATE, new PrivateActionBuilder());
        registerActionBuilder(ConditionalFilterManager.ActionType.SET_DELAY, new DelayActionBuilder());
        registerActionBuilder(ConditionalFilterManager.ActionType.MODIFY_TEXT, new ModifyTextActionBuilder());
        registerActionBuilder(ConditionalFilterManager.ActionType.DISABLE_MASTER_SWITCH, new MasterSwitchActionBuilder());
        registerActionBuilder(ConditionalFilterManager.ActionType.ENABLE_MASTER_SWITCH, new MasterSwitchActionBuilder());
        registerActionBuilder(ConditionalFilterManager.ActionType.LOG_EVENT, new LogEventActionBuilder());
        
        Log.d(TAG, "Registered " + conditionBuilders.size() + " condition builders and " + actionBuilders.size() + " action builders");
    }
    
    /**
     * Register a new condition builder (plugin-style)
     */
    public void registerConditionBuilder(ConditionalFilterManager.ConditionType type, ConditionBuilder builder) {
        conditionBuilders.put(type, builder);
        Log.d(TAG, "Registered condition builder: " + type.displayName);
    }
    
    /**
     * Register a new action builder (plugin-style)
     */
    public void registerActionBuilder(ConditionalFilterManager.ActionType type, ActionBuilder builder) {
        actionBuilders.put(type, builder);
        Log.d(TAG, "Registered action builder: " + type.displayName);
    }
    
    /**
     * Get all available condition types
     */
    public List<ConditionalFilterManager.ConditionType> getAvailableConditionTypes() {
        return new ArrayList<>(conditionBuilders.keySet());
    }
    
    /**
     * Get all available action types
     */
    public List<ConditionalFilterManager.ActionType> getAvailableActionTypes() {
        return new ArrayList<>(actionBuilders.keySet());
    }
    
    /**
     * Build UI for a specific condition
     */
    public View buildConditionUI(ViewGroup parent, ConditionalFilterManager.ConditionType type, 
                                ConditionalFilterManager.Condition existingCondition) {
        ConditionBuilder builder = conditionBuilders.get(type);
        if (builder != null) {
            return builder.buildUI(context, parent, existingCondition);
        }
        
        Log.w(TAG, "No builder found for condition type: " + type);
        return createUnsupportedConditionView(parent, type);
    }
    
    /**
     * Build UI for a specific action
     */
    public View buildActionUI(ViewGroup parent, ConditionalFilterManager.ActionType type, 
                             ConditionalFilterManager.Action existingAction) {
        ActionBuilder builder = actionBuilders.get(type);
        if (builder != null) {
            return builder.buildUI(context, parent, existingAction);
        }
        
        Log.w(TAG, "No builder found for action type: " + type);
        return createUnsupportedActionView(parent, type);
    }
    
    /**
     * Extract condition from UI
     */
    public ConditionalFilterManager.Condition extractCondition(View conditionView, 
                                                              ConditionalFilterManager.ConditionType type) {
        ConditionBuilder builder = conditionBuilders.get(type);
        if (builder != null) {
            return builder.extractCondition(conditionView);
        }
        return null;
    }
    
    /**
     * Extract action from UI
     */
    public ConditionalFilterManager.Action extractAction(View actionView, 
                                                        ConditionalFilterManager.ActionType type) {
        ActionBuilder builder = actionBuilders.get(type);
        if (builder != null) {
            return builder.extractAction(actionView);
        }
        return null;
    }
    
    /**
     * Create fallback view for unsupported condition types
     */
    private View createUnsupportedConditionView(ViewGroup parent, ConditionalFilterManager.ConditionType type) {
        TextView textView = new TextView(context);
        textView.setText("Condition type '" + type.displayName + "' not yet supported");
        textView.setTextColor(0xFF888888);
        return textView;
    }
    
    /**
     * Create fallback view for unsupported action types
     */
    private View createUnsupportedActionView(ViewGroup parent, ConditionalFilterManager.ActionType type) {
        TextView textView = new TextView(context);
        textView.setText("Action type '" + type.displayName + "' not yet supported");
        textView.setTextColor(0xFF888888);
        return textView;
    }
    
    // ============================================================================
    // Abstract Base Classes for Builders
    // ============================================================================
    
    /**
     * Abstract base class for condition builders
     * Implement this to add new condition types
     */
    public abstract static class ConditionBuilder {
        /**
         * Build the UI for this condition type
         * @param context Android context
         * @param parent Parent view group
         * @param existingCondition Existing condition to populate (null for new)
         * @return View containing the condition UI
         */
        public abstract View buildUI(Context context, ViewGroup parent, ConditionalFilterManager.Condition existingCondition);
        
        /**
         * Extract condition data from the UI
         * @param conditionView The view returned by buildUI
         * @return Condition object with user input
         */
        public abstract ConditionalFilterManager.Condition extractCondition(View conditionView);
        
        /**
         * Get display name for this condition type
         */
        public abstract String getDisplayName();
        
        /**
         * Get description for this condition type
         */
        public abstract String getDescription();
    }
    
    /**
     * Abstract base class for action builders
     * Implement this to add new action types
     */
    public abstract static class ActionBuilder {
        /**
         * Build the UI for this action type
         * @param context Android context
         * @param parent Parent view group
         * @param existingAction Existing action to populate (null for new)
         * @return View containing the action UI
         */
        public abstract View buildUI(Context context, ViewGroup parent, ConditionalFilterManager.Action existingAction);
        
        /**
         * Extract action data from the UI
         * @param actionView The view returned by buildUI
         * @return Action object with user input
         */
        public abstract ConditionalFilterManager.Action extractAction(View actionView);
        
        /**
         * Get display name for this action type
         */
        public abstract String getDisplayName();
        
        /**
         * Get description for this action type
         */
        public abstract String getDescription();
    }
    
    // ============================================================================
    // Built-in Condition Builders
    // ============================================================================
    
    /**
     * Builder for time-of-day conditions
     */
    public static class TimeConditionBuilder extends ConditionBuilder {
        @Override
        public View buildUI(Context context, ViewGroup parent, ConditionalFilterManager.Condition existingCondition) {
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            
            // Time input field
            EditText timeInput = new EditText(context);
            timeInput.setHint("HH:MM or HH:MM-HH:MM");
            timeInput.setTag("time_input");
            
            if (existingCondition != null) {
                timeInput.setText(existingCondition.value);
            }
            
            layout.addView(timeInput);
            return layout;
        }
        
        @Override
        public ConditionalFilterManager.Condition extractCondition(View conditionView) {
            EditText timeInput = conditionView.findViewWithTag("time_input");
            String timeValue = timeInput.getText().toString().trim();
            
            return new ConditionalFilterManager.Condition(
                ConditionalFilterManager.ConditionType.TIME_OF_DAY,
                "",
                ConditionalFilterManager.ComparisonOperator.IN_RANGE,
                timeValue
            );
        }
        
        @Override
        public String getDisplayName() { return "Time of Day"; }
        
        @Override
        public String getDescription() { return "Trigger based on current time"; }
    }
    
    /**
     * Builder for day-of-week conditions
     */
    public static class DayConditionBuilder extends ConditionBuilder {
        @Override
        public View buildUI(Context context, ViewGroup parent, ConditionalFilterManager.Condition existingCondition) {
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            
            // Day selection spinner
            Spinner daySpinner = new Spinner(context);
            String[] days = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "weekday", "weekend"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, days);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            daySpinner.setAdapter(adapter);
            daySpinner.setTag("day_spinner");
            
            if (existingCondition != null) {
                String value = existingCondition.value.toLowerCase();
                for (int i = 0; i < days.length; i++) {
                    if (days[i].equals(value)) {
                        daySpinner.setSelection(i);
                        break;
                    }
                }
            }
            
            layout.addView(daySpinner);
            return layout;
        }
        
        @Override
        public ConditionalFilterManager.Condition extractCondition(View conditionView) {
            Spinner daySpinner = conditionView.findViewWithTag("day_spinner");
            String selectedDay = (String) daySpinner.getSelectedItem();
            
            return new ConditionalFilterManager.Condition(
                ConditionalFilterManager.ConditionType.DAY_OF_WEEK,
                "",
                ConditionalFilterManager.ComparisonOperator.EQUALS,
                selectedDay
            );
        }
        
        @Override
        public String getDisplayName() { return "Day of Week"; }
        
        @Override
        public String getDescription() { return "Trigger on specific days"; }
    }
    
    /**
     * Builder for app package conditions
     */
    public static class AppConditionBuilder extends ConditionBuilder {
        @Override
        public View buildUI(Context context, ViewGroup parent, ConditionalFilterManager.Condition existingCondition) {
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            
            // App package input
            EditText appInput = new EditText(context);
            appInput.setHint("com.example.app");
            appInput.setTag("app_input");
            
            if (existingCondition != null) {
                appInput.setText(existingCondition.value);
            }
            
            layout.addView(appInput);
            return layout;
        }
        
        @Override
        public ConditionalFilterManager.Condition extractCondition(View conditionView) {
            EditText appInput = conditionView.findViewWithTag("app_input");
            String appValue = appInput.getText().toString().trim();
            
            return new ConditionalFilterManager.Condition(
                ConditionalFilterManager.ConditionType.APP_PACKAGE,
                "",
                ConditionalFilterManager.ComparisonOperator.EQUALS,
                appValue
            );
        }
        
        @Override
        public String getDisplayName() { return "App Package"; }
        
        @Override
        public String getDescription() { return "Trigger for specific apps"; }
    }
    
    /**
     * Builder for notification content conditions
     */
    public static class ContentConditionBuilder extends ConditionBuilder {
        @Override
        public View buildUI(Context context, ViewGroup parent, ConditionalFilterManager.Condition existingCondition) {
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            
            // Operator selection
            Spinner operatorSpinner = new Spinner(context);
            String[] operators = {"contains", "does not contain", "equals", "matches pattern"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, operators);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            operatorSpinner.setAdapter(adapter);
            operatorSpinner.setTag("operator_spinner");
            
            // Content input
            EditText contentInput = new EditText(context);
            contentInput.setHint("Text to match");
            contentInput.setTag("content_input");
            
            if (existingCondition != null) {
                contentInput.setText(existingCondition.value);
                // Set operator selection based on existing condition
                String opName = existingCondition.operator.displayName;
                for (int i = 0; i < operators.length; i++) {
                    if (operators[i].equals(opName)) {
                        operatorSpinner.setSelection(i);
                        break;
                    }
                }
            }
            
            layout.addView(operatorSpinner);
            layout.addView(contentInput);
            return layout;
        }
        
        @Override
        public ConditionalFilterManager.Condition extractCondition(View conditionView) {
            Spinner operatorSpinner = conditionView.findViewWithTag("operator_spinner");
            EditText contentInput = conditionView.findViewWithTag("content_input");
            
            String selectedOperator = (String) operatorSpinner.getSelectedItem();
            String contentValue = contentInput.getText().toString().trim();
            
            // Map display names to operators
            ConditionalFilterManager.ComparisonOperator operator;
            switch (selectedOperator) {
                case "contains": operator = ConditionalFilterManager.ComparisonOperator.CONTAINS; break;
                case "does not contain": operator = ConditionalFilterManager.ComparisonOperator.NOT_CONTAINS; break;
                case "equals": operator = ConditionalFilterManager.ComparisonOperator.EQUALS; break;
                case "matches pattern": operator = ConditionalFilterManager.ComparisonOperator.MATCHES_PATTERN; break;
                default: operator = ConditionalFilterManager.ComparisonOperator.CONTAINS; break;
            }
            
            return new ConditionalFilterManager.Condition(
                ConditionalFilterManager.ConditionType.NOTIFICATION_CONTENT,
                "",
                operator,
                contentValue
            );
        }
        
        @Override
        public String getDisplayName() { return "Notification Content"; }
        
        @Override
        public String getDescription() { return "Trigger based on notification text"; }
    }
    
    /**
     * Builder for device state conditions
     */
    public static class DeviceStateConditionBuilder extends ConditionBuilder {
        @Override
        public View buildUI(Context context, ViewGroup parent, ConditionalFilterManager.Condition existingCondition) {
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            
            // Device state selection
            Spinner stateSpinner = new Spinner(context);
            String[] states = {"on", "off", "charging", "not_charging"};
            String[] stateLabels = {"Screen On", "Screen Off", "Charging", "Not Charging"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, stateLabels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            stateSpinner.setAdapter(adapter);
            stateSpinner.setTag("state_spinner");
            
            if (existingCondition != null) {
                String value = existingCondition.value;
                for (int i = 0; i < states.length; i++) {
                    if (states[i].equals(value)) {
                        stateSpinner.setSelection(i);
                        break;
                    }
                }
            }
            
            layout.addView(stateSpinner);
            return layout;
        }
        
        @Override
        public ConditionalFilterManager.Condition extractCondition(View conditionView) {
            Spinner stateSpinner = conditionView.findViewWithTag("state_spinner");
            int selectedIndex = stateSpinner.getSelectedItemPosition();
            
            // Map display labels to actual values
            String[] states = {"on", "off", "charging", "not_charging"};
            String[] parameters = {"screen_state", "screen_state", "charging_state", "charging_state"};
            
            String selectedValue = states[selectedIndex];
            String selectedParameter = parameters[selectedIndex];
            
            return new ConditionalFilterManager.Condition(
                ConditionalFilterManager.ConditionType.DEVICE_STATE,
                selectedParameter,
                ConditionalFilterManager.ComparisonOperator.EQUALS,
                selectedValue
            );
        }
        
        @Override
        public String getDisplayName() { return "Device State"; }
        
        @Override
        public String getDescription() { return "Trigger based on device status"; }
    }
    
    // ============================================================================
    // Built-in Action Builders
    // ============================================================================
    
    /**
     * Builder for block notification actions
     */
    public static class BlockActionBuilder extends ActionBuilder {
        @Override
        public View buildUI(Context context, ViewGroup parent, ConditionalFilterManager.Action existingAction) {
            TextView textView = new TextView(context);
            textView.setText("Block this notification");
            textView.setTextColor(0xFF333333);
            return textView;
        }
        
        @Override
        public ConditionalFilterManager.Action extractAction(View actionView) {
            return new ConditionalFilterManager.Action(
                ConditionalFilterManager.ActionType.BLOCK_NOTIFICATION,
                "",
                "true"
            );
        }
        
        @Override
        public String getDisplayName() { return "Block Notification"; }
        
        @Override
        public String getDescription() { return "Completely block the notification"; }
    }
    
    /**
     * Builder for make private actions
     */
    public static class PrivateActionBuilder extends ActionBuilder {
        @Override
        public View buildUI(Context context, ViewGroup parent, ConditionalFilterManager.Action existingAction) {
            TextView textView = new TextView(context);
            textView.setText("Make notification private");
            textView.setTextColor(0xFF333333);
            return textView;
        }
        
        @Override
        public ConditionalFilterManager.Action extractAction(View actionView) {
            return new ConditionalFilterManager.Action(
                ConditionalFilterManager.ActionType.MAKE_PRIVATE,
                "",
                "true"
            );
        }
        
        @Override
        public String getDisplayName() { return "Make Private"; }
        
        @Override
        public String getDescription() { return "Replace content with [PRIVATE]"; }
    }
    
    /**
     * Builder for delay actions
     */
    public static class DelayActionBuilder extends ActionBuilder {
        @Override
        public View buildUI(Context context, ViewGroup parent, ConditionalFilterManager.Action existingAction) {
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            
            TextView label = new TextView(context);
            label.setText("Delay by ");
            
            EditText delayInput = new EditText(context);
            delayInput.setHint("seconds");
            delayInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            delayInput.setTag("delay_input");
            
            TextView suffix = new TextView(context);
            suffix.setText(" seconds");
            
            if (existingAction != null) {
                delayInput.setText(existingAction.value);
            }
            
            layout.addView(label);
            layout.addView(delayInput);
            layout.addView(suffix);
            return layout;
        }
        
        @Override
        public ConditionalFilterManager.Action extractAction(View actionView) {
            EditText delayInput = actionView.findViewWithTag("delay_input");
            String delayValue = delayInput.getText().toString().trim();
            
            return new ConditionalFilterManager.Action(
                ConditionalFilterManager.ActionType.SET_DELAY,
                "",
                delayValue
            );
        }
        
        @Override
        public String getDisplayName() { return "Set Delay"; }
        
        @Override
        public String getDescription() { return "Delay notification by specified seconds"; }
    }
    
    /**
     * Builder for modify text actions
     */
    public static class ModifyTextActionBuilder extends ActionBuilder {
        @Override
        public View buildUI(Context context, ViewGroup parent, ConditionalFilterManager.Action existingAction) {
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            
            TextView label = new TextView(context);
            label.setText("Replace with: ");
            
            EditText textInput = new EditText(context);
            textInput.setHint("New text");
            textInput.setTag("text_input");
            
            if (existingAction != null) {
                textInput.setText(existingAction.value);
            }
            
            layout.addView(label);
            layout.addView(textInput);
            return layout;
        }
        
        @Override
        public ConditionalFilterManager.Action extractAction(View actionView) {
            EditText textInput = actionView.findViewWithTag("text_input");
            String textValue = textInput.getText().toString().trim();
            
            return new ConditionalFilterManager.Action(
                ConditionalFilterManager.ActionType.MODIFY_TEXT,
                "",
                textValue
            );
        }
        
        @Override
        public String getDisplayName() { return "Modify Text"; }
        
        @Override
        public String getDescription() { return "Replace notification text"; }
    }
    
    /**
     * Builder for master switch actions
     */
    public static class MasterSwitchActionBuilder extends ActionBuilder {
        @Override
        public View buildUI(Context context, ViewGroup parent, ConditionalFilterManager.Action existingAction) {
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            
            // Action selection
            Spinner actionSpinner = new Spinner(context);
            String[] actions = {"Disable SpeakThat", "Enable SpeakThat"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, actions);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            actionSpinner.setAdapter(adapter);
            actionSpinner.setTag("action_spinner");
            
            if (existingAction != null) {
                if (existingAction.type == ConditionalFilterManager.ActionType.DISABLE_MASTER_SWITCH) {
                    actionSpinner.setSelection(0);
                } else {
                    actionSpinner.setSelection(1);
                }
            }
            
            layout.addView(actionSpinner);
            return layout;
        }
        
        @Override
        public ConditionalFilterManager.Action extractAction(View actionView) {
            Spinner actionSpinner = actionView.findViewWithTag("action_spinner");
            int selection = actionSpinner.getSelectedItemPosition();
            
            ConditionalFilterManager.ActionType actionType = (selection == 0) ? 
                ConditionalFilterManager.ActionType.DISABLE_MASTER_SWITCH : 
                ConditionalFilterManager.ActionType.ENABLE_MASTER_SWITCH;
            
            return new ConditionalFilterManager.Action(actionType, "", "true");
        }
        
        @Override
        public String getDisplayName() { return "Master Switch"; }
        
        @Override
        public String getDescription() { return "Enable or disable SpeakThat"; }
    }
    
    /**
     * Builder for log event actions
     */
    public static class LogEventActionBuilder extends ActionBuilder {
        @Override
        public View buildUI(Context context, ViewGroup parent, ConditionalFilterManager.Action existingAction) {
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.HORIZONTAL);
            
            TextView label = new TextView(context);
            label.setText("Log message: ");
            
            EditText messageInput = new EditText(context);
            messageInput.setHint("Custom log message");
            messageInput.setTag("message_input");
            
            if (existingAction != null) {
                messageInput.setText(existingAction.value);
            }
            
            layout.addView(label);
            layout.addView(messageInput);
            return layout;
        }
        
        @Override
        public ConditionalFilterManager.Action extractAction(View actionView) {
            EditText messageInput = actionView.findViewWithTag("message_input");
            String messageValue = messageInput.getText().toString().trim();
            
            return new ConditionalFilterManager.Action(
                ConditionalFilterManager.ActionType.LOG_EVENT,
                "",
                messageValue
            );
        }
        
        @Override
        public String getDisplayName() { return "Log Event"; }
        
        @Override
        public String getDescription() { return "Log a custom message"; }
    }
} 