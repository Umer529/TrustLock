import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
}

// Load release-signing credentials from keys/signing.properties (gitignored).
// If the file is missing the release build is left unsigned so debug-only
// developers don't hit "missing keystore" errors.
val signingPropsFile = rootProject.file("keys/signing.properties")
val signingProps = Properties().apply {
    if (signingPropsFile.exists()) {
        load(FileInputStream(signingPropsFile))
    }
}

android {
    namespace = "com.example.trustlock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.trustlock"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (signingPropsFile.exists()) {
                storeFile     = rootProject.file(signingProps["storeFile"] as String)
                storePassword = signingProps["storePassword"] as String
                keyAlias      = signingProps["keyAlias"]      as String
                keyPassword   = signingProps["keyPassword"]   as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingPropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AndroidX UI
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.cardview)
    implementation(libs.swiperefreshlayout)

    // Supabase via Retrofit + OkHttp REST
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Navigation Component
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // DataStore
    implementation(libs.datastore.preferences)

    // Room (SQLite local database)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // EncryptedSharedPreferences (role + sensitive cache)
    implementation(libs.security.crypto)

    // Glide
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
