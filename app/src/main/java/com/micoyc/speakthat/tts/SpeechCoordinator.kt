/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat.tts

/**
 * Coordinates exclusive speech ownership between notification readouts and summary playback,
 * both of which share [SpeakThatTtsManager].
 */
object SpeechCoordinator {

    @Volatile
    private var summaryActive = false

    @JvmStatic
    fun isSummaryActive(): Boolean = summaryActive

    @JvmStatic
    fun setSummaryActive(active: Boolean) {
        summaryActive = active
    }
}
