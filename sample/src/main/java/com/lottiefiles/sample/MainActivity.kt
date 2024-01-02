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
            binding.tvFrame.text = "Frame : %.2f".format(frame)
        }

        override fun onLoop() {
            Log.d(TAG, "On Loop")
        }

        override fun onComplete() {
            Log.d(TAG, "On Completed")
            binding.animState.text = "Play"
        }

        override fun onFreeze() {
            super.onFreeze()
            Log.d(TAG, "On Freeze")
        }

        override fun onUnFreeze() {
            super.onUnFreeze()
            Log.d(TAG, "onUnFreeze")
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
//            .src("https://lottie.host/5525262b-4e57-4f0a-8103-cfdaa7c8969e/VCYIkooYX8.json") // from url of json
//            .src("https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie") // from url of json
            .mode(Mode.Forward)
            .useFrameInterpolation(true)
            .build()

        // Sample app


        binding.lottieView.load(config)

        binding.btnSetSegment.setOnClickListener {
            val start = binding.edStartFrame.text.toString().toDoubleOrNull()
            val end = binding.edEndFrame.text.toString().toDoubleOrNull()
            if (start != null && end != null) {
                binding.lottieView.setSegments(start, end)
                binding.lottieView.stop()
                binding.lottieView.play()
            }
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
            binding.edFrame.text.toString().toDoubleOrNull()?.let {
                binding.lottieView.setFrame(it)
                binding.edFrame.text.clear()
            }
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
            binding.edSpeed.text.toString().toFloatOrNull()?.let {
                binding.lottieView.setSpeed(it)
            }
        }

        binding.btnFreeze.setOnClickListener {
            if (binding.btnFreeze.text.startsWith("Freeze")) {
                binding.lottieView.freeze()
                binding.btnFreeze.text = "Un Freeze"
            } else {
                binding.lottieView.unFreeze()
                binding.btnFreeze.text = "Freeze"
            }
        }

        binding.btnSetColor.setOnClickListener {
            val color = binding.edColor.text.toString()
            binding.lottieView.setBackgroundColor(color)
        }
    }
}