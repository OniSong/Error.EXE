package com.faitapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.transition.Explode
import android.util.Log
import android.view.View
import android.view.Window
import android.view.animation.LinearInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.faitapp.core.FileRegistry
import kotlin.random.Random

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val PREFS_NAME = "FaitPrefs"
        private const val KEY_SETUP_COMPLETE = "isSetupComplete"
        private const val TYPEWRITER_DELAY_MS = 80L
    }

    private var terminalText: TextView? = null
    private var glitchText: TextView? = null
    private var statusText: TextView? = null
    private var scanlineOverlay: View? = null
    private var isSetupComplete = false
    private var isAnimating = false
    private var hasNavigated = false
    private val handler = Handler(Looper.getMainLooper())
    private val command = "./Project.EXE"

    // Glitch characters for digital corruption effect
    private val glitchChars = "!@#\$%^&*<>[]{}|\\/?~`ÆØÅ░▒▓█▄▀■□▪▫"

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
            }

            setContentView(R.layout.activity_splash)

            terminalText = findViewById(R.id.terminalText)
            glitchText = findViewById(R.id.glitch_text)
            statusText = findViewById(R.id.statusText)
            scanlineOverlay = findViewById(R.id.scanline_overlay)

            checkSetupStatus()
            startBootSequence()

        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onCreate: ${e.message}", e)
            navigateForward()
        }
    }

    private fun checkSetupStatus() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            isSetupComplete = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
            val fileRegistry = FileRegistry(this)
            if (!isSetupComplete && fileRegistry.hasRequiredFiles()) {
                isSetupComplete = true
                prefs.edit().putBoolean(KEY_SETUP_COMPLETE, true).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking setup: ${e.message}")
            isSetupComplete = false
        }
    }

    private fun startBootSequence() {
        isAnimating = true
        startScanlineFlicker()
        typewriterEffect()
    }

    /** Continuous scanline/CRT flicker in background */
    private fun startScanlineFlicker() {
        val flickerAnimator = ValueAnimator.ofFloat(0f, 0.06f, 0f, 0.04f, 0f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                scanlineOverlay?.alpha = animator.animatedValue as Float
            }
        }
        flickerAnimator.start()
    }

    /** Typewriter effect with random glitch bursts */
    private fun typewriterEffect() {
        var charIndex = 0

        val updateText = object : Runnable {
            override fun run() {
                try {
                    if (charIndex < command.length) {
                        val fullText = "Oni-Song × " + command.substring(0, charIndex + 1)
                        terminalText?.text = buildSpannable(fullText)

                        // Random chance to trigger glitch burst mid-type
                        if (Random.nextFloat() < 0.2f) {
                            triggerGlitchBurst(fullText)
                        }

                        charIndex++
                        handler.postDelayed(this, TYPEWRITER_DELAY_MS)
                    } else {
                        // Finished typing — do final glitch sequence then navigate
                        terminalText?.text = buildSpannable("Oni-Song × $command")
                        handler.postDelayed({
                            runFinalGlitchSequence()
                        }, 400)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Typewriter error: ${e.message}")
                    isAnimating = false
                    navigateForward()
                }
            }
        }

        handler.post(updateText)
    }

    private fun buildSpannable(text: String): SpannableString {
        val span = SpannableString(text)
        try {
            val purpleColor = ForegroundColorSpan(getColor(R.color.oni_purple))
            val greenColor = ForegroundColorSpan(getColor(R.color.oni_green))
            if (text.length >= 4) span.setSpan(purpleColor, 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (text.length >= 6) span.setSpan(greenColor, 5, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } catch (e: Exception) { /* ignore */ }
        return span
    }

    /** Quick glitch flicker — corrupted characters appear briefly on glitch layer */
    private fun triggerGlitchBurst(baseText: String) {
        val corrupted = baseText.map {
            if (Random.nextFloat() < 0.3f) glitchChars.random() else it
        }.joinToString("")

        glitchText?.text = corrupted

        val showAnim = ObjectAnimator.ofFloat(glitchText, View.ALPHA, 0f, 0.7f).apply {
            duration = 40
        }
        val hideAnim = ObjectAnimator.ofFloat(glitchText, View.ALPHA, 0.7f, 0f).apply {
            duration = 60
            startDelay = 40
        }
        AnimatorSet().apply {
            playSequentially(showAnim, hideAnim)
            start()
        }

        // Shake the main text slightly
        val shakeX = ObjectAnimator.ofFloat(terminalText, View.TRANSLATION_X,
            0f, -3f, 3f, -2f, 2f, 0f).apply {
            duration = 80
        }
        shakeX.start()
    }

    /** Big glitch sequence before navigating away */
    private fun runFinalGlitchSequence() {
        var glitchCount = 0
        val maxGlitches = 5

        val glitchRunnable = object : Runnable {
            override fun run() {
                if (glitchCount >= maxGlitches) {
                    // Final flash then navigate
                    val flashOut = ObjectAnimator.ofFloat(terminalText, View.ALPHA, 1f, 0f, 1f, 0f).apply {
                        duration = 300
                    }
                    flashOut.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            navigateForward()
                        }
                    })
                    flashOut.start()
                    return
                }

                val base = "Oni-Song × $command"
                val corrupted = base.map {
                    if (Random.nextFloat() < 0.4f) glitchChars.random() else it
                }.joinToString("")

                glitchText?.text = corrupted
                val showGlitch = ObjectAnimator.ofFloat(glitchText, View.ALPHA, 0f, 0.9f).apply { duration = 30 }
                val hideGlitch = ObjectAnimator.ofFloat(glitchText, View.ALPHA, 0.9f, 0f).apply { duration = 80; startDelay = 30 }
                AnimatorSet().apply { playSequentially(showGlitch, hideGlitch); start() }

                // Shake + color flash on main text
                val shakeX = ObjectAnimator.ofFloat(terminalText, View.TRANSLATION_X,
                    0f, -6f, 6f, -4f, 4f, -2f, 2f, 0f).apply { duration = 120 }
                shakeX.start()

                glitchCount++
                handler.postDelayed(this, 150)
            }
        }

        handler.post(glitchRunnable)
    }

    private fun navigateForward() {
        if (hasNavigated || isFinishing) return
        hasNavigated = true
        isAnimating = false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.exitTransition = Explode().apply { duration = 600 }
            }
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finishAfterTransition()
        } catch (e: Exception) {
            Log.e(TAG, "Navigation error: ${e.message}")
            finish()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
