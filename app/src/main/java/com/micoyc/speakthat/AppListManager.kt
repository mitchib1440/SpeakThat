package com.micoyc.speakthat

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manager class for loading and searching the app list from JSON
 */
object AppListManager {
    private const val TAG = "AppListManager"
    private const val APP_LIST_FILE = "app_list.json"
    
    private var appList: List<AppListData> = emptyList()
    private var isLoaded = false
    
    /**
     * Load the app list from the JSON file
     */
    fun loadAppList(context: Context): List<AppListData> {
        if (isLoaded) {
            return appList
        }
        
        return try {
            val inputStream = context.assets.open(APP_LIST_FILE)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            
            val type = object : TypeToken<List<AppListData>>() {}.type
            appList = Gson().fromJson(jsonString, type)
            isLoaded = true
            
            Log.d(TAG, "Loaded ${appList.size} apps from JSON")
            appList
        } catch (e: Exception) {
            Log.e(TAG, "Error loading app list from JSON", e)
            emptyList()
        }
    }
    
    /**
     * Search apps by query
     * Searches in displayName, packageName, category, and aliases
     */
    fun searchApps(context: Context, query: String): List<AppListData> {
        val apps = loadAppList(context)
        if (query.isBlank()) {
            return apps.take(50) // Return first 50 apps if no query
        }
        
        return apps.filter { it.matchesQuery(query) }
    }
    
    /**
     * Get apps by category
     */
    fun getAppsByCategory(context: Context, category: String): List<AppListData> {
        val apps = loadAppList(context)
        return apps.filter { it.category.equals(category, ignoreCase = true) }
    }
    
    /**
     * Get all available categories
     */
    fun getCategories(context: Context): List<String> {
        val apps = loadAppList(context)
        return apps.map { it.category }.distinct().sorted()
    }
    
    /**
     * Find app by package name
     */
    fun findAppByPackage(context: Context, packageName: String): AppListData? {
        val apps = loadAppList(context)
        return apps.find { it.packageName.equals(packageName, ignoreCase = true) }
    }
    
    /**
     * Clear the loaded app list (useful for testing or memory management)
     */
    fun clearCache() {
        appList = emptyList()
        isLoaded = false
    }
} 