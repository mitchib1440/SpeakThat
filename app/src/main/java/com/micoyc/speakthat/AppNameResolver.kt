/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.content.Context
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves app display names from the device itself (PackageManager only).
 * There is no bundled/hardcoded fallback: apps that are not installed (or not visible
 * to SpeakThat under Android package visibility rules) resolve to null.
 */
object AppNameResolver {
    // Only successful lookups are cached so an app installed later is picked up.
    private val cache = ConcurrentHashMap<String, String>()

    /** Returns the installed app's label, or null if it can't be found on this device. */
    @JvmStatic
    fun resolve(context: Context, packageName: String): String? {
        cache[packageName]?.let { return it }
        return try {
            val pm = context.applicationContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.isBlank()) {
                null
            } else {
                cache[packageName] = label
                label
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    /** Returns the installed app's label, or the package name itself if it can't be resolved. */
    @JvmStatic
    fun resolveOrPackage(context: Context, packageName: String): String =
        resolve(context, packageName) ?: packageName

    @JvmStatic
    fun clearCache() {
        cache.clear()
    }
}
