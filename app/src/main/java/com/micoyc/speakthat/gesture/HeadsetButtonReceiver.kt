package com.micoyc.speakthat.gesture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class HeadsetButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            Log.d("HeadsetButtonReceiver", "Received ACTION_MEDIA_BUTTON intent")
            // The actual button press handling is done in MediaSession.Callback.onMediaButtonEvent
        }
    }
}
