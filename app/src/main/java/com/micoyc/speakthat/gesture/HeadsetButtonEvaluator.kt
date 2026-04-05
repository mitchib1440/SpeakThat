package com.micoyc.speakthat.gesture

import android.content.Context
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.util.Log
import android.view.KeyEvent

class HeadsetButtonEvaluator {

    private var mediaSession: MediaSession? = null

    fun startSession(context: Context, onStopAction: () -> Unit) {
        if (mediaSession != null) {
            stopSession()
        }

        try {
            mediaSession = MediaSession(context, "SpeakThatHeadsetButtonEvaluator").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
                
                val state = PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_PAUSE
                    )
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                    .build()
                setPlaybackState(state)

                setCallback(object : MediaSession.Callback() {
                    override fun onMediaButtonEvent(mediaButtonIntent: android.content.Intent): Boolean {
                        val keyEvent = mediaButtonIntent.getParcelableExtra<KeyEvent>(android.content.Intent.EXTRA_KEY_EVENT)
                        if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                            val keyCode = keyEvent.keyCode
                            Log.d("HeadsetButtonEvaluator", "Media button pressed: $keyCode")
                            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                                keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                                keyCode == KeyEvent.KEYCODE_MEDIA_STOP ||
                                keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                                
                                Log.d("HeadsetButtonEvaluator", "Target media button detected, triggering stop action")
                                onStopAction()
                                stopSession()
                                return true
                            }
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                })
                
                isActive = true
                Log.d("HeadsetButtonEvaluator", "MediaSession started")
            }
        } catch (e: Exception) {
            Log.e("HeadsetButtonEvaluator", "Error starting MediaSession", e)
        }
    }

    fun stopSession() {
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
            Log.d("HeadsetButtonEvaluator", "MediaSession stopped and released")
        } catch (e: Exception) {
            Log.e("HeadsetButtonEvaluator", "Error stopping MediaSession", e)
        }
    }
}
