package com.micoyc.speakthat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
    private List<FilterSettingsActivity.AppFilterItem> items;
    private FilterSettingsActivity.OnAppActionListener removeListener;
    private FilterSettingsActivity.OnAppActionListener privateToggleListener;
    private FilterSettingsActivity.OnAppActionListener editListener;

    public AppListAdapter(List<FilterSettingsActivity.AppFilterItem> items, 
                         FilterSettingsActivity.OnAppActionListener removeListener,
                         FilterSettingsActivity.OnAppActionListener privateToggleListener,
                         FilterSettingsActivity.OnAppActionListener editListener) {
        this.items = items;
        this.removeListener = removeListener;
        this.privateToggleListener = privateToggleListener;
        this.editListener = editListener;
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
        
        holder.textAppName.setText(item.packageName);
        holder.checkBoxPrivate.setChecked(item.isPrivate);
        
        // Make text clickable for editing
        holder.textAppName.setOnClickListener(v -> {
            if (editListener != null) {
                editListener.onAction(holder.getAdapterPosition());
            }
        });
        
        // Set up checkbox listener
        holder.checkBoxPrivate.setOnCheckedChangeListener(null); // Clear previous listener
        holder.checkBoxPrivate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (privateToggleListener != null) {
                privateToggleListener.onAction(holder.getAdapterPosition());
            }
        });
        
        // Set up remove button listener
        holder.buttonRemove.setOnClickListener(v -> {
            if (removeListener != null) {
                removeListener.onAction(holder.getAdapterPosition());
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
} 