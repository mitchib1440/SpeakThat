package com.micoyc.speakthat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SupportActivity extends AppCompatActivity {

    // Support type constants
    private static final int TYPE_FEATURE = 0;
    private static final int TYPE_BUG = 1;
    private static final int TYPE_QUESTION = 2;
    
    // UI Components
    private Spinner spinnerSupportType;
    private LinearLayout layoutQuestions;
    private LinearLayout layoutIncludeLogs;
    private MaterialSwitch switchIncludeLogs;
    private TextView textLogInfo;
    private TextView textLogMessage;
    private TextView textValidationWarning;
    private MaterialButton btnSubmit;
    
    // Current state
    private int currentType = TYPE_FEATURE;
    private List<QuestionView> questionViews = new ArrayList<>();
    
    // Frozen logs captured when the screen opens
    private String frozenLogs = "";
    private String frozenSystemInfo = "";
    private int frozenLogCount = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply saved theme FIRST before anything else
        SharedPreferences mainPrefs = getSharedPreferences("SpeakThatPrefs", MODE_PRIVATE);
        applySavedTheme(mainPrefs);
        
        setContentView(R.layout.activity_support);
        
        // Setup toolbar
        setupToolbar();
        
        // Initialize views
        initializeViews();
        
        // Setup spinner
        setupSupportTypeSpinner();
        
        // Capture and freeze logs immediately before any other logging occurs
        // This ensures we preserve the state of logs before the user opened this screen
        freezeLogs();
        
        // Setup submit button
        setupSubmitButton();
        
        // Load initial questions (default to feature request)
        loadQuestionsForType(TYPE_FEATURE);
        
        // Update log info (using frozen count)
        updateLogInfo();
        
        InAppLogger.logUserAction("Support screen opened", "");
    }
    
    private void applySavedTheme(SharedPreferences prefs) {
        boolean isDarkMode = prefs.getBoolean("dark_mode", true); // Default to dark mode
        int desiredMode = isDarkMode ? 
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : 
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
        int currentMode = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode();
        
        // Only set the night mode if it's different from the current mode
        // This prevents unnecessary configuration changes that cause activity recreation loops
        if (currentMode != desiredMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(desiredMode);
        }
    }
    
    private void setupToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(R.string.settings_support_title);
        }
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    
    private void initializeViews() {
        spinnerSupportType = findViewById(R.id.spinnerSupportType);
        layoutQuestions = findViewById(R.id.layoutQuestions);
        layoutIncludeLogs = findViewById(R.id.layoutIncludeLogs);
        switchIncludeLogs = findViewById(R.id.switchIncludeLogs);
        textLogInfo = findViewById(R.id.textLogInfo);
        textLogMessage = findViewById(R.id.textLogMessage);
        textValidationWarning = findViewById(R.id.textValidationWarning);
        btnSubmit = findViewById(R.id.btnSubmit);
    }
    
    private void setupSupportTypeSpinner() {
        // Create adapter with support types
        List<String> supportTypes = new ArrayList<>();
        supportTypes.add(getString(R.string.support_type_feature));
        supportTypes.add(getString(R.string.support_type_bug));
        supportTypes.add(getString(R.string.support_type_question));
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            supportTypes
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSupportType.setAdapter(adapter);
        
        // Handle selection changes
        spinnerSupportType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentType = position;
                loadQuestionsForType(currentType);
                updateLogVisibility();
                validateForm();
                
                InAppLogger.logUserAction("Support type selected", 
                    "Type: " + supportTypes.get(position));
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }
    
    private void setupSubmitButton() {
        btnSubmit.setOnClickListener(v -> {
            if (validateForm()) {
                sendSupportEmail();
            }
        });
    }
    
    private void loadQuestionsForType(int type) {
        // Clear existing questions
        layoutQuestions.removeAllViews();
        questionViews.clear();
        
        switch (type) {
            case TYPE_FEATURE:
                loadFeatureQuestions();
                break;
            case TYPE_BUG:
                loadBugQuestions();
                break;
            case TYPE_QUESTION:
                loadQuestionQuestions();
                break;
        }
        
        // Add text change listeners to all questions for validation
        for (QuestionView qv : questionViews) {
            qv.editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                
                @Override
                public void afterTextChanged(Editable s) {
                    validateForm();
                }
            });
        }
    }
    
    private void loadFeatureQuestions() {
        // Question 1 (required)
        addQuestion(
            getString(R.string.support_feature_q1),
            getString(R.string.support_feature_q1_desc),
            getString(R.string.support_feature_q1_hint),
            true
        );
        
        // Question 2 (optional)
        addQuestion(
            getString(R.string.support_feature_q2),
            getString(R.string.support_feature_q2_desc),
            getString(R.string.support_feature_q2_hint),
            false
        );
        
        // Question 3 (optional)
        addQuestion(
            getString(R.string.support_feature_q3),
            getString(R.string.support_feature_q3_desc),
            getString(R.string.support_feature_q3_hint),
            false
        );
    }
    
    private void loadBugQuestions() {
        // All questions are required for bug reports
        
        // Question 1
        addQuestion(
            getString(R.string.support_bug_q1),
            getString(R.string.support_bug_q1_desc),
            getString(R.string.support_bug_q1_hint),
            true
        );
        
        // Question 2
        addQuestion(
            getString(R.string.support_bug_q2),
            getString(R.string.support_bug_q2_desc),
            getString(R.string.support_bug_q2_hint),
            true
        );
        
        // Question 3
        addQuestion(
            getString(R.string.support_bug_q3),
            getString(R.string.support_bug_q3_desc),
            getString(R.string.support_bug_q3_hint),
            true
        );
    }
    
    private void loadQuestionQuestions() {
        // Question 1 (required)
        addQuestion(
            getString(R.string.support_question_q1),
            getString(R.string.support_question_q1_desc),
            getString(R.string.support_question_q1_hint),
            true
        );
        
        // Question 2 (optional)
        addQuestion(
            getString(R.string.support_question_q2),
            getString(R.string.support_question_q2_desc),
            getString(R.string.support_question_q2_hint),
            false
        );
    }
    
    private void addQuestion(String question, String description, String hint, boolean required) {
        View questionView = LayoutInflater.from(this).inflate(
            R.layout.support_question_item, 
            layoutQuestions, 
            false
        );
        
        TextView textQuestion = questionView.findViewById(R.id.textQuestion);
        TextView textDescription = questionView.findViewById(R.id.textDescription);
        EditText editAnswer = questionView.findViewById(R.id.editAnswer);
        
        textQuestion.setText(question);
        textDescription.setText(description);
        editAnswer.setHint(hint);
        
        layoutQuestions.addView(questionView);
        
        // Store reference
        questionViews.add(new QuestionView(question, editAnswer, required));
    }
    
    private void updateLogVisibility() {
        switch (currentType) {
            case TYPE_FEATURE:
                // Feature requests: no logs, show message
                layoutIncludeLogs.setVisibility(View.GONE);
                textLogMessage.setVisibility(View.VISIBLE);
                textLogMessage.setText(R.string.support_log_message_feature);
                break;
                
            case TYPE_BUG:
                // Bug reports: logs required, show message
                layoutIncludeLogs.setVisibility(View.GONE);
                textLogMessage.setVisibility(View.VISIBLE);
                // Use frozen log count captured when screen opened
                String logMessage = getString(R.string.support_log_message_bug)
                    .replace("[number]", String.valueOf(frozenLogCount));
                textLogMessage.setText(logMessage);
                break;
                
            case TYPE_QUESTION:
                // General support: show toggle, hide message
                layoutIncludeLogs.setVisibility(View.VISIBLE);
                textLogMessage.setVisibility(View.GONE);
                break;
        }
    }
    
    private void freezeLogs() {
        // Capture logs and system info at the moment the screen opens
        // This preserves the debugging state before the user starts filling out the form
        frozenLogCount = InAppLogger.getLogCount();
        frozenSystemInfo = InAppLogger.getSystemInfo(this);
        frozenLogs = InAppLogger.getLogsForSupport();
    }
    
    private void updateLogInfo() {
        // Use the frozen log count captured when the screen opened
        String logInfo = getString(R.string.support_log_info)
            .replace("[number]", String.valueOf(frozenLogCount));
        textLogInfo.setText(logInfo);
    }
    
    private boolean validateForm() {
        boolean allValid = true;
        
        // Check all required questions
        for (QuestionView qv : questionViews) {
            if (qv.required) {
                String answer = qv.editText.getText().toString().trim();
                if (answer.isEmpty()) {
                    allValid = false;
                    break;
                }
            }
        }
        
        // Update UI
        btnSubmit.setEnabled(allValid);
        textValidationWarning.setVisibility(allValid ? View.GONE : View.VISIBLE);
        
        // Grey out button if disabled
        if (allValid) {
            btnSubmit.setAlpha(1.0f);
        } else {
            btnSubmit.setAlpha(0.5f);
        }
        
        return allValid;
    }
    
    private void sendSupportEmail() {
        try {
            // Determine email subject
            String subject;
            switch (currentType) {
                case TYPE_FEATURE:
                    subject = getString(R.string.support_email_subject_feature);
                    break;
                case TYPE_BUG:
                    subject = getString(R.string.support_email_subject_bug);
                    break;
                case TYPE_QUESTION:
                    subject = getString(R.string.support_email_subject_question);
                    break;
                default:
                    subject = "SpeakThat! Support";
                    break;
            }
            
            // Build email body
            StringBuilder bodyBuilder = new StringBuilder();
            
            // Add questions and answers
            for (QuestionView qv : questionViews) {
                String answer = qv.editText.getText().toString().trim();
                if (!answer.isEmpty() || qv.required) {
                    bodyBuilder.append(qv.question).append("\n");
                    bodyBuilder.append(answer.isEmpty() ? "[No answer provided]" : answer);
                    bodyBuilder.append("\n\n");
                }
            }
            
            // Add reference number
            String referenceNumber = generateReferenceNumber();
            bodyBuilder.append("Ref: ").append(referenceNumber).append("\n\n");
            
            // Determine if we should include logs
            boolean includeLogs = false;
            switch (currentType) {
                case TYPE_BUG:
                    // Always include for bug reports
                    includeLogs = true;
                    break;
                case TYPE_QUESTION:
                    // Include if switch is on
                    includeLogs = switchIncludeLogs.isChecked();
                    break;
                case TYPE_FEATURE:
                    // Never include for feature requests
                    includeLogs = false;
                    break;
            }
            
            // Add debug logs if needed (use frozen logs captured when screen opened)
            if (includeLogs) {
                bodyBuilder.append("=== DEBUG INFORMATION ===\n");
                bodyBuilder.append(frozenSystemInfo);
                bodyBuilder.append("\n\n=== DEBUG LOGS ===\n");
                bodyBuilder.append(frozenLogs);
                bodyBuilder.append("\n=== END DEBUG INFO ===\n\n");
            }
            
            // Create email intent
            String recipient = "micoycbusiness@gmail.com";
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:"));
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{recipient});
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            emailIntent.putExtra(Intent.EXTRA_TEXT, bodyBuilder.toString());
            
            try {
                startActivity(emailIntent);
                InAppLogger.logUserAction("Support email opened", 
                    "Type: " + currentType + ", Logs: " + includeLogs + ", Ref: " + referenceNumber);
                finish(); // Close the activity after opening email
            } catch (Exception e) {
                // Fallback: try with chooser
                try {
                    startActivity(Intent.createChooser(emailIntent, "Send email"));
                    InAppLogger.logUserAction("Support email opened via chooser", 
                        "Type: " + currentType + ", Logs: " + includeLogs);
                    finish();
                } catch (Exception e2) {
                    Toast.makeText(this, 
                        "No email app found. Please install an email app to send support requests.", 
                        Toast.LENGTH_LONG).show();
                    InAppLogger.logError("Support", "No email app available: " + e2.getMessage());
                }
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "Error opening email: " + e.getMessage(), Toast.LENGTH_LONG).show();
            InAppLogger.logCrash(e, "Support email");
        }
    }
    
    private String generateReferenceNumber() {
        // Format: ST + DDMMYYmmhh (e.g., ST0602261242)
        SimpleDateFormat sdf = new SimpleDateFormat("ddMMyyHHmm", Locale.US);
        String timestamp = sdf.format(new Date());
        return "ST" + timestamp;
    }
    
    // Helper class to track question views
    private static class QuestionView {
        String question;
        EditText editText;
        boolean required;
        
        QuestionView(String question, EditText editText, boolean required) {
            this.question = question;
            this.editText = editText;
            this.required = required;
        }
    }
}
