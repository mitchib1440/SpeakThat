/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat.donations

import android.app.Activity

/**
 * GitHub flavor: no Play Billing. Falls back to external links.
 */
object DonationManagerProvider {
    fun get(activity: Activity): DonationManager = NonPlayDonationManager()
}

