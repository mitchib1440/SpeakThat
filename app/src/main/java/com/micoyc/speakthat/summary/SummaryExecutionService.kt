package com.micoyc.speakthat.summary

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
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
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
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

/**
 * Phase 3 execution service for proactive summaries.
 * Adds TTS sequencing + overlay synchronization on top of the Phase 2 overlay/data pipeline.
 */
class SummaryExecutionService : Service(), TextToSpeech.OnInitListener {

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
    private var touchStartX: Float = 0f

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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var buildItemsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        ensureCacheDirExists()
        initializeTextToSpeech()
        InAppLogger.log(TAG, "SummaryExecutionService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val source = intent?.getStringExtra(SummaryConstants.EXTRA_TRIGGER_SOURCE) ?: "unknown"
        val action = intent?.action ?: SummaryConstants.ACTION_TRIGGER_SUMMARY

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
                        currentIndex = index.coerceIn(0, summaryItems.lastIndex)
                        renderCurrentCard()
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
                    SummaryConstants.TTS_PAUSE_GAP_MS,
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
        val countText = resources.getQuantityString(
            R.plurals.summary_tts_notification_count,
            notificationCountForGreeting,
            notificationCountForGreeting
        )
        return getString(
            R.string.summary_tts_intro_template,
            dayPeriod,
            SummaryConstants.DEFAULT_GREETING_NAME,
            currentTime,
            batteryPercent,
            countText
        )
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
        return getString(
            R.string.summary_tts_item_template,
            relativeTime,
            item.senderText,
            item.messageText
        )
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
            "Proactive Summary",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Foreground service channel for proactive summary execution"
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
            0,
            openAppIntent,
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
            val dismissButton: ImageButton = view.findViewById(R.id.summaryDismissButton)

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
            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
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
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - touchStartX
                    if (abs(deltaX) >= SummaryConstants.SWIPE_THRESHOLD_PX) {
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

        active.sortedByDescending { it.postTime }.forEach { sbn ->
            if (shouldSkipNotification(sbn)) {
                return@forEach
            }

            val appName = resolveAppLabel(sbn.packageName)
            val sender = extractSender(sbn).ifBlank { appName }
            val rawMessage = extractMessage(sbn)
            if (sender.isBlank() && rawMessage.isBlank()) {
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
                    senderText = sender.ifBlank { getString(R.string.summary_overlay_sender_fallback) },
                    messageText = message.ifBlank { getString(R.string.summary_overlay_empty_message) },
                    notificationImagePath = extractBigPicturePathFromNotification(sbn),
                    postTimeMillis = sbn.postTime
                )
            )
        }
        return result
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
        return resolveAppLabel(sbn.packageName)
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

        val pictureBitmap = extras.get(Notification.EXTRA_PICTURE) as? Bitmap
        if (pictureBitmap != null) {
            return persistBitmapAndRecycle(pictureBitmap, sbn, "picture")
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
        currentIndex = (currentIndex + 1) % summaryItems.size
        renderCurrentCard()
        restartSpeechFromGesture()
    }

    private fun showPreviousCard() {
        if (summaryItems.size <= 1) {
            return
        }
        currentIndex = if (currentIndex == 0) summaryItems.lastIndex else currentIndex - 1
        renderCurrentCard()
        restartSpeechFromGesture()
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

    private fun resolveAppLabel(packageName: String): String {
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
    }
}
