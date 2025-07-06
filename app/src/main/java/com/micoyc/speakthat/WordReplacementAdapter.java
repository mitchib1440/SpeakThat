package com.micoyc.speakthat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class WordReplacementAdapter extends RecyclerView.Adapter<WordReplacementAdapter.ViewHolder> {
    private List<FilterSettingsActivity.WordReplacementItem> items;
    private FilterSettingsActivity.OnWordActionListener removeListener;
    private FilterSettingsActivity.OnWordActionListener editListener;

    public WordReplacementAdapter(List<FilterSettingsActivity.WordReplacementItem> items, 
                                 FilterSettingsActivity.OnWordActionListener removeListener,
                                 FilterSettingsActivity.OnWordActionListener editListener) {
        this.items = items;
        this.removeListener = removeListener;
        this.editListener = editListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_word_replacement, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FilterSettingsActivity.WordReplacementItem item = items.get(position);
        
        holder.textFrom.setText(item.from);
        holder.textTo.setText(item.to);
        
        // Make the replacement text clickable for editing
        View.OnClickListener editClickListener = v -> {
            if (editListener != null) {
                editListener.onAction(holder.getAdapterPosition());
            }
        };
        holder.textFrom.setOnClickListener(editClickListener);
        holder.textTo.setOnClickListener(editClickListener);
        
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
        TextView textFrom;
        TextView textTo;
        ImageButton buttonRemove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textFrom = itemView.findViewById(R.id.textFrom);
            textTo = itemView.findViewById(R.id.textTo);
            buttonRemove = itemView.findViewById(R.id.buttonRemove);
        }
    }
} 