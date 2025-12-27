package com.micoyc.speakthat.donations

import android.app.Activity

/**
 * Play flavor override: provides the Billing-backed donation manager.
 */
object DonationManagerProvider {
    fun get(activity: Activity): DonationManager = PlayDonationManager(activity.applicationContext)
}

