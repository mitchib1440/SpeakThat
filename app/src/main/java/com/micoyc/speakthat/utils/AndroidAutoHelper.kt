/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat.utils

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.micoyc.speakthat.InAppLogger

class AndroidAutoHelper(private val context: Context) {

    private var isConnectedToAndroidAuto = false
    private var connectionObserver: ContentObserver? = null

    companion object {
        private const val ANDROID_AUTO_CONNECTION_URI = "content://com.google.android.projection.gearhead.carconnection.provider/connection_state"
    }

    fun initialize() {
        val uri = Uri.parse(ANDROID_AUTO_CONNECTION_URI)
        val handler = Handler(Looper.getMainLooper())

        connectionObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                checkConnectionState()
            }
        }

        try {
            connectionObserver?.let {
                context.contentResolver.registerContentObserver(uri, true, it)
            }
            // Initial check
            checkConnectionState()
        } catch (e: Exception) {
            InAppLogger.logError("AndroidAutoHelper", "Failed to initialize Android Auto observer: ${e.message}")
        }
    }

    private fun checkConnectionState() {
        val uri = Uri.parse(ANDROID_AUTO_CONNECTION_URI)
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val connectionStateIndex = it.getColumnIndex("CarConnectionState")
                    if (connectionStateIndex != -1) {
                        val connectionState = it.getInt(connectionStateIndex)
                        // CarConnection.CONNECTION_TYPE_PROJECTION is 1
                        val wasConnected = isConnectedToAndroidAuto
                        isConnectedToAndroidAuto = (connectionState == 1)
                        if (wasConnected != isConnectedToAndroidAuto) {
                            InAppLogger.log("AndroidAutoHelper", "Android Auto connection state changed: $isConnectedToAndroidAuto")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            InAppLogger.logError("AndroidAutoHelper", "Failed to query Android Auto connection state: ${e.message}")
        }
    }

    fun isConnected(): Boolean {
        return isConnectedToAndroidAuto
    }

    fun cleanup() {
        try {
            connectionObserver?.let {
                context.contentResolver.unregisterContentObserver(it)
            }
            connectionObserver = null
        } catch (e: Exception) {
            InAppLogger.logError("AndroidAutoHelper", "Failed to cleanup Android Auto observer: ${e.message}")
        }
    }
}
