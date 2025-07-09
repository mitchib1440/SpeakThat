package com.micoyc.speakthat

import android.app.Notification
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Utility class to detect media notifications that contain playback controls.
 * 
 * Media notifications typically contain:
 * - Play/pause controls
 * - Progress indicators
 * - Track information
 * - Media-specific text patterns
 */
class MediaNotificationDetector {
    
    companion object {
        private const val TAG = "MediaNotificationDetector"
        
        // Common media control text patterns
        private val MEDIA_CONTROL_PATTERNS = setOf(
            "▶️", "⏸️", "⏯️", "⏹️", "⏭️", "⏮️", "⏪", "⏩", // Unicode media controls
            "play", "pause", "stop", "next", "previous", "skip", "resume",
            "playing", "paused", "stopped", "buffering", "loading",
            "now playing", "currently playing", "track", "song", "album",
            "artist", "duration", "time", "progress", "volume", "mute",
            "unmute", "shuffle", "repeat", "loop", "queue", "playlist",
            // YouTube-specific patterns
            "video", "watching", "streaming", "live", "broadcast", "upload",
            "subscriber", "view", "like", "comment", "share", "download"
        )
        
        // Media app package names (common media players)
        private val MEDIA_APPS = setOf(
            "com.spotify.music",
            "com.google.android.youtube",
            "app.revanced.android.youtube",  // Updated YouTube ReVanced package name
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "com.disney.disneyplus",
            "com.hulu.plus",
            "com.vimeo.android",
            "com.soundcloud.android",
            "com.pandora.android",
            "com.iheartradio.android",
            "com.audible.application",
            "com.google.android.apps.music",
            "com.apple.android.music",
            "com.plexapp.android",
            "com.emby.embyserver",
            "com.jellyfin.jellyfinmobile",
            "com.vanced.android.youtube",
            "com.revanced.android.youtube",  // Keep old package name for compatibility
            "com.google.android.apps.youtube.music",
            "com.spotify.lite",
            "com.deezer.android",
            "com.tidal.android",
            "com.apple.android.music",
            "com.tencent.qqmusic",
            "com.netease.cloudmusic",
            "com.kugou.android",
            "com.kuwo.kwmusic",
            "com.xiami.music",
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "com.sina.weibo",
            "com.zhihu.android",
            "com.ss.android.article.news",
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite",
            "com.ss.android.ugc.tiktok",
            "com.instagram.android",
            "com.facebook.katana",
            "com.facebook.orca",
            "com.twitter.android",
            "com.snapchat.android",
            "com.whatsapp",
            "com.telegram.messenger",
            "org.telegram.messenger",
            "com.discord",
            "com.slack",
            "com.microsoft.teams",
            "com.skype.raider",
            "com.zoom.us",
            "us.zoom.videomeetings",
            "com.google.android.apps.meetings",
            "com.google.android.apps.docs.editors.docs",
            "com.google.android.apps.docs.editors.sheets",
            "com.google.android.apps.docs.editors.slides",
            "com.microsoft.office.word",
            "com.microsoft.office.excel",
            "com.microsoft.office.powerpoint",
            "com.adobe.reader",
            "com.google.android.apps.docs",
            "com.dropbox.android",
            "com.box.android",
            "com.google.android.apps.drive",
            "com.google.android.apps.photos",
            "com.google.android.apps.maps",
            "com.waze",
            "com.ubercab",
            "com.lyft.android",
            "com.airbnb.android",
            "com.booking",
            "com.tripadvisor.tripadvisor",
            "com.yelp.android",
            "com.google.android.apps.translate",
            "com.duolingo",
            "com.memrise.android.adaptation",
            "com.babbel.mobile.android.en",
            "com.rosettastone.mobile.CoursePlayer",
            "com.ef.efenglishtown",
            "com.busuu.android.sec",
            "com.lingodeer.android",
            "com.hellotalk",
            "com.tandem",
            "com.speak",
            "com.duolingo",
            "com.memrise.android.adaptation",
            "com.babbel.mobile.android.en",
            "com.rosettastone.mobile.CoursePlayer",
            "com.ef.efenglishtown",
            "com.busuu.android.sec",
            "com.lingodeer.android",
            "com.hellotalk",
            "com.tandem",
            "com.speak"
        )
        
        // Notification categories that typically indicate media
        private val MEDIA_CATEGORIES = setOf(
            Notification.CATEGORY_SERVICE,
            "media_session",
            "media_control",
            "playback",
            "video",
            "streaming",
            "youtube"
        )
        
        /**
         * Check if a notification contains media controls
         */
        fun isMediaNotification(sbn: StatusBarNotification): Boolean {
            val notification = sbn.notification
            val packageName = sbn.packageName
            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
            val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() ?: ""
            Log.d(TAG, "[isMediaNotification] Package: $packageName | Title: $title | Text: $text | BigText: $bigText | Summary: $summaryText | Info: $infoText")
            
            // Debug logging for YouTube-related apps to help identify package names
            if (packageName.contains("youtube") || packageName.contains("revanced") || packageName.contains("vanced")) {
                Log.d(TAG, "YouTube-related app detected - Package: $packageName, App: $title")
            }
            
            // Only block if it has media session flags, media controls, or seekbar
            if (hasMediaSessionFlags(notification)) {
                Log.d(TAG, "[isMediaNotification] Detected media session flags for: $packageName (return true)")
                return true
            }
            if (containsMediaControls(notification)) {
                Log.d(TAG, "[isMediaNotification] Detected media controls in text for: $packageName (return true)")
                return true
            }
            if (hasSeekbar(notification)) {
                Log.d(TAG, "[isMediaNotification] Detected seekbar/progress bar in notification for: $packageName (return true)")
                return true
            }
            // Special check for YouTube notifications that don't have media session flags
            if (isYouTubeNotification(notification, packageName)) {
                Log.d(TAG, "[isMediaNotification] Detected YouTube notification without media session flags (return true)")
                return true
            }
            Log.d(TAG, "[isMediaNotification] No media detection for: $packageName (return false)")
            return false
        }
        
        /**
         * Check if the package is a known media app
         */
        private fun isMediaApp(packageName: String): Boolean {
            return MEDIA_APPS.contains(packageName)
        }
        
        /**
         * Check if notification has media-related category
         */
        private fun hasMediaCategory(notification: Notification): Boolean {
            val category = notification.category
            return category != null && MEDIA_CATEGORIES.contains(category)
        }
        
        /**
         * Check if notification text contains media control patterns
         */
        private fun containsMediaControls(notification: Notification): Boolean {
            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
            val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() ?: ""
            
            val fullText = "$title $text $bigText $summaryText $infoText".lowercase()
            
            // Check for media control patterns
            for (pattern in MEDIA_CONTROL_PATTERNS) {
                if (fullText.contains(pattern.lowercase())) {
                    Log.d(TAG, "Found media control pattern: $pattern")
                    return true
                }
            }
            
            return false
        }
        
        /**
         * Check for media session flags and extras
         */
        private fun hasMediaSessionFlags(notification: Notification): Boolean {
            val extras = notification.extras
            
            // Check for media session extras
            val hasMediaSession = extras.containsKey("android.mediaSession")
            val hasMediaController = extras.containsKey("android.mediaController")
            val hasPlaybackState = extras.containsKey("android.playbackState")
            
            // Check for custom media flags
            val hasCustomMediaFlag = extras.getBoolean("media_control", false) ||
                                   extras.getBoolean("playback_control", false) ||
                                   extras.getBoolean("media_session", false)
            
            // Check for YouTube-specific media flags
            val hasYouTubeMediaFlag = extras.getBoolean("youtube_media", false) ||
                                     extras.getBoolean("video_playback", false) ||
                                     extras.getBoolean("streaming", false)
            
            if (hasMediaSession || hasMediaController || hasPlaybackState || hasCustomMediaFlag || hasYouTubeMediaFlag) {
                Log.d(TAG, "Detected media session flags")
                return true
            }
            
            return false
        }
        
        /**
         * Check if notification has a seekbar/progress bar (hybrid detection)
         */
        private fun hasSeekbar(notification: Notification): Boolean {
            val extras = notification.extras
            val hasProgress = extras.containsKey(Notification.EXTRA_PROGRESS)
            val hasProgressMax = extras.containsKey(Notification.EXTRA_PROGRESS_MAX)
            val progress = extras.getInt(Notification.EXTRA_PROGRESS, -1)
            val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1)
            Log.d(TAG, "[hasSeekbar] hasProgress: $hasProgress, hasProgressMax: $hasProgressMax, progress: $progress, progressMax: $progressMax")
            // If progress and max are set, it's likely a seekbar
            val result = hasProgress && hasProgressMax && progressMax > 0
            Log.d(TAG, "[hasSeekbar] Seekbar detected: $result")
            return result
        }
        
