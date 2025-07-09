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
    private static final String PREFS_NAME = "SpeakThatPrefs";
    private static final String KEY_AUTO_START = "auto_start_on_boot";
    private static final String KEY_MASTER_SWITCH_ENABLED = "master_switch_enabled";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed received");
            
            // Check if auto-start is enabled
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean autoStartEnabled = prefs.getBoolean(KEY_AUTO_START, false);
            boolean masterSwitchEnabled = prefs.getBoolean(KEY_MASTER_SWITCH_ENABLED, true);
            
            if (autoStartEnabled && masterSwitchEnabled) {
                Log.d(TAG, "Auto-start enabled, starting notification service");
                
                // For NotificationListenerService, we don't need to start it manually
                // The system will automatically start it when the user grants permission
                // We just need to ensure the service is enabled
                try {
                    // Check if the notification listener service is enabled
                    String flat = android.provider.Settings.Secure.getString(
                        context.getContentResolver(), 
                        "enabled_notification_listeners"
                    );
                    
                    if (flat != null && !flat.isEmpty()) {
                        String[] names = flat.split(":");
                        for (String name : names) {
                            android.content.ComponentName componentName = 
                                android.content.ComponentName.unflattenFromString(name);
                            if (componentName != null && 
                                context.getPackageName().equals(componentName.getPackageName())) {
                                Log.d(TAG, "Notification service already enabled, no action needed");
                                InAppLogger.log("BootReceiver", "Notification service already enabled");
                                return;
                            }
                        }
                    }
                    
                    // If not enabled, log that user needs to grant permission
                    Log.d(TAG, "Notification service not enabled, user needs to grant permission");
                    InAppLogger.log("BootReceiver", "Notification service not enabled - user needs to grant permission");
                    
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