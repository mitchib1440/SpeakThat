package com.micoyc.speakthat.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.micoyc.speakthat.InAppLogger
import java.util.concurrent.atomic.AtomicBoolean

class ScoAudioManager {

    private var isScoActive = false
    private var scoReceiver: BroadcastReceiver? = null
    private var timeoutRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    fun requestScoAndPlay(context: Context, audioManager: AudioManager, onReadyToSpeak: () -> Unit) {
        if (!BluetoothConnectionHelper.isCurrentDeviceConfiguredForSco(context)) {
            onReadyToSpeak()
            return
        }

        val hasTriggered = AtomicBoolean(false)

        try {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            isScoActive = true
            InAppLogger.logDebug("ScoAudioManager", "Started Bluetooth SCO")
        } catch (e: Exception) {
            InAppLogger.logError("ScoAudioManager", "Failed to start Bluetooth SCO: ${e.message}")
            if (hasTriggered.compareAndSet(false, true)) {
                onReadyToSpeak()
            }
            return
        }

        scoReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED == intent.action) {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)
                    if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                        InAppLogger.logDebug("ScoAudioManager", "SCO Audio State: CONNECTED")
                        unregisterReceiverSafe(context)
                        timeoutRunnable?.let { handler.removeCallbacks(it) }
                        if (hasTriggered.compareAndSet(false, true)) {
                            onReadyToSpeak()
                        }
                    } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                        InAppLogger.logDebug("ScoAudioManager", "SCO Audio State: DISCONNECTED")
                    } else if (state == AudioManager.SCO_AUDIO_STATE_CONNECTING) {
                        InAppLogger.logDebug("ScoAudioManager", "SCO Audio State: CONNECTING")
                    }
                }
            }
        }

        try {
            context.registerReceiver(scoReceiver, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED))
        } catch (e: Exception) {
            InAppLogger.logError("ScoAudioManager", "Failed to register SCO receiver: ${e.message}")
        }

        timeoutRunnable = Runnable {
            InAppLogger.logWarning("ScoAudioManager", "SCO connection timed out after 3000ms")
            unregisterReceiverSafe(context)
            if (hasTriggered.compareAndSet(false, true)) {
                onReadyToSpeak()
            }
        }

        handler.postDelayed(timeoutRunnable!!, 3000)
    }

    fun cleanupSco(context: Context, audioManager: AudioManager) {
        unregisterReceiverSafe(context)
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null

        if (isScoActive) {
            try {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                isScoActive = false
                InAppLogger.logDebug("ScoAudioManager", "Stopped Bluetooth SCO")
            } catch (e: Exception) {
                InAppLogger.logError("ScoAudioManager", "Failed to stop Bluetooth SCO: ${e.message}")
            }
        }
    }

    private fun unregisterReceiverSafe(context: Context) {
        scoReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore if not registered
            }
            scoReceiver = null
        }
    }
}
