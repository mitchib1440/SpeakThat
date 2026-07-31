/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.content.Context

object SettingsSearchEngine {

    data class RankedResult(
        val item: SettingsItem,
        val tier: Int
    )

    data class SearchCategoryGroup(
        val category: String,
        @androidx.annotation.StringRes val categoryTitleRes: Int,
        @androidx.annotation.DrawableRes val categoryIconRes: Int,
        val items: List<SettingsItem>
    )

    fun search(context: Context, query: String, allSettings: List<SettingsItem>): List<SearchCategoryGroup> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()

        val ranked = allSettings.mapNotNull { item ->
            val title = context.getString(item.titleRes).lowercase()
            val description = context.getString(item.descriptionRes).lowercase()
            val keywords = item.searchKeywordsRes
                ?.let { context.getString(it) }
                ?.split(',')
                ?.map { it.trim().lowercase() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()

            val tier = matchTier(normalized, title, description, keywords) ?: return@mapNotNull null
            RankedResult(item, tier)
        }.sortedWith(compareBy({ it.tier }, { context.getString(it.item.titleRes).lowercase() }))

        return ranked
            .groupBy { it.item.category }
            .map { (_, results) ->
                val first = results.first().item
                SearchCategoryGroup(
                    category = first.category,
                    categoryTitleRes = first.categoryTitleRes,
                    categoryIconRes = first.categoryIconRes,
                    items = results.map { it.item }
                )
            }
            // Preserve best-tier ordering of first appearance
            .sortedBy { group ->
                ranked.indexOfFirst { it.item.category == group.category }
            }
    }

    private fun matchTier(
        query: String,
        title: String,
        description: String,
        keywords: List<String>
    ): Int? {
        // Tier 1: exact or contains on title
        if (title == query || title.contains(query)) return 1

        // Tier 2: exact or contains on description or keywords
        if (description == query || description.contains(query)) return 2
        if (keywords.any { it == query || it.contains(query) || query.contains(it) }) return 2

        // Tier 3: Levenshtein distance of exactly 1 on title words or keywords
        val titleWords = title.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (titleWords.any { levenshtein(query, it) == 1 }) return 3
        if (keywords.any { levenshtein(query, it) == 1 }) return 3

        return null
    }

    /** Returns edit distance between two strings. Early-exits when distance would exceed [max]. */
    fun levenshtein(a: String, b: String, max: Int = 1): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost
                )
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > max) return max + 1
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }
}
