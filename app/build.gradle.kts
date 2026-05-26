plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    // alias(libs.plugins.hilt)  // Temporarily disabled for AGP 9 compatibility
    // alias(libs.plugins.ksp)   // Temporarily disabled 
    // alias(libs.plugins.navigation.safeargs) // disabled temporarily to avoid plugin ordering issues
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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    // Firebase — BOM manages individual library versions
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)

    // Hilt - Temporarily disabled for AGP 9 compatibility
    // implementation(libs.hilt.android)
    // ksp(libs.hilt.compiler)
    
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    // Navigation Component
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // DataStore
    implementation(libs.datastore.preferences)

    // Glide
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
