package com.faitapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.faitapp.R
import com.faitapp.bridge.UnityBridgeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * FaitOverlayService — Phase B prototype
 *
 * Renders a lightweight Android overlay (speech bubble + status text).
 * All avatar rendering is delegated to the Unity companion app via UnityBridgeClient.
 *
 * The Unity app handles: VRM rendering, expressions, spring bones, lip sync.
 * This service handles: LLM pipeline, system integration, overlay UI, bridge commands.
 *
 * Upgrade path → Phase A (Unity as a Library): embed Unity .aar here,
 * remove UnityBridgeClient, drive avatar directly.
 */
class FaitOverlayService : Service() {

    companion object {
        private const val TAG = "FaitOverlayService"
        private const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "fait_overlay_channel"
        const val ACTION_STOP = "com.faitapp.ACTION_STOP_OVERLAY"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var speechBubble: TextView? = null
    private var statusIndicator: TextView? = null

    private val scope = CoroutineScope(Dispatchers.Main)
    private val bridge = UnityBridgeClient()
    private var isInitialized = false

    override fun onCreate() {
        super.onCreate()
        try {
            Log.d(TAG, "FaitOverlayService onCreate")
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            setupOverlay()
            isInitialized = true
            pingUnity()
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onCreate: ${e.message}", e)
            removeOverlayView()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Stop action received")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (!isInitialized) {
            try {
                setupOverlay()
                isInitialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Setup error: ${e.message}", e)
            }
        }
        return START_NOT_STICKY
    }

    // ──────────────────────────────────────────────
    // Overlay UI — speech bubble only (avatar is in Unity)
    // ──────────────────────────────────────────────

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.fait_overlay, null) as FrameLayout

        // We still inflate fait_overlay.xml but we hide the SceneView — only use speech bubble
        // The SceneView id may still exist in the layout; just make it GONE here
        overlayView?.findViewById<View>(R.id.scene_view)?.visibility = View.GONE

        speechBubble = overlayView?.findViewById(R.id.speech_bubble)
        // statusIndicator is optional — add to layout if you want a dot indicator
        // statusIndicator = overlayView?.findViewById(R.id.status_indicator)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 16
            y = 120
        }

        windowManager?.addView(overlayView, params)
        Log.d(TAG, "Overlay speech bubble added")
    }

    // ──────────────────────────────────────────────
    // Public API — called by your LLM pipeline / ChatProviderManager
    // ──────────────────────────────────────────────

    /**
     * Fait says something. Shows in speech bubble + drives Unity lip sync + expression.
     * Call this whenever your dual-hemisphere pipeline produces output.
     */
    fun speak(text: String, emotion: String = "neutral") {
        scope.launch {
            try {
                // 1. Set expression based on detected emotion
                bridge.sendEmotionFromTag(emotion)
                delay(100)

                // 2. Show speech bubble
                showBubble(text)

                // 3. Trigger lip sync for the text duration
                bridge.sendSpeak(text)

                // 4. After speaking, return to idle
                val speakDurationMs = (text.length * 60L).coerceIn(1500, 8000)
                delay(speakDurationMs)
                bridge.sendIdle()

            } catch (e: Exception) {
                Log.e(TAG, "speak error: ${e.message}", e)
            }
        }
    }

    fun showBubble(text: String, autoDismissMs: Long = 6000) {
        scope.launch {
            try {
                speechBubble?.text = text
                speechBubble?.visibility = View.VISIBLE
                delay(autoDismissMs)
                speechBubble?.visibility = View.GONE
            } catch (e: Exception) {
                Log.e(TAG, "showBubble error: ${e.message}")
            }
        }
    }

    fun setEmotion(emotion: String) {
        scope.launch {
            try { bridge.sendEmotionFromTag(emotion) }
            catch (e: Exception) { Log.e(TAG, "setEmotion error: ${e.message}") }
        }
    }

    // ──────────────────────────────────────────────
    // Internal
    // ──────────────────────────────────────────────

    private fun pingUnity() {
        scope.launch {
            delay(2000) // give Unity time to launch
            val alive = bridge.ping()
            val msg = if (alive) "Fait online ✓" else "Avatar offline — launch Fait Unity"
            showBubble(msg, 4000)
            Log.d(TAG, "Unity bridge ping: $alive")
        }
    }

    private fun removeOverlayView() {
        try {
            overlayView?.let { windowManager?.removeViewImmediate(it) }
        } catch (e: Exception) {
            Log.e(TAG, "removeOverlayView error: ${e.message}")
        } finally {
            overlayView = null
            speechBubble = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Log.d(TAG, "onDestroy — cleaning up")
            removeOverlayView()
            windowManager = null
            scope.cancel()
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy error: ${e.message}", e)
        }
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Fait System Agent", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Fait overlay active"
                enableLights(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fait")
            .setContentText("I'm here.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
