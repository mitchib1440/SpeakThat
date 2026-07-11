/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import com.micoyc.speakthat.InAppLogger

class AndroidAutoHelper(private val context: Context) {

    private var isConnectedToAndroidAuto = false
    private var connectionObserver: Observer<Int>? = null
    private val carConnection = CarConnection(context)

    fun initialize() {
        // Must be called on the main thread to avoid IllegalStateException from LiveData
        Handler(Looper.getMainLooper()).post {
            try {
                connectionObserver = Observer { connectionType ->
                    val wasConnected = isConnectedToAndroidAuto
                    isConnectedToAndroidAuto = (connectionType == CarConnection.CONNECTION_TYPE_PROJECTION)
                    if (wasConnected != isConnectedToAndroidAuto) {
                        InAppLogger.log("AndroidAutoHelper", "Android Auto connection state changed: $isConnectedToAndroidAuto")
                    }
                }
                connectionObserver?.let {
                    carConnection.type.observeForever(it)
                }
            } catch (e: Exception) {
                InAppLogger.logError("AndroidAutoHelper", "Failed to initialize Android Auto observer: ${e.message}")
            }
        }
    }

    fun isConnected(): Boolean {
        return isConnectedToAndroidAuto
    }

    fun cleanup() {
        Handler(Looper.getMainLooper()).post {
            try {
                connectionObserver?.let {
                    carConnection.type.removeObserver(it)
                }
                connectionObserver = null
            } catch (e: Exception) {
                InAppLogger.logError("AndroidAutoHelper", "Failed to cleanup Android Auto observer: ${e.message}")
            }
        }
    }
}
