package com.micoyc.speakthat.settings.sections;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.micoyc.speakthat.AppInfo;
import com.micoyc.speakthat.AppListData;
import com.micoyc.speakthat.AppListManager;
import com.micoyc.speakthat.CooldownAppAdapter;
import com.micoyc.speakthat.InAppLogger;
import com.micoyc.speakthat.LazyAppSearchAdapter;
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

public class CooldownSection implements BehaviorSettingsSection {
    private final AppCompatActivity activity;
    private final ActivityBehaviorSettingsBinding binding;
    private final BehaviorSettingsStore store;

    private final List<CooldownAppAdapter.CooldownAppItem> cooldownAppsList = new ArrayList<>();
    private CooldownAppAdapter cooldownAppAdapter;
    private LazyAppSearchAdapter cooldownAppSelectorAdapter;

    public CooldownSection(
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
        setupCooldownAppsRecycler();
        setupCooldownAppSelector();
        binding.btnAddCooldownApp.setOnClickListener(v -> addCooldownApp());
        binding.btnCooldownInfo.setOnClickListener(v -> showCooldownDialog());
    }

    @Override
    public void load() {
        loadCooldownApps();
    }

    @Override
    public void release() {
        if (cooldownAppSelectorAdapter != null) {
            cooldownAppSelectorAdapter.shutdown();
        }
    }

    private void setupCooldownAppsRecycler() {
        cooldownAppAdapter = new CooldownAppAdapter(
            activity,
            cooldownAppsList,
            new CooldownAppAdapter.OnCooldownAppActionListener() {
                @Override
                public void onCooldownTimeChanged(int position, int cooldownSeconds) {
                    if (position >= 0 && position < cooldownAppsList.size()) {
                        cooldownAppsList.get(position).cooldownSeconds = cooldownSeconds;
                        saveCooldownApps();
                    }
                }

                @Override
                public void onDeleteCooldownApp(int position) {
                    removeCooldownApp(position);
                }
            }
        );
        binding.recyclerCooldownApps.setLayoutManager(new LinearLayoutManager(activity));
        binding.recyclerCooldownApps.setAdapter(cooldownAppAdapter);
    }

