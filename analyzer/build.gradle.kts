// build.gradle.kts for the :analyzer module
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
    kotlin("plugin.serialization")
    application
}

// --- FIX: Add this block to enforce Java 17 compatibility ---
// This ensures the Java compiler for this module targets the same JVM version
// as the rest of the Android project, resolving the inconsistency.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Standard Kotlin libraries
    implementation(kotlin("stdlib"))

    // Dependency on the shared 'core' module to access the SmsParser.
    implementation(project(":core"))

    // Use the robust JSON serialization library.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

application {
    // Define the main class for the runnable application.
    mainClass.set("io.pm.finlight.analyzer.SmsAnalysisToolKt")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        // Ensure Kotlin also targets JVM 17 for consistency.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
