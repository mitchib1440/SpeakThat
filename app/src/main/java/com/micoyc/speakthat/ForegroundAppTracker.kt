package com.micoyc.speakthat

import android.util.Log

/**
 * Tracks the current foreground app based on accessibility events.
 * This is used by the rules engine for the Foreground App condition.
 */
object ForegroundAppTracker {
    private const val TAG = "ForegroundAppTracker"

    private val ignoredPackages = setOf(
        "android",
        "com.android.systemui"
    )

    @Volatile
    private var currentPackageName: String? = null

    @Volatile
    private var lastUpdatedAt: Long = 0L

    @Volatile
    private var lastUserPackageName: String? = null

    @Volatile
    private var lastUserUpdatedAt: Long = 0L

    fun updateForegroundPackage(packageName: String?) {
        if (packageName.isNullOrBlank()) {
            return
        }

        val isChanged = packageName != currentPackageName
        currentPackageName = packageName
        lastUpdatedAt = System.currentTimeMillis()
        if (!ignoredPackages.contains(packageName)) {
            lastUserPackageName = packageName
            lastUserUpdatedAt = lastUpdatedAt
        }
        if (isChanged) {
            Log.d(TAG, "Foreground app updated: $packageName")
        }
    }

    fun getCurrentPackage(): String? {
        return currentPackageName
    }

    fun getEffectivePackage(fallbackWindowMs: Long = 15000L): String? {
        val current = currentPackageName
        if (current == null) {
            return null
        }
        if (ignoredPackages.contains(current)) {
            val ageMs = System.currentTimeMillis() - lastUserUpdatedAt
            if (!lastUserPackageName.isNullOrBlank() && ageMs <= fallbackWindowMs) {
                return lastUserPackageName
            }
        }
        return current
    }

    fun getLastUpdatedAt(): Long {
        return lastUpdatedAt
    }

    fun getLastUserPackage(): String? {
        return lastUserPackageName
    }

    fun getLastUserUpdatedAt(): Long {
        return lastUserUpdatedAt
    }
}
