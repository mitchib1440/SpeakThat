package com.micoyc.speakthat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/**
 * BroadcastReceiver to handle auto-start on boot functionality
 */
public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, context.getString(R.string.log_boot_receiver_boot_completed));
            
            // Check if auto-start is enabled
            SharedPreferences prefs = context.getSharedPreferences(context.getString(R.string.boot_receiver_prefs_name), Context.MODE_PRIVATE);
            boolean autoStartEnabled = prefs.getBoolean(context.getString(R.string.boot_receiver_key_auto_start), false);
            boolean masterSwitchEnabled = prefs.getBoolean(context.getString(R.string.boot_receiver_key_master_switch), true);
            
            if (autoStartEnabled && masterSwitchEnabled) {
                Log.d(TAG, context.getString(R.string.log_boot_receiver_auto_start));
                
                // For NotificationListenerService, we don't need to start it manually
                // The system will automatically start it when the user grants permission
                // We just need to ensure the service is enabled
                try {
                    // Check if the notification listener service is enabled
                    String flat = android.provider.Settings.Secure.getString(
                        context.getContentResolver(), 
                        context.getString(R.string.boot_receiver_enabled_notification_listeners)
                    );
                    
                    if (flat != null && !flat.isEmpty()) {
                        String[] names = flat.split(":");
                        for (String name : names) {
                            android.content.ComponentName componentName = 
                                android.content.ComponentName.unflattenFromString(name);
                            if (componentName != null && 
                                context.getPackageName().equals(componentName.getPackageName())) {
                                Log.d(TAG, context.getString(R.string.log_boot_receiver_service_enabled));
                                InAppLogger.log("BootReceiver", context.getString(R.string.log_boot_receiver_service_enabled));
                                return;
                            }
                        }
                    }
                    
                    // If not enabled, log that user needs to grant permission
                    Log.d(TAG, context.getString(R.string.log_boot_receiver_service_not_enabled));
                    InAppLogger.log("BootReceiver", context.getString(R.string.log_boot_receiver_service_not_enabled));
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error checking notification service status", e);
                    InAppLogger.logError("BootReceiver", "Error checking service status: " + e.getMessage());
                }
            } else {
                Log.d(TAG, "Auto-start disabled or master switch off, not starting service");
                InAppLogger.log("BootReceiver", "Auto-start disabled or master switch off");
            }
        }
    }
} 