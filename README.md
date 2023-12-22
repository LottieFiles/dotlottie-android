<h1 align="center">Dot Lottie Android</h1><br>
<p align="center">  
Dot Lottie Android is a new lottie player that relies on ThorVG for rendering
</p>
<br>

<p align="center">
  <a href="https://android-arsenal.com/api?level=24"><img alt="API" src="https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat"/></a>
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
    maven { url("https://jitpack.io") }
}
```

```kotlin
dependencies {
    implementation("com.github.LottieFiles:dotlottie-android:0.0.1")
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

`val dotLottieAnimationView = findViewById<DotLottieAnimation>(R.id.lottie_view)
`

Set up the initial animation configuration

`val config = DotLottieConfig.Builder()
    .autoPlay(true)
    .speed(1f)
    .loop(true)
    .src("test.json") // asset from the asset folder
    .backgroundColor("#FFFFFF")
    .build()
dotLottieAnimationView.load(config)`

### API

- `DotLottieAnimation.totalFrames` : Return the number of frame of the animations
- `DotLottieAnimation.currentFrame` : Get the current frame
- `DotLottieAnimation.mode` : Return the repeat mode of the animation, 
- `DotLottieAnimation.loop` : Return the repeat mode of the animation, 
- `DotLottieAnimation.loopCount` : Return the number of loop of the animation, 
- `DotLottieAnimation.duration` : Get the animation duration in millis
- `DotLottieAnimation.speed` : Get the animation speed
- `DotLottieAnimation.autoPlay` : Get the animation speed
- `DotLottieAnimation.segments` : Get the first and last frame

- `DotLottieAnimation.setSpeed(Float)` : Modifier the animation speed
- `DotLottieAnimation.setLoop(Boolean)` : Make the animation to loop or not
- `DotLottieAnimation.play()` : Play the animation
- `DotLottieAnimation.pause()` : Pause the animation
- `DotLottieAnimation.stop()` : Stop the animation
- `DotLottieAnimation.load(Config)` : Setup the initial configuration
- `DotLottieAnimation.setSegments(Float, Float)` : Defining the first and last frame

- `DotLottieAnimation.isPlaying()` : Check if the animation is playing
- `DotLottieAnimation.isStopped()` : Check if the animation is stopped
- `DotLottieAnimation.isPaused()` : Check if the animation is paused

#### Event

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

    override fun onFrame(frame: Int) {
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

`dotLottieAnimationView.addEventListener(eventListener)`


