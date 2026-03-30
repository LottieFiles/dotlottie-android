package com.lottiefiles.dotlottie

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class RustBuildExtension {
    abstract val buildFromSource: Property<Boolean>
    abstract val features: ListProperty<String>
    abstract val abis: ListProperty<String>
    abstract val apiLevel: Property<Int>

    /**
     * Android NDK version string (e.g. "29.0.14206865").
     * Used to locate the NDK inside the SDK directory when ndk.dir is not set in local.properties.
     * Defaults to the value set via android { ndkVersion } if left blank.
     */
    abstract val ndkVersion: Property<String>
}
