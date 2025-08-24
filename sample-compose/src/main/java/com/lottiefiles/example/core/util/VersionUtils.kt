package com.lottiefiles.example.core.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.lottiefiles.example.BuildConfig

/**
 * Utility class for dynamically detecting DotLottie and app versions
 */
object VersionUtils {
    private const val TAG = "VersionUtils"
    
    /**
     * Get the DotLottie library version dynamically
     */
    fun getDotLottieVersion(): String {
        return try {
            // First try to get from BuildConfig (set during build)
            if (BuildConfig.DOTLOTTIE_VERSION.isNotEmpty()) {
                BuildConfig.DOTLOTTIE_VERSION
            } else {
                // Fallback to default version
                "0.11.0"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get DotLottie version dynamically, using fallback", e)
            "0.11.0" // Fallback version
        }
    }
    
    /**
     * Get the app version name
     */
    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Failed to get app version", e)
            "Unknown"
        }
    }
    
    /**
     * Get a formatted version string for display
     */
    fun getFormattedVersions(context: Context): String {
        val dotLottieVersion = getDotLottieVersion()
        val appVersion = getAppVersion(context)
        return "DotLottie v$dotLottieVersion (App v$appVersion)"
    }
    
    /**
     * Get just the DotLottie version for display
     */
    fun getDotLottieVersionForDisplay(): String {
        return "DotLottie v${getDotLottieVersion()}"
    }
    
    /**
     * Log version information for debugging
     */
    fun logVersionInfo(context: Context) {
        Log.i(TAG, "=== Version Information ===")
        Log.i(TAG, "DotLottie Version: ${getDotLottieVersion()}")
        Log.i(TAG, "App Version: ${getAppVersion(context)}")
        Log.i(TAG, "Build Config Version: ${BuildConfig.DOTLOTTIE_VERSION}")
        Log.i(TAG, "========================")
    }
}
