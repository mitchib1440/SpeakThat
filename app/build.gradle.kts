plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("com.mikepenz.aboutlibraries.plugin")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.micoyc.speakthat"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.micoyc.speakthat"
        minSdk = 24
        // I'm currently sticking with targetSdk 36, as I do not have a modern Android device to test with.
        targetSdk = 36
        versionCode = 63
        versionName = "1.9.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        resourceConfigurations += setOf(
            "en", "en-rGB", "ar", "de", "es", "fr", "hi", "id", "it",
            "ja", "ko", "nl", "pa-rIN", "pl", "pt", "ru", "sv", "th",
            "tr", "ur", "vi", "zh-rCN", "zh-rTW"
        )
    }

    // Personal fork: only the offline "store" variant is kept.
    // The GitHub (auto-updater, online icons) and Play (billing) variants were removed.
    // The store manifest strips the INTERNET permission, so the app cannot reach the network.
    flavorDimensions += "distribution"
    productFlavors {
        create("store") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"store\"")
            buildConfigField("boolean", "HAS_ACCESSIBILITY", "true")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

                buildFeatures {
                viewBinding = true
                buildConfig = true  // Enable BuildConfig generation
            }

    // Use default res structure (badge vectors are placed directly in drawable).

    // Configure APK output file naming
    applicationVariants.all {
        outputs.all {
            if (this is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                val versionName = defaultConfig.versionName
                outputFileName = "SpeakThat-Offline-v${versionName}-${buildType.name}.apk"
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
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("com.mikepenz:aboutlibraries:10.10.0")

    // Background work (service restart policy)
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("androidx.media:media:1.8.0")
    implementation("androidx.car.app:app:1.7.0")
}

aboutLibraries {
    excludeFields = arrayOf("generated")
}