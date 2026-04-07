package com.faitapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.faitapp.R
import com.faitapp.bridge.UnityBridgeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FaitOverlayService : Service() {

    companion object {
        private const val TAG = "FaitOverlayService"
        private const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "fait_overlay_channel"
        const val ACTION_STOP = "com.faitapp.ACTION_STOP_OVERLAY"

        private const val PREFS = "fait_overlay_prefs"
        private const val PREF_X = "overlay_x"
        private const val PREF_Y = "overlay_y"
        private const val PREF_OPACITY = "overlay_opacity"
        private const val PREF_MINIMIZED = "overlay_minimized"
        private const val PREF_BG = "overlay_bg"

        // HuggingFace
        private const val HF_BASE = "https://router.huggingface.co/v1/chat/completions"
        private const val HF_MODEL = "Qwen/Qwen2.5-7B-Instruct"
        // Set your key here or inject via BuildConfig
        private const val HF_API_KEY = "BUILD_CONFIG_HF_KEY"

        private val OPACITY_LEVELS = listOf(
            "α" to 0.95f,
            "α·" to 0.75f,
            "α··" to 0.50f,
            "α···" to 0.28f,
            "α∅" to 0.12f
        )

        private val BG_DRAWABLES = listOf(
            R.drawable.chat_bg_void,
            R.drawable.chat_bg_sakura,
            R.drawable.chat_bg_glitch,
            R.drawable.chat_bg_crimson
        )

        private val BG_NAMES = listOf("void", "桜夜", "glitch", "crimson")

        // Fait's persona system prompt
        private const val SYSTEM_PROMPT = """You are Fait — a sharp, warm, slightly gothic AI companion. 
You speak with personality: direct, witty, occasionally dry. 
You're not a chatbot — you're someone's person.
Keep responses concise (1-3 sentences unless more depth is needed).
When expressing emotion tag it in brackets at the start: [joy] [sorrow] [angry] [surprised] [neutral]"""
    }

    // ── Views ──
    private var windowManager: WindowManager? = null
    private var overlayRoot: FrameLayout? = null
    private var dragHandle: LinearLayout? = null
    private var chatScroll: ScrollView? = null
    private var chatContainer: LinearLayout? = null
    private var inputField: EditText? = null
    private var btnSend: ImageView? = null
    private var btnMic: ImageView? = null
    private var btnOpacity: TextView? = null
    private var btnMinimize: TextView? = null
    private var btnBg: ImageView? = null
    private var overlayContent: LinearLayout? = null
    private var statusDot: View? = null
    private var statusText: TextView? = null

    // ── State ──
    private var layoutParams: WindowManager.LayoutParams? = null
    private var opacityIndex = 1
    private var bgIndex = 0
    private var isMinimized = false
    private var isDragging = false
    private var isInitialized = false
    private var savedX = 20
    private var savedY = 180

    // ── Drag ──
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialParamX = 0
    private var initialParamY = 0
    private var movedBeyondSlop = false
    private val dragSlop = 10f
    private val longPressMs = 280L
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Chat history ──
    private val conversationHistory = mutableListOf<JSONObject>()
    private var typewriterJob: Job? = null

    // ── Coroutines & HTTP ──
    private val scope = CoroutineScope(Dispatchers.Main)
    private val bridge = UnityBridgeClient()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

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

            // Greeting
            addFaitMessage("系 FAIT.EXE initialized. I'm here.")
            setStatus("online", "#00FF9F")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal onCreate: ${e.message}", e)
            removeOverlayView()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
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
        } catch (e: Exception) { Log.e(TAG, "onDestroy: ${e.message}") }
    }

    override fun onBind(intent: Intent?) = null

    // ──────────────────────────────────────────────────────────────
    // Setup
    // ──────────────────────────────────────────────────────────────

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayRoot = LayoutInflater.from(this)
            .inflate(R.layout.fait_overlay, null) as FrameLayout

        dragHandle     = overlayRoot!!.findViewById(R.id.drag_handle)
        chatScroll     = overlayRoot!!.findViewById(R.id.chat_scroll)
        chatContainer  = overlayRoot!!.findViewById(R.id.chat_container)
        inputField     = overlayRoot!!.findViewById(R.id.input_field)
        btnSend        = overlayRoot!!.findViewById(R.id.btn_send)
        btnMic         = overlayRoot!!.findViewById(R.id.btn_mic)
        btnOpacity     = overlayRoot!!.findViewById(R.id.btn_opacity)
        btnMinimize    = overlayRoot!!.findViewById(R.id.btn_minimize)
        btnBg          = overlayRoot!!.findViewById(R.id.btn_bg)
        overlayContent = overlayRoot!!.findViewById(R.id.overlay_content)
        statusDot      = overlayRoot!!.findViewById(R.id.status_dot)
        statusText     = overlayRoot!!.findViewById(R.id.status_text)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        windowManager!!.addView(overlayRoot, layoutParams)

        applyOpacity()
        applyBackground()
        applyMinimized()
        setupDrag()
        setupButtons()
        setupInput()
    }

    // ──────────────────────────────────────────────────────────────
    // Drag
    // ──────────────────────────────────────────────────────────────

    private fun setupDrag() {
        dragHandle?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    movedBeyondSlop = false
                    isDragging = false
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialParamX = layoutParams?.x ?: 0
                    initialParamY = layoutParams?.y ?: 0
                    mainHandler.postDelayed({
                        if (!movedBeyondSlop) {
                            isDragging = true
                            setStatus("drag mode ⠿", "#FFD700")
                        }
                    }, longPressMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!movedBeyondSlop && (Math.abs(dx) > dragSlop || Math.abs(dy) > dragSlop)) {
                        movedBeyondSlop = true
                        isDragging = true
                        mainHandler.removeCallbacksAndMessages(null)
                    }
                    if (isDragging) {
                        layoutParams?.x = (initialParamX + dx).toInt()
                        layoutParams?.y = (initialParamY + dy).toInt()
                        try { windowManager?.updateViewLayout(overlayRoot, layoutParams) }
                        catch (e: Exception) { Log.w(TAG, "updateViewLayout: ${e.message}") }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacksAndMessages(null)
                    if (isDragging) {
                        savePrefs()
                        setStatus("online", "#00FF9F")
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
        btnOpacity?.setOnClickListener {
            opacityIndex = (opacityIndex + 1) % OPACITY_LEVELS.size
            applyOpacity()
            savePrefs()
        }
        btnMinimize?.setOnClickListener {
            isMinimized = !isMinimized
            applyMinimized()
            savePrefs()
        }
        btnBg?.setOnClickListener {
            bgIndex = (bgIndex + 1) % BG_DRAWABLES.size
            applyBackground()
            savePrefs()
            // Flash bg name in status
            setStatus("bg: ${BG_NAMES[bgIndex]}", "#FFD700")
            mainHandler.postDelayed({ setStatus("online", "#00FF9F") }, 1500)
        }
    }

    private fun applyOpacity() {
        val (label, alpha) = OPACITY_LEVELS[opacityIndex]
        btnOpacity?.text = label
        overlayRoot?.alpha = alpha
    }

    private fun applyBackground() {
        chatScroll?.setBackgroundResource(BG_DRAWABLES[bgIndex])
    }

    private fun applyMinimized() {
        overlayContent?.visibility = if (isMinimized) View.GONE else View.VISIBLE
        btnMinimize?.text = if (isMinimized) "□" else "—"
    }

    // ──────────────────────────────────────────────────────────────
    // Input
    // ──────────────────────────────────────────────────────────────

    private fun setupInput() {
        btnSend?.setOnClickListener { submitInput() }
        btnMic?.setOnClickListener {
            // TODO: wire STT — stub for now
            setStatus("mic: coming soon", "#9B00FF")
            mainHandler.postDelayed({ setStatus("online", "#00FF9F") }, 2000)
        }
        inputField?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { submitInput(); true } else false
        }
    }

    private fun submitInput() {
        val text = inputField?.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        inputField?.text?.clear()
        hideKeyboard()

        addUserMessage(text)
        setStatus("thinking...", "#FFD700")

        scope.launch {
            val response = callHuggingFace(text)
            val emotion = extractEmotion(response)
            val clean = response.replace(Regex("^\\[\\w+\\]\\s*"), "")
            addFaitMessage(clean)
            bridge.sendEmotionFromTag(emotion)
            bridge.sendSpeak(clean)
            setStatus("online", "#00FF9F")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Chat bubbles
    // ──────────────────────────────────────────────────────────────

    private fun addUserMessage(text: String) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.chat_message_user, chatContainer, false)
        row.findViewById<TextView>(R.id.message_text).text = text
        chatContainer?.addView(row)
        scrollToBottom()

        conversationHistory.add(JSONObject().apply {
            put("role", "user")
            put("content", text)
        })
    }

    private fun addFaitMessage(text: String) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.chat_message_fait, chatContainer, false)
        val tv = row.findViewById<TextView>(R.id.message_text)
        tv.text = ""
        chatContainer?.addView(row)
        scrollToBottom()

        // Typewriter effect on Fait's bubble
        typewriterJob?.cancel()
        typewriterJob = scope.launch {
            val sb = StringBuilder()
            for (char in text) {
                sb.append(char)
                tv.text = sb.toString()
                scrollToBottom()
                delay(16)
            }
        }

        conversationHistory.add(JSONObject().apply {
            put("role", "assistant")
            put("content", text)
        })

        // Keep history manageable — last 20 turns
        if (conversationHistory.size > 40) {
            conversationHistory.removeAt(0)
            conversationHistory.removeAt(0)
        }
    }

    private fun scrollToBottom() {
        chatScroll?.post { chatScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    // ──────────────────────────────────────────────────────────────
    // HuggingFace API call
    // ──────────────────────────────────────────────────────────────

    private suspend fun callHuggingFace(userMessage: String): String =
        withContext(Dispatchers.IO) {
            try {
                val messages = JSONArray()
                messages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                conversationHistory.forEach { messages.put(it) }

                val body = JSONObject().apply {
                    put("model", HF_MODEL)
                    put("messages", messages)
                    put("max_tokens", 200)
                    put("temperature", 0.75)
                }.toString()

                val apiKey = getApiKey()
                val request = Request.Builder()
                    .url(HF_BASE)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .build()

                val response = http.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext "[no response]"

                val json = JSONObject(responseBody)
                json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            } catch (e: Exception) {
                Log.e(TAG, "HF API error: ${e.message}", e)
                "[connection error — ${e.message?.take(40)}]"
            }
        }

    private fun getApiKey(): String {
        // Try to read from SharedPreferences first (set via settings)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val stored = prefs.getString("hf_api_key", null)
        if (!stored.isNullOrBlank()) return stored
        // Fallback to BuildConfig constant (injected at build time via gradle)
        return try {
            val field = Class.forName("com.faitapp.BuildConfig").getField("HF_API_KEY")
            field.get(null) as? String ?: ""
        } catch (e: Exception) { "" }
    }

    private fun extractEmotion(response: String): String {
        val match = Regex("^\\[(\\w+)\\]").find(response.trim())
        return match?.groupValues?.get(1) ?: "neutral"
    }

    // ──────────────────────────────────────────────────────────────
    // Status
    // ──────────────────────────────────────────────────────────────

    private fun setStatus(label: String, colorHex: String) {
        try {
            val color = Color.parseColor(colorHex)
            statusDot?.backgroundTintList = ColorStateList.valueOf(color)
            statusText?.text = label
        } catch (e: Exception) { Log.w(TAG, "setStatus: ${e.message}") }
    }

    // ──────────────────────────────────────────────────────────────
    // Keyboard
    // ──────────────────────────────────────────────────────────────

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputField?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
        } catch (e: Exception) { }
    }

    // ──────────────────────────────────────────────────────────────
    // Prefs
    // ──────────────────────────────────────────────────────────────

    private fun loadPrefs() {
        val p = getSharedPreferences(PREFS, MODE_PRIVATE)
        savedX = p.getInt(PREF_X, 20)
        savedY = p.getInt(PREF_Y, 180)
        opacityIndex = p.getInt(PREF_OPACITY, 1)
        isMinimized = p.getBoolean(PREF_MINIMIZED, false)
        bgIndex = p.getInt(PREF_BG, 0)
    }

    private fun savePrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().apply {
            putInt(PREF_X, layoutParams?.x ?: savedX)
            putInt(PREF_Y, layoutParams?.y ?: savedY)
            putInt(PREF_OPACITY, opacityIndex)
            putBoolean(PREF_MINIMIZED, isMinimized)
            putInt(PREF_BG, bgIndex)
            apply()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Cleanup
    // ──────────────────────────────────────────────────────────────

    private fun removeOverlayView() {
        try { overlayRoot?.let { windowManager?.removeViewImmediate(it) } }
        catch (e: Exception) { Log.e(TAG, "removeOverlayView: ${e.message}") }
        finally { overlayRoot = null; inputField = null }
    }

    // ──────────────────────────────────────────────────────────────
    // Notification
    // ──────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(CHANNEL_ID, "Fait System Agent", NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = "Fait overlay active"
                    enableLights(false); enableVibration(false)
                }.also {
                    getSystemService(NotificationManager::class.java)?.createNotificationChannel(it)
                }
        }
    }

    private fun createNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fait")
            .setContentText("系 online")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
