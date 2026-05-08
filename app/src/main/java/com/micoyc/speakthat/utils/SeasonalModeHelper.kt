package com.micoyc.speakthat.utils

import android.content.Context
import java.util.Calendar

object SeasonalModeHelper {
    const val PREF_ENABLE_FESTIVE_MODE = "enable_festive_mode"
    const val PREF_DEFAULT_ENABLE_FESTIVE_MODE = true

    @JvmStatic
    fun isDecember(): Boolean {
        return Calendar.getInstance().get(Calendar.MONTH) == Calendar.DECEMBER
    }

    @JvmStatic
    fun isFestiveEnabled(context: Context): Boolean {
        if (!isDecember()) return false
        val prefs = context.getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_ENABLE_FESTIVE_MODE, PREF_DEFAULT_ENABLE_FESTIVE_MODE)
    }
}
