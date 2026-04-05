package com.faitapp.bridge

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.net.SocketException

/**
 * Sends commands from Fait (Kotlin) to the Unity avatar app over TCP.
 *
 * Protocol: newline-delimited JSON on 127.0.0.1:9876
 *
 * Usage:
 *   val bridge = UnityBridgeClient()
 *   bridge.sendExpression("joy")
 *   bridge.sendSpeak("Hello, I'm Fait.")
 *   bridge.sendIdle()
 */
class UnityBridgeClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 9876
) {
    companion object {
        private const val TAG = "UnityBridgeClient"
        private const val TIMEOUT_MS = 2000
    }

    /**
     * Send an expression command.
     * @param expression one of: neutral, joy, happy, angry, sorrow, sad, surprised, relaxed
     * @param weight 0.0–1.0 blend weight (default 1.0)
     */
    suspend fun sendExpression(expression: String, weight: Float = 1f) {
        val cmd = JSONObject().apply {
            put("cmd", "expression")
            put("value", expression)
            put("weight", weight)
        }
        send(cmd)
    }

    /**
     * Send a speak command with text for lip sync.
     * @param text the text Fait is speaking (used for phoneme estimation)
     */
    suspend fun sendSpeak(text: String) {
        val cmd = JSONObject().apply {
            put("cmd", "speak")
            put("text", text)
        }
        send(cmd)
    }

    /** Return avatar to idle/neutral state */
    suspend fun sendIdle() {
        send(JSONObject().apply { put("cmd", "idle") })
    }

    /** Trigger a manual blink */
    suspend fun sendBlink() {
        send(JSONObject().apply { put("cmd", "blink") })
    }

    /** Check if Unity bridge is alive */
    suspend fun ping(): Boolean {
        return try {
            send(JSONObject().apply { put("cmd", "ping") })
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Map an LLM-detected emotion string to a VRM expression and send it.
     * Call this after your Fact LLM or Persona LLM tags the response emotion.
     */
    suspend fun sendEmotionFromTag(emotion: String) {
        val expression = when (emotion.lowercase().trim()) {
            "happy", "excited", "pleased" -> "joy"
            "sad", "sorry", "empathetic" -> "sorrow"
            "angry", "frustrated" -> "angry"
            "surprised", "shocked" -> "surprised"
            "calm", "neutral", "thinking" -> "neutral"
            "relaxed", "playful" -> "relaxed"
            else -> "neutral"
        }
        sendExpression(expression)
    }

    // ──────────────────────────────────────────────
    // Internal send — opens a fresh socket per message (simple, stateless)
    // For high-frequency use, swap to a persistent connection via UnityBridgeSession
    // ──────────────────────────────────────────────

    private suspend fun send(json: JSONObject) = withContext(Dispatchers.IO) {
        try {
            Socket(host, port).use { socket ->
                socket.soTimeout = TIMEOUT_MS
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val line = json.toString() + "\n"
                writer.print(line)
                writer.flush()

                // Read ack (optional — don't block if Unity is busy)
                try {
                    val ack = reader.readLine()
                    Log.d(TAG, "Ack: $ack | cmd=${json.optString("cmd")}")
                } catch (e: Exception) {
                    Log.w(TAG, "No ack (non-fatal): ${e.message}")
                }
            }
        } catch (e: SocketException) {
            Log.w(TAG, "Unity not running or bridge not listening: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Bridge send error: ${e.message}", e)
        }
    }
}
