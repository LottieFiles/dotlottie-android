package com.lottiefiles.sample

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.lottiefiles.dotlottie.core.model.Config
import com.dotlottie.dlplayer.Mode
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

        override fun onDestroy() {
            super.onDestroy()
            Log.d(TAG, "onDestroy")
            binding.tvStatus.text = "Status : Destroyed"
        }

        override fun onPause() {
            binding.tvStatus.text = "Status : Pause"
            Log.d(TAG, "onPause")
        }

        override fun onStop() {
            binding.tvStatus.text = "Status : Stop"
            binding.tvFrame.text = "Frame : %.2f / %.2f".format(
                binding.dotLottieView.currentFrame,
                binding.dotLottieView.totalFrames,
            )
            Log.d(TAG, "onStop")
        }

        override fun onFrame(frame: Float) {
            binding.tvFrame.text = "Frame : %.2f / %.2f".format(
                frame,
                binding.dotLottieView.totalFrames,
            )
        }

        override fun onLoop() {
            Log.d(TAG, "On Loop")
        }

        override fun onComplete() {
            Log.d(TAG, "On Completed")
            //binding.tvStatus.text = "Play"
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
            .speed(1.5f)
            .loop(true)
            .fileName("swinging.json") // file name of json/.lottie
//            .src("https://lottie.host/5525262b-4e57-4f0a-8103-cfdaa7c8969e/VCYIkooYX8.json")
//            .src("https://lottiefiles-mobile-templates.s3.amazonaws.com/ar-stickers/swag_sticker_piggy.lottie")
            .mode(Mode.REVERSE)
            .useFrameInterpolation(true)
            .build()

        // Sample app


        binding.dotLottieView.load(config)

        binding.btnSetSegment.setOnClickListener {
            val start = binding.edStartFrame.text.toString().toDoubleOrNull()
            val end = binding.edEndFrame.text.toString().toDoubleOrNull()
            if (start != null && end != null) {
                binding.dotLottieView.setSegments(start, end)
                binding.dotLottieView.stop()
                binding.dotLottieView.play()
            }
        }

        binding.btnPlay.setOnClickListener { v: View ->
            val isPausedOrStopped = binding.dotLottieView.isPaused || binding.dotLottieView.isStopped
            if (isPausedOrStopped) {
                binding.dotLottieView.play()
            }
        }

        binding.btnPause.setOnClickListener { v: View ->
            val isPausedOrStopped = binding.dotLottieView.isPaused || binding.dotLottieView.isStopped
            if (isPausedOrStopped) return@setOnClickListener
            binding.dotLottieView.pause()
        }

        binding.btnSetframe.setOnClickListener {
            binding.edFrame.text.toString().toFloatOrNull()?.let {
                binding.dotLottieView.setFrame(it)
                binding.edFrame.text.clear()
            }
        }

        binding.animStop.setOnClickListener {
            if (binding.dotLottieView.isStopped) return@setOnClickListener
            binding.dotLottieView.stop()
        }

        binding.dotLottieView.addEventListener(eventListener)

        binding.btnForward.setOnClickListener {
            binding.dotLottieView.setPlayMode(Mode.FORWARD)
        }
        binding.btnReverse.setOnClickListener {
            binding.dotLottieView.setPlayMode(Mode.REVERSE)
        }
        binding.btnBounce.setOnClickListener {
            binding.dotLottieView.setPlayMode(Mode.BOUNCE)
        }
        binding.btnReverseBounce.setOnClickListener {
            binding.dotLottieView.setPlayMode(Mode.REVERSE_BOUNCE)
        }
        binding.cbxLoop.addOnCheckedStateChangedListener { checkBox, state ->
            binding.dotLottieView.setLoop(checkBox.isChecked)
        }
        binding.cbxFrameInterpolation.addOnCheckedStateChangedListener { checkBox, state ->
            binding.dotLottieView.setFrameInterpolation(checkBox.isChecked)
        }
        binding.btnSetSpeed.setOnClickListener {
            binding.edSpeed.text.toString().toFloatOrNull()?.let {
                binding.dotLottieView.setSpeed(it)
            }
        }

        binding.btnFreeze.setOnClickListener {
            if (binding.btnFreeze.text.startsWith("Freeze")) {
                binding.dotLottieView.freeze()
                binding.btnFreeze.text = "Un Freeze"
            } else {
                binding.dotLottieView.unFreeze()
                binding.btnFreeze.text = "Freeze"
            }
        }

        binding.btnSetColor.setOnClickListener {
            val color = binding.edColor.text.toString()
            binding.dotLottieView.setBackgroundColor(color)
        }
    }
}
