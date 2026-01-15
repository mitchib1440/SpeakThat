package com.micoyc.speakthat.rules

import android.os.Bundle

/**
 * Per-notification context passed into the rules engine.
 * Keep this lightweight and non-serializable by default.
 */
data class NotificationContext(
    val packageName: String,
    val title: CharSequence?,
    val text: CharSequence?,
    val bigText: CharSequence?,
    val ticker: CharSequence?,
    val category: String?,
    val channelId: String?,
    val isOngoing: Boolean,
    val postTime: Long,
    val extras: Bundle? = null
)
