package com.lottiefiles.dotlottie

import org.gradle.api.Plugin
import org.gradle.api.Project

class RustBuildPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "dotlottieRust",
            RustBuildExtension::class.java
        )

        extension.buildFromSource.convention(false)
        extension.features.convention(
            listOf(
                "dotlottie",
                "state-machines",
                "theming",
                "tvg-webp",
                "tvg-png",
                "tvg-jpg",
                "tvg-ttf",
                "tvg-lottie-expressions",
                "tvg-threads"
            )
        )
        extension.abis.convention(listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"))
        extension.apiLevel.convention(21)
        extension.ndkVersion.convention("")

        project.afterEvaluate {
            // Read ndkVersion from android { ndkVersion } via reflection if not set explicitly.
            val resolvedNdkVersion = if (extension.ndkVersion.get().isNotBlank()) {
                extension.ndkVersion.get()
            } else {
                val androidExt = project.extensions.findByName("android")
                runCatching {
                    androidExt?.javaClass?.getMethod("getNdkVersion")?.invoke(androidExt) as? String
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: ""
            }

            val buildTask = project.tasks.create("buildDotLottieRs", BuildDotLottieRsTask::class.java)
            buildTask.group = "dotlottie"
            buildTask.description = "Build dotlottie-rs from source for Android"
            buildTask.rustSourceDir.set(project.rootProject.file("deps/dotlottie-rs"))
            buildTask.androidFeatures.set(extension.features)
            buildTask.abis.set(extension.abis)
            buildTask.apiLevel.set(extension.apiLevel)
            buildTask.ndkVersion.set(resolvedNdkVersion)
            buildTask.jniLibsOutputDir.set(
                project.layout.projectDirectory.dir("src/main/jniLibs")
            )
            buildTask.headerOutputFile.set(
                project.layout.projectDirectory.file("src/main/cpp/dotlottie_player.h")
            )

            // Exclude Cargo build artifacts from up-to-date checks
            val sourceTree = project.fileTree(project.rootProject.file("deps/dotlottie-rs"))
            sourceTree.exclude("**/target/**")
            sourceTree.exclude("release/**")
            buildTask.inputs.files(sourceTree)

            // Only wire to preBuild when buildFromSource is enabled
            if (extension.buildFromSource.get()) {
                project.tasks.getByName("preBuild").dependsOn(buildTask)
            }
        }
    }
}
