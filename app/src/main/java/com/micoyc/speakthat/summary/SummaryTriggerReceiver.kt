/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat.summary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.micoyc.speakthat.InAppLogger

/**
 * Static broadcast entrypoint for summaries.
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

        if (!SummarySettingsGate.isGlobalGateOpen(context)) {
            if (isScheduledAlarm) {
                SummaryScheduler.cancel(context)
            }
            InAppLogger.log(
                "SummaryTriggerReceiver",
                "Summary trigger blocked; global prerequisites are not satisfied"
            )
            return
        }

        if (isScheduledAlarm && !SummarySettingsGate.canSchedule(context)) {
            SummaryScheduler.cancel(context)
            InAppLogger.log("SummaryTriggerReceiver", "Summary alarm fired while scheduler disabled; skipping service start")
            return
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
            if (SummaryScheduler.schedule(context)) {
                InAppLogger.log("SummaryTriggerReceiver", "Scheduled next daily summary alarm")
            }
        }
    }
}
