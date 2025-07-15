package com.micoyc.speakthat

import android.widget.ImageView
import coil.load
import coil.decode.SvgDecoder

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