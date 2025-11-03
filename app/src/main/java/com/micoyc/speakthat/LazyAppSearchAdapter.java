package com.micoyc.speakthat;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LazyAppSearchAdapter extends ArrayAdapter<AppInfo> {
    
    private Context context;
    private LayoutInflater inflater;
    private List<AppInfo> currentResults = new ArrayList<>();
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final int MAX_RESULTS = 50; // Hard cap on results
    
    public LazyAppSearchAdapter(Context context) {
        super(context, R.layout.item_app_selector);
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }
    
    @Override
    public int getCount() {
        return currentResults.size();
    }
    
    @Override
    public AppInfo getItem(int position) {
        return currentResults.get(position);
    }
    
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_app_selector, parent, false);
            holder = new ViewHolder();
            holder.iconImageView = convertView.findViewById(R.id.appIcon);
            holder.nameTextView = convertView.findViewById(R.id.appName);
            holder.packageTextView = convertView.findViewById(R.id.packageName);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        AppInfo app = getItem(position);
        if (app != null) {
            holder.iconImageView.setImageDrawable(app.icon);
            holder.nameTextView.setText(app.appName);
            holder.packageTextView.setText(app.packageName);
        }
        
        return convertView;
    }
    
    @NonNull
    @Override
    public Filter getFilter() {
        return new LazyAppFilter();
    }
    
    private static class ViewHolder {
        ImageView iconImageView;
        TextView nameTextView;
        TextView packageTextView;
    }
    
    private class LazyAppFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            
            if (constraint == null || constraint.length() == 0) {
                // No search term - return empty results
                results.values = new ArrayList<AppInfo>();
                results.count = 0;
                return results;
            }
            
            String filterText = constraint.toString().toLowerCase().trim();
            
            // Start background search
            executor.execute(() -> {
                List<AppInfo> searchResults = searchAppsInBackground(filterText);
                
                // Update UI on main thread
                ((android.app.Activity) context).runOnUiThread(() -> {
                    currentResults.clear();
                    currentResults.addAll(searchResults);
                    clear();
                    addAll(currentResults);
                    notifyDataSetChanged();
                });
            });
            
            // Return empty results immediately - they'll be populated asynchronously
            results.values = new ArrayList<AppInfo>();
            results.count = 0;
            return results;
        }
        
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            // Results are published asynchronously in performFiltering
        }
        
        private List<AppInfo> searchAppsInBackground(String filterText) {
            List<AppInfo> results = new ArrayList<>();
            PackageManager pm = context.getPackageManager();
            
            try {
                // Query for all apps that can be launched
                android.content.Intent mainIntent = new android.content.Intent(android.content.Intent.ACTION_MAIN, null);
                mainIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
                
                List<ResolveInfo> resolveInfoList = pm.queryIntentActivities(mainIntent, 0);
                
                InAppLogger.log("LazyAppSearch", "Searching " + resolveInfoList.size() + " apps for: '" + filterText + "'");
                
                for (ResolveInfo resolveInfo : resolveInfoList) {
                    // Check if we've reached the result limit
                    if (results.size() >= MAX_RESULTS) {
                        break;
                    }
                    
                    try {
                        String packageName = resolveInfo.activityInfo.packageName;
                        String appName = resolveInfo.loadLabel(pm).toString();
                        
                        // Skip our own app
                        if (packageName.equals(context.getPackageName())) {
                            continue;
                        }
                        
                        // Check if this is actually a system app using ApplicationInfo flags
                        try {
                            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                            // Skip actual system apps (but allow user-installed apps even if they have system-like package names)
                            if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && 
                                (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
                                // This is a true system app (not a user-installed update)
                                continue;
                            }
                        } catch (Exception e) {
                            // If we can't get app info, skip it
                            continue;
                        }
                        
                        // Still filter out known Google system services
                        if (isGoogleSystemService(packageName)) {
                            continue;
                        }
                        
                        // Check if app matches search term
                        boolean nameMatch = appName.toLowerCase().contains(filterText);
                        boolean packageMatch = packageName.toLowerCase().contains(filterText);
                        
                        if (nameMatch || packageMatch) {
                            // Load icon (this is the expensive operation)
                            Drawable icon = resolveInfo.loadIcon(pm);
                            results.add(new AppInfo(appName, packageName, icon));
                        }
                        
                    } catch (Exception e) {
                        // Skip apps that can't be loaded
                        InAppLogger.logError("LazyAppSearch", "Error loading app: " + e.getMessage());
                    }
                }
                
                // Sort results alphabetically
                results.sort((a, b) -> a.appName.compareToIgnoreCase(b.appName));
                
                InAppLogger.log("LazyAppSearch", "Found " + results.size() + " matches for '" + filterText + "'");
                
            } catch (Exception e) {
                InAppLogger.logError("LazyAppSearch", "Error searching apps: " + e.getMessage());
            }
            
            return results;
        }
    }
    
    public void shutdown() {
        executor.shutdown();
    }
    
    /**
     * Checks if a package is a Google system service that users never interact with directly.
     * These services don't send meaningful notifications that users would want to filter.
     */
    private boolean isGoogleSystemService(String packageName) {
        return GOOGLE_SYSTEM_SERVICES.contains(packageName);
    }
    
    /**
     * Set of Google system services that users never interact with directly.
     * These are background services, system components, and setup utilities.
     * User-facing apps like Calendar, Gmail, YouTube, Maps, etc. are NOT included here.
     */
    private static final Set<String> GOOGLE_SYSTEM_SERVICES = Set.of(
        // Core Google Services
        "com.google.android.gms",                    // Google Play Services (background service)
        "com.google.android.gsf",                    // Google Services Framework
        "com.google.android.gsf.login",              // Google Services Login
        // Setup & Configuration
        "com.google.android.setupwizard",            // Setup wizard
        "com.google.android.apps.work.oobconfig",    // Work setup
        "com.google.android.apps.restore",           // Restore service
        // Package Management
        "com.google.android.packageinstaller",       // Package installer
        "com.google.android.packageinstaller.permission", // Package installer permissions
        // System Services
        "com.google.android.apps.work.clouddpc",     // Work device policy
        "com.google.android.apps.work.dpc",          // Work device policy controller
        // Background Services
        "com.google.android.apps.work.profile",      // Work profile
        "com.google.android.apps.work.managedprovisioning", // Work provisioning
        "com.google.android.apps.work.managedprovisioning.permission", // Work provisioning permissions
        // Google Play Services Components
        "com.google.android.gms.auth",               // Google Auth
        "com.google.android.gms.auth.api",           // Google Auth API
        "com.google.android.gms.auth.api.phone",     // Google Auth Phone
        "com.google.android.gms.auth.api.signin",    // Google Auth Sign-in
        "com.google.android.gms.backup",             // Google Backup
        "com.google.android.gms.cast",               // Google Cast
        "com.google.android.gms.common",             // Google Common
        "com.google.android.gms.nearby",             // Google Nearby
        "com.google.android.gms.pay",                // Google Pay API
        "com.google.android.gms.safetynet",          // Google SafetyNet
        "com.google.android.gms.tapandpay",          // Google Tap & Pay
        "com.google.android.gms.wearable",           // Google Wearable
        // Google Play Store Services
        "com.android.vending.billing.InAppBillingService.COIN", // Play billing
        "com.google.android.finsky",                 // Play Store (system component)
        "com.google.android.finsky.billing",         // Play Store billing
        "com.google.android.finsky.deviceconfig",    // Play Store device config
        "com.google.android.finsky.instantapps",     // Play Store instant apps
        "com.google.android.finsky.permission",      // Play Store permissions
        // Google One Services
        "com.google.android.apps.subscriptions.red", // Google One
        "com.google.android.apps.subscriptions.red.main" // Google One main
    );
} 