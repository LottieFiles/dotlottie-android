<h1 align="center">Dot Lottie Android</h1><br>
<p align="center">  
Dot Lottie Android is a new lottie player that relies on ThorVG for rendering
</p>
<br>

<p align="center">
  <a href="https://android-arsenal.com/api?level=24"><img alt="API" src="https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat"/></a>
  <a href="https://jitpack.io/#LottieFiles/dotlottie-android"><img alt="API" src="https://jitpack.io/v/LottieFiles/dotlottie-android.svg"/></a>
  <a href="https://github.com/LottieFiles/dotlottie-rs/releases/tag/v0.1.40"><img alt="API" src="https://img.shields.io/badge/dotlottie--rs-0.1.40-blue"/></a>
</p>

## Demo

You can run the found the sample in the `sample` directory
<p align="center">
  <img src="/assets/preview-1.gif" width="32%"/>
</p>

## Installation

To add the DotLottieAndroid you need to add this dependency to module gradle file

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

If you android application is build in XML you just need to put `DotLottieAnimation` in your layout file
and define some set of parameter such as the speed the json animation etc...

### Using XML

First put your animation in the assets folder in your android and add `DotLottieAnimation` in your XML file

```xml
    <com.lottiefiles.dotlottie.core.widget.DotLottieAnimation
        android:id="@+id/lottie_view"
        android:layout_width="200dp"
        app:speed="3"
        app:src="swinging.json"
        android:layout_height="200dp" />
```

### Using Kotlin code

In your kotlin code, get access to the component just added in your layout
and you can have access to set of method that allow you interact with the animation

```kotlin
val dotLottieAnimationView = findViewById<DotLottieAnimation>(R.id.lottie_view)
```

Set up the initial animation configuration

#### Traditional UI 
```kotlin
import com.lottiefiles.dotlottie.core.model.Config

val config = Config.Builder()
    .autoplay(true)
    .speed(1f)
    .loop(true)
    .source(DotLottieSource.Url("https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie"))
//    .source(DotLottieSource.Asset("file.json")) // asset from the asset folder .json or .lottie
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
    
    LaunchedEffect(UInt) {
        dotLottieController.setLoop(true)
        dotLottieController.setSpeed(3f)
        // Play
        dotLottieController.play()
        // Pause
        dotLottieController.pause()
        // Stop
        dotLottieController.play()
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

### API

#### Properties
- `DotLottieAnimation.totalFrames` : Return the number of frame of the animations
- `DotLottieAnimation.currentFrame` : Get the current frame
- `DotLottieAnimation.playMode` : Return the  playMode of the animation, 
- `DotLottieAnimation.loop` : Return the repeat mode of the animation, 
- `DotLottieAnimation.loopCount` : Return the number of times animation has looped, 
- `DotLottieAnimation.duration` : Get the animation duration in millis
- `DotLottieAnimation.speed` : Get the animation speed
- `DotLottieAnimation.autoplay` : Get the animation speed
- `DotLottieAnimation.segment` : Get the first and last frame

- `DotLottieAnimation.isPlaying` : Check if the animation is playing
- `DotLottieAnimation.isStopped` : Check if the animation is stopped
- `DotLottieAnimation.isPaused` : Check if the animation is paused
- `DotLottieAnimation.isLoaded` : Check if the animation is Loaded

#### Methods
- `DotLottieAnimation.setSpeed(Float)` : Modifier the animation speed
- `DotLottieAnimation.setLoop(Boolean)` : Make the animation to loop or not
- `DotLottieAnimation.play()` : Play the animation
- `DotLottieAnimation.pause()` : Pause the animation
- `DotLottieAnimation.stop()` : Stop the animation
- `DotLottieAnimation.load(Config)` : Setup the initial configuration
- `DotLottieAnimation.setSegment(Float, Float)` : Defining the first and last frame
- `DotLottieAnimation.setPlayMode(Mode)` : Defining the first and last frame
- `DotLottieAnimation.setBackgroundColor(Int)` : Set the animation background
- `DotLottieAnimation.setUseFrameInterpolation(Boolean)` : When enabled it renders frames in between.
- `DotLottieAnimation.setMarker(String)` : Sets the lottie named marker to play.
- `DotLottieAnimation.setLayout(Fit, LayoutUtil.Alignment)` : Sets the animation layout configuration.
- `DotLottieAnimation.loadTheme(String)` : Loads a new theme from the .lottie file, using its ID as specified in the manifest.json file of the .lottie file.
- `DotLottieAnimation.loadThemeData(String)` : Loads a new theme using theme data.

#### Events

It's possible to monitor the event of your animation
first create an instance of the `DotLottieEventListener` and attache it to the 
`DotLottieAnimation` component

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

Attache the listener to the component, you can add one or 

```kotlin
dotLottieAnimationView.addEventListener(eventListener)
```

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
Refer to the [Android documentation](https://developer.android.com/ndk/guides/abis) for more information on ABI management.

## Introduction

DotLottie is a file format that enables efficient delivery of Lottie animations. This library provides a native Android player for DotLottie animations with advanced features like frame interpolation.

## Usage

```kotlin
// Load a DotLottie animation from a URL
val config = Config.Builder()
    .autoplay(true)
    .loop(true)
    .source(DotLottieSource.Url("https://example.com/animation.lottie"))
    .build()
    
