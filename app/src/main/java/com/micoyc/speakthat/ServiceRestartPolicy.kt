/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0.
 * SpeakThat! Copyright © Mitchell Bell
 */

package com.micoyc.speakthat

import android.content.SharedPreferences

/**
 * Canonical storage for notification service restart behavior.
 * Values are always English literals: never | crash | periodic
 */
object ServiceRestartPolicy {

    const val PREFS_KEY = "service_restart_policy"
    private const val LEGACY_PREFS_KEY = "restart_policy"

    const val VALUE_NEVER = "never"
    const val VALUE_CRASH = "crash"
    const val VALUE_PERIODIC = "periodic"

    const val DEFAULT_POLICY = VALUE_CRASH

    private val ALLOWED = setOf(VALUE_NEVER, VALUE_CRASH, VALUE_PERIODIC)

    @JvmStatic
    fun canonicalFromStored(primary: String?, legacy: String?): String {
        val p = primary?.trim()?.lowercase()
        if (p in ALLOWED) return p!!
        val l = legacy?.trim()?.lowercase()
        if (l in ALLOWED) return l!!
        return DEFAULT_POLICY
    }

    /**
     * Reads effective policy without mutating prefs (safe for hot paths like [onStartCommand]).
     */
    @JvmStatic
    fun readPolicy(prefs: SharedPreferences): String {
        val primary = prefs.getString(PREFS_KEY, null)
        val legacy = prefs.getString(LEGACY_PREFS_KEY, null)
        return canonicalFromStored(primary, legacy)
    }

    /**
     * Normalizes [service_restart_policy], removes legacy [restart_policy]. Call when entering General Settings.
     */
    @JvmStatic
    fun migrateIfNeeded(prefs: SharedPreferences) {
        val canonical = readPolicy(prefs)
        prefs.edit()
            .putString(PREFS_KEY, canonical)
            .remove(LEGACY_PREFS_KEY)
            .apply()
    }
}
