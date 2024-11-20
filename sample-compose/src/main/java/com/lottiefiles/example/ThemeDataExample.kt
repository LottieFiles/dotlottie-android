package com.lottiefiles.example

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.lottiefiles.dotlottie.core.compose.runtime.DotLottieController
import com.lottiefiles.dotlottie.core.compose.ui.DotLottieAnimation
import com.lottiefiles.dotlottie.core.util.DotLottieSource

@Composable
fun ThemeDataExample() {
    val context = LocalContext.current
    val imageResources = listOf(
        R.drawable.blue,
        R.drawable.yellow,
        R.drawable.green,
    )
    val defaultLightColors = MaterialTheme.colorScheme
    val colorScheme = remember { mutableStateOf(defaultLightColors) }
    val selectedImageRes = remember { mutableStateOf<Int?>(null) }
    val controller = remember { DotLottieController() }

    LaunchedEffect(colorScheme.value) {
        val tertiaryRed = colorScheme.value.tertiary.red
        val tertiaryGreen = colorScheme.value.tertiary.green
        val tertiaryBlue = colorScheme.value.tertiary.blue

        val backgroundRed = colorScheme.value.background.red
        val backgroundGreen = colorScheme.value.background.green
        val backgroundBlue = colorScheme.value.background.blue

        // Create theme data based on extracted colors
        val theme = """
        {
            "rules": [
                {
                    "id": "check_mark_bg",
                    "type": "Color",
                    "value": [$backgroundRed, $backgroundGreen, $backgroundBlue, 1]
                },
                {
                    "id": "outline",
                    "type": "Color",
                    "keyframes": [
                        {
                            "frame": 48,
                            "value": [$tertiaryRed, $tertiaryGreen, $tertiaryBlue, 1],
                            "outTangent": { "x": 0.65, "y": 0 },
                            "inTangent": { "x": 0.36, "y": 1 }
                        }, 
                        {
                            "frame": 61,
                            "value": [$backgroundRed, $backgroundGreen, $backgroundBlue, 1]
                        }
                    ]
                },
                {
                    "id": "bubble",
                    "type": "Color",
                    "value": [$tertiaryRed, $tertiaryGreen, $tertiaryBlue, 1]
                }
            ]
        }
        """.trimIndent()

        controller.setThemeData(theme)
    }

    // Apply dynamic color scheme based on extracted colors
    MaterialTheme(colorScheme = colorScheme.value) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Image options for the user to select
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                imageResources.forEach { imageRes ->
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = "Selectable Image",
                        modifier = Modifier
                            .size(100.dp)
                            .clickable {
                                selectedImageRes.value = imageRes
                                extractColorsFromImage(context, imageRes, colorScheme)
                            }
                    )
                }
            }

            DotLottieAnimation(
                source = DotLottieSource.Asset("theming_example_v2.lottie"),
                autoplay = true,
                loop = true,
                controller = controller,
                modifier = Modifier
                    .size(200.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

fun extractColorsFromImage(
    context: Context,
    imageRes: Int,
    colorScheme: MutableState<ColorScheme>
) {
    val inputStream = context.resources.openRawResource(imageRes)
    val bitmap = BitmapFactory.decodeStream(inputStream)
    inputStream.close()

    // Extract colors using Palette
    bitmap?.let {
        Palette.from(it).generate { palette ->
            val backgroundColor = palette?.dominantSwatch?.rgb ?: android.graphics.Color.TRANSPARENT
            val primaryColor =
                palette?.lightVibrantSwatch?.rgb ?: android.graphics.Color.TRANSPARENT
            val tertiary = palette?.vibrantSwatch?.rgb ?: android.graphics.Color.TRANSPARENT

            // Modify only the primary and secondary colors by copying the default color scheme
            colorScheme.value.outline
            colorScheme.value = colorScheme.value.copy(
                primary = Color(primaryColor),
                onPrimary = if (isColorDark(primaryColor)) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black,
                background = Color(backgroundColor),
                onBackground = if (isColorDark(backgroundColor)) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black,
                tertiary = Color(tertiary),
                onTertiary = if (isColorDark(tertiary)) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black,
            )
        }
    }
}

// Helper function to check if a color is dark or light
fun isColorDark(color: Int): Boolean {
    val red = android.graphics.Color.red(color)
    val green = android.graphics.Color.green(color)
    val blue = android.graphics.Color.blue(color)
    val darkness = 1 - (0.299 * red + 0.587 * green + 0.114 * blue) / 255
    return darkness >= 0.5
}
