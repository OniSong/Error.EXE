package com.faitapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import com.faitapp.R
import com.faitapp.bridge.UnityBridgeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * FaitOverlayService — draggable, transparent overlay box
 *
 * Features:
 * - Drag: tap-and-drag the title bar to reposition anywhere on screen
 * - Long-press: activates drag mode with haptic feedback
 * - Transparency: tap α button to cycle through 5 opacity levels
 * - Minimize: tap — to collapse to title bar only
 * - Input field: type and send messages to Fait directly from overlay
 * - Typewriter: responses animate character by character
 * - Status dot: shows Fait's current state (idle / thinking / speaking)
 *
 * Position is saved to SharedPreferences so it persists across restarts.
 */
class FaitOverlayService : Service() {

    companion object {
        private const val TAG = "FaitOverlayService"
        private const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "fait_overlay_channel"
        const val ACTION_STOP = "com.faitapp.ACTION_STOP_OVERLAY"
        const val ACTION_SPEAK = "com.faitapp.ACTION_SPEAK"
        const val EXTRA_TEXT = "text"
        const val EXTRA_EMOTION = "emotion"

        private const val PREFS = "fait_overlay_prefs"
        private const val PREF_X = "overlay_x"
        private const val PREF_Y = "overlay_y"
        private const val PREF_OPACITY = "overlay_opacity"
        private const val PREF_MINIMIZED = "overlay_minimized"

        // Transparency levels: label → alpha (0–255 on the card background)
        private val OPACITY_LEVELS = listOf(
            "α ███" to 0.95f,   // Nearly opaque
            "α ██░" to 0.75f,   // Semi-opaque
            "α █░░" to 0.50f,   // Semi-transparent
            "α ░░░" to 0.25f,   // Very transparent
            "α ▒▒▒" to 0.10f    // Ghost mode
        )
    }

    // ── Views ──
    private var windowManager: WindowManager? = null
    private var overlayView: CardView? = null
    private var dragHandle: LinearLayout? = null
    private var speechBubble: TextView? = null
    private var inputRow: LinearLayout? = null
    private var inputField: EditText? = null
    private var btnSend: TextView? = null
    private var btnOpacity: TextView? = null
    private var btnMinimize: TextView? = null
    private var overlayContent: LinearLayout? = null
    private var statusDot: View? = null
    private var statusText: TextView? = null

    // ── State ──
    private var layoutParams: WindowManager.LayoutParams? = null
    private var opacityIndex = 1  // default: 0.75 semi-opaque
    private var isMinimized = false
    private var isDragging = false
    private var isInitialized = false

    // ── Drag tracking ──
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialParamX = 0
    private var initialParamY = 0
    private val longPressThresholdMs = 300L
    private val dragSlop = 8f  // px before we commit to a drag

    // ── Typewriter ──
    private var typewriterJob: kotlinx.coroutines.Job? = null

    // ── Coroutines ──
    private val scope = CoroutineScope(Dispatchers.Main)
    private val bridge = UnityBridgeClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    // ──────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            loadPrefs()
            setupOverlay()
            isInitialized = true
            setStatus("online", "#00FF00")
            showBubbleTypewriter("I'm here.", autoDismissMs = 3000)
        } catch (e: Exception) {
            Log.e(TAG, "Fatal onCreate: ${e.message}", e)
            removeOverlayView()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_SPEAK -> {
                val text = intent.getStringExtra(EXTRA_TEXT) ?: return START_NOT_STICKY
                val emotion = intent.getStringExtra(EXTRA_EMOTION) ?: "neutral"
                speak(text, emotion)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            savePrefs()
            removeOverlayView()
            windowManager = null
            scope.cancel()
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy error: ${e.message}", e)
        }
    }

    override fun onBind(intent: Intent?) = null

    // ──────────────────────────────────────────────────────────────
    // Overlay setup
    // ──────────────────────────────────────────────────────────────

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this)
            .inflate(R.layout.fait_overlay, null) as CardView

        // Bind views
        dragHandle      = overlayView!!.findViewById(R.id.drag_handle)
        speechBubble    = overlayView!!.findViewById(R.id.speech_bubble)
        inputRow        = overlayView!!.findViewById(R.id.input_row)
        inputField      = overlayView!!.findViewById(R.id.input_field)
        btnSend         = overlayView!!.findViewById(R.id.btn_send)
        btnOpacity      = overlayView!!.findViewById(R.id.btn_opacity)
        btnMinimize     = overlayView!!.findViewById(R.id.btn_minimize)
        overlayContent  = overlayView!!.findViewById(R.id.overlay_content)
        statusDot       = overlayView!!.findViewById(R.id.status_dot)
        statusText      = overlayView!!.findViewById(R.id.status_text)

        // WindowManager params
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        windowManager!!.addView(overlayView, layoutParams)

        applyOpacity()
        applyMinimized()
        setupDrag()
        setupButtons()
        setupInput()
    }

    // ──────────────────────────────────────────────────────────────
    // Drag — tap to drag, long-press for drag mode indicator
    // ──────────────────────────────────────────────────────────────

    private fun setupDrag() {
        var downTime = 0L
        var movedBeyondSlop = false

        dragHandle?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = System.currentTimeMillis()
                    movedBeyondSlop = false
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialParamX = layoutParams?.x ?: 0
                    initialParamY = layoutParams?.y ?: 0
                    isDragging = false

                    // Schedule long-press visual feedback
                    mainHandler.postDelayed({
                        if (!movedBeyondSlop) {
                            isDragging = true
                            dragHandle?.alpha = 0.6f
                            setStatus("drag mode", "#FFAA00")
                        }
                    }, longPressThresholdMs)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    if (!movedBeyondSlop && (Math.abs(dx) > dragSlop || Math.abs(dy) > dragSlop)) {
                        movedBeyondSlop = true
                        isDragging = true
                    }

                    if (isDragging) {
                        layoutParams?.x = (initialParamX + dx).toInt()
                        layoutParams?.y = (initialParamY + dy).toInt()
                        try { windowManager?.updateViewLayout(overlayView, layoutParams) }
                        catch (e: Exception) { Log.w(TAG, "updateViewLayout: ${e.message}") }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacksAndMessages(null)
                    dragHandle?.alpha = 1f

                    if (isDragging) {
                        savePrefs()
                        setStatus("online", "#00FF00")
                    }
                    isDragging = false
                    true
                }

                else -> false
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Buttons
    // ──────────────────────────────────────────────────────────────

    private fun setupButtons() {
        // Opacity cycle
        btnOpacity?.setOnClickListener {
            opacityIndex = (opacityIndex + 1) % OPACITY_LEVELS.size
            applyOpacity()
            savePrefs()
        }

        // Minimize toggle
        btnMinimize?.setOnClickListener {
            isMinimized = !isMinimized
            applyMinimized()
            savePrefs()
        }
    }

    private fun applyOpacity() {
        val (label, alpha) = OPACITY_LEVELS[opacityIndex]
        btnOpacity?.text = label

        // Apply alpha to the card
        overlayView?.alpha = alpha

        // Keep the drag handle slightly more visible so it's always findable
        // (handled by the card-level alpha — nothing extra needed)
    }

    private fun applyMinimized() {
        if (isMinimized) {
            overlayContent?.visibility = View.GONE
            btnMinimize?.text = "□"
        } else {
            overlayContent?.visibility = View.VISIBLE
            btnMinimize?.text = "—"
            // Show input when expanding
            inputRow?.visibility = View.VISIBLE
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Input field
    // ──────────────────────────────────────────────────────────────

    private fun setupInput() {
        inputRow?.visibility = View.VISIBLE

        btnSend?.setOnClickListener { submitInput() }

        inputField?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitInput()
                true
            } else false
        }
    }

    private fun submitInput() {
        val text = inputField?.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return

        inputField?.text?.clear()
        hideKeyboard()

        // Show user message immediately
        setStatus("thinking...", "#FFAA00")
        showBubbleTypewriter("> $text", autoDismissMs = -1)

        // TODO: Route to DualLLMOrchestrator — stub response for now
        scope.launch {
            delay(800)
            val response = "[LLM not connected yet — tap to configure]"
            speak(response, "neutral")
        }
    }

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputField?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
        } catch (e: Exception) { /* non-fatal */ }
    }

    // ──────────────────────────────────────────────────────────────
    // Public speak API
    // ──────────────────────────────────────────────────────────────

    fun speak(text: String, emotion: String = "neutral") {
        scope.launch {
            try {
                setStatus("speaking...", "#00FF00")
                bridge.sendEmotionFromTag(emotion)
                showBubbleTypewriter(text, autoDismissMs = -1)
                bridge.sendSpeak(text)

                val duration = (text.length * 55L).coerceIn(2000, 10000)
                delay(duration)

                bridge.sendIdle()
                setStatus("online", "#00FF00")
            } catch (e: Exception) {
                Log.e(TAG, "speak error: ${e.message}", e)
                setStatus("online", "#00FF00")
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Typewriter effect
    // ──────────────────────────────────────────────────────────────

    fun showBubbleTypewriter(text: String, autoDismissMs: Long = 6000) {
        typewriterJob?.cancel()
        typewriterJob = scope.launch {
            speechBubble?.visibility = View.VISIBLE
            speechBubble?.text = ""
            val sb = StringBuilder()
            for (char in text) {
                sb.append(char)
                speechBubble?.text = sb.toString()
                delay(18) // ~55 chars/sec
            }
            if (autoDismissMs > 0) {
                delay(autoDismissMs)
                speechBubble?.visibility = View.GONE
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Status indicator
    // ──────────────────────────────────────────────────────────────

    private fun setStatus(label: String, colorHex: String) {
        try {
            val color = android.graphics.Color.parseColor(colorHex)
            statusDot?.backgroundTintList =
                android.content.res.ColorStateList.valueOf(color)
            statusText?.text = label
        } catch (e: Exception) { Log.w(TAG, "setStatus: ${e.message}") }
    }

    // ──────────────────────────────────────────────────────────────
    // Persistence
    // ──────────────────────────────────────────────────────────────

    private var savedX = 40
    private var savedY = 200

    private fun loadPrefs() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        savedX       = prefs.getInt(PREF_X, 40)
        savedY       = prefs.getInt(PREF_Y, 200)
        opacityIndex = prefs.getInt(PREF_OPACITY, 1)
        isMinimized  = prefs.getBoolean(PREF_MINIMIZED, false)
    }

    private fun savePrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().apply {
            putInt(PREF_X, layoutParams?.x ?: savedX)
            putInt(PREF_Y, layoutParams?.y ?: savedY)
            putInt(PREF_OPACITY, opacityIndex)
            putBoolean(PREF_MINIMIZED, isMinimized)
            apply()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Cleanup
    // ──────────────────────────────────────────────────────────────

    private fun removeOverlayView() {
        try {
            overlayView?.let { windowManager?.removeViewImmediate(it) }
        } catch (e: Exception) {
            Log.e(TAG, "removeOverlayView: ${e.message}")
        } finally {
            overlayView = null
            speechBubble = null
            inputField = null
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Notification
    // ──────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Fait System Agent", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Fait overlay active"
                enableLights(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
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
