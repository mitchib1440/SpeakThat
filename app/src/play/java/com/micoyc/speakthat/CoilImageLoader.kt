/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

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

