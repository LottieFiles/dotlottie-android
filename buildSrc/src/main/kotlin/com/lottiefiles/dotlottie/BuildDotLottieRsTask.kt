package com.lottiefiles.dotlottie

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File
import java.util.Properties

abstract class BuildDotLottieRsTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rustSourceDir: DirectoryProperty

    @get:Input
    abstract val androidFeatures: ListProperty<String>

    @get:Input
    abstract val abis: ListProperty<String>

    @get:Input
    abstract val apiLevel: Property<Int>

    @get:Input
    abstract val ndkVersion: Property<String>

    @get:OutputDirectory
    abstract val jniLibsOutputDir: DirectoryProperty

    @get:OutputFile
    abstract val headerOutputFile: RegularFileProperty

    @TaskAction
    fun buildRust() {
        val sourceDir = rustSourceDir.get().asFile
        verifySources(sourceDir)
        verifyCargo()

        val ndkHome = resolveNdkPath()
        val makeExe = findMake()

        val abiToMakeTarget = mapOf(
            "arm64-v8a" to "android-aarch64",
            "armeabi-v7a" to "android-armv7",
            "x86_64" to "android-x86_64",
            "x86" to "android-x86"
        )

        val requestedAbis = abis.get()
        val makeTargets = requestedAbis.map { abi ->
            abiToMakeTarget[abi] ?: throw GradleException("Unknown ABI: $abi")
        }

        val featuresArg = androidFeatures.get().joinToString(",")

        val command = buildList {
            add(makeExe)
            add("-f")
            add("make/android.mk")
            addAll(makeTargets)
            add("android-package")
            add("ANDROID_DEFAULT_FEATURES=tvg,tvg-sw,c_api")
            add("ANDROID_FEATURES=$featuresArg")
            add("ANDROID_NDK_HOME=$ndkHome")
            add("API_LEVEL=${apiLevel.get()}")
        }

        logger.lifecycle("Running: ${command.joinToString(" ")}")

        val result = ProcessBuilder(command)
            .directory(project.rootDir)
            .inheritIO()
            .start()
            .waitFor()

        if (result != 0) {
            throw GradleException("Rust build failed with exit code $result")
        }

        copyOutputs(sourceDir, requestedAbis)
    }

    private fun verifySources(sourceDir: File) {
        if (!sourceDir.exists() || sourceDir.listFiles().isNullOrEmpty()) {
            throw GradleException(
                "deps/dotlottie-rs submodule is not initialized. " +
                "Run: git submodule update --init --recursive"
            )
        }
    }

    private fun verifyCargo() {
        val result = runCatching {
            ProcessBuilder("cargo", "--version")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }.getOrElse {
            throw GradleException(
                "cargo not found on PATH. Install Rust from https://rustup.rs"
            )
        }
        if (result != 0) {
            throw GradleException(
                "cargo --version failed. Ensure Rust is correctly installed."
            )
        }
    }

    private fun findMake(): String {
        val systemMake = File("/usr/bin/make")
        if (systemMake.exists()) return systemMake.absolutePath
        return "make"
    }

    private fun resolveNdkPath(): String {
        val localProps = File(project.rootDir, "local.properties")
        if (localProps.exists()) {
            val props = Properties().apply { load(localProps.inputStream()) }

            val ndkDir = props.getProperty("ndk.dir")
            if (ndkDir != null && File(ndkDir).exists()) {
                return ndkDir
            }

            val sdkDir = props.getProperty("sdk.dir")
            if (sdkDir != null) {
                val ndkPath = File(sdkDir, "ndk/${ndkVersion.get()}")
                if (ndkPath.exists()) return ndkPath.absolutePath
            }
        }

        val ndkHome = System.getenv("ANDROID_NDK_HOME")
        if (ndkHome != null && File(ndkHome).exists()) return ndkHome

        val androidHome = System.getenv("ANDROID_HOME")
        if (androidHome != null) {
            val ndkPath = File(androidHome, "ndk/${ndkVersion.get()}")
            if (ndkPath.exists()) return ndkPath.absolutePath
        }

        throw GradleException(
            """
            Android NDK not found. Provide one of:
              1. ndk.dir=/path/to/ndk  in local.properties
              2. sdk.dir=/path/to/sdk  in local.properties (NDK ${ndkVersion.get()} must be installed)
              3. ANDROID_NDK_HOME environment variable
              4. ANDROID_HOME environment variable (NDK ${ndkVersion.get()} must be installed)
            """.trimIndent()
        )
    }

    private fun copyOutputs(sourceDir: File, requestedAbis: List<String>) {
        val releaseAndroid = File(project.rootDir, "release/android")
        val jniLibsOut = jniLibsOutputDir.get().asFile
        val headerOut = headerOutputFile.get().asFile

        for (abi in requestedAbis) {
            val srcDir = File(releaseAndroid, "jniLibs/$abi")
            val dstDir = File(jniLibsOut, abi)
            dstDir.mkdirs()
            srcDir.listFiles()?.forEach { file ->
                file.copyTo(File(dstDir, file.name), overwrite = true)
                logger.lifecycle("Copied ${file.name} → jniLibs/$abi/")
            }
        }

        val header = File(releaseAndroid, "include/dotlottie_player.h")
        if (header.exists()) {
            headerOut.parentFile.mkdirs()
            header.copyTo(headerOut, overwrite = true)
            logger.lifecycle("Copied dotlottie_player.h → ${headerOut.relativeTo(project.rootDir)}")
        } else {
            throw GradleException("Header not found at ${header.absolutePath}")
        }
    }
}
