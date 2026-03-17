/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.content.Context

object UpdateAvailabilityCache {
    fun save(context: Context, updateInfo: UpdateManager.UpdateInfo) {
        UpdateManager.getInstance(context).cacheUpdateInfo(updateInfo)
    }

    fun get(context: Context): UpdateManager.UpdateInfo? {
        return UpdateManager.getInstance(context).getCachedUpdateInfo()
    }

    fun clear(context: Context) {
        UpdateManager.getInstance(context).clearCachedUpdateInfo()
    }
}

