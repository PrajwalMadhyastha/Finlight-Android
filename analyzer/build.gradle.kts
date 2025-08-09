// =================================================================================
// FILE: ./analyzer/build.gradle.kts
// REASON: REFACTOR - Added the 'kotlinx-serialization-xml' dependency to enable
// parsing of the XML SMS dump file.
// =================================================================================

plugins {
    kotlin("jvm")
    application
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

    // XML parsing library
    implementation("io.github.pdvrieze.xmlutil:serialization:0.86.3")
}

application {
    // Define the entry point of our command-line application
    mainClass.set("io.pm.finlight.analyzer.SmsAnalysisToolKt")
}
