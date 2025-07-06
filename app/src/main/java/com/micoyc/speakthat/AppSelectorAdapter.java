package com.micoyc.speakthat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class AppSelectorAdapter extends BaseAdapter implements Filterable {
    
    private Context context;
    private List<AppInfo> originalApps;
    private List<AppInfo> filteredApps;
    private LayoutInflater inflater;
    private AppFilter appFilter;
    
    public AppSelectorAdapter(Context context, List<AppInfo> apps) {
        this.context = context;
        this.originalApps = new ArrayList<>(apps);
        this.filteredApps = new ArrayList<>(apps);
        this.inflater = LayoutInflater.from(context);
        this.appFilter = new AppFilter();
    }
    
    @Override
    public int getCount() {
        return filteredApps.size();
    }
    
    @Override
    public AppInfo getItem(int position) {
        return filteredApps.get(position);
    }
    
    @Override
    public long getItemId(int position) {
        return position;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
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
        holder.iconImageView.setImageDrawable(app.icon);
        holder.nameTextView.setText(app.appName);
        holder.packageTextView.setText(app.packageName);
        
        return convertView;
    }
    
    @Override
    public Filter getFilter() {
        return appFilter;
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
            List<AppInfo> filteredList = new ArrayList<>();
            
            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(originalApps);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                
                for (AppInfo app : originalApps) {
                    // Search in app name and package name
                    if (app.appName.toLowerCase().contains(filterPattern) ||
                        app.packageName.toLowerCase().contains(filterPattern)) {
                        filteredList.add(app);
                    }
                }
            }
            
            results.values = filteredList;
            results.count = filteredList.size();
            return results;
        }
        
        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filteredApps.clear();
            filteredApps.addAll((List<AppInfo>) results.values);
            notifyDataSetChanged();
        }
    }
} 