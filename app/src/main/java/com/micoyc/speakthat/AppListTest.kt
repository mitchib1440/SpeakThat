package com.micoyc.speakthat

import android.content.Context
import android.util.Log

/**
 * Test class to verify JSON app list loading
 */
object AppListTest {
    private const val TAG = "AppListTest"
    
    fun testAppListLoading(context: Context) {
        try {
            Log.d(TAG, "Testing app list loading...")
            
            // Test loading the app list
            val apps = AppListManager.loadAppList(context)
            Log.d(TAG, "Loaded ${apps.size} apps from JSON")
            
            // Test searching
            val searchResults = AppListManager.searchApps(context, "whatsapp")
            Log.d(TAG, "Search for 'whatsapp' returned ${searchResults.size} results")
            
            // Test category search
            val categories = AppListManager.getCategories(context)
            Log.d(TAG, "Available categories: ${categories.joinToString(", ")}")
            
            // Test specific app search
            val whatsappApp = AppListManager.findAppByPackage(context, "com.whatsapp")
            if (whatsappApp != null) {
                Log.d(TAG, "Found WhatsApp: ${whatsappApp.displayName} (${whatsappApp.packageName})")
            } else {
                Log.w(TAG, "WhatsApp not found in app list")
            }
            
            // Test alias search
            val aliasResults = AppListManager.searchApps(context, "wa")
            Log.d(TAG, "Search for 'wa' alias returned ${aliasResults.size} results")
            
            Log.d(TAG, "App list test completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error testing app list loading", e)
        }
    }
} 