// This file should be in the root directory of your project

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // --- NEW: Add JitPack repository ---
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "Finlight" // Or your actual project name
include(":app")
include(":core")
include(":analyzer")
