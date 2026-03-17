package com.micoyc.speakthat.summary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.micoyc.speakthat.InAppLogger

/**
 * Static broadcast entrypoint for proactive summaries.
 *
 * This receiver is intentionally standalone and does not depend on the conditional rules engine.
 */
class SummaryTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val isExternalTrigger = action == SummaryConstants.ACTION_TRIGGER_SUMMARY
        val isScheduledAlarm = action == SummaryConstants.ACTION_SUMMARY_ALARM
        if (!isExternalTrigger && !isScheduledAlarm) {
            InAppLogger.log("SummaryTriggerReceiver", "Ignoring unsupported action: $action")
            return
        }

        if (isScheduledAlarm) {
            val enabled = context.getSharedPreferences(
                SummaryConstants.SUMMARY_SETTINGS_PREFS_NAME,
                Context.MODE_PRIVATE
            ).getBoolean(SummaryConstants.KEY_ENABLED, false)
            if (!enabled) {
                InAppLogger.log("SummaryTriggerReceiver", "Summary alarm fired while disabled; skipping service start")
                return
            }
        }

        val source = when {
            isScheduledAlarm -> SummaryConstants.TRIGGER_SOURCE_SCHEDULED_ALARM
            else -> intent.getStringExtra(SummaryConstants.EXTRA_TRIGGER_SOURCE)
                ?: SummaryConstants.TRIGGER_SOURCE_EXTERNAL_INTENT
        }

        val startIntent = Intent(context, SummaryExecutionService::class.java).apply {
            this.action = SummaryConstants.ACTION_TRIGGER_SUMMARY
            putExtra(SummaryConstants.EXTRA_TRIGGER_SOURCE, source)
        }

        ContextCompat.startForegroundService(context.applicationContext, startIntent)
        InAppLogger.log("SummaryTriggerReceiver", "SummaryExecutionService started from source=$source")

        if (isScheduledAlarm) {
            SummaryScheduler.schedule(context)
            InAppLogger.log("SummaryTriggerReceiver", "Scheduled next daily summary alarm")
        }
    }
}
