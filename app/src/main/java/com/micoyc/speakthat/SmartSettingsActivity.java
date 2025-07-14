package com.micoyc.speakthat;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.micoyc.speakthat.databinding.ActivitySmartSettingsBinding;
import com.micoyc.speakthat.ConditionalFilterManager.ConditionalRule;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/**
 * SmartSettingsActivity - Main interface for managing conditional rules
 * 
 * This activity allows users to:
 * - View existing conditional rules
 * - Add new rules with visual builder
 * - Edit existing rules
 * - Enable/disable rules
 * - Delete rules
 * - Use quick rule templates
 * 
 * Uses the ConditionalFilterManager foundation built in previous session.
 */
public class SmartSettingsActivity extends AppCompatActivity {
    
    private ActivitySmartSettingsBinding binding;
    private SharedPreferences sharedPreferences;
    private ConditionalFilterManager conditionalFilterManager;
    private SmartRulesAdapter smartRulesAdapter;
    private List<ConditionalRule> rulesList;
    
    private static final String PREFS_NAME = "SpeakThatPrefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme FIRST before anything else
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applySavedTheme();
        
        super.onCreate(savedInstanceState);
        binding = ActivitySmartSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize components
        conditionalFilterManager = new ConditionalFilterManager(this);
        rulesList = new ArrayList<>();
        
