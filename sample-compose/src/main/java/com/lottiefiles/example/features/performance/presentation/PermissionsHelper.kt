package com.lottiefiles.example.features.performance.presentation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper class for handling storage permissions in the app
 */
object PermissionsHelper {
    private const val TAG = "PermissionsHelper"

    // Make this public so it can be accessed from MainActivity
    const val STORAGE_PERMISSION_CODE = 1001

    /**
     * Check if storage permission is granted
     */
    fun hasStoragePermission(context: Context): Boolean {
        // For Android 10 and below, we need explicit permissions
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        // For Android 11+ (API 30+), we don't need specific permissions for app-specific directories
        // But we'll check for general external files dir access
        return canWriteToExternalFilesDir(context)
    }

    /**
     * Request storage permission
     */
    fun requestStoragePermission(activity: Activity, onPermissionResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+, we only need specific permissions for accessing other apps' files
            // For our own app files, we don't need special permissions
            onPermissionResult(canWriteToExternalFilesDir(activity))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // For Android 6-10, request runtime permissions
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )

            // We'll need to handle onRequestPermissionsResult in the activity
            // This result will come later
            onPermissionResult(false)
        } else {
            // For Android 5 and below, permissions are granted at install time
            onPermissionResult(true)
        }
    }

    /**
     * Handle the result of permission request
     */
    fun handlePermissionResult(requestCode: Int, grantResults: IntArray): Boolean {
        return when (requestCode) {
            STORAGE_PERMISSION_CODE -> {
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            }

            else -> false
        }
    }

    /**
     * Helper to check if we have at least write permission on external files dir
     * This is always true for app-specific directories on Android 11+
     */
    fun canWriteToExternalFilesDir(context: Context): Boolean {
        val dir = context.getExternalFilesDir(null)
        return dir?.canWrite() == true
    }

    /**
     * Get a directory suitable for writing benchmark reports
     */
    fun getBenchmarkReportsDir(context: Context): java.io.File? {
        val externalFilesDir = context.getExternalFilesDir(null)

        // Create the directory if it doesn't exist
        if (externalFilesDir != null && !externalFilesDir.exists()) {
            if (!externalFilesDir.mkdirs()) {
                Log.e(TAG, "Failed to create external files directory")
                return null
            }
        }

        return externalFilesDir
    }
}

/**
 * Composable that manages permissions or skips permission request
 * if not needed for the current Android version
 */
@Composable
fun PermissionRequiredScreen(
    onPermissionGranted: @Composable () -> Unit
) {
    val context = LocalContext.current

    // On Android 11+ (API 30+), we can always access app-specific directories
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Check if we can write to our own app directory
        if (PermissionsHelper.canWriteToExternalFilesDir(context)) {
            onPermissionGranted()
        } else {
            // This should rarely happen - show a simple error message
            StorageErrorScreen()
        }
    } else {
        // For older Android versions, we need to request permissions
        StoragePermissionScreen(onPermissionGranted)
    }
}

/**
 * Screen that requests storage permissions for Android 6-10
 */
@Composable
private fun StoragePermissionScreen(
    onPermissionGranted: @Composable () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(PermissionsHelper.hasStoragePermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    // If permission is granted, show the content
    if (hasPermission) {
        onPermissionGranted()
        return
    }

    // Otherwise show permission request UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Storage Permission Required",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "This app needs storage permission to save benchmark results."
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q
                ) {
                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        ) {
            Text(text = "Grant Permission")
        }
    }
}

/**
 * Screen shown when there's a problem accessing storage
 */
@Composable
private fun StorageErrorScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Storage Access Error",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "The app cannot access the storage to save benchmark results. Please check your device settings or contact support."
        )
    }
} 