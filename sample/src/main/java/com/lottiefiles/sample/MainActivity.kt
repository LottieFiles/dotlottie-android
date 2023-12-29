package com.lottiefiles.sample

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.lottiefiles.dotlottie.core.model.Config
import com.lottiefiles.dotlottie.core.model.Mode
import com.lottiefiles.dotlottie.core.widget.DotLottieAnimation
import com.lottiefiles.dotlottie.core.widget.DotLottieEventListener

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    private val lottieView by lazy { findViewById<DotLottieAnimation>(R.id.lottie_view) }
    private val edFrame by lazy { findViewById<EditText>(R.id.edFrame) }
    private val btnFrame by lazy { findViewById<Button>(R.id.btn_setframe) }
    private val tvStatus by lazy { findViewById<TextView>(R.id.tvStatus) }

    private val eventListener = object : DotLottieEventListener {
        override fun onPlay() {
            tvStatus.text = "Status : Play"
            Log.d(TAG, "onPlay")
        }

        override fun onPause() {
            tvStatus.text = "Status : Pause"
            Log.d(TAG, "onPause")
        }

        override fun onStop() {
            tvStatus.text = "Status : Stop"
            Log.d(TAG, "onStop")
        }

        override fun onFrame(frame: Double) {
            Log.d(TAG, "frame $frame")
        }

        override fun onLoop() {
            Log.d(TAG, "On Loop")
        }

        override fun onComplete() {
            Log.d(TAG, "On Completed")
        }

        override fun onLoad() {
            Log.d(TAG, "onLoad")
        }

        override fun onLoadError(error: Throwable) {
            Log.d(TAG, "onLoadError ${error.localizedMessage}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        val config = Config.Builder()
            .autoPlay(true)
            .speed(2f)
            .loop(true)
            .src("contact.json") // file name of json/.lottie
//            .src("https://dfdfdf") // from url of json /
//            .data("{dfdf dfdk fd}") // content of json or dotlottie by array
            .mode(Mode.Forward)
            .useFrameInterpolation(false)
            //.backgroundColor("#000000")
            .build()

        // Sample app


        lottieView.load(config)
//        lottieView.resize(700, 700)
//        lottieView.setSegments(0.0, 30.0)

        findViewById<View>(R.id.anim_state).setOnClickListener { v: View ->
            val button = v as TextView
            if ("Pause".contentEquals(button.text)) {
                lottieView.pause()
                button.text = "Resume"
            } else {
                lottieView.play()
                button.text = "Pause"
            }
        }

        btnFrame.setOnClickListener {
            val frame = edFrame.text.toString().toDouble()
            lottieView.setFrame(frame)
            edFrame.setText("")
        }

        lottieView.addEventListener(eventListener)
    }
}