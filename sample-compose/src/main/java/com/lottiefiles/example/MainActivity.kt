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

        // Set up back button handling
        setupBackButtonHandling()

        setContent {
            ExampleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
//                    DefaultAnimationDemo()
//                    AnimationWithReactiveProps()
//                    MarkerExample()
//                    ThemeExample()
//                    ThemeDataExample()
//                    LayoutExample()
                    ThreadCountExample()
//                    StateMachineExample()
                }
            }
        }
    }
}

@Composable
@Preview
fun DefaultAnimationDemo() {
    val dotLottieController = remember { DotLottieController() }
    val useFrameInterpolation = remember { mutableStateOf(true) }
    val loop = remember { mutableStateOf(true) }
    val speed = remember { mutableFloatStateOf(1f) }
    val segment = remember { mutableStateOf(1f..100f) }
    val currentFrame = remember { mutableFloatStateOf(0f) }
    val totalFrame = remember { mutableFloatStateOf(0f) }
    val dropdownExpand = remember { mutableStateOf(false) }
    val dropdownActive = remember { mutableStateOf("") }
    val hide = remember { mutableStateOf(false) }
    val events = object : DotLottieEventListener {
        override fun onLoad() {
            Log.i("DotLottie", "Loaded")
        }

        override fun onPause() {
            Log.i("DotLottie", "paused")
        }

        override fun onPlay() {
            Log.i("DotLottie", "Play")
        }

        override fun onStop() {
            Log.i("DotLottie", "Stop")
        }

        override fun onComplete() {
            Log.i("DotLottie", "Completed")
        }

        override fun onUnFreeze() {
            Log.i("DotLottie", "UnFreeze")
        }

        override fun onFrame(frame: Float) {
            currentFrame.value = frame
//            totalFrame.value = dotLottieController.totalFrames
        }

        override fun onFreeze() {
            Log.i("DotLottie", "Freeze")
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (hide.value) {
            Spacer(modifier = Modifier.padding(8.dp))
            Text(text = "Removed", fontSize = 24.sp)
        } else {
            repeat(1) {
                Row {
                    repeat(1) { // Example: Repeat 3 times
                        DotLottieAnimation(
                            autoplay = true,
                            loop = true,
                            eventListeners = listOf(events),
//                                        source = DotLottieSource.Url("https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie"),
//                            source = DotLottieSource.Url("https://lottie.host/5525262b-4e57-4f0a-8103-cfdaa7c8969e/VCYIkooYX8.json"),
                            source = DotLottieSource.Url("https://lottie.host/294b684d-d6b4-4116-ab35-85ef566d4379/VkGHcqcMUI.lottie"),
//                                        source = DotLottieSource.Asset("swinging.json"),
                            modifier = Modifier
                                .background(Color.LightGray)
                                .size(200.dp),
                            controller = dotLottieController
                        )
                    }
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            Row(modifier = Modifier.padding(2.dp)) {
                Button(onClick = {
                    hide.value = true
                }) {
                    Text(text = "Remove")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    hide.value = false
                }) {
                    Text(text = "Render")
                }
            }
            Row(modifier = Modifier.padding(2.dp)) {
                Text(text = "%.2f / %.2f ".format(currentFrame.value, totalFrame.value))
            }
            Row(modifier = Modifier.padding(2.dp)) {
                Button(onClick = {
                    dotLottieController.play()
                }) {
                    Text(text = "Play")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    Log.i("DotLottie", "Pause $dotLottieController")
                    dotLottieController.pause()
                }) {
                    Text(text = "Pause")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    dotLottieController.stop()
                }) {
                    Text(text = "Stop")
                }
            }
            Text(text = "Modes:", fontSize = 12.sp)
            Row(modifier = Modifier.padding(2.dp)) {
                Button(onClick = {
                    dotLottieController.setPlayMode(Mode.FORWARD)
                }) {
                    Text(text = ">")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    dotLottieController.setPlayMode(Mode.REVERSE)
                }) {
                    Text(text = "<")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    dotLottieController.setPlayMode(Mode.BOUNCE)
                }) {
                    Text(text = "<+>")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    dotLottieController.setPlayMode(Mode.REVERSE_BOUNCE)
                }) {
                    Text(text = "<->")
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Row(modifier = Modifier.padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "useFrameInterpolation: ")
                Checkbox(checked = useFrameInterpolation.value, onCheckedChange = {
                    dotLottieController.setUseFrameInterpolation(it)
                    useFrameInterpolation.value = it
                })
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "loop: ")
                Checkbox(checked = loop.value, onCheckedChange = {
                    dotLottieController.setLoop(it)
                    loop.value = it
                })
                Spacer(modifier = Modifier.width(8.dp))
            }
            Row(modifier = Modifier.padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Speed: ")
                Button(onClick = {
                    speed.value++
                    dotLottieController.setSpeed(speed.value)
                }) {
                    Text(text = "+")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "${speed.value}")
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (speed.value > 1) {
                        speed.value--
                        dotLottieController.setSpeed(speed.value)
                    }
                }) {
                    Text(text = "-")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    dotLottieController.setFrame(50f)
                }) {
                    Text(text = "Frame 50")
                }
            }
            Column(
                modifier = Modifier
                    .border(border = BorderStroke(1.dp, Color.LightGray))
                    .padding(10.dp, 4.dp)
            ) {
                Row {
                    RangeSlider(
                        value = segment.value,
                        onValueChange = { segment.value = it },
                        valueRange = 1f..100f
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
                text = "DotLottie Performance Testing",
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

@Composable
fun ThreadCountExample() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("6 Threads", fontSize = 16.sp)
        DotLottieAnimation(
            source = DotLottieSource.Url("https://lottie.host/294b684d-d6b4-4116-ab35-85ef566d4379/VkGHcqcMUI.lottie"),
            autoplay = true,
            loop = true,
            threads = 6u,
            modifier = Modifier
                .background(Color.LightGray)
                .size(300.dp)
                .border(BorderStroke(1.dp, Color.Gray)),
        )
    }
}
