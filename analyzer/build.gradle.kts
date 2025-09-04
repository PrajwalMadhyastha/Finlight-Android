// =================================================================================
// FILE: ./analyzer/build.gradle.kts
// REASON: REFACTOR - The mainClass for the application has been changed to point
// to the new DatasetGeneratorKt. This makes the new script directly runnable
// for the purpose of creating the TFLite model's training data.
// =================================================================================
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
    // --- UPDATED: Changed the main class to our new dataset generator.
    // To run the old tool, change this back to "io.pm.finlight.analyzer.SmsAnalysisToolKt"
    mainClass.set("io.pm.finlight.analyzer.DatasetGeneratorKt")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        // Ensure Kotlin also targets JVM 17 for consistency.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
