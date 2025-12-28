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

