package com.micoyc.speakthat.summary

object SummaryConstants {
    const val ACTION_TRIGGER_SUMMARY = "com.micoyc.speakthat.ACTION_TRIGGER_SUMMARY"
    const val ACTION_SUMMARY_ALARM = "com.micoyc.speakthat.ACTION_SUMMARY_ALARM"
    const val ACTION_STOP_SUMMARY = "com.micoyc.speakthat.ACTION_STOP_SUMMARY"
    const val ACTION_SKIP_CURRENT_NOTIFICATION = "com.micoyc.speakthat.ACTION_SKIP_CURRENT_NOTIFICATION"
    const val EXTRA_TRIGGER_SOURCE = "extra_trigger_source"
    const val TRIGGER_SOURCE_EXTERNAL_INTENT = "external_intent"
    const val TRIGGER_SOURCE_SCHEDULED_ALARM = "scheduled_alarm"

    const val ALARM_REQUEST_CODE = 60241
    const val SUMMARY_SETTINGS_PREFS_NAME = "SummarySettings"
    // Legacy combined scheduler/global key kept for migration.
    const val KEY_ENABLED = "enabled"
    const val KEY_GLOBAL_ENABLED = "global_enabled"
    const val KEY_SCHEDULER_ENABLED = "scheduler_enabled"
    const val KEY_HOUR_OF_DAY = "schedule_hour"
    const val KEY_MINUTE = "schedule_minute"
    const val KEY_GREETING_NAME = "greeting_name"
    const val KEY_PAUSE_SECONDS = "pause_seconds"
    const val KEY_NOTIFICATION_ORDER = "notification_order"
    const val ORDER_OLDEST_FIRST = "oldest_first"
    const val ORDER_NEWEST_FIRST = "newest_first"

    const val NOTIFICATION_CHANNEL_ID = "summary_execution_channel"
    const val NOTIFICATION_ID = 60242
    const val REQUEST_CODE_OPEN_SUMMARY_APP = 60243
    const val REQUEST_CODE_STOP_SUMMARY = 60244
    const val REQUEST_CODE_SKIP_SUMMARY_NOTIFICATION = 60245

    const val CACHE_DIR_NAME = "summary_overlay_cache"
    const val OVERLAY_PREFS_NAME = "summary_overlay_prefs"
    const val KEY_BOUNCE_SHOWN = "summary_bounce_shown"
    const val SWIPE_THRESHOLD_PX = 120

    const val TTS_PAUSE_GAP_MS = 1500L
    const val DEFAULT_GREETING_NAME = "Human"

    const val UTTERANCE_PREFIX_INTRO = "intro"
    const val UTTERANCE_PREFIX_ITEM = "item"
    const val UTTERANCE_PREFIX_PAUSE = "pause"
    const val UTTERANCE_PREFIX_OUTRO = "outro"
}
