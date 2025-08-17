package com.micoyc.speakthat

/**
 * Represents a category of apps with associated filtering preferences
 */
enum class FilterMode {
    BLOCK,      // Block notifications from apps in this category
    PRIVATE,    // Show notifications but keep content private
    ALLOW       // Allow all notifications normally
}

data class AppCategory(
    val id: String,                  // Unique identifier for the category
    val displayName: String,         // User-friendly name shown in UI
    val description: String,         // Brief description of the category
    val iconResId: Int,             // Resource ID for the category icon
    var filterMode: FilterMode,      // Current filter mode for this category
    val packagePatterns: List<String> // List of package name patterns that belong to this category
) {
    companion object {
        // Predefined categories with comprehensive app lists
        fun getSocialCategory(context: android.content.Context) = AppCategory(
            id = context.getString(R.string.category_social_id),
            displayName = context.getString(R.string.category_social_display_name),
            description = context.getString(R.string.category_social_description),
            iconResId = android.R.drawable.ic_menu_share,
            filterMode = FilterMode.ALLOW,
            packagePatterns = listOf(
                // Major social platforms
                context.getString(R.string.package_twitter),
                context.getString(R.string.package_facebook),
                context.getString(R.string.package_instagram),
                context.getString(R.string.package_linkedin),
                context.getString(R.string.package_reddit),
                context.getString(R.string.package_snapchat),
                context.getString(R.string.package_pinterest),
                context.getString(R.string.package_tumblr),
                // Regional social apps
                context.getString(R.string.package_weibo),
                context.getString(R.string.package_line),
                context.getString(R.string.package_tiktok),
                // Professional networking
                context.getString(R.string.package_glassdoor),
                context.getString(R.string.package_indeed),
                // Community platforms
                context.getString(R.string.package_discord),
                context.getString(R.string.package_slack),
                context.getString(R.string.package_meetup)
            )
        )

        fun getMessagingCategory(context: android.content.Context) = AppCategory(
            id = context.getString(R.string.category_messaging_id),
            displayName = context.getString(R.string.category_messaging_display_name),
            description = context.getString(R.string.category_messaging_description),
            iconResId = android.R.drawable.ic_dialog_email,
            filterMode = FilterMode.ALLOW,
            packagePatterns = listOf(
                // Popular messaging apps
                context.getString(R.string.package_whatsapp),
                context.getString(R.string.package_facebook_messenger),
                context.getString(R.string.package_google_messages),
                context.getString(R.string.package_telegram),
                context.getString(R.string.package_viber),
                context.getString(R.string.package_skype),
                // Email clients
                context.getString(R.string.package_gmail),
                context.getString(R.string.package_outlook),
                context.getString(R.string.package_yahoo_mail),
                // Video chat
                context.getString(R.string.package_google_meet),
                context.getString(R.string.package_zoom),
                context.getString(R.string.package_microsoft_teams),
                // Regional messaging
                context.getString(R.string.package_line_android),
                context.getString(R.string.package_kakaotalk),
                context.getString(R.string.package_wechat)
            )
        )

        fun getFinanceCategory(context: android.content.Context) = AppCategory(
            id = context.getString(R.string.category_finance_id),
            displayName = context.getString(R.string.category_finance_display_name),
            description = context.getString(R.string.category_finance_description),
            iconResId = android.R.drawable.ic_menu_recent_history,
            filterMode = FilterMode.PRIVATE,
            packagePatterns = listOf(
                // Payment apps
                context.getString(R.string.package_paypal),
                context.getString(R.string.package_venmo),
                context.getString(R.string.package_cash_app),
                context.getString(R.string.package_google_wallet),
                // Banking apps
                context.getString(R.string.package_bank_of_america),
                context.getString(R.string.package_chase),
                context.getString(R.string.package_wells_fargo),
                context.getString(R.string.package_us_bank),
                context.getString(R.string.package_citibank),
                // Investment apps
                context.getString(R.string.package_robinhood),
                context.getString(R.string.package_etrade),
                context.getString(R.string.package_charles_schwab),
                context.getString(R.string.package_fidelity),
                // Crypto
                context.getString(R.string.package_coinbase),
                context.getString(R.string.package_binance),
                // Budgeting
                context.getString(R.string.package_mint),
                context.getString(R.string.package_ynab)
            )
        )

        fun getHealthCategory(context: android.content.Context) = AppCategory(
            id = context.getString(R.string.category_health_id),
            displayName = context.getString(R.string.category_health_display_name),
            description = context.getString(R.string.category_health_description),
            iconResId = android.R.drawable.ic_menu_myplaces,
            filterMode = FilterMode.PRIVATE,
            packagePatterns = listOf(
                // Fitness tracking
                context.getString(R.string.package_fitbit),
                context.getString(R.string.package_google_fit),
                context.getString(R.string.package_samsung_health),
                context.getString(R.string.package_garmin_connect),
                context.getString(R.string.package_strava),
                // Health tracking
                context.getString(R.string.package_medisafe),
                context.getString(R.string.package_myfitnesspal),
                context.getString(R.string.package_noom),
                context.getString(R.string.package_weightwatchers),
                // Medical
                context.getString(R.string.package_zocdoc),
                context.getString(R.string.package_carezone),
                context.getString(R.string.package_teladoc),
                // Mental health
                context.getString(R.string.package_calm),
                context.getString(R.string.package_headspace),
                context.getString(R.string.package_betterhelp),
                // Period tracking
                context.getString(R.string.package_glow),
                context.getString(R.string.package_clue)
            )
        )

        fun getEntertainmentCategory(context: android.content.Context) = AppCategory(
            id = context.getString(R.string.category_entertainment_id),
            displayName = context.getString(R.string.category_entertainment_display_name),
            description = context.getString(R.string.category_entertainment_description),
            iconResId = android.R.drawable.ic_menu_view,
            filterMode = FilterMode.ALLOW,
            packagePatterns = listOf(
                // Music streaming
                "com.spotify.",          // Spotify
                "com.pandora.",          // Pandora
                "com.amazon.mp3",        // Amazon Music
                "com.apple.android.music", // Apple Music
                "com.google.android.apps.youtube.music", // YouTube Music
                // Video streaming
                "com.netflix.",          // Netflix
                "com.hulu.",             // Hulu
                "com.disney.disneyplus", // Disney+
                "com.amazon.avod",       // Prime Video
                "com.google.android.youtube", // YouTube
                "com.plexapp.",          // Plex
                // Podcasts
                "com.google.android.apps.podcasts", // Google Podcasts
                "com.spotify.podcasts",   // Spotify Podcasts
                "com.apple.podcasts",     // Apple Podcasts
                // Books & Audiobooks
                "com.amazon.kindle",      // Kindle
                "com.audible.application", // Audible
                "com.google.android.apps.books" // Google Play Books
            )
        )

        fun getGamesCategory(context: android.content.Context) = AppCategory(
            id = context.getString(R.string.category_games_id),
            displayName = context.getString(R.string.category_games_display_name),
            description = context.getString(R.string.category_games_description),
            iconResId = android.R.drawable.ic_menu_manage,
            filterMode = FilterMode.BLOCK,
            packagePatterns = listOf(
                // Major publishers
                "com.supercell.",        // Supercell games
                "com.king.",             // King games
                "com.zynga.",            // Zynga games
                "com.ea.",               // EA games
                "com.rovio.",            // Rovio games
                "com.gameloft.",         // Gameloft games
                "com.nintendo.",         // Nintendo games
                "com.playrix",           // Playrix games
                // Popular games
                "com.mojang.minecraft",  // Minecraft
                "com.roblox.",           // Roblox
                "com.dts.freefireth",    // Free Fire
                "com.tencent.ig",        // PUBG Mobile
                // Game platforms
                "com.epicgames.",        // Epic Games
                "com.valvesoftware.android.steam", // Steam
                "com.microsoft.xcloud",   // Xbox Cloud Gaming
                "com.nvidia.geforcenow"   // GeForce NOW
            )
        )

        // Get all predefined categories
        fun getAllCategories(context: android.content.Context) = listOf(
            getSocialCategory(context),
            getMessagingCategory(context),
            getFinanceCategory(context),
            getHealthCategory(context),
            getEntertainmentCategory(context),
            getGamesCategory(context)
        )
    }
} 