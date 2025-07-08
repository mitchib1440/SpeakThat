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
            "unmute", "shuffle", "repeat", "loop", "queue", "playlist"
        )
        
        // Media app package names (common media players)
        private val MEDIA_APPS = setOf(
            "com.spotify.music",
            "com.google.android.youtube",
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
            "com.revanced.android.youtube",
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
            "playback"
        )
        
        /**
         * Check if a notification contains media controls
         */
        fun isMediaNotification(sbn: StatusBarNotification): Boolean {
            val notification = sbn.notification
            val packageName = sbn.packageName
            
            // Check if it's a known media app
            if (isMediaApp(packageName)) {
                Log.d(TAG, "Detected media app: $packageName")
                return true
            }
            
            // Check notification category
            if (hasMediaCategory(notification)) {
                Log.d(TAG, "Detected media category for: $packageName")
                return true
            }
            
            // Check for media control patterns in text
            if (containsMediaControls(notification)) {
                Log.d(TAG, "Detected media controls in text for: $packageName")
                return true
            }
            
            // Check for media session flags
            if (hasMediaSessionFlags(notification)) {
                Log.d(TAG, "Detected media session flags for: $packageName")
                return true
            }
            
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
            
            val fullText = "$title $text $bigText".lowercase()
            
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
            
            if (hasMediaSession || hasMediaController || hasPlaybackState || hasCustomMediaFlag) {
                Log.d(TAG, "Detected media session flags")
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
            
            val fullText = "$title $text $bigText".lowercase()
            
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
            "trending", "viral", "popular", "recommended", "suggestion"
        )
    )
} 