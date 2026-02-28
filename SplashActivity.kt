package com.faitapp

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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.faitapp.core.FileRegistry
import com.faitapp.services.FaitOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val PREFS_NAME = "FaitPrefs"
        private const val KEY_SETUP_COMPLETE = "isSetupComplete"
        private const val TYPEWRITER_DELAY_MS = 80L
    }

    private var terminalText: TextView? = null
    private var statusText: TextView? = null
    private var isSetupComplete = false
    private var isAnimating = false
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)
    private val command = "./Project.EXE"

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            
            Log.d(TAG, "SplashActivity onCreate called")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
            }
            
            setContentView(R.layout.activity_splash)
            
            terminalText = findViewById(R.id.terminalText)
            statusText = findViewById(R.id.statusText)
            
            if (terminalText == null) {
                Log.e(TAG, "terminalText view not found in layout")
                finish()
                return
            }
            
            checkSetupStatus()
            startBootSequence()
            
            Log.d(TAG, "SplashActivity initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onCreate: ${e.message}", e)
            finish()
        }
    }

    private fun checkSetupStatus() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            isSetupComplete = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
            
            val fileRegistry = FileRegistry(this)
            val hasRequiredFiles = fileRegistry.hasRequiredFiles()
            
            Log.d(TAG, "Setup Complete: $isSetupComplete, Files Present: $hasRequiredFiles")
            
            if (!isSetupComplete && hasRequiredFiles) {
                isSetupComplete = true
                prefs.edit().putBoolean(KEY_SETUP_COMPLETE, true).apply()
                Log.d(TAG, "Setup marked as complete based on file presence")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking setup status: ${e.message}", e)
            isSetupComplete = false
        }
    }

    private fun startBootSequence() {
        try {
            terminalText?.text = ""
            typewriterEffect()
            
            terminalText?.setOnClickListener {
                if (!isAnimating) {
                    handleSplashTap()
                }
            }
            
            Log.d(TAG, "Boot sequence started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting boot sequence: ${e.message}", e)
        }
    }

    private fun typewriterEffect() {
        try {
            isAnimating = true
            var charIndex = 0
            
            val updateText = object : Runnable {
                override fun run() {
                    try {
                        if (charIndex < command.length) {
                            val fullText = "Oni-Song × " + command.substring(0, charIndex + 1)
                            val span = SpannableString(fullText)
                            
                            val purpleColor = ForegroundColorSpan(getColor(R.color.oni_purple))
                            val greenColor = ForegroundColorSpan(getColor(R.color.oni_green))
                            
                            span.setSpan(purpleColor, 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            span.setSpan(greenColor, 5, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            
                            terminalText?.text = span
                            charIndex++
                            handler.postDelayed(this, TYPEWRITER_DELAY_MS)
                        } else {
                            isAnimating = false
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in typewriter effect: ${e.message}")
                        isAnimating = false
                    }
                }
            }
            
            handler.post(updateText)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up typewriter effect: ${e.message}", e)
        }
    }

    private fun handleSplashTap() {
        try {
            Log.d(TAG, "Splash tapped. Setup complete: $isSetupComplete")
            
            if (isSetupComplete) {
                triggerShatter()
            } else {
                showSetupGate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling splash tap: ${e.message}", e)
        }
    }

    private fun triggerShatter() {
        try {
            Log.d(TAG, "Triggering shatter animation")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val explodeTransition = Explode().apply {
                    duration = 800
                }
                window.exitTransition = explodeTransition
            }
            
            val intent = Intent(this, FaitOverlayService::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Log.d(TAG, "FaitOverlayService started")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting FaitOverlayService: ${e.message}")
            }
            
            finishAfterTransition()
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering shatter: ${e.message}", e)
            finish()
        }
    }

    private fun showSetupGate() {
        try {
            Log.d(TAG, "Showing setup gate")
            statusText?.text = "System Initialization Required"
            statusText?.visibility = View.VISIBLE
            Log.d(TAG, "Setup dialog would show here")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing setup gate: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        try {
            handler.removeCallbacksAndMessages(null)
            scope.cancel()
            super.onDestroy()
            Log.d(TAG, "SplashActivity destroyed cleanly")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}", e)
        }
    }
}
