/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.content.Context
import com.micoyc.speakthat.settings.BehaviorSettingsActivity

/**
 * Centralizes master switch plus Honour System Modes so notification ingest, clock ticks,
 * diagnostics, and JIT speech checks stay aligned.
 */
object GlobalReadoutSuppression {

    @JvmStatic
    fun shouldGloballySuppressSpeakThatReadouts(context: Context): Boolean {
        return getGlobalSuppressionReason(context) != null
    }

    /**
     * @return A short English reason if readouts should be suppressed, or null if they may proceed.
     */
    @JvmStatic
    fun getGlobalSuppressionReason(context: Context): String? {
        if (!MainActivity.isMasterSwitchEnabled(context)) {
            return "master_switch"
        }
        if (BehaviorSettingsActivity.shouldHonourDoNotDisturb(context)) {
            return "do_not_disturb"
        }
        if (BehaviorSettingsActivity.getAudioModeBlockReason(context) != null) {
            return "audio_mode"
        }
        if (BehaviorSettingsActivity.shouldHonourPhoneCalls(context)) {
            return "phone_call"
        }
        return null
    }
}
