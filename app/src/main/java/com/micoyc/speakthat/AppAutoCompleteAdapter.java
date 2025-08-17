package com.micoyc.speakthat;

import android.content.Context;
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

public class AppAutoCompleteAdapter extends ArrayAdapter<AppInfo> {
    
    private List<AppInfo> allApps;
    private List<AppInfo> filteredApps;
    private LayoutInflater inflater;
    private Context context;
    
    public AppAutoCompleteAdapter(Context context, List<AppInfo> apps) {
        super(context, R.layout.item_app_selector);
        this.context = context;
        this.allApps = new ArrayList<>(apps);
        this.filteredApps = new ArrayList<>(apps);
        this.inflater = LayoutInflater.from(context);
        
        // Add all items to the adapter
        addAll(filteredApps);
        
        // Debug: Log some sample apps to see what we're working with
        logSampleApps();
    }
    
    private void logSampleApps() {
        InAppLogger.log("AppSelector", context.getString(R.string.log_app_selector_sample_apps));
        int count = 0;
        for (AppInfo app : allApps) {
            if (count < 5) {
                InAppLogger.log("AppSelector", String.format(context.getString(R.string.log_app_selector_app_info), app.appName, app.packageName));
                count++;
            }
            
            // Specifically look for Google Maps variations
            if (app.appName.toLowerCase().contains("maps") || 
                app.packageName.toLowerCase().contains("maps") ||
                app.packageName.toLowerCase().contains("google")) {
                InAppLogger.log("AppSelector", String.format(context.getString(R.string.app_selector_google_maps_found), app.appName, app.packageName));
            }
        }
        InAppLogger.log("AppSelector", String.format(context.getString(R.string.app_selector_total_apps), allApps.size()));
    }
    
    @Override
    public int getCount() {
        return filteredApps.size();
    }
    
    @Override
    public AppInfo getItem(int position) {
        return filteredApps.get(position);
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
        return new AppFilter();
    }
    
    private static class ViewHolder {
        ImageView iconImageView;
        TextView nameTextView;
        TextView packageTextView;
    }
    
    private class AppFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            List<AppInfo> suggestions = new ArrayList<>();
            
            String filterText = "";
            if (constraint != null && constraint.length() > 0) {
                filterText = constraint.toString().toLowerCase().trim();
                
                // Debug logging
                InAppLogger.log("AppFilter", String.format(context.getString(R.string.app_filter_filtering_with), filterText));
                
                for (AppInfo app : allApps) {
                    // Search in app name and package name
                    boolean nameMatch = app.appName.toLowerCase().contains(filterText);
                    boolean packageMatch = app.packageName.toLowerCase().contains(filterText);
                    
                    if (nameMatch || packageMatch) {
                        suggestions.add(app);
                        // Debug log matches
                        if (filterText.equals("map") || filterText.equals("maps")) {
                            InAppLogger.log("AppFilter", String.format(context.getString(R.string.app_filter_match), app.appName, app.packageName, nameMatch, packageMatch));
                        }
                    }
                    
                    // Special debugging for Google Maps detection
                    if (filterText.equals("map") || filterText.equals("maps")) {
                        if (app.appName.toLowerCase().contains("maps") || 
                            app.packageName.toLowerCase().contains("maps") ||
                            app.packageName.toLowerCase().contains("google.android.apps.maps")) {
                            InAppLogger.log("AppFilter", String.format(context.getString(R.string.app_filter_google_maps_candidate), app.appName, app.packageName));
                        }
                    }
                }
                
                InAppLogger.log("AppFilter", String.format(context.getString(R.string.app_filter_found_matches), suggestions.size(), filterText));
            } else {
                // Show all apps when no filter
                suggestions.addAll(allApps);
                InAppLogger.log("AppFilter", String.format(context.getString(R.string.app_filter_no_filter_showing_all), suggestions.size()));
            }
            
            results.values = suggestions;
            results.count = suggestions.size();
            return results;
        }
        
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filteredApps.clear();
            if (results.values != null) {
                filteredApps.addAll((List<AppInfo>) results.values);
            }
            
            // Debug logging
            InAppLogger.log("AppFilter", String.format(context.getString(R.string.app_filter_publishing_results), filteredApps.size()));
            
            // Clear and re-add all items to the adapter
            clear();
            addAll(filteredApps);
            notifyDataSetChanged();
        }
    }
} 