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