package com.micoyc.speakthat.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.MasterSwitchController

/**
 * Receives enable/disable intents from trusted automation apps when the user
 * has enabled External Automation mode.
 */
class AutomationBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appContext = context.applicationContext
        val modeManager = AutomationModeManager(appContext)
        val currentMode = modeManager.getMode()

        if (currentMode != AutomationMode.EXTERNAL_AUTOMATION) {
            InAppLogger.log(
                "AutomationReceiver",
                "Ignored $action - External Automation mode is not active"
            )
            return
        }

        val source = intent.getStringExtra("source") ?: intent.`package` ?: "unknown"
        when (action) {
            AutomationModeManager.ACTION_ENABLE_SPEAKTHAT -> {
                MasterSwitchController.setEnabled(appContext, true, "AutomationIntent:$source")
                InAppLogger.logUserAction("Automation intent toggled SpeakThat ON", "source=$source")
            }
            AutomationModeManager.ACTION_DISABLE_SPEAKTHAT -> {
                MasterSwitchController.setEnabled(appContext, false, "AutomationIntent:$source")
                InAppLogger.logUserAction("Automation intent toggled SpeakThat OFF", "source=$source")
            }
            else -> {
                InAppLogger.log(
                    "AutomationReceiver",
                    "Unknown automation action received: $action from $source"
                )
            }
        }
    }
}

