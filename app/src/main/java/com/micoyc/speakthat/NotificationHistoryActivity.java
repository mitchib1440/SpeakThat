package com.micoyc.speakthat;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.micoyc.speakthat.databinding.ActivityNotificationHistoryBinding;

public class NotificationHistoryActivity extends AppCompatActivity {
    private ActivityNotificationHistoryBinding binding;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "SpeakThatPrefs";
    private static final String KEY_DARK_MODE = "dark_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme FIRST before anything else
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applySavedTheme();
        
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_notification_history);
        }

        // Load notification history
        loadNotificationHistory();
    }

    private void applySavedTheme() {
        boolean isDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, true); // Default to dark mode
        int desiredMode = isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        int currentMode = AppCompatDelegate.getDefaultNightMode();
        
        // Only set the night mode if it's different from the current mode
        // This prevents unnecessary configuration changes that cause activity recreation loops
        if (currentMode != desiredMode) {
            AppCompatDelegate.setDefaultNightMode(desiredMode);
        }
    }

    private void loadNotificationHistory() {
        // Get stored notification history from SharedPreferences
        String history = sharedPreferences.getString("notification_history", "No notifications received yet.");
        
        if (history.equals("No notifications received yet.")) {
            binding.textHistory.setText("📱 No notifications have been captured yet.\n\n" +
                    "Once you enable the notification access permission and start receiving notifications, " +
                    "they will appear here for debugging purposes.\n\n" +
                    "This helps you see exactly what notifications SpeakThat! is reading aloud.");
        } else {
            binding.textHistory.setText(history);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
} 