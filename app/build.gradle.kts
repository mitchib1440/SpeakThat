plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.micoyc.speakthat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.micoyc.speakthat"
        minSdk = 24
        targetSdk = 35
        versionCode = 12
        versionName = "1.3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Add product flavors for different distribution channels
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            applicationIdSuffix = ".github"
            // Keep version name clean for backwards compatibility
            // versionNameSuffix = "-github"  // Removed to maintain clean version names
            
            // Build config fields for GitHub variant
            buildConfigField("boolean", "ENABLE_AUTO_UPDATER", "true")
            buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"github\"")
        }
        
        create("store") {
            dimension = "distribution"
            applicationIdSuffix = ".store"
            // Keep version name clean for backwards compatibility
            // versionNameSuffix = "-store"  // Removed to maintain clean version names
            
            // Build config fields for store variant
            buildConfigField("boolean", "ENABLE_AUTO_UPDATER", "false")
            buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"store\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    // Custom APK file naming
    applicationVariants.all { variant ->
        variant.outputs.configureEach {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val flavor = variant.flavorName
            val buildType = variant.buildType.name
            val versionName = variant.versionName
            
            val newName = when {
                flavor == "github" && buildType == "release" -> "SpeakThat-v${versionName}.apk"
                flavor == "store" && buildType == "release" -> "SpeakThat-NoUpdater-v${versionName}.apk"
                else -> "SpeakThat-${flavor}-${buildType}-v${versionName}.apk"
            }
            
            outputImpl.outputFileName = newName
        }
    }
    
    // Disable Google dependency blob for open source distribution
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true  // Enable BuildConfig generation
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.recyclerview)
    implementation(libs.gson)
    implementation("io.coil-kt:coil:2.4.0")
    implementation("io.coil-kt:coil-svg:2.4.0")
    
    // For network requests (update system) - available to both flavors
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // For background work (optional, for future enhancements)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.github.bumptech.glide:glide:4.16.0")
} 