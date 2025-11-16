package com.lottiefiles.sample

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.dotlottie.dlplayer.Mode
import com.lottiefiles.dotlottie.core.util.DotLottieEventListener
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.StateMachineEventListener

/**
 * Programmatic Example Activity
 *
 * This activity demonstrates how to use the DotLottieView wrapper component
 * programmatically, similar to the Flutter DotLottiePlatformView example.
 *
 * Key Features Demonstrated:
 * - Programmatic view creation and configuration
 * - Event listener setup
 * - State machine interaction
 * - Dynamic control methods
 * - Proper lifecycle management
 */
class ProgrammaticExampleActivity : ComponentActivity() {

    private val TAG = "ProgrammaticExample"

    private lateinit var dotLottieView: DotLottieView
    private lateinit var statusText: TextView
    private lateinit var frameText: TextView
    private lateinit var stateText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "========================================")
        Log.d(TAG, "🚀 DotLottieView Programmatic Example")
        Log.d(TAG, "========================================")

        // Create the entire UI programmatically
        val rootLayout = createUI()
        setContentView(rootLayout)

        // Configure the DotLottieView
        configureDotLottieView()

        Log.d(TAG, "Activity created successfully")
    }

    /**
     * Create the entire UI programmatically
     */
    private fun createUI(): ScrollView {
        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val mainLayout = LinearLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        mainLayout.addView(TextView(this).apply {
            text = "Programmatic DotLottieView Example"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        // DotLottieView container
        dotLottieView = DotLottieView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                400,
                400
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 32)
            }
            setBackgroundColor(Color.parseColor("#F0F0F0"))
        }
        mainLayout.addView(dotLottieView)

        // Status text
        statusText = TextView(this).apply {
            text = "Status: Initializing..."
            textSize = 16f
            setPadding(0, 8, 0, 8)
        }
        mainLayout.addView(statusText)

        // Frame text
        frameText = TextView(this).apply {
            text = "Frame: 0 / 0"
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }
        mainLayout.addView(frameText)

        // State machine state text
        stateText = TextView(this).apply {
            text = "State Machine: Not Active"
            textSize = 14f
            setPadding(0, 8, 0, 24)
        }
        mainLayout.addView(stateText)

        // Control buttons
        val controlLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
        }

        controlLayout.addView(createButton("Play") { dotLottieView.play() })
        controlLayout.addView(createButton("Pause") { dotLottieView.pause() })
        controlLayout.addView(createButton("Stop") { dotLottieView.stop() })

        mainLayout.addView(controlLayout)

        // Mode buttons
        val modeLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        modeLayout.addView(createButton("Forward") {
            dotLottieView.setPlayMode(Mode.FORWARD)
        })
        modeLayout.addView(createButton("Reverse") {
            dotLottieView.setPlayMode(Mode.REVERSE)
        })
        modeLayout.addView(createButton("Bounce") {
            dotLottieView.setPlayMode(Mode.BOUNCE)
        })

        mainLayout.addView(modeLayout)

        // Speed buttons
        val speedLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        speedLayout.addView(createButton("0.5x") { dotLottieView.setSpeed(0.5f) })
        speedLayout.addView(createButton("1x") { dotLottieView.setSpeed(1f) })
        speedLayout.addView(createButton("2x") { dotLottieView.setSpeed(2f) })

        mainLayout.addView(speedLayout)

        // State machine interaction buttons
        val smLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 0)
        }

        smLayout.addView(TextView(this).apply {
            text = "State Machine Controls:"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        })

        val smButtonLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
        }

        smButtonLayout.addView(createButton("SM Start") {
            dotLottieView.stateMachineLoad("starRating")
            dotLottieView.stateMachineStart()
        })
        smButtonLayout.addView(createButton("SM Stop") {
            dotLottieView.stateMachineStop()
        })
        smButtonLayout.addView(createButton("Get State") {
            val state = dotLottieView.stateMachineCurrentState()
            Log.d(TAG, "Current State: $state")
            stateText.text = "State Machine: $state"
        })

        smLayout.addView(smButtonLayout)
        mainLayout.addView(smLayout)

        // Info section
        val infoLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 0)
        }

        infoLayout.addView(TextView(this).apply {
            text = "💡 This example demonstrates:"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        })

        infoLayout.addView(TextView(this).apply {
            text = "• Programmatic view creation\n" +
                    "• Configuration via code\n" +
                    "• Event listener setup\n" +
                    "• State machine interaction\n" +
                    "• Touch interaction (tap the animation!)\n" +
                    "• Dynamic property changes"
            textSize = 12f
            setPadding(16, 0, 0, 16)
        })

        mainLayout.addView(infoLayout)

        scrollView.addView(mainLayout)
        return scrollView
    }

    /**
     * Helper method to create buttons
     */
    private fun createButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(4, 4, 4, 4)
            }
            setOnClickListener {
                Log.d(TAG, "Button clicked: $text")
                onClick()
            }
        }
    }

    /**
     * Configure the DotLottieView with animation and event listeners
     */
    private fun configureDotLottieView() {
        // Setup event listeners BEFORE configuration
        dotLottieView.addEventListener(object : DotLottieEventListener {
            override fun onLoad() {
                Log.d(TAG, "✅ Animation loaded")
                statusText.text = "Status: Loaded"

                // Log animation info
                Log.d(TAG, "   Duration: ${dotLottieView.duration}s")
                Log.d(TAG, "   Total Frames: ${dotLottieView.totalFrames}")
                Log.d(TAG, "   Loop: ${dotLottieView.loop}")
                Log.d(TAG, "   Speed: ${dotLottieView.speed}")

                // Log manifest if available
                try {
                    val manifest = dotLottieView.getManifest()
                    manifest?.let {
                        Log.d(TAG, "   Manifest:")
                        Log.d(TAG, "      Version: ${it.version}")
                        it.stateMachines?.let { sms ->
                            Log.d(TAG, "      State Machines: ${sms.size}")
                            sms.forEach { sm ->
                                Log.d(TAG, "         - ${sm.name} (id: ${sm.id})")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "   No manifest available")
                }
            }

            override fun onPlay() {
                Log.d(TAG, "▶️ Animation playing")
                statusText.text = "Status: Playing"
            }

            override fun onPause() {
                Log.d(TAG, "⏸️ Animation paused")
                statusText.text = "Status: Paused"
            }

            override fun onStop() {
                Log.d(TAG, "⏹️ Animation stopped")
                statusText.text = "Status: Stopped"
            }

            override fun onComplete() {
                Log.d(TAG, "✅ Animation completed")
                statusText.text = "Status: Completed"
            }

            override fun onFrame(frame: Float) {
                frameText.text = "Frame: %.1f / %.1f (%.1f%%)".format(
                    frame,
                    dotLottieView.totalFrames,
                    dotLottieView.currentProgress * 100
                )
            }

            override fun onLoop(loopCount: Int) {
                Log.d(TAG, "🔄 Loop: $loopCount")
            }

            override fun onLoadError(error: Throwable) {
                Log.e(TAG, "❌ Load error: ${error.message}", error)
                statusText.text = "Status: Error - ${error.message}"
            }
        })

        // Setup state machine event listener
        dotLottieView.addStateMachineEventListener(object : StateMachineEventListener {
            override fun onStart() {
                Log.d(TAG, "🎰 State Machine: Started")
                stateText.text = "State Machine: Active"
            }

            override fun onStop() {
                Log.d(TAG, "🎰 State Machine: Stopped")
                stateText.text = "State Machine: Stopped"
            }

            override fun onStateEntered(enteringState: String) {
                Log.d(TAG, "🎰 State Machine: Entered '$enteringState'")
                stateText.text = "State Machine: $enteringState"
            }

            override fun onStateExit(leavingState: String) {
                Log.d(TAG, "🎰 State Machine: Exited '$leavingState'")
            }

            override fun onTransition(previousState: String, newState: String) {
                Log.d(TAG, "🎰 State Machine: '$previousState' → '$newState'")
            }

            override fun onNumericInputValueChange(
                inputName: String,
                oldValue: Float,
                newValue: Float
            ) {
                Log.d(TAG, "🎰 Numeric Input '$inputName': $oldValue → $newValue")
            }

            override fun onStringInputValueChange(
                inputName: String,
                oldValue: String,
                newValue: String
            ) {
                Log.d(TAG, "🎰 String Input '$inputName': '$oldValue' → '$newValue'")
            }

            override fun onBooleanInputValueChange(
                inputName: String,
                oldValue: Boolean,
                newValue: Boolean
            ) {
                Log.d(TAG, "🎰 Boolean Input '$inputName': $oldValue → $newValue")
            }

            override fun onCustomEvent(message: String) {
                Log.d(TAG, "🎰 Custom Event: $message")
            }

            override fun onError(message: String) {
                Log.e(TAG, "🎰 Error: $message")
            }

            override fun onInputFired(inputName: String) {
                Log.d(TAG, "🎰 Input Fired: '$inputName'")
            }
        })

        // Configure the animation
        // Try different sources by uncommenting:
        dotLottieView.configure(
            // URL source with state machine
            source = DotLottieSource.Url("https://asset-cdn.lottiefiles.dev/21673d9d-1a63-4ee6-a07e-f1756cd26e36/or79UQUji5.lottie"),
            autoplay = true,
            loop = true,
            speed = 1f,
            mode = Mode.FORWARD,
            useFrameInterpolation = true,
//            stateMachineId = "starRating",  // Enable state machine
            threads = 6u
        )

        // Alternative configurations you can try:
        /*
        // Simple JSON animation
        dotLottieView.configure(
            source = DotLottieSource.Url("https://lottie.host/f8e7eccf-72da-40da-9dd1-0fdbdc93b9ea/yAX2Nay9jD.json"),
            autoplay = true,
            loop = true,
            speed = 1f
        )

        // Asset-based animation
        dotLottieView.configure(
            source = DotLottieSource.Asset("swinging.json"),
            autoplay = true,
            loop = true,
            backgroundColor = "#FF0000"
        )
        */

        Log.d(TAG, "👆 Touch the animation to interact with the state machine!")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity destroyed - cleaning up DotLottieView")
        dotLottieView.destroy()
    }
}
