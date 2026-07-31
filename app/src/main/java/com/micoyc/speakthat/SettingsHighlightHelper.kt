/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView

object SettingsHighlightHelper {

    const val EXTRA_SCROLL_TO_SETTING = "SCROLL_TO_SETTING"

    private val VIEW_IDS: Map<String, Int> = mapOf(
        // General
        "auto_start_boot" to R.id.switchAutoStart,
        "battery_optimization" to R.id.switchBatteryOptimization,
        "service_restart_policy" to R.id.radioGroupRestartPolicy,
        "auto_updates" to R.id.switchAutoUpdate,
        "export_config" to R.id.exportConfigButton,
        "import_config" to R.id.importConfigButton,
        "clear_data" to R.id.clearDataButton,
        "persistent_notification" to R.id.switchPersistentNotification,
        "notification_while_reading" to R.id.switchNotificationWhileReading,
        "dark_mode" to R.id.switchDeprecatedTheme,

        // Behavior
        "notification_behavior" to R.id.behaviorModeGroup,
        "media_behavior" to R.id.mediaBehaviorGroup,
        "earcon" to R.id.spinnerEarcon,
        "shake_to_stop" to R.id.switchShakeToStop,
        "wave_to_stop" to R.id.switchWaveToStop,
        "swipe_to_stop" to R.id.switchSwipeToStop,
        "swipe_to_stop_watch_exception" to R.id.switchWatchException,
        "swipe_to_stop_auto_cancel" to R.id.switchAutoCancel,
        "android_auto_disable_speakthat" to R.id.switchDisableSpeakThatAuto,
        "android_auto_disable_sco" to R.id.switchDisableScoAuto,
        "custom_app_names" to R.id.btnAddCustomAppName,
        "speech_formatting" to R.id.spinnerSpeechTemplate,
        "skip_repeated_prefix" to R.id.switchSkipRepeatedNotificationPrefixes,
        "honour_do_not_disturb" to R.id.switchHonourDoNotDisturb,
        "honour_audio_mode" to R.id.switchHonourSilentMode,
        "honour_phone_calls" to R.id.switchHonourPhoneCalls,

        // Voice
        "speech_rate" to R.id.speechRateSeekBar,
        "pitch" to R.id.pitchSeekBar,
        "tts_volume" to R.id.ttsVolumeSeekBar,
        "language" to R.id.languagePresetSpinner,
        "ui_language" to R.id.uiLanguageSpinner,
        "tts_language" to R.id.ttsLanguageSpinner,
        "voice_selection" to R.id.voiceSpinner,
        "tts_engine" to R.id.ttsEngineSpinner,
        "preview_voice" to R.id.previewButton,

        // Filter
        "app_filtering" to R.id.appListModeGroup,
        "word_filtering" to R.id.wordListModeGroup,
        "word_replacements" to R.id.replacementHeader,
        "tidy_speech_section" to R.id.switchRemoveEmojis,
        "tidy_speech_remove_emojis" to R.id.switchRemoveEmojis,
        "tidy_speech_separate_digits" to R.id.switchSeparateDigits,
        "tidy_speech_separate_digits_threshold" to R.id.sliderDigitThreshold,
        "tidy_speech_separate_digits_separator" to R.id.spinnerSeparatorType,
        "tidy_speech_emoji_exceptions" to R.id.emojiExceptionsSection,
        "filter_empty_text" to R.id.switchFilterEmptyText,
        "url_handling" to R.id.urlHandlingModeGroup,
        "persistent_filtering" to R.id.switchPersistentFiltering,
        "filter_persistent" to R.id.switchFilterPersistent,
        "filter_silent" to R.id.switchFilterSilent,
        "filter_foreground_services" to R.id.switchFilterForegroundServices,
        "filter_low_priority" to R.id.switchFilterLowPriority,
        "filter_system_notifications" to R.id.switchFilterSystemNotifications,

        // Compatibility
        "media_filtering" to R.id.switchMediaFiltering,
        "audio_usage" to R.id.audioUsageSpinner,
        "content_type" to R.id.contentTypeSpinner,
        "speakerphone_enabled" to R.id.speakerphoneSwitch,
        "dont_use_speaker" to R.id.dontUseSpeakerSwitch,
        "disable_media_fallback" to R.id.switchDisableMediaFallback,
        "enable_legacy_ducking" to R.id.switchEnableLegacyDucking,
        "ducking_fallback_strategy" to R.id.duckingFallbackGroup,
        "notification_deduplication" to R.id.switchNotificationDeduplication,
        "dismissal_memory" to R.id.switchDismissalMemory,
        "dismissal_memory_timeout" to R.id.dismissalMemoryTimeoutGroup,

        // Development
        "debug_logging" to R.id.switchVerboseLogging,
        "verbose_logging" to R.id.switchVerboseLogging,
        "log_filters" to R.id.switchLogFilters,
        "log_notifications" to R.id.switchLogNotifications,
        "log_user_actions" to R.id.switchLogUserActions,
        "log_system_events" to R.id.switchLogSystemEvents,
        "broadcast_to_stop" to R.id.switchBroadcastToStop,
        "test_notifications" to R.id.btnTestSettings,
        "view_logs" to R.id.btnRefreshLogs,
        "clear_logs" to R.id.btnClearLogs,

        // Support
        "feature_request" to R.id.spinnerSupportType,
        "bug_report" to R.id.spinnerSupportType,
        "general_support" to R.id.spinnerSupportType,

        // Rules
        "conditional_rules" to R.id.rulesContainer
    )

