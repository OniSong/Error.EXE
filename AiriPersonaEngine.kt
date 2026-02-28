package com.faitapp.core

import android.util.Log
import kotlin.random.Random

class AiriPersonaEngine {

    companion object {
        private const val TAG = "AiriPersonaEngine"
    }

    private val offlineResponses = listOf(
        "Connection to the central node is currently severed. My local database is... insufficient for this request.",
        "I am currently in Standalone Mode. A synchronization with the World Line (Internet) would be required to retrieve that data.",
        "Access denied. It seems the information you seek exists outside my current reach. Perhaps a miracle—or a Wi-Fi signal—will occur?",
        "Status: Disconnected. I can see the data in the ether, but I lack the bandwidth to pull it into this reality."
    )

    private val frustrationResponses = listOf(
        "I've checked 1048596 times. There is no signal. Repeating the query won't change the physics of this room.",
        "Are you testing my patience or the local signal strength? Both are currently at zero."
    )

    private val query_retry_count = mutableMapOf<String, Int>()

    fun getFallbackResponse(
        isRepeatedQuery: Boolean = false,
        isDegraded: Boolean = false,
        queryHash: String? = null
    ): String {
        return try {
            when {
                isDegraded -> {
                    Log.d(TAG, "Returning degraded response")
                    offlineResponses[Random.nextInt(offlineResponses.size)]
                }
                isRepeatedQuery -> {
                    Log.d(TAG, "Returning frustration response for repeated query")
                    frustrationResponses[Random.nextInt(frustrationResponses.size)]
                }
                else -> {
                    Log.d(TAG, "Returning standard offline response")
                    offlineResponses[Random.nextInt(offlineResponses.size)]
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting fallback response: ${e.message}", e)
            "A critical synchronization error has occurred. Please verify your connection."
        }
    }

    fun wrapError(error: Throwable, context: String = ""): String {
        return try {
            val errorMsg = error.localizedMessage ?: error.message ?: "Unknown error"
            val contextStr = if (context.isNotEmpty()) " ($context)" else ""
            Log.e(TAG, "Wrapping error: $errorMsg$contextStr")
            "System Error detected in the synchronization layer: $errorMsg$contextStr. Suggesting manual reboot or signal verification."
        } catch (e: Exception) {
            Log.e(TAG, "Error wrapping error: ${e.message}")
            "A critical error occurred during error handling. System unstable."
        }
    }

    fun trackQueryRetry(queryHash: String): Int {
        return try {
            val currentCount = query_retry_count[queryHash] ?: 0
            val newCount = currentCount + 1
            query_retry_count[queryHash] = newCount
            Log.d(TAG, "Query retry count for $queryHash: $newCount")
            newCount
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking query retry: ${e.message}")
            0
        }
    }

    fun clearQueryRetries() {
        try {
            query_retry_count.clear()
            Log.d(TAG, "Query retry tracking cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing query retries: ${e.message}")
        }
    }
}