        setupToolbar();
        setupRecyclerView();
        setupClickListeners();
        loadRules();
    }
    
    private void applySavedTheme() {
        boolean isDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false); // Default to light mode
        
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }
    
    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }
    
    private void setupRecyclerView() {
        smartRulesAdapter = new SmartRulesAdapter(rulesList, this::onRuleClick, this::onRuleToggle, this::onRuleDelete);
        binding.recyclerViewRules.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewRules.setAdapter(smartRulesAdapter);
    }
    
    private void setupClickListeners() {
        binding.btnAddRule.setOnClickListener(v -> showAddRuleDialog());
        binding.btnQuickTemplates.setOnClickListener(v -> showQuickTemplatesDialog());
        binding.btnCreateExamples.setOnClickListener(v -> createExampleRules());
    }
    
    private void loadRules() {
        rulesList.clear();
        rulesList.addAll(conditionalFilterManager.getAllRules());
        smartRulesAdapter.notifyDataSetChanged();
        
        // Show/hide empty state
        if (rulesList.isEmpty()) {
            binding.recyclerViewRules.setVisibility(View.GONE);
            binding.emptyStateLayout.setVisibility(View.VISIBLE);
        } else {
            binding.recyclerViewRules.setVisibility(View.VISIBLE);
            binding.emptyStateLayout.setVisibility(View.GONE);
        }
    }
    
    private void onRuleClick(ConditionalRule rule) {
        // TODO: Open rule editor dialog
        Toast.makeText(this, "Edit rule: " + rule.name, Toast.LENGTH_SHORT).show();
    }
    
    private void onRuleToggle(ConditionalRule rule, boolean enabled) {
        rule.enabled = enabled;
        conditionalFilterManager.updateRule(rule);
        
        String status = enabled ? "enabled" : "disabled";
        Toast.makeText(this, "Rule " + status + ": " + rule.name, Toast.LENGTH_SHORT).show();
        
        InAppLogger.log("SmartSettings", "Rule " + status + ": " + rule.name);
    }
    
    private void onRuleDelete(ConditionalRule rule) {
        conditionalFilterManager.removeRule(rule.id);
        loadRules();
        Toast.makeText(this, "Deleted rule: " + rule.name, Toast.LENGTH_SHORT).show();
        
        InAppLogger.log("SmartSettings", "Deleted rule: " + rule.name);
    }
    
    private void showAddRuleDialog() {
        // Show guidance dialog first for new users
        showRuleBuilderGuidance();
    }
    
    private void showRuleBuilderGuidance() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("🎓 How Smart Rules Work");
        
        String guidance = "Smart Rules help SpeakThat adapt to your daily routine!\n\n" +
                "📋 TRIGGERS (When): Set conditions like time, apps, or content\n" +
                "⚙️ ACTIONS (What): Choose what happens - disable, delay, or make private\n" +
                "🔗 LOGIC: Combine multiple triggers with AND/OR\n\n" +
                "💡 TIP: Start simple with one trigger and one action, then add complexity as needed!\n\n" +
                "Ready to create your first rule?";
        
        builder.setMessage(guidance);
        builder.setPositiveButton("Let's Build!", (dialog, which) -> {
            showBasicRuleBuilder();
        });
        builder.setNeutralButton("Show Templates", (dialog, which) -> {
            showQuickTemplatesDialog();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void showBasicRuleBuilder() {
        // For now, show the concept
        String message = "🚧 Visual Rule Builder Coming Soon!\n\n" +
                "The full drag-and-drop rule builder is in development.\n\n" +
                "For now, try the example rules or templates to see how Smart Rules work!";
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("🛠️ Rule Builder");
        builder.setMessage(message);
        builder.setPositiveButton("OK", null);
        builder.show();
        
        InAppLogger.log("SmartSettings", "User accessed rule builder guidance");
    }
    
    private void showQuickTemplatesDialog() {
        String[] templates = {
            "🌙 Night Mode - Disable when charging at night",
            "📱 Screen On - Disable when screen is active", 
            "💼 Work Hours - Make social media private during work",
            "🎯 Focus Mode - Delay notifications from distracting apps",
            "🔕 Quiet Time - Block notifications during set hours"
        };
        
        String[] descriptions = {
            "Automatically disables SpeakThat when it's late and your phone is charging",
            "Prevents notifications from being read when you're actively using your phone", 
            "Makes social media notifications private during work hours",
            "Adds delay to notifications from apps you find distracting",
            "Completely blocks notifications during your chosen quiet hours"
        };
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("🚀 Quick Rule Templates");
        builder.setItems(templates, (dialog, which) -> {
            createTemplateRule(which, templates[which], descriptions[which]);
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void createTemplateRule(int templateIndex, String name, String description) {
        // Create actual functional template rules
        ConditionalRule newRule = null;
        
        switch (templateIndex) {
            case 0: // Night Mode
                newRule = createNightModeRule();
                break;
            case 1: // Screen On
                newRule = createScreenOnRule();
                break;
            case 2: // Work Hours
                newRule = createWorkHoursRule();
                break;
            case 3: // Focus Mode
                newRule = createFocusModeRule();
                break;
            case 4: // Quiet Time
                newRule = createQuietTimeRule();
                break;
        }
        
        if (newRule != null) {
            conditionalFilterManager.addRule(newRule);
            loadRules();
            Toast.makeText(this, "Created rule: " + newRule.name, Toast.LENGTH_SHORT).show();
            InAppLogger.log("SmartSettings", "Created template rule: " + newRule.name);
        }
    }
    
    private ConditionalRule createNightModeRule() {
        ConditionalRule rule = new ConditionalRule();
        rule.id = "template_night_mode_" + System.currentTimeMillis();
        rule.name = "🌙 Night Mode";
        rule.description = "Automatically disables SpeakThat when charging at night (10 PM - 6 AM)";
        rule.enabled = true;
        rule.priority = 8;
        
        // Condition: Time between 10 PM and 6 AM
        ConditionalFilterManager.Condition timeCondition = new ConditionalFilterManager.Condition(
            ConditionalFilterManager.ConditionType.TIME_OF_DAY,
            "time_range",
            ConditionalFilterManager.ComparisonOperator.IN_RANGE,
            "22:00-06:00"
        );
        rule.conditions.add(timeCondition);
        
        // Action: Disable SpeakThat
        ConditionalFilterManager.Action action = new ConditionalFilterManager.Action(
            ConditionalFilterManager.ActionType.DISABLE_MASTER_SWITCH,
            "",
            ""
        );
        rule.actions.add(action);
        
        return rule;
    }
    
    private ConditionalRule createScreenOnRule() {
        ConditionalRule rule = new ConditionalRule();
        rule.id = "template_screen_on_" + System.currentTimeMillis();
        rule.name = "📱 Screen On";
        rule.description = "Prevents notifications when actively using phone (screen is on)";
        rule.enabled = true;
        rule.priority = 9;
        
        // Condition: Screen is on (using device state)
        ConditionalFilterManager.Condition screenCondition = new ConditionalFilterManager.Condition(
            ConditionalFilterManager.ConditionType.DEVICE_STATE,
            "screen_state",
            ConditionalFilterManager.ComparisonOperator.EQUALS,
            "on"
        );
        rule.conditions.add(screenCondition);
        
        // Action: Block notifications
        ConditionalFilterManager.Action action = new ConditionalFilterManager.Action(
            ConditionalFilterManager.ActionType.BLOCK_NOTIFICATION,
            "",
            ""
        );
        rule.actions.add(action);
        
        return rule;
    }
    
    private ConditionalRule createWorkHoursRule() {
        ConditionalRule rule = new ConditionalRule();
        rule.id = "template_work_hours_" + System.currentTimeMillis();
        rule.name = "💼 Work Hours";
        rule.description = "Makes social media notifications private during work hours (9 AM - 5 PM, weekdays)";
        rule.enabled = true;
        rule.priority = 6;
        
        // Condition 1: Time between 9 AM and 5 PM
        ConditionalFilterManager.Condition timeCondition = new ConditionalFilterManager.Condition(
            ConditionalFilterManager.ConditionType.TIME_OF_DAY,
            "time_range",
            ConditionalFilterManager.ComparisonOperator.IN_RANGE,
            "09:00-17:00"
        );
        rule.conditions.add(timeCondition);
        
        // Condition 2: Weekdays only
        ConditionalFilterManager.Condition dayCondition = new ConditionalFilterManager.Condition(
            ConditionalFilterManager.ConditionType.DAY_OF_WEEK,
            "weekdays",
            ConditionalFilterManager.ComparisonOperator.CONTAINS,
            "MON,TUE,WED,THU,FRI"
        );
        rule.conditions.add(dayCondition);
        
        // Condition 3: Social media apps
        ConditionalFilterManager.Condition appCondition = new ConditionalFilterManager.Condition(
            ConditionalFilterManager.ConditionType.APP_PACKAGE,
            "social_media",
            ConditionalFilterManager.ComparisonOperator.CONTAINS,
            "com.instagram.android,com.facebook.katana,com.twitter.android,com.snapchat.android,com.tiktok"
        );
        rule.conditions.add(appCondition);
        
        // Action: Make notifications private
        ConditionalFilterManager.Action action = new ConditionalFilterManager.Action(
            ConditionalFilterManager.ActionType.MAKE_PRIVATE,
            "",
            ""
        );
        rule.actions.add(action);
        
        return rule;
    }
    
    private ConditionalRule createFocusModeRule() {
        ConditionalRule rule = new ConditionalRule();
        rule.id = "template_focus_mode_" + System.currentTimeMillis();
        rule.name = "🎯 Focus Mode";
        rule.description = "Adds 5-minute delay to notifications from distracting apps";
        rule.enabled = true;
        rule.priority = 5;
        
        // Condition: Distracting apps
        ConditionalFilterManager.Condition appCondition = new ConditionalFilterManager.Condition(
            ConditionalFilterManager.ConditionType.APP_PACKAGE,
            "distracting_apps",
            ConditionalFilterManager.ComparisonOperator.CONTAINS,
            "com.instagram.android,com.facebook.katana,com.twitter.android,com.youtube.android,com.reddit.frontpage"
        );
        rule.conditions.add(appCondition);
        
        // Action: Delay notifications
        ConditionalFilterManager.Action action = new ConditionalFilterManager.Action(
            ConditionalFilterManager.ActionType.SET_DELAY,
            "delay_seconds",
            "300" // 5 minutes in seconds
        );
        rule.actions.add(action);
        
        return rule;
    }
    
    private ConditionalRule createQuietTimeRule() {
        ConditionalRule rule = new ConditionalRule();
        rule.id = "template_quiet_time_" + System.currentTimeMillis();
        rule.name = "🔕 Quiet Time";
        rule.description = "Blocks all notifications during quiet hours (11 PM - 7 AM)";
        rule.enabled = true;
        rule.priority = 10;
        
        // Condition: Quiet hours
        ConditionalFilterManager.Condition timeCondition = new ConditionalFilterManager.Condition(
            ConditionalFilterManager.ConditionType.TIME_OF_DAY,
            "quiet_hours",
            ConditionalFilterManager.ComparisonOperator.IN_RANGE,
            "23:00-07:00"
        );
        rule.conditions.add(timeCondition);
        
        // Action: Block all notifications
        ConditionalFilterManager.Action action = new ConditionalFilterManager.Action(
            ConditionalFilterManager.ActionType.BLOCK_NOTIFICATION,
            "",
            ""
        );
        rule.actions.add(action);
        
        return rule;
    }
    
    private void createExampleRules() {
        conditionalFilterManager.createExampleRules();
        loadRules();
        Toast.makeText(this, "Created example rules!", Toast.LENGTH_SHORT).show();
        
        InAppLogger.log("SmartSettings", "Created example conditional rules");
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
} 