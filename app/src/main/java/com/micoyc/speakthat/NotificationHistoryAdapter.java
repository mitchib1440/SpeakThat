/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class NotificationHistoryAdapter extends RecyclerView.Adapter<NotificationHistoryAdapter.NotificationViewHolder> {
    
    private List<NotificationReaderService.NotificationData> notifications;
    private OnFilterClickListener filterClickListener;
    
    public interface OnFilterClickListener {
        void onFilterClick(NotificationReaderService.NotificationData notification);
    }
    
    public NotificationHistoryAdapter(List<NotificationReaderService.NotificationData> notifications, OnFilterClickListener listener) {
        this.notifications = notifications;
        this.filterClickListener = listener;
    }
    
    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification_history, parent, false);
        return new NotificationViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationReaderService.NotificationData notification = notifications.get(position);
        holder.bind(notification, filterClickListener);
    }
    
    @Override
    public int getItemCount() {
        return notifications.size();
    }
    
    public void updateNotifications(List<NotificationReaderService.NotificationData> newNotifications) {
        this.notifications = newNotifications;
        notifyDataSetChanged();
    }
    
    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        private TextView textAppName;
        private TextView textTimestamp;
        private TextView textNotificationContent;
        private MaterialButton btnFilterSimilar;
        
        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            textAppName = itemView.findViewById(R.id.textAppName);
            textTimestamp = itemView.findViewById(R.id.textTimestamp);
            textNotificationContent = itemView.findViewById(R.id.textNotificationContent);
            btnFilterSimilar = itemView.findViewById(R.id.btnFilterSimilar);
        }
        
        public void bind(NotificationReaderService.NotificationData notification, OnFilterClickListener listener) {
            textAppName.setText(notification.getAppName());
            textTimestamp.setText(notification.getTimestamp());
            textNotificationContent.setText(notification.getText());
            
            btnFilterSimilar.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFilterClick(notification);
                }
            });
        }
    }
} 