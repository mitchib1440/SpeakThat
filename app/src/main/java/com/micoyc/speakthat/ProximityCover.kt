/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * SpeakThat! Copyright © Mitchell Bell
 */

package com.micoyc.speakthat

import android.hardware.Sensor

/** Standard "sensor covered" / near detection for TYPE_PROXIMITY (wave-to-stop, pocket mode). */
object ProximityCover {
    private const val MAX_CM_FOR_COVERED = 5f

    /**
     * True when the reading indicates near/covered: closer than the sensor's max range and within 5 cm
     * (caps devices that report an unusually large maximum range when uncovered).
     */
    @JvmStatic
    fun isCovered(value: Float, sensor: Sensor?): Boolean {
        val maxR = sensor?.maximumRange ?: return false
        return value < maxR && value < MAX_CM_FOR_COVERED
    }
}
