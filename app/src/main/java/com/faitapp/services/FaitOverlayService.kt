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
import com.faitapp.core.FileRegistry
import io.github.sceneview.SceneView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FaitOverlayService : Service() {

    companion object {
        private const val TAG = "FaitOverlayService"
        private const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "fait_overlay_channel"
        const val ACTION_STOP = "com.faitapp.ACTION_STOP_OVERLAY"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var sceneView: SceneView? = null
    private var speechBubble: TextView? = null
    private var fileRegistry: FileRegistry? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var isInitialized = false

    override fun onCreate() {
        super.onCreate()
        try {
            Log.d(TAG, "FaitOverlayService onCreate")
            fileRegistry = FileRegistry(this)
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            setupOverlay()
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onCreate: ${e.message}", e)
            removeOverlayView()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "Stop action received")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isInitialized) {
            try {
                setupOverlay()
                isInitialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Error in onStartCommand setup: ${e.message}", e)
            }
        }
        // START_NOT_STICKY — do NOT auto-resurrect after being killed
        return START_NOT_STICKY
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = LayoutInflater.from(this).inflate(R.layout.fait_overlay, null) as FrameLayout
        sceneView = overlayView?.findViewById(R.id.scene_view)
        speechBubble = overlayView?.findViewById(R.id.speech_bubble)

        val params = WindowManager.LayoutParams(
            450,
            700,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            // FLAG_NOT_FOCUSABLE keeps touches passing through
            // FLAG_HARDWARE_ACCELERATED ensures GPU rendering
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            // TRANSLUCENT pixel format is critical — without this the background is opaque black
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 100
        }

        windowManager?.addView(overlayView, params)
        Log.d(TAG, "Overlay added to WindowManager")

        loadVrmModel()
    }

    private fun loadVrmModel() {
        scope.launch {
            try {
                val vrmPath = withContext(Dispatchers.IO) { fileRegistry?.getVrmPath() }
                val vrmFilename = withContext(Dispatchers.IO) { fileRegistry?.getVrmFileName() }

                if (vrmPath == null) {
                    Log.w(TAG, "No VRM file registered — overlay will show empty until one is loaded")
                    showMessage("Fait online — no avatar loaded")
                    return@launch
                }

                val vrmFile = withContext(Dispatchers.IO) { File(vrmPath) }
                if (!vrmFile.exists()) {
                    Log.e(TAG, "VRM file missing at: $vrmPath")
                    showMessage("Avatar file missing")
                    return@launch
                }

                Log.d(TAG, "VRM file found: $vrmFilename (${vrmFile.length() / 1024} KB)")

                // SceneView 2.2.1 model loading via ModelLoader
                // We use the coroutine-based loadModelInstance API
                try {
                    val scene = sceneView ?: run {
                        Log.e(TAG, "SceneView is null")
                        return@launch
                    }

                    // SceneView 2.2.1 uses modelLoader.loadModelInstance()
                    // The file URI approach works for local files
                    val fileUri = "file://$vrmPath"
                    Log.d(TAG, "Loading model from URI: $fileUri")

                    scene.modelLoader.loadModelInstance(fileUri) { modelInstance ->
                        if (modelInstance != null) {
                            scene.addChildNode(
                                io.github.sceneview.node.ModelNode(
                                    modelInstance = modelInstance,
                                    scaleToUnits = 1.0f
                                ).apply {
                                    // Center the model in the scene
                                    position = io.github.sceneview.math.Position(x = 0f, y = -0.5f, z = -2f)
                                }
                            )
                            Log.d(TAG, "VRM model loaded and added to scene")
                            showMessage("Fait online ✓")
                        } else {
                            Log.e(TAG, "modelInstance returned null — check file format")
                            showMessage("Avatar load failed")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "SceneView model load error: ${e.message}", e)
                    showMessage("Avatar error: ${e.message?.take(40)}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "loadVrmModel error: ${e.message}", e)
            }
        }
    }

    fun showMessage(message: String) {
        scope.launch {
            try {
                speechBubble?.text = message
                speechBubble?.visibility = View.VISIBLE
                delay(4000)
                speechBubble?.visibility = View.GONE
            } catch (e: Exception) {
                Log.e(TAG, "showMessage error: ${e.message}")
            }
        }
    }

    private fun removeOverlayView() {
        try {
            overlayView?.let { view ->
                windowManager?.removeViewImmediate(view)
                Log.d(TAG, "Overlay removed from WindowManager")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay: ${e.message}")
        } finally {
            overlayView = null
            sceneView = null
            speechBubble = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Log.d(TAG, "FaitOverlayService onDestroy — cleaning up")
            removeOverlayView()
            windowManager = null
            fileRegistry = null
            scope.cancel()
            isInitialized = false
            Log.d(TAG, "Service destroyed cleanly")
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
                description = "Fait is running as a system overlay"
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
