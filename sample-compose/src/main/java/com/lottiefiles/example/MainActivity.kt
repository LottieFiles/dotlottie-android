package com.lottiefiles.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lottiefiles.example.homesample.presentation.HomeScreen
import com.lottiefiles.example.homesample.presentation.HomeUIState
import com.lottiefiles.example.performance.BenchmarkScreen
import com.lottiefiles.example.performance.PerformanceTestScreen
import com.lottiefiles.example.performance.PermissionsHelper
import com.lottiefiles.example.performance.enableHardwareAcceleration
import com.lottiefiles.example.ui.theme.ExampleTheme

class MainActivity : ComponentActivity() {
    // Store the current screen to handle back navigation
    private val currentScreen = mutableStateOf<Screen>(Screen.Menu)

    // Permission request launchers
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable hardware acceleration for better performance
        enableHardwareAcceleration()

        // Set up back button handling
        setupBackButtonHandling()

        // Request storage permissions early - for Android 10 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        setContent {
            ExampleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        selectedScreen = currentScreen.value,
                        onScreenSelected = { screen ->
                            currentScreen.value = screen
                        }
                    )
                }
            }
        }
    }

    private fun setupBackButtonHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // If not on the menu screen, go back to menu
                if (currentScreen.value !is Screen.Menu) {
                    currentScreen.value = Screen.Menu
                } else {
                    // If on menu screen, allow default back behavior (exit app)
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Handle the permission result
        if (requestCode == PermissionsHelper.STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun MainScreen(
    selectedScreen: Screen,
    onScreenSelected: (Screen) -> Unit
) {
    when (selectedScreen) {
        is Screen.Menu -> {
            MenuScreen(onScreenSelected = onScreenSelected)
        }

        is Screen.Home -> {
            HomeScreen(
                uiState = HomeUIState(),
                onAnimationClick = {},
                onBackClick = { onScreenSelected(Screen.Menu) }
            )
        }

        is Screen.PerformanceTest -> {
            PerformanceTestScreen(
                onBackClick = { onScreenSelected(Screen.Menu) }
            )
        }

        is Screen.Benchmark -> {
            BenchmarkScreen(
                onBackClick = { onScreenSelected(Screen.Menu) }
            )
        }
    }
}

@Composable
fun MenuScreen(onScreenSelected: (Screen) -> Unit) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DotLottie Performance Testing",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { onScreenSelected(Screen.Home) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "HomeScreen (Normal UI)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onScreenSelected(Screen.PerformanceTest) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Interactive Performance Test")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onScreenSelected(Screen.Benchmark) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Automated Benchmark Suite")
            }
        }
    }
}

sealed class Screen {
    object Menu : Screen()
    object Home : Screen()
    object PerformanceTest : Screen()
    object Benchmark : Screen()
}

