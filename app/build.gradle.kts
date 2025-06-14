// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // REQUIRED FOR KOTLIN 2.0+ WITH COMPOSE
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.parvin_project" // <--- ENSURE THIS MATCHES YOUR PROJECT'S PACKAGE NAME
    compileSdk = 34 // Or your current compile SDK version

    defaultConfig {
        applicationId = "com.example.parvin_project" // <--- ENSURE THIS MATCHES YOUR PROJECT'S PACKAGE NAME
        minSdk = 24 // Minimum SDK version required for TelephonyManager.getAllCellInfo() and CellInfoNr
        targetSdk = 34 // Or your current target SDK version
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true // Set to true to enable Jetpack Compose (even if only for theme files)
        viewBinding = false // Set to true if you plan to use View Binding
    }
    composeOptions {
        // Ensure this version is compatible with your Kotlin version and Compose BOM.
        // Check https://developer.android.com/jetpack/compose/bom/mapping
        kotlinCompilerExtensionVersion = "1.5.1" // Example, adjust if needed for your Kotlin version
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core KTX and AppCompat libraries are essential for most Android apps
    implementation("androidx.core:core-ktx:1.13.1") // Or the latest stable version
    implementation("androidx.appcompat:appcompat:1.6.1") // Or the latest stable version

    // Material Design components for UI elements like Button, TextView
    implementation("com.google.android.material:material:1.12.0") // Or the latest stable version

    // ConstraintLayout is a common layout, though not strictly needed for the provided simple layout
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // Or the latest stable version

    // Lifecycle extensions (often useful, though not strictly required by this specific code)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0") // Or the latest stable version

    // Add these for LifecycleService support
    implementation("androidx.lifecycle:lifecycle-service:2.8.0") // REQUIRED for LifecycleService
    implementation("androidx.lifecycle:lifecycle-common-java8:2.8.0") // Often needed for lifecycle-related features

    // Activity KTX for easier activity setup (often used with Compose, but good for general use)
    implementation("androidx.activity:activity-ktx:1.9.0") // Or the latest stable version

    // Jetpack Compose dependencies (needed for your theme files, even if not using Compose UI directly)
    val composeBom = platform("androidx.compose:compose-bom:2023.08.00") // Use the latest stable BOM
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3") // Or androidx.compose.material:material for older Material Design

    // For Compose tooling in Android Studio
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // JUnit for local unit tests
    testImplementation("junit:junit:4.13.2")

    // AndroidX Test for instrumented tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Retrofit for network requests
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    // GSON converter for JSON serialization/deserialization
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // OkHttp logging interceptor for debugging network requests (useful during development)
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.0")
}
