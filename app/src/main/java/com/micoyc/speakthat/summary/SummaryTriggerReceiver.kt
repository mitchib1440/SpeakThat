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
        if (action != SummaryConstants.ACTION_TRIGGER_SUMMARY) {
            InAppLogger.log("SummaryTriggerReceiver", "Ignoring unsupported action: $action")
            return
        }

        val source = intent.getStringExtra(SummaryConstants.EXTRA_TRIGGER_SOURCE)
            ?: SummaryConstants.TRIGGER_SOURCE_EXTERNAL_INTENT

        val startIntent = Intent(context, SummaryExecutionService::class.java).apply {
            this.action = SummaryConstants.ACTION_TRIGGER_SUMMARY
            putExtra(SummaryConstants.EXTRA_TRIGGER_SOURCE, source)
        }

        ContextCompat.startForegroundService(context.applicationContext, startIntent)
        InAppLogger.log("SummaryTriggerReceiver", "SummaryExecutionService started from source=$source")
    }
}
