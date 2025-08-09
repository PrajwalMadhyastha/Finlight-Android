// =================================================================================
// FILE: ./analyzer/build.gradle.kts
// REASON: NEW FILE - This Gradle build script configures the new 'analyzer'
// module. It applies the 'application' plugin, sets the main class to be our
// analysis tool, and adds the crucial dependency on the ':core' module, making
// the shared SMS parsing logic available to this command-line tool.
// =================================================================================

plugins {
    // FIX: Removed the explicit version to inherit it from the project settings,
    // resolving the version conflict build error.
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // Dependency on the shared core logic module
    implementation(project(":core"))

    // Standard Kotlin library
    implementation(kotlin("stdlib"))

    // Coroutines for any async operations if needed in the future
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // CSV writing library to generate structured reports
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.9.3")
}

application {
    // Define the entry point of our command-line application
    mainClass.set("io.pm.finlight.analyzer.SmsAnalysisToolKt")
}
