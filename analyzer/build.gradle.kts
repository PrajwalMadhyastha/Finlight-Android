// build.gradle.kts for the :analyzer module
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    // Standard Kotlin libraries
    implementation(kotlin("stdlib"))

    // Dependency on the shared 'core' module to access the SmsParser.
    implementation(project(":core"))

    // --- UPDATED: Switched from XML to the more robust JSON serialization library ---
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

application {
    // Define the main class for the runnable application.
    mainClass.set("io.pm.finlight.analyzer.SmsAnalysisToolKt")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
