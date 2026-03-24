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
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
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

    // --- ADDED: Compose Desktop Dependencies ---
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // --- TEMPORARY MOCK: TFLite removed for Phase 3 UI Testing ---
    // implementation("org.tensorflow:tensorflow-lite-api:2.14.0")
    // implementation("org.tensorflow:tensorflow-lite:2.14.0")

    testImplementation(kotlin("test-junit"))
}

application {
    // --- UPDATED: Main class is now the Desktop App
    mainClass.set("io.pm.finlight.analyzer.AnalyzerAppKt")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        // Ensure Kotlin also targets JVM 17 for consistency.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// --- CUSTOM TASK: Run NER Labeler App ---
tasks.register<JavaExec>("runNerLabeler") {
    group = "application"
    description = "Run the NER Labeler Desktop App"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.pm.finlight.analyzer.NerLabelerAppKt")
}
