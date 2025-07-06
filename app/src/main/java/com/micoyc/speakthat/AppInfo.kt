package com.micoyc.speakthat

import android.graphics.drawable.Drawable

data class AppInfo(
    @JvmField val appName: String,
    @JvmField val packageName: String,
    @JvmField val icon: Drawable?
) {
    override fun toString(): String {
        return "$appName ($packageName)"
    }
} 