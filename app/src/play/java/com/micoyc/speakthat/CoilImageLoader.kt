package com.micoyc.speakthat

import android.widget.ImageView

/**
 * Play-specific version of CoilImageLoader that mirrors the store behavior:
 * no network icon loading; always use local fallback for privacy.
 */
object CoilImageLoader {
    @JvmStatic
    fun loadSvg(imageView: ImageView, url: String) {
        // Play variant: Always use local fallback icon; no network requests.
        imageView.setImageResource(R.drawable.ic_app_unknown)
    }
}

