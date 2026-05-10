package com.micoyc.speakthat.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.micoyc.speakthat.models.ChangelogItem
import java.io.InputStreamReader

object ChangelogUtils {
    private const val TAG = "ChangelogUtils"
    private const val CHANGELOG_FILE = "changelog.json"

    fun getChangelogItems(context: Context): List<ChangelogItem> {
        return try {
            context.assets.open(CHANGELOG_FILE).use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val type = object : TypeToken<List<ChangelogItem>>() {}.type
                    Gson().fromJson(reader, type) ?: emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing changelog.json", e)
            emptyList()
        }
    }

    fun getTickerString(context: Context): String {
        val items = getChangelogItems(context)
        if (items.isEmpty()) {
            return ""
        }
        
        return items.joinToString(separator = "  ◉  ") { it.text }
    }
}
