package com.micoyc.speakthat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class WordListAdapter extends RecyclerView.Adapter<WordListAdapter.ViewHolder> {
    private List<FilterSettingsActivity.WordFilterItem> items;
    private FilterSettingsActivity.OnWordActionListener removeListener;
    private OnTypeChangeListener typeChangeListener;
    private FilterSettingsActivity.OnWordActionListener editListener;

    public WordListAdapter(List<FilterSettingsActivity.WordFilterItem> items, 
                          FilterSettingsActivity.OnWordActionListener removeListener,
                          OnTypeChangeListener typeChangeListener,
                          FilterSettingsActivity.OnWordActionListener editListener) {
        this.items = items;
        this.removeListener = removeListener;
        this.typeChangeListener = typeChangeListener;
        this.editListener = editListener;
    }

    public interface OnTypeChangeListener {
        void onTypeChange(int position, boolean isPrivate);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_word_filter, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FilterSettingsActivity.WordFilterItem item = items.get(position);
        
        holder.textWord.setText(item.word);
        
        // Make text clickable for editing
        holder.textWord.setOnClickListener(v -> {
            if (editListener != null) {
                editListener.onAction(holder.getAdapterPosition());
            }
        });
        
        // Bind private checkbox to match App List behaviour
        holder.checkBoxPrivate.setOnCheckedChangeListener(null);
        holder.checkBoxPrivate.setChecked(item.isPrivate);
        holder.checkBoxPrivate.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            if (item.isPrivate != isChecked) {
                item.isPrivate = isChecked;
                if (typeChangeListener != null) {
                    typeChangeListener.onTypeChange(holder.getAdapterPosition(), isChecked);
                }
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
        TextView textWord;
        CheckBox checkBoxPrivate;
        ImageButton buttonRemove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textWord = itemView.findViewById(R.id.textWord);
            checkBoxPrivate = itemView.findViewById(R.id.checkBoxPrivate);
            buttonRemove = itemView.findViewById(R.id.buttonRemove);
        }
    }
} 