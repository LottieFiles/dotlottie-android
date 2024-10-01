<h1 align="center">Dot Lottie Android</h1><br>
<p align="center">  
Dot Lottie Android is a new lottie player that relies on ThorVG for rendering
</p>
<br>

<p align="center">
  <a href="https://android-arsenal.com/api?level=24"><img alt="API" src="https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat"/></a>
  <a href="https://jitpack.io/#LottieFiles/dotlottie-android"><img alt="API" src="https://jitpack.io/v/LottieFiles/dotlottie-android.svg"/></a>
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
