/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import com.google.gson.annotations.SerializedName

/**
 * Data class representing an app from the JSON app list
 */
data class AppListData(
    @SerializedName("displayName")
    @JvmField val displayName: String,
    
    @SerializedName("packageName")
    @JvmField val packageName: String,
    
    @SerializedName("category")
    @JvmField val category: String,
    
    @SerializedName("aliases")
    @JvmField val aliases: List<String>,

    @SerializedName("iconSlug")
    @JvmField
    val iconSlug: String? = null
) {
    /**
     * Check if this app matches the given search query
     * Searches in displayName, packageName, category, and aliases
     */
    fun matchesQuery(query: String): Boolean {
        val lowerQuery = query.lowercase().trim()
        if (lowerQuery.isEmpty()) return true
        
        return displayName.lowercase().contains(lowerQuery) ||
               packageName.lowercase().contains(lowerQuery) ||
               category.lowercase().contains(lowerQuery) ||
               aliases.any { it.lowercase().contains(lowerQuery) }
    }
    
    /**
     * Get a display string for the app
     */
    fun getDisplayString(): String {
        return "$displayName ($packageName)"
    }
} 