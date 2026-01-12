package com.micoyc.speakthat.settings.sections;

import android.content.Intent;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.micoyc.speakthat.AppPickerActivity;
import com.micoyc.speakthat.PriorityAppAdapter;
import com.micoyc.speakthat.R;
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import com.micoyc.speakthat.settings.BehaviorSettingsActivity;
import com.micoyc.speakthat.settings.BehaviorSettingsSection;
import com.micoyc.speakthat.settings.BehaviorSettingsStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import android.text.Html;

public class NotificationBehaviorSection implements BehaviorSettingsSection {
    private final AppCompatActivity activity;
    private final ActivityBehaviorSettingsBinding binding;
    private final BehaviorSettingsStore store;

    private final List<String> priorityAppsList = new ArrayList<>();
    private PriorityAppAdapter priorityAppAdapter;
    private ActivityResultLauncher<Intent> priorityAppPickerLauncher;

    public NotificationBehaviorSection(
        AppCompatActivity activity,
        ActivityBehaviorSettingsBinding binding,
        BehaviorSettingsStore store
    ) {
        this.activity = activity;
        this.binding = binding;
        this.store = store;
    }

    @Override
    public void bind() {
        priorityAppPickerLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    ArrayList<String> selected = result.getData().getStringArrayListExtra(
                        AppPickerActivity.EXTRA_SELECTED_PACKAGES
                    );
                    if (selected != null) {
                        priorityAppsList.clear();
                        priorityAppsList.addAll(selected);
                        priorityAppAdapter.notifyDataSetChanged();
                        savePriorityApps();
                        updatePriorityAppsSummary();
                    }
                }
            }
        );

        binding.behaviorModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String mode = "interrupt";
            if (checkedId == R.id.radioQueue) {
                mode = "queue";
            } else if (checkedId == R.id.radioSkip) {
                mode = "skip";
            } else if (checkedId == R.id.radioSmart) {
                mode = "smart";
            }

            binding.priorityAppsSection.setVisibility(
                "smart".equals(mode) ? View.VISIBLE : View.GONE
            );

            saveBehaviorMode(mode);
        });

        setupPriorityAppsRecycler();
        binding.btnManagePriorityApps.setOnClickListener(v -> openPriorityAppPicker());
        binding.btnNotificationBehaviorInfo.setOnClickListener(v -> showNotificationBehaviorDialog());
    }

    @Override
    public void load() {
        String behaviorMode = store.prefs().getString(BehaviorSettingsStore.KEY_NOTIFICATION_BEHAVIOR, "interrupt");
        switch (behaviorMode) {
            case "queue":
                binding.radioQueue.setChecked(true);
                binding.priorityAppsSection.setVisibility(View.GONE);
                break;
            case "skip":
                binding.radioSkip.setChecked(true);
                binding.priorityAppsSection.setVisibility(View.GONE);
                break;
            case "smart":
                binding.radioSmart.setChecked(true);
                binding.priorityAppsSection.setVisibility(View.VISIBLE);
                break;
            default:
                binding.radioInterrupt.setChecked(true);
                binding.priorityAppsSection.setVisibility(View.GONE);
                break;
        }

        Set<String> priorityApps = store.prefs().getStringSet(
            BehaviorSettingsStore.KEY_PRIORITY_APPS,
            new HashSet<>()
        );
        priorityAppsList.clear();
        priorityAppsList.addAll(priorityApps);
        priorityAppAdapter.notifyDataSetChanged();
        updatePriorityAppsSummary();
    }

    @Override
    public void release() {
    }

    private void setupPriorityAppsRecycler() {
        priorityAppAdapter = new PriorityAppAdapter(
            priorityAppsList,
                this::removePriorityApp
        );
        binding.recyclerPriorityApps.setLayoutManager(new LinearLayoutManager(activity));
        binding.recyclerPriorityApps.setAdapter(priorityAppAdapter);
    }

    private void openPriorityAppPicker() {
        ArrayList<String> selectedPackages = new ArrayList<>(priorityAppsList);
        Intent intent = AppPickerActivity.createIntent(
            activity,
            activity.getString(R.string.behavior_priority_apps),
            selectedPackages,
            new ArrayList<>(),
            false
        );
        priorityAppPickerLauncher.launch(intent);
    }

    private void updatePriorityAppsSummary() {
        binding.txtPriorityAppsCount.setText("(" + priorityAppsList.size() + " apps)");
    }

    private void removePriorityApp(int position) {
        priorityAppsList.remove(position);
        priorityAppAdapter.notifyDataSetChanged();
        savePriorityApps();
        updatePriorityAppsSummary();
    }

    private void saveBehaviorMode(String mode) {
        store.prefs().edit().putString(BehaviorSettingsStore.KEY_NOTIFICATION_BEHAVIOR, mode).apply();
    }

    private void savePriorityApps() {
        Set<String> appsSet = new HashSet<>(priorityAppsList);
        store.prefs().edit().putStringSet(BehaviorSettingsStore.KEY_PRIORITY_APPS, appsSet).apply();
    }

    private void showNotificationBehaviorDialog() {
        store.trackDialogUsage("notification_behavior_info");
        String htmlText = "Choose how SpeakThat handles multiple notifications:<br><br>" +
            "<b>Interrupt</b> - Stops current notification and reads new one immediately. Best for urgent notifications.<br><br>" +
            "<b>Queue</b> - Finishes current notification, then reads new ones in order. Nothing gets missed.<br><br>" +
            "<b>Skip</b> - Ignores new notifications while reading. Simple but you might miss important ones.<br><br>" +
            "<b>Smart (Recommended)</b> - Priority apps interrupt, others queue. Perfect balance of urgency and completeness.<br><br>" +
            "Smart mode lets you choose which apps are urgent enough to interrupt (like calls, messages) while other apps wait their turn.";

        new MaterialAlertDialogBuilder(activity)
            .setTitle("Notification Behavior Options")
            .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.use_recommended, (dialog, which) -> {
                store.trackDialogUsage("notification_behavior_recommended");
                binding.radioSmart.setChecked(true);
                binding.priorityAppsSection.setVisibility(View.VISIBLE);
                saveBehaviorMode("smart");

                if (priorityAppsList.isEmpty()) {
                    addDefaultPriorityApps();
                }
            })
            .setNegativeButton(R.string.got_it, null)
            .show();
    }

    private void addDefaultPriorityApps() {
        String[] defaultPriorityApps = {
            "Phone", "Messages", "WhatsApp", "Telegram", "Signal"
        };

        for (String appName : defaultPriorityApps) {
            if (!priorityAppsList.contains(appName)) {
                priorityAppsList.add(appName);
            }
        }

        priorityAppAdapter.notifyDataSetChanged();
        savePriorityApps();
        Toast.makeText(
            activity,
            "Added common priority apps. You can remove or add more as needed.",
            Toast.LENGTH_LONG
        ).show();
    }
}