        /**
         * Special check for YouTube notifications that don't have media session flags
         * but are still media-related (video titles, channel names, etc.)
         */
        private fun isYouTubeNotification(notification: Notification, packageName: String): Boolean {
            // Only check YouTube apps
            if (!packageName.contains("youtube")) {
                return false
            }
            
            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            
            val fullText = "$title $text $bigText".lowercase()
            
            // Check for YouTube video patterns
            val videoPatterns = listOf(
                "episode", "season", "part", "chapter", "volume",
                "【", "】", "(", ")", ":", "•", "|", "-",
                "animation", "reaction", "stream", "live", "premiere",
                "upload", "video", "content", "channel", "creator"
            )
            
            // Check if the notification contains video title patterns
            for (pattern in videoPatterns) {
                if (fullText.contains(pattern.lowercase())) {
                    Log.d(TAG, "Found YouTube video pattern: $pattern")
                    return true
                }
            }
            
            // Check if the notification has a channel name (usually after a colon or dash)
            if (fullText.contains(":") || fullText.contains("•") || fullText.contains("|")) {
                // This looks like a video notification with channel name
                Log.d(TAG, "Detected YouTube video notification with channel name")
                return true
            }
            
            return false
        }
        
        /**
         * Get detailed information about why a notification was classified as media
         */
        fun getMediaDetectionReason(sbn: StatusBarNotification): String {
            val notification = sbn.notification
            val packageName = sbn.packageName
            val reasons = mutableListOf<String>()
            
            if (isMediaApp(packageName)) {
                reasons.add("Known media app")
            }
            
            if (hasMediaCategory(notification)) {
                reasons.add("Media category: ${notification.category}")
            }
            
            if (containsMediaControls(notification)) {
                reasons.add("Contains media control patterns")
            }
            
            if (hasMediaSessionFlags(notification)) {
                reasons.add("Has media session flags")
            }
            
            if (isYouTubeNotification(notification, packageName)) {
                reasons.add("YouTube video notification")
            }
            
            return reasons.joinToString(", ")
        }
        
