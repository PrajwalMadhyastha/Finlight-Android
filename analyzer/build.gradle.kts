// build.gradle.kts for the :analyzer module
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply the Kotlin JVM plugin using the conventional ID.
    // This is a more standard declaration and resolves the dependency lookup issue.
    id("org.jetbrains.kotlin.jvm")
    // Apply the Kotlin serialization plugin to enable the XML parser.
    kotlin("plugin.serialization")
    // Apply the application plugin to make this module runnable.
    application
}

dependencies {
    // Standard Kotlin libraries
    implementation(kotlin("stdlib"))

    // Dependency on the shared 'core' module to access the SmsParser.
    implementation(project(":core"))

    // Add the Kotlinx Serialization library for XML.
    // This provides the necessary classes for XML parsing, including the Xml format builder.
    implementation("io.github.pdvrieze.xmlutil:serialization:0.86.3")
}

application {
    // Define the main class for the runnable application.
    mainClass.set("io.pm.finlight.analyzer.SmsAnalysisToolKt")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}
