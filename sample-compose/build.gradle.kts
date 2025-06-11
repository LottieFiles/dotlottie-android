plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.lottiefiles.example"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lottiefiles.example"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
//        kotlinCompilerExtensionVersion = "1.1.0"
        kotlinCompilerExtensionVersion = "1.5.1"
//        kotlinCompilerExtensionVersion = "1.2.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts.add("**/libjnidispatch.so")
            pickFirsts.add("lib/x86/libjnidispatch.so")
            pickFirsts.add("lib/x86_64/libjnidispatch.so")
            pickFirsts.add("lib/arm64-v8a/libjnidispatch.so")
            pickFirsts.add("lib/armeabi-v7a/libjnidispatch.so")
        }
    }
}

dependencies {
    implementation(project(":dotlottie"))
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation(libs.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx.v262)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}