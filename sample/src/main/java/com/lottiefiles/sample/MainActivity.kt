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
import com.lottiefiles.sample.databinding.MainBinding

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    private val binding by lazy {
        MainBinding.inflate(layoutInflater)
    }

    private val eventListener = object : DotLottieEventListener {
        override fun onPlay() {
            binding.tvStatus.text = "Status : Play"
            Log.d(TAG, "onPlay")
        }

        override fun onPause() {
            binding.tvStatus.text = "Status : Pause"
            Log.d(TAG, "onPause")
        }

        override fun onStop() {
            binding.tvStatus.text = "Status : Stop"
            Log.d(TAG, "onStop")
        }

        override fun onFrame(frame: Double) {
            Log.d(TAG, "frame $frame")
            val f = "%.2f".format(frame)
            binding.tvFrame.text = "Frame : $f"
        }

        override fun onLoop() {
            Log.d(TAG, "On Loop")
        }

        override fun onComplete() {
            Log.d(TAG, "On Completed")
            binding.animState.text = "Play"
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
        setContentView(binding.root)

        val config = Config.Builder()
            .autoPlay(true)
            .speed(1f)
            .loop(true)
            .fileName("test.json") // file name of json/.lottie
//            .src("https://dfdfdf") // from url of json /
//            .data("{dfdf dfdk fd}") // content of json or dotlottie by array
            .mode(Mode.Forward)
            .useFrameInterpolation(true)
            .backgroundColor("#FF0000")
            .build()

        // Sample app


        binding.lottieView.load(config)

        binding.btnSetSegment.setOnClickListener {
            val start = binding.edStartFrame.text.toString().toDouble()
            val end = binding.edEndFrame.text.toString().toDouble()
            binding.lottieView.setSegments(start, end)
            binding.lottieView.stop()
            binding.lottieView.play()
        }

        binding.animState.setOnClickListener { v: View ->
            val button = v as TextView
            if (!binding.lottieView.isPaused()) {
                binding.lottieView.pause()
                button.text = "Resume"
            } else {
                binding.lottieView.play()
                button.text = "Pause"
            }
        }

        binding.btnSetframe.setOnClickListener {
            val frame = binding.edFrame.text.toString().toDouble()
            binding.lottieView.setFrame(frame)
            binding.edFrame.text.clear()
        }

        binding.animStop.setOnClickListener {
            binding.lottieView.stop()
            binding.animState.text = "Play"
        }

        binding.lottieView.addEventListener(eventListener)

        binding.btnForward.setOnClickListener {
            binding.lottieView.setRepeatMode(Mode.Forward)
        }
        binding.btnReverse.setOnClickListener {
            binding.lottieView.setRepeatMode(Mode.Reverse)
        }
        binding.btnBounce.setOnClickListener {
            binding.lottieView.setRepeatMode(Mode.Forward)
        }
        binding.btnReverseBounce.setOnClickListener {
            binding.lottieView.setRepeatMode(Mode.Reverse)
        }
        binding.btnLoop.setOnClickListener {
            val text = if (binding.lottieView.loop) {
                "Loop off"
            } else {
                "Loop on"
            }
            binding.lottieView.setLoop(!binding.lottieView.loop)
            binding.btnLoop.text = text
        }
        binding.cbxFrameInterpolation.addOnCheckedStateChangedListener { checkBox, state ->
            binding.lottieView.setFrameInterpolation(checkBox.isChecked)
        }
        binding.btnSetSpeed.setOnClickListener {
            val speed = binding.edSpeed.text.toString().toFloat()
            binding.lottieView.setSpeed(speed)
        }
    }
}