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
 */
object BadgeAssets {
    const val PREF_BADGE_SELECTION: String = "badge_selection"
    const val KEY_DEFAULT: String = "default"

    private const val PLAY_BADGE_PREFS = "play_donations"
    private const val PLAY_BADGE_KEY = "badge_count"

    enum class BadgeTier(
        val key: String,
        val requiredDonations: Int,
        val requiredReads: Int,
        val drawableRes: Int,
        val festiveDrawableRes: Int? = null
    ) {
        DIAMOND("diamond", 60, 25000, R.drawable.diamond, R.drawable.diamond_festive),
        RUBY("ruby", 50, 20000, R.drawable.ruby, R.drawable.ruby_festive),
        AMETHYST("amethyst", 40, 15000, R.drawable.amethyst, R.drawable.amethyst_festive),
        AMBER("amber", 30, 12500, R.drawable.amber, R.drawable.amber_festive),
        JET("jet", 25, 10000, R.drawable.jet, R.drawable.jet_festive),
        SAPPHIRE("sapphire", 20, 7500, R.drawable.sapphire, R.drawable.sapphire_festive),
        CITRINE("citrine", 17, 5000, R.drawable.citrine, R.drawable.citrine_festive),
        AQUAMARINE("aquamarine", 14, 3500, R.drawable.aquamarine, R.drawable.aquamarine_festive),
        EMERALD("emerald", 10, 2000, R.drawable.emerald, R.drawable.emerald_festive),
        JADE("jade", 7, 1000, R.drawable.jade, R.drawable.jade_festive),
        GOLD("gold", 5, 400, R.drawable.gold, R.drawable.gold_festive),
        PEARL("pearl", 4, 150, R.drawable.pearl, R.drawable.pearl_festive),
        ROSE_QUARTZ("rose_quartz", 3, 50, R.drawable.rose_quartz, R.drawable.rose_quartz_festive),
        SILVER("silver", 2, 10, R.drawable.silver, R.drawable.silver_festive),
        BRONZE("bronze", 1, 1, R.drawable.bronze, R.drawable.bronze_festive),
        DEFAULT(KEY_DEFAULT, 0, 0, R.drawable.logo_speakthat, R.drawable.logo_speakthat_festive)
    }

    /**
     * Read the Play badge count from PlayDonationStore prefs.
     */
    @JvmStatic
    fun getPlayBadgeCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PLAY_BADGE_PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(PLAY_BADGE_KEY, 0)
    }

    @JvmStatic
    fun getUnlockedBadges(context: Context): List<BadgeTier> {
        val userReads = StatisticsManager.getInstance(context).getNotificationsRead()
        val userDonations = getPlayBadgeCount(context)
        
        return BadgeTier.values().filter { 
            userDonations >= it.requiredDonations || userReads >= it.requiredReads 
        }
    }

    @JvmStatic
    fun bestUnlocked(context: Context): BadgeTier? =
        getUnlockedBadges(context).firstOrNull() // Since enum is ordered highest to lowest

    @JvmStatic
    fun ensureValidSelection(selection: String?, context: Context): String {
        if (selection.isNullOrEmpty() || selection == KEY_DEFAULT) return KEY_DEFAULT
        val unlocked = getUnlockedBadges(context)
        return if (unlocked.any { it.key == selection }) {
            selection
        } else {
            bestUnlocked(context)?.key ?: KEY_DEFAULT
        }
    }

    @JvmStatic
    fun drawableForSelection(selection: String?, context: Context, festiveEnabled: Boolean = false): Int {
        val sanitized = ensureValidSelection(selection, context)
        val tier = BadgeTier.values().firstOrNull { it.key == sanitized } ?: BadgeTier.DEFAULT
        
        return if (festiveEnabled) {
            tier.festiveDrawableRes ?: tier.drawableRes
        } else {
            tier.drawableRes
        }
    }
}
