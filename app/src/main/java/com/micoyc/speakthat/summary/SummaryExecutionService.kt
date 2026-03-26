package com.micoyc.speakthat.summary

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.os.BundleCompat
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.MainActivity
import com.micoyc.speakthat.NotificationReaderService
import com.micoyc.speakthat.R
import com.micoyc.speakthat.VoiceSettingsActivity
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.random.Random

/**
 * Phase 3 execution service for summaries.
 * Adds TTS sequencing + overlay synchronization on top of the Phase 2 overlay/data pipeline.
 */
class SummaryExecutionService : Service(), TextToSpeech.OnInitListener, ComponentCallbacks {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var contentContainer: View? = null
    private var paginationText: TextView? = null
    private var appIconView: ImageView? = null
    private var appTimeSubtitleText: TextView? = null
    private var senderText: TextView? = null
    private var messageText: TextView? = null
    private var notificationImageCard: View? = null
    private var notificationImageView: ImageView? = null
    private var loadingContainer: View? = null
    private var loadingLineText: TextView? = null
    private var touchStartX: Float = 0f
    private var touchStartY: Float = 0f

    private val mainHandler = Handler(Looper.getMainLooper())
    private val summaryItems = mutableListOf<SummaryItem>()
    private var currentIndex = 0
    private var notificationCountForGreeting = 0

    private var textToSpeech: TextToSpeech? = null
    @Volatile
    private var isTtsReady = false
    @Volatile
    private var pendingQueueStart = false
    @Volatile
    private var isUserSwiping = false
    @Volatile
    private var currentSpeechSession = 0
    @Volatile
    private var activeSwipeRecoverySession = -1
    @Volatile
    private var isServiceStopping = false
    @Volatile
    private var hasSpokenInitialGreeting = false

    private val utteranceIndexMap = ConcurrentHashMap<String, Int>()
    private val utteranceTypeMap = ConcurrentHashMap<String, String>()
    private val utteranceSessionMap = ConcurrentHashMap<String, Int>()

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var audioFocusRequest: AudioFocusRequest? = null
    @Volatile
    private var hasAudioFocus = false
    private var transitionGeneration = 0L

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var buildItemsJob: Job? = null
    private var lastOrientation: Int = Configuration.ORIENTATION_UNDEFINED