dotLottieAnimation.load(config)

// Or load from a JSON URL
val config = Config.Builder()
    .autoplay(true)
    .loop(true)
    .source(DotLottieSource.Url("https://example.com/animation.json"))
    .build()
    
dotLottieAnimation.load(config)
```

## Performance Testing Framework

The project includes a comprehensive performance testing framework that allows comparing DotLottie with Airbnb's Lottie animation library. This helps in benchmarking and optimizing animation performance.

### Benchmark Features

- **Comparative Testing**: Test both DotLottie and Airbnb Lottie with the same animations
- **File Format Support**: Test DotLottie with both .json and .lottie formats to compare format efficiency
- **Metrics Tracked**:
  - Frames per second (FPS)
  - Memory usage (MB)
  - CPU usage (%)
  - Frame jank percentage (%)
  - Animation startup time (ms)
- **Configurable Tests**:
  - Number of animations (1, 5, 10, 20, 30 animations)
  - Animation size (100dp, 200dp)
  - Frame interpolation (DotLottie only)
- **CSV Report Generation**: Export detailed benchmark results for analysis

### Running the Benchmark

1. Open the sample app
2. Navigate to the "Performance" section
3. Select "Comparative Benchmark"
4. Press "Start Benchmark" to begin the tests
5. Wait for all tests to complete (usually takes several minutes)
6. View results on screen or share the CSV report

### Understanding Benchmark Results

The benchmark produces extensive data comparing the performance of both libraries. Here's how to interpret the results:

1. **FPS (Frames Per Second)**: Higher is better. Shows how smoothly animations render.
2. **Memory Usage**: Lower is better. Indicates memory efficiency.
3. **CPU Usage**: Lower is better. Shows how CPU-intensive the animations are.
4. **Jank Percentage**: Lower is better. Indicates frame rendering consistency.
5. **Startup Time**: Lower is better. Shows how quickly animations initialize.

Different patterns in the results can indicate various optimizations that might be possible:

- **High CPU usage with low FPS**: The render thread might be the bottleneck.
- **High memory usage with good FPS**: Memory optimization opportunities exist.
- **Lower performance with many animations**: Test parallelization limits.
- **Significant differences between formats**: Format-specific optimizations may be possible.

### File Format Comparison

The benchmark allows comparing DotLottie performance with both JSON and LOTTIE formats:

- **JSON format**: The original Lottie format, widely compatible.
- **LOTTIE format**: DotLottie's optimized container format, which can include multiple animations, fonts, and images.

This comparison helps understand the performance trade-offs between formats and allows making informed decisions about which format to use for different use cases.

### Frame Interpolation

DotLottie supports frame interpolation, which Airbnb Lottie does not. The benchmark tests both with and without frame interpolation to measure its performance impact.

Frame interpolation allows smoother animations by generating additional frames between existing ones, but has some CPU cost. The benchmark helps quantify this tradeoff.

## License

See the LICENSE file for details.
