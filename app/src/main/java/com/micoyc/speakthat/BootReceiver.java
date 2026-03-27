/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.micoyc.speakthat.summary.SummaryScheduler;

/**
 * BroadcastReceiver to handle auto-start on boot functionality
 */
public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (isBootAction(action)) {
            Log.d(TAG, "Boot-related action received: " + action);
            
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
                    boolean listenerEnabled = NotificationListenerRecovery.isNotificationAccessGranted(context);

                    if (listenerEnabled) {
                        Log.d(TAG, context.getString(R.string.log_boot_receiver_service_enabled));
                        InAppLogger.log("BootReceiver", context.getString(R.string.log_boot_receiver_service_enabled));

                        boolean rebindRequested = NotificationListenerRecovery.requestRebind(context, "boot_completed", false);
                        Log.d(TAG, "Notification listener rebind requested at boot: " + rebindRequested);
                        InAppLogger.log("BootReceiver", "Listener rebind requested at boot (result=" + rebindRequested + ")");
                    } else {
                        // If not enabled, log that user needs to grant permission
                        Log.d(TAG, context.getString(R.string.log_boot_receiver_service_not_enabled));
                        InAppLogger.log("BootReceiver", context.getString(R.string.log_boot_receiver_service_not_enabled));
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error checking notification service status", e);
                    InAppLogger.logError("BootReceiver", "Error checking service status: " + e.getMessage());
                }
            } else {
                Log.d(TAG, "Auto-start disabled or master switch off, not starting service");
                InAppLogger.log("BootReceiver", "Auto-start disabled or master switch off");
            }

            try {
                boolean restored = SummaryScheduler.rescheduleIfEnabled(context);
                Log.d(TAG, "SummaryScheduler reschedule after reboot result=" + restored);
                InAppLogger.log("BootReceiver", "Summary alarm reschedule attempted (result=" + restored + ")");
            } catch (Exception e) {
                Log.e(TAG, "Error rescheduling summary alarm", e);
                InAppLogger.logError("BootReceiver", "Failed to reschedule summary alarm: " + e.getMessage());
            }

            try {
                SharedPreferences speakThatPrefs = context.getSharedPreferences(
                        context.getString(R.string.boot_receiver_prefs_name), Context.MODE_PRIVATE);
                ServiceRestartPolicy.migrateIfNeeded(speakThatPrefs);
                ServiceRestartPolicyScheduler.syncPeriodicWork(
                        context, ServiceRestartPolicy.readPolicy(speakThatPrefs));
            } catch (Exception e) {
                Log.e(TAG, "Error syncing service restart policy periodic work", e);
                InAppLogger.logError("BootReceiver", "Failed to sync service restart policy work: " + e.getMessage());
            }
        }
    }

    private boolean isBootAction(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action);
    }
}