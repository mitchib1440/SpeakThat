package com.micoyc.speakthat.donations

import android.app.Activity

/**
 * Store flavor: no Play Billing. Falls back to external links.
 */
object DonationManagerProvider {
    fun get(activity: Activity): DonationManager = NonPlayDonationManager()
}

