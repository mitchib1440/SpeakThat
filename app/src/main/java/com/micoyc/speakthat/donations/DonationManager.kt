/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

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

