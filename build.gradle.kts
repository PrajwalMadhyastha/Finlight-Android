plugins {
    id("com.android.application") version "9.1.0" apply false
    // Set the Kotlin version for the entire project.
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    // Set the KSP version for the entire project.
    id("com.google.devtools.ksp") version "2.3.2" apply false
    // --- ADDED: Declare the Compose Compiler plugin for the project ---
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false

    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.4" apply false
    id("org.sonarqube") version "5.0.0.4638" apply false
    // --- ADDED: Compose Multiplatform Plugin for Desktop Analyzer ---
    id("org.jetbrains.compose") version "1.6.11" apply false
}