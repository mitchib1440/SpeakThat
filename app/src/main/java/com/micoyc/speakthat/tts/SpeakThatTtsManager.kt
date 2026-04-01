package com.micoyc.speakthat.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.micoyc.speakthat.VoiceSettingsActivity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object SpeakThatTtsManager {

    private const val VOICE_PREFS_NAME = "VoiceSettings"
    private const val KEY_TTS_ENGINE = "tts_engine_package"

    fun interface InitCallback {
        fun onInit(status: Int)
    }

    interface TtsCallback {
        fun onStart(utteranceId: String?)
        fun onDone(utteranceId: String?)
        fun onError(utteranceId: String?)
        fun onStop(utteranceId: String?, interrupted: Boolean)
    }

    private val lock = Any()
    private val callbackRouter = ConcurrentHashMap<String, TtsCallback>()
    private val pendingInitCallbacks = mutableListOf<InitCallback>()
    private val utteranceCounter = AtomicLong(0L)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var isInitialized = false

    @Volatile
    private var isInitializing = false

    @Volatile
    private var activeEnginePackage: String = ""

    private val masterListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            callbackRouter[utteranceId]?.onStart(utteranceId)
        }

        override fun onDone(utteranceId: String?) {
            callbackRouter.remove(utteranceId)?.onDone(utteranceId)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            callbackRouter.remove(utteranceId)?.onError(utteranceId)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            callbackRouter.remove(utteranceId)?.onError(utteranceId)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            callbackRouter.remove(utteranceId)?.onStop(utteranceId, interrupted)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun initIfNeeded(context: Context, forceReinit: Boolean = false, callback: InitCallback? = null) {
        val appCtx = context.applicationContext
        synchronized(lock) {
            appContext = appCtx
            callback?.let { pendingInitCallbacks.add(it) }

            if (!forceReinit && isInitialized && tts != null) {
                dispatchInitResultLocked(TextToSpeech.SUCCESS)
                return
            }
            if (isInitializing) {
                return
            }

            if (forceReinit) {
                internalShutdownLocked()
            }

            isInitializing = true
            val preferredEnginePackage = resolvePreferredEnginePackage(appCtx)
            activeEnginePackage = preferredEnginePackage
            tts = TextToSpeech(
                appCtx,
                { status -> handleEngineInitResult(status) },
                preferredEnginePackage
            )
        }
    }

    @JvmStatic
    @JvmOverloads
    fun speak(
        context: Context,
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        params: Bundle? = null,
        utteranceId: String? = null,
        callback: TtsCallback? = null
    ): Int {
        initIfNeeded(context)
        val resolvedUtteranceId = utteranceId ?: newUtteranceId("speech")
        callback?.let { callbackRouter[resolvedUtteranceId] = it }
        val engine = tts ?: return TextToSpeech.ERROR
        val result = engine.speak(text, queueMode, params, resolvedUtteranceId)
        if (result == TextToSpeech.ERROR) {
            callbackRouter.remove(resolvedUtteranceId)
        }
        return result
    }

    @JvmStatic
    @JvmOverloads
    fun playEarcon(
        context: Context,
        earcon: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        params: Bundle? = null,
        utteranceId: String? = null,
        callback: TtsCallback? = null
    ): Int {
        initIfNeeded(context)
        val resolvedUtteranceId = utteranceId ?: newUtteranceId("earcon")
        callback?.let { callbackRouter[resolvedUtteranceId] = it }
        val engine = tts ?: return TextToSpeech.ERROR
        val result = engine.playEarcon(earcon, queueMode, params, resolvedUtteranceId)
        if (result == TextToSpeech.ERROR) {
            callbackRouter.remove(resolvedUtteranceId)
        }
        return result
    }

    @JvmStatic
    @JvmOverloads
    fun playSilentUtterance(durationMs: Long, queueMode: Int = TextToSpeech.QUEUE_ADD, utteranceId: String? = null): Int {
        val engine = tts ?: return TextToSpeech.ERROR
        val resolvedUtteranceId = utteranceId ?: newUtteranceId("silence")
        return engine.playSilentUtterance(durationMs, queueMode, resolvedUtteranceId)
    }

    @JvmStatic
    fun stop() {
        tts?.stop()
    }

    @JvmStatic
    fun shutdown() {
        synchronized(lock) {
            internalShutdownLocked()
        }
    }

    @JvmStatic
    fun refreshEngineIfPreferenceChanged(context: Context, callback: InitCallback? = null) {
        val appCtx = context.applicationContext
        val preferredEnginePackage = resolvePreferredEnginePackage(appCtx)
        if (preferredEnginePackage == activeEnginePackage && tts != null) {
            callback?.onInit(TextToSpeech.SUCCESS)
            return
        }
        initIfNeeded(appCtx, true, callback)
    }

    @JvmStatic
    fun getTextToSpeech(): TextToSpeech? = tts

    @JvmStatic
    fun isReady(): Boolean = isInitialized && tts != null

    @JvmStatic
    fun getActiveEnginePackage(): String = activeEnginePackage

    @JvmStatic
    fun setAudioAttributes(audioAttributes: AudioAttributes) {
        tts?.setAudioAttributes(audioAttributes)
    }

    @JvmStatic
    fun applyVoiceSettings(context: Context) {
        val engine = tts ?: return
        val prefs = context.applicationContext.getSharedPreferences(VOICE_PREFS_NAME, Context.MODE_PRIVATE)
        VoiceSettingsActivity.applyVoiceSettings(engine, prefs)
    }

    @JvmStatic
    fun addEarcon(earcon: String, earconUri: android.net.Uri): Int {
        val engine = tts ?: return TextToSpeech.ERROR
        return engine.addEarcon(earcon, earconUri)
    }

    private fun handleEngineInitResult(status: Int) {
        synchronized(lock) {
            isInitializing = false
            isInitialized = status == TextToSpeech.SUCCESS
            if (status == TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(masterListener)
            } else {
                callbackRouter.clear()
            }
            dispatchInitResultLocked(status)
        }
    }

    private fun dispatchInitResultLocked(status: Int) {
        if (pendingInitCallbacks.isEmpty()) {
            return
        }
        val callbacks = pendingInitCallbacks.toList()
        pendingInitCallbacks.clear()
        callbacks.forEach { callback -> callback.onInit(status) }
    }

    private fun resolvePreferredEnginePackage(context: Context): String {
        val prefs = context.getSharedPreferences(VOICE_PREFS_NAME, Context.MODE_PRIVATE)
        val preferred = prefs.getString(KEY_TTS_ENGINE, "").orEmpty().trim()
        if (preferred.isNotEmpty()) {
            return preferred
        }
        val systemDefault = Settings.Secure.getString(context.contentResolver, "tts_default_synth")
            ?.trim()
            .orEmpty()
        return systemDefault
    }

    private fun internalShutdownLocked() {
        try {
            tts?.stop()
            tts?.shutdown()
        } finally {
            tts = null
            isInitialized = false
            isInitializing = false
            callbackRouter.clear()
            activeEnginePackage = ""
        }
    }

    private fun newUtteranceId(prefix: String): String {
        return "${prefix}_${utteranceCounter.incrementAndGet()}"
    }
}
