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