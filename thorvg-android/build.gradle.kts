plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
}

group = "com.github.LottieFiles"
version = "0.0.3"

android {
    namespace = "com.lottiefiles.dotlottie.core"
    compileSdk = 33

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++14"
            }
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
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.LottieFiles"
            artifactId = "dotlottie-android"
            version = "0.0.3"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    implementation(libs.dotlottieloader.android)
    implementation(libs.androidx.lifecycle.runtime.ktx.v262)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