    private void setupCooldownAppSelector() {
        cooldownAppSelectorAdapter = new LazyAppSearchAdapter(activity);
        binding.editCooldownApp.setAdapter(cooldownAppSelectorAdapter);
        binding.editCooldownApp.setThreshold(1);

        binding.editCooldownApp.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo selectedApp = cooldownAppSelectorAdapter.getItem(position);
            if (selectedApp != null) {
                addCooldownAppFromSelection(selectedApp);
                binding.editCooldownApp.setText("");
            }
        });

        InAppLogger.log("AppSelector", "Lazy cooldown app selector initialized - apps will load on search");
    }

    private void addCooldownApp() {
        String appName = binding.editCooldownApp.getText().toString().trim();
        Log.d("BehaviorSettings", "addCooldownApp called with: '" + appName + "'");

        if (appName.isEmpty()) {
            Log.d("BehaviorSettings", "App name is empty");
            Toast.makeText(activity, "Please enter an app name", Toast.LENGTH_SHORT).show();
            return;
        }

        PackageManager pm = activity.getPackageManager();
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(appName, 0);
            String displayName = pm.getApplicationLabel(appInfo).toString();

            AppInfo selectedApp = new AppInfo(displayName, appName, pm.getApplicationIcon(appInfo));
            addCooldownAppFromSelection(selectedApp);
            return;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d("BehaviorSettings", "App not found in device, trying JSON app list");
        }

        AppListData appData = findAppByNameOrPackage(appName);
        if (appData == null) {
            Log.d("BehaviorSettings", "App not found for: '" + appName + "'");
            Toast.makeText(
                activity,
                "App not found. Please check the app name or package.",
                Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Log.d("BehaviorSettings", "Found app: " + appData.displayName + " (" + appData.packageName + ")");

        for (CooldownAppAdapter.CooldownAppItem item : cooldownAppsList) {
            if (item.packageName.equals(appData.packageName)) {
                Log.d("BehaviorSettings", "App already in cooldown list: " + appData.packageName);
                Toast.makeText(activity, "App already in cooldown list", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        CooldownAppAdapter.CooldownAppItem item = new CooldownAppAdapter.CooldownAppItem(
            appData.packageName,
            appData.displayName,
            appData.iconSlug,
            1
        );
        cooldownAppsList.add(item);
        cooldownAppAdapter.notifyDataSetChanged();
        binding.editCooldownApp.setText("");

        Log.d("BehaviorSettings", "Added app to cooldown list: " + appData.displayName);
        saveCooldownApps();
    }

    private void removeCooldownApp(int position) {
        if (position >= 0 && position < cooldownAppsList.size()) {
            cooldownAppsList.remove(position);
            cooldownAppAdapter.notifyDataSetChanged();
            saveCooldownApps();
        }
    }

    private void loadCooldownApps() {
        String cooldownAppsJson = store.prefs().getString(BehaviorSettingsStore.KEY_COOLDOWN_APPS, "[]");
        cooldownAppsList.clear();

        try {
            JSONArray jsonArray = new JSONArray(cooldownAppsJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String packageName = jsonObject.getString("packageName");
                String displayName = jsonObject.getString("displayName");
                String iconSlug = jsonObject.optString("iconSlug", "");
                boolean hasDeviceIcon = jsonObject.optBoolean("hasDeviceIcon", false);
                int cooldownSeconds = jsonObject.optInt("cooldownSeconds", 1);
                cooldownAppsList.add(new CooldownAppAdapter.CooldownAppItem(
                    packageName,
                    displayName,
                    iconSlug,
                    cooldownSeconds,
                    hasDeviceIcon
                ));
            }
        } catch (JSONException e) {
            Log.e("BehaviorSettings", "Error loading cooldown apps", e);
        }
    }

    private void saveCooldownApps() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (CooldownAppAdapter.CooldownAppItem item : cooldownAppsList) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("packageName", item.packageName);
                jsonObject.put("displayName", item.displayName);
                jsonObject.put("iconSlug", item.iconSlug != null ? item.iconSlug : "");
                jsonObject.put("hasDeviceIcon", item.icon != null);
                jsonObject.put("cooldownSeconds", item.cooldownSeconds);
                jsonArray.put(jsonObject);
            }

            store.prefs().edit().putString(BehaviorSettingsStore.KEY_COOLDOWN_APPS, jsonArray.toString()).apply();
            InAppLogger.log("BehaviorSettings", "Cooldown apps saved: " + cooldownAppsList.size() + " entries");
        } catch (JSONException e) {
            Log.e("BehaviorSettings", "Error saving cooldown apps", e);
        }
    }

    private AppListData findAppByNameOrPackage(String query) {
        List<AppListData> appList = AppListManager.INSTANCE.loadAppList(activity);
        if (appList == null) {
            Log.e("BehaviorSettings", "App list is null");
            return null;
        }

        String lowerQuery = query.toLowerCase().trim();
        Log.d("BehaviorSettings", "Searching for app: '" + query + "' (lowercase: '" + lowerQuery + "')");
        Log.d("BehaviorSettings", "App list size: " + appList.size());

        for (AppListData app : appList) {
            if (app.displayName.toLowerCase().equals(lowerQuery) ||
                app.packageName.toLowerCase().equals(lowerQuery)) {
                Log.d("BehaviorSettings", "Found exact match: " + app.displayName + " (" + app.packageName + ")");
                return app;
            }
        }

        for (AppListData app : appList) {
            if (app.displayName.toLowerCase().contains(lowerQuery) ||
                app.packageName.toLowerCase().contains(lowerQuery) ||
                (app.aliases != null && app.aliases.stream().anyMatch(alias -> alias.toLowerCase().contains(lowerQuery)))) {
                Log.d("BehaviorSettings", "Found partial match: " + app.displayName + " (" + app.packageName + ")");
                return app;
            }
        }

        String[] queryWords = lowerQuery.split("\\s+");
        for (AppListData app : appList) {
            String displayNameLower = app.displayName.toLowerCase();
            String packageNameLower = app.packageName.toLowerCase();

            boolean matches = false;
            for (String word : queryWords) {
                if (displayNameLower.contains(word) || packageNameLower.contains(word) ||
                    (app.aliases != null && app.aliases.stream().anyMatch(alias -> alias.toLowerCase().contains(word)))) {
                    matches = true;
                    break;
                }
            }

            if (matches) {
                Log.d("BehaviorSettings", "Found word match: " + app.displayName + " (" + app.packageName + ")");
                return app;
            }
        }

        Log.d("BehaviorSettings", "No app found for query: '" + query + "'");
        return null;
    }

    private void addCooldownAppFromSelection(AppInfo selectedApp) {
        Log.d("BehaviorSettings", "Adding cooldown app from selection: " + selectedApp.appName + " (" + selectedApp.packageName + ")");

        for (CooldownAppAdapter.CooldownAppItem item : cooldownAppsList) {
            if (item.packageName.equals(selectedApp.packageName)) {
                Toast.makeText(activity, "App already in cooldown list", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        CooldownAppAdapter.CooldownAppItem item = new CooldownAppAdapter.CooldownAppItem(
            selectedApp.packageName,
            selectedApp.appName,
            selectedApp.icon,
            1
        );
        cooldownAppsList.add(item);
        cooldownAppAdapter.notifyDataSetChanged();

        Log.d("BehaviorSettings", "Added app to cooldown list: " + selectedApp.appName);
        saveCooldownApps();

        Toast.makeText(
            activity,
            "Added " + selectedApp.appName + " to cooldown list",
            Toast.LENGTH_SHORT
        ).show();
    }

    public void testAppListFunctionality() {
        Log.d("BehaviorSettings", "=== Testing App List Functionality ===");

        List<AppListData> appList = AppListManager.INSTANCE.loadAppList(activity);
        if (appList == null) {
            Log.e("BehaviorSettings", "TEST FAILED: App list is null");
            return;
        }

        Log.d("BehaviorSettings", "TEST PASSED: App list loaded with " + appList.size() + " apps");

        String[] testQueries = {"whatsapp", "facebook", "youtube", "gmail", "chrome"};

        for (String query : testQueries) {
            AppListData found = findAppByNameOrPackage(query);
            if (found != null) {
                Log.d("BehaviorSettings", "TEST PASSED: Found '" + query + "' -> " + found.displayName);
            } else {
                Log.e("BehaviorSettings", "TEST FAILED: Could not find '" + query + "'");
            }
        }

        Log.d("BehaviorSettings", "First 5 apps in list:");
        for (int i = 0; i < Math.min(5, appList.size()); i++) {
            AppListData app = appList.get(i);
            Log.d("BehaviorSettings", "  " + (i + 1) + ". " + app.displayName + " (" + app.packageName + ")");
        }

        Log.d("BehaviorSettings", "=== App List Test Complete ===");
    }

    private void showCooldownDialog() {
        store.trackDialogUsage("cooldown_info");
        String htmlText = "Notification Cooldown prevents apps from having multiple notifications read within a specified time period:<br><br>" +
            "<b>Why use cooldown?</b><br>" +
            "Some apps send rapid-fire notifications that can be overwhelming. This feature helps manage notification spam by enforcing a \"quiet period\" between notifications from the same app:<br><br>" +
            "<b>Perfect for:</b><br>" +
            "• <b>Chat apps</b> - WhatsApp, Telegram, Discord<br>" +
            "• <b>Social media</b> - Twitter, Instagram, Facebook<br>" +
            "• <b>Games</b> - Apps with frequent updates<br>" +
            "• <b>Any app</b> that sends rapid notifications<br><br>" +
            "<b>How it works:</b><br>" +
            "1. Add an app to the cooldown list<br>" +
            "2. Set a cooldown time (e.g., 5 seconds)<br>" +
            "3. If the same app sends another notification within that time, it gets skipped<br>" +
            "4. After the cooldown period, new notifications are read normally<br><br>" +
            "<b>Recommended settings:</b><br>" +
            "• <b>1-3 seconds</b> - For apps that send 2-3 notifications quickly<br>" +
            "• <b>5-10 seconds</b> - For chat apps with message bursts<br>" +
            "• <b>15-30 seconds</b> - For very spammy apps<br>" +
            "• <b>1-5 minutes</b> - For apps that send many notifications over time<br><br>" +
            "<b>Note:</b> Only notifications from the same app are affected. Different apps can still send notifications normally.";

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_title_notification_cooldown)
            .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.button_got_it, null)
            .show();
    }
}