    /**
     * Reads SCROLL_TO_SETTING from the activity intent, scrolls the nearest scroll parent
     * to the mapped view, and plays a brief highlight flash.
     */
    @JvmStatic
    fun handleScrollToSetting(activity: Activity) {
        val settingId = activity.intent?.getStringExtra(EXTRA_SCROLL_TO_SETTING) ?: return
        val viewId = VIEW_IDS[settingId] ?: return
        val target = activity.findViewById<View>(viewId) ?: return

        // Prefer highlighting the padded row container when the target is a small control
        val highlightTarget = findHighlightContainer(target) ?: target

        highlightTarget.post {
            val scrollParent = findScrollParent(highlightTarget)
            if (scrollParent != null) {
                val y = getRelativeTop(highlightTarget, scrollParent)
                when (scrollParent) {
                    is ScrollView -> scrollParent.smoothScrollTo(0, maxOf(0, y - 120))
                    is NestedScrollView -> scrollParent.smoothScrollTo(0, maxOf(0, y - 120))
                    else -> scrollParent.scrollTo(0, maxOf(0, y - 120))
                }
            }
            // Flash after a short delay so the scroll can settle
            highlightTarget.postDelayed({ flash(highlightTarget) }, 250)
        }
    }

    private fun findHighlightContainer(view: View): View? {
        var current: ViewParent? = view.parent
        var depth = 0
        while (current is View && depth < 4) {
            val v = current
            if (v.paddingTop >= 8 || v.paddingBottom >= 8) {
                return v
            }
            current = v.parent
            depth++
        }
        return view
    }

    private fun findScrollParent(view: View): ViewGroup? {
        var parent: ViewParent? = view.parent
        while (parent != null) {
            if (parent is ScrollView || parent is NestedScrollView) {
                return parent as ViewGroup
            }
            parent = parent.parent
        }
        return null
    }

    private fun getRelativeTop(view: View, ancestor: View): Int {
        var top = 0
        var current: View? = view
        while (current != null && current !== ancestor) {
            top += current.top
            val p = current.parent
            current = if (p is View) p else null
        }
        return top
    }

    private fun flash(view: View) {
        val highlight = ContextCompat.getColor(view.context, R.color.purple_200)
        val highlightAlpha = (highlight and 0x00FFFFFF) or 0x66000000
        val original: Drawable? = view.background
        val startColor = highlightAlpha
        val endColor = 0x00000000

        val animator = ValueAnimator.ofObject(ArgbEvaluator(), startColor, endColor)
        animator.duration = 900
        animator.addUpdateListener { animation ->
            val color = animation.animatedValue as Int
            view.setBackgroundColor(color)
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                view.background = original
            }

            override fun onAnimationCancel(animation: android.animation.Animator) {
                view.background = original
            }
        })
        // Seed starting highlight
        view.setBackgroundColor(startColor)
        animator.start()
    }
}
