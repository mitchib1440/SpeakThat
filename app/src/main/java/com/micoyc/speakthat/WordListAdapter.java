package com.micoyc.speakthat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
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
        
        // Set up spinner with blacklist types
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
            holder.itemView.getContext(), R.array.blacklist_types, android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        holder.spinnerType.setAdapter(spinnerAdapter);
        
        // Set selection based on current type (0 = Block, 1 = Private)
        holder.spinnerType.setSelection(item.isPrivate ? 1 : 0);
        
        // Clear previous listener to avoid triggering during setup
        holder.spinnerType.setOnItemSelectedListener(null);
        
        // Set up spinner selection listener
        holder.spinnerType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int selection, long id) {
                boolean newIsPrivate = (selection == 1);
                if (item.isPrivate != newIsPrivate) {
                    item.isPrivate = newIsPrivate;
                    if (typeChangeListener != null) {
                        typeChangeListener.onTypeChange(holder.getAdapterPosition(), newIsPrivate);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
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
        Spinner spinnerType;
        ImageButton buttonRemove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textWord = itemView.findViewById(R.id.textWord);
            spinnerType = itemView.findViewById(R.id.spinnerType);
            buttonRemove = itemView.findViewById(R.id.buttonRemove);
        }
    }
} 