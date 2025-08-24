# dotLottie Android

<p align="center">
  <a href="https://android-arsenal.com/api?level=21"><img alt="API" src="https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat"/></a>
  <a href="https://jitpack.io/#LottieFiles/dotlottie-android"><img alt="API" src="https://jitpack.io/v/LottieFiles/dotlottie-android.svg"/></a>
  <a href="https://github.com/LottieFiles/dotlottie-rs/releases/tag/v0.1.47"><img alt="API" src="https://img.shields.io/badge/dotlottie--rs-0.1.47-blue"/></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/License-MIT-yellow.svg"/></a>
</p>
<br />

<p align="center">  
A powerful Android library for rendering Lottie and dotLottie animations with advanced features like interactivity and theming
</p>

<br>

## Introduction

**dotLottie Android** is a comprehensive Android library for rendering Lottie and dotLottie animations. It provides a simple and intuitive API for loading, playing, and controlling animations, as well as advanced features like interactivity, theming, and state machines. Built with [dotLottie-rs](https://github.com/LottieFiles/dotlottie-rs) for high-performance rendering, it supports both traditional Android Views and Jetpack Compose.

### What is dotLottie?

dotLottie is an open-source file format that bundles one or more Lottie animations along with their assets into a single, compressed `.lottie` file. It uses ZIP compression for efficient storage and distribution. The format also supports advanced features like:

- **Interactive animations** with state machines
- **Dynamic theming** for customizable appearances
- **Multi-animation support** in a single file
- **Efficient compression** for smaller file sizes

This makes dotLottie a powerful tool for creating dynamic and interactive animations that go beyond traditional Lottie capabilities.

[Learn more about dotLottie](https://dotlottie.io)

## Demo

You can find and run the sample application in the `sample` directory.

<p align="center">
  <img src="/assets/preview-1.gif" width="32%"/>
</p>

## Installation

To add DotLottie Android, you need to add this dependency to your module's gradle file:

```kotlin
repositories {
    maven(url = "https://jitpack.io")
}
```

```kotlin
dependencies {
    implementation("com.github.LottieFiles:dotlottie-android:0.5.0")
}
```

## Getting started

If your Android application is built using XML layouts, you simply need to add `DotLottieAnimation` to your layout file and define parameters such as speed, animation source, etc.

### Using XML

First, place your animation in the assets folder of your Android project and add `DotLottieAnimation` to your XML file:

```xml
    <com.lottiefiles.dotlottie.core.widget.DotLottieAnimation
        android:id="@+id/lottie_view"
        android:layout_width="200dp"
        app:speed="3"
        app:src="swinging.json"
        android:layout_height="200dp" />
```

### Using Kotlin code

In your Kotlin code, get a reference to the component you added to your layout. This gives you access to methods that allow you to interact with the animation:

```kotlin
val dotLottieAnimationView = findViewById<DotLottieAnimation>(R.id.lottie_view)
```

Set up the initial animation configuration:

#### Traditional UI

```kotlin
import com.lottiefiles.dotlottie.core.model.Config

val config = Config.Builder()
    .autoplay(true)
    .speed(1f)
    .loop(true)
    .source(DotLottieSource.Url("https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie"))
//    .source(DotLottieSource.Asset("file.json")) // asset from the asset folder .json or .lottie
//    .source(DotLottieSource.Res(R.raw.animation)) // resource from raw resources .json or .lottie
    .useInterpolation(true)
    .playMode(Mode.Forward)
    .build()
dotLottieAnimationView.load(config)
```

### Using JetPack Compose

```kotlin
import com.lottiefiles.dotlottie.core.compose.ui.DotLottieAnimation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.dotlottie.dlplayer.Mode

fun ExampleComposeComponent() {
    DotLottieAnimation(
        source = DotLottieSource.Url("https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie"), // from url .lottie / .json
//        source = DotLottieSource.Asset("file.json"), // from asset .lottie / .json
//        source = DotLottieSource.Res(R.raw.animation), // from raw resources .json or .lottie
//        source = DotLottieSource.Json("{"v":"4.8.0","meta":{"g":"LottieFiles .........."), // lottie json string
//        source = DotLottieSource.Data(ByteArray), // dotLottie data as ByteArray
        autoplay = true,
        loop = true,
        speed = 3f,
        useFrameInterpolation = false,
        playMode = Mode.FORWARD,
        modifier = Modifier.background(Color.LightGray)
    )
}
```

#### Controlling player using JetPack Compose

```kotlin
import com.lottiefiles.dotlottie.core.compose.ui.DotLottieAnimation
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottieController
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.dotlottie.dlplayer.Mode

fun ExampleComposeComponent() {
    val dotLottieController = remember { DotLottieController() }

    // This effect runs once when the composable enters the composition
    LaunchedEffect(UInt) {
        dotLottieController.setLoop(true)
        dotLottieController.setSpeed(3f)
        // You can control the animation directly using the controller
        dotLottieController.play()

        // In a real app, you would call these methods from button clicks
        // or other user events.
        // For example:
        // Button(onClick = { dotLottieController.pause() }) { Text("Pause") }
        // Button(onClick = { dotLottieController.stop() }) { Text("Stop") }
    }

    DotLottieAnimation(
        source = DotLottieSource.Url("https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie"), // url of .json or .lottie
        autoplay = false,
        loop = false,
        speed = 1f,
        useFrameInterpolation = false,
        playMode = Mode.FORWARD,
        controller = dotLottieController
    )
}

```

## State Machine Support

DotLottie Android supports interactive animations with state machines for advanced user interactions:

### Loading State Machines

```kotlin
// Load state machine by ID from .lottie file
dotLottieAnimationView.stateMachineLoad("state_machine_id")

// Or load state machine from JSON data
dotLottieAnimationView.stateMachineLoadData(stateMachineJsonData)

// Start the state machine
dotLottieAnimationView.stateMachineStart()
```

### State Machine Events

Monitor state machine events by implementing `StateMachineEventListener`:

```kotlin
private val stateMachineListener = object : StateMachineEventListener {
    override fun onStateEntered(enteringState: String) {
        Log.d(TAG, "Entered state: $enteringState")
    }

    override fun onStateExit(leavingState: String) {
        Log.d(TAG, "Exited state: $leavingState")
    }

    override fun onTransition(previousState: String, newState: String) {
        Log.d(TAG, "Transitioned from $previousState to $newState")
    }

    override fun onNumericInputValueChange(inputName: String, oldValue: Float, newValue: Float) {
        Log.d(TAG, "Input $inputName changed from $oldValue to $newValue")
    }

    override fun onStringInputValueChange(inputName: String, oldValue: String, newValue: String) {
        Log.d(TAG, "Input $inputName changed from $oldValue to $newValue")
    }

    override fun onBooleanInputValueChange(inputName: String, oldValue: Boolean, newValue: Boolean) {
        Log.d(TAG, "Input $inputName changed from $oldValue to $newValue")
    }

    override fun onInputFired(inputName: String) {
        Log.d(TAG, "Input fired: $inputName")
    }

    override fun onCustomEvent(message: String) {
        Log.d(TAG, "Custom event: $message")
    }

    override fun onError(message: String) {
        Log.e(TAG, "State machine error: $message")
    }
}

// Add the listener
dotLottieAnimationView.addStateMachineEventListener(stateMachineListener)
```

### State Machine Input Controls

```kotlin
// Set input values
dotLottieAnimationView.stateMachineSetNumericInput("inputName", 42.0f)
dotLottieAnimationView.stateMachineSetStringInput("inputName", "value")
dotLottieAnimationView.stateMachineSetBooleanInput("inputName", true)

// Get input values
val numericValue = dotLottieAnimationView.stateMachineGetNumericInput("inputName")
val stringValue = dotLottieAnimationView.stateMachineGetStringInput("inputName")
val booleanValue = dotLottieAnimationView.stateMachineGetBooleanInput("inputName")

// Fire events
dotLottieAnimationView.stateMachineFireEvent("eventName")

// Get current state
val currentState = dotLottieAnimationView.stateMachineCurrentState()

// Stop state machine
dotLottieAnimationView.stateMachineStop()
```

## Performance Optimization

### Multi-threading Support

DotLottie Android supports multi-threaded rendering for improved performance. You can specify the number of threads to use for rendering:

#### Traditional UI

```kotlin
val config = Config.Builder()
    .source(DotLottieSource.Asset("animation.lottie"))
    .threads(6u) // Use 6 threads for rendering
    .autoplay(true)
    .loop(true)
    .build()

dotLottieAnimationView.load(config)
```

#### Jetpack Compose

```kotlin
DotLottieAnimation(
    source = DotLottieSource.Asset("animation.lottie"),
    threads = 6u, // Use 6 threads for rendering
    autoplay = true,
    loop = true,
    modifier = Modifier.size(300.dp)
)
```

## Theme Support

DotLottie Android supports dynamic theming for .lottie files:

```kotlin
// Load theme by ID from .lottie file manifest
dotLottieAnimationView.loadTheme("theme_id")

// Load theme from JSON data
dotLottieAnimationView.loadThemeData(themeJsonData)
```

## API Reference

#### Playback Control

- `play()`: Plays animation from the current frame.
- `pause()`: Pauses animation at the current frame.
- `stop()`: Stops and resets the animation to its initial frame.
- `setSpeed(Float)`: Sets animation speed (`1f` is normal).
- `setLoop(Boolean)`: Toggles animation looping.
- `setPlayMode(Mode)`: Sets playback direction (e.g., `Forward`, `Reverse`).
- `setSegment(Float, Float)`: Sets a specific frame segment to play.
- `setMarker(String)`: Plays the animation between a named marker.

#### Animation State & Properties

- `isPlaying`: `true` if the animation is currently playing.
- `isPaused`: `true` if the animation is paused.
- `isStopped`: `true` if the animation is stopped.
- `isLoaded`: `true` if an animation has been successfully loaded.
- `totalFrames`: The total number of frames in the animation.
- `currentFrame`: The current frame number.
- `duration`: The total animation duration in seconds.
- `speed`: The current animation speed.
- `loop`: `true` if looping is enabled.
- `loopCount`: The number of times the animation has looped.

#### Configuration & Loading

- `load(Config)`: Loads an animation with a specified configuration.
- `setBackgroundColor(Int)`: Sets the animation's background color.
- `setUseFrameInterpolation(Boolean)`: Toggles frame interpolation for smoother playback.
- `setLayout(Fit, LayoutUtil.Alignment)`: Sets the animation layout configuration.

#### Performance

- `threads(UInt)` (Config.Builder): Sets the number of rendering threads for improved performance.

#### Theming

- `loadTheme(String)`: Loads a theme from the .lottie file by its ID.
- `loadThemeData(String)`: Loads a theme from theme JSON data.

#### State Machine

- `stateMachineLoad(String)`: Loads a state machine by its ID.
- `stateMachineStart()`: Starts the loaded state machine.
- `stateMachineStop()`: Stops the state machine.
- `stateMachineFireEvent(String)`: Fires a named event.
- `stateMachineSetNumericInput(String, Float)`: Sets a numeric input value.
- `stateMachineSetStringInput(String, String)`: Sets a string input value.
- `stateMachineSetBooleanInput(String, Boolean)`: Sets a boolean input value.
- `stateMachineGetNumericInput(String)`: Gets a numeric input value.
- `stateMachineGetStringInput(String)`: Gets a string input value.
- `stateMachineGetBooleanInput(String)`: Gets a boolean input value.
- `stateMachineCurrentState()`: Returns the current state machine state.
- `addStateMachineEventListener(StateMachineEventListener)`: Adds a listener for state machine events.

#### Events

It's possible to monitor events from your animation.
First, create an instance of `DotLottieEventListener` and attach it to the
`DotLottieAnimation` component:

```kotlin
private val eventListener = object : DotLottieEventListener {
    override fun onPlay() {
        Log.d(TAG, "onPlay")
    }

    override fun onPause() {
        Log.d(TAG, "onPause")
    }

    override fun onStop() {
        Log.d(TAG, "onStop")
    }

    override fun onFrame(frame: Float) {
        Log.d(TAG, "frame $frame")
    }

    override fun onComplete() {

    }

    override fun onDestroy() {

    }

    override fun onFreeze() {

    }

    override fun onLoad() {

    }

    override fun onLoop() {

    }

    override fun onUnFreeze() {

    }
}
```

Attach the listener to the component:

```kotlin
dotLottieAnimationView.addEventListener(eventListener)

// For state machine events
dotLottieAnimationView.addStateMachineEventListener(stateMachineListener)
```

## Contributing

We welcome contributions! Please see our [`CONTRIBUTING.md`](CONTRIBUTING.md) file for guidelines on how to report issues, request features, and submit pull requests.

## Supported ABIs

The jniLibs in this library support the following ABIs:

- armeabi-v7a
- arm64-v8a
- x86_64
- x86

Our Jitpack release is a universal AAR that includes all supported ABIs. To reduce the size of your APK, you can exclude the ABIs that you do not require by configuring the abiFilters in your build.gradle.kts as shown below:

```kotlin
android {
  defaultConfig {
    ndk {
      abiFilters.add("arm64-v8a")
      abiFilters.add("armeabi-v7a")
    }
  }
}
```

## License

This project is licensed under the **MIT License**. See the [LICENSE](./LICENSE) file for details.
