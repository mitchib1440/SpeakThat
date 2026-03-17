/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.widget.ImageView
import coil.load
import coil.decode.SvgDecoder

/**
 * GitHub-specific version of CoilImageLoader that uses Coil for network image loading
 * This allows users to see app icons from the Simple Icons CDN
 */
object CoilImageLoader {
    @JvmStatic
    fun loadSvg(imageView: ImageView, url: String) {
        imageView.load(url) {
            decoderFactory { result, options, _ -> SvgDecoder(result.source, options) }
            placeholder(R.drawable.ic_app_unknown)
            error(R.drawable.ic_app_unknown)
        }
    }
} 