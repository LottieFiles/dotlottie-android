package com.lottiefiles.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lottiefiles.example.core.theme.ExampleTheme
import com.lottiefiles.example.core.util.enableHardwareAcceleration
import com.lottiefiles.example.core.util.VersionUtils
import com.lottiefiles.example.features.benchmark.presentation.BenchmarkScreen
import com.lottiefiles.example.features.home.presentation.HomeScreen
import com.lottiefiles.example.features.home.presentation.HomeUIState
import com.lottiefiles.example.features.home.presentation.LottieLibraryType
import com.lottiefiles.example.features.performance.presentation.PerformanceTestScreen

class MainActivity : ComponentActivity() {
    // Store the current screen to handle back navigation
    private val currentScreen = mutableStateOf<Screen>(Screen.Menu)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable hardware acceleration for better performance
        enableHardwareAcceleration()
        
        // Log version information and performance setup
        VersionUtils.logVersionInfo(this)

        // Set up back button handling
        setupBackButtonHandling()

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
                onBackClick = { onScreenSelected(Screen.Menu) },
                libraryType = selectedScreen.libraryType
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
    var showLibrarySelectionDialog by remember { mutableStateOf(false) }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${VersionUtils.getDotLottieVersionForDisplay()} Performance Testing",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { showLibrarySelectionDialog = true },
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

    if (showLibrarySelectionDialog) {
        AlertDialog(
            onDismissRequest = { showLibrarySelectionDialog = false },
            title = { Text("Select Lottie Library") },
            text = { Text("Choose which Lottie library to use for the HomeScreen") },
            confirmButton = {
                Button(
                    onClick = {
                        showLibrarySelectionDialog = false
                        onScreenSelected(Screen.Home(LottieLibraryType.DOT_LOTTIE))
                    }
                ) {
                    Text("DotLottie")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showLibrarySelectionDialog = false
                        onScreenSelected(Screen.Home(LottieLibraryType.AIRBNB_LOTTIE))
                    }
                ) {
                    Text("Airbnb Lottie")
                }
            }
        )
    }
}

sealed class Screen {
    object Menu : Screen()
    data class Home(val libraryType: LottieLibraryType) : Screen()
    object PerformanceTest : Screen()
    object Benchmark : Screen()
}
