package com.lottiefiles.example

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.dotlottie.dlplayer.OpenUrl
import com.dotlottie.dlplayer.OpenUrlMode
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottieController

import com.lottiefiles.dotlottie.core.compose.ui.DotLottieAnimation
import com.lottiefiles.dotlottie.core.util.DotLottieSource

fun transformAssetPath(input: String): String {
    val baseName = input.substringAfterLast("/").substringBeforeLast(".")
    return "statemachines/sm-$baseName.json"
}

suspend fun loadJsonFromAssets(context: Context, path: String): String {
    return context.assets.open(path).bufferedReader().use { it.readText() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StateMachineExample() {
    val context = LocalContext.current
    val dotLottieController = remember { DotLottieController() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedAnimation by remember { mutableStateOf<String?>("animations/click-button.json") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Animations",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                Divider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val imageRows = listOf(
                        listOf("animations/analytics.json"),
                        listOf("animations/click-button.json"),
                        listOf("animations/hold-button.json"),
                        listOf("animations/loader.json"),
                        listOf("animations/pigeon.lottie"),
                        listOf("animations/star-marked.lottie"),
                        listOf("animations/sync-to-cursor.lottie"),
                        listOf("animations/theming.lottie"),
                        listOf("animations/toggle.json")
                    )

                    imageRows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { imagePath ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .pointerInput(Unit) {
                                            detectTapGestures {
                                                selectedAnimation = imagePath
                                                scope.launch { drawerState.close() }
                                            }
                                        }
                                ) {
                                    DotLottieAnimation(
                                        source = DotLottieSource.Asset(imagePath),
                                        autoplay = true,
                                        loop = true,
                                        speed = 1f,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1.6f)
                                    )

                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        // Main content
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("dotLottie - State Machines Interactivity Lab") },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                if (selectedAnimation != null) {
                    var jsonString by remember { mutableStateOf<String?>(null) }
                    var loadResult by remember { mutableStateOf(false) }
                    var startResult by remember { mutableStateOf(false) }
                    var loadedStateMachine by remember { mutableStateOf("") }

                    LaunchedEffect(selectedAnimation) {
                        dotLottieController.stateMachineStop()

                        val stateMachineDataFromFile = transformAssetPath(selectedAnimation!!)

                        jsonString = loadJsonFromAssets(context, stateMachineDataFromFile)

                        loadedStateMachine = stateMachineDataFromFile

                        loadResult = dotLottieController.stateMachineLoadData(jsonString ?: "")
                        val openUrl = OpenUrl(
                            mode = OpenUrlMode.INTERACTION,
                            whitelist = emptyList()
                        );
                        if (loadResult) { startResult = dotLottieController.stateMachineStart(openUrl, context = context)
                        }
                    }

                    Column {
                        DotLottieAnimation(
                            source = DotLottieSource.Asset(selectedAnimation!!),
                            autoplay = false,
                            loop = false,
                            controller = dotLottieController,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.6f)
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val position = event.changes.first().position
                                            val screenWidth = size.width
                                            val percentage = (position.x / screenWidth * 100f).coerceIn(0f, 100f)

                                            Log.d("Gestures", percentage.toString())

                                            Log.d("DotLottie - set Input",
                                                dotLottieController.stateMachineSetNumericInput("Progress", percentage)
                                                    .toString()
                                            );
                                            Log.d("DotLottie - Fire", dotLottieController.stateMachineFire("Step").toString())
                                        }
                                    }
                                }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                buildAnnotatedString {
                                    append("Loaded animation : ")
                                    withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                                        append(selectedAnimation)
                                    }
                                },
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    buildAnnotatedString {
                                        append("Loaded state machine : ")
                                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                                            append(loadedStateMachine)
                                        }
                                    },
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Text(
                                    buildAnnotatedString {
                                        append("State Machine Load Result: ")
                                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                                            append(loadResult.toString())
                                        }
                                    },
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Text(
                                    buildAnnotatedString {
                                        append("State Machine Start Result: ")
                                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                                            append(startResult.toString())
                                        }
                                    },
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}