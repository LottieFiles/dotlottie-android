pluginManagement {
    repositories {
        google()
        maven(url = "https://jitpack.io")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven(url = "https://jitpack.io")
        mavenCentral()
    }
}

rootProject.name = "dotlottie-android"
include(":sample")
include(":dotlottie")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}
