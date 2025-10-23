plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.micoyc.speakthat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.micoyc.speakthat"
        minSdk = 24
        targetSdk = 36
        versionCode = 23
        versionName = "1.5.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Add product flavors for different distribution channels
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            // Removed applicationIdSuffix to maintain compatibility with existing installs
            // Keep version name clean for backwards compatibility
            // versionNameSuffix = "-github"  // Removed to maintain clean version names
            
            // Build config fields for GitHub variant
            buildConfigField("boolean", "ENABLE_AUTO_UPDATER", "true")
            buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"github\"")
        }
        
        create("store") {
            dimension = "distribution"
            // Removed applicationIdSuffix to maintain compatibility with existing installs
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

    // Configure APK output file naming
    applicationVariants.all {
        outputs.all {
            if (this is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                val flavor = productFlavors[0].name
                val versionName = defaultConfig.versionName
                
                outputFileName = when (flavor) {
                    "store" -> "SpeakThat-NoUpdater-v${versionName}.apk"
                    "github" -> "SpeakThat-v${versionName}.apk"
                    else -> "SpeakThat-${flavor}-v${versionName}.apk"
                }
            }
        }
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
    
    // Coil for image loading - only used in GitHub variant for online icons
    implementation("io.coil-kt:coil:2.4.0")
    implementation("io.coil-kt:coil-svg:2.4.0")
    
    // For network requests (update system) - only used in GitHub variant
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // For background work (optional, for future enhancements)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.github.bumptech.glide:glide:4.16.0")
} 