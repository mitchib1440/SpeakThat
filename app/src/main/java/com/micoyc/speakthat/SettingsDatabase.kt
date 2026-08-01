/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.content.Intent
import com.micoyc.speakthat.settings.BehaviorSettingsActivity
import com.micoyc.speakthat.CompatibilitySettingsActivity

object SettingsDatabase {
    
    fun getAllSettings(context: android.content.Context): List<SettingsItem> {
        val currentFlavor = BuildConfig.DISTRIBUTION_CHANNEL
        return listOf(
            // General Settings
            SettingsItem(
                id = "dark_mode",
                titleRes = R.string.search_title_dark_mode,
                descriptionRes = R.string.search_desc_dark_mode,
                category = "development",
                categoryTitleRes = R.string.search_cat_development,
                categoryIconRes = R.drawable.ic_code_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_dark_mode,
                navigationAction = { context, id -> context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "auto_start_boot",
                titleRes = R.string.search_title_auto_start_boot,
                descriptionRes = R.string.search_desc_auto_start_boot,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_auto_start_boot,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "battery_optimization",
                titleRes = R.string.search_title_battery_optimization,
                descriptionRes = R.string.search_desc_battery_optimization,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_battery_optimization,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "service_restart_policy",
                titleRes = R.string.search_title_service_restart_policy,
                descriptionRes = R.string.search_desc_service_restart_policy,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.RADIO_GROUP,
                searchKeywordsRes = R.string.search_keywords_service_restart_policy,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "auto_updates",
                titleRes = R.string.search_title_auto_updates,
                descriptionRes = R.string.search_desc_auto_updates,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_auto_updates,
                supportedFlavors = listOf("github"),
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "export_config",
                titleRes = R.string.search_title_export_config,
                descriptionRes = R.string.search_desc_export_config,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_export_config,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "import_config",
                titleRes = R.string.search_title_import_config,
                descriptionRes = R.string.search_desc_import_config,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_import_config,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "clear_data",
                titleRes = R.string.search_title_clear_data,
                descriptionRes = R.string.search_desc_clear_data,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_clear_data,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "style_section",
                titleRes = R.string.search_title_style_section,
                descriptionRes = R.string.search_desc_style_section,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.CARD,
                searchKeywordsRes = R.string.search_keywords_style_section,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "logo_style",
                titleRes = R.string.search_title_logo_style,
                descriptionRes = R.string.search_desc_logo_style,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_logo_style,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "accessibility_permission",
                titleRes = R.string.search_title_accessibility_permission,
                descriptionRes = R.string.search_desc_accessibility_permission,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_accessibility_permission,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "notification_history_section",
                titleRes = R.string.search_title_notification_history_section,
                descriptionRes = R.string.search_desc_notification_history_section,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.CARD,
                searchKeywordsRes = R.string.search_keywords_notification_history_section,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "main_screen_history",
                titleRes = R.string.search_title_main_screen_history,
                descriptionRes = R.string.search_desc_main_screen_history,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_main_screen_history,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "log_system_blocks",
                titleRes = R.string.search_title_log_system_blocks,
                descriptionRes = R.string.search_desc_log_system_blocks,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_log_system_blocks,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "toast_notifications_section",
                titleRes = R.string.search_title_toast_notifications_section,
                descriptionRes = R.string.search_desc_toast_notifications_section,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.CARD,
                searchKeywordsRes = R.string.search_keywords_toast_notifications_section,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "toast_main_app",
                titleRes = R.string.search_title_toast_main_app,
                descriptionRes = R.string.search_desc_toast_main_app,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_toast_main_app,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "toast_quick_settings",
                titleRes = R.string.search_title_toast_quick_settings,
                descriptionRes = R.string.search_desc_toast_quick_settings,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_toast_quick_settings,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "toast_automation",
                titleRes = R.string.search_title_toast_automation,
                descriptionRes = R.string.search_desc_toast_automation,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_toast_automation,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "toast_notification_action",
                titleRes = R.string.search_title_toast_notification_action,
                descriptionRes = R.string.search_desc_toast_notification_action,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_toast_notification_action,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),

            // Behavior Settings
            SettingsItem(
                id = "notification_behavior",
                titleRes = R.string.search_title_notification_behavior,
                descriptionRes = R.string.search_desc_notification_behavior,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.CARD,
                searchKeywordsRes = R.string.search_keywords_notification_behavior,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "media_behavior",
                titleRes = R.string.search_title_media_behavior,
                descriptionRes = R.string.search_desc_media_behavior,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.CARD,
                searchKeywordsRes = R.string.search_keywords_media_behavior,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "earcon",
                titleRes = R.string.search_title_earcon,
                descriptionRes = R.string.search_desc_earcon,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.CARD,
                searchKeywordsRes = R.string.search_keywords_earcon,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "shake_to_stop",
                titleRes = R.string.search_title_shake_to_stop,
                descriptionRes = R.string.search_desc_shake_to_stop,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_shake_to_stop,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "wave_to_stop",
                titleRes = R.string.search_title_wave_to_stop,
                descriptionRes = R.string.search_desc_wave_to_stop,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_wave_to_stop,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "swipe_to_stop",
                titleRes = R.string.behavior_swipe_to_stop_title,
                descriptionRes = R.string.behavior_swipe_to_stop_description,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_swipe_to_stop,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "swipe_to_stop_watch_exception",
                titleRes = R.string.behavior_swipe_to_stop_watch_exception_title,
                descriptionRes = R.string.behavior_swipe_to_stop_watch_exception_description,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_swipe_to_stop_watch_exception,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "swipe_to_stop_auto_cancel",
                titleRes = R.string.behavior_swipe_to_stop_auto_cancel_title,
                descriptionRes = R.string.behavior_swipe_to_stop_auto_cancel_description,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_swipe_to_stop_auto_cancel,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "android_auto_disable_speakthat",
                titleRes = R.string.search_title_android_auto_disable_speakthat,
                descriptionRes = R.string.search_desc_android_auto_disable_speakthat,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_android_auto_disable_speakthat,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "android_auto_disable_sco",
                titleRes = R.string.search_title_android_auto_disable_sco,
                descriptionRes = R.string.search_desc_android_auto_disable_sco,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_android_auto_disable_sco,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "custom_app_names",
                titleRes = R.string.search_title_custom_app_names,
                descriptionRes = R.string.search_desc_custom_app_names,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.CARD,
                searchKeywordsRes = R.string.search_keywords_custom_app_names,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "speech_formatting",
                titleRes = R.string.search_title_speech_formatting,
                descriptionRes = R.string.search_desc_speech_formatting,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.CARD,
                searchKeywordsRes = R.string.search_keywords_speech_formatting,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "skip_repeated_prefix",
                titleRes = R.string.behavior_skip_repeated_prefix_title,
                descriptionRes = R.string.behavior_skip_repeated_prefix_description,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_skip_repeated_prefix,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "honour_do_not_disturb",
                titleRes = R.string.search_title_honour_do_not_disturb,
                descriptionRes = R.string.search_desc_honour_do_not_disturb,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_honour_do_not_disturb,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "honour_audio_mode",
                titleRes = R.string.search_title_honour_audio_mode,
                descriptionRes = R.string.search_desc_honour_audio_mode,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_honour_audio_mode,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "honour_phone_calls",
                titleRes = R.string.search_title_honour_phone_calls,
                descriptionRes = R.string.search_desc_honour_phone_calls,
                category = "behavior",
                categoryTitleRes = R.string.search_cat_behavior,
                categoryIconRes = R.drawable.ic_notification_settings_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_honour_phone_calls,
                navigationAction = { context, id -> context.startActivity(Intent(context, BehaviorSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "persistent_notification",
                titleRes = R.string.search_title_persistent_notification,
                descriptionRes = R.string.search_desc_persistent_notification,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_persistent_notification,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "notification_while_reading",
                titleRes = R.string.search_title_notification_while_reading,
                descriptionRes = R.string.search_desc_notification_while_reading,
                category = "general",
                categoryTitleRes = R.string.search_cat_general,
                categoryIconRes = R.drawable.ic_mobile_gear_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_notification_while_reading,
                navigationAction = { context, id -> context.startActivity(Intent(context, GeneralSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),

            // Voice Settings
            SettingsItem(
                id = "speech_rate",
                titleRes = R.string.search_title_speech_rate,
                descriptionRes = R.string.search_desc_speech_rate,
                category = "voice",
                categoryTitleRes = R.string.search_cat_voice,
                categoryIconRes = R.drawable.ic_voice_selection_24,
                settingType = SettingType.SEEK_BAR,
                searchKeywordsRes = R.string.search_keywords_speech_rate,
                navigationAction = { context, id -> context.startActivity(Intent(context, VoiceSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "pitch",
                titleRes = R.string.search_title_pitch,
                descriptionRes = R.string.search_desc_pitch,
                category = "voice",
                categoryTitleRes = R.string.search_cat_voice,
                categoryIconRes = R.drawable.ic_voice_selection_24,
                settingType = SettingType.SEEK_BAR,
                searchKeywordsRes = R.string.search_keywords_pitch,
                navigationAction = { context, id -> context.startActivity(Intent(context, VoiceSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "voice_selection",
                titleRes = R.string.search_title_voice_selection,
                descriptionRes = R.string.search_desc_voice_selection,
                category = "voice",
                categoryTitleRes = R.string.search_cat_voice,
                categoryIconRes = R.drawable.ic_voice_selection_24,
                settingType = SettingType.SPINNER,
                searchKeywordsRes = R.string.search_keywords_voice_selection,
                navigationAction = { context, id -> val intent = Intent(context, VoiceSettingsActivity::class.java); intent.putExtra("expand_advanced", true); intent.putExtra("SCROLL_TO_SETTING", id); context.startActivity(intent) }
            ),
            SettingsItem(
                id = "language",
                titleRes = R.string.search_title_language,
                descriptionRes = R.string.search_desc_language,
                category = "voice",
                categoryTitleRes = R.string.search_cat_voice,
                categoryIconRes = R.drawable.ic_voice_selection_24,
                settingType = SettingType.SPINNER,
                searchKeywordsRes = R.string.search_keywords_language,
                navigationAction = { context, id -> context.startActivity(Intent(context, VoiceSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "ui_language",
                titleRes = R.string.search_title_ui_language,
                descriptionRes = R.string.search_desc_ui_language,
                category = "voice",
                categoryTitleRes = R.string.search_cat_voice,
                categoryIconRes = R.drawable.ic_voice_selection_24,
                settingType = SettingType.SPINNER,
                searchKeywordsRes = R.string.search_keywords_ui_language,
                navigationAction = { context, id -> val intent = Intent(context, VoiceSettingsActivity::class.java); intent.putExtra("expand_advanced", true); intent.putExtra("SCROLL_TO_SETTING", id); context.startActivity(intent) }
            ),
            SettingsItem(
                id = "tts_language",
                titleRes = R.string.search_title_tts_language,
                descriptionRes = R.string.search_desc_tts_language,
                category = "voice",
                categoryTitleRes = R.string.search_cat_voice,
                categoryIconRes = R.drawable.ic_voice_selection_24,
                settingType = SettingType.SPINNER,
                searchKeywordsRes = R.string.search_keywords_tts_language,
                navigationAction = { context, id -> val intent = Intent(context, VoiceSettingsActivity::class.java); intent.putExtra("expand_advanced", true); intent.putExtra("SCROLL_TO_SETTING", id); context.startActivity(intent) }
            ),
            SettingsItem(
                id = "preview_voice",
                titleRes = R.string.search_title_preview_voice,
                descriptionRes = R.string.search_desc_preview_voice,
                category = "voice",
                categoryTitleRes = R.string.search_cat_voice,
                categoryIconRes = R.drawable.ic_voice_selection_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_preview_voice,
                navigationAction = { context, id -> context.startActivity(Intent(context, VoiceSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "tts_engine",
                titleRes = R.string.search_title_tts_engine,
                descriptionRes = R.string.search_desc_tts_engine,
                category = "voice",
                categoryTitleRes = R.string.search_cat_voice,
                categoryIconRes = R.drawable.ic_voice_selection_24,
                settingType = SettingType.SPINNER,
                searchKeywordsRes = R.string.search_keywords_tts_engine,
                navigationAction = { context, id -> val intent = Intent(context, VoiceSettingsActivity::class.java); intent.putExtra("expand_advanced", true); intent.putExtra("SCROLL_TO_SETTING", id); context.startActivity(intent) }
            ),
            SettingsItem(
                id = "tts_volume",
                titleRes = R.string.search_title_tts_volume,
                descriptionRes = R.string.search_desc_tts_volume,
                category = "voice",
                categoryTitleRes = R.string.search_cat_voice,
                categoryIconRes = R.drawable.ic_voice_selection_24,
                settingType = SettingType.SEEK_BAR,
                searchKeywordsRes = R.string.search_keywords_tts_volume,
                navigationAction = { context, id -> context.startActivity(Intent(context, VoiceSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            // Filter Settings
            SettingsItem(
                id = "app_filtering",
                titleRes = R.string.search_title_app_filtering,
                descriptionRes = R.string.search_desc_app_filtering,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.CARD,
                searchKeywordsRes = R.string.search_keywords_app_filtering,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "word_filtering",
                titleRes = R.string.search_title_word_filtering,
                descriptionRes = R.string.search_desc_word_filtering,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.CARD,
                searchKeywordsRes = R.string.search_keywords_word_filtering,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "word_replacements",
                titleRes = R.string.search_title_word_replacements,
                descriptionRes = R.string.search_desc_word_replacements,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.CARD,
                searchKeywordsRes = R.string.search_keywords_word_replacements,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "tidy_speech_section",
                titleRes = R.string.tidy_speech_title,
                descriptionRes = R.string.tidy_speech_description,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.CARD,
                searchKeywordsRes = R.string.search_keywords_tidy_speech_section,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "tidy_speech_remove_emojis",
                titleRes = R.string.tidy_speech_remove_emojis_title,
                descriptionRes = R.string.tidy_speech_remove_emojis_description,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_tidy_speech_remove_emojis,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "tidy_speech_separate_digits",
                titleRes = R.string.tidy_speech_separate_digits_title,
                descriptionRes = R.string.tidy_speech_separate_digits_description,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_tidy_speech_separate_digits,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "tidy_speech_separate_digits_threshold",
                titleRes = R.string.tidy_speech_separate_digits_threshold_title,
                descriptionRes = R.string.tidy_speech_separate_digits_description,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.SEEK_BAR,
                searchKeywordsRes = R.string.search_keywords_tidy_speech_separate_digits_threshold,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "tidy_speech_separate_digits_separator",
                titleRes = R.string.tidy_speech_separate_digits_separator_title,
                descriptionRes = R.string.tidy_speech_separate_digits_description,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.SPINNER,
                searchKeywordsRes = R.string.search_keywords_tidy_speech_separate_digits_separator,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "tidy_speech_emoji_exceptions",
                titleRes = R.string.search_title_tidy_speech_emoji_exceptions,
                descriptionRes = R.string.search_desc_tidy_speech_emoji_exceptions,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_tidy_speech_emoji_exceptions,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "filter_empty_text",
                titleRes = R.string.filter_empty_text_title,
                descriptionRes = R.string.filter_empty_text_description,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_filter_empty_text,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "url_handling",
                titleRes = R.string.search_title_url_handling,
                descriptionRes = R.string.search_desc_url_handling,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.CARD,
                searchKeywordsRes = R.string.search_keywords_url_handling,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "media_filtering",
                titleRes = R.string.search_title_media_filtering,
                descriptionRes = R.string.search_desc_media_filtering,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_media_filtering,
                navigationAction = { context, id -> context.startActivity(Intent(context, CompatibilitySettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "persistent_filtering",
                titleRes = R.string.search_title_persistent_filtering,
                descriptionRes = R.string.search_desc_persistent_filtering,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_persistent_filtering,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "filter_persistent",
                titleRes = R.string.search_title_filter_persistent,
                descriptionRes = R.string.search_desc_filter_persistent,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_filter_persistent,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "filter_silent",
                titleRes = R.string.search_title_filter_silent,
                descriptionRes = R.string.search_desc_filter_silent,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_filter_silent,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "filter_foreground_services",
                titleRes = R.string.search_title_filter_foreground_services,
                descriptionRes = R.string.search_desc_filter_foreground_services,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_filter_foreground_services,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "filter_low_priority",
                titleRes = R.string.search_title_filter_low_priority,
                descriptionRes = R.string.search_desc_filter_low_priority,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_filter_low_priority,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "filter_system_notifications",
                titleRes = R.string.search_title_filter_system_notifications,
                descriptionRes = R.string.search_desc_filter_system_notifications,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_filter_system_notifications,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "export_filter_config",
                titleRes = R.string.search_title_export_filter_config,
                descriptionRes = R.string.search_desc_export_filter_config,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_export_filter_config,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "import_filter_config",
                titleRes = R.string.search_title_import_filter_config,
                descriptionRes = R.string.search_desc_import_filter_config,
                category = "filter",
                categoryTitleRes = R.string.title_filter_settings,
                categoryIconRes = R.drawable.ic_filter_list_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_import_filter_config,
                navigationAction = { context, id -> context.startActivity(Intent(context, FilterSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),

            // Conditional Rules
            SettingsItem(
                id = "conditional_rules",
                titleRes = R.string.search_title_conditional_rules,
                descriptionRes = R.string.search_desc_conditional_rules,
                category = "conditional",
                categoryTitleRes = R.string.search_cat_conditional,
                categoryIconRes = R.drawable.ic_bluetooth_24,
                settingType = SettingType.CARD,
                searchKeywordsRes = R.string.search_keywords_conditional_rules,
                navigationAction = { context, id -> context.startActivity(Intent(context, RulesActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),

            // Development Settings
            SettingsItem(
                id = "debug_logging",
                titleRes = R.string.search_title_debug_logging,
                descriptionRes = R.string.search_desc_debug_logging,
                category = "development",
                categoryTitleRes = R.string.search_cat_development,
                categoryIconRes = R.drawable.ic_code_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_debug_logging,
                navigationAction = { context, id -> context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "verbose_logging",
                titleRes = R.string.search_title_verbose_logging,
                descriptionRes = R.string.search_desc_verbose_logging,
                category = "development",
                categoryTitleRes = R.string.search_cat_development,
                categoryIconRes = R.drawable.ic_code_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_verbose_logging,
                navigationAction = { context, id -> context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "log_filters",
                titleRes = R.string.search_title_log_filters,
                descriptionRes = R.string.search_desc_log_filters,
                category = "development",
                categoryTitleRes = R.string.search_cat_development,
                categoryIconRes = R.drawable.ic_code_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_log_filters,
                navigationAction = { context, id -> context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "log_notifications",
                titleRes = R.string.search_title_log_notifications,
                descriptionRes = R.string.search_desc_log_notifications,
                category = "development",
                categoryTitleRes = R.string.search_cat_development,
                categoryIconRes = R.drawable.ic_code_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_log_notifications,
                navigationAction = { context, id -> context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "log_user_actions",
                titleRes = R.string.search_title_log_user_actions,
                descriptionRes = R.string.search_desc_log_user_actions,
                category = "development",
                categoryTitleRes = R.string.search_cat_development,
                categoryIconRes = R.drawable.ic_code_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_log_user_actions,
                navigationAction = { context, id -> context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "log_system_events",
                titleRes = R.string.search_title_log_system_events,
                descriptionRes = R.string.search_desc_log_system_events,
                category = "development",
                categoryTitleRes = R.string.search_cat_development,
                categoryIconRes = R.drawable.ic_code_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_log_system_events,
                navigationAction = { context, id -> context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "test_notifications",
                titleRes = R.string.search_title_test_notifications,
                descriptionRes = R.string.search_desc_test_notifications,
                category = "development",
                categoryTitleRes = R.string.search_cat_development,
                categoryIconRes = R.drawable.ic_code_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_test_notifications,
                navigationAction = { context, id -> context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "view_logs",
                titleRes = R.string.search_title_view_logs,
                descriptionRes = R.string.search_desc_view_logs,
                category = "development",
                categoryTitleRes = R.string.search_cat_development,
                categoryIconRes = R.drawable.ic_code_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_view_logs,
                navigationAction = { context, id -> context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "clear_logs",
                titleRes = R.string.search_title_clear_logs,
                descriptionRes = R.string.search_desc_clear_logs,
                category = "development",
                categoryTitleRes = R.string.search_cat_development,
                categoryIconRes = R.drawable.ic_code_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_clear_logs,
                navigationAction = { context, id -> context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),

            // Support & Feedback
            SettingsItem(
                id = "feature_request",
                titleRes = R.string.search_title_feature_request,
                descriptionRes = R.string.search_desc_feature_request,
                category = "support",
                categoryTitleRes = R.string.search_cat_support,
                categoryIconRes = R.drawable.ic_lifering_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_feature_request,
                navigationAction = { context, id -> context.startActivity(Intent(context, SupportActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "bug_report",
                titleRes = R.string.search_title_bug_report,
                descriptionRes = R.string.search_desc_bug_report,
                category = "support",
                categoryTitleRes = R.string.search_cat_support,
                categoryIconRes = R.drawable.ic_lifering_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_bug_report,
                navigationAction = { context, id -> context.startActivity(Intent(context, SupportActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "general_support",
                titleRes = R.string.search_title_general_support,
                descriptionRes = R.string.search_desc_general_support,
                category = "support",
                categoryTitleRes = R.string.search_cat_support,
                categoryIconRes = R.drawable.ic_lifering_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_general_support,
                navigationAction = { context, id -> context.startActivity(Intent(context, SupportActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),

            // Compatibility Settings
            SettingsItem(
                id = "audio_usage",
                titleRes = R.string.search_title_audio_usage,
                descriptionRes = R.string.search_desc_audio_usage,
                category = "compatibility",
                categoryTitleRes = R.string.search_cat_compatibility,
                categoryIconRes = R.drawable.ic_framebug_24,
                settingType = SettingType.SPINNER,
                searchKeywordsRes = R.string.search_keywords_audio_usage,
                navigationAction = { context, id -> context.startActivity(Intent(context, CompatibilitySettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "content_type",
                titleRes = R.string.search_title_content_type,
                descriptionRes = R.string.search_desc_content_type,
                category = "compatibility",
                categoryTitleRes = R.string.search_cat_compatibility,
                categoryIconRes = R.drawable.ic_framebug_24,
                settingType = SettingType.SPINNER,
                searchKeywordsRes = R.string.search_keywords_content_type,
                navigationAction = { context, id -> context.startActivity(Intent(context, CompatibilitySettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "speakerphone_enabled",
                titleRes = R.string.search_title_speakerphone_enabled,
                descriptionRes = R.string.search_desc_speakerphone_enabled,
                category = "compatibility",
                categoryTitleRes = R.string.search_cat_compatibility,
                categoryIconRes = R.drawable.ic_framebug_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_speakerphone_enabled,
                navigationAction = { context, id -> context.startActivity(Intent(context, CompatibilitySettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "dont_use_speaker",
                titleRes = R.string.search_title_dont_use_speaker,
                descriptionRes = R.string.search_desc_dont_use_speaker,
                category = "compatibility",
                categoryTitleRes = R.string.search_cat_compatibility,
                categoryIconRes = R.drawable.ic_framebug_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_dont_use_speaker,
                navigationAction = { context, id -> context.startActivity(Intent(context, CompatibilitySettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "disable_media_fallback",
                titleRes = R.string.search_title_disable_media_fallback,
                descriptionRes = R.string.search_desc_disable_media_fallback,
                category = "compatibility",
                categoryTitleRes = R.string.search_cat_compatibility,
                categoryIconRes = R.drawable.ic_framebug_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_disable_media_fallback,
                navigationAction = { context, id -> context.startActivity(Intent(context, CompatibilitySettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "enable_legacy_ducking",
                titleRes = R.string.search_title_enable_legacy_ducking,
                descriptionRes = R.string.search_desc_enable_legacy_ducking,
                category = "compatibility",
                categoryTitleRes = R.string.search_cat_compatibility,
                categoryIconRes = R.drawable.ic_framebug_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_enable_legacy_ducking,
                navigationAction = { context, id -> context.startActivity(Intent(context, CompatibilitySettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "ducking_fallback_strategy",
                titleRes = R.string.search_title_ducking_fallback_strategy,
                descriptionRes = R.string.search_desc_ducking_fallback_strategy,
                category = "compatibility",
                categoryTitleRes = R.string.search_cat_compatibility,
                categoryIconRes = R.drawable.ic_framebug_24,
                settingType = SettingType.RADIO_GROUP,
                searchKeywordsRes = R.string.search_keywords_ducking_fallback_strategy,
                navigationAction = { context, id -> context.startActivity(Intent(context, CompatibilitySettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "notification_deduplication",
                titleRes = R.string.search_title_notification_deduplication,
                descriptionRes = R.string.search_desc_notification_deduplication,
                category = "compatibility",
                categoryTitleRes = R.string.search_cat_compatibility,
                categoryIconRes = R.drawable.ic_framebug_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_notification_deduplication,
                navigationAction = { context, id -> context.startActivity(Intent(context, CompatibilitySettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "dismissal_memory",
                titleRes = R.string.search_title_dismissal_memory,
                descriptionRes = R.string.search_desc_dismissal_memory,
                category = "compatibility",
                categoryTitleRes = R.string.search_cat_compatibility,
                categoryIconRes = R.drawable.ic_framebug_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_dismissal_memory,
                navigationAction = { context, id -> context.startActivity(Intent(context, CompatibilitySettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "dismissal_memory_timeout",
                titleRes = R.string.search_title_dismissal_memory_timeout,
                descriptionRes = R.string.search_desc_dismissal_memory_timeout,
                category = "compatibility",
                categoryTitleRes = R.string.search_cat_compatibility,
                categoryIconRes = R.drawable.ic_framebug_24,
                settingType = SettingType.SEEK_BAR,
                searchKeywordsRes = R.string.search_keywords_dismissal_memory_timeout,
                navigationAction = { context, id -> context.startActivity(Intent(context, CompatibilitySettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),
            SettingsItem(
                id = "broadcast_to_stop",
                titleRes = R.string.search_title_broadcast_to_stop,
                descriptionRes = R.string.search_desc_broadcast_to_stop,
                category = "development",
                categoryTitleRes = R.string.search_cat_development,
                categoryIconRes = R.drawable.ic_bugdroid_24,
                settingType = SettingType.SWITCH,
                searchKeywordsRes = R.string.search_keywords_broadcast_to_stop,
                navigationAction = { context, id -> context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            ),

            // Re-run Onboarding
            SettingsItem(
                id = "re_run_onboarding",
                titleRes = R.string.search_title_re_run_onboarding,
                descriptionRes = R.string.search_desc_re_run_onboarding,
                category = "onboarding",
                categoryTitleRes = R.string.search_cat_onboarding,
                categoryIconRes = R.drawable.ic_laps_24,
                settingType = SettingType.BUTTON,
                searchKeywordsRes = R.string.search_keywords_re_run_onboarding,
                navigationAction = { context, id -> context.startActivity(Intent(context, OnboardingActivity::class.java).putExtra("SCROLL_TO_SETTING", id)) }
            )
        ).filter { currentFlavor in it.supportedFlavors }
    }
} 
