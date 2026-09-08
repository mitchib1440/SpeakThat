// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    // Keep AboutLibraries at 10.10.0 unless upgrading is absolutely necessary lest I get shouted at for causing extra work for IzzyOnDroid's team... sorry izzy!
    id("com.mikepenz.aboutlibraries.plugin") version "10.10.0" apply false
} 