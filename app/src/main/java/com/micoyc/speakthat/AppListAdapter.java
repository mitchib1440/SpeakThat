package com.micoyc.speakthat;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
    private List<FilterSettingsActivity.AppFilterItem> items;
    private FilterSettingsActivity.OnAppActionListener removeListener;
    private FilterSettingsActivity.OnAppActionListener privateToggleListener;
    private FilterSettingsActivity.OnAppActionListener editListener;
    private final boolean showPrivateToggle;
    private final Map<String, String> appNameCache = new HashMap<>();

    public AppListAdapter(List<FilterSettingsActivity.AppFilterItem> items, 
                         FilterSettingsActivity.OnAppActionListener removeListener,
                         FilterSettingsActivity.OnAppActionListener privateToggleListener,
                         FilterSettingsActivity.OnAppActionListener editListener,
                         boolean showPrivateToggle) {
        this.items = items;
        this.removeListener = removeListener;
        this.privateToggleListener = privateToggleListener;
        this.editListener = editListener;
        this.showPrivateToggle = showPrivateToggle;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_filter, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FilterSettingsActivity.AppFilterItem item = items.get(position);
        
        holder.textAppName.setText(resolveDisplayName(holder.itemView.getContext(), item.packageName));
        // Show/hide private toggle based on caller intent
        holder.checkBoxPrivate.setVisibility(showPrivateToggle ? View.VISIBLE : View.GONE);
        if (showPrivateToggle) {
            holder.checkBoxPrivate.setChecked(item.isPrivate);
        } else {
            // Avoid stale listeners when reused in recycled views
            holder.checkBoxPrivate.setOnCheckedChangeListener(null);
        }
        
        // Make text clickable for editing
        holder.textAppName.setOnClickListener(v -> {
            if (editListener != null) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    editListener.onAction(pos);
                }
            }
        });
        
        // Set up checkbox listener
        if (showPrivateToggle) {
            holder.checkBoxPrivate.setOnCheckedChangeListener(null); // Clear previous listener
            holder.checkBoxPrivate.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (privateToggleListener != null) {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        privateToggleListener.onAction(pos);
                    }
                }
            });
        }
        
        // Set up remove button listener
        holder.buttonRemove.setOnClickListener(v -> {
            if (removeListener != null) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    removeListener.onAction(pos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textAppName;
        CheckBox checkBoxPrivate;
        ImageButton buttonRemove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textAppName = itemView.findViewById(R.id.textAppName);
            checkBoxPrivate = itemView.findViewById(R.id.checkBoxPrivate);
            buttonRemove = itemView.findViewById(R.id.buttonRemove);
        }
    }

    private String resolveDisplayName(android.content.Context context, String packageName) {
        if (appNameCache.containsKey(packageName)) {
            return appNameCache.get(packageName);
        }

        String displayName = packageName;
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(appInfo);
            if (label != null && label.length() > 0) {
                displayName = label.toString();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            AppListData appData = AppListManager.INSTANCE.findAppByPackage(context, packageName);
            if (appData != null && appData.displayName != null && !appData.displayName.isEmpty()) {
                displayName = appData.displayName;
            }
        }

        appNameCache.put(packageName, displayName);
        return displayName;
    }
} 