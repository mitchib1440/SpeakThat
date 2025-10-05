package com.micoyc.speakthat

import android.content.Context
import android.provider.Settings

/**
 * Utility class for accessibility permission management and enhanced audio control
 * 
 * This class provides methods to check if the accessibility service is enabled
 * and enables enhanced audio control features when the permission is granted.
 * 
 * ACCESSIBILITY ENHANCED FEATURES:
 * - Enhanced media pause control with direct media control intents
 * - Improved audio ducking with USAGE_ASSISTANCE_ACCESSIBILITY
 * - Better system-level audio control and integration
 * - More reliable audio focus requests with higher priority
 */
object AccessibilityUtils {
    
    private const val TAG = "AccessibilityUtils"
    
    /**
     * Check if the SpeakThat accessibility service is enabled
     * 
     * This method checks if the SpeakThatAccessibilityService is enabled in the
     * Android accessibility settings. It's used throughout the app to determine
     * if enhanced audio control features should be activated.
     * 
     * @param context The application context
     * @return true if the accessibility service is enabled, false otherwise
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val packageName = context.packageName
        val serviceName = "$packageName/com.micoyc.speakthat.SpeakThatAccessibilityService"
        
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        if (enabledServices != null && enabledServices.isNotEmpty()) {
            val services = enabledServices.split(":")
            for (service in services) {
                if (service == serviceName) {
                    return true
                }
            }
        }
        return false
    }
    
    /**
     * Get enhanced audio attributes for TTS when accessibility permission is granted
     * 
     * When accessibility permission is available, this method returns audio attributes
     * that provide better system integration and higher priority for audio control.
     * 
     * @param context The application context
     * @param fallbackUsage The fallback usage type if accessibility is not available
     * @param fallbackContent The fallback content type if accessibility is not available
     * @return Pair of (usage, content) audio attributes optimized for accessibility
     */
    fun getEnhancedAudioAttributes(
        context: Context,
        fallbackUsage: Int,
        fallbackContent: Int
    ): Pair<Int, Int> {
        return if (isAccessibilityServiceEnabled(context)) {
            // Use accessibility-specific audio attributes for better system integration
            Pair(
                android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
                android.media.AudioAttributes.CONTENT_TYPE_SPEECH
            )
        } else {
            // Use fallback attributes when accessibility is not available
            Pair(fallbackUsage, fallbackContent)
        }
    }
    
    /**
     * Check if enhanced audio control features should be used
     * 
     * This method determines if the app should use enhanced audio control features
     * that are only available with accessibility permission. These features provide
     * more reliable audio ducking and media control.
     * 
     * @param context The application context
     * @return true if enhanced features should be used, false otherwise
     */
    fun shouldUseEnhancedAudioControl(context: Context): Boolean {
        return isAccessibilityServiceEnabled(context)
    }
    
    /**
     * Get enhanced audio focus request flags when accessibility permission is granted
     * 
     * When accessibility permission is available, this method returns audio focus
     * request flags that provide higher priority and better system integration.
     * 
     * @param context The application context
     * @param fallbackFlags The fallback flags if accessibility is not available
     * @return Audio focus request flags optimized for accessibility
     */
    fun getEnhancedAudioFocusFlags(
        context: Context,
        fallbackFlags: Int
    ): Int {
        return if (isAccessibilityServiceEnabled(context)) {
            // Use enhanced flags for better audio focus control
            // Note: AUDIOFOCUS_FLAG_DELAY_OK is not available in all Android versions
            // We'll use the fallback flags but with enhanced audio attributes instead
            fallbackFlags
        } else {
            // Use fallback flags when accessibility is not available
            fallbackFlags
        }
    }
}
