/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;

public class CooldownAppAdapter extends RecyclerView.Adapter<CooldownAppAdapter.ViewHolder> {

    private static final int UNIT_SECONDS = 0;
    private static final int UNIT_MINUTES = 1;
    private static final int UNIT_HOURS = 2;
    private static final int MAX_COOLDOWN_SECONDS = 24 * 60 * 60;

    private final List<CooldownAppItem> cooldownApps;
    private final OnCooldownAppActionListener listener;
    private final Context context;
    private final String[] unitOptions;

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

    private static class CooldownDisplay {
        final int value;
        final int unitIndex;

        CooldownDisplay(int value, int unitIndex) {
            this.value = value;
            this.unitIndex = unitIndex;
        }
    }

    public CooldownAppAdapter(Context context, List<CooldownAppItem> cooldownApps, OnCooldownAppActionListener listener) {
        this.context = context;
        this.cooldownApps = cooldownApps;
        this.listener = listener;
        this.unitOptions = new String[] {
            context.getString(R.string.cooldown_unit_seconds),
            context.getString(R.string.cooldown_unit_minutes),
            context.getString(R.string.cooldown_unit_hours)
        };
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

        holder.cooldownTimeButton.setText(formatCooldownLabel(item.cooldownSeconds));
        holder.cooldownTimeButton.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) {
                return;
            }
            showCooldownDialog(item, adapterPosition);
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

    private void showCooldownDialog(CooldownAppItem item, int adapterPosition) {
        CooldownDisplay initialDisplay = fromSeconds(item.cooldownSeconds);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = (int) (24 * context.getResources().getDisplayMetrics().density);
        int topPadding = (int) (8 * context.getResources().getDisplayMetrics().density);
        container.setPadding(horizontalPadding, topPadding, horizontalPadding, 0);

        EditText valueInput = new EditText(context);
        valueInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        valueInput.setHint(R.string.cooldown_dialog_value_hint);
        valueInput.setText(String.valueOf(initialDisplay.value));
        valueInput.setSelection(valueInput.getText().length());
        container.addView(valueInput);

        Spinner unitSpinner = new Spinner(context);
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(
            context,
            android.R.layout.simple_spinner_item,
            unitOptions
        );
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(unitAdapter);
        unitSpinner.setSelection(initialDisplay.unitIndex);
        container.addView(unitSpinner);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.cooldown_dialog_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.button_save, null)
            .create();

        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String rawInput = valueInput.getText().toString().trim();
            if (rawInput.isEmpty()) {
                valueInput.setError(context.getString(R.string.cooldown_error_required));
                return;
            }

            int numericValue;
            try {
                numericValue = Integer.parseInt(rawInput);
            } catch (NumberFormatException e) {
                valueInput.setError(context.getString(R.string.cooldown_error_required));
                return;
            }

            if (numericValue <= 0) {
                valueInput.setError(context.getString(R.string.cooldown_error_positive));
                return;
            }

            int selectedUnitIndex = unitSpinner.getSelectedItemPosition();
            int totalSeconds = toSeconds(numericValue, selectedUnitIndex);
            if (totalSeconds <= 0 || totalSeconds > MAX_COOLDOWN_SECONDS) {
                Toast.makeText(context, context.getString(R.string.cooldown_error_max_24_hours), Toast.LENGTH_SHORT).show();
                return;
            }

            item.cooldownSeconds = totalSeconds;
            notifyItemChanged(adapterPosition);
            if (listener != null) {
                listener.onCooldownTimeChanged(adapterPosition, totalSeconds);
            }
            dialog.dismiss();
        }));

        dialog.show();
    }

    private CooldownDisplay fromSeconds(int totalSeconds) {
        int safeSeconds = Math.max(1, totalSeconds);
        if (safeSeconds % 3600 == 0) {
            return new CooldownDisplay(safeSeconds / 3600, UNIT_HOURS);
        }
        if (safeSeconds % 60 == 0) {
            return new CooldownDisplay(safeSeconds / 60, UNIT_MINUTES);
        }
        return new CooldownDisplay(safeSeconds, UNIT_SECONDS);
    }

    private int toSeconds(int value, int unitIndex) {
        long totalSeconds;
        if (unitIndex == UNIT_HOURS) {
            totalSeconds = (long) value * 3600L;
        } else if (unitIndex == UNIT_MINUTES) {
            totalSeconds = (long) value * 60L;
        } else {
            totalSeconds = value;
        }

        if (totalSeconds > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) totalSeconds;
    }

    private String formatCooldownLabel(int totalSeconds) {
        CooldownDisplay display = fromSeconds(totalSeconds);
        if (display.unitIndex == UNIT_HOURS) {
            return context.getResources().getQuantityString(
                R.plurals.cooldown_hours_label,
                display.value,
                display.value
            );
        }
        if (display.unitIndex == UNIT_MINUTES) {
            return context.getResources().getQuantityString(
                R.plurals.cooldown_minutes_label,
                display.value,
                display.value
            );
        }
        return context.getResources().getQuantityString(
            R.plurals.cooldown_seconds_label,
            display.value,
            display.value
        );
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        TextView packageName;
        TextView cooldownTimeButton;
        View btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.appIcon);
            appName = itemView.findViewById(R.id.appName);
            packageName = itemView.findViewById(R.id.packageName);
            cooldownTimeButton = itemView.findViewById(R.id.cooldownTimeButton);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}