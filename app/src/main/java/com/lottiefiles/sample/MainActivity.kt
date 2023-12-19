package com.lottiefiles.sample

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.lottiefiles.dotlottie.core.widget.DotLottieAnimation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        val lottieView = findViewById<DotLottieAnimation>(R.id.lottie_view)
        findViewById<View>(R.id.anim_state).setOnClickListener { v: View ->
            val button = v as TextView
            if ("Pause".contentEquals(button.text)) {
                lottieView.pause()
                button.text = "Resume"
            } else {
                lottieView.resume()
                button.text = "Pause"
            }
        }
    }
}