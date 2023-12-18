# Dotlottie Android

## Installation

To add the DotLottieAndroid you need to add this dependency to module gradle file

```kotlin
repositories {
    maven { url("https://jitpack.io") }
}
```

```kotlin
dependencies {
    implementation("com.github.lottiefiles:dotlottie-android:<version>")
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
        app:repeatMode="reverse"
        app:repeatCount="1"
        app:speed="3"
        app:assetFilePath="swinging.json"
        android:layout_height="200dp" />
```

### Using Kotlin code

In your kotlin code, get access to the component just added in your layout
and you can have access to set of method that allow you interact with the animation

`val dotLottieAnimationView = findViewById<DotLottieAnimation>(R.id.lottie_view)
`

- `DotLottieAnimation.totalFrame` : Return the number of frame of the animations
- `DotLottieAnimation.repeatMode` : Return the repeat mode of the animation, 
- `DotLottieAnimation.setSpeed(Float)` : Modifier the animation speed
- `DotLottieAnimation.play()` : Play the animation
- `DotLottieAnimation.pause()` : Pause the animation
- `DotLottieAnimation.resume()` : Resume the animation instead of restarting from scratch
- `DotLottieAnimation.stor()` : Stop the animation
- `DotLottieAnimation.duration()` : Get the animation duration in millis
- `DotLottieAnimation.isPlaying()` : Check if the animatin is playing


