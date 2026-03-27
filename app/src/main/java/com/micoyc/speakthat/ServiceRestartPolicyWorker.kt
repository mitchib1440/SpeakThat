/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0.
 * SpeakThat! Copyright © Mitchell Bell
 */

package com.micoyc.speakthat

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ServiceRestartPolicyWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(
            NotificationReaderService.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        if (ServiceRestartPolicy.readPolicy(prefs) != ServiceRestartPolicy.VALUE_PERIODIC) {
            return Result.success()
        }
        NotificationListenerRecovery.requestRebind(
            applicationContext,
            "periodic_restart_policy",
            false
        )
        return Result.success()
    }
}