    override fun onCreate() {
        super.onCreate()
        registerComponentCallbacks(this)
        val initialOrientation = resources.configuration.orientation
        lastOrientation = if (initialOrientation != Configuration.ORIENTATION_UNDEFINED) {
            initialOrientation
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }
        ensureNotificationChannel()
        ensureCacheDirExists()
        initializeTextToSpeech()
        InAppLogger.log(TAG, "SummaryExecutionService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: SummaryConstants.ACTION_TRIGGER_SUMMARY
        when (action) {
            SummaryConstants.ACTION_STOP_SUMMARY -> {
                requestGracefulStop("notification_action_stop")
                return START_NOT_STICKY
            }
            SummaryConstants.ACTION_SKIP_CURRENT_NOTIFICATION -> {
                skipCurrentFromNotificationAction()
                return START_NOT_STICKY
            }
        }
        val source = intent?.getStringExtra(SummaryConstants.EXTRA_TRIGGER_SOURCE) ?: "unknown"

        val notification = buildForegroundNotification(source)
        startForegroundCompat(notification)

        if (!canDrawOverlaysCompat()) {
            InAppLogger.logWarning(TAG, "Overlay permission missing; stopping summary execution safely")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val overlayAdded = ensureOverlayAttached()
        if (!overlayAdded) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        buildAndRenderSummaryItemsAsync(source, action)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isServiceStopping = true
        pendingQueueStart = false
        buildItemsJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        unregisterComponentCallbacks(this)
        stopAndReleaseTextToSpeech()
        abandonAudioFocusIfHeld()
        serviceScope.cancel()
        removeOverlayIfAttached()
        clearSummaryCache()
        super.onDestroy()
        stopForegroundCompat()
        InAppLogger.log(TAG, "SummaryExecutionService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newOrientation = newConfig.orientation
        if (newOrientation == Configuration.ORIENTATION_UNDEFINED || newOrientation == lastOrientation) {
            return
        }

        lastOrientation = newOrientation
        if (overlayView == null) {
            return
        }

        removeOverlayIfAttached()
        val attached = ensureOverlayAttached()
        if (!attached) {
            return
        }

        if (summaryItems.isNotEmpty()) {
            loadingContainer?.visibility = View.GONE
            contentContainer?.visibility = View.VISIBLE
            contentContainer?.alpha = 1f
        }
        renderCurrentCard()
    }

    override fun onLowMemory() {
        InAppLogger.logWarning(TAG, "System low memory callback received while summary overlay is active")
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            InAppLogger.logError(TAG, "TextToSpeech init failed (status=$status)")
            requestGracefulStop("tts_init_failed")
            return
        }

        val tts = textToSpeech
        if (tts == null) {
            InAppLogger.logError(TAG, "TTS init callback arrived with null instance")
            requestGracefulStop("tts_instance_null")
            return
        }

        configureTextToSpeech(tts)
        registerUtteranceProgressListener(tts)
        isTtsReady = true
        InAppLogger.log(TAG, "TextToSpeech initialized successfully")

        if (pendingQueueStart && !isServiceStopping) {
            pendingQueueStart = false
            startSummarySpeechFromCurrentIndex(triggeredBySwipe = false)
        }
    }

    private fun initializeTextToSpeech() {
        if (textToSpeech != null) {
            return
        }
        isTtsReady = false
        textToSpeech = TextToSpeech(this, this)
    }

    private fun configureTextToSpeech(tts: TextToSpeech) {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts.setAudioAttributes(attrs)
        } catch (e: Exception) {
            InAppLogger.logError(TAG, "Failed to set TTS audio attributes: ${e.message}")
        }

        val prefs: SharedPreferences = getSharedPreferences("VoiceSettings", MODE_PRIVATE)
        VoiceSettingsActivity.applyVoiceSettings(tts, prefs)
    }

    private fun registerUtteranceProgressListener(tts: TextToSpeech) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val id = utteranceId ?: return
                val session = utteranceSessionMap[id] ?: return
                if (session != currentSpeechSession) {
                    return
                }

                val type = utteranceTypeMap[id] ?: return
                if (type == SummaryConstants.UTTERANCE_PREFIX_INTRO && activeSwipeRecoverySession == session) {
                    isUserSwiping = false
                    activeSwipeRecoverySession = -1
                }

                if (type == SummaryConstants.UTTERANCE_PREFIX_ITEM) {
                    val index = utteranceIndexMap[id] ?: return
                    mainHandler.post {
                        if (isServiceStopping || session != currentSpeechSession) {
                            return@post
                        }
                        val targetIndex = index.coerceIn(0, summaryItems.lastIndex)
                        if (targetIndex == currentIndex) {
                            renderCurrentCard()
                            return@post
                        }

                        val isNext = targetIndex > currentIndex
                        animateContentSwap(targetIndex = targetIndex, isNext = isNext)
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                val id = utteranceId ?: return
                val session = utteranceSessionMap[id] ?: return
                if (session != currentSpeechSession) {
                    return
                }

                val type = utteranceTypeMap[id] ?: return
                val index = utteranceIndexMap[id] ?: -1

                if (isUserSwiping && session != activeSwipeRecoverySession) {
                    return
                }

                val isFinalItem = type == SummaryConstants.UTTERANCE_PREFIX_ITEM &&
                    index == summaryItems.lastIndex &&
                    summaryItems.isNotEmpty()
                val isOutro = type == SummaryConstants.UTTERANCE_PREFIX_OUTRO

                if (isFinalItem || isOutro) {
                    mainHandler.post {
                        if (!isServiceStopping && session == currentSpeechSession) {
                            requestGracefulStop("tts_queue_completed")
                        }
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                val id = utteranceId ?: return
                val session = utteranceSessionMap[id] ?: return
                if (session != currentSpeechSession) {
                    return
                }
                if (isUserSwiping && session != activeSwipeRecoverySession) {
                    return
                }
                mainHandler.post {
                    if (!isServiceStopping && session == currentSpeechSession) {
                        requestGracefulStop("tts_error")
                    }
                }
            }
        })
    }

    private fun startSummarySpeechFromCurrentIndex(triggeredBySwipe: Boolean) {
        if (isServiceStopping) {
            return
        }
        val tts = textToSpeech
        if (!isTtsReady || tts == null) {
            pendingQueueStart = true
            return
        }

        requestAudioFocusForSpeech()

        if (triggeredBySwipe) {
            isUserSwiping = true
        }

        currentSpeechSession += 1
        val sessionId = currentSpeechSession
        if (triggeredBySwipe) {
            activeSwipeRecoverySession = sessionId
        } else {
            activeSwipeRecoverySession = -1
            isUserSwiping = false
        }

        utteranceIndexMap.clear()
        utteranceTypeMap.clear()
        utteranceSessionMap.clear()

        try {
            tts.stop()
        } catch (e: Exception) {
            InAppLogger.logError(TAG, "Failed to stop existing TTS queue: ${e.message}")
        }

        val shouldSpeakIntro = !hasSpokenInitialGreeting && !triggeredBySwipe && currentIndex == 0
        if (shouldSpeakIntro) {
            val introId = buildUtteranceId(sessionId, SummaryConstants.UTTERANCE_PREFIX_INTRO, -1)
            enqueueSpeech(
                text = buildIntroGreetingText(),
                queueMode = TextToSpeech.QUEUE_FLUSH,
                utteranceId = introId,
                type = SummaryConstants.UTTERANCE_PREFIX_INTRO,
                index = -1,
                sessionId = sessionId
            )
            hasSpokenInitialGreeting = true
        }

        if (notificationCountForGreeting <= 0 || summaryItems.isEmpty()) {
            val outroId = buildUtteranceId(sessionId, SummaryConstants.UTTERANCE_PREFIX_OUTRO, -1)
            enqueueSpeech(
                text = getString(R.string.summary_tts_no_notifications),
                queueMode = if (shouldSpeakIntro) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH,
                utteranceId = outroId,
                type = SummaryConstants.UTTERANCE_PREFIX_OUTRO,
                index = -1,
                sessionId = sessionId
            )
            return
        }

        val pauseGapMs = readSummaryPauseGapMs()
        for (index in currentIndex until summaryItems.size) {
            val item = summaryItems[index]
            val itemId = buildUtteranceId(sessionId, SummaryConstants.UTTERANCE_PREFIX_ITEM, index)
            enqueueSpeech(
                text = buildItemSpeechText(item),
                queueMode = if (!shouldSpeakIntro && index == currentIndex) {
                    TextToSpeech.QUEUE_FLUSH
                } else {
                    TextToSpeech.QUEUE_ADD
                },
                utteranceId = itemId,
                type = SummaryConstants.UTTERANCE_PREFIX_ITEM,
                index = index,
                sessionId = sessionId
            )

            if (index < summaryItems.lastIndex) {
                val pauseId = buildUtteranceId(sessionId, SummaryConstants.UTTERANCE_PREFIX_PAUSE, index)
                utteranceTypeMap[pauseId] = SummaryConstants.UTTERANCE_PREFIX_PAUSE
                utteranceIndexMap[pauseId] = index
                utteranceSessionMap[pauseId] = sessionId
                tts.playSilentUtterance(
                    pauseGapMs,
                    TextToSpeech.QUEUE_ADD,
                    pauseId
                )
            }
        }
    }

    private fun enqueueSpeech(
        text: String,
        queueMode: Int,
        utteranceId: String,
        type: String,
        index: Int,
        sessionId: Int
    ) {
        val tts = textToSpeech ?: return
        utteranceTypeMap[utteranceId] = type
        utteranceIndexMap[utteranceId] = index
        utteranceSessionMap[utteranceId] = sessionId
        tts.speak(text, queueMode, null, utteranceId)
    }

    private fun buildUtteranceId(sessionId: Int, prefix: String, index: Int): String {
        return "summary_${sessionId}_${prefix}_${index}"
    }

    private fun requestAudioFocusForSpeech() {
        if (hasAudioFocus) {
            return
        }

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
            }
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }

        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasAudioFocus) {
            InAppLogger.logWarning(TAG, "Audio focus was not granted; continuing playback")
        }
    }

    private fun abandonAudioFocusIfHeld() {
        if (!hasAudioFocus) {
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            InAppLogger.logError(TAG, "Failed to abandon audio focus: ${e.message}")
        } finally {
            hasAudioFocus = false
        }
    }

    private fun stopAndReleaseTextToSpeech() {
        try {
            textToSpeech?.setOnUtteranceProgressListener(null)
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            InAppLogger.logError(TAG, "Error shutting down TTS: ${e.message}")
        } finally {
            textToSpeech = null
            isTtsReady = false
        }
    }

    private fun requestGracefulStop(reason: String) {
        if (isServiceStopping) {
            return
        }
        isServiceStopping = true
        pendingQueueStart = false
        InAppLogger.log(TAG, "Stopping summary execution: $reason")

        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            InAppLogger.logError(TAG, "Failed to stop TTS during shutdown: ${e.message}")
        }
        abandonAudioFocusIfHeld()
        stopSelf()
    }

    private fun buildIntroGreetingText(): String {
        val dayPeriod = resolveDayPeriodString()
        val currentTime = DateFormat.getTimeFormat(this).format(Date(System.currentTimeMillis()))
        val batteryPercent = readBatteryPercent()
        val greetingName = readSummaryGreetingName()
        val countText = resources.getQuantityString(
            R.plurals.summary_tts_notification_count,
            notificationCountForGreeting,
            notificationCountForGreeting
        )
        return getString(
            R.string.summary_tts_intro_template,
            dayPeriod,
            greetingName,
            currentTime,
            batteryPercent,
            countText
        )
    }

    private fun readSummaryGreetingName(): String {
        val name = getSummarySettingsPrefs()
            .getString(SUMMARY_SETTINGS_KEY_GREETING_NAME, "Human")
            ?.trim()
            .orEmpty()
        return if (name.isBlank()) "Human" else name
    }

    private fun readSummaryPauseGapMs(): Long {
        val seconds = getSummarySettingsPrefs()
            .getInt(SUMMARY_SETTINGS_KEY_PAUSE_SECONDS, 2)
            .coerceIn(0, 5)
        return seconds * 1000L
    }

    private fun getSummarySettingsPrefs(): SharedPreferences {
        return getSharedPreferences(SUMMARY_SETTINGS_PREFS_NAME, MODE_PRIVATE)
    }

    private fun resolveDayPeriodString(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> getString(R.string.summary_tts_day_period_morning)
            in 12..17 -> getString(R.string.summary_tts_day_period_afternoon)
            else -> getString(R.string.summary_tts_day_period_evening)
        }
    }

    private fun readBatteryPercent(): Int {
        return try {
            val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
            val value = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (value < 0) 0 else value
        } catch (e: Exception) {
            InAppLogger.logError(TAG, "Failed to read battery percentage: ${e.message}")
            0
        }
    }

    private fun buildItemSpeechText(item: SummaryItem): String {
        val relativeTime = formatRelativeTimeForSpeech(item.postTimeMillis)
        val actualTime = DateFormat.getTimeFormat(this).format(Date(item.postTimeMillis))
        val isPrivate = item.senderText.equals("Private Notification", ignoreCase = true) ||
            item.messageText.contains("private notification", ignoreCase = true)

        return if (isPrivate) {
            // Avoid app-name duplication for private-mode notifications.
            "$relativeTime, at $actualTime, ${item.messageText}"
        } else {
            "$relativeTime, at $actualTime, ${item.appName} notified you: ${item.senderText}. ${item.messageText}"
        }
    }

    private fun formatRelativeTimeForSpeech(postTimeMillis: Long): String {
        return try {
            val locale = currentLocale()
            val formatter = RelativeDateTimeFormatter.getInstance(
                ULocale.forLocale(locale),
                null,
                RelativeDateTimeFormatter.Style.LONG,
                DisplayContext.CAPITALIZATION_NONE
            )

            val deltaMs = (System.currentTimeMillis() - postTimeMillis).coerceAtLeast(0L)
            val minutes = deltaMs / 60_000L
            when {
                minutes < 1L -> formatter.format(
                    1.0,
                    RelativeDateTimeFormatter.Direction.LAST,
                    RelativeDateTimeFormatter.RelativeUnit.MINUTES
                )
                minutes < 60L -> formatter.format(
                    minutes.toDouble(),
                    RelativeDateTimeFormatter.Direction.LAST,
                    RelativeDateTimeFormatter.RelativeUnit.MINUTES
                )
                minutes < 1_440L -> formatter.format(
                    (minutes / 60L).toDouble(),
                    RelativeDateTimeFormatter.Direction.LAST,
                    RelativeDateTimeFormatter.RelativeUnit.HOURS
                )
                minutes < 10_080L -> formatter.format(
                    (minutes / 1_440L).toDouble(),
                    RelativeDateTimeFormatter.Direction.LAST,
                    RelativeDateTimeFormatter.RelativeUnit.DAYS
                )
                minutes < 43_200L -> formatter.format(
                    (minutes / 10_080L).toDouble(),
                    RelativeDateTimeFormatter.Direction.LAST,
                    RelativeDateTimeFormatter.RelativeUnit.WEEKS
                )
                minutes < 525_600L -> formatter.format(
                    (minutes / 43_200L).toDouble(),
                    RelativeDateTimeFormatter.Direction.LAST,
                    RelativeDateTimeFormatter.RelativeUnit.MONTHS
                )
                else -> formatter.format(
                    (minutes / 525_600L).toDouble(),
                    RelativeDateTimeFormatter.Direction.LAST,
                    RelativeDateTimeFormatter.RelativeUnit.YEARS
                )
            }
        } catch (e: Exception) {
            InAppLogger.logError(TAG, "Failed to format relative time: ${e.message}")
            getString(R.string.summary_tts_relative_time_fallback)
        }
    }

    private fun currentLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales[0] ?: Locale.getDefault()
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale ?: Locale.getDefault()
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                SummaryConstants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(SummaryConstants.NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            SummaryConstants.NOTIFICATION_CHANNEL_ID,
            "Ongoing Summary",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Foreground service channel for summary execution"
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(source: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            SummaryConstants.REQUEST_CODE_OPEN_SUMMARY_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopSummaryPendingIntent = PendingIntent.getService(
            this,
            SummaryConstants.REQUEST_CODE_STOP_SUMMARY,
            Intent(this, SummaryExecutionService::class.java).apply {
                action = SummaryConstants.ACTION_STOP_SUMMARY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val skipCurrentPendingIntent = PendingIntent.getService(
            this,
            SummaryConstants.REQUEST_CODE_SKIP_SUMMARY_NOTIFICATION,
            Intent(this, SummaryExecutionService::class.java).apply {
                action = SummaryConstants.ACTION_SKIP_CURRENT_NOTIFICATION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SummaryConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.speakthaticon)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.summary_service_preparing))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.speakthaticon,
                getString(R.string.summary_service_action_stop),
                stopSummaryPendingIntent
            )
            .addAction(
                R.drawable.speakthaticon,
                getString(R.string.summary_service_action_skip),
                skipCurrentPendingIntent
            )
            .build()
    }

    private fun ensureOverlayAttached(): Boolean {
        if (overlayView != null) {
            return true
        }

        return try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val view = LayoutInflater.from(this).inflate(R.layout.overlay_summary_execution, null, false)

            paginationText = view.findViewById(R.id.summaryPaginationText)
            appIconView = view.findViewById(R.id.iv_app_icon)
            appTimeSubtitleText = view.findViewById(R.id.tv_app_time_subtitle)
            senderText = view.findViewById(R.id.tv_sender)
            messageText = view.findViewById(R.id.tv_message)
            notificationImageCard = view.findViewById(R.id.notificationImageCard)
            notificationImageView = view.findViewById(R.id.iv_notification_image)
            contentContainer = view.findViewById(R.id.summaryContentContainer)
            loadingContainer = view.findViewById(R.id.summaryLoadingContainer)
            loadingLineText = view.findViewById(R.id.tv_summary_loading_line)
            val dismissButton: ImageButton = view.findViewById(R.id.summaryDismissButton)
            setRandomLoadingLine()

            dismissButton.setOnClickListener {
                InAppLogger.log(TAG, "Summary overlay dismissed by user")
                requestGracefulStop("dismiss_button")
            }
            attachSwipeListener()

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                @Suppress("DEPRECATION")
                flags = flags or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }

            windowManager?.addView(view, params)
            overlayView = view
            runFirstLaunchBounceAnimationIfNeeded()
            true
        } catch (e: Exception) {
            InAppLogger.logError(TAG, "Failed to attach summary overlay: ${e.message}")
            false
        }
    }

    private fun attachSwipeListener() {
        contentContainer?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - touchStartX
                    val deltaY = event.y - touchStartY
                    val isHorizontalGesture = abs(deltaX) > abs(deltaY)
                    if (isHorizontalGesture && abs(deltaX) >= SummaryConstants.SWIPE_THRESHOLD_PX) {
                        if (deltaX < 0) {
                            showNextCard()
                        } else {
                            showPreviousCard()
                        }
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun buildAndRenderSummaryItemsAsync(source: String, action: String) {
        buildItemsJob?.cancel()
        buildItemsJob = CoroutineScope(Dispatchers.IO).launch {
            val activeNotifications = NotificationReaderService.getActiveNotificationsSnapshot()
            val builtItems = buildSummaryItems(activeNotifications)

            withContext(Dispatchers.Main) {
                if (isServiceStopping) {
                    return@withContext
                }

                animateFromLoadingToContent()
                summaryItems.clear()
                summaryItems.addAll(
                    if (builtItems.isEmpty()) listOf(createEmptySummaryItem()) else builtItems
                )
                notificationCountForGreeting = builtItems.size
                currentIndex = 0
                renderCurrentCard()
                InAppLogger.log(
                    TAG,
                    "SummaryExecutionService running action=$action source=$source items=${summaryItems.size}"
                )
                startSummarySpeechFromCurrentIndex(triggeredBySwipe = false)
            }
        }
    }

    private fun setRandomLoadingLine() {
        val randomNum = Random.nextInt(1, 51)
        val loadingStringRes = resources.getIdentifier("loading_line_$randomNum", "string", packageName)
        val loadingText = if (loadingStringRes != 0) {
            getString(loadingStringRes)
        } else {
            getString(R.string.loading_line_5)
        }
        loadingLineText?.text = loadingText
    }

    private fun animateFromLoadingToContent() {
        val loading = loadingContainer
        val content = contentContainer

        if (content != null) {
            content.visibility = View.VISIBLE
            content.alpha = 0f
            content.animate()
                .alpha(1f)
                .setDuration(220L)
                .start()
        }

        if (loading != null) {
            loading.animate()
                .alpha(0f)
                .setDuration(220L)
                .withEndAction {
                    loading.visibility = View.GONE
                    loading.alpha = 1f
                }
                .start()
        }
    }

    private fun createEmptySummaryItem(): SummaryItem {
        return SummaryItem(
            notificationKey = "empty",
            packageName = packageName,
            appName = getString(R.string.summary_overlay_sender_fallback),
            appTimeSubtitle = getString(R.string.summary_overlay_sender_fallback),
            senderText = getString(R.string.summary_overlay_sender_fallback),
            messageText = getString(R.string.summary_overlay_empty_message),
            notificationImagePath = null,
            postTimeMillis = System.currentTimeMillis()
        )
    }

    private fun buildSummaryItems(active: Array<StatusBarNotification>): List<SummaryItem> {
        val seenFingerprints = HashSet<String>()
        val result = ArrayList<SummaryItem>()
        val notificationOrder = readSummaryNotificationOrder()
        val sortedNotifications = if (notificationOrder == SummaryConstants.ORDER_OLDEST_FIRST) {
            active.sortedBy { it.postTime }
        } else {
            active.sortedByDescending { it.postTime }
        }

        sortedNotifications.forEach { sbn ->
            if (shouldSkipNotification(sbn)) {
                return@forEach
            }

            val appName = resolveDisplayAppName(sbn.packageName)
            val rawSender = extractSender(sbn).ifBlank { appName }
            val rawMessage = extractMessage(sbn)
            if (rawSender.isBlank() && rawMessage.isBlank()) {
                return@forEach
            }

            val filterBridgeResult = NotificationReaderService.applyFiltersForSummary(
                sbn = sbn,
                appName = appName,
                fallbackText = rawMessage
            )
            if (!filterBridgeResult.shouldInclude) {
                return@forEach
            }

            val message = filterBridgeResult.processedText.ifBlank { rawMessage }
            val isPrivate = message.contains("private notification", ignoreCase = true)
            val sender = if (isPrivate) {
                "Private Notification"
            } else {
                rawSender.ifBlank { getString(R.string.summary_overlay_sender_fallback) }
            }
            val appTime = DateFormat.getTimeFormat(this).format(Date(sbn.postTime))
            val subtitle = "$appName \u2022 $appTime"

            val fingerprint = "${sbn.packageName}|${sender.lowercase()}|${message.lowercase()}"
            if (!seenFingerprints.add(fingerprint)) {
                return@forEach
            }

            result.add(
                SummaryItem(
                    notificationKey = sbn.key ?: fingerprint,
                    packageName = sbn.packageName,
                    appName = appName,
                    appTimeSubtitle = subtitle,
                    senderText = sender,
                    messageText = message.ifBlank { getString(R.string.summary_overlay_empty_message) },
                    notificationImagePath = extractBigPicturePathFromNotification(sbn),
                    postTimeMillis = sbn.postTime
                )
            )
        }
        return result
    }

    private fun readSummaryNotificationOrder(): String {
        return getSummarySettingsPrefs().getString(
            SummaryConstants.KEY_NOTIFICATION_ORDER,
            SummaryConstants.ORDER_NEWEST_FIRST
        ) ?: SummaryConstants.ORDER_NEWEST_FIRST
    }

    private fun shouldSkipNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) {
            return true
        }
        if (sbn.packageName.startsWith("com.android.") || sbn.packageName.startsWith("android.")) {
            return true
        }

        val flags = sbn.notification.flags
        val isOngoing = (flags and Notification.FLAG_ONGOING_EVENT) != 0
        val isForegroundService = (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0

        return isOngoing || isForegroundService || isGroupSummary
    }

    private fun extractSender(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras ?: return ""
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty()
        if (conversationTitle.isNotBlank()) {
            return conversationTitle
        }
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        if (title.isNotBlank()) {
            return title
        }
        return resolveDisplayAppName(sbn.packageName)
    }

    private fun extractMessage(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras ?: return ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        if (bigText.isNotBlank()) {
            return bigText
        }
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (text.isNotBlank()) {
            return text
        }
        val summary = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty()
        if (summary.isNotBlank()) {
            return summary
        }
        return extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString().orEmpty()
    }

    private fun extractBigPicturePathFromNotification(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras ?: return null

        val pictureBitmap = BundleCompat.getParcelable(extras, Notification.EXTRA_PICTURE, Bitmap::class.java)
        if (pictureBitmap != null) {
            return persistBitmapAndRecycle(pictureBitmap, sbn, "picture")
        }

        val largeIconBitmap = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            BundleCompat.getParcelable(extras, Notification.EXTRA_LARGE_ICON, Bitmap::class.java)
        } else {
            null
        }
        if (largeIconBitmap != null) {
            return persistBitmapAndRecycle(largeIconBitmap, sbn, "large_icon_bitmap")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationLargeIcon = sbn.notification.getLargeIcon()
            val iconBitmap = iconToBitmap(notificationLargeIcon)
            if (iconBitmap != null) {
                return persistBitmapAndRecycle(iconBitmap, sbn, "notif_large_icon")
            }
        }
        return null
    }

    private fun iconToBitmap(icon: Icon?): Bitmap? {
        if (icon == null) {
            return null
        }
        return try {
            val drawable = icon.loadDrawable(this) ?: return null
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 256
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 256
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            InAppLogger.logError(TAG, "Failed to convert Icon to Bitmap: ${e.message}")
            null
        }
    }

    private fun persistBitmapAndRecycle(bitmap: Bitmap, sbn: StatusBarNotification, kind: String): String? {
        val cacheDir = ensureCacheDirExists()
        val safePackageName = sbn.packageName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val targetFile = File(
            cacheDir,
            "${kind}_${safePackageName}_${sbn.id}_${System.currentTimeMillis()}.jpg"
        )

        return try {
            FileOutputStream(targetFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            InAppLogger.logError(TAG, "Failed to cache notification bitmap: ${e.message}")
            null
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun renderCurrentCard() {
        if (summaryItems.isEmpty()) {
            return
        }
        val card = summaryItems[currentIndex]
        paginationText?.text = "${currentIndex + 1}/${summaryItems.size}"
        appTimeSubtitleText?.text = card.appTimeSubtitle
        senderText?.text = card.senderText
        messageText?.text = card.messageText

        val hasPicture = if (!card.notificationImagePath.isNullOrBlank()) {
            val bitmap = BitmapFactory.decodeFile(card.notificationImagePath)
            if (bitmap != null) {
                notificationImageView?.setImageBitmap(bitmap)
                true
            } else {
                false
            }
        } else {
            false
        }

        if (hasPicture) {
            notificationImageCard?.visibility = View.VISIBLE
            notificationImageView?.visibility = View.VISIBLE
        } else {
            notificationImageCard?.visibility = View.GONE
            notificationImageView?.visibility = View.GONE
            notificationImageView?.setImageDrawable(null)
        }

        setAppIcon(card.packageName)
    }

    private fun showNextCard() {
        if (summaryItems.size <= 1) {
            return
        }
        val targetIndex = (currentIndex + 1) % summaryItems.size
        currentIndex = targetIndex
        animateContentSwap(targetIndex = targetIndex, isNext = true)
        restartSpeechFromGesture()
    }

    private fun showPreviousCard() {
        if (summaryItems.size <= 1) {
            return
        }
        val targetIndex = if (currentIndex == 0) summaryItems.lastIndex else currentIndex - 1
        currentIndex = targetIndex
        animateContentSwap(targetIndex = targetIndex, isNext = false)
        restartSpeechFromGesture()
    }

    private fun skipCurrentFromNotificationAction() {
        if (isServiceStopping || summaryItems.isEmpty()) {
            return
        }

        if (currentIndex >= summaryItems.lastIndex) {
            requestGracefulStop("notification_action_skip_last_item")
            return
        }

        val targetIndex = (currentIndex + 1).coerceAtMost(summaryItems.lastIndex)
        currentIndex = targetIndex
        animateContentSwap(targetIndex = targetIndex, isNext = true)
        restartSpeechFromGesture()
    }

    private fun animateContentSwap(targetIndex: Int, isNext: Boolean) {
        val container = contentContainer
        if (container == null) {
            renderCurrentCard()
            return
        }

        transitionGeneration += 1L
        val generation = transitionGeneration

        // Rapid swipe safety: cancel any in-flight transitions first.
        container.animate().cancel()
        container.clearAnimation()

        val distance = (container.width.takeIf { it > 0 } ?: 140).toFloat()
        val exitTranslationX = if (isNext) -distance else distance
        val entryTranslationX = -exitTranslationX

        container.animate()
            .translationX(exitTranslationX)
            .alpha(0f)
            .setDuration(CARD_SWAP_DURATION_MS)
            .withEndAction {
                if (generation != transitionGeneration || isServiceStopping) {
                    return@withEndAction
                }

                // Data swap happens only after animate-out completes.
                currentIndex = targetIndex
                renderCurrentCard()

                // Reset off-screen for entry transition.
                container.translationX = entryTranslationX
                container.alpha = 0f

                container.animate().cancel()
                container.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(CARD_SWAP_DURATION_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        if (generation != transitionGeneration) {
                            return@withEndAction
                        }
                        container.translationX = 0f
                        container.alpha = 1f
                    }
                    .start()
            }
            .start()
    }

    private fun restartSpeechFromGesture() {
        if (isServiceStopping || !isTtsReady) {
            return
        }
        startSummarySpeechFromCurrentIndex(triggeredBySwipe = true)
    }

    private fun setAppIcon(packageName: String?) {
        if (packageName.isNullOrBlank()) {
            appIconView?.setImageResource(R.drawable.speakthaticon)
            return
        }
        try {
            val appIcon = packageManager.getApplicationIcon(packageName)
            appIconView?.setImageDrawable(appIcon)
        } catch (e: Exception) {
            appIconView?.setImageResource(R.drawable.speakthaticon)
        }
    }

    private fun resolveDisplayAppName(packageName: String): String {
        val bridgedName = NotificationReaderService.getDisplayAppNameForSummary(packageName)
        if (bridgedName.isNotBlank() && bridgedName != packageName) {
            return bridgedName
        }
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun runFirstLaunchBounceAnimationIfNeeded() {
        val container = contentContainer ?: return
        val prefs = getSharedPreferences(SummaryConstants.OVERLAY_PREFS_NAME, MODE_PRIVATE)
        val alreadyShown = prefs.getBoolean(SummaryConstants.KEY_BOUNCE_SHOWN, false)
        if (alreadyShown) {
            return
        }
        container.translationX = -100f
        container.animate()
            .translationX(0f)
            .setDuration(480L)
            .setInterpolator(OvershootInterpolator(1.6f))
            .start()
        prefs.edit().putBoolean(SummaryConstants.KEY_BOUNCE_SHOWN, true).apply()
    }

    private fun removeOverlayIfAttached() {
        val view = overlayView ?: return
        try {
            windowManager?.removeViewImmediate(view)
        } catch (e: Exception) {
            InAppLogger.logError(TAG, "Error removing summary overlay view: ${e.message}")
        } finally {
            overlayView = null
            contentContainer = null
            paginationText = null
            appIconView = null
            appTimeSubtitleText = null
            senderText = null
            messageText = null
            notificationImageCard = null
            notificationImageView = null
            loadingContainer = null
            loadingLineText = null
        }
    }

    private fun ensureCacheDirExists(): File {
        val target = File(cacheDir, SummaryConstants.CACHE_DIR_NAME)
        if (!target.exists()) {
            target.mkdirs()
        }
        return target
    }

    private fun clearSummaryCache() {
        val target = File(cacheDir, SummaryConstants.CACHE_DIR_NAME)
        if (!target.exists()) {
            return
        }
        target.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                file.listFiles()?.forEach { it.delete() }
            }
            file.delete()
        }
        target.delete()
    }

    private fun canDrawOverlaysCompat(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private data class SummaryItem(
        val notificationKey: String,
        val packageName: String,
        val appName: String,
        val appTimeSubtitle: String,
        val senderText: String,
        val messageText: String,
        val notificationImagePath: String?,
        val postTimeMillis: Long
    )

    companion object {
        private const val TAG = "SummaryExecutionSvc"
        private const val CARD_SWAP_DURATION_MS = 200L
        private const val SUMMARY_SETTINGS_PREFS_NAME = "SummarySettings"
        private const val SUMMARY_SETTINGS_KEY_GREETING_NAME = "greeting_name"
        private const val SUMMARY_SETTINGS_KEY_PAUSE_SECONDS = "pause_seconds"
    }
}
