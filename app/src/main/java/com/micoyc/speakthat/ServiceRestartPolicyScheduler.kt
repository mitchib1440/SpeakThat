/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0.
 * SpeakThat! Copyright © Mitchell Bell
 */

package com.micoyc.speakthat

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ServiceRestartPolicyScheduler {

    private const val UNIQUE_WORK_NAME = "service_restart_policy_periodic"

    @JvmStatic
    fun syncPeriodicWork(context: Context, policy: String) {
        val appContext = context.applicationContext
        val wm = WorkManager.getInstance(appContext)
        if (policy == ServiceRestartPolicy.VALUE_PERIODIC) {
            val request = PeriodicWorkRequestBuilder<ServiceRestartPolicyWorker>(
                6,
                TimeUnit.HOURS
            ).build()
            wm.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            wm.cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
