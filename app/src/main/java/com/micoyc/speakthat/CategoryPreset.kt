package com.micoyc.speakthat

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages category-based app filtering presets and user preferences
 */
class CategoryPreset private constructor(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val filterConfigManager = FilterConfigManager()

    companion object {
        private const val PREFS_NAME = "category_presets"
        private const val KEY_CATEGORY_SETTINGS = "category_settings"
        
        @Volatile
        private var instance: CategoryPreset? = null

        fun getInstance(context: Context): CategoryPreset {
            return instance ?: synchronized(this) {
                instance ?: CategoryPreset(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Save category preferences
     */
    fun saveCategories(categories: List<AppCategory>) {
        val json = gson.toJson(categories)
        prefs.edit().putString(KEY_CATEGORY_SETTINGS, json).apply()
    }

    /**
     * Load saved category preferences or return default categories if none saved
     */
    fun loadCategories(): List<AppCategory> {
        val json = prefs.getString(KEY_CATEGORY_SETTINGS, null)
        return if (json != null) {
            val type = object : TypeToken<List<AppCategory>>() {}.type
            gson.fromJson(json, type)
        } else {
            AppCategory.getAllCategories(context)
        }
    }

    /**
     * Update filter mode for a specific category
     */
    fun updateCategoryFilter(categoryId: String, filterMode: FilterMode) {
        val categories = loadCategories().toMutableList()
        categories.find { it.id == categoryId }?.let { category ->
            category.filterMode = filterMode
            saveCategories(categories)
        }
    }

    /**
     * Check if a package matches any category's patterns
     */
    fun getCategoryForPackage(packageName: String): AppCategory? {
        return loadCategories().find { category ->
            category.packagePatterns.any { pattern ->
                packageName.startsWith(pattern)
            }
        }
    }

    /**
     * Get the filter mode for a specific package based on its category
     */
    fun getFilterModeForPackage(packageName: String): FilterMode {
        return getCategoryForPackage(packageName)?.filterMode ?: FilterMode.ALLOW
    }

    /**
     * Apply category filters to the app's filter settings
     */
    fun applyFilters() {
        val categories = loadCategories()
        val appList = mutableSetOf<String>()
        val appPrivateFlags = mutableSetOf<String>()
        
        // Process each category and collect apps based on filter mode
        categories.forEach { category ->
            category.packagePatterns.forEach { pattern ->
                when (category.filterMode) {
                    FilterMode.BLOCK -> appList.add(pattern)
                    FilterMode.PRIVATE -> appPrivateFlags.add(pattern)
                    FilterMode.ALLOW -> {} // No action needed for allowed apps
                }
            }
        }

        // Apply the filters by updating SharedPreferences directly
        val prefs = context.getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Set app list mode to "block" if we have blocked apps
        if (appList.isNotEmpty()) {
            editor.putString("app_list_mode", "block")
            editor.putStringSet("app_list", appList)
        }
        
        // Set private app flags
        if (appPrivateFlags.isNotEmpty()) {
            editor.putStringSet("app_private_flags", appPrivateFlags)
        }
        
        editor.apply()
        
        // Log the filter application
        InAppLogger.log("CategoryPreset", "Applied ${appList.size} blocked apps and ${appPrivateFlags.size} private apps")
    }
} 