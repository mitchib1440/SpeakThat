package com.micoyc.speakthat

import android.content.Intent
import com.micoyc.speakthat.settings.BehaviorSettingsActivity

object SettingsDatabase {
    
    fun getAllSettings(context: android.content.Context): List<SettingsItem> {
        return listOf(
            // General Settings
            SettingsItem(
                id = "dark_mode",
                title = "Dark Mode",
                description = "Enable dark mode for this app",
                category = "general",
                categoryTitle = "General Settings",
                categoryIcon = "⚙️",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("theme", "dark", "light", "mode", "appearance", "color"),
                navigationAction = { context.startActivity(Intent(context, GeneralSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "auto_start_boot",
                title = "Auto-start on Boot",
                description = "Automatically start the notification reader when device boots",
                category = "general",
                categoryTitle = "General Settings",
                categoryIcon = "⚙️",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("boot", "startup", "auto", "launch", "device", "restart"),
                navigationAction = { context.startActivity(Intent(context, GeneralSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "battery_optimization",
                title = "Battery Optimization Exemption",
                description = "Request to ignore battery optimization for better reliability",
                category = "general",
                categoryTitle = "General Settings",
                categoryIcon = "⚙️",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("battery", "optimization", "power", "save", "exemption", "ignore"),
                navigationAction = { context.startActivity(Intent(context, GeneralSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "service_restart_policy",
                title = "Service Restart Policy",
                description = "Choose when to restart the notification service",
                category = "general",
                categoryTitle = "General Settings",
                categoryIcon = "⚙️",
                settingType = SettingType.RADIO_GROUP,
                searchKeywords = listOf("service", "restart", "policy", "crash", "never", "periodic"),
                navigationAction = { context.startActivity(Intent(context, GeneralSettingsActivity::class.java)) }
            ),
            // Note: auto_updates setting is excluded from Play/Store variants
            SettingsItem(
                id = "export_config",
                title = "Export Configuration",
                description = "Export all settings to a file for backup",
                category = "general",
                categoryTitle = "General Settings",
                categoryIcon = "⚙️",
                settingType = SettingType.BUTTON,
                searchKeywords = listOf("export", "backup", "save", "configuration", "settings", "file"),
                navigationAction = { context.startActivity(Intent(context, GeneralSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "import_config",
                title = "Import Configuration",
                description = "Import settings from a backup file",
                category = "general",
                categoryTitle = "General Settings",
                categoryIcon = "⚙️",
                settingType = SettingType.BUTTON,
                searchKeywords = listOf("import", "restore", "load", "configuration", "settings", "file"),
                navigationAction = { context.startActivity(Intent(context, GeneralSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "clear_data",
                title = "Clear All Data",
                description = "Reset all settings and data to defaults",
                category = "general",
                categoryTitle = "General Settings",
                categoryIcon = "⚙️",
                settingType = SettingType.BUTTON,
                searchKeywords = listOf("clear", "reset", "data", "defaults", "factory", "wipe"),
                navigationAction = { context.startActivity(Intent(context, GeneralSettingsActivity::class.java)) }
            ),

            // Behavior Settings
            SettingsItem(
                id = "notification_behavior",
                title = "Notification Behavior",
                description = "Choose how to handle multiple notifications",
                category = "behavior",
                categoryTitle = "Behavior Settings",
                categoryIcon = "🔔",
                settingType = SettingType.CARD,
                searchKeywords = listOf("notification", "behavior", "interrupt", "queue", "skip", "smart", "multiple"),
                navigationAction = { context.startActivity(Intent(context, BehaviorSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "media_behavior",
                title = "Media Behavior",
                description = "Choose how to handle notifications during media playback",
                category = "behavior",
                categoryTitle = "Behavior Settings",
                categoryIcon = "🔔",
                settingType = SettingType.CARD,
                searchKeywords = listOf("media", "music", "video", "playback", "pause", "lower", "ignore", "silence"),
                navigationAction = { context.startActivity(Intent(context, BehaviorSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "shake_to_stop",
                title = "Shake to Stop",
                description = "Stop notifications by shaking your device",
                category = "behavior",
                categoryTitle = "Behavior Settings",
                categoryIcon = "🔔",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("shake", "stop", "gesture", "motion", "silence", "quick"),
                navigationAction = { context.startActivity(Intent(context, BehaviorSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "wave_to_stop",
                title = "Wave to Stop",
                description = "Stop notifications by waving your hand",
                category = "behavior",
                categoryTitle = "Behavior Settings",
                categoryIcon = "🔔",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("wave", "hand", "gesture", "proximity", "sensor", "stop"),
                navigationAction = { context.startActivity(Intent(context, BehaviorSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "custom_app_names",
                title = "Custom App Names",
                description = "Customize how app names are spoken",
                category = "behavior",
                categoryTitle = "Behavior Settings",
                categoryIcon = "🔔",
                settingType = SettingType.CARD,
                searchKeywords = listOf("custom", "app", "names", "speak", "pronounce", "alias"),
                navigationAction = { context.startActivity(Intent(context, BehaviorSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "speech_formatting",
                title = "Speech Formatting",
                description = "Customize how notifications are formatted when spoken",
                category = "behavior",
                categoryTitle = "Behavior Settings",
                categoryIcon = "🔔",
                settingType = SettingType.CARD,
                searchKeywords = listOf("speech", "formatting", "template", "format", "customize", "how", "spoken", "text", "style", "template", "format", "custom", "speech template", "notification format", "speak format"),
                navigationAction = { context.startActivity(Intent(context, BehaviorSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "honour_do_not_disturb",
                title = "Honour Do Not Disturb",
                description = "Respect device Do Not Disturb settings",
                category = "behavior",
                categoryTitle = "Behavior Settings",
                categoryIcon = "🔔",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("honour", "do not disturb", "dnd", "respect", "device", "silent", "quiet", "mode", "disturb", "not disturb"),
                navigationAction = { context.startActivity(Intent(context, BehaviorSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "honour_audio_mode",
                title = "Honour Audio Mode",
                description = "Respect device audio mode settings",
                category = "behavior",
                categoryTitle = "Behavior Settings",
                categoryIcon = "🔔",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("honour", "audio", "mode", "respect", "device", "ringer", "silent", "vibrate", "sound", "mode", "audio mode", "ringer mode", "silent mode", "vibrate mode"),
                navigationAction = { context.startActivity(Intent(context, BehaviorSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "honour_phone_calls",
                title = "Honour Phone Calls",
                description = "Prevent notifications during phone calls",
                category = "behavior",
                categoryTitle = "Behavior Settings",
                categoryIcon = "🔔",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("honour", "phone", "calls", "respect", "call", "telephone", "conversation", "interrupt", "during", "call", "phone call", "telephony", "call state"),
                navigationAction = { context.startActivity(Intent(context, BehaviorSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "notification_deduplication",
                title = "Notification Deduplication",
                description = "Prevent duplicate notifications from being read multiple times",
                category = "behavior",
                categoryTitle = "Behavior Settings",
                categoryIcon = "🔔",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("deduplication", "duplicate", "notification", "same", "multiple", "times", "prevent", "avoid", "repeated", "duplicate notifications", "same notification", "multiple readouts"),
                navigationAction = { context.startActivity(Intent(context, BehaviorSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "dismissal_memory",
                title = "Dismissal Memory",
                description = "Remember and don't read notifications you've dismissed",
                category = "behavior",
                categoryTitle = "Behavior Settings",
                categoryIcon = "🔔",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("dismissal", "memory", "dismissed", "remember", "swiped", "cleared", "notification", "ignore", "skip"),
                navigationAction = { context.startActivity(Intent(context, BehaviorSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "dismissal_memory_timeout",
                title = "Dismissal Memory Timeout",
                description = "How long to remember dismissed notifications",
                category = "behavior",
                categoryTitle = "Behavior Settings",
                categoryIcon = "🔔",
                settingType = SettingType.SEEK_BAR,
                searchKeywords = listOf("dismissal", "memory", "timeout", "duration", "remember", "time", "minutes"),
                navigationAction = { context.startActivity(Intent(context, BehaviorSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "persistent_notification",
                title = "Persistent Notification",
                description = "Show a persistent notification when SpeakThat is active",
                category = "general",
                categoryTitle = "General Settings",
                categoryIcon = "⚙️",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("persistent", "notification", "active", "running", "status", "indicator", "show", "display", "ongoing", "service", "foreground"),
                navigationAction = { context.startActivity(Intent(context, GeneralSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "notification_while_reading",
                title = "Notification While Reading",
                description = "Show a notification when TTS is actively reading",
                category = "general",
                categoryTitle = "General Settings",
                categoryIcon = "⚙️",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("notification", "reading", "tts", "speaking", "active", "shut up", "stop", "cancel", "while", "during", "speech", "text to speech"),
                navigationAction = { context.startActivity(Intent(context, GeneralSettingsActivity::class.java)) }
            ),

            // Voice Settings
            SettingsItem(
                id = "speech_rate",
                title = "Speech Rate",
                description = "Adjust how fast the voice speaks",
                category = "voice",
                categoryTitle = "Voice Settings",
                categoryIcon = "🎙️",
                settingType = SettingType.SEEK_BAR,
                searchKeywords = listOf("speech", "rate", "speed", "fast", "slow", "voice", "tts"),
                navigationAction = { context.startActivity(Intent(context, VoiceSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "pitch",
                title = "Pitch",
                description = "Adjust the pitch of the voice",
                category = "voice",
                categoryTitle = "Voice Settings",
                categoryIcon = "🎙️",
                settingType = SettingType.SEEK_BAR,
                searchKeywords = listOf("pitch", "tone", "voice", "high", "low", "frequency"),
                navigationAction = { context.startActivity(Intent(context, VoiceSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "voice_selection",
                title = "Voice Selection",
                description = "Choose which voice to use for speech",
                category = "voice",
                categoryTitle = "Voice Settings",
                categoryIcon = "🎙️",
                settingType = SettingType.SPINNER,
                searchKeywords = listOf("voice", "selection", "choose", "tts", "speaker", "person"),
                navigationAction = { context.startActivity(Intent(context, VoiceSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "language",
                title = "Language",
                description = "Choose the language for speech",
                category = "voice",
                categoryTitle = "Voice Settings",
                categoryIcon = "🎙️",
                settingType = SettingType.SPINNER,
                searchKeywords = listOf("language", "locale", "speech", "voice", "tts"),
                navigationAction = { context.startActivity(Intent(context, VoiceSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "audio_usage",
                title = "Audio Usage",
                description = "Configure audio usage for speech output",
                category = "voice",
                categoryTitle = "Voice Settings",
                categoryIcon = "🎙️",
                settingType = SettingType.SPINNER,
                searchKeywords = listOf("audio", "usage", "channel", "output", "speaker", "headphones"),
                navigationAction = { context.startActivity(Intent(context, VoiceSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "content_type",
                title = "Content Type",
                description = "Configure content type for speech output",
                category = "voice",
                categoryTitle = "Voice Settings",
                categoryIcon = "🎙️",
                settingType = SettingType.SPINNER,
                searchKeywords = listOf("content", "type", "speech", "audio", "format"),
                navigationAction = { context.startActivity(Intent(context, VoiceSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "preview_voice",
                title = "Preview Voice",
                description = "Test current voice settings",
                category = "voice",
                categoryTitle = "Voice Settings",
                categoryIcon = "🎙️",
                settingType = SettingType.BUTTON,
                searchKeywords = listOf("preview", "test", "voice", "speech", "sample", "demo"),
                navigationAction = { context.startActivity(Intent(context, VoiceSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "tts_engine",
                title = "TTS Engine",
                description = "Choose which text-to-speech engine to use",
                category = "voice",
                categoryTitle = "Voice Settings",
                categoryIcon = "🎙️",
                settingType = SettingType.SPINNER,
                searchKeywords = listOf("engine", "tts", "voice", "speech", "google", "samsung", "text to speech"),
                navigationAction = { context.startActivity(Intent(context, VoiceSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "tts_volume",
                title = "TTS Volume",
                description = "Adjust the volume for text-to-speech",
                category = "voice",
                categoryTitle = "Voice Settings",
                categoryIcon = "🎙️",
                settingType = SettingType.SEEK_BAR,
                searchKeywords = listOf("volume", "tts", "loud", "quiet", "sound", "level"),
                navigationAction = { context.startActivity(Intent(context, VoiceSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "speakerphone_enabled",
                title = "Force Speakerphone",
                description = "Force audio output through speakerphone",
                category = "voice",
                categoryTitle = "Voice Settings",
                categoryIcon = "🎙️",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("speakerphone", "speaker", "audio", "output", "force", "loud"),
                navigationAction = { context.startActivity(Intent(context, VoiceSettingsActivity::class.java)) }
            ),

            // Filter Settings
            SettingsItem(
                id = "app_filtering",
                title = "App Filtering",
                description = "Choose which apps to read notifications from",
                category = "filter",
                categoryTitle = "Filter Settings",
                categoryIcon = "🔍",
                settingType = SettingType.CARD,
                searchKeywords = listOf("app", "filter", "whitelist", "blacklist", "include", "exclude"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "word_filtering",
                title = "Word Filtering",
                description = "Filter out specific words from notifications",
                category = "filter",
                categoryTitle = "Filter Settings",
                categoryIcon = "🔍",
                settingType = SettingType.CARD,
                searchKeywords = listOf("word", "filter", "blacklist", "block", "censor", "hide"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "word_replacements",
                title = "Word Replacements",
                description = "Replace words in notifications with alternatives",
                category = "filter",
                categoryTitle = "Filter Settings",
                categoryIcon = "🔍",
                settingType = SettingType.CARD,
                searchKeywords = listOf("word", "replacement", "replace", "substitute", "change", "alias"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "tidy_speech_section",
                title = context.getString(R.string.tidy_speech_title),
                description = context.getString(R.string.tidy_speech_description),
                category = "filter",
                categoryTitle = context.getString(R.string.title_filter_settings),
                categoryIcon = "🔍",
                settingType = SettingType.CARD,
                searchKeywords = listOf("tidy", "speech", "clean", "cleanup", "emoji", "emojis", "symbol", "symbols", "nonverbal", "non-verbal"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "tidy_speech_remove_emojis",
                title = context.getString(R.string.tidy_speech_remove_emojis_title),
                description = context.getString(R.string.tidy_speech_remove_emojis_description),
                category = "filter",
                categoryTitle = context.getString(R.string.title_filter_settings),
                categoryIcon = "🔍",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("remove", "emoji", "emojis", "symbol", "symbols", "speech", "spoken", "nonverbal", "non-verbal"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "url_handling",
                title = "URL Handling",
                description = "Control how web links are read aloud in notifications",
                category = "filter",
                categoryTitle = "Filter Settings",
                categoryIcon = "🔍",
                settingType = SettingType.CARD,
                searchKeywords = listOf("url", "link", "web", "domain", "website", "http", "https", "www", "shorten", "omit"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "media_filtering",
                title = "Smart Media Notification Filter",
                description = "Filter out actual media control notifications",
                category = "filter",
                categoryTitle = "Filter Settings",
                categoryIcon = "🔍",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("media", "filter", "music", "video", "playback", "youtube", "spotify", "smart"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "persistent_filtering",
                title = "Persistent/Silent Notification Filtering",
                description = "Filter out persistent and silent notifications",
                category = "filter",
                categoryTitle = "Filter Settings",
                categoryIcon = "🔍",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("persistent", "silent", "filter", "ongoing", "background", "system"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "filter_persistent",
                title = "Filter Persistent Notifications",
                description = "Filter out persistent notifications specifically",
                category = "filter",
                categoryTitle = "Filter Settings",
                categoryIcon = "🔍",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("persistent", "filter", "ongoing", "continuous", "notification"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "filter_silent",
                title = "Filter Silent Notifications",
                description = "Filter out silent notifications",
                category = "filter",
                categoryTitle = "Filter Settings",
                categoryIcon = "🔍",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("silent", "filter", "quiet", "no sound", "notification"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "filter_foreground_services",
                title = "Filter Foreground Service Notifications",
                description = "Filter out foreground service notifications",
                category = "filter",
                categoryTitle = "Filter Settings",
                categoryIcon = "🔍",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("foreground", "service", "filter", "background", "running", "notification"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "filter_low_priority",
                title = "Filter Low Priority Notifications",
                description = "Filter out low priority notifications",
                category = "filter",
                categoryTitle = "Filter Settings",
                categoryIcon = "🔍",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("low", "priority", "filter", "importance", "notification"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "filter_system_notifications",
                title = "Filter System Notifications",
                description = "Filter out system notifications",
                category = "filter",
                categoryTitle = "Filter Settings",
                categoryIcon = "🔍",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("system", "filter", "android", "os", "notification"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "media_filter_excepted_apps",
                title = "Media Filter Excepted Apps",
                description = "Apps that are excepted from media filtering",
                category = "filter",
                categoryTitle = "Filter Settings",
                categoryIcon = "🔍",
                settingType = SettingType.CARD,
                searchKeywords = listOf("media", "filter", "exception", "exempt", "app", "whitelist", "music", "video"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "media_filter_important_keywords",
                title = "Media Filter Important Keywords",
                description = "Keywords that make media notifications important",
                category = "filter",
                categoryTitle = "Filter Settings",
                categoryIcon = "🔍",
                settingType = SettingType.CARD,
                searchKeywords = listOf("media", "filter", "keyword", "important", "exception", "music", "video"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),

            SettingsItem(
                id = "export_filter_config",
                title = "Export Filter Configuration",
                description = "Export filter settings to a file",
                category = "filter",
                categoryTitle = "Filter Settings",
                categoryIcon = "🔍",
                settingType = SettingType.BUTTON,
                searchKeywords = listOf("export", "filter", "configuration", "backup", "save"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "import_filter_config",
                title = "Import Filter Configuration",
                description = "Import filter settings from a file",
                category = "filter",
                categoryTitle = "Filter Settings",
                categoryIcon = "🔍",
                settingType = SettingType.BUTTON,
                searchKeywords = listOf("import", "filter", "configuration", "restore", "load"),
                navigationAction = { context.startActivity(Intent(context, FilterSettingsActivity::class.java)) }
            ),

            // Conditional Rules
            SettingsItem(
                id = "conditional_rules",
                title = "Conditional Rules",
                description = "Advanced rules for when notifications are read based on conditions",
                category = "conditional",
                categoryTitle = "Conditional Rules",
                categoryIcon = "🔧",
                settingType = SettingType.CARD,
                searchKeywords = listOf(
                    "conditional", "rules", "advanced", "conditions", "when", "if", "logic",
                    "bluetooth", "bluetooth condition", "bluetooth device", "headphones", "earbuds", "wireless",
                    "schedule", "time", "schedule condition", "time schedule", "daily", "weekly", "hours", "minutes",
                    "wifi", "wifi condition", "network", "wifi network", "ssid", "internet", "connection",
                    "screen", "screen condition", "screen state", "screen on", "screen off", "display", "awake",
                    "smart", "intelligent", "automation", "automatic", "context", "environment", "situation",
                    "filter", "filtering", "conditional filtering", "advanced filtering", "smart filtering",
                    "device", "environment", "surroundings", "location", "time", "schedule", "calendar",
                    "headphones", "earbuds", "bluetooth headset", "wireless headphones", "audio device",
                    "network", "wifi network", "cellular", "mobile data", "internet connection",
                    "screen state", "display state", "device state", "awake", "sleep", "locked", "unlocked"
                ),
                navigationAction = { context.startActivity(Intent(context, RulesActivity::class.java)) }
            ),

            // Development Settings
            SettingsItem(
                id = "debug_logging",
                title = "Debug Logging",
                description = "Enable detailed logging for debugging",
                category = "development",
                categoryTitle = "Development Settings",
                categoryIcon = "🛠️",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("debug", "logging", "log", "verbose", "detailed", "troubleshoot"),
                navigationAction = { context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "verbose_logging",
                title = "Verbose Logging",
                description = "Enable verbose logging output",
                category = "development",
                categoryTitle = "Development Settings",
                categoryIcon = "🛠️",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("verbose", "logging", "log", "debug", "detailed", "extra"),
                navigationAction = { context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "log_filters",
                title = "Log Filters",
                description = "Log filter operations and decisions",
                category = "development",
                categoryTitle = "Development Settings",
                categoryIcon = "🛠️",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("log", "filter", "filtering", "debug", "operations"),
                navigationAction = { context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "log_notifications",
                title = "Log Notifications",
                description = "Log notification processing details",
                category = "development",
                categoryTitle = "Development Settings",
                categoryIcon = "🛠️",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("log", "notification", "processing", "debug", "details"),
                navigationAction = { context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "log_user_actions",
                title = "Log User Actions",
                description = "Log user interactions and actions",
                category = "development",
                categoryTitle = "Development Settings",
                categoryIcon = "🛠️",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("log", "user", "action", "interaction", "debug", "click"),
                navigationAction = { context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "log_system_events",
                title = "Log System Events",
                description = "Log system events and changes",
                category = "development",
                categoryTitle = "Development Settings",
                categoryIcon = "🛠️",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("log", "system", "event", "debug", "android", "os"),
                navigationAction = { context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "disable_media_fallback",
                title = "Disable Media Fallback",
                description = "Disable media playback fallback mechanisms",
                category = "development",
                categoryTitle = "Development Settings",
                categoryIcon = "🛠️",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("disable", "media", "fallback", "audio", "debug", "experimental"),
                navigationAction = { context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "enable_legacy_ducking",
                title = "Enable Legacy Ducking",
                description = "Use legacy audio ducking behavior",
                category = "development",
                categoryTitle = "Development Settings",
                categoryIcon = "🛠️",
                settingType = SettingType.SWITCH,
                searchKeywords = listOf("legacy", "ducking", "audio", "volume", "debug", "old"),
                navigationAction = { context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "test_notifications",
                title = "Test Notifications",
                description = "Send test notifications to verify settings",
                category = "development",
                categoryTitle = "Development Settings",
                categoryIcon = "🛠️",
                settingType = SettingType.BUTTON,
                searchKeywords = listOf("test", "notification", "verify", "check", "demo"),
                navigationAction = { context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "view_logs",
                title = "View Logs",
                description = "View detailed application logs",
                category = "development",
                categoryTitle = "Development Settings",
                categoryIcon = "🛠️",
                settingType = SettingType.BUTTON,
                searchKeywords = listOf("view", "logs", "log", "history", "debug", "troubleshoot"),
                navigationAction = { context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java)) }
            ),
            SettingsItem(
                id = "clear_logs",
                title = "Clear Logs",
                description = "Clear all application logs",
                category = "development",
                categoryTitle = "Development Settings",
                categoryIcon = "🛠️",
                settingType = SettingType.BUTTON,
                searchKeywords = listOf("clear", "logs", "log", "wipe", "reset"),
                navigationAction = { context.startActivity(Intent(context, DevelopmentSettingsActivity::class.java)) }
            ),

            // Support & Feedback
            SettingsItem(
                id = "feature_request",
                title = "Feature Request",
                description = "Request new features for the app",
                category = "support",
                categoryTitle = "Support & Feedback",
                categoryIcon = "💬",
                settingType = SettingType.BUTTON,
                searchKeywords = listOf("feature", "request", "suggestion", "idea", "new", "improvement"),
                navigationAction = { context.startActivity(Intent(context, SupportActivity::class.java)) }
            ),
            SettingsItem(
                id = "bug_report",
                title = "Bug Report",
                description = "Report bugs or issues with the app",
                category = "support",
                categoryTitle = "Support & Feedback",
                categoryIcon = "💬",
                settingType = SettingType.BUTTON,
                searchKeywords = listOf("bug", "report", "issue", "problem", "error", "crash"),
                navigationAction = { context.startActivity(Intent(context, SupportActivity::class.java)) }
            ),
            SettingsItem(
                id = "general_support",
                title = "General Support",
                description = "Get help with using the app",
                category = "support",
                categoryTitle = "Support & Feedback",
                categoryIcon = "💬",
                settingType = SettingType.BUTTON,
                searchKeywords = listOf("support", "help", "assistance", "question", "guide"),
                navigationAction = { context.startActivity(Intent(context, SupportActivity::class.java)) }
            ),

            // Re-run Onboarding
            SettingsItem(
                id = "re_run_onboarding",
                title = "Re-run Onboarding",
                description = "See the app introduction again",
                category = "onboarding",
                categoryTitle = "Re-run Onboarding",
                categoryIcon = "🔄",
                settingType = SettingType.BUTTON,
                searchKeywords = listOf("onboarding", "tutorial", "introduction", "guide", "help", "learn"),
                navigationAction = { context.startActivity(Intent(context, OnboardingActivity::class.java)) }
            )
        )
    }
}
