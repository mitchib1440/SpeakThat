/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Shared constants and helpers for the DIY "Broadcast to Stop" feature.
 * External apps can abort an in-progress readout by sending [ACTION_ABORT_READING]
 * with an Intent extra named [EXTRA_SECRET] that matches the stored secret.
 */
object BroadcastToStop {
    const val PREFS_NAME = "SpeakThatPrefs"
    const val KEY_ENABLED = "broadcast_to_stop_enabled"
    const val KEY_SECRET = "broadcast_to_stop_secret"

    const val ACTION_ABORT_READING = "com.micoyc.speakthat.action.ABORT_READING"
    const val EXTRA_SECRET = "secret"
    const val PACKAGE_NAME = "com.micoyc.speakthat"

    private const val SECRET_BYTE_LENGTH = 18

    @JvmStatic
    fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    @JvmStatic
    fun getSecret(context: Context): String {
        return prefs(context).getString(KEY_SECRET, "") ?: ""
    }

    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    @JvmStatic
    fun setSecret(context: Context, secret: String) {
        prefs(context).edit().putString(KEY_SECRET, secret).apply()
    }

    /**
     * Returns the existing secret, or generates and persists a new one if blank.
     */
    @JvmStatic
    fun ensureSecret(context: Context): String {
        val existing = getSecret(context)
        if (existing.isNotEmpty()) {
            return existing
        }
        val generated = generateSecret()
        setSecret(context, generated)
        return generated
    }

    @JvmStatic
    fun generateSecret(): String {
        val bytes = ByteArray(SECRET_BYTE_LENGTH)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    @JvmStatic
    fun secretsMatch(expected: String, provided: String?): Boolean {
        if (expected.isEmpty() || provided.isNullOrEmpty()) {
            return false
        }
        val expectedBytes = expected.toByteArray(Charsets.UTF_8)
        val providedBytes = provided.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(expectedBytes, providedBytes)
    }
}