        /**
         * Check if a notification should be filtered based on user preferences
         * This is a separate method to allow for user customization
         */
        fun shouldFilterMediaNotification(sbn: StatusBarNotification, userPreferences: MediaFilterPreferences): Boolean {
            if (!userPreferences.isMediaFilteringEnabled) {
                return false
            }
            
            val isMedia = isMediaNotification(sbn)
            if (!isMedia) {
                return false
            }
            
            val packageName = sbn.packageName
            
            // Check if this app is in the exception list
            if (userPreferences.exceptedApps.contains(packageName)) {
                Log.d(TAG, "Media notification from excepted app: $packageName")
                return false
            }
            
            // Check if notification contains important keywords (like "reply", "comment", etc.)
            if (containsImportantKeywords(sbn.notification, userPreferences.importantKeywords)) {
                Log.d(TAG, "Media notification contains important keywords")
                return false
            }
            
            return true
        }
        
        /**
         * Check if notification contains important keywords that should not be filtered
         */
        private fun containsImportantKeywords(notification: Notification, importantKeywords: Set<String>): Boolean {
            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
            val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString() ?: ""
            
            val fullText = "$title $text $bigText $summaryText $infoText".lowercase()
            
            for (keyword in importantKeywords) {
                if (fullText.contains(keyword.lowercase())) {
                    Log.d(TAG, "Found important keyword: $keyword")
                    return true
                }
            }
            
            return false
        }
    }
    
    /**
     * User preferences for media notification filtering
     */
    data class MediaFilterPreferences(
        val isMediaFilteringEnabled: Boolean = false,
        val exceptedApps: Set<String> = emptySet(),
        val importantKeywords: Set<String> = setOf(
            "reply", "comment", "mention", "like", "follow", "subscribe",
            "upload", "download", "share", "save", "favorite", "bookmark",
            "notification", "alert", "message", "dm", "direct message",
            "live", "streaming", "broadcast", "new video", "new post",
            "trending", "viral", "popular", "recommended", "suggestion",
            // YouTube-specific important keywords
            "replied", "commented", "liked", "subscribed", "new subscriber",
            "channel", "uploaded", "published", "scheduled", "premier",
            "community", "poll", "story", "shorts", "reels"
        )
    )
} 