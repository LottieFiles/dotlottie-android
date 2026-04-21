plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
    id("com.lottiefiles.dotlottie-rust")
}

dotlottieRust {
    // ─── Master switch ────────────────────────────────────────────────────────
    // false (default) → use the pre-built .so files already in jniLibs/
    // true            → compile dotlottie-rs from source (requires Rust + NDK)
    buildFromSource = false

    // ─── Optional: trim features for a smaller binary ─────────────────────────
    // The three core features (tvg, tvg-sw, c_api) are always included by the
    // plugin and cannot be removed. Remove any of the lines below to disable
    // that feature in the compiled library.
    //
     features = listOf(
         "dotlottie",            // .lottie zip container support
         "state-machines",       // state machine support (requires dotlottie)
         "theming",              // theming support (requires dotlottie)
         "tvg-webp",             // WebP image support
         "tvg-png",              // PNG image support
         "tvg-jpg",              // JPEG image support
         "tvg-ttf",              // TrueType font support
         "tvg-lottie-expressions", // Lottie expression evaluation
         "tvg-threads",          // multi-threaded rendering
     )

    // ─── Optional: build only specific CPU architectures ──────────────────────
    // Default builds all four. Use this to speed up dev builds:
    // abis = listOf("arm64-v8a", "x86_64")

    // ─── Optional: Android API level (default: 21 = Android 5.0) ─────────────
    // apiLevel = 21
}

group = "com.github.LottieFiles"
version = "0.13.7"

android {
    namespace = "com.lottiefiles.dotlottie.core"
    compileSdk = 35
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // CMake configuration
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    // CMake build configuration
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        jniLibs {
            pickFirsts += listOf("**/libc++_shared.so")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            packaging {
                jniLibs {
                    // Keep debug symbols
                    keepDebugSymbols += listOf("**/*.so")
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.LottieFiles"
            artifactId = "dotlottie-android"
            version = version
            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
        mavenLocal()
    }
}

dependencies {
    implementation(libs.core.ktx)
    // JitPack Compose
    implementation(libs.androidx.ui) {
        exclude(group = "androidx.compose.runtime")
        exclude(group = "androidx.compose.foundation")
        exclude(group = "androidx.compose.material3")
        exclude(group = "androidx.compose.material")
    }
    implementation(libs.androidx.material) {
        exclude(group = "androidx.compose.runtime")
    }

    implementation(libs.okhttp) {
        exclude(group = "com.squareup.okhttp3", module = "logging-interceptor")
        exclude(group = "com.squareup.okhttp3", module = "okhttp-urlconnection")
    }
}
