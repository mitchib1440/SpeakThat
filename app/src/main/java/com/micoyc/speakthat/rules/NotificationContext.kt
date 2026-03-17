/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

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
