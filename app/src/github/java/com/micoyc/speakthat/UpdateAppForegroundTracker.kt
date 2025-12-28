package com.micoyc.speakthat

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean

object UpdateAppForegroundTracker : DefaultLifecycleObserver {
    private val isForeground = AtomicBoolean(false)

    fun init(application: Application) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun isAppInForeground(): Boolean = isForeground.get()

    override fun onStart(owner: LifecycleOwner) {
        isForeground.set(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        isForeground.set(false)
    }
}

