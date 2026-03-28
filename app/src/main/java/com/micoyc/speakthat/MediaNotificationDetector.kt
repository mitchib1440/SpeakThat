/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.app.Notification
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Smart utility class to detect actual media control notifications.
 * 
 * This detector focuses on reliable indicators of media controls:
 * - Media session flags (the most reliable method)
 * - Progress bars/seekbars (actual media controls)
 * - Notification categories (system-level media indicators)
 * 
 * Removed unreliable text-based detection to prevent false positives.
 */
class MediaNotificationDetector {
    
    companion object {
        private const val TAG = "MediaNotificationDetector"
        
        // Only check for actual media session flags and progress indicators
        // Removed unreliable text pattern matching
        
        /**
         * Check if a notification contains actual media controls
         * Uses only reliable detection methods to prevent false positives
         */
        fun isMediaNotification(sbn: StatusBarNotification): Boolean {
            val notification = sbn.notification
            val packageName = sbn.packageName
            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            
            Log.d(TAG, "[isMediaNotification] Package: $packageName | Title: $title | Text: $text")
            
            // Method 1: Check for actual media session flags (most reliable)
            if (hasMediaSessionFlags(notification)) {
                Log.d(TAG, "[isMediaNotification] Detected media session flags for: $packageName (return true)")
                return true
            }
            
            // Method 2: Check for progress bar/seekbar (actual media controls)
            if (hasSeekbar(notification)) {
                Log.d(TAG, "[isMediaNotification] Detected seekbar/progress bar in notification for: $packageName (return true)")
                return true
            }
            
            // Method 3: Check for media notification category (system-level indicator)
            if (hasMediaCategory(notification)) {
                Log.d(TAG, "[isMediaNotification] Detected media category for: $packageName (return true)")
                return true
            }
            
            Log.d(TAG, "[isMediaNotification] No reliable media detection for: $packageName (return false)")
            return false
        }
        
        /**
         * Check for actual media session flags and extras
         * This is the most reliable method for detecting media controls
         */
        private fun hasMediaSessionFlags(notification: Notification): Boolean {
            val extras = notification.extras
            
            // Check for official Android media session extras
            val hasMediaSession = extras.containsKey("android.mediaSession")
            val hasMediaController = extras.containsKey("android.mediaController")
            val hasPlaybackState = extras.containsKey("android.playbackState")
            
            // Check for media session object (most reliable indicator)
            val mediaSession = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable("android.mediaSession", android.os.Parcelable::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable("android.mediaSession")
            }
            val mediaController = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable("android.mediaController", android.os.Parcelable::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable("android.mediaController")
            }
            
            if (hasMediaSession || hasMediaController || hasPlaybackState || mediaSession != null || mediaController != null) {
                Log.d(TAG, "Detected actual media session flags")
                return true
            }
            
            return false
        }
        
        /**
         * Check if notification has a seekbar/progress bar (actual media controls)
         */
        private fun hasSeekbar(notification: Notification): Boolean {
            val extras = notification.extras
            val hasProgress = extras.containsKey(Notification.EXTRA_PROGRESS)
            val hasProgressMax = extras.containsKey(Notification.EXTRA_PROGRESS_MAX)
            val progress = extras.getInt(Notification.EXTRA_PROGRESS, -1)
            val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1)
            
            Log.d(TAG, "[hasSeekbar] hasProgress: $hasProgress, hasProgressMax: $hasProgressMax, progress: $progress, progressMax: $progressMax")
            
            // Only consider it a seekbar if both progress and max are set and max > 0
            val result = hasProgress && hasProgressMax && progressMax > 0
            Log.d(TAG, "[hasSeekbar] Seekbar detected: $result")
            return result
        }
        
        /**
         * Check if notification has media-related category
         * System-level indicator that's more reliable than text patterns
         */
        private fun hasMediaCategory(notification: Notification): Boolean {
            val category = notification.category
            if (category == null) return false
            
            // Only check for actual media categories, not generic service categories
            val mediaCategories = setOf(
                "media_session",
                "media_control", 
                "playback"
            )
            
            val isMediaCategory = mediaCategories.contains(category)
            if (isMediaCategory) {
                Log.d(TAG, "Detected media category: $category")
            }
            
            return isMediaCategory
        }
        
        /**
         * Get detailed information about why a notification was classified as media
         */
        fun getMediaDetectionReason(sbn: StatusBarNotification): String {
            val notification = sbn.notification
            val reasons = mutableListOf<String>()
            
            if (hasMediaSessionFlags(notification)) {
                reasons.add("Has media session flags")
            }
            
            if (hasSeekbar(notification)) {
                reasons.add("Has progress bar/seekbar")
            }
            
            if (hasMediaCategory(notification)) {
                reasons.add("Media category: ${notification.category}")
            }
            
            return reasons.joinToString(", ")
        }
        
        /**
         * Filter when the feature is enabled and the notification is a native media-style notification.
         */
        fun shouldFilterMediaNotification(sbn: StatusBarNotification, userPreferences: MediaFilterPreferences): Boolean {
            return userPreferences.isMediaFilteringEnabled && isMediaNotification(sbn)
        }
    }
    
    /** Master toggle only; classification uses [isMediaNotification]. */
    data class MediaFilterPreferences(
        val isMediaFilteringEnabled: Boolean = false
    )
} 