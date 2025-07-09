package com.micoyc.speakthat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class CustomAppNameAdapter extends RecyclerView.Adapter<CustomAppNameAdapter.ViewHolder> {
    
    private List<CustomAppNameEntry> customAppNames = new ArrayList<>();
    private OnCustomAppNameActionListener actionListener;
    
    public interface OnCustomAppNameActionListener {
        void onDelete(int position);
    }
    
    public static class CustomAppNameEntry {
        private String packageName;
        private String customName;
        
        public CustomAppNameEntry(String packageName, String customName) {
            this.packageName = packageName;
            this.customName = customName;
        }
        
        public String getPackageName() { return packageName; }
        public String getCustomName() { return customName; }
        
        public void setPackageName(String packageName) { this.packageName = packageName; }
        public void setCustomName(String customName) { this.customName = customName; }
    }
    
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textPackageName;
        TextView textCustomName;
        MaterialButton btnDelete;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textPackageName = itemView.findViewById(R.id.textPackageName);
            textCustomName = itemView.findViewById(R.id.textCustomName);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
    
    public CustomAppNameAdapter(OnCustomAppNameActionListener listener) {
        this.actionListener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_custom_app_name, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CustomAppNameEntry entry = customAppNames.get(position);
        
        holder.textPackageName.setText(entry.getPackageName());
        holder.textCustomName.setText(entry.getCustomName());
        
        holder.btnDelete.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onDelete(holder.getAdapterPosition());
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return customAppNames.size();
    }
    
    public void updateCustomAppNames(List<CustomAppNameEntry> newList) {
        this.customAppNames = new ArrayList<>(newList);
        notifyDataSetChanged();
    }
    
    public void addCustomAppName(CustomAppNameEntry entry) {
        customAppNames.add(entry);
        notifyItemInserted(customAppNames.size() - 1);
    }
    
    public void removeCustomAppName(int position) {
        if (position >= 0 && position < customAppNames.size()) {
            customAppNames.remove(position);
            notifyItemRemoved(position);
        }
    }
    
    public List<CustomAppNameEntry> getCustomAppNames() {
        return new ArrayList<>(customAppNames);
    }
} 