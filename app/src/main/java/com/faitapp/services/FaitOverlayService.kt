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
import io.github.sceneview.node.ModelNode
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
    private var modelNode: ModelNode? = null

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
                Log.e(TAG, "Setup error: ${e.message}", e)
            }
        }
        return START_NOT_STICKY
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = LayoutInflater.from(this).inflate(R.layout.fait_overlay, null) as FrameLayout
        sceneView = overlayView?.findViewById(R.id.scene_view)
        speechBubble = overlayView?.findViewById(R.id.speech_bubble)

        val params = WindowManager.LayoutParams(
            450, 700,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
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
                val vrmFilename = fileRegistry?.getVrmFileName() ?: "avatar"

                if (vrmPath == null) {
                    Log.w(TAG, "No VRM registered — waiting for file to be added")
                    showMessage("Fait online — tap to load avatar")
                    return@launch
                }

                val vrmFile = File(vrmPath)
                if (!vrmFile.exists()) {
                    Log.e(TAG, "VRM file missing at: $vrmPath")
                    showMessage("Avatar file missing")
                    return@launch
                }

                Log.d(TAG, "Loading VRM: $vrmFilename (${vrmFile.length() / 1024} KB)")

                val scene = sceneView ?: run {
                    Log.e(TAG, "SceneView is null — cannot load model")
                    return@launch
                }

                // SceneView 2.2.1: modelLoader.loadModel(file) is a suspend function
                // It loads the GLB/GLTF and returns a Model, then we get the instance from it
                val model = withContext(Dispatchers.IO) {
                    try {
                        scene.modelLoader.loadModel(vrmFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "modelLoader.loadModel error: ${e.message}", e)
                        null
                    }
                }

                if (model == null) {
                    Log.e(TAG, "Model load returned null — file may be corrupt or unsupported")
                    showMessage("Avatar load failed — check file format")
                    return@launch
                }

                // Create ModelNode from the loaded model's instance
                val instance = model.instance
                if (instance == null) {
                    Log.e(TAG, "model.instance is null")
                    showMessage("Avatar instance error")
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    try {
                        val node = ModelNode(
                            modelInstance = instance,
                            autoAnimate = true,       // plays built-in animations automatically
                            scaleToUnits = 1.0f,      // normalises model scale to 1m
                            centerOrigin = io.github.sceneview.math.Position(x = 0f, y = -1f, z = 0f)
                        ).apply {
                            position = io.github.sceneview.math.Position(x = 0f, y = 0f, z = -2f)
                        }

                        scene.addChildNode(node)
                        modelNode = node
                        Log.d(TAG, "VRM model added to scene successfully")
                        showMessage("Fait online ✓")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error adding ModelNode to scene: ${e.message}", e)
                        showMessage("Scene error: ${e.message?.take(40)}")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "loadVrmModel error: ${e.message}", e)
                showMessage("Load error: ${e.message?.take(40)}")
            }
        }
    }

    /** Reload avatar after a new file is picked — called from MainActivity */
    fun reloadAvatar() {
        modelNode?.let { node ->
            sceneView?.removeChildNode(node)
            modelNode = null
        }
        loadVrmModel()
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
            overlayView?.let { windowManager?.removeViewImmediate(it) }
            Log.d(TAG, "Overlay removed")
        } catch (e: Exception) {
            Log.e(TAG, "removeOverlayView error: ${e.message}")
        } finally {
            overlayView = null
            sceneView = null
            speechBubble = null
            modelNode = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Log.d(TAG, "FaitOverlayService onDestroy")
            removeOverlayView()
            windowManager = null
            fileRegistry = null
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
