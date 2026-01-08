package com.lottiefiles.example

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dotlottie.dlplayer.GlobalInputsObserver
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottieController
import com.lottiefiles.dotlottie.core.compose.ui.DotLottieAnimation
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import kotlinx.coroutines.delay

class MyGlobalInputsObserver : GlobalInputsObserver {
    override fun onBooleanGlobalInputValueChange(
        globalInputName: String,
        oldValue: Boolean,
        newValue: Boolean
    ) {
        Log.d("GlobalInputs", "Changed: $globalInputName old $oldValue new $newValue")
    }

    override fun onColorGlobalInputValueChange(
        globalInputName: String,
        oldValue: List<Float>,
        newValue: List<Float>
    ) {
        Log.d("GlobalInputs", "Changed: $globalInputName old $oldValue new $newValue")
    }

    override fun onGradientGlobalInputValueChange(
        globalInputName: String,
        oldValue: List<Float>,
        newValue: List<Float>
    ) {
        Log.d("GlobalInputs", "Changed: $globalInputName old $oldValue new $newValue")
    }

    override fun onNumericGlobalInputValueChange(
        globalInputName: String,
        oldValue: Float,
        newValue: Float
    ) {
        Log.d("GlobalInputs", "Changed: $globalInputName old $oldValue new $newValue")
    }

    override fun onStringGlobalInputValueChange(
        globalInputName: String,
        oldValue: String,
        newValue: String
    ) {
        Log.d("GlobalInputs", "Changed: $globalInputName old $oldValue new $newValue")
    }

    override fun onVectorGlobalInputValueChange(
        globalInputName: String,
        oldValue: List<Float>,
        newValue: List<Float>
    ) {
        Log.d("GlobalInputs", "Changed: $globalInputName old $oldValue new $newValue")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalInputsExample() {
    val dotLottieController = remember { DotLottieController() }
    var entry by remember { mutableStateOf("") }
    var isReady by remember { mutableStateOf(false) }
    val globalInputsObserver = remember { MyGlobalInputsObserver() }

    val eventListener = remember {
        object : DotLottieEventListener {
            override fun onLoad() {
                Log.d("DotLottie", "Animation loaded")
            }

            override fun onPlay() {
                Log.d("DotLottie", "Playing")
            }

            override fun onPause() {
                Log.d("DotLottie", "Paused")
            }

            override fun onStop() {
                Log.d("DotLottie", "Stopped")
            }

            override fun onComplete() {
                Log.d("DotLottie", "Completed")
            }

            override fun onFrame(frame: Float) {}

            override fun onFreeze() {
                Log.d("DotLottie", "Freeze")
            }

            override fun onUnFreeze() {
                Log.d("DotLottie", "UnFreeze")
            }
        }
    }

    // Initialize global inputs after animation loads
    LaunchedEffect(Unit) {
        // Give the animation time to load
        delay(500)

        val theme = dotLottieController.setTheme("theme")
        val load = dotLottieController.globalInputsLoad("inputs")
        val start = dotLottieController.globalInputsStart()

        dotLottieController.globalInputsSubscribe(globalInputsObserver)

        Log.d("DataBinding", "theme: $theme")
        Log.d("DataBinding", "load: $load")
        Log.d("DataBinding", "start: $start")

        isReady = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Animation view
        DotLottieAnimation(
            source = DotLottieSource.Asset("animations/test_inputs_text_static.lottie"),
            autoplay = true,
            loop = true,
            controller = dotLottieController,
            eventListeners = listOf(eventListener),
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.Black.copy(alpha = 0.1f)
                )
                .clip(RoundedCornerShape(16.dp))
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Text Input section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = "Text Input",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = entry,
                onValueChange = { newValue ->
                    entry = newValue
                    if (isReady) {
                        Log.d("DataBinding", "new value: $newValue")
                        val result = dotLottieController.globalInputsSetString("text", newValue)
                        Log.d("DataBinding", "result: $result")
                    }
                },
                placeholder = { Text("Type something...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}