package com.micoyc.speakthat.settings.sections;

import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.micoyc.speakthat.CustomAppNameAdapter;
import com.micoyc.speakthat.InAppLogger;
import com.micoyc.speakthat.LazyAppSearchAdapter;
import com.micoyc.speakthat.AppInfo;
import com.micoyc.speakthat.R;
import com.micoyc.speakthat.databinding.ActivityBehaviorSettingsBinding;
import com.micoyc.speakthat.settings.BehaviorSettingsSection;
import com.micoyc.speakthat.settings.BehaviorSettingsStore;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.text.Html;

public class CustomAppNamesSection implements BehaviorSettingsSection, CustomAppNameAdapter.OnCustomAppNameActionListener {
    private final AppCompatActivity activity;
    private final ActivityBehaviorSettingsBinding binding;
    private final BehaviorSettingsStore store;

    private final List<CustomAppNameAdapter.CustomAppNameEntry> customAppNamesList = new ArrayList<>();
    private CustomAppNameAdapter customAppNameAdapter;
    private LazyAppSearchAdapter customAppSelectorAdapter;

    public CustomAppNamesSection(
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
        setupCustomAppNamesRecycler();
        setupCustomAppSelector();
        binding.btnAddCustomAppName.setOnClickListener(v -> addCustomAppName());
        binding.btnAppNamesInfo.setOnClickListener(v -> showCustomAppNamesDialog());
    }

    @Override
    public void load() {
        loadCustomAppNames();
    }

    @Override
    public void release() {
        if (customAppSelectorAdapter != null) {
            customAppSelectorAdapter.shutdown();
        }
    }

    private void setupCustomAppNamesRecycler() {
        customAppNameAdapter = new CustomAppNameAdapter(this);
        binding.recyclerCustomAppNames.setLayoutManager(new LinearLayoutManager(activity));
        binding.recyclerCustomAppNames.setAdapter(customAppNameAdapter);
    }

    private void setupCustomAppSelector() {
        customAppSelectorAdapter = new LazyAppSearchAdapter(activity);
        binding.editAppPackage.setAdapter(customAppSelectorAdapter);
        binding.editAppPackage.setThreshold(1);

        binding.editAppPackage.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo selectedApp = customAppSelectorAdapter.getItem(position);
            if (selectedApp != null) {
                binding.editAppPackage.setText(selectedApp.packageName);
                binding.editAppPackage.setSelection(binding.editAppPackage.getText().length());
                InAppLogger.log(
                    "AppSelector",
                    "Custom name selector chose: " + selectedApp.appName + " (" + selectedApp.packageName + ")"
                );
            }
        });

        InAppLogger.log("AppSelector", "Lazy custom app selector initialized - apps will load on search");
    }

    private void addCustomAppName() {
        String packageName = binding.editAppPackage.getText().toString().trim();
        String customName = binding.editCustomAppName.getText().toString().trim();
        if (packageName.isEmpty() || customName.isEmpty()) {
            Toast.makeText(
                activity,
                "Please enter both package name and custom name",
                Toast.LENGTH_SHORT
            ).show();
            return;
        }

        for (CustomAppNameAdapter.CustomAppNameEntry entry : customAppNamesList) {
            if (entry.getPackageName().equals(packageName)) {
                Toast.makeText(
                    activity,
                    "Package name already has a custom name",
                    Toast.LENGTH_SHORT
                ).show();
                return;
            }
        }

        CustomAppNameAdapter.CustomAppNameEntry entry =
            new CustomAppNameAdapter.CustomAppNameEntry(packageName, customName);
        customAppNamesList.add(entry);
        customAppNameAdapter.addCustomAppName(entry);

        binding.editAppPackage.setText("");
        binding.editCustomAppName.setText("");

        saveCustomAppNames();
    }

    private void removeCustomAppName(int position) {
        customAppNamesList.remove(position);
        customAppNameAdapter.removeCustomAppName(position);
        saveCustomAppNames();
    }

    @Override
    public void onDelete(int position) {
        removeCustomAppName(position);
    }

    private void loadCustomAppNames() {
        String customAppNamesJson = store.prefs().getString(BehaviorSettingsStore.KEY_CUSTOM_APP_NAMES, "[]");
        customAppNamesList.clear();

        try {
            JSONArray jsonArray = new JSONArray(customAppNamesJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String packageName = jsonObject.getString("packageName");
                String customName = jsonObject.getString("customName");
                customAppNamesList.add(new CustomAppNameAdapter.CustomAppNameEntry(packageName, customName));
            }
        } catch (JSONException e) {
            android.util.Log.e("BehaviorSettings", "Error loading custom app names", e);
        }

        if (customAppNamesList.isEmpty()) {
            addDefaultCustomAppNames();
        }

        customAppNameAdapter.updateCustomAppNames(customAppNamesList);
    }

    private void addDefaultCustomAppNames() {
        CustomAppNameAdapter.CustomAppNameEntry[] defaultEntries = {
            new CustomAppNameAdapter.CustomAppNameEntry("com.twitter.android", "Twitter")
        };

        for (CustomAppNameAdapter.CustomAppNameEntry entry : defaultEntries) {
            customAppNamesList.add(entry);
        }

        saveCustomAppNames();
    }

    private void saveCustomAppNames() {
        // Skip saving during initialization to prevent activity recreation loop
        if (store.isInitializing()) {
            return;
        }
        try {
            JSONArray jsonArray = new JSONArray();
            for (CustomAppNameAdapter.CustomAppNameEntry entry : customAppNamesList) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("packageName", entry.getPackageName());
                jsonObject.put("customName", entry.getCustomName());
                jsonArray.put(jsonObject);
            }

            store.prefs().edit().putString(BehaviorSettingsStore.KEY_CUSTOM_APP_NAMES, jsonArray.toString()).apply();
            InAppLogger.log("BehaviorSettings", "Custom app names saved: " + customAppNamesList.size() + " entries");
        } catch (JSONException e) {
            android.util.Log.e("BehaviorSettings", "Error saving custom app names", e);
        }
    }

    private void showCustomAppNamesDialog() {
        store.trackDialogUsage("custom_app_names_info");
        String htmlText = "Custom App Names let you change how app names are spoken in notifications:<br><br>" +
            "<b>Why customize app names?</b><br>" +
            "Some apps have confusing or unclear names when spoken aloud. This feature lets you create custom names that are easier to understand:<br><br>" +
            "<b>Example:</b><br>" +
            "• <b>X</b> → <b>Twitter</b><br>" +
            "<b>How to use:</b><br>" +
            "1. Find the app's package name (e.g., com.twitter.android)<br>" +
            "2. Enter a custom name that's easier to say<br>" +
            "3. SpeakThat will use your custom name instead<br><br>" +
            "<b>Finding package names:</b><br>" +
            "• Check the app's Play Store URL<br>" +
            "• Use a package name finder app<br>" +
            "• Common format: com.company.appname<br><br>" +
            "<b>Note:</b> This only affects how the app name is spoken, not the actual app name on your device.";

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_title_custom_app_names)
            .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.button_got_it, null)
            .show();
    }
}
