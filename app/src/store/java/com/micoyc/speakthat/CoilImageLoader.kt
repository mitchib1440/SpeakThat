/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.widget.ImageView

/**
 * Store-specific version of CoilImageLoader that never makes network requests
 * This ensures privacy-conscious users don't have any network activity for icons
 */
object CoilImageLoader {
    @JvmStatic
    fun loadSvg(imageView: ImageView, url: String) {
        // Store variant: Always use local fallback icons for privacy
        // No network requests are made, even if iconSlug is provided
        imageView.setImageResource(R.drawable.ic_app_unknown)
    }
} 