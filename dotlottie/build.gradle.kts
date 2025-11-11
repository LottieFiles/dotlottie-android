plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
}

group = "com.github.LottieFiles"
version = "0.12.3"

android {
    namespace = "com.lottiefiles.dotlottie.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    packaging {
        jniLibs {
            pickFirsts += listOf("**/libjnidispatch.so")
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
    implementation("net.java.dev.jna:jna:5.17.0@aar")
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
