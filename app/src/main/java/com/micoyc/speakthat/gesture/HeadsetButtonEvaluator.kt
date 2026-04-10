package com.micoyc.speakthat.gesture

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.util.Log
import android.view.KeyEvent

class HeadsetButtonEvaluator {

    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    fun startSession(context: Context, onStopAction: () -> Unit) {
        if (mediaSession != null) {
            stopSession()
        }

        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Request Audio Focus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .build()
            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
            audioFocusRequest = null
            audioManager = null

            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
            Log.d("HeadsetButtonEvaluator", "MediaSession stopped and released")
        } catch (e: Exception) {
            Log.e("HeadsetButtonEvaluator", "Error stopping MediaSession", e)
        }
    }
}
