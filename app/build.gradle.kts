import java.util.Properties
import kotlin.apply

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "in.algoframe.pdfviewer"
    compileSdk {
        version = release(36)
    }
    // Load properties from local.properties
    val localProperties = Properties().apply {
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            localFile.reader().use { load(it) }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("/Users/puneeth/AndroidStudioProjects/AlgoPDFViewer/app/keystore/algoPDF.keystore")
            storePassword = localProperties.getProperty("storePassword")
            keyAlias = localProperties.getProperty("keyAlias")
            keyPassword = localProperties.getProperty("keyPassword")
        }
    }

    defaultConfig {
        applicationId = "in.algoframe.pdfviewer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    
    // PDF Viewer - Using Android's native PdfRenderer API (no external dependency needed!)
    // This is built into Android and works with Compose via AndroidView
    // No additional library required - uses framework APIs
    
    // PDF Box for extracting embedded media
    // If 2.0.27.0 doesn't exist, try these alternatives:
    // - 2.0.27.1 (patch version)
    // - 2.0.28.0 (next minor version)  
    // - 2.0.26.0 (previous minor version)
    // Check: https://search.maven.org/artifact/com.tom-roush/pdfbox-android
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // https://mvnrepository.com/artifact/androidx.compose.material/material-icons-extended
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    
    // ExoPlayer for media playback
    implementation(libs.exoplayer)
    
    // Permissions
    implementation(libs.accompanist.permissions)
    
    // Activity Result for file picking
    implementation(libs.activity.result)

    // Coil for image loading
    implementation(libs.coil.compose)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    // in app/build.gradle.kts
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-auth") // Note: Use firebase
    // For Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // For coroutine support with Firebase
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")

    implementation(libs.androidx.security.crypto)
}