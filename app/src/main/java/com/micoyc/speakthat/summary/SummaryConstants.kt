package com.micoyc.speakthat.summary

object SummaryConstants {
    const val ACTION_TRIGGER_SUMMARY = "com.micoyc.speakthat.ACTION_TRIGGER_SUMMARY"
    const val EXTRA_TRIGGER_SOURCE = "extra_trigger_source"
    const val TRIGGER_SOURCE_EXTERNAL_INTENT = "external_intent"
    const val TRIGGER_SOURCE_SCHEDULED_ALARM = "scheduled_alarm"

    const val ALARM_REQUEST_CODE = 60241
    const val PREFS_NAME = "summary_scheduler_prefs"
    const val KEY_ENABLED = "summary_daily_enabled"
    const val KEY_HOUR_OF_DAY = "summary_daily_hour_of_day"
    const val KEY_MINUTE = "summary_daily_minute"

    const val NOTIFICATION_CHANNEL_ID = "summary_execution_channel"
    const val NOTIFICATION_ID = 60242

    const val CACHE_DIR_NAME = "summary_overlay_cache"
    const val OVERLAY_PREFS_NAME = "summary_overlay_prefs"
    const val KEY_BOUNCE_SHOWN = "summary_bounce_shown"
    const val SWIPE_THRESHOLD_PX = 120

    const val TTS_PAUSE_GAP_MS = 1500L
    const val DEFAULT_GREETING_NAME = "User"

    const val UTTERANCE_PREFIX_INTRO = "intro"
    const val UTTERANCE_PREFIX_ITEM = "item"
    const val UTTERANCE_PREFIX_PAUSE = "pause"
    const val UTTERANCE_PREFIX_OUTRO = "outro"
}
