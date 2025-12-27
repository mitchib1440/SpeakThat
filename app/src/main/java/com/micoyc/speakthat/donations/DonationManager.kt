package com.micoyc.speakthat.donations

import android.app.Activity

/**
 * Entry point for donation flows. Implementations live per flavor.
 * - Play flavor uses Google Play Billing.
 * - Other flavors fall back to external donation links.
 */
interface DonationManager {
    /**
     * Launch the donation UI. Non-Play variants should immediately run [fallback].
     */
    fun showDonate(activity: Activity, fallback: () -> Unit)

    /**
     * Returns the local badge/donation count (if supported by the flavor).
     */
    fun getDonationBadgeCount(): Int
}

class NonPlayDonationManager : DonationManager {
    override fun showDonate(activity: Activity, fallback: () -> Unit) {
        // Non-Play builds just use the existing external donation dialog.
        fallback()
    }

    override fun getDonationBadgeCount(): Int = 0
}

