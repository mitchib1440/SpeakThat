package com.micoyc.speakthat

import android.app.Application

class SpeakThatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.ENABLE_AUTO_UPDATER && BuildConfig.DISTRIBUTION_CHANNEL == "github") {
            UpdateFeature.onAppStart(this)
        }
    }
}

