package com.lottiefiles.sample.performance

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper class to handle permission requests across different Android versions
 */
class PermissionsHelper(private val context: Context) {
    companion object {
        const val STORAGE_PERMISSION_REQUEST_CODE = 100
        const val MANAGE_STORAGE_REQUEST_CODE = 101
    }
    
    /**
     * Check if the app has appropriate storage permissions based on Android version
     */
    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ uses scoped storage, check if app has manage storage permission
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 uses runtime permissions
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                PackageManager.PERMISSION_GRANTED
        } else {
            // Android 5 and below use manifest permissions which are granted at install time
            true
        }
    }
    
    /**
     * Request appropriate storage permissions based on Android version
     */
    fun requestStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                // Android 11+: Request MANAGE_EXTERNAL_STORAGE permission
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse("package:${context.packageName}")
                activity.startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE)
            } catch (e: Exception) {
                // If the package URI intent fails, try with the Settings screen directly
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE)
            }
        } else {
            // Android 6-10: Request WRITE_EXTERNAL_STORAGE runtime permission
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    /**
     * Get the directory for storing benchmark files (works with scoped storage)
     * Uses app-specific directories that don't require special permissions
     */
    fun getBenchmarkStorageDirectory(): java.io.File? {
        return context.getExternalFilesDir("benchmarks")?.also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
    }
    
    /**
     * Check if the result from permission request is a grant
     */
    fun isPermissionGranted(requestCode: Int, grantResults: IntArray): Boolean {
        return when (requestCode) {
            STORAGE_PERMISSION_REQUEST_CODE -> {
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            }
            else -> false
        }
    }
    
    /**
     * Show a toast explaining why the permission is needed
     */
    fun showPermissionRationale() {
        Toast.makeText(
            context,
            "Storage permission is required to save benchmark results",
            Toast.LENGTH_LONG
        ).show()
    }
} 