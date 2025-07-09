import java.util.Properties
import java.io.FileInputStream

// It's good practice to define versions in one place.
val roomVersion = "2.6.1"
val lifecycleVersion = "2.8.2"
val activityComposeVersion = "1.9.0"
val coreKtxVersion = "1.13.1"
val navigationVersion = "2.7.7"
val androidxTestVersion = "1.6.1"
val testExtJunitVersion = "1.2.1"
val espressoVersion = "3.6.1"
val tracingVersion = "1.2.0"
val workVersion = "2.9.0"
val robolectricVersion = "4.13"
val coroutinesTestVersion = "1.8.1"
val gsonVersion = "2.10.1"
val coilVersion = "2.6.0"
// --- NEW: Add Image Cropper library version ---
val imageCropperVersion = "4.5.0"

// Read properties from local.properties
val keystorePropertiesFile = rootProject.file("local.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}


plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

android {
    namespace = "io.pm.finlight"
    compileSdk = 35

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["key.alias"] as String?
            keyPassword = keystoreProperties["key.password"] as String?
            storeFile = keystoreProperties["keystore.path"]?.let { rootProject.file(it) }
            storePassword = keystoreProperties["keystore.password"] as String?
        }
    }

    defaultConfig {
        applicationId = "io.pm.finlight"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        // --- NEW: Explicitly define the debug build type ---
        // This ensures that the App Inspector can connect to your app to view
        // databases, background workers, and other debug information.
        debug {
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // --- FIX: Return default values for Android framework methods in unit tests ---
            isReturnDefaultValues = true
        }
    }
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core-ktx:$coreKtxVersion")
        force("androidx.core:core:$coreKtxVersion")
        force("androidx.tracing:tracing-ktx:$tracingVersion")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:$coreKtxVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.activity:activity-compose:$activityComposeVersion")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime-livedata")

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.firebase:firebase-crashlytics-buildtools:3.0.4")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.navigation:navigation-compose:$navigationVersion")

    implementation("com.google.android.material:material:1.12.0")

    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    implementation("com.google.code.gson:gson:$gsonVersion")

    implementation("androidx.tracing:tracing-ktx:$tracingVersion")

    implementation("io.coil-kt:coil-compose:$coilVersion")

    implementation("com.vanniktech:android-image-cropper:$imageCropperVersion")

    // Local unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("androidx.test:core-ktx:$androidxTestVersion")
    testImplementation("androidx.test.ext:junit:$testExtJunitVersion")
    testImplementation("org.robolectric:robolectric:$robolectricVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesTestVersion")
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    // Instrumented UI tests
    androidTestImplementation("androidx.tracing:tracing-ktx:$tracingVersion")
    androidTestImplementation("androidx.test:runner:$androidxTestVersion")
    androidTestImplementation("androidx.test:core-ktx:$androidxTestVersion")
    androidTestImplementation("androidx.test.ext:junit-ktx:$testExtJunitVersion")
    androidTestImplementation("androidx.test:rules:$androidxTestVersion")
    androidTestImplementation("androidx.test.espresso:espresso-core:$espressoVersion")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Debug dependencies
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.work:work-runtime-ktx:$workVersion")
}
