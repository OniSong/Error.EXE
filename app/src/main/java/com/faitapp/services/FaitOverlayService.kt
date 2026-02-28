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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FaitOverlayService : Service() {

    companion object {
        private const val TAG = "FaitOverlayService"
        private const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "fait_overlay_channel"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var speechBubble: TextView? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var params: WindowManager.LayoutParams? = null
    private var isInitialized = false

    override fun onCreate() {
        super.onCreate()
        try {
            Log.d(TAG, "FaitOverlayService onCreate called")
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            setupOverlay()
            isInitialized = true
            Log.d(TAG, "FaitOverlayService initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onCreate: ${e.message}", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            Log.d(TAG, "FaitOverlayService onStartCommand called")
            if (!isInitialized) {
                setupOverlay()
                isInitialized = true
            }
            return START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand: ${e.message}", e)
            return START_NOT_STICKY
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Fait System Agent",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Fait is running as a system overlay"
                    enableLights(false)
                    enableVibration(false)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification channel: ${e.message}")
        }
    }

    private fun createNotification() = try {
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fait System Agent")
            .setContentText("Monitoring and protecting your device...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    } catch (e: Exception) {
        Log.e(TAG, "Error creating notification: ${e.message}")
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fait")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    private fun setupOverlay() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager ?: run {
                Log.e(TAG, "Failed to get WindowManager service")
                throw Exception("WindowManager service unavailable")
            }
            
            overlayView = try {
                LayoutInflater.from(this).inflate(R.layout.fait_overlay, null) as FrameLayout
            } catch (e: Exception) {
                Log.e(TAG, "Error inflating layout: ${e.message}")
                throw e
            }

            speechBubble = overlayView?.findViewById(R.id.speech_bubble)

            params = WindowManager.LayoutParams(
                450,
                700,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 0
                y = 100
            }

            windowManager?.addView(overlayView, params)
            Log.d(TAG, "Overlay view added to window manager successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in setupOverlay: ${e.message}", e)
            throw e
        }
    }

    fun showMessage(message: String) {
        scope.launch {
            try {
                speechBubble?.text = message
                speechBubble?.visibility = View.VISIBLE
                Log.d(TAG, "Showing message: $message")
                
                delay(4000)
                speechBubble?.visibility = View.GONE
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying message: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            overlayView?.let {
                try {
                    windowManager?.removeView(it)
                    Log.d(TAG, "Overlay view removed from window manager")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing overlay view: ${e.message}")
                }
            }
            
            overlayView = null
            speechBubble = null
            windowManager = null
            params = null
            
            scope.cancel()
            isInitialized = false
            Log.d(TAG, "FaitOverlayService destroyed cleanly")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}", e)
        }
    }

    override fun onBind(intent: Intent?) = null
}
