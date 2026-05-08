/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.content.Context

/**
 * Badge asset helpers shared across flavors.
 * Only the Play build will surface badge counts, but other flavors safely fall back.
 */
object BadgeAssets {
    const val PREF_BADGE_SELECTION: String = "badge_selection"
    const val KEY_DEFAULT: String = "default"

    private const val PLAY_BADGE_PREFS = "play_donations"
    private const val PLAY_BADGE_KEY = "badge_count"

    data class BadgeTier(
        val key: String,
        val threshold: Int,
        val drawableRes: Int
    )

    // Ordered highest threshold first so the "best" tier is found quickly.
    private val badgeTiers = listOf(
        BadgeTier(key = "ruby", threshold = 50, drawableRes = R.drawable.ruby),
        BadgeTier(key = "amethyst", threshold = 40, drawableRes = R.drawable.amethyst),
        BadgeTier(key = "amber", threshold = 30, drawableRes = R.drawable.amber),
        BadgeTier(key = "sapphire", threshold = 20, drawableRes = R.drawable.sapphire),
        BadgeTier(key = "emerald", threshold = 10, drawableRes = R.drawable.emerald),
        BadgeTier(key = "gold", threshold = 5, drawableRes = R.drawable.gold),
        BadgeTier(key = "silver", threshold = 2, drawableRes = R.drawable.silver),
        BadgeTier(key = "bronze", threshold = 1, drawableRes = R.drawable.bronze)
    )

    /**
     * Read the Play badge count from PlayDonationStore prefs. Non-Play builds return 0.
     */
    @JvmStatic
    fun getPlayBadgeCount(context: Context): Int {
        if (BuildConfig.DISTRIBUTION_CHANNEL != "play") return 0
        val prefs = context.getSharedPreferences(PLAY_BADGE_PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(PLAY_BADGE_KEY, 0)
    }

    @JvmStatic
    fun unlockedBadges(badgeCount: Int): List<BadgeTier> =
        badgeTiers.filter { badgeCount >= it.threshold }

    @JvmStatic
    fun bestUnlocked(badgeCount: Int): BadgeTier? =
        unlockedBadges(badgeCount).maxByOrNull { it.threshold }

    @JvmStatic
    fun ensureValidSelection(selection: String?, badgeCount: Int): String {
        if (selection.isNullOrEmpty() || selection == KEY_DEFAULT) return KEY_DEFAULT
        return if (unlockedBadges(badgeCount).any { it.key == selection }) {
            selection
        } else {
            bestUnlocked(badgeCount)?.key ?: KEY_DEFAULT
        }
    }

    @JvmStatic
    fun drawableForSelection(selection: String?, badgeCount: Int, festiveEnabled: Boolean = false): Int {
        val sanitized = ensureValidSelection(selection, badgeCount)
        if (sanitized == KEY_DEFAULT) {
            return if (festiveEnabled) R.drawable.logo_speakthat_festive else R.drawable.logo_speakthat
        }

        return when (sanitized) {
            "bronze" -> if (festiveEnabled) R.drawable.bronze_festive else R.drawable.bronze
            "silver" -> if (festiveEnabled) R.drawable.silver_festive else R.drawable.silver
            "gold" -> if (festiveEnabled) R.drawable.gold_festive else R.drawable.gold
            "emerald" -> if (festiveEnabled) R.drawable.emerald_festive else R.drawable.emerald
            "sapphire" -> if (festiveEnabled) R.drawable.sapphire_festive else R.drawable.sapphire
            "amber" -> if (festiveEnabled) R.drawable.amber_festive else R.drawable.amber
            "amethyst" -> if (festiveEnabled) R.drawable.amethyst_festive else R.drawable.amethyst
            "ruby" -> if (festiveEnabled) R.drawable.ruby_festive else R.drawable.ruby
            else -> if (festiveEnabled) R.drawable.logo_speakthat_festive else R.drawable.logo_speakthat
        }
    }
}

