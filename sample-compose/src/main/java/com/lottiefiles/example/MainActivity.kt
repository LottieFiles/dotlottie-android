package com.lottiefiles.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dotlottie.dlplayer.Mode
import com.lottiefiles.dotlottie.core.model.Config
import com.lottiefiles.dotlottie.core.ui.components.DotLottieAnimation
import com.lottiefiles.example.ui.theme.ExampleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    val config = Config.Builder()
                        .autoplay(true)
                        .speed(1f)
                        .loop(true)
//                        .fileName("swinging.json") // file name of json/.lottie
            .src("https://lottie.host/5525262b-4e57-4f0a-8103-cfdaa7c8969e/VCYIkooYX8.json")
//            .src("https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie")
                        .playMode(Mode.FORWARD)
                        .useFrameInterpolation(true)
                        .build()

                    DotLottieAnimation(
                        modifier = Modifier
                            .width(200.dp)
                            .height(200.dp)
                            .padding(16.dp),
                        config = config)
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ExampleTheme {
        Greeting("Android")
    }
}