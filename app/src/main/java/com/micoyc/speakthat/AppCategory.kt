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
        fun getSocialCategory() = AppCategory(
            id = "social",
            displayName = "Social Media",
            description = "Social networking and community apps",
            iconResId = android.R.drawable.ic_menu_share,
            filterMode = FilterMode.ALLOW,
            packagePatterns = listOf(
                // Major social platforms
                "com.twitter.",          // Twitter/X
                "com.facebook.",         // Facebook
                "com.instagram.",        // Instagram
                "com.linkedin.",         // LinkedIn
                "com.reddit.",           // Reddit
                "com.snapchat.",         // Snapchat
                "com.pinterest.",        // Pinterest
                "com.tumblr",            // Tumblr
                // Regional social apps
                "com.weibo.",            // Weibo
                "jp.naver.line.",        // LINE
                "com.zhiliaoapp.musically", // TikTok
                // Professional networking
                "com.glassdoor.",        // Glassdoor
                "com.indeed.",           // Indeed
                // Community platforms
                "com.discord",           // Discord
                "com.slack",             // Slack
                "com.meetup"             // Meetup
            )
        )

        fun getMessagingCategory() = AppCategory(
            id = "messaging",
            displayName = "Messaging",
            description = "Chat and communication apps",
            iconResId = android.R.drawable.ic_dialog_email,
            filterMode = FilterMode.ALLOW,
            packagePatterns = listOf(
                // Popular messaging apps
                "com.whatsapp",          // WhatsApp
                "com.facebook.messenger", // Facebook Messenger
                "com.google.android.apps.messaging", // Google Messages
                "org.telegram.",         // Telegram
                "com.viber.",            // Viber
                "com.skype.",            // Skype
                // Email clients
                "com.google.android.gm",  // Gmail
                "com.microsoft.office.outlook", // Outlook
                "com.yahoo.mobile.client.android.mail", // Yahoo Mail
                // Video chat
                "com.google.android.apps.meetings", // Google Meet
                "us.zoom.",              // Zoom
                "com.microsoft.teams",    // Microsoft Teams
                // Regional messaging
                "jp.naver.line.android", // LINE
                "com.kakao.talk",        // KakaoTalk
                "com.tencent.mm"         // WeChat
            )
        )

        fun getFinanceCategory() = AppCategory(
            id = "finance",
            displayName = "Finance",
            description = "Banking and financial apps",
            iconResId = android.R.drawable.ic_menu_recent_history,
            filterMode = FilterMode.PRIVATE,
            packagePatterns = listOf(
                // Payment apps
                "com.paypal.",           // PayPal
                "com.venmo.",            // Venmo
                "com.squareup.cash",     // Cash App
                "com.google.android.apps.walletnfcrel", // Google Wallet
                // Banking apps
                "com.bankofamerica.",    // Bank of America
                "com.chase.",            // Chase
                "com.wellsfargo.",       // Wells Fargo
                "com.usbank.",           // US Bank
                "com.citibank.",         // Citibank
                // Investment apps
                "com.robinhood.",        // Robinhood
                "com.etrade.",           // E*TRADE
                "com.schwab.",           // Charles Schwab
                "com.fidelity.",         // Fidelity
                // Crypto
                "com.coinbase.",         // Coinbase
                "com.binance.",          // Binance
                // Budgeting
                "com.mint",              // Mint
                "com.ynab"               // YNAB
            )
        )

        fun getHealthCategory() = AppCategory(
            id = "health",
            displayName = "Health & Fitness",
            description = "Health, medical, and fitness apps",
            iconResId = android.R.drawable.ic_menu_myplaces,
            filterMode = FilterMode.PRIVATE,
            packagePatterns = listOf(
                // Fitness tracking
                "com.fitbit.",           // Fitbit
                "com.google.android.apps.fitness", // Google Fit
                "com.samsung.health",    // Samsung Health
                "com.garmin.connect",    // Garmin Connect
                "com.strava",            // Strava
                // Health tracking
                "com.medisafe.",         // Medisafe
                "com.myfitnesspal.",     // MyFitnessPal
                "com.noom.",             // Noom
                "com.weightwatchers",    // WeightWatchers
                // Medical
                "com.zocdoc.",           // ZocDoc
                "org.carezone.",         // CareZone
                "com.teladoc.",          // Teladoc
                // Mental health
                "com.calm.",             // Calm
                "com.headspace.",        // Headspace
                "com.betterhelp",        // BetterHelp
                // Period tracking
                "com.glow.android",      // Glow
                "com.clue.android"       // Clue
            )
        )

        fun getEntertainmentCategory() = AppCategory(
            id = "entertainment",
            displayName = "Entertainment",
            description = "Media and entertainment apps",
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

        fun getGamesCategory() = AppCategory(
            id = "games",
            displayName = "Games",
            description = "Mobile games and gaming apps",
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
        fun getAllCategories() = listOf(
            getSocialCategory(),
            getMessagingCategory(),
            getFinanceCategory(),
            getHealthCategory(),
            getEntertainmentCategory(),
            getGamesCategory()
        )
    }
} 