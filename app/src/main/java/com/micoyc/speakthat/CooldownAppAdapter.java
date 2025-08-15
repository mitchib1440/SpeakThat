package com.micoyc.speakthat;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.micoyc.speakthat.CoilImageLoader;
import java.util.List;

public class CooldownAppAdapter extends RecyclerView.Adapter<CooldownAppAdapter.ViewHolder> {
    
    private final List<CooldownAppItem> cooldownApps;
    private final OnCooldownAppActionListener listener;
    private final Context context;
    private final String[] timeOptions;
    private final String[] timeValues;
    
    public interface OnCooldownAppActionListener {
        void onCooldownTimeChanged(int position, int cooldownSeconds);
        void onDeleteCooldownApp(int position);
    }
    
    public static class CooldownAppItem {
        public String packageName;
        public String displayName;
        public String iconSlug;
        public Drawable icon; // Actual app icon from device
        public int cooldownSeconds;
        
        // Constructor for JSON app list (with iconSlug)
        public CooldownAppItem(String packageName, String displayName, String iconSlug, int cooldownSeconds) {
            this.packageName = packageName;
            this.displayName = displayName;
            this.iconSlug = iconSlug;
            this.icon = null;
            this.cooldownSeconds = cooldownSeconds;
        }
        
        // Constructor for device apps (with actual icon)
        public CooldownAppItem(String packageName, String displayName, Drawable icon, int cooldownSeconds) {
            this.packageName = packageName;
            this.displayName = displayName;
            this.iconSlug = null;
            this.icon = icon;
            this.cooldownSeconds = cooldownSeconds;
        }
        
        // Constructor for loading from storage (loads icon from package manager if needed)
        public CooldownAppItem(String packageName, String displayName, String iconSlug, int cooldownSeconds, boolean hasDeviceIcon) {
            this.packageName = packageName;
            this.displayName = displayName;
            this.iconSlug = hasDeviceIcon ? null : iconSlug;
            this.icon = null; // Will be loaded later if needed
            this.cooldownSeconds = cooldownSeconds;
        }
    }
    
    public CooldownAppAdapter(Context context, List<CooldownAppItem> cooldownApps, OnCooldownAppActionListener listener) {
        this.context = context;
        this.cooldownApps = cooldownApps;
        this.listener = listener;
        this.timeOptions = context.getResources().getStringArray(R.array.cooldown_time_options);
        this.timeValues = context.getResources().getStringArray(R.array.cooldown_time_values);
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_cooldown_app, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CooldownAppItem item = cooldownApps.get(position);
        
        // Set app name and package
        holder.appName.setText(item.displayName);
        holder.packageName.setText(item.packageName);
        
        // Load app icon
        if (item.icon != null) {
            holder.appIcon.setImageDrawable(item.icon);
        } else if (item.iconSlug != null && !item.iconSlug.isEmpty()) {
            String iconUrl = String.format(context.getString(R.string.cooldown_app_icon_url), item.iconSlug);
            CoilImageLoader.loadSvg(holder.appIcon, iconUrl);
        } else {
            // Try to load icon from package manager (for device apps loaded from storage)
            try {
                android.content.pm.PackageManager pm = context.getPackageManager();
                android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(item.packageName, 0);
                Drawable icon = pm.getApplicationIcon(appInfo);
                holder.appIcon.setImageDrawable(icon);
                item.icon = icon; // Cache the icon for future use
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                // Fall back to default icon
                holder.appIcon.setImageResource(R.drawable.ic_app_unknown);
            }
        }
        
        // Set up dropdown adapter
        ArrayAdapter<String> dropdownAdapter = new ArrayAdapter<>(
            context, 
            android.R.layout.simple_dropdown_item_1line, 
            timeOptions
        );
        holder.cooldownTimeDropdown.setAdapter(dropdownAdapter);
        
        // Set current selection
        int selectedIndex = getTimeOptionIndex(item.cooldownSeconds);
        if (selectedIndex >= 0) {
            holder.cooldownTimeDropdown.setText(timeOptions[selectedIndex], false);
        }
        
        // Set up dropdown listener
        holder.cooldownTimeDropdown.setOnItemClickListener((parent, view, clickedPosition, id) -> {
            if (clickedPosition >= 0 && clickedPosition < timeValues.length) {
                int newCooldownSeconds = Integer.parseInt(timeValues[clickedPosition]);
                item.cooldownSeconds = newCooldownSeconds;
                if (listener != null) {
                    listener.onCooldownTimeChanged(holder.getAdapterPosition(), newCooldownSeconds);
                }
            }
        });
        
        // Set up delete button
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteCooldownApp(holder.getAdapterPosition());
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return cooldownApps.size();
    }
    
    private int getTimeOptionIndex(int cooldownSeconds) {
        for (int i = 0; i < timeValues.length; i++) {
            if (Integer.parseInt(timeValues[i]) == cooldownSeconds) {
                return i;
            }
        }
        return 0; // Default to "1 second" (first option)
    }
    
    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        TextView packageName;
        AutoCompleteTextView cooldownTimeDropdown;
        View btnDelete;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.appIcon);
            appName = itemView.findViewById(R.id.appName);
            packageName = itemView.findViewById(R.id.packageName);
            cooldownTimeDropdown = itemView.findViewById(R.id.cooldownTimeDropdown);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
} 